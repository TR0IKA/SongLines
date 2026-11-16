package dev.songlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Режимы субтитров:
 * BOTTOM  — строка над хотбаром с "караоке"-заливкой;
 * WORLD   — всплывающий текст (как было раньше): привязан к направлению взгляда в момент появления, двигается/анимируется;
 * ENTITY  — текст стоит в мире на месте и не двигается; лицом к игроку всегда или в момент появления;
 * PANEL   — несколько строк сбоку.
 * Во всех режимах можно показывать по N слов (wordsPerChunk). На паузе новые строки не появляются, а таймеры замирают.
 */
public final class Subtitles {
    private static final class Floating {
        String text;
        float yaw, pitch;      // WORLD: направление
        double x, y, z;        // ENTITY: точка в мире
        double nx, nz;         // ENTITY/FIXED: куда "смотрит" текст (горизонтальная нормаль)
        int w;                 // ширина текста в пикселях (без масштаба)
        long born;             // время рождения по "часам воспроизведения"
    }

    /** Камера, из которой считаем проекцию точек мира на экран. */
    private static final class View {
        final Vec3d cp;
        final float yaw, pitch;
        final int sw, sh;
        final double fx, fy, fz, rx, rz, ux, uy, uz, tv, th;

        View(MinecraftClient mc, int sw, int sh) {
            Camera cam = mc.gameRenderer.getCamera();
            this.cp = cam.getCameraPos();
            this.yaw = cam.getYaw();
            this.pitch = cam.getPitch();
            this.sw = sw;
            this.sh = sh;
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            double sY = Math.sin(yr), cY = Math.cos(yr), sP = Math.sin(pr), cP = Math.cos(pr);
            fx = -sY * cP;
            fy = -sP;
            fz = cY * cP;
            rx = -cY;
            rz = -sY;
            ux = -sY * sP;
            uy = cP;
            uz = cY * sP;
            tv = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
            th = tv * sw / (double) sh;
        }

        /** Точка на экране для направления (yaw, pitch) относительно камеры. */
        float[] dir(float y, float p) {
            float dyaw = MathHelper.wrapDegrees(y - yaw);
            float dpitch = pitch - p;
            if (Math.abs(dyaw) > 80f || Math.abs(dpitch) > 70f) return null;
            float px = (float) (sw / 2.0 + Math.tan(Math.toRadians(dyaw)) / th * sw / 2.0);
            float py = (float) (sh / 2.0 - Math.tan(Math.toRadians(dpitch)) / tv * sh / 2.0);
            return new float[]{px, py};
        }

        /** Точка на экране для точки мира + расстояние вдоль взгляда (zc). */
        float[] point(double x, double y, double z) {
            double dx = x - cp.x, dy = y - cp.y, dz = z - cp.z;
            double zc = dx * fx + dy * fy + dz * fz;
            if (zc < 0.3) return null;
            double xc = dx * rx + dz * rz;
            double yc = dx * ux + dy * uy + dz * uz;
            float px = (float) (sw / 2.0 + (xc / zc) / th * sw / 2.0);
            float py = (float) (sh / 2.0 - (yc / zc) / tv * sh / 2.0);
            return new float[]{px, py, (float) zc};
        }
    }

    private static final List<Floating> floating = new ArrayList<>();
    private static final Random RND = new Random();
    private static Config.SubMode floatMode = Config.SubMode.OFF;
    private static List<Lyrics.Line> lastList = List.of();
    private static int lastIdx = -2;

    // "часы воспроизведения": идут только пока трек играет, поэтому на паузе строки не исчезают
    private static long clockNs, lastNs;

    // кэш строк, разбитых по словам
    private static List<Lyrics.Line> cacheSrc;
    private static List<Lyrics.Line> cacheUnits = List.of();
    private static int cacheN = -1;

    public static void reset() {
        floating.clear();
        lastIdx = -2;
    }

