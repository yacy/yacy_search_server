/**
 *  ShardSolrConnector
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.federate.solr.instance.SolrInstance;
import net.yacy.cora.federate.solr.instance.SolrRemoteInstance;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

public class ShardSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final ArrayList<SolrRemoteInstance> instances;
    private final ArrayList<SolrConnector> connectors;
    private final ShardSelection sharding;
    private final String[] adminInterfaces;

    public ShardSolrConnector(
            ArrayList<SolrRemoteInstance> instances,
            final ShardSelection.Method method, boolean multipleConnections) {
        this.instances = instances;
        this.connectors = new ArrayList<SolrConnector>();
        SolrConnector s;
        this.adminInterfaces = new String[instances.size()];
        int c = 0;
        String defaultCoreName = instances.get(0).getDefaultCoreName();
        for (final SolrRemoteInstance instance: instances) {
            adminInterfaces[c++] = instance.getAdminInterface();
            s = multipleConnections ? new MultipleSolrConnector(instance, defaultCoreName, 2) : new RemoteSolrConnector(instance, defaultCoreName);
            this.connectors.add(s /*new RetrySolrConnector(s, timeout)*/);
        }
        this.sharding = new ShardSelection(method, this.connectors.size());
    }

    public static ArrayList<SolrRemoteInstance> getShardInstances(final String urlList) throws IOException {
        urlList.replace(' ', ',');
        String[] urls = urlList.split(",");
        ArrayList<SolrRemoteInstance> instances = new ArrayList<SolrRemoteInstance>();
        for (final String u: urls) {
            SolrRemoteInstance instance = new SolrRemoteInstance(u);
            instances.add(instance);
        }
        return instances;
    }

    public static ArrayList<SolrRemoteInstance> getShardInstances(final String urlList, Collection<String> coreNames, String defaultCoreName) throws IOException {
        urlList.replace(' ', ',');
        String[] urls = urlList.split(",");
        ArrayList<SolrRemoteInstance> instances = new ArrayList<SolrRemoteInstance>();
        for (final String u: urls) {
            SolrRemoteInstance instance = new SolrRemoteInstance(u, coreNames, defaultCoreName);
            instances.add(instance);
        }
        return instances;
    }
    
    public ArrayList<SolrRemoteInstance> getInstances() {
        return this.instances;
    }
    
    @Override
    public void commit(boolean softCommit) {
        for (final SolrConnector connector: this.connectors) connector.commit(softCommit);
    }

    /**
     * force an explicit merge of segments
     * @param maxSegments the maximum number of segments. Set to 1 for maximum optimization
     */
    @Override
    public void optimize(int maxSegments) {
        for (final SolrConnector connector: this.connectors) connector.optimize(maxSegments);
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
    public void deleteByQuery(final String querystring) throws IOException {
        for (final SolrConnector connector: this.connectors) connector.deleteByQuery(querystring);
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

        final SimpleOrderedMap<Object> facet_countsAcc = new SimpleOrderedMap<Object>();
        final SimpleOrderedMap<Object> highlightingAcc = new SimpleOrderedMap<Object>();
        final SimpleOrderedMap<Object> headerAcc = new SimpleOrderedMap<Object>();
        final SolrDocumentList resultsAcc = new SolrDocumentList();

        // concurrently call all shards
        List<Thread> t = new ArrayList<Thread>();
        for (final SolrConnector connector: this.connectors) {
            Thread t0 = new Thread() {
                @SuppressWarnings("unchecked")
                @Override
                public void run() {
                    QueryResponse rsp;
                    try {
                        rsp = connector.query(query);
                    } catch (Throwable e) {return;}
                    NamedList<Object> response = rsp.getResponse();          

                    // set the header; this is mostly always the same (well this is not evaluated much)
                    SimpleOrderedMap<Object> header = (SimpleOrderedMap<Object>) response.get("responseHeader");
                    //Integer status = (Integer) header.get("status");
                    //Integer QTime = (Integer) header.get("QTime");
                    //SimpleOrderedMap<Object> params = (SimpleOrderedMap<Object>) header.get("params");
                    if (headerAcc.size() == 0) {
                        for (Map.Entry<String, Object> e: header) headerAcc.add(e.getKey(), e.getValue());
                    }
                    
                    // accumulate the results
                    SolrDocumentList results = (SolrDocumentList) response.get("response");
                    long found = results.size();
                    for (int i = 0; i < found; i++) resultsAcc.add(results.get(i));
                    resultsAcc.setNumFound(resultsAcc.getNumFound() + results.getNumFound());
                    resultsAcc.setMaxScore(Math.max(resultsAcc.getMaxScore() == null ? 0f : resultsAcc.getMaxScore().floatValue(), results.getMaxScore() == null ? 0f : results.getMaxScore().floatValue()));
                    
                    // accumulate the highlighting
                    SimpleOrderedMap<Object> highlighting = (SimpleOrderedMap<Object>) response.get("highlighting");
                    if (highlighting != null) {
                        for (Map.Entry<String, Object> e: highlighting) highlightingAcc.add(e.getKey(), e.getValue());
                    }
                    
                    // accumulate the facets (well this is not correct at this time...)
                    SimpleOrderedMap<Object> facet_counts = (SimpleOrderedMap<Object>) response.get("facet_counts");
                    if (facet_counts != null) {
                        for (Map.Entry<String, Object> e: facet_counts) facet_countsAcc.add(e.getKey(), e.getValue());
                    }
                }
            };
            t0.start();
            t.add(t0);
        }
        for (Thread t0: t) {
            try {t0.join();} catch (InterruptedException e) {}
        }
        
        // prepare combined response
        QueryResponse rspAcc = new QueryResponse();
        NamedList<Object> nl = new NamedList<Object>();
        nl.add("responseHeader", headerAcc);
        nl.add("response", resultsAcc);
        if (highlightingAcc != null && highlightingAcc.size() > 0) nl.add("highlighting", highlightingAcc);
        if (facet_countsAcc != null && facet_countsAcc.size() > 0) nl.add("facet_counts", facet_countsAcc);
        rspAcc.setResponse(nl);
        return rspAcc;
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
        return this.adminInterfaces;
    }

}
