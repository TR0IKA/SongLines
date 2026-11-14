package dev.songlines;

import java.util.ArrayList;
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
}
