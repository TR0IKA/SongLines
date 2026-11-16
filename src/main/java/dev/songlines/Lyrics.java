package dev.songlines;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Синхронные тексты в формате LRC. */
public final class Lyrics {
    public record Line(long ms, String text) {}

    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    public static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null) return out;
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long frac = 0;
                String f = m.group(3);
                if (f != null) {
                    frac = Long.parseLong(f);
                    if (f.length() == 1) frac *= 100;
                    else if (f.length() == 2) frac *= 10;
                }
                times.add(min * 60_000L + sec * 1000L + frac);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::ms));
        return out;
    }

    /** Индекс последней строки со временем <= ms, либо -1. */
    public static int indexAt(List<Line> lines, long ms) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).ms() <= ms) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    /**
     * Разбивает строки на группы по n слов. Слов-таймингов в LRC нет, поэтому время группам
     * распределяется пропорционально длине (в пределах разумной длительности строки).
     */
    public static List<Line> chunk(List<Line> lines, int n) {
        if (n <= 0) return lines;
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line ln = lines.get(i);
            String text = ln.text().trim();
            if (text.isEmpty()) {
                out.add(ln);
                continue;
            }
            String[] words = text.split("\\s+");
            if (words.length <= n) {
                out.add(new Line(ln.ms(), text));
                continue;
            }
            long next = i + 1 < lines.size() ? lines.get(i + 1).ms() : ln.ms() + 6000;
            long avail = Math.max(300, next - ln.ms());
            long dur = Math.min(avail, Math.max(1200, text.length() * 80L));
            List<String> groups = new ArrayList<>();
            for (int w = 0; w < words.length; w += n) {
                groups.add(String.join(" ", Arrays.copyOfRange(words, w, Math.min(words.length, w + n))));
            }
            int total = 0;
            for (String g : groups) total += g.length() + 1;
            int used = 0;
            for (String g : groups) {
                out.add(new Line(ln.ms() + dur * used / total, g));
                used += g.length() + 1;
            }
        }
        return out;
    }
}
