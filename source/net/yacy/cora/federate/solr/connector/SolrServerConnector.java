/**
 *  SolrServerConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 21.06.2012 at http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.servlet.SolrRequestParsers;

public abstract class SolrServerConnector extends AbstractSolrConnector implements SolrConnector {

    protected final static Logger log = Logger.getLogger(SolrServerConnector.class);
    
    protected SolrServer server;
    protected int commitWithinMs; // max time (in ms) before a commit will happen

    protected SolrServerConnector() {
        this.server = null;
        this.commitWithinMs = 180000;
    }

    protected void init(SolrServer server) {
        this.server = server;
    }

    public SolrServer getServer() {
        return this.server;
    }

    /**
     * get the solr autocommit delay
     * @return the maximum waiting time after a solr command until it is transported to the server
     */
    @Override
    public int getCommitWithinMs() {
        return this.commitWithinMs;
    }

    /**
     * set the solr autocommit delay
     * when doing continuous inserts, don't set this value because it would cause continuous commits
     * @param c the maximum waiting time after a solr command until it is transported to the server
     */
    @Override
    public void setCommitWithinMs(int c) {
        this.commitWithinMs = c;
    }

    @Override
    public synchronized void commit(final boolean softCommit) {
        try {
            this.server.commit(true, true, softCommit);
        } catch (SolrServerException e) {
        } catch (IOException e) {
        }
    }

    /**
     * force an explicit merge of segments
     * @param maxSegments the maximum number of segments. Set to 1 for maximum optimization
     */
    public void optimize(int maxSegments) {
        try {
            this.server.optimize(true, true, maxSegments);
        } catch (SolrServerException e) {
        } catch (IOException e) {
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (this.server != null) synchronized (this.server) {this.server.commit(true, true, false);}
            this.server = null;
        } catch (SolrServerException e) {
            log.warn(e);
        } catch (IOException e) {
            log.warn(e);
        }
    }

    private static final SolrRequestParsers _parser = new SolrRequestParsers(null);

    @Override
    public long getSize() {
        String threadname = Thread.currentThread().getName();
        Thread.currentThread().setName("solr query: size");
        if (this.server instanceof EmbeddedSolrServer) {
            EmbeddedSolrServer ess = (EmbeddedSolrServer) this.server;
            CoreContainer coreContainer = ess.getCoreContainer();
            String coreName = coreContainer.getDefaultCoreName();
            SolrCore core = coreContainer.getCore(coreName);
            if (core == null) throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "No such core: " + coreName);

            try {
                SolrParams params = AbstractSolrConnector.catchSuccessQuery;
                QueryRequest request = new QueryRequest(AbstractSolrConnector.catchSuccessQuery);
                SolrQueryRequest req = _parser.buildRequestFrom(core, params, request.getContentStreams());
                String path = "/select"; 
                req.getContext().put("path", path);
                SolrQueryResponse rsp = new SolrQueryResponse();
                SolrRequestInfo.setRequestInfo(new SolrRequestInfo(req, rsp));
                SolrRequestHandler handler = core.getRequestHandler(path);
                SearchHandler sh = (SearchHandler) handler;
                List<SearchComponent> components = sh.getComponents();
                ResponseBuilder rb = new ResponseBuilder(req, rsp, components);
                QueryComponent qc = (QueryComponent) components.get(0);
                qc.prepare(rb);
                qc.process(rb);
                qc.finishStage(rb);
                int hits = rb.getResults().docList.matches();
                if (req != null) req.close();
                core.close();
                SolrRequestInfo.clearRequestInfo();
                Thread.currentThread().setName(threadname);
                return hits;
            } catch (final Throwable e) {
                log.warn(e);
                Thread.currentThread().setName(threadname);
                return 0;
            }
        }
        Thread.currentThread().setName(threadname);
        return getSize0();
    }

    public long getSize0() {
        /*
        if (this.server instanceof EmbeddedSolrServer) {
            EmbeddedSolrServer ess = (EmbeddedSolrServer) this.server;
            CoreContainer coreContainer = ess.getCoreContainer();
            String coreName = coreContainer.getDefaultCoreName();
            SolrCore core = coreContainer.getCore(coreName);
        }
        */
        try {
            final QueryResponse rsp = query(AbstractSolrConnector.catchSuccessQuery);
            if (rsp == null) return 0;
            final SolrDocumentList docs = rsp.getResults();
            if (docs == null) return 0;
            return docs.getNumFound();
        } catch (final Throwable e) {
            log.warn(e);
            return 0;
        }
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteByQuery("*:*");
                this.server.commit(true, true, false);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final String id) throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteById(id, this.commitWithinMs);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final List<String> ids) throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteById(ids, this.commitWithinMs);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    /**
     * delete entries from solr according the given solr query string
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteByQuery(querystring, this.commitWithinMs);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    public void add(final File file, final String solrId) throws IOException {
        final ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addFile(file, "application/octet-stream");
        up.setParam("literal.id", solrId);
        up.setParam("uprefix", "attr_");
        up.setParam("fmap.content", "attr_content");
        up.setCommitWithin(this.commitWithinMs);
        //up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        try {
            synchronized (this.server) {
                this.server.request(up);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException {
        if (this.server == null) return;
        try {
            if (solrdoc.containsKey("_version_")) solrdoc.setField("_version_",0L); // prevent Solr "version conflict"
            synchronized (this.server) {
                this.server.add(solrdoc, this.commitWithinMs);
            }
        } catch (Throwable e) {
            // catches "version conflict for": try this again and delete the document in advance
            try {
                this.server.deleteById((String) solrdoc.getFieldValue(YaCySchema.id.getSolrFieldName()));
            } catch (SolrServerException e1) {}
            try {
                synchronized (this.server) {
                    this.server.add(solrdoc, this.commitWithinMs);
                }
            } catch (Throwable ee) {
                log.warn(e.getMessage() + " DOC=" + solrdoc.toString());
                throw new IOException(ee);
            }
        }
    }

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList query(final String querystring, final int offset, final int count, final String ... fields) throws IOException {
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(querystring);
        params.setRows(count);
        params.setStart(offset);
        params.setFacet(false);
        //params.addSortField( "price", SolrQuery.ORDER.asc );

        if (fields.length > 0) params.setFields(fields);
        
        // query the server
        QueryResponse rsp = query(params);
        final SolrDocumentList docs = rsp.getResults();
        return docs;
    }

    /**
     * get the number of results when this query is done.
     * This should only be called if the actual result is never used, and only the count is interesting
     * @param querystring
     * @return the number of results for this query
     */
    @Override
    public long getQueryCount(String querystring) throws IOException {
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(querystring);
        params.setRows(0);
        params.setStart(0);
        params.setFacet(false);
        //params.setFields(YaCySchema.id.getSolrFieldName());

        // query the server
        QueryResponse rsp = query(params);
        final SolrDocumentList docs = rsp.getResults();
        return docs.getNumFound();
    }

    /**
     * get facets of the index: a list of lists with values that are most common in a specific field
     * @param query a query which is performed to get the facets
     * @param fields the field names which are selected as facet
     * @param maxresults the maximum size of the resulting maps
     * @return a map with key = facet field name, value = an ordered map of field values for that field
     * @throws IOException
     */
    public Map<String, ReversibleScoreMap<String>> getFacets(String query, int maxresults, final String ... fields) throws IOException {
        // construct query
        assert fields.length > 0;
        final SolrQuery params = new SolrQuery();
        params.setQuery(query);
        params.setRows(0);
        params.setStart(0);
        params.setFacet(true);
        params.setFacetLimit(maxresults);
        params.setFacetSort(FacetParams.FACET_SORT_COUNT);
        params.setFields(fields);
        for (String field: fields) params.addFacetField(field);
        
        // query the server
        QueryResponse rsp = query(params);
        Map<String, ReversibleScoreMap<String>> facets = new HashMap<String, ReversibleScoreMap<String>>(fields.length);
        for (String field: fields) {
            FacetField facet = rsp.getFacetField(field);
            ReversibleScoreMap<String> result = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
            List<Count> values = facet.getValues();
            if (values == null) continue;
            for (Count ff: values) result.set(ff.getName(), (int) ff.getCount());
            facets.put(field, result);
        }
        return facets;
    }

    @Override
    abstract public QueryResponse query(ModifiableSolrParams params) throws IOException;

    private final char[] queryIDTemplate = "id:\"            \"".toCharArray();
    
    @Override
    public SolrDocument getById(final String key, final String ... fields) throws IOException {
        final SolrQuery query = new SolrQuery();
    	assert key.length() == 12;
        // construct query
    	char[] q = new char[17];
    	System.arraycopy(this.queryIDTemplate, 0, q, 0, 17);
    	System.arraycopy(key.toCharArray(), 0, q, 4, 12);
        query.setQuery(new String(q));
        query.setRows(1);
        query.setStart(0);
        if (fields.length > 0) query.setFields(fields);

        // query the server
        try {
            final QueryResponse rsp = query(query);
            final SolrDocumentList docs = rsp.getResults();
            if (docs.isEmpty()) return null;
            return docs.get(0);
        } catch (final Throwable e) {
            throw new IOException(e.getMessage(), e);
        }
    }

}
