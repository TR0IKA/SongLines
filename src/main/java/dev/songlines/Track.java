package dev.songlines;

import java.nio.file.Path;

public final class Track {
    public final Path path;
    public final String folder, title, artist;

    public Track(Path path, String folder, String title, String artist) {
        this.path = path;
        this.folder = folder;
        this.title = title;
        this.artist = artist;
    }

    public String id() {
        return folder + "/" + path.getFileName();
    }
}
