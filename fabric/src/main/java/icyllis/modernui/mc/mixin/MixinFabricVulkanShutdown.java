package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.fabric.FabricVulkanShutdownCompat;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional, release-gated repair; the backend retains its allocator and device ownership. */
@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Vulkan", remap = false)
public abstract class MixinFabricVulkanShutdown {
    @Redirect(method = "cleanUp()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I"), require = 1)
    private static int modernui$afterDeviceIdle(VkDevice device) {
        int result = VK10.vkDeviceWaitIdle(device);
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException("Cannot retire VulkanMod owners before successful device idle: " + result);
        }
        FabricVulkanShutdownCompat.afterDeviceIdle();
        return result;
    }

    @Inject(method = "cleanUp()V", at = @At(value = "INVOKE",
            target = "Lnet/vulkanmod/vulkan/Vulkan;freeStagingBuffers()V"), require = 1)
    private static void modernui$beforeStagingFree(CallbackInfo ci) {
        FabricVulkanShutdownCompat.beforeStagingFree();
    }
}
