package icyllis.modernui.mc.test;

import icyllis.modernui.mc.BackgroundAudioControl;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.sounds.SoundSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarFile;

/** Runs real per-version volume/refresh bodies and production handlers without an OpenAL context. */
public final class BackgroundAudioControlCheck {
    private static final String HERE = Type.getInternalName(BackgroundAudioControlCheck.class);
    private static final String ENGINE = "net/minecraft/client/sounds/SoundEngine";
    private static final String SOURCE = "net/minecraft/sounds/SoundSource";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinSoundEngineAudio";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    public interface Sound { float getVolume(); SoundSource getSource(); }
    public record SoundValue(float getVolume, SoundSource getSource) implements Sound {}
    public static class Channel {
        public int updates;
        public float volume;
        public void setVolume(float volume) { this.volume=volume; updates++; }
    }
    public static class Handle {
        public final Channel channel = new Channel();
        public void execute(java.util.function.Consumer<Channel> action) { action.accept(channel); }
    }
    public static class Options {
        public final Object2FloatMap<SoundSource> sliders = new Object2FloatOpenHashMap<>();
        public float getSoundSourceVolume(SoundSource source) { return sliders.getFloat(source); }
    }
    public static class NativeCalls {
        public static int calls;
        public static boolean iconified;
        public static float listener = 1;
        public static int glfwGetWindowAttrib(long window,int attribute) {
            if (window!=42 || attribute!=org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) throw new AssertionError("Wrong window query");
            return iconified ? 1 : 0;
        }
        public static void alListenerf(int parameter,float value) {
            if(parameter!=org.lwjgl.openal.AL10.AL_GAIN)throw new AssertionError("Wrong listener parameter");
            calls++;listener=value;
        }
    }
    public static class Window { public long handle() { return 42; } }
    public static class Minecraft {
        public boolean active;
        public SoundManager manager;
        public boolean isWindowActive() { return active; }
        public Window getWindow() { return new Window(); }
        public SoundManager getSoundManager() { return manager; }
    }
    public static class SoundManager {
        public Object engine;
        public Method refresh;
        public int refreshes;
        public void refreshCategoryVolume(SoundSource source) {
            if (source!=SoundSource.MASTER) throw new AssertionError("Tick must refresh MASTER");
            refreshes++;
            try { refresh.invoke(engine,source); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }
    public static float clamp(float value,float min,float max) { return Math.max(min,Math.min(max,value)); }
    private static final class Loader extends ClassLoader {
        Loader(){super(BackgroundAudioControlCheck.class.getClassLoader());}
        Class<?> define(ClassNode node){var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] bytes=writer.toByteArray();return defineClass(node.name.replace('/','.'),bytes,0,bytes.length);}
    }
    public static void main(String[] args)throws Exception {
        if(args.length==0)throw new AssertionError("Pass one or more actual Minecraft 26.1.x client JARs");
        for(int version=0;version<args.length;version++)try(var jar=new JarFile(args[version])){verify(jar,version);}
        BackgroundAudioControl.update(1,false);
        System.out.println(args.length + " actual SoundEngine calculators, MASTER/category gain preservation, refresh-all contract and production volume/listener guards passed");
    }
    private static void verify(JarFile jar,int version)throws Exception {
        var actual=read(jar,ENGINE);var mixin=resource(MIXIN);
        var calculator=method(actual,"calculateVolume","(FL"+SOURCE+";)F");
        var load=method(actual,"loadLibrary","()V");
        var handler=method(mixin,"modernui$backgroundVolume","(FL"+SOURCE+";L"+CALLBACK+";)V");
        var reloadHandler=method(mixin,"modernui$restoreListenerGain","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        var refreshHandler=method(mixin,"modernui$updateListenerGain","(L"+SOURCE+";Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        verifyHook(refreshHandler,"refreshCategoryVolume(L"+SOURCE+";)V",1,false,"HEAD");
        verifyHook(handler,"calculateVolume(FL"+SOURCE+";)F",1,true);
        var reloadAnnotation=reloadHandler.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        check(List.of("loadLibrary()V").equals(value(reloadAnnotation,"method"))
                && Integer.valueOf(1).equals(value(reloadAnnotation,"require")) && Integer.valueOf(1).equals(value(reloadAnnotation,"allow")),"Reload success hook must remain exact");
        var reloadAt=(AnnotationNode)((List<?>)value(reloadAnnotation,"at")).getFirst();
        check("FIELD".equals(value(reloadAt,"value")) && ("L"+ENGINE+";loaded:Z").equals(value(reloadAt,"target"))
                && Integer.valueOf(Opcodes.PUTFIELD).equals(value(reloadAt,"opcode"))
                && "AFTER".equals(((String[])value(reloadAt,"shift"))[1]),"Reload gain must follow actual loaded=true success point");
        int successfulLoads=0,initPosition=-1,resetPosition=-1,loadedPosition=-1,position=0;
        for(var instruction:load.instructions){
            if(instruction instanceof MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/audio/Library") && call.name.equals("init"))initPosition=position;
            if(instruction instanceof MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/audio/Listener") && call.name.equals("reset"))resetPosition=position;
            if(instruction instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD && field.owner.equals(ENGINE)&&field.name.equals("loaded")){
                successfulLoads++;loadedPosition=position;
                var previous=instruction.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();
                check(previous!=null&&previous.getOpcode()==Opcodes.ICONST_1,"Loaded hook must select a successful initialization");
            }
            position++;
        }
        check(successfulLoads==1 && initPosition>=0 && resetPosition>initPosition && loadedPosition>resetPosition,"Actual successful audio initialization order changed");
        check(calls(load,"com/mojang/blaze3d/audio/Library","init")==1 && calls(load,"com/mojang/blaze3d/audio/Listener","reset")==1,"Actual audio-context initialization/reset changed");
        check(!hasGainWrite(load),"Vanilla loadLibrary now writes listener gain; review reapplication");
        var listener=read(jar,"com/mojang/blaze3d/audio/Listener");
        check(calls(method(listener,"reset","()V"),"com/mojang/blaze3d/audio/Listener","setTransform")==1
                && !listener.methods.stream().anyMatch(BackgroundAudioControlCheck::hasGainWrite),"Actual Listener.reset gained a gain write");

        var loader=new Loader();String generated=HERE+"$Engine"+version,optionName=HERE+"$Options"+version;
        var mappings=new HashMap<String,String>();
        mappings.put(ENGINE,generated);mappings.put(MIXIN,generated);
        mappings.put("net/minecraft/client/Options",optionName);
        mappings.put("net/minecraft/client/resources/sounds/SoundInstance",HERE+"$Sound");
        mappings.put("net/minecraft/client/sounds/ChannelAccess$ChannelHandle",HERE+"$Handle");
        mappings.put("com/mojang/blaze3d/audio/Channel",HERE+"$Channel");
        var options=shell("net/minecraft/client/Options",HERE+"$Options");
        options.methods.add(method(read(jar,"net/minecraft/client/Options"),"getFinalSoundSourceVolume","(L"+SOURCE+";)F"));
        Class<?> optionClass=loader.define(remap(options,mappings));Options option=(Options)optionClass.getConstructor().newInstance();
        option.sliders.put(SoundSource.MASTER,.5f);option.sliders.put(SoundSource.MUSIC,.4f);option.sliders.put(SoundSource.BLOCKS,.6f);
        var engine=shell(ENGINE,"java/lang/Object");
        engine.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"options","Lnet/minecraft/client/Options;",null,null));
        engine.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"gainBySource","Lit/unimi/dsi/fastutil/objects/Object2FloatMap;",null,null));
        engine.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"loaded","Z",null,null));
        engine.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"instanceToChannel","Ljava/util/Map;",null,null));
        for(var candidate:actual.methods)if(candidate.name.equals("calculateVolume")||candidate.name.equals("updateCategoryVolume")||candidate.name.equals("refreshCategoryVolume")||candidate.name.startsWith("lambda$refreshCategoryVolume$"))engine.methods.add(candidate);
        handler.access=Opcodes.ACC_PUBLIC;reloadHandler.access=Opcodes.ACC_PUBLIC;refreshHandler.access=Opcodes.ACC_PUBLIC;engine.methods.add(handler);engine.methods.add(reloadHandler);engine.methods.add(refreshHandler);
        var refreshBody=method(engine,"refreshCategoryVolume","(L"+SOURCE+";)V");
        var refreshHead=new InsnList();refreshHead.add(new VarInsnNode(Opcodes.ALOAD,0));refreshHead.add(new VarInsnNode(Opcodes.ALOAD,1));refreshHead.add(new InsnNode(Opcodes.ACONST_NULL));refreshHead.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,ENGINE,refreshHandler.name,refreshHandler.desc,false));refreshBody.instructions.insert(refreshHead);
        for(var m:engine.methods)for(var instruction:m.instructions){
            if(instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/util/Mth")&&call.name.equals("clamp"))call.owner=HERE;
            if(instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/openal/AL10")&&call.name.equals("alListenerf"))call.owner=HERE+"$NativeCalls";
        }
        // Apply the actual product RETURN handler to the actual per-version calculator.
        for(var instruction:calculator.instructions.toArray())if(instruction.getOpcode()==Opcodes.FRETURN){
            var inject=new InsnList();
            inject.add(new VarInsnNode(Opcodes.FSTORE,3));
            inject.add(new TypeInsnNode(Opcodes.NEW,CALLBACK));inject.add(new InsnNode(Opcodes.DUP));inject.add(new LdcInsnNode("calculateVolume"));inject.add(new InsnNode(Opcodes.ICONST_1));inject.add(new VarInsnNode(Opcodes.FLOAD,3));
            inject.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,CALLBACK,"<init>","(Ljava/lang/String;ZF)V",false));inject.add(new VarInsnNode(Opcodes.ASTORE,4));
            inject.add(new VarInsnNode(Opcodes.ALOAD,0));inject.add(new VarInsnNode(Opcodes.FLOAD,1));inject.add(new VarInsnNode(Opcodes.ALOAD,2));inject.add(new VarInsnNode(Opcodes.ALOAD,4));
            inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,ENGINE,handler.name,handler.desc,false));inject.add(new VarInsnNode(Opcodes.ALOAD,4));inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,CALLBACK,"getReturnValueF","()F",false));calculator.instructions.insertBefore(instruction,inject);
        }
        Class<?> type=loader.define(remap(engine,mappings));Object instance=type.getConstructor().newInstance();
        type.getField("options").set(instance,option);type.getField("loaded").setBoolean(instance,true);
        var gains=new Object2FloatOpenHashMap<SoundSource>();gains.defaultReturnValue(1);type.getField("gainBySource").set(instance,gains);
        var music=new Handle();var blocks=new Handle();var master=new Handle();
        Map<Sound,Handle> sounds=new HashMap<>();sounds.put(new SoundValue(1,SoundSource.MUSIC),music);sounds.put(new SoundValue(1,SoundSource.BLOCKS),blocks);sounds.put(new SoundValue(1,SoundSource.MASTER),master);type.getField("instanceToChannel").set(instance,sounds);
        Method volume=type.getDeclaredMethod("calculateVolume",float.class,SoundSource.class);volume.setAccessible(true);
        Method update=type.getMethod("updateCategoryVolume",SoundSource.class,float.class),refresh=type.getMethod("refreshCategoryVolume",SoundSource.class);
        BackgroundAudioControl.update(1,false);
        update.invoke(instance,SoundSource.MASTER,.25f);
        near(.2f,number(volume,instance,1,SoundSource.MUSIC),"MASTER gain map must not attenuate MUSIC");
        near(.125f,number(volume,instance,1,SoundSource.MASTER),"MASTER gain map must attenuate MASTER");
        check(gains.getFloat(SoundSource.MUSIC)==1,"MASTER update unexpectedly rewrote source gain");
        update.invoke(instance,SoundSource.MASTER,.7f);update.invoke(instance,SoundSource.MUSIC,.8f);
        var preserved=new Object2FloatOpenHashMap<SoundSource>(gains);
        check(BackgroundAudioControl.update(.25f,false),"Inactive fade did not change game output");
        check(!BackgroundAudioControl.update(.25f,false),"Unchanged state must not cause repeated refresh");
        near(.04f,number(volume,instance,1,SoundSource.MUSIC),"Per-source fade lost user's master/category/source gain");
        near(.0875f,number(volume,instance,1,SoundSource.MASTER),"MASTER options/gain must be applied once");
        near(1,BackgroundAudioControl.listenerGain(),"Non-global mode attenuated shared listener");
        int mu=music.channel.updates,bu=blocks.channel.updates,ma=master.channel.updates;
        refresh.invoke(instance,SoundSource.MASTER);
        check(music.channel.updates==mu+1&&blocks.channel.updates==bu+1&&master.channel.updates==ma+1,"MASTER refresh must update every actual source");
        near(.04f,music.channel.volume,"Refresh did not invoke the actual fixed calculator");
        refresh.invoke(instance,SoundSource.MUSIC);
        check(music.channel.updates==mu+2&&blocks.channel.updates==bu+1,"Category refresh touched another source");
        check(BackgroundAudioControl.update(.25f,true),"Mode flip at unchanged multiplier did not reapply gain");
        near(.16f,number(volume,instance,1,SoundSource.MUSIC),"Global mode applied background multiplier twice");
        near(.35f,number(volume,instance,1,SoundSource.MASTER),"Global MASTER game gain changed");
        near(.25f,BackgroundAudioControl.listenerGain(),"Global listener must apply the multiplier once");
        near(.04f,number(volume,instance,1,SoundSource.MUSIC)*BackgroundAudioControl.listenerGain(),"Global audible gain double-attenuated master");
        NativeCalls.calls=0;NativeCalls.listener=.99f;type.getField("loaded").setBoolean(instance,false);
        refresh.invoke(instance,SoundSource.MASTER);check(NativeCalls.calls==0,"Unloaded refresh called OpenAL without a context");
        type.getField("loaded").setBoolean(instance,true);refresh.invoke(instance,SoundSource.MASTER);
        check(NativeCalls.calls==1,"Loaded refresh must set listener exactly once");near(.25f,NativeCalls.listener,"Refresh did not reapply current global mode");
        Method restored=type.getMethod(reloadHandler.name,CallbackInfo.class);NativeCalls.calls=0;NativeCalls.listener=.99f;
        type.getField("loaded").setBoolean(instance,false);restored.invoke(instance,new CallbackInfo("loadLibrary",false));
        check(NativeCalls.calls==0,"Failed initialization attempted OpenAL without a context");
        type.getField("loaded").setBoolean(instance,true);restored.invoke(instance,new CallbackInfo("loadLibrary",false));
        near(.25f,NativeCalls.listener,"New audio context did not restore the global fade");
        check(NativeCalls.calls==1,"Loaded hook wrote listener gain more than once");
        check(BackgroundAudioControl.update(.25f,false),"Switching back to game-only fade did not refresh");
        restored.invoke(instance,new CallbackInfo("loadLibrary",false));near(1,NativeCalls.listener,"Mode flip did not restore shared listener");
        check(BackgroundAudioControl.update(1,false),"Focus restoration was missed");
        near(.16f,number(volume,instance,1,SoundSource.MUSIC),"Focus restoration lost category gain");
        near(.35f,number(volume,instance,1,SoundSource.MASTER),"Focus restoration lost master gain");
        check(gains.equals(preserved),"Background fade rewrote the actual source gain map");
        near(0,number(volume,instance,-1,SoundSource.MUSIC),"Negative raw volume escaped vanilla clamp");
        near(.16f,number(volume,instance,2,SoundSource.MUSIC),"High raw volume escaped vanilla clamp");
        checkActualTick(loader,version,instance,refresh,gains,music);
        System.out.println(jar.getName()+": actual calculator + Options final volume, source map, refresh lambda, mode flips and loaded guards passed");
    }
    private static void checkActualTick(Loader loader,int version,Object engine,Method refresh,Object2FloatMap<SoundSource> gains,Handle music)throws Exception {
        String original="icyllis/modernui/mc/BlurHandler",generated=HERE+"$Tick"+version;
        var actual=resource(original);var tick=method(actual,"onClientTick","()V");
        check(calls(tick,"icyllis/modernui/mc/BackgroundAudioControl","update")==1,"Tick must apply mode state even at settled volume");
        check(calls(tick,"net/minecraft/client/sounds/SoundManager","refreshCategoryVolume")==1
                && calls(tick,"net/minecraft/client/sounds/SoundManager","updateCategoryVolume")==0
                && !hasGainWrite(tick),"Tick must refresh owners without rewriting MASTER gain or using an unloaded listener");
        var holder=shell(original,"java/lang/Object");holder.methods.add(tick);
        for(String name:List.of("minecraft","mVolumeMultiplier","sGlobalVolumeControl","sMasterVolumeInactive","sMasterVolumeMinimized")){
            var field=actual.fields.stream().filter(f->f.name.equals(name)).findFirst().orElseThrow();
            holder.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|(field.access&Opcodes.ACC_STATIC),name,field.desc,null,null));
        }
        for(var instruction:tick.instructions)if(instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/glfw/GLFW"))call.owner=HERE+"$NativeCalls";
        var names=Map.of(original,generated,"net/minecraft/client/Minecraft",HERE+"$Minecraft",
                "com/mojang/blaze3d/platform/Window",HERE+"$Window","net/minecraft/client/sounds/SoundManager",HERE+"$SoundManager");
        Class<?> type=loader.define(remap(holder,names));Object instance=type.getConstructor().newInstance();
        var game=new Minecraft();var manager=new SoundManager();manager.engine=engine;manager.refresh=refresh;game.manager=manager;
        type.getField("minecraft").set(instance,game);type.getField("mVolumeMultiplier").setFloat(instance,.25f);
        type.getField("sMasterVolumeInactive").setFloat(null,.25f);type.getField("sMasterVolumeMinimized").setFloat(null,.1f);
        type.getField("sGlobalVolumeControl").setBoolean(null,false);NativeCalls.iconified=false;
        Method run=type.getMethod("onClientTick");var preserved=new Object2FloatOpenHashMap<SoundSource>(gains);
        BackgroundAudioControl.update(1,false);run.invoke(instance);
        check(manager.refreshes==1,"Actual settled tick failed initial refresh");near(.04f,music.channel.volume,"Actual inactive tick failed game fade");near(1,NativeCalls.listener,"Non-global tick faded shared listener");
        run.invoke(instance);check(manager.refreshes==1,"Settled unchanged tick refreshed again");
        type.getField("sGlobalVolumeControl").setBoolean(null,true);run.invoke(instance);
        check(manager.refreshes==2,"Actual mode flip at unchanged multiplier did not refresh");near(.25f,NativeCalls.listener,"Actual mode flip failed global listener gain");near(.16f,music.channel.volume,"Actual mode flip retained game-only fade");
        near(.25f,type.getField("mVolumeMultiplier").getFloat(instance),"Mode flip unexpectedly moved settled fade");
        type.getField("sGlobalVolumeControl").setBoolean(null,false);run.invoke(instance);
        check(manager.refreshes==3,"Actual inverse mode flip did not refresh");near(1,NativeCalls.listener,"Actual inverse flip failed listener restoration");near(.04f,music.channel.volume,"Actual inverse mode flip failed game fade");
        NativeCalls.iconified=true;for(int i=0;i<4;i++)run.invoke(instance);
        near(.1f,type.getField("mVolumeMultiplier").getFloat(instance),"Actual minimized tick failed target");near(.016f,music.channel.volume,"Actual minimized tick failed source attenuation");
        game.active=true;run.invoke(instance);run.invoke(instance);
        near(1,type.getField("mVolumeMultiplier").getFloat(instance),"Actual focus restoration failed");near(.16f,music.channel.volume,"Actual focus restoration lost user volume");near(1,NativeCalls.listener,"Actual focus restoration left listener faded");
        check(gains.equals(preserved),"Actual Blur tick modified source gainBySource");
    }
    private static float number(Method m,Object instance,float volume,SoundSource source)throws Exception{return(float)m.invoke(instance,volume,source);}
    private static void verifyHook(MethodNode method,String selector,int count,boolean cancellable){verifyHook(method,selector,count,cancellable,"RETURN");}
    private static void verifyHook(MethodNode method,String selector,int count,boolean cancellable,String position){var annotation=method.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();check(List.of(selector).equals(value(annotation,"method"))&&Integer.valueOf(count).equals(value(annotation,"require"))&&Integer.valueOf(count).equals(value(annotation,"allow")),"Product selector/count changed");if(cancellable)check(Boolean.TRUE.equals(value(annotation,"cancellable")),"Volume RETURN handler must be cancellable");var at=(AnnotationNode)((List<?>)value(annotation,"at")).getFirst();check(position.equals(value(at,"value")),"Audio hook position changed");}
    private static boolean hasGainWrite(MethodNode method){return Arrays.stream(method.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode call&&(call.name.equals("setGain")||call.name.equals("alListenerf")));}
    private static Object value(AnnotationNode node,String key){int index=node.values.indexOf(key);return index<0?null:node.values.get(index+1);}
    private static long calls(MethodNode method,String owner,String name){return Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)).count();}
    private static MethodNode method(ClassNode node,String name,String descriptor){return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst().orElseThrow();}
    private static ClassNode shell(String name,String parent){var node=new ClassNode();node.version=Opcodes.V21;node.access=Opcodes.ACC_PUBLIC;node.name=name;node.superName=parent;var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false));ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);return node;}
    private static ClassNode remap(ClassNode node,Map<String,String> names){var output=new ClassNode();node.accept(new ClassRemapper(output,new SimpleRemapper(names)));return output;}
    private static ClassNode read(JarFile jar,String name)throws Exception{try(var input=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name+".class")))){var node=new ClassNode();new ClassReader(input).accept(node,0);return node;}}
    private static ClassNode resource(String name)throws Exception{try(var input=BackgroundAudioControlCheck.class.getResourceAsStream("/"+name+".class")){var node=new ClassNode();new ClassReader(Objects.requireNonNull(input)).accept(node,0);return node;}}
    private static void near(float expected,float actual,String message){if(Math.abs(expected-actual)>1e-6f)throw new AssertionError(message+": expected="+expected+", actual="+actual);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
