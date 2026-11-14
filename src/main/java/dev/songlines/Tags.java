package dev.songlines;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Минимальный ридер ID3v2.3/2.4: название, исполнитель, обложка (APIC). Для ogg берём имя файла и cover.jpg рядом. */
public final class Tags {
    public static final class Result {
        public String title, artist;
        public byte[] cover;
    }

    public static Result scan(Path p, boolean wantCover) {
        Result r = new Result();
        try (RandomAccessFile f = new RandomAccessFile(p.toFile(), "r")) {
            if (f.length() < 10) return r;
            byte[] h = new byte[10];
            f.readFully(h);
            if (h[0] != 'I' || h[1] != 'D' || h[2] != '3') return r;
            int ver = h[3] & 0xFF;
            if (ver < 3) return r;
            long end = 10 + syncsafe(h, 6);
            long pos = 10;
            if ((h[5] & 0x40) != 0) {
                byte[] eh = new byte[4];
                f.readFully(eh);
                long sz = ver == 4 ? syncsafe(eh, 0) : be32(eh, 0) + 4;
                pos = 10 + sz;
            }
            while (pos + 10 <= end) {
                f.seek(pos);
                byte[] fh = new byte[10];
                f.readFully(fh);
                if (fh[0] == 0) break;
                String id = new String(fh, 0, 4, StandardCharsets.ISO_8859_1);
                long size = ver == 4 ? syncsafe(fh, 4) : be32(fh, 4);
                if (size <= 0 || pos + 10 + size > end) break;
                boolean text = id.equals("TIT2") || id.equals("TPE1");
                boolean pic = wantCover && id.equals("APIC") && r.cover == null;
                if ((text || pic) && size < 16_000_000L) {
                    byte[] body = new byte[(int) size];
                    f.readFully(body);
                    if (id.equals("TIT2")) r.title = text(body);
                    else if (id.equals("TPE1")) r.artist = text(body);
                    else r.cover = pic(body);
                }
                pos += 10 + size;
            }
        } catch (Exception ignored) {
        }
        return r;
    }

    public static byte[] folderCover(Path track) {
        try {
            Path dir = track.getParent();
            String base = track.getFileName().toString();
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            String[] names = {base, "cover", "folder", "front", "album"};
            String[] exts = {".jpg", ".jpeg", ".png"};
            for (String n : names) {
                for (String e : exts) {
                    Path c = dir.resolve(n + e);
                    if (Files.isRegularFile(c) && Files.size(c) < 20_000_000L) return Files.readAllBytes(c);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static long syncsafe(byte[] b, int o) {
        return ((b[o] & 0x7FL) << 21) | ((b[o + 1] & 0x7FL) << 14) | ((b[o + 2] & 0x7FL) << 7) | (b[o + 3] & 0x7FL);
    }

    private static long be32(byte[] b, int o) {
        return ((b[o] & 0xFFL) << 24) | ((b[o + 1] & 0xFFL) << 16) | ((b[o + 2] & 0xFFL) << 8) | (b[o + 3] & 0xFFL);
    }

    private static Charset cs(int enc) {
        return switch (enc) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
    }

    private static String text(byte[] body) {
        if (body.length < 2) return null;
        String s = new String(body, 1, body.length - 1, cs(body[0] & 0xFF));
        int z = s.indexOf('\0');
        if (z >= 0) s = s.substring(0, z);
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static byte[] pic(byte[] body) {
        if (body.length < 8) return null;
        int enc = body[0] & 0xFF;
        int i = 1;
        while (i < body.length && body[i] != 0) i++; // mime
        i += 2; // нулевой байт + тип картинки
        if (enc == 1 || enc == 2) {
            while (i + 1 < body.length && !(body[i] == 0 && body[i + 1] == 0)) i += 2;
            i += 2;
        } else {
            while (i < body.length && body[i] != 0) i++;
            i += 1;
        }
        if (i >= body.length) return null;
        return Arrays.copyOfRange(body, i, body.length);
    }
}
