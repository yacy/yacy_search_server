// IndexTest.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.04.2009 on http://www.anomic.de
//
// This is a part of the kelondro database,
// which is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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


package de.anomic.kelondro.index;

import java.util.HashMap;
import java.util.Random;
import java.util.TreeMap;

import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.util.MemoryControl;

/**
 * this is a speed test for performance tuning of b64 and hashing functions
 */

public class IndexTest {

    public static byte[] randomHash(final long r0, final long r1) {
        // a long can have 64 bit, but a 12-byte hash can have 6 * 12 = 72 bits
        // so we construct a generic Hash using two long values
        final String s = (Base64Order.enhancedCoder.encodeLong(Math.abs(r0), 6) +
                    Base64Order.enhancedCoder.encodeLong(Math.abs(r1), 6));
        return s.getBytes();
    }
    
    public static byte[] randomHash(final Random r) {
        return randomHash(r.nextLong(), r.nextLong());
    }
    
    public static final long mb = 1024 * 1024;
    
    public static void main(String[] args) {
        System.out.println("Performance test: comparing HashMap, TreeMap and kelondroRow\n");
        if (args.length == 0) {
            System.out.println("use one parameter: number of test entries");
            System.exit(0);
        }
        
        // pre-generate test data so it will not influence test case time
        int count = Integer.parseInt(args[0]);
        byte[][] tests = new byte[count][];
        Random r = new Random(0);
        for (int i = 0; i < count; i++) tests[i] = randomHash(r);

        // start
        long t1 = System.currentTimeMillis();
        
        // test tree map
        Runtime.getRuntime().gc();
        long freeStartTree = MemoryControl.free();
        TreeMap<byte[], Integer> tm = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        for (int i = 0; i < count; i++) tm.put(tests[i], 1);
        long t2 = System.currentTimeMillis();
        System.out.println("time   for TreeMap<byte[]> generation: " + (t2 - t1));
        
        int bugs = 0;
        for (int i = 0; i < count; i++) if (tm.get(tests[i]) == null) bugs++;
        Runtime.getRuntime().gc();
        long freeEndTree = MemoryControl.available();
        tm.clear(); tm = null;
        long t3 = System.currentTimeMillis();
        System.out.println("time   for TreeMap<byte[]> test: " + (t3 - t2) + ", " + bugs + " bugs");
        System.out.println("memory for TreeMap<byte[]>: " + (freeStartTree - freeEndTree) / mb + " MB\n");
        
        // test hash map
        Runtime.getRuntime().gc();
        long freeStartHash = MemoryControl.available();
        HashMap<String, Integer> hm = new HashMap<String, Integer>();
        for (int i = 0; i < count; i++) hm.put(new String(tests[i]), 1);
        long t4 = System.currentTimeMillis();
        System.out.println("time   for HashMap<String> generation: " + (t4 - t3));
        
        bugs = 0;
        for (int i = 0; i < count; i++) if (hm.get(new String(tests[i])) == null) bugs++;
        Runtime.getRuntime().gc();
        long freeEndHash = MemoryControl.available();
        hm.clear(); hm = null;
        long t5 = System.currentTimeMillis();
        System.out.println("time   for HashMap<String> test: " + (t5 - t4) + ", " + bugs + " bugs");
        System.out.println("memory for HashMap<String>: " + (freeStartHash - freeEndHash) / mb + " MB\n");
        
        // test kelondro index
        Runtime.getRuntime().gc();
        long freeStartKelondro = MemoryControl.available();
        IntegerHandleIndex ii = new IntegerHandleIndex(12, Base64Order.enhancedCoder, count, count);
        for (int i = 0; i < count; i++) ii.putUnique(tests[i], 1);
        ii.get(randomHash(r)); // trigger sort
        long t6 = System.currentTimeMillis();
        System.out.println("time   for kelondroMap<byte[]> generation: " + (t6 - t5));
        
        bugs = 0;
        for (int i = 0; i < count; i++) if (ii.get(tests[i]) != 1) bugs++;
        Runtime.getRuntime().gc();
        long freeEndKelondro = MemoryControl.available();
        ii.clear(); ii = null;
        long t7 = System.currentTimeMillis();
        System.out.println("time   for kelondroMap<byte[]> test: " + (t7 - t6) + ", " + bugs + " bugs");
        System.out.println("memory for kelondroMap<byte[]>: " + (freeStartKelondro - freeEndKelondro) / mb + " MB\n");
        
        System.exit(0);
    }
}
