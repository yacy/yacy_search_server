/**
 *  WeakPriorityBlockingQueue
 *  an priority blocking queue that drains elements if it gets too large
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 09.09.2010 at http://yacy.net
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

package net.yacy.cora.sorting;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * implements a stack where elements 'float' on-top of the stack according to a weight value.
 * objects pushed on the stack must implement the hashCode() method to provide a handle
 * for a double-check.
 * If the queue gets larger that the given maxsize, then elements from the tail of the queue
 * are drained (deleted).
 */
public class WeakPriorityBlockingQueue<E> implements Serializable {

	private static final long serialVersionUID = 4573442576760691887L;

	private final TreeSet<Element<E>>   queue;    // object within the stack, ordered using a TreeSet
    private final Semaphore    enqueued; // semaphore for elements in the stack
    private final ArrayList<Element<E>> drained;  // objects that had been on the stack but had been removed
    private int maxsize;

    /**
     * create a new WeakPriorityBlockingQueue
     * all elements in the stack are not ordered by their insert order but by a given element weight
     * weights that are preferred are returned first when a pop from the stack is made
     * @param maxsize the maximum size of the stack. When the stack exceeds this number, then entries are removed
     */
    public WeakPriorityBlockingQueue(final int maxsize, boolean drain) {
        // the maxsize is the maximum number of entries in the stack
        // if this is set to -1, the size is unlimited
        this.queue = new TreeSet<Element<E>>();
        this.drained = drain ? new ArrayList<Element<E>>() : null;
        this.enqueued = new Semaphore(0);
        this.maxsize = maxsize;
    }

    /**
     * clear the queue
     */
    public synchronized void clear() {
        if (this.drained != null) this.drained.clear();
        this.queue.clear();
        this.enqueued.drainPermits();
    }

    /**
     * test if the queue is empty
     * @return true if the queue is empty, false if not
     */
    public boolean isEmpty() {
        return this.queue.isEmpty() & (this.drained == null || this.drained.isEmpty());
    }

    /**
     * get the number of elements in the queue, waiting to be removed with take() or poll()
     * @return
     */
    public synchronized int sizeQueue() {
        return this.queue.size();
    }


    /**
     * get the number of elements that had been drained so far and are waiting
     * in a list to get enumerated with element()
     * @return
     */
    public synchronized int sizeDrained() {
        return this.drained == null ? 0 : this.drained.size();
    }

    /**
     * get the number of elements that are available for retrieval
     * this is a combined number of sizeQueue() and sizeDrained();
     * @return
     */
    public synchronized int sizeAvailable() {
        return this.maxsize < 0 ?
                        this.queue.size() + (this.drained == null ? 0 : this.drained.size()) :
                        Math.min(this.maxsize, this.queue.size() + (this.drained == null ? 0 : this.drained.size()));
    }

    /**
     * put a element on the stack using a order of the weight
     * elements that had been on the stack cannot be put in again,
     * they are checked against the drained list
     * @param element the element (must have a equals() method)
     * @param weight the weight of the element
     * @param remove - the rating of the element that shall be removed in case that the stack has an size overflow
     */
    public synchronized void put(final Element<E> element) {
        // put the element on the stack
        if (this.drained != null && this.drained.contains(element)) return;
        if (this.queue.size() == this.maxsize) {
            // remove last elements if stack is too large
            if (this.queue.add(element)) this.queue.remove(this.queue.last());
        } else {
            // just add entry but only release semaphore if entry was not double
            if (this.queue.add(element)) this.enqueued.release();
        }
        assert this.queue.size() >= this.enqueued.availablePermits() : "(put) queue.size() = " + this.queue.size() + ", enqueued.availablePermits() = " + this.enqueued.availablePermits();
    }

