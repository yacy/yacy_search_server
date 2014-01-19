// MapDataMining.java
// -----------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 as part of kelondroMap on http://www.anomic.de
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
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.Word;


public class MapDataMining extends MapHeap {

    private final static Long LONG0 = Long.valueOf(0);
    private final static Float FLOAT0 = Float.valueOf(0.0f);

    private final String[] sortfields, longaccfields, floataccfields;
    private Map<String, ScoreMap<String>> sortClusterMap; // a String-kelondroMScoreCluster - relation
    private Map<String, Long>   accLong; // to store accumulations of Long cells
    private Map<String, Float> accFloat; // to store accumulations of Float cells
    private final MapColumnIndex columnIndex; // to store fast select-where indexes

	@SuppressWarnings("unchecked")
	public MapDataMining(final File heapFile,
            final int keylength,
            final ByteOrder ordering,
            final int buffermax,
            final int cachesize,
            final String[] sortfields,
            final String[] longaccfields,
            final String[] floataccfields) throws IOException {
        super(heapFile, keylength, ordering, buffermax, cachesize, ' ');

        // create fast ordering clusters and acc fields
        this.sortfields = sortfields;
        this.longaccfields = longaccfields;
        this.floataccfields = floataccfields;

        this.columnIndex = new MapColumnIndex();

        ScoreMap<String>[] cluster = null;
        if (sortfields == null) this.sortClusterMap = null; else {
            this.sortClusterMap = new ConcurrentHashMap<String, ScoreMap<String>>();
            cluster = new ScoreMap[sortfields.length];
            for (int i = 0; i < sortfields.length; i++) {
                cluster[i] = new ConcurrentScoreMap<String>();
            }
        }

        Long[] longaccumulator = null;
        Float[] floataccumulator = null;
        if (longaccfields == null) {
        	this.accLong = null;
        } else {
            this.accLong = new ConcurrentHashMap<String, Long>();
            longaccumulator = new Long[longaccfields.length];
            for (int i = 0; i < longaccfields.length; i++) {
                longaccumulator[i] = LONG0;
            }
        }
        if (floataccfields == null) {
            this.accFloat = null;
        } else {
            this.accFloat = new ConcurrentHashMap<String, Float>();
            floataccumulator = new Float[floataccfields.length];
            for (int i = 0; i < floataccfields.length; i++) {
                floataccumulator[i] = FLOAT0;
            }
        }

        // fill cluster and accumulator with values
        if (sortfields != null || longaccfields != null || floataccfields != null) try {
            final CloneableIterator<byte[]> it = super.keys(true, false);
            byte[] mapnameb;
            String cell;
            long valuel;
            float valued;
            Map<String, String> map;
            while (it.hasNext()) {
                mapnameb = it.next();
                try {
                    map = super.get(mapnameb);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.warn("MapDataMining", e.getMessage());
                    break;
                }
                if (map == null) break;

                if (sortfields != null && cluster != null) {
                    for (int i = 0; i < sortfields.length; i++) {
                        cell = map.get(sortfields[i]);
                        if (cell != null) cluster[i].set(UTF8.String(mapnameb), object2score(cell));
                    }
                }

                if (longaccfields != null && longaccumulator != null) {
                    for (int i = 0; i < longaccfields.length; i++) {
                        cell = map.get(longaccfields[i]);
                        valuel = 0;
                        if (cell != null) try {
                            valuel = Long.parseLong(cell);
                            longaccumulator[i] = Long.valueOf(longaccumulator[i].longValue() + valuel);
                        } catch (final NumberFormatException e) {}
                    }
                }

                if (floataccfields != null && floataccumulator != null) {
                    for (int i = 0; i < floataccfields.length; i++) {
                        cell = map.get(floataccfields[i]);
                        valued = 0f;
                        if (cell != null) try {
                            valued = Float.parseFloat(cell);
                            floataccumulator[i] = new Float(floataccumulator[i].floatValue() + valued);
                        } catch (final NumberFormatException e) {}
                    }
                }
            }
        } catch (final IOException e) {}

        // fill cluster
        if (sortfields != null && cluster != null) {
            for (int i = 0; i < sortfields.length; i++) this.sortClusterMap.put(sortfields[i], cluster[i]);
        }

        // fill acc map
        if (longaccfields != null && longaccumulator != null) {
            for (int i = 0; i < longaccfields.length; i++) this.accLong.put(longaccfields[i], longaccumulator[i]);
        }
        if (floataccfields != null && floataccumulator != null) {
            for (int i = 0; i < floataccfields.length; i++) this.accFloat.put(floataccfields[i], floataccumulator[i]);
        }
    }

