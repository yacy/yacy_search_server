package net.yacy.http.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MultipartMemoryBudgetTest {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    @Test
    public void requestEstimateUsesLongArithmeticAtIntOverflowBoundary() {
        final long upload = 800L * MIB;
        assertEquals(2400L * MIB, MultipartMemoryBudget.requiredHeapBytes(upload));
    }

    @Test
    public void requestEstimateSaturatesInsteadOfOverflowingLong() {
        assertEquals(Long.MAX_VALUE, MultipartMemoryBudget.requiredHeapBytes(Long.MAX_VALUE));
    }

    @Test
    public void availableHeapLeavesTheCrawlerReserveAndCapsLargeRequests() {
        assertEquals(0L, MultipartMemoryBudget.maximumRequestBytes(GIB, 4L * GIB));
        assertEquals(GIB / 3L, MultipartMemoryBudget.maximumRequestBytes(2L * GIB, 4L * GIB));
        assertEquals(GIB, MultipartMemoryBudget.maximumRequestBytes(4L * GIB, 4L * GIB));
        assertEquals(GIB, MultipartMemoryBudget.maximumRequestBytes(8L * GIB, 8L * GIB));
    }

    @Test
    public void concurrentRequestsShareAndReleaseTheAggregateHeapBudget() {
        final long upload = 128L * MIB;
        final MultipartMemoryBudget.Reservation first = MultipartMemoryBudget.tryReserve(
                upload, 2L * GIB, 4L * GIB);
        assertNotNull(first);
        try {
            assertEquals(3L * upload, MultipartMemoryBudget.reservedHeapBytes());
            final MultipartMemoryBudget.Reservation second = MultipartMemoryBudget.tryReserve(
                    upload, 2L * GIB, 4L * GIB);
            assertNotNull(second);
            try {
                assertEquals(6L * upload, MultipartMemoryBudget.reservedHeapBytes());
                assertNull(MultipartMemoryBudget.tryReserve(upload, 2L * GIB, 4L * GIB));
            } finally {
                second.close();
                second.close();
            }
            assertEquals(3L * upload, MultipartMemoryBudget.reservedHeapBytes());
        } finally {
            first.close();
        }
        assertEquals(0L, MultipartMemoryBudget.reservedHeapBytes());
    }

    @Test
    public void unknownLengthRequestReservesItsWholeSafeAllowance() {
        final MultipartMemoryBudget.Reservation upload = MultipartMemoryBudget.tryReserve(
                -1L, 2L * GIB, 4L * GIB);
        assertNotNull(upload);
        try {
            assertEquals(GIB / 3L, upload.maximumRequestBytes());
            assertEquals(3L * (GIB / 3L), MultipartMemoryBudget.reservedHeapBytes());
            assertNull(MultipartMemoryBudget.tryReserve(1L * MIB, 2L * GIB, 4L * GIB));
        } finally {
            upload.close();
        }
        assertEquals(0L, MultipartMemoryBudget.reservedHeapBytes());
    }
}
