/**
 *  IntScore
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.10.2010 at http://yacy.net
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

package net.yacy.cora.storage;

import java.util.Comparator;

/**
 * This class acts as a replacement for Long and shall be used as counter object in Object-Counter relations
 * The use case of this class is given when an value element of a map must be increased or decreased. If
 * the normal Long class is used, the new value must be rewritten to the map with an increased and newly allocated number object
 * When using this class, then only the score of the Number object can be changed without the need of
 * rewriting the new key value to a map.
 */
public class IntScore implements Comparable<IntScore>, Comparator<IntScore> {

    public static IntScore ZERO = new IntScore(0);
    public static IntScore ONE = new IntScore(1);
    
    private int value;
    
    public IntScore(int value) {
        this.value = value;
    }

    public final static IntScore valueOf(final int n) {
        return new IntScore(n);
    }
    
    public int intValue() {
        return this.value;
    }
    
    public void inc() {
        this.value++;
    }
    
    public void inc(int n) {
        this.value += n;
    }
    
    public void dec() {
        this.value--;
    }
    
    public void dec(int n) {
        this.value -= n;
    }
    
    public void set(int n) {
        this.value = n;
    }
    
    public void min(int n) {
        if (n < this.value) this.value = n;
    }
    
    public void max(int n) {
        if (n > this.value) this.value = n;
    }
    
    @Override
    public boolean equals(Object o) {
        return (o instanceof IntScore) && this.value == ((IntScore) o).value;
    }
    
    @Override
    public int hashCode() {
        return this.value;
        // return (int) (this.value ^ (this.value >>> 32)); // hash code for long values
    }

    public int compareTo(IntScore o) {
        int thisVal = this.value;
        int anotherVal = o.value;
        return thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1);
    }

    public int compare(IntScore o1, IntScore o2) {
        return o1.compareTo(o2);
    }
    
    @Override
    public String toString() {
        return Integer.toString(this.value);
    }
}
