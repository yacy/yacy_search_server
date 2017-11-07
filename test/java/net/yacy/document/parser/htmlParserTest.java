package net.yacy.document.parser;

import static net.yacy.document.parser.htmlParser.parseToScraper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import junit.framework.TestCase;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;

public class htmlParserTest extends TestCase {

        @Test
	public void testGetRealCharsetEncoding() {
		String[][] testStrings = new String[][] {
	       new String[]{null,null},
	       new String[]{"windows1250","windows-1250"},
	       new String[]{"windows_1250","windows-1250"},
	       new String[]{"ISO-8859-1", StandardCharsets.ISO_8859_1.name()},
	       new String[]{"ISO8859-1", StandardCharsets.ISO_8859_1.name()},
	       new String[]{"ISO-88591", StandardCharsets.ISO_8859_1.name()},
	       new String[]{"ISO88591", StandardCharsets.ISO_8859_1.name()},
	       new String[]{"iso_8859_1", StandardCharsets.ISO_8859_1.name()},
	       new String[]{"cp-1252","windows-1252"},
	       new String[]{"gb_2312","gb2312"},           // was: x-EUC-CN
	       new String[]{"gb_2312-80","gb2312"},           // was: x-EUC-CN
	       new String[]{"UTF-8;", StandardCharsets.UTF_8.name()}
		};
		
		for (int i=0; i < testStrings.length; i++) {
			// desired conversion result
			String shouldBe = testStrings[i][1];
			shouldBe = shouldBe!=null ? shouldBe.toLowerCase(Locale.ROOT) : null;
			
			// conversion result
			String charset = htmlParser.patchCharsetEncoding(testStrings[i][0]);
			
			// test if equal
			assertEquals(shouldBe, charset!=null ? charset.toLowerCase(Locale.ROOT) : null);
			System.out.println("testGetRealCharsetEncoding: " + (testStrings[i][0]!=null?testStrings[i][0]:"null") + " -> " + (charset!=null?charset:"null") + " | Supported: " + (charset!=null?Charset.isSupported(charset):false));
			
		}
		
	}

