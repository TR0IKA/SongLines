package dev.songlines;

import java.util.List;

/** Единое состояние "что играет" — из локального плеера или из моста. */
public final class NowPlaying {
    public volatile String title = "", artist = "", sourceName = "";
    public volatile long durationMs;
    public volatile boolean playing, active, favorite;
    public volatile int serial;
    public volatile long changedAtNanos = System.nanoTime();
    public volatile List<Lyrics.Line> lyrics = List.of();
    private volatile long baseMs, baseNanos = System.nanoTime();

    public synchronized void begin(String t, String a, String src) {
        title = t == null ? "" : t;
        artist = a == null ? "" : a;
        sourceName = src == null ? "" : src;
        durationMs = 0;
        baseMs = 0;
        baseNanos = System.nanoTime();
        playing = false;
        active = true;
        lyrics = List.of();
        changedAtNanos = System.nanoTime();
        serial++;
    }

    public synchronized void clear() {
        title = "";
        artist = "";
        sourceName = "";
        durationMs = 0;
        playing = false;
        active = false;
        favorite = false;
        lyrics = List.of();
        serial++;
    }

    public void sync(boolean playing, long posMs, long durMs) {
        this.playing = playing;
        baseMs = posMs;
        baseNanos = System.nanoTime();
        if (durMs > 0) durationMs = durMs;
    }

    public long positionMs() {
        long p = playing ? baseMs + (System.nanoTime() - baseNanos) / 1_000_000L : baseMs;
        long d = durationMs;
        return d > 0 ? Math.min(p, d) : p;
    }
}
