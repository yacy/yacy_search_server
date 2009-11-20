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

package net.yacy.kelondro.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * extends the sortStack in such a way that it adds a list where objects, that had
 * been pulled from the stack with pop are listed. Provides access methods to address
 * specific elements in the list.
 * @param <E>
 */
public class SortStore<E> extends SortStack<E> {
    
    private static final Object PRESENT = new Object(); // Dummy value to associate with an Object in the backing Map
    private final ArrayList<stackElement> offstack; // objects that had been on the stack but had been removed
    private ConcurrentHashMap<E, Object> offset; // keeps track which element has been on the stack or is now in the offstack
    private long largest;
    
    public SortStore() {
        this(-1);
    }
    
    public SortStore(final int maxsize) {
        super(maxsize);
        this.largest = Long.MIN_VALUE;
        this.offstack = new ArrayList<stackElement>();
        this.offset = new ConcurrentHashMap<E, Object>();
    }
    
    public int size() {
        return super.size() + this.offstack.size();
    }
    
    public int sizeStore() {
        return this.offstack.size();
    }

    public void push(final E element, final Long weight) {
        if (this.offset.containsKey(element)) return;
        if (super.exists(element)) return;
        super.push(element, weight);
        this.largest = Math.max(this.largest, weight.longValue());
        if (this.maxsize <= 0) return;
        while ((super.size() > 0) && (this.size() > this.maxsize)) {
            this.pop();
        }
    }

    /**
     * return the element that is currently on top of the stack
     * it is removed and added to the offstack list
     * this is exactly the same as element(offstack.size())
     */
    public stackElement pop() {
        final stackElement se = super.pop();
        if (se == null) return null;
        this.offset.put(se.element, PRESENT);
        this.offstack.add(se);
        return se;
    }
    
    public stackElement top() {
        return super.top();
    }
    
    public boolean exists(final E element) {
        return super.exists(element) || this.offset.containsKey(element);
    }
    
    /**
     * return an element from a specific position. It is either taken from the offstack,
     * or removed from the onstack.
     * The offstack will grow if elements are not from the offstack and present at the onstack.
     * @param position
     * @return
     */
    public stackElement element(final int position) {
        if (position < this.offstack.size()) {
            return this.offstack.get(position);
        }
        if (position >= super.size() + this.offstack.size()) return null; // we don't have that element
        while (position >= this.offstack.size()) this.pop();
        return this.offstack.get(position);
    }
    
    /**
     * return the specific amount of entries. If they are not yet present in the offstack, they are shifted there from the onstack
     * if count is < 0 then all elements are taken
     * the returned list is not cloned from the internal list and shall not be modified in any way (read-only)
     * @param count
     * @return
     */
    public ArrayList<stackElement> list(final int count) {
        if (count < 0) {
            // shift all elements
            while (super.size() > 0) this.pop();
            return this.offstack;
        }
        if (count > super.size() + this.offstack.size()) throw new RuntimeException("list(" + count + ") exceeded avaiable number of elements (" + size() + ")"); 
        while (count > this.offstack.size()) this.pop();
        return this.offstack;
    }
    
    public void remove(final E element) {
        super.remove(element);
        synchronized (this.offstack) {
        Iterator<stackElement> i = this.offstack.iterator();
            while (i.hasNext()) {
                if (i.next().element.equals(element)) {
                    i.remove();
                    return;
                }
            }
        }
    }
    
    public synchronized boolean bottom(final long weight) {
        if (super.bottom(weight)) return true;
        return weight > this.largest;
    }

    public static void main(String[] args) {
        SortStore<String> a = new SortStore<String>();
        a.push("abc", 1L);
        a.pop();
        a.push("abc", 2L);
        a.push("6s_7dfZk4xvc", 1L);
        a.push("6s_7dfZk4xvc", 1L);
        a.pop();
        System.out.println("size = " + a.size());
    }
}
