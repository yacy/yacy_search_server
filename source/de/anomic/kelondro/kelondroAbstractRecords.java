// kelondroTray.java
// (C) 2003 - 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2003 on http://yacy.net
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.anomic.kelondro.kelondroRow.EntryIndex;
import de.anomic.server.logging.serverLog;

public abstract class kelondroAbstractRecords implements kelondroRecords {

    // static seek pointers
    private   static int  LEN_DESCR      = 60;
    private   static long POS_MAGIC      = 0;                     // 1 byte, byte: file type magic
    private   static long POS_BUSY       = POS_MAGIC      + 1;    // 1 byte, byte: marker for synchronization
    private   static long POS_PORT       = POS_BUSY       + 1;    // 2 bytes, short: hint for remote db access
    private   static long POS_DESCR      = POS_PORT       + 2;    // 60 bytes, string: any description string
    private   static long POS_COLUMNS    = POS_DESCR      + LEN_DESCR; // 2 bytes, short: number of columns in one entry
    private   static long POS_OHBYTEC    = POS_COLUMNS    + 2;    // 2 bytes, number of extra bytes on each Node
    private   static long POS_OHHANDLEC  = POS_OHBYTEC    + 2;    // 2 bytes, number of Handles on each Node
    static long POS_USEDC      = POS_OHHANDLEC  + 2;    // 4 bytes, int: used counter
    static long POS_FREEC      = POS_USEDC      + 4;    // 4 bytes, int: free counter
    static long POS_FREEH      = POS_FREEC      + 4;    // 4 bytes, int: free pointer (to free chain start)
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

    // values that are only present at run-time
    protected String           filename;     // the database's file name
    protected kelondroIOChunks entryFile;    // the database file
    protected int              overhead;     // OHBYTEC + 4 * OHHANDLEC = size of additional control bytes
    protected int              headchunksize;// overheadsize + key element column size
    protected int              tailchunksize;// sum(all: COLWIDTHS) minus the size of the key element colum
    protected int              recordsize;   // (overhead + sum(all: COLWIDTHS)) = the overall size of a record
    byte[]           spaceChunk;   // a chunk of data that is used to reserve space within the file
    
    // dynamic run-time seek pointers
    private   long POS_HANDLES  = 0; // starts after end of POS_COLWIDHS which is POS_COLWIDTHS + COLWIDTHS.length * 4
    private   long POS_TXTPROPS = 0; // starts after end of POS_HANDLES which is POS_HANDLES + HANDLES.length * 4
    protected long POS_NODES    = 0; // starts after end of POS_TXTPROPS which is POS_TXTPROPS + TXTPROPS.length * TXTPROPW

    // dynamic variables that are back-ups of stored values in file; read/defined on instantiation
    protected usageControl      USAGE;       // counter for used and re-use records and pointer to free-list
    protected short             OHBYTEC;     // number of extra bytes in each node
    protected short             OHHANDLEC;   // number of handles in each node
    protected kelondroRow       ROW;         // array with widths of columns
    private   kelondroHandle    HANDLES[];   // array with handles
    private   byte[]            TXTPROPS[];  // array with text properties
    private   int               TXTPROPW;    // size of a single TXTPROPS element

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
        protected int            USEDC; // counter of used elements
        protected int            FREEC; // counter of free elements in list of free Nodes
        protected kelondroHandle FREEH; // pointer to first element in list of free Nodes, empty = NUL

        protected usageControl(final boolean init) throws IOException {
            if (init) {
                this.USEDC = 0;
                this.FREEC = 0;
                this.FREEH = new kelondroHandle(kelondroHandle.NUL);
            } else {
                readfree();
                readused();
                try {
                    final int rest = (int) ((entryFile.length() - POS_NODES) % recordsize);// == 0 : "rest = " + ((entryFile.length()  - POS_NODES) % ((long) recordsize)) + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename;
                    final int calculated_used = (int) ((entryFile.length() - POS_NODES) / recordsize);
                    if ((rest != 0) || (calculated_used != this.USEDC + this.FREEC)) {
                        theLogger.log(Level.WARNING, "USEDC inconsistency at startup: calculated_used = " + calculated_used + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename);
                        this.USEDC = calculated_used - this.FREEC;
                        if (this.USEDC < 0) {
                            theLogger.log(Level.WARNING, "USEDC inconsistency at startup: cannot recover " + filename);
                            throw new kelondroException("cannot recover inconsistency in " + filename);
                        }
                        writeused(true);
                    }
                } catch (final IOException e) {
                    assert false;
                }
            }
        }
        
