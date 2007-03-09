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
    
    public kelondroRow row() throws IOException {
        return ki.row();
    }
    
    public synchronized int geti(byte[] key) throws IOException {
        assert (key != null);
        //assert (!(serverLog.allZero(key)));
        kelondroRow.Entry indexentry = ki.get(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int puti(byte[] key, int i) throws IOException {
        assert (key != null);
        //assert (!(serverLog.allZero(key)));
        kelondroRow.Entry newentry = ki.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        kelondroRow.Entry oldentry = ki.put(newentry);
        if (oldentry == null) return -1;
        return (int) oldentry.getColLong(1);
    }
    
    public synchronized void addi(byte[] key, int i) throws IOException {
        assert (key != null);
        //assert (!(serverLog.allZero(key)));
        kelondroRow.Entry newentry = ki.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        ki.addUnique(newentry);
    }
    
    public synchronized int removei(byte[] key) throws IOException {
        assert (key != null);
        //assert (!(serverLog.allZero(key)));
        // returns the integer index of the key, if the key can be found and was removed
        // and -1 if the key was not found.
        if (ki.size() == 0) return -1;
        kelondroRow.Entry indexentry = ki.remove(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }

    public synchronized int removeonei() throws IOException {
        if (ki.size() == 0) return -1;
        kelondroRow.Entry indexentry = ki.removeOne();
        assert (indexentry != null);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int size() throws IOException {
        return ki.size();
    }
    
    public synchronized kelondroCloneableIterator rows(boolean up, byte[] firstKey) throws IOException {
        // returns the row-iterator of the underlying kelondroIndex
        // col[0] = key
        // col[1] = integer as {b265}
        return ki.rows(up, firstKey);
    }
    
    public kelondroProfile profile() {
        return ki.profile();
    }
    
    public synchronized void close() {
        ki.close();
    }
    
}
