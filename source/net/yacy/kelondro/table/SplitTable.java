// SplitTable.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.10.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
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
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.ArrayStack;
import net.yacy.kelondro.index.Cache;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.index.RowCollection;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MergeIterator;
import net.yacy.kelondro.util.StackIterator;


public class SplitTable implements Index, Iterable<Row.Entry> {

    // this is a set of kelondro tables
    // the set is divided into tables with different entry date
    // the table type can be either kelondroFlex or kelondroEco

    private static final int EcoFSBufferSize = 20;

    // the thread pool for the keeperOf executor service
    //private ExecutorService executor;

    private Map<String, Index> tables; // a map from a date string to a kelondroIndex object
    private final Row rowdef;
    private final File path;
    private final String prefix;
    private final Order<Row.Entry> entryOrder;
    private       String current;
    private final long  fileAgeLimit;
    private final long  fileSizeLimit;
    private final boolean useTailCache;
    private final boolean exceed134217727;

    public SplitTable(
            final File path,
            final String tablename,
            final Row rowdef,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this(path, tablename, rowdef, ArrayStack.oneMonth, Integer.MAX_VALUE, useTailCache, exceed134217727);
    }

    private SplitTable(
            final File path,
            final String tablename,
            final Row rowdef,
            final long fileAgeLimit,
            final long fileSizeLimit,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.path = path;
        this.prefix = tablename;
        this.rowdef = rowdef;
        this.fileAgeLimit = fileAgeLimit;
        this.fileSizeLimit = fileSizeLimit;
        this.useTailCache = useTailCache;
        this.exceed134217727 = exceed134217727;
        this.entryOrder = new Row.EntryComparator(rowdef.objectOrder);
        init();
    }

    @Override
    public void optimize() {
        for (Index table: tables.values()) table.optimize();
    }
    
    @Override
    public long mem() {
        long m = 0;
        for (final Index i: this.tables.values()) m += i.mem();
        return m;
    }

    @Override
    public final byte[] smallestKey() {
        final HandleSet keysort = new RowHandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.tables.size());
        for (final Index oi: this.tables.values()) try {
            keysort.put(oi.smallestKey());
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        return keysort.smallestKey();
    }

    @Override
    public final byte[] largestKey() {
        final HandleSet keysort = new RowHandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.tables.size());
        for (final Index oi: this.tables.values()) try {
            keysort.put(oi.largestKey());
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        return keysort.largestKey();
    }

    private String newFilename() {
        return this.prefix + "." + GenericFormatter.SHORT_MILSEC_FORMATTER.format() + ".table";
    }

