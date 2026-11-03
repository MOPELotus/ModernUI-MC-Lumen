package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * Audits the pinned optional releases without loading VulkanMod or creating a GPU context.
 * Traces same-typed upload arguments through each release's copy helper into Vulkan fields.
 */
public final class VulkanBridgeApiCheck {
 static void requireMethod(ClassNode owner, String name, String descriptor) {
  if (owner.methods.stream().noneMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)
          && (m.access & Opcodes.ACC_PUBLIC) != 0)) throw new AssertionError(owner.name + "." + name + descriptor);
 }
 static void requireField(ClassNode owner, String name, String descriptor) {
  if (owner.fields.stream().noneMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)))
   throw new AssertionError(owner.name + "." + name + ":" + descriptor);
 }
 static void contract(JarFile jar, boolean fabric) throws Exception {
  String prefix = "net/vulkanmod/";
  var image = read(jar, prefix + "vulkan/texture/VulkanImage");
  requireMethod(image, "<init>", fabric ? "(Ljava/lang/String;JIIIIIIJ)V" : "(JIIIIIIJ)V");
  for (String name : new String[]{"mainImageView", "sampler", "id"}) requireField(image, name, "J");
  for (String name : new String[]{"width", "height", "mipLevels", "format", "aspect", "currentLayout"}) requireField(image, name, "I");
  requireMethod(image, "builder", "(II)Lnet/vulkanmod/vulkan/texture/VulkanImage$Builder;");
  requireMethod(image, "readOnlyLayout", "()V");
  requireMethod(image, "setCurrentLayout", "(I)V");
  requireMethod(image, "getCurrentLayout", "()I");
  requireMethod(image, "transitionImageLayout", "(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;I)V");
  var builder = read(jar, image.name + "$Builder");
  String builderReturn = "L" + builder.name + ";";
  for (String name : new String[]{"setFormat", "setMipLevels", "setUsage"}) requireMethod(builder, name, "(I)" + builderReturn);
  for (String name : new String[]{"setLinearFiltering", "setClamp"}) requireMethod(builder, name, "(Z)" + builderReturn);
  requireMethod(builder, "createVulkanImage", "()L" + image.name + ";");
  var texture = read(jar, prefix + "gl/" + (fabric ? "VkGlTexture" : "GlTexture"));
  requireMethod(texture, "genTextureId", "()I");
  requireMethod(texture, "bindIdToImage", "(IL" + image.name + ";)V");
  requireMethod(texture, "setVulkanImage", "(IL" + image.name + ";)V");
  requireMethod(texture, "glDeleteTextures", "(I)V");
  requireMethod(texture, "getTexture", "(I)L" + texture.name + ";");
  requireMethod(texture, "getVulkanImage", "()L" + image.name + ";");
  var queue = read(jar, prefix + "vulkan/queue/Queue");
  requireMethod(queue, fabric ? "vkQueue" : "queue", "()Lorg/lwjgl/vulkan/VkQueue;");
  requireMethod(queue, "beginCommands", "()Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;");
  requireMethod(queue, "submitCommands", "(Lnet/vulkanmod/vulkan/queue/CommandPool$CommandBuffer;)J");
  if (!fabric) requireMethod(read(jar, prefix + "vulkan/queue/GraphicsQueue"), "endRecordingAndSubmit", "()V");
  else requireMethod(read(jar, prefix + "render/texture/ImageUploadHelper"), "submitCommands", "()V");
  var pipelineManager = read(jar, prefix + (fabric ? "render/shader/" : "render/") + "PipelineManager");
  requireMethod(pipelineManager, fabric ? "getFastBlitPipeline" : "getGlFramebufferBlitPipeline",
          "()Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;");
  var renderer = read(jar, prefix + "vulkan/Renderer");
  requireMethod(renderer, "flushCmds", "()V");
  requireMethod(renderer, "bindGraphicsPipeline", "(Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;)V");
  requireMethod(renderer, "uploadAndBindUBOs", "(Lnet/vulkanmod/vulkan/shader/Pipeline;)V");
  requireMethod(renderer, "getCommandBuffer", "()Lorg/lwjgl/vulkan/VkCommandBuffer;");
  var selector = read(jar, prefix + "vulkan/texture/VTextureSelector");
  requireMethod(selector, "bindTexture", "(IL" + image.name + ";)V");
  requireMethod(selector, "getBoundTexture", "(I)L" + image.name + ";");
  requireField(read(jar, prefix + "vulkan/VRenderSystem"), "topology", "I");
  // These two native shader selections share the exact same UV convention.
  String shaderPath = "assets/vulkanmod/shaders/basic/blit/" + (fabric ? "blit.vsh" : "blit_gl.vsh");
  String shader;
  try (var input = jar.getInputStream(jar.getJarEntry(shaderPath))) {
   shader = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", "");
  }
  if (!shader.contains("vec2(0,0),vec2(2,0),vec2(0,2)")) throw new AssertionError("Native lower-left UV convention changed");

 }
 static ClassNode read(JarFile jar,String owner)throws Exception {var c=new ClassNode();try(var in=jar.getInputStream(jar.getJarEntry(owner+".class"))){new ClassReader(in).accept(c,0);}return c;}
 static String source(SourceValue v){var parts=new TreeSet<String>();for(var n:v.insns){if(n instanceof VarInsnNode x)parts.add("local"+x.var);else if(n instanceof IntInsnNode x)parts.add("constant"+x.operand);else if(n instanceof MethodInsnNode x)parts.add("return:"+x.name);else if(n instanceof InsnNode x && x.getOpcode()>=Opcodes.ICONST_M1 && x.getOpcode()<=Opcodes.ICONST_5)parts.add("constant"+(x.getOpcode()-Opcodes.ICONST_0));else parts.add("opcode"+n.getOpcode());}return parts.toString();}
 static List<String> args(MethodNode m,ClassNode owner,String target, String desc)throws Exception{
  var frames=new Analyzer<>(new SourceInterpreter()).analyze(owner.name,m);var found=new ArrayList<List<String>>();
  for(int i=0;i<m.instructions.size();i++)if(m.instructions.get(i)instanceof MethodInsnNode call && call.name.equals(target)&&(desc==null||call.desc.equals(desc))){var f=frames[i];int count=Type.getArgumentTypes(call.desc).length;var list=new ArrayList<String>();for(int j=f.getStackSize()-count;j<f.getStackSize();j++)list.add(source(f.getStack(j)));found.add(list);}
  if(found.size()!=1)throw new AssertionError(target+" call count "+found.size());return found.getFirst();
 }
 static MethodNode method(ClassNode c,String name,String desc){return c.methods.stream().filter(m->m.name.equals(name)&&(desc==null||m.desc.equals(desc))).findFirst().orElseThrow();}
 static void check(Object actual,Object expected){if(!actual.equals(expected))throw new AssertionError("Actual "+actual+" != "+expected);}
 public static void main(String[] paths)throws Exception{
  var observedFacades = new HashSet<String>();
  if (paths.length != 2) throw new AssertionError("Two pinned official VulkanMod 1.21.1 JARs required");
  for(String path:paths)try(var jar=new JarFile(path)){
   var image=read(jar,"net/vulkanmod/vulkan/texture/VulkanImage");var util=read(jar,"net/vulkanmod/vulkan/texture/ImageUtil");boolean fabric=jar.getEntry("net/vulkanmod/gl/VkGlTexture.class")!=null;
   if (!observedFacades.add(fabric ? "fabric" : "neo")) throw new AssertionError("Need one release per loader");
   contract(jar, fabric);
   var entry=method(image,"uploadSubTextureAsync","(IIIIIIIILjava/nio/ByteBuffer;)V");
   MethodNode upload;
   if(fabric){var delegate=args(entry,image,"uploadSubTextureAsync","(IIIIIIIIIJ)V");check(delegate,List.of("[local1]","[constant0]","[local2]","[local3]","[local4]","[local5]","[local6]","[local7]","[local8]","[return:memAddress]"));System.out.println("Fabric entry inserts arrayLayer=0; otherwise all eight input locals preserved: "+delegate);upload=method(image,"uploadSubTextureAsync","(IIIIIIIIIJ)V");}
   else upload=entry;
   var copy=args(upload,image,"copyBufferToImageCmd",null);System.out.println((fabric?"Fabric":"Neo")+" upload->Vk copy helper arguments: "+copy);
   int offset=fabric?4:3;
   check(copy.get(offset),fabric?"[local2]":"[local1]"); // Fabric adds array-layer before mip
   check(copy.get(offset+1),fabric?"[local1]":"[local2]");
   check(copy.get(offset+2),fabric?"[local3]":"[local3]");
   check(copy.get(offset+3),fabric?"[local4]":"[local4]");
   check(copy.get(offset+4),fabric?"[local5]":"[local5]");
   check(copy.get(offset+5),fabric?"[local6]":"[opcode136]");
   check(copy.get(copy.size() - 2), fabric ? "[local9]" : "[local8]");
   check(copy.getLast(), fabric ? "[local4]" : "[local3]");
   var helper=method(util,"copyBufferToImageCmd",null);
   check(args(helper,util,"mipLevel",null),List.of(fabric?"[local7]":"[local5]"));
   check(args(helper,util,"bufferRowLength",null),List.of(fabric?"[local13]":"[local11]"));
   check(args(helper,util,"bufferImageHeight",null),List.of(fabric?"[local14]":"[local12]"));
   check(args(helper,util,"set","(III)Lorg/lwjgl/vulkan/VkOffset3D;"),List.of(fabric?"[local10]":"[local8]",fabric?"[local11]":"[local9]","[constant0]"));
   check(args(helper,util,"set","(III)Lorg/lwjgl/vulkan/VkExtent3D;"),List.of(fabric?"[local8]":"[local6]",fabric?"[local9]":"[local7]","[constant1]"));
   System.out.println("Verified Vulkan fields: mip=input1, extent=input2,input3, offset=input4,input5, rowLength=input8; layer=0 and depth=1.");
   int staged=-1,copied=-1;for(int i=0;i<upload.instructions.size();i++)if(upload.instructions.get(i)instanceof MethodInsnNode call){if(call.name.equals("copyBuffer"))staged=i;if(call.name.equals("copyBufferToImageCmd"))copied=i;}
   if(staged<0||copied<=staged)throw new AssertionError("Upload is not synchronously staged before GPU copy recording");
   System.out.println("CPU source staged before return/copy command: instructions "+staged+" < "+copied);
  }
 }
}
