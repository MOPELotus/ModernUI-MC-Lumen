package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.neoforge.UIManagerForge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Complete Vulkan UI work only inside Reforged's acquired-frame render call. */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererEXT {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void onRenderFrameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        UIManagerForge.onVulkanRenderFrameEnd();
    }
}
