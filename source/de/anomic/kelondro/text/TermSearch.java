// TermSearch.java
// ---------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 3.6.2009 on http://yacy.net
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

package de.anomic.kelondro.text;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

public class TermSearch <ReferenceType extends Reference> {

    private ReferenceContainer<ReferenceType> joinResult;
    HashMap<byte[], ReferenceContainer<ReferenceType>> inclusionContainers, exclusionContainers;
    
    public TermSearch(
            Index<ReferenceType> base,
            final TreeSet<byte[]> queryHashes,
            final TreeSet<byte[]> excludeHashes,
            final Set<String> urlselection,
            ReferenceFactory<ReferenceType> termFactory,
            int maxDistance) {
        
        this.inclusionContainers =
            (queryHashes.size() == 0) ?
                new HashMap<byte[], ReferenceContainer<ReferenceType>>(0) :
                base.searchConjunction(queryHashes, urlselection);
                
        if ((inclusionContainers.size() != 0) &&
            (inclusionContainers.size() < queryHashes.size()))
            inclusionContainers = new HashMap<byte[], ReferenceContainer<ReferenceType>>(0); // prevent that only a subset is returned
        
        this.exclusionContainers =
            (inclusionContainers.size() == 0) ?
                new HashMap<byte[], ReferenceContainer<ReferenceType>>(0) :
                base.searchConjunction(excludeHashes, urlselection);
    
        // join and exclude the result
        this.joinResult = ReferenceContainer.joinExcludeContainers(
                termFactory,
                inclusionContainers.values(),
                exclusionContainers.values(),
                maxDistance);        
    }
    
    public ReferenceContainer<ReferenceType> joined() {
        return this.joinResult;
    }
    
    public HashMap<byte[], ReferenceContainer<ReferenceType>> inclusion() {
        return this.inclusionContainers;
    }
    
    public HashMap<byte[], ReferenceContainer<ReferenceType>> exclusion() {
        return this.exclusionContainers;
    }
    
}
