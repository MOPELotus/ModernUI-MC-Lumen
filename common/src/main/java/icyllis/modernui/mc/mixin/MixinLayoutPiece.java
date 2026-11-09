package icyllis.modernui.mc.mixin;

import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.graphics.text.Font;
import icyllis.modernui.graphics.text.FontPaint;
import icyllis.modernui.graphics.text.LayoutPiece;
import icyllis.modernui.mc.text.WideFontOrdinals;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;

@Mixin(value = LayoutPiece.class, remap = false)
public abstract class MixinLayoutPiece {
    @Shadow @Final private byte[] mFontIndices;
    @Shadow @Final private Font[] mFonts;
    // During construction: a WideFontOrdinals builder. After construction: immutable int[] or null.
    @Unique private Object modernui$wideFontIndices;

    @Redirect(method = "<init>([CIIIIZLicyllis/modernui/graphics/text/FontPaint;Licyllis/modernui/graphics/text/LayoutPiece;I)V",
            at = @At(value = "INVOKE", target =
                    "Ljava/util/HashMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private Object modernui$trackFontOrdinal(HashMap<Font, Byte> map, Object key,
                                             Function<Font, Byte> nextOrdinal) {
        int previousSize = map.size();
        Byte compact = map.computeIfAbsent((Font) key, nextOrdinal);
        if (map.size() > 256) {
            modernui$wideFontIndices = WideFontOrdinals.observe((WideFontOrdinals) modernui$wideFontIndices,
                    map, (Font) key, previousSize);
        }
        return compact;
    }

    @Redirect(method = "<init>([CIIIIZLicyllis/modernui/graphics/text/FontPaint;Licyllis/modernui/graphics/text/LayoutPiece;I)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Arrays;fill([BIIB)V"), require = 1, allow = 1)
    private void modernui$storeFontRun(byte[] compact, int start, int end, byte ordinal) {
        Arrays.fill(compact, start, end, ordinal);
        if (modernui$wideFontIndices instanceof WideFontOrdinals wide) wide.addRange(start, end);
    }

    @Inject(method = "<init>([CIIIIZLicyllis/modernui/graphics/text/FontPaint;Licyllis/modernui/graphics/text/LayoutPiece;I)V",
            at = @At("RETURN"), require = 1)
    private void modernui$finishFontOrdinals(char[] text, int contextStart, int contextEnd, int start,
                                             int end, boolean rtl, FontPaint paint, LayoutPiece hint,
                                             int computeFlags, CallbackInfo ci) {
        if (hint != null) {
            modernui$wideFontIndices = ((MixinLayoutPiece) (Object) hint).modernui$wideFontIndices;
        } else if (modernui$wideFontIndices instanceof WideFontOrdinals wide) {
            modernui$wideFontIndices = wide.finish(mFontIndices);
        }
    }

    @Inject(method = "getFont", at = @At("HEAD"), cancellable = true, require = 1)
    private void modernui$getWideFont(int glyph, CallbackInfoReturnable<Font> cir) {
        if (modernui$wideFontIndices instanceof int[] wide) cir.setReturnValue(mFonts[wide[glyph]]);
    }

    @Inject(method = "getMemoryUsage", at = @At("RETURN"), cancellable = true, require = 1)
    private void modernui$accountForWideFonts(CallbackInfoReturnable<Integer> cir) {
        if (modernui$wideFontIndices instanceof int[] wide) {
            cir.setReturnValue(cir.getReturnValueI() + 16 + MathUtil.align8(wide.length * Integer.BYTES));
        }
    }
}
