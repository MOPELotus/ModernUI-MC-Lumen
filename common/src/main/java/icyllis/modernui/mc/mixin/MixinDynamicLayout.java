package icyllis.modernui.mc.mixin;

import icyllis.modernui.text.DynamicLayout;
import icyllis.modernui.text.StaticLayout;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Arrays;

@Mixin(value = DynamicLayout.class, remap = false)
public abstract class MixinDynamicLayout {
    @Shadow private static StaticLayout sStaticLayout;

    @Redirect(method = "reflow(Ljava/lang/CharSequence;III)V",
            slice = @Slice(from = @At(value = "INVOKE", target = "Licyllis/modernui/text/StaticLayout$Builder;release()V")),
            at = @At(value = "FIELD", target = "Licyllis/modernui/text/DynamicLayout;sStaticLayout:Licyllis/modernui/text/StaticLayout;",
                    opcode = Opcodes.PUTSTATIC), require = 1, allow = 1)
    private void modernui$releasePooledLineDirections(StaticLayout layout) {
        // The active layout has copied its direction references; the scratch arrays can be reused.
        Arrays.fill(((AccessStaticLayoutDirections) layout).modernui$getLineDirections(), 0, layout.getLineCount(), null);
        sStaticLayout = layout;
    }
}
