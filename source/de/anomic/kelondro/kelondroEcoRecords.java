// kelondroEcoRecords.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.07.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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
import java.io.IOException;
import java.util.Iterator;
import java.util.TreeMap;

public class kelondroEcoRecords extends kelondroAbstractRecords {

    // static supervision objects: recognize and coordinate all activites
    private static TreeMap recordTracker = new TreeMap(); // a String/filename - kelondroTray mapping
    
    public kelondroEcoRecords(
            File file,
            short ohbytec, short ohhandlec,
            kelondroRow rowdef, int FHandles, int txtProps, int txtPropWidth) throws IOException {
        super(file, true, ohbytec, ohhandlec, rowdef, FHandles, txtProps, txtPropWidth);
        recordTracker.put(this.filename, this);
    }
    
    public kelondroEcoRecords(
            kelondroRA ra, String filename,
            short ohbytec, short ohhandlec,
            kelondroRow rowdef, int FHandles, int txtProps, int txtPropWidth,
            boolean exitOnFail) {
        super(ra, filename, true, ohbytec, ohhandlec, rowdef, FHandles, txtProps, txtPropWidth, exitOnFail);
        recordTracker.put(this.filename, this);
    }
    
    public kelondroEcoRecords(
            kelondroRA ra, String filename) throws IOException{
        super(ra, filename, true);
        recordTracker.put(this.filename, this);
    }

    public static final Iterator filenames() {
        // iterates string objects; all file names from record tracker
        return recordTracker.keySet().iterator();
    }

    protected synchronized void deleteNode(kelondroHandle handle) throws IOException {
        super.deleteNode(handle);
    }
    
    public synchronized void close() {
        if (recordTracker.get(this.filename) != null) {
            theLogger.severe("close(): file '" + this.filename + "' was tracked with record tracker, but it should not.");
        }
        super.close();
    }
    
    public kelondroNode newNode(kelondroHandle handle, byte[] bulk, int offset) throws IOException {
        return new EcoNode(handle, bulk, offset);
    }

    public final class EcoNode implements kelondroNode {
        private kelondroHandle handle = null; // index of the entry, by default NUL means undefined
        private byte[] ohChunk = null; // contains overhead values
        private byte[] bodyChunk = null; // contains all row values
        private boolean ohChanged = false;
        private boolean bodyChanged = false;

        public EcoNode(byte[] rowinstance) throws IOException {
            // this initializer is used to create nodes from bulk-read byte arrays
            assert ((rowinstance == null) || (rowinstance.length == ROW.objectsize)) : "bulkchunk.length = " + rowinstance.length + ", ROW.width(0) = " + ROW.width(0);
            this.handle = new kelondroHandle(USAGE.allocatePayload(rowinstance));
            
            // create chunks
            this.ohChunk = new byte[overhead];
            for (int i = this.ohChunk.length - 1; i >= 0; i--) this.ohChunk[i] = (byte) 0xff;
            if (rowinstance == null) {
                this.bodyChunk = new byte[ROW.objectsize()];
                for (int i = this.bodyChunk.length - 1; i >= 0; i--) this.bodyChunk[i] = (byte) 0xff;
            } else {
                this.bodyChunk = rowinstance;
            }
            
            // mark chunks as not changed
            this.ohChanged = false;
            this.bodyChanged = false;
        }
        
        public EcoNode(kelondroHandle handle, byte[] bulkchunk, int offset) throws IOException {
            // this initializer is used to create nodes from bulk-read byte arrays
            // if write is true, then the chunk in bulkchunk is written to the file
            // othervise it is considered equal to what is stored in the file
            // (that is ensured during pre-loaded enumeration)
            this.handle = handle;
            boolean changed;
            if (handle.index >= USAGE.allCount()) {
                // this causes only a write action if we create a node beyond the end of the file
                USAGE.allocateRecord(handle.index, bulkchunk, offset);
                changed = false; // we have already wrote the record, so it is considered as unchanged
            } else {
                changed = true;
            }
            assert ((bulkchunk == null) || (bulkchunk.length - offset >= recordsize)) : "bulkchunk.length = " + bulkchunk.length + ", offset = " + offset + ", recordsize = " + recordsize;
            
            if ((offset == 0) && (overhead == 0) && ((bulkchunk == null) || (bulkchunk.length == ROW.objectsize()))) {
                this.ohChunk = new byte[0];
                if (bulkchunk == null) {
                    this.bodyChunk = new byte[ROW.objectsize()];
                } else {
                    this.bodyChunk = bulkchunk;
                }
            } else {
                // create empty chunks
                this.ohChunk = new byte[overhead];
                this.bodyChunk = new byte[ROW.objectsize()];
                
                // write content to chunks
                if (bulkchunk != null) {
                    System.arraycopy(bulkchunk, offset, this.ohChunk, 0, overhead);
                    System.arraycopy(bulkchunk, offset + overhead, this.bodyChunk, 0, ROW.objectsize());
                }
            }
            
            // mark chunks as changed
            this.ohChanged = changed;
            this.bodyChanged = changed;
        }
        
