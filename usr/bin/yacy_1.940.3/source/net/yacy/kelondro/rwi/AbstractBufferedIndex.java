// AbstractBufferedIndex.java
// -----------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.3.2009 on http://yacy.net
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
import java.util.TreeSet;

import net.yacy.cora.order.Order;


public abstract class AbstractBufferedIndex<ReferenceType extends Reference> extends AbstractIndex<ReferenceType> implements BufferedIndex<ReferenceType> {

    public AbstractBufferedIndex(final ReferenceFactory<ReferenceType> factory) {
        super(factory);
    }

    @Override
    public synchronized TreeSet<ReferenceContainer<ReferenceType>> referenceContainer(byte[] startHash, final boolean rot, final boolean excludePrivate, int count, final boolean ram) throws IOException {
        // creates a set of indexContainers
        // this does not use the cache
        final Order<ReferenceContainer<ReferenceType>> containerOrder = new ReferenceContainerOrder<ReferenceType>(this.factory, termKeyOrdering().clone());
        if (startHash != null && startHash.length == 0) startHash = null;
        final ReferenceContainer<ReferenceType> emptyContainer = ReferenceContainer.emptyContainer(this.factory, startHash);
        containerOrder.rotate(emptyContainer);
        final TreeSet<ReferenceContainer<ReferenceType>> containers = new TreeSet<ReferenceContainer<ReferenceType>>(containerOrder);
        final Iterator<ReferenceContainer<ReferenceType>> i = referenceContainerIterator(startHash, rot, excludePrivate, ram);
        if (ram) count = (this instanceof IndexCell) ? count : Math.min(size(), count);
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
}
