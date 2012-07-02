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

package net.yacy.search.query;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.document.WordCache;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;

public class AccessTracker {

    public static final int minSize = 100;
    public static final int maxSize = 1000;
    public static final int maxAge = 24 * 60 * 60 * 1000;

    public enum Location {local, remote}

    private static LinkedList<QueryParams> localSearches = new LinkedList<QueryParams>();
    private static LinkedList<QueryParams> remoteSearches = new LinkedList<QueryParams>();
    private static ArrayList<String> log = new ArrayList<String>();

    public static void add(final Location location, final QueryParams query) {
        if (location == Location.local) synchronized (localSearches) {add(localSearches, query);}
        if (location == Location.remote) synchronized (remoteSearches) {add(remoteSearches, query);}
    }

    private static void add(final LinkedList<QueryParams> list, final QueryParams query) {
        // learn that this word can be a word completion for the DidYouMeanLibrary
        if (query.resultcount > 10 && query.queryString != null && query.queryString.length() > 0) {
            final StringBuilder sb = new StringBuilder(query.queryString);
            sb.append(query.queryString);
            WordCache.learn(sb);
        }

        // add query to statistics list
        list.add(query);

        // shrink dump list but keep essentials in dump
        while (list.size() > maxSize || (list.size() > 0 && MemoryControl.shortStatus())) {
            synchronized (list) {
                if (list.size() > 0) addToDump(list.removeFirst()); else break;
            }
        }

        // if the list is small we can terminate
        if (list.size() <= minSize) return;

        // if the list is large we look for too old entries
        final long timeout = System.currentTimeMillis() - maxAge;
        while (list.size() > 0) {
            final QueryParams q = list.getFirst();
            if (q.starttime > timeout) break;
            addToDump(list.removeFirst());
        }
    }

    public static Iterator<QueryParams> get(final Location location) {
        if (location == Location.local) return localSearches.descendingIterator();
        if (location == Location.remote) return remoteSearches.descendingIterator();
        return null;
    }

    public static int size(final Location location) {
        if (location == Location.local) synchronized (localSearches) {return localSearches.size();}
        if (location == Location.remote) synchronized (remoteSearches) {return remoteSearches.size();}
        return 0;
    }

    private static void addToDump(final QueryParams query) {
        //if (query.resultcount == 0) return;
        if (query.queryString == null || query.queryString.length() == 0) return;
        final StringBuilder sb = new StringBuilder(40);
        sb.append(GenericFormatter.SHORT_SECOND_FORMATTER.format(new Date(query.starttime)));
        sb.append(' ');
        sb.append(Integer.toString(query.resultcount));
        sb.append(' ');
        sb.append(query.queryString);
        log.add(sb.toString());
    }

    public static void dumpLog(final File file) {
        while (localSearches.size() > 0) {
            addToDump(localSearches.removeFirst());
        }
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            raf.seek(raf.length());
            for (final String s: log) {
                raf.write(UTF8.getBytes(s));
                raf.writeByte(10);
            }
            log.clear();
        } catch (final FileNotFoundException e) {
            Log.logException(e);
        } catch (final IOException e) {
            Log.logException(e);
        } finally {
            if (raf != null) try {raf.close();} catch (IOException e) {}
        }
    }
}
