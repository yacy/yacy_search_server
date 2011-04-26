/**
 *  AccumulateSRURSS
 *  Copyright 2010 by Michael Peter Christen
 *  First released 06.01.2011 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
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

package net.yacy.cora.services.federated.opensearch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.http.entity.mime.content.ContentBody;

import de.anomic.crawler.CrawlProfile;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.services.federated.SearchAccumulator;
import net.yacy.cora.services.federated.SearchHub;

public class SRURSSConnector extends Thread implements SearchAccumulator {

    private final static int recordsPerSession = 100;
    
    final String urlBase;
    final String query;
    final long timeoutInit;
    final int maximumRecordsInit;
    final CrawlProfile.CacheStrategy verify;
    final boolean global;
    final Map<RSSMessage, List<Integer>> result;
    final String userAgent;

    private final BlockingQueue<RSSMessage> results;
    
    public SRURSSConnector(
            final Map<RSSMessage, List<Integer>> result,
            final String query,
            final long timeoutInit,
            final String urlBase,
            final int maximumRecordsInit,
            final CrawlProfile.CacheStrategy verify,
            final boolean global,
            final String userAgent) {
        this.results = new LinkedBlockingQueue<RSSMessage>();
        this.result = result;
        this.query = query;
        this.timeoutInit = timeoutInit;
        this.urlBase = urlBase;
        this.maximumRecordsInit = maximumRecordsInit;
        this.verify = verify;
        this.global = global;
        this.userAgent = userAgent;
    }
    
    public SRURSSConnector(
            final SearchHub search,
            final String urlBase,
            final int maximumRecordsInit,
            final CrawlProfile.CacheStrategy verify,
            final boolean global,
            final String userAgent) {
        this.results = new LinkedBlockingQueue<RSSMessage>();
        this.result = search.getAccumulation();
        this.query = search.getQuery();
        this.timeoutInit = search.getTimeout();
        this.urlBase = urlBase;
        this.maximumRecordsInit = maximumRecordsInit;
        this.verify = verify;
        this.global = global;
        this.userAgent = userAgent;
    }
    
    @Override
    public void run() {
        searchSRURSS(results, urlBase, query, timeoutInit, maximumRecordsInit, verify, global, userAgent);
        int p = 1;
        RSSMessage message;
        try {
            while ((message = results.poll(timeoutInit, TimeUnit.MILLISECONDS)) != RSSMessage.POISON) {
                if (message == null) break;
                List<Integer> m = result.get(message.getLink());
                if (m == null) m = new ArrayList<Integer>();
                m.add(new Integer(p++));
                result.put(message, m);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public static Thread searchSRURSS(
            final BlockingQueue<RSSMessage> queue,
            final String urlBase,
            final String query,
            final long timeoutInit,
            final int maximumRecordsInit,
            final CrawlProfile.CacheStrategy verify,
            final boolean global,
            final String userAgent) {
        Thread job = new Thread() {
            @Override
            public void run() {
                int startRecord = 0;
                RSSMessage message;
                int maximumRecords = maximumRecordsInit;
                long timeout = timeoutInit;
                mainloop: while (timeout > 0 && maximumRecords > 0) {
                    long st = System.currentTimeMillis();
                    RSSFeed feed;
                    try {
                        feed = loadSRURSS(urlBase, query, timeout, startRecord, recordsPerSession, verify, global, userAgent);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        break mainloop;
                    }
                    if (feed == null || feed.isEmpty()) break mainloop;
                    maximumRecords -= feed.size();
                    innerloop: while (!feed.isEmpty()) {
                        message = feed.pollMessage();
                        if (message == null) break innerloop;
                        try {
                            queue.put(message);
                        } catch (InterruptedException e) {
                            e.printStackTrace(); 
                            break innerloop;
                        }
                    }
                    startRecord += recordsPerSession;
                    timeout -= System.currentTimeMillis() - st;
                }
                try { queue.put(RSSMessage.POISON); } catch (InterruptedException e) { e.printStackTrace(); }
            }
        };
        job.start();
        return job;
    }
    
    /**
     * send a query to a yacy public search interface
     * @param rssSearchServiceURL the target url base (everything before the ? that follows the SRU request syntax properties). can null, then the local peer is used
     * @param query the query as string
     * @param startRecord number of first record
     * @param maximumRecords maximum number of records
     * @param verify if true, result entries are verified using the snippet fetch (slow); if false simply the result is returned
     * @param global if true also search results from other peers are included
     * @param timeout milliseconds that are waited at maximum for a search result
     * @return
     */
    public static RSSFeed loadSRURSS(
            String rssSearchServiceURL,
            String query,
            long timeout,
            int startRecord,
            int maximumRecords,
            CrawlProfile.CacheStrategy cacheStrategy,
            boolean global,
            String userAgent) throws IOException {
        MultiProtocolURI uri = null;
        try {
            uri = new MultiProtocolURI(rssSearchServiceURL);
        } catch (MalformedURLException e) {
            throw new IOException("cora.Search failed asking peer '" + rssSearchServiceURL + "': bad url, " + e.getMessage());
        }
        
        // send request
        byte[] result = new byte[0];
        try {
            final LinkedHashMap<String,ContentBody> parts = new LinkedHashMap<String,ContentBody>();
            parts.put("query", UTF8.StringBody(query));
            parts.put("startRecord", UTF8.StringBody(Integer.toString(startRecord)));
            parts.put("maximumRecords", UTF8.StringBody(Long.toString(maximumRecords)));
            parts.put("verify", cacheStrategy == null ? UTF8.StringBody("false") : UTF8.StringBody(cacheStrategy.toName()));
            parts.put("resource", UTF8.StringBody(global ? "global" : "local"));
            parts.put("nav", UTF8.StringBody("none"));
            // result = HTTPConnector.getConnector(userAgent == null ? MultiProtocolURI.yacybotUserAgent : userAgent).post(new MultiProtocolURI(rssSearchServiceURL), (int) timeout, uri.getHost(), parts);
            final HTTPClient httpClient = new HTTPClient(userAgent == null ? ClientIdentification.getUserAgent() : userAgent, (int) timeout);
            result = httpClient.POSTbytes(new MultiProtocolURI(rssSearchServiceURL), uri.getHost(), parts, false);
            
            final RSSReader reader = RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, result);
            if (reader == null) {
                throw new IOException("cora.Search failed asking peer '" + uri.getHost() + "': probably bad response from remote peer (1), reader == null");
            }
            final RSSFeed feed = reader.getFeed();
            if (feed == null) {
                // case where the rss reader does not understand the content
                throw new IOException("cora.Search failed asking peer '" + uri.getHost() + "': probably bad response from remote peer (2)");
            }
            return feed;
        } catch (final IOException e) {
            String debug = UTF8.String(result); System.out.println("*** DEBUG: " + debug);
            throw new IOException("cora.Search error asking peer '" + uri.getHost() + "':" + e.toString());
        }
    }

}
