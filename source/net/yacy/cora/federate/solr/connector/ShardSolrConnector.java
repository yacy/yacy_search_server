/**
 *  SolrChardingConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 25.05.2011 at http://yacy.net
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

package net.yacy.cora.federate.solr.connector;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.protocol.Domains;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;


public class ShardSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final List<SolrConnector> connectors;
    private final ShardSelection sharding;
    private final String[] urls;

    public ShardSolrConnector(final String urlList, final ShardSelection.Method method, final long timeout, boolean multipleConnections) throws IOException {
        urlList.replace(' ', ',');
        this.urls = urlList.split(",");
        this.connectors = new ArrayList<SolrConnector>();
        SolrConnector s;
        for (final String u: this.urls) {
            s = multipleConnections ? new MultipleSolrConnector(u.trim(), 2) : new RemoteSolrConnector(u.trim());
            this.connectors.add(new RetrySolrConnector(s, timeout));
        }
        this.sharding = new ShardSelection(method, this.urls.length);
    }

    @Override
    public int getCommitWithinMs() {
        return this.connectors.get(0).getCommitWithinMs();
    }

    /**
     * set the solr autocommit delay
     * @param c the maximum waiting time after a solr command until it is transported to the server
     */
    @Override
    public void setCommitWithinMs(int c) {
        for (final SolrConnector connector: this.connectors) connector.setCommitWithinMs(c);
    }

    @Override
    public void commit() {
        for (final SolrConnector connector: this.connectors) connector.commit();
    }

    @Override
    public synchronized void close() {
        for (final SolrConnector connector: this.connectors) connector.close();
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        for (final SolrConnector connector: this.connectors) connector.clear();
    }

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void delete(final String id) throws IOException {
        for (final SolrConnector connector: this.connectors) connector.delete(id);
    }

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    @Override
    public void delete(final List<String> ids) throws IOException {
        for (final SolrConnector connector: this.connectors) connector.delete(ids);
    }

    @Override
    public int deleteByQuery(final String querystring) throws IOException {
        int count = 0;
        for (final SolrConnector connector: this.connectors) count += connector.deleteByQuery(querystring);
        return count;
    }

    /**
     * check if a given id exists in solr
     * @param id
     * @return true if any entry in solr exists
     * @throws IOException
     */
    @Override
    public boolean exists(final String fieldName, final String key) throws IOException {
        for (final SolrConnector connector: this.connectors) {
            if (connector.exists(fieldName, key)) return true;
        }
        return false;
    }

    @Override
    public SolrDocument getById(final String key, final String ... fields) throws IOException {
        for (final SolrConnector connector: this.connectors) {
            SolrDocument doc = connector.getById(key, fields);
            if (doc != null) return doc;
        }
        return null;
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException {
        this.connectors.get(this.sharding.select(solrdoc)).add(solrdoc);
    }

    /**
     * add a collection of Solr documents
     * @param docs
     * @throws IOException
     */
    protected void addSolr(final Collection<SolrInputDocument> docs) throws IOException {
        for (final SolrInputDocument doc: docs) add(doc);
    }

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList query(final String querystring, final int offset, final int count, final String ... fields) throws IOException {
        final SolrDocumentList list = new SolrDocumentList();
        List<Thread> t = new ArrayList<Thread>();
        for (final SolrConnector connector: this.connectors) {
            Thread t0 = new Thread() {
                @Override
                public void run() {
                    try {
                        final SolrDocumentList l = connector.query(querystring, offset, count, fields);
                        for (final SolrDocument d: l) {
                            list.add(d);
                        }
                    } catch (IOException e) {}
                }
            };
            t0.start();
            t.add(t0);
        }
        for (Thread t0: t) {
            try {t0.join();} catch (InterruptedException e) {}
        }
        return list;
    }

    @Override
    public QueryResponse query(final ModifiableSolrParams query) throws IOException, SolrException {
        for (final SolrConnector connector: this.connectors) {
            QueryResponse rsp = connector.query(query);
            if (rsp != null && rsp.getResults().size() > 0) return rsp;
        }
        return new QueryResponse();
    }
    
    @Override
    public long getQueryCount(final String querystring) throws IOException {
        final AtomicLong count = new AtomicLong(0);
        List<Thread> t = new ArrayList<Thread>();
        for (final SolrConnector connector: this.connectors) {
            Thread t0 = new Thread() {
                @Override
                public void run() {
                    try {
                        count.addAndGet(connector.getQueryCount(querystring));
                    } catch (IOException e) {}
                }
            };
            t0.start();
            t.add(t0);
        }
        for (Thread t0: t) {
            try {t0.join();} catch (InterruptedException e) {}
        }
        return count.get();
    }

    @Override
    public Map<String, ReversibleScoreMap<String>> getFacets(String query, int maxresults, final String ... fields) throws IOException {
        Map<String, ReversibleScoreMap<String>> facets = new HashMap<String, ReversibleScoreMap<String>>();
        for (final SolrConnector connector: this.connectors) {
            Map<String, ReversibleScoreMap<String>> peer = connector.getFacets(query, maxresults, fields);        
            innerloop: for (Map.Entry<String, ReversibleScoreMap<String>> facet: facets.entrySet()) {
                ReversibleScoreMap<String> peerfacet = peer.remove(facet.getKey());
                if (peerfacet == null) continue innerloop;
                for (String key: peerfacet) facet.getValue().inc(key, peerfacet.get(key));
            }
            for (Map.Entry<String, ReversibleScoreMap<String>> peerfacet: peer.entrySet()) {
                facets.put(peerfacet.getKey(), peerfacet.getValue());
            }
        }
        return facets;
    }
    
    
    public long[] getSizeList() {
        final long[] size = new long[this.connectors.size()];
        int i = 0;
        for (final SolrConnector connector: this.connectors) {
            size[i++] = connector.getSize();
        }
        return size;
    }

    @Override
    public long getSize() {
        final long[] size = getSizeList();
        long s = 0;
        for (final long l: size) s += l;
        return s;
    }

    public String[] getAdminInterfaceList() {
        final String[] urlAdmin = new String[this.connectors.size()];
        int i = 0;
        final InetAddress localhostExternAddress = Domains.myPublicLocalIP();
        final String localhostExtern = localhostExternAddress == null ? Domains.LOCALHOST : localhostExternAddress.getHostAddress();
        for (String u: this.urls) {
            int p = u.indexOf("localhost",0);
            if (p < 0) p = u.indexOf("127.0.0.1",0);
            if (p < 0) p = u.indexOf("0:0:0:0:0:0:0:1",0);
            if (p >= 0) u = u.substring(0, p) + localhostExtern + u.substring(p + 9);
            urlAdmin[i++] = u + (u.endsWith("/") ? "admin/" : "/admin/");
        }
        return urlAdmin;
    }

}
