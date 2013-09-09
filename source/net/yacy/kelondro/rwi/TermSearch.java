// TermSearch.java
// ---------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 3.6.2009 on http://yacy.net
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

import java.util.TreeMap;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;


public class TermSearch <ReferenceType extends Reference> {

    private final ReferenceContainer<ReferenceType> joinResult;
    private TreeMap<byte[], ReferenceContainer<ReferenceType>> inclusionContainers;

    public TermSearch(
            Index<ReferenceType> base,
            final HandleSet queryHashes,
            final HandleSet excludeHashes,
            final HandleSet urlselection,
            ReferenceFactory<ReferenceType> termFactory,
            int maxDistance) throws SpaceExceededException {

        this.inclusionContainers =
            (queryHashes.isEmpty()) ?
                new TreeMap<byte[], ReferenceContainer<ReferenceType>>(Base64Order.enhancedCoder) :
                base.searchConjunction(queryHashes, urlselection);

        if (!this.inclusionContainers.isEmpty() &&
            (this.inclusionContainers.size() < queryHashes.size()))
            this.inclusionContainers = new TreeMap<byte[], ReferenceContainer<ReferenceType>>(Base64Order.enhancedCoder); // prevent that only a subset is returned

        TreeMap<byte[], ReferenceContainer<ReferenceType>> exclusionContainers =
            (this.inclusionContainers.isEmpty()) ?
                new TreeMap<byte[], ReferenceContainer<ReferenceType>>(Base64Order.enhancedCoder) :
                base.searchConjunction(excludeHashes, urlselection);

        // join and exclude the result
        this.joinResult = ReferenceContainer.joinExcludeContainers(
                termFactory,
                this.inclusionContainers.values(),
                exclusionContainers.values(),
                maxDistance);
    }

    public ReferenceContainer<ReferenceType> joined() {
        return this.joinResult;
    }

    public TreeMap<byte[], ReferenceContainer<ReferenceType>> inclusion() {
        return this.inclusionContainers;
    }

}
