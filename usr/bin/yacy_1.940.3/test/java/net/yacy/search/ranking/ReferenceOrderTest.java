package net.yacy.search.ranking;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.schema.CollectionConfiguration;

public class ReferenceOrderTest {


    /**
     * Test of cardinal method for URIMetadataNodes, of class ReferenceOrder.
     * (only used if no Solr score supplied)
     */
    @Test
    public void testCardinal_URIMetadataNode() throws MalformedURLException, IOException {
        File config = new File("defaults/solr.collection.schema");
        CollectionConfiguration cc = new CollectionConfiguration(config, true);

        /**
         * simple test of score result with default and zero ranking coefficient
         */
        RankingProfile rpText = new RankingProfile(Classification.ContentDomain.TEXT); // default text profile
        RankingProfile rpZero = new RankingProfile(Classification.ContentDomain.TEXT);
        rpZero.allZero(); // sets all ranking factors to 0

        ReferenceOrder roText = new ReferenceOrder(rpText, "xx"); // use unknown language
        ReferenceOrder roZero = new ReferenceOrder(rpZero, "xx"); // use unknown language

        DigestURL url = new DigestURL("http://test.org/index.html");
        URIMetadataNode uri = new URIMetadataNode(url);

        // to simulate document retrieved from index, follow transformation as in storeToIndex
        SolrInputDocument sid = cc.metadata2solr(uri);
        // generate a node for further testing
        URIMetadataNode testuri = new URIMetadataNode(cc.toSolrDocument(sid));

        long scoreText = roText.cardinal(testuri); // score with text profile
        long scoreZero = roZero.cardinal(testuri); // score 0-profile

        assertTrue("Zero-Score larger as Text-Score", scoreText >= scoreZero);

    }

}
