package icyllis.modernui.mc.text;

import icyllis.modernui.text.Directions;

/** Full ICU levels for lines whose levels exceed the six packed bits in Directions. */
public interface BidiRunLevels {
    byte[] modernui$getFullBidiRunLevels();

    void modernui$setFullBidiRunLevels(byte[] levels);

    static byte[] get(Directions directions) {
        return directions instanceof BidiRunLevels levels ? levels.modernui$getFullBidiRunLevels() : null;
    }

    static void attachSingle(Directions directions, int level) {
        if (level >= 64) {
            ((BidiRunLevels) directions).modernui$setFullBidiRunLevels(new byte[]{(byte) level});
        }
    }

    static void attachVisual(Directions directions, byte[] logicalLevels, int[] visualOrder, boolean paragraphRtl) {
        for (byte level : logicalLevels) {
            if (Byte.toUnsignedInt(level) >= 64) {
                byte[] visualLevels = new byte[logicalLevels.length];
                for (int i = 0; i < visualLevels.length; i++) {
                    int visualIndex = paragraphRtl ? visualLevels.length - i - 1 : i;
                    visualLevels[i] = logicalLevels[visualOrder[visualIndex]];
                }
                ((BidiRunLevels) directions).modernui$setFullBidiRunLevels(visualLevels);
                return;
            }
        }
    }

    static int levelAt(Directions directions, byte[] levels, int offset, int length, int baseLevel) {
        if (offset >= 0 && offset < length) {
            for (int i = 0; i < levels.length; i++) {
                int start = directions.getRunStart(i);
                if (offset >= start && offset - start < directions.getRunLength(i)) {
                    return Byte.toUnsignedInt(levels[i]);
                }
            }
        }
        return baseLevel;
    }
}
