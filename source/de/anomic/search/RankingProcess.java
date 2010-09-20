// RankingProcess.java
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
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.storage.WeakPriorityBlockingQueue;
import net.yacy.cora.storage.WeakPriorityBlockingQueue.ReverseElement;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.meta.URIMetadataRow.Components;
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

import de.anomic.yacy.graphics.ProfilingGraph;

public final class RankingProcess extends Thread {
    
    public  static BinSearch[] ybrTables = null; // block-rank tables
    private static final int maxYBR = 3; // the lower this value, the faster the search
    private static boolean useYBR = true;
    private static final int maxDoubleDomAll = 100, maxDoubleDomSpecial = 10000;
    
    private final QueryParams query;
    private final TreeSet<byte[]> urlhashes; // map for double-check; String/Long relation, addresses ranking number (backreference for deletion)
    private final int[] flagcount; // flag counter
    private final TreeSet<byte[]> misses; // contains url-hashes that could not been found in the LURL-DB
    //private final int[] domZones;
    private TreeMap<byte[], ReferenceContainer<WordReference>> localSearchInclusion;
    
    private int remote_resourceSize, remote_indexCount, remote_peerCount;
    private int local_resourceSize, local_indexCount;
    private final WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>> stack;
    private int feeders;
    private final ConcurrentHashMap<String, WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>>> doubleDomCache; // key = domhash (6 bytes); value = like stack
    //private final HandleSet handover; // key = urlhash; used for double-check of urls that had been handed over to search process
    
    private final Navigator ref;  // reference score computation for the commonSense heuristic
    private final Navigator hostNavigator;
    private final Navigator authorNavigator;
    private final Navigator namespaceNavigator;
    private final ReferenceOrder order;
    
