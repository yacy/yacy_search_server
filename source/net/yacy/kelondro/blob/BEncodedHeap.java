// BEncodedHeap.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.01.2010 on http://yacy.net
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 6563 $
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

package net.yacy.kelondro.blob;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.BDecoder;
import net.yacy.kelondro.util.BEncoder;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.BDecoder.BObject;

/**
 * store a table of properties (instead of fixed-field entries)
 * this is realized using blobs and BEncoded property lists
 */
public class BEncodedHeap implements Iterable<Map.Entry<byte[], Map<String, byte[]>>> {

    private Heap table;
    private LinkedHashSet<String> columnames;
    
    /**
     * produce or open a properties table
     * @param location the file
     * @param keylength length of access keys
     * @param ordering ordering on the keys
     * @param buffermax maximum number of lines that shall be buffered for writing
     * @throws IOException
     */
    public BEncodedHeap(
            final File location,
            final int keylength,
            final ByteOrder ordering,
            int buffermax) throws IOException {
        this.table = new Heap(location, keylength, ordering, buffermax);
        this.columnames = new LinkedHashSet<String>();
    }
    
    /**
     * convenience method to open a properies table
     * @param location the file
     * @param keylength length of access keys
     */
    public BEncodedHeap(
            final File location,
            final int keylength) throws IOException {
        this.table = new Heap(location, keylength, NaturalOrder.naturalOrder, 100);
        this.columnames = new LinkedHashSet<String>();  
    }
    
    public File getFile() {
        return this.table.heapFile;
    }
    
    public int size() {
        return this.table.size();
    }
    
    public void close() {
        this.table.close();
    }
    
    public void clear() throws IOException {
        this.table.clear();
    }
    
    /**
     * insert a map into the table
     * @param key
     * @param map
     * @throws RowSpaceExceededException
     * @throws IOException
     */
    public void put(byte[] pk, Map<String, byte[]> map) throws RowSpaceExceededException, IOException {
        byte[] b = BEncoder.encode(BEncoder.transcode(map));
        this.table.put(pk, b);
        this.columnames.addAll(map.keySet());
    }
    
    public void put(
            byte[] pk,
            String key, byte[] value
            ) throws RowSpaceExceededException, IOException {
        byte[] b = BEncoder.encodeMap(key, value);
        this.table.put(pk, b);
        this.columnames.add(key);
    }
    public void put(
            byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1
            ) throws RowSpaceExceededException, IOException {
        byte[] b = BEncoder.encodeMap(
                key0, value0,
                key1, value1
                );
        this.table.put(pk, b);
        this.columnames.add(key0);
        this.columnames.add(key1);
    }
    public void put(
            byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2
            ) throws RowSpaceExceededException, IOException {
        byte[] b = BEncoder.encodeMap(
                key0, value0,
                key1, value1,
                key2, value2
                );
        this.table.put(pk, b);
        this.columnames.add(key0);
        this.columnames.add(key1);
        this.columnames.add(key2);
    }
    public void put(
            byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2,
            String key3, byte[] value3
            ) throws RowSpaceExceededException, IOException {
        byte[] b = BEncoder.encodeMap(
                key0, value0,
                key1, value1,
                key2, value2,
                key3, value3
                );
        this.table.put(pk, b);
        this.columnames.add(key0);
        this.columnames.add(key1);
        this.columnames.add(key2);
        this.columnames.add(key3);
    }
    
    /**
     * select a map from the table
     * @param key
     * @return the map if one found or NULL if no entry exists or the entry is corrupt
     * @throws IOException
     */
    public Map<String, byte[]> get(byte[] pk) throws IOException {
        byte[] b = this.table.get(pk);
        if (b == null) return null;
        return b2m(b);
    }

    public byte[] getProp(byte[] pk, String key) throws IOException {
        byte[] b = this.table.get(pk);
        if (b == null) return null;
        Map<String, byte[]> map = b2m(b);
        return map.get(key);
    }
    
    static Map<String, byte[]> b2m(byte[] b) {
        if (b == null) return null;
        //System.out.println("b = " + new String(b));
        BDecoder decoder = new BDecoder(b);
        BObject bobj = decoder.parse();
        if (bobj.getType() != BDecoder.BType.dictionary) return null;
        Map<String, BDecoder.BObject> map = bobj.getMap();
        Map<String, byte[]> m = new HashMap<String, byte[]>();
        for (Map.Entry<String, BDecoder.BObject> entry: map.entrySet()) {
            if (entry.getValue().getType() != BDecoder.BType.string) continue;
            m.put(entry.getKey(), entry.getValue().getString());
        }
        return m;
    }
    
