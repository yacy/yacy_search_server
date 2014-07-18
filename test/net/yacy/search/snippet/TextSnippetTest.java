
package net.yacy.search.snippet;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.storage.HandleSet;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.common.SolrDocument;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;


public class TextSnippetTest {

    // declare some required parameter
    final CacheStrategy cacheStrategy = CacheStrategy.CACHEONLY;
    final boolean pre = true;
    final int snippetMaxLength = 220;
    final boolean reindexing = false;

    SolrDocument doc;

    public TextSnippetTest() {
    }

    @Before
    public void setUp() throws Exception {

        // prepare a empty test document
        doc = new SolrDocument();
        DigestURL url = new DigestURL("http://localhost/page.html");
        doc.addField(CollectionSchema.id.name(), ASCII.String(url.hash()));
        doc.addField(CollectionSchema.sku.name(), url.toNormalform(false));
        // for testcases add other fields
        // fields involved in snippet extraction:
        // url, title, keywords, author, text_t
    }

    @Test
    public void testTextSnippet() {

        URIMetadataNode testpage = new URIMetadataNode(doc);
        testpage.addField(CollectionSchema.title.name(), "New test case");
        testpage.addField(CollectionSchema.keywords.name(), "junit");
        testpage.addField(CollectionSchema.author.name(), "test author");
        testpage.addField(CollectionSchema.text_t.name(), "A new testcase has been introduced. "
                + "It includes a few test lines and one line that should match.");

        String querywords = "testcase line";
        QueryGoal qg = new QueryGoal(querywords);
        HandleSet queryhashes = qg.getIncludeHashes();

        TextSnippet ts = new TextSnippet(
                null,
                testpage,
                queryhashes,
                cacheStrategy,
                pre,
                snippetMaxLength,
                reindexing
        );
        String rstr = ts.getError();
        assertEquals("testTextSnippet Error Code: ", "", rstr);

        String[] wordlist = querywords.split(" ");
        rstr = ts.toString();
        System.out.println("testTextSnippet: query=" + querywords);
        System.out.println("testTextSnippet: snippet=" + rstr);
        // check words included in snippet
        for (String word : wordlist) {
            assertTrue("testTextSnippet word included " + word, rstr.contains(word));
        }

    }

    /**
     * Test of getLineMarked method, of class TextSnippet.
     */
    @Test
    public void testGetLineMarked() {
        URIMetadataNode testpage = new URIMetadataNode(doc);
        testpage.addField(CollectionSchema.title.name(), "New test case");
        testpage.addField(CollectionSchema.keywords.name(), "junit");
        testpage.addField(CollectionSchema.author.name(), "test author");
        testpage.addField(CollectionSchema.text_t.name(),
                "A new testcase has been introduced. "
                + "It includes a few test lines and one line that should match.");

        String querywords = "testcase line";
        QueryGoal qg = new QueryGoal(querywords);
        HandleSet queryhashes = qg.getIncludeHashes();

        TextSnippet ts = new TextSnippet(
                null,
                testpage,
                queryhashes,
                cacheStrategy,
                pre,
                snippetMaxLength,
                reindexing
        );

        String rstr = ts.getError();
        assertEquals("testGetLineMarked Error Code: ", "", rstr);

        // check words marked in snippet
        rstr = ts.getLineMarked(qg);
        System.out.println("testGetLineMarked: query=" + querywords);
        System.out.println("testGetLineMarked: snippet=" + rstr);
        String[] wordlist = querywords.split(" ");
        for (String wordstr : wordlist) {
            assertTrue("testGetLineMarked marked word " + wordstr, rstr.contains("<b>" + wordstr + "</b>"));
        }
    }

}
