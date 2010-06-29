package de.anomic.document;

import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.*;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;


public class ParserTest {

	@Test public void testParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description, 
			new String[]{"umlaute_windows.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "", ""},
			new String[]{"umlaute_windows.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "Folie 1", "", ""},
			new String[]{"umlaute_linux.odt", "application/vnd.oasis.opendocument.text", "Münchner Hofbräuhaus", "", "Kommentar zum Hofbräuhaus"},
			new String[]{"umlaute_linux.ods", "application/vnd.oasis.opendocument.spreadsheat", "", "", ""},
			new String[]{"umlaute_linux.odp", "application/vnd.oasis.opendocument.presentation", "", "", ""},
			new String[]{"umlaute_linux.pdf", "application/pdf", "", "", ""},
			new String[]{"umlaute_windows.doc", "application/msword", "", "", ""},
		};


		for (int i=0; i < testFiles.length; i++) {
			String filename = "test/parsertest/" + testFiles[i][0];
			File file = new File(filename);
			String mimetype = testFiles[i][1];
			DigestURI url = new DigestURI("http://localhost/"+filename);

			Document[] docs = TextParser.parseSource(url, mimetype, null, file.length(), new FileInputStream(file));
			for (Document doc: docs) {
    			Reader content = new InputStreamReader(doc.getText(), doc.getCharset());
    			StringBuilder str = new StringBuilder();
    			int c;
    			while( (c = content.read()) != -1 )
    				str.append((char)c);
    
    			System.out.println("Parsed " + filename + ": " + str);
    			assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
    			assertThat(doc.dc_title(), containsString(testFiles[i][2]));
    			assertThat(doc.dc_creator(), containsString(testFiles[i][3]));
    			assertThat(doc.dc_description(), containsString(testFiles[i][4]));
			}			
		}
	}
}

