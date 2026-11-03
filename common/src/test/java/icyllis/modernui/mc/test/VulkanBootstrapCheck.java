package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Executes real NeoForge startup bytecode before its mod constructor, without a game or GPU. */
public final class VulkanBootstrapCheck {
    private static final String COMMON = "icyllis/modernui/mc/ModernUIMod";
    private static final String HELPER = "icyllis/modernui/mc/neoforge/VulkanModBootstrap";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinRenderSystem";
    private static final String CLIENT = "icyllis/modernui/mc/ModernUIClient";
    private static final String PREFIX = "icyllis/modernui/mc/test/VulkanBootstrapExecuted";
    private static final List<String> trace = new ArrayList<>();
    private static boolean present, vulkanSuccess = true, glSuccess = true, wrapFailure;
    private static String stagingKey, spirvKey;
    private static Options observedOptions;
    private static int checks;

    public static void main(String[] args) throws Exception {
        ClassNode common = read(COMMON, null), helper = read(HELPER, null), mixin = read(MIXIN, null);
        ClassNode client = read(CLIENT, null);
        stagingKey = constant(client, "BOOTSTRAP_USE_STAGING_BUFFERS_IN_OPENGL");
        spirvKey = constant(client, "BOOTSTRAP_ALLOW_SPIRV_IN_OPENGL");
        var run = new Runtime(common, helper, mixin, false);
        reset(true);
        run.flag(false);
        run.invoke();
        require(run.flag(), "metadata detection publishes the shared flag before the mod constructor");
        require(trace.equals(expected(true)), "VK startup preserves initialization/options/detection order: " + trace);
        checkOptions();
        reset(false);
        run.flag(false);
        run.invoke();
        require(!run.flag(), "absent metadata publishes false");
        require(trace.equals(expected(false)), "absent VulkanMod uses GL only: " + trace);
        checkOptions();
        // A previous true flag cannot override the actual startup metadata.
        reset(false);
        run.flag(true);
        run.invoke();
        require(!run.flag() && trace.contains("core.gl") && !trace.contains("core.vulkan"),
                "fresh metadata replaces a stale true flag");
        reset(true);
        run.flag(false);
        vulkanSuccess = false;
        expectFailure(run, "Unable to initialize the VulkanMod UI renderer");
        require(!trace.contains("core.gl") && !trace.contains("core.glDialog") && !trace.contains("integration.caps"),
                "failed Vulkan initialization throws without GL fallback or shader-cap configuration");
        reset(true);
        run.flag(false);
        wrapFailure = true;
        expectFailure(run, "synthetic wrap failure");
        require(!trace.contains("core.gl") && !trace.contains("core.vulkan"),
                "Vulkan context-wrap failure propagates without opening another backend");
        reset(false);
        run.flag(false);
        glSuccess = false;
        run.invoke();
        require(trace.contains("core.glDialog") && !trace.contains("core.vulkan"), "GL error-dialog behavior is retained");
        if (args.length > 0) {
            Path oldJar = Path.of(args[0]);
            var legacy = new Runtime(read(COMMON, oldJar), null, read(MIXIN, oldJar), true);
            reset(true);
            legacy.flag(false);
            legacy.invoke();
            require(!legacy.flag() && trace.contains("core.gl") && !trace.contains("core.vulkan") &&
                    !trace.contains("metadata.get"),
                    "frozen old bytecode reproduces incorrect GL branch before its constructor");
            System.out.println("Old-JAR negative control reproduced: " + oldJar + " sha256=" +
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(oldJar))));
        } else {
            System.out.println("Old-JAR negative control not supplied; pass the frozen old JAR as argument 0.");
        }
        System.out.println("Vulkan bootstrap: " + checks + " real detector/renderer bytecode checks passed without constructor, ModList or GPU initialization");
    }

    private static List<String> expected(boolean vk) {
        var result = new ArrayList<>(List.of("metadata.get", "metadata.lookup:vulkanmod", "core.initialize",
                "options.new", "property:" + stagingKey, "property:" + spirvKey, "workarounds"));
        result.addAll(vk ? List.of("integration.wrap", "core.vulkan", "integration.caps") : List.of("core.gl"));
        return result;
    }
    private static void checkOptions() {
        require(observedOptions != null && observedOptions.mUseStagingBuffers && !observedOptions.mAllowGLSPIRV &&
                observedOptions.mDriverBugWorkarounds == Client.workarounds, "both bootstrap options and workarounds reach the selected Core backend");
    }
    private static void reset(boolean metadataPresent) {
        trace.clear(); present = metadataPresent; vulkanSuccess = glSuccess = true; wrapFailure = false; observedOptions = null;
    }
    private static void expectFailure(Runtime run, String text) throws Exception {
        try { run.invoke(); throw new AssertionError("Expected Vulkan startup failure"); }
        catch (InvocationTargetException failure) {
            require(failure.getCause() instanceof IllegalStateException && failure.getCause().getMessage().contains(text),
                    "real startup failure propagates: " + failure.getCause());
        }
    }
    private static String constant(ClassNode node, String name) {
        return (String) node.fields.stream().filter(field -> field.name.equals(name)).findFirst().orElseThrow().value;
    }
    private static ClassNode read(String name, Path jar) throws Exception {
        var result = new ClassNode();
        if (jar != null) {
            try (var input = new JarFile(jar.toFile())) {
                var entry = input.getJarEntry(name + ".class");
                if (entry == null) throw new AssertionError("Missing " + name + " in " + jar);
                try (var stream = input.getInputStream(entry)) {
                    new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG);
                }
            }
        } else {
            Path neoClass = Path.of("neoforge/build/classes/java/main", name + ".class");
            if ((name.equals(HELPER) || name.equals(MIXIN)) && java.nio.file.Files.isRegularFile(neoClass)) {
                new ClassReader(java.nio.file.Files.readAllBytes(neoClass)).accept(result, ClassReader.SKIP_DEBUG);
                return result;
            }
            try (var stream = VulkanBootstrapCheck.class.getResourceAsStream("/" + name + ".class")) {
                if (stream == null) throw new AssertionError("Missing Neo product class " + name);
                new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG);
            }
        }
        return result;
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }

    private static final class Runtime extends ClassLoader {
        private final Class<?> common;
        private final Method renderer;
        private final boolean legacy;
        Runtime(ClassNode commonNode, ClassNode helper, ClassNode mixin, boolean legacy) throws Exception {
            super(VulkanBootstrapCheck.class.getClassLoader());
            this.legacy = legacy;
            var names = new HashMap<String, String>();
            names.put(COMMON, PREFIX + "Common"); names.put(HELPER, PREFIX + "Helper"); names.put(MIXIN, PREFIX + "Renderer");
            names.put(CLIENT, Type.getInternalName(Client.class));
            names.put("icyllis/modernui/core/Core", Type.getInternalName(CoreBoundary.class));
            names.put("icyllis/arc3d/engine/ContextOptions", Type.getInternalName(Options.class));
            names.put("icyllis/arc3d/engine/DriverBugWorkarounds", Type.getInternalName(Workarounds.class));
            names.put("icyllis/arc3d/vulkan/VulkanBackendContext", Type.getInternalName(Backend.class));
            names.put("icyllis/modernui/mc/VulkanModIntegration", Type.getInternalName(Integration.class));
            names.put("net/neoforged/fml/loading/LoadingModList", Type.getInternalName(Metadata.class));
            names.put("net/neoforged/fml/loading/moddiscovery/ModFileInfo", Type.getInternalName(FileInfo.class));
            names.put("net/neoforged/fml/ModList", Type.getInternalName(ForbiddenModList.class));
            common = copy(commonNode, names, "isVulkanModLoaded", true);
            if (!legacy) copy(Objects.requireNonNull(helper), names, "detectVulkanMod", false);
            renderer = copy(mixin, names, "onInitRenderer", false).getMethod("onInitRenderer", int.class,
                    boolean.class, CallbackInfo.class);
        }
        void flag(boolean value) throws ReflectiveOperationException { common.getField("sVulkanModLoaded").setBoolean(null, value); }
        boolean flag() throws ReflectiveOperationException { return common.getField("sVulkanModLoaded").getBoolean(null); }
        void invoke() throws Exception {
            CoreBoundary.beforeInitialize = () -> {
                if (legacy) return;
                try {
                    require(trace.contains("metadata.lookup:vulkanmod") && flag() == present,
                            "actual helper publishes metadata result before Core.initialize");
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            };
            renderer.invoke(null, 3, true, new CallbackInfo("initRenderer", false));
        }
        private Class<?> copy(ClassNode node, Map<String, String> names, String methodName, boolean flag) {
            var map = new SimpleRemapper(names);
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            String name = names.get(node.name);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
            if (flag) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
                    "sVulkanModLoaded", "Z", null, null).visitEnd();
            var methods = node.methods.stream().filter(method -> method.name.equals(methodName)).toList();
            require(methods.size() == 1, "one actual " + node.name + "." + methodName + " method");
            MethodNode original = methods.getFirst();
            require((original.access & Opcodes.ACC_STATIC) != 0, "copied startup method is static");
            original.accept(new MethodRemapper(writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    original.name, map.mapMethodDesc(original.desc), null, null), map) {
                @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                    if (field.equals("sVulkanModLoaded") && (owner.equals(COMMON) || owner.equals(HELPER))) owner = COMMON;
                    super.visitFieldInsn(opcode, owner, field, descriptor);
                }
            });
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
        }
    }

    public static final class Workarounds {}
    public static final class Backend {}
    public static final class FileInfo {}
    public static final class Options {
        public boolean mUseStagingBuffers, mAllowGLSPIRV;
        public Workarounds mDriverBugWorkarounds;
        public Options() { trace.add("options.new"); }
    }
    public static final class Client {
        public static final Workarounds workarounds = new Workarounds();
        public static String getBootstrapProperty(String key) {
            trace.add("property:" + key);
            if (Objects.equals(key, stagingKey)) return "true";
            if (Objects.equals(key, spirvKey)) return "false";
            throw new AssertionError("Unexpected bootstrap property " + key);
        }
        public static Workarounds getGpuDriverBugWorkarounds() { trace.add("workarounds"); return workarounds; }
    }
    public static final class Metadata {
        private static final Metadata INSTANCE = new Metadata();
        public static Metadata get() { trace.add("metadata.get"); return INSTANCE; }
        public FileInfo getModFileById(String id) {
            trace.add("metadata.lookup:" + id);
            if (!id.equals("vulkanmod")) throw new AssertionError("Unexpected mod lookup " + id);
            return present ? new FileInfo() : null;
        }
    }
    public static final class ForbiddenModList {
        public static ForbiddenModList get() { throw new AssertionError("ModList is unavailable before the mod constructor"); }
        public boolean isLoaded(String id) { throw new AssertionError("Late ModList lookup is forbidden"); }
    }
    public static final class Integration {
        public static Backend wrapContext() {
            trace.add("integration.wrap");
            if (wrapFailure) throw new IllegalStateException("synthetic wrap failure");
            return new Backend();
        }
        public static void configureShaderCaps() { trace.add("integration.caps"); }
    }
    public static final class CoreBoundary {
        public static Runnable beforeInitialize = () -> {};
        public static void initialize() { trace.add("core.initialize"); beforeInitialize.run(); }
        public static boolean initOpenGL(Options options) { trace.add("core.gl"); observedOptions = options; return glSuccess; }
        public static boolean initVulkan(Backend backend, Options options) {
            trace.add("core.vulkan"); observedOptions = options;
            if (backend == null) throw new AssertionError("Missing wrapped Vulkan context");
            return vulkanSuccess;
        }
        public static void glShowCapsErrorDialog() { trace.add("core.glDialog"); }
    }
}
