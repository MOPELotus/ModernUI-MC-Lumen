package icyllis.modernui.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone regression checks using the production queue, no Minecraft or GPU stubs. */
public final class OwnedGpuRetirementsCheck {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static <T extends Throwable> T expect(Class<T> type, Runnable call) {
        try { call.run(); }
        catch (Throwable failure) {
            check(type.isInstance(failure), "Unexpected exception: " + failure);
            return type.cast(failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
    public static void main(String[] args) throws Exception {
        normalFrameAndShutdown();
        failedWaitRetainsOwnership();
        releaseFailuresDoNotSkipOthers();
        inFlightCallbackFinishesBeforeShutdown();
        concurrentFrameAndShutdown();
        rejectReentrantShutdown();
        System.out.println("PASS: " + checks + " GPU retirement assertions");
    }
    private static void normalFrameAndShutdown() {
        var q = new OwnedGpuRetirements();
        var events = new ArrayList<String>();
        Runnable first = q.track(() -> events.add("first"));
        Runnable second = q.track(() -> events.add("second"));
        var otherModCalls = new AtomicInteger();
        List<Runnable> hostQueue = List.of(first, second, otherModCalls::incrementAndGet);
        first.run();
        q.close(() -> events.add("idle"));
        events.add("allocator-close");
        check(events.equals(List.of("first", "idle", "second", "allocator-close")), "Release/wait/allocator order");
        check(otherModCalls.get() == 0, "Shutdown touched the host's unrelated callback");
        hostQueue.forEach(Runnable::run);
        check(otherModCalls.get() == 1 && events.size() == 4, "Host callbacks must remain valid, owned callbacks once only");
        q.close(() -> { throw new AssertionError("Wait after successful close"); });
        expect(IllegalStateException.class, () -> q.track(() -> {}));
    }
    private static void failedWaitRetainsOwnership() {
        var q = new OwnedGpuRetirements();
        var releases = new AtomicInteger();
        Runnable first = q.track(releases::incrementAndGet);
        var waitFailure = expect(OwnedGpuRetirements.GpuWaitFailure.class,
                () -> q.close(() -> { throw new IllegalStateException("wait failed"); }));
        check(waitFailure.getCause().getMessage().equals("wait failed"), "GPU wait cause was lost");
        check(releases.get() == 0, "Failed wait ran a release");
        q.track(releases::incrementAndGet);
        q.close(() -> {}); first.run();
        check(releases.get() == 2, "Failed wait lost pending callbacks or retry double-released");
    }
    private static void releaseFailuresDoNotSkipOthers() {
        var q = new OwnedGpuRetirements();
        var releases = new AtomicInteger();
        var firstFailure = new IllegalArgumentException("first failure");
        var secondFailure = new IllegalArgumentException("second failure");
        Runnable first = q.track(() -> { throw firstFailure; });
        q.track(() -> q.track(releases::incrementAndGet));
        q.track(() -> { throw secondFailure; });
        var failure = expect(IllegalStateException.class, () -> q.close(() -> {}));
        check(!(failure instanceof OwnedGpuRetirements.GpuWaitFailure), "Release failure misclassified as retryable wait");
        check(failure.getCause() == firstFailure && firstFailure.getSuppressed().length == 1 &&
                firstFailure.getSuppressed()[0] == secondFailure, "Release failures not preserved");
        check(releases.get() == 1, "Failure skipped nested or later release");
        first.run(); q.close(() -> { throw new AssertionError("Closed queue waited again"); });
    }
    private static void inFlightCallbackFinishesBeforeShutdown() throws Exception {
        var q = new OwnedGpuRetirements();
        var entered = new CountDownLatch(1);
        var allowFinish = new CountDownLatch(1);
        var closing = new CountDownLatch(1);
        var waited = new CountDownLatch(1);
        var events = new CopyOnWriteArrayList<String>();
        Runnable callback = q.track(() -> {
            events.add("release-start"); entered.countDown();
            try {
                if (!allowFinish.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test callback timed out");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            events.add("release-end");
        });
        try (var threads = Executors.newFixedThreadPool(2)) {
            var frame = threads.submit(callback);
            check(entered.await(5, TimeUnit.SECONDS), "Frame callback did not start");
            var shutdown = threads.submit(() -> { closing.countDown(); q.close(() -> {
                events.add("gpu-idle"); waited.countDown();
            }); events.add("allocator-close"); });
            check(closing.await(5, TimeUnit.SECONDS), "Shutdown did not start");
            check(!waited.await(150, TimeUnit.MILLISECONDS) && !shutdown.isDone(), "Shutdown overtook an already-claimed release");
            allowFinish.countDown(); frame.get(5, TimeUnit.SECONDS); shutdown.get(5, TimeUnit.SECONDS);
            check(events.equals(List.of("release-start", "release-end", "gpu-idle", "allocator-close")), "In-flight callback still executing at allocator close");
        } finally { allowFinish.countDown(); }
    }
    private static void concurrentFrameAndShutdown() throws Exception {
        for (int i = 0; i < 40; i++) {
            var q = new OwnedGpuRetirements();
            var calls = new AtomicInteger();
            var waits = new AtomicInteger();
            var start = new CountDownLatch(1);
            Runnable action = q.track(calls::incrementAndGet);
            try (var threads = Executors.newFixedThreadPool(2)) {
                var frame = threads.submit(() -> { start.await(); action.run(); return null; });
                var shutdown = threads.submit(() -> { start.await(); q.close(waits::incrementAndGet); return null; });
                start.countDown(); frame.get(5, TimeUnit.SECONDS); shutdown.get(5, TimeUnit.SECONDS);
            }
            action.run();
            check(calls.get() == 1 && waits.get() == 1, "Concurrent completion double-released or skipped wait");
        }
    }
    private static void rejectReentrantShutdown() {
        var q = new OwnedGpuRetirements();
        Runnable action = q.track(() -> expect(IllegalStateException.class, () -> q.close(() -> {})));
        action.run(); q.close(() -> {});
    }
}
