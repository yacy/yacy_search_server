// Table.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2008 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.table;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.HandleMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.index.RowCollection;
import net.yacy.kelondro.index.RowHandleMap;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.io.BufferedRecords;
import net.yacy.kelondro.io.Records;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.kelondroException;


/*
 * The Table builds upon the EcoFS and tries to reduce the number of IO requests that the
 * EcoFS must do to a minimum. In best cases, no IO has to be done for read operations (complete database shadow in RAM)
 * and a rare number of write IO operations must be done for a large number of table-writings (using the write buffer of EcoFS)
 * To make the Table scalable in question of available RAM, there are two elements that must be scalable:
 * - the access index can be either completely in RAM (kelondroRAMIndex) or it is file-based (kelondroTree)
 * - the content cache can be either a complete RAM-based shadow of the File, or empty.
 * The content cache can also be deleted during run-time, if the available RAM gets too low.
 */

public class Table implements Index, Iterable<Row.Entry> {

    // static tracker objects
    private final static ConcurrentLog log = new ConcurrentLog("TABLE");
    private final static TreeMap<String, Table> tableTracker = new TreeMap<String, Table>();
    private final static long maxarraylength = 134217727L; // (2^27-1) that may be the maximum size of array length in some JVMs

    private final long minmemremaining; // if less than this memory is remaininig, the memory copy of a table is abandoned
    private final int buffersize;
    private final Row rowdef;
    private final Row taildef;
    private       HandleMap index;
    private       BufferedRecords file;
    private       RowSet table;

    public Table(
    		final File tablefile,
    		final Row rowdef,
    		final int buffersize,
    		final int initialSpace,
    		boolean useTailCache,
    		final boolean exceed134217727,
    		final boolean warmUp) throws SpaceExceededException, kelondroException {

        this.rowdef = rowdef;
        this.buffersize = buffersize;
        this.minmemremaining = Math.max(200L * 1024L * 1024L, MemoryControl.available() / 10);
        //this.fail = 0;
        // define the taildef, a row like the rowdef but without the first column
        final Column[] cols = new Column[rowdef.columns() - 1];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = rowdef.column(i + 1);
        }
        this.taildef = new Row(cols, NaturalOrder.naturalOrder);

        // initialize table file
        boolean freshFile = false;
        if (!tablefile.exists()) {
            // make new file
            freshFile = true;
            FileOutputStream fos = null;
            tablefile.getParentFile().mkdirs();
            try {
                fos = new FileOutputStream(tablefile);
            } catch (final FileNotFoundException e) {
                // should not happen
                log.severe("", e);
            } finally {
                if (fos != null) try { fos.close(); } catch (final IOException e) {}
            }
        }

