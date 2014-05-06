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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.ymark.YMarkUtil;
import net.yacy.kelondro.util.FileUtils;


public class Tables implements Iterable<String> {

    private final static String p1 = "(?:^|.*,)";
    private final static String p2 = "((?:";
    private final static String p3 = ")(?:,.*|$)){";
    private final static String CIDX = "_cidx";
    private final static int NOINDEX = 50000;
    private final static int RAMINDEX = 100000;

	private static final String suffix = ".bheap";
    private static final String system_table_pkcounter = "pkcounter";
    private static final String system_table_pkcounter_counterName = "pk";

    private final File location;
    private final ConcurrentHashMap<String, BEncodedHeap> tables;
    private final ConcurrentHashMap<String, TablesColumnIndex> cidx;
    private int keymaxlen;

    // use our own formatter to prevent concurrency locks with other processes
    private final static GenericFormatter my_SHORT_MILSEC_FORMATTER  = new GenericFormatter(GenericFormatter.FORMAT_SHORT_MILSEC, 1);

    public Tables(final File location, final int keymaxlen) {
        this.location = new File(location.getAbsolutePath());
        if (!this.location.exists()) this.location.mkdirs();
        this.keymaxlen = keymaxlen;
        this.tables = new ConcurrentHashMap<String, BEncodedHeap>();
        final String[] files = this.location.list();
        File file;
        // lazy initialization: do not open the database files here
        for (final String f: files) {
            if (f.endsWith(suffix)) {
                file = new File(this.location, f);
                if (file.length() == 0) {
                    file.delete();
                    continue;
                }
            }
        }
        this.cidx = new ConcurrentHashMap<String, TablesColumnIndex>();
    }

    public TablesColumnIndex getIndex(final String tableName, TablesColumnIndex.INDEXTYPE indexType) throws TableColumnIndexException, IOException {
    	final TablesColumnIndex index;
    	switch(indexType) {
	    	case RAM:
	    		index = new TablesColumnRAMIndex();
	    		break;
	    	case BLOB:
	    		final String idx_table = tableName+CIDX;
	    		BEncodedHeap bheap;
	   			bheap = this.getHeap(idx_table);
	   			index =  new TablesColumnBLOBIndex(bheap);
	   			break;
	   		default:
	   			throw new TableColumnIndexException("Unsupported TableColumnIndex: "+indexType.name());
    	}
    	return index;
    }

    public TablesColumnIndex getIndex(final String tableName) throws TableColumnIndexException {
    	// return an existing index
        final TablesColumnIndex tci = this.cidx.get(tableName);
    	if (tci != null) {
    		return tci;
    	}

    	// create a new index
    	int size;
    	try {
			size = this.size(tableName);
		} catch (final IOException e) {
			size = 0;
		}

    	final TablesColumnIndex index;

    	if(size < NOINDEX) {
    		throw new TableColumnIndexException("TableColumnIndex not available for tables with less than "+NOINDEX+" rows: "+tableName);
    	}
    	if(size < RAMINDEX) {
    		index = new TablesColumnRAMIndex();
    	} else {
        	final String idx_table = tableName+CIDX;
    		BEncodedHeap bheap;
    		try {
    			bheap = this.getHeap(idx_table);
    		} catch (final IOException e) {
    			bheap = null;
    			ConcurrentLog.logException(e);
    		}
    		if(bheap != null) {
    			index =  new TablesColumnBLOBIndex(bheap);
    		} else {
    			index = new TablesColumnRAMIndex();
    		}
    	}
    	this.cidx.put(tableName, index);
    	return index;
    }

    public boolean hasIndex (final String tableName) {
    	return this.cidx.contains(tableName);
    }

    public boolean hasIndex(final String tableName, final String columnName) {
        final TablesColumnIndex tci = this.cidx.get(tableName);
    	if (tci != null) {
    		return tci.hasIndex(columnName);
    	}
    	try {
			if(this.has(tableName+CIDX, YMarkUtil.getKeyId(columnName))) {
				return true;
			}
		} catch (final IOException e) {
			ConcurrentLog.logException(e);
		}
    	return false;
    }

