package icyllis.modernui.mc;

import icyllis.modernui.mc.mixin.MixinSpanSet;
import icyllis.modernui.mc.mixin.MixinGLTexture;
import icyllis.arc3d.opengl.GLTexture;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Executes production patch bodies without a driver or Minecraft runtime. */
public final class CoreBoundaryCheck {
    static class Set extends MixinSpanSet {}
    static int assertions;
    static void check(boolean value) { if (!value) throw new AssertionError(); assertions++; }
    public static void main(String[] args) throws Exception {
        Set set = new Set();
        Method grow = MixinSpanSet.class.getDeclaredMethod("grow", int.class);grow.setAccessible(true);
        grow.invoke(set, 1);check(set.spanStarts.length == 10);
        int[] starts = set.spanStarts, ends = set.spanEnds, flags = set.spanFlags;
        grow.invoke(set, 10);check(starts == set.spanStarts && ends == set.spanEnds && flags == set.spanFlags);
        for (int i=0;i<64;i++) {
            grow.invoke(set, i+1);set.spanStarts[i]=i*3;set.spanEnds[i]=i*3+2;set.spanFlags[i]=0x21+i;
            for(int j=0;j<=i;j++) check(set.spanStarts[j]==j*3 && set.spanEnds[j]==j*3+2 && set.spanFlags[j]==0x21+j);
        }
        Method label = MixinGLTexture.class.getDeclaredMethod("modernui$skipReleasedLabel", String.class, icyllis.arc3d.opengl.GLDevice.class, CallbackInfo.class);label.setAccessible(true);
        GLTexture texture = new GLTexture();
        CallbackInfo live = new CallbackInfo();label.invoke(texture,"live",null,live);check(!live.cancelled);
        ArrayList<Runnable> queue = new ArrayList<>();
        CallbackInfo released = new CallbackInfo();
        queue.add(() -> {try {label.invoke(texture,"queued",null,released);}catch(Exception e){throw new AssertionError(e);}});
        texture.destroyed=true;texture.handle=0;queue.forEach(Runnable::run);check(released.cancelled);
        texture.destroyed=false;CallbackInfo zero=new CallbackInfo();label.invoke(texture,"zero",null,zero);check(zero.cancelled);
        texture.destroyed=true;texture.handle=17;CallbackInfo stale=new CallbackInfo();label.invoke(texture,"stale",null,stale);check(stale.cancelled);
        System.out.println("PASS production patch bodies: " + assertions + " assertions");
    }
}
