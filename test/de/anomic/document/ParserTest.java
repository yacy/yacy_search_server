package de.anomic.document;

import static org.junit.Assert.*;
import org.junit.Test;
import static org.junit.matchers.JUnitMatchers.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.InputStreamReader;

import de.anomic.document.Document;
import de.anomic.document.Parser;
import de.anomic.yacy.yacyURL;

public class ParserTest {

	@Test public void testParsers() throws java.io.FileNotFoundException, java.lang.InterruptedException,
		de.anomic.document.ParserException, java.net.MalformedURLException,
	       java.io.UnsupportedEncodingException, java.io.IOException	{
		String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description, 
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
			yacyURL url = new yacyURL("http://localhost/"+filename);

			Document doc = Parser.parseSource(url, mimetype, null, file.length(), new FileInputStream(file));
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

