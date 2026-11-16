package dev.songlines;

/** Чистка названий для поиска текстов и обложек. */
public final class TrackNames {
    public static String cleanTitle(String t) {
        if (t == null) return "";
        String s = t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "");
        s = s.replaceAll("(?i)\\s*[-–—]\\s*(remaster(ed)?|live|version|edit|sped up|slowed|mono|stereo|bonus).*$", "");
        s = s.trim();
        return s.isEmpty() ? t.trim() : s;
    }

    public static String firstArtist(String a) {
        if (a == null) return "";
        String[] p = a.split("(?i)\\s*(,|&|;|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sx\\s|\\swith\\s)\\s*");
        return p.length == 0 ? a.trim() : p[0].trim();
    }

    /** Строка для сравнения: только буквы и цифры, нижний регистр. */
    public static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
