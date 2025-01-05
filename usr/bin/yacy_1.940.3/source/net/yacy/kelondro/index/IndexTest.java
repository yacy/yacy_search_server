// IndexTest.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.04.2009 on http://www.anomic.de
//
// This is a part of the kelondro database,
// which is a part of YaCy, a peer-to-peer based web search engine
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


package net.yacy.kelondro.index;

import java.util.HashMap;
import java.util.Random;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleMap;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.util.MemoryControl;


/**
 * this is a speed test for performance tuning of b64 and hashing functions
 */

public class IndexTest {

    public static byte[] randomHash(final long r0, final long r1) {
        // a long can have 64 bit, but a 12-byte hash can have 6 * 12 = 72 bits
        // so we construct a generic Hash using two long values
        final String s = (Base64Order.enhancedCoder.encodeLongSB(Math.abs(r0), 6).toString() +
                    Base64Order.enhancedCoder.encodeLongSB(Math.abs(r1), 6).toString());
        return ASCII.getBytes(s);
    }

    public static byte[] randomHash(final Random r) {
        return randomHash(r.nextLong(), r.nextLong());
    }

    public static final long mb = 1024 * 1024;

    public static void main(final String[] args) {

        // pre-generate test data so it will not influence test case time
        final int count = args.length == 0 ? 1000000 : Integer.parseInt(args[0]);
        byte[][] tests = new byte[count][];
        final Random r = new Random(0);
        for (int i = 0; i < count; i++) tests[i] = randomHash(r);
        System.out.println("generated " + count + " test data entries \n");

        // start
        System.out.println("\nSTANDARD JAVA CLASS MAPS \n");
        final long t1 = System.currentTimeMillis();

        // test tree map
        System.out.println("sorted map");
        Runtime.getRuntime().gc();
        final long freeStartTree = MemoryControl.free();
        TreeMap<byte[], Integer> tm = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        for (int i = 0; i < count; i++) tm.put(tests[i], 1);
        final long t2 = System.currentTimeMillis();
        System.out.println("time   for TreeMap<byte[]> generation: " + (t2 - t1));

        int bugs = 0;
        for (int i = 0; i < count; i++) if (tm.get(tests[i]) == null) bugs++;
        Runtime.getRuntime().gc();
        final long freeEndTree = MemoryControl.available();
        tm.clear(); tm = null;
        final long t3 = System.currentTimeMillis();
        System.out.println("time   for TreeMap<byte[]> test: " + (t3 - t2) + ", " + bugs + " bugs");
        System.out.println("memory for TreeMap<byte[]>: " + (freeStartTree - freeEndTree) / mb + " MB\n");

        // test hash map
        System.out.println("unsorted map");
        Runtime.getRuntime().gc();
        final long freeStartHash = MemoryControl.available();
        HashMap<String, Integer> hm = new HashMap<String, Integer>();
        for (int i = 0; i < count; i++) hm.put(UTF8.String(tests[i]), 1);
        final long t4 = System.currentTimeMillis();
        System.out.println("time   for HashMap<String> generation: " + (t4 - t3));

        bugs = 0;
        for (int i = 0; i < count; i++) if (hm.get(UTF8.String(tests[i])) == null) bugs++;
        Runtime.getRuntime().gc();
        final long freeEndHash = MemoryControl.available();
        hm.clear(); hm = null;
        final long t5 = System.currentTimeMillis();
        System.out.println("time   for HashMap<String> test: " + (t5 - t4) + ", " + bugs + " bugs");
        System.out.println("memory for HashMap<String>: " + (freeStartHash - freeEndHash) / mb + " MB\n");

        System.out.println("\nKELONDRO-ENHANCED MAPS \n");

        // test kelondro index
        System.out.println("sorted map");
        Runtime.getRuntime().gc();
        final long freeStartKelondro = MemoryControl.available();
        HandleMap ii = null;
        ii = new RowHandleMap(12, Base64Order.enhancedCoder, 4, count, "test");
        for (int i = 0; i < count; i++)
            try {
                ii.putUnique(tests[i], 1);
            } catch (final SpaceExceededException e) {
                e.printStackTrace();
            }
        ii.get(randomHash(r)); // trigger sort
        final long t6 = System.currentTimeMillis();
        System.out.println("time   for HandleMap<byte[]> generation: " + (t6 - t5));

        bugs = 0;
        for (int i = 0; i < count; i++) if (ii.get(tests[i]) != 1) bugs++;
        Runtime.getRuntime().gc();
        final long freeEndKelondro = MemoryControl.available();
        ii.clear(); ii.close(); ii = null;
        final long t7 = System.currentTimeMillis();
        System.out.println("time   for HandleMap<byte[]> test: " + (t7 - t6) + ", " + bugs + " bugs");
        System.out.println("memory for HandleMap<byte[]>: " + (freeStartKelondro - freeEndKelondro) / mb + " MB\n");

        // test ByteArray
        System.out.println("unsorted map");
        Runtime.getRuntime().gc();
        final long freeStartBA = MemoryControl.available();
        HashMap<ByteArray, Integer> bm = new HashMap<ByteArray, Integer>();
        for (int i = 0; i < count; i++) bm.put(new ByteArray(tests[i]), 1);
        final long t8 = System.currentTimeMillis();
        System.out.println("time   for HashMap<ByteArray> generation: " + (t8 - t7));

        bugs = 0;
        for (int i = 0; i < count; i++) if (bm.get(new ByteArray(tests[i])) == null) bugs++;
        Runtime.getRuntime().gc();
        final long freeEndBA = MemoryControl.available();
        bm.clear(); bm = null;
        final long t9 = System.currentTimeMillis();
        System.out.println("time   for HashMap<ByteArray> test: " + (t9 - t8) + ", " + bugs + " bugs");
        System.out.println("memory for HashMap<ByteArray>: " + (freeStartBA - freeEndBA) / mb + " MB\n");

        System.exit(0);
    }
}

/*

sorted map
time   for kelondroMap<byte[]> generation: 1781
time   for kelondroMap<byte[]> test: 2452, 0 bugs
memory for kelondroMap<byte[]>: 15 MB

unsorted map
time   for HashMap<ByteArray> generation: 828
time   for HashMap<ByteArray> test: 953, 0 bugs
memory for HashMap<ByteArray>: 9 MB

*/