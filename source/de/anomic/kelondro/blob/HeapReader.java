// kelondroBLOBHeapReader.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.12.2008 on http://yacy.net
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

package de.anomic.kelondro.blob;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import de.anomic.kelondro.index.LongHandleIndex;
import de.anomic.kelondro.io.CachedRandomAccess;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.RotateIterator;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.Log;

public class HeapReader {

    protected int                keylength;  // the length of the primary key
    protected LongHandleIndex       index;      // key/seek relation for used records
    protected Gap                free;       // set of {seek, size} pairs denoting space and position of free records
    protected final File         heapFile;   // the file of the heap
    protected final ByteOrder    ordering;   // the ordering on keys
    protected CachedRandomAccess file;       // a random access to the file
    
    public HeapReader(
            final File heapFile,
            final int keylength,
            final ByteOrder ordering) throws IOException {
        this.ordering = ordering;
        this.heapFile = heapFile;
        this.keylength = keylength;
        this.index = null; // will be created as result of initialization process
        this.free = null; // will be initialized later depending on existing idx/gap file
        this.file = new CachedRandomAccess(heapFile);
        
        // read or initialize the index
        if (initIndexReadDump(heapFile)) {
            // verify that everything worked just fine
            // pick some elements of the index
            Iterator<byte[]> i = this.index.keys(true, null);
            int c = 3;
            byte[] b, b1 = new byte[index.row().primaryKeyLength];
            long pos;
            boolean ok = true;
            while (i.hasNext() && c-- > 0) {
                b = i.next();
                pos = this.index.get(b);
                file.seek(pos + 4);
                file.readFully(b1, 0, b1.length);
                if (this.ordering.compare(b, b1) != 0) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                Log.logWarning("kelondroBLOBHeap", "verification of idx file for " + heapFile.toString() + " failed, re-building index");
                initIndexReadFromHeap();
            } else {
                Log.logInfo("kelondroBLOBHeap", "using a dump of the index of " + heapFile.toString() + ".");
            }
        } else {
            // if we did not have a dump, create a new index
            initIndexReadFromHeap();
        }
    }
    
