package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanShutdownOrder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Vulkan", remap = false)
public abstract class MixinVulkanShutdownOrder {
    // VulkanMod's Minecraft hook cleans native resources BEFORE GpuDevice.close.
    // Close the Java backend after idle and before its original deferred drain.
    @Inject(method = "cleanUp()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I",
            shift = At.Shift.AFTER), require = 1, allow = 1)
    private static void modernui$closeJavaBackend(CallbackInfo ci) {
        VulkanShutdownOrder.closeBackend();
    }
}
