/**
 *  Metadata
 *  Copyright 2012 by Michael Peter Christen
 *  First released 3.4.2012 at http://yacy.net
 *
 *  This file is part of YaCy Content Integration
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.search.index;

import java.io.IOException;

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.kelondro.data.meta.URIMetadata;
import net.yacy.kelondro.data.word.WordReference;

public interface Metadata {

    public void clearCache();

    public void clear() throws IOException;

    public int size();

    public void close();

    public int writeCacheSize();

    public URIMetadata load(final WeakPriorityBlockingQueue.Element<WordReference> obrwi);

    public URIMetadata load(final byte[] urlHash);

    public void store(final URIMetadata entry) throws IOException;

    public boolean remove(final byte[] urlHashBytes);

    public boolean exists(final byte[] urlHash);

    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey);

    public CloneableIterator<URIMetadata> entries() throws IOException;

    public CloneableIterator<URIMetadata> entries(final boolean up, final String firstHash) throws IOException;

    /**
     * using a fragment of the url hash (5 bytes: bytes 6 to 10) it is possible to address all urls from a specific domain
     * here such a fragment can be used to delete all these domains at once
     * @param hosthash
     * @return number of deleted domains
     * @throws IOException
     */
    public int deleteDomain(final String hosthash) throws IOException;
}
