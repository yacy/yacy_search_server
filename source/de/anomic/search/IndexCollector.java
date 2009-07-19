// IndexCollector.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.06.2009 on http://yacy.net
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

package de.anomic.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import de.anomic.document.Condenser;
import de.anomic.kelondro.index.BinSearch;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.ReferenceOrder;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.TermSearch;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.text.referencePrototype.WordReferenceVars;
import de.anomic.kelondro.util.SortStack;
import de.anomic.search.QueryParams;
import de.anomic.server.serverProfiling;
import de.anomic.yacy.yacyURL;
import de.anomic.ymage.ProfilingGraph;

public final class IndexCollector extends Thread {
    
    public  static BinSearch[] ybrTables = null; // block-rank tables
    public  static final int maxYBR = 3; // the lower this value, the faster the search
    public  static final ReferenceContainer<WordReference> poison = ReferenceContainer.emptyContainer(Segment.wordReferenceFactory, null, 0);
    
    private final SortStack<WordReferenceVars> stack;
    private final QueryParams query;
    private final int maxentries;
    private int remote_peerCount, remote_indexCount, remote_resourceSize, local_resourceSize;
    private final ReferenceOrder order;
    private final ConcurrentHashMap<String, Integer> urlhashes; // map for double-check; String/Long relation, addresses ranking number (backreference for deletion)
    private final int[] flagcount; // flag counter
    private final Segment indexSegment;
    private final int[] domZones;
    private final ConcurrentHashMap<String, HostInfo> hostNavigator;
    private HashMap<byte[], ReferenceContainer<WordReference>> localSearchInclusion;
    private final BlockingQueue<ReferenceContainer<WordReference>> rwiQueue;
    
