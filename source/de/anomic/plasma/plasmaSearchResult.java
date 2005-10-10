// plasmaSearchResult.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created: 10.10.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


package de.anomic.plasma;

import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.net.URL;

import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.serverCodings;

public final class plasmaSearchResult {
    
    public static final char O_QUALITY = 'q';
    public static final char O_AGE     = 'a';
    public static final String splitrex = " |/|\\(|\\)|-|\\:|_|\\.|,|\\?|!|'|" + '"';
    
    private TreeMap pageAcc;            // key = order hash; value = plasmaLURL.entry
    private kelondroMScoreCluster ref;  // reference score computation for the commonSense heuristic
    private Set searchhashes;           // hashes that are searched here
    private Set stopwords;              // words that are excluded from the commonSense heuristic
    private char[] order;               // order of heuristics
    private ArrayList results;          // this is a buffer for plasmaWordIndexEntry + plasmaCrawlLURL.entry - objects
    
    public plasmaSearchResult(Set searchhashes, Set stopwords, char[] order) {
        this.pageAcc = new TreeMap();
        ref = new kelondroMScoreCluster();
        this.searchhashes = searchhashes;
        this.stopwords = stopwords;
        this.order = order;
        this.results = new ArrayList();
    }
    
    public plasmaSearchResult cloneSmart() {
        // clones only the top structure
        plasmaSearchResult theClone = new plasmaSearchResult(this.searchhashes, this.stopwords, this.order);
        theClone.pageAcc = (TreeMap) this.pageAcc.clone();
        theClone.ref = this.ref;
        theClone.results = this.results;
        return theClone;
    }
    
    public int sizeOrdered() {
        return pageAcc.size();
    }
    
    public int sizeFetched() {
        return results.size();
    }
    
    public boolean hasMoreElements() {
        return pageAcc.size() > 0;
    }
    
    public plasmaCrawlLURL.Entry nextElement() {
        Object top = pageAcc.lastKey();
        return (plasmaCrawlLURL.Entry) pageAcc.remove(top);
    }
    
    protected void addResult(plasmaWordIndexEntry indexEntry, plasmaCrawlLURL.Entry page) {
        // this does 3 things:
        // 1. simply store indexEntry and page to a cache
        // 2. calculate references and store them to cache
        // 2. add reference to reference sorting table
        
        // take out relevant information for reference computation
        URL url = page.url();
        String descr = page.descr();
        if ((url == null) || (descr == null)) return;
        String[] urlcomps = url.toString().split(splitrex); // word components of the url
        String[] descrcomps = descr.split(splitrex); // words in the description
        
        // store everything
        Object[] resultVector = new Object[] {indexEntry, page, urlcomps, descrcomps};
        results.add(resultVector);
        
        // add references
        addScoreFiltered(urlcomps);
        addScoreFiltered(descrcomps);
    }
    
    protected void sortResults() {
        // finally sort the results
        
        // create a commonSense - set that represents a set of words that is
        // treated as 'typical' for this search request
        Object[] references = getReferences(16);
        Set commonSense = new HashSet();
        for (int i = 0; i < references.length; i++) commonSense.add((String) references[i]);
        
        Object[] resultVector;
        plasmaWordIndexEntry indexEntry;
        plasmaCrawlLURL.Entry page;
        String[] urlcomps;
        String[] descrcomps;
        long ranking;
        long inc = 4096 * 4096;
        String queryhash;
        for (int i = 0; i < results.size(); i++) {
            // take out values from result array
            resultVector = (Object[]) results.get(i);
            indexEntry = (plasmaWordIndexEntry) resultVector[0];
            page = (plasmaCrawlLURL.Entry) resultVector[1];
            urlcomps = (String[]) resultVector[2];
            descrcomps = (String[]) resultVector[3];
            
            // apply pre-calculated order attributes
            ranking = 0;
            if (order[0] == O_QUALITY)  ranking  = 4096 * indexEntry.getQuality();
            else if (order[0] == O_AGE) ranking  = 4096 * indexEntry.getVirtualAge();
            if (order[1] == O_QUALITY)  ranking += indexEntry.getQuality();
            else if (order[1] == O_AGE) ranking += indexEntry.getVirtualAge();
            
            // apply 'common-sense' heuristic using references
            for (int j = 0; j < urlcomps.length; j++) if (commonSense.contains(urlcomps[j])) ranking += inc;
            for (int j = 0; j < descrcomps.length; j++) if (commonSense.contains(descrcomps[j])) ranking += inc;
            
            // apply query-in-result matching
            Set urlcomph = plasmaSearchQuery.words2hashes(urlcomps);
            Set descrcomph = plasmaSearchQuery.words2hashes(descrcomps);
            Iterator shi = searchhashes.iterator();
            while (shi.hasNext()) {
                queryhash = (String) shi.next();
                if (urlcomph.contains(queryhash)) ranking += 10 * inc;
                if (descrcomph.contains(queryhash)) ranking += 100 * inc;
            }
            
            // insert value
            //System.out.println("Ranking " + ranking + " for URL " + url.toString());
            pageAcc.put(serverCodings.encodeHex(ranking, 16) + indexEntry.getUrlHash(), page);
        }
        // flush memory
        results = null;
    }
    
    public Object[] getReferences(int count) {
        // create a list of words that had been computed by statistics over all
        // words that appeared in the url or the description of all urls
        return ref.getScores(count, false, 2, Integer.MAX_VALUE);
    }
    
    public void addScoreFiltered(String[] words) {
        String word;
        for (int i = 0; i < words.length; i++) {
            word = words[i].toLowerCase();
            if ((word.length() > 2) &&
            (!(stopwords.contains(word))) &&
            ("http_html_php_ftp_www_com_org_net_gov_edu_index_home_page_for_usage_the_and_".indexOf(word) < 0) &&
            (!(searchhashes.contains(plasmaWordIndexEntry.word2hash(word)))))
                ref.incScore(word);
        }
    }
    
    private void printSplitLog(String x, String[] y) {
        String s = "";
        for (int i = 0; i < y.length; i++) s = s + ", " + y[i];
        if (s.length() > 0) s = s.substring(2);
        System.out.println("Split '" + x + "' = {" + s + "}");
    }
    
    
}