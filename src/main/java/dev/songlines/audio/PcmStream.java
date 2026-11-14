package dev.songlines.audio;

import java.io.IOException;

/** Источник PCM 16 бит (interleaved). */
interface PcmStream extends AutoCloseable {
    int channels();

    int rate();

    long durationMs();

    /** Заполняет dst как можно полнее. Возвращает число short или 0 в конце файла. */
    int read(short[] dst) throws IOException;

    @Override
    void clos
  e();
}
