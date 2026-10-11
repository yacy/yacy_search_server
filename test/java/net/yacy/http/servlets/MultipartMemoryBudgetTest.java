package net.yacy.http.servlets;

import static org.junit.Assert.assertEquals;

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
}
