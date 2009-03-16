// IndexPackage.java
// -----------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.3.2009 on http://yacy.net
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

/*
 * an IndexPackage is an integration of different index types, i.e.
 * - ReferenceContainerArray
 * - ReferenceContainerCache
 * - IndexCache (which is a wrapper of a ReferenceContainerCache)
 * - IndexCollection
 * This interface was created from the methods that are used in CachedIndexCollection
 * (which integrates IndexCache and IndexCollection)
 * and is applied to the new index integration class IndexCell
 * (which integrates ReferenceContainerArray and ReferenceContainerCache)
 * to make it possible to switch between the old and new index data structure
 */
public interface IndexPackage extends Index {

    /*
     *  methods for monitoring of the cache
     */
    
    public int maxURLinCache();

    public long minAgeOfCache();

    public long maxAgeOfCache();

    public int indexCacheSize();
    
    public long indexCacheSizeBytes();

    public void setMaxWordCount(final int maxWords);

    public void cacheFlushControl();
    
    public void flushCacheFor(int time);

    public int backendSize();
    
    public int cacheSize();

    
    
    /*
     * methods to search the index
     */
    
    public HashMap<String, ReferenceContainer>[] localSearchContainers(
                            final TreeSet<String> queryHashes, 
                            final TreeSet<String> excludeHashes, 
                            final Set<String> urlselection);
    
    public TreeSet<ReferenceContainer> indexContainerSet(final String startHash, final boolean ram, final boolean rot, int count);
    
    
}
