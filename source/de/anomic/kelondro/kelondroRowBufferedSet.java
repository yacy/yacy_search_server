// kelondroRowBufferedSet.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 21.06.2006 on http://www.anomic.de
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

package de.anomic.kelondro;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Random;

public class kelondroRowBufferedSet extends kelondroRowSet {

    private static final long memBlockLimit = 2000000;      // do not fill cache further if the amount of available memory is less that this
    private static final int bufferFlushLimit = 10000;
    private static final int bufferFlushMinimum = 1000; 
    private final boolean useRowCollection = true;
    private kelondroProfile profile;
    private HashMap buffer;

    public kelondroRowBufferedSet(kelondroRow rowdef) {
        super(rowdef);
        buffer = new HashMap();
        profile = new kelondroProfile();
    }

    public kelondroRowBufferedSet(kelondroRow rowdef, int objectCount) {
        super(rowdef, objectCount);
        buffer = new HashMap();
        profile = new kelondroProfile();
    }
    
    private final void flush() {
        // call only in synchronized environment
        Iterator i = buffer.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            super.add((kelondroRow.Entry) entry.getValue());
        }
        buffer.clear();
    }
    
    public final void trim() {
        synchronized (buffer) {
            flush();
            super.trim();
        }
    }
    
    public void removeOne() {
        synchronized (buffer) {
            if (buffer.size() == 0) {
                super.removeOne();
            } else {
                buffer.remove(buffer.keySet().iterator().next());
            }
        }
    }
    
    public void clear() {
        synchronized (buffer) {
            super.clear();
            buffer.clear();
        }
    }
    
    public int size() {
        synchronized (buffer) {
            return buffer.size() + super.size();
        }
    }
    
    public Iterator elements() {
        synchronized (buffer) {
            flush();
        }
        return super.elements();
    }
    
    public void uniq() {
        synchronized (buffer) {
            flush();
            super.uniq();
        }
    }
    
    public String toString() {
        synchronized (buffer) {
            flush();
            return super.toString();
        }
    }
    
    public byte[] toByteArray() {
        synchronized (buffer) {
            flush();
            return super.toByteArray();
        }
    }
    
    public void add(byte[] a) {
        this.add(super.rowdef.newEntry(a));
    }
    
    public void add(kelondroRow.Entry a) {
        this.put(a);
    }
    
    public kelondroRow.Entry get(byte[] key) {
        long handle = profile.startRead();
        kelondroRow.Entry entry = null;
        synchronized (buffer) {
            entry = (kelondroRow.Entry) buffer.get(new Integer((int) kelondroNaturalOrder.decodeLong(key)));
            if ((entry == null) && (useRowCollection)) entry = super.get(key);
        }
        profile.stopRead(handle);
        return entry;
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry newentry) {
        long handle = profile.startWrite();
        byte[] key = newentry.getColBytes(super.sortColumn);
        kelondroRow.Entry oldentry = null;
        synchronized (buffer) {
            if (useRowCollection) {
                oldentry = (kelondroRow.Entry) buffer.get(new Integer((int) kelondroNaturalOrder.decodeLong(key)));
                if (oldentry == null) {
                    // try the collection
                    oldentry = super.get(key);
                    if (oldentry == null) {
                        // this was not anywhere
                        buffer.put(new Integer((int) kelondroNaturalOrder.decodeLong(key)), newentry);
                        if (((buffer.size() > bufferFlushMinimum) &&  (kelondroRecords.availableMemory() > memBlockLimit)) ||
                            (buffer.size() > bufferFlushLimit)) flush();
                    } else {
                        // replace old entry
                        super.put(newentry);
                    }
                } else {
                    // the entry is already in buffer
                    // simply replace old entry
                    buffer.put(new Integer((int) kelondroNaturalOrder.decodeLong(key)), newentry);
                }
            } else {
                oldentry = (kelondroRow.Entry) buffer.put(new Integer((int) kelondroNaturalOrder.decodeLong(key)), newentry);
            }
        }
        profile.stopWrite(handle);
        return oldentry;
    }
    
    public kelondroRow.Entry removeShift(byte[] key) {
        long handle = profile.startDelete();
        kelondroRow.Entry oldentry = null;
        synchronized (buffer) {
            if (useRowCollection) {
                oldentry = (kelondroRow.Entry) buffer.remove(new Integer((int) kelondroNaturalOrder.decodeLong(key)));
                if (oldentry == null) {
                    // try the collection
                    oldentry = super.removeShift(key);
                }
            } else {
                oldentry = (kelondroRow.Entry) buffer.remove(new Integer((int) kelondroNaturalOrder.decodeLong(key)));
            }
        }
        profile.stopDelete(handle);
        return oldentry;
    }
    
    public kelondroRow.Entry removeMarked(byte[] key) {
        long handle = profile.startDelete();
        kelondroRow.Entry oldentry = null;
        synchronized (buffer) {
            if (useRowCollection) {
                oldentry = (kelondroRow.Entry) buffer.remove(new Integer((int) kelondroNaturalOrder.decodeLong(key)));
                if (oldentry == null) {
                    // try the collection
                    return super.removeMarked(key);
                }
            } else {
                oldentry = (kelondroRow.Entry) buffer.remove(new Integer((int) kelondroNaturalOrder.decodeLong(key)));
            }
        }
        profile.stopDelete(handle);
        return oldentry;
    }
    
    public void removeMarkedAll(kelondroRowCollection c) {
        long handle = profile.startDelete();
        synchronized (buffer) {
            flush();
            super.removeMarkedAll(c);
        }
        profile.stopDelete(handle);
    }

    public kelondroProfile profile() {
        return profile;
    }
    
    public static void main(String[] args) {
        String[] test = { "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn" };
        kelondroRowBufferedSet c = new kelondroRowBufferedSet(new kelondroRow(new int[]{10, 3}));
        c.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        for (int i = 0; i < test.length; i++) c.add(test[i].getBytes());
        c.removeMarked("fuenf".getBytes());
        c.shape();
        Iterator i = c.elements();
        String s;
        System.out.print("INPUT-ITERATOR: ");
        while (i.hasNext()) {
            s = new String((byte[]) i.next()).trim();
            System.out.print(s + ", ");
            if (s.equals("drei")) i.remove();
        }
        System.out.println("");
        System.out.println("INPUT-TOSTRING: " + c.toString());
        c.shape();
        System.out.println("SORTED        : " + c.toString());
        c.uniq();
        System.out.println("UNIQ          : " + c.toString());
        c.trim();
        System.out.println("TRIM          : " + c.toString());
        
        // second test
        c = new kelondroRowBufferedSet(new kelondroRow(new int[]{10, 3}));
        c.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        Random rand = new Random(0);
        long start = System.currentTimeMillis();
        long t, d = 0;
        String w;
        for (long k = 0; k < 60000; k++) {
            t = System.currentTimeMillis();
            w = "a" + Long.toString(rand.nextLong());
            c.add(w.getBytes());
            if (k % 10000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : k) +
                    " entries/second, size = " + c.size());
        }
        System.out.println("bevore sort: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        c.shape();
        System.out.println("after sort: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        c.uniq();
        System.out.println("after uniq: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        System.out.println("RESULT SIZE: " + c.size());
        System.out.println();
        
        // third test
        c = new kelondroRowBufferedSet(new kelondroRow(new int[]{10, 3}), 60000);
        c.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
        rand = new Random(0);
        start = System.currentTimeMillis();
        d = 0;
        for (long k = 0; k < 60000; k++) {
            t = System.currentTimeMillis();
            w = "a" + Long.toString(rand.nextLong());
            if (c.get(w.getBytes()) == null) c.add(w.getBytes()); else d++;
            if (k % 10000 == 0)
                System.out.println("added " + k + " entries in " +
                    ((t - start) / 1000) + " seconds, " +
                    (((t - start) > 1000) ? (k / ((t - start) / 1000)) : k) +
                    " entries/second, " + d + " double, size = " + c.size() + 
                    ", sum = " + (c.size() + d));
        }
        System.out.println("RESULT SIZE: " + c.size());
    }
}
