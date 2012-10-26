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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.kelondro.util.MemoryControl;

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
    private final ARC<String, Object> hitCache, missCache;
    private final ARC<String, SolrDocument> documentCache;
    public long cacheHit_Hit = 0, cacheHit_Miss = 0, cacheHit_Insert = 0; // for statistics only; do not write
    public long cacheMiss_Hit = 0, cacheMiss_Miss = 0, cacheMiss_Insert = 0; // for statistics only; do not write
    public long cacheDocument_Hit = 0, cacheDocument_Miss = 0, cacheDocument_Insert = 0; // for statistics only; do not write


    public MirrorSolrConnector(int hitCacheMax, int missCacheMax, int docCacheMax) {
        this.solr0 = null;
        this.solr1 = null;
        int partitions = Runtime.getRuntime().availableProcessors() * 2;
        this.hitCache = new ConcurrentARC<String, Object>(hitCacheMax, partitions);
        this.missCache = new ConcurrentARC<String, Object>(missCacheMax, partitions);
        this.documentCache = new ConcurrentARC<String, SolrDocument>(docCacheMax, partitions);
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
        this.hitCache.clear();
        this.missCache.clear();
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
    public void commit() {
        if (this.solr0 != null) this.solr0.commit();
        if (this.solr1 != null) this.solr1.commit();
    }
    
    @Override
    public synchronized void close() {
        if (this.solr0 != null) this.solr0.close();
        if (this.solr1 != null) this.solr1.close();
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
        this.hitCache.remove(id);
        this.missCache.put(id, EXIST);
        cacheMiss_Insert++;
        this.documentCache.remove(id);
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
            this.hitCache.remove(id);
            this.missCache.put(id, EXIST);
            cacheMiss_Insert++;
            this.documentCache.remove(id);
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

    /**
     * check if a given id exists in solr
     * @param id
     * @return true if any entry in solr exists
     * @throws IOException
     */
    @Override
    public boolean exists(final String id) throws IOException {
        if (this.hitCache.containsKey(id)) {
        	cacheHit_Hit++;
        	return true;
        }
        cacheHit_Miss++;
        if (this.documentCache.containsKey(id)) {
        	cacheDocument_Hit++;
        	return true;
        }
        cacheDocument_Miss++;
        if (this.missCache.containsKey(id)) {
        	cacheMiss_Hit++;
        	return false;
        }
        cacheMiss_Miss++;
        for (SolrConnector solr: new SolrConnector[]{this.solr0, this.solr1}) {
            if (solr != null) {
                if (solr.exists(id)) {
                    this.hitCache.put(id, EXIST);
                    cacheHit_Insert++;
                    return true;
                }
            }
        }
        this.missCache.put(id, EXIST);
        cacheMiss_Insert++;
        return false;
    }

    @Override
    public SolrDocument get(String id) throws IOException {
        SolrDocument doc = this.documentCache.get(id);
        if (doc != null) {
        	cacheDocument_Hit++;
        	return doc;
        }
        cacheDocument_Miss++;
        if (this.missCache.containsKey(id)) {
        	cacheMiss_Hit++;
        	return null;
        }
        cacheMiss_Miss++;
        
        for (SolrConnector solr: new SolrConnector[]{this.solr0, this.solr1}) {
            if (solr != null) {
                doc = solr.get(id);
                if (doc != null) {
                    this.hitCache.put(id, EXIST);
                    cacheHit_Insert++;
                    this.documentCache.put(id, doc);
                    cacheDocument_Insert++;
                    return doc;
                }
            }
        }
        this.missCache.put(id, EXIST);
        cacheMiss_Insert++;
        return null;
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException {
        String id = (String) solrdoc.getFieldValue(YaCySchema.id.name());
        assert id != null;
        if (id == null) return;
        this.missCache.remove(id);
        this.documentCache.put(id, ClientUtils.toSolrDocument(solrdoc));
        cacheDocument_Insert++;
        if (this.solr0 != null) this.solr0.add(solrdoc);
        if (this.solr1 != null) this.solr1.add(solrdoc);
        this.hitCache.put(id, EXIST);
        cacheHit_Insert++;
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument solrdoc: solrdocs) add(solrdoc);
    }

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList query(final String querystring, final int offset, final int count) throws IOException {
        if (this.solr0 == null && this.solr1 == null) return new SolrDocumentList();
        if (offset == 0 && count == 1 && querystring.startsWith("id:")) {
            final SolrDocumentList list = new SolrDocumentList();
            SolrDocument doc = get(querystring.charAt(3) == '"' ? querystring.substring(4, querystring.length() - 1) : querystring.substring(3));
            list.add(doc);
            // no addToCache(list) here because that was already handlet in get();
            return list;
        }
        if (this.solr0 != null && this.solr1 == null) {
            SolrDocumentList list = this.solr0.query(querystring, offset, count);
            addToCache(list);
            return list;
        }
        if (this.solr1 != null && this.solr0 == null) {
            SolrDocumentList list = this.solr1.query(querystring, offset, count);
            addToCache(list);
            return list;
        }

        // combine both lists
        SolrDocumentList l;
        l = this.solr0.query(querystring, offset, count);
        if (l.size() >= count) return l;

        // at this point we need to know how many results are in solr0
        // compute this with a very bad hack; replace with better method later
        int size0 = 0;
        { //bad hack - TODO: replace
            SolrDocumentList lHack = this.solr0.query(querystring, 0, Integer.MAX_VALUE);
            size0 = lHack.size();
        }

        // now use the size of the first query to do a second query
        final SolrDocumentList list = new SolrDocumentList();
        for (final SolrDocument d: l) list.add(d);
        l = this.solr1.query(querystring, offset + l.size() - size0, count - l.size());
        for (final SolrDocument d: l) list.add(d);

        // add caching
        addToCache(list);
        return list;
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
    
    @Override
    public long getQueryCount(final String querystring) throws IOException {
        if (this.solr0 == null && this.solr1 == null) return 0;
        if (this.solr0 != null && this.solr1 == null) {
            return this.solr0.getQueryCount(querystring);
        }
        if (this.solr1 != null && this.solr0 == null) {
            return this.solr1.getQueryCount(querystring);
        }
        final AtomicLong count = new AtomicLong(0);
        Thread t0 = new Thread() {
            @Override
            public void run() {
                try {
                    count.addAndGet(MirrorSolrConnector.this.solr0.getQueryCount(querystring));
                } catch (IOException e) {}
            }
        };
        t0.start();
        Thread t1 = new Thread() {
            @Override
            public void run() {
                try {
                    count.addAndGet(MirrorSolrConnector.this.solr1.getQueryCount(querystring));
                } catch (IOException e) {}
            }
        };
        t1.start();
        try {t0.join();} catch (InterruptedException e) {}
        try {t1.join();} catch (InterruptedException e) {}
        return count.get();
    }

    /**
     * get a facet of the index: a list of values that are most common in a specific field
     * @param field the field which is selected for the facet
     * @param maxresults the maximum size of the resulting map
     * @return an ordered map of fields
     * @throws IOException
     */
    public ReversibleScoreMap<String> getFacet(String field, int maxresults) throws IOException {
        if (this.solr0 == null && this.solr1 == null) return new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
        if (this.solr0 != null && this.solr1 == null) {
            return this.solr0.getFacet(field, maxresults);
        }
        if (this.solr1 != null && this.solr0 == null) {
            return this.solr1.getFacet(field, maxresults);
        }
        ReversibleScoreMap<String> facet0 = this.solr0.getFacet(field, maxresults);
        ReversibleScoreMap<String> facet1 = this.solr1.getFacet(field, maxresults);
        for (String key: facet1) facet0.inc(key, facet1.get(key));
        return facet0;
    }
    
    private void addToCache(SolrDocumentList list) {
        if (MemoryControl.shortStatus()) clearCache();
        for (final SolrDocument solrdoc: list) {
            String id = (String) solrdoc.getFieldValue(YaCySchema.id.getSolrFieldName());
            if (id != null) {
                this.hitCache.put(id, EXIST);
                cacheHit_Insert++;
                this.documentCache.put(id, solrdoc);
                cacheDocument_Insert++;
            }
        }
    }

    @Override
    public long getSize() {
        long s = 0;
        if (this.solr0 != null) s += this.solr0.getSize();
        if (this.solr1 != null) s += this.solr1.getSize();
        return s;
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
}
