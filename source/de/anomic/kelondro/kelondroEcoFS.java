// kelondroEcoFS.java
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class kelondroEcoFS {


    /*
     * The EcoFS is a flat file with records of fixed length. The file does not contain
     * any meta information and the first record starts right at file position 0
     * The access rules are in such a way that a minimum of IO operations are necessary
     * Two caches provide a mirror to content in the file: a read cache and a write buffer
     * The read cache contains a number of entries from the file; a mirror that moves
     * whenever information outsite the mirror is requested.
     * The write buffer always exists only at the end of the file. It contains only records
     * that have never been written to the file before. When the write buffer is flushed,
     * the file grows
     * The record file may also shrink when the last entry of the file is removed.
     * Removal of Entries inside the file is not possible, but such entries can be erased
     * by overwriting the data with zero bytes
     * All access to the file is made with byte[] that are generated outsite of this class
     * This class only references byte[] that are handed over to methods of this class.
     */
    
    private RandomAccessFile raf;
    private File tablefile;
    protected int recordsize;  // number of bytes in one record
    private long cacheindex;
    private int cachecount, buffercount; // number of entries in buffer
    private byte[] cache, buffer, zero;
    
    private static final int maxBuffer = 4 * 1024; // stay below hard disc cache (is that necessary?)
    
    
    public kelondroEcoFS(File tablefile, int recordsize) throws IOException {
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
            } catch (FileNotFoundException e) {
                // should not happen
                e.printStackTrace();
            }
            try { fos.close(); } catch (IOException e) {}
        }
        
        // open an existing table file
        try {
            raf = new RandomAccessFile(tablefile, "rw");
        } catch (FileNotFoundException e) {
            // should never happen
            e.printStackTrace();
        }
        
        // initialize cache and buffer
        int maxrecords = Math.max(1, maxBuffer / recordsize);
        cache = new byte[maxrecords * recordsize];
        buffer = new byte[maxrecords * recordsize];
        this.buffercount = 0;
        
        // first-time read of cache
        fillCache(0);
    }
    
    public static long tableSize(File tablefile, long recordsize) {
        // returns number of records in table
        if (!tablefile.exists()) return 0;
        long size = tablefile.length();
        assert size % recordsize == 0;
        return size / (long) recordsize;
    }

    public synchronized long size() throws IOException {
        // return the number of records in file plus number of records in buffer
        return filesize() + (long) this.buffercount;
    }
    
    public File filename() {
        return this.tablefile;
    }
    
    private long filesize() throws IOException {
        return raf.length() / (long) recordsize;
    }

    private int inCache(long index) {
        // checks if the index is inside the cache and returns the index offset inside
        // the cache if the index is inside the cache
        // returns -1 if the index is not in the cache
        if ((index >= this.cacheindex) && (index < this.cacheindex + this.cachecount)) {
            return (int) (index - this.cacheindex);
        }
        return -1;
    }
    
    private int inBuffer(long index) throws IOException {
        // checks if the index is inside the buffer and returns the index offset inside
        // the buffer if the index is inside the buffer
        // returns -1 if the index is not in the buffer
        long fs = filesize();
        if ((index >= fs) && (index < fs + this.buffercount)) {
            return (int) (index - fs);
        }
        return -1;
    }
    
    private void fillCache(long index) throws IOException {
        // load cache with copy of disc content; start with record at index
        // if the record would overlap with the write buffer,
        // its start is shifted forward until it fits
        
        // first check if the index is inside the current cache
        assert inCache(index) < 0;
        if (inCache(index) >= 0) return;
        
        // calculate new start position
        long fs = this.filesize();
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
        raf.seek((long) this.recordsize * (long) index);
        raf.read(this.cache, 0, this.recordsize * this.cachecount);
    }
    
    private void flushBuffer() {
        // write buffer to end of file
        try {
            raf.seek(raf.length());
            raf.write(this.buffer, 0, this.recordsize * this.buffercount);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.buffercount = 0;
    }
    
    public synchronized void close() {
        flushBuffer();
        
        // then close the file
        try {
            raf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        raf = null;
        buffer = null;
        cache = null;
    }

    public synchronized void get(long index, byte[] b, int start) throws IOException {
        assert b.length - start >= this.recordsize;
        if (index >= size()) throw new IndexOutOfBoundsException("kelondroEcoFS.get(" + index + ") outside bounds (" + this.size() + ")");
        // check if index is inside of cache
        int p = inCache(index);
        int q = (p >= 0) ? -1 : inBuffer(index);
        if ((p < 0) && (q < 0)) {
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

    public synchronized void put(long index, byte[] b, int start) throws IOException {
        assert b.length - start >= this.recordsize;
        if (index > size()) throw new IndexOutOfBoundsException("kelondroEcoFS.put(" + index + ") outside bounds (" + this.size() + ")");
        // check if this is an empty entry
        if (isClean(b , start, this.recordsize)) {
            clean(index);
            return;
        }
        // check if index is inside of cache
        int p = inCache(index);
        int q = (p >= 0) ? -1 : inBuffer(index);
        if (p >= 0) {
            // write entry to the cache and to the file
            System.arraycopy(b, start, this.cache, p * this.recordsize, this.recordsize);
            raf.seek((long) index * (long) this.recordsize);
            raf.write(b, start, this.recordsize);
            return;
        }
        if (q >= 0) {
            // write entry to the buffer
            System.arraycopy(b, start, this.buffer, q * this.recordsize, this.recordsize);
            return;
        }
        if (index == size()) {
            // append the record to the end of the file;
            
            // look if there is space in the buffer
            int bufferpos = (int) (index - filesize());
            if (bufferpos >= this.buffer.length / this.recordsize) {
                assert this.buffercount == this.buffer.length / this.recordsize;
                // the record does not fit in current buffer
                // write buffer
                flushBuffer();
                // write new entry to buffer
                System.arraycopy(b, start, this.buffer, 0, this.recordsize);
                this.buffercount = 1;
            } else {
                System.arraycopy(b, start, this.buffer, bufferpos * this.recordsize, this.recordsize);
                this.buffercount++;
            }
            assert this.buffercount <= this.buffer.length / this.recordsize;
        } else {
            // write the record directly to the file,
            // do not care about the cache; this case was checked before
            raf.seek((long) index * (long) this.recordsize);
            raf.write(b, start, this.recordsize);
        }
    }
    

    public synchronized void add(byte[] b, int start) throws IOException {
        put(size(), b, start);
    }
    
    private boolean isClean(byte[] b, int offset, int length) {
        for (int i = 0; i < length; i++) {
            if (b[i + offset] != 0) return false;
        }
        return true;
    }
    
    private boolean isClean(long index) throws IOException {
         assert index < size();
         // check if index is inside of cache
         int p = inCache(index);
         int q = (p >= 0) ? -1 : inBuffer(index);
         if ((p < 0) && (q < 0)) {
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
    
    public synchronized void clean(long index, byte[] b, int start) throws IOException {
        // removes an entry by cleaning (writing zero bytes to the file)
        // the entry that had been at the specific place before is copied to the given array b
        // if the last entry in the file was cleaned, the file shrinks by the given record
        // this is like
        // get(index, b, start);
        // put(index, zero, 0);
        // plus an additional check if the file should shrink
        
        assert b.length - start >= this.recordsize;
        if (index >= size()) throw new IndexOutOfBoundsException("kelondroEcoFS.clean(" + index + ") outside bounds (" + this.size() + ")");
        if (index == size() - 1) {
            cleanLast(b, start);
            return;
        }
        
        // check if index is inside of cache
        int p = inCache(index);
        int q = (p >= 0) ? -1 : inBuffer(index);
        if ((p < 0) && (q < 0)) {
            // the index is outside of cache and buffer index. shift cache window
            fillCache(index);
            p = inCache(index);
            assert p >= 0;
        }
        if (p >= 0) {
            // read entry from the cache
            System.arraycopy(this.cache, p * this.recordsize, b, start, this.recordsize); 
            
            // write zero bytes to the cache and to the file
            System.arraycopy(zero, 0, this.cache, p * this.recordsize, this.recordsize);
            this.raf.seek((long) index * (long) this.recordsize);
            this.raf.write(zero, 0, this.recordsize);
            return;
        }
        if (q >= 0) {
            // read entry from the buffer
            System.arraycopy(this.buffer, q * this.recordsize, b, start, this.recordsize);
            // write zero to the buffer
            System.arraycopy(zero, 0, this.buffer, q * this.recordsize, this.recordsize);
            return;
        }
        assert false;
    }

    public synchronized void clean(long index) throws IOException {
        if (index >= size()) throw new IndexOutOfBoundsException("kelondroEcoFS.clean(" + index + ") outside bounds (" + this.size() + ")");
        if (index == size() - 1) {
            cleanLast();
            return;
        }
        
        // check if index is inside of cache
        int p = inCache(index);
        int q = (p >= 0) ? -1 : inBuffer(index);
        if (p >= 0) {
            // write zero bytes to the cache and to the file
            System.arraycopy(zero, 0, this.cache, p * this.recordsize, this.recordsize);
            raf.seek((long) index * (long) this.recordsize);
            raf.write(zero, 0, this.recordsize);
            return;
        }
        if (q >= 0) {
            // write zero to the buffer
            System.arraycopy(zero, 0, this.buffer, q * this.recordsize, this.recordsize);
            return;
        }
        
        raf.seek((long) index * (long) this.recordsize);
        raf.write(zero, 0, this.recordsize);
    }

    public synchronized void cleanLast(byte[] b, int start) throws IOException {
        cleanLast0(b, start);
        long i;
        while (((i = size()) > 0) && (isClean(i - 1))) {
            //System.out.println("Extra clean/1: before size = " + size());
            cleanLast0();
            //System.out.println("               after  size = " + size());
        }
    }
    
    private synchronized void cleanLast0(byte[] b, int start) throws IOException {
        // this is like
        // clean(this.size() - 1, b, start);

        assert b.length - start >= this.recordsize;
        // check if index is inside of cache
        int p = inCache(this.size() - 1);
        int q = (p >= 0) ? -1 : inBuffer(this.size() - 1);
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
            this.raf.setLength((long) (this.size() - 1) * (long) this.recordsize);
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
    
    public synchronized void cleanLast() throws IOException {
        cleanLast0();
        long i;
        while (((i = size()) > 0) && (isClean(i - 1))) {
            //System.out.println("Extra clean/0: before size = " + size());
            cleanLast0();
            //System.out.println("               after  size = " + size());
        }
    }
    
    private synchronized void cleanLast0() throws IOException {

        // check if index is inside of cache
        long p = inCache(this.size() - 1);
        long q = (p >= 0) ? -1 : inBuffer(this.size() - 1);
        if (p >= 0) {
            // shrink cache and file
            assert this.buffercount == 0;
            this.raf.setLength((long) (this.size() - 1) * (long) this.recordsize);
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
        this.raf.setLength((long) (this.size() - 1) * (long) this.recordsize);
    }
    
    public static void main(String[] args) {
        // open a file, add one entry and exit
        File f = new File(args[0]);
        if (f.exists()) f.delete();
        try {
            kelondroEcoFS t = new kelondroEcoFS(f, 8);
            byte[] b = new byte[8];
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
            System.out.println(new String(b));
            t.clean(2, b, 0);
            System.out.println(new String(b));
            t.get(1, b, 0);
            System.out.println(new String(b));
            t.put(1, "AbCdEfGh".getBytes(), 0);
            t.get(1, b, 0);
            System.out.println(new String(b));
            t.get(3, b, 0);
            System.out.println(new String(b));
            t.get(4, b, 0);
            System.out.println(new String(b));
            System.out.println("size = " + t.size());
            t.clean(t.size() - 2);
            t.cleanLast();
            long start = System.currentTimeMillis();
            long c = 0;
            for (int i = 0; i < 100000; i++) {
                c = t.size();
            }
            System.out.println("size() needs " + ((System.currentTimeMillis() - start) / 100) + " nanoseconds");
            System.out.println("size = " + c);
            
            t.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
