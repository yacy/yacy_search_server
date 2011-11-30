/**
 *  ScoreCluster
 *  Copyright 2004, 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 28.09.2004 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.ranking;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.storage.OutOfLimitsException;

public final class ClusteredScoreMap<E> extends AbstractScoreMap<E> implements ReversibleScoreMap<E> {

    protected final Map<E, Long> map; // a mapping from a reference to the cluster key
    protected final TreeMap<Long, E> pam; // a mapping from the cluster key to the reference
    private long gcount;
    private int encnt;

    public ClusteredScoreMap()  {
        this.map = new TreeMap<E, Long>();
        this.pam = new TreeMap<Long, E>();
        this.gcount = 0;
        this.encnt = 0;
    }

    public ClusteredScoreMap(final Comparator<E> c)  {
        this.map = new TreeMap<E, Long>(c);
        this.pam = new TreeMap<Long, E>();
        this.gcount = 0;
        this.encnt = 0;
    }

    public Iterator<E> iterator() {
        return this.map.keySet().iterator();
    }

    public synchronized void clear() {
        this.map.clear();
        this.pam.clear();
        this.gcount = 0;
        this.encnt = 0;
    }

    /**
     * shrink the cluster to a demanded size
     * @param maxsize
     */
    public void shrinkToMaxSize(final int maxsize) {
        if (maxsize < 0) return;
        Long key;
        synchronized (this) {
            while (this.map.size() > maxsize) {
                // find and remove smallest objects until cluster has demanded size
                key = this.pam.firstKey();
                if (key == null) break;
                this.map.remove(this.pam.remove(key));
            }
        }
    }

    /**
     * shrink the cluster in such a way that the smallest score is equal or greater than a given minScore
     * @param minScore
     */
    public void shrinkToMinScore(final int minScore) {
        int score;
        Long key;
        synchronized (this) {
            while (this.pam.size() > 0) {
                // find and remove objects where their score is smaller than the demanded minimum score
                key = this.pam.firstKey();
                if (key == null) break;
                score = (int) ((key.longValue() & 0xFFFFFFFF00000000L) >> 32);
                if (score >= minScore) break;
                this.map.remove(this.pam.remove(key));
            }
        }
    }

    public static final String shortDateFormatString = "yyyyMMddHHmmss";
    public static final SimpleDateFormat shortFormatter = new SimpleDateFormat(shortDateFormatString, Locale.US);
    public static final long minutemillis = 60000;
    public static long date2000 = 0;

    static {
        try {
            date2000 = shortFormatter.parse("20000101000000").getTime();
        } catch (final ParseException e) {}
    }

    public static int object2score(Object o) {
        if (o instanceof Integer) return ((Integer) o).intValue();
        if (o instanceof Long) {
            final long l = ((Long) o).longValue();
            if (l < Integer.MAX_VALUE) return (int) l;
            o = ((Long) o).toString();
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
        if (s == null || s.length() == 0 || s.charAt(0) == '-') return 0;
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
            if (l > Integer.MAX_VALUE) return Integer.MAX_VALUE; //(int) (l & (Integer.MAX_VALUE));
            if (l < 0) {
                System.out.println("string2score: negative score for input " + s);
                return 0;
            }
            return (int) l;
        } catch (final Exception e) {
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

    private static final byte[] plainByteArray = new byte[256];
    static {
        for (int i = 0; i < 32; i++) plainByteArray[i] = (byte) i;
        for (int i = 32; i < 96; i++) plainByteArray[i] = (byte) (i - 32);
        for (int i = 96; i < 128; i++) plainByteArray[i] = (byte) (i - 64);
        for (int i = 128; i < 256; i++) plainByteArray[i] = (byte) (i & 0X20);
    }

    private long scoreKey(final int elementNr, final int elementCount) {
        return (((elementCount & 0xFFFFFFFFL)) << 32) | ((elementNr & 0xFFFFFFFFL));
    }

    public synchronized long totalCount() {
        return this.gcount;
    }

    public synchronized int size() {
        return this.map.size();
    }

    public boolean sizeSmaller(final int size) {
        return this.map.size() < size;
    }

    public synchronized boolean isEmpty() {
        return this.map.isEmpty();
    }

    public synchronized void inc(final E obj) {
        inc(obj, 1);
    }

    public synchronized void dec(final E obj) {
        inc(obj, -1);
    }

    public void set(final E obj, final int newScore) {
        if (obj == null) return;
        synchronized (this) {
            Long usk = this.map.remove(obj); // get unique score key, old entry is not needed any more
            if (newScore < 0) throw new OutOfLimitsException(newScore);

            if (usk == null) {
                // set new value
                usk = Long.valueOf(scoreKey(this.encnt++, newScore));

                // put new value into cluster
                this.map.put(obj, usk);
                this.pam.put(usk, obj);

            } else {
                // delete old entry
                this.pam.remove(usk);

                // get previous handle and score
                final long c = usk.longValue();
                final int oldScore = (int) ((c & 0xFFFFFFFF00000000L) >> 32);
                final int oldHandle = (int) (c & 0xFFFFFFFFL);
                this.gcount -= oldScore;

                // set new value
                usk = Long.valueOf(scoreKey(oldHandle, newScore)); // generates an unique key for a specific score
                this.map.put(obj, usk);
                this.pam.put(usk, obj);
            }
        }
        // increase overall counter
        this.gcount += newScore;
    }

    public void inc(final E obj, final int incrementScore) {
        if (obj == null) return;
        synchronized (this) {
            Long usk = this.map.remove(obj); // get unique score key, old entry is not needed any more

            if (usk == null) {
                // set new value
                if (incrementScore < 0) throw new OutOfLimitsException(incrementScore);
                usk = Long.valueOf(scoreKey(this.encnt++, incrementScore));

                // put new value into cluster
                this.map.put(obj, usk);
                this.pam.put(usk, obj);

            } else {
                // delete old entry
                this.pam.remove(usk);

                // get previous handle and score
                final long c = usk.longValue();
                final int oldScore = (int) ((c & 0xFFFFFFFF00000000L) >> 32);
                final int oldHandle = (int) (c & 0xFFFFFFFFL);

                // set new value
                final int newValue = oldScore + incrementScore;
                if (newValue < 0) throw new OutOfLimitsException(newValue);
                usk = Long.valueOf(scoreKey(oldHandle, newValue)); // generates an unique key for a specific score
                this.map.put(obj, usk);
                this.pam.put(usk, obj);
            }
        }
        // increase overall counter
        this.gcount += incrementScore;
    }

    public void dec(final E obj, final int incrementScore) {
        inc(obj, -incrementScore);
    }

    public int delete(final E obj) {
        // deletes entry and returns previous score
        if (obj == null) return 0;
        final Long usk;
        synchronized (this) {
            usk = this.map.remove(obj); // get unique score key, old entry is not needed any more
            if (usk == null) return 0;

            // delete old entry
            this.pam.remove(usk);
        }

        // get previous handle and score
        final int oldScore = (int) ((usk.longValue() & 0xFFFFFFFF00000000L) >> 32);

        // decrease overall counter
        this.gcount -= oldScore;

        return oldScore;
    }

    public synchronized boolean containsKey(final E obj) {
        return this.map.containsKey(obj);
    }

    public int get(final E obj) {
        if (obj == null) return 0;
        final Long cs;
        synchronized (this) {
            cs = this.map.get(obj);
        }
        if (cs == null) return 0;
        return (int) ((cs.longValue() & 0xFFFFFFFF00000000L) >> 32);
    }

    public synchronized int getMaxScore() {
        if (this.map.isEmpty()) return -1;
        return (int) ((this.pam.lastKey().longValue() & 0xFFFFFFFF00000000L) >> 32);
    }

    public synchronized int getMinScore() {
        if (this.map.isEmpty()) return -1;
        return (int) ((this.pam.firstKey().longValue() & 0xFFFFFFFF00000000L) >> 32);
    }

    public synchronized E getMaxKey() {
        if (this.map.isEmpty()) return null;
        return this.pam.get(this.pam.lastKey());
    }

    public synchronized E getMinKey() {
        if (this.map.isEmpty()) return null;
        return this.pam.get(this.pam.firstKey());
    }

    public String toString() {
        return this.map + " / " + this.pam;
    }

    public synchronized Iterator<E> keys(final boolean up) {
        if (up) return new simpleScoreIterator<E>();
        return new reverseScoreIterator<E>();
    }

    private class reverseScoreIterator<A extends E> implements Iterator<E> {

        SortedMap<Long, E> view;
        Long key;

        public reverseScoreIterator() {
            this.view = ClusteredScoreMap.this.pam;
        }

        public boolean hasNext() {
            return !this.view.isEmpty();
        }

        public E next() {
            this.key = this.view.lastKey();
            this.view = this.view.headMap(this.key);
            final E value = ClusteredScoreMap.this.pam.get(this.key);
            //System.out.println("cluster reverse iterator: score = " + ((((Long) key).longValue() & 0xFFFFFFFF00000000L) >> 32) + ", handle = " + (((Long) key).longValue() & 0xFFFFFFFFL) + ", value = " + value);
            return value;
        }

        public void remove() {
            final Object val = ClusteredScoreMap.this.pam.remove(this.key);
            if (val != null) ClusteredScoreMap.this.map.remove(val);
        }

    }

    private class simpleScoreIterator<A extends E> implements Iterator<E> {

        Iterator<Map.Entry<Long, E>> ii;
        Map.Entry<Long, E> entry;

        public simpleScoreIterator() {
            this.ii = ClusteredScoreMap.this.pam.entrySet().iterator();
        }

        public boolean hasNext() {
            return this.ii.hasNext();
        }

        public E next() {
            this.entry = this.ii.next();
            //System.out.println("cluster simple iterator: score = " + ((((Long) entry.getKey()).longValue() & 0xFFFFFFFF00000000L) >> 32) + ", handle = " + (((Long) entry.getKey()).longValue() & 0xFFFFFFFFL) + ", value = " + entry.getValue());
            return this.entry.getValue();
        }

        public void remove() {
            this.ii.remove();
            if (this.entry.getValue() != null) ClusteredScoreMap.this.map.remove(this.entry.getValue());
        }

    }

    public static void main(final String[] args) {

        final String t = "ZZZZZZZZZZ";
        System.out.println("score of " + t + ": " + object2score(t));
        if (args.length > 0) {
            System.out.println("score of " + args[0] + ": " + object2score(args[0]));
            System.exit(0);
        }

        System.out.println("Test for Score: start");
        final ClusteredScoreMap<String> s = new ClusteredScoreMap<String>();
        long c = 0;

        // create cluster
        final long time = System.currentTimeMillis();
        final Random random = new Random(1234);
        int r;
        final int count = 20;

        for (int x = 0; x < 100000; x++) {
            for (int i = 0; i < count; i++) {
                r = Math.abs(random.nextInt(100));
                s.inc("score#" + r, r);
                c += r;
            }

            // delete some
            int p;
            for (int i = 0; i < (count / 2); i++) {
                p = Math.abs(random.nextInt(1000));
                if (s.containsKey("score#" + p)) {
                    //System.out.println("delete score#" + s.get("score#" + p));
                    c -= s.delete("score#" + p);
                }
            }
        }

        System.out.println("finished create. time = " + (System.currentTimeMillis() - time));

        System.out.println("result:");
        Iterator<String> i = s.keys(true);
        while (i.hasNext()) System.out.println("up: " + i.next());
        i = s.keys(false);
        while (i.hasNext()) System.out.println("down: " + i.next());

        System.out.println("total=" + s.totalCount() + ", elements=" + s.size() + ", redundant count=" + c);
    }
}
