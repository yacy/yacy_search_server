/**
 *  AccumulateSRURSS
 *  Copyright 2010 by Michael Peter Christen
 *  First released 06.01.2011 at https://yacy.net
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

package net.yacy.cora.federate.opensearch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.concurrent.BlockingQueue;

import org.apache.http.entity.mime.content.ContentBody;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;

public class SRURSSConnector {

    private final static int recordsPerSession = 250;

    public static Thread searchSRURSS(
            final BlockingQueue<RSSMessage> queue,
            final String urlBase,
            final String query,
            final long timeoutInit,
            final int maximumRecordsInit,
            final CacheStrategy verify,
            final boolean global,
            final ClientIdentification.Agent agent) {
        final Thread job = new Thread("searchSRURSS:" + urlBase) {
            @Override
            public void run() {
                int startRecord = 0;
                RSSMessage message;
                int maximumRecords = maximumRecordsInit;
                long timeout = timeoutInit;
                mainloop: while (timeout > 0 && maximumRecords > 0) {
                    final long st = System.currentTimeMillis();
                    RSSFeed feed;
                    try {
                        feed = loadSRURSS(urlBase, query, startRecord, recordsPerSession, verify, global, agent);
                    } catch (final IOException e1) {
                        break mainloop;
                    }
                    if (feed == null || feed.isEmpty()) break mainloop;
                    maximumRecords -= feed.size();
                    innerloop: while (!feed.isEmpty()) {
                        message = feed.pollMessage();
                        if (message == null) break innerloop;
                        try {
                            queue.put(message);
                        } catch (final InterruptedException e) {
                            break innerloop;
                        }
                    }
                    startRecord += recordsPerSession;
                    timeout -= System.currentTimeMillis() - st;
                }
                try { queue.put(RSSMessage.POISON); } catch (final InterruptedException e) { e.printStackTrace(); }
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
     * @param cacheStrategy
     * @param global if true also search results from other peers are included
     * @param agent
     * @return
     */
    public static RSSFeed loadSRURSS(
            final String rssSearchServiceURL,
            final String query,
            final int startRecord,
            final int maximumRecords,
            final CacheStrategy cacheStrategy,
            final boolean global,
            final ClientIdentification.Agent agent) throws IOException {
        MultiProtocolURL uri = null;
        try {
            uri = new MultiProtocolURL(rssSearchServiceURL);
        } catch (final MalformedURLException e) {
            throw new IOException("cora.Search failed asking peer '" + rssSearchServiceURL + "': bad url, " + e.getMessage());
        }

        // send request
        byte[] result = new byte[0];
        try {
            final LinkedHashMap<String,ContentBody> parts = new LinkedHashMap<>();
            parts.put("query", UTF8.StringBody(query));
            parts.put("startRecord", UTF8.StringBody(Integer.toString(startRecord)));
            parts.put("maximumRecords", UTF8.StringBody(Long.toString(maximumRecords)));
            parts.put("verify", cacheStrategy == null ? UTF8.StringBody("false") : UTF8.StringBody(cacheStrategy.toName()));
            parts.put("resource", UTF8.StringBody(global ? "global" : "local"));
            parts.put("nav", UTF8.StringBody("none"));
            // result = HTTPConnector.getConnector(userAgent == null ? MultiProtocolURI.yacybotUserAgent : userAgent).post(new MultiProtocolURI(rssSearchServiceURL), (int) timeout, uri.getHost(), parts);
            try (final HTTPClient httpClient = new HTTPClient(agent)) {
                result = httpClient.POSTbytes(new MultiProtocolURL(rssSearchServiceURL), uri.getHost(), parts, false, false);
            }

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
