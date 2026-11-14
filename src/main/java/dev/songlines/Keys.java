package dev.songlines;

import dev.songlines.ui.MainScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Клавиши можно переназначить в стандартном меню управления (категория Songlines). */
public final class Keys {
    private static KeyBinding menu, prev, next, pause, fav, subs;

    public static void register() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(Songlines.ID, "main"));
        menu = reg("menu", GLFW.GLFW_KEY_M, cat);
        prev = reg("prev", GLFW.GLFW_KEY_LEFT_BRACKET, cat);
        next = reg("next", GLFW.GLFW_KEY_RIGHT_BRACKET, cat);
        pause = reg("pause", GLFW.GLFW_KEY_BACKSLASH, cat);
        fav = reg("fav", GLFW.GLFW_KEY_APOSTROPHE, cat);
        subs = reg("subs", GLFW.GLFW_KEY_SEMICOLON, cat);
    }

    private static KeyBinding reg(String name, int key, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.songlines." + name, InputUtil.Type.KEYSYM, key, cat));
    }

    public static void tick(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        Playback p = Songlines.playback;
        while (menu.wasPressed()) mc.setScreen(new MainScreen());
        while (prev.wasPressed()) p.prev();
        while (next.wasPressed()) p.next();
        while (pause.wasPressed()) p.togglePause();
        while (fav.wasPressed()) p.toggleFavorite();
        while (subs.wasPressed()) {
            Songlines.cfg.subMode = Config.next(Songlines.cfg.subMode);
            Playback.say(L.t("Subtitles: ", "Субтитры: ") + Songlines.cfg.subMode);
        }
    }
}
