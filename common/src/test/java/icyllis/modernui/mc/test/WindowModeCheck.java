package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarFile;

/** Runs the product enum and each real Window state machine; only OS/device boundaries are substituted. */
public final class WindowModeCheck {
    private static final String HERE=Type.getInternalName(WindowModeCheck.class);
    private static final String WINDOW="com/mojang/blaze3d/platform/Window";
    private static final String MODE="icyllis/modernui/mc/Config$Client$WindowMode";
    private static final List<String> CUSTOM=List.of("FULLSCREEN_BORDERLESS","MAXIMIZED","MAXIMIZED_BORDERLESS","WINDOWED","WINDOWED_BORDERLESS");
    public record Geometry(int x,int y,int width,int height) {}
    public record MonitorCall(long window,long monitor,int x,int y,int width,int height,int refresh) {}
    public static final class Video {
        public int getWidth(){return 1920;} public int getHeight(){return 1080;} public int getRefreshRate(){return 144;}
    }
    public static final class Monitor {
        public int x(){return -1920;} public int y(){return 240;} public long monitor(){return 77;}
        public Video currentMode(){return new Video();} public Video getPreferredVidMode(Optional<?> mode){return new Video();}
    }
    public static final class Screens {
        public boolean available=true;
        public Monitor findBestMonitor(Object window){return available?new Monitor():null;}
    }
    public interface EventHandler { void framebufferSizeChanged(); }
    public static final class Events implements EventHandler {
        public int resized;
        public void framebufferSizeChanged(){resized++;Native.events.add("framebufferSizeChanged");}
    }
    public static final class Device {
        public int updates;
        public void setVsync(boolean value){updates++;Native.events.add("vsync:"+value);}
    }
    public static final class RenderSystem {
        public static final Device device=new Device();
        public static void assertOnRenderThread() {}
        public static Device getDevice(){return device;}
    }
    public static final class Minecraft {
        public static Minecraft instance;
        public Object window;
        public static Minecraft getInstance(){return instance;}
        public Object getWindow(){return window;}
    }
    public static final class Macos {
        public static final boolean IS_MACOS=false;
        public static void exitNativeFullscreen(Object window){throw new AssertionError("Unexpected macOS path");}
        public static void clearResizableBit(Object window){throw new AssertionError("Unexpected macOS path");}
    }
    public static final class Native {
        public static long monitor;
        public static Geometry geometry;
        public static int decorated, restores, maximizes;
        public static final List<String> events=new ArrayList<>();
        public static final List<MonitorCall> monitors=new ArrayList<>();
        private static void window(long handle){if(handle!=42)throw new AssertionError("Wrong native window handle");}
        public static long glfwGetWindowMonitor(long handle){window(handle);return monitor;}
        public static void glfwSetWindowMonitor(long handle,long target,int x,int y,int width,int height,int refresh){
            window(handle);monitor=target;geometry=new Geometry(x,y,width,height);monitors.add(new MonitorCall(handle,target,x,y,width,height,refresh));events.add("monitor:"+width+"x"+height);
        }
        public static void glfwRestoreWindow(long handle){window(handle);restores++;events.add("restore");}
        public static void glfwMaximizeWindow(long handle){window(handle);maximizes++;geometry=new Geometry(-1920,240,1920,1080);events.add("maximize");}
        public static void glfwSetWindowAttrib(long handle,int attribute,int value){window(handle);if(attribute!=org.lwjgl.glfw.GLFW.GLFW_DECORATED)throw new AssertionError("Wrong window attribute");decorated=value;events.add("decorated:"+value);}
        static void reset(boolean exclusive){monitor=exclusive?77:0;geometry=exclusive?new Geometry(0,0,1920,1080):new Geometry(211,133,1366,768);decorated=1;restores=maximizes=0;events.clear();monitors.clear();RenderSystem.device.updates=0;}
    }
    private static final class Loader extends ClassLoader {
        Loader(){super(WindowModeCheck.class.getClassLoader());}
        Class<?> define(ClassNode node){var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] bytes=writer.toByteArray();return defineClass(node.name.replace('/','.'),bytes,0,bytes.length);}
    }
    private record Harness(Class<?> window,Class<?> mode,Method apply,Method frame) {}
    public static void main(String[] args)throws Exception {
        if(args.length==0)throw new AssertionError("Pass actual Minecraft client JARs");
        for(int i=0;i<args.length;i++)try(var jar=new JarFile(args[i])){
            verifyCallSite(jar);
            var fixed=create(jar,i,true);var baseline=create(jar,i,false);
            for(String mode:CUSTOM)for(boolean pending:new boolean[]{false,true}){
                checkCase(fixed,mode,pending,true);checkCase(baseline,mode,pending,false);
            }
            checkStable(fixed);checkDeferredFullscreen(fixed);checkMissingMonitor(fixed);
            System.out.println(jar.getName()+": five custom modes, fullscreen/pending exit, retained restore/framebuffer callback path, next-frame negative controls and already-windowed/NORMAL behavior passed");
        }
        System.out.println(args.length+" actual Window bytecode contracts and production WindowMode.apply passed");
    }
    private static Harness create(JarFile jar,int index,boolean fixed)throws Exception {
        var loader=new Loader();String suffix=index+(fixed?"Fixed":"Baseline"),windowName=HERE+"$Window"+suffix,modeName=HERE+"$Mode"+suffix;
        var names=new HashMap<String,String>();names.put(WINDOW,windowName);names.put(MODE,modeName);
        names.put("com/mojang/blaze3d/platform/MonitorManager",HERE+"$Screens");names.put("com/mojang/blaze3d/platform/Monitor",HERE+"$Monitor");names.put("com/mojang/blaze3d/platform/VideoMode",HERE+"$Video");names.put("com/mojang/blaze3d/platform/WindowEventHandler",HERE+"$EventHandler");
        names.put("com/mojang/blaze3d/systems/RenderSystem",HERE+"$RenderSystem");names.put("com/mojang/blaze3d/systems/GpuDevice",HERE+"$Device");names.put("com/mojang/blaze3d/platform/MacosUtil",HERE+"$Macos");names.put("net/minecraft/client/Minecraft",HERE+"$Minecraft");
        var actual=read(jar,WINDOW);var window=shell(WINDOW);
        for(var field:actual.fields)window.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|(field.access&Opcodes.ACC_STATIC),field.name,field.desc,null,null));
        Set<String> methods=Set.of("toggleFullScreen","updateFullscreenIfChanged","updateFullscreen","setMode","updateVsync","allowedWindowMinSize","findBestMonitor","handle","isFullscreen");
        for(var method:actual.methods)if(methods.contains(method.name))window.methods.add(method);
        for(var method:window.methods)for(var instruction:method.instructions){
            if(instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/glfw/GLFW"))call.owner=HERE+"$Native";
            if(instruction instanceof MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/platform/MonitorManager")&&call.name.equals("findBestMonitor"))call.desc="(Ljava/lang/Object;)Lcom/mojang/blaze3d/platform/Monitor;";
            if(instruction instanceof MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/platform/MacosUtil"))call.desc="(Ljava/lang/Object;)V";
        }
        Class<?> windowClass=loader.define(remap(window,names));
        var mode=resource(MODE);mode.nestHostClass=null;mode.nestMembers=null;mode.innerClasses.clear();mode.outerClass=null;mode.outerMethod=null;mode.outerMethodDesc=null;
        mode.methods.removeIf(m->m.name.equals("toString"));
        MethodNode apply=method(mode,"apply","()V");int flushes=0;
        for(var instruction:apply.instructions.toArray()){
            if(instruction instanceof MethodInsnNode call && call.owner.equals(WINDOW)&&call.name.equals("updateFullscreenIfChanged")){
                flushes++;
                if(!fixed){var receiver=instruction.getPrevious();check(receiver instanceof VarInsnNode var && var.getOpcode()==Opcodes.ALOAD,"Expected the actual window receiver before flush");apply.instructions.remove(receiver);apply.instructions.remove(instruction);}
            }
            if(instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/glfw/GLFW"))call.owner=HERE+"$Native";
            if(instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/Minecraft")&&call.name.equals("getWindow")){
                call.desc="()Ljava/lang/Object;";apply.instructions.insert(call,new TypeInsnNode(Opcodes.CHECKCAST,WINDOW));
            }
        }
        check(flushes==5,"Production apply must flush each of the five custom modes exactly once");
        Class<?> modeClass=loader.define(remap(mode,names));return new Harness(windowClass,modeClass,modeClass.getMethod("apply"),windowClass.getMethod("updateFullscreenIfChanged"));
    }
    private static Object setup(Harness h,boolean exclusive,boolean pending)throws Exception {
        Native.reset(exclusive);Object window=h.window.getConstructor().newInstance();
        set(h,window,"handle",42L);set(h,window,"fullscreen",exclusive&&!pending);set(h,window,"actuallyFullscreen",exclusive);set(h,window,"vsync",true);
        set(h,window,"windowedX",120);set(h,window,"windowedY",130);set(h,window,"windowedWidth",1600);set(h,window,"windowedHeight",900);
        set(h,window,"x",Native.geometry.x);set(h,window,"y",Native.geometry.y);set(h,window,"width",Native.geometry.width);set(h,window,"height",Native.geometry.height);
        set(h,window,"monitorManager",new Screens());set(h,window,"eventHandler",new Events());set(h,window,"preferredFullscreenVideoMode",Optional.empty());
        var game=new Minecraft();game.window=window;Minecraft.instance=game;return window;
    }
    private static void apply(Harness h,String mode)throws Exception {h.apply.invoke(h.mode.getField(mode).get(null));}
    private static void checkCase(Harness h,String mode,boolean pending,boolean fixed)throws Exception {
        Object window=setup(h,true,pending);apply(h,mode);Geometry applied=Native.geometry;int calls=Native.monitors.size(),events=Native.events.size();
        var callback=(Events)h.window.getField("eventHandler").get(window);
        if(fixed){
            check(!h.window.getField("fullscreen").getBoolean(window)&&!h.window.getField("actuallyFullscreen").getBoolean(window),"Pending vanilla fullscreen state remained after custom apply");
            check(callback.resized==1,"Custom mode must retain vanilla framebufferSizeChanged transition");
            check(Native.events.indexOf("monitor:1600x900")<firstCustomEvent(),"Custom GLFW action ran before vanilla restore");
            if(mode.equals("FULLSCREEN_BORDERLESS")){
                check(Native.monitors.getLast().equals(new MonitorCall(42,0,-1920,240,1920,1080,-1)),"Borderless monitor x/y/width/height/refresh parameters changed");
                check(applied.equals(new Geometry(-1920,240,1920,1080)),"Borderless geometry changed");
            }
            if(mode.startsWith("WINDOWED"))check(applied.equals(new Geometry(120,130,1600,900)),"Windowed restore geometry changed");
            check(Native.decorated==(mode.equals("MAXIMIZED")||mode.equals("WINDOWED")?1:0),"Decoration parameter changed");
            check(Native.maximizes==(mode.startsWith("MAXIMIZED")?1:0),"Unexpected maximize path");
        }else check(callback.resized==0,"Negative control unexpectedly drained pending state");
        h.frame.invoke(window);
        if(fixed)check(Native.geometry.equals(applied)&&Native.monitors.size()==calls&&Native.events.size()==events&&callback.resized==1,"A later real frame overwrote custom geometry or repeated transition");
        else{
            check(Native.geometry.equals(new Geometry(120,130,1600,900))&&Native.monitors.size()==calls+1,"Negative control did not reproduce the later vanilla overwrite");
            if(mode.equals("FULLSCREEN_BORDERLESS"))check(applied.width==1920&&Native.geometry.width==1600,"Borderless negative control must reproduce 1920 to 1600");
        }
    }
    private static int firstCustomEvent(){for(int i=0;i<Native.events.size();i++)if(Native.events.get(i).startsWith("restore")||Native.events.get(i).startsWith("decorated")||Native.events.get(i).startsWith("maximize"))return i;return -1;}
    private static void checkStable(Harness h)throws Exception {
        for(String mode:CUSTOM){Object window=setup(h,false,false);apply(h,mode);Geometry applied=Native.geometry;int calls=Native.monitors.size();h.frame.invoke(window);check(Native.geometry.equals(applied)&&Native.monitors.size()==calls,"Already-windowed mode introduced a later transition");check(((Events)h.window.getField("eventHandler").get(window)).resized==0,"Already-windowed no-op flush changed framebuffer callback");if(mode.startsWith("WINDOWED"))check(applied.equals(new Geometry(211,133,1366,768)),"Windowed mode forced stale saved dimensions");}
        for(boolean exclusive:new boolean[]{false,true}){Object window=setup(h,exclusive,false);Geometry before=Native.geometry;apply(h,"NORMAL");check(Native.geometry.equals(before)&&Native.events.isEmpty()&&h.window.getField("fullscreen").getBoolean(window)==exclusive,"NORMAL must remain a no-op");}
    }
    private static void checkDeferredFullscreen(Harness h)throws Exception {
        Object window=setup(h,false,false);apply(h,"FULLSCREEN");check(Native.events.isEmpty()&&h.window.getField("fullscreen").getBoolean(window)&&!h.window.getField("actuallyFullscreen").getBoolean(window),"FULLSCREEN should retain the original deferred transition");h.frame.invoke(window);check(Native.monitors.getLast().equals(new MonitorCall(42,77,0,0,1920,1080,144)),"Actual vanilla fullscreen parameters changed");
    }
    private static void checkMissingMonitor(Harness h)throws Exception {
        Object window=setup(h,true,false);((Screens)h.window.getField("monitorManager").get(window)).available=false;apply(h,"FULLSCREEN_BORDERLESS");check(Native.maximizes==1,"Missing-monitor fallback must maximize after native exit");Geometry applied=Native.geometry;h.frame.invoke(window);check(Native.geometry.equals(applied),"Later frame overwrote monitor fallback");
    }
    private static void verifyCallSite(JarFile jar)throws Exception {
        var render=method(read(jar,"net/minecraft/client/Minecraft"),"renderFrame","(Z)V");check(Arrays.stream(render.instructions.toArray()).filter(i->i instanceof MethodInsnNode call&&call.owner.equals(WINDOW)&&call.name.equals("updateFullscreenIfChanged")&&call.desc.equals("()V")).count()==1,"Actual next-frame fullscreen update call changed");
    }
    private static void set(Harness h,Object window,String name,Object value)throws Exception{h.window.getField(name).set(window,value);}
    private static MethodNode method(ClassNode node,String name,String descriptor){return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst().orElseThrow();}
    private static ClassNode shell(String name){var node=new ClassNode();node.version=Opcodes.V21;node.access=Opcodes.ACC_PUBLIC;node.name=name;node.superName="java/lang/Object";var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);return node;}
    private static ClassNode remap(ClassNode node,Map<String,String> names){var output=new ClassNode();node.accept(new ClassRemapper(output,new SimpleRemapper(names)));return output;}
    private static ClassNode read(JarFile jar,String name)throws Exception{try(var input=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name+".class")))){var node=new ClassNode();new ClassReader(input).accept(node,0);return node;}}
    private static ClassNode resource(String name)throws Exception{try(var input=WindowModeCheck.class.getResourceAsStream("/"+name+".class")){var node=new ClassNode();new ClassReader(Objects.requireNonNull(input)).accept(node,0);return node;}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
