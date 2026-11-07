package icyllis.modernui.mc.mixin;

import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.geom.RasterTextStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Formatter;

/** Color glyphs supply primitive color, not a separate coverage output. */
@Mixin(value = RasterTextStep.class, remap = false)
public abstract class MixinRasterTextStep {
    @Shadow @Final private int mMaskFormat;

    /** @author MOPELotus @reason The color step receives no coverage variable (null). */
    @Overwrite
    public void emitFragmentCoverageCode(Formatter formatter, String outputCoverage) {
        if (mMaskFormat == Engine.MASK_FORMAT_ARGB) return;
        if (mMaskFormat == Engine.MASK_FORMAT_A8) {
            formatter.format("%s = texture(%s, %s).rrrr;\n", outputCoverage, "u_GlyphAtlas", "f_TexCoords");
        } else {
            assert mMaskFormat == Engine.MASK_FORMAT_A565;
            formatter.format("%s = texture(%s, %s);\n", outputCoverage, "u_GlyphAtlas", "f_TexCoords");
        }
    }
}
