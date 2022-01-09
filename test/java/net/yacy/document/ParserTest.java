package net.yacy.document;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.parser.docParser;
import net.yacy.document.parser.odtParser;
import net.yacy.document.parser.pdfParser;
import net.yacy.document.parser.pptParser;


public class ParserTest {

        	@Test public void testodtParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_linux.odt", "application/vnd.oasis.opendocument.text", "Münchner Hofbräuhaus", "", "Kommentar zum Hofbräuhaus"},
			new String[]{"umlaute_linux.ods", "application/vnd.oasis.opendocument.spreadsheat", "", "", ""},
			new String[]{"umlaute_linux.odp", "application/vnd.oasis.opendocument.presentation", "", "", ""},
		};

		for (final String[] testFile : testFiles) {
					FileInputStream inStream = null;
                    final String filename = "test/parsertest/" + testFile[0];
                    try {
                        final File file = new File(filename);
                        final String mimetype = testFile[1];
                        final AnchorURL url = new AnchorURL("http://localhost/"+filename);

                        AbstractParser p = new odtParser();
                        inStream = new FileInputStream(file);
                        final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, inStream);
                        for (final Document doc: docs) {
                        	Reader content = null;
                        	try {
                        		content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                        		final StringBuilder str = new StringBuilder();
                        		int c;
                        		while( (c = content.read()) != -1 )
                                    str.append((char)c);

                        		System.out.println("Parsed " + filename + ": " + str);
                        		assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                        		assertThat(doc.dc_title(), containsString(testFile[2]));
                        		assertThat(doc.dc_creator(), containsString(testFile[3]));
                        		if (testFile[4].length() > 0) assertThat(doc.dc_description()[0], containsString(testFile[4]));
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
                    } catch (final InterruptedException ex) {
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

        	@Test public void testpdfParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
                        // meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_linux.pdf", "application/pdf", "", "", ""},
		};

		for (final String[] testFile : testFiles) {
            		final String filename = "test/parsertest/" + testFile[0];
					FileInputStream inStream = null;
                    try {
                        final File file = new File(filename);
                        final String mimetype = testFile[1];
                        final AnchorURL url = new AnchorURL("http://localhost/"+filename);

                        AbstractParser p = new pdfParser();
                        inStream = new FileInputStream(file);
                        final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, inStream);
                        for (final Document doc: docs) {
                        	Reader content = null;
                        	try {
                        		content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                        		final StringBuilder str = new StringBuilder();
                        		int c;
                        		while( (c = content.read()) != -1 )
                                    str.append((char)c);
                             
                        		System.out.println("Parsed " + filename + ": " + str);
                        		assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                        		assertThat(doc.dc_title(), containsString(testFile[2]));
                        		assertThat(doc.dc_creator(), containsString(testFile[3]));
                        		if (testFile[4].length() > 0) assertThat(doc.dc_description()[0], containsString(testFile[4]));
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
                    } catch (final InterruptedException ex) {
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
                
        	@Test public void testdocParsers() throws FileNotFoundException, Parser.Failure, MalformedURLException, UnsupportedEncodingException, IOException	{
		final String[][] testFiles = new String[][] {
			// meaning:  filename in test/parsertest, mimetype, title, creator, description,
			new String[]{"umlaute_windows.doc", "application/msword", "", "", ""},
		};

		for (final String[] testFile : testFiles) {
            		final String filename = "test/parsertest/" + testFile[0];
					FileInputStream inStream = null;
                    try {
                        final File file = new File(filename);
                        final String mimetype = testFile[1];
                        final AnchorURL url = new AnchorURL("http://localhost/"+filename);

                        AbstractParser p = new docParser();
                        inStream = new FileInputStream(file);
                        final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, inStream);
                        for (final Document doc: docs) {
                            Reader content = null;
                            try {
                            	content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
                            	final StringBuilder str = new StringBuilder();
                            	int c;
                            	while( (c = content.read()) != -1 )
                                    str.append((char)c);

                            	System.out.println("Parsed " + filename + ": " + str);
                            	assertThat(str.toString(), containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
                            	assertThat(doc.dc_title(), containsString(testFile[2]));
                            	assertThat(doc.dc_creator(), containsString(testFile[3]));
                            	if (testFile[4].length() > 0) assertThat(doc.dc_description()[0], containsString(testFile[4]));
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
                    } catch (final InterruptedException ex) {
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
            			assertThat(doc.dc_title(), containsString(testFile[2]));
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
