package icyllis.modernui.mc;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.render.engine.VkGpuDevice;

/** Finishes Java owners before VulkanMod drains deferred frees and destroys its device. */
public final class VulkanShutdownOrder {
    private VulkanShutdownOrder() {}

    public static void closeBackend() {
        if (RenderSystem.getDevice().backend instanceof VkGpuDevice backend) {
            backend.close();
        }
    }
}
