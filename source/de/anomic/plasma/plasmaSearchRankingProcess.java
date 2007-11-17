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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIEntryOrder;
import de.anomic.kelondro.kelondroBinSearch;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverProfiling;
import de.anomic.yacy.yacyURL;

public final class plasmaSearchRankingProcess {
    
    public  static kelondroBinSearch[] ybrTables = null; // block-rank tables
    private static boolean useYBR = true;
    
    private TreeMap pageAcc; // key = ranking (Long); value = indexRWIEntry
    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    private int filteredCount;
    private indexRWIEntryOrder order;
    private serverProfiling process;
    private int maxentries;
    private int globalcount;
    private HashMap urlhashes; // map for double-check; String/Long relation, addresses ranking number (backreference for deletion)
    private kelondroMScoreCluster ref;  // reference score computation for the commonSense heuristic
    private int[] c; // flag counter
    
    public plasmaSearchRankingProcess(plasmaSearchQuery query, serverProfiling process, plasmaSearchRankingProfile ranking, int maxentries) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime
        this.pageAcc = new TreeMap();
        this.process = process;
        this.order = null;
        this.query = query;
        this.ranking = ranking;
        this.maxentries = maxentries;
        this.globalcount = 0;
        this.urlhashes = new HashMap();
        this.ref = new kelondroMScoreCluster();
        c = new int[32];
        for (int i = 0; i < 32; i++) {c[i] = 0;}
    }
    
    public void insert(indexContainer container, boolean local) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        assert (container != null);
        if (container.size() == 0) return;
        
        if (process != null) process.startTimer();
        if (this.order == null) {
            this.order = new indexRWIEntryOrder(ranking);
        }
        this.order.extend(container);
        if (process != null) process.yield(plasmaSearchEvent.NORMALIZING, container.size());
        
        /*
        container.setOrdering(o, 0);
        container.sort();
        */
        
        // normalize entries and get ranking
        if (process != null) process.startTimer();
        Iterator i = container.entries();
        this.pageAcc = new TreeMap();
        indexRWIEntry iEntry, l;
        long biggestEntry = 0;
        //long s0 = System.currentTimeMillis();
        Long r;
        while (i.hasNext()) {
            iEntry = (indexRWIEntry) i.next();
            if (iEntry.urlHash().length() != container.row().primaryKeyLength) continue;

            // increase flag counts
            for (int j = 0; j < 32; j++) {
                if (iEntry.flags().get(j)) {c[j]++;}
            }
            
            // kick out entries that are too bad according to current findings
            r = new Long(order.cardinal(iEntry));
            if ((maxentries >= 0) && (pageAcc.size() >= maxentries) && (r.longValue() > biggestEntry)) continue;
                        
            // check constraints
            if ((!(query.constraint.equals(plasmaSearchQuery.catchall_constraint))) && (!(iEntry.flags().allOf(query.constraint)))) continue; // filter out entries that do not match the search constraint
            if (query.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasaudio)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasvideo)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasimage)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP  ) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasapp  )))) continue;
            }
            if ((maxentries < 0) || (pageAcc.size() < maxentries)) {
                if (urlhashes.containsKey(iEntry.urlHash())) continue;
                while (pageAcc.containsKey(r)) r = new Long(r.longValue() + 1);
                pageAcc.put(r, iEntry);
            } else {
                if (r.longValue() > biggestEntry) {
                    continue;
                } else {
                    if (urlhashes.containsKey(iEntry.urlHash())) continue;
                    l = (indexRWIEntry) pageAcc.remove((Long) pageAcc.lastKey());
                    urlhashes.remove(l.urlHash());
                    while (pageAcc.containsKey(r)) r = new Long(r.longValue() + 1);
                    pageAcc.put(r, iEntry);
                    biggestEntry = order.cardinal((indexRWIEntry) pageAcc.get(pageAcc.lastKey()));
                }
            }
            urlhashes.put(iEntry.urlHash(), r);
            
            // increase counter for statistics
            if (!local) this.globalcount++;
        }
        this.filteredCount = pageAcc.size();
        //long sc = Math.max(1, System.currentTimeMillis() - s0);
        //System.out.println("###DEBUG### time to sort " + container.size() + " entries to " + this.filteredCount + ": " + sc + " milliseconds, " + (container.size() / sc) + " entries/millisecond, ranking = " + tc);
        
        if (container.size() > query.neededResults()) remove(true, true);

        if (process != null) process.yield(plasmaSearchEvent.PRESORT, container.size());
    }
    
    public class rIterator implements Iterator {

    	boolean urls;
    	Iterator r;
    	plasmaWordIndex wi;
    	public rIterator(plasmaWordIndex wi, boolean fetchURLs) {
    		// if fetchURLs == true, this iterates indexURLEntry objects, otherwise it iterates indexRWIEntry objects
    		this.urls = fetchURLs;
    		this.r = pageAcc.entrySet().iterator();
    		this.wi = wi;
    	}
    	
		public boolean hasNext() {
			return r.hasNext();
		}

		public Object next() {
			Map.Entry entry = (Map.Entry) r.next();
			indexRWIEntry ientry = (indexRWIEntry) entry.getValue();
			if (urls) {
				return wi.loadedURL.load(ientry.urlHash(), ientry, ((Long) entry.getKey()).longValue());
			} else {
				return ientry;
			}
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
    }
    
    public int size() {
        assert pageAcc.size() == urlhashes.size();
        return pageAcc.size();
    }
    
    public int[] flagCount() {
    	return c;
    }
    
    public int filteredCount() {
        return this.filteredCount;
    }

    public int getGlobalCount() {
        return this.globalcount;
    }
    
    public indexRWIEntry remove(String urlHash) {
        Long r = (Long) urlhashes.get(urlHash);
        if (r == null) return null;
        assert pageAcc.containsKey(r);
        indexRWIEntry iEntry = (indexRWIEntry) pageAcc.remove(r);
        urlhashes.remove(urlHash);
        return iEntry;
    }

    public Iterator entries(plasmaWordIndex wi, boolean fetchURLs) {
    	// if fetchURLs == true, this iterates indexURLEntry objects, otherwise it iterates indexRWIEntry objects
        return new rIterator(wi, fetchURLs);
    }
    
    public Set getReferences(int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        Object[] refs = ref.getScores(count, false, 2, Integer.MAX_VALUE);
        TreeSet s = new TreeSet(String.CASE_INSENSITIVE_ORDER);
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
    
    private void remove(boolean rootDomExt, boolean doubleDom) {
        // this removes all refererences to urls that are extended paths of existing 'RootDom'-urls
        if (pageAcc.size() <= query.neededResults()) return;
        HashSet rootDoms = new HashSet();
        HashSet doubleDoms = new HashSet();
        Iterator i = pageAcc.entrySet().iterator();
        Map.Entry entry;
        indexRWIEntry iEntry;
        String hashpart;
        boolean isWordRootURL;
        TreeSet querywords = plasmaSearchQuery.cleanQuery(query.queryString())[0];
        while (i.hasNext()) {
            if (pageAcc.size() <= query.neededResults()) break;
            entry = (Map.Entry) i.next();
            iEntry = (indexRWIEntry) entry.getValue();
            hashpart = iEntry.urlHash().substring(6);
            isWordRootURL = yacyURL.isWordRootURL(iEntry.urlHash(), querywords);
            if (isWordRootURL) {
                rootDoms.add(hashpart);
            } else {
            	if (((rootDomExt) && (rootDoms.contains(hashpart))) ||
                    ((doubleDom) && (doubleDoms.contains(hashpart)))) {
            		i.remove();
                }
            }
            doubleDoms.add(hashpart);
        }
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
    
}
