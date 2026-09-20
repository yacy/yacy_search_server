/**
 *  Table
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;

/**
 * A persistent table handle with an in-memory snapshot. Mutating methods are
 * available according to the selected {@link AccessMode} and persist their
 * changes before returning.
 * <p>
 * Each handle owns an independent snapshot. Getters do not read the file;
 * call {@link #reload()} to see changes made through other handles or the store.
 * Successful appends add only the new row to this handle's snapshot, so its row
 * indices may differ from the file until reloaded. An edit requires the entire
 * stored header and rows to still match this snapshot; otherwise it fails with
 * {@link ConcurrentModificationException} before writing. Appends require only
 * an unchanged header, including column names and order.
 * <p>
 * A rejected mutation leaves this snapshot unchanged. After a conflict, reload
 * and select the intended row/column again before retrying. Coordination covers
 * calls through {@link TableStore} in the same JVM, not external file writers.
 * On an I/O failure the handle remains unchanged, but the file may need inspection
 * before retrying; see {@link TableStore} for rollback and atomic-move guarantees.
 */
public final class Table {

    /** Controls which persistent operations a table handle may perform. */
    public enum AccessMode {
        /** Read the snapshot and reload it, but do not mutate the table. */
        READ_ONLY,
        /** Read the snapshot and efficiently append complete rows. */
        APPEND_ONLY,
        /** Append rows or edit arbitrary cells with immediate persistence. */
        EDITABLE
    }

    private final TableStore store;
    private final String tableName;
    private final AccessMode accessMode;
    private List<String> header;
    private List<List<String>> rows;

    Table(final TableStore store, final String tableName, final AccessMode accessMode,
            final TableStore.TableData data) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.accessMode = Objects.requireNonNull(accessMode, "accessMode must not be null");
        replaceSnapshot(data);
    }

    /** @return the table name including its file extension */
    public String getTableName() {
        return this.tableName;
    }

    /** @return the access mode selected when this handle was opened */
    public AccessMode getAccessMode() {
        return this.accessMode;
    }

    /** @return an immutable copy of the header from the current snapshot */
    public synchronized List<String> getHeader() {
        return Collections.unmodifiableList(new ArrayList<>(this.header));
    }

    /** @return an immutable deep copy of all rows from the current snapshot */
    public synchronized List<List<String>> getRows() {
        return immutableRowsCopy(this.rows);
    }

    /**
     * Reload the in-memory snapshot from persistent storage.
     * Header and rows are replaced together; a failed read leaves both unchanged.
     * Copies returned by earlier getter calls remain unchanged.
     *
     * @throws IOException when the table cannot be read or is malformed
     */
    public synchronized void reload() throws IOException {
        replaceSnapshot(this.store.readTableData(this.tableName));
    }

    /**
     * Edit a zero-based data cell and immediately rewrite the persistent file.
     * Only available in {@link AccessMode#EDITABLE} mode.
     * The entire current file must still match this handle's snapshot. Comparison
     * and writing share the same table lock, preventing stale indices or values
     * from overwriting another handle's changes. On success this snapshot also
     * contains the edited value; other handles are not refreshed.
     *
     * @param rowIndex zero-based data row index
     * @param columnIndex zero-based column index
     * @param value new cell value
     * @throws IOException when the table cannot be read or written
     * @throws ConcurrentModificationException when the stored header or rows
     *         differ from this snapshot; reload and reselect the cell before retrying
     */
    public synchronized void editCell(final int rowIndex, final int columnIndex, final String value)
            throws IOException {
        requireMode(AccessMode.EDITABLE, "edit cells");
        replaceSnapshot(this.store.editCellAndRead(this.tableName, rowIndex, columnIndex, value, snapshot()));
    }

    /**
     * Append a row directly to persistent storage without rewriting existing
     * data. Available in append-only and editable modes.
     * Column names and order must still match this snapshot. The row is appended
     * to the current file and to this snapshot, without importing intervening
     * changes from other writers. Call {@link #reload()} to obtain current row
     * indices before editing after another writer has changed the table.
     *
     * @param row cells to append; the number must match the header
     * @throws IOException when the table cannot be appended
     * @throws ConcurrentModificationException when the stored header differs
     *         from this snapshot; reload and adapt the row before retrying
     */
    public synchronized void addRow(final List<String> row) throws IOException {
        if (this.accessMode == AccessMode.READ_ONLY) {
            throw new IllegalStateException("Table was opened read-only; cannot append rows");
        }
        final List<String> validatedRow = copyAndValidateRow(row, this.header.size(), "appended row");
        this.store.addRow(this.tableName, validatedRow, this.header);
        this.rows.add(validatedRow);
    }

    synchronized TableStore.TableData snapshot() {
        return new TableStore.TableData(this.header, this.rows);
    }

    private void requireMode(final AccessMode requiredMode, final String operation) {
        if (this.accessMode != requiredMode) {
            throw new IllegalStateException("Table access mode " + this.accessMode + " cannot " + operation);
        }
    }

    private void replaceSnapshot(final TableStore.TableData data) {
        this.header = new ArrayList<>(data.header);
        this.rows = mutableRowsCopy(data.rows);
    }

    static List<String> validateHeader(final List<String> header) {
        Objects.requireNonNull(header, "header must not be null");
        if (header.isEmpty()) {
            throw new IllegalArgumentException("Table header must contain at least one column");
        }
        return copyAndValidateRow(header, header.size(), "header");
    }

    static List<String> copyAndValidateRow(final List<String> row, final int columnCount,
            final String description) {
        Objects.requireNonNull(row, description + " must not be null");
        if (row.size() != columnCount) {
            throw new IllegalArgumentException(description + " has " + row.size() + " cells; expected "
                    + columnCount);
        }
        final List<String> copy = new ArrayList<>(row.size());
        for (final String cell : row) {
            copy.add(validateCell(cell));
        }
        return copy;
    }

    static String validateCell(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Table cells must not be null");
        }
        if (value.indexOf(TableStore.SEPARATOR) >= 0) {
            throw new IllegalArgumentException("Table cells must not contain semicolons");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Table cells must not contain line breaks");
        }
        return value;
    }

    private static List<List<String>> mutableRowsCopy(final List<? extends List<String>> source) {
        final List<List<String>> copy = new ArrayList<>(source.size());
        for (final List<String> row : source) {
            copy.add(new ArrayList<>(row));
        }
        return copy;
    }

    private static List<List<String>> immutableRowsCopy(final List<? extends List<String>> source) {
        final List<List<String>> copy = new ArrayList<>(source.size());
        for (final List<String> row : source) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        return Collections.unmodifiableList(copy);
    }
}
