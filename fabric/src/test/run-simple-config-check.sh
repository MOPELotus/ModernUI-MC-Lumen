#!/usr/bin/env bash
# Standalone JDK 21+ check, without Minecraft, Gradle or external mod dependencies.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "$0")/../../.." && pwd)"
check_dir="$(mktemp -d)"
trap 'rm -rf -- "$check_dir"' EXIT
mkdir -p "$check_dir/src/icyllis/modernui/mc" "$check_dir/src/org/jetbrains/annotations" "$check_dir/src/javax/annotation" "$check_dir/classes"
cat > "$check_dir/src/icyllis/modernui/mc/ModernUIMod.java" <<'JAVA'
package icyllis.modernui.mc;
public final class ModernUIMod {
    public static final Object MARKER = new Object();
    public static final Logger LOGGER = new Logger();
    public static final class Logger {
        public void warn(Object marker, String message, Object... args) {}
        public void error(Object marker, String message, Object... args) {}
    }
}
JAVA
printf '%s\n' 'package org.jetbrains.annotations; public @interface Nullable {}' > "$check_dir/src/org/jetbrains/annotations/Nullable.java"
printf '%s\n' 'package javax.annotation; public @interface Nonnull {}' > "$check_dir/src/javax/annotation/Nonnull.java"
javac --release 21 -encoding UTF-8 -d "$check_dir/classes" \
    "$check_dir/src/icyllis/modernui/mc/ModernUIMod.java" \
    "$check_dir/src/org/jetbrains/annotations/Nullable.java" \
    "$check_dir/src/javax/annotation/Nonnull.java" \
    "$repo_root/fabric/src/main/java/icyllis/modernui/mc/fabric/SimpleConfigSpec.java" \
    "$repo_root/fabric/src/test/java/icyllis/modernui/mc/fabric/SimpleConfigSpecCheck.java"
java -cp "$check_dir/classes" icyllis.modernui.mc.fabric.SimpleConfigSpecCheck "$@"