    @Override
    public synchronized void clear() {
    	super.clear();
        if (this.sortfields == null) this.sortClusterMap = null; else {
            this.sortClusterMap = new HashMap<String, ScoreMap<String>>();
            for (final String sortfield : this.sortfields) {
            	this.sortClusterMap.put(sortfield, new ConcurrentScoreMap<String>());
            }
        }

        if (this.longaccfields == null) {
            this.accLong = null;
        } else {
            this.accLong = new HashMap<String, Long>();
            for (final String longaccfield : this.longaccfields) {
                this.accLong.put(longaccfield, LONG0);
            }
        }
        if (this.floataccfields == null) {
            this.accFloat = null;
        } else {
            this.accFloat = new HashMap<String, Float>();
            for (final String floataccfield : this.floataccfields) {
                this.accFloat.put(floataccfield, FLOAT0);
            }
        }

        this.columnIndex.clear();
    }

    @Override
    public synchronized void insert(final byte[] key, final Map<String, String> newMap) throws IOException, SpaceExceededException {
        assert (key != null);
        assert (key.length > 0);
        assert (newMap != null);

        // update elementCount
        if ((this.longaccfields != null) || (this.floataccfields != null)) {
            final Map<String, String> oldMap = super.get(key, false);
            if (oldMap != null) {
                // element exists, update acc
                updateAcc(oldMap, false);
            }

            // update accumulators with new values (add)
            updateAcc(newMap, true);
        }

        super.insert(key, newMap);

        // update sortCluster
        if (this.sortClusterMap != null) updateSortCluster(UTF8.String(key), newMap);

        this.columnIndex.update(key, newMap);
    }

    private void updateAcc(final Map<String, String> map, final boolean add) {
        String value;
        long valuel;
        float valued;
        Long longaccumulator;
        Float floataccumulator;
        if (this.longaccfields != null) {
            for (final String longaccfield : this.longaccfields) {
                value = map.get(longaccfield);
                if (value != null) {
                    try {
                        valuel = Long.parseLong(value);
                        longaccumulator = this.accLong.get(longaccfield);
                        if (add) {
                            this.accLong.put(longaccfield, Long.valueOf(longaccumulator.longValue() + valuel));
                        } else {
                            this.accLong.put(longaccfield, Long.valueOf(longaccumulator.longValue() - valuel));
                        }
                    } catch (final NumberFormatException e) {}
                }
            }
        }
        if (this.floataccfields != null) {
            for (final String floataccfield : this.floataccfields) {
                value = map.get(floataccfield);
                if (value != null) {
                    try {
                        valued = Float.parseFloat(value);
                        floataccumulator = this.accFloat.get(floataccfield);
                        if (add) {
                            this.accFloat.put(floataccfield, Float.valueOf(floataccumulator.floatValue() + valued));
                        } else {
                            this.accFloat.put(floataccfield, Float.valueOf(floataccumulator.floatValue() - valued));
                        }
                    } catch (final NumberFormatException e) {}
                }
            }
        }
    }

    private void updateSortCluster(final String key, final Map<String, String> map) {
        Object cell;
        ScoreMap<String> cluster;
        for (final String sortfield : this.sortfields) {
            cell = map.get(sortfield);
            if (cell != null) {
                cluster = this.sortClusterMap.get(sortfield);
                cluster.set(key, object2score(cell));
                this.sortClusterMap.put(sortfield, cluster);
            }
        }
    }

