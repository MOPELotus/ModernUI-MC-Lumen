package icyllis.modernui.mc.mixin;

import icyllis.arc3d.opengl.GLDevice;
import icyllis.arc3d.opengl.GLTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Check on the device thread: releasing a texture can overtake a queued label update. */
@Mixin(value = GLTexture.class, remap = false)
public abstract class MixinGLTexture {
    @Inject(method = "lambda$onSetLabel$0(Ljava/lang/String;Licyllis/arc3d/opengl/GLDevice;)V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void modernui$skipReleasedLabel(String label, GLDevice device, CallbackInfo ci) {
        GLTexture texture = (GLTexture) (Object) this;
        if (texture.isDestroyed() || texture.getHandle() == 0) ci.cancel();
    }
}
