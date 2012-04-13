/**
 *  DocumentReference
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
import net.yacy.cora.storage.MapStore;
import net.yacy.kelondro.data.meta.URIReference;
import net.yacy.kelondro.data.word.WordReference;

public class DocumentReference {

	public MapStore data;

    public void clear() throws IOException {
    	data.clear();
    }

    public int size() {
    	return data.size();
    }

    public void close() {
    	if (data != null) {
    		data.close();
    	}
    	data = null;
    }

    public void store(final URIReference entry) throws IOException {
    	data.put(entry.hash(), entry.toMap());
    }

    public URIReference load(final WeakPriorityBlockingQueue.Element<WordReference> obrwi) {
    	return null;
    }

    public URIReference load(final byte[] urlHash){
    	return null;
    }

    public boolean remove(final byte[] urlHashBytes) {
    	return false;
    }

    public boolean exists(final byte[] urlHash) {
    	return false;
    }

    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
    	return null;
    }

    public CloneableIterator<URIReference> entries() throws IOException {
    	return null;
    }

    public CloneableIterator<URIReference> entries(final boolean up, final String firstHash) throws IOException {
    	return null;
    }

    /**
     * using a fragment of the url hash (5 bytes: bytes 6 to 10) it is possible to address all urls from a specific domain
     * here such a fragment can be used to delete all these domains at once
     * @param hosthash
     * @return number of deleted domains
     * @throws IOException
     */
    public int deleteDomain(final String hosthash) throws IOException {
    	return -1;
    }
}
