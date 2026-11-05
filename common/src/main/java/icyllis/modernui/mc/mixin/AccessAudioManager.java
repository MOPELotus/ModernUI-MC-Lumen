package icyllis.modernui.mc.mixin;

import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.audio.Track;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

/** Exact Core fields used only during final, integrated client shutdown. */
@Mixin(value = AudioManager.class, remap = false)
public interface AccessAudioManager {
    @Accessor("mExecutorService")
    ScheduledExecutorService modernui$getExecutorService();

    @Accessor("mTracks")
    CopyOnWriteArrayList<Track> modernui$getTracks();

    @Accessor("mIntegrated")
    boolean modernui$isIntegrated();
}
