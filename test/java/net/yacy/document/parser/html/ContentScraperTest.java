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
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.VocabularyScraper;
import net.yacy.kelondro.util.FileUtils;

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

    @Test
    public void testGetStartDates() throws MalformedURLException, IOException {
        List<Date> dateResultList;
        DigestURL root = new DigestURL("http://test.org/test.html");

        String page = "<html><body>"
                + "<time datetime='2016-12-23'>23. Dezember 2016</time>" // html5 time tag
                + "</body></html>";

        ContentScraper scraper = new ContentScraper(root, 10, new VocabularyScraper(), 0);
        final Writer writer = new TransformerWriter(null, null, scraper, null, false);

        FileUtils.copy(new StringReader(page), writer);
        writer.close();

        dateResultList = scraper.getStartDates();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(0); // to zero hours
        cal.set(2016, Calendar.DECEMBER, 23);

        for (Date d : dateResultList) {
            Assert.assertEquals(cal.getTime(), d);
        }
        scraper.close();
    }
    
    /**
     * Test absolute URLs detection in plain text
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testFindAbsoluteURLs() throws MalformedURLException {
		final String[] urlStrings = { "http://yacy.net", "http://forum.yacy.de", "https://en.wikipedia.org" };
		final List<AnchorURL> urls = new ArrayList<>();
		for (String urlString : urlStrings) {
			urls.add(new AnchorURL(urlString));
		}

		/* Test with various white space separators */
		String[] separators = { " ", "\n", "\t", "\r" };
		for (String separator : separators) {
			StringBuilder text = new StringBuilder();
			for (String urlString : urlStrings) {
				if (text.length() > 0) {
					text.append(separator);
				}
				text.append(urlString);
			}
			Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text.toString(), detectedURLs, null);
			Assert.assertEquals(urls.size(), detectedURLs.size());
			Assert.assertTrue(urls.containsAll(detectedURLs));
		}
		
		/* URLs surrounded with parenthesis */
		String[] texts = { "(http://yacy.net)", "YaCy home page (http://yacy.net)",
				"Nested parentheses (YaCy home page (http://yacy.net))",
				"Text in parenthesis (example : http://yacy.net)", "A markdown link [YaCy home page](http://yacy.net)",
				"A markdown [example](http://yacy.net \"YaCy home page\") inline link" };
		for (String text : texts) {
			Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
			Assert.assertEquals(1, detectedURLs.size());
			Assert.assertEquals(new AnchorURL("http://yacy.net"), detectedURLs.iterator().next());
		}
		
		/* URLs surrounded with square brackets */ 
		//http://[abcd:ef01:2345:6789:abcd:ef01:2345:6789]/
		String[] squareBracketsTexts = { "[http://yacy.net]", "YaCy home page [http://yacy.net]",
				"Nested brackets [YaCy home page [http://yacy.net]]",
				"A mediawiki external link with different label [http://yacy.net YaCy home page]" };
		for(String text : squareBracketsTexts) {
			Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
			Assert.assertEquals(1, detectedURLs.size());
			Assert.assertEquals(new AnchorURL("http://yacy.net"), detectedURLs.iterator().next());
		}
		
		/* URLs surrounded with curly brackets */ 
		//http://[abcd:ef01:2345:6789:abcd:ef01:2345:6789]/
		String[] curlyBracketsTexts = { "{http://yacy.net}", "YaCy home page {http://yacy.net}",
				"Nested brackets {YaCy home page {http://yacy.net}}",
				"Text in brackets {example : http://yacy.net}" };
		for(String text : curlyBracketsTexts) {
			Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
			Assert.assertEquals(1, detectedURLs.size());
			Assert.assertEquals(new AnchorURL("http://yacy.net"), detectedURLs.iterator().next());
		}
		
		/* URL with parenthesis */
		String text = "Example: https://en.wikipedia.org/wiki/Firefox_(disambiguation)";
		Collection<AnchorURL> detectedURLs = new ArrayList<>();
		ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
		Assert.assertEquals(1, detectedURLs.size());
		Assert.assertEquals(new AnchorURL("https://en.wikipedia.org/wiki/Firefox_(disambiguation)"), detectedURLs.iterator().next());
		
		/* IPV6 host */
		text = "URL with IPV6 host : http://[abcd:ef01:2345:6789:abcd:ef01:2345:6789]";
		detectedURLs = new ArrayList<>();
		ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
		Assert.assertEquals(1, detectedURLs.size());
		Assert.assertEquals(new AnchorURL("http://[abcd:ef01:2345:6789:abcd:ef01:2345:6789]"), detectedURLs.iterator().next());
		
		/* Text containing only the '://' pattern */
		detectedURLs = new ArrayList<>();
		ContentScraper.findAbsoluteURLs("An absolute URL should contain the '://' pattern", detectedURLs, null);
		Assert.assertEquals(0, detectedURLs.size());
		
		/* Text containing only the 'http://' and 'https://' patterns */
		detectedURLs = new ArrayList<>();
		ContentScraper.findAbsoluteURLs("An absolute HTTP URL should start with 'http://' or 'https://'", detectedURLs, null);
		Assert.assertEquals(0, detectedURLs.size());
		
		/* Text containing a malformed URL */
		detectedURLs = new ArrayList<>();
		ContentScraper.findAbsoluteURLs("The URL https://example.com:demo is malformed", detectedURLs, null);
		Assert.assertEquals(0, detectedURLs.size());
		
		/* Empty text */
		detectedURLs = new ArrayList<>();
		ContentScraper.findAbsoluteURLs("", detectedURLs, null);
		Assert.assertEquals(0, detectedURLs.size());
		
		/* Null text */
		detectedURLs = new ArrayList<>();
		ContentScraper.findAbsoluteURLs("", detectedURLs, null);
		Assert.assertEquals(0, detectedURLs.size());
    }
    
    /**
     * Test unpaired brackets cleaning
     */
    @Test
    public void testRemoveUnpairedBrackets() {
    	/* Null String */
    	Assert.assertEquals(null, ContentScraper.removeUnpairedBrackets(null, '{', '}'));
    	/* Empty string */
    	Assert.assertEquals("", ContentScraper.removeUnpairedBrackets("", '{', '}'));
    	/* No bracket at all */
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc", '{', '}'));
    	
    	/* Missing one or more opening mark */
    	Assert.assertEquals("", ContentScraper.removeUnpairedBrackets("}", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc}", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc}def", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc}}", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc}def}", '{', '}'));
    	Assert.assertEquals("{abc}", ContentScraper.removeUnpairedBrackets("{abc}}", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc}{def}}", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc}{def}", '{', '}'));
    	Assert.assertEquals("{abc}def", ContentScraper.removeUnpairedBrackets("{abc}def}", '{', '}'));
    	Assert.assertEquals("{abc}def", ContentScraper.removeUnpairedBrackets("{abc}def}hij}", '{', '}'));
    	Assert.assertEquals("{{abc}{def}}", ContentScraper.removeUnpairedBrackets("{{abc}{def}}}", '{', '}'));
    	
    	/* Missing both opening and closing */
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc}de{f", '{', '}'));
    	
    	/* Missing one or more closing mark */
    	Assert.assertEquals("", ContentScraper.removeUnpairedBrackets("{", '{', '}'));
    	Assert.assertEquals("", ContentScraper.removeUnpairedBrackets("{abc", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc{def", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc{{", '{', '}'));
    	Assert.assertEquals("abc", ContentScraper.removeUnpairedBrackets("abc{def{", '{', '}'));
    	Assert.assertEquals("", ContentScraper.removeUnpairedBrackets("{{abc}", '{', '}'));
    	Assert.assertEquals("", ContentScraper.removeUnpairedBrackets("{abc{def}", '{', '}'));
    	Assert.assertEquals("{{abc}{def}}", ContentScraper.removeUnpairedBrackets("{{abc}{def}}{", '{', '}'));
    	
    	/* Correctly paired marks */
    	Assert.assertEquals("abc{}", ContentScraper.removeUnpairedBrackets("abc{}", '{', '}'));
    	Assert.assertEquals("{abc}", ContentScraper.removeUnpairedBrackets("{abc}", '{', '}'));
    	Assert.assertEquals("{abc}{def}", ContentScraper.removeUnpairedBrackets("{abc}{def}", '{', '}'));
    	Assert.assertEquals("{{abc}{def}}", ContentScraper.removeUnpairedBrackets("{{abc}{def}}", '{', '}'));
    }

}
