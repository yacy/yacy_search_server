// kelondroRAMIndex.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 12.08.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
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
import java.util.Date;
import java.util.Iterator;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroRow.Entry;

public class kelondroRAMIndex implements kelondroIndex {

    private TreeMap index;
    private kelondroOrder order;
    private kelondroRow rowdef;
    private kelondroProfile profile;
    
    public kelondroRAMIndex(kelondroOrder defaultOrder, kelondroRow rowdef) {
        this.index = new TreeMap(defaultOrder);
        this.order = defaultOrder;
        this.rowdef = rowdef;
        this.profile = new kelondroProfile();
    }
    
    public kelondroOrder order() {
        return this.order;
    }

    public int primarykey() {
        return 0;
    }
    
    public synchronized int size() {
        return this.index.size();
    }

    public kelondroRow row() {
        return this.rowdef;
    }

    public synchronized Entry get(byte[] key) {
        return (kelondroRow.Entry) index.get(key);
    }

    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        return put(row);
    }
    
    public synchronized Entry put(Entry row) {
        return (kelondroRow.Entry) index.put(row.getColBytes(0), row);
    }

    public synchronized void addUnique(kelondroRow.Entry row) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        throw new UnsupportedOperationException();
    }

    public synchronized Entry remove(byte[] key) {
        return (kelondroRow.Entry) index.remove(key);
    }

    public synchronized Entry removeOne() {
        if (this.index.size() == 0) return null;
        return remove((byte[]) index.keySet().iterator().next());
    }
    
    public synchronized Iterator rows(boolean up, boolean rotating, byte[] firstKey) {
        return index.values().iterator();
    }

    public void close() {
        index = null;
    }
    
    public kelondroProfile profile() {
        return profile;
    }
    
}
