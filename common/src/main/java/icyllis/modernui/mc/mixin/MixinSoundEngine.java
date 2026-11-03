package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.resources.sounds.SoundInstance;
import java.util.Map;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the inactive-window multiplier to game channels on the older sound API. */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow protected abstract float calculateVolume(SoundInstance sound);

    // 1.21 stops channels at zero during category changes. A transient window fade must
    // retain those channels so they resume when the window becomes active again.
    @Inject(method = "updateCategoryVolume(Lnet/minecraft/sounds/SoundSource;F)V",
            at = @At("HEAD"), cancellable = true)
    private void modernui$refreshChannelVolumes(SoundSource source, float volume, CallbackInfo ci) {
        if (source != SoundSource.MASTER && BlurHandler.isRefreshingChannelVolumes()) {
            instanceToChannel.forEach((sound, handle) -> {
                float gain = calculateVolume(sound);
                handle.execute(channel -> channel.setVolume(gain));
            });
            ci.cancel();
        }
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"), cancellable = true)
    private void modernui$scaleChannelVolume(float volume, SoundSource source,
                                             CallbackInfoReturnable<Float> ci) {
        ci.setReturnValue(ci.getReturnValueF() * BlurHandler.INSTANCE.getChannelVolumeMultiplier());
    }
    @ModifyArg(method = {"updateCategoryVolume(Lnet/minecraft/sounds/SoundSource;F)V", "loadLibrary()V"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Listener;setGain(F)V"),
            index = 0, require = 2, allow = 2)
    private float modernui$scaleGlobalVolume(float gain) {
        return gain * BlurHandler.INSTANCE.getGlobalVolumeMultiplier();
    }
}
