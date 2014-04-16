/**
 *  Balancer
 *  Copyright 2014 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.04.2014 at http://yacy.net
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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;

public interface Balancer {

    /**
     * close the balancer object
     */
    public void close();

    /**
     * delete all urls from the stack
     */
    public void clear();

    /**
     * get one url from the crawl stack
     * @param urlhash
     * @return the request for an url by given url hash
     * @throws IOException
     */
    public Request get(final byte[] urlhash) throws IOException;

    /**
     * delete all urls from the stack by given profile handle
     * @param profileHandle
     * @param timeout
     * @return the number of removed urls
     * @throws IOException
     * @throws SpaceExceededException
     */
    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, SpaceExceededException;

    /**
     * delete all urls which are stored for given host hashes
     * @param hosthashes
     * @return number of deleted urls
     */
    public int removeAllByHostHashes(final Set<String> hosthashes);
    
    /**
     * @param urlHashes, a list of hashes that shall be removed
     * @return number of entries that had been removed
     * @throws IOException
     */
    public int remove(final HandleSet urlHashes) throws IOException;

    /**
     * check if given url hash is contained in the balancer stack
     * @param urlhashb
     * @return true if the url is queued here, false otherwise
     */
    public boolean has(final byte[] urlhashb);

    /**
     * get the size of the stack
     * @return the number of urls waiting to be loaded
     */
    public int size();

    /**
     * check if stack is empty
     * @return true iff size() == 0
     */
    public boolean isEmpty();

    /**
     * push a crawl request on the balancer stack
     * @param entry
     * @return null if this was successful or a String explaining what went wrong in case of an error
     * @throws IOException
     * @throws SpaceExceededException
     */
    public String push(final Request entry, CrawlProfile profile, final RobotsTxt robots) throws IOException, SpaceExceededException;

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names to an integer array: {the size of the domain stack, guessed delta waiting time}
     */
    public Map<String, Integer[]> getDomainStackHosts(RobotsTxt robots);
    
    /**
     * get lists of crawl request entries for a specific host
     * @param host
     * @param maxcount
     * @param maxtime
     * @return a list of crawl loader requests
     */
    public List<Request> getDomainStackReferences(final String host, int maxcount, final long maxtime);

    /**
     * get the next entry in this crawl queue in such a way that the domain access time delta is maximized
     * and always above the given minimum delay time. An additional delay time is computed using the robots.txt
     * crawl-delay time which is always respected. In case the minimum time cannot ensured, this method pauses
     * the necessary time until the url is released and returned as CrawlEntry object. In case that a profile
     * for the computed Entry does not exist, null is returned
     * @param delay true if the requester demands forced delays using explicit thread sleep
     * @param profile
     * @return a url in a CrawlEntry object
     * @throws IOException
     * @throws SpaceExceededException
     */
    public Request pop(final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException;

    /**
     * iterate through all requests in the queue
     * @return
     * @throws IOException
     */
    public Iterator<Request> iterator() throws IOException;

}
