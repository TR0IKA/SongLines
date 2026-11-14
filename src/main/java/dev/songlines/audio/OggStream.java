package dev.songlines.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** ogg через stb_vorbis из LWJGL — он уже входит в сам Minecraft, поэтому лишних нативных библиотек нет. */
final class OggStream implements PcmStream {
    private final ByteBuffer data; // держим ссылку, пока открыт декодер
    private final long handle;
    private final int channels, rate;
    private final long durationMs;
    private final ShortBuffer tmp;

    OggStream(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        data = BufferUtils.createByteBuffer(b.length);
        data.put(b).flip();
        IntBuffer err = BufferUtils.createIntBuffer(1);
        handle = STBVorbis.stb_vorbis_open_memory(data, err, null);
        if (handle == 0L) throw new IOException("ogg open failed, code " + err.get(0));
        try (MemoryStack st = MemoryStack.stackPush()) {
            STBVorbisInfo info = STBVorbisInfo.malloc(st);
            STBVorbis.stb_vorbis_get_info(handle, info);
            channels = info.channels();
            rate = info.sample_rate();
        }
        durationMs = (long) (STBVorbis.stb_vorbis_stream_length_in_seconds(handle) * 1000.0);
        tmp = BufferUtils.createShortBuffer(8192 * Math.max(1, channels));
    }

    @Override public int channels() { return channels; }
    @Override public int rate() { return rate; }
    @Override public long durationMs() { return durationMs; }

    @Override
    public int read(short[] dst) {
        int filled = 0;
        while (filled < dst.length) {
            tmp.clear();
            int want = Math.min(tmp.capacity(), dst.length - filled);
            want -= want % channels;
            if (want <= 0) break;
            tmp.limit(want);
            int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, tmp);
            if (frames <= 0) break;
            int n = frames * channels;
            tmp.position(0);
            tmp.limit(n);
            tmp.get(dst, filled, n);
            filled += n;
        }
        return filled;
    }

    @Override
    public void close() {
        STBVorbis.stb_vorbis_close(handle);
    }
}
