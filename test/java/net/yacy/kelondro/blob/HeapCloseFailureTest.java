/*
 *  HeapCloseFailureTest
 *  Copyright 2026 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.kelondro.blob;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.kelondro.io.Writer;

public class HeapCloseFailureTest {

    private static final int KEY_LENGTH = 12;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void closePropagatesFlushFailureWithoutPublishingStaleIndex() throws Exception {
        final File directory = this.temporaryFolder.newFolder("heap-close-failure");
        final File heapFile = new File(directory, "data.blob");
        final Heap heap = new Heap(
                heapFile, KEY_LENGTH, NaturalOrder.naturalOrder, 4096);

        for (int index = 0; index < 5; index++) {
            heap.insert(key(index), ASCII.getBytes("buffered-value-" + index));
        }

        /*
         * Keep length(), seek() and close() operational, but fail the bulk write.
         * At that point flushBufferInternal() has already assigned offsets to all
         * buffered keys, so close(true) must not publish this stale RAM index.
         */
        final Writer delegate = heap.file;
        heap.file = (Writer) Proxy.newProxyInstance(
                Writer.class.getClassLoader(),
                new Class<?>[] {Writer.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("write")) {
                        throw new IOException("forced buffered write failure");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (final InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });

        try {
            heap.close(true);
            throw new AssertionError("flush failure was swallowed");
        } catch (final UncheckedIOException expected) {
            assertTrue(expected.getMessage().contains(heapFile.getName()));
            assertEquals("forced buffered write failure",
                    expected.getCause().getMessage());
        } finally {
            heap.close(false);
        }

        assertFalse(hasFingerprintFile(directory));

        /* No stale offsets may survive: reopening must scan the authoritative blob. */
        final Heap reopened = new Heap(
                heapFile, KEY_LENGTH, NaturalOrder.naturalOrder, 4096);
        try {
            assertEquals(0, reopened.size());
        } finally {
            reopened.close(false);
        }
    }

    private static byte[] key(final int index) {
        return ASCII.getBytes(String.format("%012d", Integer.valueOf(index)));
    }

    private static boolean hasFingerprintFile(final File directory) {
        final File[] fingerprints = directory.listFiles(file -> {
            final String name = file.getName();
            return name.endsWith(".idx") || name.endsWith(".gap")
                    || name.endsWith(".idx.gz") || name.endsWith(".gap.gz");
        });
        return fingerprints != null && fingerprints.length > 0;
    }
}
