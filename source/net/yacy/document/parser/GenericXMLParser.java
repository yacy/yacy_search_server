// GenericXMLParser.java
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.input.XmlStreamReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.xml.GenericXMLContentHandler;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;

/**
 * A generic XML parser without knowledge of the specific XML vocabulary.
 * @author luccioman
 *
 */
public class GenericXMLParser extends AbstractParser implements Parser {
	
	/** SAX parser instance local to each thread */
    private static final ThreadLocal<SAXParser> tlSax = new ThreadLocal<SAXParser>();
    
    /**
     * @return a SAXParser instance for the current thread
     * @throws SAXException when an error prevented parser creation
     */
    private static SAXParser getParser() throws SAXException {
    	SAXParser parser = tlSax.get();
    	if (parser == null) {
    		try {
				parser = SAXParserFactory.newInstance().newSAXParser();
			} catch (final ParserConfigurationException e) {
				throw new SAXException(e.getMessage(), e);
			}
    		tlSax.set(parser);
    	}
    	return parser;
    }

    public GenericXMLParser() {
        super("XML Parser");
        this.SUPPORTED_EXTENSIONS.add("xml");
        this.SUPPORTED_MIME_TYPES.add("application/xml");
        this.SUPPORTED_MIME_TYPES.add("text/xml");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Failure, InterruptedException {
    	
    	/* Limit the size of the in-memory buffer to at most 25% of the available memory :
    	 * because some room is needed, and before being garbage collected the buffer will be converted to a String, then to a byte array. 
    	 * Eventual stricter limits should be handled by the caller (see for example crawler.[protocol].maxFileSize configuration setting). */
    	final long availableMemory = MemoryControl.available();
    	final long maxBytes = (long)(availableMemory * 0.25);
    	final int maxChars;
    	if((maxBytes / Character.BYTES) > Integer.MAX_VALUE) {
    		maxChars = Integer.MAX_VALUE;
    	} else {
    		maxChars = ((int)maxBytes) / Character.BYTES;
    	}
    	
        try (/* Automatically closed by this try-with-resources statement*/ CharBuffer writer = new CharBuffer(maxChars);){

        	/* Use commons-io XmlStreamReader advanced rules to help with charset detection when source contains no BOM or XML declaration
        	 * (detection algorithm notably also include ContentType transmitted by HTTP headers, here eventually present as mimeType and charset parameters),  */
        	final XmlStreamReader reader = new XmlStreamReader(source, mimeType, true, charset);
			final InputSource saxSource = new InputSource(reader);
			final String detectedCharset = reader.getEncoding();

			final List<AnchorURL> detectedURLs = new ArrayList<>();

			final GenericXMLContentHandler saxHandler = new GenericXMLContentHandler(writer, detectedURLs);
			final SAXParser saxParser = getParser();
			saxParser.parse(saxSource, saxHandler);

			if (writer.isOverflow()) {
				throw new Parser.Failure("Not enough Memory available for generic the XML parser : "
						+ Formatter.bytesToString(availableMemory), location);
			}

			/* create the parsed document */
			Document[] docs = null;
			final byte[] contentBytes = UTF8.getBytes(writer.toString());
			docs = new Document[] { new Document(location, mimeType, detectedCharset, this, null, null, null, null, "",
					null, null, 0.0d, 0.0d, contentBytes, detectedURLs, null, null, false, new Date()) };
			return docs;
		} catch (final Exception e) {
			if (e instanceof InterruptedException) {
				throw (InterruptedException) e;
			}
			if (e instanceof Parser.Failure) {
				throw (Parser.Failure) e;
			}

			throw new Parser.Failure("Unexpected error while parsing XML file. " + e.getMessage(), location);
		}

	}

}
