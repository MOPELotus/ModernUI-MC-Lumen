package icyllis.modernui.mc.mixin;

import icyllis.modernui.text.StaticLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(value = StaticLayout.Builder.class, remap = false)
public abstract class MixinStaticLayoutBuilder {
    @Shadow private int[] mLeftIndents;
    @Shadow private int[] mRightIndents;

    @Inject(method = "build()Licyllis/modernui/text/StaticLayout;",
            at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$normalizeIndents(CallbackInfoReturnable<StaticLayout> callback) {
        if (mLeftIndents != null && mLeftIndents.length == 0) mLeftIndents = null;
        if (mRightIndents != null && mRightIndents.length == 0) mRightIndents = null;
        if (mLeftIndents == null || mRightIndents == null) return;

        // Line breaking combines both sides, so each side must already repeat its last value.
        if (mLeftIndents.length < mRightIndents.length) {
            mLeftIndents = modernui$repeatLastIndent(mLeftIndents, mRightIndents.length);
        } else if (mRightIndents.length < mLeftIndents.length) {
            mRightIndents = modernui$repeatLastIndent(mRightIndents, mLeftIndents.length);
        }
    }

    @Unique
    private static int[] modernui$repeatLastIndent(int[] indents, int length) {
        int[] expanded = Arrays.copyOf(indents, length);
        Arrays.fill(expanded, indents.length, length, indents[indents.length - 1]);
        return expanded;
    }
}
