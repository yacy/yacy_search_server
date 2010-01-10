// ODMetaHandler.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 18.07.2009 on http://yacy.net
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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ODMetaHandler extends DefaultHandler {
	private final StringBuilder buffer = new StringBuilder();
	
	private String docCreator = null;
	private String docLanguage = null;
	private String docKeyword = null;
	private String docSubject = null;
	private String docTitle = null;
	private String docDescription = null;
	
	public ODMetaHandler() {
	}
	
	@Override
	public void characters(final char ch[], final int start, final int length) {
	    buffer.append(ch, start, length);
	}
	
	@Override
	public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
	    buffer.delete(0, buffer.length());
	}

	@Override
	public void endElement(final String uri, final String name, final String tag) {
	    if ("dc:creator".equals(tag)) {
		this.docCreator = buffer.toString();
	    } else if ("dc:language".equals(tag)) {
		this.docLanguage  = buffer.toString();
	    } else if ("meta:keyword".equals(tag)) {
		this.docKeyword  = buffer.toString();
	    } else if ("dc:subject".equals(tag)) {
		this.docSubject  = buffer.toString();
	    } else if ("dc:title".equals(tag)) {
		this.docTitle  = buffer.toString();
	    } else if ("dc:description".equals(tag)) {
		this.docDescription  = buffer.toString();
	    }
	}

	public String getCreator() {
	    return docCreator;
	}

	public String getLanguage() {
	    return docLanguage;
	}
	public String getKeyword() {
	    return docKeyword;
	}
	public String getSubject() {
	    return docSubject;
	}
	public String getTitle() {
	    return docTitle;
	}
	public String getDescription() {
	    return docDescription;
	}
}

