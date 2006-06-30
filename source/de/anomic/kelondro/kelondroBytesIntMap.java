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

import java.io.IOException;

public class kelondroBytesIntMap {
    
    private kelondroIndex ki;
    
    public kelondroBytesIntMap(kelondroIndex ki) throws IOException {
        assert (ki.row().columns() == 2); // must be a key/index relation
        assert (ki.row().width(1) == 4);  // the value must be a b256-encoded int, 4 bytes long
        this.ki = ki;
    }
    
    public int geti(byte[] key) throws IOException {
        kelondroRow.Entry indexentry = ki.get(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLongB256(1);
    }
    
    public int puti(byte[] key, int i) throws IOException {
        kelondroRow.Entry newentry = ki.row().newEntry();
        newentry.setCol(0, key);
        newentry.setColLongB256(1, i);
        kelondroRow.Entry oldentry = ki.put(newentry);
        if (oldentry == null) return -1;
        return (int) oldentry.getColLongB256(1);
    }
    
    public int removei(byte[] key) throws IOException {
        if (ki.size() == 0) return -1;
        kelondroRow.Entry indexentry = ki.remove(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLongB256(1);
    }

    public int size() throws IOException {
        return ki.size();
    }
    
}
