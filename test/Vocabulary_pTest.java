
// Vocabulary_pTest.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.lod.vocabulary.Tagging;

/**
 * Unit tests for the {@link Vocabulary_p} class.
 */
public class Vocabulary_pTest {

	/**
	 * Unit test for the discoverFromCSVReader() function.
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testDiscoverFromCSVReader() throws IOException {
		final Map<String, Tagging.SOTuple> table = new LinkedHashMap<String, Tagging.SOTuple>();
		final Pattern separatorPattern = Pattern.compile(",");
		final String escapeChar = "\"";
		final int lineStart = 1;
		final int literalsColumn = 1;
		final int synonymsColumn = -1;
		final int objectLinkColumn = -1;
		final boolean enrichSynonyms = false;
		final boolean readSynonymFromColumn = false;
		
		/* Test content with typical cases */
		final StringBuilder csvBuilder = new StringBuilder();
		csvBuilder.append("title1,title2,title3\n"); // header line
	    csvBuilder.append("\"aaa\",\"b bb\",\"ccc\"\n"); // all fields enclosed in double quotes 
	    csvBuilder.append("zzz,yyy,xxx\n"); // no double quotes around fields
	    csvBuilder.append(",,\n"); // empty fields
	    csvBuilder.append("\"\",\"\",\"\"\n"); // quoted empty fields
	    csvBuilder.append("\"111\",\"2\"\"2'2\",\"333\"\n"); // escaped double quote
	    csvBuilder.append("key,quo\"te,desc\n"); // unescaped double quote inside a field value
	    csvBuilder.append("fff,malformed\n"); // malformed line : one field is missing 
	    csvBuilder.append("\"f,f,f\",\"foo,bar\",\",,\"\n"); // escaped separators
	    csvBuilder.append("\"m\nm\nmm\",\"multi\nline\",\"\n\n\"\n"); // escaped line breaks

		try (final BufferedReader reader = new BufferedReader(new StringReader(csvBuilder.toString()));) {

			Vocabulary_p.discoverFromCSVReader(table, escapeChar, lineStart, literalsColumn, synonymsColumn,
					objectLinkColumn, enrichSynonyms, readSynonymFromColumn, separatorPattern, reader);
			Assert.assertEquals(7, table.size());
			Assert.assertTrue(table.containsKey("b bb"));
			Assert.assertTrue(table.containsKey("yyy"));
			Assert.assertTrue(table.containsKey("2\"2'2"));
			Assert.assertTrue(table.containsKey("quo\"te"));
			Assert.assertTrue(table.containsKey("malformed"));
			Assert.assertTrue(table.containsKey("foo,bar"));
			Assert.assertTrue(table.containsKey("multi\nline"));
		}
		
		/* Empty content */
		table.clear();
		try (final BufferedReader reader = new BufferedReader(new StringReader(""));) {

			Vocabulary_p.discoverFromCSVReader(table, escapeChar, lineStart, literalsColumn, synonymsColumn,
					objectLinkColumn, enrichSynonyms, readSynonymFromColumn, separatorPattern, reader);
			Assert.assertEquals(0, table.size());
		}
	}

}
