package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.awt.Font;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.jar.JarFile;

/** Runs compiled accordion methods with queued widget callbacks, without initializing a GUI or GL. */
public final class PreferredFontIdentifierCheck {
    private static final String ACCORDION = "icyllis/modernui/mc/ui/PreferredFontAccordion";
    private static final String TEST = Type.getInternalName(PreferredFontIdentifierCheck.class);
    private static final String PROBE = TEST + "$ProductionProbe";
    private static int changes, fontChanges, reloads;

    public static void main(String[] args) throws Exception {
        checkCoreDispatch();
        Path temporary = Files.createTempDirectory("modernui-font-identifier-");
        try {
            Path directory = temporary.resolve("registered-font-directory");
            Files.createDirectories(directory);
            Path path = directory.resolve("a-very-long-font-file-name-that-must-remain-a-file-identifier-".repeat(2) + ".ttf");
            String family = "Fixture Resolved Family";
            if (args.length > 1) {
                Path source = Path.of(args[1]);
                if (!Files.isRegularFile(source)) throw new AssertionError("Explicit real TTF missing: " + source);
                Files.copy(source, path);
                family = Font.createFont(Font.TRUETYPE_FONT, path.toFile()).getFamily(Locale.ROOT);
                System.out.println("Explicit real-TTF fixture parsed: " + family + "; production font loading is outside this CPU probe");
            } else {
                boolean bundled = false;
                for (String resource : List.of("/assets/modernui/font/muii18ncompat/muii18ncompat.ttf",
                        "/assets/modernui/font/Inter-Regular.ttf")) {
                    try (var input = PreferredFontIdentifierCheck.class.getResourceAsStream(resource)) {
                        if (input != null) {
                            Files.copy(input, path);
                            bundled = true;
                            break;
                        }
                    }
                }
                if (!bundled) Files.writeString(path, "Identifier-only fixture; not a font payload");
                System.out.println("Default identifier/deferred-callback fixture (" + (bundled ? "bundled TTF resource" : "temporary .ttf identifier")
                        + "); resolved family is a sink fixture, no font parsing/loading claim");
            }
            FakeClient.family = new FakeFamily(family);
            ClassNode current = read(ACCORDION);
            Class<?> probe = probe(current, null);
            for (String value : List.of(path.toString(), directory.toString(), "unregistered:custom-font-identifier", family.toLowerCase(Locale.ROOT), " " + family + " ", family)) {
                Fixture fixture = new Fixture(probe, value, family);
                fixture.initialize();
                equal(value.equals(family) ? 1 : 0, fixture.spinner.selection, "raw identifier spinner choice");
                fixture.assertUnchanged("before queued dispatch");
                fixture.drain();
                fixture.assertUnchanged("after queued dispatch");
            }
            Fixture user = new Fixture(probe, path.toString(), family);
            user.initialize(); user.drain();
            user.spinner.setSelection(2);
            user.assertUnchanged("user event before queued dispatch");
            user.drain();
            equal("Different Family", FakeConfig.CLIENT.mFirstFontFamily.value, "user family persisted");
            equal("Different Family", user.input.text, "user family input");
            equal(1, FakeConfig.CLIENT.mFirstFontFamily.writes, "one config write");
            equal(1, changes, "one settings callback");
            equal(1, reloads, "one typeface reload");
            equal(1, fontChanges, "one font callback");
            // Repeated queued selection of the current family must remain a no-op.
            user.spinner.setSelection(2); user.drain();
            equal(1, changes, "same-family callback no-op"); equal(1, reloads, "same-family reload no-op");
            Fixture notReady = new Fixture(probe, path.toString(), family);
            probe.getField("mAdapter").set(notReady.instance, null);
            notReady.initialize(); notReady.assertUnchanged("adapter not ready");
            probe.getField("mSpinner").set(notReady.instance, null);
            notReady.initialize(); notReady.assertUnchanged("spinner not ready");
            if (args.length > 0) {
                ClassNode old;
                try (JarFile jar = new JarFile(args[0]); var input = jar.getInputStream(jar.getJarEntry(ACCORDION + ".class"))) {
                    old = new ClassNode(); new ClassReader(input).accept(old, 0);
                }
                Fixture regression = new Fixture(probe(read(ACCORDION), method(old, "updateSpinnerSelection")), path.toString(), family);
                regression.initialize();
                regression.assertUnchanged("old method before deferred event");
                regression.drain();
                equal(family, FakeConfig.CLIENT.mFirstFontFamily.value, "old compiled resolved-family method reproduces path loss");
                equal(1, FakeConfig.CLIENT.mFirstFontFamily.writes, "negative control writes saved path");
                equal(1, reloads, "negative control reloads unexpectedly");
                System.out.println("Negative control: old compiled updateSpinnerSelection overwrote the font-file identifier only after queued callback");
            }
            System.out.println("Preferred font identifiers: compiled initialization/callback/apply methods preserve file/directory/unknown IDs, family no-op, user changes and not-ready adapter; Core deferred dispatch verified");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static final class Fixture {
        final Object instance;
        final FakeSpinner spinner = new FakeSpinner();
        final FakeInput input = new FakeInput();
        final String initial;
        final Method update;
        Fixture(Class<?> type, String value, String family) throws Exception {
            initial = value;
            changes = fontChanges = reloads = 0;
            FakeConfig.CLIENT.mFirstFontFamily = new FakeValue(value);
            instance = type.getConstructor().newInstance();
            type.getField("mSpinner").set(instance, spinner);
            type.getField("mInput").set(instance, input);
            input.text = value;
            type.getField("mAdapter").set(instance, new FakeAdapter(new FakeItem("…"), new FakeItem(family), new FakeItem("Different Family")));
            type.getField("mOnChanged").set(instance, (Runnable) () -> changes++);
            type.getField("mOnFontChanged").set(instance, (Runnable) () -> fontChanges++);
            update = type.getMethod("updateSpinnerSelection");
            Method select = type.getMethod("onItemSelected", FakeAdapterView.class, FakeView.class, int.class, long.class);
            spinner.listener = position -> {
                try { select.invoke(instance, new FakeAdapterView(), new FakeView(), position, (long) position); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            };
        }
        void initialize() throws Exception { update.invoke(instance); }
        void drain() { spinner.drain(); }
        void assertUnchanged(String stage) {
            equal(initial, FakeConfig.CLIENT.mFirstFontFamily.value, stage + " identifier");
            equal(initial, input.text, stage + " input");
            equal(0, FakeConfig.CLIENT.mFirstFontFamily.writes, stage + " writes");
            equal(0, changes, stage + " settings callbacks"); equal(0, reloads, stage + " reloads");
            equal(0, fontChanges, stage + " font callbacks");
        }
    }

    private static Class<?> probe(ClassNode source, MethodNode oldUpdate) {
        ClassNode node = new ClassNode(); node.version = Opcodes.V21; node.access = Opcodes.ACC_PUBLIC;
        node.name = ACCORDION; node.superName = "java/lang/Object";
        for (FieldNode field : source.fields) if (Set.of("mSpinner", "mAdapter", "mInput", "mOnChanged", "mOnFontChanged").contains(field.name)) {
            field.access = Opcodes.ACC_PUBLIC; node.fields.add(field);
        }
        for (String name : List.of("updateSpinnerSelection", "onItemSelected", "applyNewValue")) {
            MethodNode selected = name.equals("updateSpinnerSelection") && oldUpdate != null ? oldUpdate : method(source, name);
            selected.access = Opcodes.ACC_PUBLIC; selected.visibleAnnotations = selected.invisibleAnnotations = null;
            selected.localVariables = null; node.methods.add(selected);
        }
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(ctor);
        Map<String, String> names = new HashMap<>(); names.put(ACCORDION, PROBE);
        names.put(ACCORDION + "$FontFamilyItem", Type.getInternalName(FakeItem.class));
        names.put("icyllis/modernui/mc/Config", Type.getInternalName(FakeConfig.class));
        names.put("icyllis/modernui/mc/Config$Client", Type.getInternalName(FakeConfigClient.class));
        names.put("icyllis/modernui/mc/ConfigItem", Type.getInternalName(FakeValue.class));
        names.put("icyllis/modernui/mc/ui/PreferencesFragment", Type.getInternalName(FakePreferences.class));
        names.put("icyllis/modernui/mc/ModernUIClient", Type.getInternalName(FakeClient.class));
        names.put("icyllis/modernui/graphics/text/FontFamily", Type.getInternalName(FakeFamily.class));
        names.put("icyllis/modernui/core/Context", Type.getInternalName(FakeContext.class));
        names.put("icyllis/modernui/view/View", Type.getInternalName(FakeView.class));
        names.put("icyllis/modernui/widget/AdapterView", Type.getInternalName(FakeAdapterView.class));
        names.put("icyllis/modernui/widget/Spinner", Type.getInternalName(FakeSpinner.class));
        names.put("icyllis/modernui/widget/ArrayAdapter", Type.getInternalName(FakeAdapter.class));
        names.put("icyllis/modernui/widget/EditText", Type.getInternalName(FakeInput.class));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(PreferredFontIdentifierCheck.class.getClassLoader()) {
            Class<?> define() { return defineClass(PROBE.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
    }

    private static void checkCoreDispatch() throws Exception {
        MethodNode selection = method(read("icyllis/modernui/widget/AbsSpinner"), "setSelection", "(I)V");
        require(calls(selection, "requestLayout") && !calls(selection, "onItemSelected"), "Core setSelection must request a later layout");
        MethodNode changed = method(read("icyllis/modernui/widget/AdapterView"), "selectionChanged");
        require(calls(changed, "post") && hasField(changed, "mInLayout") && hasField(changed, "mBlockLayoutRequests"), "Core in-layout selection must post a notifier");
        require(calls(method(read("icyllis/modernui/widget/AdapterView$SelectionNotifier"), "run"), "dispatchOnItemSelected"), "Core notifier must dispatch after layout");
        require(calls(method(read("icyllis/modernui/widget/AdapterView"), "fireOnSelected"), "onItemSelected"), "Core dispatch must reach selection callback");
    }
    private static ClassNode read(String name) throws Exception {
        try (var input = PreferredFontIdentifierCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing actual class " + name);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode method(ClassNode node, String name, String desc) { return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(); }
    private static boolean calls(MethodNode method, String name) { return Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode m && m.name.equals(name)); }
    private static boolean hasField(MethodNode method, String name) { return Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals(name)); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual, String message) { if (!Objects.equals(expected, actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual); }

    public static final class FakeConfig { public static final FakeConfigClient CLIENT = new FakeConfigClient(); }
    public static final class FakeConfigClient { public FakeValue mFirstFontFamily; }
    public static final class FakeValue {
        public Object value; public int writes;
        public FakeValue(String value) { this.value = value; }
        public Object get() { return value; }
        public void set(Object value) { this.value = value; writes++; }
    }
    public static final class FakeContext {}
    public static class FakeView { public FakeContext getContext() { return new FakeContext(); } }
    public static final class FakeAdapterView extends FakeView {}
    public static final class FakeInput extends FakeView { public String text; public void setText(CharSequence value) { text = value.toString(); } }
    public static final class FakeItem { public final String rootName; public FakeItem(String name) { rootName = name; } }
    public static final class FakeAdapter {
        private final FakeItem[] items;
        public FakeAdapter(FakeItem... items) { this.items = items; }
        public int getCount() { return items.length; }
        public Object getItem(int position) { return items[position]; }
    }
    public static final class FakeSpinner {
        public int selection = -1; public IntConsumer listener;
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        public void setSelection(int position) { selection = position; queue.add(() -> listener.accept(position)); }
        public void drain() { while (!queue.isEmpty()) queue.remove().run(); }
    }
    public static final class FakePreferences { public static void reloadDefaultTypeface(FakeContext context, Runnable callback) { reloads++; callback.run(); } }
    public static final class FakeFamily { private final String name; public FakeFamily(String name) { this.name = name; } public String getFamilyName() { return name; } }
    public static final class FakeClient { public static FakeFamily family; public static FakeClient getInstance() { return new FakeClient(); } public FakeFamily getFirstFontFamily() { return family; } }
}
