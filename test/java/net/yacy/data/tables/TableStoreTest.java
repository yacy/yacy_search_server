package net.yacy.data.tables;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TableStoreTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesListsAndReadsTable() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        store.writeTable("people", Arrays.asList("name", "city"), Arrays.asList(
                Arrays.asList("Ada", "London"),
                Arrays.asList("Grace", "New York")));

        assertEquals(Collections.singletonList("people"), store.listTables());
        final Table table = store.readTable("people.csv");
        assertEquals(Table.AccessMode.READ_ONLY, table.getAccessMode());
        assertEquals(Arrays.asList("name", "city"), table.getHeader());
        assertEquals(Arrays.asList(
                Arrays.asList("Ada", "London"),
                Arrays.asList("Grace", "New York")), table.getRows());

        final File csvFile = new File(this.temporaryFolder.getRoot(), "DATA/TABLES/people.csv");
        assertEquals("name;city\nAda;London\nGrace;New York\n",
                Files.readString(csvFile.toPath(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    @Test
    public void enforcesAccessModesAndPersistsMutationsImmediately() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        store.writeTable("people", Arrays.asList("name", "city"),
                Collections.singletonList(Arrays.asList("Ada", "London")));

        final Table readOnly = store.readTable("people");
        assertThrows(IllegalStateException.class, () -> readOnly.addRow(Arrays.asList("Grace", "New York")));
        assertThrows(IllegalStateException.class, () -> readOnly.editCell(0, 1, "Paris"));

        final Path tableFile = this.temporaryFolder.getRoot().toPath().resolve("DATA/TABLES/people.csv");
        final Object fileKeyBeforeAppend = Files.readAttributes(tableFile, BasicFileAttributes.class).fileKey();
        final Table appendOnly = store.openTable("people", Table.AccessMode.APPEND_ONLY);
        appendOnly.addRow(Arrays.asList("Grace", "New York"));
        final Object fileKeyAfterAppend = Files.readAttributes(tableFile, BasicFileAttributes.class).fileKey();
        if (fileKeyBeforeAppend != null && fileKeyAfterAppend != null) {
            assertEquals("Appending must not replace the table file", fileKeyBeforeAppend, fileKeyAfterAppend);
        }
        assertThrows(IllegalStateException.class, () -> appendOnly.editCell(0, 1, "Paris"));
        assertEquals(2, store.readTable("people").getRows().size());

        final Table editable = store.openTable("people", Table.AccessMode.EDITABLE);
        editable.editCell(0, 1, "Paris");
        editable.addRow(Arrays.asList("Katherine", "Hampton"));

        assertEquals(Arrays.asList(
                Arrays.asList("Ada", "Paris"),
                Arrays.asList("Grace", "New York"),
                Arrays.asList("Katherine", "Hampton")), store.readTable("people").getRows());
    }

    @Test
    public void reloadRefreshesSnapshotsAfterConcurrentChanges() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        store.writeTable("people", Collections.singletonList("name"),
                Collections.singletonList(Collections.singletonList("Ada")));
        final Table snapshot = store.readTable("people");

        store.addRow("people", Collections.singletonList("Grace"));
        assertEquals(1, snapshot.getRows().size());
        snapshot.reload();
        assertEquals(2, snapshot.getRows().size());
    }

    @Test
    public void concurrentHandlesKeepLocalSnapshotsAndRejectStaleRowIndices() throws IOException {
        final TableStore firstStore = new TableStore(this.temporaryFolder.getRoot());
        final TableStore secondStore = new TableStore(this.temporaryFolder.getRoot());
        final List<String> header = Collections.singletonList("name");
        final List<String> ada = Collections.singletonList("Ada");
        final List<String> grace = Collections.singletonList("Grace");
        final List<String> katherine = Collections.singletonList("Katherine");
        firstStore.writeTable("people", header, Collections.singletonList(ada));
        final Table first = firstStore.openTable("people", Table.AccessMode.EDITABLE);
        final Table second = secondStore.openTable("people", Table.AccessMode.EDITABLE);
        final List<List<String>> originalSnapshot = second.getRows();

        first.addRow(grace);
        second.addRow(katherine);
        assertEquals(Arrays.asList(ada, grace), first.getRows());
        assertEquals(Arrays.asList(ada, katherine), second.getRows());
        final List<List<String>> persisted = Arrays.asList(ada, grace, katherine);
        assertEquals(persisted, firstStore.readTable("people").getRows());

        // Row 1 in the second handle is Katherine, but row 1 on disk is Grace.
        assertThrows(ConcurrentModificationException.class, () -> second.editCell(1, 0, "Updated"));
        assertEquals(persisted, firstStore.readTable("people").getRows());
        assertEquals(Arrays.asList(ada, katherine), second.getRows());
        second.reload();
        second.editCell(2, 0, "Updated");
        assertEquals(Arrays.asList(ada, grace, Collections.singletonList("Updated")), second.getRows());
        assertEquals(second.getRows(), firstStore.readTable("people").getRows());
        assertEquals(Arrays.asList(ada, grace), first.getRows());
        first.reload();
        assertEquals(second.getRows(), first.getRows());
        assertEquals(Collections.singletonList(ada), originalSnapshot);
    }

    @Test
    public void staleCellEditsRequireReloadAfterAnotherHandleWrites() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        store.writeTable("people", Arrays.asList("name", "city"),
                Collections.singletonList(Arrays.asList("Ada", "London")));
        final Table first = store.openTable("people", Table.AccessMode.EDITABLE);
        final Table second = store.openTable("people", Table.AccessMode.EDITABLE);
        first.editCell(0, 1, "Paris");
        assertThrows(ConcurrentModificationException.class, () -> second.editCell(0, 1, "Rome"));
        assertEquals(Collections.singletonList(Arrays.asList("Ada", "London")), second.getRows());
        assertEquals(first.getRows(), store.readTable("people").getRows());
        second.reload();
        second.editCell(0, 1, "Rome");
        assertEquals(Collections.singletonList(Arrays.asList("Ada", "Rome")), store.readTable("people").getRows());
    }

    @Test
    public void competingHandleEditsAllowExactlyOneWriterUntilReload() throws Exception {
        final TableStore firstStore = new TableStore(this.temporaryFolder.getRoot());
        final TableStore secondStore = new TableStore(this.temporaryFolder.getRoot());
        firstStore.writeTable("people", Collections.singletonList("name"),
                Collections.singletonList(Collections.singletonList("Original")));
        final Table first = firstStore.openTable("people", Table.AccessMode.EDITABLE);
        final Table second = secondStore.openTable("people", Table.AccessMode.EDITABLE);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final Future<Boolean> firstEdit = executor.submit(() -> {
                start.await();
                try {
                    first.editCell(0, 0, "First");
                    return true;
                } catch (final ConcurrentModificationException expected) {
                    return false;
                }
            });
            final Future<Boolean> secondEdit = executor.submit(() -> {
                start.await();
                try {
                    second.editCell(0, 0, "Second");
                    return true;
                } catch (final ConcurrentModificationException expected) {
                    return false;
                }
            });
            start.countDown();
            final boolean firstWon = firstEdit.get(20, TimeUnit.SECONDS);
            final boolean secondWon = secondEdit.get(20, TimeUnit.SECONDS);
            assertTrue("Exactly one handle may edit the original snapshot", firstWon ^ secondWon);
            final Table winner = firstWon ? first : second;
            final Table loser = firstWon ? second : first;
            assertEquals(winner.getRows(), firstStore.readTable("people").getRows());
            assertEquals(Collections.singletonList(Collections.singletonList("Original")), loser.getRows());
            loser.reload();
            assertEquals(winner.getRows(), loser.getRows());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void replacedTableRequiresReloadBeforeEditingEvenWithTheSameHeader() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        final List<String> header = Arrays.asList("name", "city");
        store.writeTable("people", header, Collections.singletonList(Arrays.asList("Ada", "London")));
        final Table table = store.openTable("people", Table.AccessMode.EDITABLE);
        final List<List<String>> replacement = Collections.singletonList(Arrays.asList("Grace", "New York"));
        store.writeTable("people", header, replacement);
        assertThrows(ConcurrentModificationException.class, () -> table.editCell(0, 1, "Paris"));
        assertEquals(replacement, store.readTable("people").getRows());
        table.reload();
        table.editCell(0, 1, "Paris");
        assertEquals(Collections.singletonList(Arrays.asList("Grace", "Paris")), table.getRows());
    }

    @Test
    public void changedColumnsRejectHandleMutationsUntilReload() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        for (final List<String> changedHeader : Arrays.asList(
                Arrays.asList("city", "name"), Arrays.asList("name", "country"),
                Arrays.asList("name", "city", "country"), Collections.singletonList("name"))) {
            final List<String> originalHeader = Arrays.asList("name", "city");
            store.writeTable("people", originalHeader, Collections.emptyList());
            final Table editable = store.openTable("people", Table.AccessMode.EDITABLE);
            final Table appendOnly = store.openTable("people", Table.AccessMode.APPEND_ONLY);
            store.writeTable("people", changedHeader, Collections.emptyList());
            final Path file = this.temporaryFolder.getRoot().toPath().resolve("DATA/TABLES/people.csv");
            final String before = Files.readString(file, StandardCharsets.UTF_8);
            assertThrows(ConcurrentModificationException.class,
                    () -> editable.addRow(Arrays.asList("Ada", "London")));
            assertThrows(ConcurrentModificationException.class,
                    () -> appendOnly.addRow(Arrays.asList("Grace", "New York")));
            assertThrows(ConcurrentModificationException.class, () -> editable.editCell(0, 0, "Updated"));
            assertEquals(before, Files.readString(file, StandardCharsets.UTF_8));
            assertEquals(originalHeader, editable.getHeader());
            assertEquals(originalHeader, appendOnly.getHeader());
            assertTrue(editable.getRows().isEmpty());
            assertTrue(appendOnly.getRows().isEmpty());

            appendOnly.reload();
            assertEquals(changedHeader, appendOnly.getHeader());
            final List<String> row = Collections.nCopies(changedHeader.size(), "value");
            appendOnly.addRow(row);
            assertEquals(Collections.singletonList(row), store.readTable("people").getRows());
            editable.reload();
            editable.editCell(0, 0, "Updated");
            assertEquals("Updated", store.readTable("people").getRows().get(0).get(0));
        }
    }

    @Test
    public void storeOperationsUseCurrentDataAndSnapshotExportExplicitlyReplacesIt() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        store.writeTable("people", Collections.singletonList("name"),
                Collections.singletonList(Collections.singletonList("Ada")));
        final Table original = store.readTable("people");
        store.writeTable("people", Arrays.asList("name", "city"),
                Collections.singletonList(Arrays.asList("Grace", "New York")));
        store.addRow("people", Arrays.asList("Katherine", "Hampton"));
        store.editCell("people", 0, 1, "Paris");
        assertEquals(Arrays.asList(Arrays.asList("Grace", "Paris"), Arrays.asList("Katherine", "Hampton")),
                store.readTable("people").getRows());
        assertEquals(Collections.singletonList("name"), original.getHeader());
        // Export is an explicit complete replacement, including when its source snapshot is stale.
        store.writeTable("people", original);
        assertEquals(original.getHeader(), store.readTable("people").getHeader());
        assertEquals(original.getRows(), store.readTable("people").getRows());
    }

    @Test
    public void rejectsSemicolonsThroughAllMutationPaths() throws IOException {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        assertThrows(IllegalArgumentException.class, () -> store.writeTable("invalid-header",
                Arrays.asList("name", "ci;ty"), Collections.emptyList()));

        store.writeTable("people", Arrays.asList("name", "city"),
                Collections.singletonList(Arrays.asList("Ada", "London")));
        final Table appendOnly = store.openTable("people", Table.AccessMode.APPEND_ONLY);
        final Table editable = store.openTable("people", Table.AccessMode.EDITABLE);
        assertThrows(IllegalArgumentException.class,
                () -> appendOnly.addRow(Arrays.asList("Grace;Hopper", "New York")));
        assertThrows(IllegalArgumentException.class, () -> editable.editCell(0, 1, "Paris;France"));
        assertThrows(IllegalArgumentException.class,
                () -> store.addRow("people", Arrays.asList("Grace", "New;York")));
    }

    @Test
    public void boundsLockCountAcrossManyMissingTables() throws Exception {
        final Path root = this.temporaryFolder.getRoot().toPath();
        final TableStore store = new TableStore(root);
        store.listTables(); // Ensure missing-file reads reach the shared locking path.
        // Lock identities expose retained synchronization resources without relying on GC or heap-size estimates.
        final Method lockFor = TableStore.class.getDeclaredMethod("lockFor", Path.class);
        lockFor.setAccessible(true);
        final Set<ReadWriteLock> locks = Collections.newSetFromMap(new IdentityHashMap<>());
        final Path firstTable = root.resolve("DATA/TABLES/missing-0.csv");
        final ReadWriteLock firstLock = (ReadWriteLock) lockFor.invoke(null, firstTable);
        for (int i = 0; i < 4096; i++) {
            final String name = "missing-" + i;
            assertThrows(IOException.class, () -> store.readTable(name));
            locks.add((ReadWriteLock) lockFor.invoke(null, root.resolve("DATA/TABLES/" + name + ".csv")));
        }
        assertTrue("Distinct table names must use at most 256 shared locks, got " + locks.size(),
                locks.size() <= 256);
        assertSame("A table's lock must remain stable after other lookups", firstLock,
                lockFor.invoke(null, firstTable));
    }

    @Test
    public void serializesConcurrentAppendsAndEditsAcrossStoreInstances() throws Exception {
        final TableStore firstStore = new TableStore(this.temporaryFolder.getRoot());
        final TableStore secondStore = new TableStore(this.temporaryFolder.getRoot().toPath().resolve("unused/.."));
        firstStore.writeTable("events", Arrays.asList("source", "value"),
                Collections.singletonList(Arrays.asList("root", "initial")));

        final int appenderCount = 4;
        final int rowsPerAppender = 40;
        final ExecutorService executor = Executors.newFixedThreadPool(appenderCount + 1);
        final CountDownLatch start = new CountDownLatch(1);
        final Future<?>[] futures = new Future<?>[appenderCount + 1];
        try {
            for (int thread = 0; thread < appenderCount; thread++) {
                final int threadIndex = thread;
                futures[thread] = executor.submit(() -> {
                    start.await();
                    final TableStore store = threadIndex % 2 == 0 ? firstStore : secondStore;
                    for (int row = 0; row < rowsPerAppender; row++) {
                        store.addRow(threadIndex % 2 == 0 ? "events" : "events.csv",
                                Arrays.asList("thread-" + threadIndex, "row-" + row));
                    }
                    return null;
                });
            }
            futures[appenderCount] = executor.submit(() -> {
                start.await();
                for (int edit = 0; edit < 10; edit++) {
                    secondStore.editCell("events", 0, 1, "edit-" + edit);
                }
                return null;
            });

            start.countDown();
            for (final Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        final List<List<String>> rows = firstStore.readTable("events").getRows();
        assertEquals(1 + appenderCount * rowsPerAppender, rows.size());
        assertEquals(Arrays.asList("root", "edit-9"), rows.get(0));
        final Set<String> appendedRows = new HashSet<>();
        for (int row = 1; row < rows.size(); row++) {
            appendedRows.add(rows.get(row).get(0) + ":" + rows.get(row).get(1));
        }
        assertEquals(appenderCount * rowsPerAppender, appendedRows.size());
    }

    @Test
    public void rejectsMissingHeaderAndRowsWithWrongWidth() throws IOException {
        final File tablesDirectory = new File(this.temporaryFolder.getRoot(), "DATA/TABLES");
        Files.createDirectories(tablesDirectory.toPath());
        Files.writeString(new File(tablesDirectory, "empty.csv").toPath(), "", StandardCharsets.UTF_8);
        Files.writeString(new File(tablesDirectory, "wide.csv").toPath(), "a;b\n1;2;3\n",
                StandardCharsets.UTF_8);

        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        assertThrows(IOException.class, () -> store.readTable("empty"));
        assertThrows(IOException.class, () -> store.readTable("wide"));
        assertThrows(IllegalArgumentException.class, () -> store.writeTable("no-header",
                Collections.emptyList(), Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> store.writeTable("wrong-width",
                Arrays.asList("a", "b"), Collections.singletonList(Collections.singletonList("1"))));
    }

    @Test
    public void rejectsPathsAsTableNames() {
        final TableStore store = new TableStore(this.temporaryFolder.getRoot());
        assertThrows(IllegalArgumentException.class, () -> store.readTable("../outside"));
        assertThrows(IllegalArgumentException.class, () -> store.readTable("nested/table"));
        assertThrows(IllegalArgumentException.class, () -> store.readTable("nested\\table"));
        assertThrows(IllegalArgumentException.class, () -> store.readTable(".csv"));
    }
}
