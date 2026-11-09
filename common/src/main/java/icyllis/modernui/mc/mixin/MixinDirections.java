package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.text.BidiRunLevels;
import icyllis.modernui.text.Directions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Directions.class, remap = false)
public abstract class MixinDirections implements BidiRunLevels {
    @Unique
    private byte[] modernui$fullBidiRunLevels;

    @Override
    public byte[] modernui$getFullBidiRunLevels() {
        return modernui$fullBidiRunLevels;
    }

    @Override
    public void modernui$setFullBidiRunLevels(byte[] levels) {
        modernui$fullBidiRunLevels = levels;
    }
}
