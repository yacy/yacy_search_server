/*
 *  ArrayStackSSTableLifecycleTest
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.kelondro.data.citation.CitationReference;
import net.yacy.kelondro.data.citation.CitationReferenceFactory;
import net.yacy.kelondro.index.RowHandleMap;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.index.SSTableHandleMap;
import net.yacy.kelondro.rwi.ReferenceContainer;

public class ArrayStackSSTableLifecycleTest {

    private static final int KEY_LENGTH = 12;
    private static final int WRITE_BUFFER = 4096;
    private static final String PREFIX = "segment";

    private static final byte[] DELETE_KEY = ASCII.getBytes("AAAAAAAAAAAA");
    private static final byte[] FILLER_B_KEY = ASCII.getBytes("BBBBBBBBBBBB");
    private static final byte[] REDUCE_KEY = ASCII.getBytes("CCCCCCCCCCCC");
    private static final byte[] SHARED_KEY = ASCII.getBytes("DDDDDDDDDDDD");
    private static final byte[] FILLER_E_KEY = ASCII.getBytes("EEEEEEEEEEEE");
    private static final byte[] ACTIVE_KEY = ASCII.getBytes("FFFFFFFFFFFF");
    private static final byte[] FILLER_G_KEY = ASCII.getBytes("GGGGGGGGGGGG");
    private static final byte[] FILLER_H_KEY = ASCII.getBytes("HHHHHHHHHHHH");
    private static final byte[] FILLER_I_KEY = ASCII.getBytes("IIIIIIIIIIII");
    private static final byte[] RECOVERED_KEY = ASCII.getBytes("JJJJJJJJJJJJ");

    private static final CitationReferenceFactory REFERENCE_FACTORY =
            new CitationReferenceFactory();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void sealedSegmentsStaySSTablesAcrossRestartReduceDeleteAndMerge() throws Exception {
        final File directory = this.temporaryFolder.newFolder("array-stack-sstable");
        ArrayStack stack = newStack(directory);
        try {
            final byte[] twoReferences = container(
                    REDUCE_KEY, "000000000001", "000000000002");
            final byte[] oneReference = container(REDUCE_KEY, "000000000001");

            /*
             * The first five inserts create the initial writable Heap. Five entries
             * are intentional: Heap.close(true) then publishes an idx/gap pair which
             * sealWriter() must reopen through HeapReader.immutable().
             */
            stack.insert(DELETE_KEY, container(DELETE_KEY, "100000000001"));
            stack.insert(FILLER_B_KEY, container(FILLER_B_KEY, "200000000001"));
            stack.insert(REDUCE_KEY, twoReferences);
            stack.insert(SHARED_KEY, container(SHARED_KEY, "300000000001"));
            stack.insert(FILLER_E_KEY, container(FILLER_E_KEY, "400000000001"));

            /*
             * Mounting a newer full segment is the deterministic rollover path:
             * ArrayStack seals the old writer and keeps only this new segment mutable.
             */
            final File secondSegment = stack.newBLOB(
                    new Date(System.currentTimeMillis() + 60_000L));
            writeSecondSegment(secondSegment);
            stack.mountBLOB(secondSegment, true);

            assertSegmentTypes(stack, HeapModifier.class, Heap.class);
            assertTrue(((HeapModifier) segmentBlobs(stack).get(0)).index
                    instanceof SSTableHandleMap);
            assertTrue(((Heap) segmentBlobs(stack).get(1)).index
                    instanceof RowHandleMap);
            assertEquals(0L, countWorkingFiles(directory.toPath()));
            assertArrayEquals(container(SHARED_KEY, "500000000001"),
                    stack.get(SHARED_KEY));
            assertEquals(2, countOccurrences(stack, SHARED_KEY));

            /* reduce() must use HeapModifier's in-place path on the sealed segment. */
            assertEquals(twoReferences.length - oneReference.length,
                    stack.reduce(REDUCE_KEY, ignored -> oneReference));
            assertEquals(1L, countWorkingFiles(directory.toPath()));
            assertContainerSize(stack.get(REDUCE_KEY), 1);

            stack.close(true);
            stack = null;
            assertEquals(0L, countWorkingFiles(directory.toPath()));

            /* A process restart must reconstruct the same mutable/sealed boundary. */
            stack = newStack(directory);
            assertSegmentTypes(stack, HeapModifier.class, Heap.class);
            assertTrue(((HeapModifier) segmentBlobs(stack).get(0)).index
                    instanceof SSTableHandleMap);
            assertTrue(((Heap) segmentBlobs(stack).get(1)).index
                    instanceof RowHandleMap);
            assertContainerSize(stack.get(REDUCE_KEY), 1);
            assertEquals(0L, countWorkingFiles(directory.toPath()));

            /*
             * Exercise the actual ArrayStack merge protocol: unmount both sources,
             * merge their ReferenceContainers, and mount the result as a sealed heap.
             */
            final File[] mergeSources = stack.unmountSmallest(Long.MAX_VALUE);
            assertNotNull(mergeSources);
            assertEquals(0, stack.entries());
            assertEquals(0L, countWorkingFiles(directory.toPath()));

            final File mergedFile = stack.newBLOB(
                    new Date(System.currentTimeMillis() + 120_000L));
            assertEquals(mergedFile, stack.mergeMount(
                    mergeSources[0], mergeSources[1], REFERENCE_FACTORY,
                    mergedFile, WRITE_BUFFER));
            assertSegmentTypes(stack, HeapModifier.class);
            assertTrue(((HeapModifier) segmentBlobs(stack).get(0)).index
                    instanceof SSTableHandleMap);
            assertEquals(0L, countWorkingFiles(directory.toPath()));
            assertContainerSize(stack.get(SHARED_KEY), 2);
            assertContainerSize(stack.get(REDUCE_KEY), 1);
            assertEquals(9, stack.size());

            /* With one sealed segment delete() reaches HeapModifier without a RAM index. */
            stack.delete(DELETE_KEY);
            assertEquals(1L, countWorkingFiles(directory.toPath()));
            assertFalse(stack.containsKey(DELETE_KEY));
            assertEquals(8, stack.size());

            stack.close(true);
            stack = null;
            assertEquals(0L, countWorkingFiles(directory.toPath()));

            /* The newest (now sole) merged segment is writable after the next restart. */
            stack = newStack(directory);
            assertSegmentTypes(stack, Heap.class);
            assertTrue(((Heap) segmentBlobs(stack).get(0)).index
                    instanceof RowHandleMap);
            assertFalse(stack.containsKey(DELETE_KEY));
            assertContainerSize(stack.get(SHARED_KEY), 2);
            assertContainerSize(stack.get(REDUCE_KEY), 1);
            assertEquals(8, stack.size());
            assertEquals(0L, countWorkingFiles(directory.toPath()));
        } finally {
            if (stack != null) stack.close(false);
        }
    }

    @Test
    public void failedFingerprintPublishRestoresWritableHeap() throws Exception {
        final File directory = this.temporaryFolder.newFolder("array-stack-publish-failure");
        ArrayStack stack = new ArrayStack(
                directory, PREFIX, NaturalOrder.naturalOrder,
                KEY_LENGTH, 0, false, false);
        try {
            /* More than three entries make close(true) publish a fingerprint pair. */
            stack.insert(DELETE_KEY, ASCII.getBytes("value-a"));
            stack.insert(FILLER_B_KEY, ASCII.getBytes("value-b"));
            stack.insert(REDUCE_KEY, ASCII.getBytes("value-c"));
            stack.insert(SHARED_KEY, ASCII.getBytes("value-d"));
            stack.insert(FILLER_E_KEY, ASCII.getBytes("value-e"));

            final File activeBlob = onlyBlobFile(directory);
            final String fingerprint = HeapReader.fingerprintFileHash(activeBlob);
            final File gapTarget = HeapWriter.fingerprintGapFile(activeBlob, fingerprint);

            /*
             * A non-empty directory cannot be replaced by Gap.dump(). This forces
             * publishing to fail after Heap.close() has already released its file
             * and in-memory index, reproducing the stale-writer failure mode.
             */
            assertTrue(gapTarget.mkdir());
            Files.write(gapTarget.toPath().resolve("blocker"), new byte[] {1});

            final File futureSegment = stack.newBLOB(
                    new Date(System.currentTimeMillis() + 60_000L));
            UncheckedIOException publishFailure = null;
            try {
                stack.mountBLOB(futureSegment, true);
            } catch (final UncheckedIOException expected) {
                publishFailure = expected;
            }
            assertNotNull("The forced fingerprint publish must fail", publishFailure);

            /* The original segment must already be reopened and writable. */
            final byte[] recoveredValue = ASCII.getBytes("after-failure");
            stack.insert(RECOVERED_KEY, recoveredValue);
            assertArrayEquals(recoveredValue, stack.get(RECOVERED_KEY));

            stack.close(true);
            stack = null;

            stack = new ArrayStack(
                    directory, PREFIX, NaturalOrder.naturalOrder,
                    KEY_LENGTH, 0, false, false);
            assertArrayEquals(recoveredValue, stack.get(RECOVERED_KEY));
        } finally {
            if (stack != null) stack.close(false);
        }
    }

    private static ArrayStack newStack(final File directory) throws Exception {
        return new ArrayStack(directory, PREFIX, NaturalOrder.naturalOrder,
                KEY_LENGTH, WRITE_BUFFER, false, false);
    }

    private static File onlyBlobFile(final File directory) {
        final File[] blobs = directory.listFiles(
                file -> file.getName().endsWith(".blob"));
        assertNotNull(blobs);
        assertEquals(1, blobs.length);
        return blobs[0];
    }

    private static void writeSecondSegment(final File target) throws Exception {
        final File temporary = new File(target.getParentFile(), target.getName() + ".prt");
        final HeapWriter writer = new HeapWriter(
                temporary, target, KEY_LENGTH, NaturalOrder.naturalOrder, WRITE_BUFFER);
        writer.add(SHARED_KEY, container(SHARED_KEY, "500000000001"));
        writer.add(ACTIVE_KEY, container(ACTIVE_KEY, "600000000001"));
        writer.add(FILLER_G_KEY, container(FILLER_G_KEY, "700000000001"));
        writer.add(FILLER_H_KEY, container(FILLER_H_KEY, "800000000001"));
        writer.add(FILLER_I_KEY, container(FILLER_I_KEY, "900000000001"));
        writer.close(true);
    }

    private static byte[] container(final byte[] termHash, final String... urlHashes)
            throws Exception {
        final ReferenceContainer<CitationReference> container =
                new ReferenceContainer<>(REFERENCE_FACTORY, termHash);
        long modified = 1_700_000_000_000L;
        for (final String urlHash : urlHashes) {
            container.add(new CitationReference(ASCII.getBytes(urlHash), modified++));
        }
        return container.exportCollection();
    }

    private static void assertContainerSize(final byte[] payload, final int expected)
            throws Exception {
        assertNotNull(payload);
        assertEquals(expected,
                RowSet.importRowSet(payload, REFERENCE_FACTORY.getRow()).size());
    }

    private static int countOccurrences(final ArrayStack stack, final byte[] key)
            throws Exception {
        int count = 0;
        for (final byte[] payload : stack.getAll(key)) {
            assertContainerSize(payload, 1);
            count++;
        }
        return count;
    }

    private static void assertSegmentTypes(
            final ArrayStack stack, final Class<?>... expectedTypes) throws Exception {
        final List<ImmutableBLOB> segments = segmentBlobs(stack);
        assertEquals(expectedTypes.length, segments.size());
        for (int index = 0; index < expectedTypes.length; index++) {
            assertEquals(expectedTypes[index], segments.get(index).getClass());
        }
    }

    /**
     * ArrayStack deliberately keeps blobItem private. Reflection is limited to
     * this regression assertion; production code still exposes no mutable segment list.
     */
    private static List<ImmutableBLOB> segmentBlobs(final ArrayStack stack) throws Exception {
        final Field blobsField = ArrayStack.class.getDeclaredField("blobs");
        blobsField.setAccessible(true);
        final List<?> items = (List<?>) blobsField.get(stack);
        final List<ImmutableBLOB> result = new ArrayList<>(items.size());
        for (final Object item : items) {
            final Field blobField = item.getClass().getDeclaredField("blob");
            blobField.setAccessible(true);
            result.add((ImmutableBLOB) blobField.get(item));
        }
        return result;
    }

    private static long countWorkingFiles(final Path directory) throws Exception {
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString()
                    .endsWith(".sstwork")).count();
        }
    }
}
