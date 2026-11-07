package icyllis.modernui.mc.text;

import icyllis.arc3d.core.PixelUtils;
import icyllis.arc3d.sketch.*;
import icyllis.modernui.graphics.text.EmojiFont;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses real Arc3D strikes/glyph storage, without a graphics device or window. */
public final class EmojiTypefaceCheck {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        checkColorAndCoverageTogether();
        // Exercise the shipping shader generator, rather than assuming texture color-type
        // names also describe the color glyph's raw byte storage contract.
        var fragment = new java.util.Formatter();
        new icyllis.arc3d.granite.geom.RasterTextStep(icyllis.arc3d.engine.Engine.MASK_FORMAT_ARGB)
                .emitFragmentColorCode(fragment, "outputColor");
        check(fragment.toString().contains("texture(u_GlyphAtlas, f_TexCoords).bgra"),
                "shipping color glyph shader swaps raw blue/red atlas bytes");
        var image = new BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 72; y++) for (int x = 0; x < 72; x++) image.setRGB(x, y, 0x804020e0);
        var encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", encoded);
        var emoji = new EmojiFont("test", new IntOpenHashSet(), 72, 56, 4, 64,
                new Object2IntOpenHashMap<>(), List.of("test.png"));
        var loads = new AtomicInteger();
        var typeface = new EmojiTypeface(emoji, 72f / 64, 56f / 64, file -> {
            loads.incrementAndGet();
            return new ByteArrayInputStream(encoded.toByteArray());
        });
        for (boolean linear : new boolean[]{false, true}) {
            for (float size : new float[]{8, 12, 16, 24, 32, 64, 128}) {
                var font = new Font(); font.setTypeface(typeface); font.setSize(size);
                font.setLinearMetrics(linear);
                var desc = StrikeDesc.makeMask(font, new Paint(), Matrix.identity());
                var scaler = typeface.createScalerContext(desc);
                var glyph = scaler.makeGlyph(1);
                float expectedSize = linear ? size * 72 / 64 : (int) (.95 + size * 72 / 64);
                float expectedAscent = linear ? size * 56 / 64 : (int) (.95 + size * 56 / 64);
                check(glyph.getMaskFormat() == Mask.kARGB32_Format && glyph.isColor(), "native color mask");
                check(glyph.getLeft() == 0, "spacing must not be applied twice");
                check(glyph.getTop() == -(int) Math.ceil(expectedAscent), "baseline");
                check(glyph.getWidth() == (int) Math.ceil(expectedSize), "width");
                check(glyph.getHeight() == (int) Math.ceil(expectedSize - expectedAscent) +
                        (int) Math.ceil(expectedAscent), "height size=" + size + " linear=" + linear +
                        " actual=" + glyph.getHeight() + " top=" + glyph.getTop() + " expectedSize=" + expectedSize +
                        " expectedAscent=" + expectedAscent);
                check(glyph.setImage(scaler), "actual native glyph image allocated");
                byte[] bgra = new byte[glyph.getImageSize()];
                PixelUtils.copyImage(glyph.getImageBase(), glyph.getImageAddress(), glyph.getRowBytes(),
                        bgra, 0, glyph.getRowBytes(), glyph.getRowBytes(), glyph.getHeight());
                int center = (glyph.getHeight() / 2 * glyph.getWidth() + glyph.getWidth() / 2) * 4;
                check((bgra[center] & 255) == 112 && (bgra[center + 1] & 255) == 16 &&
                        (bgra[center + 2] & 255) == 32 && (bgra[center + 3] & 255) == 128,
                        "premultiplied BGRA bytes match shipping color atlas contract");
                // RGBA texture upload is a raw copy; the actual emitted .bgra yields R,G,B,A.
                check((bgra[center + 2] & 255) == 32 && (bgra[center + 1] & 255) == 16 &&
                        (bgra[center] & 255) == 112, "shader sampled color preserves red/blue");
            }
        }
        check(loads.get() == 1, "PNG decoded once across strike sizes");
        for (var transform : List.of(new java.awt.geom.AffineTransform(2, .25, -.5, 1.5, 0, 0),
                java.awt.geom.AffineTransform.getRotateInstance(.3),
                java.awt.geom.AffineTransform.getScaleInstance(-1, 1))) {
            var font = new Font(); font.setTypeface(typeface); font.setSize(32); font.setLinearMetrics(true);
            var desc = StrikeDesc.makeMask(font, new Paint(), Matrix.make(transform));
            var scaler = typeface.createScalerContext(desc); var glyph = scaler.makeGlyph(1);
            var actualTransform = new java.awt.geom.AffineTransform(desc.getPostScaleX(), desc.getPostShearY(),
                    desc.getPostShearX(), desc.getPostScaleY(), 0, 0);
            var bounds = actualTransform.createTransformedShape(new java.awt.geom.Rectangle2D.Float(0, -28, 36, 36)).getBounds();
            check(glyph.getLeft() == bounds.x && glyph.getTop() == bounds.y &&
                    glyph.getWidth() == bounds.width && glyph.getHeight() == bounds.height, "full 2D bounds");
            check(glyph.setImage(scaler), "transformed color image");
        }
        var failed = new AtomicInteger();
        var missing = new EmojiTypeface(emoji, 72f / 64, 56f / 64, file -> {
            failed.incrementAndGet(); throw new java.io.IOException("missing");
        });
        var font = new Font(); font.setTypeface(missing); font.setSize(16);
        var scaler = missing.createScalerContext(StrikeDesc.makeMask(font, new Paint(), Matrix.identity()));
        check(scaler.makeGlyph(1).isEmpty() && scaler.makeGlyph(1).isEmpty(), "missing image empty");
        check(failed.get() == 1, "missing image negatively cached");
        check(scaler.makeGlyph(0).isEmpty() && scaler.makeGlyph(1000).isEmpty(), "invalid glyph bounds");
        var oldClosed = new java.util.concurrent.atomic.AtomicBoolean();
        var generationFont = new EmojiFont("generation", new IntOpenHashSet(), 72, 56, 4, 64,
                new Object2IntOpenHashMap<>(), List.of("a.png", "b.png"));
        var old = EmojiTypeface.snapshot(generationFont, 72f / 64, 56f / 64,
                List.of("a.png", "b.png"), file -> {
                    if (oldClosed.get()) throw new java.io.IOException("ZIP closed");
                    return new ByteArrayInputStream(encoded.toByteArray());
                });
        oldClosed.set(true); // Same retained TextView can request a previously unseen glyph after reload.
        font.setTypeface(old);
        var retainedScaler = old.createScalerContext(StrikeDesc.makeMask(font, new Paint(), Matrix.identity()));
        var unseen = retainedScaler.makeGlyph(2);
        check(!unseen.isEmpty() && unseen.setImage(retainedScaler), "unseen old glyph survives closed pack");
        var fresh = EmojiTypeface.snapshot(generationFont, 72f / 64, 56f / 64,
                List.of("a.png", "b.png"), file -> new ByteArrayInputStream(encoded.toByteArray()));
        check(old != fresh, "separate generation strike identity");
        font.setTypeface(typeface); font.setSize(32); font.setLinearMetrics(true); font.setSubpixel(true);
        scaler = typeface.createScalerContext(StrikeDesc.makeMask(font, new Paint(), Matrix.identity()));
        var subpixel = scaler.makeGlyph(Glyph.packGlyphID(1, .25f, Glyph.kSubPixelXMask));
        check(subpixel.getSubX() == .25f && subpixel.getLeft() == 0 && subpixel.getWidth() == 37,
                "quarter-pixel origin is represented in raster bounds");
        check(subpixel.setImage(scaler), "subpixel color raster");
        var manyFiles = new java.util.ArrayList<String>();
        for (int i = 0; i < 130; i++) manyFiles.add(i + ".png");
        var manyFont = new EmojiFont("many", new IntOpenHashSet(), 72, 56, 4, 64,
                new Object2IntOpenHashMap<>(), manyFiles);
        var cacheLoads = new AtomicInteger();
        var bounded = new EmojiTypeface(manyFont, 72f / 64, 56f / 64, file -> {
            cacheLoads.incrementAndGet(); return new ByteArrayInputStream(encoded.toByteArray());
        });
        font.setTypeface(bounded);
        scaler = bounded.createScalerContext(StrikeDesc.makeMask(font, new Paint(), Matrix.identity()));
        for (int i = 1; i <= 130; i++) check(!scaler.makeGlyph(i).isEmpty(), "load distinct CPU sprite");
        check(!scaler.makeGlyph(1).isEmpty() && cacheLoads.get() == 131, "bounded decode cache evicts old sprite");
        var wrongSize = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "PNG", wrongSize);
        var malformed = new EmojiTypeface(emoji, 72f / 64, 56f / 64,
                file -> new ByteArrayInputStream(wrongSize.toByteArray()));
        font.setTypeface(malformed);
        scaler = malformed.createScalerContext(StrikeDesc.makeMask(font, new Paint(), Matrix.identity()));
        check(scaler.makeGlyph(1).isEmpty(), "wrong dimensions do not allocate a glyph image");
        System.out.println("EmojiTypeface: " + assertions + " actual Core/Arc3D CPU assertions passed");
    }

    /** Apply the production overwrite to a real shipping class, preserving the rest of the step. */
    private static void checkColorAndCoverageTogether() throws Exception {
        String targetName = "icyllis.arc3d.granite.geom.RasterTextStep";
        String patchName = "icyllis.modernui.mc.mixin.MixinRasterTextStep";
        var target = new org.objectweb.asm.tree.ClassNode();
        var patch = new org.objectweb.asm.tree.ClassNode();
        var loader = EmojiTypefaceCheck.class.getClassLoader();
        try (var stream = loader.getResourceAsStream(targetName.replace('.', '/') + ".class")) {
            new org.objectweb.asm.ClassReader(stream).accept(target, 0);
        }
        try (var stream = loader.getResourceAsStream(patchName.replace('.', '/') + ".class")) {
            new org.objectweb.asm.ClassReader(stream).accept(patch, 0);
        }
        var replacement = patch.methods.stream().filter(m -> m.name.equals("emitFragmentCoverageCode"))
                .findFirst().orElseThrow();
        replacement.visibleAnnotations = null; replacement.invisibleAnnotations = null;
        for (var instruction : replacement.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field && field.owner.equals(patch.name)) {
                field.owner = target.name;
            }
            if (instruction instanceof org.objectweb.asm.tree.FrameNode frame && frame.local != null) {
                frame.local.replaceAll(value -> patch.name.equals(value) ? target.name : value);
            }
        }
        target.methods.removeIf(method -> method.name.equals(replacement.name) && method.desc.equals(replacement.desc));
        target.methods.add(replacement);
        var writer = new org.objectweb.asm.ClassWriter(0); target.accept(writer);
        byte[] fixed = writer.toByteArray();
        var patchedLoader = new ClassLoader(loader) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(targetName)) return super.loadClass(name, resolve);
                var type = findLoadedClass(name);
                if (type == null) type = defineClass(name, fixed, 0, fixed.length);
                if (resolve) resolveClass(type);
                return type;
            }
        };
        var type = patchedLoader.loadClass(targetName);
        var colorStep = type.getConstructor(int.class).newInstance(icyllis.arc3d.engine.Engine.MASK_FORMAT_ARGB);
        check(!(boolean) type.getMethod("emitsCoverage").invoke(colorStep), "color step declares no coverage output");
        var fragment = new java.util.Formatter();
        type.getMethod("emitFragmentColorCode", java.util.Formatter.class, String.class)
                .invoke(colorStep, fragment, "primitiveColor");
        type.getMethod("emitFragmentCoverageCode", java.util.Formatter.class, String.class)
                .invoke(colorStep, fragment, null);
        check(fragment.toString().equals("primitiveColor = texture(u_GlyphAtlas, f_TexCoords).bgra;\n"),
                "combined color/coverage generation emits no null coverage assignment");
        var compiler = new icyllis.arc3d.compiler.ShaderCompiler();
        var module = icyllis.arc3d.compiler.ModuleLoader.getInstance().loadGLSLFragModule(compiler);
        var shader = "#version 450 core\nlayout(binding=0) uniform sampler2D u_GlyphAtlas;\n" +
                "layout(location=0) in float2 f_TexCoords;\nlayout(location=0) out float4 FragColor0;\n" +
                "void main() { float4 primitiveColor;\n" + fragment + "FragColor0=primitiveColor; }\n";
        var unit = compiler.parse(shader, icyllis.arc3d.compiler.ShaderKind.FRAGMENT,
                new icyllis.arc3d.compiler.CompileOptions(), module);
        check(unit != null && compiler.errorCount() == 0,
                "combined native color/coverage fragment compiles: " + compiler.getErrorMessage());
        var alphaStep = type.getConstructor(int.class).newInstance(icyllis.arc3d.engine.Engine.MASK_FORMAT_A8);
        check((boolean) type.getMethod("emitsCoverage").invoke(alphaStep), "outline step retains coverage output");
        var coverage = new java.util.Formatter();
        type.getMethod("emitFragmentCoverageCode", java.util.Formatter.class, String.class)
                .invoke(alphaStep, coverage, "outputCoverage");
        check(coverage.toString().equals("outputCoverage = texture(u_GlyphAtlas, f_TexCoords).rrrr;\n"),
                "outline coverage shader unchanged");
    }
}
