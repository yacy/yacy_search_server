/**
 *  AccessTracker
 *  an interface for Adaptive Replacement Caches
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.08.2009 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.logging.Log;

public class AccessTracker {

    public static final int minSize = 1000;
    public static final int maxSize = 5000;
    public static final int maxAge = 10 * 60 * 1000;
    
    public enum Location {local, remote}
    
    private static LinkedList<QueryParams> localSearches = new LinkedList<QueryParams>();
    private static LinkedList<QueryParams> remoteSearches = new LinkedList<QueryParams>();
    private static ArrayList<String> log = new ArrayList<String>();
    
    public static void add(Location location, QueryParams query) {
        if (location == Location.local) synchronized (localSearches) {add(localSearches, query);}
        if (location == Location.remote) synchronized (remoteSearches) {add(remoteSearches, query);}
    }
    
    private static void add(LinkedList<QueryParams> list, QueryParams query) {
        list.add(query);
        while (list.size() > maxSize) {
            addToDump(list.removeFirst());
        }
        if (list.size() <= minSize) {
            return;
        }
        long timeout = System.currentTimeMillis() - maxAge;
        while (list.size() > 0) {
            QueryParams q = list.getFirst();
            if (q.time.longValue() > timeout) break;
            addToDump(list.removeFirst());
        }
        // learn that this word can be a word completion for the DidYouMeanLibrary
        if (query.resultcount > 0 && query.queryString != null && query.queryString.length() > 0) LibraryProvider.dymLib.learn(query.queryString);
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
    
    private static void addToDump(QueryParams query) {
        //if (query.resultcount == 0) return;
        if (query.queryString == null || query.queryString.length() == 0) return;
        StringBuilder sb = new StringBuilder(40);
        sb.append(GenericFormatter.SHORT_SECOND_FORMATTER.format());
        sb.append(' ');
        sb.append(Integer.toString(query.resultcount));
        sb.append(' ');
        sb.append(query.queryString);
        log.add(sb.toString());
    }
    
    public static void dumpLog(File file) {
        while (localSearches.size() > 0) {
            addToDump(localSearches.removeFirst());
        }
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            raf.seek(raf.length());
            for (String s: log) {
                raf.write(UTF8.getBytes(s));
                raf.writeByte(10);
            }
            log.clear();
        } catch (FileNotFoundException e) {
            Log.logException(e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
