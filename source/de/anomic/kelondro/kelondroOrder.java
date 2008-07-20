// kelondroOrder.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 29.12.2005
//
// $LastChangedDate: 2005-09-22 22:01:26 +0200 (Thu, 22 Sep 2005) $
// $LastChangedRevision: 774 $
// $LastChangedBy: orbiter $
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

import java.util.Comparator;

public interface kelondroOrder<A> extends Comparator<A> {

    public boolean wellformed(A a); // returns true if and only if a has only characters that belong to the implemented order
    
    public kelondroOrder<A> clone();
    
    public void direction(boolean ascending); // the ordering direction can be changed at any time
    
    public String signature(); // returns a signature String so that different orderings have different signatures
    
    public long partition(A key, int forkes);

    public long cardinal(A key); // returns a cardinal number in the range of 0 .. Long.MAX_VALUE

    public int compare(A a, A b);
    
    public A zero(); // returns the zero point of the Ordering; null if not defined

    public void rotate(A zero); // defines that the ordering rotates, and sets the zero point for the rotation
    
    public boolean equals(kelondroOrder<A> o); // used to compare different order objects; they may define the same ordering

}
