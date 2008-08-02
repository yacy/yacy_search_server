// kelondroCloneableMapIterator.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 25.04.2007 on http://yacy.net
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


package de.anomic.kelondro;

import java.util.Iterator;
import java.util.TreeMap;

public class kelondroCloneableMapIterator<E> implements kelondroCloneableIterator<E> {

	TreeMap<E, ?> map;
	E next, last;
	Object start;
	Iterator<E> iter;
	

	public kelondroCloneableMapIterator(final TreeMap<E, ?> map, final E start) {
		// map must contain eiter a byte[]/Object or a String/Object mapping.
		// start must be either of type byte[] or String
        // this iterator iterates then only the key elements of the map
		this.map = map;
		this.start = start;
		this.iter = map.keySet().iterator();
		if (this.start == null) {
			if (iter.hasNext()) this.next = iter.next(); else this.next = null;
		} else while (iter.hasNext()) {
			this.next = iter.next();
			if (map.comparator().compare(next, start) > 1) break;
		}
		this.last = null;
	}
	
	@SuppressWarnings("unchecked")
    public kelondroCloneableMapIterator<E> clone(final Object modifier) {
		return new kelondroCloneableMapIterator(map, modifier);
	}

	public boolean hasNext() {
		return this.next != null;
	}

	public E next() {
		// returns key-elements, not entry-elements
		this.last = this.next;
		if (this.iter.hasNext()) {
			this.next = this.iter.next();
		} else {
			this.next = null;
		}
		return this.last;
	}

	public void remove() {
		this.map.remove(this.last);
	}

}
