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

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.MapStore;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.util.BDecoder;
import net.yacy.kelondro.util.BDecoder.BObject;
import net.yacy.kelondro.util.BEncoder;
import net.yacy.kelondro.util.FileUtils;

/**
 * store a table of properties (instead of fixed-field entries) this is realized using blobs and BEncoded
 * property lists
 */
public class BEncodedHeap implements MapStore {

    private Heap table;
    private final LinkedHashSet<String> columnames;

    /**
     * produce or open a properties table
     *
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
        final int buffermax) throws IOException {
        this.table = new Heap(location, keylength, ordering, buffermax);
        this.columnames = new LinkedHashSet<String>();
    }

    /**
     * convenience method to open a properies table
     *
     * @param location the file
     * @param keylength length of access keys
     */
    public BEncodedHeap(final File location, final int keylength) throws IOException {
        this.table = new Heap(location, keylength, NaturalOrder.naturalOrder, 100);
        this.columnames = new LinkedHashSet<String>();
    }

    @Override
    public ByteOrder getOrdering() {
        return this.table.ordering;
    }

    @Override
    public CloneableIterator<byte[]> keyIterator() {
        try {
            return this.table.keys(true, false);
        } catch (final IOException e) {
            ConcurrentLog.severe("BEncodedHeap", "returning empty iterator for failed key iteration: " + e.getMessage(), e);
            return new CloneableIterator<byte[]>(){

                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public byte[] next() {
                    return null;
                }

                @Override
                public void remove() {
                }

                @Override
                public CloneableIterator<byte[]> clone(Object modifier) {
                    return this;
                }

                @Override
                public void close() {
                }

            };
        }
    }

    public byte[] encodedKey(final String key) {
        return Base64Order.enhancedCoder.encodeSubstring(Digest.encodeMD5Raw(key), this.table.keylength);
    }

    private static class EntryIter implements Iterator<Map.Entry<byte[], Map<String, byte[]>>>
    {
        HeapReader.entries iter;

        public EntryIter(final File location, final int keylen) throws IOException {
            this.iter = new HeapReader.entries(location, keylen);
        }

        @Override
        public boolean hasNext() {
            return this.iter.hasNext();
        }

