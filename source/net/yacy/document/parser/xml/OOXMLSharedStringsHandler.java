// OOXMLSharedStringsHandler.java
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
import java.util.List;

import org.apache.commons.io.input.ClosedInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler for Office Open XML SpreadSheet xl/sharedStrings.xml files.
 * 
 * @author luccioman
 * @see <a href=
 *      "http://www.ecma-international.org/publications/standards/Ecma-376.htm">Ecma
 *      Standard for Office Open XML File Formats</a>
 *
 */
public class OOXMLSharedStringsHandler extends DefaultHandler {

	/** The entry name of a shared strings table in an OOXML container */
	public static final String ENTRY_NAME = "xl/sharedStrings.xml";

	/** Name of a shared string tag */
	private static final String SHARED_STRING_TAG = "t";

	/** Shared strings list */
	private final List<String> sharedStrings;

	/** Currently parsed string builder. */
	private StringBuilder currentString;

	/**
	 * @param sharedStrings
	 *            the mutable list of shared strings to fill
	 * @throws IllegalArgumentException
	 *             when a parameter is null
	 */
	public OOXMLSharedStringsHandler(final List<String> sharedStrings) throws IllegalArgumentException {
		if (sharedStrings == null) {
			throw new IllegalArgumentException("sharedStrings list must not be null");
		}
		this.sharedStrings = sharedStrings;
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
		this.currentString = new StringBuilder();
	}

	@Override
	public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
			throws SAXException {
		if (SHARED_STRING_TAG.equals(qName)) {
			this.currentString.setLength(0);
		}
	}

	/**
	 * Append characters to the current string builder. May be called multiple times
	 * before obtaining the whole current element string.
	 */
	@Override
	public void characters(final char ch[], final int start, final int length) throws SAXException {
		this.currentString.append(ch, start, length);
	}

	/**
	 * Add the current string content to the list when ending a shared string
	 * element
	 */
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (SHARED_STRING_TAG.equals(qName)) {
			final String sharedString = this.currentString.toString();
			this.sharedStrings.add(sharedString);
			this.currentString.setLength(0);
		}
	}

	@Override
	public void endDocument() throws SAXException {
		/* Release the StringBuilder now useless */
		this.currentString = null;
	}

}