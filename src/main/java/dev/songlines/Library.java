package dev.songlines;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Папки с треками: songlines/music/<папка>/*.mp3|ogg. Файлы прямо в music/ попадают в "Unsorted". */
public final class Library {
    public static final class Folder {
        public final String name;
        public final boolean virtual;
        public final List<Track> tracks = new ArrayList<>();

        Folder(String name, boolean virtual) {
            this.name = name;
            this.virtual = virtual;
        }
    }

    private volatile List<Folder> folders = List.of();

    public List<Folder> folders() {
        return folders;
    }

    public Folder favorites(Favorites fav) {
        Folder f = new Folder("★ " + L.t("Favorites", "Избранное"), true);
        for (Folder d : folders) for (Track t : d.tracks) if (fav.has(t)) f.tracks.add(t);
        return f;
    }

    public static boolean isAudio(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".ogg");
    }

    public void rescan() {
        List<Folder> out = new ArrayList<>();
        Folder root = new Folder("Unsorted", false);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Dirs.music)) {
            List<Path> list = new ArrayList<>();
            ds.forEach(list::add);
            list.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
            for (Path p : list) {
                if (Files.isDirectory(p)) {
                    Folder f = new Folder(p.getFileName().toString(), false);
                    addFiles(f, p);
                    out.add(f);
                } else if (isAudio(p)) {
                    root.tracks.add(makeTrack(p, root.name));
                }
            }
        } catch (IOException e) {
            Songlines.LOG.warn("scan failed", e);
        }
        if (!root.tracks.isEmpty()) out.add(0, root);
        folders = out;
    }

    private void addFiles(Folder f, Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) if (Files.isRegularFile(p) && isAudio(p)) files.add(p);
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        for (Path p : files) f.tracks.add(makeTrack(p, f.name));
    }

    private Track makeTrack(Path p, String folder) {
        Tags.Result r = Tags.scan(p, false);
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String title = r.title, artist = r.artist;
        if (title == null || title.isBlank()) {
            int i = base.indexOf(" - ");
            if (i > 0 && artist == null) {
                artist = base.substring(0, i).trim();
                title = base.substring(i + 3).trim();
            } else {
                title = base;
            }
        }
        return new Track(p, folder, title, artist == null ? "" : artist);
    }

    private static String sanitize(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.isEmpty() || s.equals(".") || s.equals("..")) return null;
        return s;
    }

    public boolean createFolder(String raw) {
        String s = sanitize(raw);
        if (s == null) return false;
        try {
            Files.createDirectories(Dirs.music.resolve(s));
            rescan();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Копирует файлы в папку (null = корень music/). Возвращает число скопированных. */
    public int importFiles(List<Path> src, String folder) {
        Path dest = Dirs.music;
        String s = folder == null ? null : sanitize(folder);
        if (s != null && !s.equals("Unsorted")) dest = Dirs.music.resolve(s);
        int n = 0;
        try {
            Files.createDirectories(dest);
            for (Path p : src) {
                if (Files.isRegularFile(p) && isAudio(p)) {
                    Files.copy(p, dest.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                    n++;
                }
            }
        } catch (IOException e) {
            Songlines.LOG.warn("import failed", e);
        }
        rescan();
        return n;
    }
}
