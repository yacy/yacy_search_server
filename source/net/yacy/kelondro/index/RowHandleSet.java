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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.util.SetTools;


public final class RowHandleSet implements HandleSet, Iterable<byte[]>, Cloneable, Serializable {

    private static final long serialVersionUID=444204785291174968L;

    private final Row rowdef;
    private RowSet index;

    public RowHandleSet(final int keylength, final ByteOrder objectOrder, final int expectedspace) {
        this.rowdef = new Row(new Column[]{new Column("key", Column.celltype_binary, Column.encoder_bytes, keylength, "key")}, objectOrder);
        try {
            this.index = new RowSet(this.rowdef, expectedspace);
        } catch (final SpaceExceededException e) {
            try {
                this.index = new RowSet(this.rowdef, 0);
            } catch (final SpaceExceededException ee) {
                ConcurrentLog.logException(ee);
                this.index = null;
            }
        }
    }

    private RowHandleSet(Row rowdef, RowSet index) {
        this.rowdef = rowdef;
        this.index = index;
    }

    public RowHandleSet(Row rowdef, byte[] b) throws SpaceExceededException {
        this.rowdef = rowdef;
        this.index = RowSet.importRowSet(b, this.rowdef);
    }

    @Override
    public RowHandleSet clone() {
        optimize();
        return new RowHandleSet(this.rowdef, this.index.clone());
    }

    @Override
    public byte[] export() {
        return this.index.exportCollection();
    }

    @Override
    public void optimize() {
        this.index.sort();
        this.index.trim();
    }
    
