// AbstractIndex.java
// -----------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.3.2009 on http://yacy.net
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

import java.io.IOException;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.order.Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;

public abstract class AbstractIndex <ReferenceType extends Reference> implements Index<ReferenceType> {

    final protected ReferenceFactory<ReferenceType> factory;

    public AbstractIndex(final ReferenceFactory<ReferenceType> factory) {
        this.factory = factory;
    }


    /**
     * merge this index with another index
     * @param otherIndex
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public void merge(final Index<ReferenceType> otherIndex) throws IOException, SpaceExceededException {
        byte[] term;
        for (final ReferenceContainer<ReferenceType> otherContainer: otherIndex) {
            term = otherContainer.getTermHash();
            synchronized (this) {
                final ReferenceContainer<ReferenceType> container = get(term, null);
                if (container == null) {
                    this.add(otherContainer);
                } else {
                    container.merge(otherContainer);
                    delete(term); // in some file-based environments we cannot just change the container
                    this.add(container);
                }
            }
        }
    }

    @Override
    public void removeDelayed(final HandleSet termHashes, final byte[] urlHashBytes) throws IOException {
        // remove the same url hashes for multiple words
        // this is mainly used when correcting a index after a search
        final Iterator<byte[]> i = termHashes.iterator();
        while (i.hasNext()) {
            removeDelayed(i.next(), urlHashBytes);
        }
    }

    @Override
    public int remove(final HandleSet termHashes, final byte[] urlHashBytes) throws IOException {
        // remove the same url hashes for multiple words
        // this is mainly used when correcting a index after a search
        final Iterator<byte[]> i = termHashes.iterator();
        int c = 0;
        while (i.hasNext()) {
            if (remove(i.next(), urlHashBytes)) c++;
        }
        return c;
    }

    @Override
    public synchronized TreeSet<ReferenceContainer<ReferenceType>> referenceContainer(final byte[] startHash, final boolean rot, final boolean excludePrivate, int count) throws IOException {
        // creates a set of indexContainers
        // this does not use the cache
        final Order<ReferenceContainer<ReferenceType>> containerOrder = new ReferenceContainerOrder<ReferenceType>(this.factory, termKeyOrdering().clone());
        final ReferenceContainer<ReferenceType> emptyContainer = ReferenceContainer.emptyContainer(this.factory, startHash);
        containerOrder.rotate(emptyContainer);
        final TreeSet<ReferenceContainer<ReferenceType>> containers = new TreeSet<ReferenceContainer<ReferenceType>>(containerOrder);
        final Iterator<ReferenceContainer<ReferenceType>> i = referenceContainerIterator(startHash, rot, excludePrivate);
        //if (ram) count = Math.min(size(), count);
        ReferenceContainer<ReferenceType> container;
        // this loop does not terminate using the i.hasNex() predicate when rot == true
        // because then the underlying iterator is a rotating iterator without termination
        // in this case a termination must be ensured with a counter
        // It must also be ensured that the counter is in/decreased every loop
        while ((count > 0) && (i.hasNext())) {
            container = i.next();
            if (container != null && !container.isEmpty()) {
                containers.add(container);
            }
            count--; // decrease counter even if the container was null or empty to ensure termination
        }
        return containers; // this may return less containers as demanded
    }


    // methods to search in the index

    /**
     * collect containers for given word hashes.
     * This collection stops if a single container does not contain any references.
     * In that case only a empty result is returned.
     * @param wordHashes
     * @param urlselection
     * @return map of wordhash:indexContainer
     */
    @Override
    public TreeMap<byte[], ReferenceContainer<ReferenceType>> searchConjunction(final HandleSet wordHashes, final HandleSet urlselection) {
    	// first check if there is any entry that has no match; this uses only operations in ram
    	/*
    	Iterator<byte[]> i = wordHashes.iterator();
        while (i.hasNext()) {
            if (!this.has(i.next())); return new TreeMap<byte[], ReferenceContainer<ReferenceType>>(0);
        }
        */
    	// retrieve entities that belong to the hashes
        final TreeMap<byte[], ReferenceContainer<ReferenceType>> containers = new TreeMap<byte[], ReferenceContainer<ReferenceType>>(Base64Order.enhancedCoder);
        byte[] singleHash;
        ReferenceContainer<ReferenceType> singleContainer;
        final Iterator<byte[]> i = wordHashes.iterator();
        while (i.hasNext()) {

            // get next word hash:
            singleHash = i.next();

            // retrieve index
            try {
                singleContainer = get(singleHash, urlselection);
            } catch (final IOException e) {
                Log.logException(e);
                continue;
            }

            // check result
            if ((singleContainer == null || singleContainer.isEmpty())) return new TreeMap<byte[], ReferenceContainer<ReferenceType>>(Base64Order.enhancedCoder);

            containers.put(singleHash, singleContainer);
        }
        return containers;
    }

    /**
     * collect containers for given word hashes and join them as they are retrieved.
     * This collection stops if a single container does not contain any references
     * or the current result of the container join results in an empty container.
     * In any fail case only a empty result container is returned.
     * @param wordHashes
     * @param urlselection
     * @param maxDistance the maximum distance that the words in the result may have
     * @return ReferenceContainer the join result
     * @throws SpaceExceededException
     */
    public ReferenceContainer<ReferenceType> searchJoin(final HandleSet wordHashes, final HandleSet urlselection, final int maxDistance) throws SpaceExceededException {
        // first check if there is any entry that has no match;
        // this uses only operations in ram
        for (final byte[] wordHash: wordHashes) {
            if (!has(wordHash)) return ReferenceContainer.emptyContainer(this.factory, null, 0);
        }

        // retrieve entities that belong to the hashes
        ReferenceContainer<ReferenceType> resultContainer = null;
        ReferenceContainer<ReferenceType> singleContainer;
        for (final byte[] wordHash: wordHashes) {
            // retrieve index
            try {
                singleContainer = get(wordHash, urlselection);
            } catch (final IOException e) {
                Log.logException(e);
                continue;
            }

            // check result
            if ((singleContainer == null || singleContainer.isEmpty())) return ReferenceContainer.emptyContainer(this.factory, null, 0);
            if (resultContainer == null) resultContainer = singleContainer; else {
                resultContainer = ReferenceContainer.joinConstructive(this.factory, resultContainer, singleContainer, maxDistance);
            }

            // finish if the result is empty
            if (resultContainer.isEmpty()) return resultContainer;
        }
        return resultContainer;
    }

    public TermSearch<ReferenceType> query(
            final HandleSet queryHashes,
            final HandleSet excludeHashes,
            final HandleSet urlselection,
            final ReferenceFactory<ReferenceType> termFactory,
            final int maxDistance) throws SpaceExceededException {
        return new TermSearch<ReferenceType>(this, queryHashes, excludeHashes, urlselection, termFactory, maxDistance);
    }

    @Override
    public Row referenceRow() {
        return this.factory.getRow();
    }
}
