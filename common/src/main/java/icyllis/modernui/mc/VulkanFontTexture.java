package icyllis.modernui.mc;

import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.Swizzle;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** VulkanMod-owned font atlas, shared by the Fabric and NeoForge GL compatibility facades. */
public final class VulkanFontTexture implements AutoCloseable {
    private final Object image;
    private final int bytesPerPixel;
    private int textureId;
    private final int width;
    private final int height;
    private final int mipLevels;
    private boolean dirty;

    private VulkanFontTexture(int width, int height, int maskFormat, boolean mipmaps, boolean linear) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid atlas dimensions");
        this.width = width;
        this.height = height;
        bytesPerPixel = maskFormat == Engine.MASK_FORMAT_A8 ? 1 : 4;
        mipLevels = mipmaps ? 32 - Integer.numberOfLeadingZeros(Math.max(width, height)) : 1;
        Class<?> owner = VulkanModIntegration.imageClass();
        Object builder = VulkanModIntegration.call(owner, null, "builder", new Class<?>[]{int.class, int.class}, width, height);
        Class<?> type = builder.getClass();
        VulkanModIntegration.call(type, builder, "setFormat", new Class<?>[]{int.class},
                bytesPerPixel == 1 ? VK_FORMAT_R8_UNORM : VK_FORMAT_R8G8B8A8_UNORM);
        VulkanModIntegration.call(type, builder, "setMipLevels", new Class<?>[]{int.class}, mipLevels);
        VulkanModIntegration.call(type, builder, "setUsage", new Class<?>[]{int.class},
                VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        VulkanModIntegration.call(type, builder, "setLinearFiltering", new Class<?>[]{boolean.class}, linear);
        VulkanModIntegration.call(type, builder, "setClamp", new Class<?>[]{boolean.class}, true);
        image = VulkanModIntegration.call(type, builder, "createVulkanImage", new Class<?>[0]);
        if (bytesPerPixel == 1) {
            // Text shaders multiply RGBA: alpha masks must sample as (1,1,1,r).
            VulkanModIntegration.replaceMainImageView(image, Swizzle.make("111r"));
        }
        textureId = (int) VulkanModIntegration.textureCall("genTextureId", new Class<?>[0]);
        VulkanModIntegration.textureCall("bindIdToImage", new Class<?>[]{int.class, owner}, textureId, image);
        // Fresh atlas borders must be transparent, including regions not yet occupied by glyphs.
        ByteBuffer zero = MemoryUtil.memCalloc(Math.multiplyExact(Math.multiplyExact(width, height), bytesPerPixel));
        try { upload(0, 0, width, height, MemoryUtil.memAddress(zero)); }
        finally { MemoryUtil.memFree(zero); }
    }

    public static VulkanFontTexture create(int width, int height, int maskFormat, boolean mipmaps, boolean linear) {
        return new VulkanFontTexture(width, height, maskFormat, mipmaps, linear);
    }

    public int getTextureId() { return textureId; }

    /** Input rows are tightly packed in A8 or RGBA byte order; VulkanMod copies them before returning. */
    public void upload(int x, int y, int width, int height, long pixels) {
        if (textureId == 0) throw new IllegalStateException("Closed font atlas");
        if (x < 0 || y < 0 || width < 0 || height < 0 || x + width > this.width || y + height > this.height)
            throw new IllegalArgumentException("Glyph upload outside atlas");
        if (width == 0 || height == 0) return;
        ByteBuffer buffer = MemoryUtil.memByteBuffer(pixels,
                Math.multiplyExact(Math.multiplyExact(width, height), bytesPerPixel));
        dirty = true;
        VulkanModIntegration.imageCall(image, "uploadSubTextureAsync", new Class<?>[]{
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class},
                0, width, height, x, y, 0, 0, width, buffer);
    }

