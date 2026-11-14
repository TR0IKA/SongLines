package dev.songlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Карточка в углу экрана: обложка, название, исполнитель, время и полоса прогресса. */
public final class CoverCard {
    public static void render(DrawContext ctx, MinecraftClient mc) {
        Config c = Songlines.cfg;
        NowPlaying np = Songlines.playback.np;
        if (c.cardMode == Config.CardMode.OFF || !np.active) return;

        float alpha = 1f;
        if (c.cardMode == Config.CardMode.ON_CHANGE) {
            float t = (System.nanoTime() - np.changedAtNanos) / 1e9f;
            if (t > c.cardSeconds) return;
            alpha = Math.max(0.02f, Math.min(1f, Math.min(t / 0.3f, (c.cardSeconds - t) / 0.5f)));
        }

        TextRenderer tr = mc.textRenderer;
        int pad = 4, size = c.coverSize, textW = c.cardStyle == Config.CardStyle.CARD ? 110 : 90;
        boolean coverOnly = c.cardStyle == Config.CardStyle.COVER_ONLY;
        int w = coverOnly ? size : pad + size + pad + textW + pad;
        int h = coverOnly ? size : size + 2 * pad;

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight(), m = c.margin;
        boolean right = c.corner == Config.Corner.TOP_RIGHT || c.corner == Config.Corner.BOTTOM_RIGHT;
        boolean bottom = c.corner == Config.Corner.BOTTOM_LEFT || c.corner == Config.Corner.BOTTOM_RIGHT;
        int x = right ? sw - m - w : m;
        int y = bottom ? sh - m - h : m;

        if (!coverOnly) {
            int rgb = switch (c.cardColor) {
                case AUTO -> darken(CoverTexture.avgColor, 0.35f);
                case DARK -> 0x141414;
                case CUSTOM -> Config.rgb(c.cardCustomColor, 0x1E1E2E);
            };
            int bg = Gfx.argb(rgb, alpha * c.cardOpacity / 100f);
            Gfx.fill(ctx, x + 1, y, x + w - 1, y + h, bg); // скруглённые углы в 1 пиксель
            Gfx.fill(ctx, x, y + 1, x + w, y + h - 1, bg);
            if (c.showBorder) {
                int bc = Gfx.argb(Config.rgb(c.borderColor, 0xFFFFFF), alpha);
                Gfx.fill(ctx, x + 1, y, x + w - 1, y + 1, bc);
                Gfx.fill(ctx, x + 1, y + h - 1, x + w - 1, y + h, bc);
                Gfx.fill(ctx, x, y + 1, x + 1, y + h - 1, bc);
                Gfx.fill(ctx, x + w - 1, y + 1, x + w, y + h - 1, bc);
            }
        }

        int cx = coverOnly ? x : x + pad, cy = coverOnly ? y : y + pad;
        if (CoverTexture.ready) {
            Gfx.tex(ctx, CoverTexture.ID, cx, cy, size, CoverTexture.w, CoverTexture.h, alpha);
        } else {
            Gfx.fill(ctx, cx, cy, cx + size, cy + size, Gfx.argb(0x555555, alpha));
            Gfx.text(ctx, Text.literal("♪"), cx + size / 2 - 3, cy + size / 2 - 4, Gfx.argb(0xFFFFFF, alpha), false);
        }
        if (coverOnly) return;

        int tx = x + pad + size + pad;
        int titleW = textW - (np.favorite ? 10 : 0);
        String title = tr.trimToWidth(np.title, titleW);
        String artist = tr.trimToWidth(np.artist, textW);
        Gfx.text(ctx, Text.literal(title).formatted(Formatting.BOLD), tx, y + pad + 1, Gfx.argb(0xFFFFFF, alpha), false);
        if (np.favorite) Gfx.text(ctx, Text.literal("★"), tx + textW - 8, y + pad + 1, Gfx.argb(0xFFD166, alpha), false);
        Gfx.text(ctx, Text.literal(artist), tx, y + pad + 12, Gfx.argb(0xB8B8B8, alpha), false);

        if (c.cardStyle == Config.CardStyle.CARD && c.showProgress && np.durationMs > 0) {
            long pos = np.positionMs(), dur = np.durationMs;
            int barY = y + h - pad - 2;
            Gfx.fill(ctx, tx, barY, tx + textW, barY + 2, Gfx.argb(0xFFFFFF, alpha * 0.25f));
            int filled = (int) (textW * Math.min(1f, pos / (float) dur));
            Gfx.fill(ctx, tx, barY, tx + filled, barY + 2, Gfx.argb(Config.rgb(c.activeColor, 0xFFD166), alpha));
            if (size >= 36) {
                Gfx.text(ctx, Text.literal(fmt(pos) + " / " + fmt(dur)), tx, barY - 11, Gfx.argb(0x9A9A9A, alpha), false);
            }
        }
    }

    private static int darken(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    public static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
