package icyllis.modernui.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

/** Restore the existing override before Reforged cancels in-world menu blur. */
@Mixin(Screen.class)
public abstract class MixinReforgedScreenBlur {
    @WrapMethod(method = "renderBlurredBackground", require = 1)
    private void modernui$processReforgedMenuBlur(float partialTick, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && BlurHandler.sOverrideVanillaBlur) {
            try {
                BlurHandler.INSTANCE.processBlurEffect(partialTick);
            } finally {
                minecraft.getMainRenderTarget().bindWrite(false);
            }
        }
        // Wrap the fully injected body: Reforged still resets scissor and cancels vanilla.
        original.call(partialTick);
    }
}
