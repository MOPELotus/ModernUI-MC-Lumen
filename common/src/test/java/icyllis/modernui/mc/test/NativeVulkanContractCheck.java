package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

/** Checks the native backend's actual destruction contract without creating a GPU. */
public final class NativeVulkanContractCheck {
    public static void main(String[] args) throws Exception {
        String backend = "com.mojang.blaze3d.vulkan.";
        Class<?> encoder = Class.forName(backend + "VulkanCommandEncoder", false,
                NativeVulkanContractCheck.class.getClassLoader());
        encoder.getMethod("submit");
        Class<?> queue = encoder.getDeclaredField("destroyQueue").getType();
        if (!AutoCloseable.class.isAssignableFrom(queue)) throw new AssertionError("Native destruction queue changed");
        queue.getMethod("close");
        Class<?> device = Class.forName(backend + "VulkanDevice", false,
                NativeVulkanContractCheck.class.getClassLoader());
        device.getMethod("graphicsQueue").getReturnType().getMethod("waitIdle");
        ClassNode minecraft = read("net/minecraft/client/Minecraft");
        int surface = -1, renderer = -1, index = 0, count = 0;
        for (var method : minecraft.methods) {
            if (!method.name.equals("close") || !method.desc.equals("()V")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals("com/mojang/blaze3d/systems/GpuSurface") && call.name.equals("close")) {
                        surface = index; count++;
                    }
                    if (call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("shutdownRenderer")) renderer = index;
                }
                index++;
            }
        }
        if (count != 1 || surface < 0 || renderer <= surface) {
            throw new AssertionError("Native shutdown injection no longer precedes surface/device destruction");
        }
        var renderPass = read("com/mojang/blaze3d/vulkan/VulkanRenderPass");
        int sampledLayoutDeclarations = 0;
        for (var method : renderPass.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.startsWith("org/lwjgl/vulkan/VkDescriptorImageInfo")
                        && call.name.equals("imageLayout")) {
                    if (instruction.getPrevious().getOpcode() != org.objectweb.asm.Opcodes.ICONST_1) {
                        throw new AssertionError("Minecraft's sampled-image descriptor no longer requires GENERAL");
                    }
                    sampledLayoutDeclarations++;
                }
            }
        }
        if (sampledLayoutDeclarations == 0) throw new AssertionError("Missing actual native sampled-image descriptor");
        var bridge = read("icyllis/modernui/mc/NativeVulkanIntegration");
        for (String name : new String[]{"syncImageLayoutFromArc3D", "syncImageLayoutFromVulkan"}) {
            boolean general = false;
            for (var method : bridge.methods) {
                if (!method.name.equals(name)) continue;
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == org.objectweb.asm.Opcodes.ICONST_1) general = true;
                    if (instruction.getOpcode() == org.objectweb.asm.Opcodes.ICONST_5) {
                        throw new AssertionError(name + " must not use the incompatible SHADER_READ_ONLY layout");
                    }
                }
            }
            if (!general) throw new AssertionError(name + " must hand off GENERAL layout");
        }
        boolean shaderTarget = false;
        for (var method : bridge.methods) {
            if (!method.name.equals("configureShaderCaps")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.name.equals("SPIRV_1_5")) shaderTarget = true;
            }
        }
        if (!shaderTarget) throw new AssertionError("Minecraft Vulkan 1.2 requires the Arc3D SPIR-V 1.5 target");
        var instance = read("com/mojang/blaze3d/vulkan/VulkanInstance");
        boolean api12 = false;
        for (var method : instance.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("apiVersion")
                    && ((instruction.getPrevious() instanceof LdcInsnNode constant
                        && Integer.valueOf(4202496).equals(constant.cst))
                        || (instruction.getPrevious() instanceof FieldInsnNode field
                            && field.owner.equals("org/lwjgl/vulkan/VK12") && field.name.equals("VK_API_VERSION_1_2")))) api12 = true;
        }
        if (!api12) throw new AssertionError("Minecraft's actual Vulkan instance API contract changed");
        System.out.println("Native Vulkan queue/shutdown, GENERAL descriptor layout and Vulkan 1.2 / SPIR-V 1.5 contracts verified");
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = NativeVulkanContractCheck.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing " + name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
