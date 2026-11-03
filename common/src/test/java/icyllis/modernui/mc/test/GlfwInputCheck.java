package icyllis.modernui.mc.test;

import icyllis.modernui.text.Selection;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.method.TextKeyListener;
import icyllis.modernui.view.KeyEvent;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import static org.lwjgl.glfw.GLFW.*;
import static org.objectweb.asm.Opcodes.*;

/** Exercises the real 1.21.8 GLFW/Core boundary without creating Minecraft or a window. */
public final class GlfwInputCheck {
    private static final String UI = "icyllis/modernui/mc/UIManager";
    private static final String HERE = "icyllis/modernui/mc/test/GlfwInputCheck";
    private static final String PROBE = HERE + "$InputProbe";
    private static int scheduled;

    /** Optional arguments are additional compiled-class directories or named Minecraft JARs. */
    public static void main(String[] args) throws Exception {
        var roots = new ArrayList<Path>();
        for (String arg : args) roots.add(Path.of(arg));
        for (String module : List.of("common", "fabric", "neoforge")) {
            roots.add(Path.of(module, "build/classes/java/main"));
        }
        ClassNode ui = read(UI, roots);
        MethodNode normalize = method(ui, "normalizeModifiers", "(I)I");
        for (String name : List.of("onKeyPress", "onKeyRelease", "onPostMouseInput")) {
            MethodNode input = method(ui, name, "(III)V");
            MethodInsnNode call = call(input, UI, "normalizeModifiers", "(I)I");
            var before = previous(call, 1);
            variable(before.getFirst(), ILOAD, 3);
            variable(next(call), ISTORE, 3);
            if (!name.equals("onPostMouseInput")) checkCoreObtain(input, name.equals("onKeyPress") ? 0 : 1);
        }
        checkKeyboard(read("net/minecraft/client/KeyboardHandler", roots));
        checkKeyboardMixin(read("icyllis/modernui/mc/mixin/MixinKeyboardHandler", roots));
        ClassNode listener = read("net/minecraft/client/gui/components/events/GuiEventListener", roots);
        for (String loader : List.of("fabric", "neoforge")) {
            for (String screen : List.of("SimpleScreen", "MenuScreen")) {
                ClassNode node = read("icyllis/modernui/mc/" + loader + "/" + screen, roots);
                for (String name : List.of("keyPressed", "keyReleased")) {
                    method(listener, name, "(III)Z");
                    MethodNode input = method(node, name, "(III)Z");
                    var forwarded = previous(call(input, UI,
                            name.equals("keyPressed") ? "onKeyPress" : "onKeyRelease", "(III)V"), 3);
                    for (int i = 0; i < 3; i++) variable(forwarded.get(i), ILOAD, i + 1);
                }
                method(listener, "charTyped", "(CI)Z");
                var chars = method(node, "charTyped", "(CI)Z");
                variable(previous(call(chars, UI, "onCharTyped", "(C)Z"), 1).getFirst(), ILOAD, 1);
                // Mouse button state is delivered by the global post-input hook, not this Screen callback.
                method(listener, "mouseClicked", "(DDI)Z");
                var mouse = method(node, "mouseClicked", "(DDI)Z");
                var instructions = executable(mouse);
                if (instructions.size() != 2 || instructions.get(0).getOpcode() != ICONST_0
                        || instructions.get(1).getOpcode() != IRETURN) {
                    throw new AssertionError(node.name + " mouseClicked must leave global mouse dispatch unchanged");
                }
            }
        }
        Class<?> probe = probe(normalize, method(ui, "onCharTyped", "(C)Z"));
        for (int locks : new int[]{0, GLFW_MOD_CAPS_LOCK, GLFW_MOD_NUM_LOCK,
                GLFW_MOD_CAPS_LOCK | GLFW_MOD_NUM_LOCK}) {
            for (int shortcuts = 0; shortcuts < 16; shortcuts++) {
                int mods = (int) probe.getMethod("normalizeModifiers", int.class).invoke(null, locks | shortcuts);
                equal(shortcuts, mods, "lock normalization");
                var event = KeyEvent.obtain(123456789L, KeyEvent.ACTION_DOWN, GLFW_KEY_A, 7, mods, 38, 0);
                try {
                    equal(GLFW_KEY_A, event.getKeyCode(), "Core key");
                    equal(38, event.getScanCode(), "Core scancode");
                    equal(7, event.getRepeatCount(), "Core repeat");
                    equal(KeyEvent.ACTION_DOWN, event.getAction(), "Core action");
                    equal(shortcuts, event.getModifiers(), "Core mods");
                    if (event.getEventTimeNano() != 123456789L) throw new AssertionError("Core event time order");
                    if (!event.hasModifiers(shortcuts)) throw new AssertionError("Exact shortcut comparison");
                    if (event.isControlPressed() != ((shortcuts & GLFW_MOD_CONTROL) != 0)
                            || event.isShiftPressed() != ((shortcuts & GLFW_MOD_SHIFT) != 0)) {
                        throw new AssertionError("Ctrl/Shift interpretation changed");
                    }
                } finally { event.recycle(); }
            }
            var text = new SpannableStringBuilder("A测😀");
            Selection.setSelection(text, text.length());
            var backspace = KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, GLFW_KEY_BACKSPACE, 0,
                    (int) probe.getMethod("normalizeModifiers", int.class).invoke(null, locks), 22, 0);
            try {
                if (!TextKeyListener.getInstance().onKeyDown(null, text, GLFW_KEY_BACKSPACE, backspace)
                        || !text.toString().equals("A测")) throw new AssertionError("Supplementary Backspace with locks");
            } finally { backspace.recycle(); }
        }
        Object input = probe.getConstructor().newInstance();
        var buffer = new StringBuilder();
        probe.getField("mCharInputBuffer").set(input, buffer);
        probe.getField("mCommitCharInput").set(input, (Runnable) () -> {});
        var charTyped = probe.getMethod("onCharTyped", char.class);
        for (char ch : "A测😀𠮷".toCharArray()) {
            if (!(boolean) charTyped.invoke(input, ch)) throw new AssertionError("UTF-16 unit rejected");
        }
        if (!buffer.toString().equals("A测😀𠮷") || buffer.codePointCount(0, buffer.length()) != 4
                || scheduled != 6) throw new AssertionError("Actual UTF-16 buffer concatenation changed");
        for (char ch : new char[]{0, 127}) {
            if ((boolean) charTyped.invoke(input, ch)) throw new AssertionError("NUL/DEL accepted");
        }
        if (scheduled != 6 || !buffer.toString().equals("A测😀𠮷")) throw new AssertionError("Rejected input mutated buffer");
        var commit = method(ui, "commitCharInput", "()V");
        call(commit, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        call(commit, "java/lang/StringBuilder", "setLength", "(I)V");
        System.out.println("1.21.8 raw GLFW descriptors and forwarding, both loaders' Simple/MenuScreen, actual Core obtain/scancode/mods, four lock states and UTF-16 buffering passed");
    }

