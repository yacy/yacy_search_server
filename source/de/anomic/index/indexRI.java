// indexRI.java
// -----------------------------
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

import java.io.IOException;
import java.util.Set;

import de.anomic.kelondro.order.CloneableIterator;

public interface indexRI {
    
    public int size();
    public int minMem();
    public CloneableIterator<indexContainer> wordContainers(String startWordHash, boolean rot) throws IOException; // method to replace wordHashes
    public boolean hasContainer(String wordHash); // should only be used if in case that true is returned the getContainer is NOT called
    public indexContainer getContainer(String wordHash, Set<String> urlselection) throws IOException; // if urlselection != null all url references which are not in urlselection are removed from the container
    public indexContainer deleteContainer(String wordHash) throws IOException;
    public boolean removeEntry(String wordHash, String urlHash) throws IOException;
    public int removeEntries(String wordHash, Set<String> urlHashes) throws IOException;
    public void addEntries(indexContainer newEntries) throws IOException;
    public void clear() throws IOException;
    public void close();

}
