package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanPipelineCleanupCompat;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.jar.JarFile;

/** Actual NeoForge classes plus fixed-upstream, absent-field and alias negative cases. */
public final class VulkanPipelineCleanupCheck {
    private static final String TARGET = "com/mojang/blaze3d/vulkan/VulkanRenderPipeline";

    public static void main(String[] args) throws Exception {
        try (var input = VulkanPipelineCleanupCheck.class.getResourceAsStream("/" + TARGET + ".class")) {
            require(input != null, "Actual NeoForge pipeline missing");
            check(new ClassReader(input), "compile/runtime classpath");
        }
        for (String jar : args) {
            try (var file = new JarFile(jar); var input = file.getInputStream(file.getJarEntry(TARGET + ".class"))) {
                check(new ClassReader(input), jar);
            }
        }
        require(!VulkanPipelineCleanupCompat.ownsDistinctHandle(0, 1, 2), "Null handle must not be destroyed");
        require(!VulkanPipelineCleanupCompat.ownsDistinctHandle(1, 1, 2), "Depth alias must not be destroyed twice");
        require(!VulkanPipelineCleanupCompat.ownsDistinctHandle(2, 1, 2), "Plain alias must not be destroyed twice");
        require(VulkanPipelineCleanupCompat.ownsDistinctHandle(3, 1, 2), "Extra owned stencil must be destroyed");
        var mixin = read("icyllis/modernui/mc/mixin/MixinNeoVulkanRenderPipeline");
        var hook = mixin.methods.stream().filter(m -> m.name.equals("modernui$destroyStencilPipeline")).findFirst().orElseThrow();
        var annotations = new java.util.ArrayList<AnnotationNode>();
        if (hook.invisibleAnnotations != null) annotations.addAll(hook.invisibleAnnotations);
        if (hook.visibleAnnotations != null) annotations.addAll(hook.visibleAnnotations);
        require(annotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
                        && a.values.toString().contains("destroy")), "Cleanup must inject into native destroy");
        require(hook.instructions.iterator().hasNext(), "Missing cleanup implementation");
        int destroys = 0, guards = 0;
        for (var instruction : hook.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("vkDestroyPipeline")) destroys++;
                if (call.name.equals("ownsDistinctHandle")) guards++;
            }
        }
        require(destroys == 1 && guards == 1, "Expected one guarded Vulkan pipeline destruction");
        System.out.println("NeoForge extra stencil cleanup: actual compile/runtime bytecode, future fixed/absent field and alias guards passed");
    }

    private static void check(ClassReader reader, String origin) {
        var node = new ClassNode();reader.accept(node, 0);
        require(VulkanPipelineCleanupCompat.needsCleanup(node), "Expected confirmed unfixed NeoForge pipeline: " + origin);
        var destroy = node.methods.stream().filter(m -> m.name.equals("destroy") && m.desc.equals("()V")).findFirst().orElseThrow();
        // Model a future upstream implementation that releases its own extra field.
        destroy.instructions.insert(new FieldInsnNode(Opcodes.GETFIELD, node.name, "withDepthStencilPipeline", "J"));
        require(!VulkanPipelineCleanupCompat.needsCleanup(node), "Fixed upstream must retain ownership: " + origin);
        destroy.instructions.remove(destroy.instructions.getFirst());
        destroy.instructions.insert(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name,
                "withDepthStencilPipeline", "()J", false));
        require(!VulkanPipelineCleanupCompat.needsCleanup(node), "Accessor-based upstream cleanup must retain ownership");
        destroy.instructions.remove(destroy.instructions.getFirst());
        node.fields.removeIf(f -> f.name.equals("withDepthStencilPipeline"));
        require(!VulkanPipelineCleanupCompat.needsCleanup(node), "Vanilla/absent field must not receive NeoForge workaround");
    }

    private static ClassNode read(String name) throws Exception {
        try (var input = VulkanPipelineCleanupCheck.class.getResourceAsStream("/" + name + ".class")) {
            require(input != null, "Missing compiled class " + name);
            var node = new ClassNode();new ClassReader(input).accept(node, 0);return node;
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
