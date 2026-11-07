#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
check_dir="$(mktemp -d)"
trap 'rm -rf -- "$check_dir"' EXIT
javac --release 21 -encoding UTF-8 -d "$check_dir" \
  "$repo_root/common/src/main/java/icyllis/modernui/mc/UiQueueShutdown.java" \
  "$repo_root/common/src/test/java/icyllis/modernui/mc/UiQueueShutdownCheck.java" \
  "$repo_root/common/src/main/java/icyllis/modernui/mc/RendererShutdownState.java" \
  "$repo_root/common/src/test/java/icyllis/modernui/mc/RendererShutdownStateCheck.java"
java -cp "$check_dir" icyllis.modernui.mc.UiQueueShutdownCheck
java -cp "$check_dir" icyllis.modernui.mc.RendererShutdownStateCheck
