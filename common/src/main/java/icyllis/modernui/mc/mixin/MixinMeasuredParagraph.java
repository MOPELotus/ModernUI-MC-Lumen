package icyllis.modernui.mc.mixin;

import icyllis.modernui.text.MeasuredParagraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = MeasuredParagraph.class, remap = false)
public abstract class MixinMeasuredParagraph {
    @Shadow private int mTextStart;

    @ModifyArgs(method = "applyMetricsAffectingSpan", at = @At(value = "INVOKE", target =
            "Licyllis/modernui/text/style/ReplacementSpan;getSize(Licyllis/modernui/text/TextPaint;" +
                    "Ljava/lang/CharSequence;IILicyllis/modernui/graphics/text/FontMetricsInt;)I"),
            require = 1, allow = 1)
    private void modernui$keepReplacementTextCoordinates(Args args) {
        // The run boundaries already refer to mSpanned, not to the copied paragraph buffer.
        args.set(2, args.<Integer>get(2) - mTextStart);
        args.set(3, args.<Integer>get(3) - mTextStart);
    }
}
