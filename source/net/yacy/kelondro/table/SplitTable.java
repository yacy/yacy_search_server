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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.kelondro.blob.ArrayStack;
import net.yacy.kelondro.index.Cache;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowCollection;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.Order;
import net.yacy.kelondro.order.StackIterator;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.NamePrefixThreadFactory;


public class SplitTable implements Index, Iterable<Row.Entry> {

    // this is a set of kelondro tables
    // the set is divided into tables with different entry date
    // the table type can be either kelondroFlex or kelondroEco

    private static final int EcoFSBufferSize = 20;

    // the thread pool for the keeperOf executor service
    private ExecutorService executor;
    
    private Map<String, Index> tables; // a map from a date string to a kelondroIndex object
    private final Row rowdef;
    private final File path;
    private final String prefix;
    private final Order<Row.Entry> entryOrder;
    private       String current;
    private long  fileAgeLimit;
    private long  fileSizeLimit;
    private boolean useTailCache;
    private boolean exceed134217727;

    public SplitTable(
            final File path, 
            final String tablename, 
            final Row rowdef,
            final boolean useTailCache,
            final boolean exceed134217727) throws RowSpaceExceededException {
        this(path, tablename, rowdef, ArrayStack.oneMonth, (long) Integer.MAX_VALUE, useTailCache, exceed134217727);
    }

