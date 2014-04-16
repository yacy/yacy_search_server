// Response.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.08.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.crawler.retrieval;

import java.util.Date;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.NumberTools;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.ResultURLs.EventOrigin;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;

public class Response {

    // doctypes:
    public static final char DT_PDFPS   = 'p';
    public static final char DT_TEXT    = 't';
    public static final char DT_HTML    = 'h';
    public static final char DT_DOC     = 'd';
    public static final char DT_IMAGE   = 'i';
    public static final char DT_MOVIE   = 'm';
    public static final char DT_FLASH   = 'f';
    public static final char DT_SHARE   = 's';
    public static final char DT_AUDIO   = 'a';
    public static final char DT_BINARY  = 'b';
    public static final char DT_UNKNOWN = 'u';

    // the class objects
    private final  Request            request;
    private final  RequestHeader      requestHeader;
    private final  ResponseHeader     responseHeader;
    private final  CrawlProfile       profile;
    private        byte[]             content;
    private        int                status;          // tracker indexing status, see status defs below
    private final  boolean            fromCache;

    // doctype calculation
    public static char docType(final MultiProtocolURL url) {
        String ext = MultiProtocolURL.getFileExtension(url.getFileName());
        if (ext == null) return DT_UNKNOWN;
        if (ext.equals(".gif"))  return DT_IMAGE;
        if (ext.equals(".ico"))  return DT_IMAGE;
        if (ext.equals(".bmp"))  return DT_IMAGE;
        if (ext.equals(".jpg"))  return DT_IMAGE;
        if (ext.equals(".jpeg")) return DT_IMAGE;
        if (ext.equals(".png"))  return DT_IMAGE;
        if (ext.equals(".tif"))  return DT_IMAGE;
        if (ext.equals(".tiff")) return DT_IMAGE;
        if (ext.equals(".htm"))  return DT_HTML;
        if (ext.equals(".html")) return DT_HTML;
        if (ext.equals(".txt"))  return DT_TEXT;
        if (ext.equals(".doc"))  return DT_DOC;
        if (ext.equals(".rtf"))  return DT_DOC;
        if (ext.equals(".pdf"))  return DT_PDFPS;
        if (ext.equals(".ps"))   return DT_PDFPS;
        if (ext.equals(".avi"))  return DT_MOVIE;
        if (ext.equals(".mov"))  return DT_MOVIE;
        if (ext.equals(".qt"))   return DT_MOVIE;
        if (ext.equals(".mpg"))  return DT_MOVIE;
        if (ext.equals(".md5"))  return DT_SHARE;
        if (ext.equals(".mpeg")) return DT_MOVIE;
        if (ext.equals(".asf"))  return DT_FLASH;
        return DT_UNKNOWN;
    }

