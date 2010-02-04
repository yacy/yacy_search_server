// Tables.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 6539 $
// $LastChangedBy: low012 $
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.LookAheadIterator;


public class Tables {
 
    private static final String suffix = ".bheap";
    private static final String system_table_pkcounter = "pkcounter";
    private static final String system_table_pkcounter_counterName = "pk";
    
    private File location;
    private ConcurrentHashMap<String, BEncodedHeap> tables;
    int keymaxlen;
    
    public Tables(final File location, final int keymaxlen) {
        this.location = new File(location.getAbsolutePath());
        if (!this.location.exists()) this.location.mkdirs();
        this.keymaxlen = keymaxlen;
        this.tables = new ConcurrentHashMap<String, BEncodedHeap>();
        String[] files = this.location.list();
        String tablename;
        File file;
        for (String f: files) {
            if (f.endsWith(suffix)) {
                file = new File(this.location, f);
                if (file.length() == 0) {
                    file.delete();
                    continue;
                }
                tablename = f.substring(0, f.length() - suffix.length());
                try {
                    getHeap(tablename);
                } catch (IOException e) {
                }
            }
        }
    }
    
    public Iterator<String> tables() {
        return this.tables.keySet().iterator();
    }
    
    public void close(final String tablename) {
        final BEncodedHeap heap = this.tables.remove(tablename);
        if (heap == null) return;
        heap.close();
    }

    public void close() {
        for (BEncodedHeap heap: this.tables.values()) heap.close();
        this.tables.clear();
    }

    public void clear(final String tablename) throws IOException {
        BEncodedHeap heap = getHeap(tablename);
        if (heap == null) return;
        heap.clear();
        File f = heap.getFile();
        heap.close();
        heap = null;
        this.tables.remove(tablename);
        FileUtils.deletedelete(f);
    }
    
    public boolean hasHeap(final String tablename) {
        try {
            return getHeap(tablename) != null;
        } catch (IOException e) {
            return false;
        }
    }
    
    BEncodedHeap getHeap(final String tablename) throws IOException {
        final String table = tablename + suffix;
        BEncodedHeap heap = this.tables.get(tablename);
        if (heap != null) return heap;
        
        // open a new heap and register it in the tables
        final File heapf = new File(this.location, table);
        heap = new BEncodedHeap(heapf, this.keymaxlen);
        this.tables.put(tablename, heap);
        return heap;
    }

