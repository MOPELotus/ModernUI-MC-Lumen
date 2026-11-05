package icyllis.modernui.mc.mixin;

import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.audio.FFT;
import icyllis.modernui.audio.SoundSample;
import icyllis.modernui.audio.Track;
import icyllis.modernui.mc.BackgroundMusicTrack;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ShortBuffer;
import java.util.function.Consumer;

/** Core audio resources share this Track's monitor, including exceptional exits. */
@Mixin(value = Track.class, remap = false)
public abstract class MixinAudioTrack implements BackgroundMusicTrack {
    @Shadow private int mSource;
    @Shadow @Final private SoundSample mSample;
    @Shadow private int[] mBuffers;
    @Shadow private ShortBuffer mClientBuffer;
    @Shadow private int mClientState;
    @Shadow private FFT mFFT;
    @Shadow private Consumer<FFT> mFFTCallback;
    @Shadow private float[] mMixedSamples;
    @Shadow private int mMixedSampleCount;

    @Unique private boolean modernui$closed;
    @Unique private Throwable modernui$closeFailure;

    @Override
    public synchronized void modernui$configureBackgroundPlayback() {
        if (modernui$closed || mSource == 0) return;
        AL11.alSourcei(mSource, AL11.AL_SOURCE_RELATIVE, AL11.AL_TRUE);
        AL11.alSource3f(mSource, AL11.AL_POSITION, 0f, 0f, 0f);
    }

    /** @author MOPELotus @reason Release source ownership before buffers, once per Track. */
    @Overwrite
    public void close() {
        // MixinConfigPlugin makes this and every original resource entry synchronized.
        if (modernui$closed) {
            if (modernui$closeFailure != null) {
                MixinAudioTrack.<RuntimeException>modernui$rethrow(modernui$closeFailure);
            }
            return;
        }
        modernui$closed = true;
        mClientState = 0;
        int source = mSource;
        int[] buffers = mBuffers;
        ShortBuffer clientBuffer = mClientBuffer;
        mFFT = null;
        mFFTCallback = null;
        mMixedSamples = null;
        mMixedSampleCount = 0;

        Throwable failure = null;
        try {
            AudioManager.getInstance().removeTrack((Track) (Object) this);
        } catch (Throwable thrown) {
            failure = thrown;
        }
        boolean sourceReleased = source == 0;
        if (source != 0) {
            try {
                AL11.alDeleteSources(source);
                mSource = 0;
                sourceReleased = true;
            } catch (Throwable thrown) {
                failure = modernui$recordFailure(failure, thrown);
            }
        }
        // A Java exception leaves source ownership uncertain: do not delete attached buffers.
        if (sourceReleased && buffers != null) {
            try {
                AL11.alDeleteBuffers(buffers);
                mBuffers = null;
            } catch (Throwable thrown) {
                failure = modernui$recordFailure(failure, thrown);
            }
        }
        try {
            MemoryUtil.memFree(clientBuffer);
            mClientBuffer = null;
        } catch (Throwable thrown) {
            failure = modernui$recordFailure(failure, thrown);
        }
        try {
            mSample.close();
        } catch (Throwable thrown) {
            failure = modernui$recordFailure(failure, thrown);
        }
        modernui$closeFailure = failure;
        if (failure != null) MixinAudioTrack.<RuntimeException>modernui$rethrow(failure);
    }

    @Unique
    private static Throwable modernui$recordFailure(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void modernui$rethrow(Throwable failure) throws T {
        throw (T) failure;
    }

    @Inject(method = {"play()V", "pause()V", "tick()V", "setPosition(FFF)V", "setGain(F)V",
            "setAnalyzer(Licyllis/modernui/audio/FFT;Ljava/util/function/Consumer;)V"},
            at = @At("HEAD"), cancellable = true)
    private void modernui$skipClosedOperation(CallbackInfo ci) {
        if (modernui$closed || mSource == 0) ci.cancel();
    }

    @Inject(method = {"isPlaying()Z", "seek(I)Z", "seekToSeconds(F)Z"},
            at = @At("HEAD"), cancellable = true)
    private void modernui$closedFalse(CallbackInfoReturnable<Boolean> ci) {
        if (modernui$closed || mSource == 0) ci.setReturnValue(false);
    }

    @Inject(method = "getTime()F", at = @At("HEAD"), cancellable = true)
    private void modernui$closedFloatZero(CallbackInfoReturnable<Float> ci) {
        if (modernui$closed || mSource == 0) ci.setReturnValue(0.0f);
    }

}
