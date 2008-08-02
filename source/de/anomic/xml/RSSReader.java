// rssReader.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.07.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.server.serverByteBuffer;
import de.anomic.server.logging.serverLog;

public class RSSReader extends DefaultHandler {
    
    // class variables
    private RSSMessage item;
    private final StringBuffer buffer;
    private boolean parsingChannel, parsingImage, parsingItem;
    private final RSSFeed theChannel;
    
    public RSSReader() {
        theChannel = new RSSFeed();
        buffer = new StringBuffer();
        item = null;
        parsingChannel = false;
        parsingImage = false;
        parsingItem = false;
    }
    
    public RSSReader(final String path) {
        this();
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            final SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(path, this);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    public RSSReader(final InputStream stream) {
        this();
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            final SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(stream, this);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    public static RSSReader parse(final byte[] a) {

        // check integrity of array
        if ((a == null) || (a.length == 0)) {
            serverLog.logWarning("rssReader", "response=null");
            return null;
        }
        if (a.length < 100) {
            serverLog.logWarning("rssReader", "response=" + new String(a));
            return null;
        }
        if (!serverByteBuffer.equals(a, "<?xml".getBytes())) {
            serverLog.logWarning("rssReader", "response does not contain valid xml");
            return null;
        }
        final String end = new String(a, a.length - 10, 10);
        if (end.indexOf("rss") < 0) {
            serverLog.logWarning("rssReader", "response incomplete");
            return null;
        }
        
        // make input stream
        final ByteArrayInputStream bais = new ByteArrayInputStream(a);
        
        // parse stream
        RSSReader reader = null;
        try {
            reader = new RSSReader(bais);
        } catch (final Exception e) {
            serverLog.logWarning("rssReader", "parse exception: " + e);
            return null;
        }
        try { bais.close(); } catch (final IOException e) {}
        return reader;
    }

    public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
        if ("channel".equals(tag)) {
            item = new RSSMessage();
            parsingChannel = true;
        } else if ("item".equals(tag)) {
            item = new RSSMessage();
            parsingItem = true;
        } else if ("image".equals(tag)) {
            parsingImage = true;
        }
    }

    public void endElement(final String uri, final String name, final String tag) {
        if (tag == null) return;
        if ("channel".equals(tag)) {
            parsingChannel = false;
            theChannel.setChannel(item);
        } else if ("item".equals(tag)) {
            theChannel.addMessage(item);
            parsingItem = false;
        } else if ("image".equals(tag)) {
            parsingImage = false;
        } else if ((parsingImage) && (parsingChannel)) {
            final String value = buffer.toString().trim();
            buffer.setLength(0);
            if ("url".equals(tag)) theChannel.setImage(value);
        } else if (parsingItem)  {
            final String value = buffer.toString().trim();
            buffer.setLength(0);
            if (RSSMessage.tags.contains(tag)) item.setValue(tag, value);
        } else if (parsingChannel) {
            final String value = buffer.toString().trim();
            buffer.setLength(0);
            if (RSSMessage.tags.contains(tag)) item.setValue(tag, value);
        }
    }

    public void characters(final char ch[], final int start, final int length) {
        if (parsingItem || parsingChannel) {
            buffer.append(ch, start, length);
        }
    }
    
    public RSSFeed getFeed() {
        return theChannel;
    }

}