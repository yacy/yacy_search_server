// ObjectIndexDaemon.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.03.2009 on http://yacy.net
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

package de.anomic.kelondro.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.anomic.kelondro.index.Row.Entry;
import de.anomic.kelondro.index.Row.Queue;
import de.anomic.kelondro.order.CloneableIterator;

public class ObjectIndexDaemon {

    private Row.Entry poison;    
    private PutScheduler putScheduler;
    private RowSet index;
    private Queue queue;
    private int queueCount;
    
    public ObjectIndexDaemon(final Row rowdef, final int objectCount, final int queueCount) {
        this.index = new RowSet(rowdef, objectCount);
        assert rowdef.objectOrder != null;
        this.poison = rowdef.newEntry();
        this.queueCount = queueCount;
        this.queue = index.rowdef.newQueue(queueCount);
        this.putScheduler = new PutScheduler();
        this.putScheduler.start();
    }
    
    public class PutScheduler extends Thread {
        public PutScheduler() {}        
        public void run() {
            Row.Entry next;
            try {
                while ((next = queue.take()) != poison) index.put(next);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void close() {
        try {
            this.queue.put(poison);
            this.putScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void addUnique(Entry row) {
        index.addUnique(row);
    }

    public void addUnique(List<Entry> rows) {
        index.addUnique(rows);
    }

    public void clear() throws IOException {
        try {
            this.queue.put(poison);
            this.putScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.index.clear();
        this.queue = index.rowdef.newQueue(queueCount);
        this.putScheduler = new PutScheduler();
        this.putScheduler.start();
    }

    public void deleteOnExit() {
        this.index.deleteOnExit();
    }

    public String filename() {
        return this.index.filename();
    }

    public Entry get(byte[] key) {
        Entry entry = this.queue.get(key);
        if (entry != null) return entry;
        return this.index.get(key);
    }

    public boolean has(byte[] key) {
        Entry entry = this.queue.get(key);
        if (entry != null) return true;
        return this.index.has(key);
    }

    public Entry replace(Entry row) {
        Entry entry = get(row.getPrimaryKeyBytes());
        try {
            this.queue.put(row);
        } catch (InterruptedException e) {
            this.index.put(row);
        }
        return entry;
    }

    public void put(Entry row) {
        try {
            this.queue.put(row);
        } catch (InterruptedException e) {
            this.index.put(row);
        }
    }

    public void put(List<Entry> rows) {
        for (Entry entry: rows) try {
            this.queue.put(entry);
        } catch (InterruptedException e) {
            this.index.put(entry);
        }
    }

    public Entry remove(byte[] key) {
        Entry entry = this.queue.delete(key);
        if (entry == null) return this.index.remove(key);
        this.index.remove(key);
        return entry;
    }

    public synchronized ArrayList<RowCollection> removeDoubles() {
        try {
            this.queue.put(poison);
            this.putScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ArrayList<RowCollection> d = index.removeDoubles();
        this.queue = index.rowdef.newQueue(queueCount);
        this.putScheduler = new PutScheduler();
        this.putScheduler.start();
        return d;
    }

    public synchronized Entry removeOne() {
        try {
            this.queue.put(poison);
            this.putScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Entry d = index.removeOne();
        this.queue = index.rowdef.newQueue(queueCount);
        this.putScheduler = new PutScheduler();
        this.putScheduler.start();
        return d;
    }

    public Row row() {
        return this.index.row();
    }

    public synchronized CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) {
        try {
            this.queue.put(poison);
            this.putScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        CloneableIterator<byte[]> keys = index.keys(up, firstKey);
        this.queue = index.rowdef.newQueue(queueCount);
        this.putScheduler = new PutScheduler();
        this.putScheduler.start();
        return keys;
    }

    public synchronized CloneableIterator<Entry> rows(boolean up, byte[] firstKey) {
        try {
            this.queue.put(poison);
            this.putScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        CloneableIterator<Entry> rows = index.rows(up, firstKey);
        this.queue = index.rowdef.newQueue(queueCount);
        this.putScheduler = new PutScheduler();
        this.putScheduler.start();
        return rows;
    }

    public CloneableIterator<Entry> rows() {
        return rows(true, null);
    }

    public synchronized int size() {
        try {
            this.queue.put(poison);
            this.putScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int size = index.size();
        this.queue = index.rowdef.newQueue(queueCount);
        this.putScheduler = new PutScheduler();
        this.putScheduler.start();
        return size;
    }

}
