package dev.songlines.audio;

import dev.songlines.Songlines;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Потоковое воспроизведение через OpenAL (тот же, что использует Minecraft) в отдельном потоке.
 * Никаких javax.sound — его нет на Android.
 */
public final class AudioPlayer {
    private static final int BUFFERS = 4;
    private static final int CHUNK = 16384; // short на буфер

    private final BlockingQueue<Runnable> cmds = new LinkedBlockingQueue<>();
    private Thread thread;
    private volatile boolean running = true;

    // читаются из основного потока
    public volatile float volume = 1f;
    public volatile long positionMs, durationMs;
    public volatile boolean playing, active;
    public volatile Runnable onEnded;
    public volatile Consumer<String> onError;

    // состояние принадлежит аудио-потоку
    private long ctx = 0;
    private int src = -1;
    private final int[] bufs = new int[BUFFERS];
    private PcmStream stream;
    private boolean paused, eof;
    private int format, rate, channels;
    private long playedFrames;
    private final Map<Integer, Integer> bufFrames = new HashMap<>();
    private final short[] chunk = new short[CHUNK];
    private final ShortBuffer sbuf = BufferUtils.createShortBuffer(CHUNK);

    private synchronized void start() {
        if (thread != null) return;
        thread = new Thread(this::loop, "songlines-audio");
        thread.setDaemon(true);
        thread.start();
    }

    public void play(Path p) {
        start();
        positionMs = 0;
        durationMs = 0;
        playing = true;
        active = true;
        cmds.add(() -> open(p));
    }

    public void togglePause() {
        cmds.add(() -> {
            if (stream == null) return;
            paused = !paused;
            if (paused) AL10.alSourcePause(src);
            else AL10.alSourcePlay(src);
            playing = !paused;
        });
    }

    public void stop() {
        playing = false;
        active = false;
        positionMs = 0;
        cmds.add(this::closeStream);
    }

    public void shutdown() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    // ---------- аудио-поток ----------

    private void loop() {
        while (running) {
            try {
                Runnable c;
                while ((c = cmds.poll()) != null) {
                    try {
                        c.run();
                    } catch (Throwable t) {
                        fail(t);
                    }
                }
                if (stream != null) {
                    try {
                        pump();
                    } catch (Throwable t) {
                        fail(t);
                    }
                }
                Thread.sleep(15);
            } catch (InterruptedException e) {
                break;
            }
        }
        try {
            closeStream();
            if (src != -1) AL10.alDeleteSources(src);
        } catch (Throwable ignored) {
        }
    }

    private void fail(Throwable t) {
        Songlines.LOG.warn("audio error", t);
        try {
            closeStream();
        } catch (Throwable ignored) {
        }
        playing = false;
        active = false;
        Consumer<String> e = onError;
        if (e != null) e.accept(String.valueOf(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
    }

    private void ensureAl() {
        long cur = ALC10.alcGetCurrentContext();
        if (cur == 0L) throw new IllegalStateException("OpenAL is not ready (sound disabled?)");
        if (cur == ctx && src != -1) return;
        ctx = cur;
        src = AL10.alGenSources();
        for (int i = 0; i < BUFFERS; i++) bufs[i] = AL10.alGenBuffers();
        AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 0f);
        AL10.alSource3f(src, AL10.AL_POSITION, 0f, 0f, 0f);
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            src = -1;
            throw new IllegalStateException("OpenAL error " + err);
        }
    }

    private void open(Path p) {
        closeStream();
        ensureAl();
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        PcmStream s;
        try {
            s = name.endsWith(".ogg") ? new OggStream(p) : new Mp3Stream(p);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open " + p.getFileName() + ": " + e.getMessage(), e);
        }
        if (s.channels() < 1 || s.channels() > 2) {
            s.close();
            throw new IllegalStateException("Unsupported channel count " + s.channels());
        }
        stream = s;
        channels = s.channels();
        rate = s.rate();
        format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        durationMs = s.durationMs();
        playedFrames = 0;
        eof = false;
        paused = false;
        bufFrames.clear();
        for (int i = 0; i < BUFFERS; i++) {
            if (fill(bufs[i])) AL10.alSourceQueueBuffers(src, bufs[i]);
        }
        AL10.alSourcef(src, AL10.AL_GAIN, volume);
        AL10.alSourcePlay(src);
        playing = true;
        active = true;
    }

    private boolean fill(int buf) {
        int n;
        try {
            n = stream.read(chunk);
        } catch (Exception e) {
            n = 0;
        }
        if (n <= 0) {
            eof = true;
            return false;
        }
        sbuf.clear();
        sbuf.put(chunk, 0, n);
        sbuf.flip();
        AL10.alBufferData(buf, format, sbuf, rate);
        bufFrames.put(buf, n / channels);
        return true;
    }

    private void pump() {
        AL10.alSourcef(src, AL10.AL_GAIN, volume);
        if (paused) return;
        int processed = AL10.alGetSourcei(src, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buf = AL10.alSourceUnqueueBuffers(src);
            playedFrames += bufFrames.getOrDefault(buf, 0);
            if (!eof && fill(buf)) AL10.alSourceQueueBuffers(src, buf);
        }
        int queued = AL10.alGetSourcei(src, AL10.AL_BUFFERS_QUEUED);
        int state = AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE);
        if (queued == 0 && eof) {
            finish();
            return;
        }
        if (state != AL10.AL_PLAYING && queued > 0) AL10.alSourcePlay(src); // буфер опустел — продолжаем
        positionMs = (playedFrames + AL10.alGetSourcei(src, AL10.AL_SAMPLE_OFFSET)) * 1000L / Math.max(1, rate);
    }

    private void finish() {
        long d = durationMs;
        closeStream();
        positionMs = d;
        playing = false;
        Runnable r = onEnded;
        if (r != null) r.run();
    }

    private void closeStream() {
        if (stream != null) {
            stream.close();
            stream = null;
        }
        if (src != -1 && ALC10.alcGetCurrentContext() == ctx) {
            AL10.alSourceStop(src);
            int q = AL10.alGetSourcei(src, AL10.AL_BUFFERS_QUEUED);
            while (q-- > 0) AL10.alSourceUnqueueBuffers(src);
        }
        playing = false;
    }
}
