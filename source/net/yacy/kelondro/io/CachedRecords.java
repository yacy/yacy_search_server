// CachedRecords.java
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;


/**
 * The Records data structure is a flat file with records of fixed length.
 * The file does not contain any meta information and the first record starts
 * right at file position 0.
 * The access rules are in such a way that a minimum of IO operations are necessary
 * Two caches provide a mirror to content in the file: a read cache and a write buffer
 * The read cache contains a number of entries from the file; a mirror that moves
 * whenever information outside the mirror is requested.
 * The write buffer always exists only at the end of the file. It contains only records
 * that have never been written to the file before. When the write buffer is flushed,
 * the file grows
 * The record file may also shrink when the last entry of the file is removed.
 * Removal of Entries inside the file is not possible, but such entries can be erased
 * by overwriting the data with zero bytes
 * All access to the file is made with byte[] that are generated outside of this class
 * This class only references byte[] that are handed over to methods of this class.
 */
public final class CachedRecords {

    private RandomAccessFile raf;
    private final File tablefile;
    /**
     * number of bytes in one record
     */
    protected final int recordsize;
    private long cacheindex;
    /**
     * number of entries in buffer
     */
    private int cachecount;
    private int buffercount;
    private byte[] cache;
    private byte[] buffer;
    private final byte[] zero;

    /**
     * stay below hard disc cache (is that necessary?)
     */
    private static final int maxReadCache = 16 * 1024;
    private static final int maxWriteBuffer = 16 * 1024;


