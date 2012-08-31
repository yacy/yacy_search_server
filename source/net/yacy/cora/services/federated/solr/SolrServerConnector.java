/**
 *  SolrServerConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 21.06.2012 at http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.yacy.kelondro.logging.Log;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;

public abstract class SolrServerConnector extends AbstractSolrConnector implements SolrConnector {

    protected SolrServer server;
    protected int commitWithinMs; // max time (in ms) before a commit will happen

    protected SolrServerConnector() {
        this.server = null;
        this.commitWithinMs = 180000;
    }

    protected void init(SolrServer server) {
        this.server = server;
    }

    public SolrServer getServer() {
        return this.server;
    }

    /**
     * get the solr autocommit delay
     * @return the maximum waiting time after a solr command until it is transported to the server
     */
    @Override
    public int getCommitWithinMs() {
        return this.commitWithinMs;
    }

    /**
     * set the solr autocommit delay
     * @param c the maximum waiting time after a solr command until it is transported to the server
     */
    @Override
    public void setCommitWithinMs(int c) {
        this.commitWithinMs = c;
    }

    @Override
    public synchronized void close() {
        try {
            synchronized (this.server) {
                this.server.commit();
            }
            this.server = null;
        } catch (SolrServerException e) {
            Log.logException(e);
        } catch (IOException e) {
            Log.logException(e);
        }
    }

    @Override
    public long getSize() {
        try {
            final QueryResponse rsp = query(AbstractSolrConnector.catchallQuery);
            if (rsp == null) return 0;
            final SolrDocumentList docs = rsp.getResults();
            if (docs == null) return 0;
            return docs.getNumFound();
        } catch (final Throwable e) {
            Log.logException(e);
            return 0;
        }
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteByQuery("*:*");
                this.server.commit();
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final String id) throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteById(id, this.commitWithinMs);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final List<String> ids) throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteById(ids, this.commitWithinMs);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    /**
     * delete entries from solr according the given solr query string
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        try {
            synchronized (this.server) {
                this.server.deleteByQuery(querystring, this.commitWithinMs);
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    public void add(final File file, final String solrId) throws IOException {
        final ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addFile(file);
        up.setParam("literal.id", solrId);
        up.setParam("uprefix", "attr_");
        up.setParam("fmap.content", "attr_content");
        up.setCommitWithin(this.commitWithinMs);
        //up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        try {
            synchronized (this.server) {
                this.server.request(up);
                this.server.commit();
            }
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException {
        try {
            synchronized (this.server) {
                this.server.add(solrdoc, this.commitWithinMs);
                //this.server.commit();
            }
        } catch (SolrServerException e) {
            Log.logWarning("SolrConnector", e.getMessage() + " DOC=" + solrdoc.toString());
            throw new IOException(e);
        }
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        ArrayList<SolrInputDocument> l = new ArrayList<SolrInputDocument>();
        for (SolrInputDocument d: solrdocs) l.add(d);
        try {
            synchronized (this.server) {
                this.server.add(l, this.commitWithinMs);
                //this.server.commit();
            }
        } catch (SolrServerException e) {
            Log.logWarning("SolrConnector", e.getMessage() + " DOC=" + solrdocs.toString());
            throw new IOException(e);
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
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(querystring);
        params.setRows(count);
        params.setStart(offset);
        //params.addSortField( "price", SolrQuery.ORDER.asc );

        // query the server
        QueryResponse rsp = query(params);
        final SolrDocumentList docs = rsp.getResults();
        return docs;
    }

    @Override
    public long getQueryCount(String querystring) throws IOException {
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(querystring);
        params.setRows(1);
        params.setStart(0);

        // query the server
        QueryResponse rsp = query(params);
        final SolrDocumentList docs = rsp.getResults();
        return docs.getNumFound();
    }

    abstract public QueryResponse query(SolrParams params) throws IOException;

    private final char[] queryIDTemplate = "id:\"            \"".toCharArray();

    /**
     * get a document from solr by given id
     * @param id
     * @return one result or null if no result exists
     * @throws IOException
     */
    @Override
    public SolrDocument get(final String id) throws IOException {
    	assert id.length() == 12;
        // construct query
    	char[] q = new char[17];
    	System.arraycopy(this.queryIDTemplate, 0, q, 0, 17);
    	System.arraycopy(id.toCharArray(), 0, q, 4, 12);
        final SolrQuery query = new SolrQuery();
        query.setQuery(new String(q));
        query.setRows(1);
        query.setStart(0);

        // query the server
        try {
            final QueryResponse rsp = query(query);
            final SolrDocumentList docs = rsp.getResults();
            if (docs.isEmpty()) return null;
            return docs.get(0);
        } catch (final Throwable e) {
            throw new IOException(e.getMessage(), e);
        }
    }

}
