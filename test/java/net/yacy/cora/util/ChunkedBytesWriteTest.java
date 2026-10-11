/*
 * ChunkedBytesWriteTest
 * Copyright 2026 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.cora.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.util.Random;

import org.junit.Test;

public class ChunkedBytesWriteTest {

    @Test
    public void writesEmptySmallAndMultiBufferContentsExactly() {
        assertWritten(new byte[0]);
        assertWritten(new byte[] { 1, 2, 3, 4, 5 });

        final byte[] large = new byte[256 * 1024 + 37];
        new Random(41L).nextBytes(large);
        assertWritten(large);
    }

    @Test
    public void tinyToByteArrayDoesNotAllocateAStreamingBuffer() throws Exception {
        final java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue("thread allocation measurements are unavailable",
                platformBean instanceof com.sun.management.ThreadMXBean);
        final com.sun.management.ThreadMXBean allocationBean =
                (com.sun.management.ThreadMXBean) platformBean;
        assumeTrue("thread allocation measurements are unavailable",
                allocationBean.isThreadAllocatedMemorySupported());
        try {
            if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
                allocationBean.setThreadAllocatedMemoryEnabled(true);
            }
        } catch (final SecurityException | UnsupportedOperationException e) {
            assumeNoException("cannot enable thread allocation measurements", e);
        }

        final ChunkedBytes tiny = new ChunkedBytes(new byte[] { 42 });
        final int iterations = 128;
        final byte[][] results = new byte[iterations][];
        final long threadId = Thread.currentThread().getId();
        for (int i = 0; i < 24; i++) tiny.toByteArray(); // warm the call path before measuring
        final long before = allocationBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < iterations; i++) results[i] = tiny.toByteArray();
        final long allocated = allocationBean.getThreadAllocatedBytes(threadId) - before;

        assertArrayEquals(new byte[] { 42 }, results[iterations - 1]);
        assertTrue("tiny conversion allocated " + allocated + " bytes",
                allocated < (long) iterations * 8 * 1024);
    }

    private static void assertWritten(final byte[] expected) {
        try (ChunkedBytes bytes = new ChunkedBytes(expected)) {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            bytes.writeTo(output);
            assertArrayEquals(expected, output.toByteArray());
        }
    }
}
