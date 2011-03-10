// Tables.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2010 on http://yacy.net
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


package net.yacy.kelondro.blob;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteArray;
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

    // use our own formatter to prevent concurrency locks with other processes
    private final static GenericFormatter my_SHORT_MILSEC_FORMATTER  = new GenericFormatter(GenericFormatter.FORMAT_SHORT_MILSEC, 1);

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
    
    public BEncodedHeap getHeap(final String tablename) throws IOException {
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
    
    private byte[] ukey(String tablename) throws IOException, RowSpaceExceededException {
        Row row = select(system_table_pkcounter, UTF8.getBytes(tablename));
        if (row == null) {
            // table counter entry in pkcounter table does not exist: make a new table entry
            row = new Row(UTF8.getBytes(tablename), system_table_pkcounter_counterName, UTF8.getBytes(int2key(0)));
            update(system_table_pkcounter, row);
        }
        byte[] pk = row.get(system_table_pkcounter_counterName);
        int pki;
        if (pk == null) {
            pki = size(tablename);
        } else {
            pki = (int) (ByteArray.parseDecimal(pk) + 1);
        }
        while (true) {
            pk = UTF8.getBytes(int2key(pki));
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
     * @throws RowSpaceExceededException 
     */
    public byte[] insert(final String tablename, Map<String, byte[]> map) throws IOException, RowSpaceExceededException {
        byte[] uk = ukey(tablename);
        update(tablename, uk, map);
        BEncodedHeap heap = getHeap(system_table_pkcounter);
        heap.insert(UTF8.getBytes(tablename), system_table_pkcounter_counterName, uk);
        return uk;
    }

    public void insert(final String table, byte[] pk, Map<String, byte[]> map) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.insert(pk, map);
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void insert(final String table, Row row) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.insert(row.pk, row);
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public void update(final String table, byte[] pk, Map<String, byte[]> map) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.update(pk, map);
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void update(final String table, Row row) throws IOException {
        BEncodedHeap heap = getHeap(table);
        try {
            heap.update(row.pk, row);
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public byte[] createRow(String table) throws IOException, RowSpaceExceededException {
        return this.insert(table, new ConcurrentHashMap<String, byte[]>());
    }
    
    public Row select(final String table, byte[] pk) throws IOException, RowSpaceExceededException {
        BEncodedHeap heap = getHeap(table);
        if (heap.containsKey(pk)) return new Row(pk, heap.get(pk));
        return null;
    }

    public void delete(final String table, byte[] pk) throws IOException {
        BEncodedHeap heap = getHeap(table);
        heap.delete(pk);
    }

    public boolean has(String table, byte[] key) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.containsKey(key);
    }

    public Iterator<byte[]> keys(String table) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.keys();
    }

    public Iterator<Row> iterator(String table) throws IOException {
        return new RowIterator(table);
    }
    
    public Iterator<Row> iterator(String table, String whereColumn, byte[] whereValue) throws IOException {
        return new RowIterator(table, whereColumn, whereValue);
    }
    
    public Iterator<Row> iterator(String table, String whereColumn, Pattern wherePattern) throws IOException {
        return new RowIterator(table, whereColumn, wherePattern);
    }
    
    public Iterator<Row> iterator(String table, Pattern wherePattern) throws IOException {
        return new RowIterator(table, wherePattern);
    }
    
    public Collection<Row> orderByPK(Iterator<Row> rowIterator, int maxcount) {
        TreeMap<String, Row> sortTree = new TreeMap<String, Row>();
        Row row;
        while ((maxcount < 0 || maxcount-- > 0) && rowIterator.hasNext()) {
            row = rowIterator.next();
            sortTree.put(UTF8.String(row.pk), row);
        }
        return sortTree.values();
    }
    
    public Collection<Row> orderBy(Iterator<Row> rowIterator, int maxcount, String sortColumn) {
        TreeMap<String, Row> sortTree = new TreeMap<String, Row>();
        Row row;
        byte[] r;
        while ((maxcount < 0 || maxcount-- > 0) && rowIterator.hasNext()) {
            row = rowIterator.next();
            r = row.get(sortColumn);
            if (r == null) {
                sortTree.put("0000" + UTF8.String(row.pk), row);
            } else {
                sortTree.put(UTF8.String(r) + UTF8.String(row.pk), row);
            }
        }
        return sortTree.values();
    }
    
    public ArrayList<String> columns(String table) throws IOException {
        BEncodedHeap heap = getHeap(table);
        return heap.columns();
    }

    public class RowIterator extends LookAheadIterator<Row> implements Iterator<Row> {

        private final String whereColumn;
        private final byte[] whereValue;
        private final Pattern wherePattern;
        private final Iterator<Map.Entry<byte[], Map<String, byte[]>>> i;
        
        /**
         * iterator that iterates all elements in the given table
         * @param table
         * @throws IOException
         */
        public RowIterator(String table) throws IOException {
            this.whereColumn = null;
            this.whereValue = null;
            this.wherePattern = null;
            BEncodedHeap heap = getHeap(table);
            i = heap.iterator();
        }
        
        /**
         * iterator that iterates all elements in the given table
         * where a given column is equal to a given value
         * @param table
         * @param whereColumn
         * @param whereValue
         * @throws IOException
         */
        public RowIterator(String table, String whereColumn, byte[] whereValue) throws IOException {
            assert whereColumn != null || whereValue == null;
            this.whereColumn = whereColumn;
            this.whereValue = whereValue;
            this.wherePattern = null;
            BEncodedHeap heap = getHeap(table);
            i = heap.iterator();
        }
        
        /**
         * iterator that iterates all elements in the given table
         * where a given column matches with a given value
         * @param table
         * @param whereColumn
         * @param wherePattern
         * @throws IOException
         */
        public RowIterator(String table, String whereColumn, Pattern wherePattern) throws IOException {
            this.whereColumn = whereColumn;
            this.whereValue = null;
            this.wherePattern = wherePattern;
            BEncodedHeap heap = getHeap(table);
            i = heap.iterator();
        }
        
        /**
         * iterator that iterates all elements in the given table
         * where any column matches with a given value
         * @param table
         * @param pattern
         * @throws IOException
         */
        public RowIterator(String table, Pattern pattern) throws IOException {
            this.whereColumn = null;
            this.whereValue = null;
            this.wherePattern = pattern;
            BEncodedHeap heap = getHeap(table);
            i = heap.iterator();
        }
        
        protected Row next0() {
            while (i.hasNext()) {
                Row r = new Row(i.next());
                if (this.whereValue != null) {
                    if (ByteBuffer.equals(r.get(this.whereColumn), this.whereValue)) return r;
                } else if (this.wherePattern != null) {
                    if (this.whereColumn == null) {
                        // shall match any column
                        for (byte[] b: r.values()) {
                            if (this.wherePattern.matcher(UTF8.String(b)).matches()) return r;
                        }
                    } else {
                        // must match the given column
                        if (this.wherePattern.matcher(UTF8.String(r.get(this.whereColumn))).matches()) return r;
                    }
                } else {
                    return r;
                }                
            }
            return null;
        }
        
    }
    
    public static class Data extends LinkedHashMap<String, byte[]> {
        
        private static final long serialVersionUID = 978426054043749337L;
        
        public Data() {
            super();
        }
        
        private Data(final Map<String, byte[]> map) {
            super();
            assert map != null;
            this.putAll(map);
        }
        
        public void put(String colname, String value) {
            super.put(colname, UTF8.getBytes(value));
        }
        
        public void put(String colname, int value) {
            super.put(colname, UTF8.getBytes(Integer.toString(value)));
        }
        
        public void put(String colname, long value) {
            super.put(colname, UTF8.getBytes(Long.toString(value)));
        }
        
        public void put(String colname, Date value) {
            super.put(colname, UTF8.getBytes(my_SHORT_MILSEC_FORMATTER.format(value)));
        }
        
        public byte[] get(String colname, byte[] dflt) {
            byte[] r = this.get(colname);
            if (r == null) return dflt;
            return r;
        }
        
        public String get(String colname, String dflt) {
            byte[] r = this.get(colname);
            if (r == null) return dflt;
            return UTF8.String(r);
        }
        
        public int get(String colname, int dflt) {
            byte[] r = this.get(colname);
            if (r == null) return dflt;
            try {
                return (int) ByteArray.parseDecimal(r);
            } catch (NumberFormatException e) {
                return dflt;
            }
        }
        
        public long get(String colname, long dflt) {
            byte[] r = this.get(colname);
            if (r == null) return dflt;
            try {
                return ByteArray.parseDecimal(r);
            } catch (NumberFormatException e) {
                return dflt;
            }
        }
        
        public Date get(String colname, Date dflt) {
            byte[] r = this.get(colname);
            if (r == null) return dflt;
            try {
                return my_SHORT_MILSEC_FORMATTER.parse(UTF8.String(r));
            } catch (ParseException e) {
                return dflt;
            }
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(this.size() * 40);
            sb.append('{');
            for (Map.Entry<String, byte[]> entry: this.entrySet()) {
                sb.append(entry.getKey()).append('=').append(UTF8.String(entry.getValue())).append(", ");
            }
            if (sb.length() > 1) sb.setLength(sb.length() - 2);
            sb.append('}');
            return sb.toString();
        }
    }
    
    public class Row extends Data {
        
        private static final long serialVersionUID = 978426054043749338L;
        
        private final byte[] pk;
        
        private Row(final Map.Entry<byte[], Map<String, byte[]>> entry) {
            super(entry.getValue());
            assert entry != null;
            assert entry.getKey() != null;
            assert entry.getValue() != null;
            this.pk = entry.getKey();
        }
        
        private Row(final byte[] pk, final Map<String, byte[]> map) {
            super(map);
            assert pk != null;
            assert map != null;
            this.pk = pk;
        }
        
        private Row(final byte[] pk, String k0, byte[] v0) {
            super();
            assert k0 != null;
            assert v0 != null;
            this.put(k0, v0);
            this.pk = pk;
        }
        
        public byte[] getPK() {
            return this.pk;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(keymaxlen + 20 * this.size());
            sb.append(UTF8.String(pk)).append(":").append(super.toString());
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
            map.update("testdao", "123".getBytes(), m);
            m.put("k", "111".getBytes());
            map.update("testdao", "456".getBytes(), m);
            m.put("k", "222".getBytes());
            map.update("testdao", "789".getBytes(), m);
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
