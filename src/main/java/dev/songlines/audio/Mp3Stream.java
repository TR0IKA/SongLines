package dev.songlines.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** mp3 через JLayer — чистая Java, без нативных библиотек. */
final class Mp3Stream implements PcmStream {
    private final Bitstream bs;
    private final Decoder dec = new Decoder();
    private short[] carry;
    private int carryPos;
    private final int channels, rate;
    private final long durationMs;

    Mp3Stream(Path p) throws IOException {
        InputStream in = new BufferedInputStream(Files.newInputStream(p), 1 << 16);
        bs = new Bitstream(in);
        try {
            Header h = bs.readFrame();
            if (h == null) throw new IOException("empty mp3");
            SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
            channels = sb.getChannelCount();
            rate = sb.getSampleFrequency();
            durationMs = (long) h.total_ms((int) Math.min(Integer.MAX_VALUE, Files.size(p)));
            carry = Arrays.copyOf(sb.getBuffer(), sb.getBufferLength());
            bs.closeFrame();
        } catch (JavaLayerException e) {
            throw new IOException(e);
        }
    }

    @Override public int channels() { return channels; }
    @Override public int rate() { return rate; }
    @Override public long durationMs() { return durationMs; }

    @Override
    public int read(short[] dst) throws IOException {
        int filled = 0;
        try {
            while (filled < dst.length) {
                if (carryPos >= carry.length) {
                    Header h = bs.readFrame();
                    if (h == null) break;
                    SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                    carry = Arrays.copyOf(sb.getBuffer(), sb.getBufferLength());
                    carryPos = 0;
                    bs.closeFrame();
                }
                int n = Math.min(dst.length - filled, carry.length - carryPos);
                System.arraycopy(carry, carryPos, dst, filled, n);
                carryPos += n;
                filled += n;
            }
        } catch (JavaLayerException e) {
            throw new IOException(e);
        }
        return filled;
    }

    @Override
    public void close() {
        try {
            bs.close();
        } catch (Exception ignored) {
        }
    }
}
