// plasmaSearchProfile.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created: 17.10.2005
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

package de.anomic.plasma;

import java.util.HashMap;
import java.lang.StringBuffer;
import java.lang.Cloneable;

/**
 *
 * This class provides timing properties for search processes
 * It shall be used to initiate a search and also to evaluate
 * the real obtained timings after a search is performed
 */

public class plasmaSearchProfile implements Cloneable {
    
    // collection:
    // time = time to get a RWI out of RAM cache, assortments and WORDS files
    // count = maximum number of RWI-entries that shall be collected
    
    // join
    // time = time to perform the join between all collected RWIs
    // count = maximum number of entries that shall be joined
    
    // presort:
    // time = time to do a sort of the joined URL-records
    // count = maximum number of entries that shall be pre-sorted
    
    // urlfetch:
    // time = time to fetch the real URLs from the LURL database
    // count = maximum number of urls that shall be fetched
    
    // postsort:
    // time = time for final sort of URLs
    // count = maximum number oof URLs that shall be retrieved during sort
    
    // snippetfetch:
    // time = time to fetch snippets for selected URLs
    // count = maximum number of snipptes to be fetched
    
    public static final char PROCESS_COLLECTION   = 'c';
    public static final char PROCESS_JOIN         = 'j';
    public static final char PROCESS_PRESORT      = 'r';
    public static final char PROCESS_URLFETCH     = 'u';
    public static final char PROCESS_POSTSORT     = 'o';
    public static final char PROCESS_SNIPPETFETCH = 's';
    
    public static char[] sequence = new char[]{
        PROCESS_COLLECTION,
        PROCESS_JOIN,
        PROCESS_PRESORT,
        PROCESS_URLFETCH,
        PROCESS_POSTSORT,
        PROCESS_SNIPPETFETCH
    };

    private HashMap targetTime;
    private HashMap targetCount;
    private HashMap yieldTime;
    private HashMap yieldCount;
    private long timer;
    
    private plasmaSearchProfile() {
        targetTime = new HashMap();
        targetCount = new HashMap();
        yieldTime = new HashMap();
        yieldCount = new HashMap();
        timer = 0;
    }
    
    public plasmaSearchProfile(long time, int count) {
        this(
          3 * time / 12, 10 * count, 
          1 * time / 12, 10 * count, 
          1 * time / 12, 10 * count, 
          2 * time / 12,  5 * count, 
          4 * time / 12, count, 
          1 * time / 12, 1
        );
    }
    
    public plasmaSearchProfile(
            long time_collection,   int count_collection,
            long time_join,         int count_join,
            long time_presort,      int count_presort,
            long time_urlfetch,     int count_urlfetch,
            long time_postsort,     int count_postsort,
            long time_snippetfetch, int count_snippetfetch) {
        this();
        
        targetTime.put(new Character(PROCESS_COLLECTION), new Long(time_collection));
        targetTime.put(new Character(PROCESS_JOIN), new Long(time_join));
        targetTime.put(new Character(PROCESS_PRESORT), new Long(time_presort));
        targetTime.put(new Character(PROCESS_URLFETCH), new Long(time_urlfetch));
        targetTime.put(new Character(PROCESS_POSTSORT), new Long(time_postsort));
        targetTime.put(new Character(PROCESS_SNIPPETFETCH), new Long(time_snippetfetch));
        targetCount.put(new Character(PROCESS_COLLECTION), new Integer(count_collection));
        targetCount.put(new Character(PROCESS_JOIN), new Integer(count_join));
        targetCount.put(new Character(PROCESS_PRESORT), new Integer(count_presort));
        targetCount.put(new Character(PROCESS_URLFETCH), new Integer(count_urlfetch));
        targetCount.put(new Character(PROCESS_POSTSORT), new Integer(count_postsort));
        targetCount.put(new Character(PROCESS_SNIPPETFETCH), new Integer(count_snippetfetch));
        
    }

