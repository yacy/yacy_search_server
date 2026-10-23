// Bounded worker reuse for concurrent search-result snippet fetches.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.search.query;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reuses a bounded set of workers for asynchronous snippet fetches. When all
 * workers are busy, the submitting search thread performs the work itself,
 * applying back-pressure instead of creating an unbounded number of threads.
 */
final class SearchSnippetExecutor extends ThreadPoolExecutor {

    private static final int MAX_THREADS = 16;
    private static final long IDLE_TIMEOUT_SECONDS = 30L;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    static final SearchSnippetExecutor SHARED = new SearchSnippetExecutor(MAX_THREADS, task -> {
        final Thread thread = new Thread(task, "SearchEvent.snippetFetch-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    SearchSnippetExecutor(final int maximumThreads, final ThreadFactory threadFactory) {
        super(0, maximumThreads, IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(), threadFactory, new CallerRunsPolicy());
    }
}
