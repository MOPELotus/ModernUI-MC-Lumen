package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.text.BidiRunLevels;
import icyllis.modernui.text.Directions;
import icyllis.modernui.text.TextLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = TextLine.class, remap = false)
public abstract class MixinTextLineBidiLevels {
    @Shadow private Directions mDirections;

    // TextLine belongs to one caller until recycle. Each capture is consumed by the adjacent
    // level store; there are no calls in the original bytecode between the two points.
    @Unique private int modernui$capturedBidiLevelPlusOne;

    @Unique
    private void modernui$captureRunLevel(int packedRunIndex) {
        byte[] levels = BidiRunLevels.get(mDirections);
        modernui$capturedBidiLevelPlusOne = levels == null ? 0
                : Byte.toUnsignedInt(levels[packedRunIndex / 2]) + 1;
    }

    @Unique
    private int modernui$takeRunLevel(int packedLevel) {
        int captured = modernui$capturedBidiLevelPlusOne;
        modernui$capturedBidiLevelPlusOne = 0;
        return captured == 0 ? packedLevel : captured - 1;
    }

    @Inject(method = "getOffsetToLeftRightOf(IZ)I",
            at = @At(value = "CONSTANT", args = "intValue=63", ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILHARD, require = 1, allow = 1)
    private void modernui$captureCurrentRun(int cursor, boolean toLeft, CallbackInfoReturnable<Integer> cir,
                                            int lineStart, int lineEnd, boolean paragraphRtl, int[] runs,
                                            int runIndex) {
        modernui$captureRunLevel(runIndex);
    }

    @ModifyVariable(method = "getOffsetToLeftRightOf(IZ)I", at = @At(value = "STORE", ordinal = 0), index = 8,
            slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=63", ordinal = 0),
                    to = @At(value = "CONSTANT", args = "intValue=63", ordinal = 1)), require = 1, allow = 1)
    private int modernui$restoreCurrentRunLevel(int packedLevel) {
        return modernui$takeRunLevel(packedLevel);
    }

    @Inject(method = "getOffsetToLeftRightOf(IZ)I",
            at = @At(value = "CONSTANT", args = "intValue=63", ordinal = 1),
            locals = LocalCapture.CAPTURE_FAILHARD, require = 1, allow = 1)
    private void modernui$capturePreviousRun(int cursor, boolean toLeft, CallbackInfoReturnable<Integer> cir,
                                             int lineStart, int lineEnd, boolean paragraphRtl, int[] runs,
                                             int runIndex, int runLevel, int runStart, int runLimit,
                                             int newCaret, boolean trailing, int previousRunIndex) {
        modernui$captureRunLevel(previousRunIndex);
    }

    @ModifyVariable(method = "getOffsetToLeftRightOf(IZ)I", at = @At(value = "STORE", ordinal = 0), index = 14,
            slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=63", ordinal = 1),
                    to = @At(value = "CONSTANT", args = "intValue=63", ordinal = 2)), require = 1, allow = 1)
    private int modernui$restorePreviousRunLevel(int packedLevel) {
        return modernui$takeRunLevel(packedLevel);
    }

    @Inject(method = "getOffsetToLeftRightOf(IZ)I",
            at = @At(value = "CONSTANT", args = "intValue=63", ordinal = 2),
            locals = LocalCapture.CAPTURE_FAILHARD, require = 1, allow = 1)
    private void modernui$captureOtherRun(int cursor, boolean toLeft, CallbackInfoReturnable<Integer> cir,
                                          int lineStart, int lineEnd, boolean paragraphRtl, int[] runs,
                                          int runIndex, int runLevel, int runStart, int runLimit,
                                          int newCaret, boolean trailing, boolean advance, int otherRunIndex) {
        modernui$captureRunLevel(otherRunIndex);
    }

    @ModifyVariable(method = "getOffsetToLeftRightOf(IZ)I", at = @At(value = "STORE", ordinal = 0), index = 17,
            slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=63", ordinal = 2)),
            require = 1, allow = 1)
    private int modernui$restoreOtherRunLevel(int packedLevel) {
        return modernui$takeRunLevel(packedLevel);
    }
}