    public void generateMipmaps() {
        if (textureId == 0) throw new IllegalStateException("Closed font atlas");
        if (!dirty) return;
        VulkanModIntegration.finishTextureUploads();
        Object queue = VulkanModIntegration.graphicsQueue();
        Object command = VulkanModIntegration.call(queue.getClass(), queue, "beginCommands", new Class<?>[0]);
        VkCommandBuffer cb = (VkCommandBuffer) VulkanModIntegration.call(command.getClass(), command, "getHandle", new Class<?>[0]);
        long handle = (long) VulkanModIntegration.imageCall(image, "getId", new Class<?>[0]);
        try (var stack = MemoryStack.stackPush()) {
            VulkanModIntegration.imageCall(image, "transitionImageLayout",
                    new Class<?>[]{MemoryStack.class, VkCommandBuffer.class, int.class}, stack, cb, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            // Fabric 0.6.7 ImageUtil.generateMipmaps writes COLOR_ATTACHMENT/GENERAL
            // layouts into transfer barriers. Emit the actual transfer layouts here.
            for (int level = 1; level < mipLevels; level++) {
                barrier(stack, cb, handle, level - 1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT,
                        VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
                var blit = VkImageBlit.calloc(1, stack);
                blit.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level - 1).baseArrayLayer(0).layerCount(1);
                blit.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level).baseArrayLayer(0).layerCount(1);
                blit.srcOffsets(0).set(0, 0, 0);
                blit.srcOffsets(1).set(Math.max(1, width >> (level - 1)), Math.max(1, height >> (level - 1)), 1);
                blit.dstOffsets(0).set(0, 0, 0);
                blit.dstOffsets(1).set(Math.max(1, width >> level), Math.max(1, height >> level), 1);
                vkCmdBlitImage(cb, handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR);
                barrier(stack, cb, handle, level - 1, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_TRANSFER_READ_BIT,
                        VK_ACCESS_SHADER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
            }
            barrier(stack, cb, handle, mipLevels - 1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        }
        VulkanModIntegration.imageCall(image, "setCurrentLayout", new Class<?>[]{int.class}, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        long fence = (long) VulkanModIntegration.call(queue.getClass(), queue, "submitCommands",
                new Class<?>[]{command.getClass()}, command);
        VKUtilCheck(vkWaitForFences(VulkanModIntegration.device(), fence, true, -1L));
        dirty = false;
    }

    private static void VKUtilCheck(int result) {
        icyllis.arc3d.vulkan.VKUtil._CHECK_(result);
    }

    private static void barrier(MemoryStack stack, VkCommandBuffer cb, long image, int level,
                                int oldLayout, int newLayout, int srcAccess, int dstAccess,
                                int srcStage, int dstStage) {
        var barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default()
                .srcAccessMask(srcAccess).dstAccessMask(dstAccess)
                .oldLayout(oldLayout).newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image);
        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(level).levelCount(1).baseArrayLayer(0).layerCount(1);
        vkCmdPipelineBarrier(cb, srcStage, dstStage, 0, null, null, barrier);
    }

    /** Override only the sampler captured by the current draw, retaining the normal atlas policy. */
    public static long pushLinearSampler(int textureId) {
        Object image = VulkanModIntegration.getImage(textureId);
        long previous = VulkanModIntegration.sampler(image);
        Class<?> owner = VulkanModIntegration.imageClass();
        int levels = (int) VulkanModIntegration.get(VulkanModIntegration.field(owner, "mipLevels"), image);
        if (VulkanModIntegration.textureFacadeClass().getSimpleName().equals("VkGlTexture")) {
            Class<?> samplers = VulkanModIntegration.type("net.vulkanmod.vulkan.texture.SamplerManager");
            long linear = (long) VulkanModIntegration.call(samplers, null, "getSampler",
                    new Class<?>[]{boolean.class, boolean.class, int.class}, true, true, levels);
            VulkanModIntegration.sampler(image, linear);
        } else {
            VulkanModIntegration.imageCall(image, "updateTextureSampler",
                    new Class<?>[]{boolean.class, boolean.class, boolean.class}, true, true, levels > 1);
        }
        return previous;
    }

    public static void popSampler(int textureId, long previousSampler) {
        VulkanModIntegration.sampler(VulkanModIntegration.getImage(textureId), previousSampler);
    }

    @Override public void close() {
        if (textureId != 0) {
            // Native VkImage and all views are owned by VulkanMod, which retires them after the frame fence.
            VulkanModIntegration.textureCall("glDeleteTextures", new Class<?>[]{int.class}, textureId);
            textureId = 0;
        }
    }
}
