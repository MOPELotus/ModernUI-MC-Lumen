package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.jar.JarFile;

/** Executes the release close bodies, replacing native operations with counted CPU operations. */
public final class VulkanShutdownOrderCheck {
    private static final String HERE = Type.getInternalName(VulkanShutdownOrderCheck.class);
    private static final String GPU = "net/vulkanmod/render/engine/VkGpuDevice";
    private static final String FACADE = "com/mojang/blaze3d/systems/GpuDevice";
    private static final String VULKAN = "net/vulkanmod/vulkan/Vulkan";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/";
    private static final String PREFIX = HERE + "$Generated";
    public interface Backend { void close(); }
    public static final class Program {
        public static final Program INVALID_PROGRAM = new Program();
        public void close() { scheduled++; }
    }
    public static final class Shader {
        public static final Shader INVALID_SHADER = new Shader();
        public void close() { scheduled++; }
    }
    public record Pipeline(Program program) {}
    public static final class Calls {
        public static VkDevice vkDevice;
        public static Object facade;
        public static int vkDeviceWaitIdle(VkDevice device) { events.add("idle"); return 0; }
        public static void vkDestroyCommandPool(VkDevice device,long handle,VkAllocationCallbacks callbacks) {}
        public static void vkDestroyFence(VkDevice device,long handle,VkAllocationCallbacks callbacks) {}
        public static void destroyPipelineCache() {}
        public static Object renderer() { return null; }
        public static void rendererCleanup(Object renderer) {}
        public static void freeStagingBuffers() {}
        public static Object memory() { return null; }
        public static void drain(Object memory) { events.add("drain"); drained += scheduled; scheduled = 0; }
        public static void vmaDestroyAllocator(long allocator) {}
        public static void samplerCleanup() {}
        public static void destroy() { events.add("destroy"); premature = resourcesClosed != 1 || scheduled != 0; }
        public static void destroyDebugUtilsMessengerEXT(VkInstance instance,long handle,VkAllocationCallbacks callbacks) {}
        public static void vkDestroySurfaceKHR(VkInstance instance,long handle,VkAllocationCallbacks callbacks) {}
        public static void vkDestroyInstance(VkInstance instance,VkAllocationCallbacks callbacks) {}
        public static Object getDevice() { return facade; }
        public static Object backend(Object facade) {
            try { return facade.getClass().getField("backend").get(facade); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }
    private static int scheduled, drained, resourcesClosed;
    private static boolean premature;
    private static final List<String> events = new ArrayList<>();
    private static class Loader extends ClassLoader {
        Loader() { super(VulkanShutdownOrderCheck.class.getClassLoader()); }
        Class<?> define(ClassNode node) {
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
            byte[] bytes = writer.toByteArray(); return defineClass(node.name.replace('/','.'), bytes,0,bytes.length);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new AssertionError("Pass actual VulkanMod and Minecraft client JARs");
        try (var vm = new JarFile(args[0]); var mc = new JarFile(args[1])) {
            var mixin = read(vm,"net/vulkanmod/mixin/render/MinecraftMixin");
            var early = method(mixin,"close2","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
            var injection = annotation(early,"Inject");
            check(List.of("close").equals(value(injection,"method")),"VM close target changed");
            var at = (AnnotationNode)((List<?>)value(injection,"at")).getFirst();
            check("INVOKE".equals(value(at,"value")) && ("L"+FACADE+";close()V").equals(value(at,"target")),"VM cleanup no longer precedes facade.close");
            check(value(at,"shift")==null,"VM fixed its cleanup position; review compatibility hook");
            var headInject = annotation(method(mixin,"close","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),"Inject");
            check(List.of("close").equals(value(headInject,"method"))
                    && "HEAD".equals(value((AnnotationNode)((List<?>)value(headInject,"at")).getFirst(),"value")),
                    "VM initial idle must precede vanilla owner cleanup");
            var mcClose = method(read(mc,"net/minecraft/client/Minecraft"),"close","()V");
            check(calls(mcClose,FACADE,"close")==1,"Actual Minecraft close no longer closes exactly one facade");
            var facadeClose = method(read(mc,FACADE),"close","()V");
            check(calls(facadeClose,"com/mojang/blaze3d/systems/GpuDeviceBackend","close")==1,"Actual facade backend delegation changed");
            var cleanup = method(read(vm,VULKAN),"cleanUp","()V");
            var names = new ArrayList<String>();
            for(var instruction:cleanup.instructions)if(instruction instanceof MethodInsnNode call)names.add(call.name);
            check(names.indexOf("vkDeviceWaitIdle")<names.indexOf("freeAllBuffers") && names.indexOf("freeAllBuffers")<names.indexOf("destroy"),"Actual VM idle/drain/device destroy order changed");
            check(calls(cleanup,"org/lwjgl/vulkan/VK10","vkDeviceWaitIdle")==1,
                    "Expected exactly one actual device-idle injection target");
            verifyHook();
            run(vm,mc,false); run(vm,mc,true);
        }
        System.out.println("Actual VM before-close negative control, facade/backend bodies, early owner close and later cache-empty close passed");
    }
    private static void run(JarFile vm,JarFile mc,boolean fixed) throws Exception {
        scheduled=drained=resourcesClosed=0;premature=false;events.clear();
        var loader=new Loader();
        var mappings=new HashMap<String,String>();
        mappings.put(GPU,PREFIX+"Backend"); mappings.put(FACADE,PREFIX+"Facade"); mappings.put(VULKAN,PREFIX+"Vulkan");
        mappings.put("com/mojang/blaze3d/systems/GpuDeviceBackend",HERE+"$Backend");
        mappings.put("com/mojang/blaze3d/opengl/GlRenderPipeline",HERE+"$Pipeline");
        mappings.put("com/mojang/blaze3d/opengl/GlProgram",HERE+"$Program");
        mappings.put("com/mojang/blaze3d/opengl/GlShaderModule",HERE+"$Shader");
        var backend=shell(GPU); backend.interfaces.add("com/mojang/blaze3d/systems/GpuDeviceBackend");
        var realBackend=read(vm,GPU);
        for(String field:List.of("pipelineCache","shaderCache")) backend.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,field,"Ljava/util/Map;",null,null));
        backend.methods.add(method(realBackend,"close","()V")); backend.methods.add(method(realBackend,"clearPipelineCache","()V"));
        var owner=resource(MIXIN+"MixinVulkanGpuDeviceOwnership");
        var ownerMethod=method(owner,"modernui$closeOwnedResources","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        // The existing production handler only uses its actual backend identity and owner tracker.
        ownerMethod.access=Opcodes.ACC_PUBLIC;backend.methods.add(ownerMethod);
        var start=new InsnList();start.add(new VarInsnNode(Opcodes.ALOAD,0));start.add(new InsnNode(Opcodes.ACONST_NULL));start.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,GPU,ownerMethod.name,ownerMethod.desc,false));
        method(backend,"close","()V").instructions.insert(start);
        mappings.put(owner.name,PREFIX+"Backend");
        Class<?> backendClass=loader.define(remap(backend,mappings));
        Object device=backendClass.getConstructor().newInstance();
        backendClass.getField("pipelineCache").set(device,new HashMap<>(Map.of("pipeline",new Pipeline(new Program()))));
        backendClass.getField("shaderCache").set(device,new HashMap<>(Map.of("shader",new Shader())));
        VulkanGpuDeviceOwnership.track(device,new Object(),()->{resourcesClosed++;scheduled++;events.add("owner-close");});
        var facade=shell(FACADE);facade.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"backend","Lcom/mojang/blaze3d/systems/GpuDeviceBackend;",null,null));
        facade.methods.add(method(read(mc,FACADE),"close","()V"));
        Class<?> facadeClass=loader.define(remap(facade,mappings));Object facadeInstance=facadeClass.getConstructor().newInstance();facadeClass.getField("backend").set(facadeInstance,device);Calls.facade=facadeInstance;
        // Execute the production early-close helper through the real facade field.
        var helper=resource("icyllis/modernui/mc/VulkanShutdownOrder");var closeHelper=method(helper,"closeBackend","()V");
        for(var instruction:closeHelper.instructions.toArray()) {
            if(instruction instanceof MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")) {call.owner=HERE+"$Calls";call.desc="()Ljava/lang/Object;";}
            if(instruction instanceof FieldInsnNode field && field.owner.equals(FACADE) && field.name.equals("backend")) closeHelper.instructions.set(field,new MethodInsnNode(Opcodes.INVOKESTATIC,HERE+"$Calls","backend","(Ljava/lang/Object;)Ljava/lang/Object;",false));
        }
        mappings.put(helper.name,PREFIX+"Helper");var helperShell=shell(helper.name);helperShell.methods.add(closeHelper);Class<?> helperClass=loader.define(remap(helperShell,mappings));
        var cleanup=shell(VULKAN);for(var field:read(vm,VULKAN).fields)cleanup.fields.add(field);
        var body=method(read(vm,VULKAN),"cleanUp","()V");cleanup.methods.add(body);
        for(var instruction:body.instructions.toArray()) {
            if(instruction instanceof FieldInsnNode f && f.owner.equals("net/vulkanmod/vulkan/device/DeviceManager") && f.name.equals("vkDevice"))f.owner=HERE+"$Calls";
            if(!(instruction instanceof MethodInsnNode call))continue;
            if(call.owner.startsWith("java/"))continue;
            String previousOwner=call.owner;call.owner=HERE+"$Calls";call.setOpcode(Opcodes.INVOKESTATIC);call.itf=false;
            if(previousOwner.equals("net/vulkanmod/vulkan/Renderer") && call.name.equals("getInstance")){call.name="renderer";call.desc="()Ljava/lang/Object;";}
            else if(previousOwner.equals("net/vulkanmod/vulkan/Renderer")){call.name="rendererCleanup";call.desc="(Ljava/lang/Object;)V";}
            else if(previousOwner.equals("net/vulkanmod/vulkan/memory/MemoryManager") && call.name.equals("getInstance")){call.name="memory";call.desc="()Ljava/lang/Object;";}
            else if(previousOwner.equals("net/vulkanmod/vulkan/memory/MemoryManager")){call.name="drain";call.desc="(Ljava/lang/Object;)V";}
            else if(previousOwner.equals("net/vulkanmod/vulkan/texture/SamplerManager"))call.name="samplerCleanup";
            if(fixed && call.name.equals("vkDeviceWaitIdle")) {
                var hook=method(resource(MIXIN+"MixinVulkanShutdownOrder"),"modernui$closeJavaBackend","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
                hook.name="earlyClose";hook.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;cleanup.methods.add(hook);
                var insert=new InsnList();insert.add(new InsnNode(Opcodes.ACONST_NULL));insert.add(new MethodInsnNode(Opcodes.INVOKESTATIC,VULKAN,"earlyClose",hook.desc,false));body.instructions.insert(call,insert);
            }
        }
        loader.define(remap(cleanup,mappings));
        var vmClose=shell(PREFIX+"MinecraftMixin");vmClose.methods.add(method(read(vm,"net/vulkanmod/mixin/render/MinecraftMixin"),"close","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"));vmClose.methods.add(method(read(vm,"net/vulkanmod/mixin/render/MinecraftMixin"),"close2","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"));
        // Only the VM HEAD waitIdle is redirected; close2 invokes the real generated cleanup body.
        for(var i:method(vmClose,"close","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V").instructions)if(i instanceof MethodInsnNode call){call.owner=HERE+"$Calls";call.name="vkDeviceWaitIdle";call.desc="(Lorg/lwjgl/vulkan/VkDevice;)I";}
        var head=method(vmClose,"close","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");head.instructions.insert(new InsnNode(Opcodes.ACONST_NULL));for(var i:head.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN)head.instructions.insertBefore(i,new InsnNode(Opcodes.POP));
        Class<?> vmClass=loader.define(remap(vmClose,mappings));Object vmInstance=vmClass.getConstructor().newInstance();
        for(String method:List.of("close","close2"))vmClass.getMethod(method,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class).invoke(vmInstance,new Object[]{null});
        facadeClass.getMethod("close").invoke(facadeInstance);
        check(resourcesClosed==1,"Owner close count changed");
        if(fixed)check(!premature && scheduled==0 && drained==3 && events.indexOf("owner-close")<events.indexOf("drain"),"Fixed cleanup lost or duplicated a native owner: "+events);
        else check(premature && scheduled==3 && drained==0 && events.indexOf("owner-close")>events.indexOf("destroy"),"Negative control did not reproduce late close: "+events);
        facadeClass.getField("backend").set(facadeInstance,(Backend)()->{throw new AssertionError("Foreign backend closed");});
        helperClass.getMethod("closeBackend").invoke(null);
        System.out.println((fixed?"fixed":"baseline")+": "+events+", drained="+drained+", pending="+scheduled);
    }
    private static void verifyHook() throws Exception {
        var hook=method(resource(MIXIN+"MixinVulkanShutdownOrder"),"modernui$closeJavaBackend","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        var inject=annotation(hook,"Inject");var at=(AnnotationNode)((List<?>)value(inject,"at")).getFirst();
        check(List.of("cleanUp()V").equals(value(inject,"method")) && Integer.valueOf(1).equals(value(inject,"require")) && Integer.valueOf(1).equals(value(inject,"allow")),"Shutdown hook must remain exact");
        check("AFTER".equals(((String[])value(at,"shift"))[1]) && "Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I".equals(value(at,"target")),"Early close must follow device idle");
    }
    private static ClassNode remap(ClassNode node,Map<String,String> mappings){var output=new ClassNode();node.accept(new ClassRemapper(output,new SimpleRemapper(mappings)));return output;}
    private static ClassNode shell(String name){var node=new ClassNode();node.version=Opcodes.V21;node.access=Opcodes.ACC_PUBLIC;node.name=name;node.superName="java/lang/Object";var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);return node;}
    private static MethodNode method(ClassNode node,String name,String descriptor){return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst().orElseThrow();}
    private static ClassNode read(JarFile jar,String name)throws Exception{try(var input=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name+".class")))){var node=new ClassNode();new ClassReader(input).accept(node,0);return node;}}
    private static ClassNode resource(String name)throws Exception{try(var input=VulkanShutdownOrderCheck.class.getResourceAsStream("/"+name+".class")){var node=new ClassNode();new ClassReader(Objects.requireNonNull(input)).accept(node,0);return node;}}
    private static AnnotationNode annotation(MethodNode method,String suffix){return method.visibleAnnotations.stream().filter(a->a.desc.endsWith("/"+suffix+";")).findFirst().orElseThrow();}
    private static Object value(AnnotationNode annotation,String key){int i=annotation.values.indexOf(key);return i<0?null:annotation.values.get(i+1);}
    private static long calls(MethodNode method,String owner,String name){return Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode call && call.owner.equals(owner)&&call.name.equals(name)).count();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
