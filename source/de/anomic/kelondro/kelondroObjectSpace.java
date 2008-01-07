// kelondroObjectSpace.java 
// ------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 12.12.2004
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class kelondroObjectSpace {

    private static final int minSize = 10;
    private static final int maxSize = 256;
    
    private static HashMap<Integer, ArrayList<byte[]>> objHeap = new HashMap<Integer, ArrayList<byte[]>>();
    private static TreeMap<Integer, Integer> aliveNow = new TreeMap<Integer, Integer>();
    //private static TreeMap aliveMax = new TreeMap();
    
    private static void incAlive(int size) {
        final Integer s = new Integer(size);
        synchronized (aliveNow) {
            final Integer x = (Integer) aliveNow.get(s);
            if (x == null) aliveNow.put(s, new Integer(1)); else aliveNow.put(s, new Integer(x.intValue() + 1));
        }
    }
    
    private static void decAlive(int size) {
        final Integer s = new Integer(size);
        synchronized (aliveNow) {
            final Integer x = aliveNow.get(s);
            if (x == null) aliveNow.put(s, new Integer(-1)); else aliveNow.put(s, new Integer(x.intValue() - 1));
        }
    }
    
    public static byte[] alloc(int len) {
        if ((len < minSize) || (len > maxSize)) return new byte[len];
        incAlive(len);
        synchronized (objHeap) {
            ArrayList<byte[]> buf = objHeap.get(new Integer(len));
            if ((buf == null) || (buf.size() == 0)) return new byte[len];
            return buf.remove(buf.size() - 1);
        }
    }
    
    public static void recycle(byte[] b) {
        if ((b.length < minSize) || (b.length > maxSize)) {
            b = null;
            return;
        }
        decAlive(b.length);
        synchronized (objHeap) {
            final Integer i = new Integer(b.length);
            ArrayList<byte[]> buf = objHeap.get(i);
            if (buf == null) {
                buf = new ArrayList<byte[]>();
                buf.add(b);
                objHeap.put(i, buf);
            } else {
                buf.add(b);
            }
        }
        b = null;
    }
    
    public static TreeMap<Integer, Integer> statAlive() {
        return aliveNow;
    }
    
    public static TreeMap<Integer, Integer> statHeap() {
        // creates a statistic output of this object space
        // the result is a mapping from Integer (chunk size) to Integer (number of counts)
        // and shows how many Objects are held in this space for usage
        TreeMap<Integer, Integer> result = new TreeMap<Integer, Integer>();
        synchronized (objHeap) {
            Iterator<Map.Entry<Integer, ArrayList<byte[]>>> i = objHeap.entrySet().iterator();
            Map.Entry<Integer, ArrayList<byte[]>> entry;
            while (i.hasNext()) {
                entry = i.next();
                result.put(entry.getKey(), new Integer(entry.getValue().size()));
            }
        }
        return result;
    }
    
}
