// BufferedRecords.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2008 on http://yacy.net
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

package net.yacy.kelondro.io;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;



/**
 * The kelondroBufferedEcoFS extends the IO reduction to EcoFS by providing a
 * write buffer to elements that are INSIDE the filed entries of the file
 * That means, each time, an entry is written to the end of the file, it is NOT buffered here,
 * but possibly buffered in the enclosed kelondroEcoFS
 */
public final class BufferedRecords {

    private final Records efs;
    private final int maxEntries;
    private final TreeMap<Long, byte[]> buffer;

    public BufferedRecords(final Records efs, final int maxEntries) {
        this.efs = efs;
        this.maxEntries = maxEntries;
        this.buffer = new TreeMap<Long, byte[]>();
    }
    
    public synchronized void clear() {
        this.efs.clear();
        this.buffer.clear();
    }

    /**
     * flush the buffer: this shall be called before any file-based iterations
     * on data structures on records are made
     * @throws IOException
     */
    public synchronized void flushBuffer() throws IOException {
        flushBuffer0();
        if (this.efs != null) this.efs.flushBuffer();
    }

    private final void flushBuffer0() throws IOException {
        if (this.efs == null) return;
        for (final Map.Entry<Long, byte[]> entry: this.buffer.entrySet()) {
            this.efs.put(entry.getKey().intValue(), entry.getValue(), 0);
        }
        this.buffer.clear();
    }

    public final synchronized long size() throws IOException {
        return this.efs == null ? 0 : this.efs.size();
    }

    public final File filename() {
        return this.efs.filename();
    }

    public final synchronized void close() {
        try {
            flushBuffer0();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        if (this.efs != null) this.efs.close();
    }

    @Override
    protected final synchronized void finalize() {
        if (this.efs != null) close();
    }

    public final void get(final long index, final byte[] b, final int start) throws IOException {
        final Long idx = Long.valueOf(index);
        final byte[] bb;
        synchronized (this) {
            assert b.length - start >= this.efs.recordsize;
            bb = this.buffer.get(idx);
            if (bb == null) {
                this.efs.get(index, b, start);
                return;
            }
        }
        System.arraycopy(bb, 0, b, start, this.efs.recordsize);
    }

    public final synchronized void put(final long index, final byte[] b, final int start) throws IOException {
        assert b.length - start >= this.efs.recordsize;
        final long s = size();
        if (index > s) throw new IndexOutOfBoundsException("kelondroBufferedEcoFS.put(" + index + ") outside bounds (" + size() + ")");
        if (index == s) {
            this.efs.add(b, start);
        } else {
            final byte[] bb = new byte[this.efs.recordsize];
            System.arraycopy(b, start, bb, 0, this.efs.recordsize);
            this.buffer.put(Long.valueOf(index), bb);
            if (this.buffer.size() > this.maxEntries) flushBuffer0();
       }
    }

    public final synchronized void add(final byte[] b, final int start) throws IOException {
        assert b.length - start >= this.efs.recordsize;
        // index == size() == efs.size();
        this.efs.add(b, start);
    }

    public final synchronized void cleanLast(final byte[] b, final int start) throws IOException {
        assert b.length - start >= this.efs.recordsize;
        final byte[] bb = this.buffer.remove(Long.valueOf(size() - 1));
        if (bb == null) {
            this.efs.cleanLast(b, start);
        } else {
            System.arraycopy(bb, 0, b, start, this.efs.recordsize);
            this.efs.cleanLast();
        }
    }

    public final synchronized void cleanLast() throws IOException {
        this.buffer.remove(Long.valueOf(size() - 1));
        this.efs.cleanLast();
    }

    public final void deleteOnExit() {
        this.efs.deleteOnExit();
    }

    /**
     * main - writes some data and checks the tables size (with time measureing)
     * @param args
     */
    public static void main(final String[] args) {
        // open a file, add one entry and exit
        final File f = new File(args[0]);
        if (f.exists()) FileUtils.deletedelete(f);
        try {
            final Records t = new Records(f, 8);
            final byte[] b = new byte[8];
            t.add("01234567".getBytes(), 0);
            t.add("ABCDEFGH".getBytes(), 0);
            t.add("abcdefgh".getBytes(), 0);
            t.add("--------".getBytes(), 0);
            t.add("********".getBytes(), 0);
            for (int i = 0; i < 1000; i++) t.add("++++++++".getBytes(), 0);
            t.add("=======0".getBytes(), 0);
            t.add("=======1".getBytes(), 0);
            t.add("=======2".getBytes(), 0);
            t.cleanLast(b, 0);
            System.out.println(UTF8.String(b));
            t.cleanLast(b, 0);
            //t.clean(2, b, 0);
            System.out.println(UTF8.String(b));
            t.get(1, b, 0);
            System.out.println(UTF8.String(b));
            t.put(1, "AbCdEfGh".getBytes(), 0);
            t.get(1, b, 0);
            System.out.println(UTF8.String(b));
            t.get(3, b, 0);
            System.out.println(UTF8.String(b));
            t.get(4, b, 0);
            System.out.println(UTF8.String(b));
            System.out.println("size = " + t.size());
            //t.clean(t.size() - 2);
            t.cleanLast();
            final long start = System.currentTimeMillis();
            long c = 0;
            for (int i = 0; i < 100000; i++) {
                c = t.size();
            }
            System.out.println("size() needs " + ((System.currentTimeMillis() - start) / 100) + " nanoseconds");
            System.out.println("size = " + c);

            t.close();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }
}