    /**
     * delete a map from the table
     * @param key
     * @throws IOException
     */
    public void delete(byte[] pk) throws IOException {
        this.table.remove(pk);
    }
    
    /**
     * check if a row with given key exists in the table
     * @param key
     * @return true if the row exists
     */
    public boolean has(byte[] pk) {
        return this.table.has(pk);
    }
    
    /**
     * iterate all keys of the table
     * @return an iterator of byte[]
     * @throws IOException
     */
    public Iterator<byte[]> keys() throws IOException {
        return this.table.keys(true, false);
    }

    /**
     * iterate all rows of the table.
     * Be aware that this first closes the table to force flushing of all elements in 
     * the write buffer. After that an iterator on the closed file is generated and then
     * the file is opened again.
     */
    public Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator() {
        File location = this.table.location();
        int keylen = this.table.keylength();
        ByteOrder order = this.table.ordering();
        int buffermax = this.table.getBuffermax();
        this.table.close();
        try {
            Iterator<Map.Entry<byte[], Map<String, byte[]>>> iter = new EntryIter(location, keylen);
            this.table = new Heap(location, keylen, order, buffermax);
            return iter;
        } catch (IOException e) {
            Log.logSevere("PropertiesTable", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * iterate all rows of the table. this is a static method that expects that the given
     * file is not opened by any other application
     * @param location
     * @param keylen
     * @return
     * @throws IOException
     */
    public static Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator(File location, int keylen) throws IOException {
        return new EntryIter(location, keylen);
    }
    
    private static class EntryIter implements Iterator<Map.Entry<byte[], Map<String, byte[]>>> {
        HeapReader.entries iter;
        public EntryIter(File location, int keylen) throws IOException {
            iter = new HeapReader.entries(location, keylen);
        }
        
        public boolean hasNext() {
            return iter.hasNext();
        }

        public Entry<byte[], Map<String, byte[]>> next() {
            Map.Entry<byte[], byte[]> entry = iter.next();
            Map<String, byte[]> map = b2m(entry.getValue());
            return new b2mEntry(entry.getKey(), map);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }
    
    public static class b2mEntry implements Map.Entry<byte[], Map<String, byte[]>> {
        private final byte[] s;
        private Map<String,byte[]> b;
        
        public b2mEntry(final byte[] s, final Map<String, byte[]> b) {
            this.s = s;
            this.b = b;
        }
    
        public byte[] getKey() {
            return s;
        }

        public Map<String, byte[]> getValue() {
            return b;
        }

        public Map<String, byte[]> setValue(Map<String, byte[]> value) {
            Map<String, byte[]> b1 = b;
            b = value;
            return b1;
        }
    }
    
    public List<String> columns() {
        if (this.columnames.size() == 0) {
            for (Map.Entry<byte[], Map<String, byte[]>> row: this) {
                this.columnames.addAll(row.getValue().keySet());
            }
        }
        List<String> l = new ArrayList<String>();
        l.addAll(this.columnames);
        return l;
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            // test the class
            File f = new File(new File("maptest").getAbsolutePath());
            //System.out.println(f.getAbsolutePath());
            //System.out.println(f.getParent());
            if (f.exists()) FileUtils.deletedelete(f);
            try {
                BEncodedHeap map = new BEncodedHeap(f, 4);
                // put some values into the map
                Map<String, byte[]> m = new HashMap<String, byte[]>();
                m.put("k", "000".getBytes()); map.put("123".getBytes(), m);
                m.put("k", "111".getBytes()); map.put("456".getBytes(), m);
                m.put("k", "222".getBytes()); map.put("789".getBytes(), m);
                // iterate over keys
                Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = map.iterator();
                while (i.hasNext()) {
                    Map.Entry<byte[], Map<String, byte[]>> entry = i.next();
                    System.out.println(new String(entry.getKey(), "UTF-8") + ": " + entry.getValue());
                }
                // clean up
                map.close();
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
        } else {
            File f = new File(args[0]);
            try {
                BEncodedHeap map = new BEncodedHeap(f, 12);
                Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = map.iterator();
                while (i.hasNext()) {
                    Map.Entry<byte[], Map<String, byte[]>> entry = i.next();
                    System.out.println(new String(entry.getKey(), "UTF-8") + ": " + entry.getValue());
                }
                map.close();
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }
}
