/**
 *  AbstractSolrConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 27.06.2012 at http://yacy.net
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.kelondro.data.word.Word;
import net.yacy.search.schema.CollectionSchema;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;

public abstract class AbstractSolrConnector implements SolrConnector {

    protected static Set<String> SOLR_ID_FIELDS = new HashSet<String>();
    protected static Set<String> SOLR_ID_and_LOAD_DATE_FIELDS = new HashSet<String>();
    static {
        SOLR_ID_FIELDS.add(CollectionSchema.id.getSolrFieldName());
        SOLR_ID_and_LOAD_DATE_FIELDS.add(CollectionSchema.id.getSolrFieldName());
        SOLR_ID_and_LOAD_DATE_FIELDS.add(CollectionSchema.load_date_dt.getSolrFieldName());
    }
    
    public final static SolrDocument POISON_DOCUMENT = new SolrDocument();
    public final static String POISON_ID = "POISON_ID";
    public final static String CATCHALL_TERM = "[* TO *]";
    public final static String CATCHALL_DTERM = ":" + CATCHALL_TERM;
    public final static String CATCHALL_QUERY = "*:*";
    public final static SolrQuery catchallQuery = new SolrQuery();
    static {
        catchallQuery.setQuery(CATCHALL_QUERY);
        catchallQuery.setFields(CollectionSchema.id.getSolrFieldName());
        catchallQuery.setRows(0);
        catchallQuery.setStart(0);
    }
    public final static SolrQuery catchSuccessQuery = new SolrQuery();
    static {
        //catchSuccessQuery.setQuery("-" + CollectionSchema.failreason_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM);
        catchSuccessQuery.setQuery(CATCHALL_QUERY); // failreason_s is only available for core collection1
        catchSuccessQuery.setFields(CollectionSchema.id.getSolrFieldName());
        catchSuccessQuery.clearSorts();
        catchSuccessQuery.setIncludeScore(false);
        catchSuccessQuery.setRows(0);
        catchSuccessQuery.setStart(0);
    }
    protected final static int pagesize = 100;
    
    protected static Metadata getMetadata(final Object doc) {
        if (doc == null) return null;
        Object d = null;
        String url = null;
        if (doc instanceof SolrInputDocument) {
            d = ((SolrInputDocument) doc).getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
            url = (String) ((SolrInputDocument) doc).getFieldValue(CollectionSchema.sku.getSolrFieldName());
        }
        if (doc instanceof SolrDocument) {
            d = ((SolrDocument) doc).getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
            url = (String) ((SolrDocument) doc).getFieldValue(CollectionSchema.sku.getSolrFieldName());
        }
        if (doc instanceof org.apache.lucene.document.Document) {
            String ds = ((org.apache.lucene.document.Document) doc).get(CollectionSchema.load_date_dt.getSolrFieldName());
            try {
                d = Long.parseLong(ds);
            } catch (NumberFormatException e) {
                d = -1l;
            }
            url = ((org.apache.lucene.document.Document) doc).get(CollectionSchema.sku.getSolrFieldName());
        }
        if (d == null) return null;
        long date = -1;
        if (d instanceof Long) date = ((Long) d).longValue();
        if (d instanceof Date) date = ((Date) d).getTime();
        return new Metadata(url, date);
    }

    /**
     * check if fields contain id and load_date_dt date
     * @param fields
     * @return fields with added id and load_date_dt if necessary
     */
    protected static String[] ensureEssentialFieldsIncluded(String[] fields) {
        if (fields != null && fields.length > 0) {
            Set<String> f = new HashSet<String>();
            for (String s: fields) f.add(s);
            f.add(CollectionSchema.id.getSolrFieldName());
            f.add(CollectionSchema.load_date_dt.getSolrFieldName());
            fields = f.toArray(new String[f.size()]);
        }
        return fields;
    }
    
    /**
     * Get a query result from solr as a stream of documents.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_DOCUMENT is returned.
     * The method returns immediately and feeds the search results into the queue
     * @param querystring the solr query string
     * @param sort the solr sort string, may be null to be not used
     * @param offset first result offset
     * @param maxcount the maximum number of results
     * @param maxtime the maximum time in milliseconds
     * @param buffersize the size of an ArrayBlockingQueue; if <= 0 then a LinkedBlockingQueue is used
     * @param concurrency is the number of AbstractSolrConnector.POISON_DOCUMENT entries to add at the end of the feed
     * @return a blocking queue which is terminated  with AbstractSolrConnector.POISON_DOCUMENT as last element
     */
    @Override
    public BlockingQueue<SolrDocument> concurrentDocumentsByQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency,
            final String ... fields) {
        final BlockingQueue<SolrDocument> queue = buffersize <= 0 ? new LinkedBlockingQueue<SolrDocument>() : new ArrayBlockingQueue<SolrDocument>(buffersize);
        final long endtime = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final Thread t = new Thread() {
            @Override
            public void run() {
                this.setName("AbstractSolrConnector:concurrentDocumentsByQuery(" + querystring + ")");
                int o = offset;
                int count = 0;
                while (System.currentTimeMillis() < endtime && count < maxcount) {
                    try {
                        SolrDocumentList sdl = getDocumentListByQuery(querystring, sort, o, Math.min(maxcount, pagesize), fields);
                        for (SolrDocument d: sdl) {
                            try {queue.put(d);} catch (final InterruptedException e) {break;}
                            count++;
                        }
                        if (sdl.size() < pagesize) break;
                        o += sdl.size();
                    } catch (final SolrException e) {
                        break;
                    } catch (final IOException e) {
                        break;
                    }
                }
                for (int i = 0; i < concurrency; i++) {
                    try {queue.put(AbstractSolrConnector.POISON_DOCUMENT);} catch (final InterruptedException e1) {}
                }
            }
        };
        t.start();
        return queue;
    }

    @Override
    public BlockingQueue<String> concurrentIDsByQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency) {
        final BlockingQueue<String> queue = buffersize <= 0 ? new LinkedBlockingQueue<String>() : new ArrayBlockingQueue<String>(buffersize);
        final long endtime = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final Thread t = new Thread() {
            @Override
            public void run() {
                this.setName("AbstractSolrConnector:concurrentIDsByQuery(" + querystring + ")");
                int o = offset;
                while (System.currentTimeMillis() < endtime) {
                    try {
                        SolrDocumentList sdl = getDocumentListByQuery(querystring, sort, o, Math.min(maxcount, pagesize), CollectionSchema.id.getSolrFieldName());
                        for (SolrDocument d: sdl) {
                            try {queue.put((String) d.getFieldValue(CollectionSchema.id.getSolrFieldName()));} catch (final InterruptedException e) {break;}
                        }
                        if (sdl.size() < pagesize) break;
                        o += sdl.size();
                    } catch (final SolrException e) {
                        break;
                    } catch (final IOException e) {
                        break;
                    }
                }
                for (int i = 0; i < concurrency; i++) {
                    try {queue.put(AbstractSolrConnector.POISON_ID);} catch (final InterruptedException e1) {}
                }
            }
        };
        t.start();
        return queue;
    }

    @Override
    public Iterator<String> iterator() {
        final BlockingQueue<String> queue = concurrentIDsByQuery(CATCHALL_QUERY, null, 0, Integer.MAX_VALUE, 60000, 2 * pagesize, 1);
        return new LookAheadIterator<String>() {
            @Override
            protected String next0() {
                try {
                    String s = queue.poll(60000, TimeUnit.MILLISECONDS);
                    if (s == AbstractSolrConnector.POISON_ID) return null;
                    return s;
                } catch (final InterruptedException e) {
                    return null;
                }
            }

        };
    }
    
    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList getDocumentListByQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int count,
            final String ... fields) throws IOException {
        // construct query
        final SolrQuery params = getSolrQuery(querystring, sort, offset, count, fields);
        
        // query the server
        final SolrDocumentList docs = getDocumentListByParams(params);
        return docs;
    }

    public static SolrQuery getSolrQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int count,
            final String ... fields) {
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(querystring);
        params.clearSorts();
        if (sort != null) {
            params.set("sort", sort);
        }
        params.setRows(count);
        params.setStart(offset);
        params.setFacet(false);
        if (fields.length > 0) params.setFields(fields);
        params.setIncludeScore(false);
        
        return params;
    }
    
    
    @Override
    public long getDocumentCountByParams(ModifiableSolrParams params) throws IOException, SolrException {
        final SolrDocumentList sdl = getDocumentListByParams(params);
        return sdl == null ? 0 : sdl.getNumFound();
    }
    
    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return metadata if any entry in solr exists, null otherwise
     * @throws IOException
     */
    @Override
    public Metadata getMetadata(String id) throws IOException {
        // construct raw query
        final SolrQuery params = new SolrQuery();
        //params.setQuery(CollectionSchema.id.getSolrFieldName() + ":\"" + id + "\"");
        params.setQuery("{!raw f=" + CollectionSchema.id.getSolrFieldName() + "}" + id);
        //params.set("defType", "raw");
        params.setRows(1);
        params.setStart(0);
        params.setFacet(false);
        params.clearSorts();
        params.setFields(CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName(), CollectionSchema.load_date_dt.getSolrFieldName());
        params.setIncludeScore(false);

        // query the server
        final SolrDocumentList sdl = getDocumentListByParams(params);
        if (sdl == null || sdl.getNumFound() <= 0) return null;
        SolrDocument doc = sdl.iterator().next();
        Metadata md = getMetadata(doc);
        return md;
    }
    
    /**
     * get the number of results when this query is done.
     * This should only be called if the actual result is never used, and only the count is interesting
     * @param querystring
     * @return the number of results for this query
     */
    @Override
    public long getCountByQuery(String querystring) throws IOException {
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(querystring);
        params.setRows(0);
        params.setStart(0);
        params.setFacet(false);
        params.clearSorts();
        params.setFields(CollectionSchema.id.getSolrFieldName());
        params.setIncludeScore(false);

        // query the server
        return getDocumentCountByParams(params);
    }

    /**
     * get facets of the index: a list of lists with values that are most common in a specific field
     * @param query a query which is performed to get the facets
     * @param fields the field names which are selected as facet
     * @param maxresults the maximum size of the resulting maps
     * @return a map with key = facet field name, value = an ordered map of field values for that field
     * @throws IOException
     */
    @Override
    public Map<String, ReversibleScoreMap<String>> getFacets(String query, int maxresults, final String ... fields) throws IOException {
        // construct query
        assert fields.length > 0;
        final SolrQuery params = new SolrQuery();
        params.setQuery(query);
        params.setRows(0);
        params.setStart(0);
        params.setFacet(true);
        params.setFacetMinCount(1); // there are many 0-count facets in the uninverted index cache
        params.setFacetLimit(maxresults);
        params.setFacetSort(FacetParams.FACET_SORT_COUNT);
        params.setParam(FacetParams.FACET_METHOD, FacetParams.FACET_METHOD_fcs);
        params.setFields(fields);
        params.clearSorts();
        params.setIncludeScore(false);
        for (String field: fields) params.addFacetField(field);
        
        // query the server
        QueryResponse rsp = getResponseByParams(params);
        Map<String, ReversibleScoreMap<String>> facets = new HashMap<String, ReversibleScoreMap<String>>(fields.length);
        for (String field: fields) {
            FacetField facet = rsp.getFacetField(field);
            ReversibleScoreMap<String> result = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
            List<Count> values = facet.getValues();
            if (values == null) continue;
            for (Count ff: values) if (ff.getCount() > 0) result.set(ff.getName(), (int) ff.getCount());
            facets.put(field, result);
        }
        return facets;
    }
    
    @Override
    public SolrDocument getDocumentById(final String id, final String ... fields) throws IOException {
        assert id.length() == Word.commonHashLength : "wrong id: " + id;
        final SolrQuery query = new SolrQuery();
        // construct query
        StringBuilder sb = new StringBuilder(23);
        sb.append("{!raw f=").append(CollectionSchema.id.getSolrFieldName()).append('}').append(id);
        query.setQuery(sb.toString());
        query.clearSorts();
        query.setRows(1);
        query.setStart(0);
        if (fields.length > 0) query.setFields(fields);
        query.setIncludeScore(false);

        // query the server
        try {
            final SolrDocumentList docs = getDocumentListByParams(query);
            if (docs == null || docs.isEmpty()) return null;
            return docs.get(0);
        } catch (final Throwable e) {
            clearCaches(); // we clear the in case that this is caused by OOM
            throw new IOException(e.getMessage(), e);
        }
    }

}
