package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.VulkanScreenshotCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public abstract class MixinReforgedScreenshot {
    // The public grab already marshalled to the render thread; no NativeImage exists yet.
    @Inject(method = "_grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void modernui$deferMainScreenshot(File directory, String filename, RenderTarget target,
                                                    Consumer<Component> consumer, CallbackInfo ci) {
        Renderer renderer = Renderer.getInstance();
        if (renderer == null || target != Minecraft.getInstance().getMainRenderTarget()) {
            return; // Offscreen screenshots retain the original immediate path.
        }
        RenderSystem.assertOnRenderThread();
        if (((VulkanScreenshotCapture) renderer).modernui$deferScreenshot(directory, filename, target, consumer)) {
            ci.cancel();
        }
    }
}
