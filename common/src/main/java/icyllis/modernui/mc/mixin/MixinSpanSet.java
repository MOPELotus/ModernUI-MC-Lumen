package icyllis.modernui.mc.mixin;

import icyllis.modernui.text.SpanSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;

/** Preserve span boundaries when per-character styling grows beyond the initial buffer. */
@Mixin(value = SpanSet.class, remap = false)
public abstract class MixinSpanSet {
    @Shadow public int[] spanStarts;
    @Shadow public int[] spanEnds;
    @Shadow public int[] spanFlags;

    /** @author MOPELotus @reason Core 3.13.0 discards existing boundaries during expansion. */
    @Overwrite
    private void grow(int length) {
        if (spanStarts != null && spanStarts.length >= length) return;
        int capacity = spanStarts == null ? Math.max(10, length)
                : Math.max(length, spanStarts.length + (spanStarts.length >> 1));
        spanStarts = spanStarts == null ? new int[capacity] : Arrays.copyOf(spanStarts, capacity);
        spanEnds = spanEnds == null ? new int[capacity] : Arrays.copyOf(spanEnds, capacity);
        spanFlags = spanFlags == null ? new int[capacity] : Arrays.copyOf(spanFlags, capacity);
    }
}