    private static void updateClock() {
        long now = System.nanoTime();
        if (lastNs != 0 && Songlines.playback.np.playing) clockNs += Math.min(now - lastNs, 200_000_000L);
        lastNs = now;
    }

    private static List<Lyrics.Line> units(NowPlaying np, Config c) {
        List<Lyrics.Line> src = np.lyrics;
        if (src != cacheSrc || c.wordsPerChunk != cacheN) {
            cacheSrc = src;
            cacheN = c.wordsPerChunk;
            cacheUnits = Lyrics.chunk(src, c.wordsPerChunk);
        }
        return cacheUnits;
    }

    // ---------- такт: появление новых строк ----------

    public static void tick(MinecraftClient mc) {
        updateClock();
        Config c = Songlines.cfg;
        NowPlaying np = Songlines.playback.np;
        if (c.subMode != floatMode) {
            floatMode = c.subMode;
            floating.clear();
            lastIdx = -2;
        }
        if ((c.subMode != Config.SubMode.WORLD && c.subMode != Config.SubMode.ENTITY) || !np.active || mc.world == null) return;
        List<Lyrics.Line> lines = units(np, c);
        if (lines != lastList) {
            lastList = lines;
            lastIdx = -2;
        }
        int idx = Lyrics.indexAt(lines, np.positionMs() + c.lyricsOffsetMs);
        if (idx != lastIdx) {
            lastIdx = idx;
            // на паузе (и при перемотке на паузе) новые строки не создаются
            if (np.playing && idx >= 0 && !lines.get(idx).text().isBlank()) spawn(mc, c, lines.get(idx).text());
        }
    }

