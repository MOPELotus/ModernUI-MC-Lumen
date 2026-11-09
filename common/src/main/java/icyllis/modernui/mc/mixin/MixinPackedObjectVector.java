package icyllis.modernui.mc.mixin;

import icyllis.modernui.text.PackedObjectVector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(value = PackedObjectVector.class, remap = false)
abstract class MixinPackedObjectVector {

    @Shadow @Final private int mColumns;
    @Shadow private Object[] mValues;
    @Shadow private int mRowGapStart;
    @Shadow private int mRowGapLength;

    @Inject(method = "moveRowGapTo(I)V", at = @At(value = "FIELD",
            target = "Licyllis/modernui/text/PackedObjectVector;mRowGapStart:I", opcode = Opcodes.PUTFIELD),
            require = 1, allow = 1)
    private void lumen$clearMovedGap(int where, CallbackInfo ci) {
        // Only the newly exposed part of the gap can contain copied references.
        if (where < mRowGapStart) {
            lumen$clearRows(where, Math.min(mRowGapStart, where + mRowGapLength));
        } else {
            lumen$clearRows(Math.max(where, mRowGapStart + mRowGapLength), where + mRowGapLength);
        }
    }

    @Inject(method = "deleteAt(II)V", at = @At("RETURN"), require = 1, allow = 1)
    private void lumen$clearDeletedRows(int row, int count, CallbackInfo ci) {
        lumen$clearRows(mRowGapStart, mRowGapStart + count);
    }

    @Unique
    private void lumen$clearRows(int start, int end) {
        Arrays.fill(mValues, start * mColumns, end * mColumns, null);
    }
}
