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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroException;
import de.anomic.server.logging.serverLog;

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
    
    public static final char PROCESS_COLLECTION   = 'c';
    public static final char PROCESS_JOIN         = 'j';
    public static final char PROCESS_PRESORT      = 'r';
    public static final char PROCESS_URLFETCH     = 'u';
    public static final char PROCESS_POSTSORT     = 'o';
    public static final char PROCESS_FILTER       = 'f';
    public static final char PROCESS_SNIPPETFETCH = 's';
    
    private static final long minimumTargetTime = 100;
    
    public static char[] sequence = new char[]{
        PROCESS_COLLECTION,
        PROCESS_JOIN,
        PROCESS_PRESORT,
        PROCESS_URLFETCH,
        PROCESS_POSTSORT,
        PROCESS_FILTER,
        PROCESS_SNIPPETFETCH
    };

    private HashMap targetTime;
    private HashMap targetCount;
    private HashMap yieldTime;
    private HashMap yieldCount;
    private long timer;
    
    private plasmaSearchProcessing() {
        targetTime = new HashMap();
        targetCount = new HashMap();
        yieldTime = new HashMap();
        yieldCount = new HashMap();
        timer = 0;
    }
    
    public plasmaSearchProcessing(long time, int count) {
        this(
          3 * time / 12, 10 * count, 
          1 * time / 12, 10 * count, 
          1 * time / 12, 10 * count, 
          2 * time / 12,  5 * count, 
          3 * time / 12, count,
          1 * time / 12, count, 
          1 * time / 12, 1
        );
    }
    
    public plasmaSearchProcessing(
            long time_collection,   int count_collection,
            long time_join,         int count_join,
            long time_presort,      int count_presort,
            long time_urlfetch,     int count_urlfetch,
            long time_postsort,     int count_postsort,
            long time_filter,       int count_filter,
            long time_snippetfetch, int count_snippetfetch) {
        this();
        
        targetTime.put(new Character(PROCESS_COLLECTION), new Long(time_collection));
        targetTime.put(new Character(PROCESS_JOIN), new Long(time_join));
        targetTime.put(new Character(PROCESS_PRESORT), new Long(time_presort));
        targetTime.put(new Character(PROCESS_URLFETCH), new Long(time_urlfetch));
        targetTime.put(new Character(PROCESS_POSTSORT), new Long(time_postsort));
        targetTime.put(new Character(PROCESS_FILTER), new Long(time_filter));
        targetTime.put(new Character(PROCESS_SNIPPETFETCH), new Long(time_snippetfetch));
        targetCount.put(new Character(PROCESS_COLLECTION), new Integer(count_collection));
        targetCount.put(new Character(PROCESS_JOIN), new Integer(count_join));
        targetCount.put(new Character(PROCESS_PRESORT), new Integer(count_presort));
        targetCount.put(new Character(PROCESS_URLFETCH), new Integer(count_urlfetch));
        targetCount.put(new Character(PROCESS_POSTSORT), new Integer(count_postsort));
        targetCount.put(new Character(PROCESS_FILTER), new Integer(count_filter));
        targetCount.put(new Character(PROCESS_SNIPPETFETCH), new Integer(count_snippetfetch));
        
    }

    public Object clone() {
        plasmaSearchProcessing p = new plasmaSearchProcessing();
        p.targetTime = (HashMap) this.targetTime.clone();
        p.targetCount = (HashMap) this.targetCount.clone();
        p.yieldTime = (HashMap) this.yieldTime.clone();
        p.yieldCount = (HashMap) this.yieldCount.clone();
        return p;
    }
    
    public plasmaSearchProcessing(String s) {
        targetTime = new HashMap();
        targetCount = new HashMap();
        yieldTime = new HashMap();
        yieldCount = new HashMap();
        
        intoMap(s, targetTime, targetCount);
    }
    
    public long duetime() {
        // returns the old duetime value as sum of all waiting times
        long d = 0;
        for (int i = 0; i < sequence.length; i++) {
            d += ((Long) targetTime.get(new Character(sequence[i]))).longValue();
        }
        return d;
    }
    
    public void putYield(String s) {
        intoMap(s, yieldTime, yieldCount);
    }

    public String yieldToString() {
        return toString(yieldTime, yieldCount);
    }
    
    public String targetToString() {
        return toString(targetTime, targetCount);
    }
    
    public long getTargetTime(char type) {
        // sum up all time that was demanded and subtract all that had been wasted
        long sum = 0;
        Long t;
        Character element;
        for (int i = 0; i < sequence.length; i++) {
            element = new Character(sequence[i]);
            t = (Long) targetTime.get(element);
            if (t != null) sum += t.longValue();
            if (type == sequence[i]) return (sum < 0) ? minimumTargetTime : sum;
            t = (Long) yieldTime.get(element);
            if (t != null) sum -= t.longValue();
        }
        return minimumTargetTime;
    }
    
    public int getTargetCount(char type) {
        Integer i = (Integer) targetCount.get(new Character(type));
        if (i == null) return -1; else return i.intValue();
    }
    
    public long getYieldTime(char type) {
        Long l = (Long) yieldTime.get(new Character(type));
        if (l == null) return -1; else return l.longValue();
    }
    
    public int getYieldCount(char type) {
        Integer i = (Integer) yieldCount.get(new Character(type));
        if (i == null) return -1; else return i.intValue();
    }
    
    public void startTimer() {
        this.timer = System.currentTimeMillis();
    }
    
    public void setYieldTime(char type) {
        // sets a time that is computed using the timer
        long t = System.currentTimeMillis() - this.timer;
        yieldTime.put(new Character(type), new Long(t));
    }
    
    public void setYieldCount(char type, int count) {
        yieldCount.put(new Character(type), new Integer(count));
    }
    
    public String reportToString() {
        return "target=" + toString(targetTime, targetCount) + "; yield=" + toString(yieldTime, yieldCount);
    }
    
    public static String toString(HashMap time, HashMap count) {
        // put this into a format in such a way that it can be send in a http header or post argument
        // that means that no '=' or spaces are allowed
        StringBuffer sb = new StringBuffer(sequence.length * 10);
        Character element;
        Integer xi;
        Long xl;
        for (int i = 0; i < sequence.length; i++) {
            element = new Character(sequence[i]);
            sb.append("t");
            sb.append(element);
            xl = (Long) time.get(element);
            sb.append((xl == null) ? "0" : xl.toString());
            sb.append("|");
            sb.append("c");
            sb.append(element);
            xi = (Integer) count.get(element);
            sb.append((xi == null) ? "0" : xi.toString());
            sb.append("|");
        }
        return sb.toString();
    }
    
    public static void intoMap(String s, HashMap time, HashMap count) {
        // this is the reverse method to toString
        int p = 0;
        char ct;
        String elt;
        String v;
        int p1;
        while ((p < s.length()) && ((p1 = s.indexOf('|', p)) > 0)) {
            ct = s.charAt(p);
            elt = s.substring(p + 1, p + 2);
            v = s.substring(p + 2, p1);
            if (ct == 't') {
                time.put(elt, new Long(Long.parseLong(v)));
            } else {
                count.put(elt, new Integer(Integer.parseInt(v)));
            }
        }
    }
    
    // the processes

    // collection
    public Map[] localSearchContainers(
            plasmaSearchQuery query,
            plasmaWordIndex wordIndex,
            Set urlselection) {
        // search for the set of hashes and return a map of of wordhash:indexContainer containing the seach result

        // retrieve entities that belong to the hashes
        startTimer();
        long start = System.currentTimeMillis();
        Map inclusionContainers = (query.queryHashes.size() == 0) ? new HashMap() : wordIndex.getContainers(
                        query.queryHashes,
                        urlselection,
                        true,
                        true,
                        getTargetTime(plasmaSearchProcessing.PROCESS_COLLECTION) * query.queryHashes.size() / (query.queryHashes.size() + query.excludeHashes.size()));
        if ((inclusionContainers.size() != 0) && (inclusionContainers.size() < query.queryHashes.size())) inclusionContainers = new HashMap(); // prevent that only a subset is returned
        long remaintime =  getTargetTime(plasmaSearchProcessing.PROCESS_COLLECTION) - System.currentTimeMillis() + start;
        Map exclusionContainers = ((inclusionContainers == null) || (inclusionContainers.size() == 0) || (remaintime <= 0)) ? new HashMap() : wordIndex.getContainers(
                query.excludeHashes,
                urlselection,
                true,
                true,
                remaintime);
        setYieldTime(plasmaSearchProcessing.PROCESS_COLLECTION);
        setYieldCount(plasmaSearchProcessing.PROCESS_COLLECTION, inclusionContainers.size());

        return new Map[]{inclusionContainers, exclusionContainers};
    }
    
    // join
    public indexContainer localSearchJoinExclude(
            Collection includeContainers,
            Collection excludeContainers,
            long time, int maxDistance) {
        // join a search result and return the joincount (number of pages after join)

        // since this is a conjunction we return an empty entity if any word is not known
        if (includeContainers == null) return plasmaWordIndex.emptyContainer(null);

        // join the result
        startTimer();
        long start = System.currentTimeMillis();
        indexContainer rcLocal = indexContainer.joinContainers(includeContainers, time, maxDistance);
        long remaining = getTargetTime(plasmaSearchProcessing.PROCESS_JOIN) - System.currentTimeMillis() + start;
        if ((rcLocal != null) && (remaining > 0)) {
            indexContainer.excludeContainers(rcLocal, excludeContainers, remaining);
        }
        if (rcLocal == null) rcLocal = plasmaWordIndex.emptyContainer(null);
        setYieldTime(plasmaSearchProcessing.PROCESS_JOIN);
        setYieldCount(plasmaSearchProcessing.PROCESS_JOIN, rcLocal.size());

        return rcLocal;
    }
    
    // presort
    public plasmaSearchPreOrder preSort(
            plasmaSearchQuery query,
            plasmaSearchRankingProfile ranking,
            indexContainer resultIndex) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        assert (resultIndex != null);
        
        long preorderTime = getTargetTime(plasmaSearchProcessing.PROCESS_PRESORT);
        
        startTimer();
        long pst = System.currentTimeMillis();
        resultIndex.sort();
        resultIndex.uniq(1000);
        preorderTime = preorderTime - (System.currentTimeMillis() - pst);
        if (preorderTime < 0) preorderTime = 200;
        plasmaSearchPreOrder preorder = new plasmaSearchPreOrder(query, ranking, resultIndex, preorderTime);
        if (resultIndex.size() > query.wantedResults) preorder.remove(true, true);
        setYieldTime(plasmaSearchProcessing.PROCESS_PRESORT);
        setYieldCount(plasmaSearchProcessing.PROCESS_PRESORT, resultIndex.size());
        
        return preorder;
    }
    
    // urlfetch
    public plasmaSearchPostOrder urlFetch(
            plasmaSearchQuery query,
            plasmaSearchRankingProfile ranking,
            plasmaWordIndex wordIndex,
            plasmaSearchPreOrder preorder) {

        // start url-fetch
        long postorderTime = getTargetTime(plasmaSearchProcessing.PROCESS_POSTSORT);
        //System.out.println("DEBUG: postorder-final (urlfetch) maxtime = " + postorderTime);
        long postorderLimitTime = (postorderTime < 0) ? Long.MAX_VALUE : (System.currentTimeMillis() + postorderTime);
        startTimer();
        plasmaSearchPostOrder acc = new plasmaSearchPostOrder(query, ranking);
        
        indexRWIEntry entry;
        indexURLEntry page;
        Long preranking;
        Object[] preorderEntry;
        indexURLEntry.Components comp;
        String pagetitle, pageurl, pageauthor;
        int minEntries = getTargetCount(plasmaSearchProcessing.PROCESS_POSTSORT);
        try {
            ordering: while (preorder.hasNext()) {
                if ((System.currentTimeMillis() >= postorderLimitTime) || (acc.sizeFetched() >= minEntries)) break;
                preorderEntry = preorder.next();
                entry = (indexRWIEntry) preorderEntry[0];
                // load only urls if there was not yet a root url of that hash
                preranking = (Long) preorderEntry[1];
                // find the url entry
                page = wordIndex.loadedURL.load(entry.urlHash(), entry);
                if (page != null) {
                    comp = page.comp();
                    pagetitle = comp.title().toLowerCase();
                    if (comp.url() == null) continue ordering; // rare case where the url is corrupted
                    pageurl = comp.url().toString().toLowerCase();
                    pageauthor = comp.author().toLowerCase();
                    
                    // check exclusion
                    if (plasmaSearchQuery.matches(pagetitle, query.excludeHashes)) continue ordering;
                    if (plasmaSearchQuery.matches(pageurl, query.excludeHashes)) continue ordering;
                    if (plasmaSearchQuery.matches(pageauthor, query.excludeHashes)) continue ordering;
                    
                    // check url mask
                    if (!(pageurl.matches(query.urlMask))) continue ordering;
                    
                    // check constraints
                    if ((!(query.constraint.equals(plasmaSearchQuery.catchall_constraint))) &&
                        (query.constraint.get(plasmaCondenser.flag_cat_indexof)) &&
                        (!(comp.title().startsWith("Index of")))) {
                        serverLog.logFine("PLASMA", "filtered out " + comp.url().toString());
                        // filter out bad results
                        Iterator wi = query.queryHashes.iterator();
                        while (wi.hasNext()) wordIndex.removeEntry((String) wi.next(), page.hash());
                    } else if (query.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                        if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (page.laudio() > 0)) acc.addPage(page, preranking);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (page.lvideo() > 0)) acc.addPage(page, preranking);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (page.limage() > 0)) acc.addPage(page, preranking);
                        else if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP) && (page.lapp() > 0)) acc.addPage(page, preranking);
                    } else {
                        acc.addPage(page, preranking);
                    }
                }
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        setYieldTime(plasmaSearchProcessing.PROCESS_URLFETCH);
        setYieldCount(plasmaSearchProcessing.PROCESS_URLFETCH, acc.sizeFetched());

        acc.filteredResults = preorder.filteredCount();
        
        return acc;
    }

    //acc.localContributions = (resultIndex == null) ? 0 : resultIndex.size();
    
    // postsort
    public void postSort(
            boolean postsort,
            plasmaSearchPostOrder acc) {

        // start postsorting
        startTimer();
        acc.sortPages(postsort);
        setYieldTime(plasmaSearchProcessing.PROCESS_POSTSORT);
        setYieldCount(plasmaSearchProcessing.PROCESS_POSTSORT, acc.sizeOrdered());
    }
    
    // filter
    public void applyFilter(
            plasmaSearchPostOrder acc) {

        // apply filter
        startTimer();
        acc.removeRedundant();
        setYieldTime(plasmaSearchProcessing.PROCESS_FILTER);
        setYieldCount(plasmaSearchProcessing.PROCESS_FILTER, acc.sizeOrdered());
    }
}
