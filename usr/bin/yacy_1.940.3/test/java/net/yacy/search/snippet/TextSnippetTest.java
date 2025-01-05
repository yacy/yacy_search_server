
package net.yacy.search.snippet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.solr.common.SolrDocument;
import org.junit.Before;
import org.junit.Test;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;


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

        TextSnippet ts = new TextSnippet(
                null,
                testpage,
                qg.getIncludeWordsSet(),
                qg.getIncludeHashes(),
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
     * Test snippet extraction when only document title matches searched terms.
     * @throws MalformedURLException when the test document URL is malformed. Should not happen.
     */
	@Test
	public void testTextSnippetMatchTitle() throws MalformedURLException {
		final URIMetadataNode testDoc = new URIMetadataNode(doc);
		testDoc.addField(CollectionSchema.title.name(), "New test case title");
		testDoc.addField(CollectionSchema.keywords.name(), "junit");
		testDoc.addField(CollectionSchema.author.name(), "test author");
		testDoc.addField(CollectionSchema.text_t.name(),
				"A new testcase has been introduced. " + "It includes a few test lines but only title should match.");

		final String querywords = "title";
		final QueryGoal qg = new QueryGoal(querywords);

		final TextSnippet ts = new TextSnippet(null, testDoc, qg.getIncludeWordsSet(), qg.getIncludeHashes(),
				cacheStrategy, pre, snippetMaxLength, reindexing);
		assertEquals("testTextSnippet Error Code: ", "", ts.getError());
		assertTrue("Snippet line should be extracted from first text lines.",
				ts.getLineRaw().startsWith("A new testcase has been introduced."));
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

        TextSnippet ts = new TextSnippet(
                null,
                testpage,
                qg.getIncludeWordsSet(),
                qg.getIncludeHashes(),
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
            url,
            rawtestline,
            true, // isMarked,
            TextSnippet.ResultClass.SOURCE_METADATA, "");

        String sniptxt = ts.descriptionline(qg); // snippet text for display
        System.out.println("testDescriptionline: snippet=" + sniptxt);
        assertFalse ("HTML code not allowed in snippet text",sniptxt.contains("<pre>")); // display text not to include unwanted html

        // test with marking of query word
         ts = new TextSnippet(
            url,
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
            url,
            rawtestline,
            false, // isMarked,
            TextSnippet.ResultClass.SOURCE_METADATA, "");
        sniptxt = ts.descriptionline(qg);
        System.out.println("testDescriptionline: (with numbers) snippet="+sniptxt);
        assertTrue ("number (.) broken up",sniptxt.contains("1.83"));
        assertTrue ("number (,) broken up",sniptxt.contains("3,14"));
    }
    
	/**
	 * Run text snippet extraction from a given plain text file.
	 * @param args <ol><li>first element : the plain text file path. When not specified, "test/parsertest/umlaute_linux.txt" is used as default.</li>
	 * <li>other elements : the search terms. When not specified, "Maßkrügen" is used as default</li>
	 * </ol>
	 * @throws IOException when a read/write error occurred
	 */
	public static void main(final String args[]) throws IOException {
		try {
			final SolrDocument doc = new SolrDocument();
			final DigestURL url = new DigestURL("http://localhost/page.html");
			doc.addField(CollectionSchema.id.name(), ASCII.String(url.hash()));
			doc.addField(CollectionSchema.sku.name(), url.toNormalform(false));

			final URIMetadataNode urlEntry = new URIMetadataNode(doc);
			urlEntry.addField(CollectionSchema.title.name(), "New test case");
			urlEntry.addField(CollectionSchema.keywords.name(), "junit");
			urlEntry.addField(CollectionSchema.author.name(), "test author");
			
			final Path testFilePath;
			if(args.length > 0) {
				testFilePath = Paths.get(args[0]);
			} else {
				testFilePath = Paths.get("test/parsertest/umlaute_linux.txt");
			}
			
			urlEntry.addField(CollectionSchema.text_t.name(), new String(Files.readAllBytes(testFilePath),
					StandardCharsets.UTF_8));
			
			final StringBuilder queryWords = new StringBuilder();
			if(args.length > 1) {
				for(int i = 1; i < args.length; i++) {
					if(queryWords.length() > 0) {
						queryWords.append(" ");
					}
					queryWords.append(args[i]);	
				}
			} else {
				queryWords.append("Maßkrügen");
			}

			final QueryGoal goal = new QueryGoal(queryWords.toString());
			
			System.out.println("Extracting text snippet for terms \"" + queryWords + "\" from file " + testFilePath);
			
			TextSnippet.statistics.setEnabled(true);
			final TextSnippet snippet = new TextSnippet(null, urlEntry, goal.getIncludeWordsSet(), goal.getIncludeHashes(),
					CacheStrategy.CACHEONLY, false, SearchEvent.SNIPPET_MAX_LENGTH, false);
			System.out.println("Snippet initialized in " + TextSnippet.statistics.getMaxInitTime() + "ms");
			System.out.println("Snippet status : " + snippet.getErrorCode());
			System.out.println("Snippet : " + snippet.descriptionline(goal));
		} finally {
			/* Shutdown running threads */
			try {
				Domains.close();
			} finally {
				ConcurrentLog.shutdown();
			}
		}
	}
}
