package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the inactive-window multiplier to game channels without changing the vanilla category map. */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Shadow private boolean loaded;

    @Unique
    private void modernui$updateListenerGain() {
        if (loaded) {
            AL10.alListenerf(AL10.AL_GAIN, BlurHandler.INSTANCE.getListenerVolumeMultiplier());
        }
    }

    @Inject(method = "refreshCategoryVolume(Lnet/minecraft/sounds/SoundSource;)V", at = @At("HEAD"))
    private void modernui$refreshListenerGain(SoundSource source, CallbackInfo ci) {
        modernui$updateListenerGain();
    }

    @Inject(method = "loadLibrary()V", at = @At("RETURN"))
    private void modernui$restoreListenerGain(CallbackInfo ci) {
        modernui$updateListenerGain();
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"), cancellable = true)
    private void modernui$scaleChannelVolume(float volume, SoundSource source,
                                             CallbackInfoReturnable<Float> ci) {
        ci.setReturnValue(ci.getReturnValueF() * BlurHandler.INSTANCE.getChannelVolumeMultiplier());
    }
}
