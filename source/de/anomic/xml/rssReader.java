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

public class rssReader extends DefaultHandler {
    
    // statics for item generation and automatic categorization
    static int guidcount = 0;
    private static final String[] tagsDef = new String[]{
        "author",      //
        "copyright",   //
        "category",    //
        "title",       //
        "link",        //
        "referrer",    //
        "language",    //
        "description", //
        "creator",     //
        "pubDate",     //
        "guid",        //
        "docs"         //
        };

    private static final HashSet<String> tags = new HashSet<String>();
    static {
        for (int i = 0; i < tagsDef.length; i++) {
            tags.add(tagsDef[i]);
        }
    }
    
    // class variables
    private Item channel, item;
    private StringBuffer buffer;
    private boolean parsingChannel, parsingImage, parsingItem;
    private String imageURL;
    private ArrayList<String> itemsGUID; // a list of GUIDs, so the items can be retrieved by a specific order
    private HashMap<String, Item> items; // a guid:Item map
    
    
    public rssReader() {
        itemsGUID = new ArrayList<String>();
        items = new HashMap<String, Item>();
        buffer = new StringBuffer();
        item = null;
        channel = null;
        parsingChannel = false;
        parsingImage = false;
        parsingItem = false;
    }
    
    public rssReader(String path) {
        this();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(path, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public rssReader(InputStream stream) {
        this();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(stream, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static rssReader parse(byte[] a) {

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
        rssReader reader = null;
        try {
            reader = new rssReader(bais);
        } catch (Exception e) {
            serverLog.logWarning("rssReader", "parse exception: " + e);
            return null;
        }
        try { bais.close(); } catch (IOException e) {}
        return reader;
    }

    public void startElement(String uri, String name, String tag, Attributes atts) throws SAXException {
        if ("channel".equals(tag)) {
            channel = new Item();
            parsingChannel = true;
        } else if ("item".equals(tag)) {
            item = new Item();
            parsingItem = true;
        } else if ("image".equals(tag)) {
            parsingImage = true;
        }
    }

    public void endElement(String uri, String name, String tag) {
        if (tag == null) return;
        if ("channel".equals(tag)) {
            parsingChannel = false;
        } else if ("item".equals(tag)) {
            String guid = item.getGuid();
            itemsGUID.add(guid);
            items.put(guid, item);
            parsingItem = false;
        } else if ("image".equals(tag)) {
            parsingImage = false;
        } else if ((parsingImage) && (parsingChannel)) {
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if ("url".equals(tag)) imageURL = value;
        } else if (parsingItem)  {
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if (tags.contains(tag)) item.setValue(tag, value);
        } else if (parsingChannel) {
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if (tags.contains(tag)) channel.setValue(tag, value);
        }
    }

    public void characters(char ch[], int start, int length) {
        if (parsingItem || parsingChannel) {
            buffer.append(ch, start, length);
        }
    }

    public Item getChannel() {
        return channel;
    }

    public Item getItem(int i) {
        // retrieve item by order number
        return getItem((String) itemsGUID.get(i));
    }

    public Item getItem(String guid) {
        // retrieve item by guid
        return (Item) items.get(guid);
    }

    public int items() {
        return items.size();
    }
    
    public String getImage() {
        return this.imageURL;
    }
    
    public static class Item {
        
        private HashMap<String, String> map;

        public Item() {
            this.map = new HashMap<String, String>();
            this.map.put("guid", Long.toHexString(System.currentTimeMillis()) + ":" + guidcount++);
        }
        
        public void setValue(String name, String value) {
            map.put(name, value);
        }
        
        public String getAuthor() {
            return (String) map.get("author");
        }
        
        public String getCopyright() {
            return (String) map.get("copyright");
        }
        
        public String getCategory() {
            return (String) map.get("category");
        }
        
        public String getTitle() {
            return (String) map.get("title");
        }
        
        public String getLink() {
            return (String) map.get("link");
        }
        
        public String getReferrer() {
            return (String) map.get("referrer");
        }
        
        public String getLanguage() {
            return (String) map.get("language");
        }
        
        public String getDescription() {
            return (String) map.get("description");
        }
        
        public String getCreator() {
            return (String) map.get("creator");
        }
        
        public String getPubDate() {
            return (String) map.get("pubDate");
        }
        
        public String getGuid() {
            return (String) map.get("guid");
        }
        
        public String getDocs() {
            return (String) map.get("docs");
        }
    }
}