    public Iterator<Row> getByIndex(final String table, final String whereColumn, final String separator, final String whereValue) {
    	final HashSet<Tables.Row> rows = new HashSet<Tables.Row>();
    	final TreeSet<byte[]> set1 = new TreeSet<byte[]>(TablesColumnIndex.NATURALORDER);
    	final TreeSet<byte[]> set2 = new TreeSet<byte[]>(TablesColumnIndex.NATURALORDER);
    	final String[] values = whereValue.split(separator);
    	if(this.hasIndex(table, whereColumn)) {
    		try {
    			final TablesColumnIndex index = this.getIndex(table);
    			for(int i=0; i<values.length; i++) {
    			    final Collection<byte[]> b = index.get(whereColumn, values[i]);
        			if (b != null) {
    	        		final Iterator<byte[]> biter = b.iterator();
    	        		while(biter.hasNext()) {
    	        			set1.add(biter.next());
    	        		}
    	        		if(i==0) {
    	        			set2.addAll(set1);
    	        		} else {
    	        			set2.retainAll(set1);
    	        		}
    	        		set1.clear();
    	    		}
    			}
    			for(byte[] pk : set2) {
    				rows.add(this.select(table, pk));
    			}

			} catch (final Exception e) {
				ConcurrentLog.logException(e);
				return new HashSet<Row>().iterator();
			}
    	} else if (!separator.isEmpty()) {
        	final StringBuilder patternBuilder = new StringBuilder(256);
        	patternBuilder.append(p1);
        	patternBuilder.append(p2);
        	for (final String value : values) {
        		patternBuilder.append(Pattern.quote(value));
            	patternBuilder.append('|');
    		}
        	patternBuilder.deleteCharAt(patternBuilder.length()-1);
        	patternBuilder.append(p3);
        	patternBuilder.append(values.length);
        	patternBuilder.append('}');
        	final Pattern p = Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
    		try {
				return this.iterator(table, whereColumn, p);
			} catch (final IOException e) {
				ConcurrentLog.logException(e);
				return new HashSet<Row>().iterator();
			}
    	} else {
    		try {
				return this.iterator(table, whereColumn, UTF8.getBytes(whereValue));
			} catch (final IOException e) {
				ConcurrentLog.logException(e);
				return new HashSet<Row>().iterator();
			}
    	}
    	return rows.iterator();
    }

    @Override
    public Iterator<String> iterator() {
        return getTablenames().iterator();
    }
    
