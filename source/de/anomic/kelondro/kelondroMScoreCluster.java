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
import java.util.TreeMap;

public class kelondroMScoreCluster {
    
    private TreeMap refkeyDB;
    private TreeMap keyrefDB;
    private long gcount;
    private int encnt;
    
    public kelondroMScoreCluster()  {
        refkeyDB = new TreeMap();
        keyrefDB = new TreeMap();
        gcount = 0;
        encnt = 0;
    }
    
    public static SimpleDateFormat shortFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
    
    public static int string2score(String s) {
        // this can be used to calculate a score from a string
        try { // try a number
            return Integer.parseInt(s);
        } catch (NumberFormatException e) { 
            try { // try a date
                return (int) ((90000000 + System.currentTimeMillis() - shortFormatter.parse(s).getTime()) / 60000);
            } catch (ParseException ee) {
                // try it lex
                int len = s.length();
                if (len > 5) len = 5;
                int c = 0;
                for (int i = 0; i < len; i++) {
                    c <<= 6;
                    c += plainByteArray[(byte) s.charAt(i)];
                }
                for (int i = len; i < 5; i++) c <<= 6;
                return c;
            }
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
    
    public synchronized void incScore(Object[] objs) {
        addScore(objs, 1);
    }
    
    public synchronized void addScore(Object[] objs, int count) {
        if (objs != null)
            for (int i = 0; i < objs.length; i++)
                addScore(objs[i], count);
    }
    
    public synchronized void setScore(Object[] objs, int count) {
        if (objs != null)
            for (int i = 0; i < objs.length; i++)
                setScore(objs[i], count);
    }
     
    public synchronized void incScore(Object obj) {
        addScore(obj, 1);
    }
    
    public synchronized void addScore(Object obj, int count) {
        if (obj == null) return;
        Long cs = (Long) refkeyDB.get(obj);
        long c;
        int ec = count;
        int en;
        if (cs == null) {
            // new entry
            en = encnt++;
        } else {
            // delete old entry
            keyrefDB.remove(cs);
            c = cs.longValue();
            ec += (int) ((c & 0xFFFFFFFF00000000L) >> 32);
            //System.out.println("Debug:" + ec);
            en =  (int)  (c & 0xFFFFFFFFL);
        }
        
        // set new value
        c = scoreKey(en, ec);
        cs = new Long(c);
        Object oldcs = refkeyDB.remove(obj); if (oldcs != null) keyrefDB.remove(oldcs); // avoid memory leak
        refkeyDB.put(obj, cs);
        keyrefDB.put(cs, obj);
        
        // increase overall counter
        gcount += count;
    }
    
    public synchronized void setScore(Object obj, int count) {
        if (obj == null) return;
        //System.out.println("setScore " + obj.getClass().getName());
        Long cs = (Long) refkeyDB.get(obj);
        long c;
        int ec = count;
        int en;
        if (cs == null) {
            // new entry
            en = encnt++;
        } else {
            // delete old entry
            keyrefDB.remove(cs);
            c = cs.longValue();
            gcount -= (c & 0xFFFFFFFF00000000L) >> 32;
            en = (int) (c & 0xFFFFFFFFL);
        }
        
        // set new value
        c = scoreKey(en, ec);
        cs = new Long(c);
        Object oldcs = refkeyDB.remove(obj); if (oldcs != null) keyrefDB.remove(oldcs); // avoid memory leak
        refkeyDB.put(obj, cs);
        keyrefDB.put(cs, obj);
        
        // increase overall counter
        gcount += count;
    }
    
    public synchronized int deleteScore(Object obj) {
        if (obj == null) return -1;
        Long cs = (Long) refkeyDB.get(obj);
        if (cs == null) {
            return -1;
        } else {
            // delete entry
            keyrefDB.remove(cs);
            refkeyDB.remove(obj);
            // decrease overall counter
            long oldScore = (cs.longValue() & 0xFFFFFFFF00000000L) >> 32;
            gcount -= oldScore;
            return (int) oldScore;
        }
    }

    public synchronized boolean existsScore(Object obj) {
        return (refkeyDB.get(obj) != null);
    }
    
    public synchronized int getScore(Object obj) {
        if (obj == null) return 0;
        Long cs = (Long) refkeyDB.get(obj);
        if (cs == null) {
            return 0;
        } else {
            return (int) ((cs.longValue() & 0xFFFFFFFF00000000L) >> 32);
        }
    }
    
    public synchronized int getMaxScore() {
        if (refkeyDB.size() == 0) return -1;
	return (int) ((((Long) keyrefDB.lastKey()).longValue() & 0xFFFFFFFF00000000L) >> 32);
    }

    public int getMinScore() {
        if (refkeyDB.size() == 0) return -1;
	return (int) ((((Long) keyrefDB.firstKey()).longValue() & 0xFFFFFFFF00000000L) >> 32);
    }

    public synchronized Object getMaxObject() {
        if (refkeyDB.size() == 0) return null;
        //return getScores(1, false)[0];
	return keyrefDB.get((Long) keyrefDB.lastKey());
    }
    
    public synchronized Object getMinObject() {
        if (refkeyDB.size() == 0) return null;
        //return getScores(1, true)[0];
	return keyrefDB.get((Long) keyrefDB.firstKey());
    }
    
    public synchronized Object[] getScores(int maxCount, boolean up) {
        return getScores(maxCount, up, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    public synchronized Object[] getScores(int maxCount, boolean up, int minScore, int maxScore) {
        if (maxCount > refkeyDB.size()) maxCount = refkeyDB.size();
        Object[] s = new Object[maxCount];
        Iterator it = scores(up, minScore, maxScore);
        int i = 0;
        while ((i < maxCount) && (it.hasNext())) s[i++] = (Object) it.next();
        if (i < maxCount) {
            // re-copy the result array
            Object[] sc = new Object[i];
            System.arraycopy(s, 0, sc, 0, i);
            s = sc;
            sc = null;
        }
        return s;
    }
    
    public synchronized Iterator scores(boolean up) {
        if (up) return new simpleScoreIterator();
        else return scores(false, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    public synchronized Iterator scores(boolean up, int minScore, int maxScore) {
        return new komplexScoreIterator(up, minScore, maxScore);
    }
    
    private class komplexScoreIterator implements Iterator {

        boolean up;
        TreeMap keyrefDBcopy;
        Object n;
        int min, max;
        
        public komplexScoreIterator(boolean up, int minScore, int maxScore) {
            this.up = up;
            this.min = minScore;
            this.max = maxScore;
            this.keyrefDBcopy = (TreeMap) keyrefDB.clone(); // NoSuchElementException here?
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
                n = keyrefDBcopy.get(key);
                keyrefDBcopy.remove(key);
                score = (int) ((key.longValue() & 0xFFFFFFFF00000000L) >> 32);
                if ((score >= min) && (score <= max)) return;
                if (((up) && (score > max)) || ((!(up)) && (score < min))) {
                    keyrefDBcopy = new TreeMap();
                    n = null;
                    return;
                }
            } 
            n = null;
        }
        
        public Object next() {
            Object o = n;
            internalNext();
            return o;
        }
        
        public void remove() {
            if (n != null) deleteScore(n);
        }
        
    }
    
    private class simpleScoreIterator implements Iterator {

        Iterator ii;
        Map.Entry entry;
        
        public simpleScoreIterator() {
            ii = keyrefDB.entrySet().iterator();
        }
       
        public boolean hasNext() {
            return ii.hasNext();
        }
        
        public Object next() {
            entry = (Map.Entry) ii.next();
            return entry.getValue();
        }
        
        public void remove() {
            ii.remove();
        }
        
    }
        
    public static void main(String[] args) {
        System.out.println("Test for Score: start");
        kelondroMScoreCluster s = new kelondroMScoreCluster();
	int c = 0;

	// create cluster
        long time = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
	    s.addScore("score#" + i + "xxx" + i + "xxx" + i + "xxx" + i + "xxx", i/10);
	    c += i/10;
	}
	/*
        System.out.println("result:");
        Object[] result;
        result = s.getScores(s.size(), true);
        for (int i = 0; i < s.size(); i++) System.out.println("up: " + result[i]);
        result = s.getScores(s.size(), false);
        for (int i = 0; i < s.size(); i++) System.out.println("down: " + result[i]);
	*/
        System.out.println("finished create. time = " + (System.currentTimeMillis() - time));
        System.out.println("total=" + s.totalCount() + ", elements=" + s.size() + ", redundant count=" + c);

	// delete cluster
        time = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
	    s.deleteScore("score#" + i + "xxx" + i + "xxx" + i + "xxx" + i + "xxx");
	    c -= i/10;
	}
        System.out.println("finished delete. time = " + (System.currentTimeMillis() - time));
        System.out.println("total=" + s.totalCount() + ", elements=" + s.size() + ", redundant count=" + c);

    }
}
