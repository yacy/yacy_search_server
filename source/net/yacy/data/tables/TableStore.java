/**
 *  TableStore
 *  Copyright 2026 by Michael Peter Christen
 *  First released 29.08.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.data.tables;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thread-safe access to UTF-8 encoded, semicolon-separated CSV tables below
 * {@code DATA/TABLES}.
 * <p>
 * This deliberately implements a restricted CSV dialect without quoting or
 * escaping. Every file has a header row, all rows have the same number of
 * cells and cells must not contain semicolons or line breaks.
 * <p>
 * Directory aliases are resolved before acquiring shared JVM locks. Table files
 * must be regular files, not symbolic links; hard links are rejected on file
 * systems exposing a Unix link count. Hard-link aliases on other file systems
 * and concurrent external file writers are unsupported.
 * <p>
 * Replacements use a temporary file in the same directory and require an atomic
 * move. An unsupported atomic move throws {@link AtomicMoveNotSupportedException}
 * without a non-atomic fallback. Failed
 * appends attempt to restore the original file length before releasing the lock;
 * a rollback failure is attached as a suppressed exception. After an I/O failure,
 * inspect/reload the file before retrying, especially if rollback or closing the
 * channel failed. This is not a crash-recovery protocol: writes are not forced to
 * stable storage and appends are not atomic across process or power failures.
 */
public final class TableStore {

    public static final String TABLES_PATH = "DATA/TABLES";
    public static final String FILE_EXTENSION = ".csv";
    public static final char SEPARATOR = ';';

    /**
     * A fixed pool shared by all store instances, without retaining table paths.
     * Hash collisions only serialize otherwise independent tables.
     */
    private static final ReadWriteLock[] TABLE_LOCKS = new ReadWriteLock[256];
    static {
        for (int i = 0; i < TABLE_LOCKS.length; i++) {
            TABLE_LOCKS[i] = new ReentrantReadWriteLock(true);
        }
    }

    private final Path tablesDirectory;
    private final FileOperations files;

    /**
     * Create a table store rooted at YaCy's data path.
     *
     * @param dataPath the YaCy data root containing the {@code DATA} directory
     */
    public TableStore(final File dataPath) {
        this(Objects.requireNonNull(dataPath, "dataPath must not be null").toPath());
    }

    /**
     * Create a table store rooted at YaCy's data path.
     *
     * @param dataPath the YaCy data root containing the {@code DATA} directory
     */
    public TableStore(final Path dataPath) {
        this(dataPath, new FileOperations());
    }

    /** Package-private I/O boundary for deterministic write-failure tests. */
    TableStore(final Path dataPath, final FileOperations files) {
        Objects.requireNonNull(dataPath, "dataPath must not be null");
        this.tablesDirectory = dataPath.toAbsolutePath().normalize().resolve(TABLES_PATH);
        this.files = Objects.requireNonNull(files, "files must not be null");
    }

