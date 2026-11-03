package icyllis.modernui.mc.test;

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.SamplerDesc;
import icyllis.arc3d.granite.SubRunContainer;
import icyllis.arc3d.granite.TextBlobCache;
import icyllis.arc3d.sketch.Font;
import icyllis.arc3d.sketch.GlyphRun;
import icyllis.arc3d.sketch.GlyphRunList;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.StrikeDesc;
import icyllis.arc3d.sketch.j2d.Typeface_JDK;
import icyllis.modernui.mc.CanvasTextOrigin;
import icyllis.modernui.mc.text.TextSampling;
import icyllis.modernui.mc.text.FontAtlasPixels;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.nio.file.Files;
import java.nio.file.Path;

/** CPU checks of legacy glyph placement/filtering and the actual Arc3D cache and injection site. */
public final class TextMarqueeCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        checkAtlasPixels();
        checkPlacement();
        checkSampling();
        checkCanvas();
        checkCanvasInjection();
        System.out.println("Legacy text marquee: " + checks + " placement, filtering, cache and injection checks passed");
    }

    private static void checkAtlasPixels() {
        for (int bpp : new int[]{1, 4}) {
            var source = MemoryUtil.memAlloc(4 * bpp);
            try (var pixels = new FontAtlasPixels(bpp)) {
                for (int i = 0; i < source.capacity(); i++) source.put(i, (byte) (i + 1));
                pixels.resize(3, 2);
                pixels.write(1, 0, 2, 2, MemoryUtil.memAddress(source));
                for (int[] size : new int[][]{{3, 2}, {6, 2}, {6, 4}, {12, 4}, {12, 8}}) {
                    pixels.resize(size[0], size[1]);
                    var actual = MemoryUtil.memByteBuffer(pixels.address(), (int) pixels.size());
                    for (int y = 0; y < size[1]; y++) {
                        for (int x = 0; x < size[0]; x++) {
                            for (int c = 0; c < bpp; c++) {
                                byte expected = y < 2 && x >= 1 && x < 3
                                        ? source.get((y * 2 + x - 1) * bpp + c) : 0;
                                check(actual.get((y * size[0] + x) * bpp + c) == expected,
                                        "atlas resize preserves old stride and clears new space");
                            }
                        }
                    }
                }
                pixels.write(9, 6, 2, 2, MemoryUtil.memAddress(source));
                var actual = MemoryUtil.memByteBuffer(pixels.address(), (int) pixels.size());
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 2 * bpp; x++) {
                        check(actual.get(((y + 6) * 12 + 9) * bpp + x) == source.get(y * 2 * bpp + x),
                                "new glyph uploads use the resized stride");
                    }
                }
                try {
                    pixels.resize(6, 4);
                    throw new AssertionError("Atlas shrink must be rejected");
                } catch (IllegalArgumentException expected) {
                    checks++;
                }
            } finally {
                MemoryUtil.memFree(source);
            }
        }
    }

    private static void checkPlacement() {
        for (int guiScale : new int[]{1, 2, 3, 4}) {
            for (float scale : new float[]{0.375f, 0.7f, 1, 1.25f}) {
                float density = guiScale * scale;
                float firstA = TextSampling.place(0.18f, -2, density);
                float firstB = TextSampling.place(4.72f, -2, density);
                for (int frame = 0; frame <= 320; frame++) {
                    float origin = -2 + frame / 80f;
                    float a = TextSampling.place(0.18f, origin, density);
                    float b = TextSampling.place(4.72f, origin, density);
                    close((a - firstA) * scale, (origin + 2) * scale, "continuous glyph movement");
                    close(b - a, firstB - firstA, "stable glyph spacing");
                    var pose = new Matrix4f().translation(scale * origin, 0, 0).scale(scale);
                    float posed = pose.transformPosition(TextSampling.place(0.18f, 0, density), 0, 0,
                            new org.joml.Vector3f()).x;
                    close(posed, a * scale, "pose and draw origin use the same layout");
                }
            }
        }
        float oldMin = Float.POSITIVE_INFINITY, oldMax = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 100; i++) {
            float x = i / 100f;
            float gap = Math.round(4.72f + x) - Math.round(0.18f + x);
            oldMin = Math.min(oldMin, gap);
            oldMax = Math.max(oldMax, gap);
        }
        check(oldMax - oldMin == 1, "fixture reproduces old one-pixel spacing wobble");
    }

    private static void checkSampling() {
        int width = 1920, height = 1080;
        for (int guiScale : new int[]{1, 2, 3, 4}) {
            var projection = new Matrix4f().setOrtho(0, width / (float) guiScale,
                    height / (float) guiScale, 0, -1000, 1000);
            for (float scale : new float[]{0.5f, 0.75f, 1, 1.25f}) {
                for (int phase = -32; phase <= 32; phase++) {
                    float deviceOffset = phase / 16f;
                    var pose = new Matrix4f().translation(deviceOffset / guiScale, 0, 0).scale(scale);
                    check(TextSampling.needsLinear(pose, projection, 0, 0,
                            guiScale * scale, width, height) == (phase % 16 != 0),
                            "sampling follows physical-pixel phase");
                }
            }
        }
        var projection = new Matrix4f().setOrtho(0, width / 2f, height / 2f, 0, -1000, 1000);
        check(TextSampling.needsLinear(new Matrix4f(), projection, 0.125f, 0, 2, width, height),
                "fractional draw origin");
        check(TextSampling.needsLinear(new Matrix4f().scaling(0.5f), projection, 0, 0, 2, width, height),
                "minification");
        check(TextSampling.needsLinear(new Matrix4f().rotateZ(0.1f), projection, 0, 0, 2, width, height),
                "rotation");
        check(TextSampling.needsLinear(new Matrix4f(), new Matrix4f().perspective(1, 1, 0.1f, 100),
                0, 0, 2, width, height), "perspective");
        projection.m30(projection.m30() + 0.5f / width);
        check(TextSampling.needsLinear(new Matrix4f(), projection, 0, 0, 2, width, height),
                "fractional projection origin");
    }

    private static void checkCanvas() {
        var font = new Font();
        font.setTypeface(new Typeface_JDK(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12)));
        var run = new GlyphRun();
        run.set(new int[]{1, 2}, 0, new float[]{0, 0, 5.4f, 0}, 0, 2, font);
        var runs = new GlyphRunList();
        runs.set(new GlyphRun[]{run}, 1, null, new Rect2f(0, -9, 12, 3), 3.25f, -1.5f);
        try (var paint = new Paint()) {
            var initial = new Matrix();
            initial.setScale(0.75f, 0.75f);
            var baseline = new TextBlobCache.FeatureKey();
            baseline.update(runs, paint, initial);
            var subRun = new SubRunContainer.DirectMaskSubRun(
                    StrikeDesc.makeMask(font, paint, initial), initial, new Rect2f(0, 0, 12, 9),
                    Engine.MASK_FORMAT_A8, new int[]{1, 2}, 0, new float[]{0, 0, 5, 0}, 0, 2, 1, 1);
            for (int i = -128; i <= 128; i++) {
                var actual = new Matrix(initial);
                actual.setTranslateX(i / 32f);
                actual.setTranslateY(i / 64f);
                var baking = new Matrix(actual);
                baking.preTranslate(runs.mOriginX, runs.mOriginY);
                CanvasTextOrigin.forRasterization(baking);
                var key = new TextBlobCache.FeatureKey();
                key.update(runs, paint, baking);
                check(key.equals(baseline) && key.hashCode() == baseline.hashCode(), "reuse Canvas cache while scrolling");
                var toLocal = new Matrix();
                var toDevice = new Matrix();
                int filter = subRun.getMatrixAndFilter(actual, runs.mOriginX, runs.mOriginY, toLocal, toDevice);
                float x = actual.getTranslateX() + 0.75f * runs.mOriginX;
                float y = actual.getTranslateY() + 0.75f * runs.mOriginY;
                close(toDevice.getTranslateX(), x, "Canvas restores actual X and run origin");
                close(toDevice.getTranslateY(), y, "Canvas restores actual Y and run origin");
                check(filter == ((x == Math.floor(x) && y == Math.floor(y))
                        ? SamplerDesc.FILTER_NEAREST : SamplerDesc.FILTER_LINEAR), "Canvas fractional filtering");
            }
            var resized = new Matrix();
            resized.setScale(1, 1);
            var resizedKey = new TextBlobCache.FeatureKey();
            resizedKey.update(runs, paint, resized);
            check(!resizedKey.equals(baseline), "font scale still invalidates Canvas cache");
        }
        var perspective = new Matrix();
        perspective.m14(0.000001f);
        perspective.setTranslateX(13.25f);
        var expected = new Matrix(perspective);
        CanvasTextOrigin.forRasterization(perspective);
        check(perspective.equals(expected), "perspective rendering remains position-dependent");
    }

    private static void checkCanvasInjection() throws Exception {
        // Check the pinned dependency's bytecode, not only our mixin annotation.
        var node = new ClassNode();
        try (var in = TextMarqueeCheck.class.getResourceAsStream("/icyllis/arc3d/granite/GraniteDevice.class")) {
            if (in == null) throw new AssertionError("Missing GraniteDevice");
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        int sites = 0;
        for (var method : node.methods) {
            if (!method.name.equals("onDrawGlyphRunList")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("icyllis/arc3d/sketch/Matrix")
                        && call.name.equals("preTranslate") && call.desc.equals("(FF)V")) sites++;
            }
        }
        check(sites == 1, "exactly one Canvas translation injection site");
        var root = Path.of(System.getProperty("modernui.projectRoot"));
        for (String loader : new String[]{"fabric", "neoforge"}) {
            check(Files.readString(root.resolve(loader + "/src/main/resources/mixins.modernui-" + loader + ".json"))
                    .contains("\"MixinGraniteText\""), "Canvas mixin registered for " + loader);
        }
    }

    private static void close(float actual, float expected, String what) {
        check(Math.abs(actual - expected) < 0.0001f, what + ": " + actual + " != " + expected);
    }

    private static void check(boolean value, String what) {
        if (!value) throw new AssertionError(what);
        checks++;
    }

}
