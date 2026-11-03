package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.jar.JarFile;

/** Verify real FML 12 config ABI and preserve explicit per-edition file persistence. */
public final class ConfigRegistrationCheck {
    private static final String TYPE = "net/neoforged/fml/config/ModConfig$Type";
    private static final String REGISTER = "(L" + TYPE + ";Lnet/neoforged/fml/config/IConfigSpec;Ljava/lang/String;)V";

    public static void main(String[] args) throws Exception {
        var common = read("icyllis/modernui/mc/neoforge/ModernUIForge");
        var client = read("icyllis/modernui/mc/neoforge/ModernUIForge$Client");
        var type = read(TYPE);
        require(validTypes(common, type) && validTypes(client, type), "Compiled config enum ABI differs from actual FML");
        require(type.fields.stream().anyMatch(f -> f.name.equals("LOCAL"))
                && type.fields.stream().noneMatch(f -> f.name.equals("COMMON")), "FML 12 LOCAL configuration contract changed");
        require(countType(common, "LOCAL") == 1 && countType(client, "CLIENT") == 2,
                "Use unsynced LOCAL common config and two CLIENT configs");
        require(literal(common, "ModernUI/common.toml"), "Preserve the explicit common filename across COMMON to LOCAL rename");
        String variant = args[0];
        require(literal(client, "ModernUI/client-" + variant + ".toml") && literal(client, "ModernUI/text.toml"),
                "Preserve edition-specific client and shared text configuration files");
        var container = read("net/neoforged/fml/ModContainer");
        require(container.methods.stream().anyMatch(m -> m.name.equals("registerConfig") && m.desc.equals(REGISTER)),
                "Explicit filename registration overload is missing");
        int registrations = 0;
        for (var node : new ClassNode[]{common, client}) for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(container.name)
                    && call.name.equals("registerConfig")) {
                require(call.desc.equals(REGISTER), "Config filenames must be explicitly passed to FML");registrations++;
            }
        }
        require(registrations == 3, "Expected common, client and text registrations");
        var impl = read("icyllis/modernui/mc/neoforge/ConfigImpl");
        var save = impl.methods.stream().filter(m -> m.name.equals("saveConfig")).findFirst().orElseThrow();
        boolean persists = false;
        for (var instruction : save.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals("net/neoforged/neoforge/common/ModConfigSpec") && call.name.equals("save") && call.desc.equals("()V")) persists = true;
        require(persists, "UI configuration changes must still save through the registered spec");
        require(read("net/neoforged/neoforge/common/ModConfigSpec").methods.stream().anyMatch(m -> m.name.equals("save") && m.desc.equals("()V")),
                "Actual NeoForge persistence API is missing");
        // Reproduce the previously shipped incompatible field, without loading game classes.
        for (var method : common.methods) for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(TYPE) && field.name.equals("LOCAL")) {
                field.name = "COMMON";require(!validTypes(common, type), "Old COMMON ABI must be rejected");field.name = "LOCAL";
            }
        }
        if (args.length > 1) try (var jar = new JarFile(args[1])) {
            var actual = new ClassNode();try (var input = jar.getInputStream(jar.getJarEntry(TYPE + ".class"))) {
                new ClassReader(input).accept(actual, 0);
            }
            require(validTypes(common, actual) && validTypes(client, actual), "Actual launched FML jar enum ABI mismatch");
        }
        System.out.println("FML 12 LOCAL/CLIENT ABI, explicit " + variant + " filenames and NeoForge spec persistence verified; old COMMON rejected");
    }
    private static boolean validTypes(ClassNode node, ClassNode type) {
        for (var method : node.methods) for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field
                && field.owner.equals(TYPE) && type.fields.stream().noneMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc))) return false;
        return true;
    }
    private static int countType(ClassNode node, String name) {
        int count=0;for (var method : node.methods) for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals(TYPE) && field.name.equals(name)) count++;
        return count;
    }
    private static boolean literal(ClassNode node, String value) {
        for (var method : node.methods) for (var instruction : method.instructions)
            if (instruction instanceof LdcInsnNode ldc && value.equals(ldc.cst)) return true;
        return false;
    }
    private static ClassNode read(String name) throws Exception {
        try (var input = ConfigRegistrationCheck.class.getResourceAsStream("/" + name + ".class")) {
            require(input != null, "Missing actual class " + name);var node=new ClassNode();new ClassReader(input).accept(node, 0);return node;
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
