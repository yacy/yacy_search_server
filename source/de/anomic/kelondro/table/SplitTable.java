// kelondroSplitTable.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.10.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.kelondro.table;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.anomic.kelondro.blob.BLOBArray;
import de.anomic.kelondro.blob.Cache;
import de.anomic.kelondro.index.Column;
import de.anomic.kelondro.index.ObjectIndexCache;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowCollection;
import de.anomic.kelondro.index.ObjectIndex;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.order.MergeIterator;
import de.anomic.kelondro.order.Order;
import de.anomic.kelondro.order.StackIterator;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.Log;
import de.anomic.kelondro.util.NamePrefixThreadFactory;

public class SplitTable implements ObjectIndex {

    // this is a set of kelondro tables
    // the set is divided into tables with different entry date
    // the table type can be either kelondroFlex or kelondroEco

    private static final int EcoFSBufferSize = 20;
    static final ObjectIndex dummyIndex = new ObjectIndexCache(new Row(new Column[]{new Column("key", Column.celltype_binary, Column.encoder_bytes, 2, "key")}, NaturalOrder.naturalOrder), 0, 0);

    // the thread pool for the keeperOf executor service
    private ExecutorService executor;
    
    private HashMap<String, ObjectIndex> tables; // a map from a date string to a kelondroIndex object
    private final Row rowdef;
    private final File path;
    private final String prefix;
    private final Order<Row.Entry> entryOrder;
    private       String current;
    private long  fileAgeLimit;
    private long  fileSizeLimit;
    
    public SplitTable(
            final File path, 
            final String tablename, 
            final Row rowdef,
            final boolean resetOnFail) {
        this(path, tablename, rowdef, BLOBArray.oneMonth, (long) Integer.MAX_VALUE, resetOnFail);
    }

    public SplitTable(
            final File path, 
            final String tablename, 
            final Row rowdef, 
            final long fileAgeLimit,
            final long fileSizeLimit,
            final boolean resetOnFail) {
        this.path = path;
        this.prefix = tablename;
        this.rowdef = rowdef;
        this.fileAgeLimit = fileAgeLimit;
        this.fileSizeLimit = fileSizeLimit;
        this.entryOrder = new Row.EntryComparator(rowdef.objectOrder);
        init(resetOnFail);
    }
    
    String newFilename() {
        return prefix + "." + DateFormatter.formatShortMilliSecond(new Date()) + ".table";
    }
    
