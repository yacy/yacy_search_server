package net.yacy.document.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import junit.framework.TestCase;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import static net.yacy.document.parser.htmlParser.parseToScraper;
import org.junit.Test;

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
			shouldBe = shouldBe!=null ? shouldBe.toLowerCase() : null;
			
			// conversion result
			String charset = htmlParser.patchCharsetEncoding(testStrings[i][0]);
			
			// test if equal
			assertEquals(shouldBe, charset!=null ? charset.toLowerCase() : null);
			System.out.println("testGetRealCharsetEncoding: " + (testStrings[i][0]!=null?testStrings[i][0]:"null") + " -> " + (charset!=null?charset:"null") + " | Supported: " + (charset!=null?Charset.isSupported(charset):false));
			
		}
		
	}

    /**
     * Test of parse method, of class htmlParser.
     * - test getCharset
     */
    @Test
    public void testParse() throws MalformedURLException, Parser.Failure, InterruptedException, FileNotFoundException {
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
            final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, new FileInputStream(file));

            Document doc = docs[0];
            String txt = doc.getCharset();
            assertTrue("get Charset", txt != null);
            System.out.println("detected charset = " + txt);

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

        ContentScraper scraper = parseToScraper(url, charset, new VocabularyScraper(), 0, testhtml, 10);
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

        ContentScraper scraper = parseToScraper(url, charset, new VocabularyScraper(), 0, testhtml, 10);

        String txt = scraper.getText();
        System.out.println("ScraperTagTest: [" + textSource + "] = [" + txt + "]");
        assertEquals(txt, textSource);
    }
}
