// BEncodedHeap.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.01.2010 on http://yacy.net
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

package net.yacy.kelondro.blob;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.BDecoder;
import net.yacy.kelondro.util.BEncoder;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.BDecoder.BObject;

/**
 * store a table of properties (instead of fixed-field entries)
 * this is realized using blobs and BEncoded property lists
 */
public class BEncodedHeap implements Map<byte[], Map<String, byte[]>>, Iterable<Map.Entry<byte[], Map<String, byte[]>>> {

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
    
    public byte[] encodedKey(String key) {
        return Base64Order.enhancedCoder.encodeSubstring(Digest.encodeMD5Raw(key), this.table.keylength);
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
    
    private static Map<String, byte[]> b2m(byte[] b) {
        if (b == null) return null;
        //System.out.println("b = " + UTF8.String(b));
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
     * the map is stored inside a file; this method may return the file
     * @return the file where the map is stored
     */
    public File getFile() {
        return this.table.heapFile;
    }

    /**
     * Retur the number of key-value mappings in this map.
     * @return the number of entries mappings in this map
     */
    public int size() {
        return this.table.size();
    }
    
    /**
     * return true if the table is empty
     */
    public boolean isEmpty() {
        return this.table.size() == 0;
    }
    
    /**
     * check if a row with given key exists in the table
     * @param name
     * @return true if the row exists
     */
    public boolean containsKey(byte[] pk) {
        return this.table.containsKey(pk);
    }
    
    /**
     * check if a row with given key exists in the table
     * This method is here to implement the Map interface
     * @param name
     * @return true if the row exists
     */
    public boolean containsKey(Object key) {
        if (key instanceof byte[]) return containsKey((byte[]) key);
        return false;
    }
    
    /**
     * the containsValue method cannot be used in this method
     * and is only here to implement the Map interface
     */
    public boolean containsValue(Object value) {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }
    
    /**
     * get a map from the table
     * @param name
     * @return the map if one found or NULL if no entry exists or the entry is corrupt
     * @throws RowSpaceExceededException 
     * @throws IOException
     */
    public Map<String, byte[]> get(byte[] pk) throws IOException, RowSpaceExceededException {
        byte[] b = this.table.get(pk);
        if (b == null) return null;
        return b2m(b);
    }
    
    /**
     * get a map from the table
     * this method is here to implement the Map interface
     * @param name
     * @return the map if one found or NULL if no entry exists or the entry is corrupt
     */
    public Map<String, byte[]> get(Object key) {
        if (key instanceof byte[])
            try {
                return get((byte[]) key);
            } catch (IOException e) {
                Log.logException(e);
                return null;
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
                return null;
            }
        return null;
    }
    
    /**
     * convenience method to get a value from a map
     * @param pk
     * @param key
     * @return the value
     * @throws IOException
     * @throws RowSpaceExceededException
     */
    public byte[] getProp(byte[] pk, String key) throws IOException, RowSpaceExceededException {
        byte[] b = this.table.get(pk);
        if (b == null) return null;
        Map<String, byte[]> map = b2m(b);
        return map.get(key);
    }

    /**
     * select all rows from a table where a given matcher matches with elements in a given row
     * this method makes a full-table scan of the whole table
     * @param columnName the name of the column where the matcher shall match
     * @param columnMatcher the matcher for the elements of the column
     * @return a set of primary keys where the matcher matched
     */
    public Set<byte[]> select(String columnName, Pattern columnMatcher) {
        Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = iterator();
        Map.Entry<byte[], Map<String, byte[]>> row;
        Map<String, byte[]> prop;
        byte[] val;
        Set<byte[]> pks = new TreeSet<byte[]>(this.table.ordering);
        while (i.hasNext()) {
            row = i.next();
            prop = row.getValue();
            val = prop.get(columnName);
            if (val != null) {
                if (columnMatcher.matcher(UTF8.String(val)).matches()) pks.add(row.getKey());
            }
        }
        return pks;
    }

    /**
     * select one row from a table where a given matcher matches with elements in a given row
     * this method stops the full-table scan as soon as a first matcher was found
     * @param columnName the name of the column where the matcher shall match
     * @param columnMatcher the matcher for the elements of the column
     * @return the row where the matcher matched the given column
     */
    public Map.Entry<byte[], Map<String, byte[]>> selectOne(String columnName, Pattern columnMatcher) {
        Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = iterator();
        Map.Entry<byte[], Map<String, byte[]>> row;
        Map<String, byte[]> prop;
        byte[] val;
        while (i.hasNext()) {
            row = i.next();
            prop = row.getValue();
            val = prop.get(columnName);
            if (val != null) {
                if (columnMatcher.matcher(UTF8.String(val)).matches()) return row;
            }
        }
        return null;
    }
    
    /**
     * insert a map into the table
     * this method shall be used in exchange of the get method if the
     * previous entry value is not needed.
     * @param name
     * @param map
     * @throws RowSpaceExceededException
     * @throws IOException
     */
    public void insert(byte[] pk, Map<String, byte[]> map) throws RowSpaceExceededException, IOException {
        byte[] b = BEncoder.encode(BEncoder.transcode(map));
        this.table.insert(pk, b);
        this.columnames.addAll(map.keySet());
    }
    
    public void insert(byte[] pk, String key, byte[] value) throws IOException {
        byte[] b = BEncoder.encodeMap(key, value);
        this.table.insert(pk, b);
        this.columnames.add(key);
    }
    
    public void update(byte[] pk, Map<String, byte[]> map) throws RowSpaceExceededException, IOException {
        Map<String, byte[]> entry = this.get(pk);
        if (entry == null) {
            insert(pk, map);
        } else {
            entry.putAll(map);
            insert(pk, entry);
        }
    }
    
    public void update(byte[] pk, String key, byte[] value) throws RowSpaceExceededException, IOException  {
        Map<String, byte[]> entry = this.get(pk);
        if (entry == null) {
            entry = new HashMap<String, byte[]>();
            entry.put(key, value);
            insert(pk, entry);
        } else {
            entry.put(key, value);
            insert(pk, entry);
        }
    }

    /**
     * insert a map into the table
     * @param name
     * @param map
     */
    public Map<String, byte[]> put(byte[] pk, Map<String, byte[]> map)  {
        try {
            Map<String, byte[]> entry = this.get(pk);
            byte[] b = BEncoder.encode(BEncoder.transcode(map));
            this.table.insert(pk, b);
            this.columnames.addAll(map.keySet());
            return entry;
        } catch (IOException e) {
            Log.logException(e);
            return null;
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            return null;
        }
    }
    
    /**
     * delete a map from the table
     * @param name
     * @throws IOException
     */
    public void delete(byte[] pk) throws IOException {
        this.table.delete(pk);
    }
    
    /**
     * delete a map from the table
     * @param name
     * @throws RowSpaceExceededException 
     * @throws IOException
     */
    public Map<String, byte[]> remove(byte[] key) throws IOException, RowSpaceExceededException {
        Map<String, byte[]> value = get(key);
        this.delete(key);
        return value;
    }

    public Map<String, byte[]> remove(Object key) {
        if (key instanceof byte[])
            try {
                return remove((byte[]) key);
            } catch (IOException e) {
                Log.logException(e);
                return null;
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
                return null;
            }
        return null;
    }
    
    /**
     * Copy all the mappings from the specified map to this map.
     *
     * @param m mappings to be stored in this map
     */
    public void putAll(Map<? extends byte[], ? extends Map<String, byte[]>> map) {
        for (Map.Entry<? extends byte[], ? extends Map<String, byte[]>> me: map.entrySet()) {
            try {
                this.insert(me.getKey(), me.getValue());
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }
    
    /**
     * remove all entries from the map;
     * possibly removes the backend-file
     */
    public void clear() {
        try {
            this.table.clear();
        } catch (IOException e) {
            Log.logException(e);
        }
    }
    
    /**
     * close the backen-file.
     * Should be called explicitely to ensure that all data
     * waiting in IO write buffers are flushed
     */
    public void close() {
        this.table.close();
    }
    
    /**
     * Return a Set of the keys contained in this map.
     * This may not be a useful method, if possible use the keys()
     * method instead to iterate all keys from the backend-file
     * 
     * @return a set view of the keys contained in this map
     */
    public Set<byte[]> keySet() {
        TreeSet<byte[]> set = new TreeSet<byte[]>(this.table.ordering);
        try {
            Iterator<byte[]> i = this.table.keys(true, false);
            while (i.hasNext()) set.add(i.next());
        } catch (IOException e) {}
        return set;
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
     * the values() method is not implemented in this class
     * because it does not make sense to use such a method for
     * file-based data structures. To get a collection view of
     * all the entries, just use a entry iterator instead.
     *
     * @return nothing. The method throws always a UnsupportedOperationException
     */
    public Collection<Map<String, byte[]>> values() {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }
    
    /**
     * The abstract method entrySet() from AbstractMap must be implemented,
     * but never used because that is not useful for this file-based storage class.
     * To prevent the usage, a UnsupportedOperationException is thrown.
     * To prevent that the method is used by the methods from AbstractMap, all such
     * methods must be overriden in this class. These methods are:
     * size, containsValue, containsKey, get, remove, putAll, clear,
     * keySet, values, equals, hashCode and toString
     * 
     * Instead of using this method, use the iterator() method to iterate
     * all elements in the back-end blob file
     */
    public Set<Map.Entry<byte[], Map<String, byte[]>>> entrySet() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * iterate all rows of the table.
     * This method implements the
     * Iterable<Map.Entry<byte[], Map<String, byte[]>>>
     * interface
     */
    public Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator() {
        File location = this.table.location();
        int keylen = this.table.keylength();
        try {
            this.table.flushBuffer();
            return new EntryIter(location, keylen);
        } catch (IOException e1) {
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
    
    
    /**
     * a hashcode for the object
     */
    @Override
    public int hashCode() {
        return this.table.name().hashCode();
    }

    /**
     * Produce a list of column names from this table
     * This method may be useful if the table shall be displayed
     * as a table in GUIs. To show the first line of the table, the
     * table header, a list of all column names is required. This can
     * be generated with this method
     * @return a list of column names
     */
    public ArrayList<String> columns() {
        if (this.columnames.size() == 0) {
            for (Map.Entry<byte[], Map<String, byte[]>> row: this) {
                this.columnames.addAll(row.getValue().keySet());
            }
        }
        ArrayList<String> l = new ArrayList<String>();
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
                m.put("k", "000".getBytes()); map.insert("123".getBytes(), m);
                m.put("k", "111".getBytes()); map.insert("456".getBytes(), m);
                m.put("k", "222".getBytes()); map.insert("789".getBytes(), m);
                // iterate over keys
                Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = map.iterator();
                while (i.hasNext()) {
                    Map.Entry<byte[], Map<String, byte[]>> entry = i.next();
                    System.out.println(UTF8.String(entry.getKey()) + ": " + entry.getValue());
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
                    System.out.println(UTF8.String(entry.getKey()) + ": " + entry.getValue());
                }
                map.close();
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }

}
