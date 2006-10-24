// kelondroCachedIndex
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 23.10.2006 on http://www.anomic.de
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

import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.server.logging.serverLog;

public class kelondroCachedIndex implements kelondroIndex {

    public final static int cacheObjectMissSize = 120;
    public final static int defaultObjectCachePercent = 10;
    
    private kelondroObjectCache objectCache;
    private kelondroIndex theIndex;

    public kelondroCachedIndex(kelondroIndex superIndex, long objectbuffersize) throws IOException {
        this.theIndex = superIndex;
        long objecthitcachesize = objectbuffersize * 4 / 5 / cacheObjectChunkSize();
        long objectmisscachesize = objectbuffersize / 5 / cacheObjectMissSize;
        this.objectCache = new kelondroObjectCache("generic", (int) objecthitcachesize, (int) objectmisscachesize, objecthitcachesize * 3000 , 4*1024*1024);
    }
    
    public final int cacheObjectChunkSize() {
        try {
            return this.theIndex.row().objectsize() + /* overhead */ 16 * this.theIndex.row().columns();
        } catch (IOException e) {
            return 0;
        }
    }
    
    public long[] cacheObjectStatus() {
        if (this.objectCache == null) return null;
        return this.objectCache.status();
    }
    
    public final int cacheNodeChunkSize() {
        // returns the size that the node cache uses for a single entry
        return theIndex.cacheNodeChunkSize();
    }
    
    public final int[] cacheNodeStatus() {
        // a collection of different node cache status values
        return theIndex.cacheNodeStatus();
    }
    
    public void addUnique(Entry row) throws IOException {
        // the use case for add implies that usually the objects are not needed in the cache
        // therefore omit an object cache write here
        this.theIndex.addUnique(row);
    }

    public void addUnique(Entry row, Date entryDate) throws IOException {
        this.theIndex.addUnique(row, entryDate);
    }

    public void close() throws IOException {
        this.objectCache = null;
        this.theIndex.close();
        
    }

    public Entry get(byte[] key) throws IOException {
        // get result from cache
        kelondroRow.Entry result = (objectCache == null) ? null : (kelondroRow.Entry) objectCache.get(key);
        if (result != null) return result;
        // check if we have an entry in the miss cache
        if ((objectCache != null) && (objectCache.has(key) == -1)) return null;
        // finally: get it from the index
        result = this.theIndex.get(key);
        if (result == null) objectCache.hasnot(key); else  objectCache.put(key, result);
        return result;
    }

    public kelondroOrder order() {
        return this.theIndex.order();
    }

    public int primarykey() {
        return this.theIndex.primarykey();
    }

    public kelondroProfile profile() {
        return this.theIndex.profile();
    }

    public Entry put(Entry row) throws IOException {
        assert (row != null);
        assert (row.columns() == row().columns());
        assert (!(serverLog.allZero(row.getColBytes(theIndex.primarykey()))));
        objectCache.put(row.getColBytes(theIndex.primarykey()), row);
        return this.theIndex.put(row);
    }

    public Entry put(Entry row, Date entryDate) throws IOException {
        assert (row.columns() == row().columns());
        objectCache.put(row.getColBytes(theIndex.primarykey()), row);
        return this.theIndex.put(row, entryDate);
    }

    public Entry remove(byte[] key) throws IOException {
        if (objectCache.has(key) == -1) return null;
        objectCache.remove(key);
        return this.theIndex.remove(key);
    }

    public Entry removeOne() throws IOException {
        Entry entry = this.theIndex.removeOne();
        if (entry == null) return null;
        this.objectCache.remove(entry.getColBytes(this.theIndex.primarykey()));
        return entry;
    }

    public kelondroRow row() throws IOException {
        return this.theIndex.row();
    }

    public Iterator rows(boolean up, boolean rotating, byte[] firstKey) throws IOException {
        return this.theIndex.rows(up, rotating, firstKey);
    }

    public int size() throws IOException {
        return this.theIndex.size();
    }

}
