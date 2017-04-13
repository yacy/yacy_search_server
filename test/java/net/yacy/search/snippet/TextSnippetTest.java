
package net.yacy.search.snippet;

import java.net.MalformedURLException;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.CommonPattern;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.common.SolrDocument;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;


public class TextSnippetTest {

    // declare some required parameter
    final CacheStrategy cacheStrategy = CacheStrategy.CACHEONLY;
    final boolean pre = true;
    final int snippetMaxLength = SearchEvent.SNIPPET_MAX_LENGTH;
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
    public void testTextSnippet() throws MalformedURLException {

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

        String[] wordlist = CommonPattern.SPACE.split(querywords);
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
    public void testGetLineMarked() throws MalformedURLException {
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
        String[] wordlist = CommonPattern.SPACE.split(querywords);
        for (String wordstr : wordlist) {
            assertTrue("testGetLineMarked marked word " + wordstr, rstr.contains("<b>" + wordstr + "</b>"));
        }
    }

    /**
     * Test of descriptionline method, of class TextSnippet.
     * checking poper encoding of remaining html in raw snippet line.
     */
    @Test
    public void testDescriptionline() throws MalformedURLException {
        String rawtestline = "Über großer test case </span> <pre> <hr><hr /></pre>"; // test line with html, risk of snippet format issue

        DigestURL url = new DigestURL("http://localhost/page.html");
        QueryGoal qg = new QueryGoal("test");

        // test with raw line (no marking added by YaCy)
        TextSnippet ts = new TextSnippet(
            url.hash(),
            rawtestline,
            true, // isMarked,
            TextSnippet.ResultClass.SOURCE_METADATA, "");

        String sniptxt = ts.descriptionline(qg); // snippet text for display
        System.out.println("testDescriptionline: snippet=" + sniptxt);
        assertFalse ("HTML code not allowed in snippet text",sniptxt.contains("<pre>")); // display text not to include unwanted html

        // test with marking of query word
         ts = new TextSnippet(
            url.hash(),
            rawtestline,
            false, // isMarked,
            TextSnippet.ResultClass.SOURCE_METADATA, "");

        sniptxt = ts.descriptionline(qg);
        System.out.println("testDescriptionline: snippet=" + sniptxt);
        assertFalse ("HTML code not allowed in snippet text",sniptxt.contains("<pre>")); // display text not to include unwanted html
        assertTrue ("Query word not marked", sniptxt.contains("<b>test</b>")); // query word to be marked

        // test text with some numbers (english/german format)
        rawtestline = "Test Version 1.83 calculates pi to 3,14 always";
        ts = new TextSnippet(
            url.hash(),
            rawtestline,
            false, // isMarked,
            TextSnippet.ResultClass.SOURCE_METADATA, "");
        sniptxt = ts.descriptionline(qg);
        System.out.println("testDescriptionline: (with numbers) snippet="+sniptxt);
        assertTrue ("number (.) broken up",sniptxt.contains("1.83"));
        assertTrue ("number (,) broken up",sniptxt.contains("3,14"));
    }
}
