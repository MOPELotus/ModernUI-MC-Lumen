package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Checks actual NeoForge event parameters and the non-destructive visibility invalidation path. */
public final class ZoomVisibilityCheck {
    public static void main(String[] args) throws Exception {
        var ui = read("icyllis/modernui/mc/neoforge/UIManagerForge");
        var helper = ui.methods.stream().filter(m -> m.name.equals("invalidateLevelGeometry"))
                .findFirst().orElseThrow();
        require(calls(helper, "net/minecraft/client/renderer/LevelRenderer", "sectionOcclusionGraph") == 1,
                "Zoom must invalidate the current visibility graph");
        require(calls(helper, "net/minecraft/client/renderer/SectionOcclusionGraph", "invalidate") == 1,
                "Zoom visibility update is disconnected");
        require(calls(helper, "net/minecraft/client/renderer/LevelRenderer", "cloudRenderer") == 1
                        && calls(helper, "net/minecraft/client/renderer/CloudRenderer", "markForRebuild") == 1,
                "Zoom must refresh clouds as the original needsUpdate contract did");
        require(calls(helper, "net/minecraft/client/renderer/LevelRenderer", "invalidateCompiledGeometry") == 0,
                "Zoom must never release compiled terrain buffers");
        var handler = ui.methods.stream().filter(m -> m.name.equals("onChangeFov")).findFirst().orElseThrow();
        require(calls(handler, ui.name, "invalidateLevelGeometry") == 2,
                "Refresh belongs to zoom press/release transitions");
        var graph = read("net/minecraft/client/renderer/SectionOcclusionGraph");
        var invalidate = graph.methods.stream().filter(m -> m.name.equals("invalidate") && m.desc.equals("()V"))
                .findFirst().orElseThrow();
        boolean marksDirty = false;
        for (var instruction : invalidate.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.desc.equals("Z")) marksDirty = true;
            if (instruction instanceof MethodInsnNode call &&
                    (call.name.equals("releaseAllBuffers") || call.name.equals("clearCompileQueue"))) {
                throw new AssertionError("Actual visibility invalidation must not destroy terrain buffers");
            }
        }
        require(marksDirty, "Actual graph invalidation no longer marks visibility dirty");
        var event = read("net/neoforged/neoforge/client/event/ViewportEvent$ComputeFov");
        var constructor = event.methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals(
                "(Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/Camera;FF)V"))
                .findFirst().orElseThrow(() -> new AssertionError("NeoForge FOV event parameter types changed"));
        boolean forwardsFov = false;
        for (var instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("setFOV")) {
                require(call.desc.equals("(F)V") && instruction.getPrevious() instanceof VarInsnNode value
                                && value.getOpcode() == Opcodes.FLOAD && value.var == 4,
                        "NeoForge FOV value must be fourth constructor parameter, after partial ticks");
                forwardsFov = true;
            }
        }
        require(forwardsFov, "NeoForge event constructor no longer forwards the FOV value");
        System.out.println("Actual NeoForge FOV parameter order and non-destructive visibility/cloud refresh verified");
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = ZoomVisibilityCheck.class.getResourceAsStream("/" + name + ".class")) {
            require(stream != null, "Missing actual runtime class " + name);
            var result = new ClassNode();
            new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static int calls(MethodNode method, String owner, String name) {
        int result = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result++;
        }
        return result;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
