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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.net.URL;
import de.anomic.server.serverCodings;

public final class plasmaSearchPostOrder {
    
    private TreeMap pageAcc;            // key = order hash; value = plasmaLURL.entry
    private kelondroMScoreCluster ref;  // reference score computation for the commonSense heuristic
    private ArrayList results;          // this is a buffer for plasmaWordIndexEntry + plasmaCrawlLURL.entry - objects
    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    
    public plasmaSearchPostOrder(plasmaSearchQuery query, plasmaSearchRankingProfile ranking) {
        this.pageAcc = new TreeMap();
        this.ref = new kelondroMScoreCluster();
        this.results = new ArrayList();
        this.query = query;
        this.ranking = ranking;
    }
    
    public plasmaSearchPostOrder cloneSmart() {
        // clones only the top structure
        plasmaSearchPostOrder theClone = new plasmaSearchPostOrder(this.query, this.ranking);
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
    
    public indexURLEntry nextElement() {
        Object top = pageAcc.firstKey();
        //System.out.println("postorder-key: " + ((String) top));
        return (indexURLEntry) pageAcc.remove(top);
    }
    
    protected void addPage(indexURLEntry page, Long preranking) {
        
        // take out relevant information for reference computation
        indexURLEntry.Components comp = page.comp();
        if ((comp.url() == null) || (comp.title() == null)) return;
        String[] urlcomps = htmlFilterContentScraper.urlComps(comp.url().toNormalform(true, true)); // word components of the url
        String[] descrcomps = comp.title().toLowerCase().split(htmlFilterContentScraper.splitrex); // words in the description
        
        // store everything
        results.add(new Object[] {page, urlcomps, descrcomps, preranking});
        
        // add references
        addScoreFiltered(urlcomps);
        addScoreFiltered(descrcomps);
    }
    
    protected void sortPages(boolean postsort) {
        // finally sort the results
        
        // create a commonSense - set that represents a set of words that is
        // treated as 'typical' for this search request
        Object[] references = getReferences(16);
        Set commonSense = new HashSet();
        for (int i = 0; i < references.length; i++) commonSense.add(references[i]);
        
        Object[] resultVector;
        indexURLEntry page;
        long ranking;
        for (int i = 0; i < results.size(); i++) {
            // take out values from result array
            resultVector = (Object[]) results.get(i);
            page = (indexURLEntry) resultVector[0];
            
            // calculate ranking
            if (postsort)
                ranking = this.ranking.postRanking(
                            ((Long) resultVector[3]).longValue(),
                            query,
                            commonSense,
                            (String[]) resultVector[1],
                            (String[]) resultVector[2],
                            page
                            );
            else
                ranking = ((Long) resultVector[3]).longValue();
            
            // insert value
            //System.out.println("Ranking " + ranking + ", YBR-" + plasmaSearchPreOrder.ybr(indexEntry.getUrlHash()) + " for URL " + page.url());
            pageAcc.put(serverCodings.encodeHex(Long.MAX_VALUE - ranking, 16) + page.hash(), page);
        }
        
        // flush memory
        results = null;
    }
    
    public void removeRedundant() {
        // remove all urls from the pageAcc structure that occur double by specific redundancy rules
        // a link is redundant, if a sub-path of the url is cited before. redundant urls are removed
        // we find redundant urls by iteration over all elements in pageAcc
        Iterator i = pageAcc.entrySet().iterator();
        HashMap paths = new HashMap(); // a url-subpath to pageAcc-key relation
        Map.Entry entry;
        
        // first scan all entries and find all urls that are referenced
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            paths.put(((indexURLEntry) entry.getValue()).comp().url().toNormalform(true, true), entry.getKey());
            //if (path != null) path = shortenPath(path);
            //if (path != null) paths.put(path, entry.getKey());
        }
        
        // now scan the pageAcc again and remove all redundant urls
        i = pageAcc.entrySet().iterator();
        String shorten;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            shorten = shortenPath(((indexURLEntry) entry.getValue()).comp().url().toNormalform(true, true));
            // scan all subpaths of the url
            while (shorten != null) {
                if (pageAcc.size() <= query.wantedResults) break;
                if (paths.containsKey(shorten)) {
                    //System.out.println("deleting path from search result: " + path + " is redundant to " + shorten);
                    try {
                        i.remove();
                    } catch (IllegalStateException e) {
                        
                    }
                }
                shorten = shortenPath(shorten);
            }
        }
    }
    
    private static String shortenPath(String path) {
	int pos = path.lastIndexOf('/');
        if (pos < 0) return null;
        return path.substring(0, pos);
    }
    /*
    private static String urlPath(URL url) {
        String port = ((url.getPort() < 0) ? "" : ":" + url.getPort());
        String path = url.getPath();
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int pos = path.lastIndexOf('/');
        if ((pos >= 0) && (path.length() > pos + 5) && (path.substring(pos + 1).toLowerCase().startsWith("index"))) {
            path = path.substring(0, pos);
        }
        return url.getHost() + port + path;
    }
    */
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
                ("http_html_php_ftp_www_com_org_net_gov_edu_index_home_page_for_usage_the_and_".indexOf(word) < 0) &&
                (!(query.queryHashes.contains(plasmaCondenser.word2hash(word)))))
                ref.incScore(word);
        }
    }
    
    /*
    private void printSplitLog(String x, String[] y) {
        String s = "";
        for (int i = 0; i < y.length; i++) s = s + ", " + y[i];
        if (s.length() > 0) s = s.substring(2);
        System.out.println("Split '" + x + "' = {" + s + "}");
    }
    */
    
    public static void main(String[] args) {
        URL[] urls = new URL[10];
        try {
            urls[0] = new URL("http://www.yacy.net");
            urls[1] = new URL("http://www.yacy.de/");
            urls[2] = new URL("http://yacy.net/");
            urls[3] = new URL("http://www.yacy.net:80/");
            urls[4] = new URL("http://yacy.net:80/");
            urls[5] = new URL("http://www.yacy.net/index.html");
            urls[6] = new URL("http://www.yacy.net/yacy");
            urls[7] = new URL("http://www.yacy.net/yacy/");
            urls[8] = new URL("http://www.yacy.net/yacy/index.html");
            urls[9] = new URL("ftp://www.yacy.net/yacy/index.html");
            String hash, fill;
            String[] paths1 = new String[urls.length]; for (int i = 0; i < urls.length; i++) {
                fill = ""; for (int j = 0; j < 35 - urls[i].toString().length(); j++) fill +=" ";
                paths1[i] = urls[i].toNormalform(true, true);
                hash = plasmaURL.urlHash(urls[i]);
                System.out.println("paths1[" + urls[i] + fill +"] = " + hash + ", typeID=" + plasmaURL.flagTypeID(hash) + ", tldID=" + plasmaURL.flagTLDID(hash) + ", lengthID=" + plasmaURL.flagLengthID(hash) + " / " + paths1[i]);
            }
            String[] paths2 = new String[urls.length]; for (int i = 0; i < urls.length; i++) {
                fill = ""; for (int j = 0; j < 35 - urls[i].toString().length(); j++) fill +=" ";
                paths2[i] = shortenPath(paths1[i]);
                hash = plasmaURL.urlHash(urls[i]);
                System.out.println("paths2[" + urls[i] + fill + "] = " + hash + ", typeID=" + plasmaURL.flagTypeID(hash) + ", tldID=" + plasmaURL.flagTLDID(hash) + ", lengthID=" + plasmaURL.flagLengthID(hash) + " / " + paths2[i]);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

}