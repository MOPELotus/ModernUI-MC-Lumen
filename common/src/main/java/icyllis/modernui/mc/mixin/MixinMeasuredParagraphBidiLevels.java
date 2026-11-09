package icyllis.modernui.mc.mixin;

import com.ibm.icu.text.Bidi;
import icyllis.modernui.mc.text.BidiRunLevels;
import icyllis.modernui.text.Directions;
import icyllis.modernui.text.MeasuredParagraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = MeasuredParagraph.class, remap = false)
public abstract class MixinMeasuredParagraphBidiLevels {
    @Shadow private Bidi mBidi;

    // The preceding returns are the shared all-LTR/all-RTL Directions.
    @Inject(method = "getDirections(II)Licyllis/modernui/text/Directions;",
            at = @At(value = "RETURN", ordinal = 5), locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1, allow = 1)
    private void modernui$attachSingleBidiLevel(int start, int end, CallbackInfoReturnable<Directions> cir,
                                               Bidi line) {
        BidiRunLevels.attachSingle(cir.getReturnValue(), line.getRunLevel(0));
    }

    @Inject(method = "getDirections(II)Licyllis/modernui/text/Directions;",
            at = @At(value = "RETURN", ordinal = 6), locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1, allow = 1)
    private void modernui$attachVisualBidiLevels(int start, int end, CallbackInfoReturnable<Directions> cir,
                                                Bidi line, byte[] levels, int[] visualOrder) {
        BidiRunLevels.attachVisual(cir.getReturnValue(), levels, visualOrder, (mBidi.getBaseLevel() & 1) != 0);
    }
}
