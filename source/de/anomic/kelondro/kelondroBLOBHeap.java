// kelondroBLOBHeap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.07.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
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
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;

public final class kelondroBLOBHeap implements kelondroBLOB {

    private kelondroBytesLongMap index;    // key/seek relation for used records
    private ArrayList<gap>    free;     // list of {size, seek} pairs denoting space and position of free records
    private final File                 heapFile; // the file of the heap
    private final kelondroByteOrder    ordering; // the ordering on keys
    private RandomAccessFile     file;     // a random access to the file

    public class gap {
        public long seek;
        public int size;
        public gap(final long seek, final int size) {
            this.seek = seek;
            this.size = size;
        }
    }
    
    /*
     * This class implements a BLOB management based on a sequence of records in a random access file
     * The data structure is:
     * file   :== record*
     * record :== reclen key blob
     * reclen :== <4 byte integer == length of key and blob>
     * key    :== <bytes as defined with keylen, if first byte is zero then record is empty>
     * blob   :== <bytes of length reclen - keylen>
     * that means that each record has the size reclen+4
     * 
     * The elements are organized in two data structures:
     * index<kelondroBytesLongMap> : key/seek relation for used records
     * free<ArrayList<Integer[]>>  : list of {size, seek} pairs denoting space and position of free records
     * 
     * Because the blob sizes are stored with integers, one entry may not exceed 2GB
     * 
     * If a record is removed, it becomes a free record.
     * New records are either appended to the end of the file or filled into a free record.
     * A free record must either fit exactly to the size of the new record, or an old record is splitted
     * into a filled and a new, smaller empty record.
     */

    /**
     * create a heap file: a arbitrary number of BLOBs, indexed by an access key
     * The heap file will be indexed upon initialization.
     * @param heapFile
     * @param keylength
     * @param ordering
     * @throws IOException
     */
    public kelondroBLOBHeap(final File heapFile, final int keylength, final kelondroByteOrder ordering) throws IOException {
        this.ordering = ordering;
        this.heapFile = heapFile;
        
        this.index = new kelondroBytesLongMap(keylength, this.ordering, 0);
        this.free = new ArrayList<gap>();
        this.file = new RandomAccessFile(heapFile, "rw");
        final byte[] key = new byte[keylength];
        int reclen;
        long seek = 0;

        loop: while (true) { // don't test available() here because this does not work for files > 2GB
            
            try {
                // go to seek position
                file.seek(seek);
            
                // read length of the following record without the length of the record size bytes
                reclen = file.readInt();
                assert reclen > 0;
                if (reclen == 0) {
                    // very bad file inconsistency
                    this.file.setLength(seek); // delete everything else at the remaining of the file :-(
                    break loop;
                }
                
                // read key
                file.readFully(key);
                
            } catch (final IOException e) {
                // EOF reached
                break loop; // terminate loop
            }
            
            // check if this record is empty
            if (key == null || key[0] == 0) {
                // it is an empty record, store to free list
                if (reclen > 0) free.add(new gap(seek, reclen));
            } else {
                // store key and access address of entry in index
                try {
                    if (this.ordering.wellformed(key)) {
                        index.addl(key, seek);
                    } else {
                        serverLog.logWarning("kelondroBLOBHeap", "BLOB " + heapFile.getName() + ": skiped not wellformed key " + new String(key) + " at seek pos " + seek);
                    }
                } catch (final IOException e) {
                    e.printStackTrace();
                    break loop;
                }
            }            
            // new seek position
            seek += 4L + reclen;
        }
        
        // try to merge free entries
        if (this.free.size() > 1) {
            int merged = 0;
            gap lastFree, nextFree;
            final Iterator<gap> i = this.free.iterator();
            lastFree = i.next();
            while (i.hasNext()) {
                nextFree = i.next();
                System.out.println("*** DEBUG BLOB: free-seek = " + nextFree.seek + ", size = " + nextFree.size);
                // check if they follow directly
                if (lastFree.seek + lastFree.size + 4 == nextFree.seek) {
                    // merge those records
                    file.seek(lastFree.seek);
                    lastFree.size = lastFree.size + nextFree.size + 4; // this updates also the free array
                    file.writeInt(lastFree.size);
                    file.seek(nextFree.seek);
                    file.write(0);file.write(0);file.write(0);file.write(0);
                    i.remove();
                    merged++;
                } else {
                    lastFree = nextFree;
                }
            }
            serverLog.logInfo("kelondroBLOBHeap", "BLOB " + heapFile.getName() + ": merged " + merged + " free records");
        }
        
        // DEBUG
        /*
        Iterator<byte[]> i = index.keys(true, null);
        byte[] b;
        int c = 0;
        while (i.hasNext()) {
            key = i.next();
            System.out.println("KEY=" + new String(key));
            b = get(key);
            System.out.println("BLOB=" + new String(b));
            System.out.println();
            c++;
        }
        System.out.println("*** DEBUG - counted " + c + " BLOBs");
        */
    }
    
