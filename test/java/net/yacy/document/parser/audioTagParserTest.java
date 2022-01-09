// audioTagParserTest.java
// ---------------------------
// Copyright 2019 by luccioman; https://github.com/luccioman
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.document.VocabularyScraper;

/**
 * Unit tests for the {@link audioTagParser} class
 * 
 */
public class audioTagParserTest {

	/** Folder containing test files */
	private static final File TEST_FOLDER = new File("test", "parsertest");

	/** The parser under test */
	private audioTagParser parser;

	@Before
	public void before() {
		this.parser = new audioTagParser();
	}

	/**
	 * Unit test for the
	 * {@link audioTagParser#parse(DigestURL, String, String, VocabularyScraper, int, java.io.InputStream)}
	 * function with some small (1 second length) test files.
	 * 
	 * @throws Failure              when a file could not be parsed
	 * @throws InterruptedException when the test was interrupted before its
	 *                              termination
	 * @throws IOException          when a read/write error occurred
	 */
	@Test
	public void testParse() throws Failure, InterruptedException, IOException {
		final String[] fileNames = { "umlaute_windows.aiff", "umlaute_windows.flac", "umlaute_windows.m4a",
				"umlaute_windows.mp3", "umlaute_windows.ogg", "umlaute_windows.wav" };

		for (final String fileName : fileNames) {
			final DigestURL location = new DigestURL("http://localhost/" + fileName);
			try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLDER, fileName));) {
				final Document[] documents = this.parser.parse(location, "audio/ogg", StandardCharsets.UTF_8.name(),
						new VocabularyScraper(), 0, inStream);
				assertNotNull("Parser result must not be null for file " + fileName, documents);
				assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
				assertTrue("Parsed text must contain test word with umlaut char" + fileName,
						documents[0].getTextString().contains("Maßkrügen"));
				final Collection<AnchorURL> anchors = documents[0].getAnchors();
				assertNotNull("Detected URLS must not be null for file " + fileName, anchors);
				assertEquals("One URL must have been detected for file " + fileName, 1, anchors.size());
				assertTrue(anchors.iterator().next().toString().equals("https://yacy.net/"));
			}
		}
	}

	/**
	 * Test support for parsing audio document with proper Media Type but without
	 * extension or unrelated extension in its file name.
	 * 
	 * @throws Failure              when the file could not be parsed
	 * @throws InterruptedException when the test was interrupted before its
	 *                              termination
	 * @throws IOException          when a read/write error occurred
	 */
	@Test
	public void testParseDocUrlWithoutFileExt() throws Failure, InterruptedException, IOException {
		final String testFileName = "umlaute_windows.ogg";
		final String[] locations = { "http://localhost/audioTrack", "http://localhost/example.audio" };

		for (final String locationStr : locations) {
			final DigestURL location = new DigestURL(locationStr);
			try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLDER, testFileName));) {
				final Document[] documents = this.parser.parse(location, "audio/ogg", StandardCharsets.UTF_8.name(),
						new VocabularyScraper(), 0, inStream);
				assertNotNull("Parser result must not be null for URL " + location, documents);
			}
		}

	}

	/**
	 * Test support for parsing audio document with unknown or generic Media Type
	 * 
	 * @throws Failure              when the file could not be parsed
	 * @throws InterruptedException when the test was interrupted before its
	 *                              termination
	 * @throws IOException          when a read/write error occurred
	 */
	@Test
	public void testParseUnkownMediaType() throws Failure, InterruptedException, IOException {
		final String testFileName = "umlaute_windows.ogg";
		final DigestURL location = new DigestURL("http://localhost/" + testFileName);
		final String[] mediaTypes = { null, "application/octet-stream" };

		for (final String mediaType : mediaTypes) {
			try (final FileInputStream inStream = new FileInputStream(new File(TEST_FOLDER, testFileName));) {
				final Document[] documents = this.parser.parse(location, mediaType, StandardCharsets.UTF_8.name(),
						new VocabularyScraper(), 0, inStream);
				assertNotNull("Parser result must not be null for Media Type " + mediaType, documents);
			}
		}

	}

}
