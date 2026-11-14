package dev.songlines;

import net.minecraft.client.MinecraftClient;

/** Мини-локализация: L.t("English", "Русский"). */
public final class L {
    public static boolean ru() {
        try {
            return MinecraftClient.getInstance().getLanguageManager().getLanguage().startsWith("ru");
        } catch (Throwable t) {
            return false;
        }
    }

    public static String t(String en, String ru) {
        return ru() ? ru : en;
    }
}
