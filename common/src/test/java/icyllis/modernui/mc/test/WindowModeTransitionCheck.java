package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

import static org.objectweb.asm.Opcodes.*;

/** Runs the actual Window transition and compiled Mixin code with native calls instrumented. */
public final class WindowModeTransitionCheck {
    private static final String WINDOW = "com/mojang/blaze3d/platform/Window";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinWindow";
    private static final String HERE = "icyllis/modernui/mc/test/WindowModeTransitionCheck";
    private static final String PROBE = HERE + "$Probe";
    private static final String SYNC = "modernUI_MC$updateFullscreenIfChanged";
    private static int nativeWidth, nativeHeight, flips, resizes, vsyncs, errors, assertions;
    private static boolean failNative, failError;
    private static Object current;
    private static final List<String> events = new ArrayList<>();
    public static final Logger LOGGER = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(),
            new Class<?>[]{Logger.class}, (proxy, method, args) -> {
                if (method.getName().equals("error")) errors++;
                return method.getReturnType() == boolean.class ? false : null;
            });
    public static final org.apache.logging.log4j.Logger MUI_LOGGER =
            (org.apache.logging.log4j.Logger) Proxy.newProxyInstance(
                    org.apache.logging.log4j.Logger.class.getClassLoader(),
                    new Class<?>[]{org.apache.logging.log4j.Logger.class}, (proxy, method, args) -> {
                        if (method.getName().equals("error")) errors++;
                        return method.getReturnType() == boolean.class ? false : null;
                    });
    public interface Handler { void resizeDisplay(); }

    public static void main(String[] args) throws Exception {
        checkTarget(null);
        for (String path : args) checkTarget(Path.of(path));
    }

    private static void checkTarget(Path target) throws Exception {
        System.out.println("Auditing actual Window: " + (target == null ? "common mapped Minecraft" : target));
        ClassNode mixin = read(MIXIN, null);
        ClassNode vanilla = read(WINDOW, target);
        MethodNode bridge = method(mixin, SYNC);
        verifyContract(mixin, vanilla, bridge);
        for (String module : System.getProperty("modernui.windowCheckModules", "common,fabric,neoforge").split(",")) {
            ClassNode config = read("icyllis/modernui/mc/Config$Client$WindowMode",
                    Path.of(module, "build/classes/java/main"));
            checkConfig(config);
        }
        Class<?> fixed = probe(vanilla, bridge, true);
        Class<?> missingRefresh = probe(vanilla, bridge, false);
        for (boolean pendingF11 : new boolean[]{false, true}) {
            Object old = setup(fixed, pendingF11);
            exitRequest(old);
            nativeWidth = 1920; nativeHeight = 1080; // MUI's requested borderless bounds.
            advance(old);
            equal(1600, nativeWidth, "old delayed setMode must reproduce overwritten width");
            equal(900, nativeHeight, "old delayed setMode must reproduce overwritten height");

            Object next = setup(fixed, pendingF11);
            exitRequest(next);
            sync(next);
            equal(0, flips, "bridge must not flip a frame");
            equal(1, resizes, "one resize notification");
            equal(1, vsyncs, "one vsync application");
            if (next.getClass().getField("fullscreen").getBoolean(next) ||
                    next.getClass().getField("actuallyFullscreen").getBoolean(next))
                throw new AssertionError("requested/applied fullscreen state not synchronized");
            equal(1600, value(next, "framebufferWidth"), "refresh observes native width before resize");
            equal(900, value(next, "framebufferHeight"), "refresh observes native height before resize");
            if (!events.equals(List.of("assert", "native-mode", "refresh", "resize", "vsync")))
                throw new AssertionError("transition order " + events);
            nativeWidth = 1920; nativeHeight = 1080;
            advance(next);
            equal(1920, nativeWidth, "new borderless width survives actual next updateDisplay");
            equal(1080, nativeHeight, "new borderless height survives actual next updateDisplay");
            equal(1, flips, "only the ordinary next-frame flip");
            equal(1, resizes, "no second delayed transition");
            sync(next);
            equal(1, resizes, "synchronized state is a no-op");

            Object noRefresh = setup(missingRefresh, pendingF11);
            exitRequest(noRefresh); sync(noRefresh);
            if (value(noRefresh, "framebufferWidth") == 1600)
                throw new AssertionError("missing-refresh mutation must retain stale framebuffer width");
        }
        Object failed = setup(fixed, false); exitRequest(failed); failNative = true; sync(failed);
        equal(1, errors, "native Exception is logged once");
        equal(0, resizes, "failed mode change must not resize");
        failNative = false;
        Object fatal = setup(fixed, false); exitRequest(fatal); failError = true;
        try {
            sync(fatal);
            throw new AssertionError("native Error must propagate");
        } catch (java.lang.reflect.InvocationTargetException exception) {
            if (!(exception.getCause() instanceof AssertionError)) throw exception;
        }
        equal(0, errors, "native Error must not be swallowed by Exception handler");
        failError = false;
        System.out.println("Window transition checks passed: actual target bytecode + compiled bridge; old/pending-F11 negatives, geometry, framebuffer, vsync, ordering, no extra flip, Exception semantics");
    }

    private static void verifyContract(ClassNode mixin, ClassNode vanilla, MethodNode bridge) {
        for (FieldNode f : mixin.fields) {
            if (Set.of("fullscreen", "actuallyFullscreen", "vsync", "eventHandler").contains(f.name)) {
                if (vanilla.fields.stream().noneMatch(v -> v.name.equals(f.name) && v.desc.equals(f.desc)))
                    throw new AssertionError("missing actual shadow field " + f.name + f.desc);
            }
        }
        for (String name : List.of("setMode", "refreshFramebufferSize", "updateVsync")) method(vanilla, name);
        if (vanilla.methods.stream().anyMatch(m -> m.name.equals("updateFullscreenIfChanged")))
            throw new AssertionError("this legacy bridge is not for modern Window");
        for (AbstractInsnNode insn : bridge.instructions) {
            if (insn instanceof MethodInsnNode call &&
                    (call.name.equals("updateDisplay") || call.name.equals("flipFrame") || call.name.equals("updateFullscreen")))
                throw new AssertionError("bridge presents a frame: " + call.name);
        }
        if (bridge.tryCatchBlocks.stream().noneMatch(b -> "java/lang/Exception".equals(b.type)))
            throw new AssertionError("vanilla Exception boundary missing");
    }

    private static void checkConfig(ClassNode config) {
        MethodNode apply = method(config, "apply"); int calls = 0;
        for (AbstractInsnNode i : apply.instructions) {
            if (i instanceof MethodInsnNode m && m.name.equals(SYNC)) {
                calls++;
                int position = apply.instructions.indexOf(i);
                boolean exitsToggleIf = false;
                for (AbstractInsnNode before = i.getPrevious(); before != null; before = before.getPrevious()) {
                    if (before instanceof JumpInsnNode jump && jump.getOpcode() == IFEQ) {
                        int target = apply.instructions.indexOf(jump.label);
                        if (target < position && target > apply.instructions.indexOf(before)) {
                            exitsToggleIf = true;
                        }
                        break;
                    }
                }
                if (!exitsToggleIf) throw new AssertionError("pending-F11 false path bypasses flush");
                AbstractInsnNode next = i.getNext();
                while (next != null && !(next instanceof MethodInsnNode)) next = next.getNext();
                if (!(next instanceof MethodInsnNode call) || !call.name.equals("handle"))
                    throw new AssertionError("flush must precede native window operation");
            }
        }
        equal(5, calls, "all five nonexclusive modes must flush unconditionally after the toggle-if");
    }

    private static Class<?> probe(ClassNode vanilla, MethodNode bridge, boolean refresh) throws Exception {
        ClassNode n = new ClassNode(); n.version = V21; n.access = ACC_PUBLIC; n.name = PROBE; n.superName = "java/lang/Object";
        for (FieldNode f : vanilla.fields) if (Set.of("window", "handle", "fullscreen", "actuallyFullscreen", "vsync", "eventHandler",
                "windowedX", "windowedY", "windowedWidth", "windowedHeight", "x", "y", "width", "height", "framebufferWidth", "framebufferHeight").contains(f.name))
            n.fields.add(new FieldNode(ACC_PUBLIC, f.name, f.desc, null, null));
        MethodNode ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)); ctor.instructions.add(new InsnNode(RETURN)); n.methods.add(ctor);
        for (String name : List.of("toggleFullScreen", "updateDisplay", "updateFullscreen", "refreshFramebufferSize")) n.methods.add(copy(method(vanilla, name)));
        MethodNode mode = method(vanilla, "setMode"); LabelNode start = null;
        for (AbstractInsnNode i : mode.instructions) if (i instanceof FieldInsnNode f && f.name.equals("fullscreen")) {
            AbstractInsnNode next = i.getNext(); while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (next instanceof JumpInsnNode jump && jump.getOpcode() == IFEQ) { start = jump.label; break; }
        }
        if (start == null) throw new AssertionError("actual setMode windowed branch missing");
        MethodNode windowed = new MethodNode(ACC_PUBLIC, "setMode", "()V", null, null);
        Map<LabelNode,LabelNode> labels = new HashMap<>();
        for (AbstractInsnNode i : mode.instructions) if (i instanceof LabelNode l) labels.put(l, new LabelNode());
        for (AbstractInsnNode i = start; i != null; i = i.getNext()) if (!(i instanceof FrameNode) && !(i instanceof LineNumberNode)) windowed.instructions.add(i.clone(labels));
        n.methods.add(windowed);
        MethodNode b = copy(bridge); b.access = ACC_PUBLIC;
        if (!refresh) for (AbstractInsnNode i : b.instructions.toArray()) if (i instanceof MethodInsnNode call && call.name.equals("refreshFramebufferSize")) {
            b.instructions.remove(i.getPrevious()); b.instructions.remove(i);
        }
        n.methods.add(b);
        MethodNode vsync = new MethodNode(ACC_PUBLIC, "updateVsync", "(Z)V", null, null);
        vsync.instructions.add(new VarInsnNode(ILOAD, 1)); vsync.instructions.add(new MethodInsnNode(INVOKESTATIC, HERE, "vsync", "(Z)V", false)); vsync.instructions.add(new InsnNode(RETURN)); n.methods.add(vsync);
        Remapper mapper = new Remapper() { @Override public String map(String name) {
            if (name.equals(WINDOW) || name.equals(MIXIN)) return PROBE;
            if (name.equals("com/mojang/blaze3d/platform/WindowEventHandler")) return HERE + "$Handler";
            if (name.equals("com/mojang/blaze3d/TracyFrameCapture")) return "java/lang/Object";
            return name;
        }};
        ClassNode mapped = new ClassNode(); n.accept(new ClassRemapper(mapped, mapper));
        for (MethodNode m : mapped.methods) {
            m.access = ACC_PUBLIC;
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof FieldInsnNode f && f.name.equals("LOGGER")) {
                    f.owner = HERE;
                    if (f.desc.equals("Lorg/apache/logging/log4j/Logger;")) f.name = "MUI_LOGGER";
                }
                if (i instanceof MethodInsnNode c) {
                    if (c.owner.equals("com/mojang/blaze3d/systems/RenderSystem")) c.owner = HERE;
                    if (c.owner.equals("org/lwjgl/glfw/GLFW")) c.owner = HERE;
                    if (c.name.equals("flipFrame") && c.desc.startsWith("(L")) c.desc = "(Ljava/lang/Object;Ljava/lang/Object;)V";
                }
            }
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        mapped.accept(cw); byte[] bytes = cw.toByteArray();
        return new ClassLoader(WindowModeTransitionCheck.class.getClassLoader()) { Class<?> make() { return defineClass(null, bytes, 0, bytes.length); }}.make();
    }
    private static Object setup(Class<?> type, boolean pending) throws Exception {
        current = type.getConstructor().newInstance(); nativeWidth = 1920; nativeHeight = 1080;
        flips = resizes = vsyncs = errors = assertions = 0; failNative = failError = false; events.clear();
        for (String f : List.of("window", "handle")) if (Arrays.stream(type.getFields()).anyMatch(x -> x.getName().equals(f))) type.getField(f).setLong(current, 7);
        type.getField("fullscreen").setBoolean(current, !pending); type.getField("actuallyFullscreen").setBoolean(current, true);
        type.getField("vsync").setBoolean(current, true);
        for (String f : List.of("windowedWidth", "width")) type.getField(f).setInt(current, 1600);
        for (String f : List.of("windowedHeight", "height")) type.getField(f).setInt(current, 900);
        type.getField("windowedX").setInt(current, 33); type.getField("windowedY").setInt(current, 44);
        type.getField("framebufferWidth").setInt(current, 1920); type.getField("framebufferHeight").setInt(current, 1080);
        type.getField("eventHandler").set(current, (Handler) () -> { resizes++; events.add("resize"); }); return current;
    }
    private static void exitRequest(Object p) throws Exception { if (p.getClass().getField("fullscreen").getBoolean(p)) p.getClass().getMethod("toggleFullScreen").invoke(p); }
    private static void sync(Object p) throws Exception { p.getClass().getMethod(SYNC).invoke(p); }
    private static void advance(Object p) throws Exception { p.getClass().getMethod("updateDisplay", Object.class).invoke(p, new Object[]{null}); }
    private static int value(Object p, String f) throws Exception { return p.getClass().getField(f).getInt(p); }
    public static void assertOnRenderThread() { assertions++; events.add("assert"); }
    public static void vsync(boolean enabled) { if (!enabled) throw new AssertionError("lost vsync value"); vsyncs++; events.add("vsync"); }
    public static void glfwSetWindowMonitor(long handle, long monitor, int x, int y, int w, int h, int rate) {
        if (failNative) throw new IllegalStateException("injected native failure");
        if (failError) throw new AssertionError("injected native Error");
        if (handle != 7 || monitor != 0 || x != 33 || y != 44 || rate != -1) throw new AssertionError("GLFW argument order");
        nativeWidth = w; nativeHeight = h; events.add("native-mode");
    }
    public static void glfwGetFramebufferSize(long handle, int[] w, int[] h) { if (handle != 7) throw new AssertionError(); w[0] = nativeWidth; h[0] = nativeHeight; events.add("refresh"); }
    public static void flipFrame(long handle, Object capture) throws Exception { flipFrame((Object)null, capture); }
    public static void flipFrame(Object window, Object capture) throws Exception {
        flips++; current.getClass().getField("framebufferWidth").setInt(current, nativeWidth); current.getClass().getField("framebufferHeight").setInt(current, nativeHeight);
    }
    private static MethodNode copy(MethodNode m) { MethodNode n = new MethodNode(m.access, m.name, m.desc, null, m.exceptions.toArray(String[]::new)); m.accept(n); return n; }
    private static MethodNode method(ClassNode n, String name) { return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(() -> new AssertionError(n.name + "." + name)); }
    private static ClassNode read(String name, Path root) throws Exception {
        byte[] data;
        if (root != null && Files.isDirectory(root)) data = Files.readAllBytes(root.resolve(name + ".class"));
        else if (root != null) try (JarFile j = new JarFile(root.toFile())) { data = j.getInputStream(j.getJarEntry(name + ".class")).readAllBytes(); }
        else if (name.startsWith("icyllis/")) data = Files.readAllBytes(Path.of("common/build/classes/java/main", name + ".class"));
        else try (InputStream in = WindowModeTransitionCheck.class.getClassLoader().getResourceAsStream(name + ".class")) { if (in == null) throw new AssertionError(name); data = in.readAllBytes(); }
        ClassNode n = new ClassNode(); new ClassReader(data).accept(n, ClassReader.SKIP_FRAMES); return n;
    }
    private static void equal(int expected, int actual, String message) { if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected); }
}
