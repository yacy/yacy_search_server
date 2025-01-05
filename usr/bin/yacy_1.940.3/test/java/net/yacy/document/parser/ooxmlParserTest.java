// ooxmlParserTest.java
// ---------------------------
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;

import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;

/**
 * Unit tests for the {@link ooxmlParser} class
 * 
 * @author luccioman
 *
 */
public class ooxmlParserTest {

	/**
	 * Unit test for the ooxmlParser.parse() function with some small tests
	 * documents.
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParse() throws Exception {
		final String[][] testFiles = new String[][] {
				// meaning: filename in test/parsertest, mimetype, title, creator, description
				new String[] { "umlaute_windows.docx",
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
						"In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen", "", "" },
				new String[] { "umlaute_mac.docx",
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "", "", "" },
				new String[] { "umlaute_windows.pptx",
						"application/vnd.openxmlformats-officedocument.presentationml.presentation", "Folie 1", "",
						"" },
				new String[] { "umlaute_mac.pptx",
						"application/vnd.openxmlformats-officedocument.presentationml.presentation", "Slide 1", "",
						"" },
				new String[] { "umlaute_linux.ppsx",
						"application/vnd.openxmlformats-officedocument.presentationml.slideshow",
						"Office Open XML test slideshow from LibreOffice on Linux", "", "" },
				new String[] { "umlaute_mac.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
						"", "", "" },
				new String[] { "umlaute_windows.xlsx",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "", "", "" },
				new String[] { "umlaute_linux.xlsx",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
						"Office Open XML Spreadsheet test document from LibreOffice Calc on Linux", "",
						"Test spreadsheet document for YaCy ooxml parser" } };

		for (final String[] testFile : testFiles) {
			FileInputStream inStream = null;
			final String filename = testFile[0];
			try {
				final File file = new File("test" + File.separator + "parsertest" + File.separator + filename);
				final String mimetype = testFile[1];
				final AnchorURL url = new AnchorURL("http://localhost/" + filename);

				final AbstractParser p = new ooxmlParser();
				inStream = new FileInputStream(file);
				final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, inStream);
				for (final Document doc : docs) {
					Reader content = null;
					try {
						content = new InputStreamReader(doc.getTextStream(), doc.getCharset());
						final StringBuilder str = new StringBuilder();
						int c;
						while ((c = content.read()) != -1)
							str.append((char) c);

						System.out.println("Parsed " + filename + ": " + str);
						assertThat(str.toString(),
								containsString("In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen"));
						assertThat(doc.dc_title(), containsString(testFile[2]));
						assertThat(doc.dc_creator(), containsString(testFile[3]));
						if (testFile[4].length() > 0)
							assertThat(doc.dc_description()[0], containsString(testFile[4]));
					} finally {
						if (content != null) {
							try {
								content.close();
							} catch (final IOException ioe) {
								System.out.println("Could not close text input stream");
							}
						}
					}
				}
			} finally {
				if (inStream != null) {
					try {
						inStream.close();
					} catch (final IOException ioe) {
						System.out.println("Could not close input stream on file " + filename);
					}
				}
			}
		}
	}
	
	/**
	 * Test URLs detection on the ooxmlParser.parse() function.
	 * @throws Exception when an unexpected error occurred
	 */
	@Test
	public void testParseURLs() throws Exception {
		final String fileName = "umlaute_linux.xlsx";
		final File file = new File("test" + File.separator + "parsertest" + File.separator + fileName);
		final String mimetype = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		final AnchorURL url = new AnchorURL("http://localhost/" + fileName);

		final AbstractParser p = new ooxmlParser();
		try(InputStream inStream = new FileInputStream(file);) {
			final Document[] docs = p.parse(url, mimetype, null, new VocabularyScraper(), 0, inStream);
			assertNotNull("Documents result must not be null", docs);
			final Collection<AnchorURL> anchors = docs[0].getAnchors();
			assertNotNull("Detected URLs must not be null", anchors);
			assertEquals("2 URLs should be detected", 2, anchors.size());
			assertTrue("YaCy home page URL should have been parsed: " + anchors.toString(), anchors.contains(new AnchorURL("https://yacy.net/")));
			assertTrue("YaCy forum URL should have been parsed: " + anchors.toString(), anchors.contains(new AnchorURL("https://community.searchlab.eu/")));
		}
	}

}
