package icyllis.modernui.mc.mixin;

import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.graphics.text.Font;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.mc.text.WideFontOrdinals;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShapedText.class, remap = false)
public abstract class MixinShapedText {
    @Shadow @Final private Font[] mFonts;
    @Unique private int[] modernui$wideFontIndices;

    @Redirect(method = {"<init>([CIIIIILicyllis/modernui/graphics/text/FontPaint;I)V",
            "<init>(Lcom/ibm/icu/text/Bidi;IILicyllis/modernui/graphics/text/FontPaint;I)V"},
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/bytes/ByteArrayList;toByteArray()[B"),
            require = 2, allow = 2)
    private byte[] modernui$finishFontOrdinals(ByteArrayList list) {
        byte[] compact = list.toByteArray();
        modernui$wideFontIndices = ((WideFontOrdinals.GlyphIndexList) list).finishWide(compact);
        return compact;
    }

    @Inject(method = "getFont", at = @At("HEAD"), cancellable = true, require = 1)
    private void modernui$getWideFont(int glyph, CallbackInfoReturnable<Font> cir) {
        if (modernui$wideFontIndices != null) cir.setReturnValue(mFonts[modernui$wideFontIndices[glyph]]);
    }

    @Inject(method = "getMemoryUsage", at = @At("RETURN"), cancellable = true, require = 1)
    private void modernui$accountForWideFonts(CallbackInfoReturnable<Integer> cir) {
        if (modernui$wideFontIndices != null) {
            cir.setReturnValue(cir.getReturnValueI() + 16 + MathUtil.align8(modernui$wideFontIndices.length * Integer.BYTES));
        }
    }
}