        synchronized void writeused(final boolean finalwrite) throws IOException {
            // we write only at close time, not in between. othervise, the read/write head
            // needs to run up and own all the way between the beginning and the end of the
            // file for each record. We check consistency beteen file size and
            if (finalwrite) synchronized (entryFile) {
                assert this.USEDC >= 0 : "this.USEDC = " + this.USEDC;
                entryFile.writeInt(POS_USEDC, this.USEDC);
                entryFile.commit();
            }
        }
        
        private synchronized void writefree() throws IOException {
            //synchronized (entryFile) {
                entryFile.writeInt(POS_FREEC, FREEC);
                entryFile.writeInt(POS_FREEH, FREEH.index);
                entryFile.commit();
                checkConsistency();
            //}
        }
        
        private synchronized void readused() throws IOException {
            //synchronized (entryFile) {
                this.USEDC = entryFile.readInt(POS_USEDC);
                assert this.USEDC >= 0 : "this.USEDC = " + this.USEDC + ", filename = " + filename;
            //}
        }
        
        private synchronized void readfree() throws IOException {
            //synchronized (entryFile) {
                this.FREEC = entryFile.readInt(POS_FREEC);
                this.FREEH = new kelondroHandle(entryFile.readInt(POS_FREEH));
            //}
        }
        
        protected synchronized int allCount() {
            checkConsistency();
            return this.USEDC + this.FREEC;
        }
        
        synchronized int used() {
            checkConsistency();
            return this.USEDC;
        }
        
        protected synchronized void dispose(final kelondroHandle h) throws IOException {
            // delete element with handle h
            // this element is then connected to the deleted-chain and can be
            // re-used change counter
            assert (h.index >= 0);
            assert (h.index != kelondroHandle.NUL);
            //synchronized (USAGE) {
                synchronized (entryFile) {
                    assert (h.index < USEDC + FREEC) : "USEDC = " + USEDC + ", FREEC = " + FREEC + ", h.index = " + h.index;
                    final long sp = seekpos(h);
                    assert (sp <= entryFile.length() + ROW.objectsize) : h.index + "/" + sp + " exceeds file size " + entryFile.length();
                    USEDC--;
                    FREEC++;
                    // change pointer
                    entryFile.writeInt(sp, FREEH.index); // extend free-list
                    // write new FREEH Handle link
                    FREEH = h;
                    writefree();
                    writeused(false);
                }
            //}
        }
        
        protected synchronized int allocatePayload(byte[] chunk) throws IOException {
            // reserves a new record and returns index of record
            // the return value is not a seek position
            // the seek position can be retrieved using the seekpos() function
            if (chunk == null) {
                chunk = spaceChunk;
            }
            assert (chunk.length == ROW.objectsize) : "chunk.length = " + chunk.length + ", ROW.objectsize() = " + ROW.objectsize;
            //synchronized (USAGE) {
                synchronized (entryFile) {
                    if (USAGE.FREEC == 0) {
                        // generate new entry
                        final int index = USAGE.allCount();
                        entryFile.write(seekpos(index) + overhead, chunk, 0, ROW.objectsize); // occupy space, othervise the USAGE computaton does not work
                        USAGE.USEDC++;
                        writeused(false);
                        return index;
                    }
                    // re-use record from free-list
                    USAGE.USEDC++;
                    USAGE.FREEC--;
                    // take link
                    int index = USAGE.FREEH.index;
                    if (index == kelondroHandle.NUL) {
                        serverLog.logSevere("kelondroTray/" + filename, "INTERNAL ERROR (DATA INCONSISTENCY): re-use of records failed, lost " + (USAGE.FREEC + 1) + " records.");
                        // try to heal..
                        USAGE.USEDC = USAGE.allCount() + 1;
                        USAGE.FREEC = 0;
                        index = USAGE.USEDC - 1;
                    } else {
                        //System.out.println("*DEBUG* ALLOCATED DELETED INDEX " + index);
                        // check for valid seek position
                        final long seekp = seekpos(USAGE.FREEH);
                        if (seekp >= entryFile.length()) {
                            // this is a severe inconsistency. try to heal..
                            serverLog.logSevere("kelondroTray/" + filename, "new Handle: lost " + USAGE.FREEC + " marked nodes; seek position " + seekp + "/" + USAGE.FREEH.index + " out of file size " + entryFile.length() + "/" + ((entryFile.length() - POS_NODES) / recordsize));
                            index = USAGE.allCount(); // a place at the end of the file
                            USAGE.USEDC += USAGE.FREEC; // to avoid that non-empty records at the end are overwritten
                            USAGE.FREEC = 0; // discard all possible empty nodes
                            USAGE.FREEH.index = kelondroHandle.NUL;
                        } else {
                            // read link to next element of FREEH chain
                            USAGE.FREEH.index = entryFile.readInt(seekp);
                            assert ((USAGE.FREEH.index == kelondroHandle.NUL) && (USAGE.FREEC == 0)) || seekpos(USAGE.FREEH) < entryFile.length() : "allocatePayload: USAGE.FREEH.index = " + USAGE.FREEH.index + ", seekp = " + seekp;
                        }
                    }
                    USAGE.writeused(false);
                    USAGE.writefree();
                    entryFile.write(seekpos(index) + overhead, chunk, 0, ROW.objectsize); // overwrite space
                    return index;
                }
            //}
        }
        
