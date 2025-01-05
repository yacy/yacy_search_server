/**
 *  ByteOrder
 *  (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 25.04.2007 on http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.order;

import java.util.Iterator;
import java.util.TreeMap;



public class CloneableMapIterator<E> implements CloneableIterator<E> {

	TreeMap<E, ?> map;
	E next, last;
	Object start;
	Iterator<E> iter;


	public CloneableMapIterator(final TreeMap<E, ?> map, final E start) {
		// map must contain eiter a byte[]/Object or a String/Object mapping.
		// start must be either of type byte[] or String
        // this iterator iterates then only the key elements of the map
		this.map = map;
		this.start = start;
		this.iter = map.keySet().iterator();
		if (this.start == null) {
			if (this.iter.hasNext()) this.next = this.iter.next(); else this.next = null;
		} else while (this.iter.hasNext()) {
			this.next = this.iter.next();
			if (map.comparator().compare(this.next, start) > 1) break;
		}
		this.last = null;
	}

    @Override
    @SuppressWarnings("unchecked")
	public CloneableMapIterator<E> clone(final Object modifier) {
		return new CloneableMapIterator<E>(this.map, (E) modifier);
	}

	@Override
    public boolean hasNext() {
		return this.next != null;
	}

	@Override
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

	@Override
    public void remove() {
		this.map.remove(this.last);
	}

    @Override
    public void close() {
    }

}