    public IndexCollector(
            final Segment indexSegment,
            final QueryParams query,
            final int maxentries,
            final int concurrency) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        // sortorder: 0 = hash, 1 = url, 2 = ranking
        this.stack = new SortStack<WordReferenceVars>(maxentries);
        this.order = (query == null) ? null : new ReferenceOrder(query.ranking, query.targetlang);
        this.query = query;
        this.maxentries = maxentries;
        this.remote_peerCount = 0;
        this.remote_indexCount = 0;
        this.remote_resourceSize = 0;
        this.local_resourceSize = 0;
        this.urlhashes = new ConcurrentHashMap<String, Integer>(0, 0.75f, concurrency);
        this.indexSegment = indexSegment;
        this.flagcount = new int[32];
        for (int i = 0; i < 32; i++) {this.flagcount[i] = 0;}
        this.hostNavigator = new ConcurrentHashMap<String, HostInfo>();
        this.domZones = new int[8];
        for (int i = 0; i < 8; i++) {this.domZones[i] = 0;}
        this.localSearchInclusion = null;
        this.rwiQueue = new LinkedBlockingQueue<ReferenceContainer<WordReference>>();
    }
    
    public long ranking(final WordReferenceVars word) {
        return order.cardinal(word);
    }
    
    public int[] zones() {
        return this.domZones;
    }
    
    public void insertRanked(final ReferenceContainer<WordReference> index, final boolean local, final int fullResource) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        assert (index != null);
        if (index.size() == 0) return;
        if (local) {
            this.local_resourceSize += fullResource;
        } else {
            this.remote_resourceSize += fullResource;
            this.remote_peerCount++;
            this.remote_indexCount += index.size();
        }
        try {
            this.rwiQueue.put(index);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
    }
    
    public void shutdown(boolean waitfor) {
        try {
            this.rwiQueue.put(poison);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (waitfor && this.isAlive()) try {this.join();} catch (InterruptedException e) {} 
    }
    
    public void run() {
        
        long timer = System.currentTimeMillis();
        final TermSearch<WordReference> search = this.indexSegment.termIndex().query(
                query.queryHashes,
                query.excludeHashes,
                null,
                Segment.wordReferenceFactory,
                query.maxDistance);
        this.localSearchInclusion = search.inclusion();
        ReferenceContainer<WordReference> index = search.joined();
        insertRanked(index, true, index.size());
        
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), QueryEvent.JOIN, index.size(), System.currentTimeMillis() - timer), false);

        try {
            while ((index = this.rwiQueue.take()) != poison) {
            
                // normalize entries
                final ArrayList<WordReferenceVars> decodedEntries = this.order.normalizeWith(index);
                serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), QueryEvent.NORMALIZING, index.size(), System.currentTimeMillis() - timer), false);
                
                // iterate over normalized entries and select some that are better than currently stored
                timer = System.currentTimeMillis();
                final Iterator<WordReferenceVars> i = decodedEntries.iterator();
                WordReferenceVars iEntry;
                Long r;
                HostInfo hs;
                String domhash;
                boolean nav_hosts = this.query.navigators.equals("all") || this.query.navigators.indexOf("hosts") >= 0;
                while (i.hasNext()) {
                    iEntry = i.next();
                    assert (iEntry.metadataHash().length() == index.row().primaryKeyLength);
                    //if (iEntry.urlHash().length() != index.row().primaryKeyLength) continue;
   
                    // increase flag counts
                    for (int j = 0; j < 32; j++) {
                        if (iEntry.flags().get(j)) {flagcount[j]++;}
                    }
                    
                    // kick out entries that are too bad according to current findings
                    r = Long.valueOf(order.cardinal(iEntry));
                    if ((maxentries >= 0) && (stack.size() >= maxentries) && (stack.bottom(r.longValue()))) continue;
                    
                    // check constraints
                    if (!testFlags(iEntry)) continue;
                    
                    // check document domain
                    if (query.contentdom != QueryParams.CONTENTDOM_TEXT) {
                        if ((query.contentdom == QueryParams.CONTENTDOM_AUDIO) && (!(iEntry.flags().get(Condenser.flag_cat_hasaudio)))) continue;
                        if ((query.contentdom == QueryParams.CONTENTDOM_VIDEO) && (!(iEntry.flags().get(Condenser.flag_cat_hasvideo)))) continue;
                        if ((query.contentdom == QueryParams.CONTENTDOM_IMAGE) && (!(iEntry.flags().get(Condenser.flag_cat_hasimage)))) continue;
                        if ((query.contentdom == QueryParams.CONTENTDOM_APP  ) && (!(iEntry.flags().get(Condenser.flag_cat_hasapp  )))) continue;
                    }
   
                    // check tld domain
                    if (!yacyURL.matchesAnyDomDomain(iEntry.metadataHash(), this.query.zonecode)) {
                        // filter out all tld that do not match with wanted tld domain
                        continue;
                    }
                    
                    // check site constraints
                    if (query.sitehash != null && !iEntry.metadataHash().substring(6).equals(query.sitehash)) {
                        // filter out all domains that do not match with the site constraint
                        continue;
                    }
                    
                    // count domZones
                    this.domZones[yacyURL.domDomain(iEntry.metadataHash())]++;
                    
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
                    
                    // insert
                    if ((maxentries < 0) || (stack.size() < maxentries)) {
                        // in case that we don't have enough yet, accept any new entry
                        if (urlhashes.containsKey(iEntry.metadataHash())) continue;
                        stack.push(iEntry, r);
                    } else {
                        // if we already have enough entries, insert only such that are necessary to get a better result
                        if (stack.bottom(r.longValue())) {
                            continue;
                        }
                        // double-check
                        if (urlhashes.containsKey(iEntry.metadataHash())) continue;
                        stack.push(iEntry, r);
                    }
                    
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), QueryEvent.PRESORT, index.size(), System.currentTimeMillis() - timer), false);
    }

    public Map<byte[], ReferenceContainer<WordReference>> searchContainerMap() {
        // direct access to the result maps is needed for abstract generation
        // this is only available if execQuery() was called before
        return localSearchInclusion;
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
}