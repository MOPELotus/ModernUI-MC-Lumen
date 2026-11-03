package icyllis.modernui.mc.neoforge;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

import static org.objectweb.asm.Opcodes.*;

/** Executes compiled renderer diagnostics with CPU capabilities; inspects the actual GUI query gate. */
public final class RendererDebugInfoCheck {
    private static final String HELPER = "icyllis/modernui/mc/neoforge/RendererDebugInfo";
    private static final String UI = "icyllis/modernui/mc/neoforge/UIManagerForge";
    private static final String GL = "icyllis/arc3d/opengl/GLDevice";
    private static final String EXECUTED = "icyllis/modernui/mc/neoforge/RendererDebugInfoExecuted";
    private static int checks, nativeQueries;

    public static void main(String[] args) throws Exception {
        Method dump = executedHelper();
        Caps caps = new Caps("physical=Tesla T10; driver=NVIDIA; maxTextureSize=16384");
        Device vk = new Device(1, caps);
        assertDump(dump, vk, false, "Vulkan", "physical=Tesla T10", "driver=NVIDIA", "maxTextureSize=16384");
        require(caps.dumpCalls == 1 && !caps.verbose && caps.glQueries == 0, "Vulkan emits real caps without any GL capability query");
        GLCaps dsa = new GLCaps(true); assertDump(dump, new GLDevice(dsa), true, "OpenGL", "GL CPU caps");
        require(dsa.glQueries == 1 && dsa.dumpCalls == 1, "GL DSA is queried exactly once after caps output");
        GLCaps noDsa = new GLCaps(false); assertDump(dump, new GLDevice(noDsa), false, "OpenGL", "GL CPU caps");
        require(noDsa.glQueries == 1 && noDsa.dumpCalls == 1, "GL without DSA still emits diagnostics");
        assertDump(dump, new Device(17, new Caps("unknown CPU caps")), false, "Unknown (17)", "unknown CPU caps");
        assertDump(dump, new Device(0, new Caps("non-GL implementation")), false, "OpenGL", "non-GL implementation");
        require(nativeQueries == 1, "only the actual DSA GL device permits caller native queries");
        StringWriter nullOutput = new StringWriter();
        Throwable nullFailure = failure(dump, new PrintWriter(nullOutput), null);
        require(nullFailure instanceof NullPointerException && nullOutput.toString().isEmpty(), "null device fails before reporting fabricated capabilities");
        RuntimeException capsFailure = new IllegalStateException("caps dump sentinel");
        Caps broken = new Caps("broken"); broken.failure = capsFailure;
        require(failure(dump, new PrintWriter(new StringWriter()), new Device(1, broken)) == capsFailure,
                "actual caps failure propagates without fake fallback output");
        Error backendFailure = new AssertionError("backend sentinel"); Device brokenDevice = new Device(1, caps);
        brokenDevice.failure = backendFailure;
        require(failure(dump, new PrintWriter(new StringWriter()), brokenDevice) == backendFailure, "device query error propagates");
        auditGuiGate(read(product(UI)));
        if (args.length == 1) oldCastNegativeControl(Path.of(args[0]));
        else require(args.length == 0, "at most one frozen old-JAR argument");
        System.out.println("PASS: renderer debug info " + checks + " CPU checks; GL DSA on/off, Vulkan/unknown/null/errors and compiled GL-query dominance; no GPU initialized");
    }

    private static void assertDump(Method method, Device device, boolean expected, String... text) throws Exception {
        StringWriter output = new StringWriter(); boolean allow = (boolean) method.invoke(null, new PrintWriter(output), device);
        require(allow == expected, "GL query permission matches device and DSA");
        if (allow) nativeQueries++;
        String report = output.toString();
        require(report.contains(device.getClass().getName()), "reports actual device class");
        for (String part : text) require(report.contains(part), "reports actual diagnostic: " + part);
    }

