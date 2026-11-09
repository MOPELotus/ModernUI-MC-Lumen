#!/usr/bin/env bash
set -euo pipefail

if (( $# != 2 )); then
    echo "Usage: $0 <Core or packaged Lumen JAR> <Fabric client dependency classpath>" >&2
    echo "Use Java 25 and a classpath containing Fabric Loader, Mixin, ASM, and the Core runtime dependencies." >&2
    exit 2
fi

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)
src="$root/common/src/main/java/icyllis/modernui/mc"
fixtures="$root/common/src/test/fixtures"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/classes"
jdk=${JAVA_HOME:+$JAVA_HOME/bin/}
cp="$work/classes:$1:$2"

mixins=(
    MixinMeasuredParagraph MixinSpannableStringInternal MixinSpanSet
    MixinLayoutCache MixinStaticLayoutBuilder MixinPackedObjectVector
    MixinLayoutPiece MixinShapedText MixinShapedTextGlyphsBuilder
    MixinDirections MixinMeasuredParagraphBidiLevels MixinLayoutBidiLevels MixinTextLineBidiLevels
    MixinDynamicLayout AccessStaticLayoutDirections
)
targets=(
    icyllis.modernui.text.MeasuredParagraph icyllis.modernui.text.SpannableStringInternal icyllis.modernui.text.SpanSet
    icyllis.modernui.graphics.text.LayoutCache 'icyllis.modernui.text.StaticLayout$Builder' icyllis.modernui.text.PackedObjectVector
    icyllis.modernui.graphics.text.LayoutPiece icyllis.modernui.graphics.text.ShapedText 'icyllis.modernui.graphics.text.ShapedText$GlyphsBuilder'
    icyllis.modernui.text.Directions icyllis.modernui.text.Layout icyllis.modernui.text.TextLine
    icyllis.modernui.text.StaticLayout icyllis.modernui.text.DynamicLayout
)
tests=(
    icyllis.modernui.mc.text.MeasuredParagraphCheck icyllis.modernui.mc.ActualSpanCopyCheck
    icyllis.modernui.mc.ActualLayoutBoundsCheck ActualPackedVectorCheck icyllis.modernui.mc.ActualSpanSetCheck
    icyllis.modernui.mc.ActualUnicodeLayoutCheck WideFontOrdinalsCheck ActualBidiRunLevelsCheck
)
sources=("$src/text/WideFontOrdinals.java" "$src/text/BidiRunLevels.java")
for mixin in "${mixins[@]}"; do sources+=("$src/mixin/$mixin.java"); done
for test in "${tests[@]}"; do sources+=("$fixtures/${test##*.}.java"); done

{
    printf '%s' '{"required":true,"package":"icyllis.modernui.mc.mixin","compatibilityLevel":"JAVA_25","client":['
    printf '"%s"' "${mixins[0]}"
    printf ',"%s"' "${mixins[@]:1}"
    printf '%s\n' ']}'
} > "$work/classes/text-boundary.mixins.json"

"${jdk}javac" -proc:none -cp "$cp" -d "$work/classes" "$fixtures/ActualCoreMixinTransform.java" "${sources[@]}"
"${jdk}java" -cp "$cp" ActualCoreMixinTransform text-boundary.mixins.json "$work/woven" "${targets[@]}"
for test in "${tests[@]}"; do
    "${jdk}java" --enable-native-access=ALL-UNNAMED -ea -Djava.awt.headless=true -cp "$work/woven:$cp" "$test"
done
