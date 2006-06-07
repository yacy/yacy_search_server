// kelondroCollectionIntMap.java
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

public class kelondroCollectionIntMap extends kelondroCollection {

    private kelondroRow indexrow;
    
    public kelondroCollectionIntMap(int keySize, int initSize) {
        super(keySize + 4, initSize);
        
        // initialize row
        this.indexrow = new kelondroRow(new int[]{keySize, 4});
        
        // initialize ordering
        this.setOrdering(kelondroNaturalOrder.naturalOrder);
    }

    public void addi(byte[] key, int i) {
        kelondroRow.Entry indexentry = indexrow.newEntry();
        indexentry.setCol(0, key);
        indexentry.setColLongB256(1, i);
        add(indexentry.bytes());
    }
    
    public int puti(byte[] key, int i) {
        int index = -1;
        synchronized (chunkcache) {
            index = find(key, key.length);
        }
        if (index < 0) {
            kelondroRow.Entry indexentry = indexrow.newEntry();
            indexentry.setCol(0, key);
            indexentry.setColLongB256(1, i);
            add(indexentry.bytes());
            return -1;
        } else {
            kelondroRow.Entry indexentry = indexrow.newEntry(get(index));
            int oldi = (int) indexentry.getColLongB256(1);
            indexentry.setColLongB256(1, i);
            set(index, indexentry.bytes());
            return oldi;
        }
    }

    public int geti(byte[] key) {
        int index = -1;
        synchronized (chunkcache) {
            index = find(key, key.length);
        }
        if (index < 0) {
            return -1;
        } else {
            kelondroRow.Entry indexentry = indexrow.newEntry(get(index));
            return (int) indexentry.getColLongB256(1);
        }
    }
    
    public int removei(byte[] key) {
        byte[] b = remove(key);
        if (b == null) return -1;
        kelondroRow.Entry indexentry = indexrow.newEntry(b);
        return (int) indexentry.getColLongB256(1);
    }
}
