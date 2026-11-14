package dev.songlines;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Порядок: файл .lrc рядом с треком → кэш → LRCLIB (https://lrclib.net). */
public final class LyricsFetcher {
    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "songlines-lyrics");
        t.setDaemon(true);
        return t;
    });

    public static void request(String title, String artist, long durationMs, Path trackFile,
                               boolean allowNet, Consumer<List<Lyrics.Line>> cb) {
        EX.execute(() -> {
            List<Lyrics.Line> result = List.of();
            try {
                result = load(title, artist, durationMs, trackFile, allowNet);
            } catch (Throwable t) {
                Songlines.LOG.debug("lyrics failed", t);
            }
            cb.accept(result);
        });
    }

    private static List<Lyrics.Line> load(String title, String artist, long durationMs, Path track, boolean net) throws Exception {
        if (track != null) {
            String n = track.getFileName().toString();
            int dot = n.lastIndexOf('.');
            Path lrc = track.resolveSibling((dot > 0 ? n.substring(0, dot) : n) + ".lrc");
            if (Files.isRegularFile(lrc)) {
                List<Lyrics.Line> l = Lyrics.parse(Files.readString(lrc));
                if (!l.isEmpty()) return l;
            }
        }
        if (title == null || title.isBlank()) return List.of();
        Path cache = Dirs.lyrics.resolve(sha1(title + "\u0000" + artist) + ".lrc");
        if (Files.isRegularFile(cache)) return Lyrics.parse(Files.readString(cache));
        if (!net) return List.of();

        String synced = fetchLrclib(title, artist, durationMs);
        if (synced == null || synced.isBlank()) return List.of();
        Files.writeString(cache, synced);
        return Lyrics.parse(synced);
    }

    private static String fetchLrclib(String title, String artist, long durationMs) throws Exception {
        String q = "track_name=" + enc(title) + "&artist_name=" + enc(artist == null ? "" : artist);
        String get = "https://lrclib.net/api/get?" + q + (durationMs > 0 ? "&duration=" + (durationMs / 1000) : "");
        String body = Http.get(get, 8000);
        if (body != null) {
            String s = synced(JsonParser.parseString(body));
            if (s != null) return s;
        }
        body = Http.get("https://lrclib.net/api/search?" + q, 8000);
        if (body != null) {
            JsonElement e = JsonParser.parseString(body);
            if (e.isJsonArray()) {
                JsonArray arr = e.getAsJsonArray();
                for (JsonElement it : arr) {
                    String s = synced(it);
                    if (s != null) return s;
                }
            }
        }
        return null;
    }

    private static String synced(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        JsonElement s = o.get("syncedLyrics");
        if (s == null || s.isJsonNull()) return null;
        String str = s.getAsString();
        return str.isBlank() ? null : str;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
