package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;

/** Checks the compiled GL/Vulkan ownership and lazy-init paths without starting a GPU. */
public final class EffectTextureOwnershipCheck {
    private static final String OWNER = "icyllis/modernui/mc/text/EffectRenderType";
    private static final String VK = "icyllis/modernui/mc/VulkanFontTexture";
    private static int checks;

    public static void main(String[] args) throws Exception {
        var node = new ClassNode();
        if (args.length == 1) {
            new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node, 0);
        } else {
            try (var in = EffectTextureOwnershipCheck.class.getResourceAsStream("/" + OWNER + ".class")) {
                if (in == null) throw new AssertionError("Missing EffectRenderType");
                new ClassReader(in).accept(node, 0);
            }
        }
        int entrances = 0, setupCalls = 0;
        for (var method : node.methods) {
            if (method.name.equals("getRenderType")) {
                require(calls(method, OWNER, "ensureWhiteTexture") == 1, "both public modes initialize their owner");
                entrances++;
            }
            if (method.name.startsWith("lambda$")) setupCalls += calls(method, OWNER, "getWhiteTextureId");
        }
        require(entrances == 2, "both getRenderType overloads checked");
        require(setupCalls == 3, "all three setup callbacks use the backend-neutral texture ID");
        var ensure = method(node, "ensureWhiteTexture");
        require(fields(ensure, "WHITE", Opcodes.GETSTATIC) == 1 &&
                fields(ensure, "WHITE_VULKAN", Opcodes.GETSTATIC) == 1,
                "lazy initialization recognizes both existing owners");
        require(calls(ensure, OWNER, "makeWhiteTexture") == 1, "one allocation site behind owner guard");
        var id = method(node, "getWhiteTextureId");
        require(calls(id, OWNER, "ensureWhiteTexture") == 1, "retained RenderTypes recover after clear");
        require(calls(id, VK, "getTextureId") == 1 &&
                calls(id, "icyllis/arc3d/opengl/GLTexture", "getHandle") == 1,
                "each backend supplies its own texture ID");
        var clear = method(node, "clear");
        require(calls(clear, "icyllis/arc3d/core/RefCnt", "move") == 1, "GL owner retains reference-counted release");
        require(calls(clear, VK, "close") == 1 && fields(clear, "WHITE_VULKAN", Opcodes.PUTSTATIC) == 1,
                "Vulkan owner closes and is removed on clear");
        var create = method(node, "makeWhiteTexture");
        require(calls(create, VK, "create") == 1 && calls(create, VK, "upload") == 1,
                "Vulkan white image is allocated and uploaded");
        require(calls(create, VK, "generateMipmaps") == 1, "non-mipmapped white image becomes sampleable");
        require(calls(create, VK, "close") == 1 && !create.tryCatchBlocks.isEmpty(),
                "failed Vulkan creation releases its local owner");
        int uploaded = -1, ready = -1, published = -1, whiteFills = 0;
        for (int i = 0; i < create.instructions.size(); i++) {
            var instruction = create.instructions.get(i);
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(VK) && call.name.equals("upload")) uploaded = i;
                if (call.owner.equals(VK) && call.name.equals("generateMipmaps")) ready = i;
                if (call.owner.equals("org/lwjgl/system/MemoryUtil") && call.name.equals("memSet")) whiteFills++;
            }
            if (instruction instanceof FieldInsnNode field && field.name.equals("WHITE_VULKAN") &&
                    field.getOpcode() == Opcodes.PUTSTATIC) published = i;
        }
        require(uploaded < ready && ready < published, "owner is published only after upload and layout transition");
        require(whiteFills == 2, "both GL and Vulkan initialize white RGBA pixels");
        System.out.println("Effect texture ownership: " + checks + " compiled-path checks passed");
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static int calls(MethodNode method, String owner, String name) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        }
        return count;
    }
    private static int fields(MethodNode method, String name, int opcode) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(OWNER) &&
                    field.name.equals(name) && field.getOpcode() == opcode) count++;
        }
        return count;
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
