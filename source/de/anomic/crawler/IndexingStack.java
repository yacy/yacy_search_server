// IndexingStack.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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


package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.http.httpHeader;
import de.anomic.http.httpResponseHeader;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.table.RecordStack;
import de.anomic.kelondro.text.MetadataRepository;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public class IndexingStack {
    
    private static final byte[] zbytes = new byte[0];
    //private static final String queuesPrefix = "indexing";
    //private static final String sbQueueName = "sbQueueStack";
    
    protected final CrawlProfile profiles;
    protected final RecordStack sbQueueStack;
    protected final yacySeedDB peers;
    protected final ConcurrentHashMap<String, QueueEntry> queueInProcess;
    
    public IndexingStack(
            final yacySeedDB peers,
            final File queuesRoot,
            final String sbQueueStackName,
            final CrawlProfile profiles) {
        this.profiles = profiles;
        this.peers = peers;
        this.queueInProcess = new ConcurrentHashMap<String, QueueEntry>();
        this.sbQueueStack = RecordStack.open(new File(queuesRoot, sbQueueStackName), rowdef);
    }
    
    public static final Row rowdef = new Row(
            "String url-256, " +                                       // the url
            "String refhash-" + yacySeedDB.commonHashLength + ", " +   // the url's referrer hash
            "Cardinal modifiedsince-11 {b64e}, " +                     // from ifModifiedSince
            "byte[] flags-1, " +                                       // flags
            "String initiator-" + yacySeedDB.commonHashLength + ", " + // the crawling initiator
            "Cardinal depth-2 {b64e}, " +                              // the prefetch depth so far, starts at 0
            "String profile-" + yacySeedDB.commonHashLength + ", " +   // the name of the prefetch profile handle
            "String urldescr-80",
            NaturalOrder.naturalOrder
    );

    public synchronized int size() {
        return (sbQueueStack == null) ? 0 : sbQueueStack.size();
    }

    public String file() {
    	return this.sbQueueStack.filename;
    }
    
    public synchronized void push(final QueueEntry entry) throws IOException {
        if (entry == null) return;
        if (sbQueueStack == null) return; // may occur during shutdown
        sbQueueStack.push(sbQueueStack.row().newEntry(new byte[][]{
            entry.url.toString().getBytes(),
            (entry.referrerHash == null) ? zbytes : entry.referrerHash.getBytes(),
            Base64Order.enhancedCoder.encodeLong((entry.ifModifiedSince == null) ? 0 : entry.ifModifiedSince.getTime(), 11).getBytes(),
            new byte[]{entry.flags},
            (entry.initiator == null) ? zbytes : entry.initiator.getBytes(),
            Base64Order.enhancedCoder.encodeLong(entry.depth, rowdef.width(5)).getBytes(),
            (entry.profileHandle == null) ? zbytes : entry.profileHandle.getBytes(),
            (entry.anchorName == null) ? "-".getBytes("UTF-8") : entry.anchorName.getBytes("UTF-8")
        }));
    }

    public synchronized QueueEntry pop() throws IOException {
    	if (sbQueueStack == null) return null;
    	// this shall not return null because it may cause a emergency reset!
		int sizeBefore;
    	while ((sizeBefore = sbQueueStack.size()) > 0) {
	        Row.Entry b = sbQueueStack.pot();
	        if (b == null) {
	        	Log.logInfo("IndexingStack", "sbQueueStack.pot() == null, file = " + sbQueueStack.filename);
	        	if (sbQueueStack.size() < sizeBefore) continue;
	        	Log.logSevere("IndexingStack", "sbQueueStack " + sbQueueStack.filename + " does not shrink after pot() == null; trying pop()");
	        }
	        if (sbQueueStack.size() < sizeBefore) {
	        	return new QueueEntry(b);
	        }
	        Log.logSevere("IndexingStack", "sbQueueStack " + sbQueueStack.filename + " does not shrink after pot() != null; trying pop()");
	        
	        sizeBefore = sbQueueStack.size();
	        b = sbQueueStack.pop();
	        if (b == null) {
	        	Log.logInfo("IndexingStack", "sbQueueStack.pop() == null, file = " + sbQueueStack.filename);
	        	if (sbQueueStack.size() < sizeBefore) continue;
	        	Log.logSevere("IndexingStack", "sbQueueStack does not shrink after pop() == null; failed; file = " + sbQueueStack.filename);
	        	return null;
	        }
	        return new QueueEntry(b);
    	}
    	Log.logInfo("IndexingStack", "sbQueueStack.size() == 0");
        return null;
    }

    public synchronized QueueEntry remove(final String urlHash) {
        final Iterator<Row.Entry> i = sbQueueStack.stackIterator(true);
        Row.Entry rowentry;
        QueueEntry entry;
        while (i.hasNext()) {
            rowentry = i.next();
            entry = new QueueEntry(rowentry);
            if (entry.url().hash().equals(urlHash)) {
                i.remove();
                return entry;
            }
        }
        return null;
    }

    public void clear() {
        sbQueueStack.clear();
    }

    public void close() {
        if (sbQueueStack != null) {
            sbQueueStack.close();
        }
    }

    protected void finalize() throws Throwable {
        try {
        close();
        } catch (final Exception e) {
            throw new IOException("plasmaSwitchboardQueue.finalize()" + e.getMessage());
    }
        super.finalize();
    }

    public Iterator<QueueEntry> entryIterator(final boolean up) {
        // iterates the elements in an ordered way.
        // returns plasmaSwitchboardQueue.Entry - type Objects
        return new entryIterator(up);
    }

    public class entryIterator implements Iterator<QueueEntry> {

        Iterator<Row.Entry> rows;
        
        public entryIterator(final boolean up) {
            rows = sbQueueStack.stackIterator(up);
        }

        public boolean hasNext() {
            return rows.hasNext();
        }

        public QueueEntry next() {
            return new QueueEntry(rows.next());
        }

        public void remove() {
            rows.remove();
        }
    }
    
    public QueueEntry newEntry(final yacyURL url, final String referrer, final Date ifModifiedSince, final boolean requestWithCookie,
                     final String initiator, final int depth, final String profilehandle, final String anchorName) {
        return new QueueEntry(url, referrer, ifModifiedSince, requestWithCookie, initiator, depth, profilehandle, anchorName);
    }
    
    public void enQueueToActive(final QueueEntry entry) {
        queueInProcess.put(entry.url().hash(), entry);
    }
    
    public QueueEntry getActiveEntry(final String urlhash) {
        // show one entry from the queue
        return this.queueInProcess.get(urlhash);
    }
    
    public int getActiveQueueSize() {
        return this.queueInProcess.size();
    }
   
    public Collection<QueueEntry> getActiveQueueEntries() {
        // todo: check dead entries?
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
        private httpResponseHeader responseHeader;
        private yacyURL referrerURL;

        public QueueEntry(final yacyURL url, final String referrer, final Date ifModifiedSince, final boolean requestWithCookie,
                     final String initiator, final int depth, final String profileHandle, final String anchorName) {
            this.url = url;
            this.referrerHash = referrer;
            this.ifModifiedSince = ifModifiedSince;
            this.flags = (requestWithCookie) ? (byte) 1 : (byte) 0;
            this.initiator = initiator;
            this.depth = depth;
            this.profileHandle = profileHandle;
            this.anchorName = (anchorName==null)?"":anchorName.trim();
            
            this.profileEntry = null;
            this.responseHeader = null;
            this.referrerURL = null;
            this.status = QUEUE_STATE_FRESH;
        }

        public QueueEntry(final Row.Entry row) {
            final long ims = row.getColLong(2);
            final byte flags = row.getColByte(3);
            try {
                this.url = new yacyURL(row.getColString(0, "UTF-8"), null);
            } catch (final MalformedURLException e) {
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
            this.responseHeader = null;
            this.referrerURL = null;
            this.status = QUEUE_STATE_FRESH;
        }

        public QueueEntry(final byte[][] row) throws IOException {
            final long ims = (row[2] == null) ? 0 : Base64Order.enhancedCoder.decodeLong(new String(row[2], "UTF-8"));
            final byte flags = (row[3] == null) ? 0 : row[3][0];
            try {
                this.url = new yacyURL(new String(row[0], "UTF-8"), null);
            } catch (final MalformedURLException e) {
                this.url = null;
            }
            this.referrerHash = (row[1] == null) ? null : new String(row[1], "UTF-8");
            this.ifModifiedSince = (ims == 0) ? null : new Date(ims);
            this.flags = ((flags & 1) == 1) ? (byte) 1 : (byte) 0;
            this.initiator = (row[4] == null) ? null : new String(row[4], "UTF-8");
            this.depth = (int) Base64Order.enhancedCoder.decodeLong(new String(row[5], "UTF-8"));
            this.profileHandle = new String(row[6], "UTF-8");
            this.anchorName = (row[7] == null) ? null : (new String(row[7], "UTF-8")).trim();

            this.profileEntry = null;
            this.responseHeader = null;
            this.referrerURL = null;
            this.status = QUEUE_STATE_FRESH;
        }
        
        public void updateStatus(final int newStatus) {
            this.status = newStatus;
        }
        
        public void close() {
            queueInProcess.remove(this.url.hash());
        }
        
        protected void finalize() {
            this.close();
        }
        
        public yacyURL url() {
            return url;
        }

        public boolean requestedWithCookie() {
            return (flags & 1) == 1;
        }

        public boolean proxy() {
            // FIXME the equals seems to be incorrect: String.equals(boolean)
            return (initiator == null) || (initiator.equals(initiator.length() == 0));
        }

        public String initiator() {
            return (initiator == null) ? "------------" : initiator; // TODO: this may cause problems for methods that check if the initiator is the proxy
        }
        
        public yacySeed initiatorPeer() {
            if ((initiator == null) || (initiator.length() == 0)) return null;
            if (initiator.equals(peers.mySeed().hash)) {
                // normal crawling
                return null;
            }
            // this was done for remote peer (a global crawl)
            return peers.getConnected(initiator);
        }

        public int depth() {
            return depth;
        }

        public long size() {
            return plasmaHTCache.getResourceContentLength(this.url);
        }

        public CrawlProfile.entry profile() {
            if (profileEntry == null) profileEntry = profiles.getEntry(profileHandle);
            return profileEntry;
        }

        private void getResponseHeader() {
            if (this.responseHeader == null) try {
                this.responseHeader = plasmaHTCache.loadResponseHeader(this.url);
            } catch (final Exception e) {
                Log.logSevere("PLASMA", "responseHeader: failed to get header", e);
            }
        }

        public String getMimeType() {
            this.getResponseHeader();
            return (responseHeader == null) ? null : responseHeader.mime();
        }
        
        public String getCharacterEncoding() {
            getResponseHeader();
            return (responseHeader == null) ? null : responseHeader.getCharacterEncoding();
        }
        
        public Date getModificationDate() {
            getResponseHeader();
            return (responseHeader == null) ? new Date() : responseHeader.lastModified();            
        }
        
        public yacyURL referrerURL(MetadataRepository repository) {
            if (referrerURL == null) {
                // FIXME the equals seems to be incorrect: String.equals(boolean)
                if ((referrerHash == null) || ((initiator != null) && (referrerHash.equals(initiator.length() == 0)))) return null;
                final URLMetadataRow entry = repository.load(referrerHash, null, 0);
                if (entry == null) referrerURL = null; else referrerURL = entry.metadata().url();
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
            int processCase = plasmaSwitchboardConstants.PROCESSCASE_0_UNKNOWN;
            // FIXME the equals seems to be incorrect: String.equals(boolean)
            if ((initiator == null) || initiator.length() == 0 || initiator.equals("------------")) {
                // proxy-load
                processCase = plasmaSwitchboardConstants.PROCESSCASE_4_PROXY_LOAD;
            } else if (initiator.equals(peers.mySeed().hash)) {
                // normal crawling
                processCase = plasmaSwitchboardConstants.PROCESSCASE_5_LOCAL_CRAWLING;
            } else {
                // this was done for remote peer (a global crawl)
                processCase = plasmaSwitchboardConstants.PROCESSCASE_6_GLOBAL_CRAWLING;
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

            getResponseHeader();
            if (responseHeader != null) {
                // -set-cookie in response
                // the set-cookie from the server does not indicate that the content is special
                // thus we do not care about it here for indexing                
                
                // a picture cannot be indexed
                final String mimeType = responseHeader.mime();
                if (plasmaHTCache.isPicture(mimeType)) {
                    return "Media_Content_(Picture)";
                }
                if (!plasmaHTCache.isText(mimeType)) {
                    return "Media_Content_(not_text)";
                }
    
                // -if-modified-since in request
                // if the page is fresh at the very moment we can index it
                final Date ifModifiedSince = getModificationDate();
                if ((ifModifiedSince != null) && (responseHeader.containsKey(httpHeader.LAST_MODIFIED))) {
                    // parse date
                    Date d = responseHeader.lastModified();
                    if (d == null) {
                        d = new Date(DateFormatter.correctedUTCTime());
                    }
                    // finally, we shall treat the cache as stale if the modification time is after the if-.. time
                    if (d.after(ifModifiedSince)) {
                        //System.out.println("***not indexed because if-modified-since");
                        return "Stale_(Last-Modified>Modified-Since)";
                    }
                }
    
                // -pragma in cached response
                if (responseHeader.containsKey(httpHeader.PRAGMA) &&
                    (responseHeader.get(httpHeader.PRAGMA)).toUpperCase().equals("NO-CACHE")) {
                    return "Denied_(pragma_no_cache)";
                }
    
                // see for documentation also:
                // http://www.web-caching.com/cacheability.html
    
                // look for freshnes information
    
                // -expires in cached response
                // the expires value gives us a very easy hint when the cache is stale
                // sometimes, the expires date is set to the past to prevent that a page is cached
                // we use that information to see if we should index it
                final Date expires = responseHeader.expires();
                if (expires != null && expires.before(new Date(DateFormatter.correctedUTCTime()))) {
                    return "Stale_(Expired)";
                }
    
                // -lastModified in cached response
                // this information is too weak to use it to prevent indexing
                // even if we can apply a TTL heuristic for cache usage
    
                // -cache-control in cached response
                // the cache-control has many value options.
                String cacheControl = responseHeader.get(httpHeader.CACHE_CONTROL);
                if (cacheControl != null) {
                    cacheControl = cacheControl.trim().toUpperCase();
                    /* we have the following cases for cache-control:
                       "public" -- can be indexed
                       "private", "no-cache", "no-store" -- cannot be indexed
                       "max-age=<delta-seconds>" -- stale/fresh dependent on date
                     */
                    if (cacheControl.startsWith("PRIVATE") ||
                        cacheControl.startsWith("NO-CACHE") ||
                        cacheControl.startsWith("NO-STORE")) {
                        // easy case
                        return "Stale_(denied_by_cache-control=" + cacheControl + ")";
    //              } else if (cacheControl.startsWith("PUBLIC")) {
    //                  // ok, do nothing
                    } else if (cacheControl.startsWith("MAX-AGE=")) {
                        // we need also the load date
                        final Date date = responseHeader.date();
                        if (date == null) {
                            return "Stale_(no_date_given_in_response)";
                        }
                        try {
                            final long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                            if (DateFormatter.correctedUTCTime() - date.getTime() > ttl) {
                                //System.out.println("***not indexed because cache-control");
                                return "Stale_(expired_by_cache-control)";
                            }
                        } catch (final Exception e) {
                            return "Error_(" + e.getMessage() + ")";
                        }
                    }
                }
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
            getResponseHeader();
            if (responseHeader != null) {
                final String mimeType = responseHeader.mime();
                if (plasmaHTCache.isPicture(mimeType)) { return "Media_Content_(Picture)"; }
                if (!plasmaHTCache.isText(mimeType)) { return "Media_Content_(not_text)"; }
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
        
        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#toString()
         */
        public String toString() {
            return "QueueEntry of "+ url.toString() + ", ref="+referrerHash +", initiator="+initiator +", flags="+ flags +", anchor="+ anchorName;
        }
    } // class Entry
}