    /**
     * List table names in deterministic order. Names do not include the
     * {@code .csv} extension.
     *
     * @return the table names
     * @throws IOException when the table directory cannot be accessed
     */
    public List<String> listTables() throws IOException {
        Files.createDirectories(this.tablesDirectory);
        try (Stream<Path> entries = Files.list(this.tablesDirectory.toRealPath())) {
            return entries
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(FILE_EXTENSION))
                    .map(name -> name.substring(0, name.length() - FILE_EXTENSION.length()))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Open a read-only table snapshot.
     *
     * @param tableName table name, with or without the {@code .csv} extension
     * @return the read-only table
     * @throws IOException when the table cannot be read or is malformed
     */
    public Table readTable(final String tableName) throws IOException {
        return openTable(tableName, Table.AccessMode.READ_ONLY);
    }

    /**
     * Open a table with explicit mutation capabilities. All mutations on the
     * returned table are persisted before the method returns.
     * Each call returns an independent snapshot: see {@link Table} for reload
     * and conflict handling when multiple handles refer to the same file.
     *
     * @param tableName table name, with or without the {@code .csv} extension
     * @param accessMode requested access mode
     * @return a persistent table handle with an initial in-memory snapshot
     * @throws IOException when the table cannot be read or is malformed
     */
    public Table openTable(final String tableName, final Table.AccessMode accessMode) throws IOException {
        Objects.requireNonNull(accessMode, "accessMode must not be null");
        final Path tableFile = resolveTable(tableName);
        return new Table(this, tableFile.getFileName().toString(), accessMode, readTableDataLocked(tableFile));
    }

    /**
     * Validate and atomically replace a table.
     * Writes exactly the supplied handle's snapshot, without reloading or merging
     * newer stored data. This is an explicit full replacement, even when the
     * snapshot is stale. Existing handles are not refreshed.
     *
     * @param tableName table name, with or without the {@code .csv} extension
     * @param table table contents, including a non-empty header
     * @throws IOException when the table cannot be written
     */
    public void writeTable(final String tableName, final Table table) throws IOException {
        Objects.requireNonNull(table, "table must not be null");
        writeTableData(resolveTable(tableName, true), table.snapshot());
    }

    /**
     * Validate and atomically replace a table.
     * This explicit replacement does not check existing snapshots for conflicts
     * or refresh open handles; use {@link Table#reload()} to read the replacement.
     *
     * @param tableName table name, with or without the {@code .csv} extension
     * @param header column names
     * @param rows data rows
     * @throws IOException when the table cannot be written
     */
    public void writeTable(final String tableName, final List<String> header,
            final List<? extends List<String>> rows) throws IOException {
        final TableData data = new TableData(header, rows);
        writeTableData(resolveTable(tableName, true), data);
    }

    /**
     * Edit a zero-based data cell and immediately replace the complete file.
     * The current file is read after acquiring its exclusive lock, preventing
     * concurrent appends or edits from being overwritten.
     * Indices address the current stored data. Unlike {@link Table#editCell},
     * this method has no snapshot to check: the last write to a cell wins and
     * open handles are not refreshed.
     *
     * @param tableName table name, with or without the {@code .csv} extension
     * @param rowIndex zero-based data row index
     * @param columnIndex zero-based column index
     * @param value new cell value
     * @throws IOException when the table cannot be read or written
     * @throws IndexOutOfBoundsException when an index is outside the table
     * @throws IllegalArgumentException when the value is not a valid cell
     */
    public void editCell(final String tableName, final int rowIndex, final int columnIndex,
            final String value) throws IOException {
        editCellAndRead(tableName, rowIndex, columnIndex, value, null);
    }

    /**
     * Validate and append one row without reading or rewriting all data rows.
     * Uses the current stored header and leaves all open handles unchanged.
     * Unlike {@link Table#addRow}, no earlier header snapshot is checked.
     *
     * @param tableName table name, with or without the {@code .csv} extension
     * @param row cells to append; the number must match the header
     * @throws IOException when the table cannot be read or appended
     * @throws IllegalArgumentException when the row is malformed
     */
    public void addRow(final String tableName, final List<String> row) throws IOException {
        addRow(tableName, row, null);
    }

    void addRow(final String tableName, final List<String> row, final List<String> expectedHeader) throws IOException {
        final Path tableFile = resolveTable(tableName);
        final Lock lock = lockFor(tableFile).writeLock();
        lock.lock();
        try {
            appendRowFile(tableFile, row, expectedHeader);
        } finally {
            lock.unlock();
        }
    }

    TableData readTableData(final String tableName) throws IOException {
        return readTableDataLocked(resolveTable(tableName));
    }

    TableData editCellAndRead(final String tableName, final int rowIndex, final int columnIndex,
            final String value, final TableData expectedSnapshot) throws IOException {
        final Path tableFile = resolveTable(tableName);
        final Lock lock = lockFor(tableFile).writeLock();
        lock.lock();
        try {
            final TableData data = readTableFile(tableFile);
            if (expectedSnapshot != null && (!data.header.equals(expectedSnapshot.header)
                    || !data.rows.equals(expectedSnapshot.rows))) {
                throw new ConcurrentModificationException("Table has changed; reload and reselect the cell before editing");
            }
            data.rows.get(rowIndex).set(columnIndex, Table.validateCell(value));
            writeTableFile(tableFile, data);
            return data.copy();
        } finally {
            lock.unlock();
        }
    }

    private TableData readTableDataLocked(final Path tableFile) throws IOException {
        final Lock lock = lockFor(tableFile).readLock();
        lock.lock();
        try {
            return readTableFile(tableFile);
        } finally {
            lock.unlock();
        }
    }

    private TableData readTableFile(final Path tableFile) throws IOException {
        checkTableFile(tableFile, true);

        final List<String> header;
        final List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(tableFile, StandardCharsets.UTF_8)) {
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                throw malformed(tableFile, "missing header row");
            }
            header = splitRow(headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                final List<String> row = splitRow(line);
                if (row.size() != header.size()) {
                    throw malformed(tableFile, "line " + lineNumber + " has " + row.size()
                            + " cells; expected " + header.size());
                }
                rows.add(row);
            }
        }

