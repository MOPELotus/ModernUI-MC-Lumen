package icyllis.modernui.mc;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.function.Consumer;

/** Screenshot-only row correction for the known NeoForge Reforged readback API. */
public final class VulkanScreenshotRows {
    private VulkanScreenshotRows() {}

    public static Consumer<NativeImage> wrap(Consumer<NativeImage> consumer,
                                           String modVersion, String deviceClass) {
        if (consumer == null || !"0.3.0-ALPHA+1.21.11".equals(modVersion) ||
                !"net.vulkanmod.render.engine.VkGpuDevice".equals(deviceClass)) {
            return consumer;
        }
        return image -> {
            // Vanilla writes readback rows bottom-up; this Reforged backend returns top-down rows.
            // Correct only the completed screenshot, leaving every other texture readback alone.
            int width = image.getWidth();
            int height = image.getHeight();
            for (int y = 0; y < height / 2; y++) {
                int opposite = height - 1 - y;
                for (int x = 0; x < width; x++) {
                    int top = image.getPixel(x, y);
                    int bottom = image.getPixel(x, opposite);
                    image.setPixel(x, y, bottom);
                    image.setPixel(x, opposite, top);
                }
            }
            consumer.accept(image);
        };
    }
}
