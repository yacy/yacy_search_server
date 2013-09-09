// ODContentHandler.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.07.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import net.yacy.cora.util.ConcurrentLog;

import org.xml.sax.helpers.DefaultHandler;

/**
 * This is a SAX Handler, which handles the content.xml file
 * of an OpenDocument-file and passes all interesting data to
 * a Writer
 * @author f1ori
 *
 */
public class ODContentHandler extends DefaultHandler {
	private final Writer out;
	public ODContentHandler(Writer out) {
	    this.out = out;
	}
	@Override
	public void characters(final char ch[], final int start, final int length) {
	    try {
		out.write(ch, start, length);
	    } catch (final IOException e) {
	        ConcurrentLog.logException(e);
	    }
	}
	@Override
	public void endElement(final String uri, final String name, final String tag) {
	    if ("text:p".equals(tag) || "table:table-row".equals(tag) || "w:p".equals(tag)) {
		// add newlines after paragraphs 
		try {
		    out.append("\n");
		} catch (final IOException e) {
		    ConcurrentLog.logException(e);
		}
	    }
	}
}