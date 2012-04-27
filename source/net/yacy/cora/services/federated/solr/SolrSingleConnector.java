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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.document.Document;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import org.apache.http.HttpHost;
import org.apache.http.client.AuthCache;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;


public class SolrSingleConnector implements SolrConnector {

    private final String solrurl, host, solrpath, solraccount, solrpw;
    private final int port;
    private HttpSolrServer server;
    private final SolrScheme scheme;

    private final static int transmissionQueueCount = 4; // allow concurrent http sessions to solr
    private final static int transmissionQueueSize = 50; // number of documents that are collected until a commit is sent
    private final Worker[] transmissionWorker; // the transmission workers to solr
    private final BlockingQueue<SolrInputDocument>[] transmissionQueue; // the queues quere documents are collected
    private int transmissionRoundRobinCounter; // a rount robin counter for the transmission queues

    /**
     * create a new solr connector
     * @param url the solr url, like http://192.168.1.60:8983/solr/ or http://admin:pw@192.168.1.60:8983/solr/
     * @param scheme
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public SolrSingleConnector(final String url, final SolrScheme scheme) throws IOException {
        this.solrurl = url;
        this.scheme = scheme;
        this.transmissionRoundRobinCounter = 0;
        this.transmissionQueue = new ArrayBlockingQueue[transmissionQueueCount];
        for (int i = 0; i < transmissionQueueCount; i++) {
            this.transmissionQueue[i] = new ArrayBlockingQueue<SolrInputDocument>(transmissionQueueSize);
        }

        // connect using authentication
        final MultiProtocolURI u = new MultiProtocolURI(this.solrurl);
        this.host = u.getHost();
        this.port = u.getPort();
        this.solrpath = u.getPath();
        final String userinfo = u.getUserInfo();
        if (userinfo == null || userinfo.length() == 0) {
            this.solraccount = ""; this.solrpw = "";
        } else {
            final int p = userinfo.indexOf(':');
            if (p < 0) {
                this.solraccount = userinfo; this.solrpw = "";
            } else {
                this.solraccount = userinfo.substring(0, p); this.solrpw = userinfo.substring(p + 1);
            }
        }
        if (this.solraccount.length() > 0) {
            final HttpClient client = new DefaultHttpClient() {
                @Override
                protected HttpContext createHttpContext() {
                    HttpContext context = super.createHttpContext();
                    AuthCache authCache = new BasicAuthCache();
                    BasicScheme basicAuth = new BasicScheme();
                    HttpHost targetHost = new HttpHost(u.getHost(), u.getPort(), u.getProtocol());
                    authCache.put(targetHost, basicAuth);
                    context.setAttribute(ClientContext.AUTH_CACHE, authCache);
                    return context;
                }
            };
            this.server = new HttpSolrServer("http://" + this.host + ":" + this.port + this.solrpath, client);
        } else {
            this.server = new HttpSolrServer(this.solrurl);
        }
        this.server.setDefaultMaxConnectionsPerHost( 128 );
        this.server.setMaxTotalConnections( 256 );

        // start worker
        this.transmissionWorker = new Worker[transmissionQueueCount];
        for (int i = 0; i < transmissionQueueCount; i++) {
            this.transmissionWorker[i] = new Worker(i);
            this.transmissionWorker[i].start();
        }
    }

    private class Worker extends Thread {
        boolean shallRun;
        int idx;
        public Worker(final int i) {
            this.idx = i;
            this.shallRun = true;
        }
        public void pleaseStop() {
            this.shallRun = false;
        }
        @Override
        public void run() {
            while (this.shallRun) {
                if (SolrSingleConnector.this.transmissionQueue[this.idx].size() > 0) {
                    try {
                        flushTransmissionQueue(this.idx);
                    } catch (final IOException e) {
                        Log.logSevere("SolrSingleConnector", "flush Transmission failed in worker:IO", e);
                        continue;
                    } catch (final SolrException e) {
                        Log.logSevere("SolrSingleConnector", "flush Transmission failed in worker:Solr", e);
                        continue;
                    }
                } else {
                    try {Thread.sleep(1000);} catch (final InterruptedException e) {}
                }
            }
            try {
                flushTransmissionQueue(this.idx);
            } catch (final IOException e) {}
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < transmissionQueueCount; i++) {
            if (this.transmissionWorker[i].isAlive()) {
                this.transmissionWorker[i].pleaseStop();
                try {this.transmissionWorker[i].join();} catch (final InterruptedException e) {}
            }
        }
        for (int i = 0; i < transmissionQueueCount; i++) {
            try {
                flushTransmissionQueue(i);
            } catch (final IOException e) {
                Log.logException(e);
            } catch (final SolrException e) {
                Log.logException(e);
            }

        }
    }

    @Override
    public SolrScheme getScheme() {
        return this.scheme;
    }

    @Override
    public long getSize() {
        try {
            final SolrDocumentList list = get("*:*", 0, 1);
            return list.getNumFound();
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
            this.server.deleteByQuery("*:*");
            this.server.commit();
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final String id) throws IOException {
        try {
            this.server.deleteById(id);
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final List<String> ids) throws IOException {
        try {
            this.server.deleteById(ids);
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean exists(final String id) throws IOException {
        try {
            final SolrDocumentList list = get("id:" + id, 0, 1);
            return list.getNumFound() > 0;
        } catch (final Throwable e) {
            Log.logException(e);
            return false;
        }
    }

    public void add(final File file, final String solrId) throws IOException {
        final ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addFile(file);
        up.setParam("literal.id", solrId);
        up.setParam("uprefix", "attr_");
        up.setParam("fmap.content", "attr_content");
        //up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        try {
            this.server.request(up);
            this.server.commit();
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void add(final String id, final ResponseHeader header, final Document doc) throws IOException, SolrException {
        add(this.scheme.yacy2solr(id, header, doc));
    }

    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException {
        int thisrrc = this.transmissionRoundRobinCounter;
        int nextrrc = thisrrc++;
        if (nextrrc >= transmissionQueueCount) nextrrc = 0;
        this.transmissionRoundRobinCounter = nextrrc;
        if (this.transmissionWorker[thisrrc].isAlive()) {
            this.transmissionQueue[thisrrc].offer(solrdoc);
        } else {
            if (this.transmissionQueue[thisrrc].size() > 0) flushTransmissionQueue(thisrrc);
            final Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
            docs.add(solrdoc);
            addSolr(docs);
        }
    }

    protected void addSolr(final Collection<SolrInputDocument> docs) throws IOException, SolrException {

        try {
            if (docs.size() != 0) this.server.add(docs);
            this.server.commit();
            /* To immediately commit after adding documents, you could use:
                  UpdateRequest req = new UpdateRequest();
                  req.setAction( UpdateRequest.ACTION.COMMIT, false, false );
                  req.add( docs );
                  UpdateResponse rsp = req.process( server );
             */
        } catch (final SolrException e) {
            // the field is probably not known
            Log.logWarning("SolrConnector", e.getMessage());
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void err(final DigestURI digestURI, final String failReason, final int httpstatus) throws IOException {

            final SolrInputDocument solrdoc = new SolrInputDocument();
            solrdoc.addField("id", ASCII.String(digestURI.hash()));
            solrdoc.addField("sku", digestURI.toNormalform(true, false), 3.0f);
            final InetAddress address = digestURI.getInetAddress();
            if (address != null) solrdoc.addField("ip_s", address.getHostAddress());
            if (digestURI.getHost() != null) solrdoc.addField("host_s", digestURI.getHost());

            // path elements of link
            final String path = digestURI.getPath();
            if (path != null) {
                final String[] paths = path.split("/");
                if (paths.length > 0) solrdoc.addField("attr_paths", paths);
            }

            solrdoc.addField("failreason_t", failReason);
            solrdoc.addField("httpstatus_i", httpstatus);

            add(solrdoc);
    }

    private void flushTransmissionQueue(final int idx) throws IOException, SolrException {
        final Collection<SolrInputDocument> c = new ArrayList<SolrInputDocument>();
        while (this.transmissionQueue[idx].size() > 0) {
            try {
                c.add(this.transmissionQueue[idx].take());
            } catch (final InterruptedException e) {
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
    @Override
    public SolrDocumentList get(final String querystring, final int offset, final int count) throws IOException {
        // construct query
        final SolrQuery query = new SolrQuery();
        query.setQuery(querystring);
        query.setRows(count);
        query.setStart(offset);
        //query.addSortField( "price", SolrQuery.ORDER.asc );

        // query the server
        //SearchResult result = new SearchResult(count);
        try {
            final QueryResponse rsp = this.server.query( query );
            final SolrDocumentList docs = rsp.getResults();
            return docs;
            // add the docs into the YaCy search result container
            /*
            for (SolrDocument doc: docs) {
                result.put(element)
            }
            */
        } catch (final Throwable e) {
            throw new IOException(e);
        }

        //return result;
    }


    public String getAdminInterface() {
        final InetAddress localhostExternAddress = Domains.myPublicLocalIP();
        final String localhostExtern = localhostExternAddress == null ? "127.0.0.1" : localhostExternAddress.getHostAddress();
        String u = this.solrurl;
        int p = u.indexOf("localhost",0); if (p < 0) p = u.indexOf("127.0.0.1",0);
        if (p >= 0) u = u.substring(0, p) + localhostExtern + u.substring(p + 9);
        return u + (u.endsWith("/") ? "admin/" : "/admin/");
    }

    public static void main(final String args[]) {
        SolrSingleConnector solr;
        try {
            solr = new SolrSingleConnector("http://127.0.0.1:8983/solr", new SolrScheme());
            solr.clear();
            final File exampleDir = new File("/Data/workspace2/yacy/test/parsertest/");
            long t, t0, a = 0;
            int c = 0;
            for (final String s: exampleDir.list()) {
                if (s.startsWith(".")) continue;
                t = System.currentTimeMillis();
                solr.add(new File(exampleDir, s), s);
                t0 = (System.currentTimeMillis() - t);
                a += t0;
                c++;
                System.out.println("pushed file " + s + " to solr, " + t0 + " milliseconds");
            }
            System.out.println("pushed " + c + " files in " + a + " milliseconds, " + (a / c) + " milliseconds average; " + (60000 / a * c) + " PPM");
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