    public void init(final boolean resetOnFail) {
        current = null;

        // init the thread pool for the keeperOf executor service
        this.executor = new ThreadPoolExecutor(
        		Runtime.getRuntime().availableProcessors() + 1, 
        		Runtime.getRuntime().availableProcessors() + 1, 10, 
        		TimeUnit.SECONDS, 
        		new LinkedBlockingQueue<Runnable>(), 
        		new NamePrefixThreadFactory(prefix));
        
        // initialized tables map
        this.tables = new HashMap<String, ObjectIndex>();
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
        tablefile = path.list();
        
        // first pass: find tables
        final HashMap<String, Long> t = new HashMap<String, Long>();
        long ram, sum = 0, time, maxtime = 0;
        Date d;
        for (int i = 0; i < tablefile.length; i++) {
            if ((tablefile[i].startsWith(prefix)) &&
                (tablefile[i].charAt(prefix.length()) == '.') &&
                (tablefile[i].length() == prefix.length() + 24)) {
                f = new File(path, tablefile[i]);
                try {
                    d = DateFormatter.parseShortMilliSecond(tablefile[i].substring(prefix.length() + 1, prefix.length() + 18));
                } catch (ParseException e) {
                    e.printStackTrace();
                    continue;
                }
                time = d.getTime();
                if (time > maxtime) {
                    current = tablefile[i];
                    maxtime = time;
                }
                
                ram = EcoTable.staticRAMIndexNeed(f, rowdef);
                if (ram > 0) {
                    t.put(tablefile[i], Long.valueOf(ram));
                    sum += ram;
                }
            }
        }
        
        // second pass: open tables
        Iterator<Map.Entry<String, Long>> i;
        Map.Entry<String, Long> entry;
        String maxf;
        long maxram;
        ObjectIndex table;
        while (t.size() > 0) {
            // find maximum table
            maxram = 0;
            maxf = null;
            i = t.entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                ram = entry.getValue().longValue();
                if (ram > maxram) {
                    maxf = entry.getKey();
                    maxram = ram;
                }
            }
            
            // open next biggest table
            t.remove(maxf);
            if (maxf != null) {
                f = new File(path, maxf);
                Log.logInfo("kelondroSplitTable", "opening partial eco table " + f);
                table = new EcoTable(f, rowdef, EcoTable.tailCacheUsageAuto, EcoFSBufferSize, 0);
                tables.put(maxf, table);
            }
        }
    }
    
    public void clear() throws IOException {
    	this.close();
    	final String[] l = path.list();
    	for (int i = 0; i < l.length; i++) {
    		if (l[i].startsWith(prefix)) {
    		    final File f = new File(path, l[i]);
    		    if (f.isDirectory()) FlexWidthArray.delete(path, l[i]); else FileUtils.deletedelete(f);
    		}
    	}
    	init(true);
    }
    
    public String filename() {
        return new File(path, prefix).toString();
    }
    
    public int size() {
        final Iterator<ObjectIndex> i = tables.values().iterator();
        int s = 0;
        while (i.hasNext()) s += i.next().size();
        return s;
    }
    
    public int writeBufferSize() {
        int s = 0;
        for (final ObjectIndex index : tables.values()) {
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
    
    public synchronized Row.Entry get(final byte[] key) throws IOException {
        final ObjectIndex keeper = keeperOf(key);
        if (keeper == null) return null;
        return keeper.get(key);
    }
    
    private ObjectIndex newTable() {
        this.current = newFilename();
        final File f = new File(path, this.current);
        EcoTable table = new EcoTable(f, rowdef, EcoTable.tailCacheDenyUsage, EcoFSBufferSize, 0);
        tables.put(this.current, table);
        return table;
    }
    
    private ObjectIndex checkTable(ObjectIndex table) {
        // check size and age of given table; in case it is too large or too old
        // create a new table
        String name = new File(table.filename()).getName();
        long d;
        try {
            d = DateFormatter.parseShortMilliSecond(name.substring(prefix.length() + 1, prefix.length() + 18)).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            d = 0;
        }
        if (d + this.fileAgeLimit < System.currentTimeMillis() || new File(this.path, name).length() >= this.fileSizeLimit) {
            return newTable();
        }
        return table;
    }
    
    public synchronized Row.Entry replace(final Row.Entry row) throws IOException {
        assert row.objectsize() <= this.rowdef.objectsize;
        ObjectIndex keeper = keeperOf(row.getColBytes(0));
        if (keeper != null) return keeper.replace(row);
        keeper = (this.current == null) ? newTable() : checkTable(this.tables.get(this.current));
        keeper.put(row);
        return null;
    }
    
    public synchronized void put(final Row.Entry row) throws IOException {
        assert row.objectsize() <= this.rowdef.objectsize;
        ObjectIndex keeper = keeperOf(row.getColBytes(0));
        if (keeper != null) {keeper.put(row); return;}
        keeper = (this.current == null) ? newTable() : checkTable(this.tables.get(this.current));
        keeper.put(row);
    }
    
    public synchronized ObjectIndex keeperOf(final byte[] key) {
        // because the index is stored only in one table,
        // and the index is completely in RAM, a concurrency will create
        // not concurrent File accesses
        //long start = System.currentTimeMillis();
        
        // start a concurrent query to database tables
        final CompletionService<ObjectIndex> cs = new ExecutorCompletionService<ObjectIndex>(executor);
        final int s = tables.size();
        int rejected = 0;
        for (final ObjectIndex table : tables.values()) {
            try {
                cs.submit(new Callable<ObjectIndex>() {
                    public ObjectIndex call() {
                        if (table.has(key)) return table;
                        return dummyIndex;
                    }
                });
            } catch (final RejectedExecutionException e) {
                // the executor is either shutting down or the blocking queue is full
                // execute the search direct here without concurrency
                if (table.has(key)) return table;
                rejected++;
            }
        }

        // read the result
        try {
            for (int i = 0, n = s - rejected; i < n; i++) {
                final Future<ObjectIndex> f = cs.take();
                final ObjectIndex index = f.get();
                if (index != dummyIndex) {
                    //System.out.println("*DEBUG SplitTable success.time = " + (System.currentTimeMillis() - start) + " ms");
                    return index;
                }
            }
            //System.out.println("*DEBUG SplitTable fail.time = " + (System.currentTimeMillis() - start) + " ms");
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
        	e.printStackTrace();
            throw new RuntimeException(e.getCause());
        }
        //System.out.println("*DEBUG SplitTable fail.time = " + (System.currentTimeMillis() - start) + " ms");
        return null;
    }
    
    public synchronized void addUnique(final Row.Entry row) throws IOException {
        assert row.objectsize() <= this.rowdef.objectsize;
        ObjectIndex table = (this.current == null) ? null : tables.get(this.current);
        if (table == null) table = newTable(); else table = checkTable(table);
        table.addUnique(row);
    }
    
    public ArrayList<RowCollection> removeDoubles() throws IOException {
        final Iterator<ObjectIndex> i = tables.values().iterator();
        final ArrayList<RowCollection> report = new ArrayList<RowCollection>();
        while (i.hasNext()) {
            report.addAll(i.next().removeDoubles());
        }
        return report;
    }
    
    public synchronized Row.Entry remove(final byte[] key) throws IOException {
        final ObjectIndex table = keeperOf(key);
        if (table == null) return null;
        return table.remove(key);
    }
    
    public synchronized Row.Entry removeOne() throws IOException {
        final Iterator<ObjectIndex> i = tables.values().iterator();
        ObjectIndex table, maxtable = null;
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
    
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(tables.size());
        final Iterator<ObjectIndex> i = tables.values().iterator();
        CloneableIterator<byte[]> k;
        while (i.hasNext()) {
            k = i.next().keys(up, firstKey);
            if (k != null) c.add(k);
        }
        return MergeIterator.cascade(c, rowdef.objectOrder, MergeIterator.simpleMerge, up);
    }
    
    public synchronized CloneableIterator<Row.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        final List<CloneableIterator<Row.Entry>> c = new ArrayList<CloneableIterator<Row.Entry>>(tables.size());
        final Iterator<ObjectIndex> i = tables.values().iterator();
        while (i.hasNext()) {
            c.add(i.next().rows(up, firstKey));
        }
        return MergeIterator.cascade(c, entryOrder, MergeIterator.simpleMerge, up);
    }
    
    public synchronized CloneableIterator<Row.Entry> rows() throws IOException {
        final List<CloneableIterator<Row.Entry>> c = new ArrayList<CloneableIterator<Row.Entry>>(tables.size());
        final Iterator<ObjectIndex> i = tables.values().iterator();
        while (i.hasNext()) {
            c.add(i.next().rows());
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
        final Iterator<ObjectIndex> i = tables.values().iterator();
        while (i.hasNext()) {
            i.next().close();
        }
        this.tables = null;
    }

    public void deleteOnExit() {
        for (ObjectIndex i: this.tables.values()) i.deleteOnExit();
    }
    
}