    private boolean initIndexReadDump(File f) {
        // look for an index dump and read it if it exist
        // if this is successfull, return true; otherwise false
        File fif = HeapWriter.fingerprintIndexFile(f);
        File fgf = HeapWriter.fingerprintGapFile(f);
        if (!fif.exists() || !fgf.exists()) {
            HeapWriter.deleteAllFingerprints(f);
            return false;
        }
        
        // there is an index and a gap file:
        // read the index file:
        try {
            this.index = new LongHandleIndex(this.keylength, this.ordering, fif, 1000000);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        // an index file is a one-time throw-away object, so just delete it now
        fif.delete();
        
        // read the gap file:
        try {
            this.free = new Gap(fgf);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        // same with gap file
        fgf.delete();
        
        // everything is fine now
        return this.index.size() > 0;
    }
    
    private void initIndexReadFromHeap() throws IOException {
        // this initializes the this.index object by reading positions from the heap file

        this.free = new Gap();
        LongHandleIndex.initDataConsumer indexready = LongHandleIndex.asynchronusInitializer(keylength, this.ordering, 0, Math.max(10, (int) (Runtime.getRuntime().freeMemory() / (10 * 1024 * 1024))), 100000);
        byte[] key = new byte[keylength];
        int reclen;
        long seek = 0;
        loop: while (true) { // don't test available() here because this does not work for files > 2GB
            
            try {
                // go to seek position
                file.seek(seek);
            
                // read length of the following record without the length of the record size bytes
                reclen = file.readInt();
                //assert reclen > 0 : " reclen == 0 at seek pos " + seek;
                if (reclen == 0) {
                    // very bad file inconsistency
                    Log.logSevere("kelondroBLOBHeap", "reclen == 0 at seek pos " + seek + " in file " + heapFile);
                    this.file.setLength(seek); // delete everything else at the remaining of the file :-(
                    break loop;
                }
                
                // read key
                file.readFully(key, 0, key.length);
                
            } catch (final IOException e) {
                // EOF reached
                break loop; // terminate loop
            }
            
            // check if this record is empty
            if (key == null || key[0] == 0) {
                // it is an empty record, store to free list
                if (reclen > 0) free.put(seek, reclen);
            } else {
                if (this.ordering.wellformed(key)) {
                    indexready.consume(key, seek);
                    key = new byte[keylength];
                } else {
                    Log.logWarning("kelondroBLOBHeap", "BLOB " + heapFile.getName() + ": skiped not wellformed key " + new String(key) + " at seek pos " + seek);
                }
            }            
            // new seek position
            seek += 4L + reclen;
        }
        indexready.finish();
        
        // finish the index generation
        try {
            this.index = indexready.result();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
    
    public String name() {
        return this.heapFile.getName();
    }
    
    /**
     * the number of BLOBs in the heap
     * @return the number of BLOBs in the heap
     */
    public synchronized int size() {
        return this.index.size();
    }
    
    /**
     * test if a key is in the heap file. This does not need any IO, because it uses only the ram index
     * @param key
     * @return true if the key exists, false otherwise
     */
    public synchronized boolean has(final byte[] key) {
        assert index != null;
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // check if the file index contains the key
        return index.get(key) >= 0;
    }

    public ByteOrder ordering() {
        return this.ordering;
    }
    
    /**
     * read a blob from the heap
     * @param key
     * @return
     * @throws IOException
     */
    public synchronized byte[] get(final byte[] key) throws IOException {
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
       
        // check if the index contains the key
        final long pos = index.get(key);
        if (pos < 0) return null;
        
        // access the file and read the container
        file.seek(pos);
        final int len = file.readInt() - index.row().primaryKeyLength;
        if (MemoryControl.available() < len) {
            if (!MemoryControl.request(len, true)) return null; // not enough memory available for this blob
        }
        
        // read the key
        final byte[] keyf = new byte[index.row().primaryKeyLength];
        file.readFully(keyf, 0, keyf.length);
        if (this.ordering.compare(key, keyf) != 0) {
            // verification of the indexed access failed. we must re-read the index
            Log.logWarning("kelondroBLOBHeap", "verification indexed access for " + heapFile.toString() + " failed, re-building index");
            // this is a severe operation, it should never happen.
            // but if the process ends in this state, it would completely fail
            // if the index is not rebuild now at once
            initIndexReadFromHeap();
        }
        
        // read the blob
        byte[] blob = new byte[len];
        file.readFully(blob, 0, blob.length);
        
        return blob;
    }

    /**
     * retrieve the size of the BLOB
     * @param key
     * @return the size of the BLOB or -1 if the BLOB does not exist
     * @throws IOException
     */
    public synchronized long length(byte[] key) throws IOException {
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        
        // check if the index contains the key
        final long pos = index.get(key);
        if (pos < 0) return -1;
        
        // access the file and read the size of the container
        file.seek(pos);
        return file.readInt() - index.row().primaryKeyLength;
    }
    
    /**
     * close the BLOB table
     */
    public synchronized void close() {
        if (file != null) try {
            file.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        file = null;
        
        free.clear();
        free = null;
        index.close();
        index = null;
    }
    
    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    public int keylength() {
        return this.index.row().primaryKeyLength;
    }
    
    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        return new RotateIterator<byte[]>(this.index.keys(up, null), null, this.index.size());
    }

    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        return this.index.keys(up, firstKey);
    }

    public long length() throws IOException {
        return this.heapFile.length();
    }
    
    /**
     * static iterator of entries in BLOBHeap files:
     * this is used to import heap dumps into a write-enabled index heap
     */
    public static class entries implements
        CloneableIterator<Map.Entry<String, byte[]>>,
        Iterator<Map.Entry<String, byte[]>>,
        Iterable<Map.Entry<String, byte[]>> {
        
        DataInputStream is;
        int keylen;
        private File blobFile;
        Map.Entry<String, byte[]> nextEntry;
        
        public entries(final File blobFile, final int keylen) throws IOException {
            if (!(blobFile.exists())) throw new IOException("file " + blobFile + " does not exist");
            this.is = new DataInputStream(new BufferedInputStream(new FileInputStream(blobFile), 1024*1024));
            this.keylen = keylen;
            this.blobFile = blobFile;
            this.nextEntry = next0();
        }

        public CloneableIterator<Entry<String, byte[]>> clone(Object modifier) {
            try {
                return new entries(blobFile, keylen);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        
        public boolean hasNext() {
            return this.nextEntry != null;
        }

        private Map.Entry<String, byte[]> next0() {
            try {
                while (true) {
                    int len = is.readInt();
                    byte[] key = new byte[this.keylen];
                    if (is.read(key) < key.length) return null;
                    byte[] payload = new byte[len - this.keylen];
                    if (is.read(payload) < payload.length) return null;
                    if (key[0] == 0) continue; // this is an empty gap
                    return new entry(new String(key), payload);
                }
            } catch (final IOException e) {
                return null;
            }
        }
        
        public Map.Entry<String, byte[]> next() {
            final Map.Entry<String, byte[]> n = this.nextEntry;
            this.nextEntry = next0();
            return n;
        }

        public void remove() {
            throw new UnsupportedOperationException("blobs cannot be altered during read-only iteration");
        }

        public Iterator<Map.Entry<String, byte[]>> iterator() {
            return this;
        }
        
        public void close() {
            if (is != null) try { is.close(); } catch (final IOException e) {}
            is = null;
        }
        
        protected void finalize() {
            this.close();
        }
    }

    public static class entry implements Map.Entry<String, byte[]> {
        private String s;
        private byte[] b;
        
        public entry(final String s, final byte[] b) {
            this.s = s;
            this.b = b;
        }
    
        public String getKey() {
            return s;
        }

        public byte[] getValue() {
            return b;
        }

        public byte[] setValue(byte[] value) {
            byte[] b1 = b;
            b = value;
            return b1;
        }
    }
    
}
