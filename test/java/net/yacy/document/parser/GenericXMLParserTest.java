// GenericXMLParserTest.java
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;

/**
 * Unit tests for the {@link GenericXMLParser} class
 *
 * @author luccioman
 *
 */
public class GenericXMLParserTest {

	/** Example test tag including non-ascii characters */
	private static final String UMLAUT_TEXT_TAG = "<text>In München steht ein Hofbräuhaus, dort gibt es Bier in Maßkrügen</text>";

	private GenericXMLParser parser;

	@Before
	public void setUp() {
		this.parser = new GenericXMLParser();
	}

	/**
	 * Unit test for the GenericXMLParser.parse() function with some small XML
	 * test files.
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParse() throws Exception {
		final String[] fileNames = { "umlaute_dc_xml_iso.xml", "umlaute_dc_xml_utf8.xml" };
		final File folder = new File("test" + File.separator + "parsertest" + File.separator);

		for (final String fileName : fileNames) {
			final FileInputStream inStream = new FileInputStream(new File(folder, fileName));
			final DigestURL location = new DigestURL("http://localhost/" + fileName);
			try {
				final Document[] documents = this.parser.parse(location, "text/xml", null, new VocabularyScraper(), 0,
						inStream);
				assertNotNull("Parser result must not be null for file " + fileName, documents);
				assertNotNull("Parsed text must not be empty for file " + fileName, documents[0].getTextString());
				assertTrue("Parsed text must contain test word with umlaut char in file " + fileName,
						documents[0].getTextString().contains("Maßkrügen"));
			} finally {
				inStream.close();
			}
		}
	}

	/**
	 *
	 * @param parser
	 *            generic xml parser instance. Must not be null.
	 * @param encodedXML
	 *            xml encoded bytes to test
	 * @param contentTypeHeader
	 *            Content-Type header value
	 * @param expectedCharset
	 *            expected character set name to be detected
	 * @param expectedConntainedText
	 *            expected text to be contained in the parsed text
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	private void testCharsetDetection(final GenericXMLParser parser, final byte[] encodedXML,
			final String contentTypeHeader, final String expectedCharset, final String expectedConntainedText)
			throws Exception {
		final InputStream inStream = new ByteArrayInputStream(encodedXML);
		final String charsetFromHttpHeader = HeaderFramework.getCharacterEncoding(contentTypeHeader);
		final DigestURL location = new DigestURL("http://localhost/testfile.xml");
		try {
			final Document[] documents = parser.parse(location, contentTypeHeader, charsetFromHttpHeader,
					new VocabularyScraper(), 0, inStream);
			assertEquals(expectedCharset, documents[0].getCharset());
			assertNotNull(documents[0].getTextString());
			assertTrue(documents[0].getTextString().contains(expectedConntainedText));
		} finally {
			inStream.close();
		}
	}

	/**
	 * Test UTF-8 charset detection
	 *
	 * @see RFC 7303 "UTF-8 Charset" example
	 *      (https://tools.ietf.org/html/rfc7303#section-8.1)
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseUTF8Charset() throws Exception {
		/*
		 * UTF-8 charset provided both in Content-Type HTTP header and in XML
		 * declaration
		 */
		byte[] encodedXML = ("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + UMLAUT_TEXT_TAG)
				.getBytes(StandardCharsets.UTF_8);
		testCharsetDetection(this.parser, encodedXML, "application/xml; charset=utf-8", StandardCharsets.UTF_8.name(),
				"Maßkrügen");

		/*
		 * Charset provided in Content-Type HTTP header but omitted in XML
		 * declaration
		 */
		encodedXML = ("<?xml version=\"1.0\"?>" + UMLAUT_TEXT_TAG).getBytes(StandardCharsets.UTF_8);
		testCharsetDetection(this.parser, encodedXML, "application/xml; charset=utf-8", StandardCharsets.UTF_8.name(),
				"Maßkrügen");
	}

	/**
	 * Test UTF-16 charset detection
	 *
	 * @see RFC 7303 "UTF-16 Charset" and
	 *      "Omitted Charset and 16-Bit MIME Entity" examples
	 *      (https://tools.ietf.org/html/rfc7303#section-8.2 and
	 *      https://tools.ietf.org/html/rfc7303#section-8.4)
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseUTF16Charset() throws Exception {
		/*
		 * UTF-16 charset provided both in Content-Type HTTP header and in XML
		 * declaration with BOM (Byte Order Mark)
		 */
		byte[] encodedXML = ("<?xml version=\"1.0\" encoding=\"utf-16\"?>" + UMLAUT_TEXT_TAG)
				.getBytes(StandardCharsets.UTF_16);
		testCharsetDetection(this.parser, encodedXML, "application/xml; charset=utf-16", StandardCharsets.UTF_16.name(),
				"Maßkrügen");

