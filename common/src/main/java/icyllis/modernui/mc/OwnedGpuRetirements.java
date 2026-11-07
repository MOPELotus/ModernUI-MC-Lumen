package icyllis.modernui.mc;

import java.util.LinkedHashSet;
import java.util.Objects;

/** Tracks only Arc3D releases submitted by this bridge, never the host renderer's queue. */
public final class OwnedGpuRetirements {
    /** A failed wait forces no pending release; retry without repeating resource teardown. */
    public static final class GpuWaitFailure extends IllegalStateException {
        private GpuWaitFailure(Throwable cause) {
            super("Could not finish GPU work before releasing Lumen resources", cause);
        }
    }

    private final LinkedHashSet<Entry> pending = new LinkedHashSet<>();
    private boolean closed;
    private int executing;

    public synchronized Runnable track(Runnable release) {
        Objects.requireNonNull(release, "release");
        if (closed) throw new IllegalStateException("Lumen GPU retirement queue is closed");
        Entry entry = new Entry(release);
        pending.add(entry);
        return entry;
    }

    /** UI producers must have stopped before calling this method. A failed GPU wait is retryable. */
    public synchronized void close(Runnable waitForGpu) {
        if (closed) return;
        if (executing != 0) {
            throw new IllegalStateException("Cannot shut down the GPU queue from a release callback");
        }
        // Holding this monitor also waits for an already-running frame callback to finish.
        // In particular, an entry claimed by another thread must not make shutdown return early.
        Objects.requireNonNull(waitForGpu, "waitForGpu");
        try {
            waitForGpu.run();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable waitFailure) {
            throw new GpuWaitFailure(waitFailure);
        }
        Throwable failure = null;
        while (!pending.isEmpty()) {
            try {
                pending.iterator().next().run();
            } catch (Throwable error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        closed = true;
        if (failure != null) throw new IllegalStateException("Failed to release Lumen GPU resources", failure);
    }

    private final class Entry implements Runnable {
        private Runnable release;
        private boolean done;

        private Entry(Runnable release) { this.release = release; }

        @Override
        public void run() {
            synchronized (OwnedGpuRetirements.this) {
                if (done) return;
                done = true;
                pending.remove(this);
                Runnable action = release;
                release = null;
                executing++;
                try { action.run(); }
                finally { executing--; }
            }
        }
    }
}
