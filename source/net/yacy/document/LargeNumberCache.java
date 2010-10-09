/**
 *  LargeNumberCache.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.10.2010 at http://yacy.net
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

package net.yacy.document;

/**
 * a LargeIntegerCache is used whenever a Integer.valueOf(int i) is used.
 * The Integer java class provides a cache for values from -128 to +127
 * which is not enough for the parser to organize word positions in texts
 * Using this large cache the parser has a lower memory allocation and is faster.
 */
public class LargeNumberCache {

        private static final int integerCacheLimit = 3000;
        private static final Integer integerCache[];

        // fill the cache
        static {
            integerCache = new Integer[integerCacheLimit];
            for (int i = 0; i < integerCache.length; i++) integerCache[i] = new Integer(i);
        }

    /**
     * Returns a Integer instance representing the specified int value.
     * If a new Integer instance is not required, this method
     * should generally be used in preference to the constructor
     * {@link #Integer(int)}, as this method is likely to yield
     * significantly better space and time performance by caching
     * frequently requested values.
     *
     * @param  i an int value.
     * @return a Integer instance representing i.
     */
    public final static Integer valueOf(final int i) {
        if (i < 0) return Integer.valueOf(i);
        if (i >= integerCacheLimit) return new Integer(i);
        return integerCache[i];
    }
    
}
