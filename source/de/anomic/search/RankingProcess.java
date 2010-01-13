// plasmaSearchRankingProcess.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.BinSearch;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.TermSearch;
import net.yacy.kelondro.util.EventTracker;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.SortStack;

import de.anomic.yacy.graphics.ProfilingGraph;

public final class RankingProcess extends Thread {
    
    public  static BinSearch[] ybrTables = null; // block-rank tables
    public  static final int maxYBR = 3; // the lower this value, the faster the search
    private static boolean useYBR = true;
    private static final int maxDoubleDomAll = 20, maxDoubleDomSpecial = 10000;
    
    private final QueryParams query;
    private final int maxentries;
    private final ConcurrentHashMap<String, Long> urlhashes; // map for double-check; String/Long relation, addresses ranking number (backreference for deletion)
    private final int[] flagcount; // flag counter
    private final TreeSet<String> misses; // contains url-hashes that could not been found in the LURL-DB
    //private final int[] domZones;
    private HashMap<byte[], ReferenceContainer<WordReference>> localSearchInclusion;
    
    private int remote_resourceSize, remote_indexCount, remote_peerCount;
    private int local_resourceSize, local_indexCount;
    private final SortStack<WordReferenceVars> stack;
    private int feeders;
    private final ConcurrentHashMap<String, SortStack<WordReferenceVars>> doubleDomCache; // key = domhash (6 bytes); value = like stack
    private final HashSet<String> handover; // key = urlhash; used for double-check of urls that had been handed over to search process
    
    private final ConcurrentHashMap<String, Integer> ref;  // reference score computation for the commonSense heuristic
    private final ConcurrentHashMap<String, HostInfo> hostNavigator;
    private final ConcurrentHashMap<String, AuthorInfo> authorNavigator;
    private final ReferenceOrder order;
    
