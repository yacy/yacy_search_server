// Multipart request memory budget.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.http.servlets;

/**
 * Bounds multipart bodies before parsing. File uploads are copied into
 * heap-backed request values, and some handlers make an additional byte-array
 * copy, so reserve three times the request size and retain crawler/search heap
 * headroom while parsing.
 */
final class MultipartMemoryBudget {

    static final long MAX_REQUEST_BYTES = 1024L * 1024L * 1024L;
    static final String REQUEST_ATTRIBUTE = MultipartMemoryBudget.class.getName() + ".reservation";

    private static final long MIN_HEAP_HEADROOM = 512L * 1024L * 1024L;
    private static final long HEADROOM_FRACTION_DENOMINATOR = 4L;
    private static final long REQUEST_MEMORY_MULTIPLIER = 3L;
    private static long reservedHeapBytes;

    static final class Reservation implements AutoCloseable {
        private final long maximumRequestBytes;
        private final long reservedBytes;
        private boolean closed;

        private Reservation(final long maximumRequestBytes, final long reservedBytes) {
            this.maximumRequestBytes = maximumRequestBytes;
            this.reservedBytes = reservedBytes;
        }

        long maximumRequestBytes() {
            return this.maximumRequestBytes;
        }

        @Override
        public void close() {
            synchronized (MultipartMemoryBudget.class) {
                if (this.closed) return;
                reservedHeapBytes -= this.reservedBytes;
                this.closed = true;
            }
        }
    }

    private MultipartMemoryBudget() { }

    /**
     * Atomically account for the heap estimate for a request across all
     * concurrent multipart requests. MemoryControl.request() is an observer,
     * not a reservation, so per-request checks alone can all admit the same
     * currently free heap.
     *
     * @param contentLength known request length, or a negative value when the
     *        request is streamed without Content-Length
     * @param availableHeap currently available JVM heap
     * @param maximumHeap maximum JVM heap
     * @return a reservation to close after the entire servlet request, or
     *         {@code null} when the aggregate budget cannot admit it
     */
    static synchronized Reservation tryReserve(final long contentLength,
            final long availableHeap, final long maximumHeap) {
        if (contentLength > MAX_REQUEST_BYTES) return null;
        final long unreservedAvailable = availableHeap <= reservedHeapBytes
                ? 0L : availableHeap - reservedHeapBytes;
        final long maximum = maximumRequestBytes(unreservedAvailable, maximumHeap);
        if (maximum <= 0L) return null;
        final long requestBytes;
        final long requiredHeap;
        if (contentLength > 0L) {
            if (contentLength > maximum) return null;
            requestBytes = contentLength;
            requiredHeap = requiredHeapBytes(contentLength);
        } else {
            // With no declared length, reserve the entire currently safe
            // request allowance and enforce that same cap while parsing.
            requestBytes = maximum;
            requiredHeap = requiredHeapBytes(maximum);
        }
        if (requiredHeap <= 0L || requiredHeap == Long.MAX_VALUE
                || reservedHeapBytes > Long.MAX_VALUE - requiredHeap) return null;
        reservedHeapBytes += requiredHeap;
        return new Reservation(requestBytes, requiredHeap);
    }

    static synchronized long reservedHeapBytes() {
        return reservedHeapBytes;
    }

    static long requiredHeapBytes(final long contentLength) {
        if (contentLength <= 0L) return 0L;
        if (contentLength > Long.MAX_VALUE / REQUEST_MEMORY_MULTIPLIER) return Long.MAX_VALUE;
        return contentLength * REQUEST_MEMORY_MULTIPLIER;
    }

    static long maximumRequestBytes(final long availableHeap, final long maximumHeap) {
        final long reserve = Math.max(MIN_HEAP_HEADROOM,
                maximumHeap > 0L ? maximumHeap / HEADROOM_FRACTION_DENOMINATOR : MIN_HEAP_HEADROOM);
        final long allocatable = Math.max(0L, availableHeap - reserve);
        return Math.min(MAX_REQUEST_BYTES, allocatable / REQUEST_MEMORY_MULTIPLIER);
    }
}
