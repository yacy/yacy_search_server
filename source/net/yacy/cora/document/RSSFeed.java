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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class RSSFeed implements Iterable<Hit> {

    // class variables
    private RSSMessage channel;
    private String imageURL;
    private Map<String, RSSMessage> messages; // a guid:Item map
    private int maxsize;
    
    public RSSFeed() {
        messages = Collections.synchronizedMap(new LinkedHashMap<String, RSSMessage>());
        channel = null;
        maxsize = Integer.MAX_VALUE;
    }

    public RSSFeed(final int maxsize) {
        this();
        this.maxsize = maxsize;
    }

    public void setMaxsize(final int maxsize) {
        this.maxsize = maxsize;
        while (messages.size() > this.maxsize) pollMessage();
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
    
    public void addMessage(final RSSMessage item) {
        final String guid = item.getGuid();
        messages.put(guid, item);
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
    
    public Iterator<Hit> iterator() {
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

    public class messageIterator implements Iterator<Hit>{
        
        Iterator<String> GUIDiterator;
        String lastGUID;
        
        public messageIterator() {
            GUIDiterator = messages.keySet().iterator();
            lastGUID = null;
        }

        public boolean hasNext() {
            return GUIDiterator.hasNext();
        }

        public RSSMessage next() {
            lastGUID = GUIDiterator.next();
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
