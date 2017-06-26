// GenericXMLContentHandler.java
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

package net.yacy.document.parser.xml;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

import org.apache.commons.io.input.ClosedInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.parser.html.ContentScraper;

/**
 * SAX handler for XML contents, only extracting text and eventual URLs from
 * XML.
 * 
 * @author luccioman
 *
 */
public class GenericXMLContentHandler extends DefaultHandler {

	/** Output writer */
	private final Writer out;

	/** Detected URLs */
	private final Collection<AnchorURL> urls;
	
	/** Text of the currently parsed element. May not contain the whole text when the element has nested elements embedded in its own text */
	private StringBuilder currentElementText;
	
	/** Set to true when the last character written to the output writer is a space */
	private boolean lastAppendedIsSpace;
	
	/** The number of text chunks handled in the current element (reset to zero when the element has nested elements) */
	private int currentElementTextChunks;
	
	/** Set to false until some text is detected in at least one element of the document */
	private boolean documentHasText;

	/**
	 * @param out
	 *            the output writer to write extracted text. Must not be null.
	 * @param urls the mutable collection of URLs to fill with detected URLs
	 * @throws IllegalArgumentException
	 *             when out is null
	 */
	public GenericXMLContentHandler(final Writer out, final Collection<AnchorURL> urls) throws IllegalArgumentException {
		if (out == null) {
			throw new IllegalArgumentException("out writer must not be null");
		}
		if (urls == null) {
			throw new IllegalArgumentException("urls collection must not be null");
		}
		this.out = out;
		this.urls = urls;
	}

	/**
	 * @return an empty source to prevent the SAX parser opening an unwanted
	 *         connection to resolve an external entity
	 */
	@Override
	public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
		return new InputSource(new ClosedInputStream());
	}
	
	@Override
	public void startDocument() throws SAXException {
		this.currentElementText = new StringBuilder();
		this.lastAppendedIsSpace = false;
		this.currentElementTextChunks = 0;
		this.documentHasText = false;
	}

	/**
	 * Try to detect URLs eventually contained in attributes
	 */
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		this.currentElementText.setLength(0);
		this.currentElementTextChunks = 0;

		if (attributes != null) {
			for (int i = 0; i < attributes.getLength(); i++) {
				String attribute = attributes.getValue(i);
				ContentScraper.findAbsoluteURLs(attribute, this.urls, null);
			}
		}
	}

	/**
	 * Write characters to the output writer
	 */
	@Override
	public void characters(final char ch[], final int start, final int length) {
		try {
			if(this.currentElementTextChunks == 0 && this.documentHasText) {
				/* We are on the first text chunk of the element, or the first text chunk after processing nested elements : 
				 * if necessary we add a space to separate text content of different elements */
				if(length > 0 && !this.lastAppendedIsSpace && !Character.isWhitespace(ch[0])) {
					this.out.write(" ");
					this.currentElementText.append(" ");
				}
			}
			
			this.out.write(ch, start, length);
			this.currentElementText.append(ch, start, length);
			
			if(length > 0) {
				this.currentElementTextChunks++;
				this.documentHasText = true;
				this.lastAppendedIsSpace = Character.isWhitespace(ch[length - 1]);
			}
		} catch (final IOException e) {
			ConcurrentLog.logException(e);
		}
	}

	/**
	 * When the eventual element text doesn't end with a terminal punctuation character,
	 * add a period ('.' character) to help future SentenceReader work.
	 */
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		ContentScraper.findAbsoluteURLs(this.currentElementText.toString(), urls, null);
		this.currentElementText.setLength(0);
		this.currentElementTextChunks = 0;
	}
	
	@Override
	public void endDocument() throws SAXException {
		/* Release the StringBuilder now useless */
		this.currentElementText = null;
	}

}