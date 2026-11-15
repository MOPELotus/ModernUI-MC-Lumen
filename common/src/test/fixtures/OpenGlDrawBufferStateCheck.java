package icyllis.modernui.mc;

import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;

/** Hardware regression check; run on the render thread of a dedicated SDL/OpenGL context. */
public final class OpenGlDrawBufferStateCheck {
    public static void main(String[] args) {
        require(SDL_Init(SDL_INIT_VIDEO), "SDL video initialization");
        try {
            require(SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE), "core profile");
            require(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3), "GL major");
            require(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 3), "GL minor");
            long window = SDL_CreateWindow("Lumen GL state test", 64, 64, SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
            require(window != 0, "SDL window");
            try {
                long context = SDL_GL_CreateContext(window);
                require(context != 0, "GL context");
                try {
                    require(SDL_GL_MakeCurrent(window, context), "current GL context");
                    GL.createCapabilities();
                    System.out.println(glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION));
                    checkState();
                } finally {
                    SDL_GL_DestroyContext(context);
                }
            } finally {
                SDL_DestroyWindow(window);
            }
        } finally {
            SDL_Quit();
        }
    }

    private static void checkState() {
        boolean core = GL.getCapabilities().OpenGL40;
        boolean indexed = core || GL.getCapabilities().GL_ARB_draw_buffers_blend;
        require(glGetInteger(GL_MAX_DRAW_BUFFERS) >= 2, "multiple draw buffers");
        var state = new GlDrawBufferState();
        for (int attempt = 0; attempt < 2; attempt++) {
            glEnablei(GL_BLEND, attempt);
            glDisablei(GL_BLEND, 1 - attempt);
            glColorMaski(attempt, true, false, true, false);
            glColorMaski(1 - attempt, false, true, false, true);
            if (core) {
                GL40C.glBlendFuncSeparatei(attempt, GL_ONE, GL_ZERO, GL_ZERO, GL_ONE);
                GL40C.glBlendFuncSeparatei(1 - attempt, GL_ZERO, GL_ONE, GL_ONE, GL_ZERO);
                GL40C.glBlendEquationSeparatei(attempt, GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_ADD);
            } else if (indexed) {
                ARBDrawBuffersBlend.glBlendFuncSeparateiARB(attempt, GL_ONE, GL_ZERO, GL_ZERO, GL_ONE);
                ARBDrawBuffersBlend.glBlendFuncSeparateiARB(1 - attempt, GL_ZERO, GL_ONE, GL_ONE, GL_ZERO);
                ARBDrawBuffersBlend.glBlendEquationSeparateiARB(attempt, GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_ADD);
            }
            state.capture();
            glEnable(GL_BLEND);
            glColorMask(true, true, true, true);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBlendEquation(GL_FUNC_ADD);
            require(glIsEnabledi(GL_BLEND, 1 - attempt), "negative control reproduces global enable");
            state.restore();
            require(glIsEnabledi(GL_BLEND, attempt) && !glIsEnabledi(GL_BLEND, 1 - attempt), "blend enables");
            try (var stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                for (int i = 0; i < 2; i++) {
                    glGetBooleani_v(GL_COLOR_WRITEMASK, i, mask);
                    for (int c = 0; c < 4; c++) {
                        require((mask.get(c) != 0) == ((i == attempt) == (c % 2 == 0)), "color mask");
                    }
                }
            }
            if (indexed) {
                require(glGetIntegeri(GL_BLEND_SRC_RGB, attempt) == GL_ONE, "first attachment factors");
                require(glGetIntegeri(GL_BLEND_SRC_RGB, 1 - attempt) == GL_ZERO, "second attachment factors");
                require(glGetIntegeri(GL_BLEND_EQUATION_RGB, attempt) == GL_FUNC_REVERSE_SUBTRACT, "blend equation");
            }
            require(glGetError() == GL_NO_ERROR, "OpenGL error");
        }
        System.out.println("Draw buffer state survives two Arc3D-style submissions.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
