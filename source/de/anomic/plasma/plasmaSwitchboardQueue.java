// plasmaSwitchboardQueueEntry.java 
// --------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.crawler.CrawlProfile;
import de.anomic.index.indexURLReference;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public class plasmaSwitchboardQueue {

    kelondroStack sbQueueStack;
    CrawlProfile profiles;
    plasmaWordIndex wordIndex;
    private File sbQueueStackPath;
    ConcurrentHashMap<String, QueueEntry> queueInProcess;
    
    public plasmaSwitchboardQueue(plasmaWordIndex wordIndex, File sbQueueStackPath, CrawlProfile profiles) {
        this.sbQueueStackPath = sbQueueStackPath;
        this.profiles = profiles;
        this.wordIndex = wordIndex;
        this.queueInProcess = new ConcurrentHashMap<String, QueueEntry>();

        initQueueStack();
    }
    
    public static final kelondroRow rowdef = new kelondroRow(
            "String url-256, " +                                       // the url
            "String refhash-" + yacySeedDB.commonHashLength + ", " +   // the url's referrer hash
            "Cardinal modifiedsince-11 {b64e}, " +                     // from ifModifiedSince
            "byte[] flags-1, " +                                       // flags
            "String initiator-" + yacySeedDB.commonHashLength + ", " + // the crawling initiator
            "Cardinal depth-2 {b64e}, " +                              // the prefetch depth so far, starts at 0
            "String profile-" + yacySeedDB.commonHashLength + ", " +   // the name of the prefetch profile handle
            "String urldescr-80",
            kelondroNaturalOrder.naturalOrder,
            0);
    
    private void initQueueStack() {
        sbQueueStack = kelondroStack.open(sbQueueStackPath, rowdef);
    }
    
    /*
    private void resetQueueStack() {
        try {sbQueueStack.close();} catch (Exception e) {}
        if (sbQueueStackPath.exists()) sbQueueStackPath.delete();
        initQueueStack();
    }
    */
    public int size() {
        return sbQueueStack.size();
    }

    public synchronized void push(QueueEntry entry) throws IOException {
        if (entry == null) return;
        sbQueueStack.push(sbQueueStack.row().newEntry(new byte[][]{
            entry.url.toString().getBytes(),
            (entry.referrerHash == null) ? "".getBytes() : entry.referrerHash.getBytes(),
            kelondroBase64Order.enhancedCoder.encodeLong((entry.ifModifiedSince == null) ? 0 : entry.ifModifiedSince.getTime(), 11).getBytes(),
            new byte[]{entry.flags},
            (entry.initiator == null) ? "".getBytes() : entry.initiator.getBytes(),
            kelondroBase64Order.enhancedCoder.encodeLong((long) entry.depth, rowdef.width(5)).getBytes(),
            (entry.profileHandle == null) ? "".getBytes() : entry.profileHandle.getBytes(),
            (entry.anchorName == null) ? "-".getBytes("UTF-8") : entry.anchorName.getBytes("UTF-8")
        }));
    }

    public synchronized QueueEntry pop() throws IOException {
        if (sbQueueStack.size() == 0) return null;
        kelondroRow.Entry b = sbQueueStack.pot();
        if (b == null) return null;
        return new QueueEntry(b);
    }

    public synchronized QueueEntry remove(String urlHash) {
        Iterator<kelondroRow.Entry> i = sbQueueStack.stackIterator(true);
        kelondroRow.Entry rowentry;
        QueueEntry entry;
        while (i.hasNext()) {
            rowentry = (kelondroRow.Entry) i.next();
            entry = new QueueEntry(rowentry);
            if (entry.urlHash().equals(urlHash)) {
                i.remove();
                return entry;
            }
        }
        return null;
    }

    public void clear() {
        sbQueueStack = kelondroStack.reset(sbQueueStack);
    }

    public void close() {
        if (sbQueueStack != null) {
            sbQueueStack.close();
        }
        sbQueueStack = null;
    }

    protected void finalize() throws Throwable {
        try {
        close();
        } catch (Exception e) {
            throw new IOException("plasmaSwitchboardQueue.finalize()" + e.getMessage());
    }
        super.finalize();
    }

    public Iterator<QueueEntry> entryIterator(boolean up) {
        // iterates the elements in an ordered way.
        // returns plasmaSwitchboardQueue.Entry - type Objects
        return new entryIterator(up);
    }

    public class entryIterator implements Iterator<QueueEntry> {

        Iterator<kelondroRow.Entry> rows;
        
        public entryIterator(boolean up) {
            rows = sbQueueStack.stackIterator(up);
        }

        public boolean hasNext() {
            return rows.hasNext();
        }

        public QueueEntry next() {
            return new QueueEntry((kelondroRow.Entry) rows.next());
        }

        public void remove() {
            rows.remove();
        }
    }
    
    public QueueEntry newEntry(yacyURL url, String referrer, Date ifModifiedSince, boolean requestWithCookie,
                     String initiator, int depth, String profilehandle, String anchorName) {
        return new QueueEntry(url, referrer, ifModifiedSince, requestWithCookie, initiator, depth, profilehandle, anchorName);
    }
    
    public void enQueueToActive(QueueEntry entry) {
        queueInProcess.put(entry.urlHash(), entry);
    }
    
    public QueueEntry getActiveEntry(String urlhash) {
        // show one entry from the queue
        return this.queueInProcess.get(urlhash);
    }
    
    public int getActiveQueueSize() {
        return this.queueInProcess.size();
    }
   
    public Collection<QueueEntry> getActiveQueueEntries() {
        return this.queueInProcess.values();
    }
    
    public static final int QUEUE_STATE_FRESH             = 0;
    public static final int QUEUE_STATE_PARSING           = 1;
    public static final int QUEUE_STATE_CONDENSING        = 2;
    public static final int QUEUE_STATE_STRUCTUREANALYSIS = 3;
    public static final int QUEUE_STATE_INDEXSTORAGE      = 4;
    public static final int QUEUE_STATE_FINISHED          = 5;
    
    public class QueueEntry {
        yacyURL url;          // plasmaURL.urlStringLength
        String referrerHash;  // plasmaURL.urlHashLength
        Date ifModifiedSince; // 6
        byte flags;           // 1
        String initiator;     // yacySeedDB.commonHashLength
        int depth;            // plasmaURL.urlCrawlDepthLength
        String profileHandle; // plasmaURL.urlCrawlProfileHandleLength
        String anchorName;    // plasmaURL.urlDescrLength
        int status;
        
        // computed values
        private CrawlProfile.entry profileEntry;
        private IResourceInfo contentInfo;
        private yacyURL referrerURL;

        public QueueEntry(yacyURL url, String referrer, Date ifModifiedSince, boolean requestWithCookie,
                     String initiator, int depth, String profileHandle, String anchorName) {
            this.url = url;
            this.referrerHash = referrer;
            this.ifModifiedSince = ifModifiedSince;
            this.flags = (requestWithCookie) ? (byte) 1 : (byte) 0;
            this.initiator = initiator;
            this.depth = depth;
            this.profileHandle = profileHandle;
            this.anchorName = (anchorName==null)?"":anchorName.trim();
            
            this.profileEntry = null;
            this.contentInfo = null;
            this.referrerURL = null;
            this.status = QUEUE_STATE_FRESH;
        }

        public QueueEntry(kelondroRow.Entry row) {
            long ims = row.getColLong(2);
            byte flags = row.getColByte(3);
            try {
                this.url = new yacyURL(row.getColString(0, "UTF-8"), null);
            } catch (MalformedURLException e) {
                this.url = null;
            }
            this.referrerHash = row.getColString(1, "UTF-8");
            this.ifModifiedSince = (ims == 0) ? null : new Date(ims);
            this.flags = ((flags & 1) == 1) ? (byte) 1 : (byte) 0;
            this.initiator = row.getColString(4, "UTF-8");
            this.depth = (int) row.getColLong(5);
            this.profileHandle = row.getColString(6, "UTF-8");
            this.anchorName = row.getColString(7, "UTF-8");

            this.profileEntry = null;
            this.contentInfo = null;
            this.referrerURL = null;
            this.status = QUEUE_STATE_FRESH;
        }

        public QueueEntry(byte[][] row) throws IOException {
            long ims = (row[2] == null) ? 0 : kelondroBase64Order.enhancedCoder.decodeLong(new String(row[2], "UTF-8"));
            byte flags = (row[3] == null) ? 0 : row[3][0];
            try {
                this.url = new yacyURL(new String(row[0], "UTF-8"), null);
            } catch (MalformedURLException e) {
                this.url = null;
            }
            this.referrerHash = (row[1] == null) ? null : new String(row[1], "UTF-8");
            this.ifModifiedSince = (ims == 0) ? null : new Date(ims);
            this.flags = ((flags & 1) == 1) ? (byte) 1 : (byte) 0;
            this.initiator = (row[4] == null) ? null : new String(row[4], "UTF-8");
            this.depth = (int) kelondroBase64Order.enhancedCoder.decodeLong(new String(row[5], "UTF-8"));
            this.profileHandle = new String(row[6], "UTF-8");
            this.anchorName = (row[7] == null) ? null : (new String(row[7], "UTF-8")).trim();

            this.profileEntry = null;
            this.contentInfo = null;
            this.referrerURL = null;
            this.status = QUEUE_STATE_FRESH;
        }
        
        public void updateStatus(int newStatus) {
            this.status = newStatus;
        }
        
        public void close() {
            queueInProcess.remove(this.url.hash());
        }
        
        public void finalize() {
            this.close();
        }
        
        public yacyURL url() {
            return url;
        }

        public String urlHash() {
            return url.hash();
        }

        public boolean requestedWithCookie() {
            return (flags & 1) == 1;
        }

        public File cacheFile() {
            return plasmaHTCache.getCachePath(url);
        }

        public boolean proxy() {
            return (initiator == null) || (initiator.equals(initiator.length() == 0));
        }

        public String initiator() {
            return (initiator == null) ? "" : initiator;
        }
        
        public yacySeed initiatorPeer() {
            if ((initiator == null) || (initiator.length() == 0)) return null;
            if (initiator.equals(wordIndex.seedDB.mySeed().hash)) {
                // normal crawling
                return null;
            } else {
                // this was done for remote peer (a global crawl)
                return wordIndex.seedDB.getConnected(initiator);
            }
        }

        public int depth() {
            return depth;
        }

        public long size() {
            if (cacheFile().exists()) return cacheFile().length(); else return 0;
        }

        public CrawlProfile.entry profile() {
            if (profileEntry == null) profileEntry = profiles.getEntry(profileHandle);
            return profileEntry;
        }

        private IResourceInfo getCachedObjectInfo() {
            if (this.contentInfo == null) try {
                this.contentInfo = plasmaHTCache.loadResourceInfo(this.url);
            } catch (Exception e) {
                serverLog.logSevere("PLASMA", "responseHeader: failed to get header", e);
                return null;
            }
            return this.contentInfo;
        }

        public String getMimeType() {
            IResourceInfo info = this.getCachedObjectInfo();
            return (info == null) ? null : info.getMimeType();
        }
        
        public String getCharacterEncoding() {
            IResourceInfo info = this.getCachedObjectInfo();
            return (info == null) ? null : info.getCharacterEncoding();
        }
        
        public Date getModificationDate() {
            IResourceInfo info = this.getCachedObjectInfo();
            return (info == null) ? new Date() : info.getModificationDate();            
        }
        
        public yacyURL referrerURL() {
            if (referrerURL == null) {
                if ((referrerHash == null) || (referrerHash.equals(initiator.length() == 0))) return null;
                indexURLReference entry = wordIndex.getURL(referrerHash, null, 0);
                if (entry == null) referrerURL = null; else referrerURL = entry.comp().url();
            }
            return referrerURL;
        }
        
        public String referrerHash() {
            return (referrerHash == null) ? "" : referrerHash;
        }

        public String anchorName() {
            return anchorName;
        }

        public int processCase() {
            // we must distinguish the following cases: resource-load was initiated by
            // 1) global crawling: the index is extern, not here (not possible here)
            // 2) result of search queries, some indexes are here (not possible here)
            // 3) result of index transfer, some of them are here (not possible here)
            // 4) proxy-load (initiator is "------------")
            // 5) local prefetch/crawling (initiator is own seedHash)
            // 6) local fetching for global crawling (other known or unknwon initiator)
            int processCase = plasmaSwitchboard.PROCESSCASE_0_UNKNOWN;
            if ((initiator == null) || (initiator.equals(initiator.length() == 0))) {
                // proxy-load
                processCase = plasmaSwitchboard.PROCESSCASE_4_PROXY_LOAD;
            } else if ((initiator != null) && (initiator.equals(wordIndex.seedDB.mySeed().hash))) {
                // normal crawling
                processCase = plasmaSwitchboard.PROCESSCASE_5_LOCAL_CRAWLING;
            } else {
                // this was done for remote peer (a global crawl)
                processCase = plasmaSwitchboard.PROCESSCASE_6_GLOBAL_CRAWLING;
            }
            return processCase;
        }
        
        /**
         * decide upon header information if a specific file should be indexed
         * this method returns null if the answer is 'YES'!
         * if the answer is 'NO' (do not index), it returns a string with the reason
         * to reject the crawling demand in clear text
         * 
         * This function is used by plasmaSwitchboard#processResourceStack
         */
        public final String shallIndexCacheForProxy() {
            if (profile() == null) {
                return "shallIndexCacheForProxy: profile() is null !";
            }

            // check profile
            if (!profile().indexText() && !profile().indexMedia()) {
                return "Indexing_Not_Allowed";
            }

            // -CGI access in request
            // CGI access makes the page very individual, and therefore not usable in caches
            if (!profile().crawlingQ()) {
                if (url.isPOST()) {
                    return "Dynamic_(POST)";
                }
                if (url.isCGI()) {
                    return "Dynamic_(CGI)";
                }
            }

            // -authorization cases in request
            // we checked that in shallStoreCache

            // -ranges in request
            // we checked that in shallStoreCache

            // a picture cannot be indexed
            if (plasmaHTCache.noIndexingURL(url)) {
                return "Media_Content_(forbidden)";
            }

            // -cookies in request
            // unfortunately, we cannot index pages which have been requested with a cookie
            // because the returned content may be special for the client
            if (requestedWithCookie()) {
//              System.out.println("***not indexed because cookie");
                return "Dynamic_(Requested_With_Cookie)";
            }

            if (getCachedObjectInfo() != null) {
                return this.getCachedObjectInfo().shallIndexCacheForProxy();
            }
            return null;
        }

        /**
         * decide upon header information if a specific file should be indexed
         * this method returns null if the answer is 'YES'!
         * if the answer is 'NO' (do not index), it returns a string with the reason
         * to reject the crawling demand in clear text
         *
         * This function is used by plasmaSwitchboard#processResourceStack
         */
        public final String shallIndexCacheForCrawler() {
            if (profile() == null) {
                return "shallIndexCacheForCrawler: profile() is null !";
            }

            // check profile
            if (!profile().indexText() && !profile().indexMedia()) {
                return "Indexing_Not_Allowed";
            }

            // -CGI access in request
            // CGI access makes the page very individual, and therefore not usable in caches
            if (!profile().crawlingQ()) {
                if (url().isPOST()) { return "Dynamic_(POST)"; }
                if (url().isCGI()) { return "Dynamic_(CGI)"; }
            }

            // -authorization cases in request
            // we checked that in shallStoreCache

            // -ranges in request
            // we checked that in shallStoreCache

            // a picture cannot be indexed
            if (getCachedObjectInfo() != null) {
                String status = this.getCachedObjectInfo().shallIndexCacheForCrawler();
                if (status != null) return status;
            }
            if (plasmaHTCache.noIndexingURL(url())) { return "Media_Content_(forbidden)"; }

            // -if-modified-since in request
            // if the page is fresh at the very moment we can index it
            // -> this does not apply for the crawler

            // -cookies in request
            // unfortunately, we cannot index pages which have been requested with a cookie
            // because the returned content may be special for the client
            // -> this does not apply for a crawler

            // -set-cookie in response
            // the set-cookie from the server does not indicate that the content is special
            // thus we do not care about it here for indexing
            // -> this does not apply for a crawler

            // -pragma in cached response
            // -> in the crawler we ignore this

            // look for freshnes information

            // -expires in cached response
            // the expires value gives us a very easy hint when the cache is stale
            // sometimes, the expires date is set to the past to prevent that a page is cached
            // we use that information to see if we should index it
            // -> this does not apply for a crawler

            // -lastModified in cached response
            // this information is too weak to use it to prevent indexing
            // even if we can apply a TTL heuristic for cache usage

            // -cache-control in cached response
            // the cache-control has many value options.
            // -> in the crawler we ignore this

            return null;
        }
    } // class Entry
}