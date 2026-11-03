package icyllis.modernui.mc.neoforge;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

import static org.objectweb.asm.Opcodes.*;

/** Exact upstream bytecode plus the compiled retirement method; never initializes Minecraft or Vulkan. */
public final class ReforgedShutdownCompatCheck {
    private static final String SHADER = "net/vulkanmod/vulkan/shader/";
    private static final String PIPELINE = SHADER + "Pipeline";
    private static final String GRAPHICS = SHADER + "GraphicsPipeline";
    private static final String HELPER = "icyllis/modernui/mc/neoforge/ReforgedShutdownCompat";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinReforgedVulkanShutdown";
    private static final String VULKAN = "net/vulkanmod/vulkan/Vulkan";
    private static final String MEMORY = "net/vulkanmod/vulkan/memory/MemoryManager";
    private static int checks;

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Supply the exact VulkanMod Reforged 0.3.5 JAR as argument 0");
        Path jar = Path.of(args[0]);
        Map<String, ClassNode> upstream = readJar(jar);
        auditUpstream(upstream);
        auditMixin(readProduct(MIXIN));
        auditHelper(readProduct(HELPER));
        exerciseRetirement();
        System.out.println("PASS: Reforged shutdown " + checks + " checks; upstream SHA-256=" +
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))) +
                "; compiled mixin and real retirement helper, CPU only, no GPU/native calls");
    }

    private static void auditUpstream(Map<String, ClassNode> nodes) {
        ClassNode pipeline = requireNode(nodes, PIPELINE), graphics = requireNode(nodes, GRAPHICS);
        require(GRAPHICS.equals(graphics.name) && PIPELINE.equals(graphics.superName)
                        && (graphics.access & ACC_ABSTRACT) == 0, "exact concrete direct graphics subclass");
        Set<String> subclasses = new TreeSet<>();
        for (ClassNode node : nodes.values()) {
            for (String parent = node.superName; parent != null;) {
                if (PIPELINE.equals(parent)) { subclasses.add(node.name); break; }
                ClassNode ancestor = nodes.get(parent);
                parent = ancestor == null ? null : ancestor.superName;
            }
        }
        require(subclasses.equals(Set.of(GRAPHICS)), "the exact JAR has only GraphicsPipeline: " + subclasses);
        FieldNode registry = field(pipeline, "PIPELINES", "Ljava/util/List;");
        require((registry.access & (ACC_PUBLIC | ACC_STATIC | ACC_FINAL)) ==
                (ACC_PUBLIC | ACC_STATIC | ACC_FINAL), "public static final registry");
        MethodNode init = method(pipeline, "<clinit>", "()V");
        require(hasNew(init, "java/util/LinkedList") && writes(init, PIPELINE, "PIPELINES"),
                "registry initialized as the audited unsynchronized LinkedList");
        FieldNode closed = field(graphics, "closed", "Z");
        require((closed.access & ACC_PRIVATE) != 0 && (closed.access & ACC_STATIC) == 0,
                "private instance closed guard");
        MethodNode cleanup = method(graphics, "cleanUp", "()V");
        require((cleanup.access & ACC_PUBLIC) != 0 && (cleanup.access & ACC_STATIC) == 0,
                "public instance cleanup");
        List<AbstractInsnNode> body = code(cleanup);
        require(body.size() > 8 && body.get(0).getOpcode() == ALOAD
                        && body.get(1) instanceof FieldInsnNode f && f.getOpcode() == GETFIELD && f.name.equals("closed")
                        && body.get(2).getOpcode() == IFEQ && body.get(3).getOpcode() == RETURN
                        && body.get(4).getOpcode() == ALOAD && body.get(5).getOpcode() == ICONST_1
                        && body.get(6) instanceof FieldInsnNode f2 && f2.getOpcode() == PUTFIELD && f2.name.equals("closed"),
                "closed -> return; otherwise closed=true before destructor begins");
        require(calls(cleanup, SHADER + "ResourceRollback", "close"), "cleanup executes synchronous rollback close");
        require(!calls(cleanup, PIPELINE, "scheduleCleanUp") && !calls(cleanup, MEMORY, "addFrameOp"),
                "cleanup does not enqueue itself");
        boolean removes = false;
        for (MethodNode m : graphics.methods) {
            if (m.name.startsWith("lambda$cleanUp$") && reads(m, "PIPELINES") && calls(m, "java/util/List", "remove")) {
                require(referencesHandle(cleanup, GRAPHICS, m.name), "registry remove action belongs to actual cleanup");
                removes = true;
            }
        }
        require(removes, "successful native cleanup removes itself from registry");
        require(calls(method(graphics, "destroyGraphicsPipelines", "()V"),
                "it/unimi/dsi/fastutil/objects/Object2LongMap", "clear"), "native pipeline map is cleared before teardown returns");
        MethodNode rollback = method(requireNode(nodes, SHADER + "ResourceRollback"), "close", "()V");
        require(calls(rollback, "java/lang/Runnable", "run") && hasOpcode(rollback, ATHROW)
                        && !calls(rollback, MEMORY, "addFrameOp"), "rollback invokes releases now and propagates failures");
        Set<MethodNode> teardown = reachable(graphics, cleanup);
        for (String nativeRelease : List.of("vkDestroyPipeline", "vkDestroyPipelineLayout", "vkDestroyDescriptorSetLayout")) {
            require(teardown.stream().anyMatch(m -> calls(m, "org/lwjgl/vulkan/VK10", nativeRelease)),
                    "actual cleanup reaches synchronous native release: " + nativeRelease);
        }
        require(teardown.stream().noneMatch(m -> calls(m, MEMORY, "addFrameOp")),
                "cleanup closure does not replace synchronous destruction with frame retirement");
        require(callsAny(requireNode(nodes, GRAPHICS + "$1"), "org/lwjgl/vulkan/VK10", "vkDestroyShaderModule"),
                "shader module backend destroys synchronously");
        MethodNode pairClose = method(requireNode(nodes, SHADER + "DescriptorResources$Pair"), "close",
                "(L" + SHADER + "DescriptorResources$Backend;)V");
        require(calls(pairClose, SHADER + "DescriptorResources$Backend", "destroyPool")
                        && !calls(pairClose, SHADER + "DescriptorResources$Backend", "retirePool"),
                "descriptor close destroys pool rather than scheduling retirement");
        MethodNode shutdown = method(requireNode(nodes, VULKAN), "cleanUp", "()V");
        int idle = callIndex(shutdown, "org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle");
        int drain = callIndex(shutdown, MEMORY, "cleanUp");
        int vma = callIndex(shutdown, "org/lwjgl/util/vma/Vma", "vmaDestroyAllocator");
        int device = callIndex(shutdown, "net/vulkanmod/vulkan/device/DeviceManager", "destroy");
        require(idle >= 0 && idle < drain && drain < vma && vma < device,
                "upstream order: native idle -> terminal frame operations -> allocator -> device");
        require(callCount(shutdown, "org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle") == 1
                        && callCount(shutdown, MEMORY, "cleanUp") == 1, "unique shutdown injection sites");
        MethodNode mm = method(requireNode(nodes, MEMORY), "cleanUp", "()V");
        require(calls(mm, MEMORY, "freeBuffers") && calls(mm, MEMORY, "doFrameOps"),
                "normal terminal memory drain includes freeBuffers and doFrameOps");
        // The hook is anchored at this normal-returning invocation, not MemoryManager's own RETURN.
        require(shutdown.tryCatchBlocks.isEmpty(), "no upstream catch can continue past a failed drain");
    }

    private static void auditMixin(ClassNode mixin) {
        require(annotation(mixin.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Pseudo;") != null
                        || annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Pseudo;") != null,
                "optional Reforged mixin is @Pseudo");
        AnnotationNode target = anyAnnotation(mixin, "Lorg/spongepowered/asm/mixin/Mixin;");
        // Mixin ClassInfo.forName normalizes binary names to internal names;
        // Loom may already store the internal form in the remapped artifact.
        Object targets = target == null ? null : value(target, "targets");
        require(target != null && Objects.equals(value(target, "remap"), false)
                        && targets instanceof List<?> names && names.size() == 1
                        && names.getFirst() instanceof String name && name.replace('.', '/').equals(VULKAN),
                "mixin exact optional upstream target and remap=false");
        MethodNode wait = annotatedMethod(mixin, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        AnnotationNode redirect = anyAnnotation(wait, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        require(methodSelector(value(redirect, "method"), "cleanUp()V"), "redirect only terminal cleanup");
        AnnotationNode at = (AnnotationNode) value(redirect, "at");
        require("INVOKE".equals(value(at, "value")) &&
                        ("Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I").equals(value(at, "target")),
                "redirect exact native device-idle call, preserving VkDevice argument/result");
        require(wait.desc.equals("(Lorg/lwjgl/vulkan/VkDevice;)I") && (wait.access & ACC_STATIC) != 0,
                "redirect native signature");
        require(callCount(wait, "org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle") == 1
                        && callCount(wait, HELPER, "afterDeviceIdle") == 1
                        && callIndex(wait, "org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle") < callIndex(wait, HELPER, "afterDeviceIdle"),
                "original native idle executes exactly once before compatibility hook");
        List<AbstractInsnNode> waitBody = code(wait);
        int nativeAt = callIndex(wait, "org/lwjgl/vulkan/VK10", "vkDeviceWaitIdle");
        int helperAt = callIndex(wait, HELPER, "afterDeviceIdle");
        require(waitBody.subList(nativeAt + 1, helperAt).stream().anyMatch(i -> i instanceof JumpInsnNode)
                        && waitBody.subList(nativeAt + 1, helperAt).stream().anyMatch(i -> i.getOpcode() == ATHROW),
                "failed native idle is checked before the hook");
        require(hasOpcode(wait, IRETURN), "redirect preserves the native integer result");
        require(waitBody.get(nativeAt + 1) instanceof VarInsnNode stored && stored.getOpcode() == ISTORE
                        && waitBody.get(waitBody.size() - 2) instanceof VarInsnNode returned
                        && returned.getOpcode() == ILOAD && stored.var == returned.var
                        && waitBody.getLast().getOpcode() == IRETURN,
                "redirect returns the same successful integer stored from the original native call");
        MethodNode after = annotatedMethod(mixin, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        AnnotationNode inject = anyAnnotation(after, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        require(methodSelector(value(inject, "method"), "cleanUp()V"), "drain hook only terminal cleanup");
        Object locations = value(inject, "at");
        require(locations instanceof List<?> l && l.size() == 1 && l.getFirst() instanceof AnnotationNode, "single drain injection location");
        AnnotationNode afterAt = (AnnotationNode) ((List<?>) locations).getFirst();
        require("INVOKE".equals(value(afterAt, "value"))
                        && ("L" + MEMORY + ";cleanUp()V").equals(value(afterAt, "target")), "hook actual MemoryManager drain invocation");
        Object shift = value(afterAt, "shift");
        require(shift instanceof String[] pair && pair.length == 2 && pair[1].equals("AFTER"),
                "hook is after normal drain return, before subsequent VMA destruction");
        require(!Boolean.TRUE.equals(value(inject, "cancellable")) && callCount(after, HELPER, "afterMemoryCleanup") == 1,
                "noncancelling hook calls terminal retirement once");
        require(wait.tryCatchBlocks.isEmpty() && after.tryCatchBlocks.isEmpty(), "mixin does not swallow native/helper failures");
        require(mixin.methods.stream().filter(m -> m.name.equals("cleanUp")).findAny().isEmpty(),
                "original upstream cleanup is not overwritten");
    }

    private static void auditHelper(ClassNode helper) {
        MethodNode retire = method(helper, "retireRemaining", "(Ljava/util/List;Ljava/lang/Class;)V");
        require((retire.access & ACC_STATIC) != 0, "CPU retirement entry is static");
        require(helper.methods.stream().noneMatch(m -> m.name.equals("<clinit>")),
                "CPU class loading does not initialize Minecraft, Vulkan, or native state");
        for (MethodNode m : helper.methods) for (AbstractInsnNode i : m.instructions) {
            if (i instanceof MethodInsnNode c) require(!c.owner.startsWith("org/lwjgl/"),
                    "helper leaves native destruction to upstream owners: " + c.owner + "." + c.name);
        }
        require(calls(method(helper, "afterMemoryCleanup", "()V"), HELPER, "retireRemaining"),
                "runtime hook uses the tested retirement method");
    }

    private static void exerciseRetirement() throws Exception {
        Fixture f = new Fixture();
        LinkedList<Object> list = new LinkedList<>();
        Object a = f.object(list, 0), b = f.object(list, 0), c = f.object(list, 0);
        list.addAll(List.of(a, b, c)); f.retire(list, f.graphics);
        require(list.isEmpty() && f.count() == 3 && f.closed(a) && f.closed(b) && f.closed(c),
                "snapshot permits each cleanup to remove itself without skipping/CME");
        f.retire(list, f.graphics); require(f.count() == 3, "empty registry does not destroy again");
        f.rejectPreflight(new ArrayList<>(), f.graphics, "non-LinkedList registry");
        f.rejectPreflight(null, f.graphics, "null registry");
        f.rejectPreflight(new LinkedList<>(), Object.class, "foreign exact graphics class");
        f.rejectPreflight(new LinkedList<>(), null, "null exact graphics class");
        for (String bad : List.of("foreign", "subclass", "null", "duplicate", "closed")) {
            list = new LinkedList<>(); Object good = f.object(list, 0); list.add(good);
            switch (bad) {
                case "foreign" -> list.add(new Object());
                case "subclass" -> list.add(f.subclass.getConstructor().newInstance());
                case "null" -> list.add(null);
                case "duplicate" -> list.add(good);
                case "closed" -> { Object dead = f.object(list, 0); f.setClosed(dead); list.add(dead); }
            }
            f.rejectPreflight(list, f.graphics, bad + " entry rejects entire snapshot before touching earlier valid object");
            require(!f.closed(good), "invalid snapshot preserves preceding valid object: " + bad);
        }
        for (Throwable failure : List.of(new IllegalStateException("cleanup sentinel"), new AssertionError("cleanup error sentinel"))) {
            list = new LinkedList<>(); Object bad = f.object(list, 1); Object untouched = f.object(list, 0);
            list.add(bad); list.add(untouched); f.graphics.getField("failure").set(null, failure);
            Throwable actual = f.failure(list, f.graphics);
            require(actual == failure, "cleanup throwable is unwrapped and propagated by identity");
            require(!f.closed(untouched), "failed cleanup does not continue and fabricate terminal success");
        }
        for (int mode : new int[]{2, 3, 4}) {
            list = new LinkedList<>(); list.add(f.object(list, mode));
            Throwable failure = f.failure(list, f.graphics);
            require(failure != null, "reject abnormal cleanup mode " + mode + " (no removal/no closed transition/registry mutation)");
        }
    }

    /** Child defines exact-name upstream fixtures and actual compiled helper bytes; no upstream class is loaded. */
    private static final class Fixture extends ClassLoader {
        final Class<?> graphics, subclass, helper;
        final Method retire;
        Fixture() throws Exception {
            super(ReforgedShutdownCompatCheck.class.getClassLoader());
            define(PIPELINE, baseClass());
            graphics = define(GRAPHICS, graphicsClass());
            subclass = define(GRAPHICS + "$ForeignSubclass", subclassClass());
            helper = define(HELPER, productBytes(HELPER));
            retire = helper.getDeclaredMethod("retireRemaining", List.class, Class.class); retire.setAccessible(true);
        }
        Class<?> define(String name, byte[] code) { return defineClass(name.replace('/', '.'), code, 0, code.length); }
        Object object(List<?> registry, int mode) throws Exception {
            Object result = graphics.getConstructor().newInstance();
            graphics.getField("registry").set(result, registry); graphics.getField("mode").setInt(result, mode); return result;
        }
        boolean closed(Object obj) throws Exception { var field = graphics.getDeclaredField("closed"); field.setAccessible(true); return field.getBoolean(obj); }
        void setClosed(Object obj) throws Exception { var field = graphics.getDeclaredField("closed"); field.setAccessible(true); field.setBoolean(obj, true); }
        int count() throws Exception { return graphics.getField("calls").getInt(null); }
        void retire(List<?> list, Class<?> type) throws Exception { retire.invoke(null, list, type); }
        Throwable failure(List<?> list, Class<?> type) throws Exception {
            try { retire(list, type); return null; }
            catch (InvocationTargetException e) { return e.getCause(); }
        }
        void rejectPreflight(List<?> list, Class<?> type, String label) throws Exception {
            int before = count(); Throwable e = failure(list, type);
            require(e instanceof RuntimeException, "reject " + label + ": " + e);
            require(count() == before, "preflight is atomic: " + label);
        }
    }

    private static byte[] baseClass() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(V21, ACC_PUBLIC, PIPELINE, null, "java/lang/Object", null);
        constructor(w, PIPELINE, "java/lang/Object"); w.visitEnd(); return w.toByteArray();
    }
    private static byte[] subclassClass() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(V21, ACC_PUBLIC, GRAPHICS + "$ForeignSubclass", null, GRAPHICS, null);
        constructor(w, GRAPHICS + "$ForeignSubclass", GRAPHICS); w.visitEnd(); return w.toByteArray();
    }
    private static byte[] graphicsClass() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        w.visit(V21, ACC_PUBLIC, GRAPHICS, null, PIPELINE, null);
        w.visitField(ACC_PRIVATE, "closed", "Z", null, null).visitEnd();
        w.visitField(ACC_PUBLIC, "registry", "Ljava/util/List;", null, null).visitEnd();
        w.visitField(ACC_PUBLIC, "mode", "I", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC, "calls", "I", null, null).visitEnd();
        w.visitField(ACC_PUBLIC | ACC_STATIC, "failure", "Ljava/lang/Throwable;", null, null).visitEnd();
        constructor(w, GRAPHICS, PIPELINE);
        // Distinct owners deliberately collide under equals/hashCode: retirement is about identity.
        MethodVisitor equals = w.visitMethod(ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null);
        equals.visitCode(); equals.visitInsn(ICONST_1); equals.visitInsn(IRETURN); equals.visitMaxs(0, 0); equals.visitEnd();
        MethodVisitor hash = w.visitMethod(ACC_PUBLIC, "hashCode", "()I", null, null);
        hash.visitCode(); hash.visitInsn(ICONST_0); hash.visitInsn(IRETURN); hash.visitMaxs(0, 0); hash.visitEnd();
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "cleanUp", "()V", null, null); m.visitCode();
        m.visitFieldInsn(GETSTATIC, GRAPHICS, "calls", "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD); m.visitFieldInsn(PUTSTATIC, GRAPHICS, "calls", "I");
        Label normal = new Label(); mode(m, 1); m.visitJumpInsn(IF_ICMPNE, normal);
        m.visitFieldInsn(GETSTATIC, GRAPHICS, "failure", "Ljava/lang/Throwable;"); m.visitInsn(ATHROW); m.visitLabel(normal);
        Label noFlip = new Label(); mode(m, 3); m.visitJumpInsn(IF_ICMPEQ, noFlip);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ICONST_1); m.visitFieldInsn(PUTFIELD, GRAPHICS, "closed", "Z"); m.visitLabel(noFlip);
        Label noRemove = new Label(); mode(m, 2); m.visitJumpInsn(IF_ICMPEQ, noRemove);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, GRAPHICS, "registry", "Ljava/util/List;"); m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "remove", "(Ljava/lang/Object;)Z", true); m.visitInsn(POP); m.visitLabel(noRemove);
        Label finish = new Label(); mode(m, 4); m.visitJumpInsn(IF_ICMPNE, finish);
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, GRAPHICS, "registry", "Ljava/util/List;");
        m.visitTypeInsn(NEW, "java/lang/Object"); m.visitInsn(DUP); m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true); m.visitInsn(POP);
        m.visitLabel(finish); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }
    private static void mode(MethodVisitor m, int mode) { m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, GRAPHICS, "mode", "I"); m.visitLdcInsn(mode); }
    private static void constructor(ClassWriter w, String name, String parent) {
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false);
        m.visitInsn(RETURN); m.visitMaxs(1, 1); m.visitEnd();
    }

    private static Map<String, ClassNode> readJar(Path jar) throws Exception {
        Map<String, ClassNode> result = new HashMap<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = file.getInputStream(entry)) { ClassNode n = read(in.readAllBytes()); result.put(n.name, n); }
            }
        }
        return result;
    }
    private static byte[] productBytes(String name) throws Exception {
        try (InputStream in = ReforgedShutdownCompatCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing compiled product " + name); return in.readAllBytes();
        }
    }
    private static ClassNode readProduct(String name) throws Exception { return read(productBytes(name)); }
    private static ClassNode read(byte[] b) { ClassNode n = new ClassNode(); new ClassReader(b).accept(n, ClassReader.SKIP_DEBUG); return n; }
    private static ClassNode requireNode(Map<String, ClassNode> nodes, String name) { ClassNode n = nodes.get(name); require(n != null, "class present: " + name); return n; }
    private static FieldNode field(ClassNode n, String name, String desc) { return n.fields.stream().filter(f -> f.name.equals(name) && f.desc.equals(desc)).findFirst().orElseThrow(() -> new AssertionError("Missing field " + n.name + "." + name)); }
    private static MethodNode method(ClassNode n, String name, String desc) { return n.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(() -> new AssertionError("Missing method " + n.name + "." + name + desc)); }
    private static List<AbstractInsnNode> code(MethodNode m) { List<AbstractInsnNode> r = new ArrayList<>(); for (AbstractInsnNode i : m.instructions) if (i.getOpcode() >= 0) r.add(i); return r; }
    private static boolean calls(MethodNode m, String owner, String name) { return callCount(m, owner, name) > 0; }
    private static int callCount(MethodNode m, String owner, String name) { return (int) code(m).stream().filter(i -> i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name)).count(); }
    private static int callIndex(MethodNode m, String owner, String name) { List<AbstractInsnNode> b = code(m); for (int i = 0; i < b.size(); i++) if (b.get(i) instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name)) return i; return -1; }
    private static Set<MethodNode> reachable(ClassNode owner, MethodNode first) {
        Set<MethodNode> result = new HashSet<>(); Deque<MethodNode> pending = new ArrayDeque<>(); pending.add(first);
        while (!pending.isEmpty()) {
            MethodNode m = pending.removeFirst(); if (!result.add(m)) continue;
            Set<String> references = new HashSet<>();
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(owner.name)) references.add(call.name + call.desc);
                if (insn instanceof InvokeDynamicInsnNode indy) for (Object arg : indy.bsmArgs)
                    if (arg instanceof Handle h && h.getOwner().equals(owner.name)) references.add(h.getName() + h.getDesc());
            }
            for (MethodNode candidate : owner.methods) if (references.contains(candidate.name + candidate.desc)) pending.add(candidate);
        }
        return result;
    }
    private static boolean callsAny(ClassNode n, String owner, String name) { return n.methods.stream().anyMatch(m -> calls(m, owner, name)); }
    private static boolean hasOpcode(MethodNode m, int opcode) { return code(m).stream().anyMatch(i -> i.getOpcode() == opcode); }
    private static boolean hasNew(MethodNode m, String type) { return code(m).stream().anyMatch(i -> i instanceof TypeInsnNode t && t.getOpcode() == NEW && t.desc.equals(type)); }
    private static boolean writes(MethodNode m, String owner, String field) { return code(m).stream().anyMatch(i -> i instanceof FieldInsnNode f && f.getOpcode() == PUTSTATIC && f.owner.equals(owner) && f.name.equals(field)); }
    private static boolean reads(MethodNode m, String field) { return code(m).stream().anyMatch(i -> i instanceof FieldInsnNode f && f.getOpcode() == GETSTATIC && f.name.equals(field)); }
    private static boolean referencesHandle(MethodNode m, String owner, String name) { for (AbstractInsnNode i : m.instructions) if (i instanceof InvokeDynamicInsnNode indy) for (Object arg : indy.bsmArgs) if (arg instanceof Handle h && h.getOwner().equals(owner) && h.getName().equals(name)) return true; return false; }
    private static AnnotationNode annotation(List<AnnotationNode> list, String desc) { return list == null ? null : list.stream().filter(a -> a.desc.equals(desc)).findFirst().orElse(null); }
    private static AnnotationNode anyAnnotation(ClassNode n, String desc) { AnnotationNode a = annotation(n.visibleAnnotations, desc); return a != null ? a : annotation(n.invisibleAnnotations, desc); }
    private static AnnotationNode anyAnnotation(MethodNode n, String desc) { AnnotationNode a = annotation(n.visibleAnnotations, desc); return a != null ? a : annotation(n.invisibleAnnotations, desc); }
    private static MethodNode annotatedMethod(ClassNode n, String desc) { return n.methods.stream().filter(m -> anyAnnotation(m, desc) != null).findFirst().orElseThrow(() -> new AssertionError("Missing annotation " + desc)); }
    private static Object value(AnnotationNode a, String key) { if (a.values != null) for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals(key)) return a.values.get(i + 1); return null; }
    private static boolean methodSelector(Object value, String method) { return value instanceof List<?> l && l.equals(List.of(method)); }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