    @Override
    public synchronized void delete(final byte[] key) throws IOException {
        if (key == null) return;

        // update elementCount
        if (this.sortfields != null || this.longaccfields != null || this.floataccfields != null) {
            Map<String, String> map;
            try {
                map = super.get(key, false);
                if (map != null) {

                    // update accumulators (subtract)
                    if (this.longaccfields != null || this.floataccfields != null) updateAcc(map, false);

                    // remove from sortCluster
                    if (this.sortfields != null) deleteSortCluster(UTF8.String(key));
                }
            } catch (final SpaceExceededException e) {
                map = null;
                ConcurrentLog.logException(e);
            }
        }
        super.delete(key);

        this.columnIndex.delete(key);
    }

    private void deleteSortCluster(final String key) {
        if (key == null) return;
        ScoreMap<String> cluster;
        for (final String sortfield : this.sortfields) {
            cluster = this.sortClusterMap.get(sortfield);
            cluster.delete(key);
            this.sortClusterMap.put(sortfield, cluster);
        }
    }

    private synchronized Iterator<byte[]> keys(final boolean up, /* sorted by */ final String field) {
        // sorted iteration using the sortClusters
        if (this.sortClusterMap == null) return null;
        final ScoreMap<String> cluster = this.sortClusterMap.get(field);
        if (cluster == null) return null; // sort field does not exist
        //System.out.println("DEBUG: cluster for field " + field + ": " + cluster.toString());
        return new string2bytearrayIterator(cluster.keys(up));
    }

    private synchronized Iterator<byte[]> keys() throws IOException {
        return super.keys(true, null);
    }

    private static class string2bytearrayIterator implements Iterator<byte[]> {

        private final Iterator<String> s;

        private string2bytearrayIterator(final Iterator<String> s) {
            this.s = s;
        }

        @Override
        public boolean hasNext() {
            return this.s.hasNext();
        }

        @Override
        public byte[] next() {
            final String r = this.s.next();
            if (r == null) return null;
            return UTF8.getBytes(r);
        }

        @Override
        public void remove() {
            this.s.remove();
        }

    }

    public synchronized Collection<byte[]> select(final String whereKey, final String isValue) throws IOException {
        Collection<byte[]> idx = null;
        try {
            idx = this.columnIndex.getIndex(whereKey, isValue);
        } catch (final UnsupportedOperationException e) {
            this.columnIndex.init(whereKey, isValue, new FullMapIterator(keys()));
            try {
                idx = this.columnIndex.getIndex(whereKey, isValue);
            } catch (final UnsupportedOperationException ee) {
                throw ee;
            }
        }
        return idx;
    }

    public synchronized Iterator<Map.Entry<byte[], Map<String, String>>> entries(final boolean up, final String field) {
        return new FullMapIterator(keys(up, field));
    }

    public synchronized long getLongAcc(final String field) {
        final Long accumulator = this.accLong.get(field);
        if (accumulator == null) return -1;
        return accumulator.longValue();
    }

    public synchronized float getFloatAcc(final String field) {
        final Float accumulator = this.accFloat.get(field);
        if (accumulator == null) return -1;
        return accumulator.floatValue();
    }

