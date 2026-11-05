package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.VulkanScreenshotCapture;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.util.ScreenshotUtil", remap = false)
public abstract class MixinVulkanScreenshot {
    // Run before ScreenshotUtil caches a texture from the last-presented image index.
    @Inject(method = "takeScreenshot", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void modernui$deferMainScreenshot(RenderTarget target, int scale,
                                                    Consumer<NativeImage> consumer, CallbackInfo ci) {
        Renderer renderer = Renderer.getInstance();
        if (renderer == null || target != Minecraft.getInstance().getMainRenderTarget() ||
                renderer.getMainPass().getMainFramebuffer() != renderer.getSwapChain()) {
            return; // Offscreen targets keep their original immediate capture path.
        }
        RenderSystem.assertOnRenderThread();
        if (((VulkanScreenshotCapture) renderer).modernui$deferScreenshot(target, scale, consumer)) {
            ci.cancel();
        }
    }
}

