// opensearchdescriptionReader.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.03.2008 on http://yacy.net
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.server.serverByteBuffer;
import de.anomic.server.logging.serverLog;

public class opensearchdescriptionReader extends DefaultHandler {
    
    // statics for item generation and automatic categorization
    static int guidcount = 0;
    //private static final String recordTag = "OpenSearchDescription";
    private static final String[] tagsDef = new String[]{
        "ShortName",
        "LongName",
        "Image",
        "Language",
        "OutputEncoding",
        "InputEncoding",
        "AdultContent",
        "Description",
        "Url",
        "Developer",
        "Query",
        "Tags",
        "Contact",
        "Attribution",
        "SyndicationRight"
        };
    /*
    <?xml version="1.0" encoding="UTF-8"?>
    <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
      <ShortName>YaCy/#[clientname]#</ShortName>
      <LongName>YaCy.net - #[SearchPageGreeting]#</LongName>
      <Image type="image/gif">http://#[thisaddress]#/env/grafics/yacy.gif</Image>
      <Language>en-us</Language>
      <OutputEncoding>UTF-8</OutputEncoding>
      <InputEncoding>UTF-8</InputEncoding>
      <AdultContent>true</AdultContent>
      <Description>YaCy is an open-source GPL-licensed software that can be used for stand-alone search engine installations or as a client for a multi-user P2P-based web indexing cluster. This is the access to peer '#[clientname]#'.</Description>
      <Url type="application/rss+xml" method="GET" template="http://#[thisaddress]#/yacysearch.rss?search={searchTerms}&amp;Enter=Search" />
      <Developer>See http://developer.berlios.de/projects/yacy/</Developer>
      <Query role="example" searchTerms="yacy" />
      <Tags>YaCy P2P Web Search</Tags>
      <Contact>See http://#[thisaddress]#/ViewProfile.html?hash=localhash</Contact>
      <Attribution>YaCy Software &amp;copy; 2004-2007 by Michael Christen et al., YaCy.net; Content: ask peer owner</Attribution>
      <SyndicationRight>open</SyndicationRight>
    </OpenSearchDescription>
    */
    
    private static final HashSet<String> tags = new HashSet<String>();
    static {
        for (int i = 0; i < tagsDef.length; i++) {
            tags.add(tagsDef[i]);
        }
    }
    
    // class variables
    private Item channel;
    private final StringBuffer buffer;
    private boolean parsingChannel;
    private String imageURL;
    private final ArrayList<String> itemsGUID; // a list of GUIDs, so the items can be retrieved by a specific order
    private final HashMap<String, Item> items; // a guid:Item map
    
    
    public opensearchdescriptionReader() {
        itemsGUID = new ArrayList<String>();
        items = new HashMap<String, Item>();
        buffer = new StringBuffer();
        channel = null;
        parsingChannel = false;
    }
    
    public opensearchdescriptionReader(final String path) {
        this();
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            final SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(path, this);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    public opensearchdescriptionReader(final InputStream stream) {
        this();
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            final SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(stream, this);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    public static opensearchdescriptionReader parse(final byte[] a) {

        // check integrity of array
        if ((a == null) || (a.length == 0)) {
            serverLog.logWarning("opensearchdescriptionReader", "response=null");
            return null;
        }
        if (a.length < 100) {
            serverLog.logWarning("opensearchdescriptionReader", "response=" + new String(a));
            return null;
        }
        if (!serverByteBuffer.equals(a, "<?xml".getBytes())) {
            serverLog.logWarning("opensearchdescriptionReader", "response does not contain valid xml");
            return null;
        }
        final String end = new String(a, a.length - 10, 10);
        if (end.indexOf("rss") < 0) {
            serverLog.logWarning("opensearchdescriptionReader", "response incomplete");
            return null;
        }
        
        // make input stream
        final ByteArrayInputStream bais = new ByteArrayInputStream(a);
        
        // parse stream
        opensearchdescriptionReader reader = null;
        try {
            reader = new opensearchdescriptionReader(bais);
        } catch (final Exception e) {
            serverLog.logWarning("opensearchdescriptionReader", "parse exception: " + e);
            return null;
        }
        try { bais.close(); } catch (final IOException e) {}
        return reader;
    }

    public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
        if ("channel".equals(tag)) {
            channel = new Item();
            parsingChannel = true;
        }
    }

    public void endElement(final String uri, final String name, final String tag) {
        if (tag == null) return;
        if ("channel".equals(tag)) {
            parsingChannel = false;
        } else if (parsingChannel) {
            final String value = buffer.toString().trim();
            buffer.setLength(0);
            if (tags.contains(tag)) channel.setValue(tag, value);
        }
    }

    public void characters(final char ch[], final int start, final int length) {
        if (parsingChannel) {
            buffer.append(ch, start, length);
        }
    }

    public Item getChannel() {
        return channel;
    }

    public Item getItem(final int i) {
        // retrieve item by order number
        return getItem(itemsGUID.get(i));
    }

    public Item getItem(final String guid) {
        // retrieve item by guid
        return items.get(guid);
    }

    public int items() {
        return items.size();
    }
    
    public String getImage() {
        return this.imageURL;
    }
    
    public static class Item {
        
        private final HashMap<String, String> map;

        public Item() {
            this.map = new HashMap<String, String>();
            this.map.put("guid", Long.toHexString(System.currentTimeMillis()) + ":" + guidcount++);
        }
        
        public void setValue(final String name, final String value) {
            map.put(name, value);
        }
    }
}