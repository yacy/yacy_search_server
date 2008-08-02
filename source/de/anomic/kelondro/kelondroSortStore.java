// kelondroSortStore.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.02.2008 on http://yacy.net
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

import java.util.ArrayList;
import java.util.Iterator;

public class kelondroSortStore<E> extends kelondroSortStack<E> {

    // extends the sortStack in such a way that it adds a list where objects, that had
    // been pulled from the stack with pop are listed. Provides access methods to address
    // specific elements in the list.
    
    private final ArrayList<stackElement> offstack; // objects that had been on the stack but had been removed
    
    public kelondroSortStore(final int maxsize) {
        super(maxsize);
        this.offstack = new ArrayList<stackElement>();
    }
    
    public int size() {
        return super.onstack.size() + this.offstack.size();
    }
    
    public int sizeStore() {
        return this.offstack.size();
    }

    public synchronized void push(final E element, final Long weight) {
        super.push(element, weight);
        if (this.maxsize <= 0) return;
        while ((this.onstack.size() > 0) && (super.onstack.size() + this.offstack.size() > this.maxsize)) {
            this.onstack.remove(this.onstack.lastKey());
        }
    }

    public synchronized stackElement pop() {
        // returns the element that is currently on top of the stack
        // it is removed and added to the offstack list
        // this is exactly the same as element(offstack.size())
        final stackElement se = super.pop();
        if (se == null) return null;
        this.offstack.add(se);
        return se;
    }
    
    public synchronized stackElement element(final int position) {
        // returns an element from a specific position. It is either taken from the offstack,
        // or removed from the onstack.
        // The offstack will grow if elements are not from the offstack and present at the onstack.
        if (position < this.offstack.size()) {
            return this.offstack.get(position);
        }
        if (position >= size()) return null; // we don't have that element
        while (position >= this.offstack.size()) {
            final Long w = this.onstack.firstKey();
            final E element = this.onstack.remove(w);
            final stackElement se = new stackElement(element, w);
            this.offstack.add(se);
        }
        return this.offstack.get(position);
    }
    
    public ArrayList<stackElement> list(final int count) {
        // returns the specific amount of entries. If they are not yet present in the offstack, they are shifted there from the onstack
        // if count is < 0 then all elements are taken
        // the returned list is not cloned from the internal list and shall not be modified in any way (read-only)
        if (count < 0) {
            // shift all elements
            while (this.onstack.size() > 0) {
                final Long w = this.onstack.firstKey();
                final E element = this.onstack.remove(w);
                final stackElement se = new stackElement(element, w);
                this.offstack.add(se);
            }
            return this.offstack;
        }
        if (size() < count) throw new RuntimeException("list(" + count + ") exceeded avaiable number of elements (" + size() + ")"); 
        while (this.offstack.size() < count) {
            final Long w = this.onstack.firstKey();
            final E element = this.onstack.remove(w);
            final stackElement se = new stackElement(element, w);
            this.offstack.add(se);
        }
        return this.offstack;
    }
    
    public stackElement get(final int hashcode) {
        stackElement se = super.get(hashcode);
        if (se != null) return se;
        final Iterator<stackElement> j = this.offstack.iterator();
        while (j.hasNext()) {
            se = j.next();
            if (se.element.hashCode() == hashcode) return se;
        }
        return null;
    }
    
    public stackElement remove(final int hashcode) {
        stackElement se = super.remove(hashcode);
        if (se != null) return se;
        for (int j = 0; j < this.offstack.size(); j++) {
            se = this.offstack.get(j);
            if (se.element.hashCode() == hashcode) {
                this.offstack.remove(j);
                return se;
            }
        }
        return null;
    }
}
