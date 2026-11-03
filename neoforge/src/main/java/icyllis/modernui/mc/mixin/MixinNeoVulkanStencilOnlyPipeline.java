package icyllis.modernui.mc.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import icyllis.modernui.mc.VulkanPipelineCleanupCompat;
import org.lwjgl.vulkan.VK12;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FML 12-era NeoForge adds a separate stencil-only pipeline as well. */
@Mixin(value = VulkanRenderPipeline.class, remap = false)
public abstract class MixinNeoVulkanStencilOnlyPipeline {
    @Shadow @Final private long withStencilPipeline;
    @Shadow @Final private long withDepthStencilPipeline;
    @Shadow @Final private long withDepthPipeline;
    @Shadow @Final private long withoutDepthPipeline;
    @Shadow @Final private VulkanDevice device;

    @Inject(method = "destroy", at = @At("HEAD"), require = 1)
    private void modernui$destroyStencilOnlyPipeline(CallbackInfo ci) {
        if (VulkanPipelineCleanupCompat.ownsDistinctStencilHandle(
                withStencilPipeline, withDepthStencilPipeline, withDepthPipeline, withoutDepthPipeline)) {
            VK12.vkDestroyPipeline(device.vkDevice(), withStencilPipeline, null);
        }
    }
}
