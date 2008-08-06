// kelondroBytesLongMap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 08.04.2008 on http://yacy.net
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
import java.util.ArrayList;
import java.util.Iterator;

public class kelondroBytesLongMap {
    
    private final kelondroRow rowdef;
    private kelondroIndex index;
    
    public kelondroBytesLongMap(final kelondroIndex ki) {
        assert (ki.row().columns() == 2); // must be a key/index relation
        assert (ki.row().width(1) == 8);  // the value must be a b256-encoded int, 4 bytes long
        this.index = ki;
        this.rowdef = ki.row();
    }
    
    public kelondroBytesLongMap(final int keylength, final kelondroByteOrder objectOrder, final int space) {
        this.rowdef = new kelondroRow(new kelondroColumn[]{new kelondroColumn("key", kelondroColumn.celltype_binary, kelondroColumn.encoder_bytes, keylength, "key"), new kelondroColumn("long c-8 {b256}")}, objectOrder, 0);
        this.index = new kelondroRAMIndex(rowdef, space);
    }
    
    public kelondroRow row() {
        return index.row();
    }
    
    public void clear() throws IOException {
        index.clear();
    }
    
    public synchronized long getl(final byte[] key) throws IOException {
        assert (key != null);
        final kelondroRow.Entry indexentry = index.get(key);
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }
    
    public synchronized long putl(final byte[] key, final long l) throws IOException {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final kelondroRow.Entry newentry = index.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        final kelondroRow.Entry oldentry = index.put(newentry);
        if (oldentry == null) return -1;
        return oldentry.getColLong(1);
    }
    
    public synchronized boolean addl(final byte[] key, final long l) throws IOException {
        assert l >= 0 : "l = " + l;
        assert (key != null);
        final kelondroRow.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, l);
        return index.addUnique(newentry);
    }
    
    public synchronized ArrayList<Long[]> removeDoubles() throws IOException {
        final ArrayList<kelondroRowCollection> indexreport = index.removeDoubles();
        final ArrayList<Long[]> report = new ArrayList<Long[]>();
        Long[] is;
        Iterator<kelondroRow.Entry> ei;
        int c;
        for (final kelondroRowCollection rowset: indexreport) {
            is = new Long[rowset.size()];
            ei = rowset.rows();
            c = 0;
            while (ei.hasNext()) {
                is[c++] = Long.valueOf(ei.next().getColLong(1));
            }
            report.add(is);
        }
        return report;
    }
    
    public synchronized long removel(final byte[] key) throws IOException {
        assert (key != null);
        final kelondroRow.Entry indexentry = index.remove(key);
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }

    public synchronized long removeonel() throws IOException {
        final kelondroRow.Entry indexentry = index.removeOne();
        if (indexentry == null) return -1;
        return indexentry.getColLong(1);
    }
    
    public synchronized int size() {
        return index.size();
    }
    
    public synchronized kelondroCloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return index.keys(up, firstKey);
    }

    public synchronized kelondroCloneableIterator<kelondroRow.Entry> rows(final boolean up, final byte[] firstKey) throws IOException {
        return index.rows(up, firstKey);
    }
    
    public kelondroProfile profile() {
        return index.profile();
    }
    
    public synchronized void close() {
        index.close();
        index = null;
    }
    
}
