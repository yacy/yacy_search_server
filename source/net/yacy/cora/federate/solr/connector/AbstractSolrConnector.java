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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.LookAheadIterator;
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

    public final static SolrDocument POISON_DOCUMENT = new SolrDocument();
    public final static String POISON_ID = "POISON_ID";
    public final static SolrQuery catchallQuery = new SolrQuery();
    static {
        catchallQuery.setQuery("*:*");
        catchallQuery.setFields(CollectionSchema.id.getSolrFieldName());
        catchallQuery.setRows(0);
        catchallQuery.setStart(0);
    }
    public final static SolrQuery catchSuccessQuery = new SolrQuery();
    static {
        //catchSuccessQuery.setQuery("-" + CollectionSchema.failreason_t.getSolrFieldName() + ":[* TO *]");
        catchSuccessQuery.setQuery("*:*"); // failreason_t is only available for core collection1
        catchSuccessQuery.setFields(CollectionSchema.id.getSolrFieldName());
        catchSuccessQuery.setRows(0);
        catchSuccessQuery.setStart(0);
    }
    private final static int pagesize = 100;

    @Override
    public boolean exists(final String fieldName, final String key) throws IOException {
        if (fieldName == null) return false;
        try {
            long count = getQueryCount(fieldName + ":\"" + key + "\"");
            return count > 0;
        } catch (final Throwable e) {
            return false;
        }
    }
    
    @Override
    public Object getFieldById(final String key, final String field) throws IOException {
        SolrDocument doc = getById(key, field);
        if (doc == null) return null;
        return doc.getFieldValue(field);
    }
    
    /**
     * Get a query result from solr as a stream of documents.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_DOCUMENT is returned.
     * The method returns immediately and feeds the search results into the queue
     * @param querystring the solr query string
     * @param offset first result offset
     * @param maxcount the maximum number of results
     * @param maxtime the maximum time in milliseconds
     * @param buffersize the size of an ArrayBlockingQueue; if <= 0 then a LinkedBlockingQueue is used
     * @return a blocking queue which is terminated  with AbstractSolrConnector.POISON_DOCUMENT as last element
     */
    @Override
    public BlockingQueue<SolrDocument> concurrentQuery(final String querystring, final int offset, final int maxcount, final long maxtime, final int buffersize, final String ... fields) {
        final BlockingQueue<SolrDocument> queue = buffersize <= 0 ? new LinkedBlockingQueue<SolrDocument>() : new ArrayBlockingQueue<SolrDocument>(buffersize);
        final long endtime = System.currentTimeMillis() + maxtime;
        final Thread t = new Thread() {
            @Override
            public void run() {
                int o = offset;
                while (System.currentTimeMillis() < endtime) {
                    try {
                        SolrDocumentList sdl = query(querystring, o, pagesize, fields);
                        for (SolrDocument d: sdl) {
                            try {queue.put(d);} catch (InterruptedException e) {break;}
                        }
                        if (sdl.size() < pagesize) break;
                        o += pagesize;
                    } catch (SolrException e) {
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
                try {queue.put(AbstractSolrConnector.POISON_DOCUMENT);} catch (InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }

    @Override
    public BlockingQueue<String> concurrentIDs(final String querystring, final int offset, final int maxcount, final long maxtime) {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
        final long endtime = System.currentTimeMillis() + maxtime;
        final Thread t = new Thread() {
            @Override
            public void run() {
                int o = offset;
                while (System.currentTimeMillis() < endtime) {
                    try {
                        SolrDocumentList sdl = query(querystring, o, pagesize, CollectionSchema.id.getSolrFieldName());
                        for (SolrDocument d: sdl) {
                            try {queue.put((String) d.getFieldValue(CollectionSchema.id.getSolrFieldName()));} catch (InterruptedException e) {break;}
                        }
                        if (sdl.size() < pagesize) break;
                        o += pagesize;
                    } catch (SolrException e) {
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
                try {queue.put(AbstractSolrConnector.POISON_ID);} catch (InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }

    @Override
    public Iterator<String> iterator() {
        final BlockingQueue<String> queue = concurrentIDs("*:*", 0, Integer.MAX_VALUE, 60000);
        return new LookAheadIterator<String>() {
            @Override
            protected String next0() {
                try {
                    String s = queue.poll(60000, TimeUnit.MILLISECONDS);
                    if (s == AbstractSolrConnector.POISON_ID) return null;
                    return s;
                } catch (InterruptedException e) {
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
        params.setFields(CollectionSchema.id.getSolrFieldName());

        // query the server
        QueryResponse rsp = query(params);
        final SolrDocumentList docs = rsp.getResults();
        return docs == null ? 0 : docs.getNumFound();
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
    
    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument solrdoc: solrdocs) {
           add(solrdoc);
        }
    }
}