        try {
            // open an existing table file
            final int fileSize = (int) tableSize(tablefile, rowdef.objectsize, true);

            // initialize index and copy table
            final int  records = Math.max(fileSize, initialSpace);
            final long neededRAM4table = 200L * 1024L * 1024L + records * (this.taildef.objectsize + rowdef.primaryKeyLength + 4L) * 3L / 2L;
            this.table = null;
            
            try {
                this.table = ((exceed134217727 || neededRAM4table < maxarraylength) &&
            	    	      useTailCache && MemoryControl.available() > 600L * 1024L * 1024L &&
            	    	      MemoryControl.request(neededRAM4table, true)) ? new RowSet(this.taildef, records) : null;
            } catch (final SpaceExceededException e) {
            	this.table = null;
            } catch (final Throwable e) {
            	this.table = null;
            }
            
            if (log.isFine()) log.fine("initialization of " + tablefile.getName() + ". table copy: " + ((this.table == null) ? "no" : "yes") + ", available RAM: " + (MemoryControl.available() / 1024L / 1024L) + "MB, needed: " + (neededRAM4table / 1024L / 1024L) + "MB, allocating space for " + records + " entries");
            final long neededRAM4index = 100L * 1024L * 1024L + records * (rowdef.primaryKeyLength + 4L) * 3L / 2L;
            if (records > 0 && !MemoryControl.request(neededRAM4index, true)) {
                // despite calculations seemed to show that there is enough memory for the table AND the index
                // there is now not enough memory left for the index. So delete the table again to free the memory
                // for the index
                log.severe(tablefile.getName() + ": not enough RAM (" + (MemoryControl.available() / 1024L / 1024L) + "MB) left for index, deleting allocated table space to enable index space allocation (needed: " + (neededRAM4index / 1024L / 1024L) + "MB)");
                this.table = null; System.gc();
                log.severe(tablefile.getName() + ": RAM after releasing the table: " + (MemoryControl.available() / 1024L / 1024L) + "MB");
            }
            this.index = new RowHandleMap(rowdef.primaryKeyLength, rowdef.objectOrder, 4, records, tablefile.getAbsolutePath());
            final RowHandleMap errors = new RowHandleMap(rowdef.primaryKeyLength, NaturalOrder.naturalOrder, 4, records, tablefile.getAbsolutePath() + ".errors");
            if (log.isFine()) log.fine(tablefile + ": TABLE " + tablefile.toString() + " has table copy " + ((this.table == null) ? "DISABLED" : "ENABLED"));

            // read all elements from the file into the copy table
            if (log.isFine()) log.fine("initializing RAM index for TABLE " + tablefile.getName() + ", please wait.");
            int i = 0;
            byte[] key;
            if (this.table == null) {
                final Iterator<byte[]> ki = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.primaryKeyLength);
                while (ki.hasNext()) {
                    key = ki.next();
                    // write the key into the index table
                    assert key != null;
                    if (key == null) {i++; continue;}
                    if (rowdef.objectOrder.wellformed(key)) {
                        this.index.putUnique(key, i++);
                    } else {
                        errors.putUnique(key, i++);
                    }
                }
            } else {
                byte[] record;
                key = new byte[rowdef.primaryKeyLength];
                final Iterator<byte[]> ri = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.objectsize);
                while (ri.hasNext()) {
                    record = ri.next();
                    assert record != null;
                    if (record == null) {i++; continue;}
                    System.arraycopy(record, 0, key, 0, rowdef.primaryKeyLength);

                    // write the key into the index table
                    if (rowdef.objectOrder.wellformed(key)) {
                        this.index.putUnique(key, i++);
                        // write the tail into the table
                        try {
                            this.table.addUnique(this.taildef.newEntry(record, rowdef.primaryKeyLength, true));
                        } catch (final SpaceExceededException e) {
                            this.table = null;
                            break;
                        }
                    } else {
                        errors.putUnique(key, i++);
                    }
                }
                Runtime.getRuntime().gc();
                if (abandonTable()) {
                    this.table = null;
                }
            }
            optimize();

            // open the file
            this.file = new BufferedRecords(new Records(tablefile, rowdef.objectsize), this.buffersize);
            assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size() + ", file = " + filename();

            // clean up the file by cleaning badly formed entries
            final int errorc = errors.size();
            int errorcc = 0;
            int idx;
            for (final Map.Entry<byte[], Long> entry: errors) {
                idx = (int) entry.getValue().longValue();
                removeInFile(idx);
                key = entry.getKey();
                if (key == null) continue;
                log.warn("removing not well-formed entry " + idx + " with key: " + NaturalOrder.arrayList(key, 0, key.length) + ", " + errorcc++ + "/" + errorc);
            }
            errors.close();
            assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size() + ", file = " + filename();

            // warm up
            if (!freshFile && warmUp) {warmUp0();}
        } catch (final FileNotFoundException e) {
            // should never happen
            log.severe("", e);
            throw new kelondroException(e.getMessage());
        } catch (final IOException e) {
            log.severe("", e);
            throw new kelondroException(e.getMessage());
        }

        // track this table
        tableTracker.put(tablefile.toString(), this);
    }

    public synchronized void warmUp() {
        warmUp0();
    }

    private void warmUp0() {
        // remove doubles
        try {
            final ArrayList<long[]> doubles = this.index.removeDoubles();
            //assert index.size() + doubles.size() == i;
            //System.out.println(" -removed " + doubles.size() + " doubles- done.");
            if (doubles.isEmpty()) return;
            log.info(filename() + ": WARNING - TABLE " + filename() + " has " + doubles.size() + " doubles");
            // from all the doubles take one, put it back to the index and remove the others from the file
            // first put back one element each
            final byte[] record = new byte[this.rowdef.objectsize];
            final byte[] key = new byte[this.rowdef.primaryKeyLength];
            for (final long[] ds: doubles) {
                this.file.get((int) ds[0], record, 0);
                System.arraycopy(record, 0, key, 0, this.rowdef.primaryKeyLength);
                this.index.putUnique(key, (int) ds[0]);
            }
            // then remove the other doubles by removing them from the table, but do a re-indexing while doing that
            // first aggregate all the delete positions because the elements from the top positions must be removed first
            final TreeSet<Long> delpos = new TreeSet<Long>();
            for (final long[] ds: doubles) {
                for (int j = 1; j < ds.length; j++) delpos.add(ds[j]);
            }
            // now remove the entries in a sorted way (top-down)
            Long top;
            while (!delpos.isEmpty()) {
                top = delpos.last();
                delpos.remove(top);
                removeInFile(top.intValue());
            }
        } catch (final SpaceExceededException e) {
            log.severe("", e);
        } catch (final IOException e) {
            log.severe("", e);
        }
        optimize();
    }

    @Override
    public void optimize() {
        this.index.optimize();
        if (this.table != null) this.table.optimize();
    }

    @Override
    public long mem() {
        return this.index.mem() + ((this.table == null) ? 0 : this.table.mem());
    }

    private boolean abandonTable() {
        // check if not enough memory is there to maintain a memory copy of the table
        return MemoryControl.shortStatus() || MemoryControl.available() < this.minmemremaining;
    }

    @Override
    public byte[] smallestKey() {
        return this.index.smallestKey();
    }

    @Override
    public byte[] largestKey() {
        return this.index.largestKey();
    }

    public static long tableSize(final File tablefile, final int recordsize, final boolean fixIfCorrupted) throws kelondroException {
        try {
            return Records.tableSize(tablefile, recordsize);
        } catch (final IOException e) {
            if (!fixIfCorrupted) {
                log.severe("table size broken for file " + tablefile.toString(), e);
                throw new kelondroException(e.getMessage());
            }
            log.severe("table size broken, try to fix " + tablefile.toString());
            try {
                Records.fixTableSize(tablefile, recordsize);
                log.info("successfully fixed table file " + tablefile.toString());
                return Records.tableSize(tablefile, recordsize);
            } catch (final IOException ee) {
                log.severe("table size fix did not work", ee);
                throw new kelondroException(e.getMessage());
            }
        }
    }

    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return tableTracker.keySet().iterator();
    }

    public static final Map<StatKeys, String> memoryStats(final String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record objects,
        // i.e. for cache memory allocation
        final Table theTABLE = tableTracker.get(filename);
        return theTABLE.memoryStats();
    }

    public enum StatKeys {
        tableSize, tableKeyChunkSize, tableKeyMem, tableValueChunkSize, tableValueMem
    }

    private final Map<StatKeys, String> memoryStats() {
        // returns statistical data about this object
        synchronized (this) {
            assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
        }
        final HashMap<StatKeys, String> map = new HashMap<StatKeys, String>(8);
        if (this.index == null) return map; // possibly closed or beeing closed
        map.put(StatKeys.tableSize, Integer.toString(this.index.size()));
        map.put(StatKeys.tableKeyChunkSize, (this.index instanceof RowHandleMap) ? Integer.toString(((RowHandleMap) this.index).row().objectsize) : "-1");
        map.put(StatKeys.tableKeyMem, (this.index instanceof RowHandleMap) ? Integer.toString(((RowHandleMap) this.index).row().objectsize * this.index.size()) : "-1");
        map.put(StatKeys.tableValueChunkSize, (this.table == null) ? "0" : Integer.toString(this.table.row().objectsize));
        map.put(StatKeys.tableValueMem, (this.table == null) ? "0" : Integer.toString(this.table.row().objectsize * this.table.size()));
        return map;
    }

    public boolean usesFullCopy() {
        return this.table != null;
    }

    public static long staticRAMIndexNeed(final File f, final Row rowdef) {
        return (((rowdef.primaryKeyLength + 4)) * tableSize(f, rowdef.objectsize, true) * RowCollection.growfactorLarge100 / 100L);
    }

    public boolean consistencyCheck() {
        try {
            return this.file.size() == this.index.size();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return false;
        }
    }

    @Override
    public synchronized void addUnique(final Entry row) throws IOException, SpaceExceededException {
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
        final int i = (int) this.file.size();
        try {
            this.index.putUnique(row.getPrimaryKeyBytes(), i);
        } catch (final SpaceExceededException e) {
            if (this.table == null) throw e; // in case the table is not used, there is no help here
            this.table = null;
            // try again with less memory
            this.index.putUnique(row.getPrimaryKeyBytes(), i);
        }
        final byte[] rowbytes = row.bytes();
        if (this.table != null) {
            assert this.table.size() == i;
            try {
                this.table.addUnique(this.taildef.newEntry(rowbytes, this.rowdef.primaryKeyLength, true));
            } catch (final SpaceExceededException e) {
                this.table = null;
            }
            if (abandonTable()) this.table = null;
        }
        this.file.add(rowbytes, 0);
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
    }

    public synchronized void addUnique(final List<Entry> rows) throws IOException, SpaceExceededException {
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        for (final Entry entry: rows) {
            try {
                addUnique(entry);
            } catch (final SpaceExceededException e) {
                if (this.table == null) throw e;
                this.table = null;
                addUnique(entry);
            }
        }
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
    }

    /**
     * @throws SpaceExceededException
     * remove double-entries from the table
     * this process calls the underlying removeDoubles() method from the table index
     * and
     * @throws
     */
    @Override
    public synchronized List<RowCollection> removeDoubles() throws IOException, SpaceExceededException {
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        final List<RowCollection> report = new ArrayList<RowCollection>();
        RowSet rows;
        final TreeSet<Long> d = new TreeSet<Long>();
        final byte[] b = new byte[this.rowdef.objectsize];
        Row.Entry inconsistentEntry;
        // iterate over all entries that have inconsistent index references
        long lastlog = System.currentTimeMillis();
        List<long[]> doubles;
        try {
            doubles = this.index.removeDoubles();
        } catch (final SpaceExceededException e) {
            if (this.table == null) throw e;
            this.table = null;
            doubles = this.index.removeDoubles();
        }
        for (final long[] is: doubles) {
            // 'is' is the set of all indexes, that have the same reference
            // we collect that entries now here
            rows = new RowSet(this.rowdef, is.length);
            for (final long L : is) {
                assert (int) L < this.file.size() : "L.intValue() = " + (int) L + ", file.size = " + this.file.size(); // prevent ooBounds Exception
                d.add(L);
                if ((int) L >= this.file.size()) continue; // prevent IndexOutOfBoundsException
                this.file.get((int) L, b, 0); // TODO: fix IndexOutOfBoundsException here
                inconsistentEntry = this.rowdef.newEntry(b);
                try {
                    rows.addUnique(inconsistentEntry);
                } catch (final SpaceExceededException e) {
                    if (this.table == null) throw e;
                    this.table = null;
                    rows.addUnique(inconsistentEntry);
                }
            }
            report.add(rows);
        }
        // finally delete the affected rows, but start with largest id first, otherwise we overwrite wrong entries
        Long s;
        while (!d.isEmpty()) {
            s = d.last();
            d.remove(s);
            removeInFile(s.intValue());
            if (System.currentTimeMillis() - lastlog > 30000) {
                log.info("removing " + d.size() + " entries in " + filename());
                lastlog = System.currentTimeMillis();
            }
        }
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        return report;
    }

    @Override
    public void close() {
    	String tablefile = null;
        if (this.file != null) {
        	tablefile = this.file.filename().toString();
        	this.file.close();
        }
        this.file = null;
        if (this.table != null) this.table.close();
        this.table = null;
        if (this.index != null) this.index.close();
        this.index = null;
		if (tablefile != null) tableTracker.remove(tablefile);
    }

    @Override
    protected void finalize() {
        if (this.file != null) close();
    }

    @Override
    public String filename() {
        return this.file.filename().toString();
    }

    @Override
    public Entry get(final byte[] key, final boolean _forcecopy) throws IOException {
        if (this.file == null || this.index == null) return null;
        Entry e = get0(key);
        if (e != null && this.rowdef.objectOrder.equal(key, e.getPrimaryKeyBytes())) return e;
        synchronized (this) {
            //assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size() + ", file = " + filename();
            assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size() + ", file = " + filename();
            e = get0(key);
            assert e == null || this.rowdef.objectOrder.equal(key, e.getPrimaryKeyBytes()) : "key = " + ASCII.String(key) + ", e.k = " + ASCII.String(e.getPrimaryKeyBytes());
            return e;
        }
    }

    private Entry get0(final byte[] key) throws IOException {
    	if (this.file == null || this.index == null) return null;
        final int i = (int) this.index.get(key);
        if (i == -1) return null;
        final byte[] b = new byte[this.rowdef.objectsize];
        final Row.Entry cacherow;
        if (this.table == null || (cacherow = this.table.get(i, false)) == null) {
            // read row from the file
            try {
                this.file.get(i, b, 0);
            } catch (final IndexOutOfBoundsException e) {
                // there must be a problem with the table index
                log.severe("IndexOutOfBoundsException: " + e.getMessage(), e);
                this.index.remove(key);
                if (this.table != null) this.table.remove(key);
                return null;
            }
        } else {
            // construct the row using the copy in RAM
            assert key.length == this.rowdef.primaryKeyLength;
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(cacherow.bytes(), 0, b, this.rowdef.primaryKeyLength, this.rowdef.objectsize - this.rowdef.primaryKeyLength);
        }
        return this.rowdef.newEntry(b);
    }

    @Override
    public Map<byte[], Row.Entry> get(final Collection<byte[]> keys, final boolean forcecopy) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(row().objectOrder);
        Row.Entry entry;
        for (final byte[] key: keys) {
            entry = get(key, forcecopy);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    @Override
    public boolean has(final byte[] key) {
        if (this.index == null) return false;
        return this.index.has(key);
    }

    @Override
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return this.index.keys(up, firstKey);
    }

    @Override
    public Entry replace(final Entry row) throws IOException, SpaceExceededException {
        assert row != null;
        if (this.file == null || row == null) return null;
        final byte[] rowb = row.bytes();
        assert rowb != null;
        if (rowb == null) return null;
        final byte[] key = row.getPrimaryKeyBytes();
        synchronized (this) {
            //assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
            //assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
            final int i = (int) this.index.get(key);
            if (i == -1) {
                try {
                    addUnique(row);
                } catch (final SpaceExceededException e) {
                    if (this.table == null) throw e;
                    this.table = null;
                    addUnique(row);
                }
                return null;
            }

            final byte[] b = new byte[this.rowdef.objectsize];
            Row.Entry cacherow;
            if (this.table == null || (cacherow = this.table.get(i, false)) == null) {
                // read old value
                this.file.get(i, b, 0);
                // write new value
                this.file.put(i, rowb, 0);
            } else {
                // read old value
                assert cacherow != null;
                System.arraycopy(key, 0, b, 0, this.rowdef.primaryKeyLength);
                System.arraycopy(cacherow.bytes(), 0, b, this.rowdef.primaryKeyLength, this.rowdef.objectsize - this.rowdef.primaryKeyLength);
                // write new value
                try {
                    this.table.set(i, this.taildef.newEntry(rowb, this.rowdef.primaryKeyLength, true));
                } catch (final SpaceExceededException e) {
                    this.table = null;
                }
                if (abandonTable()) this.table = null;
                this.file.put(i, rowb, 0);
            }
            assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
            assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
            // return old value
            return this.rowdef.newEntry(b);
        }
    }

    /**
     * Adds the row to the index. The row is identified by the primary key of the row.
     * @param row a index row
     * @return true if this set did _not_ already contain the given row.
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public boolean put(final Entry row) throws IOException, SpaceExceededException {
        assert row != null;
        if (this.file == null || row == null) return true;
        final byte[] rowb = row.bytes();
        assert rowb != null;
        if (rowb == null) return true;
        final byte[] key = row.getPrimaryKeyBytes();
        synchronized (this) {
            //assert this.file == null || this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size() + ", file = " + filename();
            //assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size() + ", file = " + filename();
            final int i = (int) this.index.get(key);
            if (i == -1) {
                try {
                    addUnique(row);
                } catch (final SpaceExceededException e) {
                    if (this.table == null) throw e;
                    this.table = null;
                    addUnique(row);
                }
                return true;
            }

            if (this.table == null) {
                // write new value
                this.file.put(i, rowb, 0);
            } else {
                // write new value
                this.file.put(i, rowb, 0);
                if (abandonTable()) this.table = null; else try {
                    this.table.set(i, this.taildef.newEntry(rowb, this.rowdef.primaryKeyLength, true));
                } catch (final SpaceExceededException e) {
                    this.table = null;
                }
            }
            assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
            assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
            return false;
        }
    }

    /**
     * remove one entry from the file
     * @param i an index position within the file (not a byte position)
     * @throws IOException
     * @throws SpaceExceededException
     */
    private void removeInFile(final int i) throws IOException, SpaceExceededException {
        assert i >= 0;

        final byte[] p = new byte[this.rowdef.objectsize];
        if (this.table == null) {
            if (i == this.index.size() - 1) {
                this.file.cleanLast();
            } else {
                while (this.file.size() > 0) {
                    this.file.cleanLast(p, 0);
                    if (!(this.rowdef.objectOrder.wellformed(p, 0, this.rowdef.primaryKeyLength))) {
                        continue;
                    }
                    this.file.put(i, p, 0);
                    final byte[] k = new byte[this.rowdef.primaryKeyLength];
                    System.arraycopy(p, 0, k, 0, this.rowdef.primaryKeyLength);
                    this.index.put(k, i);
                    break;
                }
            }
        } else {
            if (i == this.index.size() - 1) {
                // special handling if the entry is the last entry in the file
                this.table.removeRow(i, false);
                this.file.cleanLast();
            } else {
                // switch values
                final Row.Entry te = this.table.removeOne();
                try {
                    this.table.set(i, te);
                } catch (final SpaceExceededException e) {
                    this.table = null;
                }

                while (this.file.size() > 0) {
                    this.file.cleanLast(p, 0);
                    final Row.Entry lr = this.rowdef.newEntry(p);
                    if (lr == null) {
                        // in case that p is not well-formed lr may be null
                        // drop table copy because that becomes too complicated here
                        this.table.clear();
                        this.table = null;
                        continue;
                    }
                    this.file.put(i, p, 0);
                    byte[] pk = lr.getPrimaryKeyBytes();
                    if (pk == null) {
                        // Table file might be corrupt
                        log.warn("Possible corruption found in table " + this.filename() + " detected. i=" + i + ",p=" + p);
                        continue;
                    }
                    this.index.put(pk, i);
                    break;
                }
            }
        }
    }

    @Override
    public boolean delete(final byte[] key) throws IOException {
        return remove(key) != null;
    }

    @Override
    public synchronized Entry remove(final byte[] key) throws IOException {
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
        assert key.length == this.rowdef.primaryKeyLength;
        final int i = (int) this.index.get(key);
        if (i == -1) return null; // nothing to do

        // prepare result
        final byte[] b = new byte[this.rowdef.objectsize];
        final byte[] p = new byte[this.rowdef.objectsize];
        final int sb = this.index.size();
        int ix;
        assert i < this.index.size();
        final Row.Entry cacherow;
        if (this.table == null || (cacherow = this.table.get(i, false)) == null) {
            if (i == this.index.size() - 1) {
                // element is at last entry position
                ix = (int) this.index.remove(key);
                assert this.index.size() < i + 1 : "index.size() = " + this.index.size() + ", i = " + i;
                assert ix == i;
                this.file.cleanLast(b, 0);
            } else {
                // remove entry from index
                assert i < this.index.size() - 1 : "index.size() = " + this.index.size() + ", i = " + i;
                ix = (int) this.index.remove(key);
                assert i < this.index.size() : "index.size() = " + this.index.size() + ", i = " + i;
                assert ix == i;

                // read element that shall be removed
                this.file.get(i, b, 0);

                // fill the gap with value from last entry in file
                this.file.cleanLast(p, 0);
                this.file.put(i, p, 0);
                final byte[] k = new byte[this.rowdef.primaryKeyLength];
                System.arraycopy(p, 0, k, 0, this.rowdef.primaryKeyLength);
                try {
                    this.index.put(k, i);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                    throw new IOException("RowSpaceExceededException: " + e.getMessage());
                }
            }
            assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        } else {
            // get result value from the table copy, so we don't need to read it from the file
            System.arraycopy(key, 0, b, 0, key.length);
            System.arraycopy(cacherow.bytes(), 0, b, this.rowdef.primaryKeyLength, this.taildef.objectsize);

            if (i == this.index.size() - 1) {
                // special handling if the entry is the last entry in the file
                ix = (int) this.index.remove(key);
                assert this.index.size() < i + 1  : "index.size() = " + this.index.size() + ", i = " + i;
                assert ix == i;
                this.table.removeRow(i, false);
                this.file.cleanLast();
            } else {
                // remove entry from index
                ix = (int) this.index.remove(key);
                assert i < this.index.size() : "index.size() = " + this.index.size() + ", i = " + i;
                assert ix == i;

                // switch values:
                // remove last entry from the file copy to fill it in the gap
                final Row.Entry te = this.table.removeOne();
                // fill the gap in file copy
                try {
                    this.table.set(i, te);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                    this.table = null;
                }

                // move entry from last entry in file to gap position
                this.file.cleanLast(p, 0);
                this.file.put(i, p, 0);
                // set new index for moved entry in index
                final Row.Entry lr = this.rowdef.newEntry(p);
                try {
                    this.index.put(lr.getPrimaryKeyBytes(), i);
                } catch (final SpaceExceededException e) {
                    this.table = null;
                    throw new IOException("RowSpaceExceededException: " + e.getMessage());
                }
            }
            assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
            assert this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
        }
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
        assert this.index.size() + 1 == sb : "index.size() = " + this.index.size() + ", sb = " + sb;
        return this.rowdef.newEntry(b);
    }

    @Override
    public synchronized Entry removeOne() throws IOException {
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
        final byte[] le = new byte[this.rowdef.objectsize];
        final long fsb = this.file.size();
        assert fsb != 0 : "file.size() = " + fsb;
        this.file.cleanLast(le, 0);
        assert this.file.size() < fsb : "file.size() = " + this.file.size();
        final Row.Entry lr = this.rowdef.newEntry(le);
        assert lr != null;
        assert lr.getPrimaryKeyBytes() != null;
        final int is = this.index.size();
        assert this.index.has(lr.getPrimaryKeyBytes());
        final int i = (int) this.index.remove(lr.getPrimaryKeyBytes());
        assert i < 0 || this.index.size() < is : "index.size() = " + this.index.size() + ", is = " + is;
        assert i >= 0;
        if (this.table != null) {
            final int tsb = this.table.size();
            this.table.removeOne();
            assert this.table.size() < tsb : "table.size() = " + this.table.size() + ", tsb = " + tsb;
        }
        assert this.file.size() == this.index.size() : "file.size() = " + this.file.size() + ", index.size() = " + this.index.size();
        assert this.table == null || this.table.size() == this.index.size() : "table.size() = " + this.table.size() + ", index.size() = " + this.index.size();
        return lr;
    }

    @Override
    public List<Row.Entry> top(int count) throws IOException {
        if (count > this.size()) count = this.size();
        final ArrayList<Row.Entry> list = new ArrayList<Row.Entry>();
        if (this.file == null || this.index == null || this.size() == 0 || count == 0) return list;
        long i = this.file.size() - 1;
        while (count > 0 && i >= 0) {
            final byte[] b = new byte[this.rowdef.objectsize];
            this.file.get(i, b, 0);
            list.add(this.rowdef.newEntry(b));
            i--;
            count--;
        }
        return list;
    }

    @Override
    public List<Row.Entry> random(int count) throws IOException {
        if (count > this.size()) count = this.size();
        final ArrayList<Row.Entry> list = new ArrayList<Row.Entry>();
        if (this.file == null || this.index == null || this.size() == 0 || count == 0) return list;
        long cursor = 0;
        int stepsize = this.size() / count;
        while (count > 0 && cursor < this.size()) {
            final byte[] b = new byte[this.rowdef.objectsize];
            this.file.get(cursor, b, 0);
            list.add(this.rowdef.newEntry(b));
            count--;
            cursor += stepsize;
        }
        return list;
    }

    @Override
    public synchronized void clear() throws IOException {
        this.file.clear();
        // initialize index and copy table
        this.table = (this.table == null) ? null : new RowSet(this.taildef);
        this.index.clear();
    }

    @Override
    public Row row() {
        return this.rowdef;
    }

    @Override
    public int size() {
        if (this.index == null) return 0;
        return this.index.size();
    }

    @Override
    public boolean isEmpty() {
        return this.index == null || this.index.isEmpty();
    }

    @Override
    public Iterator<Entry> iterator() {
        try {
            return rows();
        } catch (final IOException e) {
            return null;
        }
    }

    @Override
    public synchronized CloneableIterator<Entry> rows() throws IOException {
        this.file.flushBuffer();
        return new rowIteratorNoOrder();
    }

    private class rowIteratorNoOrder implements CloneableIterator<Entry> {
        Iterator<Map.Entry<byte[], Long>> i;
        long idx;
        byte[] key;

        public rowIteratorNoOrder() {
            // don't use the ChunkIterator here because it may create too many open files during string load
            //ri = new ChunkIterator(tablefile, rowdef.objectsize, rowdef.objectsize);
            this.i = Table.this.index.iterator();
        }

        @Override
        public CloneableIterator<Entry> clone(final Object modifier) {
            return new rowIteratorNoOrder();
        }

        @Override
        public boolean hasNext() {
            return this.i != null && this.i.hasNext();
        }

        @Override
        public Entry next() {
            final Map.Entry<byte[], Long> entry = this.i.next();
            if (entry == null) return null;
            this.key = entry.getKey();
            if (this.key == null) return null;
            this.idx = entry.getValue().longValue();
            try {
                return get(this.key, false);
            } catch (final IOException e) {
                return null;
            }
        }

        @Override
        public void remove() {
            if (this.key != null) {
                try {
                    removeInFile((int) this.idx);
                } catch (final IOException e) {
                } catch (final SpaceExceededException e) {
                }
                this.i.remove();
            }
        }

        @Override
        public void close() {
            if (this.i instanceof CloneableIterator) {
                ((CloneableIterator<Map.Entry<byte[], Long>>) this.i).close();
            }
        }

    }

    @Override
    public synchronized CloneableIterator<Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return new rowIterator(up, firstKey);
    }

    private class rowIterator implements CloneableIterator<Entry> {
        private final CloneableIterator<byte[]> i;
        private final boolean up;
        private final byte[] fk;
        private int c;

        private rowIterator(final boolean up, final byte[] firstKey) {
            this.up = up;
            this.fk = firstKey;
            this.i  = Table.this.index.keys(up, firstKey);
            this.c = -1;
        }

        @Override
        public CloneableIterator<Entry> clone(final Object modifier) {
            return new rowIterator(this.up, this.fk);
        }

        @Override
        public boolean hasNext() {
            return this.i.hasNext();
        }

        @Override
        public Entry next() {
            final byte[] k = this.i.next();
            assert k != null;
            if (k == null) return null;
            this.c = (int) Table.this.index.get(k);
            if (this.c < 0) throw new ConcurrentModificationException(); // this should only happen if the table was modified during the iteration
            final byte[] b = new byte[Table.this.rowdef.objectsize];
            final Row.Entry cacherow;
            if (Table.this.table == null || (cacherow = Table.this.table.get(this.c, false)) == null) {
                // read from file
                try {
                    Table.this.file.get(this.c, b, 0);
                } catch (final IOException e) {
                    log.severe("", e);
                    return null;
                }
            } else {
                // compose from table and key
                System.arraycopy(k, 0, b, 0, Table.this.rowdef.primaryKeyLength);
                System.arraycopy(cacherow.bytes(), 0, b, Table.this.rowdef.primaryKeyLength, Table.this.taildef.objectsize);
            }
            return Table.this.rowdef.newEntry(b);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("no remove in Table.rowIterator");
        }

        @Override
        public void close() {
            this.i.close();
        }
    }

    private static byte[] testWord(final char c) {
        return new byte[]{(byte) c, 32, 32, 32};
    }

    private static String[] permutations(final int letters) {
        String p = "";
        for (int i = 0; i < letters; i++) p = p + ((char) (('A') + i));
        return permutations(p);
    }

    private static String[] permutations(final String source) {
        if (source.isEmpty()) return new String[0];
        if (source.length() == 1) return new String[]{source};
        final char c = source.charAt(0);
        final String[] recres = permutations(source.substring(1));
        final String[] result = new String[source.length() * recres.length];
        for (int perm = 0; perm < recres.length; perm++) {
            result[perm * source.length()] = c + recres[perm];
            for (int pos = 1; pos < source.length() - 1; pos++) {
                result[perm * source.length() + pos] = recres[perm].substring(0, pos) + c + recres[perm].substring(pos);
            }
	    result[perm * source.length() + source.length() - 1] = recres[perm] + c;
        }
        return result;
    }

    private static Table testTable(final File f, final String testentities, final boolean useTailCache, final boolean exceed134217727) throws IOException, SpaceExceededException {
        if (f.exists()) FileUtils.deletedelete(f);
        final Row rowdef = new Row("byte[] a-4, byte[] b-4", NaturalOrder.naturalOrder);
        final Table tt = new Table(f, rowdef, 100, 0, useTailCache, exceed134217727, true);
        byte[] b;
        final Row.Entry row = rowdef.newEntry();
        for (int i = 0; i < testentities.length(); i++) {
            b = testWord(testentities.charAt(i));
            row.setCol(0, b);
            row.setCol(1, b);
            tt.put(row);
        }
        return tt;
    }

    private static int countElements(final Table t) {
        int count = 0;
        for (final Row.Entry row: t) {
            count++;
            if (row == null) System.out.println("ERROR! null element found");
            // else System.out.println("counted element: " + new
            // String(n.getKey()));
        }
        return count;
    }

    public static void bigtest(final int elements, final File testFile, final boolean useTailCache, final boolean exceed134217727) {
        System.out.println("starting big test with " + elements + " elements:");
        final long start = System.currentTimeMillis();
        final String[] s = permutations(elements);
        Table tt;
        int count;
        try {
            for (int i = 0; i < s.length; i++) {
                System.out.println("*** probing tree " + i + " for permutation " + s[i]);
                // generate tree and delete elements
                tt = testTable(testFile, s[i], useTailCache, exceed134217727);
                count = countElements(tt);
                if (count != tt.size()) {
                    System.out.println("wrong size for " + s[i] + ": count = " + count + ", size() = " + tt.size());
                }
                tt.close();
                for (final String element : s) {
                    tt = testTable(testFile, s[i], useTailCache, exceed134217727);
                    // delete by permutation j
                    for (int elt = 0; elt < element.length(); elt++) {
                        tt.remove(testWord(element.charAt(elt)));
                        count = countElements(tt);
                        if (count != tt.size()) {
                            System.out.println("ERROR! wrong size for probe tree " + s[i] + "; probe delete " + element + "; position " + elt + "; count = " + count + ", size() = " + tt.size());
                        }
                    }
                    tt.close();
                }
            }
            System.out.println("FINISHED test after " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
            System.out.println("TERMINATED");
        }
    }

    public void print() throws IOException {
        System.out.println("PRINTOUT of table, length=" + size());
        Entry row;
        byte[] key;
        final CloneableIterator<byte[]> i = keys(true, null);
        while (i.hasNext()) {
            System.out.print("row " + i + ": ");
            key = i.next();
            row = get(key, false);
            System.out.println(row.toString());
        }
        System.out.println("EndOfTable");
    }

    public static void main(final String[] args) {
        // open a file, add one entry and exit
        final File f = new File(args[0]);
        System.out.println("========= Testcase: no tail cache:");
        bigtest(5, f, false, false);
        System.out.println("========= Testcase: with tail cache:");
        bigtest(5, f, true, true);
        /*
        kelondroRow row = new kelondroRow("byte[] key-4, byte[] x-5", kelondroNaturalOrder.naturalOrder, 0);
        try {
            kelondroTABLE t = new kelondroTABLE(f, row);
            kelondroRow.Entry entry = row.newEntry();
            entry.setCol(0, "abcd".getBytes());
            entry.setCol(1, "dummy".getBytes());
            t.put(entry);
            t.close();
        } catch (final IOException e) {
            Log.logException(e);
        }
        */
    }

    @Override
    public void deleteOnExit() {
        this.file.deleteOnExit();
    }

}