    /**
     * return the element with the smallest weight and remove it from the stack
     * @return null if no element is on the queue or the head of the queue
     */
    public Element<E> poll() {
        boolean a = this.enqueued.tryAcquire();
        if (!a) return null;
        synchronized (this) {
            return takeUnsafe();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * up to the specified wait time if no elements are present on this queue.
     * @param timeout milliseconds until timeout
     * @return the head element from the queue
     * @throws InterruptedException
     */
    public Element<E> poll(long timeout) throws InterruptedException {
        boolean a = (timeout <= 0) ? this.enqueued.tryAcquire() : this.enqueued.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        if (!a) return null;
        synchronized (this) {
            return takeUnsafe();
        }
    }

    private Element<E> takeUnsafe() {
        final Element<E> element = this.queue.pollFirst();
        assert element != null;
        if (this.drained != null && (this.maxsize == -1 || this.drained.size() < this.maxsize)) this.drained.add(element);
        assert this.queue.size() >= this.enqueued.availablePermits() : "(take) queue.size() = " + this.queue.size() + ", enqueued.availablePermits() = " + this.enqueued.availablePermits();
        return element;
    }
    
    /**
     * remove a drained element
     * @param element
     */
    /*
    public void removeDrained(Element<E> element) {
        if (element == null) return;
        synchronized (this.drained) {
            int p = this.drained.size() - 1;
            if (this.drained.get(p) == element) {
                this.drained.remove(p);
                return;
            }
        }
        this.drained.remove(element);
    }
    */
    
    /**
     * return the element with the smallest weight, but do not remove it
     * @return null if no element is on the queue or the head of the queue
     */
    public synchronized Element<E> peek() {
        if (this.queue.isEmpty()) return null;
        return this.queue.first();
    }

    /**
     * all objects that have been returned by poll or take are stored in a back-up list
     * where they can be retrieved afterward. The elements from that list are stored in
     * the specific order as they had been retrieved. This method returns the elements
     * in that specific order and if the list is not large enough, elements available
     * with poll() are taken and written to the list until the required position is
     * written. If the stach size together with the recorded list is not large enough,
     * null is returned
     * @param position inside the drained queue
     * @return the element from the recorded position or null if that position is not available
     */
    public Element<E> element(final int position) {
        if (this.drained == null) return null;
        if (position < this.drained.size()) {
            return this.drained.get(position);
        }
        synchronized (this) {
            if (position >= this.queue.size() + this.drained.size()) return null; // we don't have that element
            Element<E> p;
            int s;
            while (position >= this.drained.size()) {
                s = this.drained.size();
                p = this.poll();
                if (this.drained.size() <= s) break;
                if (p == null) break;
            }
            if (position >= this.drained.size()) return null;
            return this.drained.get(position);
        }
    }

    /**
     * retrieve an element from the drained queue but wait until a timeout
     * until returning null when no element will be available within the time
     * from the input queue
     * @param position inside the drained queue
     * @param time the timeout
     * @return the element from the recorded position or null if that position is not available within the timeout
     * @throws InterruptedException
     */
    public Element<E> element(final int position, long time) throws InterruptedException {
        if (this.drained == null) return null;
        long timeout = time == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + time;
        if (position < this.drained.size()) {
            return this.drained.get(position);
        }
        while (position >= this.drained.size()) {
            long t = timeout - System.currentTimeMillis();
            if (t <= 0) break;
            this.poll(t);
        }
        if (position >= this.drained.size()) return null; // we still don't have that element
        return this.drained.get(position);
    }

    /**
     * return the specific amount of entries as they would be retrievable with element()
     * if count is < 0 then all elements are taken
     * the returned list is not cloned from the internal list and shall not be modified in any way (read-only)
     * @param count
     * @return a list of elements in the stack
     */
    public synchronized ArrayList<Element<E>> list(final int count) {
        if (this.drained == null) return null;
        if (count < 0) {
            return list();
        }
        if (count > sizeAvailable()) throw new RuntimeException("list(" + count + ") exceeded avaiable number of elements (" + sizeAvailable() + ")");
        while (count > this.drained.size()) this.poll();
        return this.drained;
    }

    /**
     * return all entries as they would be retrievable with element()
     * @return a list of all elements in the stack
     */
    private synchronized ArrayList<Element<E>> list() {
        if (this.drained == null) return null;
        // shift all elements
        while (!this.queue.isEmpty()) this.poll();
        return this.drained;
    }

    /**
     * iterate over all elements available. All elements that are still in the queue are drained to recorded positions
     * @return an iterator over all drained positions.
     */
    public synchronized Iterator<Element<E>> iterator() {
        if (this.drained == null) return null;
        // shift all elements to the offstack
        while (!this.queue.isEmpty()) this.poll();
        return this.drained.iterator();
    }

    public interface Element<E> extends Serializable {
        public long getWeight();
        public E getElement();
        public boolean equals(Element<E> o);
        @Override
        public int hashCode();
        @Override
        public String toString();
    }

    private abstract static class AbstractElement<E> implements Element<E>, Serializable {

		private static final long serialVersionUID = -7026597258248026566L;

		public long weight;
        public E element;

        @Override
        public long getWeight() {
            return this.weight;
        }

        @Override
        public E getElement() {
            return this.element;
        }

        @Override
        public boolean equals(Element<E> o) {
            return this.element.equals(o.getElement());
        }

        @Override
        public int hashCode() {
            return this.element.hashCode();
        }

        @Override
        public String toString() {
            return this.element.toString() + "/" + this.weight;
        }
    }

    /**
     * natural ordering elements, can be used as container of objects <E> in the priority queue
     * the elements with smallest ordering weights are first in the queue when elements are taken
     */
    public static class NaturalElement<E> extends AbstractElement<E> implements Element<E>, Comparable<NaturalElement<E>>, Comparator<NaturalElement<E>> {

		private static final long serialVersionUID = 6816543012966928794L;

		public NaturalElement(final E element, final long weight) {
            this.element = element;
            this.weight = weight;
        }

        @Override
        public int compare(NaturalElement<E> o1, NaturalElement<E> o2) {
            return o1.compareTo(o2);
        }

        @Override
        public int compareTo(NaturalElement<E> o) {
            if (this.element == o.getElement()) return 0;
            if (this.element.equals(o.getElement())) return 0;
            if (this.weight > o.getWeight()) return 1;
            if (this.weight < o.getWeight()) return -1;
            int o1h = this.hashCode();
            int o2h = o.hashCode();
            if (o1h > o2h) return 1;
            if (o1h < o2h) return -1;
            return 0;
        }

    }

    /**
     * reverse ordering elements, can be used as container of objects <E> in the priority queue
     * the elements with highest ordering weights are first in the queue when elements are taken
     */
    public static class ReverseElement<E> extends AbstractElement<E> implements Element<E>, Comparable<ReverseElement<E>>, Comparator<ReverseElement<E>> {

		private static final long serialVersionUID = -8166724491837508921L;

		public ReverseElement(final E element, final long weight) {
            this.element = element;
            this.weight = weight;
        }

        @Override
        public int compare(ReverseElement<E> o1, ReverseElement<E> o2) {
            return o1.compareTo(o2);
        }

        @Override
        public int compareTo(ReverseElement<E> o) {
            if (this.element == o.getElement()) return 0;
            if (this.element.equals(o.getElement())) return 0;
            if (this.weight > o.getWeight()) return -1;
            if (this.weight < o.getWeight()) return 1;
            int o1h = this.hashCode();
            int o2h = o.hashCode();
            if (o1h > o2h) return -1;
            if (o1h < o2h) return 1;
            return 0;
        }
    }

    public static void main(String[] args) {
        final WeakPriorityBlockingQueue<String> a = new WeakPriorityBlockingQueue<String>(3, true);
        //final Element<String> REVERSE_POISON = new ReverseElement<String>("", Long.MIN_VALUE);
        new Thread(){
            @Override
            public void run() {
                Element<String> e;
                try {
                    while ((e = a.poll(1000)) != null) System.out.println("> " + e.toString());
                } catch (final InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }.start();
        a.put(new ReverseElement<String>("abc", 1));
        //a.poll();
        a.put(new ReverseElement<String>("abcx", 2));
        a.put(new ReverseElement<String>("6s_7dfZk4xvc", 3));
        a.put(new ReverseElement<String>("6s_7dfZk4xvcx", 4));
        //a.put((Element<String>) REVERSE_POISON);
        //a.poll();
        System.out.println("size = " + a.sizeAvailable());
        //while (a.sizeQueue() > 0) System.out.println("> " + a.poll().toString());
    }
}