    private void init() {
        this.current = null;

        // initialized tables map
        this.tables = new HashMap<String, Index>();
        if (!(this.path.exists())) this.path.mkdirs();
        String[] tablefile = this.path.list();

        // zero pass: migrate old table names
        File f;
        final Random r = new Random(System.currentTimeMillis());
        for (final String element : tablefile) {
            if ((element.startsWith(this.prefix)) &&
                (element.charAt(this.prefix.length()) == '.') &&
                (element.length() == this.prefix.length() + 7)) {
                f = new File(this.path, element);
                final String newname = element + "0100000" + (Long.toString(r.nextLong())+"00000").substring(1,5) + ".table";
                f.renameTo(new File(this.path, newname));
            }
        }
        // read new list again
        tablefile = this.path.list();

        // first pass: find tables
        final HashMap<String, Long> t = new HashMap<String, Long>();
        long ram, time, maxtime = 0;
        Date d;
        for (final String element : tablefile) {
            if ((element.startsWith(this.prefix)) &&
                (element.charAt(this.prefix.length()) == '.') &&
                (element.length() == this.prefix.length() + 24)) {
                f = new File(this.path, element);
                try {
                    d = GenericFormatter.SHORT_MILSEC_FORMATTER.parse(element.substring(this.prefix.length() + 1, this.prefix.length() + 18));
                } catch (final ParseException e) {
                    ConcurrentLog.severe("SplitTable", "", e);
                    continue;
                }
                time = d.getTime();
                if (time > maxtime) {
                    this.current = element;
                    assert this.current != null;
                    maxtime = time;
                }

                t.put(element, Table.staticRAMIndexNeed(f, this.rowdef));
            }
        }

        // second pass: open tables
        Iterator<Map.Entry<String, Long>> i;
        Map.Entry<String, Long> entry;
        String maxf;
        long maxram;
        final List<Thread> warmingUp = new ArrayList<Thread>(); // for concurrent warming up
        maxfind: while (!t.isEmpty()) {
            // find maximum table
            maxram = 0;
            maxf = null;
            i = t.entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                ram = entry.getValue().longValue();
                if (maxf == null || ram > maxram) {
                    maxf = entry.getKey();
                    maxram = ram;
                }
            }

            // open next biggest table
            t.remove(maxf);
            f = new File(this.path, maxf);
            ConcurrentLog.info("kelondroSplitTable", "opening partial eco table " + f);
            Table table;
            try {
                table = new Table(f, this.rowdef, EcoFSBufferSize, 0, this.useTailCache, this.exceed134217727, false);
            } catch (final SpaceExceededException e) {
                try {
                    table = new Table(f, this.rowdef, 0, 0, false, this.exceed134217727, false);
                } catch (final SpaceExceededException ee) {
                    ConcurrentLog.severe("SplitTable", "Table " + f.toString() + " cannot be initialized: " + ee.getMessage(), ee);
                    continue maxfind;
                }
            }
            final Table a = table;
            final Thread p = new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName("SplitTable.warmUp");
                    a.warmUp();
                }
            };
            p.start();
            warmingUp.add(p);
            this.tables.put(maxf, table);
        }
        // collect warming up threads
        for (final Thread p: warmingUp) try {p.join();} catch (final InterruptedException e) {}
        assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;

        // init the thread pool for the keeperOf executor service
        /*
        this.executor = new ThreadPoolExecutor(
                Math.max(this.tables.size(), Runtime.getRuntime().availableProcessors()) + 1,
                Math.max(this.tables.size(), Runtime.getRuntime().availableProcessors()) + 1, 10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new NamePrefixThreadFactory(this.prefix));
        */
    }

    @Override
    public void clear() throws IOException {
    	close();
    	final String[] l = this.path.list();
    	for (final String element : l) {
    		if (element.startsWith(this.prefix)) {
    		    final File f = new File(this.path, element);
    		    if (f.isDirectory()) delete(this.path, element); else FileUtils.deletedelete(f);
    		}
    	}
    	init();
    }

    public static void delete(final File path, final String tablename) {
        if (path == null || tablename == null) return;
        final File tabledir = new File(path, tablename);
        if (!(tabledir.exists())) return;
        if ((!(tabledir.isDirectory()))) {
            FileUtils.deletedelete(tabledir);
            return;
        }

        final String[] files = tabledir.list();
        for (final String file : files) {
            FileUtils.deletedelete(new File(tabledir, file));
        }

        FileUtils.deletedelete(tabledir);
    }

    @Override
    public String filename() {
        return new File(this.path, this.prefix).toString();
    }

    @Override
    public int size() {
        final Iterator<Index> i = this.tables.values().iterator();
        int s = 0;
        while (i.hasNext()) s += i.next().size();
        return s;
    }

    @Override
    public boolean isEmpty() {
        final Iterator<Index> i = this.tables.values().iterator();
        while (i.hasNext()) if (!i.next().isEmpty()) return false;
        return true;
    }

    public int writeBufferSize() {
        int s = 0;
        for (final Index index : this.tables.values()) {
            if (index instanceof Cache) s += ((Cache) index).writeBufferSize();
        }
        return s;
    }

    @Override
    public Row row() {
        return this.rowdef;
    }

    @Override
    public boolean has(final byte[] key) {
        return keeperOf(key) != null;
    }

    @Override
    public Row.Entry get(final byte[] key, final boolean forcecopy) throws IOException {
        final Index keeper = keeperOf(key);
        if (keeper == null) return null;
        synchronized (this) { // avoid concurrent IO from different methods
            return keeper.get(key, forcecopy);
        }
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

    private Index newTable() {
        this.current = newFilename();
        final File f = new File(this.path, this.current);
        Table table = null;
        try {
            table = new Table(f, this.rowdef, EcoFSBufferSize, 0, this.useTailCache, this.exceed134217727, true);
        } catch (final SpaceExceededException e) {
            try {
                table = new Table(f, this.rowdef, 0, 0, false, this.exceed134217727, true);
            } catch (final SpaceExceededException e1) {
                ConcurrentLog.logException(e1);
            }
        }
        this.tables.put(this.current, table);
        assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
        return table;
    }

    private Index checkTable(final Index table) {
        // check size and age of given table; in case it is too large or too old
        // create a new table
        assert table != null;
        long t = System.currentTimeMillis();
        if (((t / 1000) % 10) != 0) return table; // we check only every 10 seconds because all these file and parser operations are very expensive
        final String name = new File(table.filename()).getName();
        long d;
        try {
            d = GenericFormatter.SHORT_MILSEC_FORMATTER.parse(name.substring(this.prefix.length() + 1, this.prefix.length() + 18)).getTime();
        } catch (final ParseException e) {
            ConcurrentLog.severe("SplitTable", "", e);
            d = 0;
        }
        if (d + this.fileAgeLimit < t || new File(this.path, name).length() >= this.fileSizeLimit) {
            return newTable();
        }
        return table;
    }

    @Override
    public Row.Entry replace(final Row.Entry row) throws IOException, SpaceExceededException {
        assert row.objectsize() <= this.rowdef.objectsize;
        Index keeper = keeperOf(row.getPrimaryKeyBytes());
        if (keeper != null) synchronized (this) { // avoid concurrent IO from different methods
            return keeper.replace(row);
        }
        synchronized (this) {
            assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
            keeper = (this.current == null) ? newTable() : checkTable(this.tables.get(this.current));
        }
        keeper.put(row);
        return null;
    }

    /**
     * Adds the row to the index. The row is identified by the primary key of the row.
     * @param row a index row
     * @return true if this set did _not_ already contain the given row.
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public boolean put(final Row.Entry row) throws IOException, SpaceExceededException {
        assert row.objectsize() <= this.rowdef.objectsize;
        final byte[] key = row.getPrimaryKeyBytes();
        if (this.tables == null) return true;
        Index keeper = keeperOf(key);
        if (keeper != null) synchronized (this) { // avoid concurrent IO from different methods
            return keeper.put(row);
        }
        synchronized (this) {
            keeper = keeperOf(key); // we must check that again because it could have changed in between
            if (keeper != null) return keeper.put(row);
            assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
            keeper = (this.current == null) ? newTable() : checkTable(this.tables.get(this.current));
            final boolean b = keeper.put(row);
            assert b;
            return b;
        }
    }

    private Index keeperOf(final byte[] key) {
        if (key == null) return null;
        if (this.tables == null) return null;
        for (final Index oi: this.tables.values()) {
            if (oi.has(key)) return oi;
        }
        return null;
    }

    @Override
    public void addUnique(final Row.Entry row) throws IOException, SpaceExceededException {
        assert row.objectsize() <= this.rowdef.objectsize;
        Index keeper = (this.current == null) ? null : this.tables.get(this.current);
        synchronized (this) {
            assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
            if (keeper == null) keeper = newTable(); else keeper = checkTable(keeper);
        }
        keeper.addUnique(row);
    }

    @Override
    public List<RowCollection> removeDoubles() throws IOException, SpaceExceededException {
        final Iterator<Index> i = this.tables.values().iterator();
        final List<RowCollection> report = new ArrayList<RowCollection>();
        while (i.hasNext()) {
            report.addAll(i.next().removeDoubles());
        }
        return report;
    }

    @Override
    public boolean delete(final byte[] key) throws IOException {
        final Index table = keeperOf(key);
        if (table == null) return false;
        synchronized (this) { // avoid concurrent IO from different methods
            return table.delete(key);
        }
    }

    @Override
    public Row.Entry remove(final byte[] key) throws IOException {
        final Index table = keeperOf(key);
        if (table == null) return null;
        synchronized (this) { // avoid concurrent IO from different methods
            return table.remove(key);
        }
    }

    @Override
    public Row.Entry removeOne() throws IOException {
        final Iterator<Index> i = this.tables.values().iterator();
        Index table, maxtable = null;
        int maxcount = -1;
        while (i.hasNext()) {
            table = i.next();
            if (table.size() > maxcount) {
                maxtable = table;
                maxcount = table.size();
            }
        }
        if (maxtable == null) {
            return null;
        }
        synchronized (this) { // avoid concurrent IO from different methods
            return maxtable.removeOne();
        }
    }

    @Override
    public List<Row.Entry> top(final int count) throws IOException {
        final Iterator<Index> i = this.tables.values().iterator();
        Index table, maxtable = null;
        int maxcount = -1;
        while (i.hasNext()) {
            table = i.next();
            if (table.size() > maxcount) {
                maxtable = table;
                maxcount = table.size();
            }
        }
        if (maxtable == null) {
            return null;
        }
        synchronized (this) { // avoid concurrent IO from different methods
            return maxtable.top(count);
        }
    }

    @Override
    public List<Row.Entry> random(final int count) throws IOException {
        final Iterator<Index> i = this.tables.values().iterator();
        Index table, maxtable = null;
        int maxcount = -1;
        while (i.hasNext()) {
            table = i.next();
            if (table.size() > maxcount) {
                maxtable = table;
                maxcount = table.size();
            }
        }
        if (maxtable == null) {
            return null;
        }
        synchronized (this) { // avoid concurrent IO from different methods
            return maxtable.random(count);
        }
    }

    @Override
    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(this.tables.size());
        final Iterator<Index> i = this.tables.values().iterator();
        CloneableIterator<byte[]> k;
        while (i.hasNext()) {
            k = i.next().keys(up, firstKey);
            if (k != null) c.add(k);
        }
        return MergeIterator.cascade(c, this.rowdef.objectOrder, MergeIterator.simpleMerge, up);
    }

    @Override
    public CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        final List<CloneableIterator<Row.Entry>> c = new ArrayList<CloneableIterator<Row.Entry>>(this.tables.size());
        final Iterator<Index> i = this.tables.values().iterator();
        while (i.hasNext()) {
            c.add(i.next().rows(up, firstKey));
        }
        return MergeIterator.cascade(c, this.entryOrder, MergeIterator.simpleMerge, up);
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
    @SuppressWarnings("unchecked")
    public synchronized CloneableIterator<Row.Entry> rows() throws IOException {
        final CloneableIterator<Row.Entry>[] c = new CloneableIterator[this.tables.size()];
        final Iterator<Index> i = this.tables.values().iterator();
        int d = 0;
        while (i.hasNext()) {
            c[d++] = i.next().rows();
        }
        return StackIterator.stack(c);
    }

    @Override
    public synchronized void close() {
        if (this.tables == null) return;
        /*
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
        }
        this.executor = null;
        */
        final Iterator<Index> i = this.tables.values().iterator();
        while (i.hasNext()) {
            i.next().close();
        }
        this.tables = null;
    }

    @Override
    public void deleteOnExit() {
        for (final Index i: this.tables.values()) i.deleteOnExit();
    }

}
