package net.yacy.data.tables;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TableStoreFileAccessTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path root() {
        return this.temporaryFolder.getRoot().toPath();
    }

    private Path tableFile() {
        return root().resolve("DATA/TABLES/people.csv");
    }

    private TableStore initialStore() throws IOException {
        final TableStore store = new TableStore(root());
        store.writeTable("people", Arrays.asList("name", "city"),
                Collections.singletonList(Arrays.asList("Ada", "London")));
        return store;
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(tableFile().getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".table-")));
        }
    }

    private static void createSymbolicLink(final Path link, final Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (final UnsupportedOperationException | IOException unavailable) {
            Assume.assumeNoException("Symbolic links unavailable on this test filesystem", unavailable);
        }
    }

    @Test
    public void directoryAliasesSerializeReplacementAndAppend() throws Exception {
        final Path realRoot = this.temporaryFolder.newFolder("real").toPath();
        final Path aliasRoot = root().resolve("alias");
        createSymbolicLink(aliasRoot, realRoot);
        final TableStore normal = new TableStore(realRoot);
        normal.writeTable("people", Collections.singletonList("name"),
                Collections.singletonList(Collections.singletonList("Original")));
        assertReplacementSerializesAppend(realRoot, aliasRoot, "people");
    }

    @Test
    public void caseAliasesSerializeReplacementAndAppend() throws Exception {
        final TableStore store = new TableStore(root());
        store.writeTable("people", Collections.singletonList("name"),
                Collections.singletonList(Collections.singletonList("Original")));
        final Path uppercase = tableFile().resolveSibling("PEOPLE.csv");
        Assume.assumeTrue("Requires a case-insensitive filesystem", Files.exists(uppercase));
        assertTrue(Files.isSameFile(tableFile(), uppercase));
        assertReplacementSerializesAppend(root(), root(), "PEOPLE");
    }

    /** Hold a replacement at its commit point while a second store tries to append. */
    private static void assertReplacementSerializesAppend(final Path writeRoot, final Path appendRoot,
            final String appendName) throws Exception {
        final CountDownLatch replacing = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch appending = new CountDownLatch(1);
        final TableStore writer = new TableStore(writeRoot, new TableStore.FileOperations() {
            @Override
            void replaceAtomically(final Path source, final Path target) throws IOException {
                replacing.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test release timed out");
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                super.replaceAtomically(source, target);
            }
        });
        final TableStore appender = new TableStore(appendRoot);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> replacement = executor.submit(() -> {
                writer.writeTable("people", Collections.singletonList("name"),
                        Collections.singletonList(Collections.singletonList("Replacement")));
                return null;
            });
            assertTrue(replacing.await(5, TimeUnit.SECONDS));
            final Future<?> append = executor.submit(() -> {
                appending.countDown();
                appender.addRow(appendName, Collections.singletonList("Appended"));
                return null;
            });
            assertTrue(appending.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> append.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            replacement.get(5, TimeUnit.SECONDS);
            append.get(5, TimeUnit.SECONDS);
            assertEquals(Arrays.asList(Collections.singletonList("Replacement"), Collections.singletonList("Appended")),
                    appender.readTable(appendName).getRows());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void symbolicTableFilesAreRejectedIncludingDanglingLinks() throws IOException {
        final TableStore store = initialStore();
        final Path outside = root().resolve("outside.csv");
        Files.writeString(outside, "name;city\nGrace;Paris\n", StandardCharsets.UTF_8);
        final byte[] before = Files.readAllBytes(outside);
        for (final Path target : Arrays.asList(outside, root().resolve("missing.csv"))) {
            final Path link = tableFile().resolveSibling("link.csv");
            createSymbolicLink(link, target);
            assertThrows(IOException.class, () -> store.readTable("link"));
            assertThrows(IOException.class, () -> store.addRow("link", Arrays.asList("New", "Row")));
            assertThrows(IOException.class, () -> store.editCell("link", 0, 0, "Updated"));
            assertThrows(IOException.class,
                    () -> store.writeTable("link", Collections.singletonList("name"), Collections.emptyList()));
            assertTrue(Files.isSymbolicLink(link));
            assertEquals(Collections.singletonList("people"), store.listTables());
            Files.delete(link);
        }
        assertArrayEquals(before, Files.readAllBytes(outside));
        assertFalse(Files.exists(root().resolve("missing.csv")));
        assertNoTemporaryFiles();
    }

    @Test
    public void hardLinkedTablesAreRejectedWhenLinkCountsAreAvailable() throws IOException {
        Assume.assumeTrue(root().getFileSystem().supportedFileAttributeViews().contains("unix"));
        final TableStore store = initialStore();
        final byte[] before = Files.readAllBytes(tableFile());
        final Path alias = tableFile().resolveSibling("alias.csv");
        Files.createLink(alias, tableFile());
        for (final String name : Arrays.asList("people", "alias")) {
            assertThrows(IOException.class, () -> store.readTable(name));
            assertThrows(IOException.class, () -> store.addRow(name, Arrays.asList("New", "Row")));
            assertThrows(IOException.class, () -> store.editCell(name, 0, 0, "Updated"));
            assertThrows(IOException.class,
                    () -> store.writeTable(name, Collections.singletonList("name"), Collections.emptyList()));
        }
        assertTrue(Files.isSameFile(alias, tableFile()));
        assertArrayEquals(before, Files.readAllBytes(alias));
        assertNoTemporaryFiles();
    }

    @Test
    public void directoryAtTablePathIsNeverReplaced() throws IOException {
        final TableStore store = initialStore();
        final Path directory = tableFile().resolveSibling("directory.csv");
        Files.createDirectory(directory);
        assertThrows(IOException.class,
                () -> store.writeTable("directory", Collections.singletonList("name"), Collections.emptyList()));
        assertTrue(Files.isDirectory(directory));
        assertNoTemporaryFiles();
    }

    @Test
    public void unsupportedAtomicReplacementLeavesExistingFileAndSnapshotIntact() throws IOException {
        initialStore();
        final byte[] before = Files.readAllBytes(tableFile());
        final TableStore failing = new TableStore(root(), new TableStore.FileOperations() {
            @Override
            void replaceAtomically(final Path source, final Path target) throws IOException {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected");
            }
        });
        final Table table = failing.openTable("people", Table.AccessMode.EDITABLE);
        final List<List<String>> snapshot = table.getRows();
        assertThrows(AtomicMoveNotSupportedException.class, () -> table.editCell(0, 0, "Updated"));
        assertThrows(AtomicMoveNotSupportedException.class,
                () -> failing.writeTable("new", Collections.singletonList("name"), Collections.emptyList()));
        assertArrayEquals(before, Files.readAllBytes(tableFile()));
        assertEquals(snapshot, table.getRows());
        assertFalse(Files.exists(tableFile().resolveSibling("new.csv")));
        assertNoTemporaryFiles();
    }

    @Test
    public void temporaryWriteAndCloseFailuresPreserveExistingData() throws IOException {
        initialStore();
        final byte[] before = Files.readAllBytes(tableFile());
        for (final boolean failOnClose : new boolean[] {false, true}) {
            final IOException failure = new IOException("injected temporary-file failure");
            final TableStore store = new TableStore(root(), new TableStore.FileOperations() {
                @Override
                BufferedWriter openWriter(final Path path) throws IOException {
                    return new BufferedWriter(super.openWriter(path)) {
                        @Override
                        public void write(final String text, final int offset, final int length) throws IOException {
                            super.write(text, offset, failOnClose ? length : Math.min(3, length));
                            flush();
                            if (!failOnClose) throw failure;
                        }

                        @Override
                        public void close() throws IOException {
                            super.close();
                            if (failOnClose) throw failure;
                        }
                    };
                }
            });
            final Table table = store.openTable("people", Table.AccessMode.EDITABLE);
            final List<List<String>> snapshot = table.getRows();
            assertSame(failure, assertThrows(IOException.class, () -> table.editCell(0, 0, "Updated")));
            assertArrayEquals(before, Files.readAllBytes(tableFile()));
            assertEquals(snapshot, table.getRows());
            assertNoTemporaryFiles();
        }
    }

    @Test
    public void cleanupFailureDoesNotHideReplacementFailure() throws IOException {
        final TableStore normal = initialStore();
        final byte[] before = Files.readAllBytes(tableFile());
        final IOException writeFailure = new IOException("replacement failed");
        final IOException cleanupFailure = new IOException("cleanup failed");
        final TableStore store = new TableStore(root(), new TableStore.FileOperations() {
            @Override
            void replaceAtomically(final Path source, final Path target) throws IOException {
                throw writeFailure;
            }

            @Override
            void deleteTemporaryFile(final Path path) throws IOException {
                throw cleanupFailure;
            }
        });
        assertSame(writeFailure, assertThrows(IOException.class,
                () -> store.writeTable("people", Collections.singletonList("name"), Collections.emptyList())));
        assertArrayEquals(new Throwable[] {cleanupFailure}, writeFailure.getSuppressed());
        assertArrayEquals(before, Files.readAllBytes(tableFile()));
        assertEquals(Collections.singletonList("people"), normal.listTables());
    }

    private static TableStore.FileOperations partialAppendFailure(final IOException failure) {
        return new TableStore.FileOperations() {
            @Override
            void write(final FileChannel channel, final ByteBuffer bytes) throws IOException {
                if (bytes.remaining() <= 2) {
                    super.write(channel, bytes); // Let any missing final newline reach disk first.
                    return;
                }
                final ByteBuffer fragment = bytes.slice();
                fragment.limit(3);
                while (fragment.hasRemaining()) channel.write(fragment);
                throw failure;
            }
        };
    }

    @Test
    public void partialAppendRollsBackIncludingInsertedNewline() throws IOException {
        final TableStore normal = initialStore();
        for (final String ending : Arrays.asList("", "\n", "\r\n")) {
            Files.writeString(tableFile(), "name;city\nAda;London" + ending, StandardCharsets.UTF_8);
            final byte[] before = Files.readAllBytes(tableFile());
            final IOException failure = new IOException("append failed");
            final TableStore store = new TableStore(root(), partialAppendFailure(failure));
            final Table table = store.openTable("people", Table.AccessMode.APPEND_ONLY);
            final List<List<String>> snapshot = table.getRows();
            assertSame(failure, assertThrows(IOException.class, () -> table.addRow(Arrays.asList("Grace", "Paris"))));
            assertArrayEquals(before, Files.readAllBytes(tableFile()));
            assertEquals(snapshot, table.getRows());
            normal.addRow("people", Arrays.asList("Grace", "Paris"));
            assertEquals(2, normal.readTable("people").getRows().size());
        }
    }

    @Test
    public void failedRollbackIsReportedWithoutUpdatingSnapshot() throws IOException {
        initialStore();
        final long originalSize = Files.size(tableFile());
        final IOException failure = new IOException("append failed");
        final IOException rollbackFailure = new IOException("rollback failed");
        final TableStore.FileOperations partial = partialAppendFailure(failure);
        final TableStore store = new TableStore(root(), new TableStore.FileOperations() {
            @Override
            void write(final FileChannel channel, final ByteBuffer bytes) throws IOException {
                partial.write(channel, bytes);
            }

            @Override
            void truncate(final FileChannel channel, final long size) throws IOException {
                throw rollbackFailure;
            }
        });
        final Table table = store.openTable("people", Table.AccessMode.APPEND_ONLY);
        final List<List<String>> snapshot = table.getRows();
        assertSame(failure, assertThrows(IOException.class, () -> table.addRow(Arrays.asList("Grace", "Paris"))));
        assertArrayEquals(new Throwable[] {rollbackFailure}, failure.getSuppressed());
        assertTrue(Files.size(tableFile()) > originalSize);
        assertEquals(snapshot, table.getRows());
        assertThrows(IOException.class, table::reload);
        assertEquals(snapshot, table.getRows());
    }

    @Test
    public void missingOrMalformedFilesLeaveOpenSnapshotsUnchanged() throws IOException {
        final TableStore store = initialStore();
        final Table table = store.openTable("people", Table.AccessMode.EDITABLE);
        final List<List<String>> rows = table.getRows();
        final List<String> header = table.getHeader();
        Files.delete(tableFile());
        assertThrows(IOException.class, table::reload);
        assertThrows(IOException.class, () -> table.editCell(0, 0, "Updated"));
        assertThrows(IOException.class, () -> table.addRow(Arrays.asList("Grace", "Paris")));
        assertFalse(Files.exists(tableFile()));
        Files.write(tableFile(), new byte[] {(byte) 0xC3, 0x28}); // Invalid UTF-8.
        assertThrows(IOException.class, table::reload);
        assertEquals(rows, table.getRows());
        assertEquals(header, table.getHeader());
        assertNoTemporaryFiles();
    }
}
