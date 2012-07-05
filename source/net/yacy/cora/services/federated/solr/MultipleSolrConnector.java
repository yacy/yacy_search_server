package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;

public class MultipleSolrConnector implements SolrConnector {

    private final static SolrDoc POISON_DOC = new SolrDoc();

    private final ArrayBlockingQueue<SolrDoc> queue;
    private final AddWorker[] worker;
    private final SolrConnector solr;
    private int commitWithinMs;

    public MultipleSolrConnector(final String url, int connections) throws IOException {
        this.solr = new SingleSolrConnector(url);
        this.queue = new ArrayBlockingQueue<SolrDoc>(1000);
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
            this.solr = new SingleSolrConnector(url);
            this.solr.setCommitWithinMs(MultipleSolrConnector.this.commitWithinMs);
        }
        @Override
        public void run() {
            SolrDoc doc;
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
    public boolean exists(String id) throws IOException {
        return this.solr.exists(id);
    }

    @Override
    public void add(final SolrDoc solrdoc) throws IOException, SolrException {
        try {
            this.queue.put(solrdoc);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void add(final Collection<SolrDoc> solrdocs) throws IOException, SolrException {
        for (SolrDoc d: solrdocs) {
            try {
                this.queue.put(d);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public SolrDocumentList get(String querystring, int offset, int count) throws IOException {
        return this.solr.get(querystring, offset, count);
    }

    @Override
    public long getSize() {
        return this.solr.getSize();
    }

}
