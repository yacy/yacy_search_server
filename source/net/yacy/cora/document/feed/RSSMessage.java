/**
 *  RSSMessage
 *  Copyright 2007 by Michael Peter Christen
 *  First released 16.7.2007 at https://yacy.net
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
import java.time.ZonedDateTime;
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
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.CommonPattern;

/**
 * Represents a RSS/Atom feed entry or channel.
 */
public class RSSMessage implements Hit, Comparable<RSSMessage>, Comparator<RSSMessage> {

	/**
	 * Feed entry data tokens, mostly matching Atom and RSS elements.
	 * @see <a href="http://www.rssboard.org/rss-specification">Latest RSS specification</a>
	 * @see <a href="https://tools.ietf.org/html/rfc4287">The Atom Syndication Format RFC</a>
	 */
    public static enum Token {

    	/** Human-readable title for an entry or a feed. */
        title(new String[]{"title","atom:title","rss:title",DublinCore.Title.getURIref()}),

        /** Reference from an entry or feed to a Web resource URL */
        link(new String[]{"link","atom:link","rss:link"}),

        /** Human-readable description or subtitle for an entry or a feed. */
        description(new String[]{"description","subtitle","atom:subtitle","rss:description", DublinCore.Description.getURIref()}),

        /** The publication date for content in a feed or for an antry. */
        pubDate(new String[]{"pubDate","lastBuildDate","updated","rss:lastBuildDate","rss:updated"}),

        /** Copyright notice for content in the channel. */
        copyright(new String[]{"copyright","publisher",DublinCore.Publisher.getURIref()}),

        /** The author of an item (Email address) */
        author(new String[]{"author","creator",DublinCore.Creator.getURIref()}),

        subject(new String[]{"subject",DublinCore.Subject.getURIref()}),

        /** One or more categories a channel or item belongs to */
        category(new String[]{"category"}),

        referrer(new String[]{"referrer","referer"}),

        /** The language the channel is written in. */
        language(new String[]{"language",DublinCore.Language.getURIref()}),

        /** A string that uniquely identifies an item (RSS 2.0) */
        guid(new String[]{"guid"}),

        /** URL describing a media object that is attached to a feed item */
        enclosure(new String[]{"enclosure"}),

        /**  Time To Live : number of minutes that indicates how long a channel (RSS 2.0) can be cached before refreshing from the source. */
        ttl(new String[]{"ttl"}),

        /** URL to the documentation for the format used in the RSS file (for example http://www.rssboard.org/rss-specification) */
        docs(new String[]{"docs"}),

        size(new String[]{"size", "length", YaCyMetadata.size.getURIref()}),
        lon(new String[]{Geo.Long.getURIref(), "geo:lon"}), // include possible misspelling geo:lon (instead of geo:long)
        lat(new String[]{Geo.Lat.getURIref()});
        //point("gml:pos,georss:point,coordinates");

        private Set<String> keys;

