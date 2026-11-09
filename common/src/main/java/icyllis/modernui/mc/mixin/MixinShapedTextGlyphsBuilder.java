package icyllis.modernui.mc.mixin;

import icyllis.modernui.graphics.text.Font;
import icyllis.modernui.mc.text.WideFontOrdinals;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.function.Function;

@Mixin(targets = "icyllis.modernui.graphics.text.ShapedText$GlyphsBuilder", remap = false)
public abstract class MixinShapedTextGlyphsBuilder {
    @Shadow @Final private ByteArrayList fontIndices;

    @Redirect(method = "<init>(I)V", at = @At(value = "NEW", target =
            "(I)Lit/unimi/dsi/fastutil/bytes/ByteArrayList;"), require = 1, allow = 1)
    private static ByteArrayList modernui$makeFontIndexList(int capacity) {
        return new WideFontOrdinals.GlyphIndexList(capacity);
    }

    @Redirect(method = "accept", at = @At(value = "INVOKE", target =
            "Ljava/util/HashMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private Object modernui$trackFontOrdinal(HashMap<Font, Byte> map, Object key,
                                             Function<Font, Byte> nextOrdinal) {
        int previousSize = map.size();
        Byte compact = map.computeIfAbsent((Font) key, nextOrdinal);
        if (map.size() > 256) {
            ((WideFontOrdinals.GlyphIndexList) fontIndices).observeFont(map, (Font) key, previousSize);
        }
        return compact;
    }
}
