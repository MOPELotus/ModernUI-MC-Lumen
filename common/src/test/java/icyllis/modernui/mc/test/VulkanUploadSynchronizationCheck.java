package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Runs compiled upload synchronization and reflection helpers against CPU-only native/queue boundaries. */
public final class VulkanUploadSynchronizationCheck {
    private static final String OWNER = "icyllis/modernui/mc/VulkanModIntegration";
    private static final String EXECUTED = "icyllis/modernui/mc/test/VulkanUploadSynchronizationExecuted";
    private static final List<String> trace = new ArrayList<>();
    private static boolean submitFailure, syncFailure, fabricFailure;
    private static int waitResult, nextCommand, checks;
    private static Command lastSubmitted;

    public static void main(String[] args) throws Exception {
        var product = new Executed(read(null));
        reset(false);
        product.finish();
        require(trace.equals(List.of("device.queue", "sync.wait")), "empty Neo batch only drains independent fences: " + trace);
        require(Queue.currentCmdBuffer == null && nextCommand == 0, "empty query never allocates a recording buffer");
        reset(false);
        Sync.INSTANCE.independent.add(77L);
        product.finish();
        require(Sync.INSTANCE.independent.isEmpty() && Sync.INSTANCE.drained.equals(List.of(77L)),
                "already-submitted independent upload fence is drained with no current batch");
        require(trace.equals(List.of("device.queue", "sync.wait")), "standalone upload avoids end/start/native batch wait");
        product.finish();
        require(Queue.currentCmdBuffer == null && Sync.INSTANCE.waitCalls == 2, "repeated empty finish remains valid");

        reset(false);
        Command first = pending();
        Sync.INSTANCE.independent.add(88L);
        product.finish();
        require(trace.equals(List.of("device.queue", "neo.end", "cmd.fence:1", "vk.wait:4097:true:-1",
                "vk.check:0", "neo.start", "sync.wait")), "pending Neo order captures and waits the submitted command before replacement: " + trace);
        require(first.submissions == 1 && Queue.currentCmdBuffer != null && Queue.currentCmdBuffer != first,
                "pending batch is submitted exactly once and replaced for its enclosing tick");
        require(Sync.INSTANCE.drained.equals(List.of(88L)), "independent fences still drain after the captured batch wait");
        require(Queue.resetsQueued == 1, "queue remains the sole deferred-reset owner of the submitted command");
        Command second = Queue.currentCmdBuffer;
        product.finish();
        require(first.submissions == 1 && second.submissions == 1 && Queue.currentCmdBuffer != second,
                "repeated finish submits each current generation once");
        Command replacement = Queue.currentCmdBuffer;
        Queue.INSTANCE.endRecordingAndSubmit();
        require(replacement.submissions == 1 && Queue.currentCmdBuffer == null && Queue.resetsQueued == 3,
                "outer TextureManager tick can still end the replacement batch without NPE or duplicate reset");

        reset(false); pending(); submitFailure = true;
        expectFailure(product, IllegalStateException.class, "submit failed");
        require(!trace.contains("neo.start") && !trace.contains("sync.wait"), "failed submission is not swallowed or replaced");
        reset(false); pending(); waitResult = -4;
        expectFailure(product, IllegalStateException.class, "vk result -4");
        require(trace.contains("vk.check:-4") && !trace.contains("neo.start") && !trace.contains("sync.wait"),
                "native fence failure is checked and stops the synchronization path");
        reset(false); syncFailure = true;
        expectFailure(product, IllegalStateException.class, "sync failed");
        require(Queue.currentCmdBuffer == null, "independent synchronization failure does not create a batch");

        reset(true);
        Sync.INSTANCE.independent.add(99L);
        product.finish();
        require(trace.equals(List.of("fabric.submit", "sync.wait")) && Sync.INSTANCE.drained.equals(List.of(99L)),
                "Fabric helper submit then synchronization remains unchanged");
        reset(true); fabricFailure = true;
        expectFailure(product, IllegalStateException.class, "fabric failed");
        require(!trace.contains("device.queue") && !trace.contains("sync.wait"), "Fabric helper failure propagates without Neo fallback");
        if (args.length > 0) {
            Path old = Path.of(args[0]);
            var legacy = new Executed(read(old));
            reset(false);
            expectFailure(legacy, NullPointerException.class, null);
            require(Queue.currentCmdBuffer == null && !trace.contains("sync.wait"),
                    "old bytecode reproduces a real null-current-command dereference before synchronization");
            System.out.println("Old-JAR empty-batch NPE reproduced: " + old + " sha256=" +
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(old))));
        } else {
            System.out.println("Old-JAR negative control not supplied; pass frozen dbf7 JAR as argument 0.");
        }
        System.out.println("Vulkan upload synchronization: " + checks + " real-method CPU boundary checks passed; no GPU initialized");
    }

    private static Command pending() {
        Command command = new Command(++nextCommand);
        Queue.currentCmdBuffer = command;
        return command;
    }
    private static void reset(boolean fabric) {
        trace.clear(); Api.FABRIC = fabric; submitFailure = syncFailure = fabricFailure = false;
        waitResult = nextCommand = 0; lastSubmitted = null; Queue.currentCmdBuffer = null; Queue.resetsQueued = 0;
        Sync.INSTANCE.independent.clear(); Sync.INSTANCE.drained.clear(); Sync.INSTANCE.waitCalls = 0;
    }
    private static void expectFailure(Executed product, Class<? extends Throwable> type, String message) throws Exception {
        try { product.finish(); throw new AssertionError("Expected " + type.getSimpleName()); }
        catch (InvocationTargetException failure) {
            require(type.isInstance(failure.getCause()) && (message == null || failure.getCause().getMessage().contains(message)),
                    "real reflection/native failure propagates: " + failure.getCause());
        }
    }
    private static ClassNode read(Path jar) throws Exception {
        var node = new ClassNode();
        if (jar == null) {
            try (var stream = VulkanUploadSynchronizationCheck.class.getResourceAsStream("/" + OWNER + ".class")) {
                if (stream == null) throw new AssertionError("Missing product bridge");
                new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG);
            }
        } else {
            try (var file = new JarFile(jar.toFile()); var stream = file.getInputStream(file.getJarEntry(OWNER + ".class"))) {
                new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG);
            }
        }
        return node;
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }

    private static final class Executed extends ClassLoader {
        private final Method method;
        Executed(ClassNode original) throws Exception {
            super(VulkanUploadSynchronizationCheck.class.getClassLoader());
            Map<String, String> names = Map.of(OWNER, EXECUTED, OWNER + "$Api", Type.getInternalName(Api.class),
                    "org/lwjgl/vulkan/VkDevice", Type.getInternalName(Device.class),
                    "org/lwjgl/vulkan/VK10", Type.getInternalName(NativeBoundary.class),
                    "icyllis/arc3d/vulkan/VKUtil", Type.getInternalName(ResultBoundary.class));
            var remapper = new SimpleRemapper(names);
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, EXECUTED, null, "java/lang/Object", null);
            Set<String> selected = Set.of("finishTextureUploads", "field", "get", "call", "graphicsQueue", "device");
            int copied = 0;
            for (var source : original.methods) {
                if (!selected.contains(source.name)) continue;
                source.accept(new MethodRemapper(writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        source.name, remapper.mapMethodDesc(source.desc), null, null), remapper) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                        if (owner.equals(OWNER) && name.equals("type")) owner = Type.getInternalName(TypeBoundary.class);
                        super.visitMethodInsn(opcode, owner, name, descriptor, itf);
                    }
                });
                copied++;
            }
            require(copied == selected.size(), "copied actual bridge method and five reflection/device helpers");
            writer.visitEnd(); byte[] bytes = writer.toByteArray();
            method = defineClass(EXECUTED.replace('/', '.'), bytes, 0, bytes.length).getMethod("finishTextureUploads");
        }
        void finish() throws Exception { method.invoke(null); }
    }

    public static final class Api { public static boolean FABRIC; public static final Class<?> DEVICE = Device.class; }
    public static final class TypeBoundary {
        public static Class<?> type(String name) {
            return switch (name) {
                case "net.vulkanmod.vulkan.Synchronization" -> Sync.class;
                case "net.vulkanmod.vulkan.queue.GraphicsQueue" -> Queue.class;
                case "net.vulkanmod.render.texture.ImageUploadHelper" -> Fabric.class;
                default -> throw new AssertionError("Unexpected real API class lookup " + name);
            };
        }
    }
    public static final class Device {
        public static final Device vkDevice = new Device();
        public static Queue getGraphicsQueue() { trace.add("device.queue"); return Queue.INSTANCE; }
    }
    public static final class Command {
        final int id;
        int submissions;
        Command(int id) { this.id = id; }
        public long getFence() { trace.add("cmd.fence:" + id); return 4096L + id; }
    }
    public static final class Queue {
        public static final Queue INSTANCE = new Queue();
        private static Command currentCmdBuffer;
        static int resetsQueued;
        public void endRecordingAndSubmit() {
            trace.add("neo.end");
            if (submitFailure) throw new IllegalStateException("submit failed");
            // This is Reforged's boundary contract: a null current buffer really dereferences null.
            currentCmdBuffer.submissions++;
            lastSubmitted = currentCmdBuffer;
            resetsQueued++;
            currentCmdBuffer = null;
        }
        public void startRecording() { trace.add("neo.start"); pending(); }
        public Command getCommandBuffer() { throw new AssertionError("A read-only pending query must not allocate via getCommandBuffer"); }
    }
    public static final class Sync {
        public static final Sync INSTANCE = new Sync();
        final List<Long> independent = new ArrayList<>(), drained = new ArrayList<>();
        int waitCalls;
        public void waitFences() {
            trace.add("sync.wait"); waitCalls++;
            if (syncFailure) throw new IllegalStateException("sync failed");
            drained.addAll(independent); independent.clear();
        }
    }
    public static final class Fabric {
        public static final Fabric INSTANCE = new Fabric();
        public void submitCommands() { trace.add("fabric.submit"); if (fabricFailure) throw new IllegalStateException("fabric failed"); }
    }
    public static final class NativeBoundary {
        public static int vkWaitForFences(Device device, long fence, boolean all, long timeout) {
            trace.add("vk.wait:" + fence + ":" + all + ":" + timeout);
            require(device == Device.vkDevice && lastSubmitted != null && fence == 4096L + lastSubmitted.id && all && timeout == -1L,
                    "checked native wait targets the captured submitted fence on the borrowed device");
            return waitResult;
        }
    }
    public static final class ResultBoundary {
        public static void _CHECK_(int result) {
            trace.add("vk.check:" + result);
            if (result != 0) throw new IllegalStateException("vk result " + result);
        }
    }
}
