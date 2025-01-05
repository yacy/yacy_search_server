
package net.yacy.document.parser.images;

import java.io.File;
import java.io.FileInputStream;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;
import org.junit.Test;
import static org.junit.Assert.*;

public class genericImageParserTest {

    /**
     * Test of parse method, of class genericImageParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("genericImageParser.parse Jpeg");

        final String testFiles = "YaCyLogo_120ppi.jpg";
        final String mimetype = "image/jpeg";
        final String charset = null;

        final String filename = "test/parsertest/" + testFiles;
        final File file = new File(filename);

        final AnchorURL url = new AnchorURL("http://localhost/" + filename);
        System.out.println("parse file: " + filename);

        genericImageParser p = new genericImageParser();
        FileInputStream inStream = new FileInputStream(file);
        try {
        	final Document[] docs = p.parse(url, mimetype, charset, new VocabularyScraper(), 0, inStream);
            Document doc = docs[0];
            assertEquals("YaCy Logo",doc.dc_title());
            System.out.println(doc.toString());
        } finally {
        	inStream.close();
        }
    }

}
