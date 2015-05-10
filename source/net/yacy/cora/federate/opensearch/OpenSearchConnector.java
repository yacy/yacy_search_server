/**
 *  OpenSearchConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 03.11.2012 at http://yacy.net
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.AbstractFederateSearchConnector;
import net.yacy.cora.federate.FederateSearchConnector;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryParams;
import net.yacy.search.schema.CollectionSchema;

/**
 * Handling of queries to remote OpenSearch systems. Iterates to a list of
 * configured systems until number of needed results are available.
 */
public class OpenSearchConnector extends AbstractFederateSearchConnector implements FederateSearchConnector {

    @Override
    public boolean init(final String name, final String urltemplate) {
        this.baseurl = urltemplate;
        this.instancename = name;
        this.localcfg = null; // no field mapping needed
        return true;
    }

    /**
     * replace Opensearchdescription search template parameter with actual values
     */
    private String parseSearchTemplate(String searchurltemplate, String query, int start, int rows) {
        String tmps = searchurltemplate.replaceAll("\\?}", "}"); // some optional parameters may include question mark '{param?}='
        tmps = tmps.replace("{startIndex}", Integer.toString(start));
        tmps = tmps.replace("{startPage}", "");
        tmps = tmps.replace("{count}", Integer.toString(rows));
        tmps = tmps.replace("{language}", "");
        tmps = tmps.replace("{inputEncoding}", "UTF-8");
        tmps = tmps.replace("{outputEncoding}", "UTF-8");
        return tmps.replace("{searchTerms}", query);
    }

    /**
     * queries remote system and returns the resultlist (waits until results
     * transmitted or timeout) This is the main access routine used for the
     * serach and query operation For internal access delay time, also the
     * this.lastaccessed time needs to be set here.
     *
     * @return query results (metadata) with fields according to YaCy schema
     */
    @Override
    public List<URIMetadataNode> query(QueryParams query) {
        List<URIMetadataNode> docs = new ArrayList<URIMetadataNode>();

        // see http://www.loc.gov/standards/sru/
        String searchurl = this.parseSearchTemplate(baseurl, query.getQueryGoal().getQueryString(false), 0, query.itemsPerPage);
        try {
            MultiProtocolURL aurl = new MultiProtocolURL(searchurl);
            try {
                this.lastaccesstime = System.currentTimeMillis();
                final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
                byte[] result = httpClient.GETbytes(aurl, null, null, false);
                RSSReader rssReader =  RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, result);
                if (rssReader != null) {
                    final RSSFeed feed = rssReader.getFeed();
                    if (feed != null) {
                        for (final RSSMessage item : feed) {
                            try {
                                DigestURL uri = new DigestURL(item.getLink());

                                URIMetadataNode doc = new URIMetadataNode(uri);
                                doc.setField(CollectionSchema.charset_s.getSolrFieldName(), UTF8.charset.name());
                                doc.setField(CollectionSchema.author.getSolrFieldName(), item.getAuthor());
                                doc.setField(CollectionSchema.title.getSolrFieldName(), item.getTitle());
                                doc.setField(CollectionSchema.language_s.getSolrFieldName(), item.getLanguage());
                                doc.setField(CollectionSchema.last_modified.getSolrFieldName(), item.getPubDate());
                                final String mime = TextParser.mimeOf(uri);
                                if (mime != null) {
                                    doc.setField(CollectionSchema.content_type.getSolrFieldName(), mime);
                                }
                                if (item.getCategory().isEmpty()) {
                                    doc.setField(CollectionSchema.keywords.getSolrFieldName(), Arrays.toString(item.getSubject()));
                                } else {
                                    doc.setField(CollectionSchema.keywords.getSolrFieldName(), Arrays.toString(item.getSubject()) + " " + item.getCategory());
                                }
                                doc.setField(CollectionSchema.publisher_t.getSolrFieldName(), item.getCopyright());

                                doc.setField(CollectionSchema.text_t.getSolrFieldName(), item.getDescriptions());
                                // we likely got only a search related snippet (take is as text content)
                                // add collection "dht" which is used to differentiate metadata from full crawl data in the index
                                doc.setField(CollectionSchema.collection_sxt.getSolrFieldName(), "dht");

                                if (item.getLat() != 0.0 && item.getLon() != 0.0) {
                                    doc.setField(CollectionSchema.coordinate_p.getSolrFieldName(), item.getLat() + "," + item.getLon());
                                }
                                if (item.getSize() > 0) {
                                    doc.setField(CollectionSchema.size_i.getSolrFieldName(), item.getSize());
                                }

                                docs.add(doc);
                            } catch (final MalformedURLException e) {
                            }
                        }
                        ConcurrentLog.info("OpenSerachConnector", "received " + docs.size() + " results from " + this.instancename);
                    }
                }
            } catch (IOException ex) {
                ConcurrentLog.logException(ex);
                ConcurrentLog.info("OpenSearchConnector", "no connection to " + searchurl);
            }
        } catch (MalformedURLException ee) {
            ConcurrentLog.warn("OpenSearchConnector", "malformed url " + searchurl);
        }
        return docs;
    }
}