    /**
     * Test of parse method, of class htmlParser.
     * - test getCharset
     * @throws IOException 
     */
    @Test
    public void testParse() throws Parser.Failure, InterruptedException, IOException {
        System.out.println("htmlParser.parse");

        String[] testFiles = {
            "umlaute_html_iso.html",
            "umlaute_html_utf8.html",
            "umlaute_html_namedentities.html"};

        final String mimetype = "text/html";
        //final String resulttxt = "In München steht ein Hofbräuhaus. Dort gibt es Bier aus Maßkrügen.";

        for (String testfile : testFiles) {
            final String filename = "test/parsertest/" + testfile;
            final File file = new File(filename);

            final AnchorURL url = new AnchorURL("http://localhost/" + filename);
            System.out.println("parse file: " + filename);

            htmlParser p = new htmlParser();
            FileInputStream inStream = null;
            try {
            	inStream = new FileInputStream(file);
            	
                final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, inStream);

                Document doc = docs[0];
                String txt = doc.getCharset();
                assertTrue("get Charset", txt != null);
                System.out.println("detected charset = " + txt);
            } finally {
            	if(inStream != null) {
            		inStream.close();
            	}
            }
        }
    }
    
	/**
	 * Test the htmlParser.parse() method, with no charset information, neither
	 * provided by HTTP header nor by meta tags or attributes.
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseHtmlWithoutCharset() throws Exception {
		final AnchorURL url = new AnchorURL("http://localhost/test.html");
		final String mimetype = "text/html";
		final StringBuilder testHtml = new StringBuilder("<!DOCTYPE html><html><body><p>");
		/*
		 * Include some non ASCII characters : once encoded they should make the charset
		 * detector to detect the exact encoding
		 */
		testHtml.append("In München steht ein Hofbräuhaus.\n" + "Dort gibt es Bier aus Maßkrügen.<br>");
		testHtml.append("<a href=\"http://localhost/doc1.html\">First link</a>");
		testHtml.append("<a href=\"http://localhost/doc2.html\">Second link</a>");
		testHtml.append("<a href=\"http://localhost/doc3.html\">Third link</a>");
		testHtml.append("</p></body></html>");

		final htmlParser parser = new htmlParser();

		final Charset[] charsets = new Charset[] { StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1 };

		for (final Charset charset : charsets) {
			try (InputStream sourceStream = new ByteArrayInputStream(testHtml.toString().getBytes(charset));) {
				final Document[] docs = parser.parse(url, mimetype, null, new VocabularyScraper(), 0, sourceStream);
				final Document doc = docs[0];
				assertEquals(3, doc.getAnchors().size());
				assertTrue(doc.getTextString().contains("Maßkrügen"));
				assertEquals(charset.toString(), doc.getCharset());
			}
		}
	}
    
    /**
     * Test the htmlParser.parseWithLimits() method with test content within bounds.
     * @throws Exception when an unexpected error occurred
     */
    @Test
    public void testParseWithLimitsUnreached() throws Exception {
        System.out.println("htmlParser.parse");

        String[] testFiles = {
            "umlaute_html_iso.html",
            "umlaute_html_utf8.html",
            "umlaute_html_namedentities.html"};

        final String mimetype = "text/html";
        //final String resulttxt = "In München steht ein Hofbräuhaus. Dort gibt es Bier aus Maßkrügen.";

        htmlParser parser = new htmlParser();
        for (final String testfile : testFiles) {
            final String fileName = "test" + File.separator + "parsertest" + File.separator + testfile;
            final File file = new File(fileName);

            final AnchorURL url = new AnchorURL("http://localhost/" + fileName);

            try (final FileInputStream inStream = new FileInputStream(file);) {
            	
                final Document[] docs = parser.parseWithLimits(url, mimetype, null, new VocabularyScraper(), 0, inStream, 1000, 10000);
                final Document doc = docs[0];
                assertNotNull("Parser result must not be null for file " + fileName, docs);
                final String parsedText = doc.getTextString();
				assertNotNull("Parsed text must not be empty for file " + fileName, parsedText);
				assertTrue("Parsed text must contain test word with umlaut char in file " + fileName,
						parsedText.contains("Maßkrügen"));
				assertEquals("Test anchor must have been parsed for file " + fileName, 1, doc.getAnchors().size());
				assertFalse("Parsed document should not be marked as partially parsed for file " + fileName, doc.isPartiallyParsed());
                
            }
        }
    }
    
	/**
	 * Test the htmlParser.parseWithLimits() method, with various maxLinks values
	 * ranging from zero to the exact anchors number contained in the test content.
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseWithLimitsOnAnchors() throws Exception {
		final AnchorURL url = new AnchorURL("http://localhost/test.html");
		final String mimetype = "text/html";
		final String charset = StandardCharsets.UTF_8.name();
		final StringBuilder testHtml = new StringBuilder("<!DOCTYPE html><html><body><p>");
		testHtml.append("<a href=\"http://localhost/doc1.html\">First link</a>");
		testHtml.append("<a href=\"http://localhost/doc2.html\">Second link</a>");
		testHtml.append("<a href=\"http://localhost/doc3.html\">Third link</a>");
		testHtml.append("</p></body></html>");

		final htmlParser parser = new htmlParser();

		for (int maxLinks = 0; maxLinks <= 3; maxLinks++) {
			try (InputStream sourceStream = new ByteArrayInputStream(
					testHtml.toString().getBytes(StandardCharsets.UTF_8));) {
				final Document[] docs = parser.parseWithLimits(url, mimetype, charset, new VocabularyScraper(), 0,
						sourceStream, maxLinks, Long.MAX_VALUE);
				final Document doc = docs[0];
				assertEquals(maxLinks, doc.getAnchors().size());
				assertEquals("The parsed document should be marked as partially parsed only when the limit is exceeded",
						maxLinks < 3, doc.isPartiallyParsed());
			}
		}
	}
    
	/**
	 * Test the htmlParser.parseWithLimits() method, with various maxLinks values
	 * ranging from zero the exact RSS feed links number contained in the test
	 * content.
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseWithLimitsOnRSSFeeds() throws Exception {
		final AnchorURL url = new AnchorURL("http://localhost/test.html");
		final String mimetype = "text/html";
		final String charset = StandardCharsets.UTF_8.name();
		final StringBuilder testHtml = new StringBuilder("<!DOCTYPE html><html>");
		testHtml.append("<head>");
		testHtml.append(
				"<link rel=\"alternate\" type=\"application/rss+xml\" title=\"Feed1\" href=\"http://localhost/rss1.xml\" />");
		testHtml.append(
				"<link rel=\"alternate\" type=\"application/rss+xml\" title=\"Feed2\" href=\"http://localhost/rss2.xml\" />");
		testHtml.append(
				"<link rel=\"alternate\" type=\"application/rss+xml\" title=\"Feed3\" href=\"http://localhost/rss3.xml\" />");
		testHtml.append("</head>");
		testHtml.append("<body><p>HTML test content</p></body></html>");

		final htmlParser parser = new htmlParser();

		for (int maxLinks = 0; maxLinks <= 3; maxLinks++) {
			try (InputStream sourceStream = new ByteArrayInputStream(
					testHtml.toString().getBytes(StandardCharsets.UTF_8));) {
				final Document[] docs = parser.parseWithLimits(url, mimetype, charset, new VocabularyScraper(), 0,
						sourceStream, maxLinks, Long.MAX_VALUE);
				final Document doc = docs[0];
				assertEquals(maxLinks, doc.getRSS().size());
				assertEquals("The parsed document should be marked as partially parsed only when the limit is exceeded",
						maxLinks < 3, doc.isPartiallyParsed());
			}
		}
    }
    
    /**
     * Test of parseToScraper method, of class htmlParser.
     */
    @Test
    public void testParseToScraper_4args() throws Exception {
        // test link with inline html in text
        // expectation to deliver pure text as it is possibly indexed in outboundlinks_anchortext_txt/inboundlinks_anchortext_txt
        final AnchorURL url = new AnchorURL("http://localhost/");
        final String charset = StandardCharsets.UTF_8.name();
        final String testhtml = "<html><body>"
                + "<a href='x1.html'><span>testtext</span></a>" // "testtext"
                + "<a href=\"http://localhost/x2.html\">   <i id=\"home-icon\" class=\"img-sprite\"></i>Start</a>" // "Start"
                + "<a href='x1.html'><span class='button'><img src='pic.gif'/></span></a>" // ""  + image
                + "<figure><img width=\"550px\" title=\"image as exemple\" alt=\"image as exemple\" src=\"./img/my_image.png\"></figrue>" // + img width 550 (+html5 figure)
                + "</body></html>";

        ContentScraper scraper = parseToScraper(url, charset, new VocabularyScraper(), 0, testhtml, 10, 10);
        List<AnchorURL> anchorlist = scraper.getAnchors();

        String linktxt = anchorlist.get(0).getTextProperty();
        assertEquals("testtext", linktxt);

        linktxt = anchorlist.get(1).getTextProperty();
        assertEquals("Start", linktxt);

        linktxt = anchorlist.get(2).getTextProperty();
        assertEquals("", linktxt);

        int cnt = scraper.getImages().size();
        assertEquals(2,cnt);
        ImageEntry img = scraper.getImages().get(1);
        assertEquals(550,img.width());
    }
    
    /**
     * Test parser resistance against nested anchors pattern 
     * (<a> tag embedding other <a> tags : invalid HTML, but occasionally encountered in some real-world Internet resources. 
     * See case reported at http://forum.yacy-websuche.de/viewtopic.php?f=23&t=6005). 
     * The parser must be able to terminate in a finite time.
     * @throws IOException when an unexpected error occurred
     */
    @Test
    public void testParseToScraperNestedAnchors() throws IOException {
        final AnchorURL url = new AnchorURL("http://localhost/");
        final String charset = StandardCharsets.UTF_8.name();
        final StringBuilder testHtml = new StringBuilder("<!DOCTYPE html><html><body><p>");
        /* With prior recursive processing implementation and an average 2017 desktop computer, 
         * computing time started to be problematic over a nesting depth of 21 */
        final int nestingDepth = 30;
        for (int count = 0; count < nestingDepth; count++) {
        	testHtml.append("<a href=\"http://localhost/doc" + count + ".html\">");
        }
        testHtml.append("<img src=\"./img/my_image.png\">");
        for (int count = 0; count < nestingDepth; count++) {
        	testHtml.append("</a>");
        }
        testHtml.append("</p></body></html>");
        
        ContentScraper scraper = parseToScraper(url, charset, new VocabularyScraper(), 0, testHtml.toString(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(nestingDepth, scraper.getAnchors().size());
        assertEquals(1, scraper.getImages().size());

    }

    /**
     * Test of parseToScraper method, of class htmlParser
     * for scraping tag content from text (special test to verify <style> not counted as text
     */
    @Test
    public void testParseToScraper_TagTest() throws Exception {
        final AnchorURL url = new AnchorURL("http://localhost/");
        final String charset = StandardCharsets.UTF_8.name();
        final String textSource = "test text";
        final String testhtml = "<html>"
                + "<head><style type=\"text/css\"> h1 { color: #ffffff; }</style></head>"
                + "<body>"
                + "<p>" + textSource + "</p>"
                + "</body></html>";

        ContentScraper scraper = parseToScraper(url, charset, new VocabularyScraper(), 0, testhtml, 10, 10);

        String txt = scraper.getText();
        System.out.println("ScraperTagTest: [" + textSource + "] = [" + txt + "]");
        assertEquals(txt, textSource);
    }

    /**
     * Test for parseToScraper of class htmlParser for scraping html with a
     * <script> tag which contains code with similar to other opening tag
     * like "<a " see https://github.com/yacy/yacy_search_server/issues/109
     */
    @Test
    public void testParseToScraper_ScriptTag() throws MalformedURLException, IOException {
        final AnchorURL url = new AnchorURL("http://localhost/");
        final String charset = StandardCharsets.UTF_8.name();
        final String textSource = "test text";
        // extract from test case provided by https://github.com/yacy/yacy_search_server/issues/109
        String testhtml = "<!doctype html>"
                + "<html class=\"a-no-js\" data-19ax5a9jf=\"dingo\">"
                + "<head><script>var aPageStart = (new Date()).getTime();</script><meta charset=\"utf-8\"><!--  emit CSM JS -->\n"
                + "<script>\n"
                + "function D(){if(E){var a=f.innerWidth?{w:f.innerWidth,h:f.innerHeight}:{w:k.clientWidth,h:k.clientHeight};5<Math.abs(a.w-\n"
                //  the  50<a  is a possible error case
                + "P.w)||50<a.h-P.h?(P=a,Q=4,(a=l.mobile||l.tablet?450<a.w&&a.w>a.h:1250==a.w)?C(k,\"a-ws\"):ca(k,\"a-ws\")):Q--&&(ea=setTimeout(D,16))}}function na(a){(E=void 0===a?!E:!!a)&&D()}"
                + "</script>\n"
                + "</head>\n"
                + "<body>" + textSource + "</body>\n"
                + "</html>";
        ContentScraper scraper = parseToScraper(url, charset, new VocabularyScraper(), 0, testhtml, 10, 10);

        String txt = scraper.getText();
        System.out.println("ScraperScriptTagTest: [" + textSource + "] = [" + txt + "]");
        assertEquals(txt, textSource);
    }
}
