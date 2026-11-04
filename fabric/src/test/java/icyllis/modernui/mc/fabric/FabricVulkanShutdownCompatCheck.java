package icyllis.modernui.mc.fabric;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

import static org.objectweb.asm.Opcodes.*;

/** Exact upstream ABI and real compiled retention code; isolated CPU fixtures, no Minecraft/native initialization. */
public final class FabricVulkanShutdownCompatCheck {
    private static final String HELPER = "icyllis/modernui/mc/fabric/FabricVulkanShutdownCompat";
    private static final String MEMORY = "net/vulkanmod/vulkan/memory/MemoryManager";
    private static final String BUFFER = "net/vulkanmod/vulkan/memory/buffer/";
    private static final String STAGING = BUFFER + "StagingBuffers", SINGLE = BUFFER + "StagingBuffer";
    private static final String ARRAY = "it/unimi/dsi/fastutil/objects/ObjectArrayList";
    private static final String VBO = "net/vulkanmod/render/VBO", CLOUD = "net/vulkanmod/render/sky/CloudRenderer";
    private static int checks;

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Supply exact Fabric VulkanMod 5597 JAR");
        Path jar = Path.of(args[0]);
        require(sha(Files.readAllBytes(jar)).equals("5597e2b42c9e1a2d8e5cd432306a8cc9cf2939b7b12ccb0503e57e83971d7698"), "exact upstream JAR");
        Map<String, ClassNode> upstream = new HashMap<>();
        Map<String, String> pins = Map.of("net/vulkanmod/vulkan/Vulkan", "7cce80a6437b41696ba7c89966b9d6ec1b62bd1689ac48bae2fac3704f6f81bc",
                STAGING, "29f6d235c47d43945f3d9e507e079eda801112412656ba0098619006f31133c3",
                BUFFER + "Buffer", "89610dfa02fcede64bb404ff61ce94ff7e55d216db63e740a63825d61c3aab84",
                VBO, "a9bad0ae2c664a58d33c2f72228c0e6f5773c9d7caf1fe51322b9cc4e4ac2413",
                CLOUD, "5854f78988fa25c8466c72f87bb8993eba1dbb48070c1d6aa88cf600b5820e8f");
        try (JarFile file = new JarFile(jar.toFile())) {
            for (var pin : pins.entrySet()) try (InputStream in = file.getInputStream(file.getJarEntry(pin.getKey() + ".class"))) {
                byte[] bytes = in.readAllBytes(); require(sha(bytes).equals(pin.getValue()), "exact class " + pin.getKey()); upstream.put(pin.getKey(), read(bytes));
            }
        }
        audit(upstream); exerciseAtShapes(); auditMixin(read(resource("icyllis/modernui/mc/mixin/MixinFabricVulkanShutdown")));
        ClassNode helper = read(resource(HELPER));
        Fixture f = new Fixture(helper, upstream);
        exerciseRetention(f); exerciseOwners(f);
        System.out.println("PASS: Fabric Vulkan shutdown " + checks + " checks; exact 5597 ABI + compiled helper + actual VBO/cloud methods; CPU only, no native calls");
    }

    private static void audit(Map<String, ClassNode> nodes) {
        MethodNode cleanup = method(nodes.get("net/vulkanmod/vulkan/Vulkan"), "cleanUp", "()V");
        String[][] calls = {{"org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle"}, {"net/vulkanmod/vulkan/Renderer", "cleanUpResources"},
                {"net/vulkanmod/vulkan/Vulkan", "freeStagingBuffers"}, {MEMORY, "freeAllBuffers"}, {"org/lwjgl/util/vma/Vma", "vmaDestroyAllocator"}};
        int previous = -1;
        for (String[] call : calls) { int at = callIndex(cleanup, call[0], call[1]); require(at > previous && callCount(cleanup, call[0], call[1]) == 1, "unique ordered cleanup " + call[1]); previous = at; }
        MethodNode free = method(nodes.get(STAGING), "free", "()V");
        require(!fieldRead(free, "currentBuffer"), "upstream free omits current buffer");
        MethodNode wrapper = method(nodes.get("net/vulkanmod/vulkan/Vulkan"), "freeStagingBuffers", "()V");
        require(code(wrapper).stream().filter(i -> i instanceof MethodInsnNode).count() == 1 && callCount(wrapper, STAGING, "free") == 1, "original handoff wrapper only calls pool free; no intervening current access");
        require(fieldRead(free, "availableBuffers") && fieldRead(free, "usedBuffersByFrame") && callCount(free, SINGLE, "scheduleFree") == 2,
                "upstream schedules both managed pool paths");
        method(nodes.get(BUFFER + "Buffer"), "getId", "()J"); method(nodes.get(BUFFER + "Buffer"), "getAllocation", "()J");
        require(field(nodes.get(BUFFER + "Buffer"), "id").desc.equals("J") && field(nodes.get(BUFFER + "Buffer"), "allocation").desc.equals("J"), "live buffer fields are longs");
        MethodNode close = method(nodes.get(VBO), "close", "()V");
        require(fieldRead(close, "autoIndexed") && callCount(close, BUFFER + "VertexBuffer", "scheduleFree") == 1
                && callCount(close, BUFFER + "IndexBuffer", "scheduleFree") == 1, "VBO has guarded shared-index and vertex retirement");
        MethodNode reset = method(nodes.get(CLOUD), "resetBuffer", "()V");
        require(callCount(reset, VBO, "close") == 1 && code(reset).stream().anyMatch(i -> i instanceof FieldInsnNode v && v.getOpcode() == PUTFIELD && v.name.equals("cloudBuffer")), "cloud reset closes and clears owner");
    }

    private static void auditMixin(ClassNode mixin) {
        MethodNode redirect = mixin.methods.stream().filter(m -> annotation(m, "Lorg/spongepowered/asm/mixin/injection/Redirect;") != null).findFirst().orElseThrow();
        AnnotationNode r = annotation(redirect, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        require(Objects.equals(value(r, "method"), List.of("cleanUp()V")) && Objects.equals(value(r, "require"), 1), "redirect exact cleanup, require=1");
        AnnotationNode at = oneAt(value(r, "at"));
        require(Objects.equals(value(at, "value"), "INVOKE") && value(at, "shift") == null &&
                Objects.equals(value(at, "target"), "Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I"), "redirect exact native idle, default shift");
        int idle = callIndex(redirect, "org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle"), hook = callIndex(redirect, HELPER, "afterDeviceIdle");
        require(callCount(redirect, "org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle") == 1 && callCount(redirect, HELPER, "afterDeviceIdle") == 1 && idle < hook, "native idle once then helper");
        require(code(redirect).subList(idle + 1, hook).stream().anyMatch(i -> i.getOpcode() == ATHROW), "failed native idle throws before helper");
        List<AbstractInsnNode> body = code(redirect);
        require(body.get(idle + 1) instanceof VarInsnNode store && store.getOpcode() == ISTORE && body.get(body.size() - 2) instanceof VarInsnNode load && load.getOpcode() == ILOAD && load.var == store.var && body.getLast().getOpcode() == IRETURN, "original successful idle result returned");
        MethodNode inject = mixin.methods.stream().filter(m -> annotation(m, "Lorg/spongepowered/asm/mixin/injection/Inject;") != null).findFirst().orElseThrow();
        AnnotationNode a = annotation(inject, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        require(Objects.equals(value(a, "method"), List.of("cleanUp()V")) && Objects.equals(value(a, "require"), 1) && !Boolean.TRUE.equals(value(a, "cancellable")), "staging exact required noncancelling hook");
        AnnotationNode before = oneAt(value(a, "at"));
        require(Objects.equals(value(before, "value"), "INVOKE") && Objects.equals(value(before, "target"), "Lnet/vulkanmod/vulkan/Vulkan;freeStagingBuffers()V") && value(before, "shift") == null && callCount(inject, HELPER, "beforeStagingFree") == 1, "handoff immediately before original pool drain");
        require(redirect.tryCatchBlocks.isEmpty() && inject.tryCatchBlocks.isEmpty(), "mixin propagates failed contract/native idle");
    }

    // Fabric Mixin 0.17.4 changed Redirect.at from At to At[]; Inject.at is also
    // an array. Both representations must describe exactly one real @At node.
    private static AnnotationNode oneAt(Object raw) {
        Object node = raw;
        if (raw instanceof List<?> sites) {
            require(sites.size() == 1, "exactly one injection site");
            node = sites.getFirst();
        }
        require(node instanceof AnnotationNode at && at.desc.equals("Lorg/spongepowered/asm/mixin/injection/At;"),
                "exact @At annotation node");
        return (AnnotationNode) node;
    }

    private static void exerciseAtShapes() {
        var at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        require(oneAt(at) == at && oneAt(List.of(at)) == at, "scalar and singleton-array @At preserve the same node");
        for (Object bad : List.of(List.of(), List.of(at, at), List.of("wrong"), "wrong",
                new AnnotationNode("Lnot/At;"))) {
            boolean rejected = false;
            try { oneAt(bad); } catch (AssertionError expected) { rejected = true; }
            require(rejected, "malformed, empty or multiple injection sites rejected");
        }
    }

    private static void exerciseRetention(Fixture f) throws Exception {
        Object staging = f.staging(null); f.retain(staging); require(f.current(staging) == null, "null current no-op");
        Object a = f.buffer(), b = f.buffer(); staging = f.staging(a); f.available(staging).add(b);
        f.retain(staging); require(f.current(staging) == null && f.available(staging).size() == 2 && f.available(staging).get(1) == a, "unique current retained by identity, then detached");
        f.retain(staging); require(f.available(staging).size() == 2, "repeat retention is a no-op");
        for (boolean inAvailable : new boolean[]{true, false}) {
            staging = f.staging(a); (inAvailable ? f.available(staging) : f.used(staging, 0)).add(a);
            f.retain(staging); require(f.current(staging) == null && f.available(staging).size() + f.used(staging, 0).size() == 1, "already managed current is not added twice");
        }
        for (String bad : List.of("duplicate-available", "duplicate-across", "duplicate-used", "foreign", "null-entry", "closed-current", "unallocated-current", "foreign-list", "subclass-buffer", "registry-absent", "registry-stale", "queued-twice", "queued-and-pool", "queued-wrong-allocation", "null-frame")) {
            Object current = f.buffer(); staging = f.staging(current); Object good = f.buffer(); f.available(staging).add(good);
            switch (bad) {
                case "duplicate-available" -> f.available(staging).add(good);
                case "duplicate-across" -> f.used(staging, 0).add(good);
                case "duplicate-used" -> { f.used(staging, 0).add(a); f.used(staging, 1).add(a); }
                case "foreign" -> f.used(staging, 0).add(new Object());
                case "null-entry" -> f.used(staging, 0).add(null);
                case "closed-current" -> f.buffer.getSuperclass().getField("id").setLong(current, 0);
                case "unallocated-current" -> f.buffer.getSuperclass().getField("allocation").setLong(current, 0);
                case "foreign-list" -> f.staging.getField("availableBuffers").set(staging, f.foreignList.getConstructor().newInstance());
                case "subclass-buffer" -> f.staging.getField("currentBuffer").set(staging, f.subclass.getConstructor().newInstance());
                case "registry-absent" -> f.registry().remove(f.id(current));
                case "registry-stale" -> f.registry().put(f.id(current), new Object());
                case "queued-twice" -> { f.queue(current); f.queue(current); }
                case "queued-and-pool" -> { f.queue(current); f.available(staging).add(current); }
                case "queued-wrong-allocation" -> f.info.getField("allocation").setLong(f.queue(current), 999);
                case "null-frame" -> Array.set(f.staging.getField("usedBuffersByFrame").get(staging), 1, null);
            }
            Object oldAvailable = f.staging.getField("availableBuffers").get(staging), oldUsed = f.staging.getField("usedBuffersByFrame").get(staging);
            List<?> before = new ArrayList<>((List<?>) oldAvailable); List<Object> frames = new ArrayList<>();
            for (int n = 0; n < 2; n++) frames.add(f.used(staging, n) == null ? null : new ArrayList<>(f.used(staging, n)));
            Object expectedCurrent = f.current(staging); Object[] queueArray = f.queues(); List<List<?>> queueBefore = new ArrayList<>();
            for (Object queue : queueArray) queueBefore.add(new ArrayList<>((List<?>) queue));
            Map<Long,Object> registryBefore = new HashMap<>(f.registry()); Throwable error = f.failure(staging);
            require(error instanceof RuntimeException, "preflight rejects " + bad + ": " + error);
            require(f.current(staging) == expectedCurrent && f.staging.getField("availableBuffers").get(staging) == oldAvailable
                    && f.staging.getField("usedBuffersByFrame").get(staging) == oldUsed && identityEqual((List<?>) oldAvailable, before)
                    && identityEqual(f.used(staging, 0), (List<?>) frames.get(0)) && identityEqual(f.used(staging, 1), (List<?>) frames.get(1)), "failure has no partial mutation: " + bad);
            require(f.queues() == queueArray && identityEqual((List<?>) queueArray[0], queueBefore.get(0)) && identityEqual((List<?>) queueArray[1], queueBefore.get(1))
                    && f.registry().keySet().equals(registryBefore.keySet()) && registryBefore.entrySet().stream().allMatch(e -> { try { return f.registry().get(e.getKey()) == e.getValue(); } catch (Exception failure) { throw new AssertionError(failure); } }), "failure preserves deferred queues and registry: " + bad);
        }
        Object queued = f.buffer(); staging = f.staging(queued); f.queue(queued); f.retain(staging);
        require(f.current(staging) == null && f.available(staging).isEmpty(), "already queued current detaches without entering another retirement pool");
        require(f.count() == 0, "retention never calls scheduleFree/getters/native methods");
        staging = f.staging(f.buffer()); f.used(staging, 0).add(f.buffer()); f.available(staging).add(f.buffer()); f.retain(staging);
        int before = f.count();
        for (int n = 0; n < 2; n++) for (Object owner : f.used(staging, n)) f.base.getMethod("scheduleFree").invoke(owner);
        while (!f.available(staging).isEmpty()) f.base.getMethod("scheduleFree").invoke(f.available(staging).removeLast());
        require(f.current(staging) == null && f.count() == before + 3, "audited upstream pool-drain model schedules each retained/pooled identity once");
        require(f.failure(new Object()) instanceof RuntimeException, "foreign staging owner rejected");
    }

    private static void exerciseOwners(Fixture f) throws Exception {
        for (boolean shared : new boolean[]{false, true}) {
            Object owner = f.vbo.getConstructor().newInstance(), vertex = f.vertex.getConstructor().newInstance(), index = f.index.getConstructor().newInstance();
            f.vbo.getField("vertexCount").setInt(owner, 3); f.vbo.getField("indexCount").setInt(owner, 3); f.vbo.getField("autoIndexed").setBoolean(owner, shared);
            f.vbo.getField("vertexBuffer").set(owner, vertex); f.vbo.getField("indexBuffer").set(owner, index);
            int before = f.count(); f.vbo.getMethod("close").invoke(owner);
            require(f.count() == before + (shared ? 1 : 2) && f.vbo.getField("vertexBuffer").get(owner) == null
                    && f.vbo.getField("vertexCount").getInt(owner) == 0 && (shared ? f.vbo.getField("indexBuffer").get(owner) == index : f.vbo.getField("indexBuffer").get(owner) == null), "actual close preserves shared auto-index ownership");
            f.vbo.getMethod("close").invoke(owner); require(f.count() == before + (shared ? 1 : 2), "actual close idempotent");
            Object cloudOwner = f.vbo.getConstructor().newInstance(); f.vbo.getField("vertexCount").setInt(cloudOwner, 3); f.vbo.getField("autoIndexed").setBoolean(cloudOwner, shared);
            f.vbo.getField("vertexBuffer").set(cloudOwner, f.vertex.getConstructor().newInstance()); f.vbo.getField("indexBuffer").set(cloudOwner, f.index.getConstructor().newInstance());
            Object cloud = f.cloud.getConstructor().newInstance(); f.cloud.getField("cloudBuffer").set(cloud, cloudOwner);
            f.cloud.getMethod("resetBuffer").invoke(cloud); f.cloud.getMethod("resetBuffer").invoke(cloud);
            require(f.cloud.getField("cloudBuffer").get(cloud) == null && f.count() == before + (shared ? 2 : 4), "actual cloud reset clears owner and repeats safely");
        }
    }

    private static final class Fixture extends ClassLoader {
        final Class<?> buffer, subclass, staging, array, base, vertex, index, vbo, cloud, memory, info, foreignList; long nextId; final Method retain;
        Fixture(ClassNode helper, Map<String, ClassNode> upstream) throws Exception {
            super(FabricVulkanShutdownCompatCheck.class.getClassLoader());
            base = define(BUFFER + "Buffer", bufferBase()); buffer = define(SINGLE, simple(SINGLE, BUFFER + "Buffer")); subclass = define(SINGLE + "$Foreign", simple(SINGLE + "$Foreign", SINGLE));
            ClassWriter stack = new ClassWriter(0); stack.visit(V21, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, "it/unimi/dsi/fastutil/Stack", null, "java/lang/Object", null); stack.visitEnd(); define("it/unimi/dsi/fastutil/Stack", stack.toByteArray());
            array = define(ARRAY, stackList(ARRAY)); foreignList = define(ARRAY + "$Foreign", stackList(ARRAY + "$Foreign"));
            memory = define(MEMORY, memoryClass()); info = define(BUFFER + "Buffer$BufferInfo", fields(BUFFER + "Buffer$BufferInfo", Map.of("id", "J", "allocation", "J"), null));
            memory.getField("buffers").set(null, new HashMap<Long, Object>()); memory.getField("INSTANCE").set(null, memory.getConstructor().newInstance());
            staging = define(STAGING, fields(STAGING, Map.of("currentBuffer", "L" + SINGLE + ";", "availableBuffers", "Lit/unimi/dsi/fastutil/Stack;", "usedBuffersByFrame", "[L" + ARRAY + ";"), null));
            vertex = define(BUFFER + "VertexBuffer", simple(BUFFER + "VertexBuffer", BUFFER + "Buffer")); index = define(BUFFER + "IndexBuffer", simple(BUFFER + "IndexBuffer", BUFFER + "Buffer"));
            vbo = define(VBO, fields(VBO, Map.of("vertexCount", "I", "indexCount", "I", "autoIndexed", "Z", "vertexBuffer", "L" + BUFFER + "VertexBuffer;", "indexBuffer", "L" + BUFFER + "IndexBuffer;"), method(upstream.get(VBO), "close", "()V")));
            cloud = define(CLOUD, fields(CLOUD, Map.of("cloudBuffer", "L" + VBO + ";"), method(upstream.get(CLOUD), "resetBuffer", "()V")));
            byte[] transplanted = transplant(helper); Class<?> real = define(HELPER, transplanted); retain = real.getDeclaredMethod("retainCurrentStagingBuffer", Object.class); retain.setAccessible(true);
        }
        Class<?> define(String n, byte[] bytes) { return defineClass(n.replace('/', '.'), bytes, 0, bytes.length); }
        Object buffer() throws Exception { Object b = buffer.getConstructor().newInstance(); base.getField("id").setLong(b, ++nextId); base.getField("allocation").setLong(b, 2); registry().put(nextId, b); return b; }
        Object staging(Object current) throws Exception { memory.getField("freeableBuffers").set(memory.getField("INSTANCE").get(null), new Object[]{array.getConstructor().newInstance(),array.getConstructor().newInstance()}); Object s = staging.getConstructor().newInstance(); staging.getField("currentBuffer").set(s, current); staging.getField("availableBuffers").set(s, array.getConstructor().newInstance()); Object frames = Array.newInstance(array, 2); for (int i = 0; i < 2; i++) Array.set(frames, i, array.getConstructor().newInstance()); staging.getField("usedBuffersByFrame").set(s, frames); return s; }
        Object current(Object s) throws Exception { return staging.getField("currentBuffer").get(s); }
        @SuppressWarnings("unchecked") List<Object> available(Object s) throws Exception { return (List<Object>) staging.getField("availableBuffers").get(s); }
        @SuppressWarnings("unchecked") List<Object> used(Object s, int n) throws Exception { return (List<Object>) Array.get(staging.getField("usedBuffersByFrame").get(s), n); }
        void retain(Object s) throws Exception { retain.invoke(null, s); }
        Throwable failure(Object s) throws Exception { try { retain(s); return null; } catch (InvocationTargetException e) { return e.getCause(); } }
        int count() throws Exception { return base.getField("calls").getInt(null); }
        @SuppressWarnings("unchecked") Map<Long,Object> registry() throws Exception { return (Map<Long,Object>) memory.getField("buffers").get(null); }
        long id(Object b) throws Exception { return base.getField("id").getLong(b); }
        Object[] queues() throws Exception { return (Object[]) memory.getField("freeableBuffers").get(memory.getField("INSTANCE").get(null)); }
        @SuppressWarnings("unchecked") Object queue(Object b) throws Exception { Object row = info.getConstructor().newInstance(); info.getField("id").setLong(row,id(b)); info.getField("allocation").setLong(row,base.getField("allocation").getLong(b)); ((List<Object>) queues()[0]).add(row); return row; }
    }

    private static byte[] transplant(ClassNode source) {
        MethodNode entry = method(source, "retainCurrentStagingBuffer", "(Ljava/lang/Object;)V");
        Set<MethodNode> chosen = new LinkedHashSet<>(); Deque<MethodNode> pending = new ArrayDeque<>(); pending.add(entry);
        while (!pending.isEmpty()) {
            MethodNode m = pending.removeFirst(); if (!chosen.add(m)) continue;
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof MethodInsnNode call) {
                    require((call.owner.startsWith("java/") || call.owner.equals(HELPER)) && !call.name.equals("scheduleFree") && !call.name.equals("getId") && !call.name.equals("getAllocation"), "retention does not invoke native/retirement/getter: " + call.owner + "." + call.name);
                    if (call.owner.equals(HELPER)) pending.add(method(source, call.name, call.desc));
                }
                if (i instanceof InvokeDynamicInsnNode indy) for (Object arg : indy.bsmArgs) if (arg instanceof Handle h && h.getOwner().equals(HELPER)) pending.add(method(source, h.getName(), h.getDesc()));
            }
        }
        ClassWriter w = writer(HELPER, "java/lang/Object");
        for (FieldNode field : source.fields) if (field.value != null) field.accept(w);
        for (MethodNode m : chosen) m.accept(w); w.visitEnd(); return w.toByteArray();
    }
    private static byte[] stackList(String name) { ClassNode n = read(simple(name, "java/util/ArrayList")); n.interfaces.add("it/unimi/dsi/fastutil/Stack"); ClassWriter w = new ClassWriter(0); n.accept(w); return w.toByteArray(); }
    private static byte[] memoryClass() { ClassWriter w = writer(MEMORY, "java/lang/Object"); w.visitField(ACC_PUBLIC | ACC_STATIC,"buffers","Ljava/util/Map;",null,null).visitEnd(); w.visitField(ACC_PUBLIC | ACC_STATIC,"INSTANCE","L"+MEMORY+";",null,null).visitEnd(); w.visitField(ACC_PUBLIC,"freeableBuffers","[Ljava/lang/Object;",null,null).visitEnd(); w.visitEnd(); return w.toByteArray(); }
    private static byte[] bufferBase() {
        ClassWriter w = writer(BUFFER + "Buffer", "java/lang/Object");
        for (String field : List.of("id", "allocation")) w.visitField(ACC_PUBLIC, field, "J", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC, "calls", "I", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "scheduleFree", "()V", null, null); m.visitCode(); m.visitFieldInsn(GETSTATIC, BUFFER + "Buffer", "calls", "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD); m.visitFieldInsn(PUTSTATIC, BUFFER + "Buffer", "calls", "I"); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        MethodVisitor equal = w.visitMethod(ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null); equal.visitCode(); equal.visitInsn(ICONST_1); equal.visitInsn(IRETURN); equal.visitMaxs(0, 0); equal.visitEnd();
        MethodVisitor hash = w.visitMethod(ACC_PUBLIC, "hashCode", "()I", null, null); hash.visitCode(); hash.visitInsn(ICONST_0); hash.visitInsn(IRETURN); hash.visitMaxs(0, 0); hash.visitEnd();
        for (String name : List.of("getId", "getAllocation")) { m = w.visitMethod(ACC_PUBLIC, name, "()J", null, null); m.visitCode(); m.visitTypeInsn(NEW, "java/lang/AssertionError"); m.visitInsn(DUP); m.visitLdcInsn("getter override sentinel"); m.visitMethodInsn(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false); m.visitInsn(ATHROW); m.visitMaxs(0, 0); m.visitEnd(); }
        w.visitEnd(); return w.toByteArray();
    }
    private static byte[] fields(String name, Map<String, String> fields, MethodNode real) { ClassWriter w = writer(name, "java/lang/Object"); fields.forEach((n, d) -> w.visitField(ACC_PUBLIC, n, d, null, null).visitEnd()); if (real != null) real.accept(w); w.visitEnd(); return w.toByteArray(); }
    private static byte[] simple(String name, String parent) { ClassWriter w = writer(name, parent); w.visitEnd(); return w.toByteArray(); }
    private static ClassWriter writer(String name, String parent) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(V21, ACC_PUBLIC, name, null, parent, null); MethodVisitor m = w.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); m.visitCode(); m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false); m.visitInsn(RETURN); m.visitMaxs(1, 1); m.visitEnd(); return w; }
    private static boolean identityEqual(List<?> a, List<?> b) { if (a == null || b == null) return a == b; if (a.size() != b.size()) return false; for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false; return true; }
    private static byte[] resource(String name) throws Exception { try (InputStream in = FabricVulkanShutdownCompatCheck.class.getClassLoader().getResourceAsStream(name + ".class")) { require(in != null, "compiled helper exists"); return in.readAllBytes(); } }
    private static ClassNode read(byte[] bytes) { ClassNode n = new ClassNode(); new ClassReader(bytes).accept(n, ClassReader.SKIP_DEBUG); return n; }
    private static MethodNode method(ClassNode n, String name, String desc) { return n.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(() -> new AssertionError("Missing " + n.name + "." + name + desc)); }
    private static FieldNode field(ClassNode n, String name) { return n.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> code(MethodNode m) { List<AbstractInsnNode> r = new ArrayList<>(); for (AbstractInsnNode i : m.instructions) if (i.getOpcode() >= 0) r.add(i); return r; }
    private static int callCount(MethodNode m, String owner, String name) { return (int) code(m).stream().filter(i -> i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name)).count(); }
    private static int callIndex(MethodNode m, String owner, String name) { List<AbstractInsnNode> b = code(m); for (int i = 0; i < b.size(); i++) if (b.get(i) instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name)) return i; return -1; }
    private static boolean fieldRead(MethodNode m, String name) { return code(m).stream().anyMatch(i -> i instanceof FieldInsnNode f && (f.getOpcode() == GETFIELD || f.getOpcode() == GETSTATIC) && f.name.equals(name)); }
    private static AnnotationNode annotation(MethodNode m, String desc) { for (List<AnnotationNode> list : List.of(m.visibleAnnotations == null ? List.<AnnotationNode>of() : m.visibleAnnotations, m.invisibleAnnotations == null ? List.<AnnotationNode>of() : m.invisibleAnnotations)) for (AnnotationNode a : list) if (a.desc.equals(desc)) return a; return null; }
    private static Object value(AnnotationNode a, String name) { if (a.values != null) for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals(name)) return a.values.get(i + 1); return null; }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
