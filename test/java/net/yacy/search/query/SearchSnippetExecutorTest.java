package net.yacy.search.query;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

/** Regression coverage for bounded snippet-fetch worker creation. */
public class SearchSnippetExecutorTest {

    @Test
    public void runsOverloadInSubmittingThreadAtWorkerLimit() throws Exception {
        final AtomicInteger workersCreated = new AtomicInteger();
        final SearchSnippetExecutor executor = new SearchSnippetExecutor(2, task -> {
            workersCreated.incrementAndGet();
            final Thread worker = new Thread(task, "snippet-test-worker");
            worker.setDaemon(true);
            return worker;
        });
        final CountDownLatch workersStarted = new CountDownLatch(2);
        final CountDownLatch callerStarted = new CountDownLatch(1);
        final CountDownLatch releaseTasks = new CountDownLatch(1);
        final AtomicReference<Thread> callerThread = new AtomicReference<>();
        final Runnable blockingTask = () -> {
            workersStarted.countDown();
            await(releaseTasks);
        };
        final Thread submitter = new Thread(() -> executor.execute(() -> {
            callerThread.set(Thread.currentThread());
            callerStarted.countDown();
            await(releaseTasks);
        }), "snippet-test-submitter");

        try {
            executor.execute(blockingTask);
            executor.execute(blockingTask);
            Assert.assertTrue("worker tasks did not start", workersStarted.await(2, TimeUnit.SECONDS));
            Assert.assertEquals(2, workersCreated.get());

            submitter.start();
            Assert.assertTrue("overload task did not run", callerStarted.await(2, TimeUnit.SECONDS));
            Assert.assertSame("overload must run on the submitting thread", submitter, callerThread.get());
            Assert.assertEquals("worker creation exceeded the configured bound", 2, workersCreated.get());
        } finally {
            releaseTasks.countDown();
            submitter.join(2000);
            executor.shutdown();
            Assert.assertTrue("snippet workers did not terminate", executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
