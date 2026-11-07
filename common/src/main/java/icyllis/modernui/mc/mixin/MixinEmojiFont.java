package icyllis.modernui.mc.mixin;

import icyllis.arc3d.sketch.Typeface;
import icyllis.modernui.graphics.text.EmojiFont;
import icyllis.modernui.mc.text.EmojiTypeface;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/** Makes image-font runs representable by Core's ordinary TextBlob pipeline. */
@Mixin(value = EmojiFont.class, remap = false)
public abstract class MixinEmojiFont implements EmojiTypeface.Provider {
    @Shadow @Final private float mBaseSize;
    @Shadow @Final private float mBaseAscent;
    @Shadow @Final private List<String> mFiles;
    @Unique private volatile Typeface modernui$emojiTypeface;

    @Override
    public void modernui$bindEmojiResources(ResourceManager resources) {
        if (modernui$emojiTypeface != null) throw new IllegalStateException("Emoji resources already bound");
        modernui$emojiTypeface = EmojiTypeface.fromResources((EmojiFont) (Object) this,
                mBaseSize, mBaseAscent, mFiles, resources);
    }

    /** @author MOPELotus @reason Core skips image-font glyphs whose native typeface is null. */
    @Overwrite
    public Typeface getNativeTypeface() {
        return modernui$emojiTypeface;
    }
}
