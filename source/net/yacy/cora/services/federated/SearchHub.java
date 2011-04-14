/**
 *  Search
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.05.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General private
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.services.federated;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.crawler.CrawlProfile;

import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.services.federated.opensearch.SRURSSConnector;
import net.yacy.cora.storage.ConcurrentScoreMap;
import net.yacy.cora.storage.ScoreMap;

public class SearchHub {

    private static final String[] SRURSSServicesList = {
        //"http://192.168.1.51:8000/yacysearch.rss"//,
        "http://127.0.0.1:8008/yacysearch.rss"//,
        /*
        "http://yacy.dyndns.org:8000/yacysearch.rss",
        "http://yacy.caloulinux.net:8085/yacysearch.rss",
        "http://algire.dyndns.org:8085/yacysearch.rss",
        "http://breyvogel.dyndns.org:8002/yacysearch.rss"*/
    };

    public final static SearchHub EMPTY = new SearchHub("", 0);
    
    private String query;
    private int timeout;
    private List<SearchAccumulator> threads;
    private Map<RSSMessage, List<Integer>> result;
    
    public SearchHub(final String query, final int timeout) {
        this.query = query;
        this.timeout = timeout;
        this.threads = new ArrayList<SearchAccumulator>();
        this.result = new ConcurrentHashMap<RSSMessage, List<Integer>>();
    }

    /**
     * get the result of the accumulation
     * @return
     */
    public Map<RSSMessage, List<Integer>> getAccumulation() {
        return this.result;
    }

    /**
     * add an accumulator to the list of accumulation theads.
     * this is mainly used for awaitTermination() and isTerminated()
     * @param a
     */
    public void addAccumulator(SearchAccumulator a) {
        this.threads.add(a);
    }
    
    /**
     * get the original query string
     * @return
     */
    public String getQuery() {
        return this.query;
    }
    
    /**
     * get the given time-out of the search request
     * @return
     */
    public int getTimeout() {
        return this.timeout;
    }
    
    /**
     * get the list of search results as scored map.
     * The results are combined using their appearance positions.
     * Every time this method is called the list is re-computed to reflect the latest results
     * @return a score map of urls
     */
    public ScoreMap<String> getResults() {
        ScoreMap<String> scores = new ConcurrentScoreMap<String>();
        int m = threads.size();
        for (Map.Entry<RSSMessage, List<Integer>> entry: this.result.entrySet()) {
            int a = 0;
            for (Integer i : entry.getValue()) a += i.intValue();
            scores.inc(entry.getKey().getLink(), a * m / entry.getValue().size());
        }
        return scores;
    }

    /**
     * wait until all accumulation threads have terminated
     */
    public void waitTermination() {
        for (SearchAccumulator t: threads) try {t.join();} catch (InterruptedException e) {}
    }
    
    /**
     * return true if all accumulation threads have terminated
     * @return
     */
    public boolean isTerminated() {
        for (SearchAccumulator t: threads) if (t.isAlive()) return false;
        return true;
    }
    
    /**
     * return a hash code of the search hub.
     * This is computed using only the query string because that identifies the object
     */
    @Override
    public int hashCode() {
        return query.hashCode();
    }
    
    /**
     * test method to add a list of SRU RSS services.
     * such services are provided by YaCy peers
     * @param search
     * @param rssServices
     * @param count
     * @param verify
     * @param global
     */
    public static void addSRURSSServices(SearchHub search, String[] rssServices, int count, CrawlProfile.CacheStrategy verify, boolean global, String userAgent) {
        for (String service: rssServices) {
            SRURSSConnector accumulator = new SRURSSConnector(search, service, count, verify, global, userAgent);
            accumulator.start();
            search.addAccumulator(accumulator);
        }
    }
    
    public static void main(String[] args) {
        HTTPClient.setDefaultUserAgent("searchhub");
        HTTPClient.initConnectionManager();
        
        StringBuilder sb = new StringBuilder();
        for (String s: args) sb.append(s).append(' ');
        String query = sb.toString().trim();
        SearchHub search = new SearchHub(query, 10000);
        addSRURSSServices(search, SRURSSServicesList, 100, CrawlProfile.CacheStrategy.CACHEONLY, false, "searchhub");
        try {Thread.sleep(100);} catch (InterruptedException e1) {}
        search.waitTermination();
        ScoreMap<String> result = search.getResults();
        Iterator<String> i = result.keys(true);
        String u;
        while (i.hasNext()) {
            u = i.next();
            System.out.println("[" + result.get(u) + "] " + u);
        }
        try {HTTPClient.closeConnectionManager();} catch (InterruptedException e) { e.printStackTrace(); }
    }
}
