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
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.cora.federate.solr.instance.SolrInstance;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.request.UnInvertedField;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.QueryResultKey;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;

public class EmbeddedSolrConnector extends SolrServerConnector implements SolrConnector {
    
    public static final String SELECT = "/select";
    public static final String CONTEXT = "/solr";
    
    private final SearchHandler requestHandler;
    private final EmbeddedInstance instance;
    private final SolrCore core;

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

    @Override
    public int hashCode() {
        return this.instance.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof EmbeddedSolrConnector && this.instance.equals(((EmbeddedSolrConnector) o).instance);
    }
    
    @Override
    public int bufferSize() {
        return 0;
    }

    public Map<String, SolrInfoMBean> getSolrInfoBeans() {
        return this.core.getInfoRegistry();
    }
    
    @Override
    public void clearCaches() {
        SolrConfig solrConfig = this.core.getSolrConfig();
        @SuppressWarnings("unchecked")
        SolrCache<String, UnInvertedField> fieldValueCache = solrConfig.fieldValueCacheConfig == null ? null : solrConfig.fieldValueCacheConfig.newInstance();
        if (fieldValueCache != null) fieldValueCache.clear();
        @SuppressWarnings("unchecked")
        SolrCache<Query, DocSet> filterCache= solrConfig.filterCacheConfig == null ? null : solrConfig.filterCacheConfig.newInstance();
        if (filterCache != null) filterCache.clear();
        @SuppressWarnings("unchecked")
        SolrCache<QueryResultKey, DocList> queryResultCache = solrConfig.queryResultCacheConfig == null ? null : solrConfig.queryResultCacheConfig.newInstance();
        if (queryResultCache != null) queryResultCache.clear();
        @SuppressWarnings("unchecked")
        SolrCache<Integer, Document> documentCache = solrConfig.documentCacheConfig == null ? null : solrConfig.documentCacheConfig.newInstance();
        if (documentCache != null) documentCache.clear();
        for (SolrInfoMBean ib: this.core.getInfoRegistry().values()) {
            // clear 'lost' caches
            if (ib instanceof SolrCache) ((SolrCache<?,?>) ib).clear();
        }
        FieldCache.DEFAULT.purgeAllCaches(); // purge the lucene field cache
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
    public boolean isClosed() {
        return this.core == null || this.core.isClosed();
    }
    
    @Override
    protected void finalize() throws Throwable {
        this.close();
    }

    @Override
    public synchronized void close() {
        if (this.core != null && !this.core.isClosed()) try {this.commit(false);} catch (final Throwable e) {ConcurrentLog.logException(e);}
        try {super.close();} catch (final Throwable e) {ConcurrentLog.logException(e);}
        // we do NOT close the core here because that is closed if the enclosing instance is closed
        // do NOT uncomment the following line, which caused a "org.apache.solr.core.SolrCore Too many close [count:-1] on org.apache.solr.core.SolrCore@51af7c57" error
        // try {this.core.close();} catch (final Throwable e) {ConcurrentLog.logException(e);}
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
        SolrQueryRequest req = new SolrQueryRequestBase(this.core, params){};
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
        //SolrRequestInfo.setRequestInfo(new SolrRequestInfo(req, rsp));

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
    
    /**
     * conversion from a SolrQueryResponse (which is a solr-internal data format) to SolrDocumentList (which is a solrj-format)
     * The conversion is done inside the solrj api using the BinaryResponseWriter and a very complex unfolding process
     * via org.apache.solr.common.util.JavaBinCodec.marshal. 
     * @param request
     * @param sqr
     * @return
     */
    public SolrDocumentList SolrQueryResponse2SolrDocumentList(final SolrQueryRequest req, final SolrQueryResponse rsp) {
        SolrDocumentList sdl = new SolrDocumentList();
        NamedList<?> nl = rsp.getValues();
        ResultContext resultContext = (ResultContext) nl.get("response");
        DocList response = resultContext == null ? new DocSlice(0, 0, new int[0], new float[0], 0, 0.0f) : resultContext.docs;
        sdl.setNumFound(response == null ? 0 : response.matches());
        sdl.setStart(response == null ? 0 : response.offset());
        if (response != null) {
            try {
                SolrIndexSearcher searcher = req.getSearcher();
                final int responseCount = response.size();
                DocIterator iterator = response.iterator();
                for (int i = 0; i < responseCount; i++) {
                    sdl.add(doc2SolrDoc(searcher.doc(iterator.nextDoc(), (Set<String>) null)));
                }
            } catch (IOException e) {
                ConcurrentLog.logException(e);
            }
        }
        return sdl;
    }
    
    public SolrDocument doc2SolrDoc(Document doc) {
        SolrDocument solrDoc = new SolrDocument();
        for (IndexableField field : doc) {
            String fieldName = field.name();
            SchemaField sf = this.core.getLatestSchema().getFieldOrNull(fieldName);
            Object val = null;
            try {
                FieldType ft = null;
                if (sf != null) ft = sf.getType();
                if (ft == null) {
                    BytesRef bytesRef = field.binaryValue();
                    if (bytesRef != null) {
                        if (bytesRef.offset == 0 && bytesRef.length == bytesRef.bytes.length) {
                            val = bytesRef.bytes;
                        } else {
                            final byte[] bytes = new byte[bytesRef.length];
                            System.arraycopy(bytesRef.bytes, bytesRef.offset, bytes, 0, bytesRef.length);
                            val = bytes;
                        }
                    } else {
                        val = field.stringValue();
                    }
                } else {
                    val = ft.toObject(field);
                }
            } catch (Throwable e) {
                continue;
            }

            if (sf != null && sf.multiValued() && !solrDoc.containsKey(fieldName)) {
                ArrayList<Object> l = new ArrayList<Object>();
                l.add(val);
                solrDoc.addField(fieldName, l);
            } else {
                solrDoc.addField(fieldName, val);
            }
        }
        return solrDoc;
    }

    
    /**
     * the usage of getResponseByParams is disencouraged for the embedded Solr connector. Please use request(SolrParams) instead.
     * Reason: Solr makes a very complex folding/unfolding including data compression for SolrQueryResponses.
     */
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
            if (rsp != null) if (log.isFine()) log.fine(rsp.getResults().getNumFound() + " results for q=" + q);
            return rsp;
        } catch (final SolrServerException e) {
            throw new IOException(e);
        } catch (final Throwable e) {
            throw new IOException("Error executing query", e);
        }
    }

