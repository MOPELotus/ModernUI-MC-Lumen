package icyllis.modernui.mc;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Restrict the workaround to NeoForge's unfreed extra stencil pipelines. */
public final class VulkanPipelineCleanupCompat {
    private VulkanPipelineCleanupCompat() {}

    public static boolean needsCleanup(ClassNode target) {
        return needsCleanup(target, "withDepthStencilPipeline");
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

    public static boolean ownsDistinctStencilHandle(long stencil, long depthStencil, long depth, long plain) {
        // The depth/stencil variant (or upstream) owns any alias of its handle.
        return ownsDistinctHandle(stencil, depth, plain) && stencil != depthStencil;
    }

    public static boolean ownsDistinctHandle(long stencil, long depth, long plain) {
        return stencil != 0 && stencil != depth && stencil != plain;
    }
}
