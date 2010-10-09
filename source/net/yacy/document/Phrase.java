/**
 *  Phrase.java
 *  Copyright 2008 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 26.03.2008 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document;

import java.util.HashSet;

public class Phrase {
    // object carries statistics for words and sentences
    
    private int count;                   // number of occurrences
    private final int handle;            // unique handle, is initialized with sentence counter
    private final HashSet<Integer> hash; //

    public Phrase(final int handle) {
        this.count = 1;
        this.handle = handle;
        this.hash = new HashSet<Integer>();
    }

    public int handle() {
        return this.handle;
    }
    
    public int occurrences() {
        return count;
    }

    public void inc() {
        count++;
    }

    public void check(final int i) {
        hash.add(LargeNumberCache.valueOf(i));
    }

    
}
