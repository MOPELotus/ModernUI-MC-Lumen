package icyllis.modernui.mc;

import icyllis.modernui.graphics.text.LayoutCache;
import icyllis.modernui.graphics.text.LineBreaker;
import icyllis.modernui.graphics.text.MeasuredText;
import icyllis.modernui.text.DynamicLayout;
import icyllis.modernui.text.Layout;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.StaticLayout;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextUtils;

import java.util.Arrays;

/** Headless behavior checks for the layout fixes on transformed Core classes. */
public final class ActualLayoutBoundsCheck {
    public static void main(String[] args) throws ReflectiveOperationException {
        checkCache();
        checkIndents();
        checkPooledDirections();
        System.out.println("PASS actual Core layout cache, StaticLayout indents and pooled directions");
    }

    private static void checkCache() {
        var paint = new TextPaint().createInternalPaint();
        int cutoff = LayoutCache.MAX_PIECE_LENGTH;
        char[] word = "a".repeat(cutoff * 4).toCharArray();
        LayoutCache.clear();
        var cached = LayoutCache.getOrCreate(word, 1, 1 + cutoff, 2, 3, false, paint, 0);
        require(cached == LayoutCache.getOrCreate(word, 1, 1 + cutoff, 2, 3, false, paint, 0), "cutoff stays cacheable");
        var uncached = LayoutCache.getOrCreate(word, 1, 2 + cutoff, 2, 3, false, paint, LayoutCache.COMPUTE_CLUSTER_ADVANCES);
        require(uncached.getCharCount() == 1 && uncached.getAdvances().length == 1
                && uncached.getAdvance() == cached.getAdvance() && LayoutCache.getSize() == 1, "long context bypass retains requested piece and advances");

        LayoutCache.clear();
        var width = new LineBreaker.ParagraphConstraints(); // zero width forces a line per ASCII grapheme
        var measured = new MeasuredText.Builder(word).appendStyleRun(paint, word.length, false).build();
        var lines = LineBreaker.computeLineBreaks(measured, width, null, 0);
        require(lines.getLineCount() == word.length && lines.getLineBreakOffset(word.length - 1) == word.length, "long word still wraps fully");
        require(LayoutCache.getSize() == 0, "each wrapped line must not cache another copy of the long word");
        var shortWord = new MeasuredText.Builder(new char[]{'b'}).appendStyleRun(paint, 1, false).build();
        var shortLines = LineBreaker.computeLineBreaks(shortWord, width, null, 0);
        require(shortLines.getLineCount() == 1 && shortLines.getLineBreakOffset(0) == 1 && LayoutCache.getSize() == 1,
                "short text after long word wraps and caches normally");
    }

    private static void checkIndents() {
        var paint = new TextPaint();
        paint.setTextSize(12);
        String text = "a".repeat(80);
        var empty = StaticLayout.builder(text, 0, text.length(), paint, 60)
                .setIndents(new int[0], new int[0]).setEllipsize(TextUtils.TruncateAt.END).build();
        require(empty.getIndentAdjust(0, Layout.Alignment.ALIGN_LEFT) == 0
                && empty.getIndentAdjust(0, Layout.Alignment.ALIGN_RIGHT) == 0, "empty arrays act as no indents");
        for (boolean left : new boolean[]{true, false}) {
            int[] shortSide = {39};
            int[] zeros = {0, 0, 0};
            var builder = StaticLayout.builder(text, 0, text.length(), paint, 60)
                    .setIndents(left ? shortSide : zeros, left ? zeros : shortSide);
            shortSide[0] = 40;
            var actual = builder.build();
            int[] repeated = {40, 40, 40};
            var expected = StaticLayout.builder(text, 0, text.length(), paint, 60)
                    .setIndents(left ? repeated : zeros, left ? zeros : repeated).build();
            require(actual.getLineCount() == expected.getLineCount(), "repeat the short side's last inset in line breaking");
            for (int line = 0; line < actual.getLineCount(); line++) {
                require(actual.getLineEnd(line) == expected.getLineEnd(line), "same line boundary at " + line);
            }
            require(Arrays.equals(shortSide, new int[]{40}) && Arrays.equals(zeros, new int[]{0, 0, 0}), "caller arrays unchanged");
        }
    }

    private static void checkPooledDirections() throws ReflectiveOperationException {
        var paint = new TextPaint();
        paint.setTextSize(12);
        var text = new SpannableStringBuilder("a \u05d0\u05d1 z\n".repeat(32));
        var layout = DynamicLayout.builder(text, paint, 120).build();
        require(layout.getLineCount() > 32, "long mixed-direction text grew the line tables");
        var pool = DynamicLayout.class.getDeclaredField("sStaticLayout");
        pool.setAccessible(true);
        var directions = StaticLayout.class.getDeclaredField("mLineDirections");
        directions.setAccessible(true);
        for (int pass = 0; pass < 2; pass++) {
            var scratch = (StaticLayout) pool.get(null);
            var stored = (Object[]) directions.get(scratch);
            require(Arrays.stream(stored).allMatch(value -> value == null), "scratch layout retains no line directions");
            require(scratch.getText() == null && scratch.getPaint() == null, "scratch layout never owns the text or paint");
            for (int line = 0; line < layout.getLineCount(); line++) {
                require(layout.getLineDirections(line) != null, "active layout still owns line directions at " + line);
            }
            if (pass == 0) text.replace(0, text.length(), "a");
        }
        require(layout.getLineCount() == 1 && layout.getLineEnd(0) == 1, "long-to-short reflow still updates active lines");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