    /**
     * the number of BLOBs in the heap
     * @return the number of BLOBs in the heap
     */
    public int size() {
        return this.index.size();
    }

    /**
     * test if a key is in the heap file. This does not need any IO, because it uses only the ram index
     * @param key
     * @return true if the key exists, false othervise
     */
    public boolean has(final byte[] key) {
        assert index != null;
        assert index.row().primaryKeyLength == key.length;
        
        // check if the index contains the key
        try {
            return index.getl(key) >= 0;
        } catch (final IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * add a BLOB to the heap: this adds the blob always to the end of the file
     * @param key
     * @param blob
     * @throws IOException
     */
    private void add(final byte[] key, final byte[] blob) throws IOException {
        add(key, blob, 0, blob.length);
    }
    
    /**
     * add a BLOB to the heap: this adds the blob always to the end of the file
     * @param key
     * @param blob
     * @throws IOException
     */
    private void add(final byte[] key, final byte[] blob, final int offset, final int len) throws IOException {
        assert len > 0;
        assert index.row().primaryKeyLength == key.length;
        assert blob == null || blob.length - offset >= len;
        if ((blob == null) || (blob.length == 0)) return;
        final int pos = (int) file.length();
        file.seek(file.length());
        file.writeInt(len + key.length);
        file.write(key);
        file.write(blob, offset, len);
        index.putl(key, pos);
    }
    
    /**
     * read a blob from the heap
     * @param key
     * @return
     * @throws IOException
     */
    public synchronized byte[] get(final byte[] key) throws IOException {
        assert index.row().primaryKeyLength == key.length;
        
        // check if the index contains the key
        final long pos = index.getl(key);
        if (pos < 0) return null;
        
        // access the file and read the container
        file.seek(pos);
        final int len = file.readInt() - index.row().primaryKeyLength;
        if (serverMemory.available() < len) {
            if (!serverMemory.request(len, false)) return null; // not enough memory available for this blob
        }
        final byte[] blob = new byte[len];
        
        // read the key
        final byte[] keyf = new byte[index.row().primaryKeyLength];
        file.readFully(keyf);
        assert this.ordering.compare(key, keyf) == 0;
        
        // read the blob
        file.readFully(blob);
        
        return blob;
    }

    /**
     * clears the content of the database
     * @throws IOException
     */
    public synchronized void clear() throws IOException {
        index.clear();
        free.clear();
        try {
            file.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        this.heapFile.delete();
        this.file = new RandomAccessFile(heapFile, "rw");
    }

    /**
     * close the BLOB table
     */
    public synchronized void close() {
        index.close();
        free.clear();
        try {
            file.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        index = null;
        free = null;
        file = null;
    }

    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    public int keylength() {
        return this.index.row().primaryKeyLength;
    }

    /**
     * write a whole byte array as BLOB to the table
     * @param key  the primary key
     * @param b
     * @throws IOException
     */
    public synchronized void put(final byte[] key, final byte[] b) throws IOException {
        assert key.length == index.row().primaryKeyLength;
        
        // first remove the old entry
        this.remove(key);
        
        // then look if we can use a free entry
        if (this.free.size() > 0) {
            // find the largest entry
            long lseek = -1;
            int  lsize = 0;
            final int reclen = b.length + index.row().primaryKeyLength;
            gap entry;
            Iterator<gap> i = this.free.iterator();
            while (i.hasNext()) {
                entry = i.next();
                if (entry.size == reclen) {
                    // we found an entry that has exactly the size that we need!
                    // we use that entry and stop looking for a larger entry
                    file.seek(entry.seek);
                    final int reclenf = file.readInt();
                    assert reclenf == reclen;
                    file.write(key);
                    file.write(b);
                    
                    // remove the entry from the free list
                    i.remove();
                    
                    // add the entry to the index
                    this.index.putl(key, entry.seek);
                    
                    System.out.println("*** DEBUG BLOB: replaced-fit record at " + entry.seek + ", reclen=" + reclen + ", key=" + new String(key));
                    
                    // finished!
                    return;
                }
                // look for the biggest size
                if (entry.size > lsize) {
                    lseek = entry.seek;
                    lsize = entry.size;
                }
            }
            
            // check if the found entry is large enough
            if (lsize > reclen + 4) {
                // split the free entry into two new entries
                // if would be sufficient if lsize = reclen + 4, but this would mean to create
                // an empty entry with zero next bytes for BLOB and key, which is not very good for the
                // data structure in the file
                
                // write the new entry
                file.seek(lseek);
                file.writeInt(reclen);
                file.write(key);
                file.write(b);
                
                // add the index to the new entry
                index.putl(key, lseek);
                
                // define the new empty entry
                final int newfreereclen = lsize - reclen - 4;
                assert newfreereclen > 0;
                file.writeInt(newfreereclen);
                
                // remove the old free entry
                i = this.free.iterator();
                while (i.hasNext()) {
                    entry = i.next();
                    if (entry.size == (long) lsize && entry.seek == lseek) {
                        // remove the entry from the free list
                        i.remove();
                        break;
                    }
                }
                
                // add a new free entry
                free.add(new gap(lseek + 4 + reclen, newfreereclen));
                
                System.out.println("*** DEBUG BLOB: replaced-split record at " + lseek + ", reclen=" + reclen + ", new reclen=" + newfreereclen + ", key=" + new String(key));
                
                // finished!
                return;
            }
        }
        
        // if there is no free entry or no free entry is large enough, append the entry at the end of the file
        this.add(key, b);
    }

    /**
     * remove a BLOB
     * @param key  the primary key
     * @throws IOException
     */
    public synchronized void remove(final byte[] key) throws IOException {
        assert index.row().primaryKeyLength == key.length;
        
        // check if the index contains the key
        final long pos = index.getl(key);
        if (pos < 0) return;
        
        // access the file and read the container
        file.seek(pos);
        int len = file.readInt();
        
        // add entry to free array
        this.free.add(new gap(pos, len));
        
        // fill zeros to the content
        while (len-- > 0) file.write(0);
        
        // remove entry from index
        this.index.removel(key);
    }

    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public synchronized kelondroCloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        return new kelondroRotateIterator<byte[]>(this.index.keys(up, null), null, 1);
    }

    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    public synchronized kelondroCloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return this.index.keys(up, firstKey);
    }

    public static void main(final String[] args) {
        final File f = new File("/Users/admin/blobtest.heap");
        try {
            //f.delete();
            final kelondroBLOBHeap heap = new kelondroBLOBHeap(f, 12, kelondroNaturalOrder.naturalOrder);
            heap.put("aaaaaaaaaaaa".getBytes(), "eins zwei drei".getBytes());
            heap.put("aaaaaaaaaaab".getBytes(), "vier fuenf sechs".getBytes());
            heap.put("aaaaaaaaaaac".getBytes(), "sieben acht neun".getBytes());
            heap.put("aaaaaaaaaaad".getBytes(), "zehn elf zwoelf".getBytes());
            heap.remove("aaaaaaaaaaab".getBytes());
            heap.remove("aaaaaaaaaaac".getBytes());
            heap.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
    
}
