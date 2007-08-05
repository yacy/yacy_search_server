// crawlHandler.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.07.2007 on http://yacy.net
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.helpers.DefaultHandler;

public class crawlHandler extends DefaultHandler {
    
    // statics for item generation and automatic categorization
    private static int guidcount = 0;
    private static final String[] startpointTags = new String[]{
        "author",      //
        "copyright",   //
        "category",    //
        "title",       //
        "link",        //
        "language",    //
        "description", //
        "creator",     //
        "pubDate",     //
        "guid",        //
        "docs"         //
        };

    private static final HashSet startpointTagsSet = new HashSet();
    static {
        for (int i = 0; i < startpointTags.length; i++) {
            startpointTagsSet.add(startpointTags[i]);
        }
    }
    
    // class variables
    private Startpoint channel, startpoint;
    private StringBuffer buffer;
    private boolean parsingAttributes, parsingStartpoint;
    private ArrayList startpointsGUID; // a list of GUIDs, so the items can be retrieved by a specific order
    private HashMap startpoints; // a guid:Item map
    
    
    public crawlHandler(String path) {
        init();
        parse(path);
    }
    
    public crawlHandler(InputStream stream) {
        init();
        parse(stream);
    }
    
    private void init() {
        startpointsGUID = new ArrayList();
        startpoints = new HashMap();
        buffer = new StringBuffer();
        startpoint = null;
        channel = null;
        parsingAttributes = false;
        parsingStartpoint = false;
    }
    
    private void parse(String path) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(path, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void parse(InputStream stream) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(stream, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startElement(String uri, String name, String tag, Attributes atts) {
        if ("channel".equals(tag)) {
            channel = new Startpoint();
            parsingAttributes = true;
        } else if ("item".equals(tag)) {
            startpoint = new Startpoint();
            parsingStartpoint = true;
        }
    }

    public void endElement(String uri, String name, String tag) {
        if (tag == null) return;
        if ("channel".equals(tag)) {
            parsingAttributes = false;
        } else if ("item".equals(tag)) {
            String guid = startpoint.getGuid();
            startpointsGUID.add(guid);
            startpoints.put(guid, startpoint);
            parsingStartpoint = false;
        } else if (parsingStartpoint)  {
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if (startpointTagsSet.contains(tag)) startpoint.setValue(tag, value);
        } else if (parsingAttributes) {
            String value = buffer.toString().trim();
            buffer.setLength(0);
            if (startpointTagsSet.contains(tag)) channel.setValue(tag, value);
        }
    }

    public void characters(char ch[], int start, int length) {
        if (parsingStartpoint || parsingAttributes) {
            buffer.append(ch, start, length);
        }
    }

    public Startpoint getChannel() {
        return channel;
    }

    public Startpoint getStartpoint(int i) {
        // retrieve item by order number
        return getStartpoint((String) startpointsGUID.get(i));
    }

    public Startpoint getStartpoint(String guid) {
        // retrieve item by guid
        return (Startpoint) startpoints.get(guid);
    }

    public int startpoints() {
        return startpoints.size();
    }
    
    public static class Attributes {
        
        private HashMap map;

        public Attributes() {
            this.map = new HashMap();
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
    
    public static class Startpoint {
        
        private HashMap map;

        public Startpoint() {
            this.map = new HashMap();
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