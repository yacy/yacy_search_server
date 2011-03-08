/**
 *  ASCIIComparator
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.2.2011 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This file is part of YaCy Content Integration
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

package net.yacy.cora.document;

import java.util.Comparator;

/**
 * this is a replacement of an insensitive collator object, produced by a RuleBasedCollator Class
 * The RuleBasedCollator is a very inefficient class if it is used only for insensitive ASCII comparisments
 * This class is a very simple comparator for Strings which can be used to compare also Strings with upper/lowercase
 * Strings without applying .toUpperCase or .toLowerCase
 * Strings must contain no other than ASCII code.
 */
public class ASCIIComparator implements Comparator<String> {

    public static final ASCIIComparator insensitiveASCIIComparator = new ASCIIComparator(true);
  
    public boolean insensitive;
    
    public ASCIIComparator(boolean insensitive) {
        this.insensitive = insensitive;
    }

    @Override
    public Object clone() {
        return this; // because we do not have any class variables that changes
    }

    public int compare(String s0, String s1) {
        if (s0 == null && s1 == null) return 0;
        if (s0 == null) return -1;
        if (s1 == null) return 1;
        int i = 0;
        int l0 = s0.length(), l1 = s1.length();
        int lm = Math.min(l0, l1);
        char c0, c1;
        while (i < lm) {
            c0 = s0.charAt(i);
            c1 = s1.charAt(i);
            if (this.insensitive && c0 >= 'A' && c0 <='Z') c0 = (char) ((byte) c0 + 32);
            if (this.insensitive && c1 >= 'A' && c1 <='Z') c1 = (char) ((byte) c1 + 32);
            if (c0 > c1) return 1;
            if (c1 > c0) return -1;
            i++;
        }
        if (l0 > l1) return 1;
        if (l1 > l0) return -1;
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj == this);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

}