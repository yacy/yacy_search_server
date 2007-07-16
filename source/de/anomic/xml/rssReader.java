package de.anomic.xml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class rssReader extends DefaultHandler {
    
    // statics for item generation and automatic categorization
    private static int guidcount = 0;
    private static final String[] tagsDef = new String[]{
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

    private static final HashSet tags = new HashSet();
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
    private ArrayList itemsGUID; // a list of GUIDs, so the items can be retrieved by a specific order
    private HashMap items; // a guid:Item map
    
    
    public rssReader(String path) {
        init();
        parse(path);
    }
    
    public rssReader(InputStream stream) {
        init();
        parse(stream);
    }
    
    private void init() {
        itemsGUID = new ArrayList();
        items = new HashMap();
        buffer = new StringBuffer();
        item = null;
        channel = null;
        parsingChannel = false;
        parsingImage = false;
        parsingItem = false;
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
        
        private HashMap map;

        public Item() {
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