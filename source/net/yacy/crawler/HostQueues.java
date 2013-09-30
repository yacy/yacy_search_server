/**
 *  HostQueues
 *  Copyright 2013 by Michael Christen
 *  First released 24.09.2013 at http://yacy.net
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

package net.yacy.crawler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;

/**
 * wrapper for single HostQueue queues; this is a collection of such queues.
 * All these queues are stored in a common directory for the queue stacks
 */
public class HostQueues {

    private final File queuesPath;
    private final boolean useTailCache;
    private final boolean exceed134217727;
    private final Map<String, HostQueue> queues;

    public HostQueues(
            final File queuesPath,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.queuesPath = queuesPath;
        this.useTailCache = useTailCache;
        this.exceed134217727 = exceed134217727;
        
        // create a stack for newly entered entries
        if (!(queuesPath.exists())) queuesPath.mkdir(); // make the path
        this.queuesPath.mkdirs();
        this.queues = new HashMap<String, HostQueue>();
        String[] list = this.queuesPath.list();
        for (String queuefile: list) {
            if (queuefile.endsWith(HostQueue.indexSuffix)) {
                String hosthash = queuefile.substring(0, queuefile.length() - HostQueue.indexSuffix.length());
                HostQueue queue = new HostQueue(this.queuesPath, hosthash, this.useTailCache, this.exceed134217727);
                this.queues.put(hosthash, queue);
            }
        }
    }

    public synchronized void close() {
        for (HostQueue queue: this.queues.values()) queue.close();
        this.queues.clear();
    }

    public void clear() {
        for (HostQueue queue: this.queues.values()) queue.clear();
        this.queues.clear();
    }

    public Request get(final byte[] urlhash) throws IOException {
        String hosthash = ASCII.String(urlhash, 6, 6);
        HostQueue queue = this.queues.get(hosthash);
        if (queue == null) return null;
        return queue.get(urlhash);
    }

    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, SpaceExceededException {
        int c = 0;
        for (HostQueue queue: this.queues.values()) c += queue.removeAllByProfileHandle(profileHandle, timeout);
        return c;
    }

    public synchronized int remove(final HandleSet urlHashes) throws IOException {
        Map<String, HandleSet> removeLists = new HashMap<String, HandleSet>();
        for (byte[] urlhash: urlHashes) {
            String hosthash = ASCII.String(urlhash, 6, 6);
            HandleSet removeList = removeLists.get(hosthash);
            if (removeList == null) {
                removeList = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 100);
                removeLists.put(hosthash, removeList);
            }
            try {removeList.put(urlhash);} catch (SpaceExceededException e) {}
        }
        int c = 0;
        for (Map.Entry<String, HandleSet> entry: removeLists.entrySet()) {
            HostQueue queue = this.queues.get(entry.getKey());
            if (queue != null) c += queue.remove(entry.getValue());
        }
        return c;
    }

    public boolean has(final byte[] urlhashb) {
        String hosthash = ASCII.String(urlhashb, 6, 6);
        HostQueue queue = this.queues.get(hosthash);
        if (queue == null) return false;
        return queue.has(urlhashb);
    }

    public int size() {
        int c = 0;
        for (HostQueue queue: this.queues.values()) c += queue.size();
        return c;
    }

    public boolean isEmpty() {
        for (HostQueue queue: this.queues.values()) if (!queue.isEmpty()) return false;
        return true;
    }
    
    /**
     * push a request to one of the host queues. If the queue does not exist, it is created
     * @param entry
     * @param profile
     * @param robots
     * @return null if everything is ok or a string with an error message if the push is not allowed according to the crawl profile or robots
     * @throws IOException
     * @throws SpaceExceededException
     */
    public String push(final Request entry, CrawlProfile profile, final RobotsTxt robots) throws IOException, SpaceExceededException {
        String hosthash = ASCII.String(entry.url().hash(), 6, 6);
        HostQueue queue = this.queues.get(hosthash);
        if (queue == null) {
            queue = new HostQueue(this.queuesPath, hosthash, this.useTailCache, this.exceed134217727);
            this.queues.put(hosthash, queue);
        }
        return queue.push(entry, profile, robots);
    }
    
    /**
     * remove one request from all stacks except from those as listed in notFromHost
     * @param notFromHost do not collect from these hosts
     * @return a list of requests
     * @throws IOException
     */
    public List<Request> pop(Set<String> notFromHost) throws IOException {
        ArrayList<Request> requests = new ArrayList<Request>();
        for (Map.Entry<String, HostQueue> entry: this.queues.entrySet()) {
            if (notFromHost.contains(entry.getKey())) continue;
            Request r = entry.getValue().pop();
            if (r != null) requests.add(r);
        }
        return requests;
    }

}
