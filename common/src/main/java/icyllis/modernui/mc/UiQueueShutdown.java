/*
 * Modern UI.
 * Copyright (C) 2019-2026 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/** Completes frame cancellation before the UI message queue starts its final drain. */
final class UiQueueShutdown {
    private UiQueueShutdown() {}

    static Runnable wrap(Runnable unscheduleTraversal, IntConsumer cancelFramePhase,
                         int[] framePhases, Runnable quitQueue) {
        int[] phases = framePhases.clone();
        AtomicBoolean finished = new AtomicBoolean();
        return () -> {
            if (!finished.compareAndSet(false, true)) return;
            Throwable failure = null;
            try {
                unscheduleTraversal.run();
            } catch (Throwable error) {
                failure = error;
            }
            for (int phase : phases) {
                try {
                    cancelFramePhase.accept(phase);
                } catch (Throwable error) {
                    failure = combine(failure, error);
                }
            }
            try {
                quitQueue.run();
            } catch (Throwable error) {
                failure = combine(failure, error);
            }
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
            if (failure != null) throw new IllegalStateException(failure);
        };
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
}
