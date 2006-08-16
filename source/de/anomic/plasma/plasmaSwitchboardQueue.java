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

import de.anomic.http.httpHeader;
import de.anomic.index.indexURL;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroStack;
import de.anomic.kelondro.kelondroRow;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverDate;
import de.anomic.yacy.yacySeedDB;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import de.anomic.net.URL;
import java.util.ArrayList;
import java.util.Date;

public class plasmaSwitchboardQueue {

    private kelondroStack sbQueueStack;
    private plasmaCrawlProfile profiles;
    private plasmaHTCache htCache;
    private plasmaCrawlLURL lurls;
    private File sbQueueStackPath;
    
    public plasmaSwitchboardQueue(plasmaHTCache htCache, plasmaCrawlLURL lurls, File sbQueueStackPath, plasmaCrawlProfile profiles) {
        this.sbQueueStackPath = sbQueueStackPath;
        this.profiles = profiles;
        this.htCache = htCache;
        this.lurls = lurls;

        initQueueStack();
    }

    private void initQueueStack() {
        kelondroRow rowdef = new kelondroRow(
                "String url-"          + indexURL.urlStringLength             + ", " +        // the url
                "String refhash-"      + indexURL.urlHashLength               + ", " +        // the url's referrer hash
                "Cardinal modifiedsince-11"                                   + " {b64e}, " + // from ifModifiedSince
                "byte[] flags-1"                                              + ", " +        // flags
                "String initiator-"    + yacySeedDB.commonHashLength          + ", " +        // the crawling initiator
                "Cardinal depth-"      + indexURL.urlCrawlDepthLength         + " {b64e}, " + // the prefetch depth so far, starts at 0
                "String profile-"      + indexURL.urlCrawlProfileHandleLength + ", " +        // the name of the prefetch profile handle
                "String urldescr-"     + indexURL.urlDescrLength);                            //
            
        if (sbQueueStackPath.exists()) try {
            sbQueueStack = new kelondroStack(sbQueueStackPath);
            sbQueueStack.assignRowdef(rowdef);
        } catch (IOException e) {
            sbQueueStackPath.delete();
            sbQueueStack = new kelondroStack(sbQueueStackPath, rowdef, true);
        } else {
            sbQueueStack = new kelondroStack(sbQueueStackPath, rowdef, true);
        }
    }
    
    private void resetQueueStack() {
        try {sbQueueStack.close();} catch (Exception e) {}
        if (sbQueueStackPath.exists()) sbQueueStackPath.delete();
        initQueueStack();
    }
    
    public int size() {
        return sbQueueStack.size();
    }

    public void push(Entry entry) throws IOException {
        sbQueueStack.push(sbQueueStack.row().newEntry(new byte[][]{
            entry.url.toString().getBytes(),
            (entry.referrerHash == null) ? indexURL.dummyHash.getBytes() : entry.referrerHash.getBytes(),
            kelondroBase64Order.enhancedCoder.encodeLong((entry.ifModifiedSince == null) ? 0 : entry.ifModifiedSince.getTime(), 11).getBytes(),
            new byte[]{entry.flags},
            (entry.initiator == null) ? indexURL.dummyHash.getBytes() : entry.initiator.getBytes(),
            kelondroBase64Order.enhancedCoder.encodeLong((long) entry.depth, indexURL.urlCrawlDepthLength).getBytes(),
            (entry.profileHandle == null) ? indexURL.dummyHash.getBytes() : entry.profileHandle.getBytes(),
            (entry.anchorName == null) ? "-".getBytes() : entry.anchorName.getBytes()
        }));
    }

    public Entry pop() throws IOException {
        if (sbQueueStack.size() == 0) return null;
        kelondroRow.Entry b = sbQueueStack.pot();
        if (b == null) return null;
        return new Entry(b);
    }

    public Entry remove(int index) throws IOException {
        if (sbQueueStack.size() == 0) return null;
        return new Entry(sbQueueStack.pot(index));
    }

    public Entry get(int index) throws IOException {
        if ((index < 0) || (index >= sbQueueStack.size())) throw new ArrayIndexOutOfBoundsException();
        return new Entry(sbQueueStack.bot(index));
    }

    public ArrayList list() throws IOException {
        return list(0);
    }

    public ArrayList list(int index) throws IOException {
        if ((index == 0) && (sbQueueStack.size() == 0)) return new ArrayList(0);
        if ((index < 0) || (index >= sbQueueStack.size())) throw new ArrayIndexOutOfBoundsException();
        try {
            ArrayList list = sbQueueStack.botList(index);
            for (int i = 0; i < list.size(); i++) {
                list.set(i, new Entry((kelondroRow.Entry) list.get(i)));
            }
            return list;
        } catch (kelondroException e) {
            resetQueueStack();
            return new ArrayList();
        }
    }

