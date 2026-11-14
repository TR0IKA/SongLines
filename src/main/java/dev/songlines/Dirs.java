package dev.songlines;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Dirs {
    public static Path root, music, lyrics, config;

    public static void init() {
        root = FabricLoader.getInstance().getGameDir().resolve("songlines");
        music = root.resolve("music");
        lyrics = root.resolve("lyrics");
        config = root.resolve("config.json");
        try {
            Files.createDirectories(music);
            Files.createDirectories(lyrics);
        } catch (IOException e) {
            Songlines.LOG.warn("cannot create dirs", e);
        }
    }
}
