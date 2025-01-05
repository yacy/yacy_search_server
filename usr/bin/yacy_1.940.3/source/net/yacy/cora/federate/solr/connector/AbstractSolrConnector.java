/**
 *  AbstractSolrConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 27.06.2012 at https://yacy.net
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
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.FacetParams;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.kelondro.data.word.Word;
import net.yacy.search.schema.CollectionSchema;

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

    protected final static int pagesize_docs = 100;
    protected final static int pagesize_ids = 1000;

    protected static String getURL(final Object doc) {
        if (doc == null) return null;
        String url = null;
        if (doc instanceof SolrInputDocument) {
            url = (String) ((SolrInputDocument) doc).getFieldValue(CollectionSchema.sku.getSolrFieldName());
        }
        if (doc instanceof SolrDocument) {
            url = (String) ((SolrDocument) doc).getFieldValue(CollectionSchema.sku.getSolrFieldName());
        }
        if (doc instanceof org.apache.lucene.document.Document) {
            url = ((org.apache.lucene.document.Document) doc).get(CollectionSchema.sku.getSolrFieldName());
        }
        if (url == null) return null;
        return url;
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
     * Get results from a solr query as a stream of documents.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_DOCUMENT is returned.
     * The method returns immediately and feeds the search results into the queue
     * @param querystring the solr query string
     * @param sort the solr sort string, may be null to be not used
     * @param offset first result offset
     * @param maxcount the maximum number of results
     * @param maxtime the maximum time in milliseconds
     * @param buffersize the size of an ArrayBlockingQueue; if <= 0 then a LinkedBlockingQueue is used
     * @param concurrency is the number of AbstractSolrConnector.POISON_DOCUMENT entries to add at the end of the feed
     * @param prefetchIDs if true, then first all IDs are fetched and then all documents are queries by the ID. If false then documents are retrieved directly
     * @param fields list of fields
     * @return a blocking queue which is terminated with AbstractSolrConnector.POISON_DOCUMENT as last element
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
            final boolean prefetchIDs,
            final String ... fields) {
        List<String> querystrings = new ArrayList<>(1);
        querystrings.add(querystring);
        return concurrentDocumentsByQueries(querystrings, sort, offset, maxcount, maxtime, buffersize, concurrency, prefetchIDs, fields);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockingQueue<SolrDocument> concurrentDocumentsByQueries(
            final List<String> querystrings,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency,
            final boolean prefetchIDs,
            final String ... fields) {
        final BlockingQueue<SolrDocument> queue = buffersize <= 0 ? new LinkedBlockingQueue<SolrDocument>() : new ArrayBlockingQueue<SolrDocument>(Math.max(buffersize, concurrency));
        if (!prefetchIDs) {
        	final Thread t = new Thread(newDocumentsByQueriesTask(queue, querystrings, sort, offset, maxcount, maxtime, buffersize, concurrency, fields));
        	t.start();
        	return queue;
        }
        final BlockingQueue<String> idQueue = concurrentIDsByQueries(querystrings, sort, offset, maxcount, maxtime, Math.min(maxcount, 10000000), concurrency);
        final long endtime = maxtime < 0 || maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final Thread[] t = new Thread[concurrency];
        for (int i = 0; i < Math.max(1, concurrency); i++) {
            t[i] = new Thread("AbstractSolrConnector:concurrentDocumentsByQueriesWithPrefetch(" + querystrings.size() + " queries, first: " + querystrings.iterator().next() + ")") {
                @Override
                public void run() {
                    String nextID;
                    try {
                        while (System.currentTimeMillis() < endtime && (nextID = idQueue.take()) != AbstractSolrConnector.POISON_ID) {
                            try {
                                SolrDocument d = getDocumentById(nextID, fields);
                                // document may be null if another process has deleted the document meanwhile
                                // in case that the document is absent then, we silently ignore that case
                                if (d != null) try {queue.put(d);} catch (final InterruptedException e) {}
                            } catch (final SolrException | IOException e) {
                                ConcurrentLog.logException(e);
                                // fail
                                ConcurrentLog.severe("AbstractSolrConnector", "aborted concurrentDocumentsByQuery: " + e.getMessage());
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        ConcurrentLog.severe("AbstractSolrConnector", "interrupted concurrentDocumentsByQuery: " + e.getMessage());
                    }
                    try {queue.put(AbstractSolrConnector.POISON_DOCUMENT);} catch (final InterruptedException e1) {}
                }
            };
            t[i].start();
        }
        return queue;
    }

    @Override
    public Runnable newDocumentsByQueriesTask(
    		final BlockingQueue<SolrDocument> queue,
            final List<String> querystrings,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency,
            final String ... fields) {
    	Objects.requireNonNull(queue, "The queue parameter must not be null.");

        if (querystrings == null || querystrings.isEmpty()) {
			return () -> {
				for (int i = 0; i < Math.max(1, concurrency); i++) {
					try {
						queue.put(AbstractSolrConnector.POISON_DOCUMENT);
					} catch (final InterruptedException e1) {
						Thread.currentThread().interrupt(); // preserve interrupted thread state
					}
				}
			};
        }
        final long endtime = maxtime < 0 || maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final int ps = buffersize < 0 ? pagesize_docs : Math.min(pagesize_docs, buffersize);
        final int maxretries = 6;
        return () -> {
        	long remainingTime = endtime - System.currentTimeMillis();
            try {
                for (final String querystring: querystrings) {
                    Thread.currentThread().setName("AbstractSolrConnector:concurrentDocumentsByQueryNoPrefetch(" + querystring + ")");
                    int o = offset;
                    int count = 0;
                    int retry = 0;
                    loop: while (remainingTime > 0 && count < maxcount) {
                          try {
                             final SolrDocumentList sdl = getDocumentListByQuery(querystring, sort, o, Math.min(maxcount, ps), fields);
                             for (final SolrDocument d: sdl) {
                            		if (endtime != Long.MAX_VALUE) {
                            			/*
                            			 * A timeout is defined : we must not use here queue.put() otherwise this
                            			 * thread could indefinitely wait here when the queue is full and the
                            			 * consumer thread has stopped taking in the queue.
                            			 */
                            			if (!queue.offer(d, remainingTime, TimeUnit.MILLISECONDS)) {
                            				break;
                            			}
                            		} else {
                            			queue.put(d);
                            		}
                                count++;
                             }
                             if (sdl.size() < ps) {
                                break loop; // finished
                             }
                             o += sdl.size();
                             retry = 0;
                         } catch(final InterruptedIOException e) {
                        	 throw new InterruptedException(); // rethrow to finish the process
                         } catch (final SolrException | IOException e) {
                             ConcurrentLog.logException(e);
                             if (retry++ < maxretries) {
                                // remote Solr may be temporary down, so we wait a bit
								Thread.sleep(100);
                                continue loop;
                             }
                             // fail
                             ConcurrentLog.severe("AbstractSolrConnector", "aborted concurrentDocumentsByQueryNoPrefetch after " + maxretries + " retries: " + e.getMessage());
                             break;
                        }
                        remainingTime = endtime - System.currentTimeMillis();
                    }
                }
            } catch(final InterruptedException e) {
            	Thread.currentThread().interrupt(); // preserve interrupted thread state
            } catch (final RuntimeException e) {
                ConcurrentLog.logException(e);
            } finally {
               	/* Add poison elements only when the thread has not been interrupted */
               	for (int i = 0; i < Math.max(1, concurrency); i++) {
               		try {
               			queue.put(AbstractSolrConnector.POISON_DOCUMENT);
               		} catch (final InterruptedException e1) {
               			Thread.currentThread().interrupt(); // preserve interrupted thread state
               			break; // thread is interrupted : in that case we no more try to add poison elements to the queue
               		}
               	}
            }
        };
    }

    /**
     * get a document id result stream from a solr query.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_ID is returned.
     * The method returns immediately and feeds the search results into the queue
     * @param querystring
     * @param sort the solr sort string, may be null to be not used
     * @param offset
     * @param maxcount
     * @param buffersize the size of an ArrayBlockingQueue; if <= 0 then a LinkedBlockingQueue is used
     * @param concurrency is the number of AbstractSolrConnector.POISON_ID entries to add at the end of the feed
     * @return a list of ids in q blocking queue which is terminated with a number of AbstractSolrConnector.POISON_ID
     */
    @Override
    public BlockingQueue<String> concurrentIDsByQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency) {
        List<String> querystrings = new ArrayList<>(1);
        querystrings.add(querystring);
        return concurrentIDsByQueries(querystrings, sort, offset, maxcount, maxtime, buffersize, concurrency);
    }

    /**
     * get a document id result stream from a set of solr queries.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_ID is returned.
     * The method returns immediately and feeds the search results into the queue
     * @param querystrings a list of query strings
     * @param sort the solr sort string, may be null to be not used
     * @param offset common offset of all queries
     * @param maxcount maximum count for each query
     * @param buffersize the size of an ArrayBlockingQueue; if <= 0 then a LinkedBlockingQueue is used
     * @param concurrency is the number of AbstractSolrConnector.POISON_ID entries to add at the end of the feed
     * @return a list of ids in q blocking queue which is terminated with a number of AbstractSolrConnector.POISON_ID
     */
    @Override
    public BlockingQueue<String> concurrentIDsByQueries(
            final List<String> querystrings,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency) {
        final BlockingQueue<String> queue = buffersize <= 0 ? new LinkedBlockingQueue<String>() : new ArrayBlockingQueue<String>(buffersize);
        final long endtime = maxtime < 0 || maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime; // we know infinity!
        final Thread t = new Thread() {
            @Override
            public void run() {
                // CPU-intensive tasks will be performed when accessing the solr index because there decompression of content happens
                this.setPriority(Thread.MAX_PRIORITY);
                try {
                    for (String querystring: querystrings) {
                        this.setName("AbstractSolrConnector:concurrentIDsByQueries(" + querystring + ")");
                        int o = offset;
                        while (System.currentTimeMillis() < endtime) {
                            try {
                                SolrDocumentList sdl = getDocumentListByQuery(querystring, sort, o, maxcount < 0 ? pagesize_ids : Math.min(maxcount, pagesize_ids), CollectionSchema.id.getSolrFieldName());
                                int count = 0;
                                for (SolrDocument d: sdl) {
                                    try {queue.put((String) d.getFieldValue(CollectionSchema.id.getSolrFieldName()));} catch (final InterruptedException e) {break;}
                                    count++;
                                }
                                if (count < pagesize_ids) break;
                                o += count;
                                if (o > maxcount && maxcount > 0) break;
                            } catch (final SolrException e) {
                                break;
                            } catch (final IOException e) {
                                break;
                            }
                        }
                    }
                } catch (Throwable e) {} finally {
                    for (int i = 0; i < concurrency; i++) {
                        try {queue.put(AbstractSolrConnector.POISON_ID);} catch (final InterruptedException e1) {}
                    }
                }
            }
        };
        t.start();
        return queue;
    }

    @Override
    public Iterator<String> iterator() {
        final BlockingQueue<String> queue = concurrentIDsByQuery(CATCHALL_QUERY, null, 0, Integer.MAX_VALUE, 60000, 2 * pagesize_ids, 1);
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
        //if (count < 2 && querystring.startsWith("{!raw f=")) {
        //    params.setQuery("*:*");
        //    params.addFilterQuery(querystring);
        //} else {
            params.setQuery(querystring);
        //}
        params.clearSorts();
        if (sort != null) {
            params.set(CommonParams.SORT, sort);
        }
        params.setRows(count);
        params.setStart(offset);
        params.setFacet(false);
        if (fields != null && fields.length > 0) params.setFields(fields);
        params.setIncludeScore(false);
        if (count > 1) {
            params.setParam("defType", "edismax");
            params.setParam(DisMaxParams.QF, CollectionSchema.text_t.getSolrFieldName() + "^1.0");
        }
        return params;
    }

    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return url if document exist or null otherwise
     * @throws IOException
     */
    @Override
    public String getURL(String id) throws IOException {
        // construct raw query
        final SolrQuery params = new SolrQuery();
        //params.setQuery(CollectionSchema.id.getSolrFieldName() + ":\"" + id + "\"");
        String q = "{!cache=false raw f=" + CollectionSchema.id.getSolrFieldName() + "}" + id;
        params.setQuery(q);
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
        return getURL(doc);
    }

    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return whether the documents exists
     */
    @Override
    public boolean exists(final String id) {
        final String query = "{!cache=false raw f=" + CollectionSchema.id.getSolrFieldName() + "}" + id;
        try {
            return getCountByQuery(query) > 0l;
        } catch (IOException e) {
            ConcurrentLog.logException(e);
            return false;
        }
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
        params.setRows(0); // essential to just get count
        params.setStart(0);
        params.setFacet(false);
        params.clearSorts();
        params.setFields(CollectionSchema.id.getSolrFieldName());
        params.setIncludeScore(false);

        // query the server
        final SolrDocumentList sdl = getDocumentListByParams(params);
        return sdl == null ? 0 : sdl.getNumFound();
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
    public LinkedHashMap<String, ReversibleScoreMap<String>> getFacets(String query, int maxresults, final String ... fields) throws IOException {
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
        params.setParam(FacetParams.FACET_METHOD, FacetParams.FACET_METHOD_enum); // fight the fieldcache
        params.setFields(fields);
        params.clearSorts();
        params.setIncludeScore(false);
        for (String field: fields) params.addFacetField(field);

        // query the server
        QueryResponse rsp = getResponseByParams(params);
        LinkedHashMap<String, ReversibleScoreMap<String>> facets = new LinkedHashMap<String, ReversibleScoreMap<String>>(fields.length);
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
        sb.append("{!cache=false raw f=").append(CollectionSchema.id.getSolrFieldName()).append('}').append(id);
        query.setQuery(sb.toString());
        //query.setQuery("*:*");
        //query.addFilterQuery(sb.toString());
        query.clearSorts();
        query.setRows(1);
        query.setStart(0);
        if (fields != null && fields.length > 0) query.setFields(fields);
        query.setIncludeScore(false);

        // query the server
        try {
            final SolrDocumentList docs = getDocumentListByParams(query);
            if (docs == null || docs.isEmpty()) return null;
            SolrDocument doc = docs.get(0);
            return doc;
        } catch (final Throwable e) {
            clearCaches(); // we clear the in case that this is caused by OOM
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Update a solr document.
     * This will write only a partial update for all fields given in the SolrInputDocument
     * and leaves all other fields untouched.
     * @param solrdoc
     * @throws IOException
     * @throws SolrException
     */
    @Override
    public void update(final SolrInputDocument solrdoc) throws IOException, SolrException {
        this.add(partialUpdatePatch(solrdoc));
    }

    /**
     * Update a collection of solr input documents.
     * This will write only a partial update for all fields given in the SolrInputDocuments
     * and leaves all other fields untouched.
     * @param solrdoc
     * @throws IOException
     * @throws SolrException
     */
    @Override
    public void update(final Collection<SolrInputDocument> solrdoc) throws IOException, SolrException {
        Collection<SolrInputDocument> docs = new ArrayList<>(solrdoc.size());
        for (SolrInputDocument doc: solrdoc) docs.add(partialUpdatePatch(doc));
        this.add(docs);
    }

    private SolrInputDocument partialUpdatePatch(final SolrInputDocument docIn) {
        SolrInputDocument docOut = new SolrInputDocument();
        docOut.setField(CollectionSchema.id.name(), docIn.getFieldValue(CollectionSchema.id.name()));
        for (Entry<String, SolrInputField> entry: docIn.entrySet()) {
            if (entry.getKey().equals(CollectionSchema.id.name())) continue;
            SolrInputField sif = entry.getValue();
            Map<String, Object> partialUpdate = new HashMap<>(1);
            Object value = sif.getValue();
            docOut.removeField(entry.getKey());
            partialUpdate.put("set", value);
            docOut.setField(entry.getKey(), partialUpdate);
        }
        return docOut;
    }


}
