// ReferenceIterator.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.03.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.kelondro.rwi;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import net.yacy.kelondro.blob.HeapReader;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;

/**
 * iterator of BLOBHeap files: is used to import heap dumps into a write-enabled index heap
 */
public class ReferenceIterator <ReferenceType extends Reference> implements CloneableIterator<ReferenceContainer<ReferenceType>>, Iterable<ReferenceContainer<ReferenceType>> {
    HeapReader.entries blobs;
    Row payloadrow;
    File blobFile;
    ReferenceFactory<ReferenceType> factory;
    
    public ReferenceIterator(final File blobFile, ReferenceFactory<ReferenceType> factory, final Row payloadrow) throws IOException {
        this.blobs = new HeapReader.entries(blobFile, payloadrow.primaryKeyLength);
        this.payloadrow = payloadrow;
        this.blobFile = blobFile;
        this.factory = factory;
    }
    
    public boolean hasNext() {
        if (blobs == null) return false;
        if (blobs.hasNext()) return true;
        close();
        return false;
    }

    /**
     * return an index container
     * because they may get very large, it is wise to deallocate some memory before calling next()
     */
    public ReferenceContainer<ReferenceType> next() {
        Map.Entry<byte[], byte[]> entry = blobs.next();
        byte[] payload = entry.getValue();
        try {
            return new ReferenceContainer<ReferenceType>(factory, entry.getKey(), RowSet.importRowSet(payload, payloadrow));
        } catch (RowSpaceExceededException e) {
            Log.logSevere("ReferenceIterator", "lost entry '" + entry.getKey() + "' because of too low memory: " + e.toString());
            return null;
        }
    }
    
    public void remove() {
        throw new UnsupportedOperationException("heap dumps are read-only");
    }

    public Iterator<ReferenceContainer<ReferenceType>> iterator() {
        return this;
    }
    
    public void close() {
        if (blobs != null) this.blobs.close();
        blobs = null;
    }

    public CloneableIterator<ReferenceContainer<ReferenceType>> clone(Object modifier) {
        if (blobs != null) this.blobs.close();
        blobs = null;
        try {
            return new ReferenceIterator<ReferenceType>(this.blobFile, factory, this.payloadrow);
        } catch (IOException e) {
            Log.logException(e);
            return null;
        }
    }
}