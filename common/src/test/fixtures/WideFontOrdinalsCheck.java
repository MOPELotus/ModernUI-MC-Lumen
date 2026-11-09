import com.ibm.icu.text.Bidi;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.text.Font;
import icyllis.modernui.graphics.text.FontCollection;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.FontPaint;
import icyllis.modernui.graphics.text.LayoutPiece;
import icyllis.modernui.graphics.text.ShapedText;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Locale;

/** Run with ActualCoreMixinTransform output first on the class path, or baseline to reproduce the wrap. */
public final class WideFontOrdinalsCheck {
    private static final int FIRST = 0xE000;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        boolean woven = args.length == 0;
        var families = new FontFamily[601];
        for (int i = 0; i < 600; i++) families[i] = new FontFamily(new TestFont(FIRST + i));
        families[600] = new FontFamily(new TestFont('א'));
        var paint = new FontPaint();
        paint.setLocale(Locale.ROOT);
        paint.setFont(new FontCollection(families));
        paint.setFontSize(12);
        var constructor = LayoutPiece.class.getDeclaredConstructor(char[].class, int.class, int.class, int.class,
                int.class, boolean.class, FontPaint.class, LayoutPiece.class, int.class);
        constructor.setAccessible(true);
        for (int count : new int[]{1, 255, 256, 257, 600}) {
            int repeats = count == 600 ? 6 : 0;
            char[] text = new char[count + repeats];
            for (int i = 0; i < count; i++) text[i] = (char) (FIRST + i);
            if (repeats > 0) {
                int[] revisit = {256, 255, 512, 256, 0, 599};
                for (int i = 0; i < repeats; i++) text[count + i] = (char) (FIRST + revisit[i]);
            }
            for (boolean rtl : new boolean[]{false, true}) {
                var piece = constructor.newInstance(text, 0, text.length, 0, text.length, rtl, paint, null, 0);
                checkPiece(piece, text.length);
                var hint = constructor.newInstance(text, 0, text.length, 0, text.length, rtl, paint, piece, 1);
                checkPiece(hint, text.length);
                check(piece.getGlyphs() == hint.getGlyphs(), "hint retains original glyph array");
                check(piece.getPositions() == hint.getPositions(), "hint retains original position array");
                check(hint.getAdvances() != null && hint.getAdvances().length == text.length,
                        "hint fills requested cluster advances");
                if (woven) {
                    var indices = wide(piece);
                    check((indices != null) == (count > 256), "LayoutPiece compact/wide boundary " + count);
                    check(indices == wide(hint), "hint shares immutable wide indices");
                }
            }
            // Spaces force many independent layout pieces; the combined run must keep each font identity.
            char[] spaced = new char[text.length * 2];
            for (int i = 0; i < text.length; i++) {
                spaced[2 * i] = text[i];
                spaced[2 * i + 1] = ' ';
            }
            for (int direction : new int[]{ShapedText.BIDI_OVERRIDE_LTR, ShapedText.BIDI_OVERRIDE_RTL}) {
                var shaped = new ShapedText(spaced, 0, spaced.length, 0, spaced.length, direction, paint);
                checkShaped(shaped, spaced.length);
                if (woven) check((wide(shaped) != null) == (count > 256), "ShapedText compact/wide boundary " + count);
            }
            // The precomputed-Bidi constructor is a separate finalization path.
            String mixed = new String(spaced) + 'א';
            var bidi = new Bidi(mixed, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            var shaped = new ShapedText(bidi, 0, mixed.length(), paint, ShapedText.CREATE_POSITIONED_GLYPHS);
            checkShaped(shaped, mixed.length());
            if (woven) check((wide(shaped) != null) == (count >= 256), "mixed Bidi adds one distinct font " + count);
        }
        System.out.println("WideFontOrdinals: " + assertions + " actual Core glyph/font assertions passed");
    }

    private static Object wide(Object shaped) throws ReflectiveOperationException {
        var field = shaped.getClass().getDeclaredField("modernui$wideFontIndices");
        field.setAccessible(true);
        return field.get(shaped);
    }

    private static void checkPiece(LayoutPiece piece, int count) {
        check(piece.getGlyphCount() == count, "LayoutPiece glyph count");
        for (int i = 0; i < count; i++) verifyFont(piece.getGlyphs()[i], piece.getFont(i));
    }

    private static void checkShaped(ShapedText shaped, int count) {
        check(shaped.getGlyphCount() == count, "ShapedText glyph count");
        for (int i = 0; i < count; i++) verifyFont(shaped.getGlyph(i), shaped.getFont(i));
    }

    private static void verifyFont(int glyph, Font font) {
        int expected = glyph == ' ' ? FIRST : glyph;
        check(((TestFont) font).codePoint == expected, "glyph " + glyph + " got font " + ((TestFont) font).codePoint);
    }

    private static void check(boolean result, String description) {
        if (!result) throw new AssertionError(description);
        assertions++;
    }

    private record TestFont(int codePoint) implements Font {
        @Override public int getStyle() { return 0; }
        @Override public String getFullName(Locale locale) { return "Test " + codePoint; }
        @Override public String getFamilyName(Locale locale) { return getFullName(locale); }
        @Override public boolean hasGlyph(int code, int selector) {
            return code == codePoint || code == ' ' && codePoint == FIRST;
        }
        @Override public int getMetrics(FontPaint paint, FontMetricsInt metrics) {
            if (metrics != null) metrics.extendBy(-8, 2);
            return 10;
        }
        @Override public float doSimpleLayout(char[] text, int start, int end, FontPaint paint,
                                              IntArrayList glyphs, FloatArrayList positions, float x, float y) {
            return doComplexLayout(text, start, end, start, end, false, paint, glyphs, positions,
                    null, start, null, x, y);
        }
        @Override public float doComplexLayout(char[] text, int contextStart, int contextEnd, int start, int end,
                                               boolean rtl, FontPaint paint, IntArrayList glyphs,
                                               FloatArrayList positions, float[] advances, int offset,
                                               Rect bounds, float x, float y) {
            for (int i = 0; i < end - start; i++) {
                int index = rtl ? end - 1 - i : start + i;
                if (glyphs != null) { glyphs.add(text[index]); positions.add(x + i); positions.add(y); }
                if (advances != null) advances[index - offset] = 1;
            }
            return end - start;
        }
        @Override public icyllis.arc3d.sketch.Typeface getNativeTypeface() { return null; }
    }
}
