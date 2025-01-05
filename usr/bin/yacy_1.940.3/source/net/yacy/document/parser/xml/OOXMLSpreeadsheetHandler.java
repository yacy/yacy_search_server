// OOXMLSpreeadsheetHandler.java
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
import java.util.List;

import javax.naming.SizeLimitExceededException;

import org.apache.commons.io.input.ClosedInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.parser.html.ContentScraper;

/**
 * SAX handler for Office Open XML SpreadSheet xl/sharedStrings.xml files.
 * 
 * @author luccioman
 * @see <a href=
 *      "http://www.ecma-international.org/publications/standards/Ecma-376.htm">Ecma
 *      Standard for Office Open XML File Formats</a>
 *
 */
public class OOXMLSpreeadsheetHandler extends DefaultHandler {

	/** The entry name prefix of a data sheets in an OOXML container */
	public static final String ENTRY_PREFIX = "xl/worksheets/sheet";
	
	/** Name of a cell tag in a data sheet */
	private static final String CELL_TAG = "c";

	/** Attribute name indicating the type of a cell element in a data sheet */
	private static final String CELL_TYPE_ATTRIBUTE = "t";

	/** Name of a cell value tag in a data sheet */
	private static final String CELL_VALUE_TAG = "v";

	/**
	 * Cell type attribute value for a cell using a shared string
	 * 
	 * @see "'Ecma Office Open XML Part 1 - Fundamentals And Markup Language Reference.pdf' - section 18.18.11 ST_CellType (Cell Type)"
	 */
	private static final String SHARED_STRING_CELL_TYPE = "s";

	/** The document shared strings list */
	private final List<String> sharedStrings;

	/** Output writer for cells text */
	private final Writer out;

	/** Detected URLs */
	private final Collection<AnchorURL> urls;

	/** Maximum number of URLs to parse */
	private final int maxURLs;

	/** Number of parsed URLs in the data sheet */
	private long detectedURLs;

	/**
	 * Set to true when the last character written to the output writer is a space
	 */
	private boolean lastAppendedIsSpace;

	/** Currently parsed cell value content. */
	private StringBuilder cellValue;

	/** Set to true when we are currently processing a XMl cell element */
	private boolean inCell;

	/** Set to true when we are currently processing a XML cell value element */
	private boolean inCellValue;

	/**
	 * Set to true when we are currently processing a XML cell element of Shared
	 * String type
	 */
	private boolean sharedStringCell;

	/**
	 * @param sharedStrings
	 *            the list of shared strings of the parent spredsheet document
	 * @param out
	 *            the output writer to write text extracted from cells. Must not be
	 *            null.
	 * @param urls
	 *            the mutable collection of URLs to fill with detected URLs
	 * @throws IllegalArgumentException
	 *             when out is null
	 */
	public OOXMLSpreeadsheetHandler(final List<String> sharedStrings, final Writer out,
			final Collection<AnchorURL> urls) throws IllegalArgumentException {
		this(sharedStrings, out, urls, Integer.MAX_VALUE);
	}

	/**
	 * @param out
	 *            the output writer to write extracted text. Must not be null.
	 * @param urls
	 *            the mutable collection of URLs to fill with detected URLs
	 * @param maxURLs
	 *            the maximum number of urls to parse
	 * @throws IllegalArgumentException
	 *             when out or urls parmeter is null
	 */
	public OOXMLSpreeadsheetHandler(final List<String> sharedStrings, final Writer out,
			final Collection<AnchorURL> urls, final int maxURLs) throws IllegalArgumentException {
		if (out == null) {
			throw new IllegalArgumentException("out writer must not be null");
		}
		if (urls == null) {
			throw new IllegalArgumentException("urls collection must not be null");
		}
		this.sharedStrings = sharedStrings;
		this.out = out;
		this.urls = urls;
		this.maxURLs = maxURLs;
		this.detectedURLs = 0;
		this.lastAppendedIsSpace = false;
		this.inCell = false;
		this.inCellValue = false;
		this.sharedStringCell = false;
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
		this.cellValue = new StringBuilder();
		this.detectedURLs = 0;
		this.lastAppendedIsSpace = false;
		this.inCell = false;
		this.inCellValue = false;
		this.sharedStringCell = false;
	}

	@Override
	public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
			throws SAXException {
		if (CELL_TAG.equals(qName)) {
			this.cellValue.setLength(0);
			this.inCell = true;
			final String cellType = attributes.getValue(CELL_TYPE_ATTRIBUTE);
			this.sharedStringCell = SHARED_STRING_CELL_TYPE.equals(cellType);
		} else if (this.inCell && CELL_VALUE_TAG.equals(qName)) {
			this.cellValue.setLength(0);
			this.inCellValue = true;
		}
	}

	/**
	 * Append characters to the current string builder. May be called multiple times
	 * before obtaining the whole current element string.
	 */
	@Override
	public void characters(final char ch[], final int start, final int length) throws SAXException {
		if (this.inCellValue) {
			this.cellValue.append(ch, start, length);
		}
	}

	/**
	 * Perform URLs detection on the ending element text
	 * 
	 * @throws SAXException
	 *             when the maxURLs limit has been reached
	 */
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (CELL_VALUE_TAG.equals(qName)) {
			String cellText = null;
			if (this.sharedStringCell) {
				/* Try to retrieve the cell text from the shared strings list */
				try {
					int index = Integer.parseInt(this.cellValue.toString());
					if (this.sharedStrings != null && this.sharedStrings.size() > index) {
						cellText = this.sharedStrings.get(index);
					}
				} catch (NumberFormatException ignored) {
					/* Do not terminate parsing if one shared strings index value is malformed */
				}
			} else {
				/* Use directly the cell value as text */
				cellText = this.cellValue.toString();
			}
			try {
				if (cellText != null && !cellText.isEmpty()) {
					this.detectedURLs += ContentScraper.findAbsoluteURLs(cellText, this.urls, null,
							this.maxURLs - this.detectedURLs);

					/*
					 * Iif necessary we add a space to separate text content of different elements
					 */
					if (!this.lastAppendedIsSpace && !Character.isWhitespace(cellText.charAt(0))) {
						this.out.write(" ");
					}

					this.out.write(cellText);
					this.lastAppendedIsSpace = Character.isWhitespace(cellText.charAt(cellText.length() - 1));
				}
			} catch (IOException ioe) {
				throw new SAXException("Error while appending characters to the output writer", ioe);
			} finally {
				this.cellValue.setLength(0);
				this.inCellValue = false;
			}

			if (this.detectedURLs >= this.maxURLs) {
				throw new SAXException(
						new SizeLimitExceededException("Reached maximum URLs to parse : " + this.maxURLs));
			}
		} else if (CELL_TAG.equals(qName)) {
			this.inCell = false;
			this.inCellValue = false;
		}
	}

	@Override
	public void endDocument() throws SAXException {
		/* Release the StringBuilder now useless */
		this.cellValue = null;
	}

}