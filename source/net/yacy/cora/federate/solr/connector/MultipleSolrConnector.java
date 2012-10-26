/**
 *  MultipleSolrConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 08.11.2011 at http://yacy.net
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
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.sorting.ReversibleScoreMap;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

public class MultipleSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final static SolrInputDocument POISON_DOC = new SolrInputDocument();

    private final ArrayBlockingQueue<SolrInputDocument> queue;
    private final AddWorker[] worker;
    private final SolrConnector solr;
    private int commitWithinMs;

    public MultipleSolrConnector(final String url, int connections) throws IOException {
        this.solr = new RemoteSolrConnector(url);
        this.queue = new ArrayBlockingQueue<SolrInputDocument>(1000);
        this.worker = new AddWorker[connections];
        this.commitWithinMs = 180000;
        for (int i = 0; i < connections; i++) {
            this.worker[i] = new AddWorker(url);
            this.worker[i].start();
        }
    }

    private class AddWorker extends Thread {
        private final SolrConnector solr;
        public AddWorker(final String url) throws IOException {
            this.solr = new RemoteSolrConnector(url);
            this.solr.setCommitWithinMs(MultipleSolrConnector.this.commitWithinMs);
        }
        @Override
        public void run() {
            SolrInputDocument doc;
            try {
                while ((doc = MultipleSolrConnector.this.queue.take()) != POISON_DOC) {
                    try {
                        this.solr.add(doc);
                    } catch (SolrException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
            } finally {
                this.solr.close();
            }
        }
    }

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
        this.solr.setCommitWithinMs(c);
        for (AddWorker w: this.worker) w.solr.setCommitWithinMs(c);
    }

    @Override
    public void commit() {
        this.solr.commit();
    }
    
    @Override
    public void close() {
        // send termination signal to worker
        for (@SuppressWarnings("unused") AddWorker element : this.worker) {
            try {
                this.queue.put(POISON_DOC);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // wait for termination
        for (AddWorker element : this.worker) {
            try {
                element.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.solr.close();
    }

    @Override
    public void clear() throws IOException {
        this.solr.clear();
    }

    @Override
    public void delete(String id) throws IOException {
        this.solr.delete(id);
    }

    @Override
    public void delete(List<String> ids) throws IOException {
        this.solr.delete(ids);
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        this.solr.deleteByQuery(querystring);
    }

	@Override
	public SolrDocument get(String id) throws IOException {
		return this.solr.get(id);
	}

    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException {
        try {
            this.queue.put(solrdoc);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument d: solrdocs) {
            try {
                this.queue.put(d);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public SolrDocumentList query(String querystring, int offset, int count) throws IOException {
        return this.solr.query(querystring, offset, count);
    }

    @Override
    public QueryResponse query(ModifiableSolrParams query) throws IOException, SolrException {
        return this.solr.query(query);
    }

    @Override
    public long getQueryCount(final String querystring) throws IOException {
        return this.solr.getQueryCount(querystring);
    }

    @Override
    public ReversibleScoreMap<String> getFacet(final String field, final int maxresults) throws IOException {
        return this.solr.getFacet(field, maxresults);
    }

    @Override
    public long getSize() {
        return this.solr.getSize();
    }

}
