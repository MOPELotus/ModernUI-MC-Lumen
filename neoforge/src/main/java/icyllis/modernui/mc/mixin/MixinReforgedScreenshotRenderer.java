package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.VulkanScreenshotCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

/** F2 between frames must not read the previous, already presented swapchain image. */
@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Renderer", remap = false)
public abstract class MixinReforgedScreenshotRenderer implements VulkanScreenshotCapture {
    @Shadow private boolean recordingCmds;
    @Shadow public static boolean skipRendering;

    @Unique private final ArrayDeque<Request> modernui$screenshots = new ArrayDeque<>();
    @Unique private boolean modernui$screenshotImageAcquired;
    @Unique private boolean modernui$screenshotClosed;
    @Unique private boolean modernui$drainingScreenshots;
    @Unique private Request modernui$replayingScreenshot;
    @Unique private boolean modernui$replayEntered;

    // Observe acquisition only; Reforged already owns attachAcquireWait in flushCmds.
    @Redirect(method = "beginFrame()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"),
            require = 1, allow = 1)
    private int modernui$observeScreenshotAcquire(VkDevice device, long swapchain, long timeout,
                                                 long semaphore, long fence, IntBuffer imageIndex) {
        int result = KHRSwapchain.vkAcquireNextImageKHR(device, swapchain, timeout, semaphore, fence, imageIndex);
        modernui$screenshotAcquired(result);
        return result;
    }

    @Override
    public void modernui$screenshotAcquired(int result) {
        RenderSystem.assertOnRenderThread();
        // Observe the actual native result, not SwapChain's inferred acquired flag.
        modernui$screenshotImageAcquired = result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR;
    }

    @Override
    public boolean modernui$deferScreenshot(File directory, String filename, RenderTarget target, Consumer<Component> consumer) {
        RenderSystem.assertOnRenderThread();
        if (modernui$screenshotClosed) throw new IllegalStateException("Screenshot renderer is closed");
        Request replay = modernui$replayingScreenshot;
        if (replay != null && !modernui$replayEntered &&
                replay.directory() == directory && replay.filename() == filename &&
                replay.target() == target && replay.consumer() == consumer) {
            modernui$replayEntered = true;
            return false;
        }
        modernui$screenshots.addLast(new Request(Objects.requireNonNull(directory), filename, Objects.requireNonNull(target),
                Objects.requireNonNull(consumer)));
        return true;
    }

    @Inject(method = "endFrame()Z", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$captureBeforePresent(CallbackInfoReturnable<Boolean> ci) {
        RenderSystem.assertOnRenderThread();
        if (modernui$drainingScreenshots || modernui$screenshotClosed ||
                !recordingCmds || skipRendering || !modernui$screenshotImageAcquired) return;
        int count = modernui$screenshots.size();
        modernui$drainingScreenshots = true;
        try {
            // New requests made by a consumer wait for the next frame, preserving FIFO.
            for (int i = 0; i < count; i++) {
                if (modernui$screenshotClosed || modernui$screenshots.isEmpty() ||
                        !recordingCmds || !modernui$screenshotImageAcquired) break;
                Request request = modernui$screenshots.removeFirst();
                try {
                    if (request.target() != Minecraft.getInstance().getMainRenderTarget()) {
                        ModernUIMod.LOGGER.warn("Cancelled deferred Vulkan screenshot: main target is no longer usable");
                        continue;
                    }
                    modernui$replayingScreenshot = request;
                    modernui$replayEntered = false;
                    // Preserve directory, nullable filename, target and original chat consumer.
                    // Reforged flushCmds(Supplier) consumes its own acquire wait, then
                    // waits its fence, downloads and resumes the original rendering state.
                    Screenshot.grab(request.directory(), request.filename(), request.target(), request.consumer());
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

    @Inject(method = "submitFrame()Z", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I",
            shift = At.Shift.AFTER), require = 1, allow = 1)
    private void modernui$forgetPresentedImage(CallbackInfoReturnable<Boolean> ci) {
        modernui$screenshotImageAcquired = false;
    }

    @Inject(method = "destroySyncObjects()V", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$forgetDestroyedImage(CallbackInfo ci) {
        // A queued request holds no image or semaphore; next frame resolves its target afresh.
        modernui$screenshotImageAcquired = false;
    }

    @Inject(method = "endFrame()Z", at = @At("RETURN"), require = 1)
    private void modernui$forgetEndedFrame(CallbackInfoReturnable<Boolean> ci) {
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
