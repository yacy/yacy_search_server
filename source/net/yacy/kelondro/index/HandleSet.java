// HandleSet.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.03.2009 on http://www.anomic.de
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.util.SetTools;


public final class HandleSet implements Iterable<byte[]>, Cloneable {
    
    private final Row rowdef;
    private RowSet index;
    
    public HandleSet(final int keylength, final ByteOrder objectOrder, final int expectedspace) {
        this.rowdef = new Row(new Column[]{new Column("key", Column.celltype_binary, Column.encoder_bytes, keylength, "key")}, objectOrder);
        try {
            this.index = new RowSet(rowdef, expectedspace);
        } catch (RowSpaceExceededException e) {
            try {
                this.index = new RowSet(rowdef, 0);
            } catch (RowSpaceExceededException ee) {
                Log.logException(ee);
                this.index = null;
            }
        }
    }

    private HandleSet(Row rowdef, RowSet index) {
        this.rowdef = rowdef;
        this.index = index;
    }

    public HandleSet(Row rowdef, byte[] b) throws RowSpaceExceededException {
        this.rowdef = rowdef;
        this.index = RowSet.importRowSet(b, this.rowdef);
    }

    @Override
    public HandleSet clone() {
        return new HandleSet(this.rowdef, this.index.clone());
    }
    
    public byte[] export() {
        return index.exportCollection();
    }
    
