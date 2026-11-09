package icyllis.modernui.mc.text;

import icyllis.modernui.graphics.text.Font;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

/** Extends a byte font table only after it acquires a 257th distinct font. */
public final class WideFontOrdinals {
    private final HashMap<Font, Integer> ordinals = new HashMap<>();
    private final IntArrayList ranges = new IntArrayList();
    private int current;

    public static WideFontOrdinals observe(WideFontOrdinals wide, HashMap<Font, Byte> compact,
                                           Font font, int previousSize) {
        int size = compact.size();
        if (size <= 256) return wide;
        if (wide == null) {
            assert previousSize == 256 && size == 257;
            wide = new WideFontOrdinals();
            for (var entry : compact.entrySet()) {
                int ordinal = Objects.equals(entry.getKey(), font) ? 256 : Byte.toUnsignedInt(entry.getValue());
                wide.ordinals.put(entry.getKey(), ordinal);
            }
        } else if (size != previousSize) {
            wide.ordinals.put(font, previousSize);
        }
        wide.current = wide.ordinals.get(font);
        return wide;
    }

    public void addRange(int start, int end) {
        if (current <= 255 || start == end) return;
        int size = ranges.size();
        if (size >= 3 && ranges.getInt(size - 1) == current && ranges.getInt(size - 2) == start) {
            ranges.set(size - 2, end);
        } else {
            ranges.add(start);
            ranges.add(end);
            ranges.add(current);
        }
    }

    public int[] finish(byte[] compact) {
        if (ranges.isEmpty()) return null;
        int[] wide = new int[compact.length];
        for (int i = 0; i < compact.length; i++) wide[i] = Byte.toUnsignedInt(compact[i]);
        for (int i = 0; i < ranges.size(); i += 3) {
            Arrays.fill(wide, ranges.getInt(i), ranges.getInt(i + 1), ranges.getInt(i + 2));
        }
        return wide;
    }

    /** Carries overflow records from the glyph collector to either ShapedText constructor. */
    public static final class GlyphIndexList extends ByteArrayList {
        private WideFontOrdinals wide;

        public GlyphIndexList(int capacity) {
            super(capacity);
        }

        public void observeFont(HashMap<Font, Byte> map, Font font, int previousSize) {
            wide = observe(wide, map, font, previousSize);
            if (wide != null) wide.addRange(size(), size() + 1);
        }

        public int[] finishWide(byte[] compact) {
            return wide == null ? null : wide.finish(compact);
        }
    }
}
