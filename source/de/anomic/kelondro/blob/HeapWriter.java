// kelondroBLOBHeapWriter.java
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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.anomic.kelondro.index.LongHandleIndex;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.Digest;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.Log;

public final class HeapWriter  {

    private int                keylength;     // the length of the primary key
    private LongHandleIndex    index;         // key/seek relation for used records
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
        this.index = new LongHandleIndex(keylength, ordering, 10, 100000);
        this.os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporaryHeapFile), outBuffer));
        //this.doublecheck = new HashSet<String>();
        this.seek = 0;
    }

    /**
     * add a BLOB to the heap: this adds the blob always to the end of the file
     * newly added heap entries must have keys that have not been added before
     * @param key
     * @param blob
     * @throws IOException
     */
    public synchronized void add(final byte[] key, final byte[] blob) throws IOException {
        //System.out.println("HeapWriter.add: " + new String(key));
        assert blob.length > 0;
        assert key.length == this.keylength;
        assert index.row().primaryKeyLength == key.length : index.row().primaryKeyLength + "!=" + key.length;
        assert index.get(key) < 0 : "index.get(key) = " + index.get(key) + ", index.size() = " + index.size() + ", file.length() = " + this.heapFileTMP.length() +  ", key = " + new String(key); // must not occur before
        if ((blob == null) || (blob.length == 0)) return;
        int chunkl = key.length + blob.length;
        os.writeInt(chunkl);
        os.write(key);
        os.write(blob);
        index.putUnique(key, seek);
        //assert (this.doublecheck.add(new String(key))) : "doublecheck failed for " + new String(key);
        this.seek += chunkl + 4;
    }
    
    protected static File fingerprintIndexFile(File f, String fingerprint) {
        assert f != null;
        return new File(f.getParentFile(), f.getName() + "." + fingerprint + ".idx");
    }
    
    protected static File fingerprintGapFile(File f, String fingerprint) {
        assert f != null;
        return new File(f.getParentFile(), f.getName() + "." + fingerprint + ".gap");
    }
    
    protected static String fingerprintFileHash(File f) {
        assert f != null;
        assert f.exists() : "file = " + f.toString();
        String fp = Digest.fastFingerprintB64(f, false);
        assert fp != null : "file = " + f.toString();
        if (fp == null) return null;
        return fp.substring(0, 12);
    }
    
    public static void deleteAllFingerprints(File f) {
        File d = f.getParentFile();
        String n = f.getName();
        String[] l = d.list();
        for (int i = 0; i < l.length; i++) {
            if (l[i].startsWith(n) && (l[i].endsWith(".idx") || l[i].endsWith(".gap") || l[i].endsWith(".idx.gz") || l[i].endsWith(".gap.gz"))) FileUtils.deletedelete(new File(d, l[i]));
        }
    }
    
    /**
     * close the BLOB table
     * @throws  
     */
    public synchronized void close(boolean writeIDX) throws IOException {
        // close the file
        os.flush();
        os.close();
        os = null;
        
        // rename the file into final name
        if (this.heapFileREADY.exists()) FileUtils.deletedelete(this.heapFileREADY);
        boolean renameok = this.heapFileTMP.renameTo(this.heapFileREADY);
        if (!renameok) throw new IOException("cannot rename " + this.heapFileTMP + " to " + this.heapFileREADY);
        if (!this.heapFileREADY.exists()) throw new IOException("renaming of " + this.heapFileREADY.toString() + " failed: files still exists");
        if (this.heapFileTMP.exists()) throw new IOException("renaming to " + this.heapFileTMP.toString() + " failed: file does not exist");
        
        // generate index and gap files
        if (writeIDX && index.size() > 3) {
            // now we can create a dump of the index and the gap information
            // to speed up the next start
            try {
                long start = System.currentTimeMillis();
                String fingerprint = HeapWriter.fingerprintFileHash(this.heapFileREADY);
                if (fingerprint == null) {
                    Log.logSevere("kelondroBLOBHeapWriter", "cannot write a dump for " + heapFileREADY.getName()+ ": fingerprint is null");
                } else {
                    new Gap().dump(fingerprintGapFile(this.heapFileREADY, fingerprint));
                    index.dump(fingerprintIndexFile(this.heapFileREADY, fingerprint));
                    Log.logInfo("kelondroBLOBHeapWriter", "wrote a dump for the " + this.index.size() +  " index entries of " + heapFileREADY.getName()+ " in " + (System.currentTimeMillis() - start) + " milliseconds.");
                }
                index.close();
                index = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // this is small.. just free resources, do not write index
            index.close();
            index = null;
        }
    }

}
