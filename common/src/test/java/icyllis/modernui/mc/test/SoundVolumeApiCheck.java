package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.*;

/** Executes copied production sound methods with only Minecraft/audio/native boundaries replaced. */
public final class SoundVolumeApiCheck {
    private static final String BLUR = "icyllis/modernui/mc/BlurHandler";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinSoundEngine";
    private static final String ENGINE = "net/minecraft/client/sounds/SoundEngine";
    private static final String SOURCE = "net/minecraft/sounds/SoundSource";
    private static final String PREFIX = "icyllis/modernui/mc/test/SoundVolumeExecuted";
    private static int checks;

    public static void main(String[] args) throws Exception {
        ClassNode engine = read(ENGINE), blur = read(BLUR), mixin = read(MIXIN);
        checkApi(engine, mixin);
        var names = new HashMap<String, String>();
        names.put(BLUR, PREFIX + "Blur");
        names.put(MIXIN, PREFIX + "Mixin");
        names.put(ENGINE, PREFIX + "Engine");
        names.put("net/minecraft/util/Mth", PREFIX + "Math");
        names.put(SOURCE, Type.getInternalName(Source.class));
        names.put("net/minecraft/client/Minecraft", Type.getInternalName(Client.class));
        names.put("net/minecraft/client/Options", Type.getInternalName(Options.class));
        names.put("net/minecraft/client/sounds/SoundManager", Type.getInternalName(SoundManager.class));
        names.put("com/mojang/blaze3d/platform/Window", Type.getInternalName(Window.class));
        names.put("org/lwjgl/glfw/GLFW", Type.getInternalName(NativeBoundary.class));
        var loader = new ExecutedLoader();
        loader.copy(read("net/minecraft/util/Mth"), names, Set.of("clamp(FFF)F"));
        Class<?> tickType = loader.copy(blur, names, Set.of("onClientTick()V", "getChannelVolumeMultiplier()F",
                "getGlobalVolumeMultiplier()F", "isRefreshingChannelVolumes()Z"));
        Class<?> handlersType = loader.copy(mixin, names, Set.of("modernui$scaleChannelVolume", "modernui$scaleGlobalVolume"));
        Class<?> engineType = loader.copy(engine, names, Set.of("getVolume(L" + SOURCE + ";)F",
                "calculateVolume(FL" + SOURCE + ";)F"));
        var client = new Client();
        Object tick = tickType.getConstructor().newInstance();
        Object handlers = handlersType.getConstructor().newInstance();
        Object soundEngine = engineType.getConstructor().newInstance();
        set(tickType, tick, "minecraft", client);
        set(tickType, null, "INSTANCE", tick);
        set(engineType, soundEngine, "options", client.options);
        set(tickType, null, "sMasterVolumeInactive", 0.4f);
        set(tickType, null, "sMasterVolumeMinimized", 0.4f);
        set(tickType, tick, "mVolumeMultiplier", 0.4f);
        set(tickType, tick, "mLastGlobalVolumeControl", false);
        set(tickType, null, "sGlobalVolumeControl", false);
        Method refreshing = tickType.getMethod("isRefreshingChannelVolumes");
        client.sounds.refreshing = () -> {
            try { return (boolean) refreshing.invoke(null); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        };
        Method onTick = tickType.getMethod("onClientTick");
        Method channel = handlersType.getMethod("modernui$scaleChannelVolume", float.class, Source.class,
                CallbackInfoReturnable.class);
        Method global = handlersType.getMethod("modernui$scaleGlobalVolume", float.class);
        Method calculate = engineType.getMethod("calculateVolume", float.class, Source.class);
        float vanilla = (float) calculate.invoke(soundEngine, 0.8f, Source.MUSIC);
        close(vanilla, 0.4f, "actual vanilla raw/category volume excludes MASTER");
        close((float) calculate.invoke(soundEngine, 0.8f, Source.MASTER), 0.8f, "MASTER is not multiplied into channel volume");
        float normalChannel = channel(handlers, channel, vanilla);
        float normalListener = (float) global.invoke(handlers, 0.6f);
        close(normalChannel, 0.16f, "game-only channel gain");
        close(normalListener, 0.6f, "game-only listener gain");
        onTick.invoke(tick);
        require(client.sounds.calls.isEmpty(), "stable mode and volume cause no redundant refresh");

        set(tickType, null, "sGlobalVolumeControl", true);
        onTick.invoke(tick);
        refreshed(client, "mode flip at an already-settled fade");
        close((float) tickType.getMethod("getChannelVolumeMultiplier").invoke(tick), 1, "real global-mode channel getter");
        close((float) tickType.getMethod("getGlobalVolumeMultiplier").invoke(tick), 0.4f, "real global-mode listener getter");
        float globalChannel = channel(handlers, channel, vanilla);
        float globalListener = (float) global.invoke(handlers, 0.6f);
        close(globalChannel, 0.4f, "global-mode channel gain");
        close(globalListener, 0.24f, "global-mode listener gain");
        close(normalChannel * normalListener, globalChannel * globalListener, "switch preserves effective game loudness");
        close(0.75f * normalListener, 0.45f, "external music retains its own channel gain in game-only mode");
        close(0.75f * globalListener, 0.18f, "external music follows listener gain in global mode");
        client.sounds.calls.clear();
        onTick.invoke(tick);
        require(client.sounds.calls.isEmpty(), "repeated global mode does not refresh channels");

        set(tickType, null, "sGlobalVolumeControl", false);
        onTick.invoke(tick);
        refreshed(client, "reverse mode flip at settled volume");
        close(channel(handlers, channel, (float) calculate.invoke(soundEngine, 3f, Source.MUSIC)), 0.4f,
                "production mixin scales the already-clamped vanilla volume");
        close(channel(handlers, channel, (float) calculate.invoke(soundEngine, -1f, Source.MUSIC)), 0,
                "negative raw volume remains clamped");
        client.sounds.calls.clear();
        set(tickType, null, "sMasterVolumeInactive", 0.2f);
        set(tickType, null, "sMasterVolumeMinimized", 0.2f);
        onTick.invoke(tick);
        refreshed(client, "changed fade target refreshes listener and all channels");
        close((float) tickType.getField("mVolumeMultiplier").get(tick), 0.35f, "actual tick fades down by 0.05");
        client.sounds.calls.clear();
        client.active = true;
        onTick.invoke(tick);
        refreshed(client, "focus restore refreshes both sound paths");
        close((float) tickType.getField("mVolumeMultiplier").get(tick), 0.85f, "actual tick fades up by 0.5");
        client.sounds.calls.clear();
        onTick.invoke(tick);
        refreshed(client, "focus fade clamps at one");
        close((float) tickType.getField("mVolumeMultiplier").get(tick), 1f, "fade reaches unity");
        client.sounds.calls.clear();
        onTick.invoke(tick);
        require(client.sounds.calls.isEmpty(), "settled focus gain causes no refresh");
        client.sounds.throwOnAmbient = true;
        set(tickType, null, "sGlobalVolumeControl", true);
        try {
            onTick.invoke(tick);
            throw new AssertionError("Expected the sound-manager boundary to fail");
        } catch (java.lang.reflect.InvocationTargetException failure) {
            require(failure.getCause() instanceof AudioBoundaryFailure, "audio failure propagates from the real tick");
        }
        require(!(boolean) refreshing.invoke(null), "real tick finally block clears internal refresh flag after failure");
        require(NativeBoundary.calls == 0, "equal minimized/inactive targets avoid the native GLFW boundary");
        System.out.println("Sound volume API: " + checks + " actual getter, modifier, tick/mode-switch and vanilla-path checks passed");
    }

    private static float channel(Object instance, Method handler, float original) throws Exception {
        var callback = new CallbackInfoReturnable<Float>("calculateVolume", true, Float.valueOf(original));
        handler.invoke(instance, 0.8f, Source.MUSIC, callback);
        return callback.getReturnValueF();
    }

    private static void refreshed(Client client, String reason) {
        require(client.sounds.calls.size() == 2, reason + ": exactly two refreshes");
        require(client.sounds.calls.get(0).source == Source.MASTER &&
                client.sounds.calls.get(1).source == Source.AMBIENT, reason + ": listener plus all-channel path");
        require(!client.sounds.calls.get(0).internalRefresh && client.sounds.calls.get(1).internalRefresh,
                reason + ": special zero-safe refresh is scoped to AMBIENT only");
        close(client.sounds.calls.get(0).volume, 0.6f, reason + ": raw MASTER, no double focus multiplier");
        close(client.sounds.calls.get(1).volume, 0.5f, reason + ": raw category gain");
    }

    private static void checkApi(ClassNode engine, ClassNode mixin) {
        int calculateReturns = 0, listenerCalls = 0, libraryListenerCalls = 0, refreshes = 0;
        for (var method : engine.methods) {
            for (var instruction : method.instructions) {
                if (method.name.equals("calculateVolume") && method.desc.equals("(FL" + SOURCE + ";)F") &&
                        instruction.getOpcode() == Opcodes.FRETURN) calculateReturns++;
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals("com/mojang/blaze3d/audio/Listener") && call.name.equals("setGain")) {
                        if (method.name.equals("updateCategoryVolume")) listenerCalls++;
                        if (method.name.equals("loadLibrary")) libraryListenerCalls++;
                    }
                    if (method.name.equals("updateCategoryVolume") && call.owner.equals("java/util/Map") &&
                            call.name.equals("forEach")) refreshes++;
                }
            }
        }
        require(calculateReturns == 1 && listenerCalls == 1 && libraryListenerCalls == 1 && refreshes == 1,
                "actual vanilla channel return, MASTER listener, device reload and non-MASTER refresh paths");
        boolean bothTargets = false;
        for (var method : mixin.methods) {
            var annotations = new ArrayList<AnnotationNode>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (var annotation : annotations) {
                if (!annotation.desc.endsWith("/ModifyArg;")) continue;
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (annotation.values.get(i).equals("method") && annotation.values.get(i + 1) instanceof List<?> targets)
                        bothTargets = targets.contains("updateCategoryVolume(L" + SOURCE + ";F)V") && targets.contains("loadLibrary()V");
                }
            }
        }
        require(bothTargets, "production listener modifier covers MASTER updates and sound-device reload");
    }

    private static ClassNode read(String owner) throws Exception {
        var node = new ClassNode();
        try (var stream = SoundVolumeApiCheck.class.getResourceAsStream("/" + owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing product class " + owner);
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG);
        }
        return node;
    }
    private static void set(Class<?> owner, Object instance, String name, Object value) throws Exception {
        owner.getField(name).set(instance, value);
    }
    private static void close(float actual, float expected, String reason) {
        require(Math.abs(actual - expected) < 0.00001f, reason + ": " + actual + " != " + expected);
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }

    private static final class ExecutedLoader extends ClassLoader {
        ExecutedLoader() { super(SoundVolumeApiCheck.class.getClassLoader()); }
        Class<?> copy(ClassNode original, Map<String, String> names, Set<String> selected) {
            var remapper = new SimpleRemapper(names);
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            String owner = names.get(original.name);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
            // Constructors/static initializers are deliberately not copied: they open the game/audio/native owners.
            var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(1, 1); ctor.visitEnd();
            Set<String> fields = new HashSet<>();
            int copied = 0;
            for (var method : original.methods) {
                if (!selected.contains(method.name) && !selected.contains(method.name + method.desc)) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.owner.equals(original.name) && fields.add(field.name)) {
                        boolean isStatic = field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC;
                        writer.visitField(Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0), field.name,
                                remapper.mapDesc(field.desc), null, null).visitEnd();
                    }
                }
                var target = writer.visitMethod(Opcodes.ACC_PUBLIC | (method.access & Opcodes.ACC_STATIC),
                        method.name, remapper.mapMethodDesc(method.desc), null, null);
                method.accept(new MethodRemapper(target, remapper));
                copied++;
            }
            require(copied == selected.size(), "all selected production methods exist in " + original.name);
            if (original.name.equals(BLUR) && !fields.contains("INSTANCE"))
                writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "INSTANCE", "L" + owner + ";", null, null).visitEnd();
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            return defineClass(owner.replace('/', '.'), bytes, 0, bytes.length);
        }
    }

    public enum Source { MASTER, AMBIENT, MUSIC }
    public static final class Options {
        public float getSoundSourceVolume(Source source) { return source == Source.MASTER ? 0.6f : 0.5f; }
    }
    public record Refresh(Source source, float volume, boolean internalRefresh) {}
    public static final class SoundManager {
        public final List<Refresh> calls = new ArrayList<>();
        public java.util.function.BooleanSupplier refreshing = () -> false;
        public boolean throwOnAmbient;
        public void updateSourceVolume(Source source, float volume) {
            calls.add(new Refresh(source, volume, refreshing.getAsBoolean()));
            if (throwOnAmbient && source == Source.AMBIENT) throw new AudioBoundaryFailure();
        }
    }
    public static final class AudioBoundaryFailure extends RuntimeException {}
    public static final class Client {
        public final Options options = new Options();
        public final SoundManager sounds = new SoundManager();
        public boolean active;
        public boolean isWindowActive() { return active; }
        public SoundManager getSoundManager() { return sounds; }
        public Window getWindow() { return new Window(); }
    }
    public static final class Window { public long getWindow() { return 0; } }
    public static final class NativeBoundary {
        public static int calls;
        public static int glfwGetWindowAttrib(long window, int attribute) {
            calls++;
            throw new AssertionError("Unexpected native-window path");
        }
    }
}