    /**
     * initialize a HandleSet with the content of a dump
     * @param keylength
     * @param objectOrder
     * @param file
     * @throws IOException 
     * @throws RowSpaceExceededException 
     */
    public HandleSet(final int keylength, final ByteOrder objectOrder, final File file) throws IOException, RowSpaceExceededException {
        this(keylength, objectOrder, (int) (file.length() / (keylength + 8)));
        // read the index dump and fill the index
        final InputStream is = new BufferedInputStream(new FileInputStream(file), 1024 * 1024);
        final byte[] a = new byte[keylength];
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
    public final int dump(final File file) throws IOException {
        // we must use an iterator from the combined index, because we need the entries sorted
        // otherwise we could just write the byte[] from the in kelondroRowSet which would make
        // everything much faster, but this is not an option here.
        final Iterator<Row.Entry> i = this.index.rows(true, null);
        OutputStream os;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file), 1024 * 1024);
        } catch (OutOfMemoryError e) {
            os = new FileOutputStream(file);
        }
        int c = 0;
        while (i.hasNext()) {
            os.write(i.next().bytes());
            c++;
        }
        os.flush();
        os.close();
        return c;
    }
    
    public final synchronized byte[] smallestKey() {
        return this.index.smallestKey();
    }
    
    public final synchronized byte[] largestKey() {
        return this.index.largestKey();
    }
    
    public ByteOrder comparator() {
        return this.rowdef.objectOrder;
    }
    
    public final Row row() {
        return index.row();
    }
    
    public final void clear() {
        this.index.clear();
    }
    
    public final synchronized boolean has(final byte[] key) {
        assert (key != null);
        return index.has(key);
    }
    
    public final void putAll(final HandleSet aset) throws RowSpaceExceededException {
        for (byte[] b: aset) put(b);
    }

    /**
     * Adds the key to the set
     * @param key
     * @return true if this set did _not_ already contain the given key. 
     * @throws IOException
     * @throws RowSpaceExceededException
     */
    public final boolean put(final byte[] key) throws RowSpaceExceededException {
        assert (key != null);
        final Row.Entry newentry = index.row().newEntry(key);
        return index.put(newentry);
    }
    
    public final void putUnique(final byte[] key) throws RowSpaceExceededException {
        assert (key != null);
        final Row.Entry newentry = index.row().newEntry(key);
        index.addUnique(newentry);
    }
    
    public final boolean remove(final byte[] key) {
        assert (key != null);
        Row.Entry indexentry;
        indexentry = index.remove(key);
        return indexentry != null;
    }

    public final synchronized byte[] removeOne() {
        Row.Entry indexentry;
        indexentry = index.removeOne();
        if (indexentry == null) return null;
        return indexentry.getPrimaryKeyBytes();
    }
    
    /**
     * get one entry; objects are taken from the end of the list
     * a getOne(0) would return the same object as removeOne() would remove
     * @param idx
     * @return entry from the end of the list
     */
    public final synchronized byte[] getOne(int idx) {
        if (idx >= this.size()) return null;
        Row.Entry indexentry;
        indexentry = index.get(this.size() - 1 - idx, true);
        if (indexentry == null) return null;
        return indexentry.getPrimaryKeyBytes();
    }
    
    public final synchronized boolean isEmpty() {
        return index.isEmpty();
    }
    
    public final synchronized int size() {
        return index.size();
    }
    
    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        return index.keys(up, firstKey);
    }
    
    public final Iterator<byte[]> iterator() {
        return keys(true, null);
    }
    
    public final synchronized void close() {
        index.close();
        index = null;
    }
    
    @Override
    public final String toString() {
        return this.index.toString();
    }
    
    // set tools
    
    public HandleSet joinConstructive(final HandleSet other) throws RowSpaceExceededException {
        return joinConstructive(this, other);
    }

    // now the same for set-set
    public static HandleSet joinConstructive(final HandleSet set1, final HandleSet set2) throws RowSpaceExceededException {
        // comparators must be equal
        if ((set1 == null) || (set2 == null)) return null;
        assert set1.comparator() == set2.comparator();
        if (set1.comparator() != set2.comparator()) return null;
        if (set1.isEmpty() || set2.isEmpty()) return new HandleSet(set1.rowdef.primaryKeyLength, set1.comparator(), 0);

        // decide which method to use
        final int high = ((set1.size() > set2.size()) ? set1.size() : set2.size());
        final int low  = ((set1.size() > set2.size()) ? set2.size() : set1.size());
        final int stepsEnum = 10 * (high + low - 1);
        final int stepsTest = 12 * SetTools.log2a(high) * low;

        // start most efficient method
        if (stepsEnum > stepsTest) {
            if (set1.size() < set2.size()) return joinConstructiveByTest(set1, set2);
            return joinConstructiveByTest(set2, set1);
        }
        return joinConstructiveByEnumeration(set1, set2);
    }

    private static HandleSet joinConstructiveByTest(final HandleSet small, final HandleSet large) throws RowSpaceExceededException {
        final Iterator<byte[]> mi = small.iterator();
        final HandleSet result = new HandleSet(small.rowdef.primaryKeyLength, small.comparator(), 0);
        byte[] o;
        while (mi.hasNext()) {
            o = mi.next();
            if (large.has(o)) result.put(o);
        }
        return result;
    }

    private static HandleSet joinConstructiveByEnumeration(final HandleSet set1, final HandleSet set2) throws RowSpaceExceededException {
        // implement pairwise enumeration
        final ByteOrder comp = set1.comparator();
        final Iterator<byte[]> mi = set1.iterator();
        final Iterator<byte[]> si = set2.iterator();
        final HandleSet result = new HandleSet(set1.rowdef.primaryKeyLength, comp, 0);
        int c;
        if (mi.hasNext() && si.hasNext()) {
            byte[] mobj = mi.next();
            byte[] sobj = si.next();
            while (true) {
                c = comp.compare(mobj, sobj);
                if (c < 0) {
                    if (mi.hasNext()) mobj = mi.next(); else break;
                } else if (c > 0) {
                    if (si.hasNext()) sobj = si.next(); else break;
                } else {
                    result.put(mobj);
                    if (mi.hasNext()) mobj = mi.next(); else break;
                    if (si.hasNext()) sobj = si.next(); else break;
                }
            }
        }
        return result;
    }

    public void excludeDestructive(final HandleSet other) {
        excludeDestructive(this, other);
    }
    
    private static void excludeDestructive(final HandleSet set1, final HandleSet set2) {
        if (set1 == null) return;
        if (set2 == null) return;
        assert set1.comparator() == set2.comparator();
        if (set1.isEmpty() || set2.isEmpty()) return;
        
        if (set1.size() < set2.size())
            excludeDestructiveByTestSmallInLarge(set1, set2);
        else
            excludeDestructiveByTestLargeInSmall(set1, set2);
    }
    
    private static void excludeDestructiveByTestSmallInLarge(final HandleSet small, final HandleSet large) {
        final Iterator<byte[]> mi = small.iterator();
        while (mi.hasNext()) if (large.has(mi.next())) mi.remove();
    }
    
    private static void excludeDestructiveByTestLargeInSmall(final HandleSet large, final HandleSet small) {
        final Iterator<byte[]> si = small.iterator();
        while (si.hasNext()) large.remove(si.next());
    }
}
