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

package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.search.index.YaCySchema;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

/**
 * Implementation of a mirrored solr connector.
 * Two Solr servers can be attached to serve as storage and search locations.
 * When doing a retrieval only the first Solr is requested, if it does not answer or has no result, the second is used.
 * When data is stored or deleted this applies to both attached solr.
 * It is also possible to attach only one of the solr instances.
 * Because it is not possible to set a cache in front of this class (the single connect methods would need to be passed through the cache class),
 * this class also contains an object and hit/miss cache.
 */
public class MirrorSolrConnector implements SolrConnector {

    private final static Object EXIST = new Object();

    private SolrConnector solr0;
    private SolrConnector solr1;
    private final ARC<String, Object> hitCache, missCache;
    private final ARC<String, SolrDocument> documentCache;


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
        this.hitCache.clear();
        this.missCache.clear();
        this.documentCache.clear();
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
            this.documentCache.remove(id);
        }
        if (this.solr0 != null) this.solr0.delete(ids);
        if (this.solr1 != null) this.solr1.delete(ids);
    }

    /**
     * check if a given id exists in solr
     * @param id
     * @return true if any entry in solr exists
     * @throws IOException
     */
    @Override
    public boolean exists(final String id) throws IOException {
        if (this.hitCache.containsKey(id) || this.documentCache.containsKey(id)) return true;
        if (this.missCache.containsKey(id)) return false;
        if (this.solr0 != null) {
            if (this.solr0.exists(id)) {
                this.hitCache.put(id, EXIST);
                return true;
            }
        }
        if (this.solr1 != null) {
            if (this.solr1.exists(id)) {
                this.hitCache.put(id, EXIST);
                return true;
            }
        }
        this.missCache.put(id, EXIST);
        return false;
    }

    @Override
    public SolrDocument get(String id) throws IOException {
        SolrDocument doc = this.documentCache.get(id);
        if (this.missCache.containsKey(id)) return null;
        if (doc != null) return doc;
        if (this.solr0 != null) {
            doc = this.solr0.get(id);
            if (doc != null) {
                this.hitCache.put(id, EXIST);
                this.documentCache.put(id, doc);
                return doc;
            }
        }
        if (this.solr1 != null) {
            doc = this.solr1.get(id);
            if (doc != null) {
                this.hitCache.put(id, EXIST);
                this.documentCache.put(id, doc);
                return doc;
            }
        }
        this.missCache.put(id, EXIST);
        return null;
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException {
        if (this.solr0 != null) {
            this.solr0.add(solrdoc);
        }
        if (this.solr1 != null) {
            this.solr1.add(solrdoc);
        }
        String id = (String) solrdoc.getFieldValue(YaCySchema.id.name());
        if (id != null) {
            this.hitCache.put(id, EXIST);
            this.documentCache.put(id, ClientUtils.toSolrDocument(solrdoc));
        }
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        if (this.solr0 != null) {
            for (SolrInputDocument d: solrdocs) this.solr0.add(d);
        }
        if (this.solr1 != null) {
            for (SolrInputDocument d: solrdocs) this.solr1.add(d);
        }
        for (SolrInputDocument solrdoc: solrdocs) {
            String id = (String) solrdoc.getFieldValue(YaCySchema.id.name());
            if (id != null) {
                this.hitCache.put(id, EXIST);
                this.documentCache.put(id, ClientUtils.toSolrDocument(solrdoc));
            }
        }
    }

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList query(final String querystring, final int offset, final int count) throws IOException {
        final SolrDocumentList list = new SolrDocumentList();
        if (this.solr0 == null && this.solr1 == null) return list;
        if (offset == 0 && count == 1 && querystring.startsWith("id:")) {
            SolrDocument doc = get(querystring.charAt(3) == '"' ? querystring.substring(4, querystring.length() - 1) : querystring.substring(3));
            list.add(doc);
            return list;
        }
        if (this.solr0 != null && this.solr1 == null) return this.solr0.query(querystring, offset, count);
        if (this.solr1 != null && this.solr0 == null) return this.solr1.query(querystring, offset, count);

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
        for (final SolrDocument d: l) list.add(d);
        l = this.solr1.query(querystring, offset + l.size() - size0, count - l.size());
        for (final SolrDocument d: l) list.add(d);

        // add caching
        for (final SolrDocument solrdoc: list) {
            String id = (String) solrdoc.getFieldValue(YaCySchema.id.name());
            if (id != null) {
                this.hitCache.put(id, EXIST);
                this.documentCache.put(id, solrdoc);
            }
        }
        return list;
    }

    @Override
    public long getSize() {
        long s = 0;
        if (this.solr0 != null) s += this.solr0.getSize();
        if (this.solr1 != null) s += this.solr1.getSize();
        return s;
    }

}
