// HeapModifier.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 05.01.2009 on http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.SortedMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.io.CachedFileWriter;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;


public class HeapModifier extends HeapReader implements BLOB {

    /*
     * This class adds a remove operation to a BLOBHeapReader. That means that a BLOBModifier can
     * - read elements from a BLOB
     * - remove elements from a BLOB
     * but cannot write new entries to the BLOB
     */

    /**
     * create a heap file: a arbitrary number of BLOBs, indexed by an access key
     * The heap file will be indexed upon initialization.
     * @param heapFile
     * @param keylength
     * @param ordering
     * @throws IOException
     */
    public HeapModifier(final File heapFile, final int keylength, final ByteOrder ordering) throws IOException {
        super(heapFile, keylength, ordering);
    }

    /**
     * clears the content of the database
     * @throws IOException
     */
    @Override
    public synchronized void clear() throws IOException {
        this.index.clear();
        this.free.clear();
        this.file.close();
        this.file = null;
        FileUtils.deletedelete(this.heapFile);
        super.deleteFingerprint();
        this.file = new CachedFileWriter(this.heapFile);
    }

    /**
     * close the BLOB table
     */
    @Override
    public synchronized void close(boolean writeIDX) {
        shrinkWithGapsAtEnd();
        super.close(writeIDX);
    }

    @Override
    public synchronized void close() {
        close(true);
    }

    @Override
    public void finalize() {
        this.close();
    }

    /**
     * remove a BLOB
     * @param key  the primary key
     * @throws IOException
     */
    @Override
    public void delete(byte[] key) throws IOException {
        if (this.index == null) return;
        key = normalizeKey(key);

        // pre-check before synchronization
        long seek = this.index.get(key);
        if (seek < 0) return;

        synchronized (this) {
            // check again if the index contains the key
            seek = this.index.get(key);
            if (seek < 0) return;

            // check consistency of the index
            //assert (checkKey(key, seek)) : "key compare failed; key = " + UTF8.String(key) + ", seek = " + seek;

            // access the file and read the container
            this.file.seek(seek);
            int size = this.file.readInt();
            //assert seek + size + 4 <= this.file.length() : heapFile.getName() + ": too long size " + size + " in record at " + seek;
            long filelength = this.file.length(); // put in separate variable for debugging
            if (seek + size + 4 > filelength) {
                ConcurrentLog.severe("BLOBHeap", this.heapFile.getName() + ": too long size " + size + " in record at " + seek);
                throw new IOException(this.heapFile.getName() + ": too long size " + size + " in record at " + seek);
            }
            super.deleteFingerprint();

            // add entry to free array
            this.free.put(seek, size);

            // fill zeros to the content
            int l = size; byte[] fill = new byte[size];
            while (l-- > 0) fill[l] = 0;
            this.file.write(fill, 0, size);

            // remove entry from index
            this.index.remove(key);

            // recursively merge gaps
            tryMergeNextGaps(seek, size);
            tryMergePreviousGap(seek);
        }
    }

    private void tryMergePreviousGap(final long thisSeek) throws IOException {
        // this is called after a record has been removed. That may cause that a new
        // empty record was surrounded by gaps. We merge with a previous gap, if this
        // is also empty, but don't do that recursively
        // If this is successful, it removes the given marker for thisSeed and
        // because of this, this method MUST be called AFTER tryMergeNextGaps was called.

        // first find the gap entry for the closest gap in front of the give gap
        SortedMap<Long, Integer> head = this.free.headMap(thisSeek);
        if (head.isEmpty()) return;
        long previousSeek = head.lastKey().longValue();
        int previousSize = head.get(previousSeek).intValue();

        // check if this is directly in front
        if (previousSeek + previousSize + 4 == thisSeek) {
            // right in front! merge the gaps
            Integer thisSize = this.free.get(thisSeek);
            assert thisSize != null;
            mergeGaps(previousSeek, previousSize, thisSeek, thisSize.intValue());
        }
    }

    private void tryMergeNextGaps(final long thisSeek, final int thisSize) throws IOException {
        // try to merge two gaps if one gap has been processed already and the position of the next record is known
        // if the next record is also a gap, merge these gaps and go on recursively

        // first check if next gap position is outside of file size
        long nextSeek = thisSeek + thisSize + 4;
        if (nextSeek >= this.file.length()) return; // end of recursion

        // move to next position and read record size
        Integer nextSize = this.free.get(nextSeek);
        if (nextSize == null) return; // finished, this is not a gap

        // check if the record is a gap-record
        assert nextSize.intValue() > 0;
        if (nextSize.intValue() == 0) {
            // a strange gap record: we can extend the thisGap with four bytes
            // the nextRecord is a gap record; we remove that from the free list because it will be joined with the current gap
            mergeGaps(thisSeek, thisSize, nextSeek, 0);

            // recursively go on
            tryMergeNextGaps(thisSeek, thisSize + 4);
        } else {
            // check if this is a true gap!
            this.file.seek(nextSeek + 4);
            byte[] o = new byte[1];
            this.file.readFully(o, 0, 1);
            int t = o[0];
            assert t == 0;
            if (t == 0) {
                // the nextRecord is a gap record; we remove that from the free list because it will be joined with the current gap
                mergeGaps(thisSeek, thisSize, nextSeek, nextSize.intValue());

                // recursively go on
                tryMergeNextGaps(thisSeek, thisSize + 4 + nextSize.intValue());
            }
        }
    }

