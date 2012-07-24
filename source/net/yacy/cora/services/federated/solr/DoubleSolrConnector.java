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

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;


public class DoubleSolrConnector implements SolrConnector {

    private SolrConnector solr0;
    private SolrConnector solr1;

    public DoubleSolrConnector() {
        this.solr0 = null;
        this.solr1 = null;
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
        if (this.solr0 != null) {
            if (this.solr0.exists(id)) return true;
        }
        if (this.solr1 != null) {
            if (this.solr1.exists(id)) return true;
        }
        return false;
    }

    @Override
    public SolrDocument get(String id) throws IOException {
        if (this.solr0 != null) {
            SolrDocument doc = this.solr0.get(id);
            if (doc != null) return doc;
        }
        if (this.solr1 != null) {
            SolrDocument doc = this.solr1.get(id);
            if (doc != null) return doc;
        }
        return null;
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    @Override
    public void add(final SolrDoc solrdoc) throws IOException {
        if (this.solr0 != null) {
            this.solr0.add(solrdoc);
        }
        if (this.solr1 != null) {
            this.solr1.add(solrdoc);
        }
    }

    @Override
    public void add(final Collection<SolrDoc> solrdocs) throws IOException, SolrException {
        if (this.solr0 != null) {
            for (SolrDoc d: solrdocs) this.solr0.add(d);
        }
        if (this.solr1 != null) {
            for (SolrDoc d: solrdocs) this.solr1.add(d);
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
        if (this.solr0 == null && this.solr1 == null) return new SolrDocumentList();
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
        final SolrDocumentList list = new SolrDocumentList();
        for (final SolrDocument d: l) list.add(d);
        l = this.solr1.query(querystring, offset + l.size() - size0, count - l.size());
        for (final SolrDocument d: l) list.add(d);
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
