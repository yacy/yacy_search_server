// ReferenceContainerConcurrentCache.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 05.04.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-05-05 22:08:23 +0200 (Di, 05 Mai 2009) $
// $LastChangedRevision: 5924 $
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

package de.anomic.kelondro.text;

import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.index.Row;

public final class ReferenceContainerConcurrentCache<ReferenceType extends Reference> /* extends AbstractIndex<ReferenceType> implements Index<ReferenceType>, IndexReader<ReferenceType>, Iterable<ReferenceContainer<ReferenceType>>*/ {

    private final Row payloadrow;
    private final ByteOrder termOrder;
    private ReferenceContainerCache<ReferenceType> caches[];
    private final int concurrency;
    private final ReferenceFactory<ReferenceType> factory;
    
    public ReferenceContainerConcurrentCache(final ReferenceFactory<ReferenceType> factory, final Row payloadrow, ByteOrder termOrder, int concurrency) {
        //super(factory);
        this.payloadrow = payloadrow;
        this.termOrder = termOrder;
        this.concurrency = concurrency;
        this.caches = null;
        this.factory = factory;
    }

    public Row rowdef() {
        return this.payloadrow;
    }
    
    public void clear() {
        if (caches != null) {
        	for (int i = 0; i < caches.length; i++) {
        		caches[i].clear();
        		caches[i].initWriteMode();
        	}
        }
    }
    
    public void close() {
    	if (caches != null) {
        	for (int i = 0; i < caches.length; i++) {
        		caches[i].close();
        	}
        }
    	this.caches = null;
    }
    
    @SuppressWarnings("unchecked")
	public void initWriteMode() {
    	caches = new ReferenceContainerCache[concurrency];
    	for (int i = 0; i < caches.length; i++) {
    		caches[i] = new ReferenceContainerCache<ReferenceType>(factory, payloadrow, termOrder);
    	}
    }
    
    public int size() {
    	if (caches == null) return 0;
        int count = 0;
        for (int i = 0; i < caches.length; i++) {
    		count += caches[i].size();
    	}
        return count;
    }

    public int maxReferences() {
    	if (caches == null) return 0;
        int max = 0;
        for (int i = 0; i < caches.length; i++) {
    		max = Math.max(max, caches[i].maxReferences());
    	}
        return max;
    }
    
/*
    public synchronized CloneableIterator<ReferenceContainer<ReferenceType>> references(final byte[] startWordHash, final boolean rot) {
    	ArrayList<CloneableIterator<ReferenceContainer<ReferenceType>>> a = new ArrayList<CloneableIterator<ReferenceContainer<ReferenceType>>>(caches.length);
    	for (int i = 0; i < caches.length; i++) {
    		a.add(caches[i].references(startWordHash, rot));
    	}
    	return MergeIterator.cascade(a, termOrder, MergeIterator.simpleMerge, true);
    }
    
    public Iterator<ReferenceContainer<ReferenceType>> iterator() {
        return references(null, false);
    }

    public boolean has(final byte[] key) {
        return this.cache.containsKey(new ByteArray(key));
    }
    
    public ReferenceContainer<ReferenceType> get(final byte[] key, Set<String> urlselection) {

    }

    public int count(final byte[] key) {

    }

    public ReferenceContainer<ReferenceType> delete(final byte[] termHash) {

    }

    public boolean remove(final byte[] termHash, final String urlHash) {

    }
    
    public int remove(final byte[] termHash, final Set<String> urlHashes) {

    }
 
    public void add(final ReferenceContainer<ReferenceType> container) {

    }

    public void add(final byte[] termHash, final ReferenceType newEntry) {

    }

    public int minMem() {
        return 0;
    }

    public ByteOrder ordering() {
        return this.termOrder;
    }
 */
}
