// kelondroBufferedEcoFS.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2008 on http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class kelondroBufferedEcoFS {

    private kelondroEcoFS efs;
    private int maxEntries;
    private TreeMap<Long, byte[]> buffer;
    
    /*
     * The kelondroBufferedEcoFS extends the IO reduction to EcoFS by providing a
     * write buffer to elements that are inside the filed entries of the file
     * That means, each time, an entry is written to the end of the file, it is not buffered
     */
    
    public kelondroBufferedEcoFS(kelondroEcoFS efs, int maxEntries) throws IOException {
        this.efs = efs;
        this.maxEntries = maxEntries;
        this.buffer = new TreeMap<Long, byte[]>();
    }

    private void flushBuffer() throws IOException {
        Iterator<Map.Entry<Long, byte[]>> i = buffer.entrySet().iterator();
        Map.Entry<Long, byte[]> entry;
        while (i.hasNext()) {
            entry = i.next();
            efs.put(entry.getKey().intValue(), entry.getValue(), 0);
        }
        buffer.clear();
    }
    
    public synchronized long size() throws IOException {
        return efs.size();
    }
    
    public File filename() {
        return efs.filename();
    }

    public synchronized void close() {
        try {
            flushBuffer();
        } catch (IOException e) {
            e.printStackTrace();
        }
        efs.close();
        efs = null;
    }

    public synchronized void finalize() {
        if (this.efs != null) this.close();
    }
    
    public synchronized void get(long index, byte[] b, int start) throws IOException {
        assert b.length - start >= efs.recordsize;
        if (index >= size()) throw new IndexOutOfBoundsException("kelondroBufferedEcoFS.get(" + index + ") outside bounds (" + this.size() + ")");
        byte[] bb = buffer.get(new Long(index));
        if (bb == null) {
            efs.get(index, b, start);
        } else {
            System.arraycopy(bb, 0, b, start, efs.recordsize);
        }
    }

    public synchronized void put(long index, byte[] b, int start) throws IOException {
        assert b.length - start >= efs.recordsize;
        if (index > size()) throw new IndexOutOfBoundsException("kelondroBufferedEcoFS.put(" + index + ") outside bounds (" + this.size() + ")");
        if (index == efs.size()) {
            efs.put(index, b, start);
        } else {
            byte[] bb = new byte[efs.recordsize];
            System.arraycopy(b, start, bb, 0, efs.recordsize);
            buffer.put(new Long(index), bb);
            if (buffer.size() > this.maxEntries) flushBuffer();
       }
    }
    
    public synchronized void add(byte[] b, int start) throws IOException {
        put(size(), b, start);
    }
/*
    public synchronized void clean(long index, byte[] b, int start) throws IOException {
        assert b.length - start >= efs.recordsize;
        if (index >= size()) throw new IndexOutOfBoundsException("kelondroBufferedEcoFS.clean(" + index + ") outside bounds (" + this.size() + ")");
        byte[] bb = buffer.get(new Long(index));
        if (bb == null) {
            efs.clean(index, b, start);
        } else {
            System.arraycopy(bb, 0, b, start, efs.recordsize);
            buffer.remove(new Long(index));
            efs.clean(index);
        }
    }

    public synchronized void clean(long index) throws IOException {
        if (index >= size()) throw new IndexOutOfBoundsException("kelondroBufferedEcoFS.clean(" + index + ") outside bounds (" + this.size() + ")");
        buffer.remove(new Long(index));
        efs.clean(index);
    }
*/
    public synchronized void cleanLast(byte[] b, int start) throws IOException {
        assert b.length - start >= efs.recordsize;
        Long i = new Long(size() - 1);
        byte[] bb = buffer.remove(i);
        if (bb == null) {
            efs.cleanLast(b, start);
        } else {
            System.arraycopy(bb, 0, b, start, efs.recordsize);
            efs.cleanLast();
        }
    }
    
    public synchronized void cleanLast() throws IOException {
        Long i = new Long(size() - 1);
        buffer.remove(i);
        efs.cleanLast();
    }
    
}
