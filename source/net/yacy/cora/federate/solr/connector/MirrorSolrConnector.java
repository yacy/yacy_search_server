/**
 *  DoubleChardingConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 23.07.2012 at http://yacy.net
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;

/**
 * Implementation of a mirrored solr connector.
 * Two Solr servers can be attached to serve as storage and search locations.
 * When doing a retrieval only the first Solr is requested, if it does not answer or has no result, the second is used.
 * When data is stored or deleted this applies to both attached solr.
 * It is also possible to attach only one of the solr instances.
 * Because it is not possible to set a cache in front of this class (the single connect methods would need to be passed through the cache class),
 * this class also contains an object and hit/miss cache.
 */
public class MirrorSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final static Object EXIST = new Object();

    private SolrConnector solr0;
    private SolrConnector solr1;
    private int hitCacheMax, missCacheMax, partitions;
    private final Map<String, HitMissCache> hitMissCache;
    private final Map<String, ARC<String, Object>> fieldCache; // a map from a field name to a id-key/value object cache
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

    public MirrorSolrConnector(int hitCacheMax, int missCacheMax, int docCacheMax) {
        this.solr0 = null;
        this.solr1 = null;
        this.hitCacheMax = hitCacheMax;
        this.missCacheMax = missCacheMax;
        this.partitions = Runtime.getRuntime().availableProcessors() * 2;
        this.hitMissCache = new ConcurrentHashMap<String, HitMissCache>();
        this.fieldCache = new ConcurrentHashMap<String, ARC<String, Object>>();
        this.documentCache = new ConcurrentARC<String, SolrDocument>(docCacheMax, this.partitions);
    }

    public HitMissCache getHitMissCache(String field) {
        HitMissCache c = this.hitMissCache.get(field);
        if (c == null) {
            c =  new HitMissCache(this.hitCacheMax, this.missCacheMax, this.partitions);
            this.hitMissCache.put(field, c);
        }
        return c;
    }
    
    public ARC<String, Object> getFieldCache(String field) {
        ARC<String, Object> c = this.fieldCache.get(field);
        if (c == null) {
            c =  new ConcurrentARC<String, Object>(this.hitCacheMax, this.partitions);
            this.fieldCache.put(field, c);
        }
        return c;
    }
    
    public boolean isConnected0() {
        return this.solr0 != null;
    }

    public void connect0(SolrConnector c) {
        this.solr0 = c;
    }

    public SolrConnector getSolr0() {
        return this.solr0;
    }

    public void disconnect0() {
        if (this.solr0 == null) return;
        this.solr0.close();
        this.solr0 = null;
    }

    public boolean isConnected1() {
        return this.solr1 != null;
    }

    public void connect1(SolrConnector c) {
        this.solr1 = c;
    }

    public SolrConnector getSolr1() {
        return this.solr1;
    }

    public void disconnect1() {
        if (this.solr1 == null) return;
        this.solr1.close();
        this.solr1 = null;
    }

    public void clearCache() {
        for (HitMissCache c: this.hitMissCache.values()) c.clearCache();
        for (ARC<String, Object> c: this.fieldCache.values()) c.clear();
        this.documentCache.clear();
    }

    @Override
    public int getCommitWithinMs() {
        if (this.solr0 != null) this.solr0.getCommitWithinMs();
        if (this.solr1 != null) this.solr1.getCommitWithinMs();
        return -1;
    }

    /**
     * set the solr autocommit delay
     * @param c the maximum waiting time after a solr command until it is transported to the server
     */
    @Override
    public void setCommitWithinMs(int c) {
        if (this.solr0 != null) this.solr0.setCommitWithinMs(c);
        if (this.solr1 != null) this.solr1.setCommitWithinMs(c);
    }

    @Override
    public void commit(boolean softCommit) {
        if (this.solr0 != null) this.solr0.commit(softCommit);
        if (this.solr1 != null) this.solr1.commit(softCommit);
    }

    /**
     * force an explicit merge of segments
     * @param maxSegments the maximum number of segments. Set to 1 for maximum optimization
     */
    public void optimize(int maxSegments) {
        if (this.solr0 != null) this.solr0.optimize(maxSegments);
        if (this.solr1 != null) this.solr1.optimize(maxSegments);
    }
    
    @Override
    public synchronized void close() {
        if (this.solr0 != null) this.solr0.close();
        if (this.solr1 != null) this.solr1.close();
    }

    /**
     * delete everything in the local solr index
     * @throws IOException
     */
    public void clear0() throws IOException {
        this.clearCache();
        if (this.solr0 != null) this.solr0.clear();
    }

    /**
     * delete everything in the remote solr index
     * @throws IOException
     */
    public void clear1() throws IOException {
        this.clearCache();
        if (this.solr1 != null) this.solr1.clear();
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        this.clearCache();
        if (this.solr0 != null) this.solr0.clear();
        if (this.solr1 != null) this.solr1.clear();
    }

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void delete(final String id) throws IOException {
        this.documentCache.remove(id);
        HitMissCache c = getHitMissCache("id");
        c.hitCache.remove(id);
        c.missCache.put(id, EXIST);
        c.missCache_Insert++;
        if (this.solr0 != null) this.solr0.delete(id);
        if (this.solr1 != null) this.solr1.delete(id);
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
            HitMissCache c = getHitMissCache("id");
            c.hitCache.remove(id);
            c.missCache.put(id, EXIST);
            c.missCache_Insert++;
        }
        if (this.solr0 != null) this.solr0.delete(ids);
        if (this.solr1 != null) this.solr1.delete(ids);
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        if (this.solr0 != null) this.solr0.deleteByQuery(querystring);
        if (this.solr1 != null) this.solr1.deleteByQuery(querystring);
        this.clearCache();
    }

    @Override
    public boolean exists(final String fieldName, final String key) throws IOException {
        HitMissCache c = getHitMissCache(fieldName);
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
        if ((solr0 != null && solr0.exists(fieldName, key)) || (solr1 != null && solr1.exists(fieldName, key))) {
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
    public Object getFieldById(final String key, final String field) throws IOException {
        // try to get this from this cache
        ARC<String, Object> c = getFieldCache(field);
        Object o = c.get(key);
        if (o != null) return o;
        
        // load the document
        o = super.getFieldById(key, field);

        // use result to fill the cache
        if (o == null) return null;
        c.put(key, o);
        return o;
    }
    
    @Override
    public SolrDocument getById(final String key, final String ... fields) throws IOException {
        SolrDocument doc = this.documentCache.get(key);
        if (doc != null) {
            this.documentCache_Hit++;
        	return doc;
        }
        documentCache_Miss++;
        HitMissCache c = this.getHitMissCache(YaCySchema.id.getSolrFieldName());
        if (c.missCache.containsKey(key)) {
            c.missCache_Hit++;
        	return null;
        }
        c.missCache_Miss++;
        if ((solr0 != null && ((doc = solr0.getById(key, fields)) != null)) || (solr1 != null && ((doc = solr1.getById(key, fields)) != null))) {
            addToCache(doc, fields.length == 0);
            return doc;
        }
        // check if there is a autocommit problem
        if (c.hitCache.containsKey(key)) {
            // the document should be there, therefore make a commit and check again
            this.commit(true);
            if ((solr0 != null && ((doc = solr0.getById(key, fields)) != null)) || (solr1 != null && ((doc = solr1.getById(key, fields)) != null))) {
                addToCache(doc, fields.length == 0);
                return doc;
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
        String id = (String) solrdoc.getFieldValue(YaCySchema.id.getSolrFieldName());
        assert id != null;
        if (id == null) return;
        SolrDocument doc = ClientUtils.toSolrDocument(solrdoc);
        addToCache(doc, true);
        this.documentCache.put(id, doc);
        this.documentCache_Insert++;
        if (this.solr0 != null) this.solr0.add(solrdoc);
        if (this.solr1 != null) this.solr1.add(solrdoc);
    }

    @Override
    public QueryResponse query(ModifiableSolrParams query) throws IOException, SolrException {
        Integer count0 = query.getInt(CommonParams.ROWS);
        int count = count0 == null ? 10 : count0.intValue();
        Integer start0 = query.getInt(CommonParams.START);
        int start = start0 == null ? 0 : start0.intValue();
        if (this.solr0 == null && this.solr1 == null) return new QueryResponse();

        if (this.solr0 != null && this.solr1 == null) {
            QueryResponse list = this.solr0.query(query);
            return list;
        }
        if (this.solr1 != null && this.solr0 == null) {
            QueryResponse list = this.solr1.query(query);
            return list;
        }

        // combine both lists
        QueryResponse rsp = this.solr0.query(query);
        final SolrDocumentList l = rsp.getResults();
        if (l.size() >= count) return rsp;

        // at this point we need to know how many results are in solr0
        // compute this with a very bad hack; replace with better method later
        int size0 = 0;
        { //bad hack - TODO: replace
            query.set(CommonParams.START, 0);
            query.set(CommonParams.ROWS, Integer.MAX_VALUE);
            QueryResponse lHack = this.solr0.query(query);
            query.set(CommonParams.START, start);
            query.set(CommonParams.ROWS, count);
            size0 = lHack.getResults().size();
        }

        // now use the size of the first query to do a second query
        query.set(CommonParams.START, start + l.size() - size0);
        query.set(CommonParams.ROWS, count - l.size());
        QueryResponse rsp1 = this.solr1.query(query);
        query.set(CommonParams.START, start);
        query.set(CommonParams.ROWS, count);
        // TODO: combine both
        return rsp1;
    }

    private void addToCache(SolrDocument doc, boolean doccach) {
        for (Map.Entry<String, HitMissCache> e: this.hitMissCache.entrySet()) {
            Object keyo = doc.getFieldValue(e.getKey());
            if (keyo == null) continue;
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

        String id = (String) doc.getFieldValue(YaCySchema.id.getSolrFieldName());
        if (id != null) {
        for (Map.Entry<String, ARC<String, Object>> e: this.fieldCache.entrySet()) {
            Object keyo = doc.getFieldValue(e.getKey());
            if (keyo != null) e.getValue().put(id, keyo);
        }

        if (doccach) {
            this.documentCache.put(id, doc);
            this.documentCache_Insert++;
        }
        }
    }
    
    @Override
    public long getSize() {
        long s = 0;
        if (this.solr0 != null) s += this.solr0.getSize();
        if (this.solr1 != null) s += this.solr1.getSize();
        HitMissCache c = getHitMissCache("id");
        return Math.max(this.documentCache.size(), Math.max(c.hitCache.size(), s));
    }

	public int nameCacheHitSize() {
        HitMissCache c = getHitMissCache("id");
		return c.hitCache.size();
	}

	public int nameCacheMissSize() {
        HitMissCache c = getHitMissCache("id");
		return c.missCache.size();
	}

	public int nameCacheDocumentSize() {
		return this.documentCache.size();
	}
}
