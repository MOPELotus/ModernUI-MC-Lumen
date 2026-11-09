package icyllis.modernui.mc.mixin;

import icyllis.modernui.graphics.text.FontPaint;
import icyllis.modernui.graphics.text.LayoutCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = LayoutCache.class, remap = false)
public abstract class MixinLayoutCache {
    @ModifyConstant(method = "getOrCreate([CIIIIZLicyllis/modernui/graphics/text/FontPaint;I)" +
            "Licyllis/modernui/graphics/text/LayoutPiece;",
            constant = @Constant(intValue = LayoutCache.MAX_PIECE_LENGTH), require = 1, allow = 1)
    private static int modernui$boundCachedContext(int maxPieceLength, char[] text,
                                                   int contextStart, int contextLimit,
                                                   int start, int limit, boolean rtl,
                                                   FontPaint paint, int computeFlags) {
        // A cache key copies the full shaping context, even when the piece is short.
        // A negative cutoff sends every valid piece to the existing uncached path.
        return contextLimit - contextStart > maxPieceLength ? -1 : maxPieceLength;
    }
}
