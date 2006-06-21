// kelondroBytesIntMap.java
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

public class kelondroBytesIntMap extends kelondroRowBufferedSet {
    
    public kelondroBytesIntMap(int keySize, int initSize) {
        super(new kelondroRow(new int[]{keySize, 4}), initSize);
        
        // initialize ordering
        super.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
    }

    public int geti(byte[] key) {
        kelondroRow.Entry indexentry = super.get(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLongB256(1);
    }
    
    public int puti(byte[] key, int i) {
        kelondroRow.Entry newentry = rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setColLongB256(1, i);
        kelondroRow.Entry oldentry = super.put(newentry);
        if (oldentry == null) return -1;
        return (int) oldentry.getColLongB256(1);
    }

    public void addi(byte[] key, int i) {
        kelondroRow.Entry indexentry = rowdef.newEntry();
        indexentry.setCol(0, key);
        indexentry.setColLongB256(1, i);
        add(indexentry);
    }
    
    public int removei(byte[] key) {
        if (size() == 0) {
            if (System.currentTimeMillis() - this.lastTimeWrote > 10000) this.trim();
            return -1;
        }
        kelondroRow.Entry indexentry = remove(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLongB256(1);
    }

}
