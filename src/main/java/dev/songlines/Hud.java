package dev.songlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class Hud {
    private static boolean logged;

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        try {
            CoverCard.render(ctx, mc);
            Subtitles.render(ctx, mc);
        } catch (Throwable t) {
            // HUD никогда не должен ронять игру
            if (!logged) {
                logged = true;
                Songlines.LOG.error("HUD render failed", t);
            }
        }
    }
}
