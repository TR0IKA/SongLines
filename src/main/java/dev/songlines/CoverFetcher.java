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

    /** status получает ход поиска («iTunes: найдено», ошибки сети и т.п.) — он показывается в меню M. */
    public static void fetch(String title, String artist, Consumer<String> status, Consumer<byte[]> cb) {
        EX.execute(() -> {
            byte[] r = null;
            try {
                r = load(title, artist == null ? "" : artist, status);
            } catch (Throwable t) {
                Songlines.LOG.debug("cover fetch failed", t);
                status.accept(L.t("online error: ", "ошибка сети: ") + brief(t));
            }
            cb.accept(r);
        });
    }

    private static byte[] load(String title, String artist, Consumer<String> status) throws Exception {
        if (title == null || title.isBlank()) {
            status.accept(L.t("no title to search", "нет названия для поиска"));
            return null;
        }
        Path cache = Dirs.covers.resolve(sha1(title + "\u0000" + artist) + ".jpg");
        if (Files.isRegularFile(cache)) {
            byte[] c = Files.readAllBytes(cache);
            status.accept(L.t("from cache, ", "из кэша, ") + (c.length / 1024) + " KB");
            return c;
        }

        status.accept(L.t("online: searching…", "сеть: ищу…"));
        String url = null, via = "", err = "";
        try {
            url = itunes(title, artist);
            if (url != null) via = "iTunes";
        } catch (Throwable t) {
            err += " iTunes: " + brief(t);
        }
        if (url == null) {
            try {
                url = deezer(title, artist);
                if (url != null) via = "Deezer";
            } catch (Throwable t) {
                err += " Deezer: " + brief(t);
            }
        }
        if (url == null) {
            status.accept(L.t("online: not found", "сеть: не найдено") + err);
            return null;
        }
        byte[] b = null;
        try {
            b = Http.getBytes(url, 8000);
        } catch (Throwable t) {
            err += " " + brief(t);
        }
        if (b == null || b.length < 500) {
            status.accept(L.t("online (", "сеть (") + via + L.t("): image download failed", "): картинка не скачалась") + err);
            return null;
        }
        Files.write(cache, b);
        status.accept(L.t("online (", "сеть (") + via + "), " + (b.length / 1024) + " KB");
        return b;
    }

    private static String brief(Throwable t) {
        String m = t.getMessage();
        String s = t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
        return s.length() > 70 ? s.substring(0, 70) : s;
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