    public Set<String> getTablenames() {
        // we did a lazy initialization, but here we must discover all actually existing tables
        String tablename;
        File file;
        final String[] files = this.location.list();
        for (final String f: files) {
            if (f.endsWith(suffix)) {
                file = new File(this.location, f);
                if (file.length() == 0) {
                    continue;
                }
                tablename = f.substring(0, f.length() - suffix.length());
                try {
                    getHeap(tablename);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }
        // now the list of tables is enriched, return an iterator
        return this.tables.keySet();
    }

    public void close(final String tablename) {
        final BEncodedHeap heap = this.tables.remove(tablename);
        if (heap == null) return;
        heap.close();
    }

    public synchronized void close() {
        for (final BEncodedHeap heap: this.tables.values()) heap.close();
        this.tables.clear();
    }
    
    public void clear() {
        Set<String> tablenames = this.getTablenames();
        for (String tablename: tablenames) this.clear(tablename);
    }

    public void clear(final String tablename) {
        try {
            BEncodedHeap heap = getHeap(tablename);
            if (heap != null) {
                final File f = heap.getFile();
                heap.clear();
                heap.close();
                FileUtils.deletedelete(f);
                heap = null;
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } finally {
            this.tables.remove(tablename);
        }
    }

    public boolean hasHeap(final String tablename) {
        try {
            return getHeap(tablename) != null;
        } catch (final IOException e) {
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

    /**
     * get the total number of known tables
     * @return
     */
    public int size() {
        return this.tables.size();
    }

    public int size(final String table) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        return heap.size();
    }

    private byte[] ukey(final String tablename) throws IOException, SpaceExceededException {
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

    private String int2key(final int i) {
        final StringBuilder sb = new StringBuilder(this.keymaxlen);
        final String is = Integer.toString(i);
        for (int j = 0; j < this.keymaxlen - is.length(); j++) sb.append('0');
        sb.append(is);
        return sb.toString();
    }

    /**
     * insert a map into a table using a new unique key
     * @param tablename
     * @param map
     * @throws SpaceExceededException
     * @throws IOException
     * @throws SpaceExceededException
     */
    public byte[] insert(final String tablename, final Map<String, byte[]> map) throws IOException, SpaceExceededException {
        final byte[] uk = ukey(tablename);
        update(tablename, uk, map);
        final BEncodedHeap heap = getHeap(system_table_pkcounter);
        heap.insert(UTF8.getBytes(tablename), system_table_pkcounter_counterName, uk);
        return uk;
    }

    public void insert(final String table, final byte[] pk, final Map<String, byte[]> map) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        try {
            heap.insert(pk, map);
        } catch (final SpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void insert(final String table, final Row row) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        try {
            heap.insert(row.pk, row);
        } catch (final SpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void update(final String table, final byte[] pk, final Map<String, byte[]> map) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        try {
            heap.update(pk, map);
        } catch (final SpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void update(final String table, final Row row) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        try {
            heap.update(row.pk, row);
        } catch (final SpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }

    public byte[] createRow(final String table) throws IOException, SpaceExceededException {
        return this.insert(table, new ConcurrentHashMap<String, byte[]>());
    }

    public Row select(final String table, final byte[] pk) throws IOException, SpaceExceededException {
        final BEncodedHeap heap = getHeap(table);
        final Map<String,byte[]> b = heap.get(pk);
        if (b != null) return new Row(pk, b);
        return null;
    }

    public void delete(final String table, final byte[] pk) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        heap.delete(pk);
    }

    public boolean has(final String table, final byte[] key) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        return heap.containsKey(key);
    }

    public Iterator<byte[]> keys(final String table) throws IOException {
        final BEncodedHeap heap = getHeap(table);
        return heap.keys();
    }

    public Iterator<Row> iterator(final String table) throws IOException {
        return new RowIterator(table);
    }

    public Iterator<Row> iterator(final String table, final String whereColumn, final byte[] whereValue) throws IOException {
        return new RowIterator(table, whereColumn, whereValue);
    }

    public Iterator<Row> iterator(final String table, final String whereColumn, final Pattern wherePattern) throws IOException {
        return new RowIterator(table, whereColumn, wherePattern);
    }

    public Iterator<Row> iterator(final String table, final Pattern wherePattern) throws IOException {
        return new RowIterator(table, wherePattern);
    }

    public Collection<Row> orderByPK(final Iterator<Row> rowIterator, int maxcount) {
        final TreeMap<String, Row> sortTree = new TreeMap<String, Row>();
        Row row;
        while ((maxcount < 0 || maxcount-- > 0) && rowIterator.hasNext()) {
            row = rowIterator.next();
            sortTree.put(UTF8.String(row.pk), row);
        }
        return sortTree.values();
    }

    public Collection<Row> orderBy(final Iterator<Row> rowIterator, int maxcount, final String sortColumn) {
        final TreeMap<String, Row> sortTree = new TreeMap<String, Row>();
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

    public ArrayList<String> columns(final String table) throws IOException {
        final BEncodedHeap heap = getHeap(table);
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
        public RowIterator(final String table) throws IOException {
            this.whereColumn = null;
            this.whereValue = null;
            this.wherePattern = null;
            final BEncodedHeap heap = getHeap(table);
            this.i = heap.iterator();
        }

        /**
         * iterator that iterates all elements in the given table
         * where a given column is equal to a given value
         * @param table
         * @param whereColumn
         * @param whereValue
         * @throws IOException
         */
        public RowIterator(final String table, final String whereColumn, final byte[] whereValue) throws IOException {
            assert whereColumn != null || whereValue == null;
            this.whereColumn = whereColumn;
            this.whereValue = whereValue;
            this.wherePattern = null;
            final BEncodedHeap heap = getHeap(table);
            this.i = heap.iterator();
        }

        /**
         * iterator that iterates all elements in the given table
         * where a given column matches with a given value
         * @param table
         * @param whereColumn
         * @param wherePattern
         * @throws IOException
         */
        public RowIterator(final String table, final String whereColumn, final Pattern wherePattern) throws IOException {
            this.whereColumn = whereColumn;
            this.whereValue = null;
            this.wherePattern = wherePattern == null || wherePattern.toString().isEmpty() ? null : wherePattern;
            final BEncodedHeap heap = getHeap(table);
            this.i = heap.iterator();
        }

        /**
         * iterator that iterates all elements in the given table
         * where any column matches with a given value
         * @param table
         * @param pattern
         * @throws IOException
         */
        public RowIterator(final String table, final Pattern pattern) throws IOException {
            this.whereColumn = null;
            this.whereValue = null;
            this.wherePattern = pattern == null || pattern.toString().isEmpty() ? null : pattern;
            final BEncodedHeap heap = getHeap(table);
            this.i = heap.iterator();
        }

        @Override
        protected Row next0() {
            if (this.i == null) return null;
        	Row r;
            while (this.i.hasNext()) {
                r = new Row(this.i.next());
                if (this.whereValue != null) {
                    if (ByteBuffer.equals(r.get(this.whereColumn), this.whereValue)) return r;
                } else if (this.wherePattern != null) {
                    if (this.whereColumn == null) {
                        // shall match any column
                        for (final byte[] b: r.values()) {
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
            putAll(map);
        }

        public void put(final String colname, final String value) {
            super.put(colname, UTF8.getBytes(value));
        }

        public void put(final String colname, final int value) {
            super.put(colname, ASCII.getBytes(Integer.toString(value)));
        }

        public void put(final String colname, final long value) {
            super.put(colname, ASCII.getBytes(Long.toString(value)));
        }

        public void put(final String colname, final Date value) {
            super.put(colname, UTF8.getBytes(my_SHORT_MILSEC_FORMATTER.format(value)));
        }

        public byte[] get(final String colname, final byte[] dflt) {
            final byte[] r = this.get(colname);
            if (r == null) return dflt;
            return r;
        }

        public String get(final String colname, final String dflt) {
            final byte[] r = this.get(colname);
            if (r == null) return dflt;
            return UTF8.String(r);
        }

        public int get(final String colname, final int dflt) {
            final byte[] r = this.get(colname);
            if (r == null) return dflt;
            try {
                return (int) ByteArray.parseDecimal(r);
            } catch (final NumberFormatException e) {
                return dflt;
            }
        }

        public long get(final String colname, final long dflt) {
            final byte[] r = this.get(colname);
            if (r == null) return dflt;
            try {
                return ByteArray.parseDecimal(r);
            } catch (final NumberFormatException e) {
                return dflt;
            }
        }

        public Date get(final String colname, final Date dflt) {
            final byte[] r = this.get(colname);
            if (r == null) return dflt;
            try {
                return my_SHORT_MILSEC_FORMATTER.parse(UTF8.String(r));
            } catch (final ParseException e) {
                return dflt;
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(this.size() * 40);
            sb.append('{');
            for (final Map.Entry<String, byte[]> entry: entrySet()) {
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

        private Row(final byte[] pk, final String k0, final byte[] v0) {
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
            final StringBuilder sb = new StringBuilder(Tables.this.keymaxlen + 20 * this.size());
            sb.append(UTF8.String(this.pk)).append(":").append(super.toString());
            return sb.toString();
        }
    }

    public static void main(final String[] args) {
        // test the class
        final File f = new File(new File("maptest").getAbsolutePath());
        // System.out.println(f.getAbsolutePath());
        // System.out.println(f.getParent());
        try {
            final Tables map = new Tables(f.getParentFile(), 4);
            // put some values into the map
            final Map<String, byte[]> m = new HashMap<String, byte[]>();
            m.put("k", "000".getBytes());
            map.update("testdao", "123".getBytes(), m);
            m.put("k", "111".getBytes());
            map.update("testdao", "456".getBytes(), m);
            m.put("k", "222".getBytes());
            map.update("testdao", "789".getBytes(), m);
            // iterate over keys
            final Iterator<Row> i = map.iterator("testdao");
            while (i.hasNext()) {
                System.out.println(i.next().toString());
            }
            // clean up
            map.close();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }

}