    private static Vec3d spawnPoint(MinecraftClient mc, View v, float yaw, float pitch, double dist) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3d dir = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        double d = dist;
        if (mc.world != null) {
            BlockHitResult h = mc.world.raycast(new RaycastContext(v.cp, v.cp.add(dir.multiply(dist)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (h.getType() == HitResult.Type.BLOCK) d = Math.max(1.2, h.getPos().distanceTo(v.cp) - 0.5);
        }
        return v.cp.add(dir.multiply(d));
    }

    private static void spawn(MinecraftClient mc, Config c, String text) {
        while (floating.size() >= Math.max(1, c.maxFloating)) floating.remove(0);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        View v = new View(mc, sw, sh);
        boolean entity = c.subMode == Config.SubMode.ENTITY;

        Floating f = new Floating();
        f.text = text;
        f.born = clockNs;
        f.w = mc.textRenderer.getWidth(styled(text, c.textBold));

        // выбираем из нескольких кандидатов место, где строка меньше всего налезает на уже висящие
        float bestScore = Float.MAX_VALUE, bestYaw = v.yaw, bestPitch = v.pitch;
        Vec3d bestPoint = null;
        for (int k = 0; k < 14; k++) {
            float oy, op;
            if (k < 9) {
                oy = ((k % 3) - 1) * c.spreadDegrees + (RND.nextFloat() * 2f - 1f) * 4f;
                op = ((k / 3) - 1) * 9f + (RND.nextFloat() * 2f - 1f) * 2f;
            } else {
                oy = (RND.nextFloat() * 2f - 1f) * c.spreadDegrees;
                op = (RND.nextFloat() * 2f - 1f) * 11f;
            }
            float yaw = v.yaw + oy, pitch = MathHelper.clamp(v.pitch + op, -60f, 60f);
            Vec3d pt = null;
            float[] px;
            if (entity) {
                pt = spawnPoint(mc, v, yaw, pitch, c.entityDistance);
                px = v.point(pt.x, pt.y, pt.z);
            } else {
                px = v.dir(yaw, pitch);
            }
            float score = candidateScore(v, c, f.w, px, entity) + RND.nextFloat() * 0.01f;
            if (score < bestScore) {
                bestScore = score;
                bestYaw = yaw;
                bestPitch = pitch;
                bestPoint = pt;
            }
        }
        f.yaw = bestYaw;
        f.pitch = bestPitch;
        if (entity && bestPoint != null) {
            f.x = bestPoint.x;
            f.y = bestPoint.y;
            f.z = bestPoint.z;
            double hx = v.cp.x - f.x, hz = v.cp.z - f.z, len = Math.hypot(hx, hz);
            f.nx = len < 1e-4 ? 0 : hx / len;
            f.nz = len < 1e-4 ? 1 : hz / len;
        }
        floating.add(f);
    }

    /** Чем меньше, тем лучше. Штраф за пересечение с другими строками и за выход за край экрана. */
    private static float candidateScore(View v, Config c, int textW, float[] px, boolean entity) {
        if (px == null) return 1e6f;
        float sc = c.textScale;
        float w = textW * sc + 6, h = 10 * sc + 4;
        float score = 0f;
        if (px[0] - w / 2 < 4) score += 500 + (4 - (px[0] - w / 2));
        if (px[0] + w / 2 > v.sw - 4) score += 500 + (px[0] + w / 2 - (v.sw - 4));
        for (Floating e : floating) {
            float[] p = entity ? v.point(e.x, e.y, e.z) : v.dir(e.yaw, e.pitch);
            if (p == null) continue;
            float ew = e.w * sc + 6;
            float ox = Math.min(px[0] + w / 2, p[0] + ew / 2) - Math.max(px[0] - w / 2, p[0] - ew / 2);
            float oy = Math.min(px[1] + h / 2, p[1] + h / 2) - Math.max(px[1] - h / 2, p[1] - h / 2);
            if (ox > 0 && oy > 0) score += ox * oy / 10f + 10f;
        }
        return score;
    }

    // ---------- отрисовка ----------

    public static void render(DrawContext ctx, MinecraftClient mc) {
        Config c = Songlines.cfg;
        NowPlaying np = Songlines.playback.np;
        if (c.subMode == Config.SubMode.OFF || !np.active) return;
        updateClock();
        List<Lyrics.Line> lines = units(np, c);
        if (lines.isEmpty()) return;
        long pos = np.positionMs() + c.lyricsOffsetMs;
        int idx = Lyrics.indexAt(lines, pos);
        switch (c.subMode) {
            case BOTTOM -> bottom(ctx, mc, c, lines, idx, pos);
            case PANEL -> panel(ctx, mc, c, lines, idx);
            case WORLD -> world(ctx, mc, c);
            case ENTITY -> entity(ctx, mc, c);
            default -> {
            }
        }
    }

    private static MutableText styled(String s, boolean bold) {
        Style st = Fonts.apply(Style.EMPTY);
        if (bold) st = st.withBold(true);
        return Text.literal(s).setStyle(st);
    }

    private static void bottom(DrawContext ctx, MinecraftClient mc, Config c, List<Lyrics.Line> lines, int idx, long pos) {
        if (idx < 0) return;
        Lyrics.Line ln = lines.get(idx);
        String s = ln.text();
        if (s.isBlank()) return;
        TextRenderer tr = mc.textRenderer;
        float sc = c.textScale;
        Text full = styled(s, c.textBold);
        int w = tr.getWidth(full);
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float x = (sw - w * sc) / 2f, y = sh - c.bottomOffset - 9 * sc;
        int base = Gfx.argb(c.baseRgb(), 1f);
        int act = Gfx.argb(c.activeRgb(), 1f);

        Gfx.push(ctx, x, y, sc);
        if (c.textBackground) Gfx.fill(ctx, -3, -2, w + 3, 10, 0x88000000);
        if (c.karaokeWipe) {
            long next = idx + 1 < lines.size() ? lines.get(idx + 1).ms() : ln.ms() + 4000;
            float f = MathHelper.clamp((pos - ln.ms()) / (float) Math.max(1, next - ln.ms()), 0f, 1f);
            int cut = Math.round(f * s.length());
            Text a = styled(s.substring(0, cut), c.textBold), b = styled(s.substring(cut), c.textBold);
            Gfx.text(ctx, a, 0, 0, act, c.textShadow);
            Gfx.text(ctx, b, tr.getWidth(a), 0, base, c.textShadow);
        } else {
            Gfx.text(ctx, full, 0, 0, act, c.textShadow);
        }
        Gfx.pop(ctx);
    }

    private static void panel(DrawContext ctx, MinecraftClient mc, Config c, List<Lyrics.Line> lines, int idx) {
        TextRenderer tr = mc.textRenderer;
        float sc = c.textScale;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int n = Math.max(3, c.panelLines | 1), half = n / 2;
        int maxW = (int) (sw * 0.38f / sc);
        float rowH = 12 * sc;
        float y0 = sh / 2f - n * rowH / 2f;
        int baseRgb = c.baseRgb(), actRgb = c.activeRgb();
        for (int i = -half; i <= half; i++) {
            int li = idx + i;
            if (li < 0 || li >= lines.size()) continue;
            String s = tr.trimToWidth(lines.get(li).text(), maxW);
            if (s.isBlank()) continue;
            Text t = styled(s, c.textBold && i == 0);
            int w = tr.getWidth(t);
            float a = i == 0 ? 1f : Math.max(0.2f, 1f - Math.abs(i) * 0.3f);
            float x = c.panelRight ? sw - 8 - w * sc : 8;
            float y = y0 + (i + half) * rowH;
            Gfx.push(ctx, x, y, sc);
            if (c.textBackground) Gfx.fill(ctx, -3, -2, w + 3, 10, Gfx.argb(0x000000, 0.5f * a));
            Gfx.text(ctx, t, 0, 0, Gfx.argb(i == 0 ? actRgb : baseRgb, a), c.textShadow);
            Gfx.pop(ctx);
        }
    }

    /** WORLD — прежний режим: строка привязана к направлению взгляда в момент появления. */
    private static void world(DrawContext ctx, MinecraftClient mc, Config c) {
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        View v = new View(mc, sw, sh);
        float fin = Math.max(0f, c.fadeInSeconds), fout = Math.max(0f, c.fadeOutSeconds), hold = Math.max(0f, c.holdSeconds);
        float total = fin + hold + fout;
        int rgb = c.activeRgb();

        Iterator<Floating> it = floating.iterator();
        while (it.hasNext()) {
            Floating f = it.next();
            float t = (clockNs - f.born) / 1e9f;
            if (t > total) {
                it.remove();
                continue;
            }
            float[] p = v.dir(f.yaw, f.pitch);
            if (p == null) continue;

            float a;
            if (c.anim == Config.Anim.NONE) a = 1f;
            else if (t < fin) a = t / fin;
            else if (t > fin + hold) a = fout <= 0 ? 0f : 1f - (t - fin - hold) / fout;
            else a = 1f;
            float prog = fin <= 0 ? 1f : MathHelper.clamp(t / fin, 0f, 1f);

            float sc = c.textScale, dx = 0, dy = 0;
            String s = f.text;
            switch (c.anim) {
                case RISE -> dy = -14f * easeOut(MathHelper.clamp(t / Math.max(0.01f, total), 0f, 1f));
                case POP -> sc *= 0.6f + 0.4f * easeOutBack(prog);
                case SLIDE -> dx = -24f * (1f - easeOut(prog));
                case TYPEWRITER -> {
                    int vis = (int) Math.ceil(s.length() * MathHelper.clamp(t / Math.max(0.3f, fin), 0f, 1f));
                    s = s.substring(0, Math.min(s.length(), vis));
                    if (t <= fin + hold) a = 1f;
                }
                default -> {
                }
            }
            if (a < 0.03f) continue;
            Text full = styled(f.text, c.textBold);
            int w = tr.getWidth(full);
            Gfx.push(ctx, p[0] + dx - w * sc / 2f, p[1] + dy - 4.5f * sc, sc);
            if (c.textBackground) Gfx.fill(ctx, -3, -2, w + 3, 10, Gfx.argb(0x000000, 0.5f * a));
            Gfx.text(ctx, styled(s, c.textBold), 0, 0, Gfx.argb(rgb, a), c.textShadow);
            Gfx.pop(ctx);
        }
    }

    /** ENTITY — текст "стоит" в точке мира, не двигается, виден с параллаксом, прячется за блоками. */
    private static void entity(DrawContext ctx, MinecraftClient mc, Config c) {
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        View v = new View(mc, sw, sh);
        float fin = Math.max(0f, c.fadeInSeconds), fout = Math.max(0f, c.fadeOutSeconds), hold = Math.max(0f, c.holdSeconds);
        float total = fin + hold + fout;
        int rgb = c.activeRgb();

        Iterator<Floating> it = floating.iterator();
        while (it.hasNext()) {
            Floating f = it.next();
            float t = (clockNs - f.born) / 1e9f;
            if (t > total) {
                it.remove();
                continue;
            }
            float[] p = v.point(f.x, f.y, f.z);
            if (p == null) continue;
            if (p[0] < -300 || p[0] > sw + 300 || p[1] < -60 || p[1] > sh + 60) continue;

            // прячем за блоками
            if (c.entityHideBehindBlocks && mc.world != null) {
                Vec3d target = new Vec3d(f.x, f.y, f.z);
                BlockHitResult h = mc.world.raycast(new RaycastContext(v.cp, target,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (h.getType() == HitResult.Type.BLOCK && h.getPos().distanceTo(v.cp) < v.cp.distanceTo(target) - 0.3) continue;
            }

            float a;
            if (c.entityAnim == Config.EntityAnim.NONE) a = 1f;
            else if (t < fin) a = t / fin;
            else if (t > fin + hold) a = fout <= 0 ? 0f : 1f - (t - fin - hold) / fout;
            else a = 1f;
            float prog = fin <= 0 ? 1f : MathHelper.clamp(t / fin, 0f, 1f);

            float sc = c.textScale;
            String s = f.text;
            switch (c.entityAnim) {
                case POP -> sc *= 0.6f + 0.4f * easeOutBack(prog);
                case TYPEWRITER -> {
                    int vis = (int) Math.ceil(s.length() * MathHelper.clamp(t / Math.max(0.3f, fin), 0f, 1f));
                    s = s.substring(0, Math.min(s.length(), vis));
                    if (t <= fin + hold) a = 1f;
                }
                default -> {
                }
            }
            if (c.entityScaleByDistance) {
                sc *= MathHelper.clamp((float) (Math.max(1f, c.entityDistance) / p[2]), 0.35f, 2.5f);
            }

            // FIXED: плоскость текста не поворачивается; при взгляде сбоку она сужается по ширине
            float sx = sc;
            if (c.entityFacing == Config.Facing.FIXED) {
                double vx = v.cp.x - f.x, vz = v.cp.z - f.z, len = Math.hypot(vx, vz);
                double cosT = len < 1e-4 ? 1 : (vx * f.nx + vz * f.nz) / len;
                float ws = (float) Math.abs(cosT);
                if (ws < 0.12f) continue;
                a *= MathHelper.clamp((ws - 0.12f) / 0.25f, 0f, 1f);
                sx = sc * ws;
            }
            if (a < 0.03f) continue;

            Text full = styled(f.text, c.textBold);
            int w = tr.getWidth(full);
            Gfx.pushXY(ctx, p[0] - w * sx / 2f, p[1] - 4.5f * sc, sx, sc);
            if (c.textBackground) Gfx.fill(ctx, -3, -2, w + 3, 10, Gfx.argb(0x000000, 0.5f * a));
            Gfx.text(ctx, styled(s, c.textBold), 0, 0, Gfx.argb(rgb, a), c.textShadow);
            Gfx.pop(ctx);
        }
    }

    private static float easeOut(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private static float easeOutBack(float p) {
        float c1 = 1.70158f, c3 = c1 + 1f, q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }
}
