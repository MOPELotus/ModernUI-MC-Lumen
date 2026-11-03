package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

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
        System.out.println("Native Vulkan queue API and real Minecraft shutdown order verified");
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
