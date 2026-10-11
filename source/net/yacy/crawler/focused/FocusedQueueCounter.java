// Persistent focused queue counter helpers.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** Counts focused queue iterators while tolerating concurrent crawler pops. */
final class FocusedQueueCounter {

    private FocusedQueueCounter() { }

    static final class QueueSize {
        private final int size;
        private final boolean exact;
        private final boolean unavailable;

        private QueueSize(final int size, final boolean exact, final boolean unavailable) {
            this.size = size;
            this.exact = exact;
            this.unavailable = unavailable;
        }

        int size() { return this.size; }
        boolean exact() { return this.exact; }
        boolean unavailable() { return this.unavailable; }
    }

    /**
     * Use the focused-lane aggregate for a sole enabled profile. It is an
     * upper bound that avoids walking host queues just to distinguish that
     * profile's requests. Multiple enabled profiles still require an exact
     * profile-handle count.
     */
    static QueueSize countForScheduling(final boolean soleEnabledProfile,
            final IntSupplier profileSnapshot, final IntSupplier aggregateSnapshot) {
        if (soleEnabledProfile) return new QueueSize(Math.max(0, aggregateSnapshot.getAsInt()), false, false);
        final int exact = profileSnapshot.getAsInt();
        if (exact >= 0) return new QueueSize(exact, true, false);
        return new QueueSize(Math.max(0, aggregateSnapshot.getAsInt()), false, true);
    }

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
