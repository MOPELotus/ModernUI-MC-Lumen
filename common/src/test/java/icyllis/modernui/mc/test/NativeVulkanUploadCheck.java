package icyllis.modernui.mc.test;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.util.TransientBlockAllocator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/** Tests the actual native allocator and compiled redirect binding without a GPU. */
public final class NativeVulkanUploadCheck {
    private static final String BACKEND = "com/mojang/renderpearl/backend/vulkan/";
    private static final String TEXTURE = "com/mojang/renderpearl/api/textures/GpuTexture";
    private static final String FORMAT = "com/mojang/renderpearl/api/GpuFormat";
    private static final String SLICE = "com/mojang/renderpearl/api/buffers/GpuBufferSlice";
    private static final String UPLOAD = "(Ljava/nio/ByteBuffer;JI)L" + SLICE + ";";
    private static final String WRITE = "(L" + TEXTURE + ";Ljava/nio/ByteBuffer;IIIIII)V";

    private record TestBlock() implements TransientBlockAllocator.Allocator.Block {
        @Override
        public boolean suboptimal() {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        checkBinding();
        // These are the real unaligned offsets from the FontAtlas2 validation failures.
        for (long prime : new long[]{6023, 26759, 31943, 52679}) {
            long oldOffset = allocateAfter(prime, 64, 1);
            long corrected = allocateAfter(prime, 64, GpuFormat.RGBA8_UNORM.blockSize());
            if (oldOffset != prime || oldOffset % 4 == 0) {
                throw new AssertionError("Old allocation no longer reproduces the alignment regression");
            }
            if (corrected != prime + 1 || corrected % 4 != 0) {
                throw new AssertionError("RGBA8 staging offset was not aligned: " + corrected);
            }
        }
        int formats = 0;
        boolean rgb3 = false, rgb6 = false, rgb12 = false;
        for (GpuFormat format : GpuFormat.values()) {
            if (!format.hasColorAspect()) continue;
            int block = format.blockSize();
            if (block <= 0) throw new AssertionError("Invalid texel block for " + format);
            for (long prime : new long[]{1, 7, 6023, 26759}) {
                long actual = allocateAfter(prime, block * 7L, block);
                long expected = ((prime + block - 1) / block) * block;
                if (actual != expected || actual % block != 0) {
                    throw new AssertionError(format + ": expected " + expected + ", got " + actual);
                }
            }
            rgb3 |= block == 3;
            rgb6 |= block == 6;
            rgb12 |= block == 12;
            formats++;
        }
        if (!rgb3 || !rgb6 || !rgb12) throw new AssertionError("Missing non-power-of-two RGB coverage");
        System.out.println("Native texture upload binding and real allocator checks passed for " + formats + " color formats");
    }

    private static long allocateAfter(long prime, long size, long alignment) {
        var blocks = TransientBlockAllocator.Allocator.<TestBlock>create(
                ignored -> new TestBlock(), ignored -> {});
        try (var allocator = new TransientBlockAllocator<>(131072, 16, blocks)) {
            allocator.allocate(prime, 1, prime, 1);
            return allocator.allocate(size, alignment, size, 1).offset();
        }
    }

    private static void checkBinding() throws Exception {
        var nativeEncoder = read(BACKEND + "VulkanCommandEncoder");
        var nativeWrite = method(nativeEncoder, "writeToTexture", WRITE);
        int calls = 0;
        for (var instruction : nativeWrite.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(BACKEND + "VulkanTransientMemory")
                    && call.name.equals("uploadStaging") && call.desc.equals(UPLOAD)) {
                calls++;
                var usage = previousCode(call);
                if (!(usage instanceof IntInsnNode number) || number.operand != 16
                        || previousCode(usage).getOpcode() != Opcodes.LCONST_1) {
                    throw new AssertionError("Native staging arguments changed; re-audit the redirect");
                }
            }
        }
        if (calls != 1) throw new AssertionError("Expected one native texture staging upload, got " + calls);

        var mixin = read("icyllis/modernui/mc/mixin/MixinNativeVulkanUpload");
        var mixinAnnotation = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixinAnnotation == null) mixinAnnotation = annotation(mixin.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (!List.of(Type.getObjectType(BACKEND + "VulkanCommandEncoder")).equals(value(mixinAnnotation, "value"))
                || !Boolean.FALSE.equals(value(mixinAnnotation, "remap"))) {
            throw new AssertionError("Upload mixin must target the actual native encoder without remapping");
        }
        String descriptor = "(L" + BACKEND + "VulkanTransientMemory;Ljava/nio/ByteBuffer;JI"
                + "L" + TEXTURE + ";Ljava/nio/ByteBuffer;IIIIII)L" + SLICE + ";";
        var handler = method(mixin, "modernui$alignTextureUpload", descriptor);
        var redirect = annotation(handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        if (redirect == null) redirect = annotation(handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        if (!List.of("writeToTexture" + WRITE).equals(value(redirect, "method"))
                || !Integer.valueOf(1).equals(value(redirect, "require"))) {
            throw new AssertionError("Redirect must bind the full native texture method exactly once");
        }
        var at = (AnnotationNode) value(redirect, "at");
        if (!"INVOKE".equals(value(at, "value"))
                || !("L" + BACKEND + "VulkanTransientMemory;uploadStaging" + UPLOAD).equals(value(at, "target"))) {
            throw new AssertionError("Redirect must bind the actual staging call");
        }
        // Verify original receiver/source/usage slots and the captured destination slot.
        // This also catches accidentally using byteAlignment, another texture, or a coordinate.
        List<AbstractInsnNode> code = new ArrayList<>();
        for (var instruction : handler.instructions) {
            if (instruction.getOpcode() >= 0) code.add(instruction);
        }
        if (code.size() != 9) throw new AssertionError("Re-audit changed upload handler bytecode");
        variable(code.get(0), Opcodes.ALOAD, 1);
        variable(code.get(1), Opcodes.ALOAD, 2);
        variable(code.get(2), Opcodes.ALOAD, 6);
        call(code.get(3), TEXTURE, "getFormat", "()L" + FORMAT + ";");
        call(code.get(4), FORMAT, "blockSize", "()I");
        if (code.get(5).getOpcode() != Opcodes.I2L) throw new AssertionError("Alignment must widen to long");
        variable(code.get(6), Opcodes.ILOAD, 5);
        call(code.get(7), BACKEND + "VulkanTransientMemory", "uploadStaging", UPLOAD);
        if (code.get(8).getOpcode() != Opcodes.ARETURN) throw new AssertionError("Staging slice must be returned");
    }

    private static void variable(AbstractInsnNode node, int opcode, int slot) {
        if (!(node instanceof VarInsnNode variable) || variable.getOpcode() != opcode || variable.var != slot) {
            throw new AssertionError("Wrong upload argument slot; expected " + slot);
        }
    }

    private static void call(AbstractInsnNode node, String owner, String name, String descriptor) {
        if (!(node instanceof MethodInsnNode call) || !call.owner.equals(owner)
                || !call.name.equals(name) || !call.desc.equals(descriptor)) {
            throw new AssertionError("Wrong upload call; expected " + owner + "." + name + descriptor);
        }
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        if (node == null) throw new AssertionError("Missing native staging argument");
        return node;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations != null) for (var annotation : annotations) {
            if (annotation.desc.equals(descriptor)) return annotation;
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation == null || annotation.values == null) throw new AssertionError("Missing annotation " + name);
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (var method : owner.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        throw new AssertionError("Missing method " + owner.name + "." + name + descriptor);
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = NativeVulkanUploadCheck.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing " + name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}