    public static void schedule(Runnable runnable) { scheduled++; }

    private static Class<?> probe(MethodNode normalize, MethodNode chars) {
        var node = new ClassNode();
        node.version = V21; node.access = ACC_PUBLIC; node.name = PROBE; node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(ACC_PUBLIC, "mCharInputBuffer", "Ljava/lang/StringBuilder;", null, null));
        node.fields.add(new FieldNode(ACC_PUBLIC, "mCommitCharInput", "Ljava/lang/Runnable;", null, null));
        var init = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(ALOAD, 0));
        init.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(RETURN)); node.methods.add(init);
        for (MethodNode method : List.of(normalize, chars)) {
            method.access = ACC_PUBLIC | (method == normalize ? ACC_STATIC : 0);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals(UI)) field.owner = PROBE;
                if (instruction instanceof MethodInsnNode call && call.owner.equals("icyllis/modernui/core/Core")
                        && call.name.equals("postOnMainThread") && call.desc.equals("(Ljava/lang/Runnable;)V")) {
                    call.owner = HERE; call.name = "schedule";
                }
            }
            method.visibleAnnotations = null; method.invisibleAnnotations = null; node.methods.add(method);
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        return new ClassLoader(GlfwInputCheck.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, writer.toByteArray(), 0, writer.toByteArray().length); }
        }.define();
    }

    private static void checkCoreObtain(MethodNode input, int action) {
        var values = previous(call(input, "icyllis/modernui/view/KeyEvent", "obtain",
                "(JIIIIII)Licyllis/modernui/view/KeyEvent;"), 7);
        if (!(values.get(0) instanceof MethodInsnNode clock) || !clock.owner.equals("icyllis/modernui/core/Core")
                || !clock.name.equals("timeNanos")) throw new AssertionError("Core clock parameter");
        if (values.get(1).getOpcode() != ICONST_0 + action || values.get(3).getOpcode() != ICONST_0
                || values.get(6).getOpcode() != ICONST_0) throw new AssertionError("Core action/repeat/flags order");
        variable(values.get(2), ILOAD, 1); variable(values.get(4), ILOAD, 3); variable(values.get(5), ILOAD, 2);
    }

    private static void checkKeyboard(ClassNode node) {
        method(node, "keyPress", "(JIIII)V");
        method(node, "charTyped", "(JII)V");
    }

    private static void checkKeyboardMixin(ClassNode node) {
        var method = method(node, "onKeyInputPost", "(JIIIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        var forwarded = previous(call(method, "icyllis/modernui/mc/MuiModApi", "dispatchOnPreKeyInput", "(JIIII)V"), 5);
        variable(forwarded.get(0), LLOAD, 1);
        for (int i = 1; i < 5; i++) variable(forwarded.get(i), ILOAD, i + 2);
    }

    private static ClassNode read(String name, List<Path> roots) throws Exception {
        String file = name + ".class";
        for (Path root : roots) {
            if (Files.isDirectory(root) && Files.isRegularFile(root.resolve(file))) {
                try (var stream = Files.newInputStream(root.resolve(file))) { return read(stream); }
            }
            if (Files.isRegularFile(root) && root.toString().endsWith(".jar")) {
                try (var jar = new JarFile(root.toFile())) {
                    var entry = jar.getJarEntry(file);
                    if (entry != null) try (var stream = jar.getInputStream(entry)) { return read(stream); }
                }
            }
        }
        try (var stream = GlfwInputCheck.class.getClassLoader().getResourceAsStream(file)) {
            if (stream == null) throw new AssertionError("Missing real compiled class: " + name);
            return read(stream);
        }
    }

    private static ClassNode read(InputStream stream) throws Exception {
        var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG); return node;
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new AssertionError(node.name + " missing actual descriptor " + name + desc));
    }
    private static MethodInsnNode call(MethodNode method, String owner, String name, String desc) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(desc)) return call;
        }
        throw new AssertionError(method.name + " missing forwarding call " + owner + "." + name + desc);
    }
    private static List<AbstractInsnNode> previous(AbstractInsnNode call, int count) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var p = call.getPrevious(); p != null && result.size() < count; p = p.getPrevious()) {
            if (p.getOpcode() >= 0) result.addFirst(p);
        }
        if (result.size() != count) throw new AssertionError("Missing argument instructions");
        return result;
    }
    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static List<AbstractInsnNode> executable(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    private static void variable(AbstractInsnNode instruction, int opcode, int slot) {
        if (!(instruction instanceof VarInsnNode var) || var.getOpcode() != opcode || var.var != slot) {
            throw new AssertionError("Raw input parameter order changed: opcode=" + opcode + " slot=" + slot);
        }
    }
    private static void equal(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
}
