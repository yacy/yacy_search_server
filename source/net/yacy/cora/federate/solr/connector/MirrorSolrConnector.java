/**
 *  MirrorSolrConnector
 *  Copyright 2013 by Michael Peter Christen
 *  First released 18.02.2012 at http://yacy.net
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.kelondro.data.word.Word;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;

public class MirrorSolrConnector extends AbstractSolrConnector implements SolrConnector {

    // the twin solrs
    private SolrConnector solr0, solr1;

    public MirrorSolrConnector() {
        this.solr0 = null;
        this.solr1 = null;
    }
    
    public MirrorSolrConnector(SolrConnector solr0, SolrConnector solr1) {
        this.solr0 = solr0;
        this.solr1 = solr1;
    }

    @Override
    public int hashCode() {
        return (this.solr0 == null ? 0 : this.solr0.hashCode()) + (this.solr1 == null ? 0 : this.solr1.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MirrorSolrConnector &&
                ((this.solr0 == null && ((MirrorSolrConnector) o).solr0 == null) || (((this.solr0 != null && ((MirrorSolrConnector) o).solr0 != null)) && this.solr0.equals(((MirrorSolrConnector) o).solr0))) &&
                ((this.solr1 == null && ((MirrorSolrConnector) o).solr1 == null) || (((this.solr1 != null && ((MirrorSolrConnector) o).solr1 != null)) && this.solr1.equals(((MirrorSolrConnector) o).solr1)));
    }
    
    
    @Override
    public int bufferSize() {
        int b = 0;
        if (this.solr0 != null) b += this.solr0.bufferSize();
        if (this.solr1 != null) b += this.solr1.bufferSize();
        return b;
    }

    @Override
    public void clearCaches() {
        if (this.solr0 != null) this.solr0.clearCaches();
        if (this.solr1 != null) this.solr1.clearCaches();
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

    @Override
    public void commit(final boolean softCommit) {
        if (this.solr0 != null) this.solr0.commit(softCommit);
        if (this.solr1 != null) this.solr1.commit(softCommit);
    }

    @Override
    public void optimize(int maxSegments) {
        if (this.solr0 != null) this.solr0.optimize(maxSegments);
        if (this.solr1 != null) this.solr1.optimize(maxSegments);
    }

    @Override
    public int getSegmentCount() {
        int s0 = this.solr0 == null ? 0 : this.solr0.getSegmentCount();
        int s1 = this.solr1 == null ? 0 : this.solr1.getSegmentCount();
        return Math.max(s0, s1);
    }

    @Override
    public boolean isClosed() {
        return (this.solr0 == null || this.solr0.isClosed()) && (this.solr1 == null || this.solr1.isClosed());
    }
    
    @Override
    public synchronized void close() {
        if (this.solr0 != null) {this.solr0.close(); this.solr0 = null;}
        if (this.solr1 != null) {this.solr1.close(); this.solr1 = null;}
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        if (this.solr0 != null) this.solr0.clear();
        if (this.solr1 != null) this.solr1.clear();
    }

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void deleteById(final String id) throws IOException {
        if (this.solr0 != null) this.solr0.deleteById(id);
        if (this.solr1 != null) this.solr1.deleteById(id);
    }

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    @Override
    public void deleteByIds(final Collection<String> ids) throws IOException {
        if (this.solr0 != null) this.solr0.deleteByIds(ids);
        if (this.solr1 != null) this.solr1.deleteByIds(ids);
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        if (this.solr0 != null) this.solr0.deleteByQuery(querystring);
        if (this.solr1 != null) this.solr1.deleteByQuery(querystring);
    }
    
    @Override
    public SolrDocument getDocumentById(final String key, final String ... fields) throws IOException {
        assert key.length() == Word.commonHashLength : "wrong id: " + key;
        SolrDocument doc;
        if ((solr0 != null && ((doc = solr0.getDocumentById(key, fields)) != null)) || (solr1 != null && ((doc = solr1.getDocumentById(key, fields)) != null))) {
            return doc;
        }
        return null;
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException {
        if (this.solr0 != null) this.solr0.add(solrdoc);
        if (this.solr1 != null) this.solr1.add(solrdoc);
    }
    
    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        if (this.solr0 != null) this.solr0.add(solrdocs);
        if (this.solr1 != null) this.solr1.add(solrdocs);
    }

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList getDocumentListByQuery(final String querystring, final String sort, final int offset, final int count, final String ... fields) throws IOException {
        if (this.solr0 == null && this.solr1 == null) return new SolrDocumentList();
        if (offset == 0 && count == 1 && querystring.startsWith("id:") &&
            ((querystring.length() == 17 && querystring.charAt(3) == '"' && querystring.charAt(16) == '"') ||
             querystring.length() == 15)) {
            final SolrDocumentList list = new SolrDocumentList();
            SolrDocument doc = getDocumentById(querystring.charAt(3) == '"' ? querystring.substring(4, querystring.length() - 1) : querystring.substring(3), fields);
            list.add(doc);
            // no addToCache(list) here because that was already handlet in get();
            return list;
        }
        if (this.solr0 != null && this.solr1 == null) {
            SolrDocumentList list = this.solr0.getDocumentListByQuery(querystring, sort, offset, count, fields);
            return list;
        }
        if (this.solr1 != null && this.solr0 == null) {
            SolrDocumentList list = this.solr1.getDocumentListByQuery(querystring, sort, offset, count, fields);
            return list;
        }

        // combine both lists
        SolrDocumentList l;
        l = this.solr0.getDocumentListByQuery(querystring, sort, offset, count, fields);
        if (l.size() >= count) return l;

        // at this point we need to know how many results are in solr0
        // compute this with a very bad hack; replace with better method later
        int size0 = 0;
        { //bad hack - TODO: replace
            SolrDocumentList lHack = this.solr0.getDocumentListByQuery(querystring, sort, 0, Integer.MAX_VALUE, fields);
            size0 = lHack.size();
        }

        // now use the size of the first query to do a second query
        final SolrDocumentList list = new SolrDocumentList();
        for (final SolrDocument d: l) list.add(d);
        l = this.solr1.getDocumentListByQuery(querystring, sort, offset + l.size() - size0, count - l.size(), fields);
        for (final SolrDocument d: l) list.add(d);

        return list;
    }

    @Override
    public QueryResponse getResponseByParams(ModifiableSolrParams query) throws IOException, SolrException {
        Integer count0 = query.getInt(CommonParams.ROWS);
        int count = count0 == null ? 10 : count0.intValue();
        Integer start0 = query.getInt(CommonParams.START);
        int start = start0 == null ? 0 : start0.intValue();
        if (this.solr0 == null && this.solr1 == null) return new QueryResponse();

        if (this.solr0 != null && this.solr1 == null) {
            QueryResponse list = this.solr0.getResponseByParams(query);
            return list;
        }
        if (this.solr1 != null && this.solr0 == null) {
            QueryResponse list = this.solr1.getResponseByParams(query);
            return list;
        }

        // combine both lists
        QueryResponse rsp = this.solr0.getResponseByParams(query);
        final SolrDocumentList l = rsp.getResults();
        if (l.size() >= count) return rsp;

        // at this point we need to know how many results are in solr0
        // compute this with a very bad hack; replace with better method later
        int size0 = 0;
        { //bad hack - TODO: replace
            query.set(CommonParams.START, 0);
            query.set(CommonParams.ROWS, Integer.MAX_VALUE);
            QueryResponse lHack = this.solr0.getResponseByParams(query);
            query.set(CommonParams.START, start);
            query.set(CommonParams.ROWS, count);
            size0 = lHack.getResults().size();
        }

        // now use the size of the first query to do a second query
        query.set(CommonParams.START, start + l.size() - size0);
        query.set(CommonParams.ROWS, count - l.size());
        QueryResponse rsp1 = this.solr1.getResponseByParams(query);
        query.set(CommonParams.START, start);
        query.set(CommonParams.ROWS, count);
        // TODO: combine both
        return rsp1;
    }

    @Override
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams query) throws IOException, SolrException {
        Integer count0 = query.getInt(CommonParams.ROWS);
        int count = count0 == null ? 10 : count0.intValue();
        Integer start0 = query.getInt(CommonParams.START);
        int start = start0 == null ? 0 : start0.intValue();
        if (this.solr0 == null && this.solr1 == null) return new SolrDocumentList();

        if (this.solr0 != null && this.solr1 == null) {
            SolrDocumentList list = this.solr0.getDocumentListByParams(query);
            return list;
        }
        if (this.solr1 != null && this.solr0 == null) {
            SolrDocumentList list = this.solr1.getDocumentListByParams(query);
            return list;
        }

        // combine both lists
        final SolrDocumentList l = this.solr0.getDocumentListByParams(query);
        if (l.size() >= count) return l;

        // at this point we need to know how many results are in solr0
        // compute this with a very bad hack; replace with better method later
        int size0 = 0;
        { //bad hack - TODO: replace
            query.set(CommonParams.START, 0);
            query.set(CommonParams.ROWS, Integer.MAX_VALUE);
            final SolrDocumentList lHack = this.solr0.getDocumentListByParams(query);
            query.set(CommonParams.START, start);
            query.set(CommonParams.ROWS, count);
            size0 = lHack.size();
        }

        // now use the size of the first query to do a second query
        query.set(CommonParams.START, start + l.size() - size0);
        query.set(CommonParams.ROWS, count - l.size());
        final SolrDocumentList l1 = this.solr1.getDocumentListByParams(query);
        query.set(CommonParams.START, start);
        query.set(CommonParams.ROWS, count);
        // TODO: combine both
        return l1;
    }
    
    @Override
    public long getCountByQuery(final String querystring) throws IOException {
        if (this.solr0 == null && this.solr1 == null) return 0;
        if (this.solr0 != null && this.solr1 == null) {
            return this.solr0.getCountByQuery(querystring);
        }
        if (this.solr1 != null && this.solr0 == null) {
            return this.solr1.getCountByQuery(querystring);
        }
        final AtomicLong count = new AtomicLong(0);
        Thread t0 = new Thread() {
            @Override
            public void run() {
                this.setName("MirrorSolrConnector.getCountByQuery/t0");
                try {
                    count.addAndGet(MirrorSolrConnector.this.solr0.getCountByQuery(querystring));
                } catch (final IOException e) {}
            }
        };
        t0.start();
        Thread t1 = new Thread() {
            @Override
            public void run() {
                this.setName("MirrorSolrConnector.getCountByQuery/t1");
                try {
                    count.addAndGet(MirrorSolrConnector.this.solr1.getCountByQuery(querystring));
                } catch (final IOException e) {}
            }
        };
        t1.start();
        try {t0.join();} catch (final InterruptedException e) {}
        try {t1.join();} catch (final InterruptedException e) {}
        return count.get();
    }

    @Override
    public Map<String, ReversibleScoreMap<String>> getFacets(final String query, final int maxresults, final String ... fields) throws IOException {
        if (this.solr0 == null && this.solr1 == null) return new HashMap<String, ReversibleScoreMap<String>>(0);
        if (this.solr0 != null && this.solr1 == null) {
            return this.solr0.getFacets(query, maxresults, fields);
        }
        if (this.solr1 != null && this.solr0 == null) {
            return this.solr1.getFacets(query, maxresults, fields);
        }
        Map<String, ReversibleScoreMap<String>> facets0 = this.solr0.getFacets(query, maxresults, fields);
        Map<String, ReversibleScoreMap<String>> facets1 = this.solr1.getFacets(query, maxresults, fields);
        for (Map.Entry<String, ReversibleScoreMap<String>> facet0: facets0.entrySet()) {
            ReversibleScoreMap<String> facet1 = facets1.remove(facet0.getKey());
            if (facet1 == null) continue;
            for (String key: facet1) facet0.getValue().inc(key, facet1.get(key));
        }
        for (Map.Entry<String, ReversibleScoreMap<String>> facet1: facets1.entrySet()) {
            facets0.put(facet1.getKey(), facet1.getValue());
        }
        return facets0;
    }

    @Override
    public long getSize() {
        long s = 0;
        if (this.solr0 != null) s += this.solr0.getSize();
        if (this.solr1 != null) s += this.solr1.getSize();
        return s;
    }

    @Override
    public Metadata getMetadata(String id) throws IOException {
        if (this.solr0 != null && this.solr1 == null) return this.solr0.getMetadata(id);
        if (this.solr0 == null && this.solr1 != null) return this.solr1.getMetadata(id);
        if (this.solr0 == null && this.solr1 == null) return null;
        Metadata md0 = this.solr0.getMetadata(id);
        Metadata md1 = this.solr1.getMetadata(id);
        if (md0 == null) return md1;
        if (md1 == null) return md0;
        long date = Math.max(md0.date, md1.date);
        assert md0.url.equals(md1.url);
        return new Metadata(md0.url, date);
    }

    @Override
    public BlockingQueue<String> concurrentIDsByQuery(final String querystring, final String sort, final int offset, final int maxcount, final long maxtime, final int buffersize, final int concurrency) {
        if (this.solr0 != null && this.solr1 == null) return this.solr0.concurrentIDsByQuery(querystring, sort, offset, maxcount, maxtime, buffersize, concurrency);
        if (this.solr0 == null && this.solr1 != null) return this.solr1.concurrentIDsByQuery(querystring, sort, offset, maxcount, maxtime, buffersize, concurrency);
        return super.concurrentIDsByQuery(querystring, sort, offset, maxcount, maxtime, buffersize, concurrency);
    }
    
}
