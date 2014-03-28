/**
 *  ServerShard
 *  Copyright 2013 by Michael Peter Christen
 *  First released 13.02.2013 at http://yacy.net
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


package net.yacy.cora.federate.solr.instance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.beans.DocumentObjectBinder;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

import net.yacy.cora.federate.solr.connector.ShardSelection;

public class ServerShard extends SolrServer {
    
    private static final long serialVersionUID = -3524175189049786312L;
    private static final UpdateResponse _dummyOKResponse = new UpdateResponse();
    static {
        _dummyOKResponse.setElapsedTime(0);
        _dummyOKResponse.setResponse(new NamedList<Object>());
    }
    
    private final ArrayList<SolrServer> server;
    private final ShardSelection sharding;
    private final boolean writeEnabled;
    
    public ServerShard(ArrayList<SolrServer> server, final ShardSelection.Method method, final boolean writeEnabled) {
        this.server = server;
        this.sharding = new ShardSelection(method, this.server.size());
        this.writeEnabled = writeEnabled;
    }

    /**
     * Adds a collection of documents
     * @param docs  the collection of documents
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse add(Collection<SolrInputDocument> docs) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrInputDocument doc: docs) ur = server.get(this.sharding.select(doc)).add(doc);
        return ur; // TODO: this accumlation of update responses is wrong, but sufficient (because we do not evaluate it)
    }

    /**
     * Adds a collection of documents, specifying max time before they become committed
     * @param docs  the collection of documents
     * @param commitWithinMs  max time (in ms) before a commit will happen 
     * @throws IOException If there is a low-level I/O error.
     * @since solr 3.5
     */
    @Override
    public UpdateResponse add(Collection<SolrInputDocument> docs, int commitWithinMs) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrInputDocument doc: docs) ur = server.get(this.sharding.select(doc)).add(doc, commitWithinMs);
        return ur;
    }

    /**
     * Adds a collection of beans
     * @param beans  the collection of beans
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse addBeans(Collection<?> beans ) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.addBeans(beans);
        return ur;
    }
    
    /**
     * Adds a collection of beans specifying max time before they become committed
     * @param beans  the collection of beans
     * @param commitWithinMs  max time (in ms) before a commit will happen 
     * @throws IOException If there is a low-level I/O error.
     * @since solr 3.5
     */
    @Override
    public UpdateResponse addBeans(Collection<?> beans, int commitWithinMs) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.addBeans(beans, commitWithinMs);
        return ur;
    }

    /**
     * Adds a single document
     * @param doc  the input document
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse add(SolrInputDocument doc) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        return server.get(this.sharding.select(doc)).add(doc);
    }

    /**
     * Adds a single document specifying max time before it becomes committed
     * @param doc  the input document
     * @param commitWithinMs  max time (in ms) before a commit will happen 
     * @throws IOException If there is a low-level I/O error.
     * @since solr 3.5
     */
    @Override
    public UpdateResponse add(SolrInputDocument doc, int commitWithinMs) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        return server.get(this.sharding.select(doc)).add(doc, commitWithinMs);
    }

    /**
     * Adds a single bean
     * @param obj  the input bean
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse addBean(Object obj) throws IOException, SolrServerException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.addBean(obj);
        return ur;
    }

    /**
     * Adds a single bean specifying max time before it becomes committed
     * @param obj  the input bean
     * @param commitWithinMs  max time (in ms) before a commit will happen 
     * @throws IOException If there is a low-level I/O error.
     * @since solr 3.5
     */
    @Override
    public UpdateResponse addBean(Object obj, int commitWithinMs) throws IOException, SolrServerException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.addBean(obj, commitWithinMs);
        return ur;
    }

    /** 
     * Performs an explicit commit, causing pending documents to be committed for indexing
     * <p>
     * waitFlush=true and waitSearcher=true to be inline with the defaults for plain HTTP access
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse commit() throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.commit();
        return ur;
    }

    /** 
     * Performs an explicit optimize, causing a merge of all segments to one.
     * <p>
     * waitFlush=true and waitSearcher=true to be inline with the defaults for plain HTTP access
     * <p>
     * Note: In most cases it is not required to do explicit optimize
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse optimize() throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.optimize();
        return ur;
    }
    
    /** 
     * Performs an explicit commit, causing pending documents to be committed for indexing
     * @param waitFlush  block until index changes are flushed to disk
     * @param waitSearcher  block until a new searcher is opened and registered as the main query searcher, making the changes visible 
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse commit(boolean waitFlush, boolean waitSearcher) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.commit(waitFlush, waitSearcher);
        return ur;
    }

    /**
     * Performs an explicit commit, causing pending documents to be committed for indexing
     * @param waitFlush  block until index changes are flushed to disk
     * @param waitSearcher  block until a new searcher is opened and registered as the main query searcher, making the changes visible
     * @param softCommit makes index changes visible while neither fsync-ing index files nor writing a new index descriptor
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse commit(boolean waitFlush, boolean waitSearcher, boolean softCommit) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.commit(waitFlush, waitSearcher, softCommit);
        return ur;
    }

    /** 
     * Performs an explicit optimize, causing a merge of all segments to one.
     * <p>
     * Note: In most cases it is not required to do explicit optimize
     * @param waitFlush  block until index changes are flushed to disk
     * @param waitSearcher  block until a new searcher is opened and registered as the main query searcher, making the changes visible 
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse optimize(boolean waitFlush, boolean waitSearcher) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.optimize(waitFlush, waitSearcher);
        return ur;
    }

    /** 
     * Performs an explicit optimize, causing a merge of all segments to one.
     * <p>
     * Note: In most cases it is not required to do explicit optimize
     * @param waitFlush  block until index changes are flushed to disk
     * @param waitSearcher  block until a new searcher is opened and registered as the main query searcher, making the changes visible 
     * @param maxSegments  optimizes down to at most this number of segments
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse optimize(boolean waitFlush, boolean waitSearcher, int maxSegments) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.optimize(waitFlush, waitSearcher, maxSegments);
        return ur;
    }
    
    /**
     * Performs a rollback of all non-committed documents pending.
     * <p>
     * Note that this is not a true rollback as in databases. Content you have previously
     * added may have been committed due to autoCommit, buffer full, other client performing
     * a commit etc.
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse rollback() throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.rollback();
        return ur;
    }
    
    /**
     * Deletes a single document by unique ID
     * @param id  the ID of the document to delete
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse deleteById(String id) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.deleteById(id);
        return ur;
    }

    /**
     * Deletes a single document by unique ID, specifying max time before commit
     * @param id  the ID of the document to delete
     * @param commitWithinMs  max time (in ms) before a commit will happen 
     * @throws IOException If there is a low-level I/O error.
     * @since 3.6
     */
    @Override
    public UpdateResponse deleteById(String id, int commitWithinMs) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.deleteById(id, commitWithinMs);
        return ur;
    }

    /**
     * Deletes a list of documents by unique ID
     * @param ids  the list of document IDs to delete 
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse deleteById(List<String> ids) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.deleteById(ids);
        return ur;
    }

    /**
     * Deletes a list of documents by unique ID, specifying max time before commit
     * @param ids  the list of document IDs to delete 
     * @param commitWithinMs  max time (in ms) before a commit will happen 
     * @throws IOException If there is a low-level I/O error.
     * @since 3.6
     */
    @Override
    public UpdateResponse deleteById(List<String> ids, int commitWithinMs) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.deleteById(ids, commitWithinMs);
        return ur;
    }

    /**
     * Deletes documents from the index based on a query
     * @param query  the query expressing what documents to delete
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public UpdateResponse deleteByQuery(String query) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.deleteByQuery(query);
        return ur;
    }

    /**
     * Deletes documents from the index based on a query, specifying max time before commit
     * @param query  the query expressing what documents to delete
     * @param commitWithinMs  max time (in ms) before a commit will happen 
     * @throws IOException If there is a low-level I/O error.
     * @since 3.6
     */
    @Override
    public UpdateResponse deleteByQuery(String query, int commitWithinMs) throws SolrServerException, IOException {
        if (!this.writeEnabled) return _dummyOKResponse;
        UpdateResponse ur = null;
        for (SolrServer s: server) ur = s.deleteByQuery(query, commitWithinMs);
        return ur;
    }

    /**
     * Issues a ping request to check if the server is alive
     * @throws IOException If there is a low-level I/O error.
     */
    @Override
    public SolrPingResponse ping() throws SolrServerException, IOException {
        for (SolrServer s: server) {
            SolrPingResponse spr = s.ping();
            if (spr != null) return spr;
        }
        return null;
    }

    /**
     * Performs a query to the Solr server
     * @param params  an object holding all key/value parameters to send along the request
     */
    @Override
    public QueryResponse query(final SolrParams params) throws SolrServerException {

        final Collection<QueryResponse> qrl = new ConcurrentLinkedQueue<QueryResponse>();
        // concurrently call all shards
        List<Thread> t = new ArrayList<Thread>();
        for (final SolrServer s: server) {
            Thread t0 = new Thread() {
                @Override
                public void run() {
                    this.setName("ServerShard.query/1(" + params.toString() + ")");
                    QueryResponse rsp;
                    try {
                        rsp = s.query(params);
                    } catch (final Throwable e) {return;}
                    qrl.add(rsp);
                }
            };
            t0.start();
            t.add(t0);
        }
        for (Thread t0: t) {
            try {t0.join();} catch (final InterruptedException e) {}
        }
        
        // prepare combined response
        return ResponseAccumulator.combineResponses(qrl);
    }
    
    /**
     * Performs a query to the Solr server
     * @param params  an object holding all key/value parameters to send along the request
     * @param method  specifies the HTTP method to use for the request, such as GET or POST
     */
    @Override
    public QueryResponse query(final SolrParams params, final METHOD method) throws SolrServerException {

        final Collection<QueryResponse> qrl = new ConcurrentLinkedQueue<QueryResponse>();
        // concurrently call all shards
        List<Thread> t = new ArrayList<Thread>();
        for (final SolrServer s: server) {
            Thread t0 = new Thread() {
                @Override
                public void run() {
                    this.setName("ServerShard.query/2(" + params.toString() + ")");
                    QueryResponse rsp;
                    try {
                        rsp = s.query(params, method);
                    } catch (final Throwable e) {return;}
                    qrl.add(rsp);
                }
            };
            t0.start();
            t.add(t0);
        }
        for (Thread t0: t) {
            try {t0.join();} catch (final InterruptedException e) {}
        }
        
        // prepare combined response
        return ResponseAccumulator.combineResponses(qrl);
    }
    

    /**
     * Query solr, and stream the results.  Unlike the standard query, this will 
     * send events for each Document rather then add them to the QueryResponse.
     * 
     * Although this function returns a 'QueryResponse' it should be used with care
     * since it excludes anything that was passed to callback.  Also note that
     * future version may pass even more info to the callback and may not return 
     * the results in the QueryResponse.
     *
     * @since solr 4.0
     */
    @Override
    public QueryResponse queryAndStreamResponse( SolrParams params, StreamingResponseCallback callback ) throws SolrServerException, IOException {
        throw new UnsupportedOperationException("stream response not possible with shards");
    }

    /**
     * SolrServer implementations need to implement how a request is actually processed
     */ 
    @Override
    public NamedList<Object> request(final SolrRequest request) throws SolrServerException, IOException {
        ResponseAccumulator acc = new ResponseAccumulator();
        for (SolrServer s: server) acc.addResponse(s.request(request));
        return acc.getAccumulatedResponse();        
    }

    @Override
    public DocumentObjectBinder getBinder() {
        DocumentObjectBinder db;
        for (SolrServer s: server) {
            db = s.getBinder();
            if (db != null) return db;
        }
        return null;
    }
    
    @Override
    public void shutdown() {
        for (SolrServer s: server) {
            s.shutdown();
        }
    }
}
