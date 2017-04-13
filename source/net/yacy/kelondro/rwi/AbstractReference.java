// AbstractReference.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.04.2009 on http://yacy.net
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

import java.util.Iterator;
import net.yacy.cora.util.ConcurrentLog;


public abstract class AbstractReference implements Reference {

    /**
     * The average distance (in words) between search query terms for multi word searches.
     * @return word distance
     */
    @Override
    public int distance() {
        // check if positions have been joined
        if (positions() == null || positions().isEmpty()) return 0;
        int d = 0;
        Iterator<Integer> i = positions().iterator();
        int s0 = posintext(); // init with own positon
        int s1;
        while (i.hasNext()) {
            s1 = i.next();
            if (s0 > 0) d += Math.abs(s0 - s1);
            s0 = s1;
        }
        // despite first line checks for size < 2 Arithmetic exception div by zero occured  (1.91/9278 2016-10-19)
        // added d == 0 condition as protection for this (which was in all above tests the case)
        try {
            return d == 0 ? 0 : d / positions().size();
        } catch (ArithmeticException ex) {
            ConcurrentLog.fine("AbstractReference", "word distance calculation:" + ex.getMessage());
            return 0;
        }
    }
    
    @Override
    public boolean isOlder(final Reference other) {
        if (other == null) return false;
        if (this.lastModified() < other.lastModified()) return true;
        return false;
    }
}
