// plasmaSearchProcessing.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.10.2005 on http://yacy.net
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

package de.anomic.plasma;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import de.anomic.index.indexContainer;

/**
 *
 * This class provides search processes and keeps a timing record of the processes
 * It shall be used to initiate a search and also to evaluate
 * the real obtained timings after a search is performed
 */

public class plasmaSearchProcessing implements Cloneable {
    
    // collection:
    // time = time to get a RWI out of RAM cache, assortments and WORDS files
    // count = maximum number of RWI-entries that shall be collected
    
    // join
    // time = time to perform the join between all collected RWIs
    // count = maximum number of entries that shall be joined
    
    // presort:
    // time = time to do a sort of the joined URL-records
    // count = maximum number of entries that shall be pre-sorted
    
    // urlfetch:
    // time = time to fetch the real URLs from the LURL database
    // count = maximum number of urls that shall be fetched
    
    // postsort:
    // time = time for final sort of URLs
    // count = maximum number oof URLs that shall be retrieved during sort
    
    // snippetfetch:
    // time = time to fetch snippets for selected URLs
    // count = maximum number of snipptes to be fetched
    
    public static final String COLLECTION   = "collection";
    public static final String JOIN         = "join";
    public static final String PRESORT      = "presort";
    public static final String URLFETCH     = "urlfetch";
    
    private static final long minimumTargetTime = 100;

    private long targetTime;
    private int  targetCount;
    private ArrayList yield;
    private long timer;
    
    private plasmaSearchProcessing() {
        targetTime = minimumTargetTime;
        targetCount = 10;
        yield = new ArrayList();
        timer = 0;
    }
    
    public plasmaSearchProcessing(long time, int count) {
        this();
        this.targetTime = time;
        this.targetCount = count;
    }
    
    public static class Entry {
        public String process;
        public int count;
        public long time;
        public Entry(String process, int count, long time) {
            this.process = process;
            this.count = count;
            this.time = time;
        }
    }

    public int getTargetCount() {
        return this.targetCount;
    }
    
    public long getTargetTime() {
        return this.targetTime;
    }

    public void startTimer() {
        this.timer = System.currentTimeMillis();
    }

    public void yield(String s, int count) {
        long t = System.currentTimeMillis() - this.timer;
        Entry e = new Entry(s, count, t);
        yield.add(e);
    }
    
    public Iterator events() {
        // iteratese Entry-type Objects
        return yield.iterator();
    }

    public int size() {
        // returns number of events / Entry-Objects in yield array
        return yield.size();
    }
    
    // collection
    public Map[] localSearchContainers(
            plasmaSearchQuery query,
            plasmaWordIndex wordIndex,
            Set urlselection) {
        // search for the set of hashes and return a map of of wordhash:indexContainer containing the seach result

        // retrieve entities that belong to the hashes
        startTimer();
        Map inclusionContainers = (query.queryHashes.size() == 0) ? new HashMap() : wordIndex.getContainers(
                        query.queryHashes,
                        urlselection,
                        true,
                        true);
        if ((inclusionContainers.size() != 0) && (inclusionContainers.size() < query.queryHashes.size())) inclusionContainers = new HashMap(); // prevent that only a subset is returned
        Map exclusionContainers = ((inclusionContainers == null) || (inclusionContainers.size() == 0)) ? new HashMap() : wordIndex.getContainers(
                query.excludeHashes,
                urlselection,
                true,
                true);
        yield(plasmaSearchProcessing.COLLECTION, inclusionContainers.size());

        return new Map[]{inclusionContainers, exclusionContainers};
    }
    
    // join
    public indexContainer localSearchJoinExclude(
            Collection includeContainers,
            Collection excludeContainers,
            int maxDistance) {
        // join a search result and return the joincount (number of pages after join)

        // since this is a conjunction we return an empty entity if any word is not known
        if (includeContainers == null) return plasmaWordIndex.emptyContainer(null, 0);

        // join the result
        startTimer();
        indexContainer rcLocal = indexContainer.joinContainers(includeContainers, maxDistance);
        if (rcLocal != null) {
            indexContainer.excludeContainers(rcLocal, excludeContainers);
        }
        if (rcLocal == null) rcLocal = plasmaWordIndex.emptyContainer(null, 0);
        yield(plasmaSearchProcessing.JOIN, rcLocal.size());

        return rcLocal;
    }
    
}