    public Object clone() {
        plasmaSearchProfile p = new plasmaSearchProfile();
        p.targetTime = (HashMap) this.targetTime.clone();
        p.targetCount = (HashMap) this.targetCount.clone();
        p.yieldTime = (HashMap) this.yieldTime.clone();
        p.yieldCount = (HashMap) this.yieldCount.clone();
        return (Object) p;
    }
    
    public plasmaSearchProfile(String s) {
        targetTime = new HashMap();
        targetCount = new HashMap();
        yieldTime = new HashMap();
        yieldCount = new HashMap();
        
        intoMap(s, targetTime, targetCount);
    }
    
    public long duetime() {
        // returns the old duetime value as sum of all waiting times
        long d = 0;
        for (int i = 0; i < sequence.length; i++) {
            d += ((Long) targetTime.get(new Character(sequence[i]))).longValue();
        }
        return d;
    }
    
    public void putYield(String s) {
        intoMap(s, yieldTime, yieldCount);
    }

    public String yieldToString() {
        return toString(yieldTime, yieldCount);
    }
    
    public String targetToString() {
        return toString(targetTime, targetCount);
    }
    
    public long getTargetTime(char type) {
        // sum up all time that was demanded and subtract all that had been wasted
        long sum = 0;
        Long t;
        Character element;
        for (int i = 0; i < sequence.length; i++) {
            element = new Character(sequence[i]);
            t = (Long) targetTime.get(element);
            if (t != null) sum += t.longValue();
            if (type == sequence[i]) return (sum < 0) ? 0 : sum;
            t = (Long) yieldTime.get(element);
            if (t != null) sum -= t.longValue();
        }
        return 0;
    }
    
    public int getTargetCount(char type) {
        Integer i = (Integer) targetCount.get(new Character(type));
        if (i == null) return -1; else return i.intValue();
    }
    
    public long getYieldTime(char type) {
        Long l = (Long) yieldTime.get(new Character(type));
        if (l == null) return -1; else return l.longValue();
    }
    
    public int getYieldCount(char type) {
        Integer i = (Integer) yieldCount.get(new Character(type));
        if (i == null) return -1; else return i.intValue();
    }
    
    public void startTimer() {
        this.timer = System.currentTimeMillis();
    }
    
    public void setYieldTime(char type) {
        // sets a time that is computed using the timer
        long t = System.currentTimeMillis() - this.timer;
        yieldTime.put(new Character(type), new Long(t));
    }
    
    public void setYieldCount(char type, int count) {
        yieldCount.put(new Character(type), new Integer(count));
    }
    
    public String reportToString() {
        return "target=" + toString(targetTime, targetCount) + "; yield=" + toString(yieldTime, yieldCount);
    }
    
    public static String toString(HashMap time, HashMap count) {
        // put this into a format in such a way that it can be send in a http header or post argument
        // that means that no '=' or spaces are allowed
        StringBuffer sb = new StringBuffer(sequence.length * 10);
        Character element;
        Integer xi;
        Long xl;
        for (int i = 0; i < sequence.length; i++) {
            element = new Character(sequence[i]);
            sb.append("t");
            sb.append(element);
            xl = (Long) time.get(element);
            sb.append((xl == null) ? "0" : xl.toString());
            sb.append("|");
            sb.append("c");
            sb.append(element);
            xi = (Integer) count.get(element);
            sb.append((xi == null) ? "0" : xi.toString());
            sb.append("|");
        }
        return sb.toString();
    }
    
    public static void intoMap(String s, HashMap time, HashMap count) {
        // this is the reverse method to toString
        int p = 0;
        char ct;
        String elt;
        String v;
        int p1;
        while ((p < s.length()) && ((p1 = s.indexOf('|', p)) > 0)) {
            ct = s.charAt(p);
            elt = s.substring(p + 1, p + 2);
            v = s.substring(p + 2, p1);
            if (ct == 't') {
                time.put(elt, new Long(Long.parseLong(v)));
            } else {
                count.put(elt, new Integer(Integer.parseInt(v)));
            }
        }
    }
    
}
