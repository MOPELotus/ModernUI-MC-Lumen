package icyllis.modernui.mc.test;

import icyllis.modernui.mc.UIManager;
import icyllis.modernui.text.Selection;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.method.TextKeyListener;
import icyllis.modernui.view.KeyEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.lwjgl.glfw.GLFW.*;

/** Checks actual Minecraft event fields and the GLFW-to-Core editing boundary. */
public final class GlfwInputCheck {
    public static void main(String[] args) throws Exception {
        checkInputBoundary();
        // This version uses primitive (key, scancode, modifiers) callback arguments.
        final int key = GLFW_KEY_A, scancode = 38, modifiers = GLFW_MOD_CONTROL;
        for (int locks : new int[]{0, GLFW_MOD_CAPS_LOCK, GLFW_MOD_NUM_LOCK,
                GLFW_MOD_CAPS_LOCK | GLFW_MOD_NUM_LOCK}) {
            for (int shortcuts = 0; shortcuts < 16; shortcuts++) {
                equal(shortcuts, UIManager.normalizeModifiers(locks | shortcuts),
                        "shortcut modifiers with locks " + locks);
            }
            // Core compares exact modifier values, so lock bits must not reach it.
            var editable = new SpannableStringBuilder("A测😀");
            Selection.setSelection(editable, editable.length());
            var backspace = KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, GLFW_KEY_BACKSPACE,
                    0, UIManager.normalizeModifiers(locks), 22, 0);
            try {
                if (!TextKeyListener.getInstance().onKeyDown(null, editable, GLFW_KEY_BACKSPACE, backspace)
                        || !editable.toString().equals("A测")) {
                    throw new AssertionError("Core Backspace failed with lock state " + locks);
                }
            } finally {
                backspace.recycle();
            }
            var shortcut = KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, key,
                    0, UIManager.normalizeModifiers(locks | modifiers), scancode, 0);
            try {
                if (!shortcut.hasModifiers(KeyEvent.META_CONTROL_ON)) {
                    throw new AssertionError("Core Ctrl+A failed with lock state " + locks);
                }
            } finally {
                shortcut.recycle();
            }
        }
        System.out.println("GLFW event parameter order, lock modifiers and Core text editing checks passed");
    }

    private static void checkInputBoundary() throws Exception {
        // Testing the helper alone would miss a disconnected input integration.
        var node = new ClassNode();
        try (var stream = UIManager.class.getResourceAsStream("UIManager.class")) {
            if (stream == null) throw new AssertionError("Missing compiled UIManager");
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        for (String name : new String[]{"onKeyPress", "onKeyRelease", "onPostMouseInput"}) {
            boolean normalizes = false;
            for (var method : node.methods) {
                if (!method.name.equals(name)) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("icyllis/modernui/mc/UIManager")
                            && call.name.equals("normalizeModifiers") && call.desc.equals("(I)I")) {
                        normalizes = true;
                    }
                }
            }
            if (!normalizes) throw new AssertionError(name + " must normalize GLFW modifiers before Core dispatch");
        }
    }

    private static void equal(int expected, int actual, String description) {
        if (actual != expected) throw new AssertionError(description + ": expected " + expected + ", got " + actual);
    }
}
