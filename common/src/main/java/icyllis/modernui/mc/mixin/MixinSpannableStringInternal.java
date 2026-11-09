package icyllis.modernui.mc.mixin;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "icyllis.modernui.text.SpannableStringInternal", remap = false)
public abstract class MixinSpannableStringInternal {
    @Shadow private Reference2IntOpenHashMap<Object> mIndexOfSpan;

    @Redirect(method = "copySpansFromInternal(Licyllis/modernui/text/SpannableStringInternal;II)V",
            at = @At(value = "INVOKE",
                    target = "Licyllis/modernui/text/SpannableStringInternal;isIndexFollowsNextLine(I)Z"),
            require = 2, allow = 2)
    private boolean lumenCheckCopiedParagraphBoundary(@Coerce Object receiver, int index,
                                                       @Coerce Object source, int start, int end) {
        // These operands are still in source coordinates. Match the clipping that follows.
        int offset = Math.max(start, Math.min(end, index)) - start;
        return offset != 0 && offset != end - start
                && ((CharSequence) receiver).charAt(offset - 1) != '\n';
    }

    @Inject(method = "copySpansFromInternal(Licyllis/modernui/text/SpannableStringInternal;II)V",
            at = @At("RETURN"), require = 1)
    private void lumenRestoreMissingSpanIndex(CallbackInfo ci) {
        // The fastutil copy constructor copies entries, but resets the missing-key sentinel to 0.
        if (mIndexOfSpan != null) mIndexOfSpan.defaultReturnValue(-1);
    }
}
