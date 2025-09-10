// tarParserTest.java
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
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;

/**
 * Unit tests for the {@link tarParser} class
 * 
 * @author luccioman
 *
 */
public class tarParserTest {

	/** The test resources folder */
	private final static File TEST_FOLDER = new File("test" + File.separator + "parsertest" + File.separator);

	/**
	 * All these test archives include two html test files in a sub folder, then a
	 * xml and a text test files at the root
	 */
	private static final String[] TAR_FILE_NAMES = { "umlaute_html_xml_txt_gnu.tar", // created with tar option
																						// --format=gnu
			"umlaute_html_xml_txt_pax.tar", // created with tar option --format=pax
			"umlaute_html_xml_txt_ustar.tar", // created with tar option --format=ustar
			"umlaute_html_xml_txt_v7.tar", // created with tar option --format=v7
	};

	/** Tar parser test instance */
	private tarParser parser;

	@Before
	public void setUp() {
		this.parser = new tarParser();
	}

	/**
	 * Unit test for the tarParser.parse() implementation with some test archives in
	 * various common tar formats.
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParse() throws Exception {

		for (String fileName : TAR_FILE_NAMES) {
			FileInputStream inStream = new FileInputStream(new File(TEST_FOLDER, fileName));
			DigestURL location = new DigestURL("http://localhost/" + fileName);
			try {
				Document[] documents = this.parser.parse(location, "application/tar", null, new VocabularyScraper(), 0,
						inStream);
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
			} finally {
				inStream.close();
			}
		}
	}

	/**
	 * Test tarParser.parseWithLimits() with limits not reached.
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseWithLimitsNotReached() throws Exception {
		for (String fileName : TAR_FILE_NAMES) {

			FileInputStream inStream = new FileInputStream(new File(TEST_FOLDER, fileName));
			DigestURL location = new DigestURL("http://localhost/" + fileName);
			/* Content within limits */
			try {
				Document[] documents = this.parser.parseWithLimits(location, "application/tar", null,
						new VocabularyScraper(), 0, inStream, Integer.MAX_VALUE, Long.MAX_VALUE);
				assertNotNull("Parser result must not be null for file " + fileName, documents);

				final String parsedText = documents[0].getTextString();
				assertNotNull("Parsed text must not be empty for file " + fileName, parsedText);
				assertTrue("Parsed text must contain test word with umlaut char in file " + fileName,
						parsedText.contains("Maßkrügen"));
				assertTrue(parsedText.contains("Example link in ISO-8859-1 encoded HTML"));
				assertTrue(parsedText.contains("Example link in UTF-8 encoded HTML"));
				assertTrue(parsedText.contains("UTF-8 encoded XML test file"));
				assertTrue(parsedText.contains("URL reference in raw text file"));

				final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
				assertNotNull(detectedAnchors);
				assertEquals("Parsed URLs must contain all URLs from each test file included in the archive", 5,
						detectedAnchors.size());
				assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_iso.html")));
				assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_utf8.html")));
				assertTrue(detectedAnchors.contains(new AnchorURL("http://www.w3.org/1999/02/22-rdf-syntax-ns#")));
				assertTrue(detectedAnchors.contains(new AnchorURL("http://purl.org/dc/elements/1.1/")));
				assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_linux.txt")));
			} finally {
				inStream.close();
			}
		}
	}

	/**
	 * Test tarParser.parseWithLimits() with links limit exceeded
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseWithLimitsLinksExceeded() throws Exception {
		for (String fileName : TAR_FILE_NAMES) {

			FileInputStream inStream = new FileInputStream(new File(TEST_FOLDER, fileName));
			DigestURL location = new DigestURL("http://localhost/" + fileName);

			/* Links limit exceeded from the third included file */
			try {
				Document[] documents = this.parser.parseWithLimits(location, "application/tar", null,
						new VocabularyScraper(), 0, inStream, 2, Long.MAX_VALUE);
				assertNotNull("Parser result must not be null for file " + fileName, documents);

				final String parsedText = documents[0].getTextString();
				assertNotNull("Parsed text must not be empty for file " + fileName, parsedText);
				assertTrue("Parsed text must contain test word with umlaut char in file " + fileName,
						parsedText.contains("Maßkrügen"));
				assertTrue(parsedText.contains("Example link in ISO-8859-1 encoded HTML"));
				assertTrue(parsedText.contains("Example link in UTF-8 encoded HTML"));
				assertFalse(parsedText.contains("UTF-8 encoded XML test file"));
				assertFalse(parsedText.contains("URL reference in raw text file"));

				final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
				assertNotNull(detectedAnchors);
				assertEquals("Parsed URLs must only contain URLs from test files withing links limit", 2,
						detectedAnchors.size());
				assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_iso.html")));
				assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_utf8.html")));
				assertFalse(detectedAnchors.contains(new AnchorURL("http://www.w3.org/1999/02/22-rdf-syntax-ns#")));
				assertFalse(detectedAnchors.contains(new AnchorURL("http://purl.org/dc/elements/1.1/")));
				assertFalse(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_linux.txt")));
			} finally {
				inStream.close();
			}
		}
	}

	/**
	 * Test tarParser.parseWithLimits() with bytes limit exceeded
	 * 
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseWithLimitsBytesExceeded() throws Exception {
		for (String fileName : TAR_FILE_NAMES) {

			FileInputStream inStream = new FileInputStream(new File(TEST_FOLDER, fileName));
			DigestURL location = new DigestURL("http://localhost/" + fileName);

			/* Bytes limit exceeded from the third included file. */
			final long maxBytes;
			if ("umlaute_html_xml_txt_pax.tar".equals(fileName)) {
				/* pax tar format uses more bytes for extended headers */
				maxBytes = 7000;
			} else {
				/*
				 * Limit calculation : five 512 bytes tar records = 512 bytes tar header for the
				 * html directory + (2 x (512 bytes tar header + html file content below 512
				 * bytes, thus rounded to 512))
				 */
				maxBytes = 512 * 5;
			}
			try {
				Document[] documents = this.parser.parseWithLimits(location, "application/tar", null,
						new VocabularyScraper(), 0, inStream, Integer.MAX_VALUE, maxBytes);
				assertNotNull("Parser result must not be null for file " + fileName, documents);

				final String parsedText = documents[0].getTextString();
				assertNotNull("Parsed text must not be empty for file " + fileName, parsedText);
				assertTrue("Parsed text must contain test word with umlaut char in file " + fileName,
						parsedText.contains("Maßkrügen"));
				assertTrue(parsedText.contains("Example link in ISO-8859-1 encoded HTML"));
				assertTrue(parsedText.contains("Example link in UTF-8 encoded HTML"));
				assertFalse(parsedText.contains("URL reference in raw text file"));
				assertFalse(parsedText.contains("UTF-8 encoded XML test file"));

				final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
				assertNotNull(detectedAnchors);
				assertEquals("Parsed URLs must only contain URLs from test files withing bytes limit", 2,
						detectedAnchors.size());
				assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_iso.html")));
				assertTrue(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_html_utf8.html")));
				assertFalse(detectedAnchors.contains(new AnchorURL("http://www.w3.org/1999/02/22-rdf-syntax-ns#")));
				assertFalse(detectedAnchors.contains(new AnchorURL("http://purl.org/dc/elements/1.1/")));
				assertFalse(detectedAnchors.contains(new AnchorURL("http://localhost/umlaute_linux.txt")));
			} finally {
				inStream.close();
			}
		}
	}
}
