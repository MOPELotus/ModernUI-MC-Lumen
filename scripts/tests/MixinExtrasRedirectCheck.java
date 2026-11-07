import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class MixinExtrasRedirectCheck {
 public static void main(String[] args)throws Exception {
  Object transform=Class.forName("com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer").getConstructor().newInstance();
  var method=transform.getClass().getMethod("transform",Class.forName("org.spongepowered.asm.mixin.extensibility.IMixinInfo"),ClassNode.class);
  int redirects=0,failed=0;
  try(var zip=new ZipFile(args[0])) {
   for(var e:java.util.Collections.list(zip.entries())) {
    if(!e.getName().startsWith("icyllis/modernui/mc/mixin/")||!e.getName().endsWith(".class"))continue;
    var node=new ClassNode();new ClassReader(zip.getInputStream(e)).accept(node,ClassReader.SKIP_CODE);
    if(node.methods.stream().noneMatch(m->m.visibleAnnotations!=null&&m.visibleAnnotations.stream().anyMatch(a->a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"))))continue;
    redirects++;
    try {method.invoke(transform,null,node);}
    catch(java.lang.reflect.InvocationTargetException ex){if(!(ex.getCause() instanceof ClassCastException))throw ex;failed++;System.out.println("FAIL "+e.getName()+" "+ex.getCause());}
   }
  }
  System.out.println("redirectClasses="+redirects+" classCastFailures="+failed);
  if(redirects==0)throw new AssertionError("No production redirects checked");
 }
}
