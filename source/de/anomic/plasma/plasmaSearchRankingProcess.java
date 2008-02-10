// plasmaSearchRankingProcess.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIEntryOrder;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexRWIVarEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBinSearch;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverProfiling;

public final class plasmaSearchRankingProcess {
    
    public  static kelondroBinSearch[] ybrTables = null; // block-rank tables
    private static boolean useYBR = true;
    
    private TreeMap<Object, indexRWIVarEntry> sortedRWIEntries; // key = ranking (Long); value = indexRWIEntry; if sortorder < 2 then key is instance of String
    private HashMap<String, TreeMap<Object, indexRWIVarEntry>> doubleDomCache; // key = domhash (6 bytes); value = TreeMap like sortedRWIEntries
    private HashMap<String, String> handover; // key = urlhash, value = urlstring; used for double-check of urls that had been handed over to search process
    private plasmaSearchQuery query;
    private int sortorder;
    private int maxentries;
    private int remote_peerCount, remote_indexCount, remote_resourceSize, local_resourceSize;
    private indexRWIEntryOrder order;
    private HashMap<String, Object> urlhashes; // map for double-check; String/Long relation, addresses ranking number (backreference for deletion)
    private kelondroMScoreCluster<String> ref;  // reference score computation for the commonSense heuristic
    private int[] flagcount; // flag counter
    private TreeSet<String> misses; // contains url-hashes that could not been found in the LURL-DB
    private plasmaWordIndex wordIndex;
    private Map<String, indexContainer>[] localSearchContainerMaps;
    
