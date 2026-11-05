package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.VulkanScreenshotCapture;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.util.ScreenshotUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Renderer", remap = false)
public abstract class MixinVulkanScreenshotRenderer implements VulkanScreenshotCapture {
    @Shadow private boolean recordingCmds;
    @Shadow int recursion;

    @Unique private final ArrayDeque<Request> modernui$screenshots = new ArrayDeque<>();
    @Unique private boolean modernui$screenshotImageAcquired;
    @Unique private boolean modernui$screenshotClosed;
    @Unique private boolean modernui$drainingScreenshots;
    @Unique private Request modernui$replayingScreenshot;
    @Unique private boolean modernui$replayEntered;

    @Override
    public void modernui$screenshotAcquired(int result) {
        RenderSystem.assertOnRenderThread();
        // Observe the actual native result, not SwapChain's inferred acquired flag.
        modernui$screenshotImageAcquired = result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR;
    }

    @Override
    public boolean modernui$deferScreenshot(RenderTarget target, int scale, Consumer<NativeImage> consumer) {
        RenderSystem.assertOnRenderThread();
        if (modernui$screenshotClosed) throw new IllegalStateException("Screenshot renderer is closed");
        Request replay = modernui$replayingScreenshot;
        if (replay != null && !modernui$replayEntered &&
                replay.target() == target && replay.scale() == scale && replay.consumer() == consumer) {
            modernui$replayEntered = true;
            return false;
        }
        modernui$screenshots.addLast(new Request(Objects.requireNonNull(target), scale,
                Objects.requireNonNull(consumer)));
        return true;
    }

    @Inject(method = "endFrame()V", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$captureBeforePresent(CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        if (modernui$drainingScreenshots || modernui$screenshotClosed ||
                !recordingCmds || recursion != 1 || !modernui$screenshotImageAcquired) return;
        Renderer renderer = (Renderer) (Object) this;
        int count = modernui$screenshots.size();
        modernui$drainingScreenshots = true;
        try {
            // New requests made by a consumer wait for the next frame, preserving FIFO.
            for (int i = 0; i < count; i++) {
                if (modernui$screenshotClosed || modernui$screenshots.isEmpty() ||
                        !recordingCmds || !modernui$screenshotImageAcquired) break;
                Request request = modernui$screenshots.removeFirst();
                try {
                    if (request.target() != Minecraft.getInstance().getMainRenderTarget() ||
                            renderer.getMainPass().getMainFramebuffer() != renderer.getSwapChain() ||
                            !renderer.getSwapChain().hasImages()) {
                        ModernUIMod.LOGGER.warn("Cancelled deferred Vulkan screenshot: main target is no longer usable");
                        continue;
                    }
                    modernui$replayingScreenshot = request;
                    modernui$replayEntered = false;
                    // Original target/scale/consumer; only now does the util fetch its texture.
                    // Its original flush consumes the acquire signal before the standalone copy.
                    ScreenshotUtil.takeScreenshot(request.target(), request.scale(), request.consumer());
                } catch (RuntimeException failure) {
                    // The original caller's catch no longer surrounds a deferred capture.
                    // Report this request's failure without taking down frame presentation.
                    ModernUIMod.LOGGER.warn("Failed deferred Vulkan screenshot", failure);
                } finally {
                    modernui$replayingScreenshot = null;
                    modernui$replayEntered = false;
                }
            }
        } catch (Error failure) {
            modernui$cancelScreenshots("fatal capture failure");
            throw failure;
        } finally {
            modernui$drainingScreenshots = false;
        }
    }

    @Inject(method = "submitFrame()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I",
            shift = At.Shift.AFTER), require = 1, allow = 1)
    private void modernui$forgetPresentedImage(CallbackInfo ci) {
        modernui$screenshotImageAcquired = false;
    }

    @Inject(method = "destroySyncObjects()V", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$forgetDestroyedImage(CallbackInfo ci) {
        // A queued request holds no image or semaphore; next frame resolves its target afresh.
        modernui$screenshotImageAcquired = false;
    }

    @Inject(method = "endFrame()V", at = @At("RETURN"), require = 1)
    private void modernui$forgetEndedFrame(CallbackInfo ci) {
        if (!recordingCmds) modernui$screenshotImageAcquired = false;
    }

    @Inject(method = "cleanUpResources()V", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$cancelOnClose(CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        modernui$screenshotClosed = true;
        modernui$screenshotImageAcquired = false;
        modernui$cancelScreenshots("renderer closed before an acquired frame");
        modernui$replayingScreenshot = null;
    }

    @Unique
    private void modernui$cancelScreenshots(String reason) {
        int count = modernui$screenshots.size();
        modernui$screenshots.clear();
        if (count != 0) ModernUIMod.LOGGER.warn("Cancelled {} deferred Vulkan screenshots: {}", count, reason);
    }

}

