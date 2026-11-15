package dev.songlines;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Свои шрифты: положи .ttf/.otf в songlines/fonts/ и перезапусти игру.
 * Мод сам создаст ресурспак "SonglinesFonts" в папке resourcepacks — включи его в настройках ресурспаков (один раз).
 * Потом шрифт выбирается в настройках мода (вкладка Текст). Недостающие символы берутся из обычного шрифта.
 */
public final class Fonts {
    public static Path fontsDir() {
        return Dirs.root.resolve("fonts");
    }

    private static boolean isFont(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".ttf") || n.endsWith(".otf");
    }

    private static String safeName(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        String stem = dot > 0 ? n.substring(0, dot) : n;
        return stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static List<Path> files() {
        List<Path> out = new ArrayList<>();
        try {
            Files.createDirectories(fontsDir());
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(fontsDir())) {
                for (Path p : ds) if (Files.isRegularFile(p) && isFont(p)) out.add(p);
            }
        } catch (IOException ignored) {
        }
        out.sort((a, b) -> safeName(a).compareTo(safeName(b)));
        return out;
    }

    /** Список для выбора в настройках. */
    public static List<String> list() {
        List<String> l = new ArrayList<>();
        l.add("default");
        l.add("minecraft:uniform");
        for (Path p : files()) l.add("songlines:" + safeName(p));
        return l;
    }

    /** Применяет выбранный шрифт к стилю текста. */
    public static Style apply(Style st) {
        String f = Songlines.cfg.font;
        if (f == null || f.isBlank() || f.equals("default")) return st;
        Identifier id = Identifier.tryParse(f);
        return id == null ? st : st.withFont(new StyleSpriteSource.Font(id));
    }

    /** Создаёт/обновляет ресурспак со шрифтами из songlines/fonts. */
    public static void syncPack() {
        try {
            List<Path> fonts = files();
            if (fonts.isEmpty()) return;
            Path pack = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve("SonglinesFonts");
            Path fontDir = pack.resolve("assets").resolve("songlines").resolve("font");
            Files.createDirectories(fontDir);
            Files.writeString(pack.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":75,\"description\":\"Songlines fonts\"}}");
            for (Path p : fonts) {
                String name = safeName(p);
                String ext = p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".otf") ? "otf" : "ttf";
                Files.copy(p, fontDir.resolve(name + "." + ext), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(fontDir.resolve(name + ".json"),
                        "{\"providers\":["
                                + "{\"type\":\"ttf\",\"file\":\"songlines:" + name + "." + ext + "\",\"shift\":[0,0],\"size\":11.0,\"oversample\":8.0},"
                                + "{\"type\":\"reference\",\"id\":\"minecraft:default\"}"
                                + "]}");
            }
        } catch (IOException e) {
            Songlines.LOG.warn("font pack failed", e);
        }
    }
}