    public plasmaSearchRankingProcess(plasmaWordIndex wordIndex, plasmaSearchQuery query, int sortorder, int maxentries) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        // sortorder: 0 = hash, 1 = url, 2 = ranking
        this.localSearchContainerMaps = null;
        this.sortedRWIEntries = new TreeMap<Object, indexRWIVarEntry>();
        this.doubleDomCache = new HashMap<String, TreeMap<Object, indexRWIVarEntry>>();
        this.handover = new HashMap<String, String>();
        this.order = null;
        this.query = query;
        this.maxentries = maxentries;
        this.remote_peerCount = 0;
        this.remote_indexCount = 0;
        this.remote_resourceSize = 0;
        this.local_resourceSize = 0;
        this.urlhashes = new HashMap<String, Object>();
        this.ref = new kelondroMScoreCluster<String>();
        this.misses = new TreeSet<String>();
        this.wordIndex = wordIndex;
        this.sortorder = sortorder;
        this.flagcount = new int[32];
        for (int i = 0; i < 32; i++) {this.flagcount[i] = 0;}
    }
    
    public void execQuery() {
        
        long timer = System.currentTimeMillis();
        this.localSearchContainerMaps = wordIndex.localSearchContainers(query, null);
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), plasmaSearchEvent.COLLECTION, this.localSearchContainerMaps[0].size(), System.currentTimeMillis() - timer));
        
        // join and exclude the local result
        timer = System.currentTimeMillis();
        indexContainer index =
            (this.localSearchContainerMaps == null) ?
              plasmaWordIndex.emptyContainer(null, 0) :
                  indexContainer.joinExcludeContainers(
                      this.localSearchContainerMaps[0].values(),
                      this.localSearchContainerMaps[1].values(),
                      query.maxDistance);
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), plasmaSearchEvent.JOIN, index.size(), System.currentTimeMillis() - timer));
        int joincount = index.size();
        
        if ((index == null) || (joincount == 0)) {
            return;
        }
        
        if (sortorder == 2) {
            insertRanked(index, true, index.size());
        } else {            
            insertNoOrder(index, true, index.size());
        }
    }
    
    private void insertNoOrder(indexContainer index, boolean local, int fullResource) {
        final Iterator<indexRWIRowEntry> en = index.entries();
        // generate a new map where the urls are sorted (not by hash but by the url text)
        
        if (local) {
            this.local_resourceSize += fullResource;
        } else {
            this.remote_resourceSize += fullResource;
            this.remote_peerCount++;
            this.remote_indexCount += index.size();
        }
        
        indexRWIVarEntry ientry;
        indexURLEntry uentry;
        String u;
        loop: while (en.hasNext()) {
            ientry = new indexRWIVarEntry(en.next());

            // check constraints
            if (!testFlags(ientry)) continue loop;
            
            // increase flag counts
            for (int i = 0; i < 32; i++) {
                if (ientry.flags().get(i)) {flagcount[i]++;}
            }
            
            // load url
            if (sortorder == 0) {
                this.sortedRWIEntries.put(ientry.urlHash(), ientry);
                this.urlhashes.put(ientry.urlHash(), ientry.urlHash());
            } else {
                uentry = wordIndex.loadedURL.load(ientry.urlHash(), ientry, 0);
                if (uentry == null) {
                    this.misses.add(ientry.urlHash());
                } else {
                    u = uentry.comp().url().toNormalform(false, true);
                    this.sortedRWIEntries.put(u, ientry);
                    this.urlhashes.put(ientry.urlHash(), u);
                }
            }
            
            // interrupt if we have enough
            if ((query.neededResults() > 0) && (this.misses.size() + this.sortedRWIEntries.size() > query.neededResults())) break loop;
        } // end loop
    }
    
    public void insertRanked(indexContainer index, boolean local, int fullResource) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        assert (index != null);
        if (index.size() == 0) return;
        if (local) {
            this.local_resourceSize += fullResource;
        } else {
            this.remote_resourceSize += fullResource;
            this.remote_peerCount++;
        }
        
        long timer = System.currentTimeMillis();
        if (this.order == null) {
            this.order = new indexRWIEntryOrder(query.ranking);
        }
        ArrayList<indexRWIVarEntry> decodedEntries = this.order.normalizeWith(index);
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), plasmaSearchEvent.NORMALIZING, index.size(), System.currentTimeMillis() - timer));
        
        // normalize entries and get ranking
        timer = System.currentTimeMillis();
        Iterator<indexRWIVarEntry> i = decodedEntries.iterator();
        indexRWIVarEntry iEntry, l;
        long biggestEntry = 0;
        //long s0 = System.currentTimeMillis();
        Long r;
        while (i.hasNext()) {
            iEntry = i.next();
            if (iEntry.urlHash().length() != index.row().primaryKeyLength) continue;

            // increase flag counts
            for (int j = 0; j < 32; j++) {
                if (iEntry.flags().get(j)) {flagcount[j]++;}
            }
            
            // kick out entries that are too bad according to current findings
            r = new Long(order.cardinal(iEntry));
            if ((maxentries >= 0) && (sortedRWIEntries.size() >= maxentries) && (r.longValue() > biggestEntry)) continue;
            
            // check constraints
            if (!testFlags(iEntry)) continue;
            
            if (query.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasaudio)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasvideo)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasimage)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP  ) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasapp  )))) continue;
            }
            if ((maxentries < 0) || (sortedRWIEntries.size() < maxentries)) {
                if (urlhashes.containsKey(iEntry.urlHash())) continue;
                while (sortedRWIEntries.containsKey(r)) r = new Long(r.longValue() + 1);
                sortedRWIEntries.put(r, iEntry);
            } else {
                if (r.longValue() > biggestEntry) {
                    continue;
                } else {
                    if (urlhashes.containsKey(iEntry.urlHash())) continue;
                    l = sortedRWIEntries.remove((Long) sortedRWIEntries.lastKey());
                    urlhashes.remove(l.urlHash());
                    while (sortedRWIEntries.containsKey(r)) r = new Long(r.longValue() + 1);
                    sortedRWIEntries.put(r, iEntry);
                    biggestEntry = order.cardinal(sortedRWIEntries.get(sortedRWIEntries.lastKey()));
                }
            }
            
            // increase counter for statistics
            if (!local) this.remote_indexCount++;
        }
        
        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), plasmaSearchEvent.PRESORT, index.size(), System.currentTimeMillis() - timer));
    }

    private boolean testFlags(indexRWIEntry ientry) {
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
    
    public synchronized Map<String, indexContainer>[] searchContainerMaps() {
        // direct access to the result maps is needed for abstract generation
        // this is only available if execQuery() was called before
        return localSearchContainerMaps;
    }
    
    // todo:
    // - remove redundant urls (sub-path occurred before)
    // - move up shorter urls
    // - root-domain guessing to prefer the root domain over other urls if search word appears in domain name
    
    
    private synchronized Object[] /*{Object, indexRWIEntry}*/ bestRWI(boolean skipDoubleDom) {
        // returns from the current RWI list the best entry and removed this entry from the list
        Object bestEntry;
        TreeMap<Object, indexRWIVarEntry> m;
        indexRWIVarEntry rwi;
        while (sortedRWIEntries.size() > 0) {
            bestEntry = sortedRWIEntries.firstKey();
            rwi = sortedRWIEntries.remove(bestEntry);
            if (!skipDoubleDom) return new Object[]{bestEntry, rwi};
            // check doubledom
            String domhash = rwi.urlHash().substring(6);
            m = this.doubleDomCache.get(domhash);
            if (m == null) {
                // first appearance of dom
                m = new TreeMap<Object, indexRWIVarEntry>();
                this.doubleDomCache.put(domhash, m);
                return new Object[]{bestEntry, rwi};
            }
            // second appearances of dom
            m.put(bestEntry, rwi);
        }
        // no more entries in sorted RWI entries. Now take Elements from the doubleDomCache
        // find best entry from all caches
        Iterator<TreeMap<Object, indexRWIVarEntry>> i = this.doubleDomCache.values().iterator();
        bestEntry = null;
        Object o;
        indexRWIVarEntry bestrwi = null;
        while (i.hasNext()) {
            m = i.next();
            if (m.size() == 0) continue;
            if (bestEntry == null) {
                bestEntry = m.firstKey();
                bestrwi = m.remove(bestEntry);
                continue;
            }
            o = m.firstKey();
            rwi = m.remove(o);
            if (o instanceof Long) {
                if (((Long) o).longValue() < ((Long) bestEntry).longValue()) {
                    bestEntry = o;
                    bestrwi = rwi;
                }
            }
            if (o instanceof String) {
                if (((String) o).compareTo((String) bestEntry) < 0) {
                    bestEntry = o;
                    bestrwi = rwi;
                }
            }
        }
        if (bestrwi == null) return null;
        // finally remove the best entry from the doubledom cache
        m = this.doubleDomCache.get(bestrwi.urlHash().substring(6));
        m.remove(bestEntry);
        return new Object[]{bestEntry, bestrwi};
    }
    
    public synchronized indexURLEntry bestURL(boolean skipDoubleDom) {
        // returns from the current RWI list the best URL entry and removed this entry from the list
        while ((sortedRWIEntries.size() > 0) || (size() > 0)) {
            Object[] obrwi = bestRWI(skipDoubleDom);
            Object bestEntry = obrwi[0];
            indexRWIVarEntry ientry = (indexRWIVarEntry) obrwi[1];
            long ranking = (bestEntry instanceof Long) ? ((Long) bestEntry).longValue() : 0;
            indexURLEntry u = wordIndex.loadedURL.load(ientry.urlHash(), ientry, ranking);
            if (u != null) {
            	indexURLEntry.Components comp = u.comp();
            	if (comp.url() != null) this.handover.put(u.hash(), comp.url().toNormalform(true, false)); // remember that we handed over this url
                return u;
            }
            misses.add(ientry.urlHash());
        }
        return null;
    }
    
    public synchronized int size() {
        //assert sortedRWIEntries.size() == urlhashes.size() : "sortedRWIEntries.size() = " + sortedRWIEntries.size() + ", urlhashes.size() = " + urlhashes.size();
        int c = sortedRWIEntries.size();
        Iterator<TreeMap<Object, indexRWIVarEntry>> i = this.doubleDomCache.values().iterator();
        while (i.hasNext()) c += i.next().size();
        return c;
    }
    
    public int[] flagCount() {
    	return flagcount;
    }
    
    // "results from a total number of <remote_resourceSize + local_resourceSize> known (<local_resourceSize> local, <remote_resourceSize> remote), <remote_indexCount> links from <remote_peerCount> other YaCy peers."
    
    public int filteredCount() {
        // the number of index entries that are considered as result set
        return this.sortedRWIEntries.size();
    }

    public int getRemoteIndexCount() {
        // the number of result contributions from all the remote peers
        return this.remote_indexCount;
    }
    
    public int getRemotePeerCount() {
        // the number of remote peers that have contributed
        return this.remote_peerCount;
    }
    
    public int getRemoteResourceSize() {
        // the number of all hits in all the remote peers
        return this.remote_resourceSize;
    }
    
    public int getLocalResourceSize() {
        // the number of hits in the local peer (index size, size of the collection in the own index)
        return this.local_resourceSize;
    }
    
    
    public indexRWIEntry remove(String urlHash) {
        Object r = (Long) urlhashes.get(urlHash);
        if (r == null) return null;
        assert sortedRWIEntries.containsKey(r);
        indexRWIEntry iEntry = (indexRWIEntry) sortedRWIEntries.remove(r);
        urlhashes.remove(urlHash);
        return iEntry;
    }
    
    public Iterator<String> miss() {
        return this.misses.iterator();
    }
    
    public Set<String> getReferences(int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        Object[] refs = ref.getScores(count, false, 2, Integer.MAX_VALUE);
        TreeSet<String> s = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < refs.length; i++) {
            s.add((String) refs[i]);
        }
        return s;
    }
    
    public void addReferences(String[] words) {
        String word;
        for (int i = 0; i < words.length; i++) {
            word = words[i].toLowerCase();
            if ((word.length() > 2) &&
                ("http_html_php_ftp_www_com_org_net_gov_edu_index_home_page_for_usage_the_and_".indexOf(word) < 0) &&
                (!(query.queryHashes.contains(plasmaCondenser.word2hash(word)))))
                ref.incScore(word);
        }
    }
    
    protected void addReferences(plasmaSearchEvent.ResultEntry resultEntry) {
        // take out relevant information for reference computation
        if ((resultEntry.url() == null) || (resultEntry.title() == null)) return;
        String[] urlcomps = htmlFilterContentScraper.urlComps(resultEntry.url().toNormalform(true, true)); // word components of the url
        String[] descrcomps = resultEntry.title().toLowerCase().split(htmlFilterContentScraper.splitrex); // words in the description
        
        // add references
        addReferences(urlcomps);
        addReferences(descrcomps);
    }
    
    public indexRWIEntryOrder getOrder() {
        return this.order;
    }
    
    public static void loadYBR(File rankingPath, int count) {
        // load ranking tables
        if (rankingPath.exists()) {
            ybrTables = new kelondroBinSearch[count];
            String ybrName;
            File f;
            try {
                for (int i = 0; i < count; i++) {
                    ybrName = "YBR-4-" + serverCodings.encodeHex(i, 2) + ".idx";
                    f = new File(rankingPath, ybrName);
                    if (f.exists()) {
                        ybrTables[i] = new kelondroBinSearch(serverFileUtils.read(f), 6);
                    } else {
                        ybrTables[i] = null;
                    }
                }
            } catch (IOException e) {
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
    
    public static void switchYBR(boolean usage) {
        useYBR = usage;
    }
    
    public static int ybr(String urlHash) {
        // returns the YBR value in a range of 0..15, where 0 means best ranking and 15 means worst ranking
        if (ybrTables == null) return 15;
        if (!(useYBR)) return 15;
        final String domHash = urlHash.substring(6);
        for (int i = 0; i < ybrTables.length; i++) {
            if ((ybrTables[i] != null) && (ybrTables[i].contains(domHash.getBytes()))) {
                //System.out.println("YBR FOUND: " + urlHash + " (" + i + ")");
                return i;
            }
        }
        //System.out.println("NOT FOUND: " + urlHash);
        return 15;
    }
    
    public long postRanking(
                    Set<String> topwords,
                    plasmaSearchEvent.ResultEntry rentry,
                    int position) {

        long r = (255 - position) << 8;
        
        // for media search: prefer pages with many links
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) r += rentry.limage() << query.ranking.coeff_cathasimage;
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) r += rentry.laudio() << query.ranking.coeff_cathasaudio;
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) r += rentry.lvideo() << query.ranking.coeff_cathasvideo;
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_APP  ) r += rentry.lapp()   << query.ranking.coeff_cathasapp;
        
        // prefer hit with 'prefer' pattern
        if (rentry.url().toNormalform(true, true).matches(query.prefer)) r += 256 << query.ranking.coeff_prefer;
        if (rentry.title().matches(query.prefer)) r += 256 << query.ranking.coeff_prefer;
        
        // apply 'common-sense' heuristic using references
        String urlstring = rentry.url().toNormalform(true, true);
        String[] urlcomps = htmlFilterContentScraper.urlComps(urlstring);
        String[] descrcomps = rentry.title().toLowerCase().split(htmlFilterContentScraper.splitrex);
        for (int j = 0; j < urlcomps.length; j++) {
            if (topwords.contains(urlcomps[j])) r += Math.max(1, 256 - urlstring.length()) << query.ranking.coeff_urlcompintoplist;
        }
        for (int j = 0; j < descrcomps.length; j++) {
            if (topwords.contains(descrcomps[j])) r += Math.max(1, 256 - rentry.title().length()) << query.ranking.coeff_descrcompintoplist;
        }

        // apply query-in-result matching
        Set<String> urlcomph = plasmaCondenser.words2hashSet(urlcomps);
        Set<String> descrcomph = plasmaCondenser.words2hashSet(descrcomps);
        Iterator<String> shi = query.queryHashes.iterator();
        String queryhash;
        while (shi.hasNext()) {
            queryhash = shi.next();
            if (urlcomph.contains(queryhash)) r += 256 << query.ranking.coeff_appurl;
            if (descrcomph.contains(queryhash)) r += 256 << query.ranking.coeff_app_dc_title;
        }

        return r;
    }
}
