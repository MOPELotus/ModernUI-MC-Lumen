package icyllis.modernui.mc.neoforge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** CPU ABI audit against the exact renderer used by the native F2 reproduction. */
public final class ReforgedScreenshotCompatCheck {
    private static final String RENDERER = "net/vulkanmod/vulkan/Renderer";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinReforgedScreenshotRenderer";
    private static final String GRAB = "(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V";
    private static int checks;

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "supply audited Reforged JAR");
        try (var jar = new JarFile(Path.of(args[0]).toFile())) {
            ClassNode renderer = upstream(jar, RENDERER);
            MethodNode begin = method(renderer, "beginFrame", "()V");
            MethodNode end = method(renderer, "endFrame", "()Z");
            MethodNode submit = method(renderer, "submitFrame", "()Z");
            MethodNode flush = method(renderer, "flushCmds", "(Ljava/util/function/Supplier;)Ljava/lang/Object;");
            require(calls(begin, "vkAcquireNextImageKHR") == 1, "unique actual acquire observation site");
            require(callIndex(end, "end") < callIndex(end, "submitFrame"), "HEAD capture precedes MainPass.end and submit/present");
            require(calls(submit, "vkQueuePresentKHR") == 1, "unique actual present invalidation site");
            require(callIndex(flush, "attachAcquireWait") < callIndex(flush, "vkQueueSubmit") &&
                    callIndex(flush, "vkQueueSubmit") < callIndex(flush, "vkWaitForFences") &&
                    callIndex(flush, "vkWaitForFences") < lastCallIndex(flush, "get") &&
                    lastCallIndex(flush, "get") < callIndex(flush, "resume"),
                    "native flush already orders acquire wait, submit, fence, download, state resume");
            long readbacks = 0;
            for (var insn : flush.instructions) {
                if (insn instanceof MethodInsnNode m && m.owner.equals("java/util/function/Supplier") && m.name.equals("get")) readbacks++;
            }
            require(readbacks == 2, "native non-recording fast path directly runs readback (original F2 trigger)");
            method(renderer, "destroySyncObjects", "()V");
            method(renderer, "cleanUpResources", "()V");
            MethodNode capture = method(upstream(jar, "net/vulkanmod/mixin/util/ScreenshotRecorderM"),
                    "takeScreenshot", "(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;");
            require(calls(capture, "flushCmds") == 1, "actual overwrite retains native readback synchronization");
        }
        ClassNode mixin = product(MIXIN);
        for (String selector : List.of("endFrame()Z", "submitFrame()Z", "destroySyncObjects()V", "cleanUpResources()V")) {
            require(mixin.methods.stream().anyMatch(m -> annotationContains(m, selector)), "exact injection ABI " + selector);
        }
        MethodNode observer = method(mixin, "modernui$observeScreenshotAcquire",
                "(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I");
        require(calls(observer, "vkAcquireNextImageKHR") == 1 && calls(observer, "modernui$screenshotAcquired") == 1,
                "acquire observer calls native exactly once and records its result");
        require(mixin.methods.stream().noneMatch(m -> calls(m, "vkQueueSubmit") != 0 || calls(m, "attachAcquireWait") != 0),
                "screenshot deferral does not replace Reforged synchronization");
        MethodNode drain = mixin.methods.stream().filter(m -> m.name.equals("modernui$captureBeforePresent")).findFirst().orElseThrow();
        require(drain.desc.equals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"),
                "boolean endFrame uses returnable callback");
        require(calls(drain, "grab") == 1, "drain replays original public screenshot entry exactly once per request");
        for (var insn : drain.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("grab")) {
                require(call.owner.equals("net/minecraft/client/Screenshot") && call.desc.equals(GRAB),
                        "replay preserves nullable filename overload and Consumer parameter");
            }
        }
        ClassNode entry = product("icyllis/modernui/mc/mixin/MixinReforgedScreenshot");
        MethodNode hook = entry.methods.stream().filter(m -> m.name.equals("modernui$deferMainScreenshot")).findFirst().orElseThrow();
        require(annotationContains(hook, "_grab" + GRAB), "render-thread void entry, before NativeImage allocation");
        for (var insn : hook.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("modernui$deferScreenshot")) {
                require(call.desc.equals("(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)Z"),
                        "queue receives exact public screenshot arguments");
                var load = call.getPrevious();
                for (int slot = 3; slot >= 0; slot--, load = load.getPrevious()) {
                    require(load instanceof VarInsnNode v && v.getOpcode() == org.objectweb.asm.Opcodes.ALOAD && v.var == slot,
                            "File / nullable filename / target / Consumer original local slot " + slot);
                }
            }
        }
        System.out.println("PASS: Reforged screenshot " + checks + " ABI/compiled-argument checks; CPU only, GPU F2 still required");
    }

    private static boolean annotationContains(MethodNode method, String selector) {
        return (method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> valuesContain(a.values, selector))) ||
                (method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(a -> valuesContain(a.values, selector)));
    }

    private static boolean valuesContain(List<?> values, String selector) {
        return values != null && values.stream().anyMatch(v -> selector.equals(v) || (v instanceof List<?> list && valuesContain(list, selector)));
    }

    private static ClassNode upstream(JarFile jar, String name) throws Exception {
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }

    private static ClassNode product(String name) throws Exception {
        try (var stream = ReforgedScreenshotCompatCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow();
    }

    private static long calls(MethodNode method, String name) {
        long count = 0;
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode m && m.name.equals(name)) count++;
        return count;
    }

    private static int callIndex(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode m && m.name.equals(name)) return i;
        }
        throw new AssertionError("Missing call " + name);
    }

    private static int lastCallIndex(MethodNode method, String name) {
        for (int i = method.instructions.size() - 1; i >= 0; i--) {
            if (method.instructions.get(i) instanceof MethodInsnNode m && m.name.equals(name)) return i;
        }
        throw new AssertionError("Missing call " + name);
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        checks++;
    }
}
