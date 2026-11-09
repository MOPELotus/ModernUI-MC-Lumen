package icyllis.modernui.mc;

import icyllis.modernui.text.Layout;
import icyllis.modernui.text.StaticLayout;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.Typeface;

public final class ActualUnicodeLayoutCheck {
    public static void main(String[] args) {
        var paint = new TextPaint();
        paint.setTypeface(Typeface.SERIF);
        paint.setTextSize(24);
        var clusters = layout("A😀e\u0301👩‍🎤Z", paint, 1000);
        int offset = 0;
        for (int expected : new int[]{1, 3, 5, 10, 11}) {
            int next = clusters.getOffsetToRightOf(offset);
            require(next == expected, "rightward cursor split a grapheme at " + next + ", expected " + expected);
            require(clusters.getOffsetToLeftOf(next) == offset, "leftward cursor did not reverse the grapheme move");
            offset = next;
        }

        String paragraph = "A😀 e\u0301 שלום 👩‍🎤 café العربية 🎵🌸\n";
        String longText = paragraph.repeat(40) + "آخر😀";
        checkLineRanges(layout(longText, paint, 110), longText);
        checkLineRanges(layout("🌸", paint, 40), "🌸");
        checkLineRanges(layout("", paint, 40), "");
        System.out.println("PASS Core Unicode cursor movement, wrapped mixed BiDi paragraphs and long-to-empty reuse");
    }

    private static Layout layout(String text, TextPaint paint, int width) {
        return StaticLayout.builder(text, 0, text.length(), paint, width)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build();
    }

    private static void checkLineRanges(Layout layout, String text) {
        int start = 0;
        for (int line = 0; line < layout.getLineCount(); line++) {
            int end = layout.getLineEnd(line);
            require(layout.getLineStart(line) == start && end >= start && end <= text.length(),
                    "invalid line range " + start + ".." + end);
            require(end == text.length() || !Character.isLowSurrogate(text.charAt(end)), "line split a surrogate pair");
            require(Float.isFinite(layout.getPrimaryHorizontal(start)), "invalid primary cursor coordinate");
            start = end;
        }
        require(start == text.length(), "line ranges did not consume the text");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
