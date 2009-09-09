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


package de.anomic.kelondro.table;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.anomic.kelondro.index.Column;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.Row.EntryIndex;
import de.anomic.kelondro.io.chunks.IOChunksInterface;
import de.anomic.kelondro.io.chunks.RandomAccessIOChunks;
import de.anomic.kelondro.io.random.CachedFileWriter;
import de.anomic.kelondro.io.random.Writer;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.kelondroException;
import de.anomic.yacy.logging.Log;

public class Records {
    
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
    public String           filename;     // the database's file name
    protected IOChunksInterface entryFile;    // the database file
    protected int              overhead;     // OHBYTEC + 4 * OHHANDLEC = size of additional control bytes
    protected int              headchunksize;// overheadsize + key element column size
    protected int              tailchunksize;// sum(all: COLWIDTHS) minus the size of the key element colum
    protected int              recordsize;   // (overhead + sum(all: COLWIDTHS)) = the overall size of a record
    //byte[]           spaceChunk;   // a chunk of data that is used to reserve space within the file
    
    // dynamic run-time seek pointers
    private   long POS_HANDLES  = 0; // starts after end of POS_COLWIDHS which is POS_COLWIDTHS + COLWIDTHS.length * 4
    private   long POS_TXTPROPS = 0; // starts after end of POS_HANDLES which is POS_HANDLES + HANDLES.length * 4
    protected long POS_NODES    = 0; // starts after end of POS_TXTPROPS which is POS_TXTPROPS + TXTPROPS.length * TXTPROPW

    // dynamic variables that are back-ups of stored values in file; read/defined on instantiation
    protected usageControl      USAGE;       // counter for used and re-use records and pointer to free-list
    protected short             OHBYTEC;     // number of extra bytes in each node
    protected short             OHHANDLEC;   // number of handles in each node
    protected Row       ROW;         // array with widths of columns
    private   Handle    HANDLES[];   // array with handles
    private   byte[]            TXTPROPS[];  // array with text properties
    private   int               TXTPROPW;    // size of a single TXTPROPS element

    // optional logger
    protected Logger theLogger = Logger.getLogger("KELONDRO"); // default logger
    
    // tracking of file cration
    protected boolean fileExisted;
    
    // Random. This is used to shift flush-times of write-buffers to differrent time
    //private static Random random = new Random(System.currentTimeMillis());

    // check for debug mode
    public static boolean debugmode = false;
    static {
        assert debugmode = true;
    }
    
    protected final class usageControl {
        protected int            USEDC; // counter of used elements
        protected int            FREEC; // counter of free elements in list of free Nodes
        protected Handle FREEH; // pointer to first element in list of free Nodes, empty = NUL

