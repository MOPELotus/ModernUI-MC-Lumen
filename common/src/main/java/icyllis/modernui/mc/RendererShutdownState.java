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

import java.util.function.Predicate;

/** Render-thread shutdown phases; only a GPU wait known to have released nothing may be retried. */
final class RendererShutdownState {
    private boolean resourcesPrepared;
    private boolean complete;
    private boolean active;
    private Throwable failure;

    synchronized void run(Runnable prepareResources, Runnable waitAndDrain, Runnable disposeContext,
                          Predicate<Throwable> retryableWaitFailure) {
        if (failure != null) throw rethrow(failure);
        if (complete) return;
        if (active) throw new IllegalStateException("Reentrant renderer shutdown");
        active = true;
        boolean retryWait = false;
        try {
            if (!resourcesPrepared) {
                prepareResources.run();
                resourcesPrepared = true;
            }
            try {
                waitAndDrain.run();
            } catch (Throwable error) {
                retryWait = retryableWaitFailure.test(error);
                throw error;
            }
            disposeContext.run();
            complete = true;
        } catch (Throwable error) {
            if (!retryWait) failure = error;
            throw rethrow(error);
        } finally {
            active = false;
        }
    }

    private static RuntimeException rethrow(Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException(error);
    }
}
