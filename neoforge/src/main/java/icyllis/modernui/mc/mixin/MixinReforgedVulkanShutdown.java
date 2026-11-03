package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.neoforge.ReforgedShutdownCompat;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retire the audited Reforged owners before their allocator and device disappear. */
@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Vulkan", remap = false)
public abstract class MixinReforgedVulkanShutdown {
    @Redirect(method = "cleanUp()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I"), require = 1)
    private static int waitForDeviceIdle(VkDevice device) {
        int result = VK10.vkDeviceWaitIdle(device);
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException("Cannot retire Reforged resources before successful device idle: " + result);
        }
        ReforgedShutdownCompat.afterDeviceIdle();
        return result;
    }

    @Inject(method = "cleanUp()V", at = @At(value = "INVOKE",
            target = "Lnet/vulkanmod/vulkan/memory/MemoryManager;cleanUp()V", shift = At.Shift.AFTER), require = 1)
    private static void afterMemoryCleanup(CallbackInfo ci) {
        ReforgedShutdownCompat.afterMemoryCleanup();
    }
}
