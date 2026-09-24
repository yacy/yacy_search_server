// SolrCommitInterruptGuard.java
// -------------------------------------
// (C) by YaCy contributors
// This file is licensed under the terms of the GNU General Public License.

package net.yacy.peers;

/**
 * Serializes search-thread cancellation with commits to the local Solr index.
 *
 * <p>Lucene may close its IndexWriter when its owning thread is interrupted
 * during a commit. Code which requests cancellation of a thread performing a
 * guarded commit must therefore use {@link #interrupt(Thread)} (or synchronize
 * on the same thread before interrupting it).</p>
 */
public final class SolrCommitInterruptGuard {

    @FunctionalInterface
    public interface CommitOperation {
        void commit();
    }

    private SolrCommitInterruptGuard() {
    }

    /**
     * Run a local Solr commit while holding the current worker's cancellation
     * monitor. An interrupt already pending on the worker aborts before the
     * commit starts.
     *
     * @param operation Solr commit operation
     * @throws InterruptedException when cancellation was requested before the
     *         worker entered the protected commit section
     */
    public static void commitIfNotInterrupted(final CommitOperation operation)
            throws InterruptedException {
        final Thread worker = Thread.currentThread();
        synchronized (worker) {
            if (Thread.interrupted()) {
                throw new InterruptedException("interrupted before local Solr commit");
            }
            operation.commit();
        }
    }

    /**
     * Request cancellation without interrupting a thread in its protected
     * Solr-commit section.
     *
     * @param worker thread to interrupt when it is still alive
     */
    public static void interrupt(final Thread worker) {
        synchronized (worker) {
            if (worker.isAlive()) {
                worker.interrupt();
            }
        }
    }
}
