package icyllis.modernui.mc.text;

import icyllis.arc3d.core.PixelUtils;
import icyllis.arc3d.sketch.Glyph;
import icyllis.arc3d.sketch.Mask;
import icyllis.arc3d.sketch.PathBuilder;
import icyllis.arc3d.sketch.ScalerContext;
import icyllis.arc3d.sketch.StrikeDesc;
import icyllis.arc3d.sketch.Typeface;
import icyllis.modernui.graphics.text.EmojiFont;
import icyllis.modernui.mc.ModernUIMod;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rasterizes the resource-pack emoji font through Arc3D's existing color glyph atlas. */
public final class EmojiTypeface extends Typeface {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(EmojiTypeface.class);
    private static final int MAX_IMAGES = 128;
    private static final int MAX_PNG_BYTES = 1024 * 1024;
    private static final int MAX_FONT_PNG_BYTES = 32 * 1024 * 1024;

    /** Implemented by EmojiFont; each font generation owns a distinct native typeface. */
    public interface Provider {
        void modernui$bindEmojiResources(ResourceManager resources);
    }

    @FunctionalInterface
    public interface ImageSource {
        InputStream open(String file) throws IOException;
    }

    private final EmojiFont font;
    private final float baseSize;
    private final float baseAscent;
    private final ImageSource source;
    // CPU images only. StrikeCache owns rasterized glyphs and the renderer owns its GPU atlas.
    private final LinkedHashMap<String, BufferedImage> images = new LinkedHashMap<>(16, .75f, true);

    public EmojiTypeface(EmojiFont font, float baseSize, float baseAscent, ImageSource source) {
        this.font = font;
        this.baseSize = baseSize;
        this.baseAscent = baseAscent;
        this.source = source;
    }

    /** Keep compressed bytes: a Resource's supplier can refer to a ZIP closed by the next reload. */
    public static EmojiTypeface fromResources(EmojiFont font, float baseSize, float baseAscent,
                                             List<String> files, ResourceManager resources) {
        return snapshot(font, baseSize, baseAscent, files, file -> {
            var resource = resources.getResource(ModernUIMod.location("emoji/" + file));
            if (resource.isEmpty()) throw new IOException("Missing emoji " + file);
            return resource.get().open();
        });
    }

    static EmojiTypeface snapshot(EmojiFont font, float baseSize, float baseAscent,
                                  List<String> files, ImageSource source) {
        Map<String, byte[]> snapshot = new HashMap<>();
        int remaining = MAX_FONT_PNG_BYTES;
        for (String file : files) {
            if (snapshot.containsKey(file)) continue;
            try (var stream = source.open(file)) {
                byte[] bytes = stream.readNBytes(Math.min(MAX_PNG_BYTES, remaining) + 1);
                if (bytes.length > MAX_PNG_BYTES || bytes.length > remaining) {
                    LOGGER.warn("UI emoji snapshot exceeded its PNG or font byte limit at {}", file);
                    continue;
                }
                snapshot.put(file, bytes);
                remaining -= bytes.length;
            } catch (IOException | RuntimeException failure) {
                LOGGER.warn("Failed to snapshot UI emoji {}", file, failure);
            }
        }
        return new EmojiTypeface(font, baseSize, baseAscent, file -> {
            var bytes = snapshot.get(file);
            if (bytes == null) throw new IOException("Missing emoji " + file);
            return new java.io.ByteArrayInputStream(bytes);
        });
    }

