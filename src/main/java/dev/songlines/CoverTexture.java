package dev.songlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;

/** Обложка текущего трека как динамическая текстура. Декодируем встроенным в Minecraft stb_image (jpg/png) — без AWT. */
public final class CoverTexture {
    public static final Identifier ID = Identifier.of(Songlines.ID, "cover");
    public static volatile boolean ready;
    public static volatile int w, h;
    public static volatile int avgColor = 0x202020;
    private static volatile int accentColor = 0xFFD166;

    /** Основной (яркий) цвет обложки; без обложки — золотистый по умолчанию. */
    public static int accent() {
        return ready ? accentColor : 0xFFD166;
    }
    private static NativeImageBackedTexture tex;

    /** Можно вызывать из любого потока. null — убрать обложку. */
    public static void set(byte[] bytes) {
        MinecraftClient.getInstance().execute(() -> apply(bytes));
    }

    private static void clear() {
        ready = false;
        if (tex != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
            tex = null;
        }
    }

    private static void apply(byte[] bytes) {
        clear();
        if (bytes == null || bytes.length == 0) return;
        try (NativeImage src = NativeImage.read(new ByteArrayInputStream(bytes))) {
            int side = Math.min(src.getWidth(), src.getHeight());
            int sx = (src.getWidth() - side) / 2, sy = (src.getHeight() - side) / 2;
            int target = Math.min(side, 256);
            NativeImage dst = new NativeImage(target, target, false);
            src.resizeSubRectTo(sx, sy, side, side, dst);

            long r = 0, g = 0, b = 0;
            int n = 0;
            int step = Math.max(1, target / 16);
            for (int y = 0; y < target; y += step) {
                for (int x = 0; x < target; x += step) {
                    int c = dst.getColorArgb(x, y);
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                    n++;
                }
            }
            avgColor = n == 0 ? 0x202020 : (int) (((r / n) << 16) | ((g / n) << 8) | (b / n));

            accentColor = accentFrom(dst, target);
            tex = new NativeImageBackedTexture(() -> "songlines_cover", dst);
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, tex);
            w = target;
            h = target;
            ready = true;
        } catch (Exception e) {
            Songlines.LOG.warn("cover decode failed: {}", e.toString());
        }
    }

    /** Преобладающий насыщенный цвет: гистограмма по 4 бита на канал, вес зависит от насыщенности и яркости. */
    private static int accentFrom(NativeImage img, int size) {
        double[] weight = new double[4096];
        long[] rs = new long[4096], gs = new long[4096], bs = new long[4096], cnt = new long[4096];
        for (int y = 0; y < size; y += 2) {
            for (int x = 0; x < size; x += 2) {
                int c = img.getColorArgb(x, y);
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
                double v = mx / 255.0, s = mx == 0 ? 0 : (mx - mn) / (double) mx;
                if (v < 0.18) continue;
                int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                weight[key] += (0.12 + s) * (0.4 + 0.6 * v);
                rs[key] += r;
                gs[key] += g;
                bs[key] += b;
                cnt[key]++;
            }
        }
        int best = -1;
        double bw = 0;
        for (int i = 0; i < 4096; i++) {
            if (weight[i] > bw) {
                bw = weight[i];
                best = i;
            }
        }
        if (best < 0 || cnt[best] == 0) return 0xFFFFFF;
        int r = (int) (rs[best] / cnt[best]), g = (int) (gs[best] / cnt[best]), b = (int) (bs[best] / cnt[best]);
        return readable(r, g, b);
    }

    /** Делает цвет достаточно ярким для текста, сохраняя оттенок. Серые обложки дают почти белый. */
    private static int readable(int r, int g, int b) {
        double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
        double mx = Math.max(rf, Math.max(gf, bf)), mn = Math.min(rf, Math.min(gf, bf)), d = mx - mn;
        double s = mx == 0 ? 0 : d / mx, v = mx, h = 0;
        if (d > 1e-9) {
            if (mx == rf) h = ((gf - bf) / d) % 6;
            else if (mx == gf) h = (bf - rf) / d + 2;
            else h = (rf - gf) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        if (s < 0.12) return 0xF2F2F2;
        s = Math.max(s, 0.45);
        v = Math.max(v, 0.9);
        double c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c, r1, g1, b1;
        int sector = (int) Math.floor(h * 6) % 6;
        switch (sector) {
            case 0 -> { r1 = c; g1 = x; b1 = 0; }
            case 1 -> { r1 = x; g1 = c; b1 = 0; }
            case 2 -> { r1 = 0; g1 = c; b1 = x; }
            case 3 -> { r1 = 0; g1 = x; b1 = c; }
            case 4 -> { r1 = x; g1 = 0; b1 = c; }
            default -> { r1 = c; g1 = 0; b1 = x; }
        }
        return (Math.round((float) ((r1 + m) * 255)) << 16) | (Math.round((float) ((g1 + m) * 255)) << 8) | Math.round((float) ((b1 + m) * 255));
    }
}