        @Override
        public Entry<byte[], Map<String, byte[]>> next() {
            final Map.Entry<byte[], byte[]> entry = this.iter.next();
            final Map<String, byte[]> map = b2m(entry.getValue());
            return new b2mEntry(entry.getKey(), map);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    private static class b2mEntry implements Map.Entry<byte[], Map<String, byte[]>>
    {
        private final byte[] s;
        private Map<String, byte[]> b;

        private b2mEntry(final byte[] s, final Map<String, byte[]> b) {
            this.s = s;
            this.b = b;
        }

        @Override
        public byte[] getKey() {
            return this.s;
        }

        @Override
        public Map<String, byte[]> getValue() {
            return this.b;
        }

        @Override
        public Map<String, byte[]> setValue(final Map<String, byte[]> value) {
            final Map<String, byte[]> b1 = this.b;
            this.b = value;
            return b1;
        }
    }

    private static Map<String, byte[]> b2m(final byte[] b) {
        if ( b == null ) {
            return null;
        }
        //System.out.println("b = " + UTF8.String(b));
        final BDecoder decoder = new BDecoder(b);
        final BObject bobj = decoder.parse();
        if ( bobj == null || bobj.getType() != BDecoder.BType.dictionary ) {
            return null;
        }
        final Map<String, BDecoder.BObject> map = bobj.getMap();
        final Map<String, byte[]> m = new HashMap<String, byte[]>();
        for ( final Map.Entry<String, BDecoder.BObject> entry : map.entrySet() ) {
            if ( entry.getValue().getType() != BDecoder.BType.string ) {
                continue;
            }
            m.put(entry.getKey(), entry.getValue().getString());
        }
        return m;
    }

    /**
     * the map is stored inside a file; this method may return the file
     *
     * @return the file where the map is stored
     */
    public File getFile() {
        return this.table.heapFile;
    }

    /**
     * Retur the number of key-value mappings in this map.
     *
     * @return the number of entries mappings in this map
     */
    @Override
    public int size() {
        return this.table.size();
    }

    /**
     * return true if the table is empty
     */
    @Override
    public boolean isEmpty() {
        return this.table.isEmpty();
    }

    /**
     * check if a row with given key exists in the table
     *
     * @param name
     * @return true if the row exists
     */
    private boolean containsKey(final byte[] pk) {
        return this.table.containsKey(pk);
    }

    /**
     * check if a row with given key exists in the table This method is here to implement the Map interface
     *
     * @param name
     * @return true if the row exists
     */
    @Override
    public boolean containsKey(final Object key) {
        if ( key instanceof byte[] ) {
            return containsKey((byte[]) key);
        }
        return false;
    }

    /**
     * the containsValue method cannot be used in this method and is only here to implement the Map interface
     */
    @Override
    public boolean containsValue(final Object value) {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    /**
     * get a map from the table
     *
     * @param name
     * @return the map if one found or NULL if no entry exists or the entry is corrupt
     * @throws SpaceExceededException
     * @throws IOException
     */
    public Map<String, byte[]> get(final byte[] pk) throws IOException, SpaceExceededException {
        final byte[] b = this.table.get(pk);
        if ( b == null ) {
            return null;
        }
        return b2m(b);
    }

    /**
     * get a map from the table this method is here to implement the Map interface
     *
     * @param name
     * @return the map if one found or NULL if no entry exists or the entry is corrupt
     */
    @Override
    public Map<String, byte[]> get(final Object key) {
        if ( key instanceof byte[] ) {
            try {
                return get((byte[]) key);
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
                return null;
            } catch (final SpaceExceededException e ) {
                ConcurrentLog.logException(e);
                return null;
            }
        }
        return null;
    }

    /**
     * convenience method to get a value from a map
     *
     * @param pk
     * @param key
     * @return the value
     * @throws IOException
     * @throws SpaceExceededException
     */
    public byte[] getProp(final byte[] pk, final String key) throws IOException, SpaceExceededException {
        final byte[] b = this.table.get(pk);
        if ( b == null ) {
            return null;
        }
        final Map<String, byte[]> map = b2m(b);
        return map.get(key);
    }

    /**
     * select all rows from a table where a given matcher matches with elements in a given row this method
     * makes a full-table scan of the whole table
     *
     * @param columnName the name of the column where the matcher shall match
     * @param columnMatcher the matcher for the elements of the column
     * @return a set of primary keys where the matcher matched
     */
    public Set<byte[]> select(final String columnName, final Pattern columnMatcher) {
        final Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = iterator();
        Map.Entry<byte[], Map<String, byte[]>> row;
        Map<String, byte[]> prop;
        byte[] val;
        final Set<byte[]> pks = new TreeSet<byte[]>(this.table.ordering);
        while ( i.hasNext() ) {
            row = i.next();
            prop = row.getValue();
            val = prop.get(columnName);
            if ( val != null ) {
                if ( columnMatcher.matcher(UTF8.String(val)).matches() ) {
                    pks.add(row.getKey());
                }
            }
        }
        return pks;
    }

    /**
     * select one row from a table where a given matcher matches with elements in a given row this method
     * stops the full-table scan as soon as a first matcher was found
     *
     * @param columnName the name of the column where the matcher shall match
     * @param columnMatcher the matcher for the elements of the column
     * @return the row where the matcher matched the given column
     */
    public Map.Entry<byte[], Map<String, byte[]>> selectOne(
        final String columnName,
        final Pattern columnMatcher) {
        final Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = iterator();
        Map.Entry<byte[], Map<String, byte[]>> row;
        Map<String, byte[]> prop;
        byte[] val;
        while ( i.hasNext() ) {
            row = i.next();
            prop = row.getValue();
            val = prop.get(columnName);
            if ( val != null ) {
                if ( columnMatcher.matcher(UTF8.String(val)).matches() ) {
                    return row;
                }
            }
        }
        return null;
    }

    /**
     * insert a map into the table this method shall be used in exchange of the get method if the previous
     * entry value is not needed.
     *
     * @param name
     * @param map
     * @throws SpaceExceededException
     * @throws IOException
     */
    public void insert(final byte[] pk, final Map<String, byte[]> map)
        throws SpaceExceededException,
        IOException {
        final byte[] b = BEncoder.encode(BEncoder.transcode(map));
        this.table.insert(pk, b);
        this.columnames.addAll(map.keySet());
    }

    public void insert(final byte[] pk, final String key, final byte[] value) throws IOException {
        final byte[] b = BEncoder.encodeMap(key, value);
        this.table.insert(pk, b);
        this.columnames.add(key);
    }

    public void update(final byte[] pk, final Map<String, byte[]> map)
        throws SpaceExceededException,
        IOException {
        final Map<String, byte[]> entry = this.get(pk);
        if ( entry == null ) {
            insert(pk, map);
        } else {
            entry.putAll(map);
            insert(pk, entry);
        }
    }

    public void update(final byte[] pk, final String key, final byte[] value)
        throws SpaceExceededException,
        IOException {
        Map<String, byte[]> entry = this.get(pk);
        if ( entry == null ) {
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
     *
     * @param name
     * @param map
     */
    @Override
    public Map<String, byte[]> put(final byte[] pk, final Map<String, byte[]> map) {
        try {
            final Map<String, byte[]> entry = this.get(pk);
            final byte[] b = BEncoder.encode(BEncoder.transcode(map));
            this.table.insert(pk, b);
            this.columnames.addAll(map.keySet());
            return entry;
        } catch (final IOException e ) {
            ConcurrentLog.logException(e);
            return null;
        } catch (final SpaceExceededException e ) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    /**
     * delete a map from the table
     *
     * @param name
     * @throws IOException
     */
    public void delete(final byte[] pk) throws IOException {
        this.table.delete(pk);
    }

    /**
     * delete a map from the table
     *
     * @param name
     * @throws SpaceExceededException
     * @throws IOException
     */
    public Map<String, byte[]> remove(final byte[] key) throws IOException, SpaceExceededException {
        final Map<String, byte[]> value = get(key);
        delete(key);
        return value;
    }

    @Override
    public Map<String, byte[]> remove(final Object key) {
        if ( key instanceof byte[] ) {
            try {
                return remove((byte[]) key);
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
                return null;
            } catch (final SpaceExceededException e ) {
                ConcurrentLog.logException(e);
                return null;
            }
        }
        return null;
    }

    /**
     * Copy all the mappings from the specified map to this map.
     *
     * @param m mappings to be stored in this map
     */
    @Override
    public void putAll(final Map<? extends byte[], ? extends Map<String, byte[]>> map) {
        for ( final Map.Entry<? extends byte[], ? extends Map<String, byte[]>> me : map.entrySet() ) {
            try {
                this.insert(me.getKey(), me.getValue());
            } catch (final SpaceExceededException e ) {
                ConcurrentLog.logException(e);
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
            }
        }
    }

    /**
     * remove all entries from the map; possibly removes the backend-file
     */
    @Override
    public void clear() {
        try {
            this.table.clear();
            this.columnames.clear();
        } catch (final IOException e ) {
            ConcurrentLog.logException(e);
        }
    }

    /**
     * close the backen-file. Should be called explicitely to ensure that all data waiting in IO write buffers
     * are flushed
     */
    @Override
    public synchronized void close() {
        int s = this.size();
        File f = this.table.heapFile;
        this.table.close();
        if (s == 0) f.delete();
    }

    /**
     * Return a Set of the keys contained in this map. This may not be a useful method, if possible use the
     * keys() method instead to iterate all keys from the backend-file
     *
     * @return a set view of the keys contained in this map
     */
    @Override
    public Set<byte[]> keySet() {
        final TreeSet<byte[]> set = new TreeSet<byte[]>(this.table.ordering);
        try {
            final Iterator<byte[]> i = this.table.keys(true, false);
            while ( i.hasNext() ) {
                set.add(i.next());
            }
        } catch (final IOException e ) {
        }
        return set;
    }

    /**
     * iterate all keys of the table
     *
     * @return an iterator of byte[]
     * @throws IOException
     */
    public Iterator<byte[]> keys() throws IOException {
        return this.table.keys(true, false);
    }

    /**
     * the values() method is not implemented in this class because it does not make sense to use such a
     * method for file-based data structures. To get a collection view of all the entries, just use a entry
     * iterator instead.
     *
     * @return nothing. The method throws always a UnsupportedOperationException
     */
    @Override
    public Collection<Map<String, byte[]>> values() {
        // this method shall not be used because it is not appropriate for this kind of data
        throw new UnsupportedOperationException();
    }

    /**
     * The abstract method entrySet() from AbstractMap must be implemented, but never used because that is not
     * useful for this file-based storage class. To prevent the usage, a UnsupportedOperationException is
     * thrown. To prevent that the method is used by the methods from AbstractMap, all such methods must be
     * overriden in this class. These methods are: size, containsValue, containsKey, get, remove, putAll,
     * clear, keySet, values, equals, hashCode and toString Instead of using this method, use the iterator()
     * method to iterate all elements in the back-end blob file
     */
    @Override
    public Set<Map.Entry<byte[], Map<String, byte[]>>> entrySet() {
        throw new UnsupportedOperationException();
    }

    /**
     * iterate all rows of the table. This method implements the Iterable<Map.Entry<byte[], Map<String,
     * byte[]>>> interface
     */
    @Override
    public Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator() {
        final File location = this.table.location();
        final int keylen = this.table.keylength();
        try {
            this.table.flushBuffer();
            return new EntryIter(location, keylen);
        } catch (final IOException e1 ) {
            final ByteOrder order = this.table.ordering();
            final int buffermax = this.table.getBuffermax();
            this.table.close();
            try {
                final Iterator<Map.Entry<byte[], Map<String, byte[]>>> iter = new EntryIter(location, keylen);
                this.table = new Heap(location, keylen, order, buffermax);
                return iter;
            } catch (final IOException e ) {
                ConcurrentLog.severe("PropertiesTable", e.getMessage(), e);
                return null;
            }
        }
    }

    /**
     * iterate all rows of the table. this is a static method that expects that the given file is not opened
     * by any other application
     *
     * @param location
     * @param keylen
     * @return
     * @throws IOException
     */
    public static Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator(
        final File location,
        final int keylen) throws IOException {
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
     * Produce a list of column names from this table This method may be useful if the table shall be
     * displayed as a table in GUIs. To show the first line of the table, the table header, a list of all
     * column names is required. This can be generated with this method
     *
     * @return a list of column names
     */
    public ArrayList<String> columns() {
        if ( this.columnames.isEmpty() ) {
            for ( final Map.Entry<byte[], Map<String, byte[]>> row : this ) {
                this.columnames.addAll(row.getValue().keySet());
            }
        }
        final ArrayList<String> l = new ArrayList<String>();
        l.addAll(this.columnames);
        return l;
    }

    public static void main(final String[] args) {
        if ( args.length == 0 ) {
            // test the class
            final File f = new File(new File("maptest").getAbsolutePath());
            //System.out.println(f.getAbsolutePath());
            //System.out.println(f.getParent());
            if ( f.exists() ) {
                FileUtils.deletedelete(f);
            }
            try {
                final BEncodedHeap map = new BEncodedHeap(f, 4);
                // put some values into the map
                final Map<String, byte[]> m = new HashMap<String, byte[]>();
                m.put("k", "000".getBytes());
                map.insert("123".getBytes(), m);
                m.put("k", "111".getBytes());
                map.insert("456".getBytes(), m);
                m.put("k", "222".getBytes());
                map.insert("789".getBytes(), m);
                // iterate over keys
                Map.Entry<byte[], Map<String, byte[]>> entry;
                final Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = map.iterator();
                while ( i.hasNext() ) {
                    entry = i.next();
                    System.out.println(ASCII.String(entry.getKey()) + ": " + entry.getValue());
                }
                // clean up
                map.close();
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e ) {
                ConcurrentLog.logException(e);
            }
        } else {
            final File f = new File(args[0]);
            try {
                Map.Entry<byte[], Map<String, byte[]>> entry;
                final BEncodedHeap map = new BEncodedHeap(f, 12);
                final Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = map.iterator();
                while ( i.hasNext() ) {
                    entry = i.next();
                    System.out.println(ASCII.String(entry.getKey()) + ": " + entry.getValue());
                }
                map.close();
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
            }
        }
    }

}
