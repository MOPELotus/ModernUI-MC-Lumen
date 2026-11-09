package icyllis.modernui.mc.mixin;

import icyllis.modernui.text.Directions;
import icyllis.modernui.text.StaticLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StaticLayout.class, remap = false)
public interface AccessStaticLayoutDirections {
    @Accessor("mLineDirections")
    Directions[] modernui$getLineDirections();
}
