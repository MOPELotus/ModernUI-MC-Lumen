package icyllis.modernui.mc;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.function.Consumer;

/** Render-thread ownership for the legacy void screenshot entry point. */
public interface VulkanScreenshotCapture {
    void modernui$screenshotAcquired(int result);

    /** False permits only the exact original replay; true defers this request. */
    boolean modernui$deferScreenshot(File directory, String filename, RenderTarget target,
                                     Consumer<Component> consumer);

    record Request(File directory, String filename, RenderTarget target, Consumer<Component> consumer) {}
}
