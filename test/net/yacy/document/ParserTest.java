package net.yacy.document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.parser.docParser;
import net.yacy.document.parser.odtParser;
import net.yacy.document.parser.ooxmlParser;
import net.yacy.document.parser.pdfParser;
import net.yacy.document.parser.pptParser;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import org.junit.Test;


public class ParserTest {

	@Test public void testooxmlParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_windows.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "", ""},
			new String[]{"umlaute_windows.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "Folie 1", "", ""},
		};

		for (final String[] testFile : testFiles) {
                    try {
                        final String filename = "test/parsertest/" + testFile[0];
                        final File file = new File(filename);
                        final String mimetype = testFile[1];
                        final AnchorURL url = new AnchorURL("http://localhost/"+filename);

                        AbstractParser p = new ooxmlParser();
                        final Document[] docs = p.parse(url, mimetype, null, new FileInputStream(file));
                        for (final Document doc: docs) {
                            final Reader content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                            final StringBuilder str = new StringBuilder();
                            int c;
                            while( (c = content.read()) != -1 )
                                    str.append((char)c);

                            System.out.println("Parsed " + filename + ": " + str);
                            assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                            assertThat(doc.dc_title(), containsString(testFile[2]));
                            assertThat(doc.dc_creator(), containsString(testFile[3]));
                            if (testFile[4].length() > 0) assertThat(doc.dc_description()[0], containsString(testFile[4]));
                        }
                    } catch (final InterruptedException ex) {}
                    }
		}
        
        	@Test public void testodtParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_linux.odt", "application/vnd.oasis.opendocument.text", "Münchner Hofbräuhaus", "", "Kommentar zum Hofbräuhaus"},
			new String[]{"umlaute_linux.ods", "application/vnd.oasis.opendocument.spreadsheat", "", "", ""},
			new String[]{"umlaute_linux.odp", "application/vnd.oasis.opendocument.presentation", "", "", ""},
		};

		for (final String[] testFile : testFiles) {
                    try {
                        final String filename = "test/parsertest/" + testFile[0];
                        final File file = new File(filename);
                        final String mimetype = testFile[1];
                        final AnchorURL url = new AnchorURL("http://localhost/"+filename);

                        AbstractParser p = new odtParser();
                        final Document[] docs = p.parse(url, mimetype, null, new FileInputStream(file));
                        for (final Document doc: docs) {
                            final Reader content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                            final StringBuilder str = new StringBuilder();
                            int c;
                            while( (c = content.read()) != -1 )
                                    str.append((char)c);

                            System.out.println("Parsed " + filename + ": " + str);
                            assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                            assertThat(doc.dc_title(), containsString(testFile[2]));
                            assertThat(doc.dc_creator(), containsString(testFile[3]));
                            if (testFile[4].length() > 0) assertThat(doc.dc_description()[0], containsString(testFile[4]));
                        }
                    } catch (final InterruptedException ex) {}
                    }
		}

        	@Test public void testpdfParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
                        // meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_linux.pdf", "application/pdf", "", "", ""},
		};

		for (final String[] testFile : testFiles) {
                    try {
                        final String filename = "test/parsertest/" + testFile[0];
                        final File file = new File(filename);
                        final String mimetype = testFile[1];
                        final AnchorURL url = new AnchorURL("http://localhost/"+filename);

                        AbstractParser p = new pdfParser();
                        final Document[] docs = p.parse(url, mimetype, null, new FileInputStream(file));
                        for (final Document doc: docs) {
                            final Reader content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                            final StringBuilder str = new StringBuilder();
                            int c;
                            while( (c = content.read()) != -1 )
                                    str.append((char)c);
                             
                            System.out.println("Parsed " + filename + ": " + str);
                            assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                            assertThat(doc.dc_title(), containsString(testFile[2]));
                            assertThat(doc.dc_creator(), containsString(testFile[3]));
                            if (testFile[4].length() > 0) assertThat(doc.dc_description()[0], containsString(testFile[4]));
                        }
                    } catch (final InterruptedException ex) {}
                    }
		}                
                
        	@Test public void testdocParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_windows.doc", "application/msword", "", "", ""},
		};

		for (final String[] testFile : testFiles) {
                    try {
                        final String filename = "test/parsertest/" + testFile[0];
                        final File file = new File(filename);
                        final String mimetype = testFile[1];
                        final AnchorURL url = new AnchorURL("http://localhost/"+filename);

                        AbstractParser p = new docParser();
                        final Document[] docs = p.parse(url, mimetype, null, new FileInputStream(file));
                        for (final Document doc: docs) {
                            final Reader content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                            final StringBuilder str = new StringBuilder();
                            int c;
                            while( (c = content.read()) != -1 )
                                    str.append((char)c);

                            System.out.println("Parsed " + filename + ": " + str);
                            assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                            assertThat(doc.dc_title(), containsString(testFile[2]));
                            assertThat(doc.dc_creator(), containsString(testFile[3]));
                            if (testFile[4].length() > 0) assertThat(doc.dc_description()[0], containsString(testFile[4]));
                        }
                    } catch (final InterruptedException ex) {}
                    }
		}

    /**
     * Powerpoint parser test *
     */
    @Test
    public void testpptParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException, InterruptedException {
        final String[][] testFiles = new String[][]{
            // meaning:  filename in test/parsertest, mimetype, title, creator, description,
            new String[]{"umlaute_linux.ppt", "application/powerpoint", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "", ""},
            new String[]{"umlaute_windows.ppt", "application/powerpoint", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "afieg", ""},
            new String[]{"umlaute_mac.ppt", "application/powerpoint", "In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "Bob", ""}
        };

        for (final String[] testFile : testFiles) {

            final String filename = "test/parsertest/" + testFile[0];
            final File file = new File(filename);
            final String mimetype = testFile[1];
            final AnchorURL url = new AnchorURL("http://localhost/" + filename);

            AbstractParser p = new pptParser();
            final Document[] docs = p.parse(url, mimetype, null, new FileInputStream(file));
            for (final Document doc : docs) {
                final Reader content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                final StringBuilder str = new StringBuilder();
                int c;
                while ((c = content.read()) != -1) {
                    str.append((char) c);
                }

                System.out.println("Parsed " + filename + ": " + str);
                assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                assertThat(doc.dc_title(), containsString(testFile[2]));
                assertThat(doc.dc_creator(), containsString(testFile[3]));
                if (testFile[4].length() > 0) {
                    assertThat(doc.dc_description()[0], containsString(testFile[4]));
                }
            }
        }
    }
	}
