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
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.HeapReader;
import net.yacy.kelondro.index.RowSet;

/**
 * iterator of BLOBHeap files: is used to import heap dumps into a write-enabled index heap
 */
public class ReferenceIterator <ReferenceType extends Reference> extends LookAheadIterator<ReferenceContainer<ReferenceType>> implements CloneableIterator<ReferenceContainer<ReferenceType>>, Iterable<ReferenceContainer<ReferenceType>> {
    private HeapReader.entries blobs;
    private File blobFile;
    private ReferenceFactory<ReferenceType> factory;

    public ReferenceIterator(final File blobFile, final ReferenceFactory<ReferenceType> factory) throws IOException {
        this.blobs = new HeapReader.entries(blobFile, factory.getRow().primaryKeyLength);
        this.blobFile = blobFile;
        this.factory = factory;
    }

    /**
     * return an index container
     * because they may get very large, it is wise to deallocate some memory before calling next()
     */
    @Override
    public ReferenceContainer<ReferenceType> next0() {
        if (this.blobs == null) return null;
        RowSet row;
        Map.Entry<byte[], byte[]> entry;
        while (this.blobs.hasNext()) {
            entry = this.blobs.next();
            if (entry == null) break;
            try {
                row = RowSet.importRowSet(entry.getValue(), this.factory.getRow());
                if (row == null) {
                    ConcurrentLog.severe("ReferenceIterator", "lost entry '" + UTF8.String(entry.getKey()) + "' because importRowSet returned null");
                    continue; // thats a fail but not as REALLY bad if the whole method would crash here
                }
                return new ReferenceContainer<ReferenceType>(this.factory, entry.getKey(), row);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.severe("ReferenceIterator", "lost entry '" + UTF8.String(entry.getKey()) + "' because of too low memory: " + e.toString());
                continue;
            } catch (final Throwable e) {
                ConcurrentLog.severe("ReferenceIterator", "lost entry '" + UTF8.String(entry.getKey()) + "' because of error: " + e.toString());
                continue;
            }
        }
        close();
        return null;
    }

    @Override
    public synchronized void close() {
        if (this.blobs != null) this.blobs.close();
        this.blobs = null;
    }

    @Override
    public CloneableIterator<ReferenceContainer<ReferenceType>> clone(final Object modifier) {
        if (this.blobs != null) this.blobs.close();
        this.blobs = null;
        try {
            return new ReferenceIterator<ReferenceType>(this.blobFile, this.factory);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }
}