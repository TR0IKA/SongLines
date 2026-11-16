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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Обложка по названию и исполнителю: сначала iTunes Search, потом Deezer (оба без ключей). Результат кэшируется в songlines/covers. */
public final class CoverFetcher {
    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "songlines-covers");
        t.setDaemon(true);
        return t;
    });

    public static void fetch(String title, String artist, Consumer<byte[]> cb) {
        EX.execute(() -> {
            byte[] r = null;
            try {
                r = load(title, artist == null ? "" : artist);
            } catch (Throwable t) {
                Songlines.LOG.debug("cover fetch failed", t);
            }
            cb.accept(r);
        });
    }

    private static byte[] load(String title, String artist) throws Exception {
        if (title == null || title.isBlank()) return null;
        Path cache = Dirs.covers.resolve(sha1(title + "\u0000" + artist) + ".jpg");
        if (Files.isRegularFile(cache)) return Files.readAllBytes(cache);

        String url = itunes(title, artist);
        if (url == null) url = deezer(title, artist);
        if (url == null) return null;
        byte[] b = Http.getBytes(url, 8000);
        if (b != null && b.length > 500) Files.write(cache, b);
        return b;
    }

    private static String itunes(String title, String artist) throws Exception {
        String ct = TrackNames.cleanTitle(title), fa = TrackNames.firstArtist(artist);
        String body = Http.get("https://itunes.apple.com/search?media=music&entity=song&limit=8&term="
                + enc((fa + " " + ct).trim()), 8000);
        if (body == null) return null;
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("results")) return null;
        JsonArray arr = root.getAsJsonObject().getAsJsonArray("results");
        String na = TrackNames.norm(fa), first = null;
        for (JsonElement it : arr) {
            JsonObject o = it.getAsJsonObject();
            if (!o.has("artworkUrl100")) continue;
            String art = o.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb");
            if (first == null) first = art;
            String ra = TrackNames.norm(o.has("artistName") ? o.get("artistName").getAsString() : "");
            if (na.isEmpty() || ra.contains(na) || na.contains(ra)) return art;
        }
        return na.isEmpty() ? first : null;
    }

    private static String deezer(String title, String artist) throws Exception {
        String ct = TrackNames.cleanTitle(title), fa = TrackNames.firstArtist(artist);
        String[] queries = {
                "artist:\"" + fa + "\" track:\"" + ct + "\"",
                (fa + " " + ct).trim()
        };
        for (String q : queries) {
            String body = Http.get("https://api.deezer.com/search?limit=3&q=" + enc(q), 8000);
            if (body == null) continue;
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("data")) continue;
            for (JsonElement it : root.getAsJsonObject().getAsJsonArray("data")) {
                JsonObject o = it.getAsJsonObject();
                if (o.has("album") && o.getAsJsonObject("album").has("cover_big")) {
                    return o.getAsJsonObject("album").get("cover_big").getAsString();
                }
            }
        }
        return null;
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
