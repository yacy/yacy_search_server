/*
 *  ChunkedBytesTest
 *  Copyright 2026 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.cora.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ChunkedBytesTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void inputStreamHonorsZeroLengthAndNegativeSkipContracts() throws Exception {
        try (ChunkedBytes empty = new ChunkedBytes();
                InputStream input = empty.openStream()) {
            assertEquals(0, input.read(new byte[1], 0, 0));
        }

        try (ChunkedBytes bytes = new ChunkedBytes(new byte[] {1, 2, 3});
                InputStream input = bytes.openStream()) {
            assertEquals(0L, input.skip(-1L));
            assertEquals(1, input.read());
            input.close();
            assertThrows(IOException.class, input::read);
        }
    }

    @Test
    public void validatesRangesBeforeReadingOrWriting() {
        try (ChunkedBytes bytes = new ChunkedBytes(new byte[] {1, 2, 3})) {
            assertEquals(0, bytes.read(bytes.size(), new byte[0], 0, 0));
            assertEquals(0, bytes.write(bytes.size(), new byte[0], 0, 0));
            assertThrows(NullPointerException.class,
                    () -> bytes.read(bytes.size(), null, 0, 1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> bytes.read(0, new byte[1], 1, 1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> bytes.write(0, new byte[1], 0, -1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> bytes.append(new byte[1], Integer.MAX_VALUE, 1));
        }
    }

    @Test
    public void equalContentHasEqualHashCodeAndExplicitForeignComparisons() {
        try (ChunkedBytes left = new ChunkedBytes(new byte[] {1, 2, 3});
                ChunkedBytes right = new ChunkedBytes(new byte[] {1, 2, 3})) {
            assertEquals(left, right);
            assertEquals(left.hashCode(), right.hashCode());
            assertFalse(left.equals(new byte[] {1, 2, 3}));
            assertFalse(left.equals("\u0001\u0002\u0003"));
            assertTrue(left.contentEquals(new byte[] {1, 2, 3}));
            assertTrue(left.contentEquals("\u0001\u0002\u0003"));
        }
    }

    @Test
    public void closeIsIdempotentAndRejectsFurtherAccess() {
        final ChunkedBytes bytes = new ChunkedBytes(new byte[] {1});
        bytes.close();
        bytes.close();

        assertThrows(IllegalStateException.class, bytes::size);
        assertThrows(IllegalStateException.class, () -> bytes.get(0));
        assertThrows(IllegalStateException.class, () -> bytes.append(new byte[] {2}));
        assertThrows(IllegalStateException.class, bytes::openStream);
    }

    @Test
    public void fileBackedCloneOutlivesOriginalOwner() throws Exception {
        final File file = this.temporaryFolder.newFile("clone.bin");
        Files.write(file.toPath(), new byte[] {42, 43});

        final ChunkedBytes original = new ChunkedBytes();
        original.appendFile(file.toPath());
        final ChunkedBytes clone = (ChunkedBytes) original.clone();
        original.close();
        try {
            assertEquals(42, clone.get(0));
            assertArrayEquals(new byte[] {42, 43}, clone.toByteArray());
        } finally {
            clone.close();
        }
    }

    @Test(timeout = 15000L)
    public void comparesDifferentContentBeyondTwoGiB() throws Exception {
        final long length = (long) Integer.MAX_VALUE + 1L;
        final File leftFile = this.temporaryFolder.newFile("large-left.bin");
        final File rightFile = this.temporaryFolder.newFile("large-right.bin");
        createSparseFile(leftFile, length, 1);
        createSparseFile(rightFile, length, 2);

        try (ChunkedBytes left = new ChunkedBytes();
                ChunkedBytes right = new ChunkedBytes()) {
            left.appendFile(leftFile.toPath());
            right.appendFile(rightFile.toPath());
            assertEquals(length, left.size());
            assertEquals(length, right.size());
            assertTrue(left.compareTo(right) < 0);
            assertTrue(right.compareTo(left) > 0);
        }
    }

    @Test
    public void writableMappingPersistsCrossChunkChangesOnFlushAndClose() throws Exception {
        final File file = this.temporaryFolder.newFile("writable.bin");
        final long patchPosition = ChunkedBytes.CHUNK_SIZE - 4L;
        final byte[] patch = new byte[] {9, 8, 7, 6, 5, 4, 3, 2};
        createSparseFile(file, ChunkedBytes.CHUNK_SIZE + 4L, 0);

        try (ChunkedBytes bytes = new ChunkedBytes()) {
            bytes.appendFile(file.toPath(), true);
            assertEquals(patch.length,
                    bytes.write(patchPosition, patch, 0, patch.length));
            bytes.flush();
        }

        final byte[] stored = new byte[patch.length];
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(patchPosition);
            input.readFully(stored);
        }
        assertArrayEquals(patch, stored);
    }

    @Test
    public void appendFileReportsIoFailuresConsistently() {
        final File missing = new File(this.temporaryFolder.getRoot(), "missing.bin");
        try (ChunkedBytes bytes = new ChunkedBytes()) {
            assertThrows(UncheckedIOException.class,
                    () -> bytes.appendFile(missing.toPath()));
        }
    }

    @Test(timeout = 5000L)
    public void writeFromDoesNotHoldMonitorWhileCallingInputStream() throws Exception {
        final ChunkedBytes bytes = new ChunkedBytes();
        final Object inputLock = new Object();
        final CountDownLatch inputLockHeld = new CountDownLatch(1);
        final CountDownLatch readEntered = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final InputStream input = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("Bulk read expected");
            }

            @Override
            public int read(final byte[] target, final int offset, final int length) {
                readEntered.countDown();
                synchronized (inputLock) {
                    return -1;
                }
            }
        };

        final Thread appender = new Thread(() -> {
            synchronized (inputLock) {
                inputLockHeld.countDown();
                try {
                    if (!readEntered.await(2L, TimeUnit.SECONDS)) {
                        throw new AssertionError("writeFrom did not enter InputStream.read");
                    }
                    bytes.append(new byte[] {1});
                } catch (final Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }
        }, "chunked-bytes-lock-owner");
        appender.setDaemon(true);
        appender.start();
        assertTrue("Input lock was not acquired",
                inputLockHeld.await(2L, TimeUnit.SECONDS));

        final Thread writer = new Thread(() -> {
            try {
                bytes.writeFrom(input);
            } catch (final Throwable e) {
                failure.compareAndSet(null, e);
            }
        }, "chunked-bytes-stream-writer");
        writer.setDaemon(true);
        writer.start();

        writer.join(2000L);
        appender.join(2000L);
        assertFalse("writeFrom and append deadlocked", writer.isAlive());
        assertFalse("writeFrom and append deadlocked", appender.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertArrayEquals(new byte[] {1}, bytes.toByteArray());
        bytes.close();
    }

    private static void createSparseFile(final File file, final long length,
            final int firstByte) throws IOException {
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.setLength(length);
            if (length > 0) {
                output.seek(0L);
                output.write(firstByte);
            }
        }
    }
}
