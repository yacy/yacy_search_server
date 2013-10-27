/**
 *  EmbeddedSolrConnector
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

import java.io.IOException;

import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.cora.federate.solr.instance.SolrInstance;
import net.yacy.cora.util.ConcurrentLog;

import org.apache.lucene.index.DirectoryReader;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;

public class EmbeddedSolrConnector extends SolrServerConnector implements SolrConnector {

    public static final String SELECT = "/select";
    public static final String CONTEXT = "/solr";
    
    private final SearchHandler requestHandler;
    private final EmbeddedInstance instance;
    private SolrCore core;

    public EmbeddedSolrConnector(EmbeddedInstance instance) {
        super();
        this.instance = instance;
        this.core = this.instance.getDefaultCore();
        this.requestHandler = new SearchHandler();
        this.requestHandler.init(new NamedList<Object>());
        this.requestHandler.inform(this.core);
        super.init(this.instance.getDefaultServer());
    }
    
    public EmbeddedSolrConnector(EmbeddedInstance instance, String coreName) {
        super();
        this.instance = instance;
        this.core = this.instance.getCore(coreName);
        this.requestHandler = new SearchHandler();
        this.requestHandler.init(new NamedList<Object>());
        this.requestHandler.inform(this.core);
        super.init(this.instance.getServer(coreName));
    }

    public SolrInstance getInstance() {
        return this.instance;
    }
    
    public SolrCore getCore() {
        return this.core;
    }

    public SolrConfig getConfig() {
        return this.core.getSolrConfig();
    }

    @Override
    public synchronized void close() {
        try {this.commit(false);} catch (final Throwable e) {ConcurrentLog.logException(e);}
        try {super.close();} catch (final Throwable e) {ConcurrentLog.logException(e);}
        try {this.core.close();} catch (final Throwable e) {ConcurrentLog.logException(e);}
    }

    @Override
    public long getSize() {
        RefCounted<SolrIndexSearcher> refCountedIndexSearcher = this.core.getSearcher();
        SolrIndexSearcher searcher = refCountedIndexSearcher.get();
        DirectoryReader reader = searcher.getIndexReader();
        long numDocs = reader.numDocs();
        refCountedIndexSearcher.decref();
        return numDocs;
    }

    /**
     * get a new query request. MUST be closed after usage using close()
     * @param params
     * @return
     */
    public SolrQueryRequest request(final SolrParams params) {
        SolrQueryRequest req = null;
        req = new SolrQueryRequestBase(this.core, params){};
        req.getContext().put("path", SELECT);
        req.getContext().put("webapp", CONTEXT);
        return req;
    }
    
    public SolrQueryResponse query(SolrQueryRequest req) throws SolrException {
        final long startTime = System.currentTimeMillis();

        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        String q = req.getParams().get("q");
        String threadname = Thread.currentThread().getName();
        if (q != null) Thread.currentThread().setName("solr query: q = " + q);
        
        SolrQueryResponse rsp = new SolrQueryResponse();
        NamedList<Object> responseHeader = new SimpleOrderedMap<Object>();
        responseHeader.add("params", req.getOriginalParams().toNamedList());
        rsp.add("responseHeader", responseHeader);
        SolrRequestInfo.setRequestInfo(new SolrRequestInfo(req, rsp));

        // send request to solr and create a result
        this.requestHandler.handleRequest(req, rsp);

        // get statistics and add a header with that
        Exception exception = rsp.getException();
        int status = exception == null ? 0 : exception instanceof SolrException ? ((SolrException) exception).code() : 500;
        responseHeader.add("status", status);
        responseHeader.add("QTime",(int) (System.currentTimeMillis() - startTime));

        if (q != null) Thread.currentThread().setName(threadname);
        // return result
        return rsp;
    }

    @Override
    public QueryResponse getResponseByParams(ModifiableSolrParams params) throws IOException {
        if (this.server == null) throw new IOException("server disconnected");
        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        String q = params.get("q");
        String threadname = Thread.currentThread().getName();
        if (q != null) Thread.currentThread().setName("solr query: q = " + q);
        QueryResponse rsp;
        try {
            rsp = this.server.query(params);
            if (q != null) Thread.currentThread().setName(threadname);
            if (rsp != null) log.fine(rsp.getResults().getNumFound() + " results for q=" + q);
            return rsp;
        } catch (final SolrServerException e) {
            throw new IOException(e);
        } catch (final Throwable e) {
            throw new IOException("Error executing query", e);
        }
    }

}
