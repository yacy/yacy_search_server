// kelondroIntBytesMap.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 08.06.2006 on http://www.anomic.de
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

import java.io.IOException;
import java.util.Iterator;

//import java.util.Random;

public class kelondroIntBytesMap {

    private kelondroIndex index;
    
    public kelondroIntBytesMap(int payloadSize, int initSize) {
        index = new kelondroRowSet(new kelondroRow("Cardinal key-4 {b256}, byte[] payload-" + payloadSize, kelondroNaturalOrder.naturalOrder, 0), initSize);
    }
    
    public int size() {
        try {
            return index.size();
        } catch (IOException e) {
            return 0;
        }
    }
    
    public byte[] getb(int ii) {
        kelondroRow.Entry indexentry;
        try {indexentry = index.get(kelondroNaturalOrder.encodeLong((long) ii, 4));} catch (IOException e) {return null;}
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
    public void addb(int ii, byte[] value) {
        kelondroRow.Entry newentry;
        try {
            newentry = index.row().newEntry();
            newentry.setCol(0, (long) ii);
            newentry.setCol(1, value);
            index.addUnique(newentry);
        } catch (IOException e) {}
    }

    
    public byte[] putb(int ii, byte[] value) {
        kelondroRow.Entry newentry;
        try {
            newentry = index.row().newEntry();
            newentry.setCol(0, (long) ii);
            newentry.setCol(1, value);
            kelondroRow.Entry oldentry = index.put(newentry);
            if (oldentry == null) return null;
            return oldentry.getColBytes(1);
        } catch (IOException e) {
            return null;
        }
    }

    public byte[] removeb(int ii) {
        try {
            if (index.size() == 0) return null;
            kelondroRow.Entry indexentry = index.remove(kelondroNaturalOrder.encodeLong((long) ii, 4));
            if (indexentry == null) return null;
            return indexentry.getColBytes(1);
        } catch (IOException e) {
            return null;
        }
    }
    
    public byte[] removeoneb() {
        try {
            if (index.size() == 0) return null;
            kelondroRow.Entry indexentry = index.removeOne();
            if (indexentry == null) return null;
            return indexentry.getColBytes(1);
        } catch (IOException e) {
            return null;
        }
    }
    
    public Iterator rows() {
        try {
            return index.rows(true, false, null);
        } catch (IOException e) {
            return null;
        }
    }
    
    public void flush() {
        if (index instanceof kelondroRowSet) {
            ((kelondroRowSet) index).shape();
            ((kelondroRowSet) index).trim();
        }
    }
    
    public kelondroProfile profile() {
        return index.profile();
    }
    
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        kelondroIntBytesMap c = new kelondroIntBytesMap(30, 0);
        //Random random = new Random(0);
        int x;
        for (int i = 0; i < 100000; i++) {
            //x = random.nextInt(100000);
            x = i;
            c.putb(x, new byte[30]);
            //if (c.getb(x) == null) System.out.println("consistency error at " + i + " with key " + x);
            if (i % 10000 == 0) System.out.println(i + " entries. ");
        }
        System.out.println("RESULT SIZE: " + c.size());
        System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
    }
    
}
