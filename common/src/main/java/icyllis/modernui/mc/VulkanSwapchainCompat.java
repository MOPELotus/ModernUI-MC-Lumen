package icyllis.modernui.mc;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

/** Adds only surface-supported readback usage, preserving the backend's flags. */
public final class VulkanSwapchainCompat {
    private VulkanSwapchainCompat() {}

    public static int withSupportedCopySource(int requested, int queryResult, int supported) {
        if (queryResult != VK_SUCCESS) return requested;
        return requested | (supported & VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
    }
}
