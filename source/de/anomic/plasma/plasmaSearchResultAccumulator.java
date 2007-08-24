// plasmaSearchResultAccumulator.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.08.2007 on http://yacy.net
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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroException;
import de.anomic.net.URL;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class plasmaSearchResultAccumulator {

    private ArrayList hits;
    private Object[] references;
    
    public plasmaSearchResultAccumulator(
            plasmaSearchQuery theQuery,
            plasmaSearchProcessing process,
            plasmaSearchRankingProfile ranking,
            indexContainer pre,
            plasmaWordIndex wordIndex,
            TreeSet blueList,
            boolean overfetch) {

        hits = new ArrayList();
        
        // start url-fetch
        long postorderTime = process.getTargetTime(plasmaSearchProcessing.PROCESS_POSTSORT);
        //System.out.println("DEBUG: postorder-final (urlfetch) maxtime = " + postorderTime);
        long postorderLimitTime = (postorderTime < 0) ? Long.MAX_VALUE : (System.currentTimeMillis() + postorderTime);
        process.startTimer();
        plasmaSearchPostOrder acc = new plasmaSearchPostOrder(theQuery, ranking);
        
        indexRWIEntry rwientry;
        indexURLEntry page;
        indexURLEntry.Components comp;
        String pagetitle, pageurl, pageauthor;
        int minEntries = process.getTargetCount(plasmaSearchProcessing.PROCESS_POSTSORT);
        try {
            ordering: for (int i = 0; i < pre.size(); i++) {
                if ((System.currentTimeMillis() >= postorderLimitTime) || (acc.sizeFetched() >= ((overfetch) ? 4 : 1) * minEntries)) break;
                rwientry = new indexRWIEntry(pre.get(i));
                // load only urls if there was not yet a root url of that hash
                // find the url entry
                page = wordIndex.loadedURL.load(rwientry.urlHash(), rwientry);
                if (page != null) {
                    comp = page.comp();
                    pagetitle = comp.title().toLowerCase();
                    if (comp.url() == null) continue ordering; // rare case where the url is corrupted
                    pageurl = comp.url().toString().toLowerCase();
                    pageauthor = comp.author().toLowerCase();
                    
                    // check exclusion
                    if (plasmaSearchQuery.matches(pagetitle, theQuery.excludeHashes)) continue ordering;
                    if (plasmaSearchQuery.matches(pageurl, theQuery.excludeHashes)) continue ordering;
                    if (plasmaSearchQuery.matches(pageauthor, theQuery.excludeHashes)) continue ordering;
                    
                    // check url mask
                    if (!(pageurl.matches(theQuery.urlMask))) continue ordering;
                    
                    // check constraints
                    if ((!(theQuery.constraint.equals(plasmaSearchQuery.catchall_constraint))) &&
                        (theQuery.constraint.get(plasmaCondenser.flag_cat_indexof)) &&
                        (!(comp.title().startsWith("Index of")))) {
                        serverLog.logFine("PLASMA", "filtered out " + comp.url().toString());
                        // filter out bad results
                        Iterator wi = theQuery.queryHashes.iterator();
                        while (wi.hasNext()) wordIndex.removeEntry((String) wi.next(), page.hash());
                    } else if (theQuery.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                        if ((theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (page.laudio() > 0)) acc.addPage(page);
                        else if ((theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (page.lvideo() > 0)) acc.addPage(page);
                        else if ((theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (page.limage() > 0)) acc.addPage(page);
                        else if ((theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_APP) && (page.lapp() > 0)) acc.addPage(page);
                    } else {
                        acc.addPage(page);
                    }
                }
            }
        } catch (kelondroException ee) {
            serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
        }
        process.setYieldTime(plasmaSearchProcessing.PROCESS_URLFETCH);
        process.setYieldCount(plasmaSearchProcessing.PROCESS_URLFETCH, acc.sizeFetched());
        
        // start postsorting
        process.startTimer();
        acc.sortPages(true);
        process.setYieldTime(plasmaSearchProcessing.PROCESS_POSTSORT);
        process.setYieldCount(plasmaSearchProcessing.PROCESS_POSTSORT, acc.sizeOrdered());
        
        
        // apply filter
        process.startTimer();
        acc.removeRedundant();
        process.setYieldTime(plasmaSearchProcessing.PROCESS_FILTER);
        process.setYieldCount(plasmaSearchProcessing.PROCESS_FILTER, acc.sizeOrdered());
        
        // generate references
        references = acc.getReferences(16);
        
        // generate Result.Entry objects and optionally fetch snippets
        int i = 0;
        Entry entry;
        boolean includeSnippets = false;
        while ((acc.hasMoreElements()) && (i < theQuery.wantedResults)) {
            try {
                entry = new Entry(acc.nextElement(), wordIndex);
            } catch (RuntimeException e) {
                continue;
            }
            // check bluelist again: filter out all links where any
            // bluelisted word
            // appear either in url, url's description or search word
            // the search word was sorted out earlier
            /*
             * String s = descr.toLowerCase() + url.toString().toLowerCase();
             * for (int c = 0; c < blueList.length; c++) { if
             * (s.indexOf(blueList[c]) >= 0) return; }
             */
            if (includeSnippets) {
                entry.setSnippet(plasmaSnippetCache.retrieveTextSnippet(
                        entry.url(), theQuery.queryHashes, false,
                        entry.flags().get(plasmaCondenser.flag_cat_indexof), 260,
                        1000));
                // snippet =
                // snippetCache.retrieveTextSnippet(comp.url(),
                // query.queryHashes, false,
                // urlentry.flags().get(plasmaCondenser.flag_cat_indexof),
                // 260, 1000);
            } else {
                // snippet = null;
                entry.setSnippet(null);
            }
            i++;
            hits.add(entry);
        }

        /*
         * while ((acc.hasMoreElements()) && (((time + timestamp) <
         * System.currentTimeMillis()))) { urlentry = acc.nextElement();
         * urlstring = htmlFilterContentScraper.urlNormalform(urlentry.url());
         * descr = urlentry.descr();
         * 
         * addScoreForked(ref, gs, descr.split(" ")); addScoreForked(ref, gs,
         * urlstring.split("/")); }
         */

    }
    

    // filter
    public void applyFilter(
            plasmaSearchPostOrder acc) {

        
    }

    public int resultCount() {
        return hits.size();
    }
    
    public Entry resultEntry(int i) {
        return (Entry) hits.get(i);
    }
    
    public Object[] references() {
        return this.references;
    }
    
    public static class Entry {
        private indexURLEntry urlentry;
        private indexURLEntry.Components urlcomps; // buffer for components
        private String alternative_urlstring;
        private String alternative_urlname;
        private plasmaSnippetCache.TextSnippet snippet;
        
        public Entry(indexURLEntry urlentry, plasmaWordIndex wordIndex) {
            this.urlentry = urlentry;
            this.urlcomps = urlentry.comp();
            this.alternative_urlstring = null;
            this.alternative_urlname = null;
            this.snippet = null;
            String host = urlcomps.url().getHost();
            if (host.endsWith(".yacyh")) {
                // translate host into current IP
                int p = host.indexOf(".");
                String hash = yacySeed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
                yacySeed seed = yacyCore.seedDB.getConnected(hash);
                String filename = urlcomps.url().getFile();
                String address = null;
                if ((seed == null) || ((address = seed.getPublicAddress()) == null)) {
                    // seed is not known from here
                    try {
                        wordIndex.removeWordReferences(
                            plasmaCondenser.getWords(
                                ("yacyshare " +
                                 filename.replace('?', ' ') +
                                 " " +
                                 urlcomps.title()).getBytes(), "UTF-8").keySet(),
                                 urlentry.hash());
                        wordIndex.loadedURL.remove(urlentry.hash()); // clean up
                        throw new RuntimeException("index void");
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("parser failed: " + e.getMessage());
                    }
                }
                alternative_urlstring = "http://" + address + "/" + host.substring(0, p) + filename;
                alternative_urlname = "http://share." + seed.getName() + ".yacy" + filename;
                if ((p = alternative_urlname.indexOf("?")) > 0) alternative_urlname = alternative_urlname.substring(0, p);
            }
        }
        public String hash() {
            return urlentry.hash();
        }
        public URL url() {
            return urlcomps.url();
        }
        public kelondroBitfield flags() {
            return urlentry.flags();
        }
        public String urlstring() {
            return (alternative_urlstring == null) ? urlcomps.url().toNormalform(false, true) : alternative_urlstring;
        }
        public String urlname() {
            return (alternative_urlname == null) ? urlcomps.url().toNormalform(false, true) : alternative_urlname;
        }
        public String title() {
            return urlcomps.title();
        }
        public void setSnippet(plasmaSnippetCache.TextSnippet snippet) {
            this.snippet = snippet;
        }
        public plasmaSnippetCache.TextSnippet snippet() {
            return this.snippet;
        }
        public Date modified() {
            return urlentry.moddate();
        }
        public int filesize() {
            return urlentry.size();
        }
        public indexRWIEntry word() {
            return urlentry.word();
        }
        public boolean hasSnippet() {
            return false;
        }
        public plasmaSnippetCache.TextSnippet textSnippet() {
            return null;
        }
        public String resource() {
            // generate transport resource
            if ((snippet != null) && (snippet.exists())) {
                return urlentry.toString(snippet.getLineRaw());
            } else {
                return urlentry.toString();
            }
        }
    }
    
}
