package com.batclan.montaje;

import java.util.Arrays;
import java.util.List;

/** Run with javac/java; no Android runtime required. */
public final class MontageLogicTest {
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        expect(MontageLogic.maskTimestamp("4").equals("0:04"), "single digit mask");
        expect(MontageLogic.maskTimestamp("224").equals("2:24"), "minute mask");
        expect(MontageLogic.maskTimestamp("444444").equals("44:44:44"), "hour mask");
        expect(MontageLogic.parseTimestamp("2:24") == 144000, "parse minute");
        expect(MontageLogic.parseTimestamp("01:02:03") == 3723000, "parse hour");
        expect(MontageLogic.parseTimestamp("1:60") < 0, "reject invalid seconds");
        List<MontageLogic.Event> events = Arrays.asList(
                new MontageLogic.Event(10000, "KILL", 5000, 5000),
                new MontageLogic.Event(19000, "CLUTCH", 5000, 5000),
                new MontageLogic.Event(50000, "OTRO", 3000, 4000));
        List<MontageLogic.Range> merged = MontageLogic.mergedRanges(events, 60000);
        expect(merged.size() == 2, "merge overlapping clips");
        expect(merged.get(0).startMs == 5000 && merged.get(0).endMs == 24000, "merged first range");
        expect(merged.get(1).startMs == 47000 && merged.get(1).endMs == 54000, "second range");
        MontageLogic.Event combat = new MontageLogic.Event(130000, 150000, "COMBATE", "", 7000, 3000);
        MontageLogic.Event ace = new MontageLogic.Event(151000, 151000, "OTRO", "ACE", 2000, 3000);
        List<MontageLogic.Range> combatMerged = MontageLogic.mergedRanges(Arrays.asList(combat, ace), 180000);
        expect(combatMerged.size() == 1 && combatMerged.get(0).startMs == 123000 &&
                combatMerged.get(0).endMs == 154000, "combat merges with overlapping events");
        expect(ace.displayName().equals("ACE"), "custom event label");
        expect(MontageLogic.mergedRanges(Arrays.asList(
                new MontageLogic.Event(140000, 130000, "COMBATE", "", 7000, 3000)), 180000).isEmpty(),
                "invalid combat interval is ignored");
        System.out.println("MontageLogic: OK");
    }
}
