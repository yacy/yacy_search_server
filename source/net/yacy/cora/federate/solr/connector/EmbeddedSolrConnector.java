/**
 *  EmbeddedSolrConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 21.06.2012 at https://yacy.net
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
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

import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.cora.federate.solr.instance.SolrInstance;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

public class EmbeddedSolrConnector extends SolrServerConnector implements SolrConnector {

    public static final String SELECT = "/select";
    public static final String CONTEXT = "/solr";

    private final SearchHandler requestHandler;
    //private final SuggestComponent suggestHandler;
    private final EmbeddedInstance instance;
    private final SolrCore core;

    public EmbeddedSolrConnector(final EmbeddedInstance instance) {
        super();
        this.instance = instance;
        this.core = this.instance.getDefaultCore();
        this.requestHandler = new SearchHandler();
        this.requestHandler.init(new NamedList<>());
        this.requestHandler.inform(this.core);
        //this.suggestHandler = new SuggestComponent();
        //this.suggestHandler.init(new NamedList<Object>());
        //this.suggestHandler.inform(this.core);
        config();
        super.init(this.instance.getDefaultServer());

    }

    public EmbeddedSolrConnector(final EmbeddedInstance instance, final String coreName) {
        super();
        this.instance = instance;
        this.core = this.instance.getCore(coreName);
        this.requestHandler = new SearchHandler();
        this.requestHandler.init(new NamedList<>());
        this.requestHandler.inform(this.core);
        //this.suggestHandler = new SuggestComponent();
        //this.suggestHandler.init(new NamedList<Object>());
        //this.suggestHandler.inform(this.core);
        config();
        super.init(this.instance.getServer(coreName));
    }

    private void config() {
        // Define cache configuration parameters
        final Map<String, Object> cacheConfig = Map.of(
            "name", "documentCache",
            "class", "solr.LRUCache",
            "size", "10240",
            "initialSize", "512",
            "autowarmCount", "0"
        );

        // Retrieve the Solr configuration
        final SolrConfig solrConfig = this.core.getSolrConfig();
        // Get the list of plugin infos for SearchComponent
        final List<PluginInfo> pluginInfos = solrConfig.getPluginInfos(SearchComponent.class.getName());

        boolean changed = false;
        // Iterate over pluginInfos to find and update documentCache
        for (final PluginInfo pluginInfo : pluginInfos) {
            if (pluginInfo.name.equals("documentCache")) {
                pluginInfo.initArgs.addAll(cacheConfig);
                changed = true;
            }
        }

        // If documentCache is not found, create and add it
        if (!changed) {
            final Map<String, String> attrs = new HashMap<>();
            final PluginInfo pi = new PluginInfo("documentCache", attrs, new NamedList<>(cacheConfig), null);
            pluginInfos.add(pi);
        }

        // Ensure the changes are applied to Solr configuration if needed
        // This part may vary based on how SolrConfig applies updates
    }

    @Override
    public int hashCode() {
        return this.instance.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof EmbeddedSolrConnector && this.instance.equals(((EmbeddedSolrConnector) o).instance);
    }

    @Override
    public int bufferSize() {
        return 0;
    }

    @Override
    public void clearCaches() {
        final SolrConfig solrConfig = this.core.getSolrConfig();
        @SuppressWarnings("unchecked")
        final SolrCache<String, ?> fieldValueCache = solrConfig.fieldValueCacheConfig == null ? null : solrConfig.fieldValueCacheConfig.newInstance();
        if (fieldValueCache != null) fieldValueCache.clear();
        @SuppressWarnings("unchecked")
        final SolrCache<Query, DocSet> filterCache= solrConfig.filterCacheConfig == null ? null : solrConfig.filterCacheConfig.newInstance();
        if (filterCache != null) filterCache.clear();
        @SuppressWarnings("unchecked")
        final SolrCache<QueryResultKey, DocList> queryResultCache = solrConfig.queryResultCacheConfig == null ? null : solrConfig.queryResultCacheConfig.newInstance();
        if (queryResultCache != null) queryResultCache.clear();
        @SuppressWarnings("unchecked")
        final SolrCache<Integer, Document> documentCache = solrConfig.documentCacheConfig == null ? null : solrConfig.documentCacheConfig.newInstance();
        if (documentCache != null) documentCache.clear();
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
    public synchronized void close() {
        if (this.core != null && !this.core.isClosed()) try {this.commit(false);} catch (final Throwable e) {ConcurrentLog.logException(e);}
        try {super.close();} catch (final Throwable e) {ConcurrentLog.logException(e);}
        // we do NOT close the core here because that is closed if the enclosing instance is closed
        // do NOT uncomment the following line, which caused a "org.apache.solr.core.SolrCore Too many close [count:-1] on org.apache.solr.core.SolrCore@51af7c57" error
        // try {this.core.close();} catch (final Throwable e) {ConcurrentLog.logException(e);}
    }

    @Override
    public long getSize() {
        final RefCounted<SolrIndexSearcher> refCountedIndexSearcher = this.core.getSearcher();
        final SolrIndexSearcher searcher = refCountedIndexSearcher.get();
        final DirectoryReader reader = searcher.getIndexReader();
        final long numDocs = reader.numDocs();
        refCountedIndexSearcher.decref();
        return numDocs;
    }

    /**
     * get a new query request. MUST be closed after usage using close()
     * @param params
     * @return
     */
    public SolrQueryRequest request(final SolrParams params) {
        final SolrQueryRequest req = new SolrQueryRequestBase(this.core, params){};
        req.getContext().put("path", SELECT);
        req.getContext().put("webapp", CONTEXT);
        return req;
    }

    public SolrQueryResponse query(final SolrQueryRequest req) throws SolrException {
        final long startTime = System.currentTimeMillis();

        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        final String threadname = Thread.currentThread().getName();
        String ql = ""; try {ql = URLDecoder.decode(req.getParams().toString(), StandardCharsets.UTF_8.name());} catch (final UnsupportedEncodingException e) {}
        Thread.currentThread().setName("Embedded solr query: " + ql); // for debugging in Threaddump
        ConcurrentLog.fine("EmbeddedSolrConnector.query", "QUERY: " + ql);

        final SolrQueryResponse rsp = new SolrQueryResponse();
        final NamedList<Object> responseHeader = new SimpleOrderedMap<>();
        responseHeader.add("params", req.getOriginalParams().toNamedList());
        rsp.add("responseHeader", responseHeader);
        //SolrRequestInfo.setRequestInfo(new SolrRequestInfo(req, rsp));

        // send request to solr and create a result
        this.requestHandler.handleRequest(req, rsp);

        // get statistics and add a header with that
        final Exception exception = rsp.getException();
        final int status = exception == null ? 0 : exception instanceof SolrException ? ((SolrException) exception).code() : 500;
        responseHeader.add("status", status);
        responseHeader.add("QTime",(int) (System.currentTimeMillis() - startTime));

        Thread.currentThread().setName(threadname);
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
        final SolrDocumentList sdl = new SolrDocumentList();
        final NamedList<?> nl = rsp.getValues();
        final ResultContext resultContext = (ResultContext) nl.get("response");
        final DocList response = resultContext == null ? new DocSlice(0, 0, new int[0], new float[0], 0, 0.0f, TotalHits.Relation.EQUAL_TO) : resultContext.getDocList();
        sdl.setNumFound(response == null ? 0 : response.matches());
        sdl.setStart(response == null ? 0 : response.offset());
        final String originalName = Thread.currentThread().getName();
        if (response != null) {
            try {
                final SolrIndexSearcher searcher = req.getSearcher();
                final int responseCount = response.size();
                final DocIterator iterator = response.iterator();
                for (int i = 0; i < responseCount; i++) {
                    final int docid = iterator.nextDoc();
                    Thread.currentThread().setName("EmbeddedSolrConnector.SolrQueryResponse2SolrDocumentList: " + docid);
                    final Document responsedoc = searcher.doc(docid, (Set<String>) null);
                    final SolrDocument sordoc = this.doc2SolrDoc(responsedoc);
                    sdl.add(sordoc);
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
        Thread.currentThread().setName(originalName);
        return sdl;
    }

    /**
     * The following schemaFieldCache is a hack-patch of a Solr internal request which is really slow.
     * The Solr-internal method is flexible because it may respond on a real-time schema change, but that
     * effectively never happens. In our case the schema declaration against Solr never changes.
     */
    private final Map<String, SchemaField> schemaFieldCache = new ConcurrentHashMap<>();
    private final SchemaField getSchemaField(final String fieldName) {
        SchemaField sf = this.schemaFieldCache.get(fieldName);
        if (sf == null) {
            sf = this.core.getLatestSchema().getFieldOrNull(fieldName);
            this.schemaFieldCache.put(fieldName, sf);
        }
        return sf;
    }

    public SolrDocument doc2SolrDoc(final Document doc) {
        final SolrDocument solrDoc = new SolrDocument();
        for (final IndexableField field : doc) {
            final String fieldName = field.name();
            final SchemaField sf = this.getSchemaField(fieldName); // hack-patch of this.core.getLatestSchema().getFieldOrNull(fieldName); makes it a lot faster!!
            Object val = null;
            try {
                FieldType ft = null;
                if (sf != null) ft = sf.getType();
                if (ft == null) {
                    final BytesRef bytesRef = field.binaryValue();
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
            } catch (final Throwable e) {
                continue;
            }

            if (sf != null && sf.multiValued() && !solrDoc.containsKey(fieldName)) {
                final ArrayList<Object> l = new ArrayList<>();
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
    public QueryResponse getResponseByParams(final ModifiableSolrParams params) throws IOException {
        if (this.server == null) throw new IOException("server disconnected");
        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        final String threadname = Thread.currentThread().getName();
        String ql = "";
        try {ql = URLDecoder.decode(params.toString(), StandardCharsets.UTF_8.name());} catch (UnsupportedEncodingException|IllegalStateException e) {e.printStackTrace();}
        Thread.currentThread().setName("Embedded.getResponseByParams solr query: q=" + ql);
        ConcurrentLog.info("EmbeddedSolrConnector.getResponseByParams", "QUERY: " + ql);
        //System.out.println("EmbeddedSolrConnector.getResponseByParams * QUERY: " + ql); System.out.println("STACKTRACE: " + ConcurrentLog.stackTrace());
        QueryResponse rsp;
        try {
            // System.out.println("*** PARAMS: " + params);
            rsp = this.server.query(params);
            Thread.currentThread().setName(threadname);
            if (rsp != null) if (log.isFine()) log.fine(rsp.getResults().getNumFound() + " results for " + ql);
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
    public SolrDocumentList getDocumentListByParams(final ModifiableSolrParams params) throws IOException, SolrException {
        final SolrQueryRequest req = this.request(params);
        SolrQueryResponse response = null;
        final String q = params.get(CommonParams.Q);
        final String fq = params.get(CommonParams.FQ);
        final String sort = params.get(CommonParams.SORT);
        final String threadname = Thread.currentThread().getName();
        try {
            if (q != null) Thread.currentThread().setName("Embedded.getDocumentListByParams solr query: q = " + q + (fq == null ? "" : ", fq = " + fq) + (sort == null ? "" : ", sort = " + sort)); // for debugging in Threaddump
            response = this.query(req);
            if (q != null) Thread.currentThread().setName(threadname);
            if (response == null) throw new IOException("response == null");
            return this.SolrQueryResponse2SolrDocumentList(req, response);
        } finally {
            req.close();
        }
    }


    private class DocListSearcher implements AutoCloseable {
        private SolrQueryRequest request;
        private DocList response;

        public DocListSearcher(final String querystring, final String sort, final int offset, final int count, final String ... fields) {
            // construct query
            final SolrQuery params = AbstractSolrConnector.getSolrQuery(querystring, sort, offset, count, fields);

            // query the server
            this.request = EmbeddedSolrConnector.this.request(params);
            final SolrQueryResponse rsp = EmbeddedSolrConnector.this.query(this.request);
            final NamedList<?> nl = rsp.getValues();
            final ResultContext resultContext = (ResultContext) nl.get("response");
            if (resultContext == null) log.warn("DocListSearcher: no response for query '" + querystring + "'");
            this.response = resultContext == null ? new DocSlice(0, 0, new int[0], new float[0], 0, 0.0f, TotalHits.Relation.EQUAL_TO) : resultContext.getDocList();
        }

        @Override
        public void close() {
            if (this.request != null) this.request.close();
            this.request = null;
            this.response = null;
        }
    }

    @Override
    public long getCountByQuery(final String querystring) {
    	long numFound = 0;
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
     * @return whether the documents exists
     */
    @Override
    public boolean exists(final String id) {
        final String query = "{!cache=false raw f=" + CollectionSchema.id.getSolrFieldName() + "}" + id;
        try (DocListSearcher docListSearcher = new DocListSearcher(query, null, 0, 0, CollectionSchema.id.getSolrFieldName())) {
            return docListSearcher.response.matches() > 0l;
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
            return false;
        }
    }

    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return the load date if any entry in solr exists, null otherwise
     * @throws IOException
     */
    @Override
    public String getURL(final String id) throws IOException {
        int responseCount = 0;
        DocListSearcher docListSearcher = null;
        try {
            docListSearcher = new DocListSearcher("{!cache=false raw f=" + CollectionSchema.id.getSolrFieldName() + "}" + id, null, 0, 1, CollectionSchema.id.getSolrFieldName(), CollectionSchema.load_date_dt.getSolrFieldName());
            responseCount = docListSearcher.response.size();
            if (responseCount == 0) return null;
            final SolrIndexSearcher searcher = docListSearcher.request.getSearcher();
            final DocIterator iterator = docListSearcher.response.iterator();
            //for (int i = 0; i < responseCount; i++) {
            final Document doc = searcher.doc(iterator.nextDoc(), AbstractSolrConnector.SOLR_ID_and_LOAD_DATE_FIELDS);
            if (doc == null) return null;
            return AbstractSolrConnector.getURL(doc);
            //}
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
            throw new IOException(e.getMessage());
        } finally {
            if (docListSearcher != null) docListSearcher.close();
        }
    }
    /*
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
                        docListSearcher = new DocListSearcher(querystring, sort, o, pagesize_ids, CollectionSchema.id.getSolrFieldName());
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
                    if (responseCount < pagesize_ids) break;
                    o += pagesize_ids;
                }
                try {queue.put(AbstractSolrConnector.POISON_ID);} catch (final InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }
    */
}
