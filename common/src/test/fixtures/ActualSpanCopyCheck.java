package icyllis.modernui.mc;

import icyllis.modernui.text.*;
import java.util.List;

/** Behavioral checks against the actual Core class transformed by Sponge Mixin. */
public final class ActualSpanCopyCheck {
    private static int assertions;

    public static void main(String[] args) {
        checkCopy();
    }

    private static void checkCopy() {
        var source = new SpannableString("abc\ndef\nghi");
        var first = new Object();
        var second = new Object();
        var third = new Object();
        var crossing = new Object();
        var noCopy = new NoCopySpan.Concrete();
        source.setSpan(first, 0, 4, Spanned.SPAN_PARAGRAPH);
        source.setSpan(second, 4, 8, Spanned.SPAN_PARAGRAPH);
        source.setSpan(third, 8, 11, Spanned.SPAN_PARAGRAPH);
        source.setSpan(crossing, 2, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | 0x20000);
        var immutable = new SpannedString(source);
        var builder = new SpannableStringBuilder(source);
        source.setSpan(noCopy, 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        for (int from = 0; from < source.length(); from++) {
            for (int to = from + 1; to <= source.length(); to++) {
                var expected = (Spanned) builder.subSequence(from, to);
                for (var original : List.<Spanned>of(source, immutable)) {
                    var actual = (Spanned) original.subSequence(from, to);
                    require(actual.toString().equals(expected.toString()), "slice text");
                    require(actual.getSpanStart(noCopy) == -1, "NoCopySpan excluded");
                    for (var span : List.of(first, second, third, crossing)) {
                        require(actual.getSpanStart(span) == expected.getSpanStart(span)
                                && actual.getSpanEnd(span) == expected.getSpanEnd(span)
                                && actual.getSpanFlags(span) == expected.getSpanFlags(span),
                                "span clipping/flags for [" + from + "," + to + ") " + actual);
                    }
                }
            }
        }

        var dense = new SpannableString("abcdefghijklmnopqrst");
        var firstDense = new Object();
        dense.setSpan(firstDense, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        for (int i = 1; i < 19; i++) dense.setSpan(new Object(), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        var absent = new Object();
        var clone = new SpannableString(dense);
        for (var copy : List.of(clone, new SpannedString(dense), (Spanned) dense.subSequence(0, 19))) {
            require(copy.getSpanStart(absent) == -1 && copy.getSpanEnd(absent) == -1
                    && copy.getSpanFlags(absent) == 0, "absent span sentinel survives dense copy");
        }
        clone.removeSpan(absent);
        require(clone.getSpanStart(firstDense) == 0 && clone.getSpanEnd(firstDense) == 1,
                "removing an absent span does not remove the first span");
        clone.setSpan(absent, 19, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        require(clone.getSpanStart(firstDense) == 0 && clone.getSpanStart(absent) == 19,
                "adding a new span does not overwrite the first span");
        System.out.println("PASS actual Core paragraph slices and dense copies: " + assertions + " assertions");
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        assertions++;
    }
}
