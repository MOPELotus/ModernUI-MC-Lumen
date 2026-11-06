package icyllis.modernui.mc.fabric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standalone CPU regression checks; run with fabric/src/test/run-simple-config-check.sh. */
public final class SimpleConfigSpecCheck {
    private static int assertions;
    private enum Mode { FIRST, SECOND }

    private static final class Fixture {
        final SimpleConfigSpec spec;
        final SimpleConfigSpec.BooleanValue enabled;
        final SimpleConfigSpec.IntValue size;
        final SimpleConfigSpec.DoubleValue scale;
        final SimpleConfigSpec.ConfigValue<String> name;
        final SimpleConfigSpec.ConfigValue<List<? extends String>> fonts;
        final SimpleConfigSpec.EnumValue<Mode> mode;

        Fixture(Path file) {
            var builder = new SimpleConfigSpec.Builder();
            builder.push("font");
            enabled = builder.define("enabled", true);
            size = builder.defineInRange("size", 12, 6, 72);
            scale = builder.defineInRange("scale", 1.0, 0.5, 2.0);
            name = builder.define("name", "Inter");
            fonts = builder.defineList("fallback", () -> List.of("Inter", "Source Han Sans"),
                    entry -> entry instanceof String string && !string.isEmpty());
            mode = builder.defineEnum("mode", Mode.FIRST);
            builder.pop();
            spec = builder.build(file);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("modernui-simple-config-check-");
        try {
            roundTrip(root.resolve("roundtrip.toml"));
            legacyFile(root.resolve("legacy.toml"));
            invalidValues(root.resolve("invalid-values.toml"));
            malformedProtection(root.resolve("malformed.toml"));
            appendMissing(root.resolve("missing.toml"));
            externalEdits(root.resolve("external.toml"));
            rootAndQuotedKeys(root.resolve("keys.toml"));
            for (String existing : args) verifyExisting(root, Path.of(existing));
            System.out.println("SimpleConfigSpec: " + assertions + " assertions passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void roundTrip(Path file) throws Exception {
        Fixture initial = new Fixture(file);
        initial.spec.load();
        initial.enabled.set(false);
        initial.name.set(" quote\" #comma, \\ path\t\n\r\b\f \u0001 \u007f 中 \ud83c\udfb5 ");
        var selected = new ArrayList<>(List.of("C:\\Fonts\\Inter.ttf", "  spaces  ", "#hash,comma",
                "quote\"slash\\", "中文", "" + (char) 9));
        initial.fonts.set(selected);
        selected.add("must not mutate saved selection");
        initial.mode.set(Mode.SECOND);
        initial.spec.save();
        String saved = Files.readString(file);
        Fixture second = new Fixture(file);
        second.spec.load();
        equal(second.name.get(), initial.name.get(), "control/Unicode/quotes round trip");
        equal(second.fonts.get(), initial.fonts.get(), "font list preserved exactly once");
        equal(second.enabled.get(), false, "boolean round trip");
        equal(second.mode.get(), Mode.SECOND, "enum round trip");
        second.spec.save();
        equal(Files.readString(file), saved, "unchanged save byte-for-byte stable");
        second.name.set("changed once");
        second.spec.save();
        second.name.set("changed twice");
        second.spec.save();
        Fixture third = new Fixture(file);
        third.spec.load();
        equal(third.name.get(), "changed twice", "repeated atomic saves update snapshot");
    }

    private static void legacyFile(Path file) throws Exception {
        String source = "# Keep this header\r\n[font] # table note\r\n"
                + "enabled = true\r\nsize = 999 # range\r\nscale = 1_0e-1\r\n"
                + "name = 'C:\\Fonts\\Inter #, \"single\".ttf'\r\n"
                + "fallback = [ # NightConfig multi-line array\r\n"
                + "    \"C:\\\\Fonts\\\\Inter.ttf\", # Windows basic string\r\n"
                + "    'C:\\Fonts\\SourceHan.ttf', # literal path\r\n"
                + "    \"  #, spaced \\\"font\\\"  \",\r\n"
                + "    \"\\u4E2D\\U0001F3B5\",\r\n]\r\n"
                + "mode = 'SECOND'\r\nunknown = \"preserve me\" # user extension\r\n";
        Files.writeString(file, source);
        Fixture fixture = new Fixture(file);
        fixture.spec.load();
        equal(fixture.fonts.get(), List.of("C:\\Fonts\\Inter.ttf", "C:\\Fonts\\SourceHan.ttf",
                "  #, spaced \"font\"  ", "中\ud83c\udfb5"), "legacy array escapes/comments/trailing comma");
        equal(fixture.name.get(), "C:\\Fonts\\Inter #, \"single\".ttf", "literal string not unescaped");
        equal(fixture.size.get(), 72, "integer clamped to configured range");
        equal(fixture.scale.get(), 1.0, "underscores/exponent supported");
        equal(fixture.mode.get(), Mode.SECOND, "literal enum");
        fixture.spec.save();
        equal(Files.readString(file), source, "unchanged legacy file including comments is preserved");
        fixture.enabled.set(false);
        fixture.fonts.set(List.of("preferred\\font.ttf", "fallback #, font"));
        fixture.spec.save();
        String changed = Files.readString(file);
        require(changed.contains("# Keep this header\r\n"), "header survives save");
        require(changed.contains("unknown = \"preserve me\" # user extension\r\n"), "unknown key survives save");
        require(changed.contains("size = 999 # range"), "unmodified known value/comment retained");
        Fixture reload = new Fixture(file);
        reload.spec.load();
        equal(reload.fonts.get(), fixture.fonts.get(), "edited font selection survives reload");
    }

    private static void invalidValues(Path file) throws Exception {
        Files.writeString(file, "[font]\nenabled = \"false-not-a-boolean\"\nsize = 9999999999999999999999\n"
                + "scale = 1e999\nname = false\nfallback = [\"\"]\nmode = \"MISSING\"\n");
        Fixture fixture = new Fixture(file);
        fixture.spec.load();
        equal(fixture.enabled.get(), true, "invalid boolean uses declared true default");
        equal(fixture.size.get(), 12, "integer overflow uses default");
        equal(fixture.scale.get(), 1.0, "non-finite double uses default");
        equal(fixture.name.get(), "Inter", "non-string uses default");
        equal(fixture.fonts.get(), List.of("Inter", "Source Han Sans"), "list validator preserved");
        equal(fixture.mode.get(), Mode.FIRST, "invalid enum uses default");
        fixture.size.set(-100);
        fixture.scale.set(100.0);
        equal(fixture.size.get(), 6, "UI integer values are clamped");
        equal(fixture.scale.get(), 2.0, "UI doubles are clamped");
        fixture.scale.set(Double.NaN);
        equal(fixture.scale.get(), 1.0, "UI NaN cannot poison persisted numbers");
        String brokenBoolean = "[font]\nenabled = false-but-invalid\n";
        Files.writeString(file, brokenBoolean);
        fixture.spec.load();
        equal(fixture.enabled.get(), true, "malformed bare boolean does not silently become false");
        fixture.spec.save();
        equal(Files.readString(file), brokenBoolean, "malformed bare token is protected");
    }

    private static void malformedProtection(Path file) throws Exception {
        List<String> malformed = List.of(
                "[font]\nfallback = [\"one\",\n", "[font]\nfallback = [\"one\" \"two\"]\n",
                "[font]\nfallback = [\"one\",,]\n", "[font]\nfallback = [\"\\q\"]\n",
                "[font]\nname = \"\\uD800\"\n", "[font]\nname = \"\\U00110000\"\n",
                "[font]\nname = \"unterminated\n", "[font]\nname = 'unterminated\n",
                "[font]\nname = \"x\"junk\n", "[font]\nname = \"one\"\nname = \"two\"\n",
                "[font]\nname = \"one\"\n[font]\n", "[font]\nname = \"one\"\n[font.name]\n",
                "[font]\nextra = { value = 1 }\n", "[font]\nextra = 2026-11-06\n",
                "[font]\nname = \"\"\"multiline\ntext\"\"\"\n", "[[font]]\nname = \"x\"\n",
                "[font]\nfallback = [1, 2]\n", "[font]\nname.part = \"x\"\n");
        for (String source : malformed) {
            Files.writeString(file, source);
            Fixture fixture = new Fixture(file);
            fixture.spec.load();
            fixture.name.set("must not destroy original");
            fixture.spec.save();
            equal(Files.readString(file), source, "malformed/unsupported document remains intact");
        }
        Files.delete(file);
        Files.createDirectory(file);
        Fixture unreadable = new Fixture(file);
        unreadable.spec.load();
        unreadable.spec.save();
        require(Files.isDirectory(file), "unreadable non-file path is not overwritten");
        Files.delete(file);
    }

    private static void appendMissing(Path file) throws Exception {
        Files.writeString(file, "# header\n[font]\nname = 'mine' # selected\n[extension]\nunknown = true\n");
        Fixture fixture = new Fixture(file);
        fixture.spec.load();
        fixture.spec.save();
        String saved = Files.readString(file);
        require(saved.contains("name = 'mine' # selected"), "selected string retained when filling defaults");
        require(saved.contains("[extension]\nunknown = true"), "other table preserved");
        Fixture reload = new Fixture(file);
        reload.spec.load();
        equal(reload.name.get(), "mine", "missing defaults inserted into correct section");
        equal(reload.fonts.get(), List.of("Inter", "Source Han Sans"), "missing list round trip");
        Files.writeString(file, "# no known tables\n[extension]\nunknown = true\n");
        fixture = new Fixture(file);
        fixture.spec.load();
        fixture.name.set("added section");
        fixture.spec.save();
        reload = new Fixture(file);
        reload.spec.load();
        equal(reload.name.get(), "added section", "missing table appended safely");
    }

    private static void externalEdits(Path file) throws Exception {
        Fixture fixture = new Fixture(file);
        fixture.spec.load();
        fixture.spec.save();
        String external = Files.readString(file) + "# another editor changed the file\n";
        Files.writeString(file, external);
        fixture.name.set("stale process");
        fixture.spec.save();
        equal(Files.readString(file), external, "external edits are not overwritten");
        Files.delete(file);
        fixture.spec.load();
        Files.writeString(file, "# created concurrently\n");
        fixture.spec.save();
        equal(Files.readString(file), "# created concurrently\n", "new concurrent file is not overwritten");
    }

    private static void rootAndQuotedKeys(Path file) throws Exception {
        var builder = new SimpleConfigSpec.Builder();
        var rootValue = builder.define("root # key", "root");
        builder.push("quoted.section");
        var item = builder.define("font space", "selected");
        var spec = builder.build(file);
        spec.load();
        spec.save();
        rootValue.set("new root");
        item.set("new selection");
        spec.save();
        var reloadBuilder = new SimpleConfigSpec.Builder();
        var reloadedRoot = reloadBuilder.define("root # key", "wrong");
        reloadBuilder.push("quoted.section");
        var reloadedItem = reloadBuilder.define("font space", "wrong");
        var reload = reloadBuilder.build(file);
        reload.load();
        equal(reloadedRoot.get(), "new root", "quoted root key round trip");
        equal(reloadedItem.get(), "new selection", "quoted table/key round trip");
    }

    private static void verifyExisting(Path root, Path original) throws Exception {
        String source = Files.readString(original);
        // Assert the parser actually accepted the document, rather than merely refusing to save it.
        var document = Class.forName(SimpleConfigSpec.class.getName() + "$Document");
        var parse = document.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        require(parse.invoke(null, source) != null, "existing NightConfig file parses: " + original);
        Path copy = root.resolve("existing.toml");
        Files.writeString(copy, source);
        var spec = new SimpleConfigSpec.Builder().build(copy);
        spec.load();
        spec.save();
        equal(Files.readString(copy), source, "existing config is preserved: " + original);
    }

    private static void equal(Object actual, Object expected, String message) {
        require(actual.equals(expected), message + ": expected " + expected + ", got " + actual);
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
