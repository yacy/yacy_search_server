/**
 *  AccumulateSRURSS
 *  Copyright 2010 by Michael Peter Christen
 *  First released 06.01.2011 at http://yacy.net
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

package net.yacy.cora.services;

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
import org.apache.http.entity.mime.content.StringBody;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.protocol.http.HTTPConnector;

public class SearchSRURSS extends Thread implements SearchAccumulator {

    private final static int recordsPerSession = 10;
    
    final String urlBase;
    final String query;
    final long timeoutInit;
    final int maximumRecordsInit;
    final boolean verify;
    final boolean global;
    final Map<RSSMessage, List<Integer>> result;

    private final BlockingQueue<RSSMessage> results;
    
    public SearchSRURSS(
            final Map<RSSMessage, List<Integer>> result,
            final String query,
            final long timeoutInit,
            final String urlBase,
            final int maximumRecordsInit,
            final boolean verify,
            final boolean global) {
        this.results = new LinkedBlockingQueue<RSSMessage>();
        this.result = result;
        this.query = query;
        this.timeoutInit = timeoutInit;
        this.urlBase = urlBase;
        this.maximumRecordsInit = maximumRecordsInit;
        this.verify = verify;
        this.global = global;
    }
    
    public SearchSRURSS(
            final SearchHub search,
            final String urlBase,
            final int maximumRecordsInit,
            final boolean verify,
            final boolean global) {
        this.results = new LinkedBlockingQueue<RSSMessage>();
        this.result = search.getAccumulation();
        this.query = search.getQuery();
        this.timeoutInit = search.getTimeout();
        this.urlBase = urlBase;
        this.maximumRecordsInit = maximumRecordsInit;
        this.verify = verify;
        this.global = global;
    }
    
    public void run() {
        searchSRURSS(results, urlBase, query, timeoutInit, maximumRecordsInit, verify, global);
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
            final boolean verify,
            final boolean global) {
        Thread job = new Thread() {
            public void run() {
                int startRecord = 0;
                RSSMessage message;
                int maximumRecords = maximumRecordsInit;
                long timeout = timeoutInit;
                mainloop: while (timeout > 0 && maximumRecords > 0) {
                    long st = System.currentTimeMillis();
                    RSSFeed feed;
                    try {
                        feed = loadSRURSS(urlBase, query, timeout, startRecord, recordsPerSession, verify, global);
                    } catch (IOException e1) {
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
                            break innerloop;
                        }
                    }
                    startRecord += recordsPerSession;
                    timeout -= System.currentTimeMillis() - st;
                }
                try { queue.put(RSSMessage.POISON); } catch (InterruptedException e) {}
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
            boolean verify,
            boolean global) throws IOException {
        MultiProtocolURI uri = null;
        try {
            uri = new MultiProtocolURI(rssSearchServiceURL);
        } catch (MalformedURLException e) {
            throw new IOException("cora.Search failed asking peer '" + rssSearchServiceURL + "': bad url, " + e.getMessage());
        }
        
        // send request
        try {
            final LinkedHashMap<String,ContentBody> parts = new LinkedHashMap<String,ContentBody>();
            parts.put("query", new StringBody(query));
            parts.put("startRecord", new StringBody(Integer.toString(startRecord)));
            parts.put("maximumRecords", new StringBody(Long.toString(maximumRecords)));
            parts.put("verify", new StringBody(verify ? "true" : "false"));
            parts.put("resource", new StringBody(global ? "global" : "local"));
            final byte[] result = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI(rssSearchServiceURL), (int) timeout, uri.getHost(), parts);
            //String debug = new String(result); System.out.println("*** DEBUG: " + debug);
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
            throw new IOException("cora.Search error asking peer '" + uri.getHost() + "':" + e.toString());
        }
    }

}
