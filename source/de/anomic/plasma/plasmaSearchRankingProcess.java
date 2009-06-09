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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.index.BinSearch;
import de.anomic.kelondro.order.Digest;
import de.anomic.kelondro.text.Reference;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.ReferenceOrder;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.TermSearch;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.text.referencePrototype.WordReferenceVars;
import de.anomic.kelondro.util.SortStack;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.plasma.parser.Word;
import de.anomic.plasma.parser.Condenser;
import de.anomic.server.serverProfiling;
import de.anomic.yacy.yacyURL;

public final class plasmaSearchRankingProcess {
    
    public  static BinSearch[] ybrTables = null; // block-rank tables
    public  static final int maxYBR = 3; // the lower this value, the faster the search
    private static boolean useYBR = true;
    private static final int maxDoubleDomAll = 20, maxDoubleDomSpecial = 10000;
    
    private final SortStack<WordReferenceVars> stack;
    private final HashMap<String, SortStack<WordReferenceVars>> doubleDomCache; // key = domhash (6 bytes); value = like stack
    private final HashSet<String> handover; // key = urlhash; used for double-check of urls that had been handed over to search process
    private final plasmaSearchQuery query;
    private final int maxentries;
    private int remote_peerCount, remote_indexCount, remote_resourceSize, local_resourceSize;
    private final ReferenceOrder order;
    private final ConcurrentHashMap<String, Integer> urlhashes; // map for double-check; String/Long relation, addresses ranking number (backreference for deletion)
    private final int[] flagcount; // flag counter
    private final TreeSet<String> misses; // contains url-hashes that could not been found in the LURL-DB
    private final Segment indexSegment;
    private HashMap<byte[], ReferenceContainer<WordReference>> localSearchInclusion;
    private final int[] domZones;
    private final ConcurrentHashMap<String, Integer> ref;  // reference score computation for the commonSense heuristic
    private final ConcurrentHashMap<String, HostInfo> hostNavigator;
    private final ConcurrentHashMap<String, AuthorInfo> authorNavigator;
    
