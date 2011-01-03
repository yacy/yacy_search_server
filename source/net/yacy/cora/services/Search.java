/**
 *  Search
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.05.2010 at http://yacy.net
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.protocol.http.HTTPConnector;
import net.yacy.cora.protocol.http.LinkExtractor;
import net.yacy.cora.storage.ScoreMap;

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.StringBody;

public class Search extends Thread {

    private final static int recordsPerSession = 10;

    public static final String[] SRURSSServicesList = {
        "http://yacy.dyndns.org:8000/yacysearch.rss",
        "http://yacy.caloulinux.net:8085/yacysearch.rss",
        "http://algire.dyndns.org:8085/yacysearch.rss",
        "http://breyvogel.dyndns.org:8002/yacysearch.rss"
    };
    
    public static final String[] genericServicesList = {
        "http://www.scroogle.org/cgi-bin/nbbw.cgi?Gw=$&n=2",
        "http://blekko.com/ws/$+/rss",
        "http://www.bing.com/search?q=$&format=rss",
        "http://search.twitter.com/search.atom?q=$"
    };

    public static Thread accumulateSRURSS(
            final String urlBase,
            final String query,
            final long timeoutInit,
            final int maximumRecordsInit,
            final boolean verify,
            final boolean global,
            final Map<MultiProtocolURI, List<Integer>> result) {
        Thread t = new Thread() {
            BlockingQueue<RSSMessage> results = new LinkedBlockingQueue<RSSMessage>();
            public void run() {
                searchSRURSS(urlBase, query, timeoutInit, maximumRecordsInit, verify, global, results);
                int p = 1;
                RSSMessage message;
                try {
                    while ((message = results.poll(timeoutInit, TimeUnit.MILLISECONDS)) != RSSMessage.POISON) {
                        MultiProtocolURI uri;
                        if (message == null) break;
                        try {
                            uri = new MultiProtocolURI(message.getLink());
                            List<Integer> m = result.get(uri);
                            if (m == null) m = new ArrayList<Integer>();
                            m.add(new Integer(p++));
                            result.put(uri, m);
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
        return t;
    }
    
    public static Thread searchSRURSS(
            final String urlBase,
            final String query,
            final long timeoutInit,
            final int maximumRecordsInit,
            final boolean verify,
            final boolean global,
            final BlockingQueue<RSSMessage> queue) {
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

    public static Thread accumulateGeneric(
            String query,
            String service,
            final Map<MultiProtocolURI, List<Integer>> result,
            final int timeout) {
        query = query.replace(' ', '+');
        final String servicePatched = service.replaceAll("\\$", query);
        Thread t = new Thread() {
            public void run() {
                try {
                    MultiProtocolURI[] sr = loadGeneric(new MultiProtocolURI(servicePatched), timeout);
                    int p = 1;
                    for (MultiProtocolURI u: sr) {
                        List<Integer> m = result.get(u);
                        if (m == null) m = new ArrayList<Integer>();
                        m.add(new Integer(p++));
                        result.put(u, m);
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
        return t;
    }
    
    private static MultiProtocolURI[] loadGeneric(MultiProtocolURI uri, long timeout) throws IOException {
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, MultiProtocolURI.yacybotUserAgent);
        final HTTPClient client = new HTTPClient();
        client.setTimout((int) timeout);
        client.setHeader(requestHeader.entrySet());
        byte[] result = client.GETbytes(uri.toString());
        client.finish();
        if (client.getStatusCode() != 200) {
            throw new IOException("Server returned status: " + client.getHttpResponse().getStatusLine());
        }
        if (result == null) throw new IOException("cora.Search error asking peer '" + uri.getHost() + "': null");
        LinkExtractor le = new LinkExtractor(Pattern.compile(".*" + uri.getHost() + ".*"));
        le.scrape(new String(result));
        MultiProtocolURI[] links = le.getLinks();
        return links;
    }
    
    public static RSSFeed links2feed(Set<MultiProtocolURI> links, String source) {
        RSSFeed feed = new RSSFeed(Integer.MAX_VALUE);
        String u;
        RSSMessage message;
        for (MultiProtocolURI uri: links) {
            u = uri.toNormalform(true, false);
            message = new RSSMessage(u, "", u);
            message.setAuthor(source);
            feed.addMessage(message);
        }
        return feed;
    }

    private Map<MultiProtocolURI, List<Integer>> result;
    private String query;
    private int count;
    private String[] yacyServices, rssServices, genericServices;
    private List<Thread> threads;
    
    public Search(String query, int count, String[] rssServices, String[] genericServices) {
        this.result = new ConcurrentHashMap<MultiProtocolURI, List<Integer>>();
        this.query = query;
        this.count = count;
        this.yacyServices = yacyServices;
        this.rssServices = rssServices;
        this.genericServices = genericServices;
        this.threads = new ArrayList<Thread>();
    }

    public void run() {
        for (String service: this.rssServices) threads.add(accumulateSRURSS(service, this.query, 10000, this.count, false, true, this.result));
        for (String service: this.genericServices) threads.add(accumulateGeneric(this.query, service, this.result, 10000));
    }
    
    public ScoreMap<MultiProtocolURI> getResults() {
        ScoreMap<MultiProtocolURI> scores = new ScoreMap<MultiProtocolURI>();
        int m = this.rssServices.length + this.genericServices.length;
        for (Map.Entry<MultiProtocolURI, List<Integer>> entry: this.result.entrySet()) {
            int a = 0;
            for (Integer i : entry.getValue()) a += i.intValue();
            scores.inc(entry.getKey(), a * m / entry.getValue().size());
        }
        return scores;
    }

    public void waitTermination() {
        for (Thread t: threads) try {t.join();} catch (InterruptedException e) {}
    }
    
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (String s: args) sb.append(s).append(' ');
        String query = sb.toString().trim();
        Search search = new Search(query, 100, SRURSSServicesList, genericServicesList);
        search.start();
        try {Thread.sleep(100);} catch (InterruptedException e1) {}
        search.waitTermination();
        ScoreMap<MultiProtocolURI> result = search.getResults();
        Iterator<MultiProtocolURI> i = result.keys(true);
        MultiProtocolURI u;
        while (i.hasNext()) {
            u = i.next();
            System.out.println("[" + result.get(u) + "] " + u.toNormalform(true, false));
        }
        try {HTTPClient.closeConnectionManager();} catch (InterruptedException e) {}
    }
}