    public RankingProcess(final QueryParams query, final ReferenceOrder order, final int maxentries, final int concurrency) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        // sortorder: 0 = hash, 1 = url, 2 = ranking
        this.localSearchInclusion = null;
        this.stack = new SortStack<WordReferenceVars>(maxentries, true);
        this.doubleDomCache = new ConcurrentHashMap<String, SortStack<WordReferenceVars>>();
        this.handover = new HashSet<String>();
        this.query = query;
        this.order = order;
        this.maxentries = maxentries;
        this.remote_peerCount = 0;
        this.remote_resourceSize = 0;
        this.remote_indexCount = 0;
        this.local_resourceSize = 0;
        this.local_indexCount = 0;
        this.urlhashes = new ConcurrentHashMap<String, Long>(0, 0.75f, concurrency);
        this.misses = new TreeSet<String>();
        this.flagcount = new int[32];
        for (int i = 0; i < 32; i++) {this.flagcount[i] = 0;}
        this.hostNavigator = new ConcurrentHashMap<String, HostInfo>();
        this.authorNavigator = new ConcurrentHashMap<String, AuthorInfo>();
        this.ref = new ConcurrentHashMap<String, Integer>();
        //this.domZones = new int[8];
        //for (int i = 0; i < 8; i++) {this.domZones[i] = 0;}
        this.feeders = concurrency;
        assert this.feeders >= 1;
    }
    
    public QueryParams getQuery() {
        return this.query;
    }
    
    public ReferenceOrder getOrder() {
        return this.order;
    }
    
    public void run() {
        // do a search
        
        // sort the local containers and truncate it to a limited count,
        // so following sortings together with the global results will be fast
        try {
            long timer = System.currentTimeMillis();
            final TermSearch<WordReference> search = this.query.getSegment().termIndex().query(
                    query.queryHashes,
                    query.excludeHashes,
                    null,
                    Segment.wordReferenceFactory,
                    query.maxDistance);
            this.localSearchInclusion = search.inclusion();
            final ReferenceContainer<WordReference> index = search.joined();
            EventTracker.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), SearchEvent.JOIN, index.size(), System.currentTimeMillis() - timer), false, 30000, ProfilingGraph.maxTime);
            if (index.isEmpty()) {
                return;
            }
            
            add(index, true, -1);
        } catch (final Exception e) {
            Log.logException(e);
        }
        oneFeederTerminated();
    }
    
    public void add(final ReferenceContainer<WordReference> index, final boolean local, final int fullResource) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        assert (index != null);
        if (index.isEmpty()) return;
        
        if (local) {
            this.local_resourceSize += index.size();
        } else {
            this.remote_resourceSize += fullResource;
            this.remote_peerCount++;
        }
        
        long timer = System.currentTimeMillis();
        
        // normalize entries
        final BlockingQueue<WordReferenceVars> decodedEntries = this.order.normalizeWith(index);
        EventTracker.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), SearchEvent.NORMALIZING, index.size(), System.currentTimeMillis() - timer), false, 30000, ProfilingGraph.maxTime);
        
        // iterate over normalized entries and select some that are better than currently stored
        timer = System.currentTimeMillis();
        HostInfo hs;
        String domhash;
        boolean nav_hosts = this.query.navigators.equals("all") || this.query.navigators.indexOf("hosts") >= 0;
        Long r;
        final ArrayList<WordReferenceVars> filteredEntries = new ArrayList<WordReferenceVars>();

        // apply all constraints
        try {
            WordReferenceVars iEntry;
            while (true) {
			    iEntry = decodedEntries.poll(1, TimeUnit.SECONDS);
			    if (iEntry == null || iEntry == WordReferenceVars.poison) break;
			    assert (iEntry.metadataHash().length() == index.row().primaryKeyLength);
			    //if (iEntry.urlHash().length() != index.row().primaryKeyLength) continue;

			    // increase flag counts
			    for (int j = 0; j < 32; j++) {
			        if (iEntry.flags().get(j)) {flagcount[j]++;}
			    }
			    
			    // check constraints
			    if (!testFlags(iEntry)) continue;
			    
			    // check document domain
			    if (query.contentdom != ContentDomain.TEXT) {
			        if ((query.contentdom == ContentDomain.AUDIO) && (!(iEntry.flags().get(Condenser.flag_cat_hasaudio)))) continue;
			        if ((query.contentdom == ContentDomain.VIDEO) && (!(iEntry.flags().get(Condenser.flag_cat_hasvideo)))) continue;
			        if ((query.contentdom == ContentDomain.IMAGE) && (!(iEntry.flags().get(Condenser.flag_cat_hasimage)))) continue;
			        if ((query.contentdom == ContentDomain.APP  ) && (!(iEntry.flags().get(Condenser.flag_cat_hasapp  )))) continue;
			    }

			    // check tld domain
			    if (!DigestURI.matchesAnyDomDomain(iEntry.metadataHash(), this.query.zonecode)) {
			        // filter out all tld that do not match with wanted tld domain
			        continue;
			    }
			    
			    // check site constraints
			    if (query.sitehash != null && !iEntry.metadataHash().substring(6).equals(query.sitehash)) {
			        // filter out all domains that do not match with the site constraint
			    	continue;
			    }
			    
			    // count domZones
			    //this.domZones[DigestURI.domDomain(iEntry.metadataHash())]++;
			    
			    // get statistics for host navigator
			    if (nav_hosts) {
			        domhash = iEntry.urlHash.substring(6);
			        hs = this.hostNavigator.get(domhash);
			        if (hs == null) {
			        	this.hostNavigator.put(domhash, new HostInfo(iEntry.urlHash));
			        } else {
			        	hs.inc();
			        }
			    }
			    
			    // accept
			    filteredEntries.add(iEntry);
			    
			    // increase counter for statistics
			    if (local) this.local_indexCount++; else this.remote_indexCount++;
			}
            
    		// do the ranking
    		for (WordReferenceVars fEntry: filteredEntries) {
    			
    		    // kick out entries that are too bad according to current findings
    		    r = Long.valueOf(this.order.cardinal(fEntry));
    		    assert maxentries != 0;
    
                // double-check
    		    if (urlhashes.containsKey(fEntry.metadataHash())) continue;
                
    		    // insert
    		    if (maxentries < 0 || stack.size() < maxentries) {
    		        // in case that we don't have enough yet, accept any new entry
    		        stack.push(fEntry, r);
    		    } else {
    		        // if we already have enough entries, insert only such that are necessary to get a better result
    		        if (stack.bottom(r.longValue())) continue;
    		        
    		        // take the entry. the stack is automatically reduced
    		        // to the maximum size by deletion of elements at the bottom
    		        stack.push(fEntry, r);
    		    }
    		    urlhashes.put(fEntry.metadataHash(), r);
    		}

        } catch (InterruptedException e) {}
        
        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
		EventTracker.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), SearchEvent.PRESORT, index.size(), System.currentTimeMillis() - timer), false, 30000, ProfilingGraph.maxTime);
    }
    
    /**
     * method to signal the incoming stack that one feeder has terminated
     */
    public void oneFeederTerminated() {
    	this.feeders--;
    	assert this.feeders >= 0 : "feeders = " + this.feeders;
    }
    
    public void moreFeeders(final int countMoreFeeders) {
    	this.feeders += countMoreFeeders;
    }
    
    public boolean feedingIsFinished() {
    	return this.feeders == 0;
    }
    
    private boolean testFlags(final WordReference ientry) {
        if (query.constraint == null) return true;
        // test if ientry matches with filter
        // if all = true: let only entries pass that has all matching bits
        // if all = false: let all entries pass that has at least one matching bit
        if (query.allofconstraint) {
            for (int i = 0; i < 32; i++) {
                if ((query.constraint.get(i)) && (!ientry.flags().get(i))) return false;
            }
            return true;
        }
        for (int i = 0; i < 32; i++) {
            if ((query.constraint.get(i)) && (ientry.flags().get(i))) return true;
        }
        return false;
    }
    
    public Map<byte[], ReferenceContainer<WordReference>> searchContainerMap() {
        // direct access to the result maps is needed for abstract generation
        // this is only available if execQuery() was called before
        return localSearchInclusion;
    }
    
    // todo:
    // - remove redundant urls (sub-path occurred before)
    // - move up shorter urls
    // - root-domain guessing to prefer the root domain over other urls if search word appears in domain name
    
    
    private SortStack<WordReferenceVars>.stackElement takeRWI(final boolean skipDoubleDom) {
        // returns from the current RWI list the best entry and removes this entry from the list
        SortStack<WordReferenceVars> m;
        SortStack<WordReferenceVars>.stackElement rwi;
        while (!stack.isEmpty()) {
            rwi = stack.pop();
            if (rwi == null) continue; // in case that a synchronization problem occurred just go lazy over it
            if (!skipDoubleDom) return rwi;
            // check doubledom
            final String domhash = rwi.element.metadataHash().substring(6);
            m = this.doubleDomCache.get(domhash);
            if (m == null) {
                // first appearance of dom
                m = new SortStack<WordReferenceVars>((query.specialRights) ? maxDoubleDomSpecial : maxDoubleDomAll, true);
                this.doubleDomCache.put(domhash, m);
                return rwi;
            }
            // second appearances of dom
            m.push(rwi.element, rwi.weight);
        }
        // no more entries in sorted RWI entries. Now take Elements from the doubleDomCache
        // find best entry from all caches
        SortStack<WordReferenceVars>.stackElement bestEntry = null;
        SortStack<WordReferenceVars>.stackElement o;
        synchronized (this.doubleDomCache) {
            final Iterator<SortStack<WordReferenceVars>> i = this.doubleDomCache.values().iterator();
            while (i.hasNext()) {
                try {
                    m = i.next();
                } catch (ConcurrentModificationException e) {
                    Log.logException(e);
                    break; // not the best solution...
                }
                if (m == null) continue;
                if (m.isEmpty()) continue;
                if (bestEntry == null) {
                    bestEntry = m.top();
                    continue;
                }
                o = m.top();
                if (o.weight.longValue() < bestEntry.weight.longValue()) {
                    bestEntry = o;
                }
            }
        }
        if (bestEntry == null) return null;
        // finally remove the best entry from the doubledom cache
        m = this.doubleDomCache.get(bestEntry.element.metadataHash().substring(6));
        o = m.pop();
        //assert o == null || o.element.metadataHash().equals(bestEntry.element.metadataHash()) : "bestEntry.element.metadataHash() = " + bestEntry.element.metadataHash() + ", o.element.metadataHash() = " + o.element.metadataHash();
        return bestEntry;
    }
    
    /**
     * get one metadata entry from the ranked results. This will be the 'best' entry so far
     * according to the applied ranking. If there are no more entries left or the timeout
     * limit is reached then null is returned. The caller may distinguish the timeout case
     * from the case where there will be no more also in the future by calling this.feedingIsFinished()
     * @param skipDoubleDom should be true if it is wanted that double domain entries are skipped
     * @param timeout the time this method may take for a result computation
     * @return a metadata entry for a url
     */
    public URIMetadataRow takeURL(final boolean skipDoubleDom, final int timeout) {
        // returns from the current RWI list the best URL entry and removes this entry from the list
    	long timeLimit = System.currentTimeMillis() + timeout;
    	while (System.currentTimeMillis() < timeLimit) {
            final SortStack<WordReferenceVars>.stackElement obrwi = takeRWI(skipDoubleDom);
            if (obrwi == null) {
            	if (this.feedingIsFinished()) return null;
            	try {Thread.sleep(50);} catch (final InterruptedException e1) {}
            	continue;
            }
            final URIMetadataRow page = this.query.getSegment().urlMetadata().load(obrwi.element.metadataHash(), obrwi.element, obrwi.weight.longValue());
            if (page == null) {
            	misses.add(obrwi.element.metadataHash());
            	continue;
            }
            
            // prepare values for constraint check
            final URIMetadataRow.Components metadata = page.metadata();
            
            // check url constraints
            if (metadata.url() == null) {
                continue; // rare case where the url is corrupted
            }
            
            final String pageurl = metadata.url().toNormalform(true, true);
            final String pageauthor = metadata.dc_creator();
            final String pagetitle = metadata.dc_title().toLowerCase();
            
            // check exclusion
            if ((QueryParams.matches(pagetitle, query.excludeHashes)) ||
                (QueryParams.matches(pageurl.toLowerCase(), query.excludeHashes)) ||
                (QueryParams.matches(pageauthor.toLowerCase(), query.excludeHashes))) {
                continue;
            }
            
            // check url mask
            if (!(pageurl.matches(query.urlMask))) {
                continue;
            }
            
            // check index-of constraint
            if ((query.constraint != null) &&
                (query.constraint.get(Condenser.flag_cat_indexof)) &&
                (!(pagetitle.startsWith("index of")))) {
                final Iterator<byte[]> wi = query.queryHashes.iterator();
                while (wi.hasNext()) try { this.query.getSegment().termIndex().remove(wi.next(), page.hash()); } catch (IOException e) {}
                continue;
            }
            
            // check content domain
            if ((query.contentdom == ContentDomain.AUDIO && page.laudio() == 0) ||
                (query.contentdom == ContentDomain.VIDEO && page.lvideo() == 0) ||
                (query.contentdom == ContentDomain.IMAGE && page.limage() == 0) ||
                (query.contentdom == ContentDomain.APP && page.lapp() == 0)) {
            	continue;
            }
            
            // evaluate information of metadata for navigation
            // author navigation:
            if (pageauthor != null && pageauthor.length() > 0) {
            	// add author to the author navigator
                String authorhash = new String(Word.word2hash(pageauthor));
                //System.out.println("*** DEBUG authorhash = " + authorhash + ", query.authorhash = " + this.query.authorhash + ", author = " + author);
                
                // check if we already are filtering for authors
            	if (this.query.authorhash != null && !this.query.authorhash.equals(authorhash)) {
            		continue;
            	}
            	
            	// add author to the author navigator
                AuthorInfo in = this.authorNavigator.get(authorhash);
                if (in == null) {
                    this.authorNavigator.put(authorhash, new AuthorInfo(pageauthor));
                } else {
                    in.inc();
                    this.authorNavigator.put(authorhash, in);
                }
            } else if (this.query.authorhash != null) {
            	continue;
            }
            
            // accept url
            //System.out.println("handing over hash " + page.hash());
            this.handover.add(page.hash()); // remember that we handed over this url
            return page;
        }
        return null;
    }
    
    public int size() {
        //assert sortedRWIEntries.size() == urlhashes.size() : "sortedRWIEntries.size() = " + sortedRWIEntries.size() + ", urlhashes.size() = " + urlhashes.size();
        int c = stack.size();
        for (SortStack<WordReferenceVars> s: this.doubleDomCache.values()) {
            c += s.size();
        }
        return c;
    }
    
    public boolean isEmpty() {
        if (!stack.isEmpty()) return false;
        for (SortStack<WordReferenceVars> s: this.doubleDomCache.values()) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }
    
    public int[] flagCount() {
    	return flagcount;
    }
    
    // "results from a total number of <remote_resourceSize + local_resourceSize> known (<local_resourceSize> local, <remote_resourceSize> remote), <remote_indexCount> links from <remote_peerCount> other YaCy peers."
    
    public int filteredCount() {
        // the number of index entries that are considered as result set
        return this.stack.size();
    }

    public int getLocalIndexCount() {
        // the number of results in the local peer after filtering
        return this.local_indexCount;
    }
    
    public int getLocalResourceSize() {
        // the number of hits in the local peer (index size, size of the collection in the own index)
        return this.local_resourceSize;
    }
    
    public int getRemoteIndexCount() {
        // the number of result contributions from all the remote peers
        return this.remote_indexCount;
    }
    
    public int getRemoteResourceSize() {
        // the number of all hits in all the remote peers
        return this.remote_resourceSize;
    }
    
    public int getRemotePeerCount() {
        // the number of remote peers that have contributed
        return this.remote_peerCount;
    }
    
    public void remove(final WordReferenceVars reference) {
        stack.remove(reference);
        urlhashes.remove(reference.urlHash);
    }
    
    public Iterator<String> miss() {
        return this.misses.iterator();
    }
    
    public class HostInfo {
        public int count;
        public String hashsample;
        public HostInfo(String urlhash) {
            this.count = 1;
            this.hashsample = urlhash;
        }
        public void inc() {
            this.count++;
        }
    }
    
    public class AuthorInfo {
        public int count;
        public String author;
        public AuthorInfo(String author) {
            this.count = 1;
            this.author = author;
        }
        public void inc() {
            this.count++;
        }
    }
    
    public static final Comparator<HostInfo> hscomp = new Comparator<HostInfo>() {
        public int compare(HostInfo o1, HostInfo o2) {
            if (o1.count < o2.count) return 1;
            if (o2.count < o1.count) return -1;
            return 0;
        }
    };
    
    public static final Comparator<AuthorInfo> aicomp = new Comparator<AuthorInfo>() {
        public int compare(AuthorInfo o1, AuthorInfo o2) {
            if (o1.count < o2.count) return 1;
            if (o2.count < o1.count) return -1;
            return 0;
        }
    };
    
    public class NavigatorEntry {
        public int count;
        public String name;
        public NavigatorEntry(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
    
    public ArrayList<NavigatorEntry> getHostNavigator(int count) {
    	if (!this.query.navigators.equals("all") && this.query.navigators.indexOf("hosts") < 0) return new ArrayList<NavigatorEntry>(0);
        
    	HostInfo[] hsa = this.hostNavigator.values().toArray(new HostInfo[this.hostNavigator.size()]);
        Arrays.sort(hsa, hscomp);
        int rc = Math.min(count, hsa.length);
        ArrayList<NavigatorEntry> result = new ArrayList<NavigatorEntry>();
        URIMetadataRow mr;
        DigestURI url;
        String hostname;
        loop: for (int i = 0; i < rc; i++) {
            mr = this.query.getSegment().urlMetadata().load(hsa[i].hashsample, null, 0);
            if (mr == null) continue;
            url = mr.metadata().url();
            if (url == null) continue;
            hostname = url.getHost();
            if (hostname == null) continue;
            if (query.tenant != null && !hostname.contains(query.tenant) && !url.toNormalform(true, true).contains(query.tenant)) continue;
            for (NavigatorEntry entry: result) if (entry.name.equals(hostname)) continue loop; // check if one entry already exists
            result.add(new NavigatorEntry(hostname, hsa[i].count));
        }
        return result;
    }

    public static final Comparator<Map.Entry<String, Integer>> mecomp = new Comparator<Map.Entry<String, Integer>>() {
        public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
            if (o1.getValue().intValue() < o2.getValue().intValue()) return 1;
            if (o2.getValue().intValue() < o1.getValue().intValue()) return -1;
            return 0;
        }
    };
    
    public Map<String, Integer> getTopics() {
        return this.ref;
    }
    
    @SuppressWarnings("unchecked")
    public ArrayList<NavigatorEntry> getTopicNavigator(final int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        if (!this.query.navigators.equals("all") && this.query.navigators.indexOf("topics") < 0) return new ArrayList<NavigatorEntry>(0);
        
        Map.Entry<String, Integer>[] a = this.ref.entrySet().toArray(new Map.Entry[this.ref.size()]);
        Arrays.sort(a, mecomp);
        int rc = Math.min(count, a.length);
        ArrayList<NavigatorEntry> result = new ArrayList<NavigatorEntry>();
        Map.Entry<String, Integer> e;
        int c;
        for (int i = 0; i < rc; i++) {
            e = a[i];
            c = e.getValue().intValue();
            if (c == 0) break;
            result.add(new NavigatorEntry(e.getKey(), c));
        }
        return result;
    }
    
    public void addTopic(final String[] words) {
        String word;
        for (int i = 0; i < words.length; i++) {
            word = words[i].toLowerCase();
            Integer c;
            if (word.length() > 2 &&
                "http_html_php_ftp_www_com_org_net_gov_edu_index_home_page_for_usage_the_and_".indexOf(word) < 0 &&
                !query.queryHashes.contains(Word.word2hash(word)) &&
                word.matches("[a-z]+") &&
                !Switchboard.badwords.contains(word) &&
                !Switchboard.stopwords.contains(word)) {
                c = ref.get(word);
                if (c == null) ref.put(word, 1); else ref.put(word, c.intValue() + 1);
            }
        }
    }
    
    protected void addTopics(final ResultEntry resultEntry) {
        // take out relevant information for reference computation
        if ((resultEntry.url() == null) || (resultEntry.title() == null)) return;
        //final String[] urlcomps = htmlFilterContentScraper.urlComps(resultEntry.url().toNormalform(true, true)); // word components of the url
        final String[] descrcomps = DigestURI.splitpattern.split(resultEntry.title().toLowerCase()); // words in the description
        
        // add references
        //addTopic(urlcomps);
        addTopic(descrcomps);
    }
    
    public ArrayList<NavigatorEntry> getAuthorNavigator(final int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        if (!this.query.navigators.equals("all") && this.query.navigators.indexOf("authors") < 0) return new ArrayList<NavigatorEntry>(0);
        
        AuthorInfo[] a = this.authorNavigator.values().toArray(new AuthorInfo[this.authorNavigator.size()]);
        Arrays.sort(a, aicomp);
        int rc = Math.min(count, a.length);
        ArrayList<NavigatorEntry> result = new ArrayList<NavigatorEntry>();
        AuthorInfo e;
        for (int i = 0; i < rc; i++) {
            e = a[i];
            //System.out.println("*** DEBUG Author = " + e.author + ", count = " + e.count);
            result.add(new NavigatorEntry(e.author, e.count));
        }
        return result;
    }
    
    public static void loadYBR(final File rankingPath, final int count) {
        // load ranking tables
        if (rankingPath.exists()) {
            ybrTables = new BinSearch[count];
            String ybrName;
            File f;
            try {
                for (int i = 0; i < count; i++) {
                    ybrName = "YBR-4-" + Digest.encodeHex(i, 2) + ".idx";
                    f = new File(rankingPath, ybrName);
                    if (f.exists()) {
                        ybrTables[i] = new BinSearch(FileUtils.read(f), 6);
                    } else {
                        ybrTables[i] = null;
                    }
                }
            } catch (final IOException e) {
                ybrTables = null;
            }
        } else {
            ybrTables = null;
        }
    }
    
    public static boolean canUseYBR() {
        return ybrTables != null;
    }
    
    public static boolean isUsingYBR() {
        return useYBR;
    }
    
    public static void switchYBR(final boolean usage) {
        useYBR = usage;
    }
    
    public static int ybr(final String urlHash) {
        // returns the YBR value in a range of 0..15, where 0 means best ranking and 15 means worst ranking
        if (ybrTables == null) return 15;
        if (!(useYBR)) return 15;
        final String domHash = urlHash.substring(6);
        final int m = Math.min(maxYBR, ybrTables.length);
        for (int i = 0; i < m; i++) {
            if ((ybrTables[i] != null) && (ybrTables[i].contains(domHash.getBytes()))) {
                //System.out.println("YBR FOUND: " + urlHash + " (" + i + ")");
                return i;
            }
        }
        //System.out.println("NOT FOUND: " + urlHash);
        return 15;
    }

}
/*
Thread= Thread-937 id=4224 BLOCKED
Thread= Thread-919 id=4206 BLOCKED
Thread= Thread-936 id=4223 BLOCKED
at net.yacy.kelondro.util.SortStack.pop(SortStack.java:118)
at de.anomic.search.RankingProcess.takeRWI(RankingProcess.java:310)
at de.anomic.search.RankingProcess.takeURL(RankingProcess.java:371)
at de.anomic.search.ResultFetcher$Worker.run(ResultFetcher.java:161)
*/
