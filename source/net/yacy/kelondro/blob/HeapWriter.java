// HeapWriter.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.12.2008 on http://yacy.net
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

package net.yacy.kelondro.blob;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.storage.HandleMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.RowHandleMap;
import net.yacy.kelondro.util.FileUtils;


public final class HeapWriter {

    private final static ConcurrentLog log = new ConcurrentLog("HeapWriter");
    public final static byte[] ZERO = new byte[]{0};

    private final int          keylength;     // the length of the primary key
    private HandleMap          index;         // key/seek relation for used records
    private final File         heapFileTMP;   // the temporary file of the heap during writing
    private final File         heapFileREADY; // the final file of the heap when the file is closed
    private DataOutputStream   os;            // the output stream where the BLOB is written
    private long               seek;          // the current write position
    //private HashSet<String>    doublecheck;// only for testing

    /*
     * This class implements a BLOB management based on a sequence of records
     * The data structure is:
     * file   :== record*
     * record :== reclen key blob
     * reclen :== <4 byte integer == length of key and blob>
     * key    :== <bytes as defined with keylen, if first byte is zero then record is empty>
     * blob   :== <bytes of length reclen - keylen>
     * that means that each record has the size reclen+4
     *
     * Because the blob sizes are stored with integers, one entry may not exceed 2GB
     *
     * With this class a BLOB file can only be written.
     * To read them, use a kelondroBLOBHeapReader.
     * A BLOBHeap can be also read and write in random access mode with kelondroBLOBHeap.
     */

    /**
     * create a heap file: a arbitrary number of BLOBs, indexed by an access key
     * The heap file will be indexed upon initialization.
     * @param temporaryHeapFile
     * @param readyHeapFile
     * @param keylength
     * @param ordering
     * @throws IOException
     */
    public HeapWriter(final File temporaryHeapFile, final File readyHeapFile, final int keylength, final ByteOrder ordering, int outBuffer) throws IOException {
        this.heapFileTMP = temporaryHeapFile;
        this.heapFileREADY = readyHeapFile;
        this.keylength = keylength;
        this.index = new RowHandleMap(keylength, ordering, 8, 100000, readyHeapFile.getAbsolutePath());
        try {
            this.os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporaryHeapFile), outBuffer));
        } catch (final OutOfMemoryError e) {
            // try this again without buffer
            this.os = new DataOutputStream(new FileOutputStream(temporaryHeapFile));
        }
        this.seek = 0;
    }

    /**
     * add a BLOB to the heap: this adds the blob always to the end of the file
     * newly added heap entries must have keys that have not been added before
     * @param key
     * @param blob
     * @throws IOException
     * @throws SpaceExceededException
     * @throws SpaceExceededException
     */
    public synchronized void add(byte[] key, final byte[] blob) throws IOException, SpaceExceededException {
        assert blob.length > 0;
        key = HeapReader.normalizeKey(key, this.keylength);
        assert key.length == this.keylength : "key.length == " + key.length + ", this.keylength = " + this.keylength; // after normalizing they should be equal in length
        assert this.index.get(key) < 0 : "index.get(key) = " + this.index.get(key) + ", index.size() = " + this.index.size() + ", file.length() = " + this.heapFileTMP.length() +  ", key = " + UTF8.String(key); // must not occur before
        if ((blob == null) || (blob.length == 0)) return;
        this.index.putUnique(key, this.seek);
        int chunkl = this.keylength + blob.length;
        this.os.writeInt(chunkl);
        this.os.write(key);
        this.os.write(blob);
        this.seek += chunkl + 4;
        //os.flush(); // necessary? may cause bad IO performance :-(
    }

    /**
     * close the BLOB table
     * @throws
     */
    public synchronized void close(boolean writeIDX) throws IOException {
        // close the file
        this.os.flush();
        this.os.close();
        this.os = null;

        // rename the file into final name
        if (this.heapFileREADY.exists()) FileUtils.deletedelete(this.heapFileREADY);
        boolean renameok = this.heapFileTMP.renameTo(this.heapFileREADY);
        if (!renameok) throw new IOException("cannot rename " + this.heapFileTMP + " to " + this.heapFileREADY);
        if (!this.heapFileREADY.exists()) throw new IOException("renaming of " + this.heapFileREADY.toString() + " failed: files still exists");
        if (this.heapFileTMP.exists()) throw new IOException("renaming to " + this.heapFileTMP.toString() + " failed: file does not exist");

        // generate index and gap files
        if (writeIDX && this.index.size() > 3) {
            // now we can create a dump of the index and the gap information
            // to speed up the next start
            long start = System.currentTimeMillis();
            String fingerprint = HeapReader.fingerprintFileHash(this.heapFileREADY);
            if (fingerprint == null) {
                log.severe("cannot write a dump for " + this.heapFileREADY.getName()+ ": fingerprint is null");
            } else {
                new Gap().dump(fingerprintGapFile(this.heapFileREADY, fingerprint));
                this.index.dump(fingerprintIndexFile(this.heapFileREADY, fingerprint));
                log.info("wrote a dump for the " + this.index.size() +  " index entries of " + this.heapFileREADY.getName()+ " in " + (System.currentTimeMillis() - start) + " milliseconds.");
            }
            this.index.close();
            this.index = null;
        } else {
            // this is small.. just free resources, do not write index
            this.index.close();
            this.index = null;
        }
    }

    public static void delete(File f) {
        File p = f.getParentFile();
        String n = f.getName() + ".";
        String[] l = p.list();
        FileUtils.deletedelete(f);
        for (String s: l) {
            if (s.startsWith(n) &&
                (s.endsWith(".idx") || s.endsWith(".gap")))
               FileUtils.deletedelete(new File(p, s));
        }
    }

    protected static File fingerprintIndexFile(File f, String fingerprint) {
        assert f != null;
        return new File(f.getParentFile(), f.getName() + "." + fingerprint + ".idx");
    }

    protected static File fingerprintGapFile(File f, String fingerprint) {
        assert f != null;
        return new File(f.getParentFile(), f.getName() + "." + fingerprint + ".gap");
    }
}
