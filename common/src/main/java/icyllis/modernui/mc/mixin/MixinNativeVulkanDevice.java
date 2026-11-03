package icyllis.modernui.mc.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.VulkanManager;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.UIManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VulkanDevice.class, remap = false)
public abstract class MixinNativeVulkanDevice {
    // Normal shutdown already released Arc3D before closing the surface. Core
    // retains that context reference at refcount zero. Only a live context
    // needs this fallback, before Minecraft destroys its command encoder.
    @Inject(method = "close", at = @At("HEAD"))
    private void modernui$releaseSharedContext(CallbackInfo ci) {
        if (ModernUIMod.isVulkanBackend()) {
            var context = Core.peekImmediateContext();
            if (context != null) {
                if (context.getRefCntAcquire() > 0) {
                    UIManager.destroy();
                }
            } else {
                VulkanManager.get().close();
            }
        }
    }
}
