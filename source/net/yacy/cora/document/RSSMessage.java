/**
 *  RSSMessage
 *  Copyright 2007 by Michael Peter Christen
 *  First released 16.7.2007 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.document;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.protocol.HeaderFramework;

public class RSSMessage implements Hit, Comparable<RSSMessage>, Comparator<RSSMessage> {

    public static enum Token {

        title("title,atom:title,rss:title"),
        link("link,rss:link,atom:link"),
        description("description,rss:description,subtitle,atom:subtitle"),
        pubDate("pubDate,lastBuildDate,rss:lastBuildDate,updated,rss:updated"),
        copyright("copyright,dc:publisher,publisher"),
        author("author,dc:creator,creator"),
        subject("subject,dc:subject"),
        category("category"),
        referrer("referrer,referer"),
        language("language"),
        guid("guid"),
        ttl("ttl"),
        docs("docs"),
        size("size,length"),
        lon("geo:long,geo:lon"),
        lat("geo:lat");
        //point("gml:pos,georss:point,coordinates");
        
        private Set<String> keys;
        
        private Token(String keylist) {
            String[] k = keylist.split(",");
            this.keys = new HashSet<String>();
            this.keys.addAll(Arrays.asList(k));
        }
        
        public String valueFrom(Map<String, String> map, String dflt) {
            String value;
            for (String key: this.keys) {
                value = map.get(key);
                if (value != null) return value;
            }
            return dflt;
        }
        
        public Set<String> keys() {
            return this.keys;
        }
    }

    private static String artificialGuidPrefix = "c0_";
    public static final RSSMessage POISON = new RSSMessage("", "", "");
    
    public static final HashSet<String> tags = new HashSet<String>();
    static {
        for (Token token: Token.values()) {
            tags.addAll(token.keys());
        }
    }
    
    private final Map<String, String> map;

    public RSSMessage(final String title, final String description, final String link) {
        this.map = new ConcurrentHashMap<String, String>();
        map.put("title", title);
        map.put("description", description);
        map.put("link", link);
        map.put("pubDate", ISO8601Formatter.FORMATTER.format());
        map.put("guid", artificialGuidPrefix + Integer.toHexString((title + description + link).hashCode()));
    }
    
    public RSSMessage() {
        this.map = new ConcurrentHashMap<String, String>();
    }
    
    public void setValue(final String name, final String value) {
        map.put(name, value);
        // if possible generate a guid if not existent so far
        if ((name.equals("title") || name.equals("description") || name.equals("link")) &&
            (!map.containsKey("guid") || map.get("guid").startsWith(artificialGuidPrefix))) {
            map.put("guid", artificialGuidPrefix + Integer.toHexString((getTitle() + getDescription() + getLink()).hashCode()));
        }
    }
    
    public String getTitle() {
        return Token.title.valueFrom(this.map, "");
    }
    
    public String getLink() {
        return Token.link.valueFrom(this.map, "");
    }
    
    @Override
    public boolean equals(Object o) {
        return (o instanceof RSSMessage) && ((RSSMessage) o).getLink().equals(this.getLink());
    }
    
    @Override
    public int hashCode() {
        return getLink().hashCode();
    }

    @Override
    public int compareTo(RSSMessage o) {
        if (!(o instanceof RSSMessage)) return 1;
        return this.getLink().compareTo(o.getLink());
    }

    @Override
    public int compare(RSSMessage o1, RSSMessage o2) {
        return o1.compareTo(o2);
    }
    
    public String getDescription() {
        return Token.description.valueFrom(this.map, "");
    }
    
    public String getAuthor() {
        return Token.author.valueFrom(this.map, "");
    }
    
    public String getCopyright() {
        return Token.copyright.valueFrom(this.map, "");
    }
    
    public String getCategory() {
        return Token.category.valueFrom(this.map, "");
    }
    
    public String[] getSubject() {
        String subject = Token.subject.valueFrom(this.map, "");
        if (subject.indexOf(',') >= 0) return subject.split(",");
        if (subject.indexOf(';') >= 0) return subject.split(";");
        return subject.split(" ");
    }
    
    public String getReferrer() {
        return Token.referrer.valueFrom(this.map, "");
    }
    
    public String getLanguage() {
        return Token.language.valueFrom(this.map, "");
    }
    
    public Date getPubDate() {
        String dateString = Token.pubDate.valueFrom(this.map, "");
        Date date;
        try {
            date = ISO8601Formatter.FORMATTER.parse(dateString);
        } catch (ParseException e) {
            try {
                date = GenericFormatter.SHORT_SECOND_FORMATTER.parse(dateString);
            } catch (ParseException e1) {
                date = HeaderFramework.parseHTTPDate(dateString);
            }
        }
        return date;
    }
    
    public String getGuid() {
        return Token.guid.valueFrom(this.map, "");
    }
    
    public String getTTL() {
        return Token.ttl.valueFrom(this.map, "");
    }
    
    public String getDocs() {
        return Token.docs.valueFrom(this.map, "");
    }
    
    public long getSize() {
        String size = Token.size.valueFrom(this.map, "-1");
        return (size == null || size.length() == 0) ? -1 : Long.parseLong(size);
    }
    
    public String getFulltext() {
        StringBuilder sb = new StringBuilder(300);
        for (String s: map.values()) sb.append(s).append(' ');
        return sb.toString();
    }

    public float getLon() {
        return Float.parseFloat(Token.lon.valueFrom(this.map, "0.0"));
    }

    public float getLat() {
        return Float.parseFloat(Token.lat.valueFrom(this.map, "0.0"));
    }
    
    @Override
    public String toString() {
        return this.map.toString();
    }
    
    public void setAuthor(String author) {
        setValue("author", author);
    }

    public void setCategory(String category) {
        setValue("category", category);
    }

    public void setCopyright(String copyright) {
        setValue("copyright", copyright);
    }
    
    public void setSubject(String[] tags) {
        StringBuilder sb = new StringBuilder(tags.length * 10);
        for (String tag: tags) sb.append(tag).append(',');
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        setValue("subject", sb.toString());
    }
    
    public void setDescription(String description) {
        setValue("description", description);
    }

    public void setDocs(String docs) {
        setValue("docs", docs);
    }

    public void setGuid(String guid) {
        setValue("guid", guid);
    }

    public void setLanguage(String language) {
        setValue("language", language);
    }

    public void setLink(String link) {
        setValue("link", link);
    }

    public void setPubDate(Date pubdate) {
        setValue("pubDate", ISO8601Formatter.FORMATTER.format(pubdate));
    }
    
    public void setReferrer(String referrer) {
        setValue("referrer", referrer);
    }

    public void setSize(long size) {
        setValue("size", Long.toString(size));
    }

    public void setTitle(String title) {
        setValue("title", title);
    }
}