		/*
		 * UTF-16 charset provided in Content-Type HTTP header but omitted in
		 * XML declaration having only BOM (Byte Order Mark)
		 */
		encodedXML = ("<?xml version=\"1.0\"?>" + UMLAUT_TEXT_TAG).getBytes(StandardCharsets.UTF_16);
		testCharsetDetection(this.parser, encodedXML, "application/xml; charset=utf-16",
				StandardCharsets.UTF_16BE.name(), "Maßkrügen");

		/*
		 * Charset is omitted in Content-Type HTTP header, but provided in the
		 * XML declaration with BOM (Byte Order Mark)
		 */
		encodedXML = ("<?xml version=\"1.0\" encoding=\"utf-16\"?>" + UMLAUT_TEXT_TAG)
				.getBytes(StandardCharsets.UTF_16);
		testCharsetDetection(this.parser, encodedXML, "application/xml", StandardCharsets.UTF_16.name(), "Maßkrügen");

		/*
		 * Charset is omitted in both Content-Type HTTP header and XML
		 * declaration with BOM (Byte Order Mark)
		 */
		encodedXML = ("<?xml version=\"1.0\"?>" + UMLAUT_TEXT_TAG).getBytes(StandardCharsets.UTF_16);
		testCharsetDetection(this.parser, encodedXML, "application/xml", StandardCharsets.UTF_16BE.name(), "Maßkrügen");
	}

	/**
	 * Test ISO-8859-1 charset detection
	 *
	 * @see RFC 7303 "Omitted Charset and 8-Bit MIME Entity" example
	 *      (https://tools.ietf.org/html/rfc7303#section-8.3)
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseISO_8859_1Charset() throws Exception {
		/*
		 * ISO-8859-1 charset provided only in XML declaration without BOM (Byte
		 * Order Mark)
		 */
		final byte[] encodedXML = ("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>" + UMLAUT_TEXT_TAG)
				.getBytes(StandardCharsets.ISO_8859_1);
		testCharsetDetection(this.parser, encodedXML, "application/xml", StandardCharsets.ISO_8859_1.name(),
				"Maßkrügen");
	}

	/**
	 * Test charset detection when the character encoding is omitted in
	 * Content-Type header, and content has a XML declaration with no encoding
	 * declaration
	 *
	 * @see RFC 7303 "Omitted Charset, No Internal Encoding Declaration" example
	 *      (https://tools.ietf.org/html/rfc7303#section-8.5)
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseOmittedCharsetNoInternalEncoding() throws Exception {
		/*
		 * XML encoded as UTF-8 without BOM (Byte Order Mark)
		 */
		byte[] encodedXML = ("<?xml version=\"1.0\"?>" + UMLAUT_TEXT_TAG).getBytes(StandardCharsets.UTF_8);
		testCharsetDetection(this.parser, encodedXML, "application/xml", StandardCharsets.UTF_8.name(), "Maßkrügen");

		/*
		 * XML encoded as ASCII, with non ascii chars encoded as entities
		 */
		encodedXML = ("<?xml version=\"1.0\"?>"
				+ "<text>In M&#x000FC;nchen steht ein Hofbr&#x000E4;uhaus, dort gibt es Bier in Ma&#x000DF;kr&#x000FC;gen</text>")
						.getBytes(StandardCharsets.US_ASCII);
		testCharsetDetection(this.parser, encodedXML, "application/xml", StandardCharsets.UTF_8.name(), "Maßkrügen");
	}

	/**
	 * Test UTF-16BE charset detection
	 *
	 * @see RFC 7303 "UTF-16BE Charset" example
	 *      (https://tools.ietf.org/html/rfc7303#section-8.6)
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseUTF8_16BECharset() throws Exception {
		/*
		 * UTF-16BE charset provided both in Content-Type HTTP header and in XML
		 * declaration, without BOM (Byte Order Mark)
		 */
		final byte[] encodedXML = ("<?xml version='1.0' encoding='utf-16be'?>" + UMLAUT_TEXT_TAG)
				.getBytes(StandardCharsets.UTF_16BE);
		testCharsetDetection(this.parser, encodedXML, "application/xml; charset=utf-16be",
				StandardCharsets.UTF_16BE.name(), "Maßkrügen");
	}

	/**
	 * Test absolute URLs detection in XML elements attributes.
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseAttributeURLs() throws Exception {
		final String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
				+ "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\">" + "<head>"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />"
				+ "<title>XHTML attributes URLs test</title>" + "</head>" + "<body>"
				+ "Here are YaCy<a href=\"https://yacy.net\">home page</a> and <a href=\"https://community.searchlab.eu\">International Forum</a>."
				+ "And this is a relative link to a <a href=\"/document.html\">sub document</a>." + "</body>"
				+ "</html>";

		final InputStream inStream = new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8.name()));
		final String contentTypeHeader = "text/xhtml";
		final String charsetFromHttpHeader = HeaderFramework.getCharacterEncoding(contentTypeHeader);
		final DigestURL location = new DigestURL("http://localhost/testfile.xml");
		try {
			final Document[] documents = this.parser.parse(location, contentTypeHeader, charsetFromHttpHeader,
					new VocabularyScraper(), 0, inStream);
			assertEquals(1, documents.length);
			final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
			assertNotNull(detectedAnchors);
			assertEquals(3, detectedAnchors.size());
			assertTrue(detectedAnchors.contains(new AnchorURL("https://www.w3.org/1999/xhtml")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://yacy.net")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://community.searchlab.eu")));
		} finally {
			inStream.close();
		}
	}

	/**
	 * Test absolute URLs detection in XML elements text.
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseContentURLs() throws Exception {
		final String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
				+ "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\">" + "<head>"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />"
				+ "<title>XHTML content URLs test</title>" + "</head>" + "<body>" + "Here are some YaCy links:" + "<dl>"
				+ "<dt>Home page</dt>" + "<dd>https://yacy.net</dd>" + "<dt>International Forum</dt>"
				+ "<dd>https://community.searchlab.eu</dd>" + "</dl>"
				+ "And this is a mention to a relative link : /document.html " + "</body>" + "</html>";

		final InputStream inStream = new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8.name()));
		final String contentTypeHeader = "text/xhtml";
		final String charsetFromHttpHeader = HeaderFramework.getCharacterEncoding(contentTypeHeader);
		final DigestURL location = new DigestURL("http://localhost/testfile.xml");
		try {
			final Document[] documents = this.parser.parse(location, contentTypeHeader, charsetFromHttpHeader,
					new VocabularyScraper(), 0, inStream);
			assertEquals(1, documents.length);
			final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
			assertNotNull(detectedAnchors);
			assertEquals(3, detectedAnchors.size());
			assertTrue(detectedAnchors.contains(new AnchorURL("https://www.w3.org/1999/xhtml")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://yacy.net")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://community.searchlab.eu")));
		} finally {
			inStream.close();
		}
	}

	/**
	 * Test parsing well-formed XML fragment (no XML declaration, no DTD or schema)
	 * @throws Exception when an unexpected error occurred
	 */
	@Test
	public void testParseXMLFragment() throws Exception {
		final String xhtml = "<root><node><subNode1>Node content1</subNode1><subNode2>Node content2</subNode2></node></root>";

		final InputStream inStream = new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8.name()));
		final String contentTypeHeader = "text/xml";
		final String charsetFromHttpHeader = HeaderFramework.getCharacterEncoding(contentTypeHeader);
		final DigestURL location = new DigestURL("http://localhost/testfile.xml");
		try {
			final Document[] documents = this.parser.parse(location, contentTypeHeader, charsetFromHttpHeader,
					new VocabularyScraper(), 0, inStream);
			assertEquals(1, documents.length);
			assertEquals("Node content1 Node content2", documents[0].getTextString());
		} finally {
			inStream.close();
		}
	}

	/**
	 * Test URLs detection when applying limits.
	 *
	 * @throws Exception
	 *             when an unexpected error occurred
	 */
	@Test
	public void testParseWithLimits() throws Exception {
		final String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
				+ "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\">" + "<head>"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />"
				+ "<title>XHTML content URLs test</title>" + "</head>" + "<body>" + "<p>Here are some YaCy URLs: "
				+ "Home page : https://yacy.net - International Forum : "
				+ "https://community.searchlab.eu "
				+ "and this is a mention to a relative URL : /document.html</p>"
				+ "<p>Here are YaCy<a href=\"http://mantis.tokeek.de\">bug tracker</a> and <a href=\"https://wiki.yacy.net/index.php/\">Wiki</a>."
				+ "And this is a relative link to another <a href=\"/document2.html\">sub document</a></p>"
				+ "</body>" + "</html>";

		/* Content within limits */
		InputStream inStream = new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8.name()));
		final String contentTypeHeader = "text/xhtml";
		final String charsetFromHttpHeader = HeaderFramework.getCharacterEncoding(contentTypeHeader);
		final DigestURL location = new DigestURL("http://localhost/testfile.xml");
		try {
			final Document[] documents = this.parser.parseWithLimits(location, contentTypeHeader, charsetFromHttpHeader, new VocabularyScraper(), 0, inStream, Integer.MAX_VALUE, Long.MAX_VALUE);
			assertEquals(1, documents.length);
			assertFalse(documents[0].isPartiallyParsed());

			assertTrue(documents[0].getTextString().contains("And this is a relative link"));

			final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
			assertNotNull(detectedAnchors);
			assertEquals(5, detectedAnchors.size());
			assertTrue(detectedAnchors.contains(new AnchorURL("https://www.w3.org/1999/xhtml")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://yacy.net")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://community.searchlab.eu")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://wiki.yacy.net/index.php/")));
		} finally {
			inStream.close();
		}

		/* Links limit exceeded */
		inStream = new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8.name()));
		try {
			final Document[] documents = this.parser.parseWithLimits(location, contentTypeHeader, charsetFromHttpHeader,
					new VocabularyScraper(), 0, inStream, 2, Long.MAX_VALUE);
			assertEquals(1, documents.length);
			assertTrue(documents[0].isPartiallyParsed());

			assertTrue(documents[0].getTextString().contains("Home page"));
			assertFalse(documents[0].getTextString().contains("And this is a relative link"));

			final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
			assertNotNull(detectedAnchors);
			assertEquals(2, detectedAnchors.size());
			assertTrue(detectedAnchors.contains(new AnchorURL("https://www.w3.org/1999/xhtml")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://yacy.net")));
		} finally {
			inStream.close();
		}

		/* Bytes limit exceeded */
		final StringBuilder xhtmlBuilder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>")
				.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">")
				.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">")
				.append("<head>")
				.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />")
				.append("<title>XHTML content URLs test</title>")
				.append("</head>")
				.append("<body><p>Here are some YaCy URLs: ")
				.append("Home page : https://yacy.net - International Forum : ")
				.append("https://community.searchlab.eu ")
				.append("and this is a mention to a relative URL : /document.html</p>");

		/* Add some filler text to reach a total size beyond SAX parser internal input stream buffers */
		while(xhtmlBuilder.length() < 1024 * 20) {
			xhtmlBuilder.append("<p>Some text to parse</p>");
		}

		final int firstBytes = xhtmlBuilder.toString().getBytes(StandardCharsets.UTF_8.name()).length;
		xhtmlBuilder.append("<p>Here are YaCy<a href=\"http://mantis.tokeek.de\">bug tracker</a> and <a href=\"https://wiki.yacy.net/index.php/\">Wiki</a>.")
			.append("And this is a relative link to another <a href=\"/document2.html\">sub document</a></p>")
			.append("</body></html>");
		inStream = new ByteArrayInputStream(xhtmlBuilder.toString().getBytes(StandardCharsets.UTF_8.name()));
		try {
			final Document[] documents = this.parser.parseWithLimits(location, contentTypeHeader, charsetFromHttpHeader, new VocabularyScraper(), 0, inStream, Integer.MAX_VALUE, firstBytes);
			assertEquals(1, documents.length);
			assertTrue(documents[0].isPartiallyParsed());

			assertTrue(documents[0].getTextString().contains("and this is a mention to a relative URL"));
			assertFalse(documents[0].getTextString().contains("And this is a relative link to another"));

			final Collection<AnchorURL> detectedAnchors = documents[0].getAnchors();
			assertNotNull(detectedAnchors);
			assertEquals(3, detectedAnchors.size());
			assertTrue(detectedAnchors.contains(new AnchorURL("https://www.w3.org/1999/xhtml")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://yacy.net")));
			assertTrue(detectedAnchors.contains(new AnchorURL("https://community.searchlab.eu")));
		} finally {
			inStream.close();
		}
	}

}
