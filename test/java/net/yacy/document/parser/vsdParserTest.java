// vsdParserTest.java
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.Parser.Failure;

/**
 * Unit tests for the {@link vsdParser} class
 *
 * @author luccioman
 *
 */
public class vsdParserTest {

	/** Folder containing test files */
	private static final File TEST_FOLER = new File("test", "parsertest");

	/**
	 * Unit test for the vsdParser.parse() function with some small visio test
	 * files.
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
		final String[] fileNames = { "umlaute_windows.vsd", "umlaute_windows.vst" };
		/*
		 * Note : .vdx and .vtx sample files are not here as they are indeed XML based
		 * formats not supported by the current parser implementation
		 */
		final vsdParser parser = new vsdParser();

		for (final String fileName : fileNames) {
			final DigestURL location = new DigestURL("http://localhost/" + fileName);
			try (final FileInputStream inStream = new FileInputStream(new File(vsdParserTest.TEST_FOLER, fileName));) {
				final Document[] documents = parser.parse(location, "application/vnd.visio",
						StandardCharsets.UTF_8.name(), new VocabularyScraper(), 0, inStream);
				Assert.assertNotNull("Parser result must not be null for file " + fileName, documents);
				Assert.assertNotNull("Parsed text must not be empty for file " + fileName,
						documents[0].getTextString());
				Assert.assertTrue("Parsed text must contain test word with umlaut char for file " + fileName,
						documents[0].getTextString().contains("Maßkrügen"));
			}
		}
	}

}
