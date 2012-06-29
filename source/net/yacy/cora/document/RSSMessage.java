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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.kelondro.data.meta.DigestURI;

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

        private Token(final String keylist) {
            final String[] k = keylist.split(",");
            this.keys = new HashSet<String>();
            this.keys.addAll(Arrays.asList(k));
        }

        public String valueFrom(final Map<String, String> map, final String dflt) {
            String value;
            for (final String key: this.keys) {
                value = map.get(key);
                if (value != null) return value;
            }
            return dflt;
        }

        public Set<String> keys() {
            return this.keys;
        }

        @Override
        public String toString() {
            return this.keys.iterator().next();
        }
    }

    private static String artificialGuidPrefix = "c0_";
    private static String calculatedGuidPrefix = "c1_";
    public static final RSSMessage POISON = new RSSMessage("", "", "");

    public static final HashSet<String> tags = new HashSet<String>();
    static {
        for (final Token token: Token.values()) {
            tags.addAll(token.keys());
        }
    }

    private final Map<String, String> map;

    public RSSMessage(final String title, final String description, final String link) {
        this.map = new HashMap<String, String>();
        this.map.put("title", title);
        this.map.put("description", description);
        this.map.put("link", link);
        this.map.put("pubDate", ISO8601Formatter.FORMATTER.format());
        this.map.put("guid", artificialGuidPrefix + Integer.toHexString((title + description + link).hashCode()));
    }

    public RSSMessage(final String title, final String description, final DigestURI link) {
        this.map = new HashMap<String, String>();
        this.map.put("title", title);
        this.map.put("description", description);
        this.map.put("link", link.toNormalform(true, false));
        this.map.put("pubDate", ISO8601Formatter.FORMATTER.format());
        this.map.put("guid", ASCII.String(link.hash()));
    }

    public RSSMessage() {
        this.map = new HashMap<String, String>();
    }

    public void setValue(final String name, final String value) {
        this.map.put(name, value);
    }

    @Override
    public String getTitle() {
        return Token.title.valueFrom(this.map, "");
    }

    @Override
    public String getLink() {
        return Token.link.valueFrom(this.map, "");
    }

    @Override
    public boolean equals(final Object o) {
        return (o instanceof RSSMessage) && ((RSSMessage) o).getLink().equals(getLink());
    }

    @Override
    public int hashCode() {
        return getLink().hashCode();
    }

    @Override
    public int compareTo(final RSSMessage o) {
        if (!(o instanceof RSSMessage)) return 1;
        return getLink().compareTo(o.getLink());
    }

    @Override
    public int compare(final RSSMessage o1, final RSSMessage o2) {
        return o1.compareTo(o2);
    }

    @Override
    public String getDescription() {
        return Token.description.valueFrom(this.map, "");
    }

    @Override
    public String getAuthor() {
        return Token.author.valueFrom(this.map, "");
    }

    @Override
    public String getCopyright() {
        return Token.copyright.valueFrom(this.map, "");
    }

    @Override
    public String getCategory() {
        return Token.category.valueFrom(this.map, "");
    }

    @Override
    public String[] getSubject() {
        final String subject = Token.subject.valueFrom(this.map, "");
        if (subject.indexOf(',') >= 0) return subject.split(",");
        if (subject.indexOf(';') >= 0) return subject.split(";");
        return subject.split(" ");
    }

    @Override
    public String getReferrer() {
        return Token.referrer.valueFrom(this.map, "");
    }

    @Override
    public String getLanguage() {
        return Token.language.valueFrom(this.map, "");
    }

    @Override
    public Date getPubDate() {
        final String dateString = Token.pubDate.valueFrom(this.map, "");
        Date date;
        try {
            date = ISO8601Formatter.FORMATTER.parse(dateString);
        } catch (final ParseException e) {
            try {
                date = GenericFormatter.SHORT_SECOND_FORMATTER.parse(dateString);
            } catch (final ParseException e1) {
                date = HeaderFramework.parseHTTPDate(dateString);
            }
        }
        return date;
    }

    @Override
    public String getGuid() {
        String guid = Token.guid.valueFrom(this.map, "");
        if ((guid.length() == 0 || guid.startsWith(artificialGuidPrefix)) &&
            (this.map.containsKey("title") || this.map.containsKey("description") || this.map.containsKey("link"))) {
            guid = calculatedGuidPrefix + Integer.toHexString(getTitle().hashCode() + getDescription().hashCode() + getLink().hashCode());
            this.map.put("guid", guid);
        }
        return guid;
    }

    public String getTTL() {
        return Token.ttl.valueFrom(this.map, "");
    }

    @Override
    public String getDocs() {
        return Token.docs.valueFrom(this.map, "");
    }

    @Override
    public long getSize() {
        final String size = Token.size.valueFrom(this.map, "-1");
        return (size == null || size.length() == 0) ? -1 : Long.parseLong(size);
    }

    public String getFulltext() {
        final StringBuilder sb = new StringBuilder(300);
        for (final String s: this.map.values()) sb.append(s).append(' ');
        return sb.toString();
    }

    @Override
    public float getLon() {
        return Float.parseFloat(Token.lon.valueFrom(this.map, "0.0"));
    }

    @Override
    public float getLat() {
        return Float.parseFloat(Token.lat.valueFrom(this.map, "0.0"));
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

    @Override
    public void setAuthor(final String author) {
        setValue("author", author);
    }

    @Override
    public void setCategory(final String category) {
        setValue("category", category);
    }

    @Override
    public void setCopyright(final String copyright) {
        setValue("copyright", copyright);
    }

    @Override
    public void setSubject(final String[] tags) {
        final StringBuilder sb = new StringBuilder(tags.length * 10);
        for (final String tag: tags) sb.append(tag).append(',');
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        setValue("subject", sb.toString());
    }

    @Override
    public void setDescription(final String description) {
        setValue("description", description);
    }

    @Override
    public void setDocs(final String docs) {
        setValue("docs", docs);
    }

    @Override
    public void setGuid(final String guid) {
        setValue("guid", guid);
    }

    @Override
    public void setLanguage(final String language) {
        setValue("language", language);
    }

    @Override
    public void setLink(final String link) {
        setValue("link", link);
    }

    @Override
    public void setPubDate(final Date pubdate) {
        setValue("pubDate", ISO8601Formatter.FORMATTER.format(pubdate));
    }

    @Override
    public void setReferrer(final String referrer) {
        setValue("referrer", referrer);
    }

    @Override
    public void setSize(final long size) {
        setValue("size", Long.toString(size));
    }

    @Override
    public void setTitle(final String title) {
        setValue("title", title);
    }
}
