package icyllis.modernui.mc;

import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.audio.Track;
import icyllis.modernui.mc.mixin.AccessAudioManager;

import java.util.Objects;

/** Final client shutdown only; never used by sound-device/resource reload. */
final class IntegratedAudioShutdown {
    private static boolean sClosed;
    private static Throwable sFailure;

    private IntegratedAudioShutdown() {
    }

    static synchronized void close(AudioManager manager) {
        if (sFailure != null) {
            throw rethrow(sFailure);
        }
        if (sClosed) {
            return;
        }
        try {
            var access = (AccessAudioManager) (Object) manager;
            if (!access.modernui$isIntegrated()) {
                throw new IllegalStateException("Final client audio shutdown requires integrated AudioManager");
            }
            var executor = Objects.requireNonNull(access.modernui$getExecutorService());
            var tracks = Objects.requireNonNull(access.modernui$getTracks());
            Throwable failure = null;
            try {
                executor.shutdown();
            } catch (Throwable error) {
                failure = error;
            }
            Track[] snapshot;
            try {
                snapshot = tracks.toArray(new Track[0]);
            } catch (Throwable error) {
                throw rethrow(combine(failure, error));
            }
            for (Track track : snapshot) {
                try {
                    track.close();
                } catch (Throwable error) {
                    failure = combine(failure, error);
                }
            }
            if (failure != null) {
                throw rethrow(failure);
            }
            sClosed = true;
        } catch (Throwable failure) {
            sFailure = failure;
            throw rethrow(failure);
        }
    }

    static Throwable combine(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    static RuntimeException rethrow(Throwable failure) {
        IntegratedAudioShutdown.<RuntimeException>throwUnchecked(failure);
        throw new AssertionError("unreachable");
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }
}
