package icyllis.modernui.mc.text;

import org.joml.Matrix4fc;

/** Pixel placement and filtering for the legacy immediate text renderer. */
public final class TextSampling {
    private TextSampling() {
    }

    public static float place(float local, float origin, float density) {
        return Math.round(local * density) / density + origin;
    }

    public static boolean needsLinear(Matrix4fc pose, Matrix4fc projection,
                                      float x, float y, float density, int width, int height) {
        // Measure in framebuffer pixels, including scaling in the projection.
        // Perspective and off-axis transforms cannot use a one-texel mapping.
        if (pose.m01() != 0 || pose.m10() != 0 || pose.m03() != 0 || pose.m13() != 0 ||
                !near(pose.m33(), 1) || projection.m01() != 0 || projection.m10() != 0 ||
                projection.m03() != 0 || projection.m13() != 0 || projection.m23() != 0 ||
                !near(projection.m33(), 1)) {
            return true;
        }
        float scaleX = projection.m00() * width * 0.5f;
        float scaleY = projection.m11() * height * 0.5f;
        if (!near(Math.abs(pose.m00() * scaleX / density), 1) ||
                !near(Math.abs(pose.m11() * scaleY / density), 1)) {
            return true;
        }
        float originX = (pose.m00() * x + pose.m30()) * scaleX +
                (projection.m30() + 1) * width * 0.5f;
        float originY = (pose.m11() * y + pose.m31()) * scaleY +
                (projection.m31() + 1) * height * 0.5f;
        return !near(originX, Math.rint(originX)) || !near(originY, Math.rint(originY));
    }

    private static boolean near(float value, double target) {
        return Math.abs(value - target) <= 0.0001;
    }
}
