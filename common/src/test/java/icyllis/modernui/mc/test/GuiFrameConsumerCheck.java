package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Resolves the compiled frame consumer against actual Minecraft bytecode. */
public final class GuiFrameConsumerCheck {
    private static final String BASE = "icyllis/modernui/mc/mixin/";

    private static ClassNode read(InputStream stream) throws Exception {
        if (stream == null) throw new IllegalStateException("Missing compiled class");
        try (stream) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static ClassNode product(String name, JarFile jar) throws Exception {
        String resource = BASE + name + ".class";
        return read(jar == null ? GuiFrameConsumerCheck.class.getClassLoader().getResourceAsStream(resource)
                : jar.getInputStream(jar.getJarEntry(resource)));
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void verify(Path minecraft, JarFile previous) throws Exception {
        var consumers = new ArrayList<MethodNode>();
        for (String name : List.of("MixinGui", "MixinGameRenderer")) {
            for (MethodNode method : product(name, previous).methods) {
                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.equals("icyllis/modernui/mc/UIManager")
                            && call.name.equals("renderAbove")) {
                        require(name.equals("MixinGui"), "Frame consumer still targets old GameRenderer extraction");
                        consumers.add(method);
                    }
                }
            }
        }
        require(consumers.size() == 1, "Expected exactly one above-screen frame consumer");
        MethodNode consumer = consumers.getFirst();
        AnnotationNode injection = consumer.visibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow();
        require(Integer.valueOf(1).equals(value(injection, "require")), "Frame consumer must require its real callsite");
        require(List.of("extractRenderState").equals(value(injection, "method")), "Wrong extraction owner method");
        @SuppressWarnings("unchecked")
        var points = (List<AnnotationNode>) value(injection, "at");
        require(points.size() == 1, "Ambiguous frame consumer injection point");
        String target = (String) value(points.getFirst(), "target");
        require(target.equals("Lnet/minecraft/client/gui/Gui;toastManager()Lnet/minecraft/client/gui/components/toasts/ToastManager;"),
                "Frame must be consumed immediately before toasts");
        try (var jar = new JarFile(minecraft.toFile())) {
            ClassNode gui = read(jar.getInputStream(jar.getJarEntry("net/minecraft/client/gui/Gui.class")));
            var methods = gui.methods.stream().filter(m -> m.name.equals("extractRenderState")
                    && m.desc.equals("(Lnet/minecraft/client/DeltaTracker;ZZ)V")).toList();
            require(methods.size() == 1, "Actual GUI extraction descriptor changed");
            require(consumer.desc.equals("(Lnet/minecraft/client/DeltaTracker;ZZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),
                    "Compiled callback parameter order differs from actual extraction");
            long sites = 0;
            for (var insn : methods.getFirst().instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/Gui")
                        && call.name.equals("toastManager")
                        && call.desc.equals("()Lnet/minecraft/client/gui/components/toasts/ToastManager;")) sites++;
            }
            require(sites == 1, "Actual toast callsite missing or ambiguous");
            require(gui.fields.stream().anyMatch(f -> f.name.equals("guiRenderState")
                    && f.desc.equals("Lnet/minecraft/client/renderer/state/gui/GuiRenderState;")
                    && (f.access & Opcodes.ACC_FINAL) != 0), "Actual GUI state shadow differs");
        }
    }

    public static void main(String[] args) throws Exception {
        require(args.length >= 1, "Actual Minecraft JAR required");
        verify(Path.of(args[0]), null);
        if (args.length > 1) {
            boolean rejected = false;
            try (var previous = new JarFile(args[1])) {
                try { verify(Path.of(args[0]), previous); }
                catch (IllegalStateException expected) { rejected = true; }
            }
            require(rejected, "Old packaged consumer unexpectedly passed the actual API contract");
            System.out.println("Previous packaged consumer rejected as expected");
        }
        System.out.println("Compiled GUI frame consumer matches actual Minecraft extraction and toast callsite");
    }
}
