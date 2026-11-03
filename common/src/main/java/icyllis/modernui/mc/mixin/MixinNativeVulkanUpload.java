package icyllis.modernui.mc.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanTransientMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

/** Align native texture uploads to their actual texel block, including RGB formats. */
@Mixin(value = VulkanCommandEncoder.class, remap = false)
public abstract class MixinNativeVulkanUpload {
    @Redirect(
            method = "writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanTransientMemory;uploadStaging(Ljava/nio/ByteBuffer;JI)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
            require = 1)
    private GpuBufferSlice modernui$alignTextureUpload(
            VulkanTransientMemory memory, ByteBuffer source, long originalAlignment, int usage,
            GpuTexture destination, ByteBuffer originalSource,
            int mipLevel, int depthOrLayer, int destX, int destY, int width, int height) {
        // byteAlignment() is only the component alignment (1 for RGBA8).
        // Do not round to a power of two: RGB texel blocks can be 3, 6 or 12 bytes.
        return memory.uploadStaging(source, destination.getFormat().blockSize(), usage);
    }
}

