// kelondroRecords.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2003, 2004
// last major change: 11.01.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
  The Kelondro Database

  Kelondro Records are the basis for the tree structures of the needed database
  Therefore, the name is inspired by the creek words 'fakelo'=file and 'dentro'=tree.
  We omitted the 'fa' and 'de' in 'fakelodentro',
  making it sounding better by replacing the 't' by 'd'.
  The Kelondro Records are also used for non-tree structures like the KelondroStack.
  The purpose of these structures are file-based storage of lists/stacks and
  indexeable information.

  We use the following structure:

  Handle : handles are simply the abstraction of integer indexe's.
           We don't want to mix up integer values as node pointers
	   with handles. This makes node indexes more robust against
	   manipulation that is too far away of thinking about records
	   Simply think that handles are like cardinals that are used
	   like pointers.
  Node   : The emelentary storage piece for one information fragment.
           All Records, which are essentially files with a definitive
	   structure, are constructed of a list of Node elements, but
	   the Node Handles that are carried within the Node overhead
	   prefix construct a specific structure, like trees or stacks.
*/


package de.anomic.kelondro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;

public class kelondroRecords {

    // constants
    protected final static int     NUL = Integer.MIN_VALUE; // the meta value for the kelondroRecords' NUL abstraction
    public    final static boolean useWriteBuffer = false;  // currently only used during development/testing
    
    // memory calculation
    private   static final int element_in_cache = 4; // for kelondroCollectionObjectMap: 4; for HashMap: 52
    
    // caching flags
    public    static final int CP_NONE   = -1; // cache priority none; entry shall not be cached
    public    static final int CP_LOW    =  0; // cache priority low; entry may be cached
    public    static final int CP_MEDIUM =  1; // cache priority medium; entry shall be cached
    public    static final int CP_HIGH   =  2; // cache priority high; entry must be cached
    
    // static seek pointers
    private   static int  LEN_DESCR      = 60;
    private   static long POS_MAGIC      = 0;                     // 1 byte, byte: file type magic
    private   static long POS_BUSY       = POS_MAGIC      + 1;    // 1 byte, byte: marker for synchronization
    private   static long POS_PORT       = POS_BUSY       + 1;    // 2 bytes, short: hint for remote db access
    private   static long POS_DESCR      = POS_PORT       + 2;    // 60 bytes, string: any description string
    private   static long POS_COLUMNS    = POS_DESCR      + LEN_DESCR; // 2 bytes, short: number of columns in one entry
    private   static long POS_OHBYTEC    = POS_COLUMNS    + 2;    // 2 bytes, number of extra bytes on each Node
    private   static long POS_OHHANDLEC  = POS_OHBYTEC    + 2;    // 2 bytes, number of Handles on each Node
    private   static long POS_USEDC      = POS_OHHANDLEC  + 2;    // 4 bytes, int: used counter
    private   static long POS_FREEC      = POS_USEDC      + 4;    // 4 bytes, int: free counter
    private   static long POS_FREEH      = POS_FREEC      + 4;    // 4 bytes, int: free pointer (to free chain start)
    private   static long POS_MD5PW      = POS_FREEH      + 4;    // 16 bytes, string (encrypted password to this file)
    private   static long POS_ENCRYPTION = POS_MD5PW      + 16;   // 16 bytes, string (method description)
    private   static long POS_OFFSET     = POS_ENCRYPTION + 16;   // 8 bytes, long (seek position of first record)
    private   static long POS_INTPROPC   = POS_OFFSET     + 8;    // 4 bytes, int: number of INTPROP elements
    private   static long POS_TXTPROPC   = POS_INTPROPC   + 4;    // 4 bytes, int: number of TXTPROP elements
    private   static long POS_TXTPROPW   = POS_TXTPROPC   + 4;    // 4 bytes, int: width of TXTPROP elements
    private   static long POS_COLWIDTHS  = POS_TXTPROPW   + 4;    // array of 4 bytes, int[]: sizes of columns
    // after this configuration field comes:
    // POS_HANDLES: INTPROPC * 4 bytes  : INTPROPC Integer properties, randomly accessible
    // POS_TXTPROPS: TXTPROPC * TXTPROPW : an array of TXTPROPC byte arrays of width TXTPROPW that can hold any string
    // POS_NODES : (USEDC + FREEC) * (overhead + sum(all: COLWIDTHS)) : Node Objects

    // static supervision objects: recognize and coordinate all activites
    private static TreeMap recordTracker = new TreeMap(); // a String/filename - kelondroRecords mapping
    private static long memStopGrow = 4000000; // a limit for the node cache to stop growing if less than this memory amount is available
    private static long memStartShrink = 2000000; // a limit for the node cache to start with shrinking if less than this memory amount is available
    
    // values that are only present at run-time
    protected String           filename;     // the database's file name
    private   kelondroIOChunks entryFile;    // the database file
    private   int              overhead;     // OHBYTEC + 4 * OHHANDLEC = size of additional control bytes
    private   int              headchunksize;// overheadsize + key element column size
    private   int              tailchunksize;// sum(all: COLWIDTHS) minus the size of the key element colum
    private   int              recordsize;   // (overhead + sum(all: COLWIDTHS)) = the overall size of a record
    private   byte[]           spaceChunk;   // a chunk of data that is used to reserve space within the file
    
    // dynamic run-time seek pointers
    private   long POS_HANDLES  = 0; // starts after end of POS_COLWIDHS which is POS_COLWIDTHS + COLWIDTHS.length * 4
    private   long POS_TXTPROPS = 0; // starts after end of POS_HANDLES which is POS_HANDLES + HANDLES.length * 4
    private   long POS_NODES    = 0; // starts after end of POS_TXTPROPS which is POS_TXTPROPS + TXTPROPS.length * TXTPROPW

    // dynamic variables that are back-ups of stored values in file; read/defined on instantiation
    private   usageControl      USAGE;       // counter for used and re-use records and pointer to free-list
    private   short             OHBYTEC;     // number of extra bytes in each node
    private   short             OHHANDLEC;   // number of handles in each node
    private   kelondroRow       ROW;         // array with widths of columns
    private   Handle            HANDLES[];   // array with handles
    private   byte[]            TXTPROPS[];  // array with text properties
    private   int               TXTPROPW;    // size of a single TXTPROPS element

    // caching buffer
    private   kelondroIntBytesMap   cacheHeaders; // the cache; holds overhead values and key element
    private   int readHit, readMiss, writeUnique, writeDouble, cacheDelete, cacheFlush;
    
    // optional logger
    protected Logger theLogger = Logger.getLogger("KELONDRO"); // default logger
    
    // tracking of file cration
    protected boolean fileExisted;
    
    // Random. This is used to shift flush-times of write-buffers to differrent time
    private static Random random = new Random(System.currentTimeMillis());

    // check for debug mode
    public static boolean debugmode = false;
    static {
        assert debugmode = true;
    }
    
    protected final class usageControl {
        private int    USEDC; // counter of used elements
        private int    FREEC; // counter of free elements in list of free Nodes
        private Handle FREEH; // pointer to first element in list of free Nodes, empty = NUL

        public usageControl(boolean init) throws IOException {
            if (init) {
                this.USEDC = 0;
                this.FREEC = 0;
                this.FREEH = new Handle(NUL);
            } else {
                readfree();
                readused();
                try {
                    int rest = (int) ((entryFile.length() - POS_NODES) % recordsize);// == 0 : "rest = " + ((entryFile.length()  - POS_NODES) % ((long) recordsize)) + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename;
                    int calculated_used = (int) ((entryFile.length() - POS_NODES) / recordsize);
                    if ((rest != 0) || (calculated_used != this.USEDC + this.FREEC)) {
                        theLogger.log(Level.WARNING, "USEDC inconsistency at startup: calculated_used = " + calculated_used + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename);
                        this.USEDC = calculated_used - this.FREEC;
                        writeused(true);
                    }
                } catch (IOException e) {
                    assert false;
                }
            }
        }
        
        private synchronized void writeused(boolean finalwrite) throws IOException {
            // we write only at close time, not in between. othervise, the read/write head
            // needs to run up and own all the way between the beginning and the end of the
            // file for each record. We check consistency beteen file size and
            if (finalwrite) synchronized (entryFile) {
                entryFile.writeInt(POS_USEDC, USEDC);
                entryFile.commit();
            }
        }
        
