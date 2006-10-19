// kelondroBufferedIndex.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 16.10.2006 on http://www.anomic.de
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
import java.util.Map;
import java.util.TreeMap;

import de.anomic.server.serverMemory;

public class kelondroBufferedIndex implements kelondroIndex {
    
    // this implements a write buffer on index objects
    
    private static final long memBlockLimit = 2000000;  // do not fill cache further if the amount of available memory is less that this
    private static final int bufferFlushLimit = 10000;
    private static final int bufferFlushMinimum = 1000;
    private TreeMap buffer;
    private kelondroIndex index;

    public kelondroBufferedIndex(kelondroIndex theIndex) {
        index = theIndex;
        buffer = (theIndex.order() == null) ? new TreeMap() : new TreeMap(theIndex.order());
    }
    
    public synchronized void flush() throws IOException {
        if (buffer.size() == 0) return;
        Iterator i = buffer.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            index.put((kelondroRow.Entry) entry.getValue());
        }
        buffer.clear();
    }

    public synchronized void flushOnce() throws IOException {
        if (buffer.size() == 0) return;
        Iterator i = buffer.entrySet().iterator();
        Map.Entry entry;
        if (i.hasNext()) {
            entry = (Map.Entry) i.next();
            index.put((kelondroRow.Entry) entry.getValue());
        }
    }

    public void flushSome() throws IOException {
        if (buffer.size() == 0) return;
        int flush = Math.max(1, buffer.size() / 10);
        while (flush-- > 0) flushOnce();
    }
    
    public synchronized int size() throws IOException {
        return buffer.size() + index.size();
    }

    public int writeBufferSize() {
        return buffer.size();
    }
    
    public synchronized String toString() {
        try {flush();} catch (IOException e) {}
        return index.toString();
    }

    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
        long handle = (index instanceof kelondroFlexSplitTable) ? -1 : index.profile().startRead();
        kelondroRow.Entry entry = null;
        entry = (kelondroRow.Entry) buffer.get(key);
        if (entry == null) entry = index.get(key);
        if (handle >= 0) index.profile().stopRead(handle);
        return entry;
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        return put(row, null);
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        long handle = (index instanceof kelondroFlexSplitTable) ? -1 : index.profile().startWrite();
        byte[] key = row.getColBytes(index.primarykey());
        kelondroRow.Entry oldentry = null;
        oldentry = (kelondroRow.Entry) buffer.get(key);
        if (oldentry == null) {
            // try the collection
            oldentry = index.get(key);
            if (oldentry == null) {
                // this was not anywhere
                if (entryDate == null) {
                    buffer.put(key, row);
                    if (((buffer.size() > bufferFlushMinimum) && (serverMemory.available() > memBlockLimit))
                      || (buffer.size() > bufferFlushLimit))
                        flush();
                } else {
                    index.put(row, entryDate);
                }
            } else {
                // replace old entry
                if (entryDate == null) {
                    index.put(row);
                } else {
                    index.put(row, entryDate);
                }
            }
        } else {
            // the entry is already in buffer
            // simply replace old entry
            if (entryDate == null) {
                buffer.put(key, row);
            } else {
                buffer.remove(key);
                index.put(row, entryDate);
            }
        }
        if (handle >= 0) index.profile().stopWrite(handle);
        return oldentry;
    }

    public synchronized void addUnique(kelondroRow.Entry row) throws IOException {
        assert (index instanceof kelondroRowSet);
        ((kelondroRowSet) index).addUnique(row);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        addUnique(row);
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key) throws IOException {
        long handle = (index instanceof kelondroFlexSplitTable) ? -1 : index.profile().startDelete();
        kelondroRow.Entry oldentry = null;
        oldentry = (kelondroRow.Entry) buffer.remove(key);
        if (oldentry == null) {
            // try the collection
            return index.remove(key);
        }
        if (handle >= 0) index.profile().stopDelete(handle);
        return oldentry;
    }
    
    public synchronized kelondroRow.Entry removeOne() throws IOException {
        long handle = (index instanceof kelondroFlexSplitTable) ? -1 : index.profile().startDelete();
        if (buffer.size() > 0) {
            byte[] key = (byte[]) buffer.keySet().iterator().next();
            kelondroRow.Entry entry = (kelondroRow.Entry) buffer.remove(key);
            if (handle >= 0) index.profile().stopDelete(handle);
            return entry;
        } else {
            kelondroRow.Entry entry = index.removeOne();
            if (handle >= 0) index.profile().stopDelete(handle);
            return entry;
        }
    }

    public kelondroProfile profile() {
        return index.profile();
    }

    public synchronized void close() throws IOException {
        flush();
        buffer = null;
        index.close();
    }

    public kelondroOrder order() {
        return index.order();
    }

    public int primarykey() {
        return index.primarykey();
    }
    
    public kelondroRow row() throws IOException {
        return index.row();
    }

    public synchronized Iterator rows() throws IOException {
        return rows(true, false, null);
    }

    public synchronized Iterator rows(boolean up, boolean rotating, byte[] firstKey) throws IOException {
        flush();
        return index.rows(up, rotating, firstKey);
    }
    
    public static kelondroBufferedIndex getRAMIndex(kelondroRow rowdef, int initSize) {
        return new kelondroBufferedIndex(new kelondroRowSet(rowdef, kelondroNaturalOrder.naturalOrder, 0, initSize));
    }
}
