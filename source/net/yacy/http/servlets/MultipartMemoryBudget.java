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

    private static final long MIN_HEAP_HEADROOM = 512L * 1024L * 1024L;
    private static final long HEADROOM_FRACTION_DENOMINATOR = 4L;
    private static final long REQUEST_MEMORY_MULTIPLIER = 3L;

    private MultipartMemoryBudget() { }

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
