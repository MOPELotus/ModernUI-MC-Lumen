#!/usr/bin/env bash
# Real shipping Core/Arc3D JARs; Minecraft adapters are stubbed, no window or graphics device.
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd)"
: "${CORE_TEST_CP:?Set CORE_TEST_CP to ICU, fastutil, SLF4J, ASM, Mixin and LWJGL runtime JARs (including natives)}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/src/net/minecraft/resources" "$work/src/net/minecraft/server/packs/resources" \
 "$work/src/icyllis/modernui/mc/text" "$work/classes"
cat > "$work/src/net/minecraft/resources/Identifier.java" <<'JAVA'
package net.minecraft.resources; public record Identifier(String path) {}
JAVA
cat > "$work/src/net/minecraft/server/packs/resources/Resource.java" <<'JAVA'
package net.minecraft.server.packs.resources;
public interface Resource { java.io.InputStream open() throws java.io.IOException; }
JAVA
cat > "$work/src/net/minecraft/server/packs/resources/ResourceManager.java" <<'JAVA'
package net.minecraft.server.packs.resources;
public interface ResourceManager { java.util.Optional<Resource> getResource(net.minecraft.resources.Identifier id); }
JAVA
cat > "$work/src/icyllis/modernui/mc/ModernUIMod.java" <<'JAVA'
package icyllis.modernui.mc; public class ModernUIMod {
 public static net.minecraft.resources.Identifier location(String p) { return new net.minecraft.resources.Identifier(p); }
}
JAVA
cat > "$work/src/icyllis/modernui/mc/text/GlyphManager.java" <<'JAVA'
package icyllis.modernui.mc.text; public class GlyphManager { public static final int EMOJI_SIZE=72; }
JAVA
if [ "$#" -eq 0 ]; then echo 'Pass at least one shipping Core JAR' >&2; exit 2; fi
for jar in "$@"; do
 javac -encoding UTF-8 -cp "$jar:$CORE_TEST_CP" -d "$work/classes" \
  $(find "$work/src" -name '*.java') \
  "$root/common/src/main/java/icyllis/modernui/mc/text/EmojiTypeface.java" \
  "$root/common/src/main/java/icyllis/modernui/mc/mixin/MixinRasterTextStep.java" \
  "$root/common/src/test/java/icyllis/modernui/mc/text/EmojiTypefaceCheck.java"
 java --enable-native-access=ALL-UNNAMED -ea -cp "$work/classes:$jar:$CORE_TEST_CP" \
  icyllis.modernui.mc.text.EmojiTypefaceCheck
done