        private synchronized void writefree() throws IOException {
            synchronized (entryFile) {
                entryFile.writeInt(POS_FREEC, FREEC);
                entryFile.writeInt(POS_FREEH, FREEH.index);
                entryFile.commit();
                checkConsistency();
            }
        }
        
        private synchronized void readused() throws IOException {
            synchronized (entryFile) {
                this.USEDC = entryFile.readInt(POS_USEDC);
            }
        }
        
        private synchronized void readfree() throws IOException {
            synchronized (entryFile) {
                this.FREEC = entryFile.readInt(POS_FREEC);
                this.FREEH = new Handle(entryFile.readInt(POS_FREEH));
            }
        }
        
        private synchronized int allCount() {
            checkConsistency();
            return this.USEDC + this.FREEC;
        }
        
        private synchronized int used() {
            checkConsistency();
            return this.USEDC;
        }
        
        private synchronized void dispose(Handle h) throws IOException {
            // delete element with handle h
            // this element is then connected to the deleted-chain and can be
            // re-used change counter
            assert (h.index >= 0);
            assert (h.index != NUL);
            assert (h.index < USEDC + FREEC) : "USEDC = " + USEDC + ", FREEC = " + FREEC + ", h.index = " + h.index;
            long sp = seekpos(h);
            assert (sp <= entryFile.length() + ROW.objectsize) : h.index + "/" + sp + " exceeds file size " + entryFile.length();
            synchronized (USAGE) {
                USEDC--;
                FREEC++;
                // change pointer
                entryFile.writeInt(sp, FREEH.index); // extend free-list
                // write new FREEH Handle link
                FREEH = h;
                writefree();
                writeused(false);
            }
        }
        
        private synchronized int allocatePayload(byte[] chunk) throws IOException {
            // reserves a new record and returns index of record
            // the return value is not a seek position
            // the seek position can be retrieved using the seekpos() function
            if (chunk == null) {
                chunk = spaceChunk;
            }
            assert (chunk.length == ROW.objectsize()) : "chunk.length = " + chunk.length + ", ROW.objectsize() = " + ROW.objectsize();
            synchronized (USAGE) {
                if (USAGE.FREEC == 0) {
                    // generate new entry
                    int index = USAGE.allCount();
                    entryFile.write(seekpos(index) + overhead, chunk, 0, ROW.objectsize()); // occupy space, othervise the USAGE computaton does not work
                    USAGE.USEDC++;
                    writeused(false);
                    return index;
                } else {
                    // re-use record from free-list
                    USAGE.USEDC++;
                    USAGE.FREEC--;
                    // take link
                    int index;
                    if (USAGE.FREEH.index == NUL) {
                        serverLog.logSevere("kelondroRecords/" + filename, "INTERNAL ERROR (DATA INCONSISTENCY): re-use of records failed, lost " + (USAGE.FREEC + 1) + " records.");
                        // try to heal..
                        USAGE.USEDC = USAGE.allCount() + 1;
                        USAGE.FREEC = 0;
                        index = USAGE.USEDC - 1;
                    } else {
                        index = USAGE.FREEH.index;
                        //System.out.println("*DEBUG* ALLOCATED DELETED INDEX " + index);
                        // check for valid seek position
                        long seekp = seekpos(USAGE.FREEH);
                        if (seekp > entryFile.length()) {
                            // this is a severe inconsistency. try to heal..
                            serverLog.logSevere("kelondroRecords/" + filename, "new Handle: lost " + USAGE.FREEC + " marked nodes; seek position " + seekp + "/" + USAGE.FREEH.index + " out of file size " + entryFile.length() + "/" + ((entryFile.length() - POS_NODES) / recordsize));
                            index = USAGE.allCount(); // a place at the end of the file
                            USAGE.USEDC += USAGE.FREEC; // to avoid that non-empty records at the end are overwritten
                            USAGE.FREEC = 0; // discard all possible empty nodes
                            USAGE.FREEH.index = NUL;
                        } else {
                            // read link to next element of FREEH chain
                            USAGE.FREEH.index = entryFile.readInt(seekp);
                        }
                    }
                    USAGE.writeused(false);
                    USAGE.writefree();
                    entryFile.write(seekpos(index) + overhead, chunk, 0, ROW.objectsize()); // overwrite space
                    return index;
                }
            }
        }
        
        private synchronized void allocateRecord(int index, byte[] bulkchunk, int offset) throws IOException {
            // in case that the handle index was created outside this class,
            // this method ensures that the USAGE counters are consistent with the
            // new handle index
            if (bulkchunk == null) {
                bulkchunk = new byte[recordsize];
                offset = 0;
            }
            //assert (chunk.length == ROW.objectsize()) : "chunk.length = " + chunk.length + ", ROW.objectsize() = " + ROW.objectsize();
            synchronized (USAGE) {
                if (index < USAGE.allCount()) {
                    // write within the file
                    // this can be critical, if we simply overwrite fields that are marked
                    // as deleted. This case should be avoided. There is no other way to check
                    // that the field is not occupied than looking at the FREEC counter
                    assert (USAGE.FREEC == 0) : "FREEC = " + USAGE.FREEC;
                    // simply overwrite the cell
                    entryFile.write(seekpos(index), bulkchunk, offset, recordsize);
                    // no changes of counter necessary
                } else {
                    // write beyond the end of the file
                    // records that are in between are marked as deleted
                    Handle h;
                    while (index > USAGE.allCount()) {
                        h = new Handle(USAGE.allCount());
                        USAGE.FREEC++;
                        entryFile.write(seekpos(h), spaceChunk); // occupy space, othervise the USAGE computaton does not work
                        entryFile.writeInt(seekpos(h), USAGE.FREEH.index);
                        USAGE.FREEH = h;
                        USAGE.writefree();
                        entryFile.commit();
                    }
                    assert (index <= USAGE.allCount());
                
                    // adopt USAGE.USEDC
                    if (USAGE.allCount() == index) {
                        entryFile.write(seekpos(index), bulkchunk, offset, recordsize); // write chunk and occupy space
                        USAGE.USEDC++;
                        USAGE.writeused(false);
                        entryFile.commit();
                    }
                }
            }
        }
        
        
        private synchronized void checkConsistency() {
            if (debugmode) try { // in debug mode
                long efl = entryFile.length();
                assert ((efl - POS_NODES) % ((long) recordsize)) == 0 : "rest = " + ((entryFile.length()  - POS_NODES) % ((long) recordsize)) + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename;
                long calculated_used = (efl - POS_NODES) / ((long) recordsize);
                assert calculated_used == this.USEDC + this.FREEC : "calculated_used = " + calculated_used + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename;
            } catch (IOException e) {
                assert false;
            }
        }
    }

    public static int staticsize(File file) {
        if (!(file.exists())) return 0;
        try {
            kelondroRA ra = new kelondroFileRA(file.getCanonicalPath());
            kelondroIOChunks entryFile = new kelondroRAIOChunks(ra, ra.name());

            int used = entryFile.readInt(POS_USEDC); // works only if consistency with file size is given
            entryFile.close();
            ra.close();
            return used;
        } catch (FileNotFoundException e) {
            return 0;
        } catch (IOException e) {
            return 0;
        }
    }

    
    public kelondroRecords(File file, boolean useNodeCache, long preloadTime,
                           short ohbytec, short ohhandlec,
                           kelondroRow rowdef, int FHandles, int txtProps, int txtPropWidth) throws IOException {
        // opens an existing file or creates a new file
        // file: the file that shall be created
        // oha : overhead size array of four bytes: oha[0]=# of bytes, oha[1]=# of shorts, oha[2]=# of ints, oha[3]=# of longs, 
        // columns: array with size of column width; columns.length is number of columns
        // FHandles: number of integer properties
        // txtProps: number of text properties

        this.fileExisted = file.exists(); // can be used by extending class to track if this class created the file
        if (file.exists()) {
            // opens an existing tree
            this.filename = file.getCanonicalPath();
            kelondroRA raf = new kelondroFileRA(this.filename);
            //kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename), 1024, 100);
            //kelondroRA raf = new kelondroCachedRA(new kelondroFileRA(this.filename), 5000000, 1000);
            //kelondroRA raf = new kelondroNIOFileRA(this.filename, (file.length() < 4000000), 10000);
            initExistingFile(raf, useNodeCache);
        } else {
            this.filename = file.getCanonicalPath();
            kelondroRA raf = new kelondroFileRA(this.filename);
            // kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename), 1024, 100);
            // kelondroRA raf = new kelondroNIOFileRA(this.filename, false, 10000);
            initNewFile(raf, ohbytec, ohhandlec, rowdef, FHandles, txtProps, txtPropWidth, useNodeCache);
        }
        assignRowdef(rowdef);
        if (fileExisted) {
            kelondroOrder oldOrder = readOrderType();
            if ((oldOrder != null) && (!(oldOrder.equals(rowdef.objectOrder)))) {
                writeOrderType(); // write new order type
                //throw new IOException("wrong object order upon initialization. new order is " + rowdef.objectOrder.toString() + ", old order was " + oldOrder.toString());
            }
        } else {
            // create new file structure
            writeOrderType();            
        }
        initCache(useNodeCache, preloadTime);
        if (useNodeCache) recordTracker.put(this.filename, this);
    }

