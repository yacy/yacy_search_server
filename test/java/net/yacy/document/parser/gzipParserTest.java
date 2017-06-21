// gzipParserTest.java
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

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.document.VocabularyScraper;

/**
 * Unit tests for the {@link gzipParser} class
 * 
 * @author luccioman
 *
 */
public class gzipParserTest {

	/**
	 * Unit test for the gzipParser.parse() function with some small gz test files.
	 * @throws Failure when a file could not be parsed
	 * @throws InterruptedException when the test was interrupted before its termination
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testParse() throws Failure, InterruptedException, IOException {
		final String[] fileNames = {
				"umlaute_html_utf8.html.gz",
				"umlaute_linux.txt.gz"
		};
		final File folder = new File("test" + File.separator + "parsertest" + File.separator);
		gzipParser parser = new gzipParser();
		
		for (String fileName : fileNames) {
			FileInputStream inStream = new FileInputStream(new File(folder, fileName));
			DigestURL location = new DigestURL("http://localhost/" + fileName);
			try {
				Document[] documents = parser.parse(location, "application/gzip", null, new VocabularyScraper(), 0,
						inStream);
				assertNotNull("Parser result must not be null for file " + fileName, documents);
				assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
				assertTrue("Parsed text must contain test word with umlaut char" + fileName, documents[0].getTextString().contains("Maßkrügen"));
			} finally {
				inStream.close();
			}
		}
	}

}
