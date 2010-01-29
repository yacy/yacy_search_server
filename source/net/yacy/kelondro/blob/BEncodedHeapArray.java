// BEncodedHeapArray.java
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;


public class BEncodedHeapArray {
 
    private static final String suffix = ".bheap";
    private static final String system_table_pkcounter = "pkcounter";
    private static final String system_table_pkcounter_counterName = "pk";
    
    private File location;
    private ConcurrentHashMap<String, BEncodedHeap> tables;
    private int keymaxlen;
    
    public BEncodedHeapArray(final File location, final int keymaxlen) {
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
    
    private BEncodedHeap getHeap(final String tablename) throws IOException {
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
        byte[] pk = select(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName);
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
    public byte[] insert(final String tablename, Map<String, byte[]> map) throws RowSpaceExceededException, IOException {
        byte[] uk = ukey(tablename);
        insert(tablename, uk, map);
        insert(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName, uk);
        return uk;
    }

    public byte[] insert(final String tablename, String key, byte[] value) throws RowSpaceExceededException, IOException {
        byte[] uk = ukey(tablename);
        insert(tablename, uk, key, value);
        insert(system_table_pkcounter, tablename.getBytes(), system_table_pkcounter_counterName, uk);
        return uk;
    }
    
    public byte[] insert(final String tablename,
            String key0, byte[] value0,
            String key1, byte[] value1
            ) throws RowSpaceExceededException, IOException {
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
            ) throws RowSpaceExceededException, IOException {
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
            ) throws RowSpaceExceededException, IOException {
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
            ) throws RowSpaceExceededException, IOException {
        BEncodedHeap heap = getHeap(table);
        heap.put(pk, key, value);
    }

    public void insert(final String table, byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1
            ) throws RowSpaceExceededException, IOException {
        BEncodedHeap heap = getHeap(table);
        heap.put(pk,
                key0, value0,
                key1, value1
                );
    }
    
    public void insert(final String table, byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2
            ) throws RowSpaceExceededException, IOException {
        BEncodedHeap heap = getHeap(table);
        heap.put(pk,
                key0, value0,
                key1, value1,
                key2, value2
                );
    }
    
    public void insert(final String table, byte[] pk,
            String key0, byte[] value0,
            String key1, byte[] value1,
            String key2, byte[] value2,
            String key3, byte[] value3
            ) throws RowSpaceExceededException, IOException {
        BEncodedHeap heap = getHeap(table);
        heap.put(pk,
                key0, value0,
                key1, value1,
                key2, value2,
                key3, value3
                );
    }

    public void insert(final String table, byte[] pk, Map<String, byte[]> map) throws RowSpaceExceededException, IOException {
        BEncodedHeap heap = getHeap(table);
        heap.put(pk, map);
    }

    public Map<String, byte[]> select(final String table, byte[] pk) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.get(pk);
    }

    public byte[] select(final String table, byte[] pk, String key) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.getProp(pk, key);
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

    public Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator(String table) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.iterator();
    }
    
    public List<String> columns(String table) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.columns();
    }
    
    public static void main(String[] args) {
        // test the class
        File f = new File(new File("maptest").getAbsolutePath());
        // System.out.println(f.getAbsolutePath());
        // System.out.println(f.getParent());
        try {
            BEncodedHeapArray map = new BEncodedHeapArray(f.getParentFile(), 4);
            // put some values into the map
            Map<String, byte[]> m = new HashMap<String, byte[]>();
            m.put("k", "000".getBytes());
            map.insert("testdao", "123".getBytes(), m);
            m.put("k", "111".getBytes());
            map.insert("testdao", "456".getBytes(), m);
            m.put("k", "222".getBytes());
            map.insert("testdao", "789".getBytes(), m);
            // iterate over keys
            Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = map.iterator("testdao");
            while (i.hasNext()) {
                Map.Entry<byte[], Map<String, byte[]>> entry = i.next();
                System.out.println(new String(entry.getKey(), "UTF-8") + ": "
                        + entry.getValue());
            }
            // clean up
            map.close();
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
    }
}
