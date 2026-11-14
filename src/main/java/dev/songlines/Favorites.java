package dev.songlines;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Favorites {
    private final Set<String> ids = new LinkedHashSet<>();
    private final Path file = Dirs.root.resolve("favorites.json");
    private static final Gson GSON = new Gson();

    public Favorites() {
        try {
            if (Files.exists(file)) {
                String[] a = GSON.fromJson(Files.readString(file), String[].class);
                if (a != null) ids.addAll(java.util.Arrays.asList(a));
            }
        } catch (Exception e) {
            Songlines.LOG.warn("favorites load failed", e);
        }
    }

    public synchronized boolean has(Track t) {
        return ids.contains(t.id());
    }

    /** @return true если теперь в избранном */
    public synchronized boolean toggle(Track t) {
        boolean now = !ids.remove(t.id());
        if (now) ids.add(t.id());
        try {
            Files.writeString(file, GSON.toJson(ids));
        } catch (Exception e) {
            Songlines.LOG.warn("favorites save failed", e);
        }
        return now;
  
    }
}
