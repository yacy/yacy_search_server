// indexRI.java
// -----------------------------
// (C) 2005 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 6.5.2005 on http://www.anomic.de
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


package de.anomic.index;

import java.util.Iterator;
import java.util.Set;

public interface indexRI {
    
    public int size();
    public int minMem();
    
    public Iterator wordContainers(String startWordHash, boolean rot); // method to replace wordHashes
        
    public long getUpdateTime(String wordHash);
    public int indexSize(String wordHash);
    public boolean hasContainer(String wordHash); // should only be used if in case that true is returned the getContainer is NOT called
    public indexContainer getContainer(String wordHash, Set urlselection, long maxtime);
    public indexContainer deleteContainer(String wordHash);
    
    public boolean removeEntry(String wordHash, String urlHash);
    public int removeEntries(String wordHash, Set urlHashes);
    public void addEntries(indexContainer newEntries, long creationTime, boolean dhtCase);

    public void close();

}
