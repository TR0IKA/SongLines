package dev.songlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Три режима: BOTTOM (строка внизу, с заливкой "караоке"), WORLD (строки всплывают в сцене и исчезают),
 * PANEL (несколько строк сбоку, текущая подсвечена).
 * WORLD привязан к направлению взгляда в момент появления: при повороте головы строка остаётся "в мире".
 */
public final class Subtitles {
    private static final class Floating {
        String text;
        float yaw, pitch;
        long born;
    }

    private static final List<Floating> floating = new ArrayList<>();
    private static final Random RND = new Random();
    private static List<Lyrics.Line> lastList = List.of();
    private static int lastIdx = -2, slot = 0;

    public static void reset() {
        floating.clear();
        lastIdx = -2;
    }

    // ---------- такт: создаём всплывающие строки ----------

    public static void tick(MinecraftClient mc) {
        Config c = Songlines.cfg;
        NowPlaying np = Songlines.playback.np;
        if (c.subMode != Config.SubMode.WORLD || !np.active) return;
        List<Lyrics.Line> lines = np.lyrics;
        if (lines != lastList) {
            lastList = lines;
            lastIdx = -2;
        }
        int idx = Lyrics.indexAt(lines, np.positionMs() + c.lyricsOffsetMs);
        if (idx != lastIdx) {
            lastIdx = idx;
            if (idx >= 0 && np.playing && !lines.get(idx).text().isBlank()) spawn(mc, lines.get(idx).text());
        }
    }

    private static void spawn(MinecraftClient mc, String text) {
        Config c = Songlines.cfg;
        while (floating.size() >= Math.max(1, c.maxFloating)) floating.remove(0);
        Camera cam = mc.gameRenderer.getCamera();
        Floating f = new Floating();
        f.text = text;
        f.yaw = cam.getYaw() + (RND.nextFloat() * 2f - 1f) * c.spreadDegrees;
        f.pitch = MathHelper.clamp(cam.getPitch() + ((slot++ % 3) - 1) * 7f + (RND.nextFloat() * 2f - 1f) * 2f, -60f, 60f);
        f.born = System.nanoTime();
        floating.add(f);
    }

    // ---------- отрисовка ----------

    public static void render(DrawContext ctx, MinecraftClient mc) {
        Config c = Songlines.cfg;
        NowPlaying np = Songlines.playback.np;
        if (c.subMode == Config.SubMode.OFF || !np.active) return;
        List<Lyrics.Line> lines = np.lyrics;
        if (lines.isEmpty()) return;
        long pos = np.positionMs() + c.lyricsOffsetMs;
        int idx = Lyrics.indexAt(lines, pos);
        switch (c.subMode) {
            case BOTTOM -> bottom(ctx, mc, c, lines, idx, pos);
            case PANEL -> panel(ctx, mc, c, lines, idx);
            case WORLD -> world(ctx, mc, c);
            default -> {
            }
        }
    }

    private static MutableText styled(String s, boolean bold) {
        MutableText t = Text.literal(s);
        return bold ? t.formatted(Formatting.BOLD) : t;
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
        int base = Gfx.argb(Config.rgb(c.textColor, 0xFFFFFF), 1f);
        int act = Gfx.argb(Config.rgb(c.activeColor, 0xFFD166), 1f);

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
        int baseRgb = Config.rgb(c.textColor, 0xFFFFFF), actRgb = Config.rgb(c.activeColor, 0xFFD166);
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

    private static void world(DrawContext ctx, MinecraftClient mc, Config c) {
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        Camera cam = mc.gameRenderer.getCamera();
        long now = System.nanoTime();
        float fin = Math.max(0f, c.fadeInSeconds), fout = Math.max(0f, c.fadeOutSeconds), hold = Math.max(0f, c.holdSeconds);
        float total = fin + hold + fout;
        int rgb = Config.rgb(c.activeColor, 0xFFD166);

        Iterator<Floating> it = floating.iterator();
        while (it.hasNext()) {
            Floating f = it.next();
            float t = (now - f.born) / 1e9f;
            if (t > total) {
                it.remove();
                continue;
            }
            float dyaw = MathHelper.wrapDegrees(f.yaw - cam.getYaw());
            float dpitch = cam.getPitch() - f.pitch;
            if (Math.abs(dyaw) > 80f || Math.abs(dpitch) > 70f) continue;
            double tv = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
            double th = tv * sw / (double) sh;
            float px = (float) (sw / 2.0 + Math.tan(Math.toRadians(dyaw)) / th * sw / 2.0);
            float py = (float) (sh / 2.0 - Math.tan(Math.toRadians(dpitch)) / tv * sh / 2.0);

            float a;
            if (c.anim == Config.Anim.NONE) a = 1f;
            else if (t < fin) a = t / fin;
            else if (t > fin + hold) a = fout <= 0 ? 0f : 1f - (t - fin - hold) / fout;
            else a = 1f;
            if (a < 0.03f) continue;
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
                    a = t > fin + hold ? a : 1f;
                }
                default -> {
                }
            }
            Text full = styled(f.text, c.textBold);
            int w = tr.getWidth(full);
            Gfx.push(ctx, px + dx - w * sc / 2f, py + dy - 4.5f * sc, sc);
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
