// Persistent focused queue counter helpers.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Counts focused queue iterators while tolerating concurrent crawler pops. */
final class FocusedQueueCounter {

    private FocusedQueueCounter() { }

    /**
     * Count a complete queue snapshot, restarting it when a concurrent pop
     * invalidates an iterator. Never return a partial count: that could cause
     * the scheduler to overfill a profile's queue.
     *
     * @return an exact count from a completed pass, or {@code -1} when every
     *         bounded attempt raced with a queue mutation
     */
    static <T> int countSnapshot(final int streamCount, final IntFunction<Iterator<T>> streamFactory,
            final Predicate<T> include, final int maxAttempts) {
        final int attempts = Math.max(1, maxAttempts);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int count = 0;
            try {
                for (int streamIndex = 0; streamIndex < streamCount; streamIndex++) {
                    final Iterator<T> iterator = streamFactory.apply(streamIndex);
                    if (iterator == null) continue;
                    while (iterator.hasNext()) {
                        final T entry = iterator.next();
                        if (entry != null && include.test(entry)) count++;
                    }
                }
                return count;
            } catch (final NoSuchElementException | ConcurrentModificationException concurrentChange) {
                // Restart every stream so partial counts are discarded.
            }
        }
        return -1;
    }
}
