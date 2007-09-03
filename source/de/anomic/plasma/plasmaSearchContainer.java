// plasmaSearchContainer.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 29.8.2007 on http://yacy.net
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.kelondro.kelondroMScoreCluster;

public class plasmaSearchContainer {

    private indexRWIEntry entryMin, entryMax;
    private indexContainer container;
    private plasmaSearchRankingProfile ranking;
    private TreeSet searchedWords;
    private int globalcount;
    private HashSet urlhashes; // set for double-check
    private kelondroMScoreCluster ref;  // reference score computation for the commonSense heuristic
    private plasmaSearchQuery query;
    
    
    public plasmaSearchContainer(plasmaSearchQuery query, plasmaSearchRankingProfile ranking, TreeSet searchedWords) {
        this(query, ranking, searchedWords, plasmaWordIndex.emptyContainer(null, 0));
    }
    
    public plasmaSearchContainer(plasmaSearchQuery query, plasmaSearchRankingProfile ranking, TreeSet searchedWords, indexContainer presortedContainer) {
        // only for sorted containers
        this.entryMin = null;
        this.entryMax = null;
        this.container = presortedContainer;
        this.ranking = ranking;
        this.searchedWords = searchedWords;
        this.globalcount = 0;
        this.urlhashes = new HashSet();
        this.ref = new kelondroMScoreCluster();
        this.query = query;
    }
    
    public void insert(indexRWIEntry entry, boolean local) {
        // add the entry to the container into a position in such a way, that the container stays sorted
        assert (entry != null);
        
        // make a double-check: because different peers may have computed different ranking attributes,
        // the double check cannot be made using the ranking and the insert position
        if (urlhashes.contains(entry.urlHash())) return;
        urlhashes.add(entry.urlHash());
        
        // find new min/max borders
        if (this.entryMin == null) this.entryMin = (indexRWIEntry) entry.clone(); else this.entryMin.min(entry);
        if (this.entryMax == null) this.entryMax = (indexRWIEntry) entry.clone(); else this.entryMax.max(entry);
        long pivot = this.ranking.preRanking(entry, this.entryMin, this.entryMax, this.searchedWords);
        
        // insert the entry
        int insertPosition = insertPosition(pivot);
        
        // insert at found position
        container.insertUnique(insertPosition, entry.toKelondroEntry());
        
        // update counter
        if (!local) this.globalcount++;
        
    }
    
    public void insert(indexContainer c, boolean local, boolean presorted) {
        if ((this.container.size() == 0) && (presorted)) {
            this.container = c;
            if (!local) this.globalcount = c.size();
        } else {
            Iterator i = c.entries();
            while (i.hasNext()) {
                insert((indexRWIEntry) i.next(), local);
            }
        }
    }

    private int insertPosition(long pivotRanking) {
        return insertPosition(pivotRanking, 0, container.size());
    }
 
    private int insertPosition(long pivotRanking, int left /*including*/, int right /*excluding*/) {
        if (right - left < 10) {
            // do iterative search, less overhead
            for (int i = left; i < right; i++) {
                if (this.ranking.preRanking(new indexRWIEntry(container.get(i)), this.entryMin, this.entryMax, this.searchedWords) < pivotRanking) {
                    // we found the right insert position
                    return i;
                }
            }
            return right;
        }
        // find recursively
        int middle = (left + right) / 2;
        if (this.ranking.preRanking(new indexRWIEntry(container.get(middle)), this.entryMin, this.entryMax, this.searchedWords) < pivotRanking) {
            // must be on the left side
            return insertPosition(pivotRanking, left, middle);
        } else {
            // must be on the right side
            return insertPosition(pivotRanking, middle + 1, right);
        }
    }
    
    public indexRWIEntry remove(String urlHash) {
        return this.container.remove(urlHash);
    }
    
    public int removeEntries(Set urlHashes) {
        return this.container.removeEntries(urlHashes);
    }
    
    public indexContainer container() {
        return this.container;
    }
    
    public int getGlobalCount() {
        return this.globalcount;
    }
    
    public Object[] getReferences(int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        return ref.getScores(count, false, 2, Integer.MAX_VALUE);
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
    
}
