#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "$0")/../../.." && pwd)"
check_dir="$(mktemp -d)"
trap 'rm -rf -- "$check_dir"' EXIT
javac --release 21 -encoding UTF-8 -d "$check_dir" \
    "$repo_root/common/src/main/java/icyllis/modernui/mc/OwnedGpuRetirements.java" \
    "$repo_root/common/src/test/java/icyllis/modernui/mc/OwnedGpuRetirementsCheck.java"
java -cp "$check_dir" icyllis.modernui.mc.OwnedGpuRetirementsCheck
