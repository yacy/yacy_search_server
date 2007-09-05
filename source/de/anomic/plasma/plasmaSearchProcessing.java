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
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.server.serverByteBuffer;

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
    


    public static final serverByteBuffer compressIndex(indexContainer inputContainer, indexContainer excludeContainer, long maxtime) {
        // collect references according to domains
        long timeout = (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        TreeMap doms = new TreeMap();
        synchronized (inputContainer) {
            Iterator i = inputContainer.entries();
            indexRWIEntry iEntry;
            String dom, paths;
            while (i.hasNext()) {
                iEntry = (indexRWIEntry) i.next();
                if ((excludeContainer != null) && (excludeContainer.get(iEntry.urlHash()) != null)) continue; // do not include urls that are in excludeContainer
                dom = iEntry.urlHash().substring(6);
                if ((paths = (String) doms.get(dom)) == null) {
                    doms.put(dom, iEntry.urlHash().substring(0, 6));
                } else {
                    doms.put(dom, paths + iEntry.urlHash().substring(0, 6));
                }
                if (System.currentTimeMillis() > timeout)
                    break;
            }
        }
        // construct a result string
        serverByteBuffer bb = new serverByteBuffer(inputContainer.size() * 6);
        bb.append('{');
        Iterator i = doms.entrySet().iterator();
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            bb.append((String) entry.getKey());
            bb.append(':');
            bb.append((String) entry.getValue());
            if (System.currentTimeMillis() > timeout)
                break;
            if (i.hasNext())
                bb.append(',');
        }
        bb.append('}');
        return bb;
    }

    public static final void decompressIndex(TreeMap target, serverByteBuffer ci, String peerhash) {
        // target is a mapping from url-hashes to a string of peer-hashes
        if ((ci.byteAt(0) == '{') && (ci.byteAt(ci.length() - 1) == '}')) {
            //System.out.println("DEBUG-DECOMPRESS: input is " + ci.toString());
            ci = ci.trim(1, ci.length() - 2);
            String dom, url, peers;
            while ((ci.length() >= 13) && (ci.byteAt(6) == ':')) {
                assert ci.length() >= 6 : "ci.length() = " + ci.length();
                dom = ci.toString(0, 6);
                ci.trim(7);
                while ((ci.length() > 0) && (ci.byteAt(0) != ',')) {
                    assert ci.length() >= 6 : "ci.length() = " + ci.length();
                    url = ci.toString(0, 6) + dom;
                    ci.trim(6);
                    peers = (String) target.get(url);
                    if (peers == null) {
                        target.put(url, peerhash);
                    } else {
                        target.put(url, peers + peerhash);
                    }
                    //System.out.println("DEBUG-DECOMPRESS: " + url + ":" + target.get(url));
                }
                if (ci.byteAt(0) == ',') ci.trim(1);
            }
        }
    }


}
