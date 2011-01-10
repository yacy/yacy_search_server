/**
 *  RSSFeed
 *  Copyright 2007 by Michael Peter Christen
 *  First released 16.7.2007 at http://yacy.net
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

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class RSSFeed implements Iterable<RSSMessage> {

    public static final int DEFAULT_MAXSIZE = 1000;
    
    // class variables
    private RSSMessage channel;
    private String imageURL;
    private Map<String, RSSMessage> messages; // a guid:Item map
    private int maxsize;
    
    public RSSFeed(final int maxsize) {
        this.messages = Collections.synchronizedMap(new LinkedHashMap<String, RSSMessage>());
        this.channel = null;
        this.maxsize = maxsize;
    }

    /**
     * make a RSS feed using a set of urls
     * the source string is assigned to all messages as author to mark the messages' origin
     * @param links
     * @param source
     */
    public RSSFeed(Set<MultiProtocolURI> links, String source) {
        this(Integer.MAX_VALUE);
        String u;
        RSSMessage message;
        for (MultiProtocolURI uri: links) {
            u = uri.toNormalform(true, false);
            message = new RSSMessage(u, "", u);
            message.setAuthor(source);
            this.addMessage(message);
        }
    }
    
    public void setChannel(final RSSMessage channelItem) {
        this.channel = channelItem;
    }
    
    public RSSMessage getChannel() {
        return channel;
    }

    public void setImage(final String imageURL) {
        this.imageURL = imageURL;
    }

    public String getImage() {
        return this.imageURL;
    }
    
    public Set<MultiProtocolURI> getLinks() {
        Set<MultiProtocolURI> links = new HashSet<MultiProtocolURI>();
        for (RSSMessage message: messages.values()) {
            try {links.add(new MultiProtocolURI(message.getLink()));} catch (MalformedURLException e) {}
        }
        return links;
    }
    
    public void addMessage(final RSSMessage item) {
        final String guid = item.getGuid();
        messages.put(guid, item);
        // in case that the feed is full (size > maxsize) flush the oldest element
        while (messages.size() > this.maxsize) pollMessage();
    }
    
    public RSSMessage getMessage(final String guid) {
        // retrieve item by guid
        return messages.get(guid);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }
    
    public int size() {
        return messages.size();
    }
    
    public Iterator<RSSMessage> iterator() {
        return new messageIterator();
    }
    
    public RSSMessage pollMessage() {
        // retrieve and delete item
        synchronized (messages) {
            if (messages.isEmpty()) return null;
            final String nextGUID = messages.keySet().iterator().next();
            if (nextGUID == null) return null;
            return messages.remove(nextGUID);
        }
    }

    public class messageIterator implements Iterator<RSSMessage>{
        
        Iterator<String> GUIDiterator;
        String lastGUID;
        int t;
        
        public messageIterator() {
            t = messages.size(); // termination counter
            GUIDiterator = messages.keySet().iterator();
            lastGUID = null;
        }

        public boolean hasNext() {
            if (t <= 0) return false; // ensure termination
            return GUIDiterator.hasNext();
        }

        public RSSMessage next() {
            t--; // ensure termination
            try {
                lastGUID = GUIDiterator.next();
            } catch (ConcurrentModificationException e) {
                return null;
            }
            if (lastGUID == null) return null;
            return messages.get(lastGUID);
        }

        public void remove() {
            if (lastGUID == null) return;
            GUIDiterator.remove();
            messages.remove(lastGUID);
        }
    }
    
}
