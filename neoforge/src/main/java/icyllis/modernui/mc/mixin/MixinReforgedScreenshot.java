package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.VulkanScreenshotRows;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** Reforged 0.3.0 leaves vanilla's OpenGL-oriented screenshot row conversion in place. */
@Mixin(Screenshot.class)
public abstract class MixinReforgedScreenshot {
    @ModifyVariable(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, index = 2, require = 1)
    private static Consumer<NativeImage> modernui$correctReforgedScreenshotRows(Consumer<NativeImage> consumer) {
        String version = ModList.get().getModContainerById("vulkanmod")
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse(null);
        if (!"0.3.0-ALPHA+1.21.11".equals(version)) {
            return consumer;
        }
        return VulkanScreenshotRows.wrap(consumer, version, RenderSystem.getDevice().getClass().getName());
    }
}
