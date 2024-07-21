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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
        final DigestURL root = new DigestURL("http://test.org/test.html");

        final String page = "<html><body>"
                + "<time datetime='2016-12-23'>23. Dezember 2016</time>" // html5 time tag
                + "</body></html>";

        final ContentScraper scraper = new ContentScraper(root, 10, new HashSet<String>(), new VocabularyScraper(), 0);
        final Writer writer = new TransformerWriter(null, null, scraper, false);

        FileUtils.copy(new StringReader(page), writer);
        writer.close();

        dateResultList = scraper.getStartDates();

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(0); // to zero hours
        cal.set(2016, Calendar.DECEMBER, 23);

        for (final Date d : dateResultList) {
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
		final String[] urlStrings = { "https://yacy.net", "https://community.searchlab.eu", "https://en.wikipedia.org" };
		final List<AnchorURL> urls = new ArrayList<>();
		for (final String urlString : urlStrings) {
			urls.add(new AnchorURL(urlString));
		}

		/* Test with various white space separators */
		final String[] separators = { " ", "\n", "\t", "\r" };
		for (final String separator : separators) {
			final StringBuilder text = new StringBuilder();
			for (final String urlString : urlStrings) {
				if (text.length() > 0) {
					text.append(separator);
				}
				text.append(urlString);
			}
			final Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text.toString(), detectedURLs, null);
			Assert.assertEquals(urls.size(), detectedURLs.size());
			Assert.assertTrue(urls.containsAll(detectedURLs));
		}
		
		/* URLs surrounded with parenthesis */
		final String[] texts = { "(https://yacy.net)", "YaCy home page (https://yacy.net)",
				"Nested parentheses (YaCy home page (https://yacy.net))",
				"Text in parenthesis (example : https://yacy.net)", "A markdown link [YaCy home page](https://yacy.net)",
				"A markdown [example](https://yacy.net \"YaCy home page\") inline link" };
		for (final String text : texts) {
			final Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
			Assert.assertEquals(1, detectedURLs.size());
			Assert.assertEquals(new AnchorURL("https://yacy.net"), detectedURLs.iterator().next());
		}
		
		/* URLs surrounded with square brackets */ 
		//http://[abcd:ef01:2345:6789:abcd:ef01:2345:6789]/
		final String[] squareBracketsTexts = { "[https://yacy.net]", "YaCy home page [https://yacy.net]",
				"Nested brackets [YaCy home page [https://yacy.net]]",
				"A mediawiki external link with different label [https://yacy.net YaCy home page]" };
		for(final String text : squareBracketsTexts) {
			final Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
			Assert.assertEquals(1, detectedURLs.size());
			Assert.assertEquals(new AnchorURL("https://yacy.net"), detectedURLs.iterator().next());
		}
		
		/* URLs surrounded with curly brackets */ 
		//http://[abcd:ef01:2345:6789:abcd:ef01:2345:6789]/
		final String[] curlyBracketsTexts = { "{https://yacy.net}", "YaCy home page {https://yacy.net}",
				"Nested brackets {YaCy home page {https://yacy.net}}",
				"Text in brackets {example : https://yacy.net}" };
		for(final String text : curlyBracketsTexts) {
			final Collection<AnchorURL> detectedURLs = new ArrayList<>();
			ContentScraper.findAbsoluteURLs(text, detectedURLs, null);
			Assert.assertEquals(1, detectedURLs.size());
			Assert.assertEquals(new AnchorURL("https://yacy.net"), detectedURLs.iterator().next());
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
     * Test absolute URLs detection in plain text with maxURLs parameter
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testFindAbsoluteURLsMaxURLs() throws MalformedURLException {
    	final String text = "Some test URLS : https://yacy.net - https://community.searchlab.eu - https://en.wikipedia.org";
    	
    	/* No limit */
    	ArrayList<AnchorURL> detectedURLs = new ArrayList<>();
    	ContentScraper.findAbsoluteURLs(text, detectedURLs, null, Long.MAX_VALUE);
    	Assert.assertEquals(3, detectedURLs.size());
    	
    	/* Test from zero limit, to limit value equals to the total number of URLs in text */
    	for(int limit = 0; limit <=3; limit++) {
    		detectedURLs = new ArrayList<>();
    		ContentScraper.findAbsoluteURLs(text, detectedURLs, null, limit);
    		Assert.assertEquals(limit, detectedURLs.size());
    	}
    	
    	/* Limit greater than total number of URLs in text */
    	detectedURLs = new ArrayList<>();
    	ContentScraper.findAbsoluteURLs(text, detectedURLs, null, 4);
    	Assert.assertEquals(3, detectedURLs.size());
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
    
    /**
     * Test base tag URL resolution
     * @throws IOException when an unexpected error occurred
     */
    @Test
    public void testBaseTagUrlResolution() throws IOException {
    	final String htmlHeaderBeginning = "<!DOCTYPE html><head><title>Test document</title>";
        final DigestURL docUrl = new DigestURL("http://example.org/parent/base.html");
        
        final String htmlLinksList = "<ul>" 
				+ "<li><a href=\"http://example.org/sameDomain/absolute.html\">Absolute on same domain</a></li>"
				+ "<li><a href=\"http://localhost/otherDomain/absolute.html\">Absolute on another domain</a></li>"
				+ "<li><a href=\"//example.org/sameDomain/scheme-relative.html\">scheme-relative on same domain</a></li>"
				+ "<li><a href=\"//example.net/otherDomain/scheme-relative.html\">scheme-relative on another domain</a></li>"
				+ "<li><a href=\"/path/absolute.html\">path-absolute</a></li>"
				+ "<li><a href=\"path/relative/schemeless.html\">path-relative-scheme-less</a></li>"
				+ "</ul>";
        
        
        final Map<String, String[]> html2Results = new HashMap<>();
        /* No base tag */
		String html = htmlHeaderBeginning + "</head>" + htmlLinksList;
		String[] expectedUrls = { "http://example.org/sameDomain/absolute.html",
				"http://localhost/otherDomain/absolute.html", "http://example.org/sameDomain/scheme-relative.html",
				"http://example.net/otherDomain/scheme-relative.html", "http://example.org/path/absolute.html",
				"http://example.org/parent/path/relative/schemeless.html" };
    	html2Results.put(html, expectedUrls);
    	
        /* Base with absolute href on same domain */
		html = htmlHeaderBeginning + "<base href=\"http://example.org/base/index.html\"/>"
				+ "</head>" + htmlLinksList;
		expectedUrls = new String[]{ "http://example.org/sameDomain/absolute.html",
				"http://localhost/otherDomain/absolute.html", "http://example.org/sameDomain/scheme-relative.html",
				"http://example.net/otherDomain/scheme-relative.html", "http://example.org/path/absolute.html",
				"http://example.org/base/path/relative/schemeless.html" };
    	html2Results.put(html, expectedUrls);
    	
        /* Base with absolute href on another domain */
		html = htmlHeaderBeginning + "<base href=\"http://example.net/base/index.html\"/>"
				+ "</head>" + htmlLinksList;
		expectedUrls = new String[]{ "http://example.org/sameDomain/absolute.html",
				"http://localhost/otherDomain/absolute.html", "http://example.org/sameDomain/scheme-relative.html",
				"http://example.net/otherDomain/scheme-relative.html", "http://example.net/path/absolute.html",
				"http://example.net/base/path/relative/schemeless.html" };
    	html2Results.put(html, expectedUrls);
    	
        /* Base with scheme-relative href on same domain */
		html = htmlHeaderBeginning + "<base href=\"//example.org/base/index.html\"/>"
				+ "</head>" + htmlLinksList;
		expectedUrls = new String[]{ "http://example.org/sameDomain/absolute.html",
				"http://localhost/otherDomain/absolute.html", "http://example.org/sameDomain/scheme-relative.html",
				"http://example.net/otherDomain/scheme-relative.html", "http://example.org/path/absolute.html",
				"http://example.org/base/path/relative/schemeless.html" };
    	html2Results.put(html, expectedUrls);
    	
        /* Base with scheme-relative href on another domain */
		html = htmlHeaderBeginning + "<base href=\"//example.net/base/index.html\"/>"
				+ "</head>" + htmlLinksList;
		expectedUrls = new String[]{ "http://example.org/sameDomain/absolute.html",
				"http://localhost/otherDomain/absolute.html", "http://example.org/sameDomain/scheme-relative.html",
				"http://example.net/otherDomain/scheme-relative.html", "http://example.net/path/absolute.html",
				"http://example.net/base/path/relative/schemeless.html" };
    	html2Results.put(html, expectedUrls);
    	
        /* Base with path-absolute relative href */
		html = htmlHeaderBeginning + "<base href=\"/base/index.html\"/>"
				+ "</head>" + htmlLinksList;
		expectedUrls = new String[]{ "http://example.org/sameDomain/absolute.html",
				"http://localhost/otherDomain/absolute.html", "http://example.org/sameDomain/scheme-relative.html",
				"http://example.net/otherDomain/scheme-relative.html", "http://example.org/path/absolute.html",
				"http://example.org/base/path/relative/schemeless.html" };
    	html2Results.put(html, expectedUrls);
    	
        /* Base with path-relative-scheme-less relative href */
		html = htmlHeaderBeginning + "<base href=\"base/index.html\"/>"
				+ "</head>" + htmlLinksList;
		expectedUrls = new String[]{ "http://example.org/sameDomain/absolute.html",
				"http://localhost/otherDomain/absolute.html", "http://example.org/sameDomain/scheme-relative.html",
				"http://example.net/otherDomain/scheme-relative.html", "http://example.org/path/absolute.html",
				"http://example.org/parent/base/path/relative/schemeless.html" };
    	html2Results.put(html, expectedUrls);

		for (final Entry<String, String[]> html2Result : html2Results.entrySet()) {
			final ContentScraper scraper = new ContentScraper(docUrl, 10, new HashSet<String>(), new VocabularyScraper(), 0);
			try (final Writer writer = new TransformerWriter(null, null, scraper, false)) {
				FileUtils.copy(new StringReader(html2Result.getKey()), writer);

				final Set<DigestURL> expected = new HashSet<>();
				for (final String url : html2Result.getValue()) {
					expected.add(new DigestURL(url));
				}

				Assert.assertEquals(expected.size(), scraper.getAnchors().size());
				Assert.assertTrue(expected.containsAll(scraper.getAnchors()));
			} finally {
				scraper.close();
			}
		}
    }
    
    /**
     * Test microdata itemtype attribute parsing
     * @throws IOException 
     */
    @Test
    public void testParseMicroDataItemType() throws IOException {
    	final String htmlHeader = "<!DOCTYPE html><head><title>Test document</title></head>";
        final DigestURL docUrl = new DigestURL("http://example.org/microdata.html");
        
        
        final Map<String, String[]> html2Results = new HashMap<>();
        /* Basic microdata syntax example with no item type */
    	String html = htmlHeader + "<div itemscope><p>My name is <span itemprop=\"name\">Elizabeth</span>.</p></div>";
    	String[] expectedUrls = {};
    	html2Results.put(html, expectedUrls);
    	
    	/* Nested items with no item type */
    	html = "<div itemscope>\n" + 
    	" <p>Name: <span itemprop=\"name\">Amanda</span></p>\n" + 
    	" <p>Band: <span itemprop=\"band\" itemscope> <span itemprop=\"name\">Jazz Band</span> (<span itemprop=\"size\">12</span> players)</span></p>\n" + 
    	"</div>";
    	expectedUrls = new String[0];
    	html2Results.put(html, expectedUrls);
    	
    	/* One typed item */
    	html = htmlHeader + "<div itemscope itemtype=\"https://schema.org/LocalBusiness\"><img itemprop=\"logo\" src=\"our-logo.png\" alt=\"Our Company\"></div>";
    	expectedUrls = new String[]{"https://schema.org/LocalBusiness"};
    	html2Results.put(html, expectedUrls);
    	
    	/* more than one type per item */
    	html = htmlHeader + "<dl itemscope itemtype=\"https://md.example.com/loco https://md.example.com/lighting\">" + 
    	" <dt>Name:\n" + 
    	" <dd itemprop=\"name\">Tank Locomotive (DB 80)\n" + 
    	" <dt>Product code:\n" + 
    	" <dd itemprop=\"product-code\">33041\n" + 
    	" <dt>Scale:\n" + 
    	" <dd itemprop=\"scale\">HO\n" + 
    	" <dt>Digital:\n" + 
    	" <dd itemprop=\"digital\">Delta\n" + 
    	"</dl>";
    	expectedUrls = new String[]{"https://md.example.com/loco", "https://md.example.com/lighting"};
    	html2Results.put(html, expectedUrls);
    	
    	/* Nested typed items */
    	html = htmlHeader + "<div itemscope itemtype=\"http://schema.org/Product\">\n" + 
    	" <span itemprop=\"name\">Panasonic White 60L Refrigerator</span>\n" + 
    	" <img src=\"panasonic-fridge-60l-white.jpg\" alt=\"\">\n" + 
    	"  <div itemprop=\"aggregateRating\"\n" + 
    	"       itemscope itemtype=\"http://schema.org/AggregateRating\">\n" + 
    	"   <meter itemprop=\"ratingValue\" min=0 value=3.5 max=5>Rated 3.5/5</meter>\n" + 
    	"   (based on <span itemprop=\"reviewCount\">11</span> customer reviews)\n" + 
    	"  </div>\n" + 
    	"</div>";
    	expectedUrls = new String[]{"http://schema.org/Product", "http://schema.org/AggregateRating"};
    	html2Results.put(html, expectedUrls);
  

		for (final Entry<String, String[]> html2Result : html2Results.entrySet()) {
			final ContentScraper scraper = new ContentScraper(docUrl, 10, new HashSet<String>(), new VocabularyScraper(), 0);
			try (final Writer writer = new TransformerWriter(null, null, scraper, false)) {
				FileUtils.copy(new StringReader(html2Result.getKey()), writer);

				final Set<DigestURL> expected = new HashSet<>();
				for (final String url : html2Result.getValue()) {
					expected.add(new DigestURL(url));
				}

				Assert.assertEquals(expected.size(), scraper.getLinkedDataTypes().size());
				Assert.assertTrue(expected.containsAll(scraper.getLinkedDataTypes()));
			} finally {
				scraper.close();
			}
		}
    }

}
