/*
 *  SSTableHandleMapRecoveryAndLoadTest
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
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.order.NaturalOrder;

public class SSTableHandleMapRecoveryAndLoadTest {

    private static final int KEY_LENGTH = Long.BYTES;
    private static final int LARGE_RECORD_COUNT = 200000;
    private static final int LOOKUPS_PER_READER = 20000;
    private static final int REMOVALS = 5000;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Helper entry point used by {@link #globalRecoveryRemovesARealCrashArtifact()}.
     * The process deliberately bypasses every shutdown hook and finally block.
     */
    public static void main(final String[] arguments) throws Exception {
        if (arguments.length != 2) return;
        if ("--crash-helper".equals(arguments[0])) {
            final File fingerprint = new File(arguments[1]);
            final SSTableHandleMap table = SSTableHandleMap.openWorkingCopy(
                    KEY_LENGTH, NaturalOrder.naturalOrder, Long.BYTES, fingerprint);
            table.remove(key(1));
            signalReadyAndWait();
            /* Keep the owning table strongly reachable while the parent verifies its
             * cross-process lock. The helper is normally killed before this executes. */
            table.size();
            Runtime.getRuntime().halt(0);
        }
        if ("--builder-crash-helper".equals(arguments[0])) {
            final SSTableHandleMap.Builder builder = new SSTableHandleMap.Builder(
                    KEY_LENGTH, NaturalOrder.naturalOrder, Long.BYTES,
                    new File(arguments[1]), "crash-helper", 1);
            builder.consume(key(1), offset(1));
            signalReadyAndWait();
            /* Preserve the Builder and its session lock until the forced process stop. */
            builder.consume(key(2), offset(2));
            Runtime.getRuntime().halt(0);
        }
    }

    private static void signalReadyAndWait() throws IOException {
        System.out.println("READY");
        System.out.flush();
        System.in.read();
    }

    @Test(timeout = 30000)
    public void globalRecoveryRemovesARealCrashArtifact() throws Exception {
        final File repository = this.temporaryFolder.newFolder("crash-repository");
        final File fingerprint = new File(repository, "crashed.idx");
        writeTable(fingerprint, 32);
        final byte[] published = Files.readAllBytes(fingerprint.toPath());

        final File java = new File(new File(System.getProperty("java.home"), "bin"),
                isWindows() ? "java.exe" : "java");
        final Process process = new ProcessBuilder(
                java.getAbsolutePath(), "-cp", System.getProperty("java.class.path"),
                SSTableHandleMapRecoveryAndLoadTest.class.getName(),
                "--crash-helper", fingerprint.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
        final BufferedReader processOutput = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8));
        try {
            /* Newer JVMs may print runtime diagnostics (for example about explicit
             * mapped-buffer cleanup) before the helper reaches its synchronization
             * marker. These messages must not be mistaken for helper failure. */
            String outputLine;
            do {
                outputLine = processOutput.readLine();
            } while (outputLine != null && !"READY".equals(outputLine));
            assertEquals("READY", outputLine);
            assertEquals(1L, countWorkingFiles(repository.toPath()));

            final SSTableHandleMap.RecoveryReport whileRunning =
                    SSTableHandleMap.recoverWorkingCopies(repository);
            assertEquals(1L, whileRunning.candidates());
            assertEquals(0L, whileRunning.deleted());
            assertEquals(1L, whileRunning.inUse());
            assertEquals(0L, whileRunning.failures());
        } finally {
            process.destroyForcibly();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("crash helper did not terminate");
            }
        }
        assertEquals(1L, countWorkingFiles(repository.toPath()));

        final SSTableHandleMap.RecoveryReport recovery =
                SSTableHandleMap.recoverWorkingCopies(repository);
        assertEquals(1L, recovery.candidates());
        assertEquals(1L, recovery.deleted());
        assertEquals(0L, recovery.inUse());
        assertEquals(0L, recovery.failures());
        assertEquals(0L, countWorkingFiles(repository.toPath()));
        assertArrayEquals(published, Files.readAllBytes(fingerprint.toPath()));
    }

    @Test(timeout = 30000)
    public void globalRecoveryRemovesRealBuilderCrashArtifacts() throws Exception {
        final File repository = this.temporaryFolder.newFolder("builder-crash-repository");
        final File java = new File(new File(System.getProperty("java.home"), "bin"),
                isWindows() ? "java.exe" : "java");
        final Process process = new ProcessBuilder(
                java.getAbsolutePath(), "-cp", System.getProperty("java.class.path"),
                SSTableHandleMapRecoveryAndLoadTest.class.getName(),
                "--builder-crash-helper", repository.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
        final BufferedReader processOutput = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8));
        try {
            String outputLine;
            do {
                outputLine = processOutput.readLine();
            } while (outputLine != null && !"READY".equals(outputLine));
            assertEquals("READY", outputLine);
            assertEquals(2L, countBuilderArtifacts(repository.toPath()));

            final SSTableHandleMap.RecoveryReport whileRunning =
                    SSTableHandleMap.recoverWorkingCopies(repository);
            assertEquals(2L, whileRunning.candidates());
            assertEquals(0L, whileRunning.deleted());
            assertEquals(2L, whileRunning.inUse());
            assertEquals(0L, whileRunning.failures());
        } finally {
            process.destroyForcibly();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("builder crash helper did not terminate");
            }
        }

        final SSTableHandleMap.RecoveryReport recovery =
                SSTableHandleMap.recoverWorkingCopies(repository);
        assertEquals(2L, recovery.candidates());
        assertEquals(2L, recovery.deleted());
        assertEquals(0L, recovery.inUse());
        assertEquals(0L, recovery.failures());
        assertEquals(0L, countBuilderArtifacts(repository.toPath()));
    }

    @Test(timeout = 60000)
    public void largeTablePromotesSafelyUnderConcurrentReadLoad() throws Exception {
        final File repository = this.temporaryFolder.newFolder("load-repository");
        final File fingerprint = new File(repository, "large.idx");
        writeTable(fingerprint, LARGE_RECORD_COUNT);
        final byte[] published = Files.readAllBytes(fingerprint.toPath());

        final SSTableHandleMap table = SSTableHandleMap.openWorkingCopy(
                KEY_LENGTH, NaturalOrder.naturalOrder, Long.BYTES, fingerprint);
        final ExecutorService readers = Executors.newFixedThreadPool(4);
        try {
            assertEquals(LARGE_RECORD_COUNT, table.size());
            assertEquals(0L, table.mem());
            assertEquals(0L, countWorkingFiles(repository.toPath()));

            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<?>> results = new ArrayList<>();
            for (int reader = 0; reader < 4; reader++) {
                final int seed = reader;
                results.add(readers.submit(() -> {
                    final Random random = new Random(seed);
                    start.await();
                    for (int lookup = 0; lookup < LOOKUPS_PER_READER; lookup++) {
                        /* Readers stay in the untouched upper half while the lower
                         * half triggers and exercises the mapping transition. */
                        final int record = LARGE_RECORD_COUNT / 2
                                + random.nextInt(LARGE_RECORD_COUNT / 2);
                        assertEquals(offset(record), table.get(key(record)));
                    }
                    return null;
                }));
            }

            start.countDown();
            for (int record = 0; record < REMOVALS; record++) {
                assertEquals(offset(record), table.remove(key(record)));
            }
            for (final Future<?> result : results) result.get();

            assertEquals(LARGE_RECORD_COUNT - REMOVALS, table.size());
            assertEquals(-1L, table.get(key(REMOVALS - 1)));
            assertEquals(offset(LARGE_RECORD_COUNT - 1),
                    table.get(key(LARGE_RECORD_COUNT - 1)));
            assertEquals(1L, countWorkingFiles(repository.toPath()));
            assertArrayEquals(published, Files.readAllBytes(fingerprint.toPath()));
        } finally {
            readers.shutdownNow();
            try {
                assertTrue(readers.awaitTermination(10, TimeUnit.SECONDS));
            } finally {
                table.close();
            }
        }
        assertEquals(0L, countWorkingFiles(repository.toPath()));
    }

    private static void writeTable(final File target, final int records)
            throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(target.toPath())))) {
            for (int record = 0; record < records; record++) {
                output.write(key(record));
                output.write(NaturalOrder.encodeLong(offset(record), Long.BYTES));
            }
        }
    }

    private static long countWorkingFiles(final Path repository) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(repository)) {
            return files.filter(path -> path.getFileName().toString()
                    .endsWith(".sstwork")).count();
        }
    }

    private static long countBuilderArtifacts(final Path repository) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(repository)) {
            return files.filter(path -> {
                final String name = path.getFileName().toString();
                return name.startsWith("sst-build-")
                        && (name.endsWith(".sstlock") || name.endsWith(".run")
                                || name.endsWith(".merge") || name.endsWith(".sst"));
            }).count();
        }
    }

    private static byte[] key(final long record) {
        return NaturalOrder.encodeLong(record, KEY_LENGTH);
    }

    private static long offset(final long record) {
        return record * 10L;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
