/**
 *  SolrSingleConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.document.Document;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;


public class SolrSingleConnector {

    private String solrurl;
    private SolrServer server;
    private SolrScheme scheme;
    
    private final static int transmissionQueueCount = 4; // allow concurrent http sessions to solr
    private final static int transmissionQueueSize = 50; // number of documents that are collected until a commit is sent
    private Worker[] transmissionWorker; // the transmission workers to solr
    private BlockingQueue<SolrInputDocument>[] transmissionQueue; // the queues quere documents are collected
    private int transmissionRoundRobinCounter; // a rount robin counter for the transmission queues
    
    @SuppressWarnings("unchecked")
    public SolrSingleConnector(String url, SolrScheme scheme) throws IOException {
        this.solrurl = url;
        this.scheme = scheme;
        transmissionRoundRobinCounter = 0;
        this.transmissionQueue = new ArrayBlockingQueue[transmissionQueueCount];
        for (int i = 0; i < transmissionQueueCount; i++) {
            this.transmissionQueue[i] = new ArrayBlockingQueue<SolrInputDocument>(transmissionQueueSize);
        }
        try {
            this.server = new SolrHTTPClient(this.solrurl);
        } catch (MalformedURLException e) {
            throw new IOException("bad connector url: " + this.solrurl);
        }
        this.transmissionWorker = new Worker[transmissionQueueCount];
        for (int i = 0; i < transmissionQueueCount; i++) {
            this.transmissionWorker[i] = new Worker(i);
            this.transmissionWorker[i].start();
        }
    }

    private class Worker extends Thread {
        boolean shallRun;
        int idx;
        public Worker(int i) {
            this.idx = i;
            this.shallRun = true;
        }
        public void pleaseStop() {
            this.shallRun = false;
        }
        public void run() {
            while (this.shallRun) {
                if (transmissionQueue[idx].size() > 0) {
                    try {
                        flushTransmissionQueue(idx);
                    } catch (IOException e) {
                        Log.logSevere("SolrSingleConnector", "flush Transmission failed in worker", e);
                        continue;
                    }
                } else {
                    try {Thread.sleep(1000);} catch (InterruptedException e) {}
                }
            }
            try {
                flushTransmissionQueue(idx);
            } catch (IOException e) {}
        }
    }
    
    public void close() {
        for (int i = 0; i < transmissionQueueCount; i++) {
            if (this.transmissionWorker[i].isAlive()) {
                this.transmissionWorker[i].pleaseStop();
                try {this.transmissionWorker[i].join();} catch (InterruptedException e) {}
            }
        }
        for (int i = 0; i < transmissionQueueCount; i++) {
            try {
                flushTransmissionQueue(i);
            } catch (IOException e) {}
        }
    }
    
    /**
     * delete everything in the solr index
     * @throws IOException
     */
    public void clear() throws IOException {
        try {
            server.deleteByQuery("*:*");
            server.commit();
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    public void delete(String id) throws IOException {
        try {
            server.deleteById(id);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    public void delete(List<String> ids) throws IOException {
        try {
            server.deleteById(ids);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    public void add(File file, String solrId) throws IOException {
        ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addFile(file);
        up.setParam("literal.id", solrId);
        up.setParam("uprefix", "attr_");
        up.setParam("fmap.content", "attr_content");
        //up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        try {
            server.request(up);
            server.commit();
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    /*
    public void addx(File file, String solrId) throws IOException {
        ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        ModifiableSolrParams params = new ModifiableSolrParams();
        List<ContentStream> contentStreams = new ArrayList<ContentStream>();
        contentStreams.add(new ContentStreamBase.FileStream(file));
        params.set("literal.id", solrId);
        params.set("uprefix", "attr_");
        params.set("fmap.content", "attr_content");
        params.set( UpdateParams.COMMIT, "true" );
        params.set( UpdateParams.WAIT_FLUSH, String.valueOf(true));
        params.set( UpdateParams.WAIT_SEARCHER, String.valueOf(true));
          
        try {
            server.
            server.request(up);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    */
    
    public void add(String id, ResponseHeader header, Document doc) throws IOException {
        add(this.scheme.yacy2solr(id, header, doc));
    }

    private void add(SolrInputDocument solrdoc) throws IOException {
        int thisrrc = this.transmissionRoundRobinCounter;
        int nextrrc = thisrrc++;
        if (nextrrc >= transmissionQueueCount) nextrrc = 0;
        this.transmissionRoundRobinCounter = nextrrc;
        if (this.transmissionWorker[thisrrc].isAlive()) {
            this.transmissionQueue[thisrrc].offer(solrdoc);
        } else {
            if (this.transmissionQueue[thisrrc].size() > 0) flushTransmissionQueue(thisrrc);
            Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
            docs.add(solrdoc);
            addSolr(docs);
        }
    }
    
    protected void addSolr(Collection<SolrInputDocument> docs) throws IOException {
        try {
            server.add(docs);
            server.commit();
            /* To immediately commit after adding documents, you could use: 
                  UpdateRequest req = new UpdateRequest();
                  req.setAction( UpdateRequest.ACTION.COMMIT, false, false );
                  req.add( docs );
                  UpdateResponse rsp = req.process( server );
             */
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    public void err(DigestURI digestURI, String failReason, int httpstatus) throws IOException {
       
            SolrInputDocument solrdoc = new SolrInputDocument();
            solrdoc.addField("id", UTF8.String(digestURI.hash()));
            solrdoc.addField("sku", digestURI.toNormalform(true, false), 3.0f);
            InetAddress address = Domains.dnsResolve(digestURI.getHost());
            if (address != null) solrdoc.addField("ip_s", address.getHostAddress());
            if (digestURI.getHost() != null) solrdoc.addField("host_s", digestURI.getHost());

            // path elements of link
            String path = digestURI.getPath();
            if (path != null) {
                String[] paths = path.split("/");
                if (paths.length > 0) solrdoc.addField("attr_paths", paths);
            }

            solrdoc.addField("failreason_t", failReason);
            solrdoc.addField("httpstatus_i", httpstatus);
            
            add(solrdoc);
    }
    
    private void flushTransmissionQueue(int idx) throws IOException {
        Collection<SolrInputDocument> c = new ArrayList<SolrInputDocument>();
        while (this.transmissionQueue[idx].size() > 0) {
            try {
                c.add(this.transmissionQueue[idx].take());
            } catch (InterruptedException e) {
                continue;
            }
        }
        addSolr(c);
    }
    
    
    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    public SolrDocumentList get(String querystring, int offset, int count) throws IOException {
        // construct query
        SolrQuery query = new SolrQuery();
        query.setQuery(querystring);
        query.setRows(count);
        query.setStart(offset);
        query.addSortField( "price", SolrQuery.ORDER.asc );
        
        // query the server
        //SearchResult result = new SearchResult(count);
        try {
            QueryResponse rsp = server.query( query );
            SolrDocumentList docs = rsp.getResults();
            return docs;
            // add the docs into the YaCy search result container
            /*
            for (SolrDocument doc: docs) {
                result.put(element)
            }
            */
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
        
        //return result;
    }
    
    public static void main(String args[]) {
        SolrSingleConnector solr;
        try {
            solr = new SolrSingleConnector("http://127.0.0.1:8983/solr", SolrScheme.SolrCell);
            solr.clear();
            File exampleDir = new File("/Data/workspace2/yacy/test/parsertest/");
            long t, t0, a = 0;
            int c = 0;
            for (String s: exampleDir.list()) {
                if (s.startsWith(".")) continue;
                t = System.currentTimeMillis();
                solr.add(new File(exampleDir, s), s);
                t0 = (System.currentTimeMillis() - t);
                a += t0;
                c++;
                System.out.println("pushed file " + s + " to solr, " + t0 + " milliseconds");
            }
            System.out.println("pushed " + c + " files in " + a + " milliseconds, " + (a / c) + " milliseconds average; " + (60000 / a * c) + " PPM");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