    public RankingProcess(final QueryParams query, final ReferenceOrder order, final int maxentries, final int concurrency) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        // sortorder: 0 = hash, 1 = url, 2 = ranking
        this.localSearchInclusion = null;
        this.stack = new WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>>(maxentries);
        this.doubleDomCache = new ConcurrentHashMap<String, WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>>>();
        this.query = query;
        this.order = order;
        this.remote_peerCount = 0;
        this.remote_resourceSize = 0;
        this.remote_indexCount = 0;
        this.local_resourceSize = 0;
        this.local_indexCount = 0;
        this.urlhashes = new TreeSet<byte[]>(URIMetadataRow.rowdef.objectOrder);
        //this.urlhashes = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
        this.misses = new TreeSet<byte[]>(URIMetadataRow.rowdef.objectOrder);
        //this.misses = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
        this.flagcount = new int[32];
        for (int i = 0; i < 32; i++) {this.flagcount[i] = 0;}
        this.hostNavigator = new Navigator();
        this.authorNavigator = new Navigator();
        this.namespaceNavigator = new Navigator();
        this.ref = new Navigator();
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
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.JOIN, query.queryString, index.size(), System.currentTimeMillis() - timer), false);
            if (index.isEmpty()) {
                return;
            }
            
            add(index, true, "local index: " + this.query.getSegment().getLocation(), -1);
        } catch (final Exception e) {
            Log.logException(e);
        }
        oneFeederTerminated();
    }
    
    public void add(final ReferenceContainer<WordReference> index, final boolean local, String resourceName, final int fullResource) {
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
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.NORMALIZING, resourceName, index.size(), System.currentTimeMillis() - timer), false);
        
        // iterate over normalized entries and select some that are better than currently stored
        timer = System.currentTimeMillis();
        String domhash;
        boolean nav_hosts = this.query.navigators.equals("all") || this.query.navigators.indexOf("hosts") >= 0;

        // apply all constraints
        try {
            WordReferenceVars iEntry;
            while (true) {
			    iEntry = decodedEntries.poll(1, TimeUnit.SECONDS);
			    if (iEntry == null || iEntry == WordReferenceVars.poison) break;
			    assert (iEntry.metadataHash().length == index.row().primaryKeyLength);
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
			    
                // count domZones
                //this.domZones[DigestURI.domDomain(iEntry.metadataHash())]++;
                
			    // check site constraints
			    domhash = new String(iEntry.metadataHash(), 6, 6);
			    if (query.sitehash == null) {
			        // no site constraint there; maybe collect host navigation information
			        if (nav_hosts && query.urlMask_isCatchall) {
	                    this.hostNavigator.inc(domhash, new String(iEntry.metadataHash()));
	                }
			    } else {
			        if (!domhash.equals(query.sitehash)) {
			            // filter out all domains that do not match with the site constraint
			            continue;
			        }
			    }
			    
			    // finally make a double-check and insert result to stack
                if (urlhashes.add(iEntry.metadataHash())) {
                    stack.put(new ReverseElement<WordReferenceVars>(iEntry, this.order.cardinal(iEntry))); // inserts the element and removes the worst (which is smallest)

                    // increase counter for statistics
                    if (local) this.local_indexCount++; else this.remote_indexCount++;
                }
			}

        } catch (InterruptedException e) {}
        
        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
		EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(query.id(true), SearchEvent.Type.PRESORT, resourceName, index.size(), System.currentTimeMillis() - timer), false);
    }
    
    /**
     * method to signal the incoming stack that one feeder has terminated
     */
    public void oneFeederTerminated() {
    	this.feeders--;
    	assert this.feeders >= 0 : "feeders = " + this.feeders;
    }
    
    protected void moreFeeders(final int countMoreFeeders) {
    	this.feeders += countMoreFeeders;
    }
    
    private boolean feedingIsFinished() {
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
    
    protected Map<byte[], ReferenceContainer<WordReference>> searchContainerMap() {
        // direct access to the result maps is needed for abstract generation
        // this is only available if execQuery() was called before
        return localSearchInclusion;
    }
    
    private ReverseElement<WordReferenceVars> takeRWI(final boolean skipDoubleDom, long timeout) {
        
        // returns from the current RWI list the best entry and removes this entry from the list
        WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>> m;
        ReverseElement<WordReferenceVars> rwi;
        try {
            //System.out.println("feeders = " + this.feeders);
            while ((rwi = stack.poll((this.feedingIsFinished()) ? 0 : timeout)) != null) {
                if (!skipDoubleDom) return rwi;
                
                // check doubledom
                final String domhash = new String(rwi.getElement().metadataHash(), 6, 6);
                m = this.doubleDomCache.get(domhash);
                if (m == null) {
                    // first appearance of dom
                    m = new WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>>((query.specialRights) ? maxDoubleDomSpecial : maxDoubleDomAll);
                    this.doubleDomCache.put(domhash, m);
                    return rwi;
                }
                
                // second appearances of dom
                m.put(rwi);
            }
        } catch (InterruptedException e1) {
        }
        
        // no more entries in sorted RWI entries. Now take Elements from the doubleDomCache
        // find best entry from all caches
        ReverseElement<WordReferenceVars> bestEntry = null;
        ReverseElement<WordReferenceVars> o;
        synchronized (this.doubleDomCache) {
            final Iterator<WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>>> i = this.doubleDomCache.values().iterator();
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
                    bestEntry = m.peek();
                    continue;
                }
                o = m.peek();
                if (o == null) continue;
                if (o.getWeight() < bestEntry.getWeight()) {
                    bestEntry = o;
                }
            }
        }
        if (bestEntry == null) return null;
        
        // finally remove the best entry from the doubledom cache
        m = this.doubleDomCache.get(new String(bestEntry.getElement().metadataHash()).substring(6));
        o = m.poll();
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
    	int p = -1;
    	byte[] urlhash;
    	long timeleft;
    	while ((timeleft = timeLimit - System.currentTimeMillis()) > 0) {
            final ReverseElement<WordReferenceVars> obrwi = takeRWI(skipDoubleDom, timeleft);
            if (obrwi == null) {
            	if (this.feedingIsFinished()) return null;
            	try {Thread.sleep(50);} catch (final InterruptedException e1) {}
            	continue;
            }
            urlhash = obrwi.getElement().metadataHash();
            final URIMetadataRow page = this.query.getSegment().urlMetadata().load(urlhash, obrwi.getElement(), obrwi.getWeight());
            if (page == null) {
            	misses.add(obrwi.getElement().metadataHash());
            	continue;
            }
            
            // prepare values for constraint check
            final URIMetadataRow.Components metadata = page.metadata();
            
            // check errors
            if (metadata == null) {
                continue; // rare case where the url is corrupted
            }
            
            if (!query.urlMask_isCatchall) {
                // check url mask
                if (!metadata.matches(query.urlMask)) {
                    continue;
                }
                
                // in case that we do not have e catchall filter for urls
                // we must also construct the domain navigator here
                if (query.sitehash == null) {
                    this.hostNavigator.inc(new String(urlhash, 6, 6), new String(urlhash));
                }
            }
            
            // check for more errors
            if (metadata.url() == null) {
                continue; // rare case where the url is corrupted
            }

            final String pageurl = metadata.url().toNormalform(true, true);
            final String pageauthor = metadata.dc_creator();
            final String pagetitle = metadata.dc_title().toLowerCase();

            // check exclusion
            if ((QueryParams.anymatch(pagetitle, query.excludeHashes)) ||
                (QueryParams.anymatch(pageurl.toLowerCase(), query.excludeHashes)) ||
                (QueryParams.anymatch(pageauthor.toLowerCase(), query.excludeHashes))) {
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
                this.authorNavigator.inc(authorhash, pageauthor);
            } else if (this.query.authorhash != null) {
            	continue;
            }
            
            // namespace navigation
            String pagepath = metadata.url().getPath();
            if ((p = pagepath.indexOf(':')) >= 0) {
                pagepath = pagepath.substring(0,p);
                p = pagepath.lastIndexOf('/');
                if (p >= 0) {
                    pagepath = pagepath.substring(p + 1);
                    this.namespaceNavigator.inc(pagepath, pagepath);
                }
            }
            
            // accept url
            /*
            try {
                this.handover.put(page.hash()); // remember that we handed over this url
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
            */
            return page;
        }
        return null;
    }
    
    protected int size() {
        int c = stack.sizeAvailable();
        for (WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>> s: this.doubleDomCache.values()) {
            c += s.sizeAvailable();
        }
        return c;
    }
    
    public boolean isEmpty() {
        if (!stack.isEmpty()) return false;
        for (WeakPriorityBlockingQueue<ReverseElement<WordReferenceVars>> s: this.doubleDomCache.values()) {
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
        return this.stack.sizeAvailable();
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
    
    public Iterator<byte[]> miss() {
        return this.misses.iterator();
    }
    
    public ArrayList<Navigator.Item> getNamespaceNavigator(int count) {
        if (!this.query.navigators.equals("all") && this.query.navigators.indexOf("namespace") < 0) return new ArrayList<Navigator.Item>(0);
        
        Navigator.Item[] hsa = this.namespaceNavigator.entries();
        int rc = Math.min(count, hsa.length);
        ArrayList<Navigator.Item> result = new ArrayList<Navigator.Item>();
        for (int i = 0; i < rc; i++) result.add(hsa[i]);
        if (result.size() < 2) result.clear(); // navigators with one entry are not useful
        return result;
    }
    
    public List<Navigator.Item> getHostNavigator(int count) {
        List<Navigator.Item> result = new ArrayList<Navigator.Item>();
        if (!this.query.navigators.equals("all") && this.query.navigators.indexOf("hosts") < 0) return result;
        
        List<Navigator.Item> hsa = this.hostNavigator.entries(10);
        URIMetadataRow mr;
        DigestURI url;
        String hostname;
        Components metadata;
        loop: for (Navigator.Item item: hsa) {
            mr = this.query.getSegment().urlMetadata().load(item.name.getBytes(), null, 0);
            if (mr == null) continue;
            metadata = mr.metadata();
            if (metadata == null) continue;
            url = metadata.url();
            if (url == null) continue;
            hostname = url.getHost();
            if (hostname == null) continue;
            if (query.tenant != null && !hostname.contains(query.tenant) && !url.toNormalform(true, true).contains(query.tenant)) continue;
            for (Navigator.Item entry: result) if (entry.name.equals(hostname)) continue loop; // check if one entry already exists
            result.add(new Navigator.Item(hostname, item.count));
        }
        if (result.size() < 2) result.clear(); // navigators with one entry are not useful
        return result;
    }

    public static final Comparator<Map.Entry<String, Integer>> mecomp = new Comparator<Map.Entry<String, Integer>>() {
        public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
            if (o1.getValue().intValue() < o2.getValue().intValue()) return 1;
            if (o2.getValue().intValue() < o1.getValue().intValue()) return -1;
            return 0;
        }
    };
    
    public Map<String, Navigator.Item> getTopics() {
        return this.ref.map();
    }
    
    public List<Navigator.Item> getTopicNavigator(final int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        if (!this.query.navigators.equals("all") && this.query.navigators.indexOf("topics") < 0) return new ArrayList<Navigator.Item>(0);
        List<Navigator.Item> result = this.ref.entries(10);
        if (result.size() < 2) result.clear(); // navigators with one entry are not useful
        return result;
    }
    
    public void addTopic(final String[] words) {
        String word;
        for (int i = 0; i < words.length; i++) {
            word = words[i].toLowerCase();
            if (word.length() > 2 &&
                "http_html_php_ftp_www_com_org_net_gov_edu_index_home_page_for_usage_the_and_".indexOf(word) < 0 &&
                !query.queryHashes.has(Word.word2hash(word)) &&
                word.matches("[a-z]+") &&
                !Switchboard.badwords.contains(word) &&
                !Switchboard.stopwords.contains(word)) {
                ref.inc(word, word);
            }
        }
    }
    
    protected void addTopics(final ResultEntry resultEntry) {
        // take out relevant information for reference computation
        if ((resultEntry.url() == null) || (resultEntry.title() == null)) return;
        //final String[] urlcomps = htmlFilterContentScraper.urlComps(resultEntry.url().toNormalform(true, true)); // word components of the url
        final String[] descrcomps = MultiProtocolURI.splitpattern.split(resultEntry.title().toLowerCase()); // words in the description
        
        // add references
        //addTopic(urlcomps);
        addTopic(descrcomps);
    }
    
    public List<Navigator.Item> getAuthorNavigator(final int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        if (!this.query.navigators.equals("all") && this.query.navigators.indexOf("authors") < 0) return new ArrayList<Navigator.Item>(0);
        List<Navigator.Item> result = this.authorNavigator.entries(count);
        if (result.size() < 2) result.clear(); // navigators with one entry are not useful
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
    
    public static int ybr(final byte[] urlHash) {
        // returns the YBR value in a range of 0..15, where 0 means best ranking and 15 means worst ranking
        if (ybrTables == null) return 15;
        if (!(useYBR)) return 15;
        byte[] domhash = new byte[6];
        System.arraycopy(urlHash, 6, domhash, 0, 6);
        final int m = Math.min(maxYBR, ybrTables.length);
        for (int i = 0; i < m; i++) {
            if ((ybrTables[i] != null) && (ybrTables[i].contains(domhash))) {
                //System.out.println("YBR FOUND: " + urlHash + " (" + i + ")");
                return i;
            }
        }
        //System.out.println("NOT FOUND: " + urlHash);
        return 15;
    }

}
