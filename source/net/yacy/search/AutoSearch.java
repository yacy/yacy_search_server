/**
 *  AutoSearch.java
 *  Copyright 2015 by Burkhard Buelte
 *  First released 09.01.2015 at http://yacy.net
 *
 *  This is a part of YaCy, a peer-to-peer based web search engine
 *
 *  LICENSE
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

package net.yacy.search;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import static net.yacy.cora.federate.opensearch.SRURSSConnector.loadSRURSS;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.BookmarksDB.Bookmark;
import net.yacy.kelondro.workflow.AbstractBusyThread;
import net.yacy.peers.Seed;
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;

/**
 * AutoSearch retrieves queries from Bookmarks or a property file (if existing)
 * and loops to a list of connected peers and asks each for results which are
 * added to the local index.
 */
public class AutoSearch extends AbstractBusyThread {

    private Set<String> querystack; // serach query
    public String currentQuery = null; // current query
    private Set<String> currentTargets = null; // peer hashes
    final Switchboard sb;
    public int gotresults;
    private long lastInitTime; // to recognize new data (Bookmarks) to import

    public AutoSearch(Switchboard xsb) {
        super(3000, 1000); // set lower limits of cycle delay
        this.setIdleSleep(60000); // set actual cycle delays
        this.setBusySleep(10000);
        this.sb = xsb;

        gotresults = 0;
        querystack = new HashSet<String>();

        this.lastInitTime = System.currentTimeMillis() - 600000; // init to now - 10 min
        if (!checkBookmarkDB()) {
            try {
                // check for old queries in temp property file
                File pfile = new File(xsb.dataPath, "DATA/SETTINGS/autosearch.conf");
                if (pfile.exists()) {
                    ConcurrentLog.info(AutoSearch.class.getName(), "read queries from file " + pfile.getAbsolutePath());
                    Properties prop = new Properties();
                    FileInputStream fileIn = new FileInputStream(pfile);
                    prop.load(fileIn);
                    if (prop.size() > 0) {
                        Set<Object> all = prop.keySet();
                        for (Object s : all) {
                            String query = prop.getProperty((String) s);
                            if (query != null && !query.isEmpty()) {
                                querystack.add(query);
                            }
                        }
                    }
                    fileIn.close();
                }
            } catch (final IOException e) {
                ConcurrentLog.warn(AutoSearch.class.getName(), "Error reading config file");
            }
        }
    }

    /**
     * Save current queries to a (temporary) property file to allow continue
     * after a restart. Existing file will be overwritten or deleted.
     */
    private void saveasPropFile() {
        File pfile = new File(sb.dataPath, "DATA/SETTINGS/autosearch.conf");
        if (querystack.size() == 0) {
            if (pfile.exists()) {
                pfile.delete();
            }
        } else {
            try {
                Properties prop = new Properties();
                for (String s : querystack) {
                    prop.put("query" + s.hashCode(), s);
                }
                OutputStream fileOut = new FileOutputStream(pfile);
                prop.store(fileOut, "AutoSearch query list");
                fileOut.close();
            } catch (FileNotFoundException ex) {
                ConcurrentLog.warn(AutoSearch.class.getName(), "can not create file " + pfile.getAbsolutePath());
            } catch (IOException ex) {
                ConcurrentLog.warn(AutoSearch.class.getName(), "IO error writing to file " + pfile.getAbsolutePath());
            }
        }
    }

    /**
     * Get peers to query (peers connected)
     *
     * @return Set of peer hashes to contact
     */
    private void initPeerList() {
        if (currentTargets == null) {
            currentTargets = new HashSet<String>();
        }
        // TODO: DHT peers could be excluded
        Iterator<Seed> it = Switchboard.getSwitchboard().peers.seedsConnected(true, false, null, 0);
        while (it.hasNext()) {
            Seed s = it.next();
            currentTargets.add(s.hash);
        }
    }

    /**
     * Check BookmarkDB for existing queries return true if new entry added to
     * query queue. Store queries in (temporary) property file
     *
     * @return true if new query from bookmark was added
     */
    private boolean checkBookmarkDB() {
        if (Switchboard.getSwitchboard().bookmarksDB != null) {
            int added = 0;
            Iterator<Bookmark> it = Switchboard.getSwitchboard().bookmarksDB.getBookmarksIterator();
            if (it != null) {
                while (it.hasNext()) {
                    Bookmark bmk = it.next();
                    // get search bookmarks only
                    if (bmk.getFoldersString().startsWith("/search")) {
                        // take only new created or edited bookmarks
                        if (bmk.getTimeStamp() >= this.lastInitTime) {
                            final String query = bmk.getQuery();
                            if (query != null && !query.isEmpty()) {
                                {
                                    querystack.add(query);
                                    added++;
                                    ConcurrentLog.info(AutoSearch.class.getName(), "add query from Bookmarks: query=" + query);
                                }
                            }
                        }
                    }
                }
            }
            if (added > 0) {
                this.lastInitTime = System.currentTimeMillis();
                saveasPropFile();
                return true;
            }
        }
        return false;
    }

