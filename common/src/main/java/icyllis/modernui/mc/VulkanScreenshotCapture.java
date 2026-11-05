package icyllis.modernui.mc;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import java.util.function.Consumer;

/** Render-thread screenshot ownership supplied only by the Fabric VulkanMod mixin. */
public interface VulkanScreenshotCapture {
    void modernui$screenshotAcquired(int result);

    /** True defers this request; false permits exactly the original replay invocation. */
    boolean modernui$deferScreenshot(RenderTarget target, int scale, Consumer<NativeImage> consumer);

    record Request(RenderTarget target, int scale, Consumer<NativeImage> consumer) {}
}