    public int size(String table) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.size();
    }
    
    private byte[] ukey(String tablename) throws IOException {
        byte[] pk = select(system_table_pkcounter, tablename.getBytes()).from(system_table_pkcounter_counterName);
        int pki;
        if (pk == null) {
            pki = size(tablename);
        } else {
            pki = Integer.parseInt(new String(pk)) + 1;
        }
        while (true) {
            pk = int2key(pki).getBytes();
            if (!has(tablename, pk)) break;
            pki++;
        }
        return pk;
    }
    
    private String int2key(int i) {
        StringBuilder sb = new StringBuilder(this.keymaxlen);
        String is = Integer.toString(i);
        for (int j = 0; j < this.keymaxlen - is.length(); j++) sb.append('0');
        sb.append(is);
        return sb.toString();
    }
    
    /**
     * insert a map into a table using a new unique key
     * @param tablename
     * @param map
     * @throws RowSpaceExceededException
     * @throws IOException
     */
    public byte[] insert(final String tablename, Map<String, byte[]> map) throws IOException {
        byte[] uk = ukey(tablename);
        insert(tablename, uk, map);
        insert(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName, uk);
        return uk;
    }

    public byte[] insert(final String tablename, String key, byte[] value) throws IOException {
        byte[] uk = ukey(tablename);
        insert(tablename, uk, key, value);
        insert(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName, uk);
        return uk;
    }
    
    public byte[] insert(final String tablename,
            String key0, byte[] value0,
            String key1, byte[] value1
            ) throws IOException {
        byte[] uk = ukey(tablename);
        insert(tablename, uk,
            key0, value0,
            key1, value1
            );
        insert(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName, uk);
        return uk;
    }
    
    public byte[] insert(final String tablename,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2
            ) throws IOException {
        byte[] uk = ukey(tablename);
        insert(tablename, uk,
            key0, value0,
            key1, value1,
            key2, value2
            );
        insert(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName, uk);
        return uk;
    }
    
    public byte[] insert(final String tablename,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2,
            String key3, byte[] value3
            ) throws IOException {
        byte[] uk = ukey(tablename);
        insert(tablename, uk,
            key0, value0,
            key1, value1,
            key2, value2,
            key3, value3
            );
        insert(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName, uk);
        return uk;
    }

    public void insert(final String table, byte[] pk,
            String key, byte[] value
            ) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.put(pk, key, value);
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void insert(final String table, byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1
            ) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.put(pk,
                key0, value0,
                key1, value1
                );
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public void insert(final String table, byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2
            ) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.put(pk,
                key0, value0,
                key1, value1,
                key2, value2
                );
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public void insert(final String table, byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2,
            String key3, byte[] value3
            ) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.put(pk,
                key0, value0,
                key1, value1,
                key2, value2,
                key3, value3
                );
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void insert(final String table, byte[] pk, Map<String, byte[]> map) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.put(pk, map);
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void insert(final String table, Row row) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.put(row.pk, row.map);
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public byte[] createRow(String table) throws IOException {
        return this.insert(table, new HashMap<String, byte[]>());
    }
    
    public Row select(final String table, byte[] pk) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return new Row(pk, heap.get(pk));
    }

    public void delete(final String table, byte[] pk) throws IOException {
        BEncodedHeap heap = getHeap(table);
        heap.delete(pk);
    }

    public boolean has(String table, byte[] key) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.has(key);
    }

    public Iterator<byte[]> keys(String table) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.keys();
    }

    public Iterator<Row> iterator(String table) throws IOException {
        return new RowIterator(table);
    }
    
    public Iterator<Row> iterator(String table, String whereKey, byte[] whereValue) throws IOException {
        return new RowIterator(table, whereKey, whereValue);
    }
    
    public Collection<Row> orderByPK(String table, int maxcount) throws IOException {
        return orderByPK(table, maxcount, null, null);
    }
    
    public Collection<Row> orderByPK(String table, int maxcount, String whereKey, byte[] whereValue) throws IOException {
        TreeMap<String, Row> sortTree = new TreeMap<String, Row>();
        Iterator<Row> i = iterator(table, whereKey, whereValue);
        Row row;
        while ((maxcount < 0 || maxcount-- > 0) && i.hasNext()) {
            row = i.next();
            sortTree.put(new String(row.pk), row);
        }
        return sortTree.values();
    }
    
    public Collection<Row> orderBy(String table, int maxcount, String sortField) throws IOException {
        return orderBy(table, maxcount, sortField, null, null);
    }
    
    public Collection<Row> orderBy(String table, int maxcount, String sortField, String whereKey, byte[] whereValue) throws IOException {
        TreeMap<String, Row> sortTree = new TreeMap<String, Row>();
        Iterator<Row> i = iterator(table, whereKey, whereValue);
        Row row;
        while ((maxcount < 0 || maxcount-- > 0) && i.hasNext()) {
            row = i.next();
            sortTree.put(new String(row.from(sortField)) + new String(row.pk), row);
        }
        return sortTree.values();
    }
    
    public List<String> columns(String table) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.columns();
    }

    public class RowIterator extends LookAheadIterator<Row> implements Iterator<Row> {

        private final String whereKey;
        private final byte[] whereValue;
        private final Iterator<Map.Entry<byte[], Map<String, byte[]>>> i;
        
        public RowIterator(String table) throws IOException {
            this.whereKey = null;
            this.whereValue = null;
            BEncodedHeap heap = getHeap(table);
            i = heap.iterator();
        }
        
        public RowIterator(String table, String whereKey, byte[] whereValue) throws IOException {
            this.whereKey = whereKey;
            this.whereValue = whereValue;
            BEncodedHeap heap = getHeap(table);
            i = heap.iterator();
        }
        
        protected Row next0() {
            while (i.hasNext()) {
                Row r = new Row(i.next());
                if (this.whereKey == null) return r;
                if (ByteBuffer.equals(r.from(this.whereKey), this.whereValue)) return r;
            }
            return null;
        }
        
    }
    
    public class Row {
        
        final byte[] pk;
        final Map<String, byte[]> map;
        
        public Row(final Map.Entry<byte[], Map<String, byte[]>> entry) {
            this.pk = entry.getKey();
            this.map = entry.getValue();
        }
        
        public Row(final byte[] pk, final Map<String, byte[]> map) {
            this.pk = pk;
            this.map = map;
        }
        
        public byte[] getPK() {
            return this.pk;
        }
        
        public byte[] from(String colname) {
            return this.map.get(colname);
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder(keymaxlen + 20 * map.size());
            sb.append(new String(pk)).append(":").append(map.toString());
            return sb.toString();
        }
    }
    
    public static void main(String[] args) {
        // test the class
        File f = new File(new File("maptest").getAbsolutePath());
        // System.out.println(f.getAbsolutePath());
        // System.out.println(f.getParent());
        try {
            Tables map = new Tables(f.getParentFile(), 4);
            // put some values into the map
            Map<String, byte[]> m = new HashMap<String, byte[]>();
            m.put("k", "000".getBytes());
            map.insert("testdao", "123".getBytes(), m);
            m.put("k", "111".getBytes());
            map.insert("testdao", "456".getBytes(), m);
            m.put("k", "222".getBytes());
            map.insert("testdao", "789".getBytes(), m);
            // iterate over keys
            Iterator<Row> i = map.iterator("testdao");
            while (i.hasNext()) {
                System.out.println(i.next().toString());
            }
            // clean up
            map.close();
        } catch (IOException e) {
            Log.logException(e);
        }
    }
}
