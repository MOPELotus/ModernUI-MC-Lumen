import com.ibm.icu.text.Bidi;
import icyllis.modernui.text.Directions;
import icyllis.modernui.text.MeasuredParagraph;
import icyllis.modernui.text.StaticLayout;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextLine;
import icyllis.modernui.text.TextPaint;

import java.util.TreeMap;

/** Run against the actual Core classes after applying the BiDi mixins. */
public final class ActualBidiRunLevelsCheck {
    private static final int PACKED_LENGTH_MASK = (1 << 26) - 1;

    public static void main(String[] args) {
        TextPaint paint = new TextPaint();
        String deep = "A" + "\u202A".repeat(31) + "אב" + "\u202A" + "xy"
                + "\u202C" + "ג" + "\u202C".repeat(31) + "Z";
        checkLine(paint, deep, false, 64);
        checkLine(paint, "א" + "\u202B".repeat(32) + "ab" + "\u202A" + "אג"
                + "\u202C" + "c" + "\u202C".repeat(32) + "ג", true, 67);
        checkLine(paint, "A" + "\u202A".repeat(62) + "אב" + "\u202A" + "xy"
                + "\u202C" + "ג" + "\u202C".repeat(62) + "Z", false, 125);
        checkLine(paint, "abc אבג \u2067דה\u2069 x 😀", false, 1);

        // The standalone line in this slice is normalized by ICU to a single LTR run.
        int start = deep.indexOf("xy");
        MeasuredParagraph paragraph = MeasuredParagraph.buildForBidi(
                deep, 0, deep.length(), TextDirectionHeuristics.LTR, null);
        try {
            if (paragraph.getDirections(start, start + 2) != Directions.ALL_LEFT_TO_RIGHT) {
                throw new AssertionError("Unexpected Directions for the normalized single-run slice");
            }
        } finally {
            paragraph.recycle();
        }
    }

    private static void checkLine(TextPaint paint, String text, boolean rtl, int maxExpectedLevel) {
        StaticLayout layout = StaticLayout.builder(text, 0, text.length(), paint, 1000)
                .setTextDirection(rtl ? TextDirectionHeuristics.RTL : TextDirectionHeuristics.LTR)
                .build();
        if (layout.getLineCount() != 1) {
            throw new AssertionError("Fixture must fit on one line");
        }

        byte baseLevel = (byte) (rtl ? 1 : 0);
        Bidi paragraph = new Bidi();
        paragraph.setPara(text, baseLevel, null);
        byte[] expected = paragraph.createLineBidi(0, text.length()).getLevels();
        Directions directions = layout.getLineDirections(0);
        int[] reference = directions.mDirections.clone();

        // Build an equivalent cursor oracle using representable levels. Preserve the order
        // and parity of every run level while leaving run positions and lengths untouched.
        TreeMap<Integer, Integer> compressedLevels = new TreeMap<>();
        for (int run = 0; run < directions.getRunCount(); run++) {
            int level = Byte.toUnsignedInt(expected[directions.getRunStart(run)]);
            compressedLevels.put(level, 0);
        }
        if (compressedLevels.lastKey() != maxExpectedLevel) {
            throw new AssertionError("Unexpected ICU maximum level: " + compressedLevels.lastKey());
        }
        int previous = -1;
        for (var entry : compressedLevels.entrySet()) {
            int level = previous + 1;
            if ((level & 1) != (entry.getKey() & 1)) level++;
            entry.setValue(level);
            previous = level;
        }
        for (int run = 0; run < directions.getRunCount(); run++) {
            int level = Byte.toUnsignedInt(expected[directions.getRunStart(run)]);
            int packedIndex = 2 * run + 1;
            if (directions.isRunRtl(run) != ((level & 1) != 0)
                    || reference[packedIndex] >>> 26 != (level & 63)) {
                throw new AssertionError("The public packed Directions format changed");
            }
            reference[packedIndex] = (reference[packedIndex] & PACKED_LENGTH_MASK)
                    | (compressedLevels.get(level) << 26);
        }

        boolean[] bulk = layout.primaryIsTrailingPreviousAllLineOffsets(0);
        TextLine actual = TextLine.obtain();
        TextLine oracle = TextLine.obtain();
        try {
            actual.set(paint, text, 0, text.length(), rtl ? -1 : 1, directions, false, null, 0, 0);
            oracle.set(paint, text, 0, text.length(), rtl ? -1 : 1,
                    new Directions(reference), false, null, 0, 0);
            for (int offset = 0; offset <= text.length(); offset++) {
                int before = offset == 0 ? baseLevel : Byte.toUnsignedInt(expected[offset - 1]);
                int after = offset == text.length() ? baseLevel : Byte.toUnsignedInt(expected[offset]);
                boolean trailing = before < after;
                if (layout.primaryIsTrailingPrevious(offset) != trailing
                        || (offset < text.length() && bulk[offset] != trailing)) {
                    throw new AssertionError("Wrong caret priority at " + offset + ": " + before + " -> " + after);
                }
                checkMove(actual, oracle, offset, true);
                checkMove(actual, oracle, offset, false);
            }
        } finally {
            oracle.recycle();
            actual.recycle();
        }

        if (maxExpectedLevel >= 64) {
            int lastRun = directions.getRunCount() - 1;
            if (layout.isLevelBoundary(0) != (expected[directions.getRunStart(0)] != baseLevel)
                    || layout.isLevelBoundary(text.length())
                    != (expected[directions.getRunStart(lastRun)] != baseLevel)) {
                throw new AssertionError("Wrong BiDi line boundary");
            }
        }
        System.out.println("Checked " + (text.length() + 1) + " caret offsets, ICU max level " + maxExpectedLevel);
    }

    private static void checkMove(TextLine actual, TextLine oracle, int offset, boolean left) {
        if (actual.getOffsetToLeftRightOf(offset, left) != oracle.getOffsetToLeftRightOf(offset, left)) {
            throw new AssertionError("Wrong TextLine move at " + offset + ", left=" + left);
        }
    }
}
