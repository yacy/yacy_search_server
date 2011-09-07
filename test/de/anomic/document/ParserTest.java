package de.anomic.document;

import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;

import org.junit.Test;


public class ParserTest {

	@Test public void testParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_windows.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "", ""},
			new String[]{"umlaute_windows.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "Folie 1", "", ""},
			new String[]{"umlaute_linux.odt", "application/vnd.oasis.opendocument.text", "Münchner Hofbräuhaus", "", "Kommentar zum Hofbräuhaus"},
			new String[]{"umlaute_linux.ods", "application/vnd.oasis.opendocument.spreadsheat", "", "", ""},
			new String[]{"umlaute_linux.odp", "application/vnd.oasis.opendocument.presentation", "", "", ""},
			new String[]{"umlaute_linux.pdf", "application/pdf", "", "", ""},
			new String[]{"umlaute_windows.doc", "application/msword", "", "", ""},
		};


		for (final String[] testFile : testFiles) {
			final String filename = "test/parsertest/" + testFile[0];
			final File file = new File(filename);
			final String mimetype = testFile[1];
			final DigestURI url = new DigestURI("http://localhost/"+filename);

			final Document[] docs = TextParser.parseSource(url, mimetype, null, file.length(), new FileInputStream(file), true);
			for (final Document doc: docs) {
    			final Reader content = new InputStreamReader(doc.getText(), doc.getCharset());
    			final StringBuilder str = new StringBuilder();
    			int c;
    			while( (c = content.read()) != -1 )
    				str.append((char)c);

    			System.out.println("Parsed " + filename + ": " + str);
    			assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
    			assertThat(doc.dc_title(), containsString(testFile[2]));
    			assertThat(doc.dc_creator(), containsString(testFile[3]));
    			assertThat(doc.dc_description(), containsString(testFile[4]));
			}
		}
	}
}

