package net.yacy.cora.federate.solr.connector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.yacy.cora.federate.solr.SolrServlet;
import net.yacy.cora.federate.solr.YaCySchema;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;

public class EmbeddedSolrConnectorTest {

    Server jetty; // Jetty server
    EmbeddedSolrConnector solr;

    public EmbeddedSolrConnectorTest() {
    }

    @Before
    public void setUp() {
        File solr_config = new File("defaults/solr");
        File storage = new File("test/DATA/INDEX/webportal/SEGMENTS/text/solr/");
        storage.mkdirs();
        System.out.println("setup EmeddedSolrConnector using config dir: " + solr_config.getAbsolutePath());
        try {
            solr = new EmbeddedSolrConnector(storage, solr_config);

            // start a server
            jetty = startServer("/solr", 8091, solr); // try http://localhost:8091/solr/select?q=*:*

        } catch (IOException ex) {
            fail("IOException starting Jetty");
        }
    }

    @After
    public void tearDown() {
        if (jetty != null) {
            try {
                jetty.stop();
            } catch (Exception ex) {
                fail("Exception stopping Jetty");
            }
        }
        solr.close();
    }

    /**
     * Test of query solr via jetty
     */
    @Test
    public void testQuery() {
        System.out.println("adding test document to solr");
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField(YaCySchema.id.name(), "ABCD0000abcd");
        doc.addField(YaCySchema.title.name(), "Lorem ipsum");
        doc.addField(YaCySchema.host_s.name(), "yacy.net");
        doc.addField(YaCySchema.text_t.name(), "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
        try {
            solr.add(doc);
        } catch (IOException ex) {
            fail("IOException adding test document to Solr");
        } catch (SolrException ex) {
            fail("SolrExceptin adding test document to Solr");
        }
        solr.commit(true);

        System.out.println("query solr");
        long expResult = 1;
        SolrDocumentList result;
        try {
            result = solr.query(YaCySchema.text_t.name() + ":tempor", 0, 10);
            assertEquals(expResult, result.getNumFound());
        } catch (IOException ex) {
            fail("Solr query no result");
        }
    }

    public static void waitForSolr(String context, int port) throws Exception {
        // A raw term query type doesn't check the schema
        URL url = new URL("http://127.0.0.1:" + port + context + "/select?q={!raw+f=test_query}ping");

        Exception ex = null;
        // Wait for a total of 20 seconds: 100 tries, 200 milliseconds each
        for (int i = 0; i < 600; i++) {
            try {
                InputStream stream = url.openStream();
                stream.close();
            } catch (IOException e) {
                ex = e;
                Thread.sleep(200);
                continue;
            }
            return;
        }
        throw new RuntimeException("Jetty/Solr unresponsive", ex);
    }

    /**
     * from org.apache.solr.client.solrj.embedded.JettySolrRunner
     */
    public static Server startServer(String context, int port, EmbeddedSolrConnector c) {
        //this.context = context;
        Server server = new Server(port);
        /*
         SocketConnector connector = new SocketConnector();
         connector.setPort(port);
         connector.setReuseAddress(true);
         this.server.setConnectors(new Connector[] { connector });
         this.server.setSessionIdManager(new HashSessionIdManager(new Random()));
         */
        server.setStopAtShutdown(true);
        Context root = new Context(server, context, Context.SESSIONS);
        root.addServlet(SolrServlet.Servlet404.class, "/*");

        // attach org.apache.solr.response.XMLWriter to search requests
        SolrServlet.initCore(c);
        FilterHolder dispatchFilter = root.addFilter(SolrServlet.class, "*", Handler.REQUEST);

        if (!server.isRunning()) {
            try {
                server.start();
                waitForSolr(context, port);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return server;
    }

    public static void main(String[] args) {
        File solr_config = new File("defaults/solr");
        File storage = new File("DATA/INDEX/webportal/SEGMENTS/text/solr/");
        storage.mkdirs();
        try {
            EmbeddedSolrConnector solr = new EmbeddedSolrConnector(storage, solr_config);
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField(YaCySchema.id.name(), "ABCD0000abcd");
            doc.addField(YaCySchema.title.name(), "Lorem ipsum");
            doc.addField(YaCySchema.host_s.name(), "yacy.net");
            doc.addField(YaCySchema.text_t.name(), "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
            solr.add(doc);

            // start a server
            startServer("/solr", 8091, solr); // try http://localhost:8091/solr/select?q=*:*

            // do a normal query
            SolrDocumentList select = solr.query(YaCySchema.text_t.name() + ":tempor", 0, 10);
            for (SolrDocument d : select) {
                System.out.println("***TEST SELECT*** " + d.toString());
            }

            // do a facet query
            select = solr.query(YaCySchema.text_t.name() + ":tempor", 0, 10);
            for (SolrDocument d : select) {
                System.out.println("***TEST SELECT*** " + d.toString());
            }


            // try http://127.0.0.1:8091/solr/select?q=ping
            try {
                Thread.sleep(1000 * 1000);
            } catch (InterruptedException e) {
            }
            solr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
