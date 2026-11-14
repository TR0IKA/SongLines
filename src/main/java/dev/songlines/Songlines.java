package dev.songlines;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Клиентская точка входа. Мод только клиентский. */
public class Songlines implements ClientModInitializer {
    public static final String ID = "songlines";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    public static Config cfg;
    public static Playback playback;

    @Override
    public void onInitializeClient() {
        Dirs.init();
        cfg = Config.load();
        playback = new Playback();
        Keys.register();
        HudElementRegistry.addLast(Identifier.of(ID, "overlay"), Hud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                playback.tick(client);
                Keys.tick(client);
                Subtitles.tick(client);
            } catch (Throwable t) {
                LOG.warn("tick error", t);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            playback.shutdown();
            cfg.save();
        });
    }
}