    public CachedRecords(final File tablefile, final int recordsize) throws IOException {
        this.tablefile = tablefile;
        this.recordsize = recordsize;

        // initialize zero buffer
        this.zero = new byte[recordsize];
        for (int i = 0; i < recordsize; i++) this.zero[i] = 0;

        // initialize table file
        if (!tablefile.exists()) {
            // make new file
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tablefile);
            } catch (final FileNotFoundException e) {
                // should not happen
                ConcurrentLog.logException(e);
            }
            try { if (fos != null) fos.close(); } catch (final IOException e) {}
        }

        // open an existing table file
        try {
            this.raf = new RandomAccessFile(tablefile,"rw");
        } catch (final FileNotFoundException e) {
            // should never happen
            ConcurrentLog.logException(e);
        }

        // initialize cache and buffer
        int cachesize = Math.max(1, (maxReadCache / recordsize)) * recordsize;
        int buffersize = Math.max(1, (maxWriteBuffer / recordsize)) * recordsize;
        if (!MemoryControl.request(cachesize + buffersize + 1024 * 1024 * 20, true)) {
            // not enough memory there, take less
            long lessmem = Math.min(Math.min(maxReadCache, maxWriteBuffer) / 8, MemoryControl.available() - (1024 * 1024 * 6) / 6);
            //System.out.println("newmem vorher: cachesize = " + cachesize + ", buffersize = " + buffersize + ", available = " + serverMemory.available() + ", lessmem = " + lessmem);
            cachesize = Math.max(1, (int) (lessmem / recordsize)) * recordsize;
            buffersize = Math.max(1, (int) (lessmem / recordsize)) * recordsize;
            //System.out.println("newmem nachher: cachesize = " + cachesize + ", buffersize = " + buffersize);
        }

        this.cache = new byte[cachesize];
        this.buffer = new byte[buffersize];
        this.buffercount = 0;

        // first-time read of cache
        fillCache(0);
    }

    /**
     * @param tablefile
     * @param recordsize
     * @return number of records in table
     */
    public final static long tableSize(final File tablefile, final long recordsize) throws IOException {
        if (!tablefile.exists()) return 0;
        final long size = tablefile.length();
        if (size % recordsize != 0) throw new IOException("wrong file size: file = " + tablefile + ", size = " + size + ", recordsize = " + recordsize);
        return size / recordsize;
    }

    public final static void fixTableSize(final File tablefile, final long recordsize) throws IOException {
        if (!tablefile.exists()) return;
        final long size = tablefile.length();
        long cut = size % recordsize;
        if (cut > 0) {
            RandomAccessFile raf = new RandomAccessFile(tablefile, "rw");
            raf.setLength(size - cut);
            raf.close();
        }
    }

    /**
     * @return the number of records in file plus number of records in buffer
     * @throws IOException
     */
    public final synchronized long size() throws IOException {
        return filesize() + this.buffercount;
    }

    public final File filename() {
        return this.tablefile;
    }

    /**
     * @return records in file
     * @throws IOException
     */
    private final long filesize() throws IOException {
        return this.raf.length() / this.recordsize;
    }

    /**
     * checks if the index is inside the cache
     *
     * @param index
     * @return the index offset inside the cache or -1 if the index is not in the cache
     */
    private final int inCache(final long index) {
        if (index >= this.cacheindex && index < this.cacheindex + this.cachecount) {
            return (int) (index - this.cacheindex);
        }
        return -1;
    }

    /**
     * checks if the index is inside the buffer
     *
     * @param index
     * @return the index offset inside the buffer or -1 if the index is not in the buffer
     * @throws IOException
     */
    private final int inBuffer(final long index) throws IOException {
        final long fs = filesize();
        if ((index >= fs) && (index < fs + this.buffercount)) {
            return (int) (index - fs);
        }
        return -1;
    }

    /**
     * load cache with copy of disc content; start with record at index
     *
     * if the record would overlap with the write buffer,
     * its start is shifted forward until it fits
     *
     * @param index
     * @throws IOException
     */
    private final void fillCache(long index) throws IOException {
        //System.out.println("*** DEBUG FillCache " + this.tablefile.getName() + " at index " + index);
        // first check if the index is inside the current cache
        assert inCache(index) < 0;
        if (inCache(index) >= 0) return;

        // calculate new start position
        final long fs = this.filesize();
        if (index + this.cache.length / this.recordsize > fs) {
            index = fs - this.cache.length / this.recordsize;
        }
        if (index < 0) index = 0;

        // calculate number of records that shall be stored in the cache
        this.cachecount = (int) Math.min(this.cache.length / this.recordsize, this.filesize() - index);
        assert this.cachecount >= 0;

        // check if we need to read 0 bytes from the file
        this.cacheindex = index;
        if (this.cachecount == 0) return;

        // copy records from file to cache
        this.raf.seek(this.recordsize * index);
        this.raf.readFully(this.cache, 0, this.recordsize * this.cachecount);
    }

    /**
     * write buffer to end of file
     */
    public final void flushBuffer() {
        try {
            this.raf.seek(this.raf.length());
            this.raf.write(this.buffer, 0, this.recordsize * this.buffercount);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        this.buffercount = 0;
    }

    public final synchronized void close() {
        flushBuffer();

        // then close the file
        if (this.raf != null) try {
            this.raf.close();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        this.raf = null;
        this.buffer = null;
        this.cache = null;
    }

    /**
     * @param index record which should be read
     * @param b destination array
     * @param start offset in b to store data
     * @throws IOException
     */
    public final synchronized void get(final long index, final byte[] b, final int start) throws IOException {
        assert b.length - start >= this.recordsize;
        if (index >= size()) throw new IndexOutOfBoundsException("kelondroEcoFS.get(" + index + ") outside bounds (" + this.size() + ")");
        // check if index is inside of cache
        int p = inCache(index);
        final int q = (p >= 0) ? -1 : inBuffer(index);
        if (p < 0 && q < 0) {
            // the index is outside of cache and buffer index. shift cache window
            fillCache(index);
            p = inCache(index);
            assert p >= 0;
        }
        if (p >= 0) {
            // read entry from the cache
            System.arraycopy(this.cache, p * this.recordsize, b, start, this.recordsize);
            return;
        }
        if (q >= 0) {
            // read entry from the buffer
            System.arraycopy(this.buffer, q * this.recordsize, b, start, this.recordsize);
            return;
        }
        assert false;
    }

    public final synchronized void put(final long index, final byte[] b, final int start) throws IOException {
        assert b.length - start >= this.recordsize;
        final long s = filesize() + this.buffercount;
        if (index > s) throw new IndexOutOfBoundsException("kelondroEcoFS.put(" + index + ") outside bounds (" + this.size() + ")");

        // check if this is an empty entry
        if (isClean(b , start, this.recordsize)) {
            clean(index);
            return;
        }

        // check if index is inside of cache
        final int p = inCache(index);
        final int q = (p >= 0) ? -1 : inBuffer(index);
        if (p >= 0) {
            // write entry to the cache and to the file
            System.arraycopy(b, start, this.cache, p * this.recordsize, this.recordsize);
            this.raf.seek(index * this.recordsize);
            this.raf.write(b, start, this.recordsize);
            return;
        }
        if (q >= 0) {
            // write entry to the buffer
            System.arraycopy(b, start, this.buffer, q * this.recordsize, this.recordsize);
            return;
        }
        if (index == s) {
            // append the record to the end of the file;

            // look if there is space in the buffer
            if (this.buffercount >= this.buffer.length / this.recordsize) {
                assert this.buffercount == this.buffer.length / this.recordsize;
                // the record does not fit in current buffer
                // write buffer
                flushBuffer();
                // write new entry to buffer
                System.arraycopy(b, start, this.buffer, 0, this.recordsize);
                this.buffercount = 1;
            } else {
                System.arraycopy(b, start, this.buffer, this.buffercount * this.recordsize, this.recordsize);
                this.buffercount++;
            }
            assert this.buffercount <= this.buffer.length / this.recordsize;
        } else {
            // write the record directly to the file,
            // do not care about the cache; this case was checked before
            this.raf.seek(index * this.recordsize);
            this.raf.write(b, start, this.recordsize);
        }
    }

    public final synchronized void add(final byte[] b, final int start) throws IOException {
        // index == size() == filesize() + (long) this.buffercount

        assert b.length - start >= this.recordsize;

        // check if this is an empty entry
        if (isClean(b , start, this.recordsize)) {
            // it is not possible to add a clean record at the end of a EcoFS, because
            // such records should cause the record to shrink
            throw new IOException("add: record at end is clean");
        }

        // append the record to the end of the file;
        // look if there is space in the buffer
        if (this.buffercount >= this.buffer.length / this.recordsize) {
            assert this.buffercount == this.buffer.length / this.recordsize;
            // the record does not fit in current buffer
            // write buffer
            flushBuffer();
            // write new entry to buffer
            System.arraycopy(b, start, this.buffer, 0, this.recordsize);
            this.buffercount = 1;
        } else {
            System.arraycopy(b, start, this.buffer, this.buffercount * this.recordsize, this.recordsize);
            this.buffercount++;
        }
        assert this.buffercount <= this.buffer.length / this.recordsize;
    }

    private final static boolean isClean(final byte[] b, final int offset, final int length) {
        for (int i = 0; i < length; i++) {
            if (b[i + offset] != 0) return false;
        }
        return true;
    }

    private final boolean isClean(final long index) throws IOException {
         assert index < size();
         // check if index is inside of cache
         int p = inCache(index);
         final int q = (p >= 0) ? -1 : inBuffer(index);
         if (p < 0 && q < 0) {
             // the index is outside of cache and buffer index. shift cache window
             fillCache(index);
             p = inCache(index);
             assert p >= 0;
         }
         if (p >= 0) {
             // check entry from the cache
             return isClean(this.cache, p * this.recordsize, this.recordsize);
         }
         if (q >= 0) {
             // check entry from the buffer
             return isClean(this.buffer, q * this.recordsize, this.recordsize);
         }
         assert false;
         return false;
    }

    /**
     * @see clean(long, byte[], int)
     * @param index
     * @throws IOException
     */
    public final synchronized void clean(final long index) throws IOException {
        final long s = size();
        if (index >= s) throw new IndexOutOfBoundsException("kelondroEcoFS.clean(" + index + ") outside bounds (" + s + ")");
        if (index == s - 1) {
            cleanLast();
            return;
        }

        // check if index is inside of cache
        final int p = inCache(index);
        final int q = (p >= 0) ? -1 : inBuffer(index);
        if (p >= 0) {
            // write zero bytes to the cache and to the file
            System.arraycopy(this.zero, 0, this.cache, p * this.recordsize, this.recordsize);
            this.raf.seek(index * this.recordsize);
            this.raf.write(this.zero, 0, this.recordsize);
            return;
        }
        if (q >= 0) {
            // write zero to the buffer
            System.arraycopy(this.zero, 0, this.buffer, q * this.recordsize, this.recordsize);
            return;
        }

        this.raf.seek(index * this.recordsize);
        this.raf.write(this.zero, 0, this.recordsize);
    }

    /**
     * @see clean(long, byte[], int)
     * @param b
     * @param start
     * @throws IOException
     */
    public final synchronized void cleanLast(final byte[] b, final int start) throws IOException {
        cleanLast0(b, start);
        long i;
        while (((i = size()) > 0) && (isClean(i - 1))) {
            //System.out.println("Extra clean/1: before size = " + size());
            cleanLast0();
            //System.out.println("               after  size = " + size());
        }
    }

    /**
     * this is like
     * <code>clean(this.size() - 1, b, start);</code>
     *
     * @see clean(long, byte[], int)
     * @param b
     * @param start
     * @throws IOException
     */
    private final synchronized void cleanLast0(final byte[] b, final int start) throws IOException {
        assert b.length - start >= this.recordsize;
        // check if index is inside of cache
        final long s = this.size();
        int p = inCache(s - 1);
        final int q = (p >= 0) ? -1 : inBuffer(s - 1);
        if ((p < 0) && (q < 0)) {
            // the index is outside of cache and buffer index. shift cache window
            fillCache(this.size() - 1);
            p = inCache(this.size() - 1);
            assert p >= 0;
        }
        if (p >= 0) {
            // read entry from the cache
            System.arraycopy(this.cache, p * this.recordsize, b, start, this.recordsize);
            // shrink cache and file
            assert this.buffercount == 0;
            this.raf.setLength((s - 1) * this.recordsize);
            this.cachecount--;
            return;
        }
        if (q >= 0) {
            // read entry from the buffer
            System.arraycopy(this.buffer, q * this.recordsize, b, start, this.recordsize);
            // shrink buffer
            assert this.buffercount > 0;
            this.buffercount--;
            return;
        }
        assert false;
    }

    /**
     * @see clean(long, byte[], int)
     * @throws IOException
     */
    public final synchronized void cleanLast() throws IOException {
        cleanLast0();
        long i;
        while (((i = size()) > 0) && (isClean(i - 1))) {
            //System.out.println("Extra clean/0: before size = " + size());
            cleanLast0();
            //System.out.println("               after  size = " + size());
        }
    }

    private final synchronized void cleanLast0() throws IOException {

        // check if index is inside of cache
        final long s = this.size();
        final long p = inCache(s - 1);
        final long q = (p >= 0) ? -1 : inBuffer(s - 1);
        if (p >= 0) {
            // shrink cache and file
            assert this.buffercount == 0;
            this.raf.setLength((s - 1) * this.recordsize);
            this.cachecount--;
            return;
        }
        if (q >= 0) {
            // shrink buffer
            assert this.buffercount > 0;
            this.buffercount--;
            return;
        }
        // check if file should shrink
        assert this.buffercount == 0;
        this.raf.setLength((s - 1) * this.recordsize);
    }

    public final void deleteOnExit() {
        this.tablefile.deleteOnExit();
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
            final CachedRecords t = new CachedRecords(f, 8);
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
