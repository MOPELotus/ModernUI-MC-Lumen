package icyllis.modernui.mc.mixin;

import org.lwjgl.vulkan.VkPhysicalDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** VulkanMod 26.1 carries the queried physical handle inside its Device wrapper. */
@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.device.Device", remap = false)
public interface AccessVulkanDevice {
    @Accessor("physicalDevice")
    VkPhysicalDevice modernui$getPhysicalDevice();
}
