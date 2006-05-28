// indexAbstractConatiner.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 20.05.2006 on http://www.anomic.de
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

import de.anomic.kelondro.kelondroBase64Order;

public abstract class indexAbstractContainer implements indexContainer {

    private String wordHash;
    private long updateTime;

    public void setWordHash(String newWordHash) {
        // this is used to replicate a container for different word indexes during global search
        this.wordHash = newWordHash;
    }
    
    public long updated() {
        return updateTime;
    }
    
    public String wordHash() {
        return wordHash;
    }

    public int add(indexEntry entry) {
        return add(entry, System.currentTimeMillis());
    }

    public int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        if (!wordHash.equals(this.wordHash)) return 0;
        int count = 0;
        for (int i = 0; i < urlHashes.length; i++) count += (remove(urlHashes[i]) == null) ? 0 : 1;
        return count;
    }
    
    public int hashCode() {
        return (int) kelondroBase64Order.enhancedCoder.decodeLong(this.wordHash.substring(0, 4));
    }

}
