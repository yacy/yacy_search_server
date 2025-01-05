
package net.yacy.document.parser.images;

import java.io.File;
import java.io.FileInputStream;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;
import org.junit.Test;
import static org.junit.Assert.*;

public class metadataImageParserTest {


    /**
     * Test of parse method, of class metadataImageParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("metadataImageParser.parse TIF");

        final String testFiles = "YaCyLogo_120ppi.tif";
        final String mimetype = "image/tiff";
        final String charset = null;

        final String filename = "test/parsertest/" + testFiles;
        final File file = new File(filename);

        final AnchorURL url = new AnchorURL("http://localhost/" + filename);
        System.out.println("parse file: " + filename);

        metadataImageParser p = new metadataImageParser();
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
