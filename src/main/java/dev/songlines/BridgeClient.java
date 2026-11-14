package dev.songlines;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Клиент моста: маленькая программа на этом же устройстве (APK на Android, .exe на Windows)
 * отдаёт локальный HTTP-сервер на 127.0.0.1:<port>.
 *   GET  /state      -> {"playing":true,"title":"","artist":"","album":"","positionMs":0,"durationMs":0,"source":"","coverId":""}
 *   GET  /cover      -> байты картинки (jpg/png) текущего трека
 *   POST /cmd/next | /cmd/prev | /cmd/playpause
 */
public final class BridgeClient {
    private final NowPlaying np;
    private volatile boolean running;
    private Thread thread;
    public volatile boolean connected;
    private String lastKey = "", lastCover = "";

    public BridgeClient(NowPlaying np) {
        this.np = np;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        lastKey = "";
        lastCover = "";
        thread = new Thread(this::loop, "songlines-bridge");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        connected = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private String base() {
        return "http://127.0.0.1:" + Songlines.cfg.bridgePort;
    }

    private void loop() {
        while (running) {
            try {
                poll();
            } catch (Exception e) {
                if (connected) {
                    connected = false;
                    lastKey = "";
                    np.clear();
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void poll() throws Exception {
        String body = Http.get(base() + "/state", 800);
        if (body == null) throw new IllegalStateException("no state");
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        connected = true;
        String title = str(o, "title"), artist = str(o, "artist");
        if (title.isEmpty() && artist.isEmpty()) {
            if (np.active) {
                lastKey = "";
                np.clear();
            }
            return;
        }
        String key = title + "\u0000" + artist;
        if (!key.equals(lastKey)) {
            lastKey = key;
            np.begin(title, artist, str(o, "source"));
        }
        np.sync(bool(o, "playing"), lng(o, "positionMs"), lng(o, "durationMs"));
        String cid = str(o, "coverId");
        if (!cid.equals(lastCover)) {
            lastCover = cid;
            byte[] img = Http.getBytes(base() + "/cover", 3000);
            CoverTexture.set(img);
        }
    }

    public void command(String cmd) {
        Thread t = new Thread(() -> {
            try {
                Http.post(base() + "/cmd/" + cmd, 1500);
            } catch (Exception ignored) {
            }
        }, "songlines-bridge-cmd");
        t.setDaemon(true);
        t.start();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    private static long lng(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : e.getAsLong();
    }
}
