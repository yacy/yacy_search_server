// kelondroHeap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.04.2008 on http://yacy.net
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import de.anomic.kelondro.kelondroByteOrder;
import de.anomic.kelondro.kelondroBytesLongMap;

public final class kelondroHeap {

    private kelondroBytesLongMap index;
    private File heapFile;
    private kelondroByteOrder ordering;

    /**
     * create a heap file: a arbitrary number of BLOBs, indexed by an access key
     * The heap file will be opened at initialization time, indexed and closed again.
     * Heap files are only opened when BLOBs are read from it or new one are appended
     * @param heapFile
     * @param keylength
     * @param ordering
     * @throws IOException
     */
    public kelondroHeap(File heapFile, int keylength, kelondroByteOrder ordering) throws IOException {
        this.index = null;
        this.ordering = ordering;
        this.heapFile = heapFile;
        if (!(heapFile.exists())) throw new IOException("file " + heapFile + " does not exist");
        if (heapFile.length() >= (long) Integer.MAX_VALUE) throw new IOException("file " + heapFile + " too large, index can only be crated for files less than 2GB");
        
        this.index = new kelondroBytesLongMap(keylength, this.ordering, 0);
        DataInputStream is = null;
        String keystring;
        byte[] key = new byte[keylength];
        int reclen;
        long seek = 0, seek0;
        is = new DataInputStream(new BufferedInputStream(new FileInputStream(heapFile), 64*1024));

        // don't test available() here because this does not work for files > 2GB
        loop: while (true) {
            // remember seek position
            seek0 = seek;
        
            // read length of the following record without the length of the record size bytes
            try {
                reclen = is.readInt();
            } catch (IOException e) {
                break loop; // terminate loop
            }
            seek += 4L;
            
            // read key
            try {
                is.readFully(key);
            } catch (IOException e) {
                break loop; // terminate loop
            }
            keystring = new String(key);
            seek += (long) keystring.length();
        
            // skip content
            seek += (long) reclen;
            while (reclen > 0) reclen -= is.skip(reclen);
            
            // store access address to entry
            try {
                index.addl(key, seek0);
            } catch (IOException e) {
                e.printStackTrace();
                break loop;
            }
        }
        is.close();
    }
    
    /**
     * the number of BLOBs in the heap
     * @return the number of BLOBs in the heap
     */
    public int size() {
        return this.index.size();
    }

    /**
     * test if a key is in the heap file
     * @param key
     * @return true if the key exists, false othervise
     */
    public boolean has(String key) {
        assert index != null;
        assert index.row().primaryKeyLength == key.length();
        
        // check if the index contains the key
        try {
            return index.getl(key.getBytes()) >= 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * add a BLOB to the heap
     * @param key
     * @param blob
     * @throws IOException
     */
    public synchronized void add(String key, byte[] blob) throws IOException {
        add(key, blob, 0, blob.length);
    }
    
    /**
     * add a BLOB to the heap
     * @param key
     * @param blob
     * @throws IOException
     */
    public synchronized void add(String key, byte[] blob, int offset, int len) throws IOException {
        assert index.row().primaryKeyLength == key.length();
        if ((blob == null) || (blob.length == 0)) return;
        DataOutputStream os = null;
        try {
            os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(heapFile)));
        } catch (FileNotFoundException e) {
            throw new IOException(e.getMessage());
        }
        int pos = os.size();
        os.writeInt(len);
        os.write(key.getBytes());
        os.write(blob, offset, len);
        os.close();
        index.putl(key.getBytes(), pos);
    }
    
    /**
     * read a blob from the heap
     * @param key
     * @return
     * @throws IOException
     */
    public byte[] get(String key) throws IOException {
        assert index.row().primaryKeyLength == key.length();
        
        // check if the index contains the key
        long pos = index.getl(key.getBytes());
        if (pos < 0) return null;
        
        // access the file and read the container
        RandomAccessFile raf = new RandomAccessFile(heapFile, "r");
        int len = raf.readInt();
        byte[] record = new byte[len];
        
        raf.seek(pos + 4 + index.row().primaryKeyLength);
        raf.readFully(record);
        
        raf.close();
        return record;
    }

}
