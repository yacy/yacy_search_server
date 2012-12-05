/**
 *  EmbeddedSolrConnector
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


package net.yacy.cora.federate.solr.connector;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import net.yacy.cora.federate.solr.SolrServlet;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.SolrQueryResponse;
import org.xml.sax.SAXException;

import com.google.common.io.Files;

public class EmbeddedSolrConnector extends SolrServerConnector implements SolrConnector {

    public static final String SELECT = "/select";
    public static final String CONTEXT = "/solr";
    private final static String[] confFiles = {"solrconfig.xml", "schema.xml", "stopwords.txt", "synonyms.txt", "protwords.txt", "currency.xml", "elevate.xml", "xslt/example.xsl", "xslt/json.xsl", "lang/"};

    private CoreContainer cores;
    private final String defaultCoreName;
    private SolrCore defaultCore;
    
    private final SearchHandler requestHandler;
    private final File storagePath;

    public EmbeddedSolrConnector(File storagePath, File solr_config) throws IOException {
        super();
        // copy the solrconfig.xml to the storage path
        this.storagePath = storagePath;
        File conf = new File(storagePath, "conf");
        conf.mkdirs();
        File source, target;
        for (String cf: confFiles) {
            source = new File(solr_config, cf);
            if (source.isDirectory()) {
                target = new File(conf, cf);
                target.mkdirs();
                for (String cfl: source.list()) {
                    try {
                        Files.copy(new File(source, cfl), new File(target, cfl));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                target = new File(conf, cf);
                target.getParentFile().mkdirs();
                try {
                    Files.copy(source, target);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            this.cores = new CoreContainer(storagePath.getAbsolutePath(), new File(solr_config, "solr.xml"));
            if (this.cores == null) {
                // try again
                System.gc();
                this.cores = new CoreContainer(storagePath.getAbsolutePath(), new File(solr_config, "solr.xml"));
            }
        } catch (ParserConfigurationException e) {
            throw new IOException(e.getMessage(), e);
        } catch (SAXException e) {
            throw new IOException(e.getMessage(), e);
        }
        this.defaultCoreName = this.cores.getDefaultCoreName();
        this.defaultCore = this.cores.getCore(this.defaultCoreName); // should be "collection1"
        if (this.defaultCore == null) {
            // try again
            System.gc();
            this.defaultCore = this.cores.getCore(this.defaultCoreName); // should be "collection1"
        }
        if (this.defaultCore == null) {
            throw new IOException("cannot get the default core; available = " + MemoryControl.available() + ", free = " + MemoryControl.free());
        }
        final NamedList<Object> config = new NamedList<Object>();
        this.requestHandler = new SearchHandler();
        this.requestHandler.init(config);
        this.requestHandler.inform(this.defaultCore);
        super.init(new EmbeddedSolrServer(this.cores, this.defaultCoreName));
    }

    public File getStoragePath() {
        return this.storagePath;
    }
    
    public SolrCore getCore() {
        return this.defaultCore;
    }

    public SolrConfig getConfig() {
        return this.defaultCore.getSolrConfig();
    }

    @Override
    public long getSize() {
    	// do some magic here to prevent the super.getSize() call which is a bad hack
        return super.getSize();
    }

    @Override
    public synchronized void close() {
        try {this.commit();} catch (Throwable e) {Log.logException(e);}
        try {super.close();} catch (Throwable e) {Log.logException(e);}
        try {this.defaultCore.close();} catch (Throwable e) {Log.logException(e);}
        try {this.cores.shutdown();} catch (Throwable e) {Log.logException(e);}
    }

    public SolrQueryRequest request(final SolrParams params) {
        SolrQueryRequest req = null;
        req = new SolrQueryRequestBase(this.defaultCore, params){};
        req.getContext().put("path", SELECT);
        req.getContext().put("webapp", CONTEXT);
        return req;
    }
    
    public SolrQueryResponse query(SolrQueryRequest req) throws SolrException {
        final long startTime = System.currentTimeMillis();

        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        String q = req.getParams().get("q");
        String threadname = Thread.currentThread().getName();
        if (q != null) Thread.currentThread().setName("solr query: q = " + q);
        
        SolrQueryResponse rsp = new SolrQueryResponse();
        NamedList<Object> responseHeader = new SimpleOrderedMap<Object>();
        responseHeader.add("params", req.getOriginalParams().toNamedList());
        rsp.add("responseHeader", responseHeader);
        SolrRequestInfo.setRequestInfo(new SolrRequestInfo(req, rsp));

        // send request to solr and create a result
        this.requestHandler.handleRequest(req, rsp);

        // get statistics and add a header with that
        Exception exception = rsp.getException();
        int status = exception == null ? 0 : exception instanceof SolrException ? ((SolrException) exception).code() : 500;
        responseHeader.add("status", status);
        responseHeader.add("QTime",(int) (System.currentTimeMillis() - startTime));

        if (q != null) Thread.currentThread().setName(threadname);
        // return result
        return rsp;
    }

    @Override
    public QueryResponse query(ModifiableSolrParams params) throws IOException {
        if (this.server == null) throw new IOException("server disconnected");
        try {
            // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
            String q = params.get("q");
            String threadname = Thread.currentThread().getName();
            if (q != null) Thread.currentThread().setName("solr query: q = " + q);
            QueryResponse rsp = this.server.query(params);
            if (q != null) Thread.currentThread().setName(threadname);
            if (rsp != null) log.info(rsp.getResults().size() + " results for q=" + q);
            return rsp;
        } catch (SolrServerException e) {
            throw new IOException(e);
        } catch (Throwable e) {
            throw new IOException("Error executing query", e);
        }
    }

    public static void main(String[] args) {
        File solr_config = new File("defaults/solr");
        File storage = new File("DATA/INDEX/webportal/SEGMENTS/text/solr/");
        storage.mkdirs();
        try {
            EmbeddedSolrConnector solr = new EmbeddedSolrConnector(storage, solr_config);
            solr.setCommitWithinMs(100);
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField(YaCySchema.id.name(), "ABCD0000abcd");
            doc.addField(YaCySchema.title.name(), "Lorem ipsum");
            doc.addField(YaCySchema.host_s.name(), "yacy.net");
            doc.addField(YaCySchema.text_t.name(), "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
            solr.add(doc);
            
            // start a server
            SolrServlet.startServer("/solr", 8091, solr); // try http://localhost:8091/solr/select?q=*:*

            // do a normal query
            SolrDocumentList select = solr.query(YaCySchema.text_t.name() + ":tempor", 0, 10);
            for (SolrDocument d : select) System.out.println("***TEST SELECT*** " + d.toString());

            // do a facet query
            select = solr.query(YaCySchema.text_t.name() + ":tempor", 0, 10);
            for (SolrDocument d : select) System.out.println("***TEST SELECT*** " + d.toString());
            
            
            // try http://127.0.0.1:8091/solr/select?q=ping
            try {Thread.sleep(1000 * 1000);} catch (InterruptedException e) {}
            solr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
