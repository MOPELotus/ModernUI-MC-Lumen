package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.VulkanSwapchainCompat;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.LongBuffer;

/** VulkanMod's F2 path reads the current swapchain color attachment. */
@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.framebuffer.SwapChain", remap = false)
public abstract class MixinVulkanSwapChain {
    @Redirect(method = "createSwapChain()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkCreateSwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),
            require = 1, allow = 1)
    private int modernui$enableSupportedScreenshotReadback(VkDevice device,
                                                          VkSwapchainCreateInfoKHR info,
                                                          VkAllocationCallbacks allocator,
                                                          LongBuffer swapchain) {
        int requested = info.imageUsage();
        // Already-fixed backends keep their own policy and perform no extra query.
        if ((requested & VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
                int result = KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                        device.getPhysicalDevice(), info.surface(), capabilities);
                if (result != VK10.VK_SUCCESS) {
                    ModernUIMod.LOGGER.warn("Cannot query VulkanMod screenshot surface usage (VkResult {}); swapchain usage unchanged", result);
                } else {
                    int supported = capabilities.supportedUsageFlags();
                    info.imageUsage(VulkanSwapchainCompat.withSupportedCopySource(requested, result, supported));
                    if ((supported & VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0) {
                        ModernUIMod.LOGGER.warn("VulkanMod surface does not support screenshot readback; swapchain usage unchanged");
                    }
                }
            }
        }
        return KHRSwapchain.vkCreateSwapchainKHR(device, info, allocator, swapchain);
    }
}
