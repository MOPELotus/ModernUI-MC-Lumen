package icyllis.modernui.mc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Standalone regression check for queue-drain ordering; no renderer or Minecraft required. */
public final class UiQueueShutdownCheck {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[] phases = {0, 1, 2, 3};
        boolean[] queued = {true, true, true, true};
        int[] deadQueuePosts = {0};
        List<Integer> order = new ArrayList<>();
        Runnable finish = UiQueueShutdown.wrap(() -> order.add(-1), phase -> {
            queued[phase] = false;
            order.add(phase);
        }, phases, () -> {
            order.add(4);
            // A due traversal can invalidate fading scrollbars while quitSafely drains messages.
            for (boolean callback : queued) if (callback) deadQueuePosts[0]++;
        });
        phases[0] = 99; // Posting a finalizer must take a snapshot of its phases.
        finish.run();
        check(order.equals(List.of(-1, 0, 1, 2, 3, 4)), "unschedule barrier and all four phases before quit");
        check(deadQueuePosts[0] == 0, "no due callback can enqueue work into the stopped queue");
        finish.run();
        check(order.size() == 6, "duplicate finish cannot quit or cancel a second time");

        // Negative control: animation-only cancellation leaves a due traversal behind.
        boolean[] oldCallbacks = {true, true, true, true};
        oldCallbacks[1] = false;
        int survivors = 0;
        for (boolean queuedCallback : oldCallbacks) if (queuedCallback) survivors++;
        check(survivors == 3, "animation-only cleanup does not address the regression");

        List<Integer> attempts = new ArrayList<>();
        RuntimeException first = new IllegalStateException("unschedule failure");
        RuntimeException later = new IllegalArgumentException("animation cleanup failure");
        int[] quits = {0};
        Runnable broken = UiQueueShutdown.wrap(() -> { throw first; }, phase -> {
            attempts.add(phase);
            if (phase == 1) throw later;
        }, new int[]{0, 1, 2, 3}, () -> quits[0]++);
        try {
            broken.run();
            throw new AssertionError("cleanup failure must remain observable");
        } catch (RuntimeException actual) {
            check(actual == first, "first cleanup failure preserved");
            check(Arrays.asList(actual.getSuppressed()).contains(later), "later failure retained");
        }
        check(attempts.equals(List.of(0, 1, 2, 3)), "one phase failure does not skip the remaining phases");
        check(quits[0] == 1, "queue quit is attempted despite cancellation failures");
        broken.run();
        check(quits[0] == 1, "failed finalization is still idempotent");

        Error shared = new AssertionError("same failure returned by multiple cleanup hooks");
        Runnable repeated = UiQueueShutdown.wrap(() -> { throw shared; }, phase -> { throw shared; },
                new int[]{0, 1, 2, 3}, () -> { throw shared; });
        try {
            repeated.run();
            throw new AssertionError("Error must be preserved");
        } catch (Error actual) {
            check(actual == shared, "shared throwable is not self-suppressed or replaced");
        }
        System.out.println("PASS UiQueueShutdown: " + checks + " lifecycle assertions");
    }
}
