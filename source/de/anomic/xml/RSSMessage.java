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

    public RSSMessage() {
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