    private SplitTable(
            final File path, 
            final String tablename, 
            final Row rowdef, 
            final long fileAgeLimit,
            final long fileSizeLimit,
            final boolean useTailCache,
            final boolean exceed134217727) throws RowSpaceExceededException {
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
    
    public long mem() {
        long m = 0;
        for (Index i: tables.values()) m += i.mem();
        return m;
    }
    
    public final byte[] smallestKey() {
        HandleSet keysort = new HandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.tables.size());
        for (Index oi: this.tables.values()) try {
            keysort.put(oi.smallestKey());
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        return keysort.smallestKey();
    }
    
    public final byte[] largestKey() {
        HandleSet keysort = new HandleSet(this.rowdef.primaryKeyLength, this.rowdef.objectOrder, this.tables.size());
        for (Index oi: this.tables.values()) try {
            keysort.put(oi.largestKey());
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        return keysort.largestKey();
    }

    private String newFilename() {
        return prefix + "." + GenericFormatter.SHORT_MILSEC_FORMATTER.format() + ".table";
    }
    
    private void init() throws RowSpaceExceededException {
        current = null;

        // initialized tables map
        this.tables = new HashMap<String, Index>();
        if (!(path.exists())) path.mkdirs();
        String[] tablefile = path.list();
        
        // zero pass: migrate old table names
        File f;
        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < tablefile.length; i++) {
            if ((tablefile[i].startsWith(prefix)) &&
                (tablefile[i].charAt(prefix.length()) == '.') &&
                (tablefile[i].length() == prefix.length() + 7)) {
                f = new File(path, tablefile[i]);
                String newname = tablefile[i] + "0100000" + (Long.toString(r.nextLong())+"00000").substring(1,5) + ".table";
                f.renameTo(new File(path, newname));
            }
        }
        // read new list again
        tablefile = path.list();
        
        // first pass: find tables
        final HashMap<String, Long> t = new HashMap<String, Long>();
        long ram, time, maxtime = 0;
        Date d;
        for (int i = 0; i < tablefile.length; i++) {
            if ((tablefile[i].startsWith(prefix)) &&
                (tablefile[i].charAt(prefix.length()) == '.') &&
                (tablefile[i].length() == prefix.length() + 24)) {
                f = new File(path, tablefile[i]);
                try {
                    d = GenericFormatter.SHORT_MILSEC_FORMATTER.parse(tablefile[i].substring(prefix.length() + 1, prefix.length() + 18));
                } catch (ParseException e) {
                    Log.logSevere("SplitTable", "", e);
                    continue;
                }
                time = d.getTime();
                if (time > maxtime) {
                    current = tablefile[i];
                    assert this.current != null;
                    maxtime = time;
                }
                
                t.put(tablefile[i], Table.staticRAMIndexNeed(f, rowdef));
            }
        }
        
        // second pass: open tables
        Iterator<Map.Entry<String, Long>> i;
        Map.Entry<String, Long> entry;
        String maxf;
        long maxram;
        Index table;
        while (!t.isEmpty()) {
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
            f = new File(path, maxf);
            Log.logInfo("kelondroSplitTable", "opening partial eco table " + f);
            try {
                table = new Table(f, rowdef, EcoFSBufferSize, 0, this.useTailCache, this.exceed134217727);
            } catch (RowSpaceExceededException e) {
                table = new Table(f, rowdef, 0, 0, false, this.exceed134217727);
            }
            tables.put(maxf, table);
        }
        assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
        
        // init the thread pool for the keeperOf executor service
        this.executor = new ThreadPoolExecutor(
                Math.max(tables.size(), Runtime.getRuntime().availableProcessors()) + 1, 
                Math.max(tables.size(), Runtime.getRuntime().availableProcessors()) + 1, 10, 
                TimeUnit.SECONDS, 
                new LinkedBlockingQueue<Runnable>(), 
                new NamePrefixThreadFactory(prefix));
    }
    
    public void clear() throws IOException {
    	this.close();
    	final String[] l = path.list();
    	for (int i = 0; i < l.length; i++) {
    		if (l[i].startsWith(prefix)) {
    		    final File f = new File(path, l[i]);
    		    if (f.isDirectory()) delete(path, l[i]); else FileUtils.deletedelete(f);
    		}
    	}
    	try {
            init();
        } catch (RowSpaceExceededException e) {
            useTailCache = false;
            try {
                init();
            } catch (RowSpaceExceededException e1) {
                throw new IOException(e1.getMessage());
            }
        }
    }
    
    public static void delete(final File path, final String tablename) {
        final File tabledir = new File(path, tablename);
        if (!(tabledir.exists())) return;
        if ((!(tabledir.isDirectory()))) {
            FileUtils.deletedelete(tabledir);
            return;
        }

        final String[] files = tabledir.list();
        for (int i = 0; i < files.length; i++) {
            FileUtils.deletedelete(new File(tabledir, files[i]));
        }

        FileUtils.deletedelete(tabledir);
    }
    
    public String filename() {
        return new File(path, prefix).toString();
    }
    
    public int size() {
        final Iterator<Index> i = tables.values().iterator();
        int s = 0;
        while (i.hasNext()) s += i.next().size();
        return s;
    }
    
    public boolean isEmpty() {
        final Iterator<Index> i = tables.values().iterator();
        while (i.hasNext()) if (!i.next().isEmpty()) return false;
        return true;
    }
    
    public int writeBufferSize() {
        int s = 0;
        for (final Index index : tables.values()) {
            if (index instanceof Cache) s += ((Cache) index).writeBufferSize();
        }
        return s;
    }
    
    public Row row() {
        return this.rowdef;
    }
    
    public boolean has(final byte[] key) {
        return keeperOf(key) != null;
    }
    
    public Row.Entry get(final byte[] key) throws IOException {
        final Index keeper = keeperOf(key);
        if (keeper == null) return null;
        return keeper.get(key);
    }

    public Map<byte[], Row.Entry> get(Collection<byte[]> keys) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(this.row().objectOrder);
        Row.Entry entry;
        for (byte[] key: keys) {
            entry = get(key);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }
    
    private Index newTable() {
        this.current = newFilename();
        final File f = new File(path, this.current);
        Table table = null;
        try {
            table = new Table(f, rowdef, EcoFSBufferSize, 0, this.useTailCache, this.exceed134217727);
        } catch (RowSpaceExceededException e) {
            try {
                table = new Table(f, rowdef, 0, 0, false, this.exceed134217727);
            } catch (RowSpaceExceededException e1) {
                Log.logException(e1);
            }
        }
        tables.put(this.current, table);
        assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
        return table;
    }
    
    private Index checkTable(Index table) {
        // check size and age of given table; in case it is too large or too old
        // create a new table
        assert table != null;
        String name = new File(table.filename()).getName();
        long d;
        try {
            d = GenericFormatter.SHORT_MILSEC_FORMATTER.parse(name.substring(prefix.length() + 1, prefix.length() + 18)).getTime();
        } catch (ParseException e) {
            Log.logSevere("SplitTable", "", e);
            d = 0;
        }
        if (d + this.fileAgeLimit < System.currentTimeMillis() || new File(this.path, name).length() >= this.fileSizeLimit) {
            return newTable();
        }
        return table;
    }
    
    public Row.Entry replace(final Row.Entry row) throws IOException, RowSpaceExceededException {
        assert row.objectsize() <= this.rowdef.objectsize;
        Index keeper = keeperOf(row.getPrimaryKeyBytes());
        if (keeper != null) return keeper.replace(row);
        synchronized (this.tables) {
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
     * @throws RowSpaceExceededException
     */
    public boolean put(final Row.Entry row) throws IOException, RowSpaceExceededException {
        assert row.objectsize() <= this.rowdef.objectsize;
        byte[] key = row.getPrimaryKeyBytes();
        if (tables == null) return true;
        synchronized (this.tables) {
            Index keeper = keeperOf(key);
            if (keeper != null) return keeper.put(row);
            assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
            keeper = (this.current == null) ? newTable() : checkTable(this.tables.get(this.current));
            boolean b = keeper.put(row);
            assert b;
            return b;
        }
    }
    

    private Index keeperOf(final byte[] key) {
        if (key == null) return null;
        if (tables == null) return null;
        for (Index oi: tables.values()) {
            if (oi.has(key)) return oi;
        }
        return null;
    }
    
    public void addUnique(final Row.Entry row) throws IOException, RowSpaceExceededException {
        assert row.objectsize() <= this.rowdef.objectsize;
        Index table = (this.current == null) ? null : tables.get(this.current);
        synchronized (this.tables) {
            assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
            if (table == null) table = newTable(); else table = checkTable(table);
        }
        table.addUnique(row);
    }
    
    public List<RowCollection> removeDoubles() throws IOException, RowSpaceExceededException {
        final Iterator<Index> i = tables.values().iterator();
        final List<RowCollection> report = new ArrayList<RowCollection>();
        while (i.hasNext()) {
            report.addAll(i.next().removeDoubles());
        }
        return report;
    }
    
    public boolean delete(final byte[] key) throws IOException {
        final Index table = keeperOf(key);
        if (table == null) return false;
        return table.delete(key);
    }
    
    public Row.Entry remove(final byte[] key) throws IOException {
        final Index table = keeperOf(key);
        if (table == null) return null;
        return table.remove(key);
    }
    
    public Row.Entry removeOne() throws IOException {
        final Iterator<Index> i = tables.values().iterator();
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
        return maxtable.removeOne();
    }
    
    public List<Row.Entry> top(int count) throws IOException {
        final Iterator<Index> i = tables.values().iterator();
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
        return maxtable.top(count);
    }
    
    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(tables.size());
        final Iterator<Index> i = tables.values().iterator();
        CloneableIterator<byte[]> k;
        while (i.hasNext()) {
            k = i.next().keys(up, firstKey);
            if (k != null) c.add(k);
        }
        return MergeIterator.cascade(c, rowdef.objectOrder, MergeIterator.simpleMerge, up);
    }
    
    public CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        final List<CloneableIterator<Row.Entry>> c = new ArrayList<CloneableIterator<Row.Entry>>(tables.size());
        final Iterator<Index> i = tables.values().iterator();
        while (i.hasNext()) {
            c.add(i.next().rows(up, firstKey));
        }
        return MergeIterator.cascade(c, entryOrder, MergeIterator.simpleMerge, up);
    }
    
    public Iterator<Entry> iterator() {
        try {
            return rows();
        } catch (IOException e) {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    public synchronized CloneableIterator<Row.Entry> rows() throws IOException {
        final CloneableIterator<Row.Entry>[] c = new CloneableIterator[tables.size()];
        final Iterator<Index> i = tables.values().iterator();
        int d = 0;
        while (i.hasNext()) {
            c[d++] = i.next().rows();
        }
        return StackIterator.stack(c);
    }
    
    public synchronized void close() {
        if (tables == null) return;
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
        }
        this.executor = null;
        final Iterator<Index> i = tables.values().iterator();
        while (i.hasNext()) {
            i.next().close();
        }
        this.tables = null;
    }

    public void deleteOnExit() {
        for (Index i: this.tables.values()) i.deleteOnExit();
    }
    
}
