package icyllis.modernui.mc.mixin;

import icyllis.arc3d.vulkan.VKUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

import static org.lwjgl.vulkan.VK10.*;

/** Present waits belong to acquired swapchain images, not rotating submit frames. */
@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Renderer", remap = false)
public abstract class MixinReforgedPresentSemaphores {
    @Shadow private ArrayList<Long> renderFinishedSemaphores;
    @Shadow private int imagesNum;
    @Shadow private static int imageIndex;
    @Shadow private static VkDevice device;

    @Shadow
    private void destroySyncObjects() { throw new AssertionError(); }

    // Reforged calls this after initial setup and after idle/destroy on recreation.
    // Keep the native renderer's existing list as the sole destruction owner.
    @Inject(method = "createSyncObjects()V", at = @At("TAIL"), require = 1, allow = 1)
    private void modernui$sizePresentSemaphores(CallbackInfo ci) {
        try (var stack = MemoryStack.stackPush()) {
            if (imagesNum <= 0) throw new IllegalStateException("Invalid swapchain image count: " + imagesNum);
            renderFinishedSemaphores.ensureCapacity(imagesNum);
            var info = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            var result = stack.mallocLong(1);
            while (renderFinishedSemaphores.size() < imagesNum) {
                VKUtil._CHECK_(vkCreateSemaphore(device, info, null, result));
                long semaphore = result.get(0);
                if (semaphore == VK_NULL_HANDLE) throw new IllegalStateException("Created null present semaphore");
                renderFinishedSemaphores.add(semaphore);
            }
            while (renderFinishedSemaphores.size() > imagesNum) {
                int last = renderFinishedSemaphores.size() - 1;
                vkDestroySemaphore(device, renderFinishedSemaphores.get(last), null);
                renderFinishedSemaphores.remove(last);
            }
        } catch (RuntimeException | Error failure) {
            // All handles created here were published immediately to the native owner.
            // This covers extension failure after original createSyncObjects succeeded.
            try { destroySyncObjects(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    @ModifyArg(method = "submitFrame()Z", index = 0, require = 1, allow = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;get(I)Ljava/lang/Object;"),
            slice = @Slice(from = @At(value = "FIELD", ordinal = 0,
                    target = "Lnet/vulkanmod/vulkan/Renderer;renderFinishedSemaphores:Ljava/util/ArrayList;"),
                    to = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkSubmitInfo;pSignalSemaphores(Ljava/nio/LongBuffer;)Lorg/lwjgl/vulkan/VkSubmitInfo;")))
    private int modernui$signalImageIndex(int frameIndex) {
        return imageIndex;
    }

    @ModifyArg(method = "submitFrame()Z", index = 0, require = 1, allow = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;get(I)Ljava/lang/Object;"),
            slice = @Slice(from = @At(value = "FIELD", ordinal = 1,
                    target = "Lnet/vulkanmod/vulkan/Renderer;renderFinishedSemaphores:Ljava/util/ArrayList;"),
                    to = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkPresentInfoKHR;pWaitSemaphores(Ljava/nio/LongBuffer;)Lorg/lwjgl/vulkan/VkPresentInfoKHR;")))
    private int modernui$presentImageIndex(int frameIndex) {
        return imageIndex;
    }
}
