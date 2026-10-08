package com.batclan.montaje;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure timeline rules shared by the UI and export. Times are milliseconds. */
public final class MontageLogic {
    private MontageLogic() {}

    public static final long MERGE_GAP_MS = 350;

    public static final class Event {
        public long timeMs;
        public long endMs;
        public String type;
        public String customName;
        public long beforeMs;
        public long afterMs;

        public Event(long timeMs, String type, long beforeMs, long afterMs) {
            this(timeMs, timeMs, type, "", beforeMs, afterMs);
        }

        public Event(long timeMs, long endMs, String type, String customName, long beforeMs, long afterMs) {
            this.timeMs = timeMs;
            this.endMs = endMs;
            this.type = type;
            this.customName = customName;
            this.beforeMs = beforeMs;
            this.afterMs = afterMs;
        }

        public String displayName() {
            return "OTRO".equals(type) ? customName : type;
        }
    }

    public static final class Range {
        public long startMs;
        public long endMs;

        Range(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    /** Converts a 1–6 digit entry such as 224 to 2:24 or 444444 to 44:44:44. */
    public static String maskTimestamp(String digits) {
        String d = digits.replaceAll("\\D", "");
        if (d.length() > 6) d = d.substring(0, 6);
        if (d.isEmpty()) return "";
        if (d.length() <= 2) return "0:" + String.format(Locale.ROOT, "%02d", Long.parseLong(d));
        if (d.length() <= 4) {
            String minutes = d.substring(0, d.length() - 2);
            return minutes + ":" + d.substring(d.length() - 2);
        }
        String hours = d.substring(0, d.length() - 4);
        return hours + ":" + d.substring(d.length() - 4, d.length() - 2) + ":" + d.substring(d.length() - 2);
    }

    /** Returns -1 for malformed/out-of-range times. */
    public static long parseTimestamp(String value) {
        if (value == null || !value.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) return -1;
        String[] parts = value.split(":");
        long seconds;
        if (parts.length == 2) {
            int minutes = Integer.parseInt(parts[0]);
            int sec = Integer.parseInt(parts[1]);
            if (sec > 59) return -1;
            seconds = minutes * 60L + sec;
        } else {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int sec = Integer.parseInt(parts[2]);
            if (minutes > 59 || sec > 59) return -1;
            seconds = hours * 3600L + minutes * 60L + sec;
        }
        return seconds * 1000L;
    }

    public static String formatTime(long timeMs) {
        long seconds = Math.max(0, timeMs / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder);
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainder);
    }

    public static List<Range> mergedRanges(List<Event> events, long durationMs) {
        return mergedRanges(events, durationMs, MERGE_GAP_MS);
    }

    public static List<Range> mergedRanges(List<Event> events, long durationMs, long mergeGapMs) {
        List<Range> ranges = new ArrayList<>();
        for (Event event : events) {
            if (event.timeMs < 0 || event.timeMs > durationMs) continue;
            long eventEnd = "COMBATE".equals(event.type) ? event.endMs : event.timeMs;
            if (eventEnd < event.timeMs || eventEnd > durationMs ||
                    ("COMBATE".equals(event.type) && eventEnd == event.timeMs)) continue;
            long start = Math.max(0, event.timeMs - Math.max(0, event.beforeMs));
            long end = Math.min(durationMs, eventEnd + Math.max(0, event.afterMs));
            if (end > start) ranges.add(new Range(start, end));
        }
        ranges.sort(Comparator.comparingLong(range -> range.startMs));
        List<Range> merged = new ArrayList<>();
        for (Range range : ranges) {
            if (merged.isEmpty() || range.startMs > merged.get(merged.size() - 1).endMs + Math.max(0, mergeGapMs)) {
                merged.add(range);
            } else {
                Range last = merged.get(merged.size() - 1);
                last.endMs = Math.max(last.endMs, range.endMs);
            }
        }
        return merged;
    }
}
