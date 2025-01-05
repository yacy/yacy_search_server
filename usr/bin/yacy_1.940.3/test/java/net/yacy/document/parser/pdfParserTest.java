package net.yacy.document.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import static junit.framework.TestCase.assertEquals;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;
import org.junit.Test;

public class pdfParserTest {

    /**
     * Test extraction of links in parse method, of class pdfParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("pdfParser.parse");

        final String testFiles = "umlaute_linux.pdf";
        final String mimetype = "application/pdf";
        final String charset = null;

        //final String resulttxt = "In München steht ein Hofbräuhaus. Dort gibt es Bier aus Maßkrügen.";
        final String filename = "test/parsertest/" + testFiles;
        final File file = new File(filename);

        final AnchorURL url = new AnchorURL("http://localhost/" + filename);
        System.out.println("parse file: " + filename);

        pdfParser p = new pdfParser();
        FileInputStream inStream = new FileInputStream(file);
        try {
        	final Document[] docs = p.parse(url, mimetype, charset, new VocabularyScraper(), 0, inStream);

        	Document doc = docs[0];
        	int ilinks = doc.getAnchors().size();
        	assertEquals("number of links in pdf", 1, ilinks);
        
        	Collection<AnchorURL> links = doc.getAnchors();
        	System.out.println("number of links detected = " + ilinks);
        	for (AnchorURL aurl : links) {
        		System.out.println("   found: " + aurl.toString());
        	}
        } finally {
        	try {
        		inStream.close();
        	} catch(IOException ioe) {
        		System.out.println("Could not close input stream on file " + file);
        	}
        }

    }

}
