// ReverseMapIterator.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.10.2010 on http://yacy.net
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

package net.yacy.kelondro.util;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;

public class ReverseMapIterator <E, F> implements Iterator<Map.Entry<E, F>> {
    private ArrayList<E> a;
    private Map<E, F> map;
    E last;

    public ReverseMapIterator(Map<E, F> map) {
        synchronized (map) {
            this.map = map;
            this.a = new ArrayList<E>();
            while (true) {
                try {
                    for (E e: map.keySet()) {
                        a.add(e);
                    }
                    break;
                } catch (final ConcurrentModificationException e) {
                    continue;
                }
            }
        }
    }
    
    @Override
    public boolean hasNext() {
        return !a.isEmpty();
    }

    @Override
    public Map.Entry<E, F> next() {
        this.last = a.remove(a.size() - 1);
        return new Entry0(this.last, this.map.get(this.last));
    }

    @Override
    public void remove() {
        this.map.remove(this.last);
    }
    
    private class Entry0 implements Map.Entry<E, F> {
        E e;
        F f;
        public Entry0(final E e, final F f) {
            this.e = e;
            this.f = f;
        }
        
        @Override
        public E getKey() {
            return this.e;
        }

        @Override
        public F getValue() {
            return this.f;
        }

        @Override
        public F setValue(F value) {
            F f0 = this.f;
            this.f = value;
            return f0;
        }
        
    }
}
