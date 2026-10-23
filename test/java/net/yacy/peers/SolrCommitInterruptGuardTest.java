package net.yacy.peers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class SolrCommitInterruptGuardTest {

    @Test
    public void pendingCancellationPreventsSolrCommit() {
        final AtomicBoolean committed = new AtomicBoolean();
        Thread.currentThread().interrupt();
        try {
            SolrCommitInterruptGuard.commitIfNotInterrupted(() -> committed.set(true));
        } catch (final InterruptedException expected) {
            // A pending cancellation must be consumed before any Lucene call.
        } finally {
            // Do not leak the intentionally set interrupt flag into the test runner.
            Thread.interrupted();
        }
        assertFalse(committed.get());
    }

    @Test
    public void cancellationWaitsUntilProtectedSolrCommitFinishes() throws Exception {
        final CountDownLatch commitStarted = new CountDownLatch(1);
        final CountDownLatch allowCommitToFinish = new CountDownLatch(1);
        final CountDownLatch cancellationFinished = new CountDownLatch(1);
        final AtomicBoolean commitFinished = new AtomicBoolean();
        final AtomicBoolean interruptedAfterCommit = new AtomicBoolean();
        final AtomicReference<Throwable> workerFailure = new AtomicReference<>();

        final Thread worker = new Thread(() -> {
            try {
                SolrCommitInterruptGuard.commitIfNotInterrupted(() -> {
                    commitStarted.countDown();
                    try {
                        allowCommitToFinish.await();
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException("commit was interrupted", e);
                    }
                    commitFinished.set(true);
                });
                try {
                    new CountDownLatch(1).await();
                } catch (final InterruptedException expected) {
                    interruptedAfterCommit.set(commitFinished.get());
                }
            } catch (final Throwable e) {
                workerFailure.set(e);
            }
        }, "solr-commit-guard-test-worker");
        worker.setDaemon(true);
        worker.start();

        final Thread canceller = new Thread(() -> {
            try {
                SolrCommitInterruptGuard.interrupt(worker);
            } finally {
                cancellationFinished.countDown();
            }
        }, "solr-commit-guard-test-canceller");
        try {
            assertTrue("commit did not start", commitStarted.await(5, TimeUnit.SECONDS));
            canceller.start();
            assertFalse("cancellation interrupted an in-progress commit",
                    cancellationFinished.await(100, TimeUnit.MILLISECONDS));
        } finally {
            allowCommitToFinish.countDown();
        }

        assertTrue("cancellation did not complete", cancellationFinished.await(5, TimeUnit.SECONDS));
        worker.join(5000);
        canceller.join(5000);
        assertFalse("worker did not stop after cancellation", worker.isAlive());
        assertFalse("canceller did not stop", canceller.isAlive());
        assertTrue("commit did not finish before cancellation", commitFinished.get());
        assertTrue("worker was not interrupted after the commit", interruptedAfterCommit.get());
        if (workerFailure.get() != null) {
            throw new AssertionError("worker failed", workerFailure.get());
        }
    }
}
