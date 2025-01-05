package net.yacy.cora.federate.solr.connector;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;


public class EmbeddedSolrConnectorTest {

    static EmbeddedSolrConnector solr;
    static EmbeddedInstance localCollectionInstance;
    public EmbeddedSolrConnectorTest() {
    }

    /**
     * init for all test cases (via BeforeClass annotation),
     * for the expensive creating or loading of index
     */
    @BeforeClass
    public static void initTesting() {
        File solr_config = new File("defaults/solr");
        File storage = new File("test/DATA/INDEX/webportal/SEGMENTS/text/solr/");
        storage.mkdirs();
        System.out.println("setup EmeddedSolrConnector using config dir: " + solr_config.getAbsolutePath());
        try {
             localCollectionInstance = new EmbeddedInstance(solr_config, storage, CollectionSchema.CORE_NAME, new String[]{CollectionSchema.CORE_NAME, WebgraphSchema.CORE_NAME});
            solr = new EmbeddedSolrConnector(localCollectionInstance);
            solr.clear(); // delete all documents in index (for clean testing)
        } catch (final IOException ex) {
            fail("IOException starting Jetty");
        }
    }

    @AfterClass
    public static void finalizeTesting() {
        localCollectionInstance.close();
    }

    /**
     * Test of query solr via jetty
     */
    @Test
    public void testQuery() throws IOException {
        System.out.println("adding test document to solr");
        SolrInputDocument doc = new SolrInputDocument();
        String id = Long.toString(System.currentTimeMillis());
        doc.addField(CollectionSchema.id.name(), id);
        doc.addField(CollectionSchema.title.name(), "Lorem ipsum");
        doc.addField(CollectionSchema.host_s.name(), "yacy.net");
        // mixing in the id as unique word
        doc.addField(CollectionSchema.text_t.name(), "Lorem ipsum dolor sit amet, consectetur adipisicing elit, x"+id+ " sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");

        solr.add(doc);
        solr.commit(true);

        System.out.println("query solr");
        long expResult = 1;
        long result = solr.getCountByQuery(CollectionSchema.text_t.name() + ":x" + id);
        System.out.println("found = " + result + " (expected = 1 )");
        assertEquals(expResult, result);
    }

    /**
     * Test of update (partial update)
     */
    @Test
    public void testUdate() throws IOException {
        SolrInputDocument doc = new SolrInputDocument();
        String id = Long.toString(System.currentTimeMillis());
        System.out.println("testUpdate: adding test document to solr ID=" + id);
        doc.addField(CollectionSchema.id.name(), id);
        doc.addField(CollectionSchema.title.name(), "Lorem ipsum");
        doc.addField(CollectionSchema.host_s.name(), "yacy.net");
        doc.addField(CollectionSchema.text_t.name(), "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");

        solr.add(doc);
        solr.commit(true);

        System.out.println("testUpdate: update one document ID=" + id);

        HashSet<String> fieldnames = new HashSet<String>();
        fieldnames.addAll(doc.getFieldNames());

        SolrInputDocument sid = new SolrInputDocument();
        sid.addField(CollectionSchema.id.name(), doc.getFieldValue(CollectionSchema.id.name()));
        sid.addField(CollectionSchema.host_s.name(), "yacy.yacyh");
        solr.update(sid);
        solr.commit(true);

        long expResult = 1;
        SolrDocumentList sl = solr.getDocumentListByQuery(CollectionSchema.host_s.name()+":yacy.yacyh",null,0,10);
        assertTrue(sl.size() >= expResult);

        System.out.println("testUpdate: verify update of document ID=" + id);
        String foundid = null;
        for (SolrDocument rdoc : sl) {
            foundid = (String) rdoc.getFieldValue("id");
            if (id.equals(foundid)) {
                HashSet<String> newfieldnames = new HashSet<String>();
                newfieldnames.addAll(rdoc.getFieldNames());
                assertTrue(newfieldnames.containsAll(fieldnames));
                break;
            }
        }
        assertEquals(id, foundid);
    }

    /**
     * Test for partial update for document containing a multivalued date field
     * this is a Solr issue (2015-09-12)
     * the test case is just to demonstrate the effect on YaCy (currently catching the solr exception and reinserting a document with fields missing)
     *
     * Solr 5.4.0 bugfix @see http://issues.apache.org/jira/browse/SOLR-8050 Partial update on document with multivalued date field fails
     */
    @Test
    public void testUdate_withMultivaluedDateField() throws SolrException, IOException {
        SolrInputDocument doc = new SolrInputDocument();
        String id = Long.toString(System.currentTimeMillis());
        System.out.println("testUpdate: adding test document to solr ID=" + id);
        doc.addField(CollectionSchema.id.name(), id);
        doc.addField(CollectionSchema.title.name(), "Lorem ipsum");
        doc.addField(CollectionSchema.host_s.name(), "yacy.net");
        doc.addField(CollectionSchema.text_t.name(), "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
        doc.addField(CollectionSchema.dates_in_content_dts.name(), new Date());

        solr.add(doc);
        solr.commit(true);

        System.out.println("testUpdate: update one document ID=" + id);

        HashSet<String> fieldnames = new HashSet<String>();
        fieldnames.addAll(doc.getFieldNames());

        SolrInputDocument sid = new SolrInputDocument();
        sid.addField(CollectionSchema.id.name(), id);
        sid.addField(CollectionSchema.host_s.name(), "yacy.yacy");
        solr.update(sid);
        solr.commit(true);

        long expResult = 1;
        SolrDocumentList sl = solr.getDocumentListByQuery(CollectionSchema.host_s.name()+":yacy.yacy",null,0,10);
        assertTrue(sl.size() >= expResult);

        System.out.println("testUpdate: verify update of document ID=" + id);
        String foundid = null;
        for (SolrDocument rdoc : sl) {
            foundid = (String) rdoc.getFieldValue("id");
            if (id.equals(foundid)) {
                HashSet<String> newfieldnames = new HashSet<String>();
                newfieldnames.addAll(rdoc.getFieldNames());
                if (!newfieldnames.containsAll(fieldnames)) {
                    System.err.println("!!!++++++++++++++++++++++++++++++++++++!!!");
                    System.err.println("fields in original document: "+fieldnames.toString());
                    System.err.println("fields after partial update: "+newfieldnames.toString());
                    System.err.println("!!!++++++++++++++++++++++++++++++++++++!!!");
                }
                assertTrue (newfieldnames.containsAll(fieldnames));
                break;
            }
        }
        assertEquals(id, foundid);
    }

    /**
     * Test of close and reopen embedded Solr
     * test for issue http://mantis.tokeek.de/view.php?id=686
     * and debug option for EmbeddedSolrConnector.close() (cause this.core.close())
     */
    @Test
    public void testClose() throws Throwable {
        System.out.println("-close "+solr.toString());
        // we must close the instance to free all resources instead of only closing the connector
        // solr.close();
        localCollectionInstance.close();

        System.out.println("+reopen "+solr.toString());
        initTesting();
        
        assertTrue(!solr.isClosed());
    }
}
