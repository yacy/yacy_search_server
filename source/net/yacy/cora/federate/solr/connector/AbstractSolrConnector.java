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
import net.yacy.search.schema.CollectionSchema;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;

public abstract class AbstractSolrConnector implements SolrConnector {

    public final static SolrDocument POISON_DOCUMENT = new SolrDocument();
    public final static String POISON_ID = "POISON_ID";
    public final static String CATCHALL_TERM = "*:*";
    public final static SolrQuery catchallQuery = new SolrQuery();
    static {
        catchallQuery.setQuery(CATCHALL_TERM);
        catchallQuery.setFields(CollectionSchema.id.getSolrFieldName());
        catchallQuery.setRows(0);
        catchallQuery.setStart(0);
    }
    public final static SolrQuery catchSuccessQuery = new SolrQuery();
    static {
        //catchSuccessQuery.setQuery("-" + CollectionSchema.failreason_s.getSolrFieldName() + ":[* TO *]");
        catchSuccessQuery.setQuery(CATCHALL_TERM); // failreason_s is only available for core collection1
        catchSuccessQuery.setFields(CollectionSchema.id.getSolrFieldName());
        catchSuccessQuery.clearSorts();
        catchSuccessQuery.setIncludeScore(false);
        catchSuccessQuery.setRows(0);
        catchSuccessQuery.setStart(0);
    }
    protected final static int pagesize = 100;
    
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
    public BlockingQueue<SolrDocument> concurrentDocumentsByQuery(final String querystring, final int offset, final int maxcount, final long maxtime, final int buffersize, final String ... fields) {
        final BlockingQueue<SolrDocument> queue = buffersize <= 0 ? new LinkedBlockingQueue<SolrDocument>() : new ArrayBlockingQueue<SolrDocument>(buffersize);
        final long endtime = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final Thread t = new Thread() {
            @Override
            public void run() {
                int o = offset;
                int count = 0;
                while (System.currentTimeMillis() < endtime && count < maxcount) {
                    try {
                        SolrDocumentList sdl = getDocumentListByQuery(querystring, o, pagesize, fields);
                        for (SolrDocument d: sdl) {
                            try {queue.put(d);} catch (final InterruptedException e) {break;}
                            count++;
                        }
                        if (sdl.size() <= 0) break;
                        o += sdl.size();
                    } catch (final SolrException e) {
                        break;
                    } catch (final IOException e) {
                        break;
                    }
                }
                try {queue.put(AbstractSolrConnector.POISON_DOCUMENT);} catch (final InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }

    @Override
    public BlockingQueue<String> concurrentIDsByQuery(final String querystring, final int offset, final int maxcount, final long maxtime) {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
        final long endtime = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final Thread t = new Thread() {
            @Override
            public void run() {
                int o = offset;
                while (System.currentTimeMillis() < endtime) {
                    try {
                        SolrDocumentList sdl = getDocumentListByQuery(querystring, o, pagesize, CollectionSchema.id.getSolrFieldName());
                        for (SolrDocument d: sdl) {
                            try {queue.put((String) d.getFieldValue(CollectionSchema.id.getSolrFieldName()));} catch (final InterruptedException e) {break;}
                        }
                        if (sdl.size() <= 0) break;
                        o += sdl.size();
                    } catch (final SolrException e) {
                        break;
                    } catch (final IOException e) {
                        break;
                    }
                }
                try {queue.put(AbstractSolrConnector.POISON_ID);} catch (final InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }

    @Override
    public Iterator<String> iterator() {
        final BlockingQueue<String> queue = concurrentIDsByQuery(CATCHALL_TERM, 0, Integer.MAX_VALUE, 60000);
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
    public SolrDocumentList getDocumentListByQuery(final String querystring, final int offset, final int count, final String ... fields) throws IOException {
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(querystring);
        params.setRows(count);
        params.setStart(offset);
        params.setFacet(false);
        params.clearSorts();
        if (fields.length > 0) params.setFields(fields);
        params.setIncludeScore(false);
        
        // query the server
        final SolrDocumentList docs = getDocumentListByParams(params);
        return docs;
    }

    @Override
    public long getDocumentCountByParams(ModifiableSolrParams params) throws IOException, SolrException {
        final SolrDocumentList sdl = getDocumentListByParams(params);
        return sdl == null ? 0 : sdl.getNumFound();
    }
    
    /**
     * check if a given document, identified by url hash as ducument id exists
     * @param id the url hash and document id
     * @return true if any entry in solr exists
     * @throws IOException
     */
    @Override
    public boolean existsById(String id) throws IOException {
        // construct raw query
        final SolrQuery params = new SolrQuery();
        //params.setQuery(CollectionSchema.id.getSolrFieldName() + ":\"" + id + "\"");
        params.setQuery("{!raw f=" + CollectionSchema.id.getSolrFieldName() + "}" + id);
        //params.set("defType", "raw");
        params.setRows(0);
        params.setStart(0);
        params.setFacet(false);
        params.clearSorts();
        params.setFields(CollectionSchema.id.getSolrFieldName());
        params.setIncludeScore(false);

        // query the server
        return getDocumentCountByParams(params) > 0;
    }

    /**
     * check a set of ids for existence.
     * @param ids a collection of document ids
     * @return a collection of a subset of the ids which exist in the index
     * @throws IOException
     */
    public Set<String> existsByIds(Set<String> ids) throws IOException {
        if (ids == null || ids.size() == 0) return new HashSet<String>();
        // construct raw query
        final SolrQuery params = new SolrQuery();
        //params.setQuery(CollectionSchema.id.getSolrFieldName() + ":\"" + id + "\"");
        StringBuilder sb = new StringBuilder(); // construct something like "({!raw f=id}Ij7B63g-gSHA) OR ({!raw f=id}PBcGI3g-gSHA)"
        for (String id: ids) {
            sb.append("({!raw f=").append(CollectionSchema.id.getSolrFieldName()).append('}').append(id).append(") OR ");
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 4); // cut off the last 'or'
        params.setQuery(sb.toString());
        //params.set("defType", "raw");
        params.setRows(ids.size()); // we want all lines
        params.setStart(0);
        params.setFacet(false);
        params.clearSorts();
        params.setFields(CollectionSchema.id.getSolrFieldName());
        params.setIncludeScore(false);

        // query the server
        final SolrDocumentList docs = getDocumentListByParams(params);
        // construct a new id list from that
        HashSet<String> idsr = new HashSet<String>();
        for (SolrDocument doc : docs) {
            idsr.add((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
        }
        return idsr;
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
        final SolrQuery query = new SolrQuery();
        assert id.length() == 12;
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
            throw new IOException(e.getMessage(), e);
        }
    }

}
