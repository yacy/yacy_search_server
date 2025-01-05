package net.yacy.document.parser;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;

public class xlsParserTest {

    /**
     * Test of parse method, of class xlsParser.
     */
    @Test
    public void testParse() throws Exception {
        final String[][] testFiles = new String[][]{
            // meaning:  filename in test/parsertest, mimetype, title, creator, description,
            new String[]{"umlaute_linux.xls", "application/msexcel", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "", ""},
            new String[]{"umlaute_mac.xls", "application/msexcel", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "", ""},
            new String[]{"umlaute_windows.xls", "application/msexcel", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "afieg", ""}

        };

        for (final String[] testFile : testFiles) {

            final String filename = "test/parsertest/" + testFile[0];
            final File file = new File(filename);
            final String mimetype = testFile[1];
            final AnchorURL url = new AnchorURL("http://localhost/" + filename);

            AbstractParser p = new xlsParser();
            FileInputStream inStream = null;
            try {
            	inStream = new FileInputStream(file);
            	final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, inStream);
                for (final Document doc : docs) {
                	Reader content = null;
                		try {
                			content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                			final StringBuilder str = new StringBuilder();
                			int c;
                			while ((c = content.read()) != -1) {
                				str.append((char) c);
                			}
                			
                			System.out.println("Parsed " + filename + ": " + str);
                			assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                			assertThat(doc.dc_creator(), containsString(testFile[3]));
                			if (testFile[4].length() > 0) {
                				assertThat(doc.dc_description()[0], containsString(testFile[4]));
                			}
                	} finally {
                    	if(content != null) {
                    		try {
                    			content.close();
                    		} catch(IOException ioe) {
                    			System.out.println("Could not close text input stream");
                    		}
                    	}
                	}
                }
            } finally {
            	if(inStream != null) {
            		try {
            			inStream.close();
            		} catch(IOException ioe) {
            			System.out.println("Could not close input stream on file " + filename);
            		}
            	}
            }
        }
    }

}