    /**
     * initialize a HandleSet with the content of a dump
     * @param keylength
     * @param objectOrder
     * @param file
     * @throws IOException
     * @throws SpaceExceededException
     */
    public RowHandleSet(final int keylength, final ByteOrder objectOrder, final File file) throws IOException, SpaceExceededException {
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
    @Override
    public final int dump(final File file) throws IOException {
        // we must use an iterator from the combined index, because we need the entries sorted
        // otherwise we could just write the byte[] from the in kelondroRowSet which would make
        // everything much faster, but this is not an option here.
        final Iterator<Row.Entry> i = this.index.rows(true, null);
        OutputStream os;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file), 1024 * 1024);
        } catch (final OutOfMemoryError e) {
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

    @Override
    public final synchronized byte[] smallestKey() {
        return this.index.smallestKey();
    }

    @Override
    public final synchronized byte[] largestKey() {
        return this.index.largestKey();
    }

    @Override
    public ByteOrder comparator() {
        return this.rowdef.objectOrder;
    }

    public final Row row() {
        return this.index.row();
    }

    @Override
    public int keylen() {
        return this.index.rowdef.primaryKeyLength;
    }

    @Override
    public final void clear() {
        this.index.clear();
    }

    @Override
    public final synchronized boolean has(final byte[] key) {
        assert (key != null);
        return this.index.has(key);
    }

    @Override
    public final void putAll(final HandleSet aset) throws SpaceExceededException {
        for (byte[] b: aset) put(b);
    }

    /**
     * Adds the key to the set
     * @param key
     * @return true if this set did _not_ already contain the given key.
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public final boolean put(final byte[] key) throws SpaceExceededException {
        assert (key != null);
        final Row.Entry newentry = this.index.row().newEntry(key);
        return this.index.put(newentry);
    }

    @Override
    public final void putUnique(final byte[] key) throws SpaceExceededException {
        assert (key != null);
        final Row.Entry newentry = this.index.row().newEntry(key);
        this.index.addUnique(newentry);
    }

    @Override
    public final boolean remove(final byte[] key) {
        assert (key != null);
        Row.Entry indexentry;
        indexentry = this.index.remove(key);
        return indexentry != null;
    }

    @Override
    public final synchronized byte[] removeOne() {
        Row.Entry indexentry;
        indexentry = this.index.removeOne();
        if (indexentry == null) return null;
        return indexentry.getPrimaryKeyBytes();
    }

    /**
     * get one entry; objects are taken from the end of the list
     * a getOne(0) would return the same object as removeOne() would remove
     * @param idx
     * @return entry from the end of the list
     */
    @Override
    public final synchronized byte[] getOne(int idx) {
        if (idx >= this.size()) return null;
        Row.Entry indexentry;
        indexentry = this.index.get(this.size() - 1 - idx, true);
        if (indexentry == null) return null;
        return indexentry.getPrimaryKeyBytes();
    }

    @Override
    public final synchronized boolean isEmpty() {
        return this.index.isEmpty();
    }

    @Override
    public final synchronized int size() {
        return this.index.size();
    }

    @Override
    public final synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        return this.index.keys(up, firstKey);
    }

    @Override
    public final Iterator<byte[]> iterator() {
        return keys(true, null);
    }

    @Override
    public final synchronized void close() {
        this.index.close();
        this.index = null;
    }

    @Override
    public final String toString() {
        return this.index.toString();
    }

    // set tools

    public HandleSet joinConstructive(final HandleSet other) throws SpaceExceededException {
        return joinConstructive(this, other);
    }

    // now the same for set-set
    public static HandleSet joinConstructive(final HandleSet set1, final HandleSet set2) throws SpaceExceededException {
        // comparators must be equal
        if ((set1 == null) || (set2 == null)) return null;
        assert set1.comparator() == set2.comparator();
        if (set1.comparator() != set2.comparator()) return null;
        if (set1.isEmpty() || set2.isEmpty()) return new RowHandleSet(set1.keylen(), set1.comparator(), 0);

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

    private static HandleSet joinConstructiveByTest(final HandleSet small, final HandleSet large) throws SpaceExceededException {
        final Iterator<byte[]> mi = small.iterator();
        final HandleSet result = new RowHandleSet(small.keylen(), small.comparator(), 0);
        byte[] o;
        while (mi.hasNext()) {
            o = mi.next();
            if (large.has(o)) result.put(o);
        }
        result.optimize();
        return result;
    }

    private static HandleSet joinConstructiveByEnumeration(final HandleSet set1, final HandleSet set2) throws SpaceExceededException {
        // implement pairwise enumeration
        final ByteOrder comp = set1.comparator();
        final Iterator<byte[]> mi = set1.iterator();
        final Iterator<byte[]> si = set2.iterator();
        final HandleSet result = new RowHandleSet(set1.keylen(), comp, 0);
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
        result.optimize();
        return result;
    }

    @Override
    public void excludeDestructive (final Set<byte[]> other) {
        if (other == null) return;
        if (other.isEmpty()) return;

        if (other.size() > this.size()) {
            for (byte[] b: this) {if (other.contains(b)) this.remove(b);}   
        } else {
            for (byte[] b: other) {this.remove(b) ;}
        }
    }
/*   not used 2013-06-06 
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
*/
    public static void main(String[] args) {
        HandleSet s = new RowHandleSet(8, NaturalOrder.naturalOrder, 100);
        try {
            s.put(UTF8.getBytes("Hello"));
            s.put(UTF8.getBytes("World"));

            // test Serializable
            try {
                // write to file
                File f = File.createTempFile("HandleSet", "stream");
                ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f));
                out.writeObject(s);
                out.close();

                // read from file
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(f));
                RowHandleSet s1 = (RowHandleSet) in.readObject();
                in.close();

                for (byte[] b: s1) {
                    System.out.println(UTF8.String(b));
                }
                s1.close();
            } catch(IOException e) {
                e.printStackTrace();
            } catch (final ClassNotFoundException e) {
                e.printStackTrace();
            }
        } catch (final SpaceExceededException e) {
            e.printStackTrace();
        }
        s.close();
        ConcurrentLog.shutdown();
    }

}
