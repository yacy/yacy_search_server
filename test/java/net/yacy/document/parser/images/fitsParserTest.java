package net.yacy.document.parser.images;

import java.io.File;
import java.io.FileInputStream;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.Document;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import org.junit.Test;
import static org.junit.Assert.*;

public class fitsParserTest {

    @Test
    public void testParseFitsC19() throws Exception {
        System.out.println("fitsParser.parse sample_c19.fit");

        final String filename = "test/parsertest/sample_c19.fit";
        final File file = new File(filename);
        assertTrue("Test FITS file sample_c19.fit should exist", file.exists());

        final String mimetype = "image/fits";
        final AnchorURL url = new AnchorURL("http://localhost/" + file.getName());

        fitsParser parser = new fitsParser();
        FileInputStream inStream = new FileInputStream(file);
        try {
            Document[] docs = parser.parse(url, mimetype, "UTF-8", new VocabularyScraper(), 0, inStream);
            assertNotNull("Document array should not be null", docs);
            assertTrue("Document array should contain at least 1 document", docs.length > 0);

            Document doc = docs[0];
            assertNotNull("Title should not be null", doc.dc_title());
            System.out.println("Title: " + doc.dc_title());
            System.out.println("Author/Telescope: " + doc.dc_creator());
            System.out.println("Descriptions: " + java.util.Arrays.toString(doc.dc_description()));

            assertNotNull("Images map should not be null", doc.getImages());
            assertFalse("Images map should contain preview entry", doc.getImages().isEmpty());
        } finally {
            inStream.close();
        }
    }

    @Test
    public void testParseFitsM31() throws Exception {
        System.out.println("fitsParser.parse sample_m31.fit");

        final String filename = "test/parsertest/sample_m31.fit";
        final File file = new File(filename);
        assertTrue("Test FITS file sample_m31.fit should exist", file.exists());

        final String mimetype = "image/fits";
        final AnchorURL url = new AnchorURL("http://localhost/" + file.getName());

        fitsParser parser = new fitsParser();
        FileInputStream inStream = new FileInputStream(file);
        try {
            Document[] docs = parser.parse(url, mimetype, "UTF-8", new VocabularyScraper(), 0, inStream);
            assertNotNull("Document array should not be null", docs);
            assertTrue("Document array should contain at least 1 document", docs.length > 0);

            Document doc = docs[0];
            assertNotNull("Title should not be null", doc.dc_title());
            System.out.println("Title: " + doc.dc_title());
        } finally {
            inStream.close();
        }
    }

    @Test
    public void testTextParserSupport() throws Exception {
        AnchorURL url = new AnchorURL("http://localhost/test_image.fit");
        assertNull("TextParser should support image/fits mime type", TextParser.supports(url, "image/fits"));
    }
}
