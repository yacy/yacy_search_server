/**
 *  AccessTracker
 *  an interface for Adaptive Replacement Caches
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.08.2009 at http://yacy.net
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

package de.anomic.search;

import java.util.Iterator;
import java.util.LinkedList;

public class AccessTracker {

    public static final int minSize = 1000;
    public static final int maxSize = 5000;
    public static final int maxAge = 10 * 60 * 1000;
    
    public enum Location {local, remote}
    
    private static LinkedList<QueryParams> localSearches = new LinkedList<QueryParams>();
    private static LinkedList<QueryParams> remoteSearches = new LinkedList<QueryParams>();
    
    public static void add(Location location, QueryParams query) {
        if (location == Location.local) synchronized (localSearches) {add(localSearches, query);}
        if (location == Location.remote) synchronized (remoteSearches) {add(remoteSearches, query);}
    }
    
    private static void add(LinkedList<QueryParams> list, QueryParams query) {
        list.add(query);
        while (list.size() > maxSize) list.removeFirst();
        if (list.size() <= minSize) {
            return;
        }
        long timeout = System.currentTimeMillis() - maxAge;
        while (list.size() > 0) {
            QueryParams q = list.getFirst();
            if (q.time.longValue() > timeout) break;
            list.removeFirst();
        }
    }
    
    public static Iterator<QueryParams> get(Location location) {
        if (location == Location.local) return localSearches.descendingIterator();
        if (location == Location.remote) return remoteSearches.descendingIterator();
        return null;
    }
    
    public static int size(Location location) {
        if (location == Location.local) synchronized (localSearches) {return localSearches.size();}
        if (location == Location.remote) synchronized (remoteSearches) {return remoteSearches.size();}
        return 0;
    }
}