    public kelondroRecords(kelondroRA ra, String filename, boolean useCache, long preloadTime,
                           short ohbytec, short ohhandlec,
                           kelondroRow rowdef, int FHandles, int txtProps, int txtPropWidth,
                           boolean exitOnFail) {
        // this always creates a new file
        this.fileExisted = false;
        this.filename = filename;
        try {
            initNewFile(ra, ohbytec, ohhandlec, rowdef, FHandles, txtProps, txtPropWidth, useCache);
        } catch (IOException e) {
            logFailure("cannot create / " + e.getMessage());
            if (exitOnFail) System.exit(-1);
        }
        assignRowdef(rowdef);
        writeOrderType();
        initCache(useCache, preloadTime);
        if (useCache) recordTracker.put(this.filename, this);
    }

    private void initNewFile(kelondroRA ra, short ohbytec, short ohhandlec,
                      kelondroRow rowdef, int FHandles, int txtProps, int txtPropWidth, boolean useCache) throws IOException {

        // create new Chunked IO
        if (useWriteBuffer) {
            this.entryFile = new kelondroBufferedIOChunks(ra, ra.name(), 0, 30000 + random.nextLong() % 30000);
        } else {
            this.entryFile = new kelondroRAIOChunks(ra, ra.name());
        }
        
        // create row
        ROW = rowdef;
        
        // store dynamic run-time data
        this.overhead = ohbytec + 4 * ohhandlec;
        this.recordsize = this.overhead + ROW.objectsize();
        this.headchunksize = overhead + ROW.width(0);
        this.tailchunksize = this.recordsize - this.headchunksize;
        this.spaceChunk = fillSpaceChunk(recordsize);

        // store dynamic run-time seek pointers
        POS_HANDLES = POS_COLWIDTHS + ROW.columns() * 4;
        POS_TXTPROPS = POS_HANDLES + FHandles * 4;
        POS_NODES = POS_TXTPROPS + txtProps * txtPropWidth;
        //System.out.println("*** DEBUG: POS_NODES = " + POS_NODES + " for " + filename);

        // store dynamic back-up variables
        USAGE     = new usageControl(true);
        OHBYTEC   = ohbytec;
        OHHANDLEC = ohhandlec;
        HANDLES   = new Handle[FHandles];
        for (int i = 0; i < FHandles; i++) HANDLES[i] = new Handle(NUL);
        TXTPROPS  = new byte[txtProps][];
        for (int i = 0; i < txtProps; i++) TXTPROPS[i] = new byte[0];
        TXTPROPW  = txtPropWidth;

        // write data to file
        entryFile.writeByte(POS_MAGIC, 4); // magic marker for this file type
        entryFile.writeByte(POS_BUSY, 0); // unlock: default
        entryFile.writeShort(POS_PORT, 4444); // default port (not used yet)
        entryFile.write(POS_DESCR, "--AnomicRecords file structure--".getBytes());
        entryFile.writeShort(POS_COLUMNS, this.ROW.columns());
        entryFile.writeShort(POS_OHBYTEC, OHBYTEC);
        entryFile.writeShort(POS_OHHANDLEC, OHHANDLEC);
        entryFile.writeInt(POS_USEDC, 0);
        entryFile.writeInt(POS_FREEC, 0);
        entryFile.writeInt(POS_FREEH, this.USAGE.FREEH.index);
        entryFile.write(POS_MD5PW, "PASSWORDPASSWORD".getBytes());
        entryFile.write(POS_ENCRYPTION, "ENCRYPTION!#$%&?".getBytes());
        entryFile.writeLong(POS_OFFSET, POS_NODES);
        entryFile.writeInt(POS_INTPROPC, FHandles);
        entryFile.writeInt(POS_TXTPROPC, txtProps);
        entryFile.writeInt(POS_TXTPROPW, txtPropWidth);

        // write configuration arrays
        for (int i = 0; i < this.ROW.columns(); i++) {
            entryFile.writeInt(POS_COLWIDTHS + 4 * i, this.ROW.width(i));
        }
        for (int i = 0; i < this.HANDLES.length; i++) {
            entryFile.writeInt(POS_HANDLES + 4 * i, NUL);
            HANDLES[i] = new Handle(NUL);
        }
        byte[] ea = new byte[TXTPROPW];
        for (int j = 0; j < TXTPROPW; j++) ea[j] = 0;
        for (int i = 0; i < this.TXTPROPS.length; i++) {
            entryFile.write(POS_TXTPROPS + TXTPROPW * i, ea);
        }
        
        this.entryFile.commit();
    }

    private static final byte[] fillSpaceChunk(int size) {
        byte[] chunk = new byte[size];
        while (--size >= 0) chunk[size] = (byte) 0xff;
        return chunk;
    }
    
    public void setDescription(byte[] description) throws IOException {
        if (description.length > LEN_DESCR)
            entryFile.write(POS_DESCR, description, 0, LEN_DESCR);
        else
            entryFile.write(POS_DESCR, description);
    }
    
    public byte[] getDescription() throws IOException {
        byte[] b = new byte[LEN_DESCR];
        entryFile.readFully(POS_DESCR, b, 0, LEN_DESCR);
        return b;
    }
    
    public void setLogger(Logger newLogger) {
        this.theLogger = newLogger;
    }

    public void logWarning(String message) {
        if (this.theLogger == null)
            System.err.println("KELONDRO WARNING " + this.filename + ": " + message);
        else
            this.theLogger.warning("KELONDRO WARNING " + this.filename + ": " + message);
    }

    public void logFailure(String message) {
        if (this.theLogger == null)
            System.err.println("KELONDRO FAILURE " + this.filename + ": " + message);
        else
            this.theLogger.severe("KELONDRO FAILURE " + this.filename + ": " + message);
    }

    public void logFine(String message) {
        if (this.theLogger == null)
            System.out.println("KELONDRO DEBUG " + this.filename + ": " + message);
        else
            this.theLogger.fine("KELONDRO DEBUG " + this.filename + ": " + message);
    }

    public kelondroRecords(kelondroRA ra, String filename, boolean useNodeCache, long preloadTime) throws IOException{
        this.fileExisted = false;
        this.filename = filename;
        initExistingFile(ra, useNodeCache);
        readOrderType();
        initCache(useNodeCache, preloadTime);
        if (useNodeCache) recordTracker.put(this.filename, this);
    }

