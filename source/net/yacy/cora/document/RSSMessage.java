/**
 *  RSSMessage
 *  Copyright 2007 by Michael Peter Christen
 *  First released 16.7.2007 at http://yacy.net
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file COPYING.LESSER.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.document;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RSSMessage implements Hit {

    // statics for item generation and automatic categorization
    private static int guidcount = 0;

    public static enum Token {

        title("title"),
        link("link"),
        description("description"),
        pubDate("pubDate"),
        copyright("copyright,dc:publisher,publisher"),
        author("author,dc:creator,creator"),
        subject("subject,dc:subject"),
        category("category"),
        referrer("referrer,referer"),
        language("language"),
        guid("guid"),
        docs("docs");
        
        private Set<String> keys;
        
        private Token(String keylist) {
            String[] k = keylist.split(",");
            this.keys = new HashSet<String>();
            for (String s: k) this.keys.add(s);
        }
        
        public String valueFrom(Map<String, String> map) {
            String value;
            for (String key: this.keys) {
                value = map.get(key);
                if (value != null) return value;
            }
            return "";
        }
        
        public Set<String> keys() {
            return this.keys;
        }
    }
    
    public static final RSSMessage POISON = new RSSMessage("", "", "");
    
    public static final HashSet<String> tags = new HashSet<String>();
    static {
        for (Token token: Token.values()) {
            tags.addAll(token.keys());
        }
    }
    
    private final HashMap<String, String> map;

    public RSSMessage(final String title, final String description, final String link) {
        this();
        setValue("title", title);
        setValue("description", description);
        setValue("link", link);
        setValue("pubDate", new Date().toString());
        setValue("guid", Integer.toHexString((title + description + link).hashCode()));
    }
    
    public RSSMessage() {
        this.map = new HashMap<String, String>();
        this.map.put("guid", Long.toHexString(System.currentTimeMillis()) + ":" + guidcount++);
    }
    
    public void setValue(final String name, final String value) {
        map.put(name, value);
    }
    
    public String getTitle() {
        return Token.title.valueFrom(this.map);
    }
    
    public String getLink() {
        return Token.link.valueFrom(this.map);
    }
    
    public String getDescription() {
        return Token.description.valueFrom(this.map);
    }
    
    public String getAuthor() {
        return Token.author.valueFrom(this.map);
    }
    
    public String getCopyright() {
        return Token.copyright.valueFrom(this.map);
    }
    
    public String getCategory() {
        return Token.category.valueFrom(this.map);
    }
    
    public String[] getSubject() {
        String subject = Token.subject.valueFrom(this.map);
        if (subject.indexOf(',') >= 0) return subject.split(",");
        if (subject.indexOf(';') >= 0) return subject.split(";");
        if (subject.indexOf('|') >= 0) return subject.split("|");
        return subject.split(" ");
    }
    
    public String getReferrer() {
        return Token.referrer.valueFrom(this.map);
    }
    
    public String getLanguage() {
        return Token.language.valueFrom(this.map);
    }
    
    public String getPubDate() {
        return Token.pubDate.valueFrom(this.map);
    }
    
    public String getGuid() {
        return Token.guid.valueFrom(this.map);
    }
    
    public String getDocs() {
        return Token.docs.valueFrom(this.map);
    }
    
    public String getFulltext() {
        StringBuilder sb = new StringBuilder(300);
        for (String s: map.values()) sb.append(s).append(" ");
        return sb.toString();
    }
    
    public String toString() {
        return this.map.toString();
    }
    
    public void setAuthor(String title) {
        // TODO Auto-generated method stub
        
    }

    public void setCategory(String title) {
        // TODO Auto-generated method stub
        
    }

    public void setCopyright(String title) {
        // TODO Auto-generated method stub
        
    }

    public void setCreator(String pubdate) {
        // TODO Auto-generated method stub
        
    }

    public void setDescription(String description) {
        // TODO Auto-generated method stub
        
    }

    public void setDocs(String guid) {
        // TODO Auto-generated method stub
        
    }

    public void setGuid(String guid) {
        // TODO Auto-generated method stub
        
    }

    public void setLanguage(String title) {
        // TODO Auto-generated method stub
        
    }

    public void setLink(String link) {
        // TODO Auto-generated method stub
        
    }

    public void setPubDate(String pubdate) {
        // TODO Auto-generated method stub
        
    }

    public void setReferrer(String title) {
        // TODO Auto-generated method stub
        
    }

    public void setSize(long size) {
        // TODO Auto-generated method stub
        
    }

    public void setSizename(String sizename) {
        // TODO Auto-generated method stub
        
    }

    public void setTitle(String title) {
        // TODO Auto-generated method stub
        
    }
}
