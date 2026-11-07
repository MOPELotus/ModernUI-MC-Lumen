#!/usr/bin/env bash
# JDK 25+; arguments are actual shipping JARs from each Core bytecode family.
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/src/org/spongepowered/asm/mixin/injection/callback" "$work/src/icyllis/modernui/text" "$work/src/icyllis/arc3d/opengl" "$work/classes"
ann="$work/src/org/spongepowered/asm/mixin"
printf '%s\n' 'package org.spongepowered.asm.mixin; public @interface Mixin {Class<?>[] value();boolean remap() default true;}' > "$ann/Mixin.java"
for name in Shadow Overwrite;do printf 'package org.spongepowered.asm.mixin; public @interface %s {}\n' "$name" > "$ann/$name.java";done
printf '%s\n' 'package org.spongepowered.asm.mixin.injection; public @interface At {String value();}' > "$ann/injection/At.java"
printf '%s\n' 'package org.spongepowered.asm.mixin.injection; public @interface Inject {String method(); At at(); boolean cancellable(); int require(); boolean remap() default true;}' > "$ann/injection/Inject.java"
printf '%s\n' 'package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo {public boolean cancelled; public void cancel(){cancelled=true;}}' > "$ann/injection/callback/CallbackInfo.java"
printf '%s\n' 'package icyllis.modernui.text; public class SpanSet {}' > "$work/src/icyllis/modernui/text/SpanSet.java"
printf '%s\n' 'package icyllis.arc3d.opengl; public class GLDevice {}' > "$work/src/icyllis/arc3d/opengl/GLDevice.java"
printf '%s\n' 'package icyllis.arc3d.opengl; public class GLTexture extends icyllis.modernui.mc.mixin.MixinGLTexture {public boolean destroyed;public int handle=42;public boolean isDestroyed(){return destroyed;}public int getHandle(){return handle;}}' > "$work/src/icyllis/arc3d/opengl/GLTexture.java"
javac -encoding UTF-8 -d "$work/classes" $(find "$work/src" -name '*.java') \
 "$root/common/src/main/java/icyllis/modernui/mc/mixin/MixinSpanSet.java" \
 "$root/common/src/main/java/icyllis/modernui/mc/mixin/MixinGLTexture.java" \
 "$root/common/src/test/fixtures/CoreBoundaryCheck.java"
java -cp "$work/classes" icyllis.modernui.mc.CoreBoundaryCheck
for jar in "$@";do
 mkdir -p "$work/actual"
 : "${ASM_CP:?Set ASM_CP to the Minecraft ASM and ASM-tree JAR classpath (9.9+)}"
 javac -encoding UTF-8 -cp "$jar:$ASM_CP" -d "$work/actual" "$root/common/src/test/fixtures/ActualSpanSetCheck.java"
 java -cp "$work/actual:$jar:$ASM_CP" icyllis.modernui.mc.ActualSpanSetCheck "$jar" "$work/classes/icyllis/modernui/mc/mixin/MixinSpanSet.class"
done
