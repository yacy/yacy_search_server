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

package net.yacy.cora.document.feed;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.CommonPattern;

public class RSSMessage implements Hit, Comparable<RSSMessage>, Comparator<RSSMessage> {

    public static enum Token {

        title(new String[]{"title","atom:title","rss:title",DublinCore.Title.getURIref()}),
        link(new String[]{"link","atom:link","rss:link"}),
        description(new String[]{"description","subtitle","atom:subtitle","rss:description", DublinCore.Description.getURIref()}),
        pubDate(new String[]{"pubDate","lastBuildDate","updated","rss:lastBuildDate","rss:updated"}),
        copyright(new String[]{"copyright","publisher",DublinCore.Publisher.getURIref()}),
        author(new String[]{"author","creator",DublinCore.Creator.getURIref()}),
        subject(new String[]{"subject",DublinCore.Subject.getURIref()}),
        category(new String[]{"category"}),
        referrer(new String[]{"referrer","referer"}),
        language(new String[]{"language",DublinCore.Language.getURIref()}),
        guid(new String[]{"guid"}),
        ttl(new String[]{"ttl"}),
        docs(new String[]{"docs"}),
        size(new String[]{"size","length","yacy:size"}),
        lon(new String[]{"geo:lon",Geo.Long.getURIref()}),
        lat(new String[]{Geo.Lat.getURIref()});
        //point("gml:pos,georss:point,coordinates");
        
        private Set<String> keys;

        private Token(final String[] keylist) {
            this.keys = new HashSet<String>();
            this.keys.addAll(Arrays.asList(keylist));
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
            return this.keys.size() == 0 ? "" : this.keys.iterator().next();
        }
    }
    
    private static Map<String, Token> tokenNick2Token = new HashMap<String, Token>();
    static {
        for (Token t: Token.values()) {
            for (String nick: t.keys) tokenNick2Token.put(nick, t);
        }
    }

    public static Token valueOfNick(String nick) {
        return tokenNick2Token.get(nick);
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
        if (title.length() > 0) this.map.put(Token.title.name(), title);
        if (description.length() > 0) this.map.put(Token.description.name(), description);
        if (link.length() > 0) this.map.put(Token.link.name(), link);
        this.map.put(Token.pubDate.name(), ISO8601Formatter.FORMATTER.format());
        this.map.put(Token.guid.name(), artificialGuidPrefix + Integer.toHexString((title + description + link).hashCode()));
    }

    public RSSMessage(final String title, final String description, final MultiProtocolURL link, final String guid) {
        this.map = new HashMap<String, String>();
        if (title.length() > 0) this.map.put(Token.title.name(), title);
        if (description.length() > 0) this.map.put(Token.description.name(), description);
        this.map.put(Token.link.name(), link.toNormalform(true));
        this.map.put(Token.pubDate.name(), ISO8601Formatter.FORMATTER.format());
        if (guid.length() > 0) this.map.put(Token.guid.name(), guid);
    }

    public RSSMessage() {
        this.map = new HashMap<String, String>();
    }

    public void setValue(final Token token, final String value) {
        if (value.length() > 0) this.map.put(token.name(), value);
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
        return getLink().compareTo(o.getLink());
    }

    @Override
    public int compare(final RSSMessage o1, final RSSMessage o2) {
        return o1.compareTo(o2);
    }

    @Override
    public List<String> getDescriptions() {
        List<String> ds = new ArrayList<String>();
        String d = Token.description.valueFrom(this.map, "");
        if (d.length() > 0) ds.add(d);
        return ds;
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
        if (subject.indexOf(',') >= 0) return CommonPattern.COMMA.split(subject);
        if (subject.indexOf(';') >= 0) return CommonPattern.SEMICOLON.split(subject);
        return CommonPattern.SPACE.split(subject);
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
        if ((guid.isEmpty() || guid.startsWith(artificialGuidPrefix)) &&
            (this.map.containsKey("title") || this.map.containsKey("description") || this.map.containsKey("link"))) {
            guid = calculatedGuidPrefix + Integer.toHexString(getTitle().hashCode() + getDescriptions().hashCode() + getLink().hashCode());
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
        return (size == null || size.isEmpty()) ? -1 : Long.parseLong(size);
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
        setValue(Token.author, author);
    }

    @Override
    public void setCategory(final String category) {
        setValue(Token.category, category);
    }

    @Override
    public void setCopyright(final String copyright) {
        setValue(Token.copyright, copyright);
    }

    @Override
    public void setSubject(final String[] tags) {
        final StringBuilder sb = new StringBuilder(tags.length * 10);
        for (final String tag: tags) sb.append(tag).append(',');
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        setValue(Token.subject, sb.toString());
    }

    @Override
    public void setDescription(final String description) {
        setValue(Token.description, description);
    }

    @Override
    public void setDocs(final String docs) {
        setValue(Token.docs, docs);
    }

    @Override
    public void setGuid(final String guid) {
        setValue(Token.guid, guid);
    }

    @Override
    public void setLanguage(final String language) {
        setValue(Token.language, language);
    }

    @Override
    public void setLink(final String link) {
        setValue(Token.link, link);
    }

    @Override
    public void setPubDate(final Date pubdate) {
        setValue(Token.pubDate, ISO8601Formatter.FORMATTER.format(pubdate));
    }

    @Override
    public void setReferrer(final String referrer) {
        setValue(Token.referrer, referrer);
    }

    @Override
    public void setSize(final long size) {
        setValue(Token.size, Long.toString(size));
    }

    @Override
    public void setTitle(final String title) {
        setValue(Token.title, title);
    }

    public static String sizename(int size) {
        if (size < 1024) return size + " bytes";
        size = size / 1024;
        if (size < 1024) return size + " kbyte";
        size = size / 1024;
        if (size < 1024) return size + " mbyte";
        size = size / 1024;
        return size + " gbyte";
    }
    /*
    public Document toDocument() {
        DigestURI url = new DigestURI(this.getLink());
        List<String> titles = new ArrayList<String>();
        titles.add(this.getTitle());
        return new Document(
                url,
                Classification.ext2mime(url.getFileExtension(), "text/plain"),
                "UTF8",
                null,
                this.getLanguage(),
                Token.subject.valueFrom(this.map, ""),
                titles,
                this.getAuthor(),
                this.getCopyright(),
                null,
                this.getDescription(),
                0.0d, 0.0d,
                this.getFulltext(),
                null,
                null,
                null,
                false);
    }
    */
}
