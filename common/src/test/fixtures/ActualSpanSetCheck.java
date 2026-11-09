package icyllis.modernui.mc;

import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.style.MetricAffectingSpan;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.jar.*;

/** Tests the patched actual Core SpanSet with 64 real MetricAffectingSpan objects. */
public final class ActualSpanSetCheck {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            check(Class.forName("icyllis.modernui.text.SpanSet"));
            return;
        }
        byte[] original;
        try(JarFile jar=new JarFile(args[0])) { original=jar.getInputStream(jar.getJarEntry("icyllis/modernui/text/SpanSet.class")).readAllBytes(); }
        ClassNode target=new ClassNode(), patch=new ClassNode();
        new ClassReader(original).accept(target,0);new ClassReader(Files.readAllBytes(Path.of(args[1]))).accept(patch,0);
        MethodNode grow=patch.methods.stream().filter(m->m.name.equals("grow")).findFirst().orElseThrow();
        grow.visibleAnnotations=null;grow.invisibleAnnotations=null;
        for(AbstractInsnNode i:grow.instructions) if(i instanceof FieldInsnNode field && field.owner.equals(patch.name)) field.owner=target.name;
        for(AbstractInsnNode i:grow.instructions) if(i instanceof FrameNode frame) {
            if(frame.local!=null) frame.local.replaceAll(v->patch.name.equals(v)?target.name:v);
            if(frame.stack!=null) frame.stack.replaceAll(v->patch.name.equals(v)?target.name:v);
        }
        target.methods.removeIf(m->m.name.equals("grow") && m.desc.equals("(I)V"));target.methods.add(grow);
        ClassWriter writer=new ClassWriter(0);target.accept(writer);byte[] fixed=writer.toByteArray();
        ClassLoader loader=new ClassLoader(ActualSpanSetCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String n,boolean resolve) throws ClassNotFoundException {
                if(!n.equals("icyllis.modernui.text.SpanSet")) return super.loadClass(n,resolve);
                Class<?> c=findLoadedClass(n);if(c==null)c=defineClass(n,fixed,0,fixed.length);if(resolve)resolveClass(c);return c;
            }
        };
        check(loader.loadClass("icyllis.modernui.text.SpanSet"));
    }

    private static void check(Class<?> type) throws ReflectiveOperationException {
        Object set=type.getConstructor(Class.class).newInstance(MetricAffectingSpan.class);
        var add=type.getDeclaredMethod("add",Object.class,int.class,int.class,int.class);add.setAccessible(true);
        Object[] spans=new Object[64];int checks=0;
        for(int i=0;i<64;i++) {
            spans[i]=new MetricAffectingSpan(){public void updateMeasureState(TextPaint paint){}};
            add.invoke(set,spans[i],i*2,i*2+1,0x21+i);
            int[] starts=(int[])type.getField("spanStarts").get(set), ends=(int[])type.getField("spanEnds").get(set), flags=(int[])type.getField("spanFlags").get(set);
            for(int j=0;j<=i;j++) {
                if(starts[j]!=j*2 || ends[j]!=j*2+1 || flags[j]!=0x21+j || ((java.util.List<?>)set).get(j)!=spans[j]) throw new AssertionError("Lost span "+j+" after insertion "+i);
                checks++;
            }
        }
        System.out.println("PASS actual Core SpanSet / real MetricAffectingSpan: "+checks+" boundary/identity checks");
    }
}
