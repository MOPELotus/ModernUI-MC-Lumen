package icyllis.modernui.mc.text;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.LineBreakConfig;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.TextDirectionHeuristic;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.style.ReplacementSpan;

import java.lang.reflect.Method;

/** Exercises original paragraph construction with the production argument handler. */
public final class MeasuredParagraphCheck {
    private static final String TARGET = "icyllis.modernui.text.MeasuredParagraph";
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        Class<?> type = Class.forName(TARGET);
        var build = type.getMethod("buildForStaticLayout", TextPaint.class, LineBreakConfig.class,
                CharSequence.class, int.class, int.class, TextDirectionHeuristic.class, boolean.class, type);
        var paint = new TextPaint();
        paint.setTypeface(Typeface.SERIF);
        paint.setTextSize(32);
        Object paragraph = measure(type, build, paint, "prefix\nword", 7, 11, 7, 11, 7, 11,
                TextDirectionHeuristics.FIRSTSTRONG_LTR, null);
        try {
            paragraph = measure(type, build, paint, "word", 0, 4, 0, 4, 0, 4,
                    TextDirectionHeuristics.FIRSTSTRONG_LTR, paragraph);
            paragraph = measure(type, build, paint, "prefix\na[word]b", 7, 15, 8, 14, 8, 14,
                    TextDirectionHeuristics.FIRSTSTRONG_LTR, paragraph);
            paragraph = measure(type, build, paint, "prefix\nabcdefgh", 9, 13, 8, 14, 9, 13,
                    TextDirectionHeuristics.FIRSTSTRONG_LTR, paragraph);
            paragraph = measure(type, build, paint, "prefix\nאבגד abc", 7, 15, 8, 10, 8, 10,
                    TextDirectionHeuristics.RTL, paragraph);
        } finally {
            type.getMethod("recycle").invoke(paragraph);
        }
        System.out.println("MeasuredParagraph: " + checks + " actual Core coordinate assertions passed");
    }

    private static Object measure(Class<?> type, Method build, TextPaint paint, String value,
                                  int paragraphStart, int paragraphEnd, int spanStart, int spanEnd,
                                  int expectedStart, int expectedEnd, TextDirectionHeuristic direction,
                                  Object recycle) throws Exception {
        var text = new SpannableString(value);
        int[] observed = {-1, -1, 0};
        text.setSpan(new ReplacementSpan() {
            @Override public int getSize(TextPaint p, CharSequence original, int start, int end, FontMetricsInt fm) {
                check(original == text, "ReplacementSpan receives the original Spanned");
                observed[0] = start; observed[1] = end; observed[2]++;
                return 32;
            }
            @Override public void draw(Canvas canvas, CharSequence t, int s, int e, float x,
                                       int top, int y, int bottom, TextPaint p) {}
        }, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        var paragraph = build.invoke(null, paint, LineBreakConfig.NONE, text, paragraphStart, paragraphEnd,
                direction, true, recycle);
        check(observed[2] == 1 && observed[0] == expectedStart && observed[1] == expectedEnd,
                "original-text range [" + expectedStart + "," + expectedEnd + ") became [" +
                        observed[0] + "," + observed[1] + ")");
        check((int) type.getMethod("getTextStart").invoke(paragraph) == paragraphStart, "original start retained");
        check((int) type.getMethod("getTextLength").invoke(paragraph) == paragraphEnd - paragraphStart,
                "paragraph buffer retains local length");
        float width = (float) type.getMethod("getAdvance", int.class, int.class).invoke(paragraph,
                expectedStart - paragraphStart, expectedEnd - paragraphStart);
        check(width == 32, "replacement run remains at its paragraph-local index");
        check(recycle == null || recycle == paragraph, "recycled measurement reused");
        return paragraph;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
