/**
 *  SolrConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 13.09.2011 at https://yacy.net
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

import net.yacy.cora.sorting.ReversibleScoreMap;

public interface SolrConnector extends Iterable<String> /* Iterable of document IDs */ {

    /**
     * clear all caches: inside solr and ouside solr within the implementations of this interface
     */
    public void clearCaches();

    /**
     * get the size of a write buffer (if any) of pending write requests
     */
    public int bufferSize();

    /**
     * get the size of the index
     * @return number of results if solr is queries with a catch-all pattern
     */
    public long getSize();

    /**
     * force a commit
     */
    public void commit(boolean softCommit);

    /**
     * force an explicit merge of segments
     * @param maxSegments the maximum number of segments. Set to 1 for maximum optimization
     */
    public void optimize(int maxSegments);

    /**
     * ask the solr subsystem about it's segment number
     * @return the segment count, which corresponds to the number of files for an index
     */
    public int getSegmentCount();

    /**
     * test if the connector is already closed
     * @return true if the connector is closed
     */
    public boolean isClosed();

    /**
     * close the server connection
     */
    public void close();

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    public void clear() throws IOException;

    /**
     * delete an entry from solr using the url hash as document id
     * @param id the url hash of the entry
     * @throws IOException
     */
    public void deleteById(final String id) throws IOException;

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    public void deleteByIds(final Collection<String> ids) throws IOException;

    /**
     * delete entries from solr according the given solr query string
     * @param querystring
     * @throws IOException
     */
    public void deleteByQuery(final String querystring) throws IOException;

    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return the url if any entry in solr exists, null otherwise
     * @throws IOException
     */
    public String getURL(String id) throws IOException;

    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return whether the documents exists
     */
    public boolean exists(final String id);

    /**
     * add a solr input document
     * @param solrdoc
     * @throws IOException
     * @throws SolrException
     */
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException;

    /**
     * Update a solr document.
     * This will write only a partial update for all fields given in the SolrInputDocument
     * and leaves all other fields untouched.
     * @param solrdoc
     * @throws IOException
     * @throws SolrException
     */
    public void update(final SolrInputDocument solrdoc) throws IOException, SolrException;

    /**
     * add a collection of solr input documents
     * @param solrdoc
     * @throws IOException
     * @throws SolrException
     */
    public void add(final Collection<SolrInputDocument> solrdoc) throws IOException, SolrException;

    /**
     * Update a collection of solr input documents.
     * This will write only a partial update for all fields given in the SolrInputDocuments
     * and leaves all other fields untouched.
     * @param solrdoc
     * @throws IOException
     * @throws SolrException
     */
    public void update(final Collection<SolrInputDocument> solrdoc) throws IOException, SolrException;

    /**
     * get a document from solr by given key for the id-field
     * @param key
     * @param fields list of fields
     * @return one result or null if no result exists
     * @throws IOException
     */
    public SolrDocument getDocumentById(final String key, final String ... fields) throws IOException;

    /**
     * get a "full" query response from solr. Please compare to getSolrDocumentListByParams which may be much more efficient
     * @param query
     * @throws IOException
     */
    public QueryResponse getResponseByParams(final ModifiableSolrParams query) throws IOException;

    /**
     * get the solr document list from a query response
     * This differs from getResponseByParams in such a way that it does only create the fields of the response but
     * never search snippets and there are also no facets generated.
     * @param params
     * @return
     * @throws IOException
     * @throws SolrException
     */
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams params) throws IOException;

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring the solr query string
     * @param sort the solr sort string, may be null to be not used
     * @param offset the first result offset
     * @param count number of wanted results
     * @param fields list of fields
     * @throws IOException
     */
    public SolrDocumentList getDocumentListByQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int count,
            final String ... fields) throws IOException;

    /**
     * get the number of results when this query is done.
     * This should only be called if the actual result is never used, and only the count is interesting
     * @param querystring
     * @return the number of results for this query
     */
    public long getCountByQuery(final String querystring) throws IOException;

    /**
     * get facets of the index: a list of lists with values that are most common in a specific field
     * @param query a query which is performed to get the facets
     * @param maxresults the maximum size of the resulting maps
     * @param fields the field names which are selected as facet
     * @return a map with key = facet field name, value = an ordered map of field values for that field
     * @throws IOException
     */
    public LinkedHashMap<String, ReversibleScoreMap<String>> getFacets(String query, int maxresults, final String ... fields) throws IOException;

    /**
     * <p>Get results from solr queries as a stream of documents.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_DOCUMENT is returned.
     * The method returns immediately and feeds the search results into the queue.</p>
     * <p><strong>Important</strong> : be careful if the consumer thread(s) terminate before taking the poison document(s) from the queue,
     * as the producer thread(s) may indefinitely block on their last step (adding poison element) because the queue would be full.</p>
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
    public BlockingQueue<SolrDocument> concurrentDocumentsByQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency,
            final boolean prefetchIDs,
            final String ... fields);

	/**
	 * Creates a new runnable task to run a given list of Solr queries and fill a
	 * results queue by packets of a limited number of results.
	 *
	 * @param queue        the results queue. Must not be null.
	 * @param querystrings a list of Solr queries
	 * @param sort         an eventual Solr sort criteria
	 * @param offset       the results offset position for each query
	 * @param maxcount     the maximum number of documents per query to retrieve
	 * @param maxtime      the total maximum time to spend. Unlimited when the value
	 *                     is negative or equals to Long.MAX_VALUE
	 * @param buffersize   this is the maximum size of a page of results to retrieve
	 *                     in one step when running a query
	 * @param concurrency  the number of consuming threads
	 * @param fields       the indexed fields to retrieve
	 * @return a ready to run task
	 */
	public Runnable newDocumentsByQueriesTask(final BlockingQueue<SolrDocument> queue, final List<String> querystrings,
			final String sort, final int offset, final int maxcount, final long maxtime, final int buffersize,
			final int concurrency, final String... fields);

    /**
     * Get results from solr queries as a stream of documents.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_DOCUMENT is returned.
     * The method returns immediately and feeds the search results into the queue
     * @param querystrings the list of solr query strings
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
    public BlockingQueue<SolrDocument> concurrentDocumentsByQueries(
            final List<String> querystrings,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency,
            final boolean prefetchIDs,
            final String ... fields);

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
    public BlockingQueue<String> concurrentIDsByQuery(
            final String querystring,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency);

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
    public BlockingQueue<String> concurrentIDsByQueries(
            final List<String> querystrings,
            final String sort,
            final int offset,
            final int maxcount,
            final long maxtime,
            final int buffersize,
            final int concurrency);
}
