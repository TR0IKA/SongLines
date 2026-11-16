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

/** Порядок: файл .lrc рядом с треком, затем кэш, затем LRCLIB (lrclib.net) с несколькими вариантами поиска. */
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

        String synced = fetchLrclib(title, artist == null ? "" : artist, durationMs);
        if (synced == null || synced.isBlank()) return List.of();
        Files.writeString(cache, synced);
        return Lyrics.parse(synced);
    }

    private static String fetchLrclib(String title, String artist, long durationMs) throws Exception {
        String ct = TrackNames.cleanTitle(title), fa = TrackNames.firstArtist(artist);
        String dur = durationMs > 0 ? "&duration=" + (durationMs / 1000) : "";

        // 1) точные запросы: исходное название и очищенное
        String[] exact = {
                "track_name=" + enc(title) + "&artist_name=" + enc(artist),
                "track_name=" + enc(ct) + "&artist_name=" + enc(fa)
        };
        for (String q : exact) {
            String s = synced(Http.get("https://lrclib.net/api/get?" + q + dur, 8000));
            if (s == null && !dur.isEmpty()) s = synced(Http.get("https://lrclib.net/api/get?" + q, 8000));
            if (s != null) return s;
        }

        // 2) поиск: артист+название, раздельные поля, только название
        String[] searches = {
                "q=" + enc((fa + " " + ct).trim()),
                "track_name=" + enc(ct) + "&artist_name=" + enc(fa),
                "q=" + enc(ct)
        };
        for (String q : searches) {
            String s = bestFromSearch(Http.get("https://lrclib.net/api/search?" + q, 8000), ct, fa, durationMs);
            if (s != null) return s;
        }
        return null;
    }

    private static String synced(String body) {
        if (body == null) return null;
        try {
            return syncedOf(JsonParser.parseString(body));
        } catch (Exception e) {
            return null;
        }
    }

    private static String syncedOf(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonElement s = e.getAsJsonObject().get("syncedLyrics");
        if (s == null || s.isJsonNull()) return null;
        String str = s.getAsString();
        return str.isBlank() ? null : str;
    }

    /** Из результатов поиска берём подходящий по названию и исполнителю (и длительности, если известна). */
    private static String bestFromSearch(String body, String ct, String fa, long durationMs) {
        if (body == null) return null;
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) return null;
            JsonArray arr = root.getAsJsonArray();
            String nt = TrackNames.norm(ct), na = TrackNames.norm(fa);
            String fallback = null;
            for (JsonElement it : arr) {
                String s = syncedOf(it);
                if (s == null) continue;
                JsonObject o = it.getAsJsonObject();
                String rt = TrackNames.norm(str(o, "trackName")), ra = TrackNames.norm(str(o, "artistName"));
                boolean titleOk = rt.contains(nt) || nt.contains(rt);
                boolean artistOk = na.isEmpty() || ra.contains(na) || na.contains(ra);
                if (!titleOk || !artistOk) continue;
                if (durationMs > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
                    double d = o.get("duration").getAsDouble();
                    if (Math.abs(d * 1000 - durationMs) <= 6000) return s;
                    if (fallback == null) fallback = s;
                } else {
                    return s;
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
