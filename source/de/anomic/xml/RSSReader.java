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
    private StringBuffer buffer;
    private boolean parsingChannel, parsingImage, parsingItem;
    private RSSFeed theChannel;
    
    public RSSReader() {
        theChannel = new RSSFeed();
        buffer = new StringBuffer();
        item = null;
        parsingChannel = false;
        parsingImage = false;
        parsingItem = false;
    }
    
    public RSSReader(String path) {
        this();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(path, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public RSSReader(InputStream stream) {
        this();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(stream, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static RSSReader parse(byte[] a) {

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
        String end = new String(a, a.length - 10, 10);
        if (end.indexOf("rss") < 0) {
            serverLog.logWarning("rssReader", "response incomplete");
            return null;
        }
        
        // make input stream
        ByteArrayInputStream bais = new ByteArrayInputStream(a);
        
        // parse stream
        RSSReader reader = null;
        try {
            reader = new RSSReader(bais);
        } catch (Exception e) {
            serverLog.logWarning("rssReader", "parse exception: " + e);
            return null;
        }
        try { bais.close(); } catch (IOException e) {}
        return reader;
    }

    public void startElement(String uri, String name, String tag, Attributes atts) throws SAXException {
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

    public void endElement(String uri, String name, String tag) {
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
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if ("url".equals(tag)) theChannel.setImage(value);
        } else if (parsingItem)  {
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if (RSSMessage.tags.contains(tag)) item.setValue(tag, value);
        } else if (parsingChannel) {
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if (RSSMessage.tags.contains(tag)) item.setValue(tag, value);
        }
    }

    public void characters(char ch[], int start, int length) {
        if (parsingItem || parsingChannel) {
            buffer.append(ch, start, length);
        }
    }
    
    public RSSFeed getFeed() {
        return theChannel;
    }

}