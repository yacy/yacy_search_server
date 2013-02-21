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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.federate.solr.instance.SolrInstance;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

public class MultipleSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final static SolrInputDocument POISON_DOC = new SolrInputDocument();

    private final ArrayBlockingQueue<SolrInputDocument> queue;
    private final AddWorker[] worker;
    private final SolrConnector solr;

    public MultipleSolrConnector(final SolrInstance instance, final String corename, final int connections) {
        this.solr = new RemoteSolrConnector(instance, corename);
        this.queue = new ArrayBlockingQueue<SolrInputDocument>(1000);
        this.worker = new AddWorker[connections];
        for (int i = 0; i < connections; i++) {
            this.worker[i] = new AddWorker(instance, corename);
            this.worker[i].start();
        }
    }

    private class AddWorker extends Thread {
        private final SolrConnector solr;
        public AddWorker(final SolrInstance instance, final String corename) {
            this.solr = new RemoteSolrConnector(instance, corename);
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
    public void commit(boolean softCommit) {
        this.solr.commit(softCommit);
    }

    /**
     * force an explicit merge of segments
     * @param maxSegments the maximum number of segments. Set to 1 for maximum optimization
     */
    public void optimize(int maxSegments) {
        this.solr.optimize(maxSegments);
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
    public void delete(final String id) throws IOException {
        this.solr.delete(id);
    }

    @Override
    public void delete(final List<String> ids) throws IOException {
        this.solr.delete(ids);
    }

    @Override
    public void deleteByQuery(final String querystring) throws IOException {
        this.solr.deleteByQuery(querystring);
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
    public QueryResponse query(final ModifiableSolrParams query) throws IOException, SolrException {
        return this.solr.query(query);
    }

    @Override
    public long getSize() {
        return this.solr.getSize();
    }

}
