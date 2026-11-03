package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Verifies the actual Minecraft mixing and reload contracts used by the focus-volume hooks. */
public final class SoundVolumeContractCheck {
    public static void main(String[] args) throws Exception {
        String source = "Lnet/minecraft/sounds/SoundSource;";
        checkMinecraft(read("net/minecraft/client/sounds/SoundEngine"), read("net/minecraft/client/Options"),
                read("com/mojang/blaze3d/audio/Library"));
        for (String path : args) {
            try (var jar = new java.util.zip.ZipFile(path)) {
                checkMinecraft(readJar(jar, "net/minecraft/client/sounds/SoundEngine"),
                        readJar(jar, "net/minecraft/client/Options"),
                        readJar(jar, "com/mojang/blaze3d/audio/Library"));
                System.out.println("Verified actual NeoForge patched sound classes: " + java.nio.file.Path.of(path).getFileName());
            }
        }

        var handler = read("icyllis/modernui/mc/BlurHandler");
        var apply = method(handler, "applyVolumeControl", "()V");
        require(calls(apply, "net/minecraft/client/sounds/SoundManager", "refreshCategoryVolume") == 1,
                "Focus fading must refresh all active channels");
        require(calls(apply, "org/lwjgl/openal/AL10", "alListenerf") == 1,
                "Global gain and non-global reset must share the listener update");
        require(handler.methods.stream().noneMatch(m -> calls(m,
                "net/minecraft/client/sounds/SoundManager", "updateCategoryVolume") != 0),
                "Focus fading must not overwrite Minecraft or another mod's category gains");
        require(calls(method(handler, "onSoundLibraryLoaded", "()V"),
                "icyllis/modernui/mc/BlurHandler", "applyVolumeControl") == 1,
                "Reload must reapply the current fade even when its target is unchanged");
        var mixin = read("icyllis/modernui/mc/mixin/MixinSoundEngine");
        var fade = method(mixin, "modernui$applyFocusGain", "(F" + source +
                "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        require(calls(fade, "icyllis/modernui/mc/BlurHandler", "getSoundVolumeMultiplier") == 1,
                "Actual mixer hook is disconnected from the current focus gain");
        require(opcodes(fade, Opcodes.FMUL) == 1, "Mixer hook must apply focus gain exactly once");
        require(mixin.methods.stream().anyMatch(m -> m.name.equals("modernui$restoreFocusGainAfterReload")
                && calls(m, "icyllis/modernui/mc/BlurHandler", "onSoundLibraryLoaded") == 1),
                "Sound library reload hook missing");
        for (String loader : new String[]{"fabric", "neoforge"}) {
            var path = java.nio.file.Path.of(System.getProperty("modernui.projectRoot"), loader,
                    "src/main/resources/mixins.modernui-" + loader + ".json");
            var entries = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                    .getAsJsonObject().getAsJsonArray("client");
            require(entries.asList().stream().anyMatch(e -> e.getAsString().equals("MixinSoundEngine")),
                    loader + " must register the actual mixer hooks");
        }
        System.out.println("Actual Minecraft channel/master gain, refresh, OpenAL initialization and reload hook contracts verified");
    }

    private static void checkMinecraft(ClassNode engine, ClassNode optionsNode, ClassNode library) {
        String source = "Lnet/minecraft/sounds/SoundSource;";
        var volume = method(engine, "calculateVolume", "(F" + source + ")F");
        require(calls(volume, "net/minecraft/client/Options", "getFinalSoundSourceVolume") == 1,
                "Minecraft must already include the user's MASTER setting once");
        require(calls(volume, "it/unimi/dsi/fastutil/objects/Object2FloatMap", "getFloat") == 1,
                "Minecraft per-source gain map contract changed");
        var options = method(optionsNode, "getFinalSoundSourceVolume", "(" + source + ")F");
        require(opcodes(options, Opcodes.FMUL) == 1, "Minecraft's category/master combination changed");
        var refresh = method(engine, "refreshCategoryVolume", "(" + source + ")V");
        require(calls(refresh, "java/util/Map", "forEach") == 1, "Refresh must update active channels");
        require(method(engine, "loadLibrary", "()V") != null, "Reload hook target missing");
        require(engine.fields.stream().anyMatch(f -> f.name.equals("loaded") && f.desc.equals("Z")),
                "Reload success gate missing");
        var init = library.methods.stream()
                .filter(m -> m.name.equals("init")).findFirst().orElseThrow();
        int makeCurrent = -1, capabilities = -1, i = 0;
        for (var instruction : init.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("org/lwjgl/openal/ALC10") && call.name.equals("alcMakeContextCurrent")) makeCurrent = i;
                if (call.owner.equals("org/lwjgl/openal/AL") && call.name.equals("createCapabilities")) capabilities = i;
            }
            i++;
        }
        require(makeCurrent >= 0 && capabilities > makeCurrent, "Library's current OpenAL context contract changed");

    }

    private static ClassNode readJar(java.util.zip.ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name + ".class");
        require(entry != null, "Actual NeoForge jar is missing " + name);
        try (var stream = jar.getInputStream(entry)) {
            var result = new ClassNode();
            new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = SoundVolumeContractCheck.class.getResourceAsStream("/" + name + ".class")) {
            require(stream != null, "Missing class " + name);
            var result = new ClassNode();
            new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError(owner.name + "." + name + descriptor));
    }

    private static int calls(MethodNode method, String owner, String name) {
        int result = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result++;
        }
        return result;
    }

    private static int opcodes(MethodNode method, int opcode) {
        int result = 0;
        for (var instruction : method.instructions) if (instruction.getOpcode() == opcode) result++;
        return result;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
