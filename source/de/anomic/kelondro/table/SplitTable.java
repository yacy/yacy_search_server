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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.order.Order;
import net.yacy.kelondro.order.StackIterator;

import de.anomic.kelondro.blob.ArrayStack;
import de.anomic.kelondro.index.Cache;
import de.anomic.kelondro.index.Column;
import de.anomic.kelondro.index.ObjectIndexCache;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowCollection;
import de.anomic.kelondro.index.ObjectIndex;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.FileUtils;
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
    private boolean useTailCache;
    private boolean exceed134217727;
    private BlockingQueue<DiscoverOrder> orderQueue;
    private Discovery[] discoveryThreads;
    
    public SplitTable(
            final File path, 
            final String tablename, 
            final Row rowdef,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this(path, tablename, rowdef, ArrayStack.oneMonth, (long) Integer.MAX_VALUE, useTailCache, exceed134217727);
    }

    public SplitTable(
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
        this.orderQueue = new LinkedBlockingQueue<DiscoverOrder>();
        this.discoveryThreads = new Discovery[Runtime.getRuntime().availableProcessors() + 1];
        for (int i = 0; i < this.discoveryThreads.length; i++) {
            this.discoveryThreads[i] = new Discovery(this.orderQueue);
            this.discoveryThreads[i].start();
        }
        init();
    }
    
    String newFilename() {
        return prefix + "." + DateFormatter.formatShortMilliSecond(new Date()) + ".table";
    }
    
    public void init() {
        current = null;

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
                    Log.logSevere("SplitTable", "", e);
                    continue;
                }
                time = d.getTime();
                if (time > maxtime) {
                    current = tablefile[i];
                    maxtime = time;
                }
                
                try {
                    ram = Table.staticRAMIndexNeed(f, rowdef);
                    if (ram > 0) {
                        t.put(tablefile[i], Long.valueOf(ram));
                        sum += ram;
                    }
                } catch (IOException e) {
                    Log.logWarning("SplitTable", "file " + f.toString() + " appears to be corrupted: " + e.getMessage());
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
                table = new Table(f, rowdef, EcoFSBufferSize, 0, this.useTailCache, this.exceed134217727);
                tables.put(maxf, table);
            }
        }
        
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
    	init();
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
    
    public synchronized boolean has(final byte[] key) {
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
        Table table = new Table(f, rowdef, EcoFSBufferSize, 0, this.useTailCache, this.exceed134217727);
        tables.put(this.current, table);
        return table;
    }
    
    private ObjectIndex checkTable(ObjectIndex table) {
        // check size and age of given table; in case it is too large or too old
        // create a new table
        assert table != null;
        String name = new File(table.filename()).getName();
        long d;
        try {
            d = DateFormatter.parseShortMilliSecond(name.substring(prefix.length() + 1, prefix.length() + 18)).getTime();
        } catch (ParseException e) {
            Log.logSevere("SplitTable", "", e);
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
        assert this.current == null || this.tables.get(this.current) != null : "this.current = " + this.current;
        keeper = (this.current == null) ? newTable() : checkTable(this.tables.get(this.current));
        keeper.put(row);
    }
    
    /**
     * challenge class for concurrent keeperOf implementation
     *
     */
    private static final class Challenge {
        // the Challenge is a discover order entry
        private final byte[] key;
        private int responseCounter, finishCounter;
        private ObjectIndex discovery;
        private Semaphore readyCheck;
        public Challenge(final byte[] key, int finishCounter) {
            this.key = key;
            this.responseCounter = 0;
            this.finishCounter = finishCounter;
            this.readyCheck = new Semaphore(0);
            this.discovery = null;
        }
        public byte[] getKey() {
            return this.key;
        }
        public synchronized void commitNoDiscovery() {
            this.responseCounter++;
            if (this.responseCounter >= this.finishCounter) this.readyCheck.release();
        }
        public synchronized void commitDiscovery(ObjectIndex discovery) {
            this.responseCounter++;
            this.discovery = discovery;
            this.readyCheck.release();
        }
        public ObjectIndex discover(long timeout) {
            if (this.discovery != null) return this.discovery;
            try {
                this.readyCheck.tryAcquire(1, timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {}
            return this.discovery;
        }
    }
    
    /**
     * A DiscoverOrder is a class to order a check for a specific table
     * for the occurrences of a given key
     *
     */
    private static final class DiscoverOrder {
        public Challenge challenge;
        public ObjectIndex objectIndex;
        public DiscoverOrder() {
            this.challenge = null;
            this.objectIndex = null;
        }
        public DiscoverOrder(Challenge challenge, ObjectIndex objectIndex) {
            this.challenge = challenge;
            this.objectIndex = objectIndex;
        }
        public void executeOrder() {
            try {
                if (this.objectIndex.has(this.challenge.getKey())) {
                    this.challenge.commitDiscovery(this.objectIndex);
                } else {
                    this.challenge.commitNoDiscovery();
                }
            } catch (Exception e) {
                Log.logSevere("SplitTable", "", e);
                this.challenge.commitNoDiscovery();
            }
        }
    }
    private static final DiscoverOrder poisonDiscoverOrder = new DiscoverOrder();
    
    /**
     * the Discovery class is used to start some concurrent threads that check the database
     * table files for occurrences of key after a keeperOf was submitted
     *
     */
    private static final class Discovery extends Thread {
        // the class discovers keeper locations in the splitted table
        BlockingQueue<DiscoverOrder> orderQueue;
        public Discovery(BlockingQueue<DiscoverOrder> orderQueue) {
            super("SplitTable-Discovery");
            this.orderQueue = orderQueue;
        }
        public void run() {
            DiscoverOrder order;
            while (true) {
                try {
                    order = orderQueue.take();
                } catch (InterruptedException e) {
                    Log.logSevere("SplitTable", "", e);
                    continue;
                }
                if (order == poisonDiscoverOrder) break;
                // check if in the given objectIndex is the key as given in the order
                order.executeOrder();
            }
            Log.logInfo("SplitTable.Discovery", "terminated discovery thread " + this.getName());
        }
    }
    
    private boolean discoveriesAlive() {
        for (int i = 0; i < this.discoveryThreads.length; i++) {
            if (!this.discoveryThreads[i].isAlive()) return false;
        }
        return true;
    }

    private ObjectIndex keeperOf(final byte[] key) {
        if (!discoveriesAlive()) {
            synchronized (tables) {
                for (ObjectIndex oi: tables.values()) {
                    if (oi.has(key)) return oi;
                }
                return null;
            }
        }
        Challenge challenge = null;
        synchronized (tables) {
            int tableCount = this.tables.size();
            challenge = new Challenge(key, tableCount);
            
            // submit discover orders to the processing units
            final Iterator<ObjectIndex> i = tables.values().iterator();
            DiscoverOrder order;
            boolean offered;
            while (i.hasNext()) {
                order = new DiscoverOrder(challenge, i.next());
                offered = this.orderQueue.offer(order);
                if (!offered) {
                    // for some reason the queue did not accept the order
                    // so we execute the order here
                    order.executeOrder();
                    Log.logWarning("SplitTable", "executed a challenge order without concurrency. queue size = " + this.orderQueue.size());
                }
            }
        }
        // wait for a result
        ObjectIndex result = challenge.discover(1000);
        //System.out.println("result of discovery: file = " + ((result == null) ? "null" : result.filename()));
        return result;
    }
    
    /*
    private static final class ReadyCheck {
        private boolean r;
        public ReadyCheck() {
            this.r = false;
        }
        public void isReady() {
            this.r = true;
        }
        public boolean check() {
            return this.r;
        }
    }
    private ObjectIndex keeperOf(final byte[] key) {
        
        if (tables.size() < 2) {
            // no concurrency if not needed
            for (final ObjectIndex table : tables.values()) {
                if (table.has(key)) return table;
            }
            return null;
        }
        
        // because the index is stored only in one table,
        // and the index is completely in RAM, a concurrency will create
        // not concurrent File accesses
        
        // start a concurrent query to database tables
        final CompletionService<ObjectIndex> cs = new ExecutorCompletionService<ObjectIndex>(executor);
        int accepted = 0;
        final ReadyCheck ready = new ReadyCheck();
        for (final ObjectIndex table : tables.values()) {
            if (ready.check()) break; // found already a table
            try {
                cs.submit(new Callable<ObjectIndex>() {
                    public ObjectIndex call() {
                        if (table.has(key)) {
                            ready.isReady();
                            return table;
                        }
                        return dummyIndex;
                    }
                });
                accepted++;
            } catch (final RejectedExecutionException e) {
                // the executor is either shutting down or the blocking queue is full
                // execute the search direct here without concurrency
                if (table.has(key)) return table;
            }
        }

        // read the result
        try {
            for (int i = 0; i < accepted; i++) {
                final Future<ObjectIndex> f = cs.take();
                if (f == null) continue;
                final ObjectIndex index = f.get();
                if (index == dummyIndex) continue;
                return index;
            }
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
        	Log.logSevere("SplitTable", "", e);
            throw new RuntimeException(e.getCause());
        }
        return null;
    }
    */
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
        // stop discover threads
        if (this.orderQueue != null) for (int i = 0; i < this.discoveryThreads.length; i++) {
            try {
                this.orderQueue.put(poisonDiscoverOrder);
            } catch (InterruptedException e) {
                Log.logSevere("SplitTable", "", e);
            }
        }
        
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
