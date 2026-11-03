package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.BackgroundAudioControl;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngineAudio {
    @Shadow private boolean loaded;

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void modernui$backgroundVolume(float volume, SoundSource source,
                                           CallbackInfoReturnable<Float> cir) {
        // Vanilla already includes the user's MASTER and source sliders here.
        cir.setReturnValue(BackgroundAudioControl.gameVolume(cir.getReturnValueF()));
    }

    @Inject(method = "refreshCategoryVolume(Lnet/minecraft/sounds/SoundSource;)V",
            at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$updateListenerGain(SoundSource source, CallbackInfo ci) {
        if (loaded) {
            AL10.alListenerf(AL10.AL_GAIN, BackgroundAudioControl.listenerGain());
        }
    }

    // NeoForge merges vanilla's two returns; the successful loaded assignment is unique in both.
    @Inject(method = "loadLibrary()V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/sounds/SoundEngine;loaded:Z",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 1, allow = 1)
    private void modernui$restoreListenerGain(CallbackInfo ci) {
        if (loaded) {
            AL10.alListenerf(AL10.AL_GAIN, BackgroundAudioControl.listenerGain());
        }
    }
}