        private Token(final String[] keylist) {
            this.keys = new HashSet<>();
            this.keys.addAll(Arrays.asList(keylist));
            this.keys.add(this.name());
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

    private static Map<String, Token> tokenNick2Token = new HashMap<>();
    static {
        for (final Token t: Token.values()) {
            for (final String nick: t.keys) tokenNick2Token.put(nick, t);
        }
    }

    public static Token valueOfNick(final String nick) {
        return tokenNick2Token.get(nick);
    }

    private static String artificialGuidPrefix = "c0_";
    private static String calculatedGuidPrefix = "c1_";
    public static final RSSMessage POISON = new RSSMessage("", "", "");

    public static final HashSet<String> tags = new HashSet<>();
    static {
        for (final Token token: Token.values()) {
            tags.addAll(token.keys());
        }
    }

    private final Map<String, String> map;

    public RSSMessage(final String title, final String description, final String link) {
        this.map = new HashMap<>();
        if (title.length() > 0) this.map.put(Token.title.name(), title);
        if (description.length() > 0) this.map.put(Token.description.name(), description);
        if (link.length() > 0) this.map.put(Token.link.name(), link);
        this.map.put(Token.pubDate.name(), HeaderFramework.formatNowRFC1123());
        this.map.put(Token.guid.name(), artificialGuidPrefix + Integer.toHexString((title + description + link).hashCode()));
    }

    public RSSMessage(final String title, final String description, final MultiProtocolURL link, final String guid) {
        this.map = new HashMap<>();
        if (title.length() > 0) this.map.put(Token.title.name(), title);
        if (description.length() > 0) this.map.put(Token.description.name(), description);
        this.map.put(Token.link.name(), link.toNormalform(true));
        this.map.put(Token.pubDate.name(), HeaderFramework.formatNowRFC1123());
        if (guid.length() > 0) {
        	this.map.put(Token.guid.name(), guid);
        }
    }

    public RSSMessage() {
        this.map = new HashMap<>();
    }

    public void setValue(final Token token, final String value) {
        if (value.length() > 0) {
        	this.map.put(token.name(), value);
        }
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
        final List<String> ds = new ArrayList<>();
        final String d = Token.description.valueFrom(this.map, "");
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
        return CommonPattern.SPACES.split(subject);
    }

    @Override
    public String getReferrer() {
        return Token.referrer.valueFrom(this.map, "");
    }

    @Override
    public String getLanguage() {
        return Token.language.valueFrom(this.map, "");
    }

    /**
     * @return publishDate or null
     */
    @Override
    public Date getPubDate() {
        final String dateString = Token.pubDate.valueFrom(this.map, "");
        if (!dateString.isEmpty()) { // skip parse exception on empty string
            Date date;
            try {
				date = Date.from(ZonedDateTime.parse(dateString, HeaderFramework.RFC1123_FORMATTER).toInstant());
            } catch (final RuntimeException e) {
                try {
                    date = GenericFormatter.SHORT_SECOND_FORMATTER.parse(dateString, 0).getTime();
                } catch (final ParseException e1) {
                    date = HeaderFramework.parseHTTPDate(dateString); // returns null on parse error
                }
            }
            return date;
        }
        return null;
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

    @Override
    public String getEnclosure() {
    	return Token.enclosure.valueFrom(this.map, "");
    }

    public String getTTL() {
        return Token.ttl.valueFrom(this.map, "");
    }

    /**
     * A URL that points to the documentation for the format used in the RSS file.
     * @return url string
     */
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
    public double getLon() {
        return Double.parseDouble(Token.lon.valueFrom(this.map, "0.0"));
    }

    @Override
    public double getLat() {
        return Double.parseDouble(Token.lat.valueFrom(this.map, "0.0"));
    }

    @Override
    public String toString() {
        return this.toString(true);
    }

    public String toString(final boolean withItemTag) {
        final StringBuilder sb = new StringBuilder();
        if (withItemTag) sb.append("<item>\n");
        if (this.map.containsKey(Token.title.name())) sb.append("<title>").append(this.map.get(Token.title.name())).append("</title>\n");
        if (this.map.containsKey(Token.link.name())) sb.append("<link>").append(this.map.get(Token.link.name())).append("</link>\n");
        if (this.map.containsKey(Token.description.name())) sb.append("<description>").append(this.map.get(Token.description.name())).append("</description>\n");
        if (this.map.containsKey(Token.pubDate.name())) sb.append("<pubDate>").append(this.map.get(Token.pubDate.name())).append("</pubDate>\n");
        if (this.map.containsKey(Token.guid.name())) sb.append("<guid isPermaLink=\"false\">").append(this.map.get(Token.guid.name())).append("</guid>\n");
        if (withItemTag) sb.append("</item>\n");
        return sb.toString();
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

    /**
     * set a URL that points to the documentation for the format used in the RSS file.
     * @param docs e.g. "http://www.rssboard.org/rss-specification"
     */
    @Override
    public void setDocs(final String docs) {
        setValue(Token.docs, docs);
    }

    @Override
    public void setGuid(final String guid) {
        setValue(Token.guid, guid);
    }

    @Override
    public void setEnclosure(final String enclosure) {
    	setValue(Token.enclosure, enclosure);
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
        setValue(Token.pubDate, HeaderFramework.formatNowRFC1123());
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
