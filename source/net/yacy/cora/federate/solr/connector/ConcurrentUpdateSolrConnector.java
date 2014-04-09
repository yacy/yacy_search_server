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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ARH;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.cora.storage.ConcurrentARH;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.word.Word;
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

    private final static Object POISON_PROCESS = new Object();
    
    private class ProcessHandler extends Thread {
        @Override
        public void run() {
            
            try {
                Object process;
                Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
                while ((process = ConcurrentUpdateSolrConnector.this.processQueue.take()) != POISON_PROCESS) {

                    if (process instanceof String) {
                        // delete document
                        if (docs.size() > 0) addSynchronized(docs);
                        String id = (String) process;
                        try {
                            ConcurrentUpdateSolrConnector.this.connector.deleteById(id);
                        } catch (final IOException e) {
                            ConcurrentLog.logException(e);
                        }
                    }
                    
                    if (process instanceof SolrInputDocument) {
                        SolrInputDocument doc = (SolrInputDocument) process;
                        docs.add(doc);
                    }
                    
                    if (docs.size() > 0 &&
                        (ConcurrentUpdateSolrConnector.this.processQueue.size() == 0 ||
                         docs.size() >= ConcurrentUpdateSolrConnector.this.processQueue.size() + ConcurrentUpdateSolrConnector.this.processQueue.remainingCapacity())) {
                        addSynchronized(docs);
                    }
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }
        private void addSynchronized(final Collection<SolrInputDocument> docs) {
            assert docs.size() > 0;
            try {
                ConcurrentUpdateSolrConnector.this.connector.add(docs);
            } catch (final OutOfMemoryError e) {
                // clear and try again...
                clearCaches();
                try {
                    ConcurrentUpdateSolrConnector.this.connector.add(docs);
                } catch (final IOException ee) {
                    ConcurrentLog.logException(e);
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
            docs.clear();
        }
    }

    private ARC<String, Metadata> metadataCache;
    private ARH<String> missCache;
    private BlockingQueue<Object> processQueue;
    private ProcessHandler processHandler;
    
    public ConcurrentUpdateSolrConnector(final SolrConnector connector, final int updateCapacity, final int idCacheCapacity, final int concurrency) {
        this.connector = connector;
        this.metadataCache = new ConcurrentARC<String, Metadata>(idCacheCapacity, concurrency);
        this.missCache = new ConcurrentARH<String>(idCacheCapacity, concurrency);
        this.processQueue = new ArrayBlockingQueue<Object>(updateCapacity);
        this.processHandler = null;
        ensureAliveProcessHandler();
    }

    @Override
    public int hashCode() {
        return this.connector.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConcurrentUpdateSolrConnector && this.connector.equals(((ConcurrentUpdateSolrConnector) o).connector);
    }

    @Override
    public int bufferSize() {
        return this.processQueue.size();
    }

    @Override
    public void clearCaches() {
        this.connector.clearCaches();
        this.metadataCache.clear();
        this.missCache.clear();
    }

    /**
     * used for debugging
     */
    private static void cacheSuccessSign() {
        //ConcurrentLog.info("ConcurrentUpdate", "**** cache hit");
    }

    private boolean containsDeleteInProcessQueue(final String id) {
        boolean delete = false;
        boolean ctch = false;
        for (Object o: this.processQueue) {
            if (o == null) break;
            if (checkDelete(o, id)) delete = true; // do not add a break here!
            if (checkAdd(o, id)) {delete = false; ctch = true;} // do not add a break here!
        }
        if (ctch && delete) removeFromProcessQueue(id); // clean up put+remove
        return delete;
    }

    private SolrInputDocument getFromProcessQueue(final String id) {
        SolrInputDocument d = null;
        boolean ctch = false;
        for (Object o: this.processQueue) {
            if (o == null) break;
            if (checkDelete(o, id)) d = null; // do not add a break here!
            if (checkAdd(o, id)) {d = (SolrInputDocument) o; ctch = true;} // do not add a break here!
        }
        if (ctch && d == null) removeFromProcessQueue(id); // clean up put+remove
        return d;
    }

    private void removeFromProcessQueue(final String id) {
        Iterator<Object> i = this.processQueue.iterator();
        while (i.hasNext()) {
            if (checkAdd(i.next(), id)) {i.remove(); break;}
        }
    }

    private boolean checkDelete(final Object o, final String id) {
        if (!(o instanceof String)) return false;
        String docID = (String) o;
        return (docID != null && docID.equals(id));
    }
    
    private boolean checkAdd(final Object o, final String id) {
        if (!(o instanceof SolrInputDocument))  return false;
        SolrInputDocument doc = (SolrInputDocument) o;
        String docID = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        return (docID != null && docID.equals(id));
    }

    private void updateCache(final String id, final Metadata md) {
        if (id == null) return;
        if (MemoryControl.shortStatus()) {
            this.metadataCache.clear();
            this.missCache.clear();
        }
        this.metadataCache.put(id, md);
        this.missCache.delete(id);
    }
    
    public void ensureAliveProcessHandler() {
        if (this.processHandler == null || !this.processHandler.isAlive()) {
            this.processHandler = new ProcessHandler();
            this.processHandler.setName(this.getClass().getName() + "_ProcessHandler");
            this.processHandler.start();
        }
    }
    
    @Override
    public Iterator<String> iterator() {
        return this.connector.iterator();
    }

    @Override
    public long getSize() {
        return this.connector.getSize() + this.processQueue.size();
    }

    @Override
    public void commit(boolean softCommit) {
        long timeout = System.currentTimeMillis() + 1000;
        ensureAliveProcessHandler();
        while (this.processQueue.size() > 0) {
            try {Thread.sleep(10);} catch (final InterruptedException e) {}
            if (System.currentTimeMillis() > timeout) break;
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
    public boolean isClosed() {
        return this.connector == null || this.connector.isClosed(); 
    }
    
    @Override
    public void close() {
        ensureAliveProcessHandler();
        try {this.processQueue.put(POISON_PROCESS);} catch (final InterruptedException e) {}
        try {this.processHandler.join();} catch (final InterruptedException e) {}
        this.connector.close();
        this.metadataCache.clear();
        this.connector = null;
        this.metadataCache = null;
    }

    @Override
    public void clear() throws IOException {
        this.processQueue.clear();
        this.connector.clear();
        this.metadataCache.clear();
    }

    @Override
    public synchronized void deleteById(String id) throws IOException {
        this.metadataCache.remove(id);
        this.missCache.add(id);
        ensureAliveProcessHandler();
        if (this.processHandler.isAlive()) {
            try {this.processQueue.put(id);} catch (final InterruptedException e) {}
        } else {
            this.connector.deleteById(id);
        }
    }

    @Override
    public synchronized void deleteByIds(Collection<String> ids) throws IOException {
        for (String id: ids) {
            this.metadataCache.remove(id);
            this.missCache.add(id);
        }
        ensureAliveProcessHandler();
        if (this.processHandler.isAlive()) {
            for (String id: ids) try {this.processQueue.put(id);} catch (final InterruptedException e) {}
        } else {
            this.connector.deleteByIds(ids);
        }
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        try {
            ConcurrentUpdateSolrConnector.this.connector.deleteByQuery(querystring);
            ConcurrentUpdateSolrConnector.this.metadataCache.clear();
        } catch (final IOException e) {
            ConcurrentLog.severe("ConcurrentUpdateSolrConnector", e.getMessage(), e);
        }
        ConcurrentUpdateSolrConnector.this.connector.commit(true);
    }

    @Override
    public Metadata getMetadata(String id) throws IOException {
        if (this.missCache.contains(id)) {cacheSuccessSign(); return null;}
        Metadata md = this.metadataCache.get(id);
        if (md != null) {cacheSuccessSign(); return md;}
        if (containsDeleteInProcessQueue(id)) {cacheSuccessSign(); return null;}
        SolrInputDocument doc = getFromProcessQueue(id);
        if (doc != null) {cacheSuccessSign(); return AbstractSolrConnector.getMetadata(doc);}
        md = this.connector.getMetadata(id);
        if (md == null) {this.missCache.add(id); return null;}
        updateCache(id, md);
        return md;
    }
    
    @Override
    public void add(SolrInputDocument solrdoc) throws IOException, SolrException {
        String id = (String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        updateCache(id, AbstractSolrConnector.getMetadata(solrdoc));
        ensureAliveProcessHandler();
        if (this.processHandler.isAlive()) {
            try {this.processQueue.put(solrdoc);} catch (final InterruptedException e) {}
        } else {
            this.connector.add(solrdoc);
        }
    }

    @Override
    public void add(Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument doc: solrdocs) {
            String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            updateCache(id, AbstractSolrConnector.getMetadata(doc));
        }
        ensureAliveProcessHandler();
        if (this.processHandler.isAlive()) {
            for (SolrInputDocument doc: solrdocs) try {this.processQueue.put(doc);} catch (final InterruptedException e) {}
        } else {
            this.connector.add(solrdocs);
        }
    }
    
    @Override
    public SolrDocument getDocumentById(final String id, String... fields) throws IOException {
        assert id.length() == Word.commonHashLength : "wrong id: " + id;
        if (this.missCache.contains(id)) return null;
        if (containsDeleteInProcessQueue(id)) return null;
        SolrInputDocument idoc = getFromProcessQueue(id);
        if (idoc != null) {cacheSuccessSign(); return ClientUtils.toSolrDocument(idoc);}
        SolrDocument doc = this.connector.getDocumentById(id, AbstractSolrConnector.ensureEssentialFieldsIncluded(fields));
        if (doc == null) {
            this.missCache.add(id);
        } else {
            updateCache(id, AbstractSolrConnector.getMetadata(doc));
        }
        return doc;
    }

    @Override
    public QueryResponse getResponseByParams(ModifiableSolrParams query) throws IOException, SolrException {
        return this.connector.getResponseByParams(query);
    }

    @Override
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams params) throws IOException, SolrException {
        SolrDocumentList sdl = this.connector.getDocumentListByParams(params);
        return sdl;
    }

    @Override
    public long getDocumentCountByParams(ModifiableSolrParams params) throws IOException, SolrException {
        final SolrDocumentList sdl = getDocumentListByParams(params);
        return sdl == null ? 0 : sdl.getNumFound();
    }
    
    @Override
    public SolrDocumentList getDocumentListByQuery(String querystring, String sort, int offset, int count, String... fields) throws IOException, SolrException {
        if (offset == 0 && count == 1 && querystring.startsWith("id:") &&
            ((querystring.length() == 17 && querystring.charAt(3) == '"' && querystring.charAt(16) == '"') ||
             querystring.length() == 15)) {
            final SolrDocumentList list = new SolrDocumentList();
            SolrDocument doc = getDocumentById(querystring.charAt(3) == '"' ? querystring.substring(4, querystring.length() - 1) : querystring.substring(3), fields);
            list.add(doc);
            return list;
        }
        
        SolrDocumentList sdl = this.connector.getDocumentListByQuery(querystring, sort, offset, count, AbstractSolrConnector.ensureEssentialFieldsIncluded(fields));
        /*
        Iterator<SolrDocument> i = sdl.iterator();
        while (i.hasNext()) {
            SolrDocument doc = i.next();
            String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            if (doc != null) updateIdCache(id, AbstractSolrConnector.getLoadDate(doc));
        }
        */
        return sdl;
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
    public BlockingQueue<SolrDocument> concurrentDocumentsByQuery(String querystring, String sort, int offset, int maxcount, long maxtime, int buffersize, final int concurrency, String... fields) {
        return this.connector.concurrentDocumentsByQuery(querystring, sort, offset, maxcount, maxtime, buffersize, concurrency, fields);
    }

    @Override
    public BlockingQueue<String> concurrentIDsByQuery(String querystring, String sort, int offset, int maxcount, long maxtime, int buffersize, final int concurrency) {
        return this.connector.concurrentIDsByQuery(querystring, sort, offset, maxcount, maxtime, buffersize, concurrency);
    }

}
