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

package net.yacy.kelondro.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class SortStack<E> {

    // implements a stack where elements 'float' on-top of the stack according to a weight value.
    // objects pushed on the stack must implement the hashCode() method to provide a handle
    // for a double-check.
 
    private static final Object PRESENT = new Object(); // Dummy value to associate with an Object in the backing Map
    private TreeMap<Long, List<E>> onstack; // object within the stack
    private ConcurrentHashMap<E, Object> instack; // keeps track which element has been on the stack
    protected int maxsize;
    private boolean upward;
    
    public SortStack(boolean upward) {
        this(-1, upward);
    }

    /**
     * create a new sort stack
     * all elements in the stack are not ordered by their insert order but by a given element weight
     * weights that are preferred are returned first when a pop from the stack is made
     * the stack may be ordered upward (preferring small weights) or downward (preferring high wights)
     * @param maxsize the maximum size of the stack. When the stack exceeds this number, then the worst entries according to entry order are removed
     * @param upward is the entry order and controls which elements are returned on pop. if true, then the smallest is returned first
     */
    public SortStack(final int maxsize, boolean upward) {
        // the maxsize is the maximum number of entries in the stack
        // if this is set to -1, the size is unlimited
        this.onstack = new TreeMap<Long, List<E>>();
        this.instack = new ConcurrentHashMap<E, Object>();
        this.maxsize = maxsize;
        this.upward = upward;
    }

    
    public boolean isEmpty() {
        return this.instack.isEmpty();
    }
    
    public int size() {
        /*
        int c = 0;
        synchronized (onstack) {
            for (List<E> l: onstack.values()) c += l.size();
            assert c == this.instack.size() : "c = " + c + "; this.size() = " + this.instack.size();
        }
        */
        return this.instack.size();
    }
    
    /**
     * put a element on the stack using a order of the weight
     * @param element
     * @param weight
     */
    public void push(final E element, Long weight) {
        // put the element on the stack
        synchronized (this.onstack) {
            if (this.instack.put(element, PRESENT) != null) return;
            
            List<E> l = this.onstack.get(weight);
            if (l == null) {
                l = new LinkedList<E>();
                l.add(element);
                this.onstack.put(weight, l);
            } else {
                l.add(element);
            }
            //this.instack.put(element, PRESENT);
        }
        // check maximum size of the stack an remove elements if the stack gets too large
        if (this.maxsize <= 0) return;
        while (!this.onstack.isEmpty() && this.onstack.size() > this.maxsize) synchronized (this.onstack) {
            List<E> l;
            if (!this.onstack.isEmpty() && this.onstack.size() > this.maxsize) {
                l = this.onstack.remove((this.upward) ? this.onstack.lastKey() : this.onstack.firstKey());
                for (E e: l) instack.remove(e);
            }
        }
    }
    
    /**
     * return the element with the smallest weight
     * @return
     */
    public stackElement top() {
        // returns the element that is currently on top of the stack
        final E element;
        final Long w;
        synchronized (this.onstack) {
            if (this.onstack.isEmpty()) return null;
            w = (this.upward) ? this.onstack.firstKey() : this.onstack.lastKey();
            final List<E> l = this.onstack.get(w);
            element = l.get(0);
        }
        return new stackElement(element, w);
    }
    
    /**
     * return the element with the smallest weight and remove it from the stack
     * @return
     */
    public stackElement pop() {
        // returns the element that is currently on top of the stack
        // it is removed and added to the offstack list
        final E element;
        final Long w;
        synchronized (this.onstack) {
            if (this.onstack.isEmpty()) return null;
            w = (this.upward) ? this.onstack.firstKey() : this.onstack.lastKey();
            final List<E> l = this.onstack.get(w);
            element = l.remove(0);
            this.instack.remove(element);
            if (l.isEmpty()) this.onstack.remove(w);
        }
        return new stackElement(element, w);
    }
    
    public boolean exists(final E element) {
        // uses the hashCode of the element to find out of the element had been on the list or the stack
        return this.instack.contains(element);
    }
    
    public void remove(final E element) {
        synchronized (this.onstack) {
            if (!this.instack.contains(element)) return;
            for (Map.Entry<Long,List<E>> entry: this.onstack.entrySet()) {
                Iterator<E> i = entry.getValue().iterator();
                while (i.hasNext()) {
                    if (i.next().equals(element)) {
                        i.remove();
                        if (entry.getValue().isEmpty()) {
                            this.onstack.remove(entry.getKey());
                        }
                        return;
                    }
                }
            }
        }
    }
    
    public boolean bottom(final long weight) {
        // returns true if the element with that weight would be on the bottom of the stack after inserting
        if (this.onstack.isEmpty()) return true;
        Long l;
        synchronized (this.onstack) {
            l = (this.upward) ? this.onstack.lastKey() : this.onstack.firstKey();
        }
        return weight > l.longValue();
    }
    
    public class stackElement {
        public Long weight;
        public E element;
        public stackElement(final E element, final Long weight) {
            this.element = element;
            this.weight = weight;
        }
        public String toString() {
            return element.toString() + "/" + weight;
        }
    }
}
