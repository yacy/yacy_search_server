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

public class kelondroIntBytesMap extends kelondroRowBufferedSet {

    public kelondroIntBytesMap(int payloadSize, int initSize) {
        super(new kelondroRow(new int[]{4, payloadSize}), initSize);
        
        // initialize ordering
        super.setOrdering(kelondroNaturalOrder.naturalOrder, 0);
    }
    
    public byte[] getb(int ii) {
        kelondroRow.Entry indexentry = super.get(kelondroNaturalOrder.encodeLong((long) ii, 4));
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
    public byte[] putb(int ii, byte[] value) {
        kelondroRow.Entry newentry = rowdef.newEntry();
        newentry.setCol(0, kelondroNaturalOrder.encodeLong((long) ii, 4));
        newentry.setCol(1, value);
        kelondroRow.Entry oldentry = super.put(newentry);
        if (oldentry == null) return null;
        return oldentry.getColBytes(1);
    }
    
    public void addb(int ii, byte[] value) {
        kelondroRow.Entry newentry = rowdef.newEntry();
        newentry.setCol(0, kelondroNaturalOrder.encodeLong((long) ii, 4));
        newentry.setCol(1, value);
        add(newentry);
    }
    
    public byte[] removeb(int ii) {
        if (size() == 0) {
            if (System.currentTimeMillis() - this.lastTimeWrote > 10000) this.trim();
            return null;
        }
        kelondroRow.Entry indexentry = super.removeMarked(kelondroNaturalOrder.encodeLong((long) ii, 4));
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
}