    private synchronized BufferedImage image(int glyph) {
        if (glyph <= 0) return null;
        final String file;
        try {
            file = font.getFileName(glyph);
        } catch (IndexOutOfBoundsException invalidGlyph) {
            return null;
        }
        if (images.containsKey(file)) return images.get(file);
        BufferedImage image = null;
        try (InputStream stream = source.open(file)) {
            byte[] bytes = stream.readNBytes(MAX_PNG_BYTES + 1);
            if (bytes.length > MAX_PNG_BYTES) throw new IOException("Emoji PNG exceeds 1 MiB");
            // Inspect dimensions before decoding; the resource font uses 72px square sprites.
            try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(
                    new java.io.ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        if (reader.getWidth(0) == GlyphManager.EMOJI_SIZE &&
                                reader.getHeight(0) == GlyphManager.EMOJI_SIZE) {
                            image = reader.read(0);
                        } else {
                            throw new IOException("Expected a 72px square emoji PNG");
                        }
                    } finally {
                        reader.dispose();
                    }
                }
                if (image == null) throw new IOException("No image decoder accepted emoji PNG");
            }
        } catch (IOException | RuntimeException invalidResource) {
            // Negative entries are cached too: a missing resource must not trigger IO every frame.
            LOGGER.warn("Failed to rasterize UI emoji {}", file, invalidResource);
        }
        images.put(file, image);
        if (images.size() > MAX_IMAGES) images.remove(images.keySet().iterator().next());
        return image;
    }

    @Override
    protected ScalerContext onCreateScalerContext(StrikeDesc desc) {
        return new EmojiScaler(this, desc);
    }

    @Override
    protected void onFilterStrikeDesc(StrikeDesc.Lookup desc) {
        // Color bitmaps are not outline masks. Preserve the renderer's transform and positioning.
    }

    private static final class EmojiScaler extends ScalerContext {
        private final EmojiTypeface owner;
        private final float size;
        private final float ascent;

        EmojiScaler(EmojiTypeface owner, StrikeDesc desc) {
            super(owner, desc);
            this.owner = owner;
            float size = owner.baseSize * mDesc.getTextSize();
            float ascent = owner.baseAscent * mDesc.getTextSize();
            if (!isLinearMetrics()) {
                size = (int) (0.95d + size);
                ascent = (int) (0.95d + ascent);
            }
            this.size = size;
            this.ascent = ascent;
        }

        private AffineTransform transform(Glyph glyph, BufferedImage image) {
            var transform = new AffineTransform(mDesc.getPostScaleX(), mDesc.getPostShearY(),
                    mDesc.getPostShearX(), mDesc.getPostScaleY(),
                    isSubpixel() ? glyph.getSubX() : 0, 0);
            // The layout already included spacing; the glyph origin is the sprite's left edge.
            transform.translate(0, -ascent);
            transform.scale((double) size / image.getWidth(), (double) size / image.getHeight());
            return transform;
        }

        @Override
        protected GlyphMetrics generateMetrics(Glyph glyph) {
            var metrics = new GlyphMetrics(Mask.kARGB32_Format);
            metrics.mNeverRequestPath = true;
            var image = owner.image(glyph.getGlyphID());
            if (image != null && size > 0) {
                var matrix = new AffineTransform(mDesc.getPostScaleX(), mDesc.getPostShearY(),
                        mDesc.getPostShearX(), mDesc.getPostScaleY(),
                        isSubpixel() ? glyph.getSubX() : 0, 0);
                var bounds = matrix.createTransformedShape(
                        new Rectangle2D.Float(0, -ascent, size, size)).getBounds2D();
                metrics.mLeft = (float) bounds.getMinX();
                metrics.mTop = (float) bounds.getMinY();
                metrics.mRight = (float) bounds.getMaxX();
                metrics.mBottom = (float) bounds.getMaxY();
            }
            return metrics;
        }

        @Override
        protected void generateImage(Glyph glyph, Object base, long address) {
            var image = owner.image(glyph.getGlyphID());
            if (image == null) return;
            var raster = new BufferedImage(glyph.getWidth(), glyph.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            var graphics = raster.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.translate(-glyph.getLeft(), -glyph.getTop());
                graphics.drawImage(image, transform(glyph, image), null);
            } finally {
                graphics.dispose();
            }
            int[] pixels = ((DataBufferInt) raster.getRaster().getDataBuffer()).getData();
            byte[] bgra = new byte[pixels.length * 4];
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i]; // TYPE_INT_ARGB_PRE stores premultiplied channels.
                bgra[i * 4] = (byte) argb;
                bgra[i * 4 + 1] = (byte) (argb >>> 8);
                bgra[i * 4 + 2] = (byte) (argb >>> 16);
                bgra[i * 4 + 3] = (byte) (argb >>> 24);
            }
            // The color atlas copies ARGB32 storage verbatim into an RGBA texture;
            // RasterTextStep's .bgra swizzle expects premultiplied BGRA bytes (LE ARGB ints).
            PixelUtils.copyImage(bgra, 0, (long) glyph.getWidth() * 4, base, address,
                    glyph.getRowBytes(), (long) glyph.getWidth() * 4, glyph.getHeight());
        }

        @Override
        protected boolean generatePath(Glyph glyph, PathBuilder path) {
            return false; // A PNG has no outline; keep it in the color atlas.
        }
    }
}
