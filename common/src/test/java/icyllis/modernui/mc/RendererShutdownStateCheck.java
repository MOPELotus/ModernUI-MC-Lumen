package icyllis.modernui.mc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Failure-injection regression test for the production renderer shutdown phases. */
public final class RendererShutdownStateCheck {
    private static int checks;
    private static final class WaitFailure extends IllegalStateException {}

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        RendererShutdownState retry = new RendererShutdownState();
        int[] calls = {0, 0, 0};
        WaitFailure waiting = new WaitFailure();
        try {
            retry.run(() -> calls[0]++, () -> { calls[1]++; throw waiting; }, () -> calls[2]++,
                    error -> error instanceof WaitFailure);
            throw new AssertionError("wait failure must be visible");
        } catch (WaitFailure actual) {
            check(actual == waiting, "original GPU wait failure preserved");
        }
        check(calls[0] == 1 && calls[1] == 1 && calls[2] == 0, "failed GPU wait leaves context owned");
        retry.run(() -> calls[0]++, () -> calls[1]++, () -> calls[2]++, error -> error instanceof WaitFailure);
        check(calls[0] == 1, "retry does not close textures/fonts twice");
        check(calls[1] == 2 && calls[2] == 1, "retry drains GPU before disposing context exactly once");
        retry.run(() -> calls[0]++, () -> calls[1]++, () -> calls[2]++, error -> true);
        check(calls[0] == 1 && calls[1] == 2 && calls[2] == 1, "successful shutdown is idempotent");

        for (int failureStage = 0; failureStage < 3; failureStage++) {
            RendererShutdownState sticky = new RendererShutdownState();
            int[] invocations = {0, 0, 0};
            int phase = failureStage;
            // The same marker is only retryable when it actually comes from the wait phase.
            RuntimeException failure = phase == 1 ? new IllegalStateException("callback release failed") : new WaitFailure();
            Runnable[] tasks = new Runnable[3];
            for (int i = 0; i < 3; i++) {
                int index = i;
                tasks[i] = () -> { invocations[index]++; if (index == phase) throw failure; };
            }
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    sticky.run(tasks[0], tasks[1], tasks[2], error -> error instanceof WaitFailure);
                    throw new AssertionError("partial cleanup error must remain sticky");
                } catch (RuntimeException actual) {
                    check(actual == failure, "partial failure is rethrown without replaying resource destruction");
                }
            }
            for (int i = 0; i < 3; i++) {
                check(invocations[i] == (i <= phase ? 1 : 0), "partial cleanup never invokes a phase twice");
            }
        }

        RendererShutdownState concurrent = new RendererShutdownState();
        CountDownLatch insideWait = new CountDownLatch(1);
        CountDownLatch releaseWait = new CountDownLatch(1);
        AtomicInteger prepares = new AtomicInteger();
        AtomicInteger waits = new AtomicInteger();
        AtomicInteger disposals = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable enter = () -> {
            try {
                concurrent.run(prepares::incrementAndGet, () -> {
                    waits.incrementAndGet(); insideWait.countDown();
                    try { releaseWait.await(); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
                    }
                }, disposals::incrementAndGet, ignored -> false);
            } catch (Throwable failure) { error.set(failure); }
        };
        Thread first = new Thread(enter);
        Thread second = new Thread(enter);
        first.start(); insideWait.await(); second.start(); releaseWait.countDown();
        first.join(2000); second.join(2000);
        check(!first.isAlive() && !second.isAlive(), "concurrent shutdown callers complete");
        check(error.get() == null, "concurrent repeat does not enter incomplete cleanup");
        check(prepares.get() == 1 && waits.get() == 1 && disposals.get() == 1,
                "concurrent callers cannot destroy the same renderer twice");
        System.out.println("PASS RendererShutdownState: " + checks + " phase assertions");
    }
}
