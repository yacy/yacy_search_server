/**
 *  CachedSolrConnector
 *  Copyright 2013 by Michael Peter Christen
 *  First released 18.02.2013 at http://yacy.net
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
import java.util.Map;

import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
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

public class CachedSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final static Object EXIST = new Object();

    private SolrConnector solr;
    private final ARC<String, SolrDocument> documentCache;
    public final ARC<String, Object> hitCache, missCache;
    public long documentCache_Hit = 0, documentCache_Miss = 0, documentCache_Insert = 0; // for statistics only; do not write
    public long hitCache_Hit = 0, hitCache_Miss = 0, hitCache_Insert = 0; // for statistics only; do not write
    public long missCache_Hit = 0, missCache_Miss = 0, missCache_Insert = 0; // for statistics only; do not write

    private static final String idQuery(String id) {
        return CollectionSchema.id.getSolrFieldName() + ":\"" + id + "\"";
    }
    
    public CachedSolrConnector(SolrConnector c, int hitCacheMax, int missCacheMax, int docCacheMax) {
        this.solr = c;
        int partitions = Runtime.getRuntime().availableProcessors() * 2;
        this.documentCache = new ConcurrentARC<String, SolrDocument>(docCacheMax, partitions);
        this.hitCache = new ConcurrentARC<String, Object>(hitCacheMax, partitions);
        this.missCache = new ConcurrentARC<String, Object>(missCacheMax, partitions);
    }

    @Override
    public int bufferSize() {
        return solr.bufferSize();
    }

    @Override
    public void clearCaches() {
        this.hitCache.clear();
        this.missCache.clear();
        this.documentCache.clear();
        if (this.solr != null) this.solr.commit(true);
    }

    @Override
    public boolean isClosed() {
        return this.solr == null || this.solr.isClosed(); 
    }
    
    @Override
    protected void finalize() throws Throwable {
        this.close();
    }

    @Override
    public synchronized void close() {
        this.clearCaches();
        if (this.solr != null) this.solr.close();
        this.solr = null;
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        this.clearCaches();
        if (this.solr != null) this.solr.clear();
    }

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void deleteById(final String id) throws IOException {
        String q = idQuery(id);
        this.documentCache.remove(q);
        this.hitCache.remove(q);
        this.missCache.put(q, EXIST);
        this.missCache_Insert++;
        if (this.solr != null) this.solr.deleteByQuery(q);
    }

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    @Override
    public void deleteByIds(final Collection<String> ids) throws IOException {
        for (String id: ids) {
            String q = idQuery(id);
            this.documentCache.remove(q);
            this.hitCache.remove(q);
            this.missCache.put(q, EXIST);
            this.missCache_Insert++;
        }
        if (this.solr != null) this.solr.deleteByIds(ids);
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        this.clearCaches();
        this.solr.deleteByQuery(querystring);
    }
    
    @Override
    public SolrDocument getDocumentById(final String id, final String ... fields) throws IOException {
        assert id.length() == Word.commonHashLength : "wrong id: " + id;
        String q = idQuery(id);
        SolrDocument doc = fields.length == 0 ? this.documentCache.get(q) : null;
        if (doc != null) {
            this.documentCache_Hit++;
            return doc;
        }
        documentCache_Miss++;
        if (this.missCache.containsKey(q)) {
            this.missCache_Hit++;
            return null;
        }
        this.missCache_Miss++;
        if (solr != null && ((doc = solr.getDocumentById(id, fields)) != null)) {
            addToCache(doc, fields.length == 0);
            return doc;
        }
        // check if there is a autocommit problem
        if (this.hitCache.containsKey(q)) {
            // the document should be there, therefore make a commit and check again
            if (solr != null && ((doc = solr.getDocumentById(id, fields)) != null)) {
                addToCache(doc, fields.length == 0);
            }
        }
        this.missCache.put(q, EXIST);
        this.missCache_Insert++;
        return null;
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException {
        String id = (String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        assert id != null;
        if (id == null) return;
        String q = idQuery(id);
        SolrDocument doc = ClientUtils.toSolrDocument(solrdoc);
        addToCache(doc, true);
        this.documentCache.put(q, doc);
        this.documentCache_Insert++;
        if (this.solr != null) this.solr.add(solrdoc);
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument solrdoc: solrdocs) {
            String id = (String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            assert id != null;
            if (id == null) continue;
            String q = idQuery(id);
            SolrDocument doc = ClientUtils.toSolrDocument(solrdoc);
            addToCache(doc, true);
            this.documentCache.put(q, doc);
            this.documentCache_Insert++;
        }
        if (this.solr != null) this.solr.add(solrdocs);
    }
    
    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList getDocumentListByQuery(final String querystring, final String sort, final int offset, final int count, final String ... fields) throws IOException {
        if (offset == 0 && count == 1 && querystring.startsWith("id:") &&
            ((querystring.length() == 17 && querystring.charAt(3) == '"' && querystring.charAt(16) == '"') ||
             querystring.length() == 15)) {
            final SolrDocumentList list = new SolrDocumentList();
            SolrDocument doc = getDocumentById(querystring.charAt(3) == '"' ? querystring.substring(4, querystring.length() - 1) : querystring.substring(3), fields);
            list.add(doc);
            // no addToCache(list) here because that was already handlet in get();
            return list;
        }
        if (this.solr != null) {
            SolrDocumentList list = this.solr.getDocumentListByQuery(querystring, sort, offset, count, fields);
            addToCache(list, fields.length == 0);
            return list;
        }
        
        // combine both lists
        SolrDocumentList list;
        list = this.solr.getDocumentListByQuery(querystring, sort, offset, count, fields);

        // add caching
        addToCache(list, fields.length == 0);
        return list;
    }

    @Override
    public QueryResponse getResponseByParams(ModifiableSolrParams query) throws IOException, SolrException {
        QueryResponse list = this.solr.getResponseByParams(query);
        return list;
    }

    @Override
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams params) throws IOException, SolrException {
        SolrDocumentList sdl = this.solr.getDocumentListByParams(params);
        return sdl;
    }
    
    @Override
    public long getCountByQuery(final String querystring) throws IOException {
        return this.solr.getCountByQuery(querystring);
    }

    @Override
    public Map<String, ReversibleScoreMap<String>> getFacets(final String query, final int maxresults, final String ... fields) throws IOException {
        return this.solr.getFacets(query, maxresults, fields);
    }
    
    private void addToCache(SolrDocumentList list, boolean doccache) {
        if (MemoryControl.shortStatus()) clearCaches();
        for (final SolrDocument solrdoc: list) {
            addToCache(solrdoc, doccache);
        }
    }

    private void addToCache(SolrDocument doc, boolean doccach) {
        String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        String q = idQuery(id);
        this.missCache.remove(q);
        this.hitCache.put(q, EXIST);
        this.hitCache_Insert++;
        if (doccach) {
            this.documentCache.put(q, doc);
            this.documentCache_Insert++;
        }
    }

    @Override
    public long getSize() {
        long s = this.solr.getSize();
        return Math.max(this.documentCache.size(), Math.max(this.hitCache.size(), s)); // this might be incorrect if there are other requests than "id:.." in the cache
    }

    public int nameCacheHitSize() {
        return this.hitCache.size();
    }

    public int nameCacheMissSize() {
        return this.missCache.size();
    }

    public int nameCacheDocumentSize() {
        return this.documentCache.size();
    }

    @Override
    public void commit(boolean softCommit) {
        this.solr.commit(softCommit);
    }

    @Override
    public void optimize(int maxSegments) {
        this.solr.optimize(maxSegments);
    }

    @Override
    public int getSegmentCount() {
        return this.solr.getSegmentCount();
    }
    
}