    public static char docType(final String mime) {
        // serverLog.logFinest("PLASMA", "docType mime=" + mime);
        char doctype = DT_UNKNOWN;
        if (mime == null) doctype = DT_UNKNOWN;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.endsWith("/gif")) doctype = DT_IMAGE;
        else if (mime.endsWith("/jpeg")) doctype = DT_IMAGE;
        else if (mime.endsWith("/png")) doctype = DT_IMAGE;
        else if (mime.endsWith("/html")) doctype = DT_HTML;
        else if (mime.endsWith("/rtf")) doctype = DT_DOC;
        else if (mime.endsWith("/pdf")) doctype = DT_PDFPS;
        else if (mime.endsWith("/octet-stream")) doctype = DT_BINARY;
        else if (mime.endsWith("/x-shockwave-flash")) doctype = DT_FLASH;
        else if (mime.endsWith("/msword")) doctype = DT_DOC;
        else if (mime.endsWith("/mspowerpoint")) doctype = DT_DOC;
        else if (mime.endsWith("/postscript")) doctype = DT_PDFPS;
        else if (mime.startsWith("text/")) doctype = DT_TEXT;
        else if (mime.startsWith("audio/")) doctype = DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = DT_MOVIE;
        return doctype;
    }

    public static String[] doctype2mime(String ext, char doctype) {
        if (doctype == DT_PDFPS) return new String[]{"application/pdf"};
        if (doctype == DT_HTML) return new String[]{"text/html"};
        if (doctype == DT_DOC) return new String[]{"application/msword"};
        if (doctype == DT_FLASH) return new String[]{"application/x-shockwave-flash"};
        if (doctype == DT_SHARE) return new String[]{"text/plain"};
        if (doctype == DT_BINARY) return new String[]{"application/octet-stream"};
        String mime = Classification.ext2mime(ext);
        int p = mime.indexOf('/');
        if (p < 0) return new String[]{mime};
        if (doctype == DT_TEXT) return new String[]{"text" + mime.substring(p)};
    	if (doctype == DT_IMAGE) return new String[]{"image" + mime.substring(p)};
    	if (doctype == DT_AUDIO) return new String[]{"audio" + mime.substring(p)};
    	if (doctype == DT_MOVIE) return new String[]{"video" + mime.substring(p)};
    	return new String[]{mime};
    }

    public static final int QUEUE_STATE_FRESH             = 0;
    public static final int QUEUE_STATE_PARSING           = 1;
    public static final int QUEUE_STATE_CONDENSING        = 2;
    public static final int QUEUE_STATE_STRUCTUREANALYSIS = 3;
    public static final int QUEUE_STATE_INDEXSTORAGE      = 4;
    public static final int QUEUE_STATE_FINISHED          = 5;

    public Response(
            final Request request,
            final RequestHeader requestHeader,
            final ResponseHeader responseHeader,
            final CrawlProfile profile,
            final boolean fromCache,
            final byte[] content) {
        this.request = request;
        // request and response headers may be zero in case that we process surrogates
        this.requestHeader = requestHeader;
        this.responseHeader = responseHeader;
        this.profile = profile;
        this.status = QUEUE_STATE_FRESH;
        this.content = content;
        this.fromCache = fromCache;
        if (this.responseHeader != null && content != null && Integer.parseInt(this.responseHeader.get(HeaderFramework.CONTENT_LENGTH, "0")) <= content.length) {
            this.responseHeader.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(content.length)); // repair length 
        }
    }

    /**
     * create a 'virtual' response that is composed using crawl details from the request object
     * this is used when the NOLOAD queue is processed
     * @param request
     * @param profile
     */
    public Response(final Request request, final CrawlProfile profile) {
        this.request = request;
        // request and response headers may be zero in case that we process surrogates
        this.requestHeader = new RequestHeader();
        this.responseHeader = new ResponseHeader(200);
        this.responseHeader.put(HeaderFramework.CONTENT_TYPE, Classification.ext2mime(MultiProtocolURL.getFileExtension(request.url().getFileName()), "text/plain")); // tell parser how to handle the content
        this.profile = profile;
        this.status = QUEUE_STATE_FRESH;
        this.content = request.name().length() > 0 ? UTF8.getBytes(request.name()) : UTF8.getBytes(request.url().toTokens());
        this.fromCache = true;
        if (this.responseHeader != null) this.responseHeader.put(HeaderFramework.CONTENT_LENGTH, "0"); // 'virtual' length, shows that the resource was not loaded
    }

    public Response(
            final Request request,
            final RequestHeader requestHeader,
            final ResponseHeader responseHeader,
            final CrawlProfile profile,
            final boolean fromCache) {
        this(request, requestHeader, responseHeader, profile, fromCache, null);
    }

    public void updateStatus(final int newStatus) {
        this.status = newStatus;
    }

    public ResponseHeader getResponseHeader() {
        return this.responseHeader;
    }

    public boolean fromCache() {
        return this.fromCache;
    }

    public int getStatus() {
        return this.status;
    }

    public String name() {
        // the anchor name; can be either the text inside the anchor tag or the
        // page description after loading of the page
        return this.request.name();
    }

    public DigestURL url() {
        return this.request.url();
    }

    public char docType() {
        char doctype = docType(getMimeType());
        if (doctype == DT_UNKNOWN) doctype = docType(url());
        return doctype;
    }

    public Date lastModified() {
        Date docDate = null;

        if (this.responseHeader != null) {
            docDate = this.responseHeader.lastModified();
            if (docDate == null) docDate = this.responseHeader.date();
        }
        if (docDate == null && this.request != null) docDate = this.request.appdate();
        if (docDate == null) docDate = new Date(GenericFormatter.correctedUTCTime());

        return docDate;
    }

    public CrawlProfile profile() {
        return this.profile;
    }

    public byte[] initiator() {
        return this.request.initiator();
    }

    public boolean proxy() {
        return initiator() == null;
    }

    public long size() {
        if (this.responseHeader != null && this.responseHeader.getContentLengthLong() != -1) {
            // take the size from the response header
            return this.responseHeader.getContentLengthLong();
        }
        if (this.content != null) return this.content.length;
        // the size is unknown
        return -1;
    }

    public int depth() {
        return this.request.depth();
    }

    public void setContent(final byte[] data) {
        this.content = data;
        if (this.responseHeader != null && this.content != null && Integer.parseInt(this.responseHeader.get(HeaderFramework.CONTENT_LENGTH, "0")) <= content.length) {
            this.responseHeader.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(content.length)); // repair length 
        }
    }

    public byte[] getContent() {
        return this.content;
    }

    // the following three methods for cache read/write granting shall be as loose
    // as possible but also as strict as necessary to enable caching of most items

    /**
     * @return NULL if the answer is TRUE, in case of FALSE, the reason as
     *         String is returned
     */
    public String shallStoreCacheForProxy() {

        final String crawlerReason = shallStoreCacheForCrawler();
        if (crawlerReason != null) return crawlerReason;

        // check profile (disabled: we will check this in the plasmaSwitchboard)
        // if (!this.profile.storeHTCache()) { return "storage_not_wanted"; }

        // decide upon header information if a specific file should be stored to
        // the cache or not
        // if the storage was requested by prefetching, the request map is null

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable
        // in caches
        if (url().isPOST() && this.profile != null && !this.profile.crawlingQ()) {
            return "dynamic_post";
        }

        if (MultiProtocolURL.isCGI(MultiProtocolURL.getFileExtension(url().getFileName()))) {
            return "dynamic_cgi";
        }

        if (url().isLocal()) {
            return "local_URL_no_cache_needed";
        }

        if (this.responseHeader != null) {

            // -if-modified-since in request
            // we do not care about if-modified-since, because this case only occurres if the
            // cache file does not exist, and we need as much info as possible for the indexing

            // -cookies in request
            // we do not care about cookies, because that would prevent loading more pages
            // from one domain once a request resulted in a client-side stored cookie

            // -set-cookie in response
            // we do not care about cookies in responses, because that info comes along
            // any/many pages from a server and does not express the validity of the page
            // in modes of life-time/expiration or individuality

            // -pragma in response
            // if we have a pragma non-cache, we don't cache. usually if this is wanted from
            // the server, it makes sense
            String cacheControl = this.responseHeader.get(HeaderFramework.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return "controlled_no_cache"; }

            // -expires in response
            // we do not care about expires, because at the time this is called the data is
            // obvious valid and that header info is used in the indexing later on

            // -cache-control in response
            // the cache-control has many value options.
            cacheControl = this.responseHeader.get(HeaderFramework.CACHE_CONTROL);
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    final Date date = this.responseHeader.date();
                    if (date == null) return "stale_no_date_given_in_response";
                    try {
                        final long ttl = 1000 * NumberTools.parseLongDecSubstring(cacheControl, 8); // milliseconds to live
                        if (GenericFormatter.correctedUTCTime() - date.getTime() > ttl) {
                            //System.out.println("***not indexed because cache-control");
                            return "stale_expired";
                        }
                    } catch (final Exception e) {
                        return "stale_error_" + e.getMessage() + ")";
                    }
                }
            }
        }
        return null;
    }

    public String shallStoreCacheForCrawler() {
        // check storage size: all files will be handled in RAM before storage, so they must not exceed
        // a given size, which we consider as 1MB
        if (size() > 10 * 1024L * 1024L) return "too_large_for_caching_" + size();

        // check status code
        if (!validResponseStatus()) {
            return "bad_status_" + this.responseHeader.getStatusCode();
        }

        if (this.requestHeader != null) {
            // -authorization cases in request
            // authorization makes pages very individual, and therefore we cannot use the
            // content in the cache
            if (this.requestHeader.containsKey(RequestHeader.AUTHORIZATION)) { return "personalized"; }
            // -ranges in request and response
            // we do not cache partial content
            if (this.requestHeader.containsKey(HeaderFramework.RANGE)) { return "partial_request"; }
        }

        if (this.responseHeader != null) {
            // -ranges in request and response
            // we do not cache partial content
            if (this.responseHeader.containsKey(HeaderFramework.CONTENT_RANGE)) { return "partial_response"; }
        }
        return null;
    }

    /**
     * decide upon header information if a specific file should be taken from
     * the cache or not
     *
     * @return whether the file should be taken from the cache
     */
    public boolean isFreshForProxy() {

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable
        // in caches
        if (url().isPOST()) {
            return false;
        }
        if (MultiProtocolURL.isCGI(MultiProtocolURL.getFileExtension(url().getFileName()))) {
            return false;
        }

        String cacheControl;
        if (this.requestHeader != null) {
            // -authorization cases in request
            if (this.requestHeader.containsKey(RequestHeader.AUTHORIZATION)) { return false; }

            // -ranges in request
            // we do not cache partial content
            if (this.requestHeader.containsKey(HeaderFramework.RANGE)) { return false; }

            // if the client requests a un-cached copy of the resource ...
            cacheControl = this.requestHeader.get(HeaderFramework.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return false; }

            cacheControl = this.requestHeader.get(HeaderFramework.CACHE_CONTROL);
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("NO-CACHE") || cacheControl.startsWith("MAX-AGE=0")) { return false; }
            }

            // -if-modified-since in request
            // The entity has to be transferred only if it has
            // been modified since the date given by the If-Modified-Since header.
            if (this.requestHeader.containsKey(RequestHeader.IF_MODIFIED_SINCE)) {
                // checking this makes only sense if the cached response contains
                // a Last-Modified field. If the field does not exist, we go the safe way
                if (!this.responseHeader.containsKey(HeaderFramework.LAST_MODIFIED)) { return false; }
                // parse date
                Date d1, d2;
                d2 = this.responseHeader.lastModified(); if (d2 == null) { d2 = new Date(GenericFormatter.correctedUTCTime()); }
                d1 = this.requestHeader.ifModifiedSince(); if (d1 == null) { d1 = new Date(GenericFormatter.correctedUTCTime()); }
                // finally, we shall treat the cache as stale if the modification time is after the if-.. time
                if (d2.after(d1)) { return false; }
            }

            final String mimeType = getMimeType();
            if (!Classification.isPictureMime(mimeType)) {
                // -cookies in request
                // unfortunately, we should reload in case of a cookie
                // but we think that pictures can still be considered as fresh
                // -set-cookie in cached response
                // this is a similar case as for COOKIE.
                if (this.requestHeader.containsKey(RequestHeader.COOKIE) ||
                    this.responseHeader.containsKey(HeaderFramework.SET_COOKIE) ||
                    this.responseHeader.containsKey(HeaderFramework.SET_COOKIE2)) {
                    return false; // too strong
                }
            }
        }

        if (this.responseHeader != null) {
            // -pragma in cached response
            // logically, we would not need to care about no-cache pragmas in cached response headers,
            // because they cannot exist since they are not written to the cache.
            // So this IF should always fail..
            cacheControl = this.responseHeader.get(HeaderFramework.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return false; }

            // see for documentation also:
            // http://www.web-caching.com/cacheability.html
            // http://vancouver-webpages.com/CacheNow/

            // look for freshnes information
            // if we don't have any freshnes indication, we treat the file as stale.
            // no handle for freshness control:

            // -expires in cached response
            // the expires value gives us a very easy hint when the cache is stale
            final Date expires = this.responseHeader.expires();
            if (expires != null) {
    //          System.out.println("EXPIRES-TEST: expires=" + expires + ", NOW=" + serverDate.correctedGMTDate() + ", url=" + url);
                if (expires.before(new Date(GenericFormatter.correctedUTCTime()))) { return false; }
            }
            final Date lastModified = this.responseHeader.lastModified();
            cacheControl = this.responseHeader.get(HeaderFramework.CACHE_CONTROL);
            if (cacheControl == null && lastModified == null && expires == null) { return false; }

            // -lastModified in cached response
            // we can apply a TTL (Time To Live)  heuristic here. We call the time delta between the last read
            // of the file and the last modified date as the age of the file. If we consider the file as
            // middel-aged then, the maximum TTL would be cache-creation plus age.
            // This would be a TTL factor of 100% we want no more than 10% TTL, so that a 10 month old cache
            // file may only be treated as fresh for one more month, not more.
            Date date = this.responseHeader.date();
            if (lastModified != null) {
                if (date == null) { date = new Date(GenericFormatter.correctedUTCTime()); }
                final long age = date.getTime() - lastModified.getTime();
                if (age < 0) { return false; }
                // TTL (Time-To-Live) is age/10 = (d2.getTime() - d1.getTime()) / 10
                // the actual living-time is serverDate.correctedGMTDate().getTime() - d2.getTime()
                // therefore the cache is stale, if serverDate.correctedGMTDate().getTime() - d2.getTime() > age/10
                if (GenericFormatter.correctedUTCTime() - date.getTime() > age / 10) { return false; }
            }

            // -cache-control in cached response
            // the cache-control has many value options.
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("PRIVATE") ||
                    cacheControl.startsWith("NO-CACHE") ||
                    cacheControl.startsWith("NO-STORE")) {
                    // easy case
                    return false;
    //          } else if (cacheControl.startsWith("PUBLIC")) {
    //              // ok, do nothing
                } else if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    if (date == null) { return false; }
                    try {
                        final long ttl = 1000 * NumberTools.parseLongDecSubstring(cacheControl, 8); // milliseconds to live
                        if (GenericFormatter.correctedUTCTime() - date.getTime() > ttl) {
                            return false;
                        }
                    } catch (final Exception e) {
                        return false;
                    }
                }
            }
        }

        return true;
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
            return "indexing not allowed - indexText and indexMedia not set (for proxy = " + this.profile.collectionName()+ ")";
        }

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable in caches
        if (!profile().crawlingQ()) {
            if (url().isPOST()) {
                return "Dynamic_(POST)";
            }
            if (MultiProtocolURL.isCGI(MultiProtocolURL.getFileExtension(url().getFileName()))) {
                return "Dynamic_(CGI)";
            }
        }

        // -authorization cases in request
        // we checked that in shallStoreCache

        // -ranges in request
        // we checked that in shallStoreCache

        // a picture cannot be indexed
        /*
        if (Classification.isMediaExtension(url().getFileExtension())) {
            return "Media_Content_(forbidden)";
        }
         */

        // -cookies in request
        // unfortunately, we cannot index pages which have been requested with a cookie
        // because the returned content may be special for the client
        if (requestWithCookie()) {
//          System.out.println("***not indexed because cookie");
            return "Dynamic_(Requested_With_Cookie)";
        }

        if (this.responseHeader != null) {
            // -set-cookie in response
            // the set-cookie from the server does not indicate that the content is special
            // thus we do not care about it here for indexing

            // a picture cannot be indexed
            final String mimeType = this.responseHeader.mime();
            /*
            if (Classification.isPictureMime(mimeType)) {
                return "Media_Content_(Picture)";
            }
            */
            final String parserError = TextParser.supportsMime(mimeType);
            if (parserError != null) {
                return "Media_Content, no parser: " + parserError;
            }

            // -if-modified-since in request
            // if the page is fresh at the very moment we can index it
            final Date ifModifiedSince = this.ifModifiedSince();
            if ((ifModifiedSince != null) && (this.responseHeader.containsKey(HeaderFramework.LAST_MODIFIED))) {
                // parse date
                Date d = this.responseHeader.lastModified();
                if (d == null) {
                    d = new Date(GenericFormatter.correctedUTCTime());
                }
                // finally, we shall treat the cache as stale if the modification time is after the if-.. time
                if (d.after(ifModifiedSince)) {
                    //System.out.println("***not indexed because if-modified-since");
                    return "Stale_(Last-Modified>Modified-Since)";
                }
            }

            // -pragma in cached response
            if (this.responseHeader.containsKey(HeaderFramework.PRAGMA) &&
                (this.responseHeader.get(HeaderFramework.PRAGMA)).toUpperCase().equals("NO-CACHE")) {
                return "Denied_(pragma_no_cache)";
            }

            // see for documentation also:
            // http://www.web-caching.com/cacheability.html

            // look for freshnes information

            // -expires in cached response
            // the expires value gives us a very easy hint when the cache is stale
            // sometimes, the expires date is set to the past to prevent that a page is cached
            // we use that information to see if we should index it
            final Date expires = this.responseHeader.expires();
            if (expires != null && expires.before(new Date(GenericFormatter.correctedUTCTime()))) {
                return "Stale_(Expired)";
            }

            // -lastModified in cached response
            // this information is too weak to use it to prevent indexing
            // even if we can apply a TTL heuristic for cache usage

            // -cache-control in cached response
            // the cache-control has many value options.
            String cacheControl = this.responseHeader.get(HeaderFramework.CACHE_CONTROL);
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
                    final Date date = this.responseHeader.date();
                    if (date == null) {
                        return "Stale_(no_date_given_in_response)";
                    }
                    try {
                        final long ttl = 1000 * NumberTools.parseLongDecSubstring(cacheControl,8); // milliseconds to live
                        if (GenericFormatter.correctedUTCTime() - date.getTime() > ttl) {
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
            return "indexing not allowed - indexText and indexMedia not set (for crawler = " + this.profile.collectionName() + ")";
        }

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable in caches
        if (!profile().crawlingQ()) {
            if (url().isPOST()) { return "Dynamic_(POST)"; }
            if (MultiProtocolURL.isCGI(MultiProtocolURL.getFileExtension(url().getFileName()))) { return "Dynamic_(CGI)"; }
        }

        // -authorization cases in request
        // we checked that in shallStoreCache

        // -ranges in request
        // we checked that in shallStoreCache

        // check if document can be indexed
        if (this.responseHeader != null) {
            final String mimeType = this.responseHeader.mime();
            final String parserError = TextParser.supportsMime(mimeType);
            if (parserError != null && TextParser.supportsExtension(url()) != null)  return "no parser available: " + parserError;
        }
        /*
        if (Classification.isMediaExtension(url().getFileExtension()) &&
           !Classification.isImageExtension((url().getFileExtension()))) {
            return "Media_Content_(forbidden)";
        }
         */

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

    public String getMimeType() {
        if (this.responseHeader == null) return null;

        String mimeType = this.responseHeader.mime();
        mimeType = mimeType.trim().toLowerCase();

        final int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType : mimeType.substring(0, pos));
    }

    public String getCharacterEncoding() {
        if (this.responseHeader == null) return null;
        return this.responseHeader.getCharacterEncoding();
    }

    public DigestURL referrerURL() {
        if (this.requestHeader == null) return null;
        try {
            final String r = this.requestHeader.get(RequestHeader.REFERER, null);
            if (r == null) return null;
            return new DigestURL(r);
        } catch (final Exception e) {
            return null;
        }
    }

    public byte[] referrerHash() {
        if (this.requestHeader == null) return null;
        final String u = this.requestHeader.get(RequestHeader.REFERER, "");
        if (u == null || u.isEmpty()) return null;
        try {
            return new DigestURL(u).hash();
        } catch (final Exception e) {
            return null;
        }
    }

    public boolean validResponseStatus() {
        int status = this.responseHeader.getStatusCode();
        return status == 200 || status == 203;
    }

    public Date ifModifiedSince() {
        return (this.requestHeader == null) ? null : this.requestHeader.ifModifiedSince();
    }

    public boolean requestWithCookie() {
        return (this.requestHeader == null) ? false : this.requestHeader.containsKey(RequestHeader.COOKIE);
    }

    public boolean requestProhibitsIndexing() {
        return (this.requestHeader == null)
        ? false
        : this.requestHeader.containsKey(HeaderFramework.X_YACY_INDEX_CONTROL) &&
          (this.requestHeader.get(HeaderFramework.X_YACY_INDEX_CONTROL)).toUpperCase().equals("NO-INDEX");
    }

    public EventOrigin processCase(final String mySeedHash) {
        // we must distinguish the following cases: resource-load was initiated by
        // 1) global crawling: the index is extern, not here (not possible here)
        // 2) result of search queries, some indexes are here (not possible here)
        // 3) result of index transfer, some of them are here (not possible here)
        // 4) proxy-load (initiator is "------------")
        // 5) local prefetch/crawling (initiator is own seedHash)
        // 6) local fetching for global crawling (other known or unknwon initiator)
        EventOrigin processCase = EventOrigin.UNKNOWN;
        // FIXME the equals seems to be incorrect: String.equals(boolean)
        if (initiator() == null || initiator().length == 0 || ASCII.String(initiator()).equals("------------")) {
            // proxy-load
            processCase = EventOrigin.PROXY_LOAD;
        } else if (UTF8.String(initiator()).equals(mySeedHash)) {
            // normal crawling
            processCase = EventOrigin.LOCAL_CRAWLING;
        } else {
            // this was done for remote peer (a global crawl)
            processCase = EventOrigin.GLOBAL_CRAWLING;
        }
        return processCase;
    }

    public Document[] parse() throws Parser.Failure {
        final String supportError = TextParser.supports(url(), this.responseHeader == null ? null : this.responseHeader.mime());
        if (supportError != null) throw new Parser.Failure("no parser support:" + supportError, url());
        try {
            return TextParser.parseSource(new AnchorURL(url()), this.responseHeader == null ? null : this.responseHeader.mime(), this.responseHeader == null ? "UTF-8" : this.responseHeader.getCharacterEncoding(), this.request.depth(), this.content);
        } catch (final Exception e) {
            return null;
        }

    }
}