    public plasmaSearchRankingProcess(
            final Segment indexSegment,
            final plasmaSearchQuery query,
            final int maxentries,
            final int concurrency) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        // sortorder: 0 = hash, 1 = url, 2 = ranking
        this.localSearchInclusion = null;
        this.stack = new SortStack<WordReferenceVars>(maxentries);
        this.doubleDomCache = new HashMap<String, SortStack<WordReferenceVars>>();
        this.handover = new HashSet<String>();
        this.order = (query == null) ? null : new ReferenceOrder(query.ranking, query.targetlang);
        this.query = query;
        this.maxentries = maxentries;
        this.remote_peerCount = 0;
        this.remote_indexCount = 0;
        this.remote_resourceSize = 0;
        this.local_resourceSize = 0;
        this.urlhashes = new ConcurrentHashMap<String, Integer>(0, 0.75f, concurrency);
        this.misses = new TreeSet<String>();
        this.indexSegment = indexSegment;
        this.flagcount = new int[32];
        for (int i = 0; i < 32; i++) {this.flagcount[i] = 0;}
        this.hostNavigator = new ConcurrentHashMap<String, HostInfo>();
        this.authorNavigator = new ConcurrentHashMap<String, AuthorInfo>();
        this.ref = new ConcurrentHashMap<String, Integer>();
        this.domZones = new int[8];
        for (int i = 0; i < 8; i++) {this.domZones[i] = 0;}
    }
    
    public long ranking(final WordReferenceVars word) {
        return order.cardinal(word);
    }
    
    public int[] zones() {
        return this.domZones;
    }
    
    public void execQuery() {
        
        long timer = System.currentTimeMillis();
        final TermSearch<WordReference> search = this.indexSegment.termIndex().query(
                query.queryHashes,
                query.excludeHashes,
                null,
                Segment.wordReferenceFactory,
                query.maxDistance);
        this.localSearchInclusion = search.inclusion();
        final ReferenceContainer<WordReference> index = search.joined();
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), plasmaSearchEvent.JOIN, index.size(), System.currentTimeMillis() - timer), false);
        if (index.size() == 0) {
            return;
        }
        
        insertRanked(index, true, index.size());
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
        }
        
        long timer = System.currentTimeMillis();
        
        // normalize entries
        final ArrayList<WordReferenceVars> decodedEntries = this.order.normalizeWith(index);
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), plasmaSearchEvent.NORMALIZING, index.size(), System.currentTimeMillis() - timer), false);
        
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
            if (query.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (!(iEntry.flags().get(Condenser.flag_cat_hasaudio)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (!(iEntry.flags().get(Condenser.flag_cat_hasvideo)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (!(iEntry.flags().get(Condenser.flag_cat_hasimage)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP  ) && (!(iEntry.flags().get(Condenser.flag_cat_hasapp  )))) continue;
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
            
            // increase counter for statistics
            if (!local) this.remote_indexCount++;
        }
        
        //if ((query.neededResults() > 0) && (container.size() > query.neededResults())) remove(true, true);
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(query.id(true), plasmaSearchEvent.PRESORT, index.size(), System.currentTimeMillis() - timer), false);
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
    
    
    private SortStack<WordReferenceVars>.stackElement bestRWI(final boolean skipDoubleDom) {
        // returns from the current RWI list the best entry and removes this entry from the list
        SortStack<WordReferenceVars> m;
        SortStack<WordReferenceVars>.stackElement rwi;
        while (stack.size() > 0) {
            rwi = stack.pop();
            if (rwi == null) continue; // in case that a synchronization problem occurred just go lazy over it
            if (!skipDoubleDom) return rwi;
            // check doubledom
            final String domhash = rwi.element.metadataHash().substring(6);
            m = this.doubleDomCache.get(domhash);
            if (m == null) {
                // first appearance of dom
                m = new SortStack<WordReferenceVars>((query.specialRights) ? maxDoubleDomSpecial : maxDoubleDomAll);
                this.doubleDomCache.put(domhash, m);
                return rwi;
            }
            // second appearances of dom
            m.push(rwi);
        }
        // no more entries in sorted RWI entries. Now take Elements from the doubleDomCache
        // find best entry from all caches
        final Iterator<SortStack<WordReferenceVars>> i = this.doubleDomCache.values().iterator();
        SortStack<WordReferenceVars>.stackElement bestEntry = null;
        SortStack<WordReferenceVars>.stackElement o;
        while (i.hasNext()) {
            m = i.next();
            if (m == null) continue;
            if (m.size() == 0) continue;
            if (bestEntry == null) {
                bestEntry = m.top();
                continue;
            }
            o = m.top();
            if (o.weight.longValue() < bestEntry.weight.longValue()) {
                bestEntry = o;
            }
        }
        if (bestEntry == null) return null;
        // finally remove the best entry from the doubledom cache
        m = this.doubleDomCache.get(bestEntry.element.metadataHash().substring(6));
        o = m.pop();
        assert o == null || o.element.metadataHash().equals(bestEntry.element.metadataHash()) : "bestEntry.element.metadataHash() = " + bestEntry.element.metadataHash() + ", o.element.metadataHash() = " + o.element.metadataHash();
        return bestEntry;
    }
    
    public URLMetadataRow bestURL(final boolean skipDoubleDom) {
        // returns from the current RWI list the best URL entry and removes this entry from the list
        while ((stack.size() > 0) || (size() > 0)) {
            if (((stack.size() == 0) && (size() == 0))) break;
            final SortStack<WordReferenceVars>.stackElement obrwi = bestRWI(skipDoubleDom);
            if (obrwi == null) continue; // *** ? this happened and the thread was suspended silently. cause?
            final URLMetadataRow u = indexSegment.urlMetadata().load(obrwi.element.metadataHash(), obrwi.element, obrwi.weight.longValue());
            if (u != null) {
                final URLMetadataRow.Components metadata = u.metadata();

                // evaluate information of metadata for navigation
                // author navigation:
                String author = metadata.dc_creator();
                if (author != null && author.length() > 0) {
                	// add author to the author navigator
                    String authorhash = new String(Word.word2hash(author));
                    //System.out.println("*** DEBUG authorhash = " + authorhash + ", query.authorhash = " + this.query.authorhash + ", author = " + author);
                    
                    // check if we already are filtering for authors
                	if (this.query.authorhash != null && !this.query.authorhash.equals(authorhash)) {
                		continue;
                	}
                	
                	// add author to the author navigator
                    AuthorInfo in = this.authorNavigator.get(authorhash);
                    if (in == null) {
                        this.authorNavigator.put(authorhash, new AuthorInfo(author));
                    } else {
                        in.inc();
                        this.authorNavigator.put(authorhash, in);
                    }
                } else if (this.query.authorhash != null) {
                	continue;
                }
                
                // get the url
                if (metadata.url() != null) {
                	String urlstring = metadata.url().toNormalform(true, true);
                	if (urlstring == null || !urlstring.matches(query.urlMask)) continue;                    
                    this.handover.add(u.hash()); // remember that we handed over this url
                    return u;
                }
            }
            misses.add(obrwi.element.metadataHash());
        }
        return null;
    }
    
    public int size() {
        //assert sortedRWIEntries.size() == urlhashes.size() : "sortedRWIEntries.size() = " + sortedRWIEntries.size() + ", urlhashes.size() = " + urlhashes.size();
        int c = stack.size();
        final Iterator<SortStack<WordReferenceVars>> i = this.doubleDomCache.values().iterator();
        while (i.hasNext()) c += i.next().size();
        return c;
    }
    
    public int[] flagCount() {
    	return flagcount;
    }
    
    // "results from a total number of <remote_resourceSize + local_resourceSize> known (<local_resourceSize> local, <remote_resourceSize> remote), <remote_indexCount> links from <remote_peerCount> other YaCy peers."
    
    public int filteredCount() {
        // the number of index entries that are considered as result set
        return this.stack.size();
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
    
    public Reference remove(final String urlHash) {
        final SortStack<WordReferenceVars>.stackElement se = stack.remove(urlHash.hashCode());
        if (se == null) return null;
        urlhashes.remove(urlHash);
        return se.element;
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
        URLMetadataRow mr;
        yacyURL url;
        for (int i = 0; i < rc; i++) {
            mr = indexSegment.urlMetadata().load(hsa[i].hashsample, null, 0);
            if (mr == null) continue;
            url = mr.metadata().url();
            if (url == null) continue;
            result.add(new NavigatorEntry(url.getHost(), hsa[i].count));
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
                !plasmaSwitchboard.badwords.contains(word) &&
                !plasmaSwitchboard.stopwords.contains(word)) {
                c = ref.get(word);
                if (c == null) ref.put(word, 1); else ref.put(word, c.intValue() + 1);
            }
        }
    }
    
    protected void addTopics(final plasmaSearchEvent.ResultEntry resultEntry) {
        // take out relevant information for reference computation
        if ((resultEntry.url() == null) || (resultEntry.title() == null)) return;
        //final String[] urlcomps = htmlFilterContentScraper.urlComps(resultEntry.url().toNormalform(true, true)); // word components of the url
        final String[] descrcomps = resultEntry.title().toLowerCase().split(htmlFilterContentScraper.splitrex); // words in the description
        
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
    
    public ReferenceOrder getOrder() {
        return this.order;
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
    
    public long postRanking(
                    final Set<String> topwords,
                    final plasmaSearchEvent.ResultEntry rentry,
                    final int position) {

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
        final String urlstring = rentry.url().toNormalform(true, true);
        final String[] urlcomps = htmlFilterContentScraper.urlComps(urlstring);
        final String[] descrcomps = rentry.title().toLowerCase().split(htmlFilterContentScraper.splitrex);
        for (int j = 0; j < urlcomps.length; j++) {
            if (topwords.contains(urlcomps[j])) r += Math.max(1, 256 - urlstring.length()) << query.ranking.coeff_urlcompintoplist;
        }
        for (int j = 0; j < descrcomps.length; j++) {
            if (topwords.contains(descrcomps[j])) r += Math.max(1, 256 - rentry.title().length()) << query.ranking.coeff_descrcompintoplist;
        }

        // apply query-in-result matching
        final Set<byte[]> urlcomph = Word.words2hashSet(urlcomps);
        final Set<byte[]> descrcomph = Word.words2hashSet(descrcomps);
        final Iterator<byte[]> shi = query.queryHashes.iterator();
        byte[] queryhash;
        while (shi.hasNext()) {
            queryhash = shi.next();
            if (urlcomph.contains(queryhash)) r += 256 << query.ranking.coeff_appurl;
            if (descrcomph.contains(queryhash)) r += 256 << query.ranking.coeff_app_dc_title;
        }

        return r;
    }
}
