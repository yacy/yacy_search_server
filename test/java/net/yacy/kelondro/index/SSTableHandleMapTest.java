/*
 *  SSTableHandleMapTest
 *  Copyright 2026 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.kelondro.index;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;

public class SSTableHandleMapTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void builderMergesBoundedRunsAndRemovesItsWorkingFile() throws Exception {
        final File directory = this.temporaryFolder.newFolder("builder");
        final SSTableHandleMap table;
        try (SSTableHandleMap.Builder builder = new SSTableHandleMap.Builder(
                4, NaturalOrder.naturalOrder, Long.BYTES,
                directory, "multipass", 1)) {
            /* One buffered record per run forces both fan-in limiting and a second merge pass. */
            for (int index = 129; index >= 0; index--) {
                builder.consume(key(index), index);
            }
            builder.consume(key(50), 999L);
            table = builder.finish();
        }

        try {
            assertEquals(130, table.size());
            assertEquals(0L, table.get(key(0)));
            assertEquals(999L, table.get(key(50)));
            assertEquals(129L, table.get(key(129)));

            final CloneableIterator<byte[]> keys = table.keys(true, null);
            int expected = 0;
            while (keys.hasNext()) assertArrayEquals(key(expected++), keys.next());
            assertEquals(130, expected);

            assertEquals(1, directory.list().length);
        } finally {
            table.close();
        }
        assertEquals(0, directory.list().length);
    }

    @Test
    public void workingCopyPromotesLazilyAndHonorsOwnershipLocks() throws Exception {
        final File directory = this.temporaryFolder.newFolder("working-copy");
        final File fingerprint = new File(directory, "heap.fingerprint.idx");
        writeFingerprint(fingerprint);
        final byte[] publishedSnapshot = Files.readAllBytes(fingerprint.toPath());

        /* Simulate a working file left by a crashed process: it has no live lock. */
        final String prefix = "sst-work-" + Integer.toHexString(
                fingerprint.getAbsolutePath().hashCode()) + "-";
        final Path crashedRepository = Files.createDirectories(
                directory.toPath().resolve("repository/old-generation"));
        Files.write(crashedRepository.resolve(prefix + "stale.sstwork"), new byte[] {1});

        final SSTableHandleMap.RecoveryReport initialRecovery =
                SSTableHandleMap.recoverWorkingCopies(directory, crashedRepository.toFile());
        assertEquals(1L, initialRecovery.candidates());
        assertEquals(1L, initialRecovery.deleted());
        assertEquals(0L, initialRecovery.inUse());
        assertEquals(0L, initialRecovery.failures());

        final SSTableHandleMap first = SSTableHandleMap.openWorkingCopy(
                4, NaturalOrder.naturalOrder, Long.BYTES, fingerprint);
        SSTableHandleMap second = null;
        try {
            /* Reads stay directly on the published read-only mapping. */
            assertEquals(0, countWorkingFiles(directory.toPath()));
            assertEquals(20L, first.remove(key(2)));
            assertEquals(1, countWorkingFiles(directory.toPath()));

            /* The tombstone belongs to the private generation, never to the snapshot. */
            assertArrayEquals(publishedSnapshot, Files.readAllBytes(fingerprint.toPath()));

            second = SSTableHandleMap.openWorkingCopy(
                    4, NaturalOrder.naturalOrder, Long.BYTES, fingerprint);
            assertEquals(1, countWorkingFiles(directory.toPath()));
            assertEquals(20L, second.get(key(2)));
            assertEquals(30L, second.remove(key(3)));
            assertEquals(2, countWorkingFiles(directory.toPath()));

            /* A global pass may run defensively without touching live generations. */
            final SSTableHandleMap.RecoveryReport liveRecovery =
                    SSTableHandleMap.recoverWorkingCopies(directory);
            assertEquals(2L, liveRecovery.candidates());
            assertEquals(0L, liveRecovery.deleted());
            assertEquals(2L, liveRecovery.inUse());
            assertEquals(0L, liveRecovery.failures());
        } finally {
            if (second != null) second.close();
            first.close();
        }
        assertEquals(0, countWorkingFiles(directory.toPath()));
        assertArrayEquals(publishedSnapshot, Files.readAllBytes(fingerprint.toPath()));
    }

    @Test
    public void recoveryCoversBuilderArtifactsAndPreservesUnrelatedFiles() throws Exception {
        final File directory = this.temporaryFolder.newFolder("builder-recovery");
        final String session = "sst-build-deadbeef-0123456789abcdef0123456789abcdef";
        final String[] artifacts = {
                session + ".sstlock",
                session + "-1.run",
                session + "-2.merge",
                session + "-3.sst",
                "sst-deadbeef-legacy.run",
                "sst-deadbeef-legacy.merge",
                "sst-deadbeef-legacy.sst"
        };
        for (final String artifact : artifacts) {
            Files.write(directory.toPath().resolve(artifact), new byte[] {1});
        }

        final Path unrelatedRun = Files.write(
                directory.toPath().resolve("application.run"), new byte[] {2});
        final Path unrelatedSSTable = Files.write(
                directory.toPath().resolve("sst-user-data.sst"), new byte[] {3});

        final SSTableHandleMap.RecoveryReport recovery =
                SSTableHandleMap.recoverWorkingCopies(directory);
        assertEquals(artifacts.length, recovery.candidates());
        assertEquals(artifacts.length, recovery.deleted());
        assertEquals(0L, recovery.inUse());
        assertEquals(0L, recovery.failures());
        for (final String artifact : artifacts) {
            assertFalse(Files.exists(directory.toPath().resolve(artifact)));
        }
        assertTrue(Files.isRegularFile(unrelatedRun));
        assertTrue(Files.isRegularFile(unrelatedSSTable));
    }

    @Test
    public void recoveryDoesNotDeleteActiveBuilderOrFinishedTable() throws Exception {
        final File directory = this.temporaryFolder.newFolder("active-builder-recovery");
        final SSTableHandleMap.Builder builder = new SSTableHandleMap.Builder(
                4, NaturalOrder.naturalOrder, Long.BYTES,
                directory, "active", 1);
        SSTableHandleMap table = null;
        try {
            builder.consume(key(1), 10L);

            final SSTableHandleMap.RecoveryReport whileBuilding =
                    SSTableHandleMap.recoverWorkingCopies(directory);
            assertEquals(2L, whileBuilding.candidates());
            assertEquals(0L, whileBuilding.deleted());
            assertEquals(2L, whileBuilding.inUse());
            assertEquals(0L, whileBuilding.failures());

            table = builder.finish();
            builder.close();
            assertEquals(10L, table.get(key(1)));

            final SSTableHandleMap.RecoveryReport whileOpen =
                    SSTableHandleMap.recoverWorkingCopies(directory);
            assertEquals(1L, whileOpen.candidates());
            assertEquals(0L, whileOpen.deleted());
            assertEquals(1L, whileOpen.inUse());
            assertEquals(0L, whileOpen.failures());
            assertEquals(10L, table.get(key(1)));
        } finally {
            try {
                builder.close();
            } finally {
                if (table != null) table.close();
            }
        }
        assertEquals(0, directory.list().length);
    }

    @Test
    public void workingCopyDecompressesGzipFingerprint() throws Exception {
        final File directory = this.temporaryFolder.newFolder("working-copy-gzip");
        final File fingerprint = new File(directory, "heap.fingerprint.idx.gz");
        writeFingerprint(fingerprint);

        final SSTableHandleMap table = SSTableHandleMap.openWorkingCopy(
                4, NaturalOrder.naturalOrder, Long.BYTES, fingerprint);
        try {
            assertEquals(3, table.size());
            assertEquals(30L, table.get(key(3)));
            assertEquals(1, countWorkingFiles(directory.toPath()));
            final SSTableHandleMap.RecoveryReport recovery =
                    SSTableHandleMap.recoverWorkingCopies(directory);
            assertEquals(1L, recovery.candidates());
            assertEquals(0L, recovery.deleted());
            assertEquals(1L, recovery.inUse());
            assertEquals(0L, recovery.failures());
        } finally {
            table.close();
        }
        assertEquals(0, countWorkingFiles(directory.toPath()));
    }

    @Test
    public void clearAndRemoveOnePromoteOnlyWhenTheyChangeTheTable() throws Exception {
        final File directory = this.temporaryFolder.newFolder("working-copy-mutations");
        final File fingerprint = new File(directory, "heap.fingerprint.idx");
        writeFingerprint(fingerprint);
        final byte[] publishedSnapshot = Files.readAllBytes(fingerprint.toPath());

        final SSTableHandleMap removeOne = SSTableHandleMap.openWorkingCopy(
                4, NaturalOrder.naturalOrder, Long.BYTES, fingerprint);
        try {
            assertEquals(0, countWorkingFiles(directory.toPath()));
            assertEquals(30L, removeOne.removeone());
            assertEquals(1, countWorkingFiles(directory.toPath()));
        } finally {
            removeOne.close();
        }

        final SSTableHandleMap clear = SSTableHandleMap.openWorkingCopy(
                4, NaturalOrder.naturalOrder, Long.BYTES, fingerprint);
        try {
            assertEquals(0, countWorkingFiles(directory.toPath()));
            clear.clear();
            assertEquals(0, clear.size());
            assertEquals(1, countWorkingFiles(directory.toPath()));
            clear.clear();
            assertEquals(1, countWorkingFiles(directory.toPath()));
        } finally {
            clear.close();
        }

        assertEquals(0, countWorkingFiles(directory.toPath()));
        assertArrayEquals(publishedSnapshot, Files.readAllBytes(fingerprint.toPath()));
    }

    @Test
    public void rejectsTruncatedUnsortedAndDuplicateTables() throws Exception {
        final File directory = this.temporaryFolder.newFolder("invalid-tables");

        final File truncated = new File(directory, "truncated.idx");
        Files.write(truncated.toPath(), new byte[11]);
        assertInvalidTable(truncated, "not a multiple");

        final File unsorted = new File(directory, "unsorted.idx");
        writeRawTable(unsorted, new int[] {1, 3, 2, 4}, null);
        assertInvalidTable(unsorted, "out of order");

        final File duplicate = new File(directory, "duplicate.idx");
        writeRawTable(duplicate, new int[] {1, 2, 2, 3}, null);
        assertInvalidTable(duplicate, "duplicate key");
    }

    @Test
    public void rejectsUnknownNegativeOffsetsButAcceptsTombstones() throws Exception {
        final File directory = this.temporaryFolder.newFolder("invalid-offsets");
        final File invalid = new File(directory, "negative.idx");
        writeRawTable(invalid, new int[] {1, 2, 3}, new long[] {10L, -2L, 30L});
        assertInvalidTable(invalid, "negative SSTable offset -2");

        final File tombstoned = new File(directory, "tombstoned.idx");
        writeRawTable(tombstoned, new int[] {1, 2, 3}, new long[] {10L, -1L, 30L});
        final SSTableHandleMap table = new SSTableHandleMap(
                4, NaturalOrder.naturalOrder, Long.BYTES, tombstoned);
        try {
            assertEquals(2, table.size());
            assertEquals(-1L, table.get(key(2)));
            assertEquals(30L, table.get(key(3)));
        } finally {
            table.close();
        }
    }

    private static void writeFingerprint(final File target) throws Exception {
        final RowHandleMap map = new RowHandleMap(
                4, NaturalOrder.naturalOrder, Long.BYTES, 3, target.getName());
        try {
            map.putUnique(key(1), 10L);
            map.putUnique(key(2), 20L);
            map.putUnique(key(3), 30L);
            map.dump(target);
        } finally {
            map.close();
        }
    }

    private static void writeRawTable(final File target, final int[] keys,
            final long[] offsets) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(target.toPath())))) {
            for (int index = 0; index < keys.length; index++) {
                output.write(key(keys[index]));
                output.write(NaturalOrder.encodeLong(
                        offsets == null ? keys[index] * 10L : offsets[index],
                        Long.BYTES));
            }
        }
    }

    private static void assertInvalidTable(final File tableFile,
            final String expectedMessage) throws Exception {
        SSTableHandleMap table = null;
        try {
            table = new SSTableHandleMap(
                    4, NaturalOrder.naturalOrder, Long.BYTES, tableFile);
            throw new AssertionError("Invalid SSTable was accepted: " + tableFile);
        } catch (final IOException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains(expectedMessage));
        } finally {
            if (table != null) table.close();
        }
    }

    private static long countWorkingFiles(final Path directory) throws Exception {
        try (java.util.stream.Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".sstwork")).count();
        }
    }

    private static byte[] key(final int value) {
        return String.format("%04d", value).getBytes(StandardCharsets.US_ASCII);
    }
}
