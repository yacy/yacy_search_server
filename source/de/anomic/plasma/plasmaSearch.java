// plasmaSearch.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 11.06.2004
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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;

public final class plasmaSearch {

    public static final char O_QUALITY = 'q';
    public static final char O_AGE     = 'a';
    public static final String splitrex = " |/|\\(|\\)|-|\\:|_|\\.|,|\\?|!|'|" + '"';
    
    private final plasmaCrawlLURL urlStore;
    private final plasmaWordIndex wordIndex;

    public plasmaSearch(plasmaCrawlLURL urlStore, plasmaWordIndex wordIndex) {
	this.urlStore = urlStore;
        this.wordIndex = wordIndex;
    }

    public static int calcVirtualAge(Date modified) {
	// this calculates a virtual age from a given date
	// the purpose is to have an age in days of a given modified date
	// from a fixed standpoint in the past
	//if (modified == null) return 0;
	// this is milliseconds. we need days
	// one day has 60*60*24 seconds = 86400 seconds
	// we take mod 64**3 = 262144, this is the mask of the storage
	return (int) ((modified.getTime() / 86400000) % 262144);
    }
    
    public void addWords(plasmaWordIndexEntryContainer container) {
        wordIndex.addEntries(container);
    }
        
    public int addPageIndex(URL url, String urlHash, Date urlModified, plasmaCondenser condenser,
			 String language, char doctype) {
        // this is called by the switchboard to put in a new page into the index
	// use all the words in one condenser object to simultanous create index entries
	int age = calcVirtualAge(urlModified);
	int quality = 0;
	try {
	    quality = Integer.parseInt(condenser.getAnalysis().getProperty("INFORMATION_VALUE","0"), 16);
	} catch (NumberFormatException e) {
	    System.out.println("INTERNAL ERROR WITH CONDENSER.INFORMATION_VALUE: " + e.toString() + ": in URL " + url.toString());
	}

        // iterate over all words
	Iterator i = condenser.getWords().iterator();
	String word;
	int count;
	plasmaWordIndexEntry entry;
	String wordHash;
	int p = 0;
	while (i.hasNext()) {
	    word = (String) i.next();
	    count = condenser.wordCount(word);
	    //if ((s.length() > 4) && (c > 1)) System.out.println("# " + s + ": " + c);
	    wordHash = plasmaWordIndexEntry.word2hash(word);
	    entry = new plasmaWordIndexEntry(urlHash, count, p++, 0, 0,
                                         age, quality, language, doctype, true);
	    wordIndex.addEntries(plasmaWordIndexEntryContainer.instantContainer(wordHash, System.currentTimeMillis(), entry));
	}
	//System.out.println("DEBUG: plasmaSearch.addPageIndex: added " + condenser.getWords().size() + " words, flushed " + c + " entries");
        return condenser.getWords().size();
    }
    

    public static Set words2hashes(String[] words) {
	HashSet hashes = new HashSet();
        for (int i = 0; i < words.length; i++) hashes.add(plasmaWordIndexEntry.word2hash(words[i]));
        return hashes;
    }

    public static Set words2hashes(Set words) {
	Iterator i = words.iterator();
	HashSet hashes = new HashSet();
	while (i.hasNext()) hashes.add(plasmaWordIndexEntry.word2hash((String) i.next()));
        return hashes;
    }

    public plasmaWordIndexEntity searchWords(Set words, long time) throws IOException {
	// search for the set of words and return an array of urlEntry elements
        return searchHashes(words2hashes(words), time);
    }

    public plasmaWordIndexEntity searchHashes(Set hashes, long time) throws IOException {
        // search for the set of hashes and return an array of urlEntry elements
        
        long stamp = System.currentTimeMillis();
        TreeMap map = new TreeMap();
        String singleHash;
        plasmaWordIndexEntity singleResult;
        Iterator i = hashes.iterator();
        while (i.hasNext()) {
            // get next hash:
            singleHash = (String) i.next();
            
            // retrieve index
            singleResult = wordIndex.getEntity(singleHash, true);
            
            // check result
            if ((singleResult == null) || (singleResult.size() == 0)) return new plasmaWordIndexEntity(null); // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(serverCodings.enhancedCoder.encodeHex(singleResult.size(), 8) + singleHash, singleResult);
        }
        
        // check if there is any result
        if (map.size() == 0) return new plasmaWordIndexEntity(null); // no result, nothing found
        
        // the map now holds the search results in order of number of hits per word
        // we now must pairwise build up a conjunction of these sets
        String k = (String) map.firstKey(); // the smallest, which means, the one with the least entries
        plasmaWordIndexEntity searchA, searchB, searchResult = (plasmaWordIndexEntity) map.remove(k);
        while ((map.size() > 0) && (searchResult.size() > 0) && (time > 0)) {
            // take the first element of map which is a result and combine it with result
            k = (String) map.firstKey(); // the next smallest...
            time -= (System.currentTimeMillis() - stamp); stamp = System.currentTimeMillis();
	    searchA = searchResult;
	    searchB = (plasmaWordIndexEntity) map.remove(k);
            searchResult = joinConstructive(searchA, searchB, 2 * time / (map.size() + 1));
	    // close the input files/structures
	    if (searchA != searchResult) searchA.close();
	    if (searchB != searchResult) searchB.close();
        }
        searchA = null; // free resources
	searchB = null; // free resources

        // in 'searchResult' is now the combined search result
        if (searchResult.size() == 0) return new plasmaWordIndexEntity(null);
        return searchResult;
    }

