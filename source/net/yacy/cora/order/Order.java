/**
 *  Order
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.08.2011 at http://yacy.net
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

import java.util.Comparator;

public interface Order<A> extends Comparator<A> {

    /**
     * returns true if and only if a has only characters that belong to the implemented order
     * @param a
     * @return
     */
    public boolean wellformed(A a);

    public Order<A> clone();

    /**
     * the ordering direction can be changed at any time
     * @param ascending
     */
    public void direction(boolean ascending);

    /**
     * returns a signature String so that different orderings have different signatures
     * @return
     */
    public String signature();

    public long partition(A key, int forkes);

    /**
     * returns a cardinal number in the range of 0 .. Long.MAX_VALUE
     * @param key
     * @return
     */
    public long cardinal(A key);

    @Override
    public int compare(A a, A b);

    public boolean equal(A a, A b);

    /**
     * returns the zero point of the Ordering; null if not defined
     * @return
     */
    public A zero();

    /**
     * defines that the ordering rotates, and sets the zero point for the rotation
     * @param zero
     */
    public void rotate(A zero);

    /**
     * used to compare different order objects; they may define the same ordering
     */
    @Override
    public boolean equals(Object o);

    @Override
    public int hashCode();
}
