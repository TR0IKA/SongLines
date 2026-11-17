package dev.songlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Обложка текущего трека как динамическая текстура.
 * Картинка декодируется в фоновом потоке напрямую через stb_image из LWJGL (jpg/png, без AWT и без NativeImage.read),
 * обрезается до квадрата и уменьшается до 256 px в чистой Java. В основном потоке остаётся только создать текстуру.
 * Ход загрузки виден в строке «Cover:» в меню M (поле status).
 */
public final class CoverTexture {
    public static final Identifier ID = Identifier.of(Songlines.ID, "cover");
    public static volatile boolean ready;
    public static volatile int w, h;
    public static volatile int avgColor = 0x202020;
    private static volatile int accentColor = 0xFFD166;

    /** Что происходит с обложкой сейчас — для диагностики в меню. */
    public static volatile String status = "—";
    private static volatile String base = "—";

    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "songlines-cover-decode");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger GEN = new AtomicInteger();
    private static NativeImageBackedTexture tex;

    /** Основной (яркий) цвет обложки; без обложки — золотистый по умолчанию. */
    public static int accent() {
        return ready ? accentColor : 0xFFD166;
    }

    /** Записать, откуда пришла обложка (файл / мост / сеть). Результат декодирования дописывается следом. */
    public static void info(String s) {
        base = s;
        status = s;
    }

    private static final class Decoded {
        int size;
        int[] argb;
    }

    /** Можно вызывать из любого потока. null — убрать обложку. */
    public static void set(byte[] bytes) {
        final int gen = GEN.incrementAndGet();
        if (bytes == null || bytes.length == 0) {
            MinecraftClient.getInstance().execute(() -> {
                if (GEN.get() == gen) clear();
            });
            return;
        }
        EX.execute(() -> {
            Decoded d;
            try {
                d = decode(bytes);
            } catch (Throwable t) {
                Songlines.LOG.warn("cover decode failed: {}", t.toString());
                if (GEN.get() == gen) status = base + " → " + L.t("decode error: ", "ошибка декодирования: ") + brief(t);
                return;
            }
            final Decoded dd = d;
            MinecraftClient.getInstance().execute(() -> {
                if (GEN.get() == gen) upload(dd);
            });
        });
    }

    private static void clear() {
        ready = false;
        if (tex != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
            tex = null;
        }
    }

    // ---------- фоновый поток ----------

    private static Decoded decode(byte[] bytes) throws Exception {
        ByteBuffer in = MemoryUtil.memAlloc(bytes.length);
        try {
            in.put(bytes).flip();
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer bx = st.mallocInt(1), by = st.mallocInt(1), bc = st.mallocInt(1);
                ByteBuffer px = STBImage.stbi_load_from_memory(in, bx, by, bc, 4);
                if (px == null) throw new IllegalStateException("stb: " + STBImage.stbi_failure_reason());
                try {
                    return crop(px, bx.get(0), by.get(0));
                } finally {
                    STBImage.stbi_image_free(px);
                }
            }
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    /** Квадрат по центру, усреднение блоков до 256x256 (или меньше, если картинка маленькая). */
    private static Decoded crop(ByteBuffer px, int iw, int ih) {
        int side = Math.min(iw, ih);
        int sx = (iw - side) / 2, sy = (ih - side) / 2;
        int target = Math.min(side, 256);
        int[] out = new int[target * target];
        for (int ty = 0; ty < target; ty++) {
            int y0 = sy + (int) ((long) ty * side / target);
            int y1 = Math.max(y0 + 1, sy + (int) ((long) (ty + 1) * side / target));
            for (int tx = 0; tx < target; tx++) {
                int x0 = sx + (int) ((long) tx * side / target);
                int x1 = Math.max(x0 + 1, sx + (int) ((long) (tx + 1) * side / target));
                long r = 0, g = 0, b = 0, a = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    int row = yy * iw;
                    for (int xx = x0; xx < x1; xx++) {
                        int o = (row + xx) * 4;
                        r += px.get(o) & 255;
                        g += px.get(o + 1) & 255;
                        b += px.get(o + 2) & 255;
                        a += px.get(o + 3) & 255;
                        n++;
                    }
                }
                out[ty * target + tx] = ((int) (a / n) << 24) | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
            }
        }
        Decoded d = new Decoded();
        d.size = target;
        d.argb = out;
        return d;
    }

    // ---------- основной поток ----------

    private static void upload(Decoded d) {
        clear();
        try {
            int n = d.size;
            NativeImage img = new NativeImage(n, n, false);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) img.setColorArgb(x, y, d.argb[y * n + x]);
            }
            avgColor = average(d.argb, n);
            accentColor = accentFrom(d.argb, n);
            tex = new NativeImageBackedTexture(() -> "songlines_cover", img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, tex);
            w = n;
            h = n;
            ready = true;
            status = base + " → OK " + n + "px";
        } catch (Throwable t) {
            Songlines.LOG.warn("cover texture failed: {}", t.toString());
            status = base + " → " + L.t("texture error: ", "ошибка текстуры: ") + brief(t);
        }
    }

    private static String brief(Throwable t) {
        String m = t.getMessage();
        String s = t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
        return s.length() > 70 ? s.substring(0, 70) : s;
    }

    private static int average(int[] px, int size) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        int step = Math.max(1, size / 16);
        for (int y = 0; y < size; y += step) {
            for (int x = 0; x < size; x += step) {
                int c = px[y * size + x];
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
                n++;
            }
        }
        return n == 0 ? 0x202020 : (int) (((r / n) << 16) | ((g / n) << 8) | (b / n));
    }

    /** Преобладающий насыщенный цвет: гистограмма по 4 бита на канал, вес зависит от насыщенности и яркости. */
    private static int accentFrom(int[] px, int size) {
        double[] weight = new double[4096];
        long[] rs = new long[4096], gs = new long[4096], bs = new long[4096], cnt = new long[4096];
        for (int y = 0; y < size; y += 2) {
            for (int x = 0; x < size; x += 2) {
                int c = px[y * size + x];
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
