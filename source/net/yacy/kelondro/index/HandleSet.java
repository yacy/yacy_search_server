// HandleSet.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.03.2009 on http://www.anomic.de
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

package net.yacy.kelondro.index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;


public class HandleSet implements Iterable<byte[]> {
    
    private final Row rowdef;
    private ObjectIndex index;
    
    public HandleSet(final int keylength, final ByteOrder objectOrder, final int initialspace, final int expectedspace) {
        this.rowdef = new Row(new Column[]{new Column("key", Column.celltype_binary, Column.encoder_bytes, keylength, "key")}, objectOrder);
        this.index = new ObjectIndexCache(rowdef, initialspace, expectedspace);
    }

    /**
     * initialize a HandleSet with the content of a dump
     * @param keylength
     * @param objectOrder
     * @param file
     * @throws IOException 
     */
    public HandleSet(final int keylength, final ByteOrder objectOrder, final File file, final int expectedspace) throws IOException {
        this(keylength, objectOrder, (int) (file.length() / (keylength + 8)), expectedspace);
        // read the index dump and fill the index
        InputStream is = new BufferedInputStream(new FileInputStream(file), 1024 * 1024);
        byte[] a = new byte[keylength];
        int c;
        while (true) {
            c = is.read(a);
            if (c <= 0) break;
            this.index.addUnique(this.rowdef.newEntry(a));
        }
        is.close();
        assert this.index.size() == file.length() / keylength;
    }

    /**
     * write a dump of the set to a file. All entries are written in order
     * which makes it possible to read them again in a fast way
     * @param file
     * @return the number of written entries
     * @throws IOException
     */
    public int dump(File file) throws IOException {
        // we must use an iterator from the combined index, because we need the entries sorted
        // otherwise we could just write the byte[] from the in kelondroRowSet which would make
        // everything much faster, but this is not an option here.
        Iterator<Row.Entry> i = this.index.rows(true, null);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(file), 1024 * 1024);
        int c = 0;
        while (i.hasNext()) {
            os.write(i.next().bytes());
            c++;
        }
        os.flush();
        os.close();
        return c;
    }
    
    public Row row() {
        return index.row();
    }
    
    public void clear() throws IOException {
        this.index.clear();
    }
    
    public synchronized boolean has(final byte[] key) {
        assert (key != null);
        return index.has(key);
    }
    
    public synchronized int put(final byte[] key) throws IOException {
        assert (key != null);
        final Row.Entry newentry = index.row().newEntry();
        newentry.setCol(0, key);
        final Row.Entry oldentry = index.replace(newentry);
        if (oldentry == null) return -1;
        return (int) oldentry.getColLong(1);
    }
    
    public synchronized void putUnique(final byte[] key) throws IOException {
        assert (key != null);
        final Row.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        index.addUnique(newentry);
    }
    
    public synchronized int remove(final byte[] key) throws IOException {
        assert (key != null);
        final Row.Entry indexentry = index.remove(key);
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }

    public synchronized int removeone() throws IOException {
        final Row.Entry indexentry = index.removeOne();
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int size() {
        return index.size();
    }
    
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        try {
            return index.keys(up, firstKey);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public Iterator<byte[]> iterator() {
        return keys(true, null);
    }
    
    public synchronized void close() {
        index.close();
        index = null;
    }
}
