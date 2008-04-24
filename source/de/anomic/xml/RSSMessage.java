// RSSMessage.java
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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

public class RSSMessage {

    // statics for item generation and automatic categorization
    private static int guidcount = 0;
    private static final String[] tagsDef = new String[] {
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

    public static final HashSet<String> tags = new HashSet<String>();
    static {
        for (int i = 0; i < tagsDef.length; i++) {
            tags.add(tagsDef[i]);
        }
    }
    
    private HashMap<String, String> map;

    public RSSMessage(String title, String description) {
        this();
        setValue("title", title);
        setValue("description", description);
        setValue("pubDate", new Date().toString());
        setValue("guid", Integer.toHexString((title + description).hashCode()));
    }
    
    public RSSMessage() {
        this.map = new HashMap<String, String>();
        this.map.put("guid", Long.toHexString(System.currentTimeMillis()) + ":" + guidcount++);
    }
    
    public void setValue(String name, String value) {
        map.put(name, value);
    }
    
    public String getAuthor() {
        String s =  map.get("author");
        if (s == null) return ""; else return s;
    }
    
    public String getCopyright() {
        String s =  map.get("copyright");
        if (s == null) return ""; else return s;
    }
    
    public String getCategory() {
        String s = map.get("category");
        if (s == null) return ""; else return s;
    }
    
    public String getTitle() {
        String s = map.get("title");
        if (s == null) return ""; else return s;
    }
    
    public String getLink() {
        String s =  map.get("link");
        if (s == null) return ""; else return s;
    }
    
    public String getReferrer() {
        String s = map.get("referrer");
        if (s == null) return ""; else return s;
    }
    
    public String getLanguage() {
        String s =  map.get("language");
        if (s == null) return ""; else return s;
    }
    
    public String getDescription() {
        String s =  map.get("description");
        if (s == null) return ""; else return s;
    }
    
    public String getCreator() {
        String s =  map.get("creator");
        if (s == null) return ""; else return s;
    }
    
    public String getPubDate() {
        String s =  map.get("pubDate");
        if (s == null) return ""; else return s;
    }
    
    public String getGuid() {
        String s =  map.get("guid");
        if (s == null) return ""; else return s;
    }
    
    public String getDocs() {
        String s =  map.get("docs");
        if (s == null) return ""; else return s;
    }
}