    /**
     * Process query queue, select one query and peer to ask next
     *
     * @return true if something processed
     */
    @Override
    public boolean job() {

        if (currentQuery == null && querystack != null && querystack.size() > 0) {
            currentQuery = querystack.iterator().next();
            querystack.remove(currentQuery); // imediate remove to asure no repeat
            initPeerList(); // late initialization of peerlist to get currently connected
        }

        // ask next peer for search term
        if (currentQuery != null && !currentQuery.isEmpty()) {
            if (currentTargets != null && !currentTargets.isEmpty()) {
                while (currentTargets.size() > 0) { // loop only to skip disconnected peers
                    String peerhash = currentTargets.iterator().next();
                    currentTargets.remove(peerhash);
                    Seed seed = Switchboard.getSwitchboard().peers.getConnected(peerhash);
                    if (seed != null) {
                        processSingleTarget(seed);
                        return true; // just one query per busycycle is intended
                    }
                }
            }
            currentQuery = null;
        }

        // no search targets 
        checkBookmarkDB();

        // TODO: do idle processing
        // analyse content of local index
        // extend search with learned new search terms
        // follow most promising links
        ConcurrentLog.fine(AutoSearch.class.getName(), "nothing to do");
        return this.querystack.size() > 0;
    }

    /**
     * Calls one peer for search results of the current query and adds it to the
     * local index. Depending on peers SolrAvailable flag the a solr query or
     * opensearch/rss query is used.
     *
     * @param seed the peer to ask
     */
    private void processSingleTarget(Seed seed) {
        ConcurrentLog.fine(AutoSearch.class.getName(), "ask " + seed.getIP() + " " + seed.getName() + " for query=" + currentQuery);

        if (seed.getFlagSolrAvailable()) { // do a solr query
            SolrDocumentList docList = null;
            SolrQuery solrQuery = new SolrQuery();
            // use remote defaults and ranking (to query their index right)
            solrQuery.set(CommonParams.Q, currentQuery + " AND (" + CollectionSchema.httpstatus_i.name() + ":200)"); // except this yacy special
            solrQuery.set("q.op", "AND"); // except ... no one word matches please
            solrQuery.set(CommonParams.ROWS, sb.getConfig(SwitchboardConstants.REMOTESEARCH_MAXCOUNT_USER, "20"));
            this.setName("Protocol.solrQuery(" + solrQuery.getQuery() + " to " + seed.hash + ")");
            try {
                RemoteInstance instance = new RemoteInstance("http://" + seed.getPublicAddress(seed.getIP()) + "/solr/", null, null, 10000); // this is a 'patch configuration' which considers 'solr' as default collection
                try {
                    SolrConnector solrConnector = new RemoteSolrConnector(instance, true, null);
                    if (!solrConnector.isClosed()) {
                        try {
                            QueryResponse rsp = solrConnector.getResponseByParams(solrQuery);
                            docList = rsp.getResults();
                        } catch (Throwable e) {
                        } finally {
                            solrConnector.close();
                        }
                    }
                } catch (Throwable ee) {
                } finally {
                    instance.close();
                }
                if (docList != null) {
                    for (SolrDocument d : docList) {
                        sb.index.fulltext().putDocument(sb.index.fulltext().getDefaultConfiguration().toSolrInputDocument(d));
                        this.gotresults++;
                    }
                    ConcurrentLog.info(AutoSearch.class.getName(), "added " + docList.size() + " results from " + seed.getName() + " to index for solrquery=" + currentQuery);
                }
            } catch (Throwable eee) {
            }
        } else { // do a yacysearch.rss query
            final String rssSearchServiceURL = "http://" + seed.getPublicAddress(seed.getIP()) + "/yacysearch.rss";
            try {
                RSSFeed feed = loadSRURSS(
                        rssSearchServiceURL,
                        currentQuery,
                        0,
                        sb.getConfigInt(SwitchboardConstants.REMOTESEARCH_MAXCOUNT_USER, 20),
                        CacheStrategy.IFFRESH,
                        false, // just local, as we ask others too
                        ClientIdentification.yacyInternetCrawlerAgent);
                final List<DigestURL> urls = new ArrayList<DigestURL>();
                for (final MultiProtocolURL entry : feed.getLinks()) {
                    urls.add(new DigestURL(entry, (byte[]) null));
                    this.gotresults++;
                }
                sb.addToCrawler(urls, false);
                ConcurrentLog.info(AutoSearch.class.getName(), "added " + urls.size() + " results from " + seed.getName() + " to index for query=" + currentQuery);
            } catch (IOException ex) {
                ConcurrentLog.info(AutoSearch.class.getName(), "no answer from " + seed.getName());
            }
        }
    }

    /**
     * Estimate of queries to perform
     */
    @Override
    public int getJobCount() {
        if (currentTargets != null) {
            int cnt = currentTargets.size();
            cnt += querystack.size() * sb.peers.sizeConnected();
            return cnt;
        }
        return 0;
    }

    @Override
    public void freemem() {
    }

    @Override
    public void close() {
        this.saveasPropFile(); // saves or deletes property file with queries
    }
}
