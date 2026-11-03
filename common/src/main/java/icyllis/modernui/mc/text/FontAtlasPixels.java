package icyllis.modernui.mc.text;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** CPU copy of a Vulkan font atlas, used to preserve glyphs when its texture grows. */
public final class FontAtlasPixels implements AutoCloseable {
    private final int bytesPerPixel;
    private ByteBuffer pixels;
    private int width;
    private int height;

    public FontAtlasPixels(int bytesPerPixel) {
        if (bytesPerPixel != 1 && bytesPerPixel != 4) {
            throw new IllegalArgumentException("Expected A8 or RGBA8 pixels");
        }
        this.bytesPerPixel = bytesPerPixel;
    }

    public void resize(int width, int height) {
        if (width < this.width || height < this.height || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Font atlas may only grow");
        }
        int size = Math.multiplyExact(Math.multiplyExact(width, height), bytesPerPixel);
        ByteBuffer next = MemoryUtil.memCalloc(size);
        if (pixels != null) {
            long src = MemoryUtil.memAddress(pixels);
            long dst = MemoryUtil.memAddress(next);
            for (int row = 0; row < this.height; row++) {
                MemoryUtil.memCopy(src + (long) row * this.width * bytesPerPixel,
                        dst + (long) row * width * bytesPerPixel, (long) this.width * bytesPerPixel);
            }
            MemoryUtil.memFree(pixels);
        }
        pixels = next;
        this.width = width;
        this.height = height;
    }

    public void write(int x, int y, int width, int height, long source) {
        if (pixels == null || x < 0 || y < 0 || width < 0 || height < 0 ||
                (long) x + width > this.width || (long) y + height > this.height) {
            throw new IllegalArgumentException("Glyph is outside the font atlas");
        }
        long rowBytes = (long) width * bytesPerPixel;
        long dst = address();
        for (int row = 0; row < height; row++) {
            MemoryUtil.memCopy(source + row * rowBytes,
                    dst + ((long) (y + row) * this.width + x) * bytesPerPixel, rowBytes);
        }
    }

    public long address() {
        return MemoryUtil.memAddress(pixels);
    }

    public long size() {
        return pixels != null ? pixels.capacity() : 0;
    }

    @Override
    public void close() {
        MemoryUtil.memFree(pixels);
        pixels = null;
        width = height = 0;
    }
}
