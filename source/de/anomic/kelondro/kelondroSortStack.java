// kelondroSortStack.java
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class kelondroSortStack<E> {

    // implements a stack where elements 'float' on-top of the stack according to a weight value.
    // objects pushed on the stack must implement the hashCode() method to provide a handle
    // for a double-check.
    
    protected TreeMap<Long, E> onstack; // object within the stack
    protected HashSet<Integer> instack; // keeps track which element has been on the stack or is now in the offstack
    protected int maxsize;
    
    public kelondroSortStack(final int maxsize) {
        // the maxsize is the maximum number of entries in the stack
        // if this is set to -1, the size is unlimited
        this.onstack = new TreeMap<Long, E>();
        this.instack = new HashSet<Integer>();
        this.maxsize = maxsize;
    }

    public int size() {
        return this.onstack.size();
    }
    
    public void push(final stackElement se) {
        push(se.element, se.weight);
    }
    
    public synchronized void push(final E element, Long weight) {
        if (exists(element)) return;
        
        // manipulate weight in such a way that it has no conflicts
        while (this.onstack.containsKey(weight)) weight = Long.valueOf(weight.longValue() + 1);
        
        // put the element on the stack
        this.onstack.put(weight, element);
        
        // register it for double-check
        this.instack.add(Integer.valueOf(element.hashCode()));

        // check maximum size of the stack an remove elements if the stack gets too large
        if (this.maxsize <= 0) return;
        while ((this.onstack.size() > 0) && (this.onstack.size() > this.maxsize)) {
            this.onstack.remove(this.onstack.lastKey());
        }
    }
    
    public synchronized stackElement top() {
        // returns the element that is currently on top of the stack
        if (this.onstack.isEmpty()) return null;
        final Long w = this.onstack.firstKey();
        final E element = this.onstack.get(w);
        return new stackElement(element, w);
    }
    
    public synchronized stackElement pop() {
        // returns the element that is currently on top of the stack
        // it is removed and added to the offstack list
        // this is exactly the same as element(offstack.size())
        if (this.onstack.isEmpty()) return null;
        final Long w = this.onstack.firstKey();
        final E element = this.onstack.remove(w);
        final stackElement se = new stackElement(element, w);
        return se;
    }
    
    public boolean exists(final E element) {
        // uses the hashCode of the element to find out of the element had been on the list or the stack
        return this.instack.contains(Integer.valueOf(element.hashCode()));
    }
    
    public boolean exists(final int hashcode) {
        // uses the hashCode of the element to find out of the element had been on the list or the stack
        return this.instack.contains(Integer.valueOf(hashcode));
    }
    
    public stackElement get(final int hashcode) {
        final Iterator<Map.Entry<Long, E>> i = this.onstack.entrySet().iterator();
        Map.Entry<Long, E> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue().hashCode() == hashcode) return new stackElement(entry.getValue(), entry.getKey());
        }
        return null;
    }
    
    public stackElement remove(final int hashcode) {
        final Iterator<Map.Entry<Long, E>> i = this.onstack.entrySet().iterator();
        Map.Entry<Long, E> entry;
        stackElement se;
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue().hashCode() == hashcode) {
                se = new stackElement(entry.getValue(), entry.getKey());
                this.onstack.remove(se.weight);
                return se;
            }
        }
        return null;
    }
    
    public boolean bottom(final long weight) {
        // returns true if the element with that weight would be on the bottom of the stack after inserting
        return weight > this.onstack.lastKey().longValue();
    }
    
    public class stackElement {
        public Long weight;
        public E element;
        public stackElement(final E element, final Long weight) {
            this.element = element;
            this.weight = weight;
        }
    }
}