    private void initExistingFile(kelondroRA ra, boolean useNodeCache) throws IOException {
        // read from Chunked IO
        if ((useWriteBuffer) && (useNodeCache)) {
            this.entryFile = new kelondroBufferedIOChunks(ra, ra.name(), 0, 30000 + random.nextLong() % 30000);
        } else {
            this.entryFile = new kelondroRAIOChunks(ra, ra.name());
        }

        // read dynamic variables that are back-ups of stored values in file;
        // read/defined on instantiation
        
        this.OHBYTEC = entryFile.readShort(POS_OHBYTEC);
        this.OHHANDLEC = entryFile.readShort(POS_OHHANDLEC);

        kelondroColumn[] COLDEFS = new kelondroColumn[entryFile.readShort(POS_COLUMNS)];
        this.HANDLES = new Handle[entryFile.readInt(POS_INTPROPC)];
        this.TXTPROPS = new byte[entryFile.readInt(POS_TXTPROPC)][];
        this.TXTPROPW = entryFile.readInt(POS_TXTPROPW);

        if (COLDEFS.length == 0) throw new kelondroException(filename, "init: zero columns; strong failure");
        
        // calculate dynamic run-time seek pointers
        POS_HANDLES = POS_COLWIDTHS + COLDEFS.length * 4;
        POS_TXTPROPS = POS_HANDLES + HANDLES.length * 4;
        POS_NODES = POS_TXTPROPS + TXTPROPS.length * TXTPROPW;
        //System.out.println("*** DEBUG: POS_NODES = " + POS_NODES + " for " + filename);

        // read configuration arrays
        for (int i = 0; i < COLDEFS.length; i++) {
            COLDEFS[i] = new kelondroColumn("col-" + i, kelondroColumn.celltype_binary, kelondroColumn.encoder_bytes, entryFile.readInt(POS_COLWIDTHS + 4 * i), "");
        }
        for (int i = 0; i < HANDLES.length; i++) {
            HANDLES[i] = new Handle(entryFile.readInt(POS_HANDLES + 4 * i));
        }
        for (int i = 0; i < TXTPROPS.length; i++) {
            TXTPROPS[i] = new byte[TXTPROPW];
            entryFile.readFully(POS_TXTPROPS + TXTPROPW * i, TXTPROPS[i], 0, TXTPROPS[i].length);
        }
        this.ROW = new kelondroRow(COLDEFS, readOrderType(), 0);
        
        // assign remaining values that are only present at run-time
        this.overhead = OHBYTEC + 4 * OHHANDLEC;
        this.recordsize = this.overhead + ROW.objectsize();
        this.headchunksize = this.overhead + this.ROW.width(0);
        this.tailchunksize = this.recordsize - this.headchunksize;
        this.spaceChunk = fillSpaceChunk(recordsize);
        
        // init USAGE, must be done at the end because it needs the recordsize value
        this.USAGE = new usageControl(false);
    }
    
    private void writeOrderType() {
        try {
            setDescription((this.ROW.objectOrder == null) ? "__".getBytes() : this.ROW.objectOrder.signature().getBytes());
        } catch (IOException e) {}
    }
    
    private kelondroOrder readOrderType() {
        try {
            byte[] d = getDescription();
            String s = new String(d).substring(0, 2);
            return orderBySignature(s);
        } catch (IOException e) {
            return null;
        }
    }
    
    public static kelondroOrder orderBySignature(String signature) {
        kelondroOrder oo = null;
        if (oo == null) oo = kelondroNaturalOrder.bySignature(signature);
        if (oo == null) oo = kelondroBase64Order.bySignature(signature);
        if (oo == null) oo = new kelondroNaturalOrder(true);
        return oo;
    }
    
    private void initCache(boolean useNodeCache, long preloadTime) {
        if (useNodeCache) {
            this.cacheHeaders = new kelondroIntBytesMap(this.headchunksize, 0);
        } else {
            this.cacheHeaders = null;
        }
        this.readHit = 0;
        this.readMiss = 0;
        this.writeUnique = 0;
        this.writeDouble = 0;
        this.cacheDelete = 0;
        this.cacheFlush = 0;
        // pre-load node cache
        if ((preloadTime > 0) && (useNodeCache)) {
            long stop = System.currentTimeMillis() + preloadTime;
            int count = 0;
            try {
                Iterator i = contentNodes(preloadTime);
                Node n;
                while ((System.currentTimeMillis() < stop) && (cacheGrowStatus() == 2) && (i.hasNext())) {
                    n = (Node) i.next();
                    cacheHeaders.addb(n.handle.index, n.headChunk);
                    count++;
                }
                cacheHeaders.flush();
                logFine("preloaded " + count + " records into cache");
            } catch (kelondroException e) {
                // the contentNodes iterator had a time-out; we don't do a preload
                logFine("could not preload records: " + e.getMessage());
            }
            
        }
    }

    private int cacheGrowStatus() {
        return cacheGrowStatus(memStopGrow, memStartShrink);
    }
    
    public static final int cacheGrowStatus(long stopGrow, long startShrink) {
        // returns either 0, 1 or 2:
        // 0: cache is not allowed to grow, but shall shrink
        // 1: cache is allowed to grow, but need not to shrink
        // 2: cache is allowed to grow and must not shrink
        long available = serverMemory.available();
        if (available > stopGrow) return 2;
        if (available > startShrink) return 1;
        return 0;
    }
    
    public static void setCacheGrowStati(long memStopGrowNew, long memStartShrinkNew) {
        memStopGrow = memStopGrowNew;
        memStartShrink =  memStartShrinkNew;
    }
    
    public static long getMemStopGrow() {
        return memStopGrow ;
    }
    
    public static long getMemStartShrink() {
        return memStartShrink ;
    }
    
    public String filename() {
        return filename;
    }
    
    public static final Iterator filenames() {
        // iterates string objects; all file names from record tracker
        return recordTracker.keySet().iterator();
    }

