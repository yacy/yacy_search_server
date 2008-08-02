// RSSFeed.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.04.2008 on http://yacy.net
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RSSFeed implements Iterable<RSSMessage> {

    // static channel names of feeds
    public static final String PEERNEWS       = "PEERNEWS";
    public static final String REMOTESEARCH   = "REMOTESEARCH";
    public static final String LOCALSEARCH    = "LOCALSEARCH";
    public static final String REMOTEINDEXING = "REMOTEINDEXING";
    public static final String LOCALINDEXING  = "LOCALINDEXING";
    public static final String INDEXRECEIVE   = "INDEXRECEIVE";
    
    // test:
    // http://localhost:8080/xml/feed.rss?set=PEERNEWS,REMOTESEARCH,LOCALSEARCH,REMOTEINDEXING,LOCALINDEXING
    
    /**
     * the following private channels are declared to prevent that an access to the feed servlet
     * gets results from news channels that are not for the public
     */
    public static final HashSet<String> privateChannels = new HashSet<String>();
    static {
        privateChannels.add(LOCALSEARCH);
        privateChannels.add(LOCALINDEXING);
    }
    
    
    // class variables
    private RSSMessage channel;
    private String imageURL;
    ConcurrentLinkedQueue<String> messageQueue; // a list of GUIDs, so the items can be retrieved by a specific order
    ConcurrentHashMap<String, RSSMessage> messages; // a guid:Item map
    private int maxsize;
    
    public RSSFeed() {
        messageQueue = new ConcurrentLinkedQueue<String>();
        messages = new ConcurrentHashMap<String, RSSMessage>();
        channel = null;
        maxsize = Integer.MAX_VALUE;
    }

    public RSSFeed(final int maxsize) {
        this();
        this.maxsize = maxsize;
    }

    public void setMaxsize(final int maxsize) {
        this.maxsize = maxsize;
        while (messageQueue.size() > this.maxsize) pollMessage();
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
        messageQueue.add(guid);
        messages.put(guid, item);
        while (messageQueue.size() > this.maxsize) pollMessage();
    }
    
    public RSSMessage getMessage(final String guid) {
        // retrieve item by guid
        return messages.get(guid);
    }

    public int size() {
        return messages.size();
    }
    
    public Iterator<RSSMessage> iterator() {
        return new messageIterator();
    }
    
    public RSSMessage pollMessage() {
        // retrieve and delete item
        if (messageQueue.size() == 0) return null;
        final String nextGUID = messageQueue.poll();
        if (nextGUID == null) return null;
        return messages.remove(nextGUID);
    }

    public class messageIterator implements Iterator<RSSMessage>{
        
        Iterator<String> GUIDiterator;
        String lastGUID;
        
        public messageIterator() {
            GUIDiterator = messageQueue.iterator();
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
    
    /**
     * the following static channels object is used to organize a storage array for RSS feeds
     */
    private static final ConcurrentHashMap<String, RSSFeed> channels = new ConcurrentHashMap<String, RSSFeed>();
    
    public static RSSFeed channels(final String channelName) {
        final ConcurrentHashMap<String, RSSFeed> channelss = channels;
        RSSFeed feed = channelss.get(channelName);
        if (feed != null) return feed;
        feed = new RSSFeed();
        feed.setChannel(new RSSMessage(channelName, "", ""));
        feed.maxsize = 100;
        channels.put(channelName, feed);
        return feed;
    }
}
