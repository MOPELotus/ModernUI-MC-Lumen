package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fade Minecraft channels independently of the user's MASTER gain and external category gains. */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Shadow private boolean loaded;

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void modernui$applyFocusGain(float volume, SoundSource source, CallbackInfoReturnable<Float> ci) {
        ci.setReturnValue(ci.getReturnValueF() * BlurHandler.INSTANCE.getSoundVolumeMultiplier());
    }

    @Inject(method = "loadLibrary", at = @At("RETURN"))
    private void modernui$restoreFocusGainAfterReload(CallbackInfo ci) {
        if (loaded) BlurHandler.INSTANCE.onSoundLibraryLoaded();
    }
}
