package icyllis.modernui.mc.test;

import net.minecraft.sounds.SoundSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.*;

/** Executes the compiled production tick/getters/injections with only the window and AL sinks replaced. */
public final class WindowVolumeCheck {
    private static final String BLUR = "icyllis/modernui/mc/BlurHandler";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinSoundEngine";
    private static final String ENGINE = "net/minecraft/client/sounds/SoundEngine";
    private static final String OPTIONS = "net/minecraft/client/Options";
    private static final String TEST = Type.getInternalName(WindowVolumeCheck.class);
    private static final String PROBE = TEST + "$ProductionProbe";
    public static final FakeMinecraft minecraft = new FakeMinecraft();
    private static int gainWrites;
    private static float listenerGain;

    public static void recordGain(int parameter, float gain) {
        if (parameter != 0x100A) throw new AssertionError("not AL_GAIN");
        gainWrites++;
        listenerGain = gain;
    }
    public static int windowAttribute(long window, int attribute) { return 0; }

    public static void main(String[] args) throws Exception {
        checkActualApi();
        if (args.length == 0) throw new AssertionError("missing actual NeoForge patched mapped JAR");
        for (String path : args) {
            try (var jar = new java.util.jar.JarFile(path)) {
                try (var input = jar.getInputStream(jar.getJarEntry(ENGINE + ".class"))) {
                    ClassNode engine = new ClassNode();
                    new ClassReader(input).accept(engine, 0);
                    checkEngineContract(engine, path);
                }
            }
        }
        Class<?> probe = productionProbe();
        Object instance = probe.getConstructor().newInstance();
        probe.getField("INSTANCE").set(null, instance);
        probe.getField("minecraft").set(instance, minecraft);
        set(probe, instance, "mVolumeMultiplier", .5f);
        set(probe, instance, "sMasterVolumeInactive", .5f);
        set(probe, instance, "sMasterVolumeMinimized", .5f);
        set(probe, instance, "loaded", true);
        Method tick = probe.getMethod("onClientTick");
        Method refresh = probe.getMethod("modernui$refreshListenerGain", SoundSource.class, CallbackInfo.class);
        Method reload = probe.getMethod("modernui$restoreListenerGain", CallbackInfo.class);
        Method scale = probe.getMethod("modernui$scaleChannelVolume", float.class, SoundSource.class, CallbackInfoReturnable.class);
        // Stable .5 multiplier: each mode change must still refresh every channel.
        tick.invoke(instance);
        equal(0, minecraft.sounds.refreshes, "unchanged game-only mode");
        for (boolean global : new boolean[]{true, false, true}) {
            int before = minecraft.sounds.refreshes;
            set(probe, instance, "sGlobalVolumeControl", global);
            tick.invoke(instance);
            equal(before + 1, minecraft.sounds.refreshes, "stable multiplier mode flip");
            if (minecraft.sounds.source != SoundSource.MASTER) throw new AssertionError("not all categories");
            refresh.invoke(instance, SoundSource.MASTER, new CallbackInfo("refresh", false));
            near(global ? .5f : 1f, listenerGain, "listener mode gain");
            var ci = new CallbackInfoReturnable<Float>("calculateVolume", true, .48f);
            scale.invoke(instance, 1f, SoundSource.AMBIENT, ci);
            near(global ? .48f : .24f, ci.getReturnValueF(), "actual RETURN injection");
        }
        // Simulate replacement of the AL context: the production loadLibrary RETURN hook restores gain.
        listenerGain = 1;
        reload.invoke(instance, new CallbackInfo("loadLibrary", false));
        near(.5f, listenerGain, "global gain after reload");
        set(probe, instance, "sGlobalVolumeControl", false);
        reload.invoke(instance, new CallbackInfo("loadLibrary", false));
        near(1f, listenerGain, "game-only gain after reload");
        set(probe, instance, "loaded", false);
        int before = gainWrites;
        refresh.invoke(instance, SoundSource.MASTER, new CallbackInfo("refresh", false));
        reload.invoke(instance, new CallbackInfo("loadLibrary", false));
        equal(before, gainWrites, "unloaded engine must not call AL");
        System.out.println("Window volume: actual mapped API, compiled stable-mode tick, RETURN injection and loaded/reload gain guards passed");
    }

