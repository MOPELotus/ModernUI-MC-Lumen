package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Executes real Neo frame hooks against CPU frame ownership boundaries; never starts Minecraft or a GPU. */
public final class VulkanFrameLifecycleCheck {
    private static final String MANAGER = "icyllis/modernui/mc/neoforge/UIManagerForge";
    private static final String COMMON = "icyllis/modernui/mc/UIManager";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinGameRendererEXT";
    private static final String BRIDGE = "icyllis/modernui/mc/VulkanModIntegration";
    private static final String GENERATED = "icyllis/modernui/mc/test/VulkanFrameLifecycleExecuted";
    private static int checks, endCalls, consumes, outsideAcquired;
    private static boolean acquired, queued, recordingFailure, baseFailure;
    private static int bridgeCalls;

    public static void main(String[] args) throws Exception {
        ClassNode manager = read(MANAGER, null), mixin = read(MIXIN, null);
        checkInjection(mixin);
        checkFallbackGuards(read(COMMON, null));
        var run = new Executed(manager, mixin);
        reset(true);
        frame(run, true, false);
        require(endCalls == 1 && consumes == 1 && outsideAcquired == 0, "active VK frame ends exactly once inside acquired renderer");
        reset(true);
        frame(run, false, false);
        require(endCalls == 0 && consumes == 0 && queued, "not-recording frame Post preserves pending Canvas work");
        frame(run, true, false);
        require(endCalls == 1 && consumes == 1 && !queued && outsideAcquired == 0,
                "first following acquired frame consumes preserved work exactly once");
        reset(true);
        frame(run, true, true);
        require(endCalls == 0 && consumes == 0 && queued, "skipRendering Post cannot consume Canvas work");
        frame(run, true, false);
        require(endCalls == 1 && consumes == 1 && outsideAcquired == 0, "skipRendering recovery consumes once");
        reset(true);
        frame(run, false, true);
        frame(run, false, true);
        require(endCalls == 0 && queued, "repeated skipped Forge Posts never run VK fallback");
        reset(true);
        frame(run, true, false, false, false);
        require(endCalls == 0 && queued, "entry recording does not authorize consumption after nested rendering ends recording");
        frame(run, true, false);
        require(endCalls == 1 && consumes == 1 && outsideAcquired == 0, "next active frame consumes work preserved at inactive RETURN once");
        reset(true);
        frame(run, true, false, true, true);
        require(endCalls == 0 && queued, "entry recording does not authorize consumption when RETURN becomes skipRendering");
        frame(run, true, false);
        require(endCalls == 1 && consumes == 1 && outsideAcquired == 0, "next active frame consumes work preserved at skipped RETURN once");
        reset(false);
        frame(run, true, false);
        require(endCalls == 1 && consumes == 1, "GL retains one Forge Post callback despite renderer RETURN hook");
        reset(false);
        frame(run, false, true);
        require(endCalls == 1 && consumes == 1, "GL retains existing Post behavior when GameRenderer is skipped");
        reset(false);
        run.rendererReturn();
        require(endCalls == 0, "VK helper is inert on GL even when called directly");
        reset(true);
        run.post();
        require(endCalls == 0, "VK Post alone never ends UI rendering");
        reset(false);
        run.rendererReturn();
        require(bridgeCalls == 0, "GL helper short-circuits before querying the Vulkan bridge");
        reset(true);
        recordingFailure = true;
        expectFailure(run, "recording failed");
        require(endCalls == 0 && queued, "real reflective isRecording failure propagates without consuming Canvas");
        reset(true);
        ExactRenderer.recording = acquired = true;
        baseFailure = true;
        expectFailure(run, "base failed");
        require(endCalls == 1 && consumes == 0 && queued, "active base callback failure propagates without consumption");
        if (args.length > 0) {
            Path jar = Path.of(args[0]);
            var legacy = new Executed(read(MANAGER, jar), read(MIXIN, jar));
            reset(true);
            frame(legacy, true, false, false, false);
            require(endCalls == 1 && consumes == 1 && outsideAcquired == 1,
                    "old real RETURN helper consumes queued Canvas after nested rendering made the Vulkan frame inactive");
            reset(true);
            frame(legacy, true, false, true, true);
            require(endCalls == 1 && consumes == 1 && outsideAcquired == 1,
                    "old real RETURN helper also consumes when skipRendering changes during rendering");
            System.out.println("Old-JAR inactive-RETURN negative reproduced: " + jar + " sha256=" +
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(jar))));
        } else System.out.println("Old-JAR negative control not supplied; pass frozen 2984 JAR as argument 0.");
        System.out.println("Vulkan frame lifecycle: " + checks + " real-hook CPU and real-base structural checks passed; no GPU initialized");
    }

    /** Models VM entry gating and a possible nested render changing live state before RETURN. */
    private static void frame(Executed run, boolean recording, boolean skipRendering) throws Exception {
        frame(run, recording, skipRendering, recording, skipRendering);
    }
    private static void frame(Executed run, boolean entryRecording, boolean entrySkip,
                              boolean returnRecording, boolean returnSkip) throws Exception {
        ExactRenderer.recording = entryRecording;
        ExactRenderer.skipRendering = entrySkip;
        if (entryRecording && !entrySkip) {
            ExactRenderer.recording = returnRecording;
            ExactRenderer.skipRendering = returnSkip;
            acquired = returnRecording && !returnSkip;
            try { run.rendererReturn(); } finally { acquired = false; }
        }
        run.post();
    }
    public static final class BridgeApi { public static final Class<?> RENDERER = ExactRenderer.class; }
    public static final class ExactRenderer {
        public static boolean recording, skipRendering;
        public static boolean isRecording() {
            bridgeCalls++;
            if (recordingFailure) throw new IllegalStateException("recording failed");
            return recording;
        }
    }
    private static void reset(boolean vulkan) {
        Flags.vulkan = vulkan; acquired = false; queued = true;
        ExactRenderer.recording = ExactRenderer.skipRendering = false;
        endCalls = consumes = outsideAcquired = bridgeCalls = 0;
        recordingFailure = baseFailure = false;
    }
    public static final class Flags { public static boolean vulkan; public static boolean isVulkanModLoaded() { return vulkan; } }
    public static class Base {
        public static Base instance;
        public static Base getInstance() { return instance; }
        public void onRenderTick(boolean end) {
            if (!end) throw new AssertionError("Unexpected start hook");
            endCalls++;
            if (baseFailure) throw new IllegalStateException("base failed");
            if (Flags.vulkan && !acquired) outsideAcquired++;
            if (queued) { consumes++; queued = false; }
        }
    }
    private static void expectFailure(Executed run, String message) throws Exception {
        try { run.rendererReturn(); throw new AssertionError("Expected " + message); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            require(failure.getCause() instanceof IllegalStateException && message.equals(failure.getCause().getMessage()),
                    "actual hook/reflective call preserves exception: " + failure.getCause());
        }
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
    private static ClassNode read(String owner, Path jar) throws Exception {
        var node = new ClassNode();
        if (jar == null) {
            if (owner.equals(MANAGER) || owner.equals(MIXIN)) {
                Path compiled = Path.of("neoforge/build/classes/java/main", owner + ".class");
                try (var in = java.nio.file.Files.newInputStream(compiled)) {
                    new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
                }
                return node;
            }
            try (var in = VulkanFrameLifecycleCheck.class.getResourceAsStream("/" + owner + ".class")) {
                if (in == null) throw new AssertionError("Missing real compiled class " + owner);
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
            }
        } else try (var file = new JarFile(jar.toFile()); var in = file.getInputStream(file.getJarEntry(owner + ".class"))) {
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
        }
        return node;
    }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static void checkInjection(ClassNode mixin) {
        var hook = method(mixin, "onRenderFrameEnd");
        var annotations = new ArrayList<AnnotationNode>();
        if (hook.visibleAnnotations != null) annotations.addAll(hook.visibleAnnotations);
        if (hook.invisibleAnnotations != null) annotations.addAll(hook.invisibleAnnotations);
        var inject = annotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        require(value(inject, "method").equals(List.of("render(Lnet/minecraft/client/DeltaTracker;Z)V")),
                "real injection targets exact 1.21/1.21.1 DeltaTracker boolean render descriptor");
        Object at = value(inject, "at");
        require(at instanceof List<?> list && list.size() == 1 &&
                        "RETURN".equals(value((AnnotationNode) list.getFirst(), "value")),
                "real injection runs at render RETURN");
        require(hook.instructions.iterator().hasNext(), "real RETURN handler has executable bytecode");
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    /** Structural evidence only: real base fallback is guarded by screen/overlay and running checks. */
    private static void checkFallbackGuards(ClassNode common) {
        var nodes = method(common, "onRenderTick").instructions;
        MethodInsnNode render = null;
        for (var n : nodes) if (n instanceof MethodInsnNode call && call.owner.equals(COMMON) && call.name.equals("render")) {
            require(render == null, "base has a single fallback render call"); render = call;
        }
        require(render != null, "real base fallback render is present");
        int index = nodes.indexOf(render), screen = -1, overlay = -1, running = -1;
        for (var n : nodes) {
            if (nodes.indexOf(n) >= index) break;
            if (n instanceof FieldInsnNode field && field.name.equals("mScreen")) screen = nodes.indexOf(n);
            if (n instanceof MethodInsnNode call && call.name.equals("getOverlay")) overlay = nodes.indexOf(n);
            if (n instanceof MethodInsnNode call && call.name.equals("isRunning")) running = nodes.indexOf(n);
        }
        require(running >= 0 && screen > running && overlay > screen && index > overlay,
                "real fallback checks running, existing screen, then overlay before render");
        require(skipsRender(nodes, screen, index, Opcodes.IFNONNULL) && skipsRender(nodes, overlay, index, Opcodes.IFNONNULL),
                "existing screen or overlay branches bypass fallback, preventing duplicate screen render");
    }
    private static boolean skipsRender(InsnList nodes, int before, int render, int opcode) {
        for (int i = before + 1; i < render; i++) {
            var n = nodes.get(i);
            if (n.getOpcode() < 0) continue;
            return n instanceof JumpInsnNode branch && branch.getOpcode() == opcode && nodes.indexOf(branch.label) > render;
        }
        return false;
    }
    private static final class Executed extends ClassLoader {
        private final Object manager, renderer;
        private final Method post, hook;
        Executed(ClassNode original, ClassNode mixin) throws Exception {
            super(VulkanFrameLifecycleCheck.class.getClassLoader());
            Map<String,String> names = Map.of(MANAGER, GENERATED + "Manager", MIXIN, GENERATED + "Renderer",
                    COMMON, Type.getInternalName(Base.class), "icyllis/modernui/mc/ModernUIMod", Type.getInternalName(Flags.class),
                    "net/neoforged/neoforge/client/event/RenderFrameEvent$Post", "java/lang/Object",
                    "net/minecraft/client/DeltaTracker", "java/lang/Object",
                    "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "java/lang/Object",
                    BRIDGE, GENERATED + "Bridge", BRIDGE + "$Api", Type.getInternalName(BridgeApi.class));
            var remapper = new SimpleRemapper(names);
            ClassNode bridge = read(BRIDGE, null);
            Set<String> bridgeMethods = Set.of("isFrameActive", "call", "get", "field");
            require(bridge.methods.stream().filter(m -> bridgeMethods.contains(m.name)).count() == bridgeMethods.size(),
                    "execute actual frame-state method and all three real reflection helpers");
            copy(bridge, GENERATED + "Bridge", "java/lang/Object", remapper, bridgeMethods);
            Class<?> m = copy(original, GENERATED + "Manager", Type.getInternalName(Base.class), remapper,
                    Set.of("onRenderFramePost", "onVulkanRenderFrameEnd"));
            manager = m.getConstructor().newInstance(); Base.instance = (Base) manager;
            post = m.getMethod("onRenderFramePost", Object.class);
            if (mixin != null) {
                Class<?> r = copy(mixin, GENERATED + "Renderer", "java/lang/Object", remapper, Set.of("onRenderFrameEnd"));
                renderer = r.getConstructor().newInstance();
                hook = r.getMethod("onRenderFrameEnd", Object.class, boolean.class, Object.class);
            } else { renderer = null; hook = null; }
        }
        private Class<?> copy(ClassNode original, String name, String parent, SimpleRemapper remapper, Set<String> selected) {
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, parent, null);
            var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
            ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(1, 1); ctor.visitEnd();
            for (var source : original.methods) if (selected.contains(source.name))
                source.accept(new MethodRemapper(writer.visitMethod(Opcodes.ACC_PUBLIC | (source.access & Opcodes.ACC_STATIC),
                        source.name, remapper.mapMethodDesc(source.desc), null, null), remapper));
            writer.visitEnd(); byte[] bytes = writer.toByteArray();
            return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
        }
        void post() throws Exception { post.invoke(manager, new Object[]{null}); }
        void rendererReturn() throws Exception { if (hook != null) hook.invoke(renderer, null, true, null); }
    }
}
