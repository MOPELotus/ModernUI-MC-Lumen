package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Real Neo HEAD hook/helper and real preloader/getters; shader constructors, provider and SDF caps are CPU boundaries. */
public final class VulkanTextShaderPreloadCheck {
    private static final String TEXT = "icyllis/modernui/mc/text/TextRenderType";
    private static final String MANAGER = "icyllis/modernui/mc/neoforge/UIManagerForge";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinGameRendererEXT";
    private static final String GENERATED = "icyllis/modernui/mc/test/VulkanTextShaderExecuted";
    private static int checks, creations, providers, toggles, logs;
    private static boolean fail;
    private static Class<?> currentText;
    private static final List<String> names = new ArrayList<>();
    private static final Shader vanilla = new Shader("vanilla");

    public static void main(String[] args) throws Exception {
        var text = read(TEXT, null);
        structural(text);
        var mixin = read(MIXIN, null);
        var head = mixin.methods.stream().filter(m -> m.name.equals("onRenderFrameStart")).findFirst().orElseThrow();
        var headAnnotations = new ArrayList<AnnotationNode>();
        if (head.visibleAnnotations != null) headAnnotations.addAll(head.visibleAnnotations);
        if (head.invisibleAnnotations != null) headAnnotations.addAll(head.invisibleAnnotations);
        require(headAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Inject;") && at(a, "HEAD") &&
                List.of("render(Lnet/minecraft/client/DeltaTracker;Z)V").equals(annotationValue(a, "method"))),
                "real Neo startup hook injects at exact GameRenderer render HEAD descriptor");
        var run = new Executed(text, read(MANAGER, null), mixin);
        for (String getter : List.of("getShaderNormal", "getShaderSDFFill", "getShaderSDFStroke")) {
            reset(run, true, true);
            require(run.get(getter) == null, "cancelled original TAIL leaves " + getter + " uninitialized before replacement HEAD");
            run.start();
            require(run.get(getter) != null && creations == 3 && providers == 1 && toggles == 1,
                    "real HEAD initializes all three shaders before first " + getter);
            require(names.equals(List.of("rendertype_modern_text_normal", "rendertype_modern_text_sdf_fill", "rendertype_modern_text_sdf_stroke")),
                    "actual preloader preserves all three shader identifiers");
            Object cached = run.get(getter);
            run.start(); run.preload();
            require(run.get(getter) == cached && creations == 3 && providers == 1 && logs == 1,
                    "synchronized actual preloader returns cached shaders without reconstruction");
        }
        reset(run, false, true); run.start();
        require(creations == 0 && providers == 0, "GL HEAD performs no shader preload");
        reset(run, true, false); run.start();
        require(creations == 0 && providers == 0, "disabled text engine performs no shader preload");
        reset(run, false, false); run.start();
        require(creations == 0, "GL disabled branch remains inert");
        reset(run, true, true);
        Layout.sCurrentInWorldRendering = true; Layout.sUseTextShadersInWorld = false;
        require(run.get("getShaderNormal") == vanilla && run.get("getShaderSDFFill") == vanilla && creations == 0,
                "existing world vanilla normal/fill getters remain unchanged");
        reset(run, true, true); fail = true;
        try { run.start(); throw new AssertionError("Expected shader loading failure"); }
        catch (InvocationTargetException exception) {
            require(exception.getCause() instanceof IllegalStateException && exception.getCause().getCause() instanceof IOException,
                    "actual preloader propagates shader IO failure through real HEAD/helper");
        }
        require(run.get("getShaderNormal") == null && creations == 1 && toggles == 0 && logs == 0,
                "failed shader loading does not silently publish a success or current SDF selection");
        Path old = null;
        for (String argument : args) {
            Path jar = Path.of(argument);
            try (var file = new JarFile(jar.toFile())) {
                if (file.getJarEntry(TEXT + ".class") != null) old = jar;
                else if (file.getJarEntry("net/vulkanmod/mixin/render/GameRendererMixin.class") != null) auditCancellation(jar);
            }
        }
        if (old != null) {
            Path jar = old;
            var legacy = new Executed(read(TEXT, jar), null, null);
            reset(legacy, true, true);
            require(legacy.get("getShaderNormal") == null && legacy.get("getShaderSDFFill") == null && legacy.get("getShaderSDFStroke") == null,
                    "old real getters return null when Neo cancels the original preload TAIL");
            require(read(MANAGER, jar).methods.stream().noneMatch(m -> m.name.equals("onVulkanRenderFrameStart")),
                    "old Neo manager lacks replacement preload hook");
            System.out.println("Old-JAR cancelled-preload negative reproduced: " + jar + " sha256=" +
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        }
        System.out.println("Vulkan text shader preload: " + checks + " real-hook/preloader CPU and provider structural checks passed; no GPU constructors executed");
    }
    private static void reset(Executed run, boolean vk, boolean text) throws Exception {
        currentText = run.text;
        for (var field : run.text.getFields()) field.set(null, null);
        Env.vulkan = vk; Env.text = text; Layout.sCurrentInWorldRendering = false; Layout.sUseTextShadersInWorld = true;
        creations = providers = toggles = logs = 0; fail = false; names.clear();
    }
    public record Shader(String name) {}
    public static final class Env {
        public static boolean vulkan, text;
        public static final Log LOGGER = new Log();
        public static boolean isVulkanModLoaded() { return vulkan; }
        public static boolean isTextEngineEnabled() { return text; }
        public static String location(String name) { return name; }
    }
    public static final class Layout {
        public static boolean sCurrentInWorldRendering, sUseTextShadersInWorld;
        public static final Object MARKER = new Object();
    }
    public static final class Log { public void info(Object marker, String message) { logs++; } }
    public static final class Formats { public static final Object POSITION_COLOR_TEX_LIGHTMAP = new Object(); }
    public static final class Vanilla { public static Shader getRendertypeTextShader() { return vanilla; } }
    public static final class Factory {
        public static Factory get() { return new Factory(); }
        public Shader makeShaderInstance(Object provider, String name, Object format) throws IOException {
            require(provider != null && format == Formats.POSITION_COLOR_TEX_LIGHTMAP, "actual preloader passes provider and correct vertex format");
            creations++; names.add(name);
            if (fail) throw new IOException("synthetic shader load failure");
            return new Shader(name);
        }
    }
    public static final class Boundaries {
        public static Object provider() { providers++; return new Object(); }
        public static void toggle(boolean smart) throws Exception {
            require(!smart, "real preload selects ordinary SDF shaders without changing smart/caps behavior"); toggles++;
            currentText.getField("sCurrentShaderSDFFill").set(null, currentText.getField("sShaderSDFFill").get(null));
            currentText.getField("sCurrentShaderSDFStroke").set(null, currentText.getField("sShaderSDFStroke").get(null));
        }
    }
    private static void auditCancellation(Path jar) throws Exception {
        var vm = read("net/vulkanmod/mixin/render/GameRendererMixin", jar);
        var candidate = vm.methods.stream().filter(m -> m.name.equals("preloadUiShader")).findFirst();
        if (candidate.isEmpty()) return;
        var method = candidate.orElseThrow();
        var annotations = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        var inject = annotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        require(Boolean.TRUE.equals(annotationValue(inject, "cancellable")), "actual VM preload injection is cancellable: " + jar.getFileName());
        require(at(inject, "HEAD"), "actual VM preload injection runs at HEAD: " + jar.getFileName());
        require(Arrays.stream(method.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode c &&
                c.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo") && c.name.equals("cancel")),
                "actual VM callback invokes cancel: " + jar.getFileName());
        var mui = read("icyllis/modernui/mc/text/mixin/MixinGameRenderer", null);
        require(mui.methods.stream().anyMatch(m -> {
            var all = new ArrayList<AnnotationNode>();
            if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
            if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
            return all.stream().anyMatch(a -> a.desc.endsWith("/Inject;") &&
                    List.of("preloadUiShader").equals(annotationValue(a, "method")) && at(a, "TAIL"));
        }), "actual MUI common preload injection uses cancelled vanilla TAIL");
    }
    private static Object annotationValue(AnnotationNode node, String key) {
        for (int i = 0; i < node.values.size(); i += 2) if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        return null;
    }
    private static boolean at(AnnotationNode node, String location) {
        return annotationValue(node, "at") instanceof List<?> list && list.stream().anyMatch(a ->
                a instanceof AnnotationNode annotation && location.equals(annotationValue(annotation, "value")));
    }
    private static void structural(ClassNode text) {
        var preload = text.methods.stream().filter(m -> m.name.equals("preloadShaders")).findFirst().orElseThrow();
        require((preload.access & Opcodes.ACC_SYNCHRONIZED) != 0, "actual preloader remains synchronized");
        var provider = text.methods.stream().filter(m -> m.name.equals("obtainResourceProvider")).findFirst().orElseThrow();
        require(Arrays.stream(provider.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode c && c.name.equals("getVanillaPackResources")),
                "actual resource provider retains vanilla pack source");
        require(Arrays.stream(provider.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode c && c.name.equals("asProvider")),
                "actual resource provider retains vanilla fallback provider");
        require(text.methods.stream().anyMatch(m -> m.name.startsWith("lambda$obtainResourceProvider$") &&
                Arrays.stream(m.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode c && c.name.equals("getResourceAsStream")) &&
                Arrays.stream(m.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode c && c.name.equals("getResource"))),
                "actual provider lambda keeps bundled class resources and fallback lookup");
        require(preload.tryCatchBlocks.stream().anyMatch(t -> "java/io/IOException".equals(t.type)), "actual preloader handles IOException explicitly");
        for (String field : List.of("sShaderNormal", "sCurrentShaderSDFFill", "sCurrentShaderSDFStroke"))
            require(text.fields.stream().anyMatch(f -> f.name.equals(field) && (f.access & Opcodes.ACC_VOLATILE) != 0), "published shader field remains volatile: " + field);
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
    private static ClassNode read(String owner, Path jar) throws Exception {
        var node = new ClassNode();
        if (jar != null) try (var file = new JarFile(jar.toFile()); var in = file.getInputStream(file.getJarEntry(owner + ".class"))) {
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
        } else if (owner.equals(MANAGER) || owner.equals(MIXIN)) try (var in = Files.newInputStream(Path.of("neoforge/build/classes/java/main", owner + ".class"))) {
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
        } else try (var in = VulkanTextShaderPreloadCheck.class.getResourceAsStream("/" + owner + ".class")) {
            if (in == null) throw new AssertionError("Missing compiled " + owner);
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
        }
        return node;
    }
    private static final class Executed extends ClassLoader {
        final Class<?> text;
        final Object renderer;
        final Method start;
        Executed(ClassNode textNode, ClassNode manager, ClassNode mixin) throws Exception {
            super(VulkanTextShaderPreloadCheck.class.getClassLoader());
            var names = new HashMap<String,String>();
            names.put(TEXT, GENERATED + "Text"); names.put(MANAGER, GENERATED + "Manager"); names.put(MIXIN, GENERATED + "Renderer");
            names.put("icyllis/modernui/mc/ModernUIMod", Type.getInternalName(Env.class));
            names.put("icyllis/modernui/mc/text/TextLayoutEngine", Type.getInternalName(Layout.class));
            names.put("icyllis/modernui/mc/MuiModApi", Type.getInternalName(Factory.class));
            names.put("net/minecraft/client/renderer/ShaderInstance", Type.getInternalName(Shader.class));
            names.put("net/minecraft/client/renderer/GameRenderer", Type.getInternalName(Vanilla.class));
            names.put("net/minecraft/server/packs/resources/ResourceProvider", "java/lang/Object");
            names.put("net/minecraft/resources/ResourceLocation", "java/lang/String");
            names.put("com/mojang/blaze3d/vertex/VertexFormat", "java/lang/Object");
            names.put("com/mojang/blaze3d/vertex/DefaultVertexFormat", Type.getInternalName(Formats.class));
            names.put("org/apache/logging/log4j/Logger", Type.getInternalName(Log.class)); names.put("org/apache/logging/log4j/Marker", "java/lang/Object");
            names.put("net/minecraft/client/DeltaTracker", "java/lang/Object");
            names.put("org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "java/lang/Object");
            var remapper = new SimpleRemapper(names);
            text = copy(textNode, remapper, Set.of("getShaderNormal", "getShaderSDFFill", "getShaderSDFStroke", "preloadShaders"), true);
            if (manager != null) {
                copy(manager, remapper, Set.of("onVulkanRenderFrameStart"), false);
                Class<?> rendererType = copy(mixin, remapper, Set.of("onRenderFrameStart"), false);
                renderer = rendererType.getConstructor().newInstance();
                start = rendererType.getMethod("onRenderFrameStart", Object.class, boolean.class, Object.class);
            } else { renderer = null; start = null; }
        }
        Class<?> copy(ClassNode node, SimpleRemapper remapper, Set<String> selected, boolean fields) {
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            String name = remapper.mapType(node.name);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
            var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(1,1); ctor.visitEnd();
            if (fields) for (var field : node.fields) if (field.name.startsWith("sShader") || field.name.startsWith("sCurrentShader"))
                writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | (field.access & Opcodes.ACC_VOLATILE), field.name, remapper.mapDesc(field.desc), null, null).visitEnd();
            for (var method : node.methods) if (selected.contains(method.name)) method.accept(new MethodRemapper(writer.visitMethod(
                    Opcodes.ACC_PUBLIC | (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED)), method.name, remapper.mapMethodDesc(method.desc), null, null), remapper) {
                @Override public void visitMethodInsn(int opcode, String owner, String methodName, String descriptor, boolean itf) {
                    if (owner.equals(TEXT) && methodName.equals("obtainResourceProvider")) {
                        super.visitMethodInsn(opcode, Type.getInternalName(Boundaries.class), "provider", descriptor, false); return;
                    }
                    if (owner.equals(TEXT) && methodName.equals("toggleSDFShaders")) {
                        super.visitMethodInsn(opcode, Type.getInternalName(Boundaries.class), "toggle", descriptor, false); return;
                    }
                    if (owner.equals("org/apache/logging/log4j/Logger")) { opcode = Opcodes.INVOKEVIRTUAL; itf = false; }
                    super.visitMethodInsn(opcode, owner, methodName, descriptor, itf);
                }
            });
            writer.visitEnd(); byte[] bytes = writer.toByteArray();
            return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
        }
        Object get(String getter) throws Exception { return text.getMethod(getter).invoke(null); }
        void preload() throws Exception { text.getMethod("preloadShaders").invoke(null); }
        void start() throws Exception { start.invoke(renderer, null, true, null); }
    }
}