        protected usageControl(final boolean init) throws IOException {
            if (init) {
                this.USEDC = 0;
                this.FREEC = 0;
                this.FREEH = new Handle(Handle.NUL);
            } else {
                readusedfree();
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
            synchronized (entryFile) {
                entryFile.writeInt(POS_FREEC, FREEC);
                entryFile.writeInt(POS_FREEH, FREEH.index);
                entryFile.commit();
                checkConsistency();
            }
        }
        
        private synchronized void readusedfree() throws IOException {
            synchronized (entryFile) {
                this.USEDC = entryFile.readInt(POS_USEDC);
                assert this.USEDC >= 0 : "this.USEDC = " + this.USEDC + ", filename = " + filename;
                int freeh = entryFile.readInt(POS_FREEH);
                if (freeh > this.USEDC) {
                	logFailure("INCONSISTENCY in FREEH reading: USEDC = " + this.USEDC + ", FREEC = " + this.FREEC  + ", this.FREEH = " + freeh + ", file = " + filename);
                	this.FREEH = new Handle(Handle.NUL);
                	this.FREEC = 0;
                	entryFile.writeInt(POS_FREEC, FREEC);
                    entryFile.writeInt(POS_FREEH, FREEH.index);
                    entryFile.commit();
                } else {
                	this.FREEH = new Handle(freeh);
                	this.FREEC = entryFile.readInt(POS_FREEC);
                }
            }
        }
        
        protected synchronized int allCount() {
            checkConsistency();
            return this.USEDC + this.FREEC;
        }
        
        synchronized int used() {
            checkConsistency();
            return this.USEDC;
        }
        
        protected synchronized void dispose(final Handle h) throws IOException {
            // delete element with handle h
            // this element is then connected to the deleted-chain and can be
            // re-used change counter
            assert (h.index >= 0);
            assert (h.index != Handle.NUL);
            synchronized (USAGE) {
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
            }
        }
        
        protected synchronized int allocatePayload(byte[] chunk0) throws IOException {
            // reserves a new record and returns index of record
            // the return value is not a seek position
            // the seek position can be retrieved using the seekpos() function
            assert (chunk0 == null || chunk0.length == ROW.objectsize) : "chunk.length = " + chunk0.length + ", ROW.objectsize() = " + ROW.objectsize;
            synchronized (USAGE) {
                synchronized (entryFile) {
                    if (USAGE.FREEC == 0) {
                        // generate new entry
                        final int index = USAGE.allCount();
                        if (chunk0 == null) {
                        	entryFile.writeSpace(seekpos(index) + overhead, ROW.objectsize);
                        } else {
                        	entryFile.write(seekpos(index) + overhead, chunk0, 0, ROW.objectsize); // occupy space, otherwise the USAGE computation does not work
                        }
                        USAGE.USEDC++;
                        writeused(false);
                        return index;
                    }
                    // re-use record from free-list
                    USAGE.USEDC++;
                    USAGE.FREEC--;
                    // take link
                    int index = USAGE.FREEH.index;
                    if (index == Handle.NUL) {
                        Log.logSevere("kelondroAbstractRecords/" + filename, "INTERNAL ERROR (DATA INCONSISTENCY): re-use of records failed, lost " + (USAGE.FREEC + 1) + " records.");
                        // try to heal..
                        USAGE.USEDC = (int) ((entryFile.length() - POS_NODES) / recordsize);
                        index = USAGE.USEDC;
                        USAGE.USEDC++;
                        USAGE.FREEC = 0;
                        //entryFile.write(seekpos(index) + overhead, spaceChunk, 0, ROW.objectsize); // overwrite space
                    } else {
                        // check for valid seek position
                        final long seekp = seekpos(USAGE.FREEH);
                        if (seekp >= entryFile.length()) {
                            // this is a severe inconsistency. try to heal..
                            Log.logSevere("kelondroTray/" + filename, "new Handle: lost " + USAGE.FREEC + " marked nodes; seek position " + seekp + "/" + USAGE.FREEH.index + " out of file size " + entryFile.length() + "/" + ((entryFile.length() - POS_NODES) / recordsize));
                            index = USAGE.allCount(); // a place at the end of the file
                            USAGE.USEDC += USAGE.FREEC; // to avoid that non-empty records at the end are overwritten
                            USAGE.FREEC = 0; // discard all possible empty nodes
                            USAGE.FREEH.index = Handle.NUL;
                        } else {
                            // read link to next element of FREEH chain
                            USAGE.FREEH.index = entryFile.readInt(seekp);
                            // check consistency
                            if (((USAGE.FREEH.index != Handle.NUL) || (USAGE.FREEC != 0)) && seekpos(USAGE.FREEH) >= entryFile.length()) {
                                // the FREEH pointer cannot be correct, because it points to a place outside of the file.
                                // to correct this, we reset the FREH pointer and return a index that has been calculated as if USAGE.FREE == 0
                                Log.logSevere("kelondroAbstractRecords/" + filename, "INTERNAL ERROR (DATA INCONSISTENCY): USAGE.FREEH.index = " + USAGE.FREEH.index + ", entryFile.length() = " + entryFile.length() + "; wrong FREEH has been patched, lost " + (USAGE.FREEC + 1) + " records.");
                                // try to heal..
                                USAGE.USEDC = (int) ((entryFile.length() - POS_NODES) / recordsize);
                                index = USAGE.USEDC;
                                USAGE.USEDC++;
                                USAGE.FREEC = 0;
                                //entryFile.write(seekpos(index) + overhead, spaceChunk, 0, ROW.objectsize); // overwrite space
                            }
                        }
                    }
                    if (chunk0 == null) {
                    	entryFile.writeSpace(seekpos(index) + overhead, ROW.objectsize);
                    } else {
                    	entryFile.write(seekpos(index) + overhead, chunk0, 0, ROW.objectsize); // overwrite space
                    }
                    USAGE.writeused(false);
                    USAGE.writefree();
                    return index;
                }
            }
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
            synchronized (USAGE) {
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
                        Handle h;
                        while (index > USAGE.allCount()) {
                            h = new Handle(USAGE.allCount());
                            USAGE.FREEC++;
                            entryFile.writeSpace(seekpos(h), overhead + ROW.objectsize); // occupy space, otherwise the USAGE computation does not work
                            entryFile.writeInt(seekpos(h), USAGE.FREEH.index);
                            USAGE.FREEH = h;
                            assert ((USAGE.FREEH.index == Handle.NUL) && (USAGE.FREEC == 0)) || seekpos(USAGE.FREEH) < entryFile.length() : "allocateRecord: USAGE.FREEH.index = " + USAGE.FREEH.index;
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
            final Writer ra = new CachedFileWriter(new File(file.getCanonicalPath()));
            final IOChunksInterface entryFile = new RandomAccessIOChunks(ra, ra.name());

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

    public Records(final File file,
                           final short ohbytec, final short ohhandlec,
                           final Row rowdef, final int FHandles,
                           final int txtProps, final int txtPropWidth) throws IOException {
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
            Writer raf = new CachedFileWriter(new File(this.filename));
            //kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename), 1024, 100);
            //kelondroRA raf = new kelondroCachedRA(new kelondroFileRA(this.filename), 5000000, 1000);
            //kelondroRA raf = new kelondroNIOFileRA(this.filename, (file.length() < 4000000), 10000);
            //raf = new kelondroCachedRA(raf);
            initExistingFile(raf);
        } else {
            this.filename = file.getCanonicalPath();
            final Writer raf = new CachedFileWriter(new File(this.filename));
            // kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename), 1024, 100);
            // kelondroRA raf = new kelondroNIOFileRA(this.filename, false, 10000);
            initNewFile(raf, FHandles, txtProps);
        }
        assignRowdef(rowdef);
        if (fileExisted) {
        	final ByteOrder oldOrder = readOrderType();
            if ((oldOrder != null) && (!(oldOrder.equals(rowdef.objectOrder)))) {
                writeOrderType(); // write new order type
                //throw new IOException("wrong object order upon initialization. new order is " + rowdef.objectOrder.toString() + ", old order was " + oldOrder.toString());
            }
        } else {
            // create new file structure
            writeOrderType();            
        }
    }

    public void clear() throws IOException {
        Writer ra = this.entryFile.getRA();
        final File f = ra.file();
        assert f != null;
        this.entryFile.close();
        FileUtils.deletedelete(f);
        ra = new CachedFileWriter(f);
        initNewFile(ra, this.HANDLES.length, this.TXTPROPS.length);
    }
    
    private void initNewFile(final Writer ra, final int FHandles, final int txtProps) throws IOException {

        // create new Chunked IO
        this.entryFile = new RandomAccessIOChunks(ra, ra.name());
        
        // store dynamic run-time data
        this.overhead = this.OHBYTEC + 4 * this.OHHANDLEC;
        this.recordsize = this.overhead + ROW.objectsize;
        this.headchunksize = overhead + ROW.width(0);
        this.tailchunksize = this.recordsize - this.headchunksize;

        // store dynamic run-time seek pointers
        POS_HANDLES = POS_COLWIDTHS + ROW.columns() * 4;
        POS_TXTPROPS = POS_HANDLES + FHandles * 4L;
        POS_NODES = POS_TXTPROPS + txtProps * (long) this.TXTPROPW;
        //System.out.println("*** DEBUG: POS_NODES = " + POS_NODES + " for " + filename);

        // store dynamic back-up variables
        USAGE     = new usageControl(true);
        HANDLES   = new Handle[FHandles];
        for (int i = 0; i < FHandles; i++) HANDLES[i] = new Handle(Handle.NUL);
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
            entryFile.writeInt(POS_HANDLES + 4 * i, Handle.NUL);
            HANDLES[i] = new Handle(Handle.NUL);
        }
        final byte[] ea = new byte[TXTPROPW];
        for (int j = 0; j < TXTPROPW; j++) ea[j] = 0;
        for (int i = 0; i < this.TXTPROPS.length; i++) {
            entryFile.write(POS_TXTPROPS + TXTPROPW * i, ea);
        }
        
        this.entryFile.commit();
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

    private void initExistingFile(final Writer ra) throws IOException {
        // read from Chunked IO
        //useBuffer = false;
        /*if (useBuffer) {
            this.entryFile = new BufferedIOChunks(ra, ra.name(), 1024*1024, 30000 + random.nextLong() % 30000);
        } else {*/
            this.entryFile = new RandomAccessIOChunks(ra, ra.name());
        //}

        // read dynamic variables that are back-ups of stored values in file;
        // read/defined on instantiation
        
        this.OHBYTEC = entryFile.readShort(POS_OHBYTEC);
        this.OHHANDLEC = entryFile.readShort(POS_OHHANDLEC);

        final Column[] COLDEFS = new Column[entryFile.readShort(POS_COLUMNS)];
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
            COLDEFS[i] = new Column("col-" + i, Column.celltype_binary, Column.encoder_bytes, entryFile.readInt(POS_COLWIDTHS + 4 * i), "");
        }
        for (int i = 0; i < HANDLES.length; i++) {
            HANDLES[i] = new Handle(entryFile.readInt(POS_HANDLES + 4 * i));
        }
        for (int i = 0; i < TXTPROPS.length; i++) {
            TXTPROPS[i] = new byte[TXTPROPW];
            entryFile.readFully(POS_TXTPROPS + TXTPROPW * i, TXTPROPS[i], 0, TXTPROPS[i].length);
        }
        this.ROW = new Row(COLDEFS, readOrderType());
        
        // assign remaining values that are only present at run-time
        this.overhead = OHBYTEC + 4 * OHHANDLEC;
        this.recordsize = this.overhead + ROW.objectsize;
        this.headchunksize = this.overhead + this.ROW.width(0);
        this.tailchunksize = this.recordsize - this.headchunksize;
        
        // init USAGE, must be done at the end because it needs the recordsize value
        this.USAGE = new usageControl(false);
    }
    
    private void writeOrderType() {
        try {
            setDescription((this.ROW.objectOrder == null) ? "__".getBytes() : this.ROW.objectOrder.signature().getBytes());
        } catch (final IOException e) {}
    }
    
    private ByteOrder readOrderType() {
        try {
            final byte[] d = getDescription();
            final String s = new String(d).substring(0, 2);
            return NaturalOrder.orderBySignature(s);
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

    protected synchronized void deleteNode(final Handle handle) throws IOException {
        USAGE.dispose(handle);
    }
    
    protected void printChunk(final Row.Entry chunk) {
        for (int j = 0; j < chunk.columns(); j++)
            System.out.print(new String(chunk.getColBytes(j)) + ", ");
    }

    public final Row row() {
        return this.ROW;
    }
    
    private final void assignRowdef(final Row rowdef) {
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

    protected final long seekpos(final Handle handle) {
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

    protected final void setHandle(final int pos, Handle handle) throws IOException {
        if (pos >= HANDLES.length) throw new IllegalArgumentException("setHandle: handle array exceeded");
        if (handle == null) handle = new Handle(Handle.NUL);
        HANDLES[pos] = handle;
        entryFile.writeInt(POS_HANDLES + 4L * pos, handle.index);
    }

    protected final Handle getHandle(final int pos) {
        if (pos >= HANDLES.length) throw new IllegalArgumentException("getHandle: handle array exceeded");
        return (HANDLES[pos].index == Handle.NUL) ? null : HANDLES[pos];
    }

    // custom texts
    public final void setText(final int pos, byte[] text) throws IOException {
        if (pos >= TXTPROPS.length) throw new IllegalArgumentException("setText: text array exceeded");
        if (text == null) text = new byte[0];
        if (text.length > TXTPROPW) throw new IllegalArgumentException("setText: text lemgth exceeded");
        TXTPROPS[pos] = text;
        entryFile.write(POS_TXTPROPS + TXTPROPW * (long) pos, text);
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

    public synchronized final int free() {
        return USAGE.FREEC;
    }
    
    protected final Set<Handle> deletedHandles(final long maxTime) throws IOException {
        // initialize set with deleted nodes; the set contains Handle-Objects
        // this may last only the given maxInitTime
        // if the initTime is exceeded, the method returns what it found so far
        final TreeSet<Handle> markedDeleted = new TreeSet<Handle>();
        final long timeLimit = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        long seekp;
        synchronized (USAGE) {
            if (USAGE.FREEC != 0) {
                Handle h = USAGE.FREEH;
                long repair_position = POS_FREEH;
                while (h.index != Handle.NUL) {
                    // check handle
                    seekp = seekpos(h);
                    if (seekp > entryFile.length()) {
                        // repair last handle store position
                        this.theLogger.severe("KELONDRO WARNING " + this.filename + ": seek position " + seekp + "/" + h.index + " out of file size " + entryFile.length() + "/" + ((entryFile.length() - POS_NODES) / recordsize) + " after " + markedDeleted.size() + " iterations; patched wrong node");
                        entryFile.writeInt(repair_position, Handle.NUL);
                        return markedDeleted;
                    }

                    // handle seems to be correct. store handle
                    markedDeleted.add(h);
                    
                    // move to next handle
                    repair_position = seekp;
                    h = new Handle(entryFile.readInt(seekp));
                    if (h.index == Handle.NUL) break;
                    
                    // double-check for already stored handles: detect loops
                    if (markedDeleted.contains(h)) {
                        // loop detection
                        this.theLogger.severe("KELONDRO WARNING " + this.filename + ": FREE-Queue contains loops");
                        entryFile.writeInt(repair_position, Handle.NUL);
                        return markedDeleted;
                    }
                    
                    // this appears to be correct. go on.
                    if (System.currentTimeMillis() > timeLimit) return markedDeleted;
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

    protected void finalize() {
        if (entryFile != null) close();
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
        b[offset    ] = (byte) (0XFF & (Handle.NUL >> 24));
        b[offset + 1] = (byte) (0XFF & (Handle.NUL >> 16));
        b[offset + 2] = (byte) (0XFF & (Handle.NUL >>  8));
        b[offset + 3] = (byte) (0XFF & Handle.NUL);
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
        System.out.println("  NUL repres.: 0x" + Integer.toHexString(Handle.NUL));
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
        final Set<Handle> dh =  deletedHandles(-1);
        final Iterator<Handle> dhi = dh.iterator();
        Handle h;
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
        
        private final Iterator<Node> nodeIterator;
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
                final Node n = nodeIterator.next();
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
    
    protected final Iterator<Node> contentNodes(final long maxInitTime) throws kelondroException {
        // returns an iterator of Node-objects that are not marked as 'deleted'
        try {
            return new contentNodeIterator(maxInitTime);
        } catch (final IOException e) {
            return new HashSet<Node>().iterator();
        }
    }
    
    protected final class contentNodeIterator implements Iterator<Node> {
        // iterator that iterates all Node-objects in the file
        // all records that are marked as deleted are ommitted
        // this is probably also the fastest way to iterate all objects
        
        private final Set<Handle> markedDeleted;
        private final Handle pos;
        private final byte[] bulk;
        private final int bulksize;
        private int bulkstart;  // the offset of the bulk array to the node position
        private final boolean fullyMarked;
        private Node next;
        
        public contentNodeIterator(final long maxInitTime) throws IOException {
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

        public Node next() {
            final Node n = next;
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
                } catch (final IOException e) {
                    Log.logSevere("kelondroAbstractRecords", filename + " failed with " + e.getMessage(), e);
                    return null;
                }
                byte[] key = null;
                try {
                    key = nn.getKey();
                } catch (IOException e1) {
                    return null;
                }
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
        
        public Node next00() throws IOException {
            // see if the next record is in the bulk, and if not re-fill the bulk
            if (pos.index >= (bulkstart + bulksize)) {
                bulkstart = pos.index;
                final int maxlength = Math.min(USAGE.allCount() - bulkstart, bulksize);
                if (((POS_NODES) + ((long) bulkstart) * ((long) recordsize)) < 0)
                    Log.logSevere("kelondroAbstractRecords", "DEBUG: negative offset. POS_NODES = " + POS_NODES + ", bulkstart = " + bulkstart + ", recordsize = " + recordsize);
                if ((maxlength * recordsize) < 0)
                    Log.logSevere("kelondroAbstractRecords", "DEBUG: negative length. maxlength = " + maxlength + ", recordsize = " + recordsize);
                entryFile.readFully((POS_NODES) + ((long) bulkstart) * ((long) recordsize), bulk, 0, maxlength * recordsize);
            }
            /* POS_NODES = 302, bulkstart = 3277, recordsize = 655386
               POS_NODES = 302, bulkstart = 820, recordsize = 2621466
               POS_NODES = 302, bulkstart = 13106, recordsize = 163866 */                
            // read node from bulk
            final Node n = newNode(new Handle(pos.index), bulk, (pos.index - bulkstart) * recordsize);
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
    
    public void deleteOnExit() {
        this.entryFile.deleteOnExit();
    }
    
    public Node newNode(final Handle handle, final byte[] bulk, final int offset) throws IOException {
        return new Node(handle, bulk, offset);
    }
    
    public final class Node {
        private Handle handle = null; // index of the entry, by default NUL means undefined
        private byte[] ohChunk = null; // contains overhead values
        private byte[] bodyChunk = null; // contains all row values
        private boolean ohChanged = false;
        private boolean bodyChanged = false;

        public Node(final byte[] rowinstance) throws IOException {
            // this initializer is used to create nodes from bulk-read byte arrays
            assert ((rowinstance == null) || (rowinstance.length == ROW.objectsize)) : "bulkchunk.length = " + (rowinstance == null ? "null" : rowinstance.length) + ", ROW.width(0) = " + ROW.width(0);
            this.handle = new Handle(USAGE.allocatePayload(rowinstance));
            
            // create chunks
            this.ohChunk = new byte[overhead];
            this.bodyChunk = new byte[ROW.objectsize];
            for (int i = this.ohChunk.length - 1; i >= 0; i--) this.ohChunk[i] = (byte) 0xff;
            if (rowinstance == null) {
                for (int i = this.bodyChunk.length - 1; i >= 0; i--) this.bodyChunk[i] = (byte) 0xff;
            } else {
               System.arraycopy(rowinstance, 0, this.bodyChunk, 0, this.bodyChunk.length);
            }
            
            // mark chunks as not changed, we wrote that already during allocatePayload
            this.ohChanged = false;
            this.bodyChanged = false;
        }
        
        public Node(final Handle handle, final byte[] bulkchunk, final int offset) throws IOException {
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
            assert ((bulkchunk == null) || (bulkchunk.length - offset >= recordsize)) : "bulkchunk.length = " + (bulkchunk == null ? "null" : bulkchunk.length) + ", offset = " + offset + ", recordsize = " + recordsize;
            
            /*if ((offset == 0) && (overhead == 0) && ((bulkchunk == null) || (bulkchunk.length == ROW.objectsize()))) {
                this.ohChunk = new byte[0];
                if (bulkchunk == null) {
                    this.bodyChunk = new byte[ROW.objectsize()];
                } else {
                    this.bodyChunk = bulkchunk;
                }
            } else { */
                // create empty chunks
                this.ohChunk = new byte[overhead];
                this.bodyChunk = new byte[ROW.objectsize];
                
                // write content to chunks
                if (bulkchunk != null) {
                    if (overhead > 0) System.arraycopy(bulkchunk, offset, this.ohChunk, 0, overhead);
                    System.arraycopy(bulkchunk, offset + overhead, this.bodyChunk, 0, ROW.objectsize);
                }
            //}
            
            // mark chunks as changed
            this.ohChanged = changed;
            this.bodyChanged = changed;
        }
        
        public Node(final Handle handle) throws IOException {
            // this creates an entry with an pre-reserved entry position.
            // values can be written using the setValues() method,
            // but we expect that values are already there in the file.
            assert (handle != null): "node handle is null";
            assert (handle.index >= 0): "node handle too low: " + handle.index;
           
            if (handle == null) throw new kelondroException(filename, "INTERNAL ERROR: node handle is null.");
            if (handle.index >= USAGE.allCount()) {
                throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. No auto-fix node was submitted. This is a serious failure.");
            }

            // use given handle
            this.handle = new Handle(handle.index);

            // read record
            this.ohChunk = new byte[overhead];
            if (overhead > 0) entryFile.readFully(seekpos(this.handle), this.ohChunk, 0, overhead);
            this.bodyChunk = null; /*new byte[ROW.objectsize];
            entryFile.readFully(seekpos(this.handle) + overhead, this.bodyChunk, 0, this.bodyChunk.length);
            */
            // mark chunks as not changed
            this.ohChanged = false;
            this.bodyChanged = false;
        }
        
        public Handle handle() {
            // if this entry has an index, return it
            if (this.handle.index == Handle.NUL) throw new kelondroException(filename, "the entry has no index assigned");
            return this.handle;
        }

        public void setOHByte(final int i, final byte b) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("setOHByte: wrong index " + i);
            if (this.handle.index == Handle.NUL) throw new kelondroException(filename, "setOHByte: no handle assigned");
            this.ohChunk[i] = b;
            this.ohChanged = true;
        }
        
        public void setOHHandle(final int i, final Handle otherhandle) {
            assert (i < OHHANDLEC): "setOHHandle: wrong array size " + i;
            assert (this.handle.index != Handle.NUL): "setOHHandle: no handle assigned ind file" + filename;
            if (otherhandle == null) {
                NUL2bytes(this.ohChunk, OHBYTEC + 4 * i);
            } else {
                if (otherhandle.index >= USAGE.allCount()) throw new kelondroException(filename, "INTERNAL ERROR, setOHHandles: handle " + i + " exceeds file size (" + handle.index + " >= " + USAGE.allCount() + ")");
                int2bytes(otherhandle.index, this.ohChunk, OHBYTEC + 4 * i);
            }
            this.ohChanged = true;
        }
        
        public byte getOHByte(final int i) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("getOHByte: wrong index " + i);
            if (this.handle.index == Handle.NUL) throw new kelondroException(filename, "Cannot load OH values");
            return this.ohChunk[i];
        }

        public Handle getOHHandle(final int i) {
            if (this.handle.index == Handle.NUL) throw new kelondroException(filename, "Cannot load OH values");
            assert (i < OHHANDLEC): "handle index out of bounds: " + i + " in file " + filename;
            final int h = bytes2int(this.ohChunk, OHBYTEC + 4 * i);
            return (h == Handle.NUL) ? null : new Handle(h);
        }

        public synchronized void setValueRow(final byte[] row) throws IOException {
            // if the index is defined, then write values directly to the file, else only to the object
            if ((row != null) && (row.length != ROW.objectsize)) throw new IOException("setValueRow with wrong (" + row.length + ") row length instead correct: " + ROW.objectsize);
            
            // set values
            if (this.handle.index != Handle.NUL) {
                this.bodyChunk = row;
                this.bodyChanged = true;
            }
        }

        public synchronized boolean valid() {
            // returns true if the key starts with non-zero byte
            // this may help to detect deleted entries
            return this.bodyChunk == null || (this.bodyChunk[0] != 0) && ((this.bodyChunk[0] != -128) || (this.bodyChunk[1] != 0));
        }

        public synchronized byte[] getKey() throws IOException {
            // read key
            if (this.bodyChunk == null) {
                // load all values from the database file
                this.bodyChunk = new byte[ROW.objectsize];
                // read values
                entryFile.readFully(seekpos(this.handle) + overhead, this.bodyChunk, 0, this.bodyChunk.length);
            }
            return trimCopy(this.bodyChunk, 0, ROW.width(0));
        }

        public synchronized byte[] getValueRow() throws IOException {
            
            if (this.bodyChunk == null) {
                // load all values from the database file
                this.bodyChunk = new byte[ROW.objectsize];
                // read values
                entryFile.readFully(seekpos(this.handle) + overhead, this.bodyChunk, 0, this.bodyChunk.length);
            }

            return this.bodyChunk;
        }

        public synchronized void commit() throws IOException {
            // this must be called after all write operations to the node are finished

            // place the data to the file

            final boolean doCommit = this.ohChanged || this.bodyChanged;
            
            // save head
            synchronized (entryFile) {
            if (this.ohChanged) {
                //System.out.println("WRITEH(" + filename + ", " + seekpos(this.handle) + ", " + this.headChunk.length + ")");
                assert (ohChunk == null) || (ohChunk.length == overhead);
                entryFile.write(seekpos(this.handle), (this.ohChunk == null) ? new byte[overhead] : this.ohChunk);
                this.ohChanged = false;
            }

            // save tail
            if ((this.bodyChunk != null) && (this.bodyChanged)) {
                //System.out.println("WRITET(" + filename + ", " + (seekpos(this.handle) + headchunksize) + ", " + this.tailChunk.length + ")");
                assert (this.bodyChunk == null) || (this.bodyChunk.length == ROW.objectsize);
                entryFile.write(seekpos(this.handle) + overhead, (this.bodyChunk == null) ? new byte[ROW.objectsize] : this.bodyChunk);
                this.bodyChanged = false;
            }
            
            if (doCommit) entryFile.commit();
            }
        }
        
    }

    public class Handle implements Comparable<Handle> {
        
        public final static int NUL = Integer.MIN_VALUE; // the meta value for the kelondroTray' NUL abstraction

        protected int index;

        protected Handle(final int i) {
        	assert i != 1198412402;
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

        public boolean equals(final Handle h) {
            assert (index != NUL);
            assert (h.index != NUL);
            return (this.index == h.index);
        }

        public boolean equals(final Object h) {
            assert (index != NUL);
            assert (h instanceof Handle && ((Handle) h).index != NUL);
            return (h instanceof Handle && this.index == ((Handle) h).index);
        }

        public int compare(final Handle h0, final Handle h1) {
            assert ((h0).index != NUL);
            assert ((h1).index != NUL);
            if ((h0).index < (h1).index) return -1;
            if ((h0).index > (h1).index) return 1;
            return 0;
        }

        public int compareTo(final Handle h) {
            // this is needed for a TreeMap
            assert (index != NUL) : "this.index is NUL in compareTo";
            assert ((h).index != NUL) : "handle.index is NUL in compareTo";
            if (index < (h).index) return -1;
            if (index > (h).index) return 1;
            return 0;
        }

        public int hashCode() {
            assert (index != NUL);
            return this.index;
        }
    }
}
