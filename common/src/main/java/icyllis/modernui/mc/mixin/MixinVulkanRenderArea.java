package icyllis.modernui.mc.mixin;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.granite.DrawPass;
import icyllis.arc3d.granite.RecordingContext;
import icyllis.arc3d.granite.SurfaceDrawContext;
import icyllis.modernui.mc.VulkanRenderArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SurfaceDrawContext.class, remap = false)
public abstract class MixinVulkanRenderArea {
    @Shadow public abstract int getOrigin();
    @Shadow public abstract int getHeight();

    @Inject(method = "snapDrawPass(Licyllis/arc3d/granite/RecordingContext;)Licyllis/arc3d/granite/DrawPass;", at = @At("RETURN"), require = 1)
    private void modernui$normalizeRenderArea(RecordingContext context,
                                              CallbackInfoReturnable<DrawPass> ci) {
        DrawPass pass = ci.getReturnValue();
        if (pass != null) {
            // DrawPass owns this rectangle. Scissors have already been converted
            // separately; RenderPassTask forwards these bounds to Vulkan unchanged.
            VulkanRenderArea.normalize((Rect2i) pass.getBounds(), context.getBackend(), getOrigin(), getHeight());
        }
    }
}
