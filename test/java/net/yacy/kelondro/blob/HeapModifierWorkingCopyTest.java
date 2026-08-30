/*
 *  HeapModifierWorkingCopyTest
 *  Copyright 2026 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.kelondro.blob;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.order.NaturalOrder;
import net.yacy.kelondro.index.SSTableHandleMap;

public class HeapModifierWorkingCopyTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void modifierUsesPrivateGenerationAndPublishesCompactedFingerprint() throws Exception {
        final File directory = this.temporaryFolder.newFolder("heap-working-copy");
        final File temporaryHeap = new File(directory, "data.heap.prt");
        final File heap = new File(directory, "data.heap");

        /* HeapWriter publishes the initial immutable fingerprint generation. */
        final HeapWriter writer = new HeapWriter(
                temporaryHeap, heap, 4, NaturalOrder.naturalOrder, 4096);
        for (int index = 1; index <= 5; index++) {
            writer.add(key(index), value(index));
        }
        writer.close(true);

        final File initialFingerprint = HeapWriter.fingerprintIndexFile(
                heap, HeapReader.fingerprintFileHash(heap));
        assertTrue(initialFingerprint.isFile());
        final byte[] publishedBytes = Files.readAllBytes(initialFingerprint.toPath());

        final HeapModifier first = new HeapModifier(
                heap, 4, NaturalOrder.naturalOrder);
        HeapModifier second = null;
        try {
            assertTrue(first.index instanceof SSTableHandleMap);
            assertEquals(0, countWorkingFiles(directory.toPath()));
            assertArrayEquals(value(2), first.get(key(2)));
            assertArrayEquals(publishedBytes, Files.readAllBytes(initialFingerprint.toPath()));

            /* Multiple readers remain on the published generation without copying. */
            second = new HeapModifier(heap, 4, NaturalOrder.naturalOrder);
            assertTrue(second.index instanceof SSTableHandleMap);
            assertEquals(0, countWorkingFiles(directory.toPath()));
            assertArrayEquals(value(2), second.get(key(2)));
            second.close(false);
            second = null;
            assertEquals(0, countWorkingFiles(directory.toPath()));

            /* The first heap mutation creates exactly one private generation. */
            first.delete(key(2));
            assertEquals(1, countWorkingFiles(directory.toPath()));
            assertFalse(first.containsKey(key(2)));
            assertFalse(initialFingerprint.exists());
            first.close(true);
        } finally {
            if (second != null) second.close(false);
            first.close(false);
        }

        assertEquals(0, countWorkingFiles(directory.toPath()));

        /* close(true) compacted tombstones and published the next fingerprint. */
        final File compactedFingerprint = HeapWriter.fingerprintIndexFile(
                heap, HeapReader.fingerprintFileHash(heap));
        assertTrue(compactedFingerprint.isFile());

        final HeapModifier reopened = new HeapModifier(
                heap, 4, NaturalOrder.naturalOrder);
        try {
            assertTrue(reopened.index instanceof SSTableHandleMap);
            assertEquals(4, reopened.size());
            assertFalse(reopened.containsKey(key(2)));
            assertArrayEquals(value(5), reopened.get(key(5)));
        } finally {
            reopened.close(true);
        }
        assertEquals(0, countWorkingFiles(directory.toPath()));
    }

    @Test
    public void publishFailureIsPropagatedAndRollsBackIncompletePair() throws Exception {
        final File directory = this.temporaryFolder.newFolder("heap-publish-failure");
        final File temporaryHeap = new File(directory, "data.heap.prt");
        final File heap = new File(directory, "data.heap");

        final HeapWriter writer = new HeapWriter(
                temporaryHeap, heap, 4, NaturalOrder.naturalOrder, 4096);
        for (int index = 1; index <= 5; index++) {
            writer.add(key(index), value(index));
        }
        writer.close(true);

        final HeapModifier modifier = new HeapModifier(
                heap, 4, NaturalOrder.naturalOrder);
        modifier.delete(key(2));
        final String fingerprint = HeapReader.fingerprintFileHash(heap);
        final File targetIndex = HeapWriter.fingerprintIndexFile(heap, fingerprint);
        final File targetGap = HeapWriter.fingerprintGapFile(heap, fingerprint);

        /* A non-empty directory cannot be replaced by the final gap dump. The
         * index has already been published at that point and must be rolled back. */
        assertTrue(targetGap.mkdir());
        Files.write(targetGap.toPath().resolve("blocker"), new byte[] {1});

        try {
            modifier.close(true);
            throw new AssertionError("publish failure was swallowed");
        } catch (final UncheckedIOException expected) {
            assertTrue(expected.getMessage().contains(heap.getName()));
            assertTrue(expected.getCause().getMessage().contains("complete fingerprint pair"));
        } finally {
            modifier.close(false);
        }

        assertFalse(targetIndex.exists());
        assertTrue(targetGap.isDirectory());
        assertFalse(new File(targetIndex.getPath() + ".prt").exists());
        assertFalse(new File(targetGap.getPath() + ".prt").exists());
        assertEquals(0, countWorkingFiles(directory.toPath()));
    }

    @Test
    public void incompleteFingerprintPairIsRebuiltFromHeapAndRepublished() throws Exception {
        final File directory = this.temporaryFolder.newFolder("heap-pair-recovery");
        final File temporaryHeap = new File(directory, "data.heap.prt");
        final File heap = new File(directory, "data.heap");

        final HeapWriter writer = new HeapWriter(
                temporaryHeap, heap, 4, NaturalOrder.naturalOrder, 4096);
        for (int index = 1; index <= 6; index++) {
            writer.add(key(index), value(index));
        }
        writer.close(true);

        final String fingerprint = HeapReader.fingerprintFileHash(heap);
        final File indexDump = HeapWriter.fingerprintIndexFile(heap, fingerprint);
        final File gapDump = HeapWriter.fingerprintGapFile(heap, fingerprint);
        assertTrue(indexDump.isFile());
        assertTrue(gapDump.isFile());

        /* Simulate a crash between publishing the index and installing the gap
         * commit marker. The remaining index must never be accepted alone. */
        Files.delete(gapDump.toPath());
        assertFalse(gapDump.exists());

        final HeapModifier recovered = new HeapModifier(
                heap, 4, NaturalOrder.naturalOrder);
        try {
            assertEquals(6, recovered.size());
            assertArrayEquals(value(1), recovered.get(key(1)));
            assertArrayEquals(value(6), recovered.get(key(6)));
            assertFalse(indexDump.exists());
        } finally {
            recovered.close(true);
        }

        assertTrue(indexDump.isFile());
        assertTrue(gapDump.isFile());
        final HeapModifier restarted = new HeapModifier(
                heap, 4, NaturalOrder.naturalOrder);
        try {
            assertEquals(6, restarted.size());
            assertArrayEquals(value(4), restarted.get(key(4)));
        } finally {
            restarted.close(false);
        }
        assertEquals(0, countWorkingFiles(directory.toPath()));
    }

    private static long countWorkingFiles(final Path directory) throws Exception {
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString()
                    .endsWith(".sstwork")).count();
        }
    }

    private static byte[] key(final int value) {
        return String.format("%04d", value).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] value(final int value) {
        return ("value-" + value).getBytes(StandardCharsets.US_ASCII);
    }
}