    /**
     * get the solr document list from a query response
     * This differs from getResponseByParams in such a way that it does only create the fields of the response but
     * never search snippets and there are also no facets generated.
     * @param params
     * @return
     * @throws IOException
     * @throws SolrException
     */
    @Override
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams params) throws IOException, SolrException {
        SolrQueryRequest req = this.request(params);
        SolrQueryResponse response = null;
        try {
            response = this.query(req);
            if (response == null) throw new IOException("response == null");
            return SolrQueryResponse2SolrDocumentList(req, response);
        } finally {
            req.close();
            SolrRequestInfo.clearRequestInfo();
        }
    }

    @Override
    public long getDocumentCountByParams(ModifiableSolrParams params) throws IOException, SolrException {
        SolrQueryRequest req = this.request(params);
        SolrQueryResponse response = null;
        try {
            response = this.query(req);
            if (response == null) throw new IOException("response == null");
            NamedList<?> nl = response.getValues();
            ResultContext resultContext = (ResultContext) nl.get("response");
            return resultContext == null ? 0 : resultContext.docs.matches();
        } finally {
            req.close();
            SolrRequestInfo.clearRequestInfo();
        }
    }
    
    private class DocListSearcher {
        private SolrQueryRequest request;
        private DocList response;

        public DocListSearcher(final String querystring, String sort, final int offset, final int count, final String ... fields) {
            // construct query
            final SolrQuery params = AbstractSolrConnector.getSolrQuery(querystring, sort, offset, count, fields);
            
            // query the server
            this.request = EmbeddedSolrConnector.this.request(params);
            SolrQueryResponse rsp = query(request);
            NamedList<?> nl = rsp.getValues();
            ResultContext resultContext = (ResultContext) nl.get("response");
            if (resultContext == null) log.warn("DocListSearcher: no response for query '" + querystring + "'");
            this.response = resultContext == null ? new DocSlice(0, 0, new int[0], new float[0], 0, 0.0f) : resultContext.docs;
        }
        public void close() {
            if (this.request != null) this.request.close();
            this.request = null;
            this.response = null;
        }
        @Override
        protected void finalize() throws Throwable {
            try {close();} finally {super.finalize();}
        }
    }
    
    @Override
    public long getCountByQuery(String querystring) {
    	int numFound = 0;
    	DocListSearcher docListSearcher = null;
        try {
        	docListSearcher = new DocListSearcher(querystring, null, 0, 0, CollectionSchema.id.getSolrFieldName());
        	numFound = docListSearcher.response.matches();
        } finally { 
        	if (docListSearcher != null) docListSearcher.close();
        }
        return numFound;
    }

    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return the load date if any entry in solr exists, -1 otherwise
     * @throws IOException
     */
    @Override
    public Metadata getMetadata(String id) {
        int responseCount = 0;
        DocListSearcher docListSearcher = null;
        try {
            docListSearcher = new DocListSearcher("{!raw f=" + CollectionSchema.id.getSolrFieldName() + "}" + id, null, 0, 1, CollectionSchema.id.getSolrFieldName(), CollectionSchema.load_date_dt.getSolrFieldName());
            responseCount = docListSearcher.response.size();
            if (responseCount == 0) return null;
            SolrIndexSearcher searcher = docListSearcher.request.getSearcher();
            DocIterator iterator = docListSearcher.response.iterator();
            //for (int i = 0; i < responseCount; i++) {
            Document doc = searcher.doc(iterator.nextDoc(), AbstractSolrConnector.SOLR_ID_and_LOAD_DATE_FIELDS);
            if (doc == null) return null;
            return AbstractSolrConnector.getMetadata(doc);
            //}
        } catch (Throwable e) {} finally {
            if (docListSearcher != null) docListSearcher.close();
        }
        return null;
    }
    
    @Override
    public BlockingQueue<String> concurrentIDsByQuery(final String querystring, final String sort, final int offset, final int maxcount, final long maxtime, final int buffersize, final int concurrency) {
        final BlockingQueue<String> queue = buffersize <= 0 ? new LinkedBlockingQueue<String>() : new ArrayBlockingQueue<String>(buffersize);
        final long endtime = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final Thread t = new Thread() {
            @Override
            public void run() {
                this.setName("EmbeddedSolrConnector.concurrentIDsByQuery(" + querystring + ")");
                int o = offset, responseCount = 0;
                DocListSearcher docListSearcher = null;
                while (System.currentTimeMillis() < endtime) {
                    try {
                    	responseCount = 0;
                        docListSearcher = new DocListSearcher(querystring, sort, o, pagesize, CollectionSchema.id.getSolrFieldName());
                        responseCount = docListSearcher.response.size();
                        SolrIndexSearcher searcher = docListSearcher.request.getSearcher();
                        DocIterator iterator = docListSearcher.response.iterator();
                        for (int i = 0; i < responseCount; i++) {
                            Document doc = searcher.doc(iterator.nextDoc(), SOLR_ID_FIELDS);
                            try {queue.put(doc.get(CollectionSchema.id.getSolrFieldName()));} catch (final InterruptedException e) {break;}
                        }
                    } catch (final SolrException e) {
                        break;
                    } catch (IOException e) {
                    } finally {
                        if (docListSearcher != null) docListSearcher.close();
                    }
                    if (responseCount < pagesize) break;
                    o += pagesize;
                }
                try {queue.put(AbstractSolrConnector.POISON_ID);} catch (final InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }
}
