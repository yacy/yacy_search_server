// kelondroMScoreCluster.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.09.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.kelondro;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

public final class kelondroMScoreCluster<E> {
    
    protected final TreeMap<E, Long> refkeyDB; // a mapping from a reference to the cluster key
    protected final TreeMap<Long, E> keyrefDB; // a mapping from the cluster key to the reference
    private long gcount;
    private int encnt;
    
    public kelondroMScoreCluster()  {
        refkeyDB = new TreeMap<E, Long>();
        keyrefDB = new TreeMap<Long, E>();
        gcount = 0;
        encnt = 0;
    }
    
    public static final String shortDateFormatString = "yyyyMMddHHmmss";
    public static final SimpleDateFormat shortFormatter = new SimpleDateFormat(shortDateFormatString);
    public static final long minutemillis = 60000;
    public static long date2000 = 0;
    
    static {
        try {
            date2000 = shortFormatter.parse("20000101000000").getTime();
        } catch (ParseException e) {}
    }
    
    public static int object2score(Object o) {
        if (o instanceof Integer) return ((Integer) o).intValue();
        if (o instanceof Long) {
            long l = ((Long) o).longValue();
            if (l < (long) Integer.MAX_VALUE) return (int) l;
            o = ((Long) o).toString();
        }
        if (o instanceof Double) {
            double d = 1000d * ((Double) o).doubleValue();
            return (int) Math.round(d);
        }
        String s = "";
        if (o instanceof String) s = (String) o;
        
        // this can be used to calculate a score from a string
        if ((s == null) || (s.length() == 0) || (s.charAt(0) == '-')) return 0;
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
            if (l > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            if (l < 0) {
                System.out.println("string2score: negative score for input " + s);
                return 0;
            }
            return (int) l;
        } catch (Exception e) {
            // try it lex
            int len = s.length();
            if (len > 5) len = 5;
            int c = 0;
            for (int i = 0; i < len; i++) {
                c <<= 6;
                c += plainByteArray[(byte) s.charAt(i)];
            }
            for (int i = len; i < 5; i++) c <<= 6;
            if (c > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            if (c < 0) {
                System.out.println("string2score: negative score for input " + s);
                return 0;
            }
            return c;
        }
    }
    
    private static byte[] plainByteArray;
    static {
        plainByteArray = new byte[256];
        for (int i = 0; i < 32; i++) plainByteArray[i] = (byte) i;
        for (int i = 32; i < 96; i++) plainByteArray[i] = (byte) (i - 32);
        for (int i = 96; i < 128; i++) plainByteArray[i] = (byte) (i - 64);
        for (int i = 128; i < 256; i++) plainByteArray[i] = (byte) (i & 0X20);
    }
    
    private long scoreKey(int elementNr, int elementCount) {
        return (((long) (elementCount & 0xFFFFFFFFL)) << 32) | ((long) (elementNr & 0xFFFFFFFFL));
    }
    
    public synchronized long totalCount() {
        return gcount;
    }
    
    public synchronized int size() {
        return refkeyDB.size();
    }
    
    public synchronized void incScore(E[] objs) {
        for (int i = 0; i < objs.length; i++) addScore(objs[i], 1);
    }
    
    public synchronized void decScore(E[] objs) {
        for (int i = 0; i < objs.length; i++) addScore(objs[i], -1);
    }
    
    public synchronized void incScore(E obj) {
        addScore(obj, 1);
    }
    
    public synchronized void decScore(E obj) {
        addScore(obj, -1);
    }
    
    public synchronized void setScore(E obj, int newScore) {
        if (obj == null) return;
        //System.out.println("setScore " + obj.getClass().getName());
        Long usk = refkeyDB.remove(obj); // get unique score key, old entry is not needed any more
        if (newScore < 0) throw new kelondroOutOfLimitsException(newScore);
        
        if (usk == null) {
            // set new value
            usk = new Long(scoreKey(encnt++, newScore));
            
            // put new value into cluster
            refkeyDB.put(obj, usk);
            keyrefDB.put(usk, obj);
            
        } else {
            // delete old entry
            keyrefDB.remove(usk);
            
            // get previous handle and score
            long c = usk.longValue();
            int oldScore = (int) ((c & 0xFFFFFFFF00000000L) >> 32);
            int oldHandle = (int) (c & 0xFFFFFFFFL);
            gcount -= oldScore;
            
            // set new value
            usk = new Long(scoreKey(oldHandle, newScore)); // generates an unique key for a specific score
            refkeyDB.put(obj, usk);
            keyrefDB.put(usk, obj);
        }
        
        // increase overall counter
        gcount += newScore;
    }
    
    public synchronized void addScore(E obj, int incrementScore) {
        if (obj == null) return;
        //System.out.println("setScore " + obj.getClass().getName());
        Long usk = refkeyDB.remove(obj); // get unique score key, old entry is not needed any more
        
        if (usk == null) {
            // set new value
            if (incrementScore < 0) throw new kelondroOutOfLimitsException(incrementScore);
            usk = new Long(scoreKey(encnt++, incrementScore));
            
            // put new value into cluster
            refkeyDB.put(obj, usk);
            keyrefDB.put(usk, obj);
            
        } else {
            // delete old entry
            keyrefDB.remove(usk);
            
            // get previous handle and score
            long c = usk.longValue();
            int oldScore = (int) ((c & 0xFFFFFFFF00000000L) >> 32);
            int oldHandle = (int) (c & 0xFFFFFFFFL);
            
            // set new value
            int newValue = oldScore + incrementScore;
            if (newValue < 0) throw new kelondroOutOfLimitsException(newValue);
            usk = new Long(scoreKey(oldHandle, newValue)); // generates an unique key for a specific score
            refkeyDB.put(obj, usk);
            keyrefDB.put(usk, obj);
            
        }
        
        // increase overall counter
        gcount += incrementScore;
    }
    
    public synchronized int deleteScore(E obj) {
        // deletes entry and returns previous score
        if (obj == null) return 0;
        //System.out.println("setScore " + obj.getClass().getName());
        Long usk = refkeyDB.remove(obj); // get unique score key, old entry is not needed any more
        if (usk == null) return 0;

        // delete old entry
        keyrefDB.remove(usk);
        
        // get previous handle and score
        int oldScore = (int) ((usk.longValue() & 0xFFFFFFFF00000000L) >> 32);

        // decrease overall counter
        gcount -= oldScore;
        
        return oldScore;        
    }

    public synchronized boolean existsScore(E obj) {
        return (refkeyDB.get(obj) != null);
    }
    
    public synchronized int getScore(E obj) {
        if (obj == null) return 0;
        Long cs = refkeyDB.get(obj);
        if (cs == null) return 0;
        return (int) ((cs.longValue() & 0xFFFFFFFF00000000L) >> 32);
    }
    
    public synchronized int getMaxScore() {
        if (refkeyDB.size() == 0) return -1;
        return (int) ((keyrefDB.lastKey().longValue() & 0xFFFFFFFF00000000L) >> 32);
    }

    public synchronized int getMinScore() {
        if (refkeyDB.size() == 0) return -1;
        return (int) ((keyrefDB.firstKey().longValue() & 0xFFFFFFFF00000000L) >> 32);
    }

    public synchronized E getMaxObject() {
        if (refkeyDB.size() == 0) return null;
        //return getScores(1, false)[0];
        return keyrefDB.get(keyrefDB.lastKey());
    }
    
    public synchronized E getMinObject() {
        if (refkeyDB.size() == 0) return null;
        //return getScores(1, true)[0];
        return keyrefDB.get(keyrefDB.firstKey());
    }
    
    public synchronized E[] getScores(int maxCount, boolean up) {
        return getScores(maxCount, up, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    @SuppressWarnings("unchecked")
	public synchronized E[] getScores(int maxCount, boolean up, int minScore, int maxScore) {
        if (maxCount > refkeyDB.size()) maxCount = refkeyDB.size();
        E[] s = (E[]) new Object[maxCount];
        Iterator<E> it = scores(up, minScore, maxScore);
        int i = 0;
        while ((i < maxCount) && (it.hasNext())) s[i++] = it.next();
        if (i < maxCount) {
            // re-copy the result array
            E[] sc = (E[]) new Object[i];
            System.arraycopy(s, 0, sc, 0, i);
            s = sc;
            sc = null;
        }
        return s;
    }
    
    public String toString() {
        return refkeyDB + " / " + keyrefDB;
    }
    
    public synchronized Iterator<E> scores(boolean up) {
        if (up) return new simpleScoreIterator<E>();
        return new reverseScoreIterator<E>();
    }
    
    public synchronized Iterator<E> scores(boolean up, int minScore, int maxScore) {
        return new komplexScoreIterator<E>(up, minScore, maxScore);
    }
    
    private class komplexScoreIterator<A extends E> implements Iterator<E> {

        boolean up;
        TreeMap<Long, E> keyrefDBcopy;
        E n;
        int min, max;
        
		@SuppressWarnings("unchecked")
		public komplexScoreIterator(boolean up, int minScore, int maxScore) {
            this.up = up;
            this.min = minScore;
            this.max = maxScore;
            this.keyrefDBcopy = (TreeMap<Long, E>) keyrefDB.clone(); // NoSuchElementException here?
            internalNext();
        }
       
        public boolean hasNext() {
            return (n != null);
        }
        
        private void internalNext() {
            Long key;
            int score = (max + min) / 2;
            while (keyrefDBcopy.size() > 0) {
                key = (Long) ((up) ? keyrefDBcopy.firstKey() : keyrefDBcopy.lastKey());
                n = (E) keyrefDBcopy.remove(key);
                score = (int) ((key.longValue() & 0xFFFFFFFF00000000L) >> 32);
                if ((score >= min) && (score <= max)) return;
                if (((up) && (score > max)) || ((!(up)) && (score < min))) {
                    keyrefDBcopy = new TreeMap<Long, E>();
                    n = null;
                    return;
                }
            } 
            n = null;
        }
        
        public E next() {
            E o = n;
            internalNext();
            return o;
        }
        
        public void remove() {
            if (n != null) deleteScore(n);
        }
        
    }
    
    private class reverseScoreIterator<A extends E> implements Iterator<E> {

        SortedMap<Long, E> view;
        Long key;
        
        public reverseScoreIterator() {
            view = keyrefDB;
        }
       
        public boolean hasNext() {
            return view.size() > 0;
        }
        
        public E next() {
            key = view.lastKey();
            view = view.headMap(key);
            E value = keyrefDB.get(key);
            //System.out.println("cluster reverse iterator: score = " + ((((Long) key).longValue() & 0xFFFFFFFF00000000L) >> 32) + ", handle = " + (((Long) key).longValue() & 0xFFFFFFFFL) + ", value = " + value);
            return value;
        }
        
        public void remove() {
            Object val = keyrefDB.remove(key);
            if (val != null) refkeyDB.remove(val);
        }
        
    }
    
    private class simpleScoreIterator<A extends E> implements Iterator<E> {

        Iterator<Map.Entry<Long, E>> ii;
        Map.Entry<Long, E> entry;
        
        public simpleScoreIterator() {
            ii = keyrefDB.entrySet().iterator();
        }
       
        public boolean hasNext() {
            return ii.hasNext();
        }
        
        public E next() {
            entry = ii.next();
            //System.out.println("cluster simple iterator: score = " + ((((Long) entry.getKey()).longValue() & 0xFFFFFFFF00000000L) >> 32) + ", handle = " + (((Long) entry.getKey()).longValue() & 0xFFFFFFFFL) + ", value = " + entry.getValue());
            return entry.getValue();
        }
        
        public void remove() {
            ii.remove();
            if (entry.getValue() != null) refkeyDB.remove(entry.getValue());
        }
        
    }
        
    public static void main(String[] args) {
        
        String t = "ZZZZZZZZZZ";
        System.out.println("score of " + t + ": " + object2score(t));
        if (args.length > 0) {
            System.out.println("score of " + args[0] + ": " + object2score(args[0]));
            System.exit(0);
        }
        
        System.out.println("Test for Score: start");
        kelondroMScoreCluster<String> s = new kelondroMScoreCluster<String>();
        long c = 0;

        // create cluster
        long time = System.currentTimeMillis();
        Random random = new Random(1234);
        int r;
        int count = 20;
        int[] mem = new int[count];
        
        for (int x = 0; x < 100; x++) {
            for (int i = 0; i < count; i++) {
                r = random.nextInt();
                mem[i] = r;
                s.addScore("score#" + r, r);
                c += r;
            }
            
            // delete some
            int p;
            for (int i = 0; i < (count / 2); i++) {
                p = (int) (random.nextFloat() * count);
                if (s.existsScore("score#" + mem[p])) {
                    System.out.println("delete score#" + mem[p]);
                    s.deleteScore("score#" + mem[p]);
                    c -= mem[p];
                }
            }
        }
        
        System.out.println("result:");
        Object[] result;
        result = s.getScores(s.size(), true);
        for (int i = 0; i < s.size(); i++) System.out.println("up: " + result[i]);
        result = s.getScores(s.size(), false);
        for (int i = 0; i < s.size(); i++) System.out.println("down: " + result[i]);
	
        System.out.println("finished create. time = " + (System.currentTimeMillis() - time));
        System.out.println("total=" + s.totalCount() + ", elements=" + s.size() + ", redundant count=" + c);

        /*
	// delete cluster
        time = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
	    s.deleteScore("score#" + i + "xxx" + i + "xxx" + i + "xxx" + i + "xxx");
	    c -= i/10;
	}
        System.out.println("finished delete. time = " + (System.currentTimeMillis() - time));
        System.out.println("total=" + s.totalCount() + ", elements=" + s.size() + ", redundant count=" + c);
        */
    }
}
