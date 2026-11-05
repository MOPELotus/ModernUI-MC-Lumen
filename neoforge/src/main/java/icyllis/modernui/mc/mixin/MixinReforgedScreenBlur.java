package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restore the existing override before Reforged cancels in-world menu blur. */
@Mixin(value = Screen.class, priority = 2000)
public abstract class MixinReforgedScreenBlur {
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), require = 1)
    private void modernui$processReforgedMenuBlur(float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && BlurHandler.sOverrideVanillaBlur) {
            try {
                BlurHandler.INSTANCE.processBlurEffect(partialTick);
            } finally {
                minecraft.getMainRenderTarget().bindWrite(false);
            }
        }
        // Reforged's lower-priority HEAD still resets scissor and cancels vanilla.
    }
}