        try {
            return new TableData(header, rows);
        } catch (final IllegalArgumentException e) {
            throw malformed(tableFile, e.getMessage());
        }
    }

    private void writeTableData(final Path tableFile, final TableData data) throws IOException {
        final Lock lock = lockFor(tableFile).writeLock();
        lock.lock();
        try {
            writeTableFile(tableFile, data);
        } finally {
            lock.unlock();
        }
    }

    /** Caller must hold the table's write lock. */
    private void writeTableFile(final Path tableFile, final TableData data) throws IOException {
        final TableData snapshot = data.copy();
        checkTableFile(tableFile, false);

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(tableFile.getParent(), ".table-", ".tmp");
            try (BufferedWriter writer = this.files.openWriter(temporaryFile)) {
                writeRow(writer, snapshot.header);
                for (final List<String> row : snapshot.rows) {
                    writeRow(writer, row);
                }
            }
            this.files.replaceAtomically(temporaryFile, tableFile);
        } catch (final IOException | RuntimeException | Error failure) {
            if (temporaryFile != null) {
                try {
                    this.files.deleteTemporaryFile(temporaryFile);
                } catch (final IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /** Caller must hold the table's write lock. */
    private void appendRowFile(final Path tableFile, final List<String> row, final List<String> expectedHeader)
            throws IOException {
        checkTableFile(tableFile, true);

        final List<String> header;
        try (BufferedReader reader = Files.newBufferedReader(tableFile, StandardCharsets.UTF_8)) {
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                throw malformed(tableFile, "missing header row");
            }
            try {
                header = Table.validateHeader(splitRow(headerLine));
            } catch (final IllegalArgumentException e) {
                throw malformed(tableFile, e.getMessage());
            }
        }

        if (expectedHeader != null && !header.equals(expectedHeader)) {
            throw new ConcurrentModificationException("Table columns have changed; reload and adapt the row before appending");
        }
        final List<String> validatedRow = Table.copyAndValidateRow(row, header.size(), "appended row");
        final byte[] rowBytes = serializeRow(validatedRow).getBytes(StandardCharsets.UTF_8);

        try (FileChannel channel = FileChannel.open(tableFile, StandardOpenOption.READ, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            final long size = channel.size();
            boolean needsLineBreak = false;
            if (size > 0) {
                final ByteBuffer lastByte = ByteBuffer.allocate(1);
                channel.position(size - 1);
                while (lastByte.hasRemaining() && channel.read(lastByte) >= 0) {
                    // Read the final byte completely.
                }
                lastByte.flip();
                if (lastByte.hasRemaining()) {
                    final byte last = lastByte.get();
                    needsLineBreak = last != '\n' && last != '\r';
                }
            }

            channel.position(size);
            try {
                if (needsLineBreak) {
                    writeFully(channel, ByteBuffer.wrap(System.lineSeparator().getBytes(StandardCharsets.UTF_8)));
                }
                writeFully(channel, ByteBuffer.wrap(rowBytes));
            } catch (final IOException failure) {
                try {
                    this.files.truncate(channel, size);
                } catch (final IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private Path resolveTable(final String tableName) throws IOException {
        return resolveTable(tableName, false);
    }

    private Path resolveTable(final String tableName, final boolean createDirectory) throws IOException {
        Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isEmpty() || FILE_EXTENSION.equals(tableName)) {
            throw new IllegalArgumentException("tableName must not be empty");
        }
        if (tableName.indexOf('/') >= 0 || tableName.indexOf('\\') >= 0
                || ".".equals(tableName) || "..".equals(tableName)) {
            throw new IllegalArgumentException("tableName must be a file name without a path");
        }

        final String fileName = tableName.endsWith(FILE_EXTENSION) ? tableName : tableName + FILE_EXTENSION;
        if (createDirectory) Files.createDirectories(this.tablesDirectory);
        return this.tablesDirectory.toRealPath().resolve(fileName);
    }

    private static ReadWriteLock lockFor(final Path tableFile) {
        // Also serialize case variants on case-insensitive file systems, including
        // first creation. Extra collisions on case-sensitive systems are harmless.
        final int hash = tableFile.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT).hashCode();
        return TABLE_LOCKS[Math.floorMod(hash, TABLE_LOCKS.length)];
    }

    private static void checkTableFile(final Path tableFile, final boolean mustExist) throws IOException {
        final BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(tableFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (final NoSuchFileException missing) {
            if (mustExist) throw missing;
            return;
        }
        if (!attributes.isRegularFile()) {
            throw new IOException("Table is not a regular file (symbolic links are unsupported): " + tableFile.getFileName());
        }
        if (tableFile.getFileSystem().supportedFileAttributeViews().contains("unix")
                && ((Number) Files.getAttribute(tableFile, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() > 1) {
            throw new IOException("Hard-linked tables are unsupported: " + tableFile.getFileName());
        }
    }

    private static List<String> splitRow(final String line) {
        final String[] cells = line.split(String.valueOf(SEPARATOR), -1);
        final List<String> row = new ArrayList<>(cells.length);
        Collections.addAll(row, cells);
        return row;
    }

    private static void writeRow(final BufferedWriter writer, final List<String> row) throws IOException {
        writer.write(serializeRow(row));
    }

    private static String serializeRow(final List<String> row) {
        final StringBuilder serialized = new StringBuilder();
        for (int column = 0; column < row.size(); column++) {
            if (column > 0) {
                serialized.append(SEPARATOR);
            }
            serialized.append(row.get(column));
        }
        return serialized.append(System.lineSeparator()).toString();
    }

    private void writeFully(final FileChannel channel, final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            this.files.write(channel, buffer);
        }
    }

    static class FileOperations {
        BufferedWriter openWriter(final Path path) throws IOException {
            return Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        }

        void replaceAtomically(final Path source, final Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        void deleteTemporaryFile(final Path path) throws IOException {
            Files.deleteIfExists(path);
        }

        void write(final FileChannel channel, final ByteBuffer bytes) throws IOException {
            channel.write(bytes);
        }

        void truncate(final FileChannel channel, final long size) throws IOException {
            channel.truncate(size);
        }
    }

    private static IOException malformed(final Path tableFile, final String reason) {
        return new IOException("Malformed table " + tableFile.getFileName() + ": " + reason);
    }

    static final class TableData {

        final List<String> header;
        final List<List<String>> rows;

        TableData(final List<String> header, final List<? extends List<String>> rows) {
            this.header = Table.validateHeader(header);
            this.rows = new ArrayList<>(rows.size());
            int rowIndex = 0;
            for (final List<String> row : rows) {
                this.rows.add(Table.copyAndValidateRow(row, this.header.size(), "row " + rowIndex));
                rowIndex++;
            }
        }

        private TableData copy() {
            return new TableData(this.header, this.rows);
        }
    }
}
