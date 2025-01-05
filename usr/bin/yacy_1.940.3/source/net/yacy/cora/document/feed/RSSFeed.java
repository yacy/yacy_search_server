/**
 *  RSSFeed
 *  Copyright 2007 by Michael Peter Christen
 *  First released 16.7.2007 at https://yacy.net
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

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.id.MultiProtocolURL;

public class RSSFeed implements Iterable<RSSMessage> {

    public static final int DEFAULT_MAXSIZE = 10000;

    // class variables

    /** Single required element  see http://www.rssboard.org/rss-profile#element-channel */
    private RSSMessage channel = null;

    /** A guid:Item map */
    private final Map<String, RSSMessage> messages;
    private final int maxsize;

    /** Set to true when maxsize messages limit has been exceeded and exceeding messages have been discarded */
    private boolean maxSizeExceeded;



    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<rss version=\"2.0\"\n");
        sb.append("  xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\"\n");
        sb.append("  xmlns:atom=\"http://www.w3.org/2005/Atom\"\n");
        sb.append(">\n");
        sb.append("<channel>\n");
        if (this.channel != null) sb.append(this.channel.toString(false));
        sb.append("<opensearch:startIndex>0</opensearch:startIndex>\n");
        sb.append("<opensearch:itemsPerPage>" + this.size() + "</opensearch:itemsPerPage>\n");
        sb.append("<opensearch:totalResults>" + this.size() + "</opensearch:totalResults>\n");
        for (final RSSMessage item: this.messages.values()) {
            sb.append(item.toString());
        }
        sb.append("</channel>\n");
        sb.append("</rss>\n");
        return sb.toString();
    }

    public RSSFeed(final int maxsize) {
        this.messages = Collections.synchronizedMap(new LinkedHashMap<String, RSSMessage>());
        this.channel = null;
        this.maxsize = maxsize;
        this.maxSizeExceeded = false;
    }

    /**
     * make a RSS feed using a set of urls
     * the source string is assigned to all messages as author to mark the messages' origin
     * @param links
     * @param source
     */
    public RSSFeed(final Set<MultiProtocolURL> links, final String source) {
        this(Integer.MAX_VALUE);
        String u;
        RSSMessage message;
        for (final MultiProtocolURL uri: links) {
            u = uri.toNormalform(true);
            message = new RSSMessage(u, "", u);
            message.setAuthor(source);
            this.addMessage(message);
        }
    }

    public void setChannel(final RSSMessage channelItem) {
        this.channel = channelItem;
    }

    /**
     * Return Channel element
     * (This element is required and must contain three child elements: description, link and title)
     * see http://www.rssboard.org/rss-profile#element-channel
     * @return RSSMessage with channel elements or empty RSSMessage
     */
    public RSSMessage getChannel() {
        // This element is required and must contain three child elements: description, link and title.
        if (this.channel==null) this.channel = new RSSMessage();
        return this.channel;
    }

    public Set<MultiProtocolURL> getLinks() {
        final Set<MultiProtocolURL> links = new HashSet<>();
        for (final RSSMessage message: this.messages.values()) {
            try {links.add(new MultiProtocolURL(message.getLink()));} catch (final MalformedURLException e) {}
        }
        return links;
    }

    public void addMessage(final RSSMessage item) {
        final String guid = item.getGuid();
        this.messages.put(guid, item);
        // in case that the feed is full (size > maxsize) flush the oldest element
        while (this.messages.size() > this.maxsize) {
        	this.maxSizeExceeded = true;
        	pollMessage();
        }
    }

    public RSSMessage getMessage(final String guid) {
        // retrieve item by guid
        return this.messages.get(guid);
    }

    public boolean isEmpty() {
        return this.messages.isEmpty();
    }

    public int size() {
        return this.messages.size();
    }

    /**
     * @return true when maxsize messages limit has been exceeded and exceeding messages have been discarded
     */
    public boolean isMaxSizeExceeded() {
		return this.maxSizeExceeded;
	}

    @Override
    public Iterator<RSSMessage> iterator() {
        return new messageIterator();
    }

    public RSSMessage pollMessage() {
        // retrieve and delete item
        synchronized (this.messages) {
            if (this.messages.isEmpty()) return null;
            final String nextGUID = this.messages.keySet().size() == 0 ? null : this.messages.keySet().iterator().next();
            if (nextGUID == null) return null;
            return this.messages.remove(nextGUID);
        }
    }

    public class messageIterator implements Iterator<RSSMessage>{

        Iterator<String> GUIDiterator;
        String lastGUID;
        int t;

        public messageIterator() {
            this.t = RSSFeed.this.messages.size(); // termination counter
            this.GUIDiterator = RSSFeed.this.messages.keySet().iterator();
            this.lastGUID = null;
        }

        @Override
        public boolean hasNext() {
            if (this.t <= 0) return false; // ensure termination
            return this.GUIDiterator.hasNext();
        }

        @Override
        public RSSMessage next() {
            this.t--; // ensure termination
            try {
                this.lastGUID = this.GUIDiterator.next();
            } catch (final ConcurrentModificationException e) {
                //ConcurrentLog.logException(e);
                this.lastGUID = null;
            }
            if (this.lastGUID == null) return null;
            return RSSFeed.this.messages.get(this.lastGUID);
        }

        @Override
        public void remove() {
            if (this.lastGUID == null) return;
            this.GUIDiterator.remove();
            RSSFeed.this.messages.remove(this.lastGUID);
        }
    }

}