    private static Method executedHelper() throws Exception {
        byte[] actual = product(HELPER);
        ClassNode helper = read(actual);
        require(helper.methods.stream().noneMatch(m -> m.name.equals("<clinit>")), "diagnostic helper has no native static initialization");
        for (MethodNode method : helper.methods) for (AbstractInsnNode i : method.instructions) {
            if (i instanceof MethodInsnNode call) require(!call.owner.startsWith("org/lwjgl/"),
                    "diagnostic helper never invokes a native API directly: " + call.owner + "." + call.name);
        }
        Map<String, String> map = Map.of(HELPER, EXECUTED,
                "icyllis/arc3d/engine/Device", Type.getInternalName(Device.class),
                "icyllis/arc3d/engine/Caps", Type.getInternalName(Caps.class),
                GL, Type.getInternalName(GLDevice.class),
                "icyllis/arc3d/opengl/GLCaps", Type.getInternalName(GLCaps.class));
        ClassWriter w = new ClassWriter(0);
        new ClassReader(actual).accept(new ClassRemapper(w, new SimpleRemapper(map)), 0);
        Class<?> executed = new Loader().define(EXECUTED, w.toByteArray());
        return executed.getDeclaredMethod("dump", PrintWriter.class, Device.class);
    }

    private static void auditGuiGate(ClassNode ui) {
        MethodNode method = ui.methods.stream().filter(m -> m.name.equals("dump") && m.desc.equals("(Ljava/io/PrintWriter;Z)V"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing actual UIManagerForge.dump"));
        AbstractInsnNode[] insns = method.instructions.toArray();
        int resultSlot = -1, helperCount = 0;
        for (AbstractInsnNode i : insns) {
            require(!(i instanceof TypeInsnNode t && t.getOpcode() == CHECKCAST && t.desc.equals(GL)), "GUI dump has no unconditional GLDevice cast");
            if (i instanceof MethodInsnNode c && c.owner.equals(HELPER) && c.name.equals("dump")) {
                helperCount++; AbstractInsnNode next = nextCode(i);
                require(next instanceof VarInsnNode v && v.getOpcode() == ISTORE, "GUI stores helper's query permission");
                resultSlot = ((VarInsnNode) next).var;
            }
        }
        require(helperCount == 1, "GUI calls the actual helper once");
        int permittedFrom = -1, permittedTo = -1;
        for (int i = 0; i < insns.length; i++) if (insns[i] instanceof VarInsnNode v && v.getOpcode() == ILOAD && v.var == resultSlot) {
            AbstractInsnNode next = nextCode(insns[i]);
            if (next instanceof JumpInsnNode jump && (jump.getOpcode() == IFEQ || jump.getOpcode() == IFNE)) {
                // Later uses may select available/unavailable diagnostic text.
                // The first permission branch guards the GL query block itself.
                if (permittedFrom == -1) {
                    permittedFrom = method.instructions.indexOf(jump);
                    permittedTo = jump.getOpcode() == IFEQ ? permittedFrom + 1 : method.instructions.indexOf(jump.label);
                }
            }
        }
        require(permittedFrom >= 0, "actual GL permission controls a branch");
        Set<Integer> reachableWithoutPermission = reachable(method, permittedFrom, permittedTo);
        int queries = 0;
        for (int i = 0; i < insns.length; i++) if (insns[i] instanceof MethodInsnNode c && c.owner.equals("org/lwjgl/opengl/GL45C")) {
            queries++; require(!reachableWithoutPermission.contains(i), "every actual GL45C query requires true helper result: " + c.name);
        }
        require(queries > 0, "actual GUI dump retains its GL texture diagnostics");
    }

    /** Remove the permission-true edge; any remaining path to a GL query would bypass the guard. */
    private static Set<Integer> reachable(MethodNode method, int blockedFrom, int blockedTo) {
        AbstractInsnNode[] code = method.instructions.toArray(); Set<Integer> seen = new HashSet<>(); Deque<Integer> work = new ArrayDeque<>(); work.add(0);
        while (!work.isEmpty()) {
            int i = work.removeFirst(); if (i < 0 || i >= code.length || !seen.add(i)) continue;
            AbstractInsnNode n = code[i]; Set<Integer> next = new HashSet<>(); int op = n.getOpcode();
            if (n instanceof JumpInsnNode jump) {
                next.add(method.instructions.indexOf(jump.label)); if (op != GOTO) next.add(i + 1);
            } else if (n instanceof TableSwitchInsnNode table) {
                next.add(method.instructions.indexOf(table.dflt)); for (LabelNode label : table.labels) next.add(method.instructions.indexOf(label));
            } else if (n instanceof LookupSwitchInsnNode table) {
                next.add(method.instructions.indexOf(table.dflt)); for (LabelNode label : table.labels) next.add(method.instructions.indexOf(label));
            } else if (op != ATHROW && (op < IRETURN || op > RETURN)) next.add(i + 1);
            for (TryCatchBlockNode handler : method.tryCatchBlocks) {
                if (i >= method.instructions.indexOf(handler.start) && i < method.instructions.indexOf(handler.end)) next.add(method.instructions.indexOf(handler.handler));
            }
            for (int target : next) if (i != blockedFrom || target != blockedTo) work.add(target);
        }
        return seen;
    }

    private static void oldCastNegativeControl(Path jar) throws Exception {
        ClassNode old;
        try (JarFile f = new JarFile(jar.toFile()); InputStream in = f.getInputStream(f.getJarEntry(UI + ".class"))) { old = read(in.readAllBytes()); }
        MethodNode dump = old.methods.stream().filter(m -> m.name.equals("dump") && m.desc.equals("(Ljava/io/PrintWriter;Z)V"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing frozen old dump"));
        TypeInsnNode actualCast = null;
        for (AbstractInsnNode i : dump.instructions) if (i instanceof TypeInsnNode t && t.getOpcode() == CHECKCAST && t.desc.equals(GL)) { actualCast = t; break; }
        require(actualCast != null, "frozen old dump contains actual unsafe GLDevice cast");
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); String name = EXECUTED + "OldCast";
        w.visit(V21, ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "cast", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0);
        // Execute the actual extracted CHECKCAST through the same remapper as the production helper.
        actualCast.accept(new org.objectweb.asm.commons.MethodRemapper(m, new SimpleRemapper(GL, Type.getInternalName(GLDevice.class))));
        m.visitInsn(ARETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd();
        Method cast = new Loader().define(name, w.toByteArray()).getMethod("cast", Object.class);
        require(failure(cast, new Device(1, new Caps("Vulkan"))) instanceof ClassCastException,
                "actual old cast reproduces Vulkan dump crash at a CPU boundary");
        GLDevice gl = new GLDevice(new GLCaps(true)); require(cast.invoke(null, gl) == gl, "old cast accepts GL as its positive control");
        System.out.println("Old unsafe-cast negative control reproduced: " + jar + " sha256=" +
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
    }

    public static class Device {
        final int backend; final Caps caps; Throwable failure;
        Device(int backend, Caps caps) { this.backend = backend; this.caps = caps; }
        public int getBackend() { if (failure instanceof Error e) throw e; if (failure instanceof RuntimeException e) throw e; return backend; }
        public Caps getCaps() { return caps; }
    }
    public static class Caps {
        final String description; int dumpCalls, glQueries; boolean verbose; RuntimeException failure;
        Caps(String description) { this.description = description; }
        public void dump(StringBuilder builder, boolean verbose) {
            dumpCalls++; this.verbose = verbose; if (failure != null) throw failure; builder.append(description);
        }
    }
    public static final class GLCaps extends Caps {
        final boolean dsa;
        GLCaps(boolean dsa) { super("GL CPU caps"); this.dsa = dsa; }
        public boolean hasDSASupport() { glQueries++; return dsa; }
    }
    public static final class GLDevice extends Device {
        GLDevice(GLCaps caps) { super(0, caps); }
        @Override public GLCaps getCaps() { return (GLCaps) caps; }
    }
    private static final class Loader extends ClassLoader {
        Loader() { super(RendererDebugInfoCheck.class.getClassLoader()); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name.replace('/', '.'), bytes, 0, bytes.length); }
    }
    private static AbstractInsnNode nextCode(AbstractInsnNode node) { do { node = node.getNext(); } while (node != null && node.getOpcode() < 0); return node; }
    private static Throwable failure(Method method, Object... args) throws Exception {
        try { method.invoke(null, args); return null; } catch (InvocationTargetException e) { return e.getCause(); }
    }
    private static byte[] product(String name) throws Exception {
        try (InputStream in = RendererDebugInfoCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing compiled " + name); return in.readAllBytes();
        }
    }
    private static ClassNode read(byte[] bytes) { ClassNode n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
    private static void require(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
