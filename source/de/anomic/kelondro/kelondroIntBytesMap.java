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

public class kelondroIntBytesMap extends kelondroRowSet {

    // use this only, if key objects can ensure perfect hashing!
    // (that means, the hash computation must be unique)
    // this is given for kelondroRecords.Entry objects

    public kelondroIntBytesMap(int payloadSize, int initSize) {
        super(new kelondroRow(new int[]{4, payloadSize}), initSize);
    }
    
    public byte[] getb(int ii) {
        kelondroRow.Entry indexentry = super.get(kelondroNaturalOrder.encodeLong((long) ii, 4));
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
    public byte[] putb(int ii, byte[] value) {
        int index = -1;
        byte[] key = kelondroNaturalOrder.encodeLong((long) ii, 4);
        //System.out.println("ObjectMap PUT " + obj.hashCode() + ", size=" + size());
        synchronized (chunkcache) {
            index = find(key, 0, 4);
        }
        if (index < 0) {
            kelondroRow.Entry indexentry = rowdef.newEntry();
            indexentry.setCol(0, key);
            indexentry.setCol(1, value);
            add(indexentry);
            return null;
        } else {
            kelondroRow.Entry indexentry = get(index);
            byte[] old = indexentry.getColBytes(1);
            indexentry.setCol(1, value);
            set(index, indexentry);
            return old;
        }
    }

    public byte[] removeb(int ii) {
        kelondroRow.Entry indexentry = super.remove(kelondroNaturalOrder.encodeLong((long) ii, 4));
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
}
