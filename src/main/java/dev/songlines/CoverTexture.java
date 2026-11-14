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

            tex = new NativeImageBackedTexture(() -> "songlines_cover", dst);
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, tex);
            w = target;
            h = target;
            ready = true;
        } catch (Exception e) {
            Songlines.LOG.warn("cover decode failed: {}", e.toString());
        }
    }
}