    private void mergeGaps(final long seek0, final int size0, final long seek1, final int size1) throws IOException {
        //System.out.println("*** DEBUG-BLOBHeap " + heapFile.getName() + ": merging gap from pos " + seek0 + ", len " + size0 + " with next record of size " + size1 + " (+ 4)");

        Integer g = this.free.remove(seek1); // g is only used for debugging
        assert g != null;
        assert g.intValue() == size1;

        // overwrite the size bytes of next records with zeros
        this.file.seek(seek1);
        this.file.writeInt(0);

        // the new size of the current gap: old size + len + 4
        int newSize = size0 + 4 + size1;
        this.file.seek(seek0);
        this.file.writeInt(newSize);

        // register new gap in the free array; overwrite old gap entry
        g = this.free.put(seek0, newSize);
        assert g != null;
        assert g.intValue() == size0;
    }

    protected void shrinkWithGapsAtEnd() {
        // find gaps at the end of the file and shrink the file by these gaps
    	if (this.free == null) return;
        try {
            while (!this.free.isEmpty()) {
                Long seek = this.free.lastKey();
                int size = this.free.get(seek).intValue();
                if (seek.longValue() + size + 4 != this.file.length()) return;
                // shrink the file
                this.file.setLength(seek.longValue());
                this.free.remove(seek);
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }

	@Override
    public void insert(byte[] key, byte[] b) throws IOException {
		throw new UnsupportedOperationException("put is not supported in BLOBHeapModifier");
	}

	@Override
    public int replace(byte[] key, final Rewriter rewriter) throws IOException {
	    throw new UnsupportedOperationException();
    }

	@Override
    public int reduce(byte[] key, final Reducer reducer) throws IOException, SpaceExceededException {
        key = normalizeKey(key);
        assert key.length == this.keylength;

        // pre-check before synchronization
        long pos = this.index.get(key);
        if (pos < 0) return 0;

        synchronized (this) {
            long m = this.mem();

            // check again if the index contains the key
            pos = this.index.get(key);
            if (pos < 0) return 0;

            // check consistency of the index
            //assert checkKey(key, pos) : "key compare failed; key = " + UTF8.String(key) + ", seek = " + pos;

            // access the file and read the container
            this.file.seek(pos);
            final int len = this.file.readInt() - this.keylength;
            if (MemoryControl.available() < len) {
                if (!MemoryControl.request(len, true)) return 0; // not enough memory available for this blob
            }
            super.deleteFingerprint();

            // read the key
            final byte[] keyf = new byte[this.keylength];
            this.file.readFully(keyf, 0, keyf.length);
            assert this.ordering == null || this.ordering.equal(key, keyf) : "key = " + UTF8.String(key) + ", keyf = " + UTF8.String(keyf);

            // read the blob
            byte[] blob = new byte[len];
            this.file.readFully(blob, 0, blob.length);

            // rewrite the entry
            blob = reducer.rewrite(blob);
            int reduction = len - blob.length;
            if (reduction == 0) {
                // even if the reduction is zero then it is still be possible that the record has been changed
                this.file.seek(pos + 4 + key.length);
                this.file.write(blob);
                return 0;
            }

            // the new entry must be smaller than the old entry and must at least be 4 bytes smaller
            // because that is the space needed to write a new empty entry record at the end of the gap
            if (blob.length > len - 4) throw new IOException("replace of BLOB for key " + UTF8.String(key) + " failed (too large): new size = " + blob.length + ", old size = " + (len - 4));

            // replace old content
            this.file.seek(pos);
            this.file.writeInt(blob.length + key.length);
            this.file.write(key);
            this.file.write(blob);

            // define the new empty entry
            final int newfreereclen = reduction - 4;
            assert newfreereclen >= 0;
            this.file.writeInt(newfreereclen);

            // fill zeros to the content
            int l = newfreereclen; byte[] fill = new byte[newfreereclen];
            while (l-- > 0) fill[l] = 0;
            this.file.write(fill, 0, newfreereclen);

            // add a new free entry
            this.free.put(pos + 4 + blob.length + key.length, newfreereclen);

            assert mem() <= m : "m = " + m + ", mem() = " + mem();
            return reduction;
        }
    }

}
