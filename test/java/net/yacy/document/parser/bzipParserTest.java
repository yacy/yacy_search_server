// bzipParserTest.java
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.document.VocabularyScraper;

/**
 * Unit tests for the {@link bzipParser} class
 * 
 * @author luccioman
 *
 */
public class bzipParserTest {
	
	/** Folder containing test files */
	private static final File TEST_FOLER = new File("test" + File.separator + "parsertest" + File.separator);

	/**
	 * Unit test for the bzipParser.parse() function with some small bzip2 test files.
	 * 
	 * @throws Failure
	 *             when a file could not be parsed
	 * @throws InterruptedException
	 *             when the test was interrupted before its termination
	 * @throws IOException
	 *             when a read/write error occurred
	 */
	@Test
	public void testParse() throws Failure, InterruptedException, IOException {
		final String[] fileNames = { "umlaute_html_utf8.html.bz2", "umlaute_linux.txt.bz2" };
		final bzipParser parser = new bzipParser();

		for (final String fileName : fileNames) {
			final DigestURL location = new DigestURL("http://localhost/" + fileName);
			try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLER, fileName));) {
				final Document[] documents = parser.parse(location, "application/x-bzip2", StandardCharsets.UTF_8.name(),
						new VocabularyScraper(), 0, inStream);
				assertNotNull("Parser result must not be null for file " + fileName, documents);
				assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
				assertTrue("Parsed text must contain test word with umlaut char" + fileName,
						documents[0].getTextString().contains("Maßkrügen"));
				final Collection<AnchorURL> anchors = documents[0].getAnchors();
				assertNotNull("Detected URLS must not be null for file " + fileName, anchors);
				assertEquals("One URL must have been detected for file " + fileName, 1, anchors.size());
				assertTrue(anchors.iterator().next().toString().startsWith("http://localhost/umlaute_"));
			}
		}
	}

	/**
	 * Testing parse integration with the tar parser on a test tbz2 archive.
	 * 
	 * @throws Failure
	 *             when a file could not be parsed
	 * @throws InterruptedException
	 *             when the test was interrupted before its termination
	 * @throws IOException
	 *             when a read/write error occurred
	 */
	@Test
	public void testParseTbz() throws Failure, InterruptedException, IOException {
		final String fileName = "umlaute_html_xml_txt_gnu.tbz2";
		final bzipParser parser = new bzipParser();

		
		final DigestURL location = new DigestURL("http://localhost/" + fileName);
		try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLER, fileName));) {
			final Document[] documents = parser.parse(location, "application/x-bzip2", StandardCharsets.UTF_8.name(),
					new VocabularyScraper(), 0, inStream);
			
			assertNotNull("Parser result must not be null for file " + fileName, documents);

			final String parsedText = documents[0].getTextString();
			assertNotNull("Parsed text must not be empty for file " + fileName, parsedText);
			assertTrue("Parsed text must contain test word with umlaut char in file " + fileName,
					parsedText.contains("Maßkrügen"));
			assertTrue(parsedText.contains("Example link in ISO-8859-1 encoded HTML"));
			assertTrue(parsedText.contains("Example link in UTF-8 encoded HTML"));
			assertTrue(parsedText.contains("URL reference in raw text file"));
			assertTrue(parsedText.contains("UTF-8 encoded XML test file"));

			final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
			assertNotNull(detectedAnchors);
			assertEquals("Parsed URLs must contains all URLs from each test file included in the archive", 5,
					detectedAnchors.size());
			assertTrue(detectedAnchors.contains(new AnchorURL("http://www.w3.org/1999/02/22-rdf-syntax-ns#")));
			assertTrue(detectedAnchors.contains(new AnchorURL("http://purl.org/dc/elements/1.1/")));
			assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_iso.html")));
			assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_utf8.html")));
			assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_linux.txt")));
		}
	}

	/**
	 * Unit test for the bzipParser.parseWithLimits() function with some small bz2
	 * test files which content is within limits.
	 * 
	 * @throws Failure
	 *             when a file could not be parsed
	 * @throws InterruptedException
	 *             when the test was interrupted before its termination
	 * @throws IOException
	 *             when a read/write error occurred
	 */
	@Test
	public void testParseWithLimits() throws Failure, InterruptedException, IOException {
		final String[] fileNames = { "umlaute_html_utf8.html.bz2", "umlaute_linux.txt.bz2" };
		final bzipParser parser = new bzipParser();

		for (final String fileName : fileNames) {
			final DigestURL location = new DigestURL("http://localhost/" + fileName);
			try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLER, fileName));) {
				final Document[] documents = parser.parseWithLimits(location, "application/x-bzip2",
						StandardCharsets.UTF_8.name(), new VocabularyScraper(), 0, inStream, 10000,
						10000);
				assertNotNull("Parser result must not be null for file " + fileName, documents);
				assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
				assertTrue("Parsed text must contain test word with umlaut char" + fileName,
						documents[0].getTextString().contains("Maßkrügen"));
				final Collection<AnchorURL> anchors = documents[0].getAnchors();
				assertNotNull("Detected URLs must not be null for file " + fileName, anchors);
				assertEquals("One URL must have been detected for file " + fileName, 1, anchors.size());
				assertTrue(anchors.iterator().next().toString().startsWith("http://localhost/umlaute_"));
				assertFalse("Parse document must not be marked as partially parsed for file " + fileName,
						documents[0].isPartiallyParsed());
			}
		}

	}
	
	/**
	 * Unit test for the bzipParser.parseWithLimits() when maxLinks limit is exceeded
	 * 
	 * @throws Failure
	 *             when a file could not be parsed
	 * @throws InterruptedException
	 *             when the test was interrupted before its termination
	 * @throws IOException
	 *             when a read/write error occurred
	 */
	@Test
	public void testParseWithLimitsLinksExceeded() throws Failure, InterruptedException, IOException {
		final String[] fileNames = { "umlaute_html_utf8.html.bz2", "umlaute_linux.txt.bz2" };
		final bzipParser parser = new bzipParser();

		/* maxLinks limit exceeded */
		for (final String fileName : fileNames) {
			final DigestURL location = new DigestURL("http://localhost/" + fileName);
			try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLER, fileName));) {
				final Document[] documents = parser.parseWithLimits(location, "application/x-bzip2",
						StandardCharsets.UTF_8.name(), new VocabularyScraper(), 0, inStream, 0, Long.MAX_VALUE);
				assertNotNull("Parser result must not be null for file " + fileName, documents);
				assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
				assertTrue("Parsed text must contain test word with umlaut char" + fileName,
						documents[0].getTextString().contains("Maßkrügen"));
				final Collection<AnchorURL> anchors = documents[0].getAnchors();
				assertTrue("Detected URLs must be empty for file " + fileName, anchors == null || anchors.isEmpty());
				assertTrue("Parsed document must be marked as partially parsed for file " + fileName,
						documents[0].isPartiallyParsed());
			}
		}
	}
	
	/**
	 * Unit test for the bzipParser.parseWithLimits() when maxBytes limit is exceeded
	 * 
	 * @throws Failure
	 *             when a file could not be parsed
	 * @throws InterruptedException
	 *             when the test was interrupted before its termination
	 * @throws IOException
	 *             when a read/write error occurred
	 */
	@Test
	public void testParseWithLimitsBytesExceeded() throws Failure, InterruptedException, IOException {
		final String[] fileNames = { "umlaute_html_utf8.html.bz2", "umlaute_linux.txt.bz2" };
		final bzipParser parser = new bzipParser();

		String fileName = fileNames[0];
		
		DigestURL location = new DigestURL("http://localhost/" + fileName);
		try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLER, fileName));) {
			/* The bytes limit is set to let parsing the beginning text part, but stop before reaching the <a> tag */
			final long maxBytes = 258;
			final Document[] documents = parser.parseWithLimits(location, "application/x-bzip2", StandardCharsets.UTF_8.name(),
					new VocabularyScraper(), 0, inStream, Integer.MAX_VALUE, maxBytes);
			assertNotNull("Parser result must not be null for file " + fileName, documents);
			assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
			assertTrue("Parsed text must contain test word with umlaut char" + fileName,
					documents[0].getTextString().contains("Maßkrügen"));
			final Collection<AnchorURL> anchors = documents[0].getAnchors();
			assertTrue("Detected URLs must be empty for file " + fileName, anchors == null || anchors.isEmpty());
			assertTrue("Parsed document must be marked as partially parsed for file " + fileName,
					documents[0].isPartiallyParsed());
		}

		fileName = fileNames[1];
		location = new DigestURL("http://localhost/" + fileName);
		try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLER, fileName));) {
			/* The bytes limit is set to let parsing the beginning of the text, but stop before reaching the URL */
			final long maxBytes = 65;
			final Document[] documents = parser.parseWithLimits(location, "application/x-bzip2", StandardCharsets.UTF_8.name(),
					new VocabularyScraper(), 0, inStream, Integer.MAX_VALUE, maxBytes);
			assertNotNull("Parser result must not be null for file " + fileName, documents);
			assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
			assertTrue("Parsed text must contain test word with umlaut char" + fileName,
					documents[0].getTextString().contains("Maßkrügen"));
			final Collection<AnchorURL> anchors = documents[0].getAnchors();
			assertTrue("Detected URLs must be empty for file " + fileName, anchors == null || anchors.isEmpty());
			assertTrue("Parsed document must be marked as partially parsed for file " + fileName,
					documents[0].isPartiallyParsed());
		}
	}

}
