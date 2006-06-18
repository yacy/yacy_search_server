// kelondroCollectionObjectMap.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 18.06.2006 on http://www.anomic.de
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

public class kelondroCollectionObjectMap extends kelondroCollection {

    // use this only, if key objects can ensure perfect hashing!
    // (that means, the hash computation must be unique)
    // this is given for kelondroRecords.Entry objects
    
    private kelondroRow indexrow;
    
    public kelondroCollectionObjectMap(int payloadSize, int objectCount) {
        super(4 + payloadSize, objectCount);
        
        // initialize row
        this.indexrow = new kelondroRow(new int[]{4, payloadSize});
    }
    
    public byte[] get(Object key) {
        kelondroRow.Entry indexentry = indexrow.newEntry(super.get(objKey2byteKey(key)));
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
    public byte[] put(Object obj, byte[] value) {
        int index = -1;
        byte[] key = objKey2byteKey(obj);
        //System.out.println("ObjectMap PUT " + obj.hashCode() + ", size=" + size());
        synchronized (chunkcache) {
            index = find(key, 4);
        }
        if (index < 0) {
            kelondroRow.Entry indexentry = indexrow.newEntry();
            indexentry.setCol(0, key);
            indexentry.setCol(1, value);
            add(indexentry.bytes());
            return null;
        } else {
            kelondroRow.Entry indexentry = indexrow.newEntry(get(index));
            byte[] old = indexentry.getColBytes(1);
            indexentry.setCol(1, value);
            set(index, indexentry.bytes());
            return old;
        }
    }

    public byte[] remove(Object key) {
        kelondroRow.Entry indexentry = indexrow.newEntry(super.remove(objKey2byteKey(key)));
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }

    private byte[] objKey2byteKey(Object obj) {
        int i = obj.hashCode();
        byte[] b = kelondroNaturalOrder.encodeLong((long) i, 4);
        return b;
    }
    
}