    private static void checkActualApi() throws Exception {
        ClassNode engine = read(ENGINE), options = read(OPTIONS);
        checkEngineContract(engine, "common mapped Minecraft");
        MethodNode finalVolume = method(options, "getFinalSoundSourceVolume", "(Lnet/minecraft/sounds/SoundSource;)F");
        long rawReads = Arrays.stream(finalVolume.instructions.toArray()).filter(i -> i instanceof MethodInsnNode m
                && m.owner.equals(OPTIONS) && m.name.equals("getSoundSourceVolume")).count();
        if (rawReads != 3 || !field(finalVolume, "net/minecraft/sounds/SoundSource", "MASTER")
                || Arrays.stream(finalVolume.instructions.toArray()).noneMatch(i -> i.getOpcode() == Opcodes.FMUL))
            throw new AssertionError("actual MASTER/category final-volume model changed");
        if (read("com/mojang/blaze3d/audio/Listener").methods.stream().anyMatch(m -> m.name.equals("setGain")))
            throw new AssertionError("re-audit listener API for this version");
        ClassNode mixin = read(MIXIN);
        injection(method(mixin, "modernui$refreshListenerGain", "(Lnet/minecraft/sounds/SoundSource;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),
                "updateCategoryVolume(Lnet/minecraft/sounds/SoundSource;)V", "HEAD");
        injection(method(mixin, "modernui$restoreListenerGain", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"), "loadLibrary()V", "RETURN");
        injection(method(mixin, "modernui$scaleChannelVolume", "(FLnet/minecraft/sounds/SoundSource;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"),
                "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", "RETURN");
    }

    private static void checkEngineContract(ClassNode engine, String label) {
        if (engine.fields.stream().noneMatch(f -> f.name.equals("loaded") && f.desc.equals("Z")))
            throw new AssertionError("actual loaded field changed: " + label);
        MethodNode load = method(engine, "loadLibrary", "()V");
        long returns = Arrays.stream(load.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.RETURN).count();
        if (returns < 1 || !field(load, ENGINE, "loaded")) throw new AssertionError("actual load contract changed: " + label);
        MethodNode refresh = method(engine, "updateCategoryVolume", "(Lnet/minecraft/sounds/SoundSource;)V");
        if (!calls(refresh, "java/util/Map", "forEach") || !field(refresh, ENGINE, "loaded"))
            throw new AssertionError("actual category refresh no longer updates all loaded channels: " + label);
        MethodNode calculate = method(engine, "calculateVolume", "(FLnet/minecraft/sounds/SoundSource;)F");
        if (!calls(calculate, OPTIONS, "getFinalSoundSourceVolume"))
            throw new AssertionError("actual engine must consume Options final source volume: " + label);
        System.out.println(label + ": loaded field/API/source-volume chain matched; loadLibrary RETURN sites=" + returns);
    }

    private static void injection(MethodNode method, String target, String at) {
        AnnotationNode annotation = method.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        Map<String, Object> values = annotationValues(annotation);
        if (!((List<?>) values.get("method")).contains(target)) throw new AssertionError("wrong injection target");
        if (values.containsKey("allow") || values.containsKey("require"))
            throw new AssertionError("do not lock vanilla/NeoForge RETURN counts");
        AnnotationNode location = (AnnotationNode) ((List<?>) values.get("at")).getFirst();
        if (!at.equals(annotationValues(location).get("value"))) throw new AssertionError("wrong injection location");
    }
    private static Map<String, Object> annotationValues(AnnotationNode annotation) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < annotation.values.size(); i += 2) values.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        return values;
    }

    private static Class<?> productionProbe() throws Exception {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V21;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = BLUR;
        node.superName = "java/lang/Object";
        Set<String> fields = Set.of("mVolumeMultiplier", "mGlobalVolumeControl", "sGlobalVolumeControl", "sMasterVolumeInactive", "sMasterVolumeMinimized", "minecraft");
        ClassNode blur = read(BLUR), mixin = read(MIXIN);
        for (FieldNode f : blur.fields) if (fields.contains(f.name)) {
            f.access = Opcodes.ACC_PUBLIC | (f.access & Opcodes.ACC_STATIC);
            node.fields.add(f);
        }
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "INSTANCE", "L" + BLUR + ";", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "loaded", "Z", null, null));
        for (MethodNode m : blur.methods) if (Set.of("onClientTick", "getListenerVolumeMultiplier", "getChannelVolumeMultiplier").contains(m.name)) node.methods.add(m);
        for (MethodNode m : mixin.methods) if (m.name.startsWith("modernui$")) node.methods.add(m);
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(ctor);
        for (MethodNode m : node.methods) {
            m.access = Opcodes.ACC_PUBLIC;
            m.visibleAnnotations = m.invisibleAnnotations = null;
            m.localVariables = null;
            for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode call) {
                if (call.owner.equals("org/lwjgl/openal/AL10") && call.name.equals("alListenerf")) { call.owner = TEST; call.name = "recordGain"; }
                if (call.owner.equals("org/lwjgl/glfw/GLFW") && call.name.equals("glfwGetWindowAttrib")) { call.owner = TEST; call.name = "windowAttribute"; }
            }
        }
        Map<String, String> names = Map.of(BLUR, PROBE, MIXIN, PROBE,
                "net/minecraft/client/Minecraft", Type.getInternalName(FakeMinecraft.class),
                "net/minecraft/client/sounds/SoundManager", Type.getInternalName(FakeSoundManager.class),
                "com/mojang/blaze3d/platform/Window", Type.getInternalName(FakeWindow.class));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(WindowVolumeCheck.class.getClassLoader()) { Class<?> define() { return defineClass(PROBE.replace('/', '.'), bytes, 0, bytes.length); } }.define();
    }
    private static ClassNode read(String name) throws Exception {
        try (var input = WindowVolumeCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("missing actual class " + name);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(() -> new AssertionError(node.name + "." + name + desc));
    }
    private static boolean calls(MethodNode method, String owner, String name) {
        return Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode m && m.owner.equals(owner) && m.name.equals(name));
    }
    private static boolean field(MethodNode method, String owner, String name) {
        return Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(name));
    }
    private static void set(Class<?> probe, Object instance, String field, Object value) throws Exception { probe.getField(field).set(instance, value); }
    private static void near(float expected, float actual, String what) { if (Math.abs(expected - actual) > .00001f) throw new AssertionError(what + ": " + actual); }
    private static void equal(int expected, int actual, String what) { if (expected != actual) throw new AssertionError(what + ": " + actual); }
    public static final class FakeMinecraft {
        public final FakeSoundManager sounds = new FakeSoundManager();
        public boolean isWindowActive() { return false; }
        public FakeWindow getWindow() { return new FakeWindow(); }
        public FakeSoundManager getSoundManager() { return sounds; }
    }
    public static final class FakeWindow { public long handle() { return 0; } }
    public static final class FakeSoundManager {
        int refreshes; SoundSource source;
        public void updateSourceVolume(SoundSource source) { refreshes++; this.source = source; }
    }
}