    public static final Map memoryStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        kelondroRecords theRecord = (kelondroRecords) recordTracker.get(filename);
        return theRecord.memoryStats();
    }
    
    private final Map memoryStats() {
        // returns statistical data about this object
        if (cacheHeaders == null) return null;
        HashMap map = new HashMap();
        map.put("nodeChunkSize", Integer.toString(this.headchunksize + element_in_cache));
        map.put("nodeCacheCount", Integer.toString(cacheHeaders.size()));
        map.put("nodeCacheMem", Integer.toString(cacheHeaders.size() * (this.headchunksize + element_in_cache)));
        map.put("nodeCacheReadHit", Integer.toString(readHit));
        map.put("nodeCacheReadMiss", Integer.toString(readMiss));
        map.put("nodeCacheWriteUnique", Integer.toString(writeUnique));
        map.put("nodeCacheWriteDouble", Integer.toString(writeDouble));
        map.put("nodeCacheDeletes", Integer.toString(cacheDelete));
        map.put("nodeCacheFlushes", Integer.toString(cacheFlush));
        return map;
    }
    
    public final byte[] bulkRead(int start, int end) throws IOException {
        // a bulk read simply reads a piece of memory from the record file
        // this makes only sense if there are no overhead bytes or pointer
        // the end value is OUTSIDE the record interval
        assert OHBYTEC == 0;
        assert OHHANDLEC == 0;
        assert start >= 0;
        assert end <= USAGE.allCount();
        byte[] bulk = new byte[(end - start) * recordsize];
        long bulkstart = POS_NODES + ((long) recordsize * start);
        entryFile.readFully(bulkstart, bulk, 0, bulk.length);
        return bulk;
    }
    
    protected synchronized final Node newNode(byte[] row) throws IOException {
        // the row must be the raw row data only
        return new Node(row);
    }
    
    protected synchronized final Node newNode(Handle handle, byte[] bulkchunk, int offset) throws IOException {
        // bulkchunk must include the OH bytes and handles!
        return new Node(handle, bulkchunk, offset);
    }
    
    protected synchronized final Node getNode(Handle handle, boolean fillTail) throws IOException {
        return getNode(handle, null, 0, fillTail);
    }
    
    protected synchronized final Node getNode(Handle handle, Node parentNode, int referenceInParent, boolean fillTail) throws IOException {
        return new Node(handle, parentNode, referenceInParent, fillTail);
    }
    
    protected synchronized final void deleteNode(Handle handle) throws IOException {
        if ((cacheHeaders == null) || (cacheHeaders.size() == 0)) {
            USAGE.dispose(handle);
        } else synchronized (cacheHeaders) {
            cacheHeaders.removeb(handle.index);
            cacheDelete++;
            USAGE.dispose(handle);
        }
    }

    public final class Node {
        // an Node holds all information of one row of data. This includes the key to the entry
        // which is stored as entry element at position 0
        // an Node object can be created in two ways:
        // 1. instantiation with an index number. After creation the Object does not hold any
        //    value information until such is retrieved using the getValue() method
        // 2. instantiation with a value array. the values are not directly written into the
        //    file. Expanding the tree structure is then done using the save() method. at any
        //    time it is possible to verify the save state using the saved() predicate.
        // Therefore an entry object has three modes:
        // a: holding an index information only (saved() = true)
        // b: holding value information only (saved() = false)
        // c: holding index and value information at the same time (saved() = true)
        //    which can be the result of one of the two processes as follow:
        //    (i)  created with index and after using the getValue() method, or
        //    (ii) created with values and after calling the save() method
        // the method will therefore throw an IllegalStateException when the following
        // process step is performed:
        //    - create the Node with index and call then the save() method
        // this case can be decided with
        //    ((index != NUL) && (values == null))
        // The save() method represents the insert function for the tree. Balancing functions
        // are applied automatically. While balancing, the Node does never change its index key,
        // but its parent/child keys.
        //private byte[]    ohBytes  = null;  // the overhead bytes, OHBYTEC values
        //private Handle[]  ohHandle= null;  // the overhead handles, OHHANDLEC values
        //private byte[][]  values  = null;  // an array of byte[] nodes is the value vector
        protected Handle handle = null; // index of the entry, by default NUL means undefined
        protected byte[] headChunk = null; // contains ohBytes, ohHandles and the key value
        protected byte[] tailChunk = null; // contains all values except the key value
        protected boolean headChanged = false;
        protected boolean tailChanged = false;

        protected Node(byte[] rowinstance) throws IOException {
            // this initializer is used to create nodes from bulk-read byte arrays
            assert ((rowinstance == null) || (rowinstance.length == ROW.objectsize)) : "bulkchunk.length = " + rowinstance.length + ", ROW.width(0) = " + ROW.width(0);
            this.handle = new Handle(USAGE.allocatePayload(rowinstance));
            
            // create empty chunks
            this.headChunk = new byte[headchunksize];
            this.tailChunk = new byte[tailchunksize];
            
            // write content to chunks
            if (rowinstance == null) {
                for (int i = headchunksize - 1; i >= 0; i--) this.headChunk[i] = (byte) 0xff;
                for (int i = tailchunksize - 1; i >= 0; i--) this.tailChunk[i] = (byte) 0xff;
            } else {
                for (int i = overhead - 1; i >= 0; i--) this.headChunk[i] = (byte) 0xff;
                System.arraycopy(rowinstance, 0, this.headChunk, overhead, ROW.width(0));
                System.arraycopy(rowinstance, ROW.width(0), this.tailChunk, 0, tailchunksize);
            }
            
            // mark chunks as changed
            // if the head/tail chunks come from a file system read, setChanged should be false
            // if the chunks come from a overwrite attempt, it should be true
            this.headChanged = false; // we wrote the head already during allocate
            this.tailChanged = false; // we write the tail already during allocate
        }
        
        protected Node(Handle handle, byte[] bulkchunk, int offset) throws IOException {
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
            
            // create empty chunks
            this.headChunk = new byte[headchunksize];
            this.tailChunk = new byte[tailchunksize];
            
            // write content to chunks
            if (bulkchunk != null) {
                System.arraycopy(bulkchunk, offset, this.headChunk, 0, headchunksize);
                System.arraycopy(bulkchunk, offset + headchunksize, this.tailChunk, 0, tailchunksize);
            }
            
            // mark chunks as changed
            this.headChanged = changed;
            this.tailChanged = changed;
        }
        
        protected Node(Handle handle, Node parentNode, int referenceInParent, boolean fillTail) throws IOException {
            // this creates an entry with an pre-reserved entry position.
            // values can be written using the setValues() method,
            // but we expect that values are already there in the file.
            assert (handle != null): "node handle is null";
            assert (handle.index >= 0): "node handle too low: " + handle.index;
            //assert (handle.index < USAGE.allCount()) : "node handle too high: " + handle.index + ", USEDC=" + USAGE.USEDC + ", FREEC=" + USAGE.FREEC;
            
            // the parentNode can be given if an auto-fix in the following case is wanted
            if (handle == null) throw new kelondroException(filename, "INTERNAL ERROR: node handle is null.");
            if (handle.index >= USAGE.allCount()) {
                if (parentNode == null) throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. No auto-fix node was submitted. This is a serious failure.");
                try {
                    parentNode.setOHHandle(referenceInParent, null);
                    parentNode.commit(CP_NONE);
                    logWarning("INTERNAL ERROR, Node/init in " + filename + ": node handle index " + handle.index + " exceeds size. The bad node has been auto-fixed");
                } catch (IOException ee) {
                    throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. It was tried to fix the bad node, but failed with an IOException: " + ee.getMessage());
                }
            }

            // use given handle
            this.handle = new Handle(handle.index);

            // check for memory availability when fillTail is requested
            if ((fillTail) && (tailchunksize > 10000)) fillTail = false; // this is a fail-safe 'short version' of a memory check
            
            // init the content
            // create chunks; read them from file or cache
            this.tailChunk = null;
            if (cacheHeaders == null) {
                if (fillTail) {
                    // read complete record
                    byte[] chunkbuffer = new byte[recordsize];
                    entryFile.readFully(seekpos(this.handle), chunkbuffer, 0, recordsize);
                    this.headChunk = new byte[headchunksize];
                    this.tailChunk = new byte[tailchunksize];
                    System.arraycopy(chunkbuffer, 0, this.headChunk, 0, headchunksize);
                    System.arraycopy(chunkbuffer, headchunksize, this.tailChunk, 0, tailchunksize);
                    chunkbuffer = null;
                } else {
                    // read overhead and key
                    this.headChunk = new byte[headchunksize];
                    this.tailChunk = null;
                    entryFile.readFully(seekpos(this.handle), this.headChunk, 0, headchunksize);
                }
            } else synchronized(cacheHeaders) {
                byte[] cacheEntry = null;
                int cp = CP_HIGH;
                cacheEntry = cacheHeaders.getb(this.handle.index);
                if (cacheEntry == null) {
                    // cache miss, we read overhead and key from file
                    readMiss++;
                    if (fillTail) {
                        // read complete record
                        byte[] chunkbuffer = new byte[recordsize];
                        entryFile.readFully(seekpos(this.handle), chunkbuffer, 0, recordsize);
                        this.headChunk = new byte[headchunksize];
                        this.tailChunk = new byte[tailchunksize];
                        System.arraycopy(chunkbuffer, 0, this.headChunk, 0, headchunksize);
                        System.arraycopy(chunkbuffer, headchunksize, this.tailChunk, 0, tailchunksize);
                        chunkbuffer = null;
                    } else {
                        // read overhead and key
                        this.headChunk = new byte[headchunksize];
                        this.tailChunk = null;
                        entryFile.readFully(seekpos(this.handle), this.headChunk, 0, headchunksize);
                    }
                    
                    // calculate cache priority
                    cp = CP_HIGH;
                    if (OHHANDLEC == 3) {
                        Handle l = getOHHandle(1);
                        Handle r = getOHHandle(2);
                        if ((l == null) && (r == null)) cp = CP_LOW;
                        else if ((l == null) || (r == null)) cp = CP_MEDIUM;
                    }
                    
                    // if space left in cache, copy these value to the cache
                    updateNodeCache(cp);
                } else {
                    readHit++;
                    this.headChunk = cacheEntry;
                }
            }
        }
        
        private void setValue(byte[] value, int valueoffset, int valuewidth, byte[] targetarray, int targetoffset) {
            if (value == null) {
                while (valuewidth-- > 0) targetarray[targetoffset++] = 0;
            } else {
                assert ((valueoffset >= 0) && (valueoffset < value.length)) : "valueoffset = " + valueoffset;
                assert ((valueoffset + valuewidth <= value.length)) : "valueoffset = " + valueoffset + ", valuewidth = " + valuewidth + ", value.length = " + value.length;
                assert ((targetoffset >= 0) && (targetoffset < targetarray.length)) : "targetoffset = " + targetoffset;
                assert ((targetoffset + valuewidth <= targetarray.length)) : "targetoffset = " + targetoffset + ", valuewidth = " + valuewidth + ", targetarray.length = " + targetarray.length;
                System.arraycopy(value, valueoffset, targetarray, targetoffset, Math.min(value.length, valuewidth)); // error?
                while (valuewidth-- > value.length) targetarray[targetoffset + valuewidth] = 0;
            }
        }
        
        protected Handle handle() {
            // if this entry has an index, return it
            if (this.handle.index == NUL) throw new kelondroException(filename, "the entry has no index assigned");
            return this.handle;
        }

        protected void setOHByte(int i, byte b) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("setOHByte: wrong index " + i);
            if (this.handle.index == NUL) throw new kelondroException(filename, "setOHByte: no handle assigned");
            this.headChunk[i] = b;
            this.headChanged = true;
        }
        
        protected void setOHHandle(int i, Handle otherhandle) {
            assert (i < OHHANDLEC): "setOHHandle: wrong array size " + i;
            assert (this.handle.index != NUL): "setOHHandle: no handle assigned ind file" + filename;
            if (otherhandle == null) {
                NUL2bytes(this.headChunk, OHBYTEC + 4 * i);
            } else {
                if (otherhandle.index >= USAGE.allCount()) throw new kelondroException(filename, "INTERNAL ERROR, setOHHandles: handle " + i + " exceeds file size (" + handle.index + " >= " + USAGE.allCount() + ")");
                int2bytes(otherhandle.index, this.headChunk, OHBYTEC + 4 * i);
            }
            this.headChanged = true;
        }
        
        protected byte getOHByte(int i) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("getOHByte: wrong index " + i);
            if (this.handle.index == NUL) throw new kelondroException(filename, "Cannot load OH values");
            return this.headChunk[i];
        }

        protected Handle getOHHandle(int i) {
            if (this.handle.index == NUL) throw new kelondroException(filename, "Cannot load OH values");
            assert (i < OHHANDLEC): "handle index out of bounds: " + i + " in file " + filename;
            int h = bytes2int(this.headChunk, OHBYTEC + 4 * i);
            return (h == NUL) ? null : new Handle(h);
        }

        public byte[] setValueRow(byte[] row) throws IOException {
            // if the index is defined, then write values directly to the file, else only to the object
            if ((row != null) && (row.length != ROW.objectsize())) throw new IOException("setValueRow with wrong (" + row.length + ") row length instead correct: " + ROW.objectsize());
            byte[] result = getValueRow(); // previous value (this loads the values if not already happened)
            
            // set values
            if (this.handle.index != NUL) {
                setValue(row, 0, ROW.width(0), headChunk, overhead);
                if (ROW.columns() > 1) setValue(row, ROW.width(0), tailchunksize, tailChunk, 0);
            }
            this.headChanged = true;
            this.tailChanged = true;
            return result; // return previous value
        }

        public byte[] getKey() {
            // read key
            return trimCopy(headChunk, overhead, ROW.width(0));
        }

        public byte[] getValueRow() throws IOException {
            
            if (this.tailChunk == null) {
                // load all values from the database file
                this.tailChunk = new byte[tailchunksize];
                // read values
                entryFile.readFully(seekpos(this.handle) + headchunksize, this.tailChunk, 0, this.tailChunk.length);
            }

            // create return value
            byte[] row = new byte[ROW.objectsize()];

            // read key
            System.arraycopy(headChunk, overhead, row, 0, ROW.width(0));

            // read remaining values
            System.arraycopy(tailChunk, 0, row, ROW.width(0), tailchunksize);

            return row;
        }

        public synchronized void commit(int cachePriority) throws IOException {
            // this must be called after all write operations to the node are
            // finished

            // place the data to the file

            if (this.headChunk == null) {
                // there is nothing to save
                throw new kelondroException(filename, "no values to save (header missing)");
            }

            boolean doCommit = this.headChanged || this.tailChanged;
            
            // save head
            if (this.headChanged) {
                //System.out.println("WRITEH(" + filename + ", " + seekpos(this.handle) + ", " + this.headChunk.length + ")");
                entryFile.write(seekpos(this.handle), (this.headChunk == null) ? new byte[headchunksize] : this.headChunk);
                updateNodeCache(cachePriority);
                this.headChanged = false;
            }

            // save tail
            if ((this.tailChunk != null) && (this.tailChanged)) {
                //System.out.println("WRITET(" + filename + ", " + (seekpos(this.handle) + headchunksize) + ", " + this.tailChunk.length + ")");
                entryFile.write(seekpos(this.handle) + headchunksize, (this.tailChunk == null) ? new byte[tailchunksize] : this.tailChunk);
                this.tailChanged = false;
            }
            
            if (doCommit) entryFile.commit();
        }

        public synchronized void collapse() {
            // this must be called after all write and read operations to the
            // node are finished
            this.headChunk = null;
            this.tailChunk = null;
            this.handle = null;
        }

        private byte[] trimCopy(byte[] a, int offset, int length) {
            if (length > a.length - offset) length = a.length - offset;
            while ((length > 0) && (a[offset + length - 1] == 0)) length--;
            if (length == 0) return null;
            byte[] b = new byte[length];
            System.arraycopy(a, offset, b, 0, length);
            return b;
        }
        
        public String toString() {
            if (this.handle.index == NUL) return "NULL";
            String s = Integer.toHexString(this.handle.index);
            Handle h;
            while (s.length() < 4) s = "0" + s;
            try {
                for (int i = 0; i < OHBYTEC; i++) s = s + ":b" + getOHByte(i);
                for (int i = 0; i < OHHANDLEC; i++) {
                    h = getOHHandle(i);
                    if (h == null) s = s + ":hNULL"; else s = s + ":h" + h.toString();
                }
                kelondroRow.Entry content = row().newEntry(getValueRow());
                for (int i = 0; i < row().columns(); i++) s = s + ":" + ((content.empty(i)) ? "NULL" : content.getColString(i, "UTF-8").trim());
            } catch (IOException e) {
                s = s + ":***LOAD ERROR***:" + e.getMessage();
            }
            return s;
        }
        
        private boolean cacheSpace() {
            // check for space in cache
            // should be only called within a synchronized(cacheHeaders) environment
            // returns true if it is allowed to add another entry to the cache
            // returns false if the cache is considered to be full
            if (cacheHeaders == null) return false; // no caching
            if (cacheHeaders.size() == 0) return true; // nothing there to flush
            if (cacheGrowStatus() == 2) return true; // no need to flush cache space
            
            // just delete any of the entries
            if (cacheGrowStatus() <= 1) {
                cacheHeaders.removeoneb();
                cacheFlush++;
            }
            return cacheGrowStatus() > 0;
        }
        
        private void updateNodeCache(int priority) {
            if (this.handle == null) return; // wrong access
            if (this.headChunk == null) return; // nothing there to cache
            if (priority == CP_NONE) return; // it is not wanted that this shall be cached
            if (cacheHeaders == null) return; // we do not use the cache
            if (!(cacheSpace())) return;
            
            synchronized (cacheHeaders) {
                // generate cache entry
                //byte[] cacheEntry = new byte[headchunksize];
                //System.arraycopy(headChunk, 0, cacheEntry, 0, headchunksize);
                
                // store the cache entry
                boolean upd = false;
                upd = (cacheHeaders.putb(this.handle.index, headChunk) != null);
                if (upd) writeDouble++; else writeUnique++;
                
                //System.out.println("kelondroRecords cache4" + filename + ": cache record size = " + (memBefore - Runtime.getRuntime().freeMemory()) + " bytes" + ((newentry) ? " new" : ""));
                //printCache();
            }
        }
        
    }
    
    protected void printCache() {
        if (cacheHeaders == null) {
            System.out.println("### file report: " + size() + " entries");
            for (int i = 0; i < USAGE.allCount(); i++) {
                // print from  file to compare
                System.out.print("#F " + i + ": ");
                try {
                    for (int j = 0; j < headchunksize; j++) 
                        System.out.print(Integer.toHexString(0xff & entryFile.readByte(j + seekpos(new Handle(i)))) + " ");
                } catch (IOException e) {}
                
                System.out.println();
            }
        } else {
            System.out.println("### cache report: " + cacheHeaders.size() + " entries");
            
                Iterator i = cacheHeaders.rows();
                kelondroRow.Entry entry;
                while (i.hasNext()) {
                    entry = (kelondroRow.Entry) i.next();
                    
                    // print from cache
                    System.out.print("#C ");
                    printChunk(entry);
                    System.out.println();
                    
                    // print from  file to compare
                    /*
                    System.out.print("#F " + cp + " " + ((Handle) entry.getKey()).index + ": ");
                    try {
                        for (int j = 0; j < headchunksize; j++)
                            System.out.print(entryFile.readByte(j + seekpos((Handle) entry.getKey())) + ",");
                    } catch (IOException e) {}
                    */
                    System.out.println();
                }
        }
        System.out.println("### end report");
    }
    
    private void printChunk(kelondroRow.Entry chunk) {
        for (int j = 0; j < chunk.columns(); j++)
            System.out.print(new String(chunk.getColBytes(j)) + ", ");
    }

    public final kelondroRow row() {
        return this.ROW;
    }
    
    private final void assignRowdef(kelondroRow rowdef) {
        // overwrites a given rowdef
        // the new rowdef must be compatible
        /*
        if ((rowdef.columns() != ROW.columns()) &&
            ((rowdef.columns() + 1 != ROW.columns()) ||
             (rowdef.column(rowdef.columns() - 1).cellwidth() != (ROW.column(ROW.columns() - 1).cellwidth() + ROW.column(ROW.columns() - 2).cellwidth()))))
            throw new kelondroException(this.filename,
                    "new rowdef '" + rowdef.toString() + "' is not compatible with old rowdef '" + ROW.toString() + "', they have a different number of columns");
         */
        // adopt encoder and cell type
        /*
        kelondroColumn col;
        for (int i = 0; i < ROW.columns(); i++) {
            col = rowdef.column(i);
            ROW.column(i).setAttributes(col.nickname(), col.celltype(), col.encoder());
        }
        */
        this.ROW = rowdef;
    }

    protected final long seekpos(Handle handle) {
        assert (handle.index >= 0): "handle index too low: " + handle.index;
        return POS_NODES + ((long) recordsize * handle.index);
    }
    
    protected final long seekpos(int index) {
        assert (index >= 0): "handle index too low: " + index;
        return POS_NODES + ((long) recordsize * index);
    }

    // additional properties
    public final synchronized int handles() {
        return this.HANDLES.length;
    }

    protected final void setHandle(int pos, Handle handle) throws IOException {
        if (pos >= HANDLES.length) throw new IllegalArgumentException("setHandle: handle array exceeded");
        if (handle == null) handle = new Handle(NUL);
        HANDLES[pos] = handle;
        entryFile.writeInt(POS_HANDLES + 4 * pos, handle.index);
    }

    protected final Handle getHandle(int pos) {
        if (pos >= HANDLES.length) throw new IllegalArgumentException("getHandle: handle array exceeded");
        return (HANDLES[pos].index == NUL) ? null : HANDLES[pos];
    }

    // custom texts
    public final void setText(int pos, byte[] text) throws IOException {
        if (pos >= TXTPROPS.length) throw new IllegalArgumentException("setText: text array exceeded");
        if (text.length > TXTPROPW) throw new IllegalArgumentException("setText: text lemgth exceeded");
        if (text == null) text = new byte[0];
        TXTPROPS[pos] = text;
        entryFile.write(POS_TXTPROPS + TXTPROPW * pos, text);
    }

    public final byte[] getText(int pos) {
        if (pos >= TXTPROPS.length) throw new IllegalArgumentException("getText: text array exceeded");
        return TXTPROPS[pos];
    }

    // Returns true if this map contains no key-value mappings.
    public final boolean isEmpty() {
        return (USAGE.used() == 0);
    }

    // Returns the number of key-value mappings in this map.
    public int size() {
        return USAGE.used();
    }

    protected final int free() {
        return USAGE.FREEC;
    }
    
    public final Iterator contentRows(long maxInitTime) throws kelondroException {
        return new contentRowIterator(maxInitTime);
    }
    
    public final class contentRowIterator implements Iterator {
        // iterator that iterates all kelondroRow.Entry-objects in the file
        // all records that are marked as deleted are ommitted
        
        private Iterator nodeIterator;
        
        public contentRowIterator(long maxInitTime) {
            nodeIterator = contentNodes(maxInitTime);
        }

        public boolean hasNext() {
            return nodeIterator.hasNext();
        }

        public Object next() {
            try {
                Node n = (Node) nodeIterator.next();
                return row().newEntry(n.getValueRow(), n.handle.index);
            } catch (IOException e) {
                throw new kelondroException(filename, e.getMessage());
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }
    
    protected final Iterator contentNodes(long maxInitTime) throws kelondroException {
        // returns an iterator of Node-objects that are not marked as 'deleted'
        try {
            return new contentNodeIterator(maxInitTime);
        } catch (IOException e) {
            return new HashSet().iterator();
        }
    }
    
    protected final Set deletedHandles(long maxTime) throws kelondroException, IOException {
        // initialize set with deleted nodes; the set contains Handle-Objects
        // this may last only the given maxInitTime
        // if the initTime is exceeded, the method throws an kelondroException
        TreeSet markedDeleted = new TreeSet();
        long timeLimit = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        long seekp;
        synchronized (USAGE) {
            if (USAGE.FREEC != 0) {
                Handle h = USAGE.FREEH;
                long repair_position = POS_FREEH;
                int iter = 0;
                while (h.index != NUL) {
                    // check handle
                    seekp = seekpos(h);
                    if (seekp > entryFile.length()) {
                        // repair last hande store position
                        this.theLogger.severe("KELONDRO WARNING " + this.filename + ": seek position " + seekp + "/" + h.index + " out of file size " + entryFile.length() + "/" + ((entryFile.length() - POS_NODES) / recordsize) + " after " + iter + " iterations; patched wrong node");
                        entryFile.writeInt(repair_position, NUL);
                        return markedDeleted;
                    }

                    // handle seems to be correct. store handle
                    markedDeleted.add(h);
                    
                    // move to next handle
                    repair_position = seekp;
                    h = new Handle(entryFile.readInt(seekp));
                    if (h.index == NUL) break;
                    
                    // double-check for already stored handles: detect loops
                    if (markedDeleted.contains(h)) {
                        // loop detection
                        this.theLogger.severe("KELONDRO WARNING " + this.filename + ": FREE-Queue contains loops");
                        entryFile.writeInt(repair_position, NUL);
                        return markedDeleted;
                    }
                    
                    // this appears to be correct. go on.
                    iter++;
                    if (System.currentTimeMillis() > timeLimit) throw new kelondroException(filename, "time limit of " + maxTime + " exceeded; > " + markedDeleted.size() + " deleted entries");
                }
                System.out.println("\nDEBUG: " + iter + " deleted entries in " + entryFile.name());
            }
        }
        return markedDeleted;
    }
    
    protected final class contentNodeIterator implements Iterator {
        // iterator that iterates all Node-objects in the file
        // all records that are marked as deleted are ommitted
        // this is probably also the fastest way to iterate all objects
        
        private Set markedDeleted;
        private Handle pos;
        private byte[] bulk;
        private int bulksize;
        private int bulkstart;  // the offset of the bulk array to the node position
        private boolean fullyMarked;
        private Node next;
        
        public contentNodeIterator(long maxInitTime) throws IOException, kelondroException {
            // initialize markedDeleted set of deleted Handles
            markedDeleted = deletedHandles(maxInitTime);
            fullyMarked = (maxInitTime < 0);
            
            // seek first position according the delete node set
            pos = new Handle(0);
            while ((markedDeleted.contains(pos)) && (pos.index < USAGE.allCount())) pos.index++;
            
            // initialize bulk
            bulksize = Math.max(1, Math.min(65536 / recordsize, USAGE.allCount()));
            bulkstart = -bulksize;
            bulk = new byte[bulksize * recordsize];
            next = (hasNext0()) ? next0() : null;
        }

        public Object next() {
            Node n = next;
            next = next0();
            return n;
        }
        
        public boolean hasNext() {
            return next != null;
        }

        public boolean hasNext0() {
            return pos.index < USAGE.allCount();
        }
        
        public Node next0() {
            // read Objects until a non-deleted Node appears
            while (hasNext0()) {
                Node nn;
                try {
                    nn = next00();
                } catch (IOException e) {
                    serverLog.logSevere("kelondroRecords", filename + " failed with " + e.getMessage());
                    return null;
                }
                byte[] key = nn.getKey();
                if ((key == null) ||
                    ((key.length == 1) && (key[0] == (byte) 0x80)) || // the NUL pointer ('lost' chain terminator)
                    (key.length < 3) ||
                    ((key.length  > 3) && (key[2] == 0) && (key[3] == 0)) ||
                    ((key.length  > 3) && (key[0] == (byte) 0x80) && (key[1] == 0) && (key[2] == 0) && (key[3] == 0)) ||
                    ((key.length  > 0) && (key[0] == 0))              // a 'lost' pointer within a deleted-chain
                   ) {
                    // this is a deleted node; probably not commited with dispose
                    if (fullyMarked) try {USAGE.dispose(nn.handle);} catch (IOException e) {} // mark this now as deleted
                    continue;
                }
                return nn;
            }
            return null;
        }
        
        public Node next00() throws IOException {
            // see if the next record is in the bulk, and if not re-fill the bulk
            if ((pos.index - bulkstart) >= bulksize) {
                bulkstart = pos.index;
                int maxlength = Math.min(USAGE.allCount() - bulkstart, bulksize);
                entryFile.readFully(POS_NODES + bulkstart * recordsize, bulk, 0, maxlength * recordsize);
            }
                
            // read node from bulk
            Node n = new Node(new Handle(pos.index), bulk, (pos.index - bulkstart) * recordsize);
            pos.index++;
            while ((markedDeleted.contains(pos)) && (pos.index < USAGE.allCount())) pos.index++;
            return n;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }
    
    public synchronized void close() throws IOException {
        if (cacheHeaders == null) {
            if (recordTracker.get(this.filename) != null) {
                theLogger.severe("close(): file '" + this.filename + "' was tracked with record tracker, but it should not.");
            }
        } else {
            if (recordTracker.remove(this.filename) == null) {
                theLogger.severe("close(): file '" + this.filename + "' was not tracked with record tracker.");
            }
        }
        if (entryFile == null) {
            theLogger.severe("close(): file '" + this.filename + "' was closed before close was called.");
        } else {
            USAGE.writeused(true);
            this.entryFile.close();
            this.entryFile = null;
            this.cacheHeaders = null;
            theLogger.fine("file '" + this.filename + "' closed.");
        }
    }

    public void finalize() {
        try {
            if (entryFile != null) close();
        } catch (IOException e) { }
        this.entryFile = null;
    }

    protected final static String[] line2args(String line) {
        // parse the command line
        if ((line == null) || (line.length() == 0)) return null;
        String args[];
        StringTokenizer st = new StringTokenizer(line);

        args = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            args[i] = st.nextToken();
        }
        st = null;
        return args;
    }

    protected final static boolean equals(byte[] a, byte[] b) {
        if (a == b) return true;
        if ((a == null) || (b == null)) return false;
        if (a.length != b.length) return false;
        for (int n = 0; n < a.length; n++) if (a[n] != b[n]) return false;
        return true;
    }
    
    public final static void NUL2bytes(byte[] b, int offset) {
        b[offset    ] = (byte) (0XFF & (NUL >> 24));
        b[offset + 1] = (byte) (0XFF & (NUL >> 16));
        b[offset + 2] = (byte) (0XFF & (NUL >>  8));
        b[offset + 3] = (byte) (0XFF & NUL);
    }
    
    public final static void int2bytes(long i, byte[] b, int offset) {
        b[offset    ] = (byte) (0XFF & (i >> 24));
        b[offset + 1] = (byte) (0XFF & (i >> 16));
        b[offset + 2] = (byte) (0XFF & (i >>  8));
        b[offset + 3] = (byte) (0XFF & i);
    }
    
    public final static int bytes2int(byte[] b, int offset) {
        return (
            ((b[offset    ] & 0xff) << 24) |
            ((b[offset + 1] & 0xff) << 16) |
            ((b[offset + 2] & 0xff) << 8) |
             (b[offset + 3] & 0xff));  
    }

    public void print(boolean records) throws IOException {
        System.out.println("REPORT FOR FILE '" + this.filename + "':");
        System.out.println("--");
        System.out.println("CONTROL DATA");
        System.out.print("  HANDLES    : " + HANDLES.length + " int-values");
        if (HANDLES.length == 0)
            System.out.println();
        else {
            System.out.print("  {" + HANDLES[0].toString());
            for (int i = 1; i < HANDLES.length; i++)
                System.out.print(", " + HANDLES[i].toString());
            System.out.println("}");
        }
        System.out.print("  TXTPROPS   : " + TXTPROPS.length + " strings, max length " + TXTPROPW + " bytes");
        if (TXTPROPS.length == 0)
            System.out.println();
        else {
            System.out.print("  {'" + (new String(TXTPROPS[0])).trim());
            System.out.print("'");
            for (int i = 1; i < TXTPROPS.length; i++)
                System.out.print(", '" + (new String(TXTPROPS[i])).trim() + "'");
            System.out.println("}");
        }
        System.out.println("  USEDC      : " + USAGE.used());
        System.out.println("  FREEC      : " + USAGE.FREEC);
        System.out.println("  FREEH      : " + USAGE.FREEH.toString());
        System.out.println("  NUL repres.: 0x" + Integer.toHexString(NUL));
        System.out.println("  Data Offset: 0x" + Long.toHexString(POS_NODES));
        System.out.println("--");
        System.out.println("RECORDS");
        System.out.print("  Columns    : " + row().columns() + " columns  {" + ROW.width(0));
        for (int i = 1; i < row().columns(); i++) System.out.print(", " + ROW.width(i));
        System.out.println("}");
        System.out.println("  Overhead   : " + this.overhead + " bytes  (" + OHBYTEC + " OH bytes, " + OHHANDLEC + " OH Handles)");
        System.out.println("  Recordsize : " + this.recordsize + " bytes");
        System.out.println("--");
        System.out.println("DELETED HANDLES");
        Set dh =  deletedHandles(-1);
        Iterator dhi = dh.iterator();
        Handle h;
        while (dhi.hasNext()) {
            h = (Handle) dhi.next();
            System.out.print(h.index + ", ");
        }
        System.out.println("\n--");
        if (!(records)) return;
        
        // print also all records
        System.out.println("CACHE");
        printCache();
        System.out.println("--");
        System.out.println("NODES");
        for (int i = 0; i < USAGE.allCount(); i++)
            System.out.println("NODE: " + new Node(new Handle(i), (Node) null, 0, true).toString());
    }

    public String toString() {
        return size() + " RECORDS IN FILE " + filename;
    }
    
    protected final class Handle implements Comparable {
        protected int index;

        protected Handle(int i) {
            assert (i == NUL) || (i >= 0) : "node handle index too low: " + i;
            //assert (i == NUL) || (i < USAGE.allCount()) : "node handle index too high: " + i + ", USEDC=" + USAGE.USEDC + ", FREEC=" + USAGE.FREEC;
            this.index = i;
            //if ((USAGE != null) && (this.index != NUL)) USAGE.allocate(this.index);
        }
        
        public boolean isNUL() {
            return index == NUL;
        }
        
        public String toString() {
            if (index == NUL) return "NULL";
            String s = Integer.toHexString(index);
            while (s.length() < 4) s = "0" + s;
            return s;
        }

        public boolean equals(Handle h) {
            assert (index != NUL);
            assert (h.index != NUL);
            return (this.index == h.index);
        }

        public boolean equals(Object h) {
            assert (index != NUL);
            assert (((Handle) h).index != NUL);
            return (this.index == ((Handle) h).index);
        }

        public int compare(Object h0, Object h1) {
            assert (((Handle) h0).index != NUL);
            assert (((Handle) h1).index != NUL);
            if (((Handle) h0).index < ((Handle) h1).index) return -1;
            if (((Handle) h0).index > ((Handle) h1).index) return 1;
            return 0;
        }

        public int compareTo(Object h) {
            // this is needed for a TreeMap
            assert (index != NUL) : "this.index is NUL in compareTo";
            assert (((Handle) h).index != NUL) : "handle.index is NUL in compareTo";
            if (index < ((Handle) h).index) return -1;
            if (index > ((Handle) h).index) return 1;
            return 0;
        }

        public int hashCode() {
            assert (index != NUL);
            return this.index;
        }
    }

    public kelondroProfile[] profiles() {
        return new kelondroProfile[]{
                (cacheHeaders == null) ? new kelondroProfile() :
                cacheHeaders.profile(),
                entryFile.profile()
        };
    }
    
    public kelondroProfile profile() {
        return kelondroProfile.consolidate(profiles());
    }
    
}