    private static int log2(int x) {
        int l = 0;
        while (x > 0) {x = x >> 1; l++;}
        return l;
    }
        
    private plasmaWordIndexEntity joinConstructive(plasmaWordIndexEntity i1, plasmaWordIndexEntity i2, long time) throws IOException {
        if ((i1 == null) || (i2 == null)) return null;
	if ((i1.size() == 0) || (i2.size() == 0)) return new plasmaWordIndexEntity(null);

	// decide which method to use
	int high = ((i1.size() > i2.size()) ? i1.size() : i2.size());
	int low  = ((i1.size() > i2.size()) ? i2.size() : i1.size());
	int stepsEnum = 10 * (high + low - 1);
	int stepsTest = 12 * log2(high) * low;

	// start most efficient method
	if (stepsEnum > stepsTest) {
	    if (i1.size() < i2.size())
                return joinConstructiveByTest(i1, i2, time);
            else
                return joinConstructiveByTest(i2, i1, time);
	} else {
	    return joinConstructiveByEnumeration(i1, i2, time);
        }
    }
    
    private plasmaWordIndexEntity joinConstructiveByTest(plasmaWordIndexEntity small, plasmaWordIndexEntity large, long time) throws IOException {
        System.out.println("DEBUG: JOIN METHOD BY TEST");
        plasmaWordIndexEntity conj = new plasmaWordIndexEntity(null); // start with empty search result
        Enumeration se = small.elements(true);
        plasmaWordIndexEntry ie;
        long stamp = System.currentTimeMillis();
        try {
            while ((se.hasMoreElements()) && ((System.currentTimeMillis() - stamp) < time)) {
                ie = (plasmaWordIndexEntry) se.nextElement();
                if (large.contains(ie)) conj.addEntry(ie);
            }
        }  catch (kelondroException e) {
            serverLog.logError("PLASMA", "joinConstructiveByTest: Database corrupt (" + e.getMessage() + "), deleting index");
            small.deleteComplete();
            return conj;
        }
        return conj;
    }
    
