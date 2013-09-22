/**
 *  BEncodedHeapBag
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 16.12.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.kelondro.blob;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.storage.AbstractMapStore;
import net.yacy.cora.storage.MapStore;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MergeIterator;

public class BEncodedHeapBag extends AbstractMapStore implements MapStore {
    
    private Map<String, BEncodedHeap> bag; // a map from a date string to a kelondroIndex object
    private final File baseDir;
    private final String prefix;
    private final int keylength, buffermax;
    private final ByteOrder entryOrder;
    private       String current;
    private final long  fileAgeLimit;
    private final long  fileSizeLimit;
    
    public BEncodedHeapBag(
            final File path,
            final String prefix,
            final int keylength,
            final ByteOrder ordering,
            final int buffermax,
            final long fileAgeLimit,
            final long fileSizeLimit) {
        this.baseDir = path;
        this.prefix = prefix;
        this.keylength = keylength;
        this.buffermax = buffermax;
        this.entryOrder = ordering;
        this.fileAgeLimit = fileAgeLimit;
        this.fileSizeLimit = fileSizeLimit;
        init();
    }
    
    private void init() {
        this.current = null;

        // initialized tables map
        this.bag = new HashMap<String, BEncodedHeap>();
        if (!(this.baseDir.exists())) this.baseDir.mkdirs();
        String[] tablefile = this.baseDir.list();

        // first pass: find tables
        final HashMap<String, Long> t = new HashMap<String, Long>();
        long ram, time, maxtime = 0;
        Date d;
        File f;
        for (final String element : tablefile) {
            if ((element.startsWith(this.prefix)) &&
                (element.length() > this.prefix.length()) &&
                (element.charAt(this.prefix.length()) == '.') &&
                (element.length() == this.prefix.length() + 23)) {
                f = new File(this.baseDir, element);
                try {
                    d = GenericFormatter.SHORT_MILSEC_FORMATTER.parse(element.substring(this.prefix.length() + 1, this.prefix.length() + 18));
                } catch (final ParseException e) {
                    ConcurrentLog.severe("BEncodedHeapBag", "", e);
                    continue;
                }
                time = d.getTime();
                if (time > maxtime) {
                    this.current = element;
                    assert this.current != null;
                    maxtime = time;
                }

                t.put(element, f.length());
            }
        }

        // second pass: open tables
        Iterator<Map.Entry<String, Long>> i;
        Map.Entry<String, Long> entry;
        String maxf;
        long maxram;
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
            f = new File(this.baseDir, maxf);
            try {
                ConcurrentLog.info("BEncodedHeapBag", "opening partial heap " + f);
                BEncodedHeap heap = new BEncodedHeap(f, this.keylength, this.entryOrder, this.buffermax);
                this.bag.put(maxf, heap);
            } catch (final IOException e) {
                ConcurrentLog.severe("BEncodedHeapBag", "error opening partial heap " + f);
            }
        }
    }
    
    @Override
    public synchronized void close() {
        if (this.bag == null) return;
        
        final Iterator<BEncodedHeap> i = this.bag.values().iterator();
        while (i.hasNext()) {
            i.next().close();
        }
        this.bag = null;
    }
    
    @Override
    public void clear() {
        close();
        final String[] l = this.baseDir.list();
        for (final String element : l) {
            if (element.startsWith(this.prefix)) {
                final File f = new File(this.baseDir, element);
                if (!f.isDirectory()) FileUtils.deletedelete(f);
            }
        }
        init();
    }

    private MapStore keeperOf(final byte[] key) {
        if (key == null) return null;
        if (this.bag == null) return null;
        for (final MapStore oi: this.bag.values()) {
            if (oi.containsKey(key)) return oi;
        }
        return null;
    }

    private String newFilename() {
        return this.prefix + "." + GenericFormatter.SHORT_MILSEC_FORMATTER.format() + ".heap";
    }
    
    private MapStore newHeap() {
        this.current = newFilename();
        final File f = new File(this.baseDir, this.current);
        BEncodedHeap heap;
        try {
            heap = new BEncodedHeap(f, this.keylength, this.entryOrder, this.buffermax);
        } catch (final IOException e) {
            ConcurrentLog.severe("BEncodedHeapBag", "unable to open new heap file: " + e.getMessage(), e);
            return null;
        }
        this.bag.put(this.current, heap);
        return heap;
    }

    private MapStore checkHeap(final BEncodedHeap heap) {
        // check size and age of given table; in case it is too large or too old
        // create a new table
        assert heap != null;
        long t = System.currentTimeMillis();
        if (((t / 1000) % 10) != 0) return heap; // we check only every 10 seconds because all these file and parser operations are very expensive
        final String name = heap.getFile().getName();
        long d;
        try {
            d = GenericFormatter.SHORT_MILSEC_FORMATTER.parse(name.substring(this.prefix.length() + 1, this.prefix.length() + 18)).getTime();
        } catch (final ParseException e) {
            ConcurrentLog.severe("BEncodedHeapBag", "", e);
            d = 0;
        }
        if (d + this.fileAgeLimit < t || new File(this.baseDir, name).length() >= this.fileSizeLimit) {
            return newHeap();
        }
        return heap;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null || !(key instanceof byte[])) return false;
        synchronized (this.bag) {
            return keeperOf((byte[]) key) != null;
        }
    }
    
    @Override
    public Map<String, byte[]> put(byte[] key, Map<String, byte[]> map) {
        if (this.bag == null) return null;
        MapStore keeper = null;
        synchronized (this.bag) {
            keeper = keeperOf(key);
        }
        if (keeper != null) {
            return keeper.put(key, map);
        }
        synchronized (this.bag) {
            keeper = keeperOf(key); // we must check that again because it could have changed in between
            if (keeper != null) return keeper.put(key, map);
            if (this.current == null) {
                keeper = newHeap();
                return keeper.put(key, map);
            }
            keeper = checkHeap(this.bag.get(this.current));
        }
        return keeper.put(key, map);
    }
    
    @Override
    public Map<String, byte[]> get(Object key) {
        if (key == null || !(key instanceof byte[])) return null;
        synchronized (this.bag) {
            return keeperOf((byte[]) key).get(key);
        }
    }

    @Override
    public boolean isEmpty() {
        final Iterator<BEncodedHeap> i = this.bag.values().iterator();
        while (i.hasNext()) if (!i.next().isEmpty()) return false;
        return true;
    }

    @Override
    public int size() {
        final Iterator<BEncodedHeap> i = this.bag.values().iterator();
        int s = 0;
        while (i.hasNext()) s += i.next().size();
        return s;
    }

    @Override
    public Map<String, byte[]> remove(Object key) {
        if (key == null || !(key instanceof byte[])) return null;
        final MapStore heap;
        synchronized (this.bag) {
            heap = keeperOf((byte[]) key);
        }
        if (heap == null) return null;
        return heap.remove(key);
    }

    @Override
    public ByteOrder getOrdering() {
        return this.entryOrder;
    }

    @Override
    public CloneableIterator<byte[]> keyIterator() {
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(this.bag.size());
        final Iterator<BEncodedHeap> i = this.bag.values().iterator();
        CloneableIterator<byte[]> k;
        while (i.hasNext()) {
            k = i.next().keyIterator();
            if (k != null && k.hasNext()) c.add(k);
        }
        return MergeIterator.cascade(c, this.entryOrder, MergeIterator.simpleMerge, true);
    }

    protected static Map<String, byte[]> testMap(int i) {
        HashMap<String, byte[]> t = new HashMap<String, byte[]>();
        t.put("rdf:about", UTF8.getBytes("http://abc.de/testmap#" + i));
        t.put("dc:title", UTF8.getBytes("test nr " + i));
        return t;
    }
    
    private static BEncodedHeapBag testHeapBag(File f) {
        return new BEncodedHeapBag(
                f,
                "testbag",
                12,
                Base64Order.enhancedCoder,
                10,
                ArrayStack.oneMonth, 100 /*Integer.MAX_VALUE*/);
    }
    
    public static void main(String[] args) {
        File f = new File("/tmp");
        BEncodedHeapBag hb = testHeapBag(f);
        for (int i = 0; i < 10000; i++) {
            hb.put(Word.word2hash(Integer.toString(i)), testMap(i));
        }
        System.out.println("test size after put = " + hb.size());
        hb.close();
        hb = testHeapBag(f);
        Iterator<Map.Entry<byte[], Map<String, byte[]>>> mi = hb.iterator();
        int c = 1000;
        Map.Entry<byte[], Map<String, byte[]>> entry;
        while (mi.hasNext() && c-- > 0) {
            entry = mi.next();
            System.out.println(UTF8.String(entry.getKey()) + ": " + AbstractMapStore.map2String(entry.getValue()));
        }
        for (int i = 10000; i > 0; i--) {
            hb.remove(Word.word2hash(Integer.toString(i - 1)));
        }
        System.out.println("test size after remove = " + hb.size());
        hb.close();
        ConcurrentLog.shutdown();
    }
    
}
