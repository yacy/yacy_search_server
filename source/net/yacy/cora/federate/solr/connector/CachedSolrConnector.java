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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
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
    private int hitCacheMax, missCacheMax, partitions;
    private final Map<String, HitMissCache> hitMissCache;
    private final ARC<String, SolrDocument> documentCache;
    public long documentCache_Hit = 0, documentCache_Miss = 0, documentCache_Insert = 0; // for statistics only; do not write

    
    public static class HitMissCache {

        public final ARC<String, Object> hitCache, missCache;
        public long hitCache_Hit = 0, hitCache_Miss = 0, hitCache_Insert = 0; // for statistics only; do not write
        public long missCache_Hit = 0, missCache_Miss = 0, missCache_Insert = 0; // for statistics only; do not write
        
        public HitMissCache(int hitCacheMax, int missCacheMax, int partitions) {
            this.hitCache = new ConcurrentARC<String, Object>(hitCacheMax, partitions);
            this.missCache = new ConcurrentARC<String, Object>(missCacheMax, partitions);
        }
        
        public void clearCache() {
            this.hitCache.clear();
            this.missCache.clear();
        }
    }

    public CachedSolrConnector(SolrConnector c, int hitCacheMax, int missCacheMax, int docCacheMax) {
        this.solr = c;
        this.hitCacheMax = hitCacheMax;
        this.missCacheMax = missCacheMax;
        this.partitions = Runtime.getRuntime().availableProcessors() * 2;
        this.hitMissCache = new ConcurrentHashMap<String, HitMissCache>();
        this.documentCache = new ConcurrentARC<String, SolrDocument>(docCacheMax, this.partitions);
    }


    public HitMissCache getCache(String field) {
        HitMissCache c = this.hitMissCache.get(field);
        if (c == null) {
            c =  new HitMissCache(this.hitCacheMax, this.missCacheMax, this.partitions);
            this.hitMissCache.put(field, c);
        }
        return c;
    }

    public void clearCache() {
        for (HitMissCache c: hitMissCache.values()) c.clearCache();
        this.documentCache.clear();
        if (this.solr != null) this.solr.commit(true);
    }

    @Override
    public synchronized void close() {
        if (this.solr != null) this.solr.close();
        this.solr = null;
        this.clearCache();
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        this.clearCache();
        if (this.solr != null) this.solr.clear();
    }

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void delete(final String id) throws IOException {
        this.documentCache.remove(id);
        HitMissCache c = getCache("id");
        c.hitCache.remove(id);
        c.missCache.put(id, EXIST);
        c.missCache_Insert++;
        if (this.solr != null) this.solr.delete(id);
    }

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    @Override
    public void delete(final List<String> ids) throws IOException {
        for (String id: ids) {
            this.documentCache.remove(id);
            HitMissCache c = getCache("id");
            c.hitCache.remove(id);
            c.missCache.put(id, EXIST);
            c.missCache_Insert++;
        }
        if (this.solr != null) this.solr.delete(ids);
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        this.clearCache();
        this.solr.deleteByQuery(querystring);
    }

    @Override
    public boolean exists(final String fieldName, final String key) throws IOException {
        HitMissCache c = getCache(fieldName);
        if (c.hitCache.containsKey(key)) {
            c.hitCache_Hit++;
            return true;
        }
        c.hitCache_Miss++;
        if (this.documentCache.containsKey(key)) {
            this.documentCache_Hit++;
            return true;
        }
        this.documentCache_Miss++;
        if (c.missCache.containsKey(key)) {
            c.missCache_Hit++;
            return false;
        }
        c.missCache_Miss++;
        if (solr != null && solr.exists(fieldName, key)) {
            c.missCache.remove(key);
            c.hitCache.put(key, EXIST);
            c.hitCache_Insert++;
            return true;
        }
        c.missCache.put(key, EXIST);
        c.missCache_Insert++;
        return false;
    }
    
    @Override
    public SolrDocument getById(final String key, final String ... fields) throws IOException {
        SolrDocument doc = fields.length == 0 ? this.documentCache.get(key) : null;
        if (doc != null) {
            this.documentCache_Hit++;
            return doc;
        }
        documentCache_Miss++;
        HitMissCache c = this.getCache(CollectionSchema.id.getSolrFieldName());
        if (c.missCache.containsKey(key)) {
            c.missCache_Hit++;
            return null;
        }
        c.missCache_Miss++;
        if (solr != null && ((doc = solr.getById(key, fields)) != null)) {
            addToCache(doc, fields.length == 0);
            return doc;
        }
        // check if there is a autocommit problem
        if (c.hitCache.containsKey(key)) {
            // the document should be there, therefore make a commit and check again
            if (solr != null && ((doc = solr.getById(key, fields)) != null)) {
                addToCache(doc, fields.length == 0);
            }
        }
        c.missCache.put(key, EXIST);
        c.missCache_Insert++;
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
        SolrDocument doc = ClientUtils.toSolrDocument(solrdoc);
        addToCache(doc, true);
        this.documentCache.put(id, doc);
        this.documentCache_Insert++;
        if (this.solr != null) this.solr.add(solrdoc);
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument solrdoc: solrdocs) {
            String id = (String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName());
            assert id != null;
            if (id == null) continue;
            SolrDocument doc = ClientUtils.toSolrDocument(solrdoc);
            addToCache(doc, true);
            this.documentCache.put(id, doc);
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
    public SolrDocumentList query(final String querystring, final int offset, final int count, final String ... fields) throws IOException {
        if (offset == 0 && count == 1 && querystring.startsWith("id:")) {
            final SolrDocumentList list = new SolrDocumentList();
            SolrDocument doc = getById(querystring.charAt(3) == '"' ? querystring.substring(4, querystring.length() - 1) : querystring.substring(3), fields);
            list.add(doc);
            // no addToCache(list) here because that was already handlet in get();
            return list;
        }
        if (this.solr != null) {
            SolrDocumentList list = this.solr.query(querystring, offset, count, fields);
            addToCache(list, fields.length == 0);
            return list;
        }
        
        // combine both lists
        SolrDocumentList list;
        list = this.solr.query(querystring, offset, count, fields);

        // add caching
        addToCache(list, fields.length == 0);
        return list;
    }

    @Override
    public QueryResponse query(ModifiableSolrParams query) throws IOException, SolrException {
        QueryResponse list = this.solr.query(query);
        return list;
    }
    
    @Override
    public long getQueryCount(final String querystring) throws IOException {
        return this.solr.getQueryCount(querystring);
    }

    @Override
    public Map<String, ReversibleScoreMap<String>> getFacets(final String query, final int maxresults, final String ... fields) throws IOException {
        return this.solr.getFacets(query, maxresults, fields);
    }
    
    private void addToCache(SolrDocumentList list, boolean doccache) {
        if (MemoryControl.shortStatus()) clearCache();
        for (final SolrDocument solrdoc: list) {
            addToCache(solrdoc, doccache);
        }
    }

    private void addToCache(SolrDocument doc, boolean doccach) {
        for (Map.Entry<String, HitMissCache> e: this.hitMissCache.entrySet()) {
            Object keyo = doc.getFieldValue(e.getKey());
            String key = null;
            if (keyo instanceof String) key = (String) keyo;
            if (keyo instanceof Integer) key = ((Integer) keyo).toString();
            if (keyo instanceof Long) key = ((Long) keyo).toString();
            if (key != null) {
                HitMissCache c = e.getValue();
                c.missCache.remove(key);
                c.hitCache.put(key, EXIST);
                c.hitCache_Insert++;
            }
        }
        if (doccach) {
            this.documentCache.put((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()), doc);
            this.documentCache_Insert++;
        }
    }
    

    @Override
    public long getSize() {
        long s = this.solr.getSize();
        HitMissCache c = getCache("id");
        return Math.max(this.documentCache.size(), Math.max(c.hitCache.size(), s));
    }

    public int nameCacheHitSize() {
        HitMissCache c = getCache("id");
        return c.hitCache.size();
    }

    public int nameCacheMissSize() {
        HitMissCache c = getCache("id");
        return c.missCache.size();
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
}