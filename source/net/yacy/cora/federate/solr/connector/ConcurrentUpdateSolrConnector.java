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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
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

    private final static long AUTOCOMMIT = 3000; // milliseconds
    
    private class CommitHandler extends Thread {
        @Override
        public void run() {
            try {
                while (ConcurrentUpdateSolrConnector.this.commitProcessRunning) {
                    commitDocBuffer();
                    try {Thread.sleep(AUTOCOMMIT);} catch (final InterruptedException e) {
                        ConcurrentLog.logException(e);
                    }
                }
            } finally {
                commitDocBuffer();
            }
        }
    }

    private SolrConnector connector;
    private ARC<String, LoadTimeURL> metadataCache;
    //private final ARH<String> missCache;
    private final LinkedHashMap<String, SolrInputDocument> docBuffer;
    private CommitHandler processHandler;
    private final int updateCapacity;
    private boolean commitProcessRunning;
    
    public ConcurrentUpdateSolrConnector(final SolrConnector connector, final int updateCapacity, final int idCacheCapacity, final int concurrency) {
        this.connector = connector;
        this.updateCapacity = updateCapacity;
        this.metadataCache = new ConcurrentARC<>(idCacheCapacity, concurrency);
        //this.missCache = new ConcurrentARH<>(idCacheCapacity, concurrency);
        this.docBuffer = new LinkedHashMap<>();
        this.processHandler = null;
        this.commitProcessRunning = true;
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

    private void commitDocBuffer() {
        synchronized (this.docBuffer) {
            //System.out.println("*** commit of " + this.docBuffer.size() + " documents");
            //Thread.dumpStack();
            if (this.docBuffer.size() > 0) try {
                this.connector.add(this.docBuffer.values());
            } catch (final OutOfMemoryError e) {
                // clear and try again...
                clearCaches();
                try {
                    this.connector.add(this.docBuffer.values());
                } catch (final IOException ee) {
                    ConcurrentLog.logException(e);
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
            this.docBuffer.clear();
        }
    }
    
    @Override
    public int bufferSize() {
        return this.docBuffer.size();
    }

    @Override
    public void clearCaches() {
        this.connector.clearCaches();
        this.metadataCache.clear();
        //this.missCache.clear();
    }

    private void updateCache(final String id, final LoadTimeURL md) {
        if (id == null) return;
        if (MemoryControl.shortStatus()) {
            this.metadataCache.clear();
            //this.missCache.clear();
        }
        this.metadataCache.put(id, md);
        //this.missCache.delete(id);
    }
    
    public void ensureAliveProcessHandler() {
        if (this.processHandler == null || !this.processHandler.isAlive()) {
            this.processHandler = new CommitHandler();
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
        return Math.max(this.metadataCache.size(), this.connector.getSize());
    }

    @Override
    public void commit(boolean softCommit) {
        ensureAliveProcessHandler();
        commitDocBuffer();
        this.connector.commit(softCommit);
    }

    @Override
    public void optimize(int maxSegments) {
        commitDocBuffer();
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
        this.commitProcessRunning = false;
        try {this.processHandler.join();} catch (final InterruptedException e) {}
        this.connector.close();
        this.metadataCache.clear();
        this.connector = null;
        this.metadataCache = null;
    }

    @Override
    public void clear() throws IOException {
        this.docBuffer.clear();
        this.connector.clear();
        this.metadataCache.clear();
        //this.missCache.clear();
    }

    @Override
    public synchronized void deleteById(String id) throws IOException {
        this.metadataCache.remove(id);
        //this.missCache.add(id);
        synchronized (this.docBuffer) {
            this.docBuffer.remove(id);
        }
        this.connector.deleteById(id);
    }

    @Override
    public synchronized void deleteByIds(Collection<String> ids) throws IOException {
        for (String id: ids) {
            this.metadataCache.remove(id);
            //this.missCache.add(id);
        }
        synchronized (this.docBuffer) {
            for (String id: ids) {
                this.docBuffer.remove(id);
            }
        }
        this.connector.deleteByIds(ids);
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        commitDocBuffer();
        try {
            this.connector.deleteByQuery(querystring);
            this.metadataCache.clear();
        } catch (final IOException e) {
            ConcurrentLog.severe("ConcurrentUpdateSolrConnector", e.getMessage(), e);
        }
    }

    @Override
    public LoadTimeURL getLoadTimeURL(String id) throws IOException {
        //if (this.missCache.contains(id)) return null;
        LoadTimeURL md = this.metadataCache.get(id);
        if (md != null) {
            //System.out.println("*** metadata cache hit; metadataCache.size() = " + metadataCache.size());
            //Thread.dumpStack();
            return md;
        }
        SolrInputDocument doc = this.docBuffer.get(id);
        if (doc != null) {
            //System.out.println("*** docBuffer cache hit; docBuffer.size() = " + docBuffer.size());
            //Thread.dumpStack();
            return AbstractSolrConnector.getLoadTimeURL(doc);
        }
        md = this.connector.getLoadTimeURL(id);
        if (md == null) {/*this.missCache.add(id);*/ return null;}
        updateCache(id, md);
        return md;
    }
    
    @Override
    public void add(SolrInputDocument solrdoc) throws IOException, SolrException {
        String id = (String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        updateCache(id, AbstractSolrConnector.getLoadTimeURL(solrdoc));
        ensureAliveProcessHandler();
        if (this.processHandler.isAlive()) {
            synchronized (this.docBuffer) {this.docBuffer.put(id, solrdoc);}
        } else {
            this.connector.add(solrdoc);
        }
        if (MemoryControl.shortStatus() || this.docBuffer.size() > this.updateCapacity) {
            commitDocBuffer();
        }
    }

    @Override
    public void add(Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        ensureAliveProcessHandler();
        synchronized (this.docBuffer) {
            for (SolrInputDocument solrdoc: solrdocs) {
                String id = (String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                updateCache(id, AbstractSolrConnector.getLoadTimeURL(solrdoc));
                if (this.processHandler.isAlive()) {
                    this.docBuffer.put(id, solrdoc);
                } else {
                    this.connector.add(solrdoc);
                }
            }
        }
        if (MemoryControl.shortStatus() || this.docBuffer.size() > this.updateCapacity) {
            commitDocBuffer();
        }
    }
    
    @Override
    public SolrDocument getDocumentById(final String id, String... fields) throws IOException {
        assert id.length() == Word.commonHashLength : "wrong id: " + id;
        //if (this.missCache.contains(id)) return null;
        SolrInputDocument idoc = this.docBuffer.get(id);
        if (idoc != null) {
            //System.out.println("*** docBuffer cache hit; docBuffer.size() = " + docBuffer.size());
            //Thread.dumpStack();
            return ClientUtils.toSolrDocument(idoc);
        }
        SolrDocument solrdoc = this.connector.getDocumentById(id, AbstractSolrConnector.ensureEssentialFieldsIncluded(fields));
        if (solrdoc == null) {
            //this.missCache.add(id);
            this.metadataCache.remove(id);
        } else {
            updateCache(id, AbstractSolrConnector.getLoadTimeURL(solrdoc));
        }
        return solrdoc;
    }

    @Override
    public QueryResponse getResponseByParams(ModifiableSolrParams query) throws IOException, SolrException {
        commitDocBuffer();
        return this.connector.getResponseByParams(query);
    }

    @Override
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams params) throws IOException, SolrException {
        commitDocBuffer();
        SolrDocumentList sdl = this.connector.getDocumentListByParams(params);
        for (SolrDocument doc: sdl) {
            String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            updateCache(id, AbstractSolrConnector.getLoadTimeURL(doc));
        }
        return sdl;
    }
    
    @Override
    public SolrDocumentList getDocumentListByQuery(String querystring, String sort, int offset, int count, String... fields) throws IOException, SolrException {
        commitDocBuffer();
        if (offset == 0 && count == 1 && querystring.startsWith("id:") &&
            ((querystring.length() == 17 && querystring.charAt(3) == '"' && querystring.charAt(16) == '"') ||
             querystring.length() == 15)) {
            final SolrDocumentList list = new SolrDocumentList();
            SolrDocument doc = getDocumentById(querystring.charAt(3) == '"' ? querystring.substring(4, querystring.length() - 1) : querystring.substring(3), fields);
            list.add(doc);
            return list;
        }
        
        SolrDocumentList sdl = this.connector.getDocumentListByQuery(querystring, sort, offset, count, AbstractSolrConnector.ensureEssentialFieldsIncluded(fields));
        return sdl;
    }

    @Override
    public long getCountByQuery(String querystring) throws IOException {
        commitDocBuffer();
        return this.connector.getCountByQuery(querystring);
    }

    @Override
    public LinkedHashMap<String, ReversibleScoreMap<String>> getFacets(String query, int maxresults, String... fields) throws IOException {
        commitDocBuffer();
        return this.connector.getFacets(query, maxresults, fields);
    }

    @Override
    public BlockingQueue<SolrDocument> concurrentDocumentsByQuery(String querystring, String sort, int offset, int maxcount, long maxtime, int buffersize, final int concurrency, final boolean prefetchIDs, String... fields) {
        commitDocBuffer();
        return this.connector.concurrentDocumentsByQuery(querystring, sort, offset, maxcount, maxtime, buffersize, concurrency, prefetchIDs, fields);
    }

    @Override
    public BlockingQueue<SolrDocument> concurrentDocumentsByQueries(
            List<String> querystrings, String sort, int offset, int maxcount,
            long maxtime, int buffersize, int concurrency, boolean prefetchIDs,
            String... fields) {
        commitDocBuffer();
        return this.connector.concurrentDocumentsByQueries(querystrings, sort, offset, maxcount, maxtime, buffersize, concurrency, prefetchIDs, fields);
    }

    @Override
    public BlockingQueue<String> concurrentIDsByQuery(String querystring, String sort, int offset, int maxcount, long maxtime, int buffersize, final int concurrency) {
        commitDocBuffer();
        return this.connector.concurrentIDsByQuery(querystring, sort, offset, maxcount, maxtime, buffersize, concurrency);
    }
    
    @Override
    public BlockingQueue<String> concurrentIDsByQueries(
            List<String> querystrings, String sort, int offset, int maxcount,
            long maxtime, int buffersize, int concurrency) {
        commitDocBuffer();
        return this.connector.concurrentIDsByQueries(querystrings, sort, offset, maxcount, maxtime, buffersize, concurrency);
    }

    @Override
    public void update(final SolrInputDocument solrdoc) throws IOException, SolrException {
        commitDocBuffer();
        this.connector.update(solrdoc);
    }

    @Override
    public void update(final Collection<SolrInputDocument> solrdoc) throws IOException, SolrException {
        commitDocBuffer();
        this.connector.update(solrdoc);        
    }

}
