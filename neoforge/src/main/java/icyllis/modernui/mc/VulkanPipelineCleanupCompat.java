package icyllis.modernui.mc;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Restrict the workaround to NeoForge's unfreed extra stencil pipelines. */
public final class VulkanPipelineCleanupCompat {
    private VulkanPipelineCleanupCompat() {}

    private static volatile boolean nativeOwnsStencilOnly;
    private static volatile String nativeOwnershipTarget;

    public static void configureNativeOwnership(ClassNode target) {
        nativeOwnsStencilOnly = target.fields.stream().anyMatch(f -> f.name.equals("withStencilPipeline") && f.desc.equals("J"))
                && !needsCleanup(target, "withStencilPipeline");
        nativeOwnershipTarget = target.name;
    }


    public static boolean needsCleanup(ClassNode target) {
        // The 26.3 hook also reads this peer to resolve aliases against a native
        // owner. Do not apply its shadow fields to an unsupported older shape.
        return target.fields.stream().anyMatch(f -> f.name.equals("withStencilPipeline") && f.desc.equals("J"))
                && needsCleanup(target, "withDepthStencilPipeline");
    }

    public static boolean needsCleanup(ClassNode target, String fieldName) {
        if (target.fields.stream().noneMatch(f -> f.name.equals(fieldName) && f.desc.equals("J"))) {
            return false;
        }
        var destroy = target.methods.stream().filter(m -> m.name.equals("destroy") && m.desc.equals("()V"))
                .findFirst().orElse(null);
        if (destroy == null) return false;
        // An upstream implementation that already handles this field must keep
        // sole ownership. Do not initialize or load the target to inspect it.
        for (var instruction : destroy.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(target.name)
                    && field.name.equals(fieldName) && field.getOpcode() == Opcodes.GETFIELD) return false;
            if (instruction instanceof MethodInsnNode call && call.owner.equals(target.name)
                    && call.name.equals(fieldName)) return false;
        }
        return true;
    }

    public static boolean ownsDistinctDepthStencilHandle(long depthStencil, long stencil, long depth, long plain) {
        if (nativeOwnershipTarget == null) {
            throw new IllegalStateException("NeoForge native pipeline ownership was not configured by its Mixin plugin");
        }
        return ownsDistinctDepthStencilHandle(depthStencil, stencil, depth, plain, nativeOwnsStencilOnly);
    }

    public static boolean ownsDistinctDepthStencilHandle(long depthStencil, long stencil, long depth, long plain,
                                                         boolean stencilOwnedByNative) {
        return ownsDistinctHandle(depthStencil, depth, plain) && (depthStencil != stencil || !stencilOwnedByNative);
    }

    public static boolean ownsDistinctStencilHandle(long stencil, long depthStencil, long depth, long plain) {
        // The depth/stencil variant (or upstream) owns any alias of its handle.
        return ownsDistinctHandle(stencil, depth, plain) && stencil != depthStencil;
    }

    public static boolean ownsDistinctHandle(long stencil, long depth, long plain) {
        return stencil != 0 && stencil != depth && stencil != plain;
    }
}
