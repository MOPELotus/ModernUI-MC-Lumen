package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.text.BidiRunLevels;
import icyllis.modernui.text.Directions;
import icyllis.modernui.text.Layout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Layout.class, remap = false)
public abstract class MixinLayoutBidiLevels {
    @Shadow public abstract int getLineForOffset(int offset);
    @Shadow public abstract int getLineStart(int line);
    @Shadow public abstract int getLineEnd(int line);
    @Shadow public abstract int getParagraphDirection(int line);
    @Shadow public abstract Directions getLineDirections(int line);

    @Inject(method = "primaryIsTrailingPrevious(I)Z", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private void modernui$primaryUsesFullBidiLevels(int offset, CallbackInfoReturnable<Boolean> cir) {
        int line = getLineForOffset(offset);
        Directions directions = getLineDirections(line);
        byte[] levels = BidiRunLevels.get(directions);
        if (levels == null) return;

        int start = getLineStart(line);
        int length = getLineEnd(line) - start;
        int baseLevel = getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT ? 1 : 0;
        int index = offset - start;
        cir.setReturnValue(BidiRunLevels.levelAt(directions, levels, index - 1, length, baseLevel)
                < BidiRunLevels.levelAt(directions, levels, index, length, baseLevel));
    }

    @Inject(method = "primaryIsTrailingPreviousAllLineOffsets(I)[Z", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private void modernui$allPrimariesUseFullBidiLevels(int line, CallbackInfoReturnable<boolean[]> cir) {
        Directions directions = getLineDirections(line);
        byte[] levels = BidiRunLevels.get(directions);
        if (levels == null) return;

        int length = getLineEnd(line) - getLineStart(line);
        boolean[] trailing = new boolean[length + 1];
        byte[] preceding = new byte[length];
        for (int i = 0; i < levels.length; i++) {
            int start = directions.getRunStart(i);
            int end = start + Math.min(directions.getRunLength(i), length - start);
            if (end > start) preceding[end - 1] = levels[i];
        }
        int baseLevel = getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT ? 1 : 0;
        for (int i = 0; i < levels.length; i++) {
            int start = directions.getRunStart(i);
            int previous = start == 0 ? baseLevel : Byte.toUnsignedInt(preceding[start - 1]);
            trailing[start] = previous < Byte.toUnsignedInt(levels[i]);
        }
        cir.setReturnValue(trailing);
    }

    @Inject(method = "isLevelBoundary(I)Z", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private void modernui$lineEdgesUseFullBidiLevels(int offset, CallbackInfoReturnable<Boolean> cir) {
        int line = getLineForOffset(offset);
        byte[] levels = BidiRunLevels.get(getLineDirections(line));
        if (levels == null) return;

        int run;
        if (offset == getLineStart(line)) {
            run = 0;
        } else if (offset == getLineEnd(line)) {
            run = levels.length - 1;
        } else {
            return;
        }
        int baseLevel = getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT ? 1 : 0;
        cir.setReturnValue(Byte.toUnsignedInt(levels[run]) != baseLevel);
    }
}