    private plasmaWordIndexEntity joinConstructiveByEnumeration(plasmaWordIndexEntity i1, plasmaWordIndexEntity i2, long time) throws IOException {
        System.out.println("DEBUG: JOIN METHOD BY ENUMERATION");
        plasmaWordIndexEntity conj = new plasmaWordIndexEntity(null); // start with empty search result
        Enumeration e1 = i1.elements(true);
        Enumeration e2 = i2.elements(true);
        int c;
        if ((e1.hasMoreElements()) && (e2.hasMoreElements())) {
            plasmaWordIndexEntry ie1;
            plasmaWordIndexEntry ie2;
            try {
                ie1 = (plasmaWordIndexEntry) e1.nextElement();
            }  catch (kelondroException e) {
                serverLog.logError("PLASMA", "joinConstructiveByEnumeration: Database corrupt 1 (" + e.getMessage() + "), deleting index");
                i1.deleteComplete();
                return conj;
            }
            try {
                ie2 = (plasmaWordIndexEntry) e2.nextElement();
            }  catch (kelondroException e) {
                serverLog.logError("PLASMA", "joinConstructiveByEnumeration: Database corrupt 2 (" + e.getMessage() + "), deleting index");
                i2.deleteComplete();
                return conj;
            }
            long stamp = System.currentTimeMillis();
            while ((System.currentTimeMillis() - stamp) < time) {
                c = ie1.getUrlHash().compareTo(ie2.getUrlHash());
                if (c < 0) {
                    try {
                        if (e1.hasMoreElements()) ie1 = (plasmaWordIndexEntry) e1.nextElement(); else break;
                    } catch (kelondroException e) {
                        serverLog.logError("PLASMA", "joinConstructiveByEnumeration: Database 1 corrupt (" + e.getMessage() + "), deleting index");
                        i1.deleteComplete();
                        break;
                    }
                } else if (c > 0) {
                    try {
                        if (e2.hasMoreElements()) ie2 = (plasmaWordIndexEntry) e2.nextElement(); else break;
                    } catch (kelondroException e) {
                        serverLog.logError("PLASMA", "joinConstructiveByEnumeration: Database 2 corrupt (" + e.getMessage() + "), deleting index");
                        i2.deleteComplete();
                        break;
                    }
                } else {
                    // we have found the same urls in different searches!
                    conj.addEntry(ie1);
                    try {
                        if (e1.hasMoreElements()) ie1 = (plasmaWordIndexEntry) e1.nextElement(); else break;
                    } catch (kelondroException e) {
                        serverLog.logError("PLASMA", "joinConstructiveByEnumeration: Database 1 corrupt (" + e.getMessage() + "), deleting index");
                        i1.deleteComplete();
                        break;
                    }
                    try {
                        if (e2.hasMoreElements()) ie2 = (plasmaWordIndexEntry) e2.nextElement(); else break;
                    }  catch (kelondroException e) {
                        serverLog.logError("PLASMA", "joinConstructiveByEnumeration: Database 2 corrupt (" + e.getMessage() + "), deleting index");
                        i2.deleteComplete();
                        break;
                    }
                }
            }
        }
        return conj;
    }
    
    public plasmaSearch.result order(plasmaWordIndexEntity searchResult, Set searchhashes, Set stopwords, char[] priority, long maxTime, int minEntries) throws IOException {
	// we collect the urlhashes from it and construct a List with urlEntry objects
	// attention: if minEntries is too high, this method will not terminate within the maxTime

	plasmaSearch.result acc = new result(searchhashes, stopwords, priority);
	if (searchResult == null) return acc; // strange case where searchResult is not proper: acc is then empty

	Enumeration e = searchResult.elements(true);
	plasmaWordIndexEntry entry;
        long startCreateTime = System.currentTimeMillis();
	try {
	    while ((e.hasMoreElements()) &&
		   ((acc.sizeFetched() < minEntries) || (System.currentTimeMillis() - startCreateTime < maxTime))) {
		entry = (plasmaWordIndexEntry) e.nextElement();
		acc.addResult(entry);
	    }
	} catch (kelondroException ee) {
	    serverLog.logError("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage());
	    ee.printStackTrace();
	}
        long startSortTime = System.currentTimeMillis();
        acc.sortResults();
        System.out.println("plasmaSearch.order: minEntries = " + minEntries + ", effectiveEntries = " + acc.sizeOrdered() + ", demanded Time = " + maxTime + ", effectiveTime = " + (System.currentTimeMillis() - startCreateTime) + ", createTime = " + (startSortTime - startCreateTime) + ", sortTime = " + (System.currentTimeMillis() - startSortTime));
	return acc;
    }
    
    public class result /*implements Enumeration*/ {
        
        TreeMap pageAcc;            // key = order hash; value = plasmaLURL.entry
        kelondroMScoreCluster ref;  // reference score computation for the commonSense heuristic
        Set searchhashes;           // hashes that are searched here
        Set stopwords;              // words that are excluded from the commonSense heuristic
        char[] order;               // order of heuristics
        ArrayList results;          // this is a buffer for plasmaWordIndexEntry + plasmaCrawlLURL.entry - objects
        
        public result(Set searchhashes, Set stopwords, char[] order) {
            this.pageAcc = new TreeMap();
            ref = new kelondroMScoreCluster();
            this.searchhashes = searchhashes;
            this.stopwords = stopwords;
            this.order = order;
            this.results = new ArrayList();
        }
        
        public result cloneSmart() {
            // clones only the top structure
            result theClone = new result(this.searchhashes, this.stopwords, this.order);
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

        protected void addResult(plasmaWordIndexEntry indexEntry) {
            // this does 3 things:
            // 1. simply store indexEntry and page to a cache
            // 2. calculate references and store them to cache
            // 2. add reference to reference sorting table
            
            // find the url entry
            plasmaCrawlLURL.Entry page = urlStore.getEntry(indexEntry.getUrlHash());
            
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
                Set urlcomph = words2hashes(urlcomps);
                Set descrcomph = words2hashes(descrcomps);
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
        
        private void addScoreFiltered(String[] words) {
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
    
}
