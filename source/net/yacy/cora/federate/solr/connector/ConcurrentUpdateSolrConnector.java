/**
 *  ConcurrentUpdateSolrConnector
 *  Copyright 2013 by Michael Peter Christen
 *  First released 28.04.2013 at http://yacy.net
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.schema.CollectionSchema;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

/**
 * The purpose of this connector is to provide a non-blocking interface to solr.
 * All time-consuming tasks like updates and deletions are done within a concurrent process
 * which is started for this class in the background.
 * To implement this, we introduce an id exist cache, a deletion id queue and a update document queue.
 */
public class ConcurrentUpdateSolrConnector implements SolrConnector {

    SolrConnector connector;

    private final static String POISON_ID = "________";
    private final static SolrInputDocument POISON_DOCUMENT = new SolrInputDocument();
    
    private class DeletionHandler implements Runnable {
        @Override
        public void run() {
            String id;
            try {
                while ((id = ConcurrentUpdateSolrConnector.this.deleteQueue.take()) != POISON_ID) {
                    try {
                        removeIdFromUpdateQueue(id);
                        ConcurrentUpdateSolrConnector.this.connector.deleteById(id);
                        ConcurrentUpdateSolrConnector.this.idCache.remove(ASCII.getBytes(id));
                    } catch (final IOException e) {
                        ConcurrentLog.logException(e);
                    }
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }
    }
    
    private class UpdateHandler implements Runnable {
        @Override
        public void run() {
            SolrInputDocument doc;
            try {
                while ((doc = ConcurrentUpdateSolrConnector.this.updateQueue.take()) != POISON_DOCUMENT) {
                    int getmore = ConcurrentUpdateSolrConnector.this.updateQueue.size();
                    if (getmore > 0) {
                        // accumulate a collection of documents because that is better to send at once to a remote server
                        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>(getmore + 1);
                        docs.add(doc);
                        updateIdCache((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                        for (int i = 0; i < getmore; i++) {
                            SolrInputDocument d = ConcurrentUpdateSolrConnector.this.updateQueue.take();
                            if (d == POISON_DOCUMENT) {
                                ConcurrentUpdateSolrConnector.this.updateQueue.put(POISON_DOCUMENT); // make sure that the outer loop terminates as well
                                break;
                            }
                            docs.add(d);
                            updateIdCache((String) d.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                        }
                        //ConcurrentLog.info("ConcurrentUpdateSolrConnector", "sending " + docs.size() + " documents to solr");
                        try {
                            ConcurrentUpdateSolrConnector.this.connector.add(docs);
                        } catch (final IOException e) {
                            ConcurrentLog.logException(e);
                        }
                    } else {
                        // if there is only a single document, send this directly to solr
                        //ConcurrentLog.info("ConcurrentUpdateSolrConnector", "sending one document to solr");
                        try {
                            updateIdCache((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                            ConcurrentUpdateSolrConnector.this.connector.add(doc);
                        } catch (final IOException e) {
                            ConcurrentLog.logException(e);
                        }
                    }
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }
    }
    
    private HandleSet idCache;
    private BlockingQueue<SolrInputDocument> updateQueue;
    private BlockingQueue<String> deleteQueue;
    private Thread deletionHandler, updateHandler;
    private int idCacheCapacity;
    
    public ConcurrentUpdateSolrConnector(SolrConnector connector, int updateCapacity, int idCacheCapacity) {
        this.connector = connector;
        this.idCache = new RowHandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 100);
        this.updateQueue = new ArrayBlockingQueue<SolrInputDocument>(updateCapacity);
        this.deleteQueue = new LinkedBlockingQueue<String>();
        this.deletionHandler = null;
        this.updateHandler = null;
        this.idCacheCapacity = idCacheCapacity;
        ensureAliveDeletionHandler();
        ensureAliveUpdateHandler();
    }

    @Override
    public void clearCaches() {
        this.connector.clearCaches();
        this.idCache.clear();
    }

    /**
     * used for debugging
     */
    private static void cacheSuccessSign() {
        //Log.logInfo("ConcurrentUpdate", "**** cache hit");
    }
    
    private boolean existIdFromDeleteQueue(String id) {
        if (this.deleteQueue.size() == 0) return false;
        Iterator<String> i = this.deleteQueue.iterator();
        while (i.hasNext()) {
            String docID = i.next();
            if (docID == null) break;
            if (docID.equals(id)) return true;
        }
        return false;
    }

    private SolrInputDocument getFromUpdateQueue(String id) {
        if (this.updateQueue.size() == 0) return null;
        Iterator<SolrInputDocument> i = this.updateQueue.iterator();
        while (i.hasNext()) {
            SolrInputDocument doc = i.next();
            if (doc == null) break;
            String docID = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            if (docID != null && docID.equals(id)) return doc;
        }
        return null;
    }

    private boolean existIdFromUpdateQueue(String id) {
        if (this.updateQueue.size() == 0) return false;
        Iterator<SolrInputDocument> i = this.updateQueue.iterator();
        while (i.hasNext()) {
            SolrInputDocument doc = i.next();
            if (doc == null) break;
            String docID = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            if (docID != null && docID.equals(id)) return true;
        }
        return false;
    }

    private void removeIdFromUpdateQueue(String id) {
        if (this.updateQueue.size() == 0) return;
        Iterator<SolrInputDocument> i = this.updateQueue.iterator();
        while (i.hasNext()) {
            SolrInputDocument doc = i.next();
            if (doc == null) break;
            String docID = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            if (docID != null && docID.equals(id)) {
                i.remove();
                break;
            }
        }
    }

    private void removeIdFromDeleteQueue(String id) {
        if (this.updateQueue.size() == 0) return;
        Iterator<String> i = this.deleteQueue.iterator();
        while (i.hasNext()) {
            String docID = i.next();
            if (docID == null) break;
            if (docID.equals(id)) {
                i.remove();
                break;
            }
        }
    }
    
    private void updateIdCache(String id) {
        if (id == null) return;
        if (this.idCache.size() >= this.idCacheCapacity || MemoryControl.shortStatus()) this.idCache.clear();
        try {
            this.idCache.put(ASCII.getBytes(id));
        } catch (final SpaceExceededException e) {
            this.idCache.clear();
        }
    }
    
    public void ensureAliveDeletionHandler() {
        if (this.deletionHandler == null || !this.deletionHandler.isAlive()) {
            this.deletionHandler = new Thread(new DeletionHandler());
            this.deletionHandler.setName(this.getClass().getName() + "_DeletionHandler");
            this.deletionHandler.start();
        }
    }
    
    public void ensureAliveUpdateHandler() {
        if (this.updateHandler == null || !this.updateHandler.isAlive()) {
            this.updateHandler = new Thread(new UpdateHandler());
            this.updateHandler.setName(this.getClass().getName() + "_UpdateHandler");
            this.updateHandler.start();
        }
    }
    
    @Override
    public Iterator<String> iterator() {
        return this.connector.iterator();
    }

    @Override
    public long getSize() {
        return this.connector.getSize() + this.updateQueue.size();
    }

    @Override
    public void commit(boolean softCommit) {
        if (!softCommit) {
            long timeout = System.currentTimeMillis() + 1000;
            ensureAliveDeletionHandler();
            while (this.deleteQueue.size() > 0) {
                try {Thread.sleep(10);} catch (final InterruptedException e) {}
                if (System.currentTimeMillis() > timeout) break;
            }
            ensureAliveUpdateHandler();
            while (this.updateQueue.size() > 0) {
                try {Thread.sleep(10);} catch (final InterruptedException e) {}
                if (System.currentTimeMillis() > timeout) break;
            }
        }
        this.connector.commit(softCommit);
    }

    @Override
    public void optimize(int maxSegments) {
        this.connector.optimize(maxSegments);
    }

    @Override
    public int getSegmentCount() {
        return this.connector.getSegmentCount();
    }

    @Override
    public void close() {
        ensureAliveDeletionHandler();
        try {this.deleteQueue.put(POISON_ID);} catch (final InterruptedException e) {}
        ensureAliveUpdateHandler();
        try {this.updateQueue.put(POISON_DOCUMENT);} catch (final InterruptedException e) {}
        try {this.deletionHandler.join();} catch (final InterruptedException e) {}
        try {this.updateHandler.join();} catch (final InterruptedException e) {}
        this.connector.close();
        this.idCache.clear();
    }

    @Override
    public void clear() throws IOException {
        this.deleteQueue.clear();
        this.updateQueue.clear();
        try {this.updateQueue.put(POISON_DOCUMENT);} catch (final InterruptedException e) {}
        try {this.updateHandler.join();} catch (final InterruptedException e) {}
        this.connector.clear();
        this.idCache.clear();
    }

    @Override
    public void deleteById(String id) throws IOException {
        removeIdFromUpdateQueue(id);
        this.idCache.remove(ASCII.getBytes(id));
        if (this.deletionHandler.isAlive()) {
            try {this.deleteQueue.put(id);} catch (final InterruptedException e) {}
        } else {
            this.connector.deleteById(id);
        }
    }

    @Override
    public void deleteByIds(Collection<String> ids) throws IOException {
        for (String id: ids) {
            removeIdFromUpdateQueue(id);
            this.idCache.remove(ASCII.getBytes(id));
        }
        if (this.deletionHandler.isAlive()) {
            for (String id: ids) try {this.deleteQueue.put(id);} catch (final InterruptedException e) {}
        } else {
            this.connector.deleteByIds(ids);
        }
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        new Thread() {
            public void run() {
                ConcurrentUpdateSolrConnector.this.idCache.clear();
                try {
                    ConcurrentUpdateSolrConnector.this.connector.deleteByQuery(querystring);
                    ConcurrentUpdateSolrConnector.this.idCache.clear();
                } catch (final IOException e) {
                    ConcurrentLog.severe("ConcurrentUpdateSolrConnector", e.getMessage(), e);
                }
                ConcurrentUpdateSolrConnector.this.connector.commit(true);
            }
        }.start();
    }

    @Override
    public boolean existsById(String id) throws IOException {
        if (this.idCache.has(ASCII.getBytes(id))) {cacheSuccessSign(); return true;}
        if (existIdFromDeleteQueue(id)) {cacheSuccessSign(); return false;}
        if (existIdFromUpdateQueue(id)) {cacheSuccessSign(); return true;}
        if (this.connector.existsById(id)) {
            updateIdCache(id);
            return true;
        }
        return false;
    }

    @Override
    public Set<String> existsByIds(Set<String> ids) throws IOException {
        HashSet<String> e = new HashSet<String>();
        if (ids == null || ids.size() == 0) return e;
        if (ids.size() == 1) return existsById(ids.iterator().next()) ? ids : e;
        Set<String> idsC = new HashSet<String>();
        for (String id: ids) {
            if (this.idCache.has(ASCII.getBytes(id))) {cacheSuccessSign(); e.add(id); continue;}
            if (existIdFromDeleteQueue(id)) {cacheSuccessSign(); continue;}
            if (existIdFromUpdateQueue(id)) {cacheSuccessSign(); e.add(id); continue;}
            idsC.add(id);
        }
        Set<String> e1 = this.connector.existsByIds(idsC);
        for (String id1: e1) {
            updateIdCache(id1);
        }
        e.addAll(e1);
        return e;
    }
    
    @Override
    public boolean existsByQuery(String solrquery) throws IOException {
        // this is actually wrong but to make it right we need to wait until all queues are flushed. But that may take very long when the queues are filled again all the time.
        return this.connector.existsByQuery(solrquery);
    }

    @Override
    public void add(SolrInputDocument solrdoc) throws IOException, SolrException {
        String id = (String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        removeIdFromDeleteQueue(id);
        updateIdCache(id);
        if (this.updateHandler.isAlive()) {
            try {this.updateQueue.put(solrdoc);} catch (final InterruptedException e) {}
        } else {
            this.connector.add(solrdoc);
        }
    }

    @Override
    public void add(Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument doc: solrdocs) {
            String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            removeIdFromDeleteQueue(id);
            updateIdCache(id);
        }
        if (this.updateHandler.isAlive()) {
            for (SolrInputDocument doc: solrdocs) try {this.updateQueue.put(doc);} catch (final InterruptedException e) {}
        } else {
            this.connector.add(solrdocs);
        }
    }

    @Override
    public String getFieldById(String id, String field) throws IOException {
        if (existIdFromDeleteQueue(id)) return null;
        SolrInputDocument doc = getFromUpdateQueue(id);
        if (doc != null) {cacheSuccessSign(); return doc.getFieldValue(field).toString();}
        String val = this.connector.getFieldById(id, field);
        if (val != null) updateIdCache(id);
        return val;
    }

    @Override
    public SolrDocument getDocumentById(String id, String... fields) throws IOException {
        if (existIdFromDeleteQueue(id)) return null;
        SolrInputDocument idoc = getFromUpdateQueue(id);
        if (idoc != null) {cacheSuccessSign(); return ClientUtils.toSolrDocument(idoc);}
        SolrDocument doc = this.connector.getDocumentById(id, fields);
        if (doc != null) updateIdCache(id);
        return doc;
    }

    @Override
    public QueryResponse getResponseByParams(ModifiableSolrParams query) throws IOException, SolrException {
        return this.connector.getResponseByParams(query);
    }

    @Override
    public SolrDocumentList getDocumentListByQuery(String querystring, int offset, int count, String... fields) throws IOException, SolrException {
        return this.connector.getDocumentListByQuery(querystring, offset, count, fields);
    }

    @Override
    public long getCountByQuery(String querystring) throws IOException {
        return this.connector.getCountByQuery(querystring);
    }

    @Override
    public Map<String, ReversibleScoreMap<String>> getFacets(String query, int maxresults, String... fields) throws IOException {
        return this.connector.getFacets(query, maxresults, fields);
    }

    @Override
    public BlockingQueue<SolrDocument> concurrentDocumentsByQuery(String querystring, int offset, int maxcount, long maxtime, int buffersize, String... fields) {
        return this.connector.concurrentDocumentsByQuery(querystring, offset, maxcount, maxtime, buffersize, fields);
    }

    @Override
    public BlockingQueue<String> concurrentIDsByQuery(String querystring, int offset, int maxcount, long maxtime) {
        return this.connector.concurrentIDsByQuery(querystring, offset, maxcount, maxtime);
    }

}