        public EcoNode(kelondroHandle handle) throws IOException {
            // this creates an entry with an pre-reserved entry position.
            // values can be written using the setValues() method,
            // but we expect that values are already there in the file.
            assert (handle != null): "node handle is null";
            assert (handle.index >= 0): "node handle too low: " + handle.index;
            //assert (handle.index < USAGE.allCount()) : "node handle too high: " + handle.index + ", USEDC=" + USAGE.USEDC + ", FREEC=" + USAGE.FREEC;
            
            // the parentNode can be given if an auto-fix in the following case is wanted
            if (handle == null) throw new kelondroException(filename, "INTERNAL ERROR: node handle is null.");
            if (handle.index >= USAGE.allCount()) {
                throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. No auto-fix node was submitted. This is a serious failure.");
            }

            // use given handle
            this.handle = new kelondroHandle(handle.index);

            // read complete record
            byte[] bulkchunk = new byte[recordsize];
            entryFile.readFully(seekpos(this.handle), bulkchunk, 0, recordsize);

            if ((overhead == 0) && (bulkchunk.length == ROW.objectsize())) {
                this.ohChunk = new byte[0];
                this.bodyChunk = bulkchunk;
            } else {
                // create empty chunks
                this.ohChunk = new byte[overhead];
                this.bodyChunk = new byte[ROW.objectsize()];

                // write content to chunks
                if (bulkchunk != null) {
                    System.arraycopy(bulkchunk, 0, this.ohChunk, 0, overhead);
                    System.arraycopy(bulkchunk, 0 + overhead, this.bodyChunk, 0, ROW.objectsize());
                }
            }
            
            // mark chunks as not changed
            this.ohChanged = false;
            this.bodyChanged = false;
        }
        
        public kelondroHandle handle() {
            // if this entry has an index, return it
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "the entry has no index assigned");
            return this.handle;
        }

        public void setOHByte(int i, byte b) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("setOHByte: wrong index " + i);
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "setOHByte: no handle assigned");
            this.ohChunk[i] = b;
            this.ohChanged = true;
        }
        
        public void setOHHandle(int i, kelondroHandle otherhandle) {
            assert (i < OHHANDLEC): "setOHHandle: wrong array size " + i;
            assert (this.handle.index != kelondroHandle.NUL): "setOHHandle: no handle assigned ind file" + filename;
            if (otherhandle == null) {
                NUL2bytes(this.ohChunk, OHBYTEC + 4 * i);
            } else {
                if (otherhandle.index >= USAGE.allCount()) throw new kelondroException(filename, "INTERNAL ERROR, setOHHandles: handle " + i + " exceeds file size (" + handle.index + " >= " + USAGE.allCount() + ")");
                int2bytes(otherhandle.index, this.ohChunk, OHBYTEC + 4 * i);
            }
            this.ohChanged = true;
        }
        
        public byte getOHByte(int i) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("getOHByte: wrong index " + i);
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "Cannot load OH values");
            return this.ohChunk[i];
        }

        public kelondroHandle getOHHandle(int i) {
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "Cannot load OH values");
            assert (i < OHHANDLEC): "handle index out of bounds: " + i + " in file " + filename;
            int h = bytes2int(this.ohChunk, OHBYTEC + 4 * i);
            return (h == kelondroHandle.NUL) ? null : new kelondroHandle(h);
        }

        public synchronized void setValueRow(byte[] row) throws IOException {
            // if the index is defined, then write values directly to the file, else only to the object
            if ((row != null) && (row.length != ROW.objectsize())) throw new IOException("setValueRow with wrong (" + row.length + ") row length instead correct: " + ROW.objectsize());
            
            // set values
            if (this.handle.index != kelondroHandle.NUL) {
                this.bodyChunk = row;
                this.bodyChanged = true;
            }
        }

        public synchronized boolean valid() {
            // returns true if the key starts with non-zero byte
            // this may help to detect deleted entries
            return (this.bodyChunk[0] != 0) && ((this.bodyChunk[0] != -128) || (this.bodyChunk[1] != 0));
        }

        public synchronized byte[] getKey() {
            // read key
            return trimCopy(this.bodyChunk, 0, ROW.width(0));
        }

        public synchronized byte[] getValueRow() throws IOException {
            
            if (this.bodyChunk == null) {
                // load all values from the database file
                this.bodyChunk = new byte[ROW.objectsize()];
                // read values
                entryFile.readFully(seekpos(this.handle) + (long) overhead, this.bodyChunk, 0, this.bodyChunk.length);
            }

            return this.bodyChunk;
        }

        public synchronized void commit() throws IOException {
            // this must be called after all write operations to the node are finished

            // place the data to the file

            boolean doCommit = this.ohChanged || this.bodyChanged;
            
            // save head
            synchronized (entryFile) {
            if (this.ohChanged) {
                //System.out.println("WRITEH(" + filename + ", " + seekpos(this.handle) + ", " + this.headChunk.length + ")");
                assert (ohChunk == null) || (ohChunk.length == headchunksize);
                entryFile.write(seekpos(this.handle), (this.ohChunk == null) ? new byte[overhead] : this.ohChunk);
                this.ohChanged = false;
            }

            // save tail
            if ((this.bodyChunk != null) && (this.bodyChanged)) {
                //System.out.println("WRITET(" + filename + ", " + (seekpos(this.handle) + headchunksize) + ", " + this.tailChunk.length + ")");
                assert (this.bodyChunk == null) || (this.bodyChunk.length == ROW.objectsize());
                entryFile.write(seekpos(this.handle) + overhead, (this.bodyChunk == null) ? new byte[ROW.objectsize()] : this.bodyChunk);
                this.bodyChanged = false;
            }
            
            if (doCommit) entryFile.commit();
            }
        }
        
    }
    
}
