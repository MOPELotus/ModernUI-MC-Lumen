package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.LongBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Executes real VM sync owners and product hooks with CPU native boundaries; not GPU/presentation acceptance. */
public final class VulkanPresentSemaphoreCheck {
    private static final String VM = "net/vulkanmod/vulkan/Renderer";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinReforgedPresentSemaphores";
    private static final String PREFIX = "icyllis/modernui/mc/test/VulkanPresentExecuted";
    private static int checks, calls, failAt;
    private static long next;
    private static final Set<Long> live = new HashSet<>(), destroyed = new HashSet<>();
    private static final Object device = new Object();
    public static void main(String[] args) throws Exception {
        Path actualJar = null;
        for (String argument : args) try (var jar = new JarFile(argument)) {
            if (jar.getJarEntry("net/vulkanmod/gl/GlTexture.class") != null) actualJar = Path.of(argument);
        }
        if (actualJar == null) throw new IllegalArgumentException("Supply the pinned Reforged API audit JAR");
        ClassNode actual = read(actualJar, VM);
        auditActual(actual);
        var original = new Executed(actual);
        original.create(2,3);
        require(original.pool("renderFinishedSemaphores").size() == 2 && original.pool("imageAvailableSemaphores").size() == 2 && original.pool("inFlightFences").size() == 2,
                "real unpatched owner allocates all three pools by frames, not images");
        int wrong=exercise(original,true);
        require(wrong==2,"old actual frame-index expressions reproduce cross-image re-signal twice for 0,1,2,0");
        original.destroy(); original.destroy();
        require(live.isEmpty(), "actual original destroy handles every list entry once and clears for repeated destroy");
        ClassNode patch=new ClassNode();
        try(var in=Files.newInputStream(Path.of("neoforge/build/classes/java/main",MIXIN+".class"))){new ClassReader(in).accept(patch,ClassReader.SKIP_DEBUG);}
        auditPatch(patch,actual);
        var fixed=new Executed(actual,patch);
        for(int images:new int[]{3,5,1,2,3}){
            fixed.create(2,images);
            require(fixed.pool("renderFinishedSemaphores").size()==images,"present semaphore pool follows actual image count "+images);
            require(fixed.pool("imageAvailableSemaphores").size()==2&&fixed.pool("inFlightFences").size()==2,
                    "acquire semaphores and fences stay frame counted at image count "+images);
            require(new HashSet<>(fixed.pool("renderFinishedSemaphores")).size()==images,"each swapchain image has a distinct semaphore");
            require(exercise(fixed,false)==0,"real patched index handlers preserve per-image presentation ownership");
            fixed.destroy();fixed.destroy();require(live.isEmpty(),"recreate/destruction releases every generation once");
        }
        fixed.create(2,3);
        fixed.resize(6);require(fixed.pool("renderFinishedSemaphores").size()==6,"actual tail expands existing owner pool");
        fixed.resize(1);require(fixed.pool("renderFinishedSemaphores").size()==1,"actual tail shrinks and destroys excess handles");
        fixed.destroy();require(live.isEmpty(),"shrink leaves no orphaned handles");
        failAt=calls+8;
        try{fixed.create(2,5);throw new AssertionError("Expected expansion failure");}
        catch(InvocationTargetException failure){require(failure.getCause() instanceof RuntimeException,"new expansion failure propagates");}
        require(live.isEmpty()&&fixed.pool("renderFinishedSemaphores").isEmpty()&&fixed.pool("imageAvailableSemaphores").isEmpty()&&fixed.pool("inFlightFences").isEmpty(),
                "new expansion failure calls actual owner cleanup including previously appended handles");
        failAt=0;
        try{fixed.create(2,0);throw new AssertionError("Expected invalid image count");}
        catch(InvocationTargetException failure){require(failure.getCause() instanceof RuntimeException,"invalid image count fails closed");}
        fixed.destroy();require(live.isEmpty(),"invalid count cleanup leaves no owned handles");
        for(boolean error:new boolean[]{false,true}){
            fixed.create(2,2);
            var pool=new ThrowingPool(fixed.pool("renderFinishedSemaphores"),error);
            fixed.owner.getField("renderFinishedSemaphores").set(fixed.instance,pool);
            int priorDestroyed=destroyed.size();long priorHandle=next;
            try{fixed.resize(3);throw new AssertionError("Expected unpublished-add failure");}
            catch(InvocationTargetException failure){
                require(failure.getCause()==pool.failure,"real tail propagates original add failure identity: "+error);
            }
            require(next==priorHandle+1&&destroyed.contains(next),"newly created unpublished handle is immediately destroyed");
            require(live.isEmpty()&&destroyed.size()==priorDestroyed+7,
                    "unpublished handle and all six original owner resources are destroyed exactly once");
            require(fixed.pool("renderFinishedSemaphores").isEmpty()&&fixed.pool("imageAvailableSemaphores").isEmpty()&&fixed.pool("inFlightFences").isEmpty(),
                    "real outer owner cleanup clears all original pools after add failure");
            fixed.destroy();require(live.isEmpty(),"repeated destroy after add failure does not release handles twice");
        }
        System.out.println("Vulkan present semaphore: "+checks+" actual-owner/real-hook CPU and structural checks passed; queue submit/present native bodies and GPU not executed");
    }
    private static int exercise(Executed run,boolean old) throws Exception {
        Map<Long,Integer> pending=new HashMap<>();int wrong=0;
        int images=run.owner.getField("imagesNum").getInt(run.instance);
        int[] sequence=images>=3?new int[]{0,1,2,0}:new int[]{0,images-1,0,images-1};
        for(int turn=0;turn<sequence.length;turn++){
            int image=sequence[turn],frame=turn%2;
            pending.entrySet().removeIf(e->e.getValue()==image);
            long signal=run.select("signal",frame,image),present=run.select("present",frame,image);
            require(signal==present,"actual submit signal and present wait select identical semaphore");
            require(run.select("acquire",frame,image)==run.pool("imageAvailableSemaphores").get(frame)&&
                    run.select("fence",frame,image)==run.pool("inFlightFences").get(frame),
                    "unmodified actual acquire/fence expressions retain currentFrame");
            Integer prior=pending.put(signal,image);if(prior!=null&&prior!=image)wrong++;
            if(!old)require(signal==run.pool("renderFinishedSemaphores").get(image),"real patched handlers index semaphore by acquired image");
        }
        return wrong;
    }
    private static void auditPatch(ClassNode patch,ClassNode actual){
        for(String name:List.of("modernui$signalImageIndex","modernui$presentImageIndex")){
            var m=patch.methods.stream().filter(n->n.name.equals(name)).findFirst().orElseThrow();
            var all=new ArrayList<AnnotationNode>();if(m.visibleAnnotations!=null)all.addAll(m.visibleAnnotations);if(m.invisibleAnnotations!=null)all.addAll(m.invisibleAnnotations);
            var a=all.stream().filter(n->n.desc.endsWith("/ModifyArg;")).findFirst().orElseThrow();
            require(Integer.valueOf(0).equals(value(a,"index")),"real patch modifies only ArrayList.get integer argument");
            require(Integer.valueOf(1).equals(value(a,"require"))&&Integer.valueOf(1).equals(value(a,"allow")),"each real index injection requires exactly one target");
            require(List.of("submitFrame()Z").equals(value(a,"method")),"real index patch targets only submitFrame exact descriptor");
            var slice=(AnnotationNode)value(a,"slice");
            var from=(AnnotationNode)value(slice,"from");var to=(AnnotationNode)value(slice,"to");
            int ordinal=name.equals("modernui$signalImageIndex")?0:1;
            String endpoint=ordinal==0?"pSignalSemaphores":"pWaitSemaphores";
            require(Integer.valueOf(ordinal).equals(value(from,"ordinal"))&&
                    ("L"+VM+";renderFinishedSemaphores:Ljava/util/ArrayList;").equals(value(from,"target")),
                    "real slice starts at exact renderFinished ordinal "+ordinal);
            require(((String)value(to,"target")).contains(";"+endpoint+"("),"real slice ends at proper submit/present setter");
            var submit=actual.methods.stream().filter(n->n.name.equals("submitFrame")).findFirst().orElseThrow();
            int seen=0,gets=0;boolean inside=false,ended=false;
            for(var n:submit.instructions){
                if(n instanceof FieldInsnNode f&&f.name.equals("renderFinishedSemaphores")&&seen++==ordinal)inside=true;
                if(!inside)continue;
                if(n instanceof MethodInsnNode c&&c.owner.equals("java/util/ArrayList")&&c.name.equals("get"))gets++;
                if(n instanceof FieldInsnNode f)require(!f.name.equals("inFlightFences")&&!f.name.equals("imageAvailableSemaphores"),
                        "real patch slice excludes fence and acquire pool lookups");
                if(n instanceof MethodInsnNode c&&c.name.equals(endpoint)){ended=true;break;}
            }
            require(ended&&gets==1,"actual slice contains exactly one ArrayList.get and its endpoint");
        }
        var submit=actual.methods.stream().filter(n->n.name.equals("submitFrame")).findFirst().orElseThrow();
        require(Arrays.stream(submit.instructions.toArray()).filter(n->n instanceof FieldInsnNode f&&f.name.equals("inFlightFences")).count()==3,
                "all three actual submit fence lookups remain outside patched slices");
    }
    private static Object value(AnnotationNode annotation,String key){
        for(int i=0;i<annotation.values.size();i+=2)if(key.equals(annotation.values.get(i)))return annotation.values.get(i+1);
        return null;
    }
    public static final class ThrowingPool extends ArrayList<Long> {
        final Throwable failure;
        ThrowingPool(Collection<Long> original,boolean error){
            super(original);failure=error?new AssertionError("synthetic publication failure"):new IllegalStateException("synthetic publication failure");
        }
        @Override public boolean add(Long value){
            if(failure instanceof Error error)throw error;
            throw (RuntimeException)failure;
        }
    }
    public static final class Stack implements AutoCloseable {
        public static Stack stackPush() { return new Stack(); }
        public LongBuffer mallocLong(int count) { return LongBuffer.allocate(count); }
        public void close() {}
    }
    public static final class SemaphoreInfo {
        public static SemaphoreInfo calloc(Stack stack) { return new SemaphoreInfo(); }
        public SemaphoreInfo sType$Default() { return sType(9); }
        public SemaphoreInfo sType(int value) { require(value == 9, "real owner creates binary semaphore info"); return this; }
    }
    public static final class FenceInfo {
        public static FenceInfo calloc(Stack stack) { return new FenceInfo(); }
        public FenceInfo sType(int value) { return this; }
        public FenceInfo flags(int value) { require(value == 1, "real frame fence starts signaled"); return this; }
    }
    public static final class Checker {
        public static void _CHECK_(int result) { if(result!=0)throw new IllegalStateException("vk result "+result); }
    }
    public static final class Native {
        public static int vkCreateSemaphore(Object d, SemaphoreInfo info, Object allocator, LongBuffer out) {
            require(d == device, "native semaphore uses borrowed VM device");
            if (++calls == failAt) return -2;
            long handle = ++next; live.add(handle); out.put(0,handle); return 0;
        }
        public static int vkCreateFence(Object d, FenceInfo info, Object allocator, LongBuffer out) {
            if (++calls == failAt) return -2;
            long handle = ++next; live.add(handle); out.put(0,handle); return 0;
        }
        public static void vkDestroySemaphore(Object d, long handle, Object allocator) { destroy(d,handle); }
        public static void vkDestroyFence(Object d, long handle, Object allocator) { destroy(d,handle); }
        private static void destroy(Object d, long handle) {
            require(d == device && live.remove(handle) && destroyed.add(handle), "native owner destroys each allocated handle once");
        }
    }
    private static void auditActual(ClassNode actual) {
        var submit = actual.methods.stream().filter(m -> m.name.equals("submitFrame")).findFirst().orElseThrow();
        int finished = 0;
        for (var n : submit.instructions) if (n instanceof FieldInsnNode f && f.name.equals("renderFinishedSemaphores")) {
            finished++;
            var next = n.getNext(); while (next.getOpcode() < 0) next = next.getNext();
            require(next instanceof FieldInsnNode index && index.name.equals("currentFrame"),
                    "actual old submit/present renderFinished lookup uses frame index");
        }
        require(finished == 2, "actual submit has exactly two renderFinished lookups to repair");
        var acquire = actual.methods.stream().filter(m -> m.name.equals("attachAcquireWait")).findFirst().orElseThrow();
        require(Arrays.stream(acquire.instructions.toArray()).anyMatch(n -> n instanceof FieldInsnNode f && f.name.equals("imageAvailableSemaphores")),
                "actual acquire wait retains its independent per-frame semaphore pool");
        require(Arrays.stream(acquire.instructions.toArray()).anyMatch(n -> n instanceof FieldInsnNode f && f.name.equals("currentFrame")),
                "actual acquire wait remains frame indexed");
        var recreate = actual.methods.stream().filter(m -> m.name.equals("recreateSwapChain")).findFirst().orElseThrow();
        int destroy = -1, imageCount = -1, create = -1;
        for (var n : recreate.instructions) {
            if (n instanceof MethodInsnNode m && m.name.equals("destroySyncObjects")) destroy = recreate.instructions.indexOf(n);
            if (n instanceof FieldInsnNode f && f.name.equals("imagesNum") && f.getOpcode() == Opcodes.PUTFIELD) imageCount = recreate.instructions.indexOf(n);
            if (n instanceof MethodInsnNode m && m.name.equals("createSyncObjects")) create = recreate.instructions.indexOf(n);
        }
        require(destroy >= 0 && imageCount > destroy && create > imageCount,
                "actual recreate destroys old pools then updates image count before sync creation");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static ClassNode read(Path jar, String name) throws Exception {
        var node = new ClassNode();
        try (var file = new JarFile(jar.toFile()); var stream = file.getInputStream(file.getJarEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG);
        }
        return node;
    }
    private static final class Executed extends ClassLoader {
        final Class<?> owner;
        final Object instance;
        final Method create, destroy, tail;
        Executed(ClassNode actual) throws Exception { this(actual, null); }
        Executed(ClassNode actual, ClassNode mixin) throws Exception {
            super(VulkanPresentSemaphoreCheck.class.getClassLoader());
            var names = new HashMap<String,String>(); names.put(VM,PREFIX + "Owner"); names.put(MIXIN,PREFIX + "Owner");
            names.put("org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "java/lang/Object");
            names.put("org/lwjgl/system/MemoryStack",Type.getInternalName(Stack.class));
            names.put("org/lwjgl/vulkan/VkSemaphoreCreateInfo",Type.getInternalName(SemaphoreInfo.class));
            names.put("org/lwjgl/vulkan/VkFenceCreateInfo",Type.getInternalName(FenceInfo.class));
            names.put("org/lwjgl/vulkan/VkDevice","java/lang/Object"); names.put("org/lwjgl/vulkan/VkAllocationCallbacks","java/lang/Object");
            names.put("org/lwjgl/vulkan/VK10",Type.getInternalName(Native.class));
            names.put("icyllis/arc3d/vulkan/VKUtil",Type.getInternalName(Checker.class));
            var remapper = new SimpleRemapper(names);
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,PREFIX + "Owner",null,"java/lang/Object",null);
            Set<String> fields = Set.of("framesNum","imagesNum","device","renderFinishedSemaphores","imageAvailableSemaphores","inFlightFences","currentFrame","imageIndex");
            for (var f : actual.fields) if (fields.contains(f.name)) writer.visitField(Opcodes.ACC_PUBLIC | (f.access & Opcodes.ACC_STATIC),f.name,remapper.mapDesc(f.desc),null,null).visitEnd();
            var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(1,1);ctor.visitEnd();
            for (var m : actual.methods) if (m.name.equals("createSyncObjects") || m.name.equals("destroySyncObjects") || m.name.startsWith("lambda$destroySyncObjects$"))
                m.accept(new MethodRemapper(writer.visitMethod(Opcodes.ACC_PUBLIC | (m.access & Opcodes.ACC_STATIC),m.name,remapper.mapMethodDesc(m.desc),null,null),remapper));
            if (mixin != null) for (var m : mixin.methods) if (m.name.equals("modernui$sizePresentSemaphores") ||
                    m.name.equals("modernui$signalImageIndex") || m.name.equals("modernui$presentImageIndex"))
                m.accept(new MethodRemapper(writer.visitMethod(Opcodes.ACC_PUBLIC | (m.access & Opcodes.ACC_STATIC),m.name,remapper.mapMethodDesc(m.desc),null,null),remapper));
            selection(actual, writer, remapper, "submitFrame", "renderFinishedSemaphores", 0, "signal", mixin == null ? null : "modernui$signalImageIndex");
            selection(actual, writer, remapper, "submitFrame", "renderFinishedSemaphores", 1, "present", mixin == null ? null : "modernui$presentImageIndex");
            selection(actual, writer, remapper, "attachAcquireWait", "imageAvailableSemaphores", 0, "acquire", null);
            selection(actual, writer, remapper, "submitFrame", "inFlightFences", 0, "fence", null);
            writer.visitEnd();byte[] bytes = writer.toByteArray();owner=defineClass((PREFIX+"Owner").replace('/','.'),bytes,0,bytes.length);
            instance=owner.getConstructor().newInstance();owner.getField("device").set(null,device);
            create=owner.getMethod("createSyncObjects");destroy=owner.getMethod("destroySyncObjects");
            tail=mixin == null ? null : owner.getMethod("modernui$sizePresentSemaphores",Object.class);
        }
        void create(int frames,int images) throws Exception { owner.getField("framesNum").setInt(instance,frames);owner.getField("imagesNum").setInt(instance,images);create.invoke(instance);if(tail!=null)tail.invoke(instance,new Object[]{null}); }
        void resize(int images) throws Exception { owner.getField("imagesNum").setInt(instance,images);tail.invoke(instance,new Object[]{null}); }
        long select(String role,int frame,int image) throws Exception {
            owner.getField("currentFrame").setInt(null,frame);owner.getField("imageIndex").setInt(null,image);
            return (long)owner.getMethod(role).invoke(instance);
        }
        /** Extracts only a real owner's list-index expression; not the surrounding queue submit or present native call. */
        private static void selection(ClassNode actual, ClassWriter writer, SimpleRemapper remapper, String method,
                                      String field, int ordinal, String name, String handler) {
            var original=actual.methods.stream().filter(m->m.name.equals(method)).findFirst().orElseThrow();
            AbstractInsnNode start=null;int seen=0;
            for(var n:original.instructions)if(n instanceof FieldInsnNode f&&f.name.equals(field)&&seen++==ordinal){start=n.getPrevious();break;}
            if(start==null)throw new AssertionError("Missing actual expression "+field+ordinal);
            var out=new MethodRemapper(writer.visitMethod(Opcodes.ACC_PUBLIC,name,"()J",null,null),remapper);
            out.visitCode();
            for(var n=start;n!=null;n=n.getNext()){
                if(n.getOpcode()<0)continue;
                if(handler!=null&&n instanceof FieldInsnNode f&&f.name.equals("currentFrame")){
                    out.visitVarInsn(Opcodes.ALOAD,0);n.accept(out);
                    out.visitMethodInsn(Opcodes.INVOKEVIRTUAL,VM,handler,"(I)I",false);
                }else n.accept(out);
                if(n instanceof MethodInsnNode c&&c.name.equals("longValue"))break;
            }
            out.visitInsn(Opcodes.LRETURN);out.visitMaxs(4,1);out.visitEnd();
        }
        void destroy() throws Exception { destroy.invoke(instance); }
        @SuppressWarnings("unchecked") ArrayList<Long> pool(String name) throws Exception { return (ArrayList<Long>)owner.getField(name).get(instance); }
    }
}
