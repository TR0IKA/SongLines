package dev.songlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Все вызовы отрисовки, которые менялись между версиями Minecraft, собраны здесь.
 * Если после обновления что-то не компилируется — чинить нужно только этот файл.
 */
public final class Gfx {
    public static void push(DrawContext c, float x, float y, float scale) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
    }

    public static void pushXY(DrawContext c, float x, float y, float sx, float sy) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sx, sy);
    }

    public static void pop(DrawContext c) {
        c.getMatrices().popMatrix();
    }

    public static void fill(DrawContext c, int x1, int y1, int x2, int y2, int argb) {
        c.fill(x1, y1, x2, y2, argb);
    }

    public static void text(DrawContext c, Text t, int x, int y, int argb, boolean shadow) {
        c.drawText(MinecraftClient.getInstance().textRenderer, t, x, y, argb, shadow);
    }

    public static int width(Text t) {
        return MinecraftClient.getInstance().textRenderer.getWidth(t);
    }

    /** Рисует всю текстуру texW x texH растянутой до size x size, с прозрачностью alpha. */
    public static void tex(DrawContext c, Identifier id, int x, int y, int size, int texW, int texH, float alpha) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, texW, texH, texW, texH,
                argb(0xFFFFFF, alpha));
    }

    /** ARGB с прозрачностью 0..1. Минимум 5/255: у ванильного шрифта альфа < 4 считается непрозрачной. */
    public static int argb(int rgb, float alpha) {
        int a = Math.max(5, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
