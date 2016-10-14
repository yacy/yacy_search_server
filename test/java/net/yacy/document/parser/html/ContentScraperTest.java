/**
 *  ContentScraperTest
 *  part of YaCy
 *  Copyright 2016 by luccioman; https://github.com/luccioman
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.parser.html;

import java.awt.Dimension;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for ContentScrapper class.
 * @author luc
 *
 */
public class ContentScraperTest {
	
	@Test
	public final void testParseSizes() {
		/* Normal case */
		Set<Dimension> sizes = ContentScraper.parseSizes("96x128");
		Assert.assertEquals(1, sizes.size());
		Assert.assertTrue(sizes.contains(new Dimension(96, 128)));
		
		/* "any" keyword */
		sizes = ContentScraper.parseSizes("any");
		Assert.assertEquals(0, sizes.size());
		
		/* Multiple valid sizes, lower and upper case separator */
		sizes = ContentScraper.parseSizes("96x128 16X16 1X2 1024x768");
		Assert.assertEquals(4, sizes.size());
		Assert.assertTrue(sizes.contains(new Dimension(96, 128)));
		Assert.assertTrue(sizes.contains(new Dimension(16, 16)));
		Assert.assertTrue(sizes.contains(new Dimension(1, 2)));
		Assert.assertTrue(sizes.contains(new Dimension(1024, 768)));
		
		/* Duplicate entries */
		sizes = ContentScraper.parseSizes("96x128 96X128 1X2 96x128");
		Assert.assertEquals(2, sizes.size());
		Assert.assertTrue(sizes.contains(new Dimension(96, 128)));
		Assert.assertTrue(sizes.contains(new Dimension(1, 2)));
		
		/* Mutiple inner and trailing spaces */
		sizes = ContentScraper.parseSizes("  96x128  16X16  ");
		Assert.assertEquals(2, sizes.size());
		Assert.assertTrue(sizes.contains(new Dimension(96, 128)));
		Assert.assertTrue(sizes.contains(new Dimension(16, 16)));
		
		/* Empty string */
		sizes = ContentScraper.parseSizes("");
		Assert.assertEquals(0, sizes.size());
		
		/* null string */
		sizes = ContentScraper.parseSizes(null);
		Assert.assertEquals(0, sizes.size());
		
		/* Invalid sizes */
		sizes = ContentScraper.parseSizes("096x0128 -16x-16 0x0 x768 78x axb 1242");
		Assert.assertEquals(0, sizes.size());
		
		/* Mix of valid and invalid sizes */
		sizes = ContentScraper.parseSizes("96x128 16X16 axb 123 78x32");
		Assert.assertEquals(3, sizes.size());
		Assert.assertTrue(sizes.contains(new Dimension(96, 128)));
		Assert.assertTrue(sizes.contains(new Dimension(16, 16)));
		Assert.assertTrue(sizes.contains(new Dimension(78, 32)));
	}

	@Test
	public final void testParseSpaceSeparatedTokens() {
		/* Normal case */
		Set<String> tokens = ContentScraper.parseSpaceSeparatedTokens("abc de");
		Assert.assertEquals(2, tokens.size());
		Assert.assertTrue(tokens.contains("abc"));
		Assert.assertTrue(tokens.contains("de"));
		
		/* One item only */
		tokens = ContentScraper.parseSpaceSeparatedTokens("abc");
		Assert.assertEquals(1, tokens.size());
		Assert.assertTrue(tokens.contains("abc"));
		
		/* Mutiple inner and trailing spaces */
		tokens = ContentScraper.parseSpaceSeparatedTokens("  abc  d efff    fgj  ");
		Assert.assertEquals(4, tokens.size());
		Assert.assertTrue(tokens.contains("abc"));
		Assert.assertTrue(tokens.contains("d"));
		Assert.assertTrue(tokens.contains("efff"));
		Assert.assertTrue(tokens.contains("fgj"));
		
		/* Duplicate entries */
		tokens = ContentScraper.parseSpaceSeparatedTokens("abc bb abc abc ABC");
		Assert.assertEquals(3, tokens.size());
		Assert.assertTrue(tokens.contains("abc"));
		/* ignoring case is not the purpose of this function */
		Assert.assertTrue(tokens.contains("ABC"));
		Assert.assertTrue(tokens.contains("bb"));
		
		/* Empty string */
		tokens = ContentScraper.parseSpaceSeparatedTokens("");
		Assert.assertEquals(0, tokens.size());
		
		/* Null string */
		tokens = ContentScraper.parseSpaceSeparatedTokens(null);
		Assert.assertEquals(0, tokens.size());
	}

}