    @Override
    public int size() {
        return super.size();
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public synchronized void close() {
        // close cluster
        if (this.sortClusterMap != null) {
            for (final String sortfield : this.sortfields)
                this.sortClusterMap.remove(sortfield);
            this.sortClusterMap = null;
        }

        super.close();
    }
    
    private static final String shortDateFormatString = "yyyyMMddHHmmss";
    private static final SimpleDateFormat shortFormatter = new SimpleDateFormat(shortDateFormatString, Locale.US);
    private static final long minutemillis = 60000;
    private static long date2000 = 0;

    static {
        try {
            date2000 = shortFormatter.parse("20000101000000").getTime();
        } catch (final ParseException e) {}
    }

    private static final byte[] plainByteArray = new byte[256];
    static {
        for (int i = 0; i < 32; i++) plainByteArray[i] = (byte) i;
        for (int i = 32; i < 96; i++) plainByteArray[i] = (byte) (i - 32);
        for (int i = 96; i < 128; i++) plainByteArray[i] = (byte) (i - 64);
        for (int i = 128; i < 256; i++) plainByteArray[i] = (byte) (i & 0X20);
    }

    private static int object2score(Object o) {
        if (o instanceof Integer) return ((Integer) o).intValue();
        if (o instanceof Long) {
            final long l = ((Long) o).longValue();
            if (l < Integer.MAX_VALUE) return (int) l;
            return (int) (l & Integer.MAX_VALUE);
        }
        if (o instanceof Float) {
            final double d = 1000f * ((Float) o).floatValue();
            return (int) Math.round(d);
        }
        if (o instanceof Double) {
            final double d = 1000d * ((Double) o).doubleValue();
            return (int) Math.round(d);
        }
        String s = null;
        if (o instanceof String) s = (String) o;
        if (o instanceof byte[]) s = UTF8.String((byte[]) o);

        // this can be used to calculate a score from a string
        if (s == null || s.isEmpty() || s.charAt(0) == '-') return 0;
        try {
            long l = 0;
            if (s.length() == shortDateFormatString.length()) {
                // try a date
                l = ((shortFormatter.parse(s).getTime() - date2000) / minutemillis);
                if (l < 0) l = 0;
            } else {
                // try a number
                l = Long.parseLong(s);
            }
            // fix out-of-ranges
            if (l > Integer.MAX_VALUE) return (int) (l & Integer.MAX_VALUE);
            if (l < 0) {
                System.out.println("string2score: negative score for input " + s);
                return 0;
            }
            return (int) l;
        } catch (final Throwable e) {
            // try it lex
            int len = s.length();
            if (len > 5) len = 5;
            int c = 0;
            for (int i = 0; i < len; i++) {
                c <<= 6;
                c += plainByteArray[(byte) s.charAt(i)];
            }
            for (int i = len; i < 5; i++) c <<= 6;
            if (c < 0) {
                System.out.println("string2score: negative score for input " + s);
                return 0;
            }
            return c;
        }
    }

/*
    public byte[] lookupBy(
            final String whereKey,
            final String isValue
    ) {


    }
*/

    public static void main(final String[] args) {
        try {
            File f = new File("/tmp/MapDataMinig.test.db");
            f.delete();
            final MapDataMining db = new MapDataMining(f, Word.commonHashLength, Base64Order.enhancedCoder, 1024 * 512, 500, new String[] {"X"}, new String[] {"X"}, new String[] {});
            final Map<String, String> m1 = new HashMap<String, String>();
            long t = System.currentTimeMillis();
            m1.put("X", Long.toString(t));
            db.put("abcdefghijk1".getBytes(), m1);
            final Map<String, String> m2 = new HashMap<String, String>();
            m2.put("X", Long.toString(t - 1000));
            db.put("abcdefghijk2".getBytes(), m2);
            final Map<String, String> m3 = new HashMap<String, String>();
            m3.put("X", Long.toString(t + 2000));
            db.put("abcdefghijk3".getBytes(), m3);

            // iterate the keys, sorted by field X in ascending order (must be: abcdefghijk2 - abcdefghijk1 - abcdefghijk3)
            final Iterator<byte[]> i1 = db.keys(true, "X");
            byte[] k;
            while (i1.hasNext()) {
                k = i1.next();
                System.out.println(new String(k));
            }

            // iterate the maps, sorted by field X in descending order (must be: abcdefghijk3 - abcdefghijk1 - abcdefghijk2)
            final Iterator<Map.Entry<byte[], Map<String, String>>> i2 = db.entries(false, "X");
            Map.Entry<byte[], Map<String, String>> e;
            while (i2.hasNext()) {
                e = i2.next();
                System.out.println(UTF8.String(e.getKey()) + ":" + e.getValue());
            }

            System.exit(0);
        } catch (final IOException e) {
            e.printStackTrace();
        }

    }
}
