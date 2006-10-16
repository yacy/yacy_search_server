// kelondroRowBufferedSet.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 21.06.2006 on http://www.anomic.de
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

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import de.anomic.server.serverMemory;

public class kelondroRowBufferedSet implements kelondroIndex {

    private static final long memBlockLimit = 2000000;  // do not fill cache further if the amount of available memory is less that this
    private static final int bufferFlushLimit = 10000;
    private static final int bufferFlushMinimum = 1000;
    private kelondroProfile profile;
    private TreeMap buffer;
    private kelondroRowSet store;

    public kelondroRowBufferedSet(kelondroRow rowdef, kelondroOrder objectOrder, int orderColumn, int objectCount) {
        store = new kelondroRowSet(rowdef, objectCount);
        assert (objectOrder != null);
        store.setOrdering(objectOrder, orderColumn);
        buffer = new TreeMap(objectOrder);
        profile = new kelondroProfile();
    }
    
    private final void flush() {
        // call only in synchronized environment
        Iterator i = buffer.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            store.add((kelondroRow.Entry) entry.getValue());
        }
        buffer.clear();
    }
    
    public synchronized final void trim() {
        flush();
        store.trim();
    }

    public synchronized void removeOne() {
        if (buffer.size() == 0) {
            store.removeOne();
        } else try {
            // buffer.remove(buffer.keySet().iterator().next());
            buffer.remove(buffer.lastKey());
        } catch (NoSuchElementException e) {}
    }

    public synchronized void clear() {
        store.clear();
        buffer.clear();
    }

    public synchronized int size() {
        return buffer.size() + store.size();
    }

    public synchronized Iterator rows() {
        flush();
        return store.rows();
    }

    public synchronized void uniq() {
        flush();
        store.uniq();
    }

    public synchronized String toString() {
        flush();
        return store.toString();
    }

    public synchronized kelondroRow.Entry get(byte[] key) {
        long handle = profile.startRead();
        kelondroRow.Entry entry = null;
        entry = (kelondroRow.Entry) buffer.get(key);
        if (entry == null) entry = store.get(key);
        profile.stopRead(handle);
        return entry;
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) {
        return put(row);
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry newentry) {
        long handle = profile.startWrite();
        byte[] key = newentry.getColBytes(store.sortColumn);
        kelondroRow.Entry oldentry = null;
        oldentry = (kelondroRow.Entry) buffer.get(key);
        if (oldentry == null) {
            // try the collection
            oldentry = store.get(key);
            if (oldentry == null) {
                // this was not anywhere
                buffer.put(key, newentry);
                if (((buffer.size() > bufferFlushMinimum) && (serverMemory.available() > memBlockLimit))
                  || (buffer.size() > bufferFlushLimit))
                    flush();
            } else {
                // replace old entry
                store.put(newentry);
            }
        } else {
            // the entry is already in buffer
            // simply replace old entry
            buffer.put(key, newentry);
        }
        profile.stopWrite(handle);
        return oldentry;
    }

    public synchronized kelondroRow.Entry remove(byte[] key) {
        long handle = profile.startDelete();
        kelondroRow.Entry oldentry = null;
        oldentry = (kelondroRow.Entry) buffer.remove(key);
        if (oldentry == null) {
            // try the collection
            return store.remove(key);
        }
        profile.stopDelete(handle);
        return oldentry;
    }

    public synchronized void removeMarkedAll(kelondroRowCollection c) {
        long handle = profile.startDelete();
        flush();
        store.removeMarkedAll(c);
        profile.stopDelete(handle);
    }

    public kelondroProfile profile() {
        return store.profile();
    }

    public synchronized void close() {
        flush();
        store.close();
    }

    public kelondroOrder order() {
        return store.order();
    }

    public kelondroRow row() {
        return store.row();
    }

    public synchronized Iterator rows(boolean up, boolean rotating, byte[] firstKey) {
        flush();
        return store.rows(up, rotating, firstKey);
    }
}
