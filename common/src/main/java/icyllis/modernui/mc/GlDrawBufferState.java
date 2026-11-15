package icyllis.modernui.mc;

import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.system.MemoryStack;

/**
 * Saves Minecraft's draw buffer state around Arc3D submissions. Arc3D renders to a single
 * color attachment, but its non-indexed blend and color-mask calls affect all draw buffers.
 */
final class GlDrawBufferState {
    private static final int[] PARAMETERS = {
            GL33C.GL_BLEND_SRC_RGB, GL33C.GL_BLEND_DST_RGB,
            GL33C.GL_BLEND_SRC_ALPHA, GL33C.GL_BLEND_DST_ALPHA,
            GL33C.GL_BLEND_EQUATION_RGB, GL33C.GL_BLEND_EQUATION_ALPHA,
    };
    private final boolean[] enabled = new boolean[GL33C.glGetInteger(GL33C.GL_MAX_DRAW_BUFFERS)];
    private final int[] colorMasks = new int[enabled.length];
    private final boolean coreIndexed = GL.getCapabilities().OpenGL40;
    private final boolean indexed = coreIndexed || GL.getCapabilities().GL_ARB_draw_buffers_blend;
    private final int[] parameters = new int[PARAMETERS.length * (indexed ? enabled.length : 1)];

    void capture() {
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            for (int i = 0; i < enabled.length; i++) {
                enabled[i] = GL33C.glIsEnabledi(GL33C.GL_BLEND, i);
                GL33C.glGetBooleani_v(GL33C.GL_COLOR_WRITEMASK, i, mask);
                int bits = 0;
                for (int channel = 0; channel < 4; channel++) {
                    if (mask.get(channel) != 0) bits |= 1 << channel;
                }
                colorMasks[i] = bits;
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            int parameter = PARAMETERS[i % PARAMETERS.length];
            parameters[i] = indexed ? GL33C.glGetIntegeri(parameter, i / PARAMETERS.length)
                    : GL33C.glGetInteger(parameter);
        }
    }

    void restore() {
        for (int i = 0; i < enabled.length; i++) {
            if (enabled[i]) {
                GL33C.glEnablei(GL33C.GL_BLEND, i);
            } else {
                GL33C.glDisablei(GL33C.GL_BLEND, i);
            }
            int mask = colorMasks[i];
            GL33C.glColorMaski(i, (mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
        }
        for (int i = 0; i < parameters.length; i += PARAMETERS.length) {
            int attachment = i / PARAMETERS.length;
            int srcRgb = parameters[i], dstRgb = parameters[i + 1];
            int srcAlpha = parameters[i + 2], dstAlpha = parameters[i + 3];
            int rgb = parameters[i + 4], alpha = parameters[i + 5];
            if (coreIndexed) {
                GL40C.glBlendFuncSeparatei(attachment, srcRgb, dstRgb, srcAlpha, dstAlpha);
                GL40C.glBlendEquationSeparatei(attachment, rgb, alpha);
            } else if (indexed) {
                ARBDrawBuffersBlend.glBlendFuncSeparateiARB(attachment, srcRgb, dstRgb, srcAlpha, dstAlpha);
                ARBDrawBuffersBlend.glBlendEquationSeparateiARB(attachment, rgb, alpha);
            } else {
                GL33C.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                GL33C.glBlendEquationSeparate(rgb, alpha);
            }
        }
    }
}
