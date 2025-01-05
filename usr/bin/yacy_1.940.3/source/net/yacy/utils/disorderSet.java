// disorderSet.java 
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class disorderSet extends HashSet<String> implements Set<String> {

    private static final long serialVersionUID = 1L;
    private disorderHeap dh;

    public disorderSet() {
        super();
        dh = null;
    }

    public boolean hasAny() {
        return !this.isEmpty();
    }

    public String any() {
        // return just any element
        if (dh == null || dh.isEmpty()) {
            if (this.isEmpty()) return null;
            // fill up the queue
            dh = new disorderHeap();
            final Iterator<String> elements = this.iterator();
            while (elements.hasNext()) dh.add(elements.next());
        }
        return dh.remove();
    }

    public static void main(final String[] args) {
        final disorderSet ds = new disorderSet();
        ds.addAll(Arrays.asList(args));
        for (int i = 0; i < args.length * 3; i++) System.out.print(ds.any() + " ");
        System.out.println();
    }

}