package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import icyllis.modernui.mc.VulkanPipelineCleanupCompat;
import org.lwjgl.vulkan.VK12;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge adds a third pipeline but currently omits it from destroy(). */
@Mixin(value = VulkanRenderPipeline.class, remap = false)
public abstract class MixinNeoVulkanRenderPipeline {
    @Shadow @Final private long withDepthStencilPipeline;
    @Shadow @Final private long withDepthPipeline;
    @Shadow @Final private long withoutDepthPipeline;
    @Shadow @Final private VulkanDevice device;

    @Inject(method = "destroy", at = @At("HEAD"), require = 1)
    private void modernui$destroyStencilPipeline(CallbackInfo ci) {
        if (VulkanPipelineCleanupCompat.ownsDistinctHandle(
                withDepthStencilPipeline, withDepthPipeline, withoutDepthPipeline)) {
            VK12.vkDestroyPipeline(device.vkDevice(), withDepthStencilPipeline, null);
        }
    }
}