        protected synchronized void allocateRecord(final int index, byte[] bulkchunk, int offset) throws IOException {
            // in case that the handle index was created outside this class,
            // this method ensures that the USAGE counters are consistent with the
            // new handle index
            if (bulkchunk == null) {
                bulkchunk = new byte[recordsize];
                offset = 0;
            }
            //assert (chunk.length == ROW.objectsize()) : "chunk.length = " + chunk.length + ", ROW.objectsize() = " + ROW.objectsize();
            //synchronized (USAGE) {
                synchronized (entryFile) {
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
                        kelondroHandle h;
                        while (index > USAGE.allCount()) {
                            h = new kelondroHandle(USAGE.allCount());
                            USAGE.FREEC++;
                            entryFile.write(seekpos(h), spaceChunk); // occupy space, othervise the USAGE computaton does not work
                            entryFile.writeInt(seekpos(h), USAGE.FREEH.index);
                            USAGE.FREEH = h;
                            assert ((USAGE.FREEH.index == kelondroHandle.NUL) && (USAGE.FREEC == 0)) || seekpos(USAGE.FREEH) < entryFile.length() : "allocateRecord: USAGE.FREEH.index = " + USAGE.FREEH.index;
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
            //}
        }
        
        private synchronized void checkConsistency() {
            if ((debugmode) && (entryFile != null)) try { // in debug mode
                final long efl = entryFile.length();
                assert ((efl - POS_NODES) % (recordsize)) == 0 : "rest = " + ((entryFile.length()  - POS_NODES) % (recordsize)) + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename;
                final long calculated_used = (efl - POS_NODES) / (recordsize);
                if (calculated_used != this.USEDC + this.FREEC) logFailure("INCONSISTENCY in USED computation: calculated_used = " + calculated_used + ", USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", recordsize = " + recordsize + ", file = " + filename);
            } catch (final IOException e) {
                assert false;
            }
        }
    }

    public static int staticsize(final File file) {
        if (!(file.exists())) return 0;
        try {
            final kelondroRA ra = new kelondroFileRA(file.getCanonicalPath());
            final kelondroIOChunks entryFile = new kelondroRAIOChunks(ra, ra.name());

            final int used = entryFile.readInt(POS_USEDC); // works only if consistency with file size is given
            entryFile.close();
            ra.close();
            return used;
        } catch (final FileNotFoundException e) {
            return 0;
        } catch (final IOException e) {
            return 0;
        }
    }

    
    public kelondroAbstractRecords(final File file, final boolean useNodeCache,
                           final short ohbytec, final short ohhandlec,
                           final kelondroRow rowdef, final int FHandles, final int txtProps, final int txtPropWidth) throws IOException {
        // opens an existing file or creates a new file
        // file: the file that shall be created
        // oha : overhead size array of four bytes: oha[0]=# of bytes, oha[1]=# of shorts, oha[2]=# of ints, oha[3]=# of longs, 
        // columns: array with size of column width; columns.length is number of columns
        // FHandles: number of integer properties
        // txtProps: number of text properties

        this.fileExisted = file.exists(); // can be used by extending class to track if this class created the file
        this.OHBYTEC   = ohbytec;
        this.OHHANDLEC = ohhandlec;
        this.ROW = rowdef; // create row
        this.TXTPROPW  = txtPropWidth;

        if (file.exists()) {
            // opens an existing tree
            this.filename = file.getCanonicalPath();
            final kelondroRA raf = new kelondroFileRA(this.filename);
            //kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename), 1024, 100);
            //kelondroRA raf = new kelondroCachedRA(new kelondroFileRA(this.filename), 5000000, 1000);
            //kelondroRA raf = new kelondroNIOFileRA(this.filename, (file.length() < 4000000), 10000);
            initExistingFile(raf, useNodeCache);
        } else {
            this.filename = file.getCanonicalPath();
            final kelondroRA raf = new kelondroFileRA(this.filename);
            // kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename), 1024, 100);
            // kelondroRA raf = new kelondroNIOFileRA(this.filename, false, 10000);
            initNewFile(raf, FHandles, txtProps);
        }
        assignRowdef(rowdef);
        if (fileExisted) {
        	final kelondroByteOrder oldOrder = readOrderType();
            if ((oldOrder != null) && (!(oldOrder.equals(rowdef.objectOrder)))) {
                writeOrderType(); // write new order type
                //throw new IOException("wrong object order upon initialization. new order is " + rowdef.objectOrder.toString() + ", old order was " + oldOrder.toString());
            }
        } else {
            // create new file structure
            writeOrderType();            
        }
    }

    public kelondroAbstractRecords(final kelondroRA ra, final String filename, final boolean useCache,
                           final short ohbytec, final short ohhandlec,
                           final kelondroRow rowdef, final int FHandles, final int txtProps, final int txtPropWidth,
                           final boolean exitOnFail) {
        // this always creates a new file
        this.fileExisted = false;
        this.filename = filename;
        this.OHBYTEC   = ohbytec;
        this.OHHANDLEC = ohhandlec;
        this.ROW = rowdef; // create row
        this.TXTPROPW  = txtPropWidth;
        
        try {
            initNewFile(ra, FHandles, txtProps);
        } catch (final IOException e) {
            logFailure("cannot create / " + e.getMessage());
            if (exitOnFail) System.exit(-1);
        }
        assignRowdef(rowdef);
        writeOrderType();
    }

    public void clear() throws IOException {
        kelondroRA ra = this.entryFile.getRA();
        final File f = ra.file();
        assert f != null;
        this.entryFile.close();
        f.delete();
        ra = new kelondroFileRA(f);
        initNewFile(ra, this.HANDLES.length, this.TXTPROPS.length);
    }
    
    private void initNewFile(final kelondroRA ra, final int FHandles, final int txtProps) throws IOException {

        // create new Chunked IO
        this.entryFile = new kelondroRAIOChunks(ra, ra.name());
        
        // store dynamic run-time data
        this.overhead = this.OHBYTEC + 4 * this.OHHANDLEC;
        this.recordsize = this.overhead + ROW.objectsize;
        this.headchunksize = overhead + ROW.width(0);
        this.tailchunksize = this.recordsize - this.headchunksize;
        this.spaceChunk = fillSpaceChunk(recordsize);

        // store dynamic run-time seek pointers
        POS_HANDLES = POS_COLWIDTHS + ROW.columns() * 4;
        POS_TXTPROPS = POS_HANDLES + FHandles * 4;
        POS_NODES = POS_TXTPROPS + txtProps * this.TXTPROPW;
        //System.out.println("*** DEBUG: POS_NODES = " + POS_NODES + " for " + filename);

        // store dynamic back-up variables
        USAGE     = new usageControl(true);
        HANDLES   = new kelondroHandle[FHandles];
        for (int i = 0; i < FHandles; i++) HANDLES[i] = new kelondroHandle(kelondroHandle.NUL);
        TXTPROPS  = new byte[txtProps][];
        for (int i = 0; i < txtProps; i++) TXTPROPS[i] = new byte[0];

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
        entryFile.writeInt(POS_TXTPROPW, this.TXTPROPW);

        // write configuration arrays
        for (int i = 0; i < this.ROW.columns(); i++) {
            entryFile.writeInt(POS_COLWIDTHS + 4 * i, this.ROW.width(i));
        }
        for (int i = 0; i < this.HANDLES.length; i++) {
            entryFile.writeInt(POS_HANDLES + 4 * i, kelondroHandle.NUL);
            HANDLES[i] = new kelondroHandle(kelondroHandle.NUL);
        }
        final byte[] ea = new byte[TXTPROPW];
        for (int j = 0; j < TXTPROPW; j++) ea[j] = 0;
        for (int i = 0; i < this.TXTPROPS.length; i++) {
            entryFile.write(POS_TXTPROPS + TXTPROPW * i, ea);
        }
        
        this.entryFile.commit();
    }

    private static final byte[] fillSpaceChunk(int size) {
        final byte[] chunk = new byte[size];
        while (--size >= 0) chunk[size] = (byte) 0xff;
        return chunk;
    }
    
    public void setDescription(final byte[] description) throws IOException {
        if (description.length > LEN_DESCR)
            entryFile.write(POS_DESCR, description, 0, LEN_DESCR);
        else
            entryFile.write(POS_DESCR, description);
    }
    
    public byte[] getDescription() throws IOException {
        final byte[] b = new byte[LEN_DESCR];
        entryFile.readFully(POS_DESCR, b, 0, LEN_DESCR);
        return b;
    }
    
    public void setLogger(final Logger newLogger) {
        this.theLogger = newLogger;
    }

    public void logWarning(final String message) {
        if (this.theLogger == null)
            System.err.println("KELONDRO WARNING " + this.filename + ": " + message);
        else
            this.theLogger.warning("KELONDRO WARNING " + this.filename + ": " + message);
    }

    public void logFailure(final String message) {
        if (this.theLogger == null)
            System.err.println("KELONDRO FAILURE " + this.filename + ": " + message);
        else
            this.theLogger.severe("KELONDRO FAILURE " + this.filename + ": " + message);
    }

    public void logFine(final String message) {
        if (this.theLogger == null)
            System.out.println("KELONDRO DEBUG " + this.filename + ": " + message);
        else
            this.theLogger.fine("KELONDRO DEBUG " + this.filename + ": " + message);
    }

    public kelondroAbstractRecords(final kelondroRA ra, final String filename, final boolean useNodeCache) throws IOException{
        this.fileExisted = false;
        this.filename = filename;
        initExistingFile(ra, useNodeCache);
        readOrderType();
    }

    private void initExistingFile(final kelondroRA ra, final boolean useBuffer) throws IOException {
        // read from Chunked IO
        if (useBuffer) {
            this.entryFile = new kelondroBufferedIOChunks(ra, ra.name(), 0, 30000 + random.nextLong() % 30000);
        } else {
            this.entryFile = new kelondroRAIOChunks(ra, ra.name());
        }

        // read dynamic variables that are back-ups of stored values in file;
        // read/defined on instantiation
        
        this.OHBYTEC = entryFile.readShort(POS_OHBYTEC);
        this.OHHANDLEC = entryFile.readShort(POS_OHHANDLEC);

        final kelondroColumn[] COLDEFS = new kelondroColumn[entryFile.readShort(POS_COLUMNS)];
        this.HANDLES = new kelondroHandle[entryFile.readInt(POS_INTPROPC)];
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
            HANDLES[i] = new kelondroHandle(entryFile.readInt(POS_HANDLES + 4 * i));
        }
        for (int i = 0; i < TXTPROPS.length; i++) {
            TXTPROPS[i] = new byte[TXTPROPW];
            entryFile.readFully(POS_TXTPROPS + TXTPROPW * i, TXTPROPS[i], 0, TXTPROPS[i].length);
        }
        this.ROW = new kelondroRow(COLDEFS, readOrderType(), 0);
        
        // assign remaining values that are only present at run-time
        this.overhead = OHBYTEC + 4 * OHHANDLEC;
        this.recordsize = this.overhead + ROW.objectsize;
        this.headchunksize = this.overhead + this.ROW.width(0);
        this.tailchunksize = this.recordsize - this.headchunksize;
        this.spaceChunk = fillSpaceChunk(recordsize);
        
        // init USAGE, must be done at the end because it needs the recordsize value
        this.USAGE = new usageControl(false);
    }
    
    private void writeOrderType() {
        try {
            setDescription((this.ROW.objectOrder == null) ? "__".getBytes() : this.ROW.objectOrder.signature().getBytes());
        } catch (final IOException e) {}
    }
    
    private kelondroByteOrder readOrderType() {
        try {
            final byte[] d = getDescription();
            final String s = new String(d).substring(0, 2);
            return kelondroNaturalOrder.orderBySignature(s);
        } catch (final IOException e) {
            return null;
        }
    }
    
    public String filename() {
        return filename;
    }
    
    public synchronized final byte[] bulkRead(final int start, final int end) throws IOException {
        // a bulk read simply reads a piece of memory from the record file
        // this makes only sense if there are no overhead bytes or pointer
        // the end value is OUTSIDE the record interval
        assert OHBYTEC == 0;
        assert OHHANDLEC == 0;
        assert start >= 0;
        assert end <= USAGE.allCount();
        final byte[] bulk = new byte[(end - start) * recordsize];
        final long bulkstart = POS_NODES + ((long) recordsize * (long) start);
        entryFile.readFully(bulkstart, bulk, 0, bulk.length);
        return bulk;
    }

    protected synchronized void deleteNode(final kelondroHandle handle) throws IOException {
        USAGE.dispose(handle);
    }
    
    protected void printChunk(final kelondroRow.Entry chunk) {
        for (int j = 0; j < chunk.columns(); j++)
            System.out.print(new String(chunk.getColBytes(j)) + ", ");
    }

    public final kelondroRow row() {
        return this.ROW;
    }
    
    private final void assignRowdef(final kelondroRow rowdef) {
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

    protected final long seekpos(final kelondroHandle handle) {
        assert (handle.index >= 0): "handle index too low: " + handle.index;
        return POS_NODES + ((long) recordsize * (long) handle.index);
    }
    
    protected final long seekpos(final int index) {
        assert (index >= 0): "handle index too low: " + index;
        return POS_NODES + ((long) recordsize * index);
    }

    // additional properties
    public final synchronized int handles() {
        return this.HANDLES.length;
    }

    protected final void setHandle(final int pos, kelondroHandle handle) throws IOException {
        if (pos >= HANDLES.length) throw new IllegalArgumentException("setHandle: handle array exceeded");
        if (handle == null) handle = new kelondroHandle(kelondroHandle.NUL);
        HANDLES[pos] = handle;
        entryFile.writeInt(POS_HANDLES + 4 * pos, handle.index);
    }

    protected final kelondroHandle getHandle(final int pos) {
        if (pos >= HANDLES.length) throw new IllegalArgumentException("getHandle: handle array exceeded");
        return (HANDLES[pos].index == kelondroHandle.NUL) ? null : HANDLES[pos];
    }

    // custom texts
    public final void setText(final int pos, byte[] text) throws IOException {
        if (pos >= TXTPROPS.length) throw new IllegalArgumentException("setText: text array exceeded");
        if (text == null) text = new byte[0];
        if (text.length > TXTPROPW) throw new IllegalArgumentException("setText: text lemgth exceeded");
        TXTPROPS[pos] = text;
        entryFile.write(POS_TXTPROPS + TXTPROPW * pos, text);
    }

    public final byte[] getText(final int pos) {
        if (pos >= TXTPROPS.length) throw new IllegalArgumentException("getText: text array exceeded");
        return TXTPROPS[pos];
    }

    // Returns true if this map contains no key-value mappings.
    public final boolean isEmpty() {
        return (USAGE.used() == 0);
    }

    // Returns the number of key-value mappings in this map.
    public synchronized int size() {
        return USAGE.used();
    }

    protected synchronized final int free() {
        return USAGE.FREEC;
    }
    
    protected final Set<kelondroHandle> deletedHandles(final long maxTime) throws kelondroException, IOException {
        // initialize set with deleted nodes; the set contains Handle-Objects
        // this may last only the given maxInitTime
        // if the initTime is exceeded, the method throws an kelondroException
        final TreeSet<kelondroHandle> markedDeleted = new TreeSet<kelondroHandle>();
        final long timeLimit = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        long seekp;
        synchronized (USAGE) {
            if (USAGE.FREEC != 0) {
                kelondroHandle h = USAGE.FREEH;
                long repair_position = POS_FREEH;
                while (h.index != kelondroHandle.NUL) {
                    // check handle
                    seekp = seekpos(h);
                    if (seekp > entryFile.length()) {
                        // repair last handle store position
                        this.theLogger.severe("KELONDRO WARNING " + this.filename + ": seek position " + seekp + "/" + h.index + " out of file size " + entryFile.length() + "/" + ((entryFile.length() - POS_NODES) / recordsize) + " after " + markedDeleted.size() + " iterations; patched wrong node");
                        entryFile.writeInt(repair_position, kelondroHandle.NUL);
                        return markedDeleted;
                    }

                    // handle seems to be correct. store handle
                    markedDeleted.add(h);
                    
                    // move to next handle
                    repair_position = seekp;
                    h = new kelondroHandle(entryFile.readInt(seekp));
                    if (h.index == kelondroHandle.NUL) break;
                    
                    // double-check for already stored handles: detect loops
                    if (markedDeleted.contains(h)) {
                        // loop detection
                        this.theLogger.severe("KELONDRO WARNING " + this.filename + ": FREE-Queue contains loops");
                        entryFile.writeInt(repair_position, kelondroHandle.NUL);
                        return markedDeleted;
                    }
                    
                    // this appears to be correct. go on.
                    if (System.currentTimeMillis() > timeLimit) throw new kelondroException(filename, "time limit of " + maxTime + " exceeded; > " + markedDeleted.size() + " deleted entries");
                }
                System.out.println("\nDEBUG: " + markedDeleted.size() + " deleted entries in " + entryFile.name());
            }
        }
        return markedDeleted;
    }
    
    
    public synchronized void close() {
        if (entryFile == null) {
            theLogger.severe("close(): file '" + this.filename + "' was closed before close was called.");
        } else try {
            USAGE.writeused(true);
            this.entryFile.close();
            theLogger.fine("file '" + this.filename + "' closed.");
        } catch (final IOException e) {
            theLogger.severe("file '" + this.filename + "': failed to close.");
            e.printStackTrace();
        }
        this.entryFile = null;
    }

    public void finalize() {
        if (entryFile != null) close();
        this.entryFile = null;
    }

    protected final static String[] line2args(final String line) {
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

    protected final static boolean equals(final byte[] a, final byte[] b) {
        if (a == b) return true;
        if ((a == null) || (b == null)) return false;
        if (a.length != b.length) return false;
        for (int n = 0; n < a.length; n++) if (a[n] != b[n]) return false;
        return true;
    }
    
    public final static void NUL2bytes(final byte[] b, final int offset) {
        b[offset    ] = (byte) (0XFF & (kelondroHandle.NUL >> 24));
        b[offset + 1] = (byte) (0XFF & (kelondroHandle.NUL >> 16));
        b[offset + 2] = (byte) (0XFF & (kelondroHandle.NUL >>  8));
        b[offset + 3] = (byte) (0XFF & kelondroHandle.NUL);
    }
    
    public final static void int2bytes(final long i, final byte[] b, final int offset) {
        b[offset    ] = (byte) (0XFF & (i >> 24));
        b[offset + 1] = (byte) (0XFF & (i >> 16));
        b[offset + 2] = (byte) (0XFF & (i >>  8));
        b[offset + 3] = (byte) (0XFF & i);
    }
    
    public final static int bytes2int(final byte[] b, final int offset) {
        return (
            ((b[offset    ] & 0xff) << 24) |
            ((b[offset + 1] & 0xff) << 16) |
            ((b[offset + 2] & 0xff) << 8) |
             (b[offset + 3] & 0xff));  
    }

    public void print() throws IOException {
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
        System.out.println("  NUL repres.: 0x" + Integer.toHexString(kelondroHandle.NUL));
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
        final Set<kelondroHandle> dh =  deletedHandles(-1);
        final Iterator<kelondroHandle> dhi = dh.iterator();
        kelondroHandle h;
        while (dhi.hasNext()) {
            h = dhi.next();
            System.out.print(h.index + ", ");
        }
        System.out.println("\n--");
    }

    public String toString() {
        return size() + " RECORDS IN FILE " + filename;
    }
    

    public final Iterator<EntryIndex> contentRows(final long maxInitTime) throws kelondroException {
        return new contentRowIterator(maxInitTime);
    }

     public final class contentRowIterator implements Iterator<EntryIndex> {
        // iterator that iterates all kelondroRow.Entry-objects in the file
        // all records that are marked as deleted are omitted
        
        private final Iterator<kelondroNode> nodeIterator;
        private EntryIndex nextEntry;
        
        public contentRowIterator(final long maxInitTime) {
            nodeIterator = contentNodes(maxInitTime);
            if (nodeIterator.hasNext()) nextEntry = next0(); else nextEntry = null;
        }

        public boolean hasNext() {
            return nextEntry != null;
        }

        public EntryIndex next0() {
            if (!nodeIterator.hasNext()) {
                return null;
            }
            try {
                final kelondroNode n = nodeIterator.next();
                return row().newEntryIndex(n.getValueRow(), n.handle().index);
            } catch (final IOException e) {
                throw new kelondroException(filename, e.getMessage());
            }
        }
        
        public EntryIndex next() {
            final EntryIndex ni = nextEntry;
            byte[] b;
            nextEntry = null;
            while (nodeIterator.hasNext()) {
                nextEntry = next0();
                if (nextEntry == null) break;
                b = nextEntry.bytes();
                if ((b[0] != -128) || (b[1] != 0)) break;
            }            
            return ni;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }
    
    protected final Iterator<kelondroNode> contentNodes(final long maxInitTime) throws kelondroException {
        // returns an iterator of Node-objects that are not marked as 'deleted'
        try {
            return new contentNodeIterator(maxInitTime);
        } catch (final IOException e) {
            return new HashSet<kelondroNode>().iterator();
        }
    }
    
    protected final class contentNodeIterator implements Iterator<kelondroNode> {
        // iterator that iterates all Node-objects in the file
        // all records that are marked as deleted are ommitted
        // this is probably also the fastest way to iterate all objects
        
        private final Set<kelondroHandle> markedDeleted;
        private final kelondroHandle pos;
        private final byte[] bulk;
        private final int bulksize;
        private int bulkstart;  // the offset of the bulk array to the node position
        private final boolean fullyMarked;
        private kelondroNode next;
        
        public contentNodeIterator(final long maxInitTime) throws IOException, kelondroException {
            // initialize markedDeleted set of deleted Handles
            markedDeleted = deletedHandles(maxInitTime);
            fullyMarked = (maxInitTime < 0);
            
            // seek first position according the delete node set
            pos = new kelondroHandle(0);
            while ((markedDeleted.contains(pos)) && (pos.index < USAGE.allCount())) pos.index++;
            
            // initialize bulk
            bulksize = Math.max(1, Math.min(65536 / recordsize, USAGE.allCount()));
            bulkstart = -bulksize;
            bulk = new byte[bulksize * recordsize];
            next = (hasNext0()) ? next0() : null;
        }

        public kelondroNode next() {
            final kelondroNode n = next;
            next = next0();
            return n;
        }
        
        public boolean hasNext() {
            return next != null;
        }

        public boolean hasNext0() {
            return pos.index < USAGE.allCount();
        }
        
        public kelondroNode next0() {
            // read Objects until a non-deleted Node appears
            while (hasNext0()) {
                kelondroNode nn;
                try {
                    nn = next00();
                } catch (final IOException e) {
                    serverLog.logSevere("kelondroCachedRecords", filename + " failed with " + e.getMessage(), e);
                    return null;
                }
                final byte[] key = nn.getKey();
                if ((key == null) ||
                    ((key.length == 1) && (key[0] == (byte) 0x80)) || // the NUL pointer ('lost' chain terminator)
                    (key.length < 3) ||
                    ((key.length  > 3) && (key[2] == 0) && (key[3] == 0)) ||
                    ((key.length  > 3) && (key[0] == (byte) 0x80) && (key[1] == 0) && (key[2] == 0) && (key[3] == 0)) ||
                    ((key.length  > 0) && (key[0] == 0))              // a 'lost' pointer within a deleted-chain
                   ) {
                    // this is a deleted node; probably not commited with dispose
                    if (fullyMarked) try {USAGE.dispose(nn.handle());} catch (final IOException e) {} // mark this now as deleted
                    continue;
                }
                return nn;
            }
            return null;
        }
        
        public kelondroNode next00() throws IOException {
            // see if the next record is in the bulk, and if not re-fill the bulk
            if (pos.index >= (bulkstart + bulksize)) {
                bulkstart = pos.index;
                final int maxlength = Math.min(USAGE.allCount() - bulkstart, bulksize);
                if (((POS_NODES) + ((long) bulkstart) * ((long) recordsize)) < 0)
                    serverLog.logSevere("kelondroCachedRecords", "DEBUG: negative offset. POS_NODES = " + POS_NODES + ", bulkstart = " + bulkstart + ", recordsize = " + recordsize);
                if ((maxlength * recordsize) < 0)
                    serverLog.logSevere("kelondroCachedRecords", "DEBUG: negative length. maxlength = " + maxlength + ", recordsize = " + recordsize);
                entryFile.readFully((POS_NODES) + ((long) bulkstart) * ((long) recordsize), bulk, 0, maxlength * recordsize);
            }
            /* POS_NODES = 302, bulkstart = 3277, recordsize = 655386
               POS_NODES = 302, bulkstart = 820, recordsize = 2621466
               POS_NODES = 302, bulkstart = 13106, recordsize = 163866 */                
            // read node from bulk
            final kelondroNode n = newNode(new kelondroHandle(pos.index), bulk, (pos.index - bulkstart) * recordsize);
            pos.index++;
            while ((markedDeleted.contains(pos)) && (pos.index < USAGE.allCount())) pos.index++;
            return n;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }
    
    public static byte[] trimCopy(final byte[] a, final int offset, int length) {
        if (length > a.length - offset) length = a.length - offset;
        while ((length > 0) && (a[offset + length - 1] == 0)) length--;
        if (length == 0) return null;
        final byte[] b = new byte[length];
        System.arraycopy(a, offset, b, 0, length);
        return b;
    }
    
    public abstract kelondroNode newNode(kelondroHandle handle, byte[] bulk, int offset) throws IOException;
    
}
