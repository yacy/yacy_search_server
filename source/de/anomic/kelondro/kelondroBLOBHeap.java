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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class kelondroBLOBHeap extends kelondroBLOBHeapModifier implements kelondroBLOB {

    private HashMap<String, byte[]> buffer;     // a write buffer to limit IO to the file; attention: Maps cannot use byte[] as key
    private int                     buffersize; // bytes that are buffered in buffer
    private int                     buffermax;  // maximum size of the buffer
    
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
    public kelondroBLOBHeap(
            final File heapFile,
            final int keylength,
            final kelondroByteOrder ordering,
            int buffermax) throws IOException {
        super(heapFile, keylength, ordering);
        this.buffermax = buffermax;
        this.buffer = new HashMap<String, byte[]>();
        this.buffersize = 0;
        /*
        // DEBUG
        Iterator<byte[]> i = index.keys(true, null);
        //byte[] b;
        int c = 0;
        while (i.hasNext()) {
            key = i.next();
            System.out.println("*** DEBUG BLOBHeap " + this.name() + " KEY=" + new String(key));
            //b = get(key);
            //System.out.println("BLOB=" + new String(b));
            //System.out.println();
            c++;
            if (c >= 20) break;
        }
        System.out.println("*** DEBUG - counted " + c + " BLOBs");
        */
    }
    
    /**
     * the number of BLOBs in the heap
     * @return the number of BLOBs in the heap
     */
    public synchronized int size() {
        return super.size() + this.buffer.size();
    }

    
    /**
     * test if a key is in the heap file. This does not need any IO, because it uses only the ram index
     * @param key
     * @return true if the key exists, false otherwise
     */
    public synchronized boolean has(final byte[] key) {
        assert index != null;
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // check the buffer
        if (this.buffer.containsKey(new String(key))) return true;
        return super.has(key);
    }

    /**
     * add a BLOB to the heap: this adds the blob always to the end of the file
     * @param key
     * @param blob
     * @throws IOException
     */
    private void add(final byte[] key, final byte[] blob) throws IOException {
        assert blob.length > 0;
        assert key.length == this.keylength;
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        if ((blob == null) || (blob.length == 0)) return;
        final int pos = (int) file.length();
        file.seek(pos);
        file.writeInt(key.length + blob.length);
        file.write(key);
        file.write(blob, 0, blob.length);
        index.putl(key, pos);
    }
    
    /**
     * flush the buffer completely
     * this is like adding all elements of the buffer, but it needs only one IO access
     * @throws IOException
     */
    private void flushBuffer() throws IOException {
        // check size of buffer
        Iterator<Map.Entry<String, byte[]>> i = this.buffer.entrySet().iterator();
        int l = 0;
        while (i.hasNext()) l += i.next().getValue().length;
        assert l == this.buffersize;
        
        // append all contents of the buffer into one byte[]
        i = this.buffer.entrySet().iterator();
        final int pos = (int) file.length();
        int posFile = pos;
        int posBuffer = 0;
        
        byte[] ba = new byte[l + (4 + this.index.row().primaryKeyLength) * this.buffer.size()];
        Map.Entry<String, byte[]> entry;
        byte[] key, blob, b;
        while (i.hasNext()) {
            entry = i.next();
            key = entry.getKey().getBytes();
            blob = entry.getValue();
            index.putl(key, posFile);
            b = kelondroAbstractRA.int2array(key.length + blob.length);
            assert b.length == 4;
            System.arraycopy(b, 0, ba, posBuffer, 4);
            System.arraycopy(key, 0, ba, posBuffer + 4, key.length);
            System.arraycopy(blob, 0, ba, posBuffer + 4 + key.length, blob.length);
            posFile += 4 + key.length + blob.length;
            posBuffer += 4 + key.length + blob.length;
        }
        assert ba.length == posBuffer; // must fit exactly
        this.file.seek(pos);
        this.file.write(ba);
        this.buffer.clear();
        this.buffersize = 0;
    }
    
    /**
     * read a blob from the heap
     * @param key
     * @return
     * @throws IOException
     */
    public synchronized byte[] get(final byte[] key) throws IOException {
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // check the buffer
        byte[] blob = this.buffer.get(new String(key));
        if (blob != null) return blob;
        
        return super.get(key);
    }

    /**
     * retrieve the size of the BLOB
     * @param key
     * @return the size of the BLOB or -1 if the BLOB does not exist
     * @throws IOException
     */
    public long length(byte[] key) throws IOException {
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // check the buffer
        byte[] blob = this.buffer.get(new String(key));
        if (blob != null) return blob.length;

        return super.length(key);
    }
    
    /**
     * clears the content of the database
     * @throws IOException
     */
    public synchronized void clear() throws IOException {
    	this.buffer.clear();
        this.buffersize = 0;
        super.clear();
    }

    /**
     * close the BLOB table
     */
    public synchronized void close() {
    	if (file != null) {
            try {
                flushBuffer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    	this.buffer = null;
    	super.close();
    }

    /**
     * write a whole byte array as BLOB to the table
     * @param key  the primary key
     * @param b
     * @throws IOException
     */
    public synchronized void put(final byte[] key, final byte[] b) throws IOException {
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // we do not write records of length 0 into the BLOB
        if (b.length == 0) return;
        
        // first remove the old entry (removes from buffer and file)
        this.remove(key);
        
        // then look if we can use a free entry
        if (putToGap(key, b)) return; 
        
        // if there is not enough space in the buffer, flush all
        if (this.buffersize + b.length > buffermax) {
            // this is too big. Flush everything
            super.shrinkWithGapsAtEnd();
            flushBuffer();
            if (b.length > buffermax) {
                this.add(key, b);
            } else {
                this.buffer.put(new String(key), b);
                this.buffersize += b.length;
            }
            return;
        }
        
        // add entry to buffer
        this.buffer.put(new String(key), b);
        this.buffersize += b.length;
    }
    
    private boolean putToGap(final byte[] key, final byte[] b) throws IOException {
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // we do not write records of length 0 into the BLOB
        if (b.length == 0) return true;
        
        // then look if we can use a free entry
        if (this.free.size() == 0) return false;
        
        // find the largest entry
        long lseek = -1;
        int  lsize = 0;
        final int reclen = b.length + index.row().primaryKeyLength;
        Map.Entry<Long, Integer> entry;
        Iterator<Map.Entry<Long, Integer>> i = this.free.entrySet().iterator();
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue().intValue() == reclen) {
                // we found an entry that has exactly the size that we need!
                // we use that entry and stop looking for a larger entry
                file.seek(entry.getKey());
                final int reclenf = file.readInt();
                assert reclenf == reclen;
                file.write(key);
                file.write(b);
                
                // add the entry to the index
                this.index.putl(key, entry.getKey());
                
                // remove the entry from the free list
                i.remove();
                
                 //System.out.println("*** DEBUG BLOB: replaced-fit record at " + entry.seek + ", reclen=" + reclen + ", key=" + new String(key));
                
                // finished!
                return true;
            }
            // look for the biggest size
            if (entry.getValue() > lsize) {
                lseek = entry.getKey();
                lsize = entry.getValue();
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
            this.free.remove(lseek);
            
            // add a new free entry
            this.free.put(lseek + 4 + reclen, newfreereclen);
            
            //System.out.println("*** DEBUG BLOB: replaced-split record at " + lseek + ", reclen=" + reclen + ", new reclen=" + newfreereclen + ", key=" + new String(key));
            
            // finished!
            return true;
        }
        // could not insert to gap
        return false;
    }

    /**
     * remove a BLOB
     * @param key  the primary key
     * @throws IOException
     */
    public synchronized void remove(final byte[] key) throws IOException {
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // check the buffer
        byte[] blob = this.buffer.remove(new String(key));
        if (blob != null) {
            this.buffersize -= blob.length;
            return;
        }
        
        super.remove(key);
    }
    
    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public synchronized kelondroCloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        this.flushBuffer();
        return super.keys(up, rotating);
    }

    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    public synchronized kelondroCloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        this.flushBuffer();
        return super.keys(up, firstKey);
    }

    public long length() throws IOException {
        return super.length() + this.buffersize;
    }

    public static void heaptest() {
        final File f = new File("/Users/admin/blobtest.heap");
        try {
            //f.delete();
            final kelondroBLOBHeap heap = new kelondroBLOBHeap(f, 12, kelondroNaturalOrder.naturalOrder, 1024 * 512);
            heap.put("aaaaaaaaaaaa".getBytes(), "eins zwei drei".getBytes());
            heap.put("aaaaaaaaaaab".getBytes(), "vier fuenf sechs".getBytes());
            heap.put("aaaaaaaaaaac".getBytes(), "sieben acht neun".getBytes());
            heap.put("aaaaaaaaaaad".getBytes(), "zehn elf zwoelf".getBytes());
            // iterate over keys
            Iterator<byte[]> i = heap.index.keys(true, null);
            while (i.hasNext()) {
                System.out.println("key_a: " + new String(i.next()));
            }
            i = heap.keys(true, false);
            while (i.hasNext()) {
                System.out.println("key_b: " + new String(i.next()));
            }
            heap.remove("aaaaaaaaaaab".getBytes());
            heap.remove("aaaaaaaaaaac".getBytes());
            heap.put("aaaaaaaaaaaX".getBytes(), "WXYZ".getBytes());
            heap.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> map(String a, String b) {
        HashMap<String, String> m = new HashMap<String, String>();
        m.put(a, b);
        return m;
    }
    
    public static void maptest() {
        final File f = new File("/Users/admin/blobtest.heap");
        try {
            //f.delete();
            final kelondroMap heap = new kelondroMap(new kelondroBLOBHeap(f, 12, kelondroNaturalOrder.naturalOrder, 1024 * 512), 500);
            heap.put("aaaaaaaaaaaa", map("aaaaaaaaaaaa", "eins zwei drei"));
            heap.put("aaaaaaaaaaab", map("aaaaaaaaaaab", "vier fuenf sechs"));
            heap.put("aaaaaaaaaaac", map("aaaaaaaaaaac", "sieben acht neun"));
            heap.put("aaaaaaaaaaad", map("aaaaaaaaaaad", "zehn elf zwoelf"));
            heap.remove("aaaaaaaaaaab");
            heap.remove("aaaaaaaaaaac");
            heap.put("aaaaaaaaaaaX", map("aaaaaaaaaaad", "WXYZ"));
            heap.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(final String[] args) {
        //heaptest();
        maptest();
    }

}
