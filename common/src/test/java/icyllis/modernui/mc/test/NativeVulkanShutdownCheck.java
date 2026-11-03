package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.jar.JarFile;

/** Execute the compiled fallback with controlled ownership, including the real old double-close negative. */
public final class NativeVulkanShutdownCheck {
    private static final String SELF = "icyllis/modernui/mc/test/NativeVulkanShutdownCheck";
    private static int classes;

    public static void main(String[] args) throws Exception {
        var mixin = read("icyllis/modernui/mc/mixin/MixinNativeVulkanDevice");
        var hook = hook(mixin);
        var annotations = new ArrayList<AnnotationNode>();
        if (hook.visibleAnnotations != null) annotations.addAll(hook.visibleAnnotations);
        if (hook.invisibleAnnotations != null) annotations.addAll(hook.invisibleAnnotations);
        var injection = annotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        int at = injection.values.indexOf("at");
        @SuppressWarnings("unchecked") var locations = (java.util.List<AnnotationNode>) injection.values.get(at + 1);
        require(locations.size() == 1 && locations.getFirst().values.contains("HEAD"),
                "Device fallback must run before native commandEncoder.destroy, not at allocator destruction");
        var core = read("icyllis/modernui/core/Core");
        var peek = core.methods.stream().filter(m -> m.name.equals("peekImmediateContext")).findFirst().orElseThrow();
        require(java.util.stream.StreamSupport.stream(peek.instructions.spliterator(), false)
                .anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("sImmediateContext")),
                "Core's peek no longer retains the original context reference");
        var device = read("com/mojang/renderpearl/backend/vulkan/VulkanDevice");
        var close = device.methods.stream().filter(m -> m.name.equals("close") && m.desc.equals("()V")).findFirst().orElseThrow();
        int index=0, encoder=-1, allocator=-1;
        for (var instruction : close.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.endsWith("/VulkanCommandEncoder") && call.name.equals("destroy")) encoder=index;
                if (call.name.equals("vmaDestroyAllocator")) allocator=index;
            }
            index++;
        }
        require(encoder >= 0 && allocator > encoder, "Actual native shutdown order changed");
        var invoke = executable(mixin);
        State.reset(1, false);invoke.invoke(null);
        require(State.destroyCalls == 1 && State.context.refs == 0, "Live fallback must drain and release exactly once");
        State.encoderDestroyed=true;invoke.invoke(null);
        require(State.destroyCalls == 1, "Retained zero-ref context must not submit after native encoder destruction");
        State.reset(0, true);invoke.invoke(null);
        require(State.destroyCalls == 0, "Normal early surface cleanup must make the later device fallback a no-op");
        State.reset(-1, false);State.context=null;invoke.invoke(null);
        require(State.managerCloses == 1 && State.destroyCalls == 0, "Partial initialization still needs allocator cleanup");
        State.reset(1, false);State.vulkan=false;invoke.invoke(null);
        require(State.destroyCalls == 0 && State.managerCloses == 0, "OpenGL must not use Vulkan fallback");
        if (args.length > 0) try (var jar = new JarFile(args[0]); var input = jar.getInputStream(
                jar.getJarEntry("icyllis/modernui/mc/mixin/MixinNativeVulkanDevice.class"))) {
            var old = new ClassNode();new ClassReader(input).accept(old, 0);
            var previous = executable(old);State.reset(0, true);boolean rejected=false;
            try { previous.invoke(null); } catch (InvocationTargetException expected) {
                rejected = expected.getCause() instanceof AssertionError;
            }
            require(rejected, "Actual old candidate must reproduce unsafe second submission");
        }
        System.out.println("Native shutdown: actual encoder-before-allocator order, retained zero-ref context, live fallback, repeated close and old-candidate negative verified");
    }
    public static final class Context {
        public int refs;
        public int getRefCntAcquire() { return refs; }
    }
    public static final class Manager { public void close() { State.managerCloses++; } }
    public static final class State {
        public static Context context;
        public static boolean encoderDestroyed, vulkan;
        public static int destroyCalls, managerCloses;
        public static Context peekImmediateContext() { return context; }
        public static boolean isVulkanBackend() { return vulkan; }
        public static Manager get() { return new Manager(); }
        public static void destroy() {
            if (encoderDestroyed || context.refs == 0) throw new AssertionError("Submitting to destroyed native encoder / already released context");
            destroyCalls++;context.refs=0;
        }
        static void reset(int refs, boolean destroyed) {
            context=new Context();context.refs=refs;encoderDestroyed=destroyed;vulkan=true;destroyCalls=managerCloses=0;
        }
    }
    private static java.lang.reflect.Method executable(ClassNode mixin) throws Exception {
        var method=hook(mixin);
        method.name="invoke";method.access=Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;method.desc="()V";
        method.visibleAnnotations=null;method.invisibleAnnotations=null;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
            if (call.name.equals("getRefCntAcquire")) call.owner=SELF+"$Context";
            else if (call.owner.equals("icyllis/modernui/core/VulkanManager") && call.name.equals("close")) call.owner=SELF+"$Manager";
            else if (call.owner.equals("icyllis/modernui/core/Core") || call.owner.equals("icyllis/modernui/core/VulkanManager")
                    || call.owner.equals("icyllis/modernui/mc/ModernUIMod") || call.owner.equals("icyllis/modernui/mc/UIManager")) call.owner=SELF+"$State";
            call.desc=call.desc.replace("Licyllis/arc3d/engine/ImmediateContext;", "L"+SELF+"$Context;")
                    .replace("Licyllis/modernui/core/VulkanManager;", "L"+SELF+"$Manager;");
        }
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String name=SELF+"$Executable"+(++classes);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        method.accept(writer);writer.visitEnd();byte[] bytes=writer.toByteArray();
        class Loader extends ClassLoader {
            Loader() { super(NativeVulkanShutdownCheck.class.getClassLoader()); }
            Class<?> define() { return defineClass(name.replace('/','.'),bytes,0,bytes.length); }
        }
        return new Loader().define().getMethod("invoke");
    }
    private static MethodNode hook(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("modernui$releaseSharedContext")).findFirst().orElseThrow(); }
    private static ClassNode read(String name) throws Exception {
        try (var input=NativeVulkanShutdownCheck.class.getResourceAsStream("/"+name+".class")) {
            require(input!=null,"Missing actual class "+name);var node=new ClassNode();new ClassReader(input).accept(node,0);return node;
        }
    }
    private static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