    public void clear() throws IOException {
        sbQueueStack.clear();
    }

    public void close() {
        if (sbQueueStack != null) try {
            sbQueueStack.close();
        } catch (IOException e) {
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

    public Entry newEntry(URL url, String referrer, Date ifModifiedSince, boolean requestWithCookie,
                     String initiator, int depth, String profilehandle, String anchorName) {
        return new Entry(url, referrer, ifModifiedSince, requestWithCookie, initiator, depth, profilehandle, anchorName);
    }

    public class Entry {
        private URL url;              // plasmaURL.urlStringLength
        private String referrerHash;  // plasmaURL.urlHashLength
        private Date ifModifiedSince; // 6
        private byte flags;           // 1
        private String initiator;     // yacySeedDB.commonHashLength
        private int depth;            // plasmaURL.urlCrawlDepthLength
        private String profileHandle; // plasmaURL.urlCrawlProfileHandleLength
        private String anchorName;    // plasmaURL.urlDescrLength

        // computed values
        private plasmaCrawlProfile.entry profileEntry;
        private httpHeader responseHeader;
        private URL referrerURL;

        public Entry(URL url, String referrer, Date ifModifiedSince, boolean requestWithCookie,
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
            this.responseHeader = null;
            this.referrerURL = null;
        }

        public Entry(kelondroRow.Entry row) {
            long ims = row.getColLong(2);
            byte flags = row.getColByte(3);
            try {
                this.url = new URL(row.getColString(0, "UTF-8"));
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
            this.responseHeader = null;
            this.referrerURL = null;
        }

        public Entry(byte[][] row) throws IOException {
            long ims = (row[2] == null) ? 0 : kelondroBase64Order.enhancedCoder.decodeLong(new String(row[2], "UTF-8"));
            byte flags = (row[3] == null) ? 0 : row[3][0];
            try {
                this.url = new URL(new String(row[0], "UTF-8"));
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
            this.responseHeader = null;
            this.referrerURL = null;
        }

        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append("url: ")            .append(this.url==null ? "null" : this.url.toString()).append(" | ")
               .append("referrer: ")       .append(this.referrerHash==null?"null":this.referrerHash).append(" | ")
               .append("ifModifiedSince: ").append(this.ifModifiedSince==null?"null":this.ifModifiedSince.toString()).append(" | ")
               .append("flags: ")          .append(Byte.toString(this.flags)).append(" | ")
               .append("initiator: ")      .append(this.initiator==null ? "null" : this.initiator).append(" | ")
               .append("depth: ")          .append(Integer.toString(this.depth)).append(" | ")
               .append("profile: ")        .append(this.profileHandle==null?"null":this.profileHandle).append(" | ")
               .append("anchorName: ")     .append(this.anchorName==null?"null":this.anchorName);
               return str.toString();
        }         

        public URL url() {
            return url;
        }

        public String normalizedURLString() {
            return url.toNormalform();
        }

        public String urlHash() {
            return indexURL.urlHash(url);
        }

        public boolean requestedWithCookie() {
            return (flags & 1) == 1;
        }

        public File cacheFile() {
            return htCache.getCachePath(url);
        }

        public boolean proxy() {
            return (initiator == null) || (initiator.equals(indexURL.dummyHash));
        }

        public String initiator() {
            return initiator;
        }

        public int depth() {
            return depth;
        }

        public long size() {
            if (cacheFile().exists()) return cacheFile().length(); else return 0;
        }

        public plasmaCrawlProfile.entry profile() {
            if (profileEntry == null) profileEntry = profiles.getEntry(profileHandle);
            return profileEntry;
        }

        public httpHeader responseHeader() {
            if (responseHeader == null) try {
                responseHeader = htCache.getCachedResponse(indexURL.urlHash(url));
            } catch (IOException e) {
                serverLog.logSevere("PLASMA", "responseHeader: failed to get header", e);
                return null;
            }
            return responseHeader;
        }

        public URL referrerURL() {
            if (referrerURL == null) {
                if ((referrerHash == null) || (referrerHash.equals(indexURL.dummyHash))) return null;
                try {
                    referrerURL = lurls.getEntry(referrerHash, null).url();
                } catch (IOException e) {
                    referrerURL = null;
                    return null;
                }
            }
            return referrerURL;
        }
        
        public String referrerHash() {
            return referrerHash;
        }

        public String anchorName() {
            return anchorName;
        }

        /**
         * decide upon header information if a specific file should be indexed
         * this method returns null if the answer is 'YES'!
         * if the answer is 'NO' (do not index), it returns a string with the reason
         * to reject the crawling demand in clear text
         */
        public final String shallIndexCacheForProxy() {
            if (profile() == null) {
                return "shallIndexCacheForProxy: profile() is null !";
            }

            // check profile
            if (!profile().localIndexing()) {
                return "Indexing_Not_Allowed";
            }

            String nURL = normalizedURLString();
            // -CGI access in request
            // CGI access makes the page very individual, and therefore not usable in caches
            if (!profile().crawlingQ()) {
                if (plasmaHTCache.isPOST(nURL)) {
                    return "Dynamic_(POST)";
                }
                if (plasmaHTCache.isCGI(nURL)) {
                    return "Dynamic_(CGI)";
                }
            }

            // -authorization cases in request
            // we checked that in shallStoreCache

            // -ranges in request
            // we checked that in shallStoreCache

            // a picture cannot be indexed
            if (plasmaHTCache.noIndexingURL(nURL)) {
                return "Media_Content_(forbidden)";
            }

            // -cookies in request
            // unfortunately, we cannot index pages which have been requested with a cookie
            // because the returned content may be special for the client
            if (requestedWithCookie()) {
//              System.out.println("***not indexed because cookie");
                return "Dynamic_(Requested_With_Cookie)";
            }

            // -set-cookie in response
            // the set-cookie from the server does not indicate that the content is special
            // thus we do not care about it here for indexing
            if (responseHeader() != null) {
                // a picture cannot be indexed
                if (plasmaHTCache.isPicture(responseHeader())) {
                    return "Media_Content_(Picture)";
                }
                if (!plasmaHTCache.isText(responseHeader())) {
                    return "Media_Content_(not_text)";
                }

                // -if-modified-since in request
                // if the page is fresh at the very moment we can index it
                if ((ifModifiedSince != null) && (responseHeader().containsKey(httpHeader.LAST_MODIFIED))) {
                    // parse date
                    Date d = responseHeader().lastModified();
                    if (d == null) {
                        d = new Date(serverDate.correctedUTCTime());
                    }
                    // finally, we shall treat the cache as stale if the modification time is after the if-.. time
                    if (d.after(ifModifiedSince)) {
                        //System.out.println("***not indexed because if-modified-since");
                        return "Stale_(Last-Modified>Modified-Since)";
                    }
                }

                // -pragma in cached response
                if (responseHeader().containsKey(httpHeader.PRAGMA) &&
                    ((String) responseHeader().get(httpHeader.PRAGMA)).toUpperCase().equals("NO-CACHE")) {
                    return "Denied_(pragma_no_cache)";
                }

                // see for documentation also:
                // http://www.web-caching.com/cacheability.html

                // look for freshnes information

                // -expires in cached response
                // the expires value gives us a very easy hint when the cache is stale
                // sometimes, the expires date is set to the past to prevent that a page is cached
                // we use that information to see if we should index it
                final Date expires = responseHeader().expires();
                if (expires != null && expires.before(new Date(serverDate.correctedUTCTime()))) {
                    return "Stale_(Expired)";
                }

                // -lastModified in cached response
                // this information is too weak to use it to prevent indexing
                // even if we can apply a TTL heuristic for cache usage

                // -cache-control in cached response
                // the cache-control has many value options.
                String cacheControl = (String) responseHeader.get(httpHeader.CACHE_CONTROL);
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
//                  } else if (cacheControl.startsWith("PUBLIC")) {
//                      // ok, do nothing
                    } else if (cacheControl.startsWith("MAX-AGE=")) {
                        // we need also the load date
                        final Date date = responseHeader().date();
                        if (date == null) {
                            return "Stale_(no_date_given_in_response)";
                        }
                        try {
                            final long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                            if (serverDate.correctedUTCTime() - date.getTime() > ttl) {
                                //System.out.println("***not indexed because cache-control");
                                return "Stale_(expired_by_cache-control)";
                            }
                        } catch (Exception e) {
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
         */
        public final String shallIndexCacheForCrawler() {
            if (profile() == null) {
                return "shallIndexCacheForCrawler: profile() is null !";
            }

            // check profile
            if (!profile().localIndexing()) { return "Indexing_Not_Allowed"; }

            final String nURL = normalizedURLString();
            // -CGI access in request
            // CGI access makes the page very individual, and therefore not usable in caches
            if (!profile().crawlingQ()) {
                if (plasmaHTCache.isPOST(nURL)) { return "Dynamic_(POST)"; }
                if (plasmaHTCache.isCGI(nURL)) { return "Dynamic_(CGI)"; }
            }

            // -authorization cases in request
            // we checked that in shallStoreCache

            // -ranges in request
            // we checked that in shallStoreCache

            // a picture cannot be indexed
            if (responseHeader() != null) {
                if (plasmaHTCache.isPicture(responseHeader())) { return "Media_Content_(Picture)"; }
                if (!plasmaHTCache.isText(responseHeader())) { return "Media_Content_(not_text)"; }
            }
            if (plasmaHTCache.noIndexingURL(nURL)) { return "Media_Content_(forbidden)"; }

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