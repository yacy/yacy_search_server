// httpdProxyCacheEntry.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.08.2008 on http://yacy.net
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

package de.anomic.http;

import java.io.File;
import java.util.Date;

import de.anomic.crawler.CrawlProfile;
import de.anomic.index.indexDocumentMetadata;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.server.serverDate;
import de.anomic.server.serverSystem;
import de.anomic.yacy.yacyURL;

public class httpdProxyCacheEntry implements indexDocumentMetadata {
    
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
    private final  int                depth;           // the depth of pre-fetching
    private final  String             responseStatus;
    private final  File               cacheFile;       // the cache file
    private        byte[]             cacheArray;      // or the cache as byte-array
    private final  yacyURL            url;
    private final  String             name;            // the name of the link, read as anchor from an <a>-tag
    private final  CrawlProfile.entry profile;
    private final  String             initiator;
    private        httpRequestHeader  requestHeader;
    private        httpResponseHeader responseHeader;

    // doctype calculation
    public static char docType(final yacyURL url) {
        final String path = url.getPath().toLowerCase();
        // serverLog.logFinest("PLASMA", "docType URL=" + path);
        char doctype = DT_UNKNOWN;
        if (path.endsWith(".gif"))       { doctype = DT_IMAGE; }
        else if (path.endsWith(".ico"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".bmp"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpg"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpeg")) { doctype = DT_IMAGE; }
        else if (path.endsWith(".png"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".html")) { doctype = DT_HTML;  }
        else if (path.endsWith(".txt"))  { doctype = DT_TEXT;  }
        else if (path.endsWith(".doc"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".rtf"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".pdf"))  { doctype = DT_PDFPS; }
        else if (path.endsWith(".ps"))   { doctype = DT_PDFPS; }
        else if (path.endsWith(".avi"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".mov"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".qt"))   { doctype = DT_MOVIE; }
        else if (path.endsWith(".mpg"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".md5"))  { doctype = DT_SHARE; }
        else if (path.endsWith(".mpeg")) { doctype = DT_MOVIE; }
        else if (path.endsWith(".asf"))  { doctype = DT_FLASH; }
        return doctype;
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
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.startsWith("audio/")) doctype = DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = DT_MOVIE;
        //bz2     = application/x-bzip2
        //dvi     = application/x-dvi
        //gz      = application/gzip
        //hqx     = application/mac-binhex40
        //lha     = application/x-lzh
        //lzh     = application/x-lzh
        //pac     = application/x-ns-proxy-autoconfig
        //php     = application/x-httpd-php
        //phtml   = application/x-httpd-php
        //rss     = application/xml
        //tar     = application/tar
        //tex     = application/x-tex
        //tgz     = application/tar
        //torrent = application/x-bittorrent
        //xhtml   = application/xhtml+xml
        //xla     = application/msexcel
        //xls     = application/msexcel
        //xsl     = application/xml
        //xml     = application/xml
        //Z       = application/x-compress
        //zip     = application/zip
        return doctype;
    }
    
    public httpdProxyCacheEntry(
            final int depth,
            final yacyURL url,
            final String name,
            final String responseStatus,
            final httpRequestHeader requestHeader,
            final httpResponseHeader responseHeader,
            final String initiator,
            final CrawlProfile.entry profile) {
        if (responseHeader == null) {
            System.out.println("Response header information is null. " + url);
            System.exit(0);
        }
        this.requestHeader = requestHeader;
        this.responseHeader = responseHeader;
        this.url = url;
        this.name = name;
        this.cacheFile = plasmaHTCache.getCachePath(this.url);

        // assigned:
        this.depth = depth;
        this.responseStatus = responseStatus;
        this.profile = profile;
        
        // the initiator is the hash of the peer that caused the hash entry
        // it is stored here only to track processed in the peer and this
        // information is not permanently stored in the web index after the queue has
        // been processed
        // in case of proxy usage, the initiator hash is null,
        // which distinguishes local crawling from proxy indexing
        this.initiator = (initiator == null) ? null : ((initiator.length() == 0) ? null : initiator);

        // to be defined later:
        this.cacheArray = null;
    }

    public String name() {
        // the anchor name; can be either the text inside the anchor tag or the
        // page description after loading of the page
        return this.name;
    }

    public yacyURL url() {
        return this.url;
    }
    
    public char docType() {
        char doctype = docType(getMimeType());
        if (doctype == DT_UNKNOWN) doctype = docType(url);
        return doctype;
    }

    public String urlHash() {
        return this.url.hash();
    }

    public Date lastModified() {
        Date docDate = null;
        
        if (responseHeader != null) {
            docDate = responseHeader.lastModified();
            if (docDate == null) docDate = responseHeader.date();
        }
        if (docDate == null) docDate = new Date(serverDate.correctedUTCTime());   
        
        return docDate;
    }
    
    public String language() {
        // please avoid this method if a condenser document is available, because the condenser has a built-in language detection
        // this here is only a guess using the TLD
        return this.url().language();
    }

    public CrawlProfile.entry profile() {
        return this.profile;
    }

    public String initiator() {
        return this.initiator;
    }

    public boolean proxy() {
        return initiator() == null;
    }

    public long size() {
        if (this.cacheArray == null)
            return 0;
        return this.cacheArray.length;
    }

    public int depth() {
        return this.depth;
    }

    public File cacheFile() {
        return this.cacheFile;
    }

    public void setCacheArray(final byte[] data) {
        this.cacheArray = data;
    }

    public byte[] cacheArray() {
        return this.cacheArray;
    }

    // the following three methods for cache read/write granting shall be as loose
    // as possible but also as strict as necessary to enable caching of most items

    /**
     * @return NULL if the answer is TRUE, in case of FALSE, the reason as
     *         String is returned
     */
    public String shallStoreCacheForProxy() {

        // check profile (disabled: we will check this in the plasmaSwitchboard)
        // if (!this.profile.storeHTCache()) { return "storage_not_wanted"; }

        // decide upon header information if a specific file should be stored to
        // the cache or not
        // if the storage was requested by prefetching, the request map is null

        // check status code
        if (!validResponseStatus()) {
            return "bad_status_" + this.responseStatus.substring(0, 3);
        }

        // check storage location
        // sometimes a file name is equal to a path name in the same directory;
        // or sometimes a file name is equal a directory name created earlier;
        // we cannot match that here in the cache file path and therefore omit
        // writing into the cache
        if (this.cacheFile.getParentFile().isFile()
                || this.cacheFile.isDirectory()) {
            return "path_ambiguous";
        }
        if (this.cacheFile.toString().indexOf("..") >= 0) {
            return "path_dangerous";
        }
        if (this.cacheFile.getAbsolutePath().length() > serverSystem.maxPathLength) {
            return "path too long";
        }

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable
        // in caches
        if (this.url.isPOST() && !this.profile.crawlingQ()) {
            return "dynamic_post";
        }
        if (this.url.isCGI()) {
            return "dynamic_cgi";
        }
        
        if (requestHeader != null) {
            // -authorization cases in request
            // authorization makes pages very individual, and therefore we cannot use the
            // content in the cache
            if (requestHeader.containsKey(httpRequestHeader.AUTHORIZATION)) { return "personalized"; }
            // -ranges in request and response
            // we do not cache partial content
            if (requestHeader.containsKey(httpHeader.RANGE)) { return "partial"; }
        }
        
        if (responseHeader != null) {
            // -ranges in request and response
            // we do not cache partial content            
            if (responseHeader.containsKey(httpHeader.CONTENT_RANGE)) { return "partial"; }

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
            String cacheControl = responseHeader.get(httpResponseHeader.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return "controlled_no_cache"; }

            // -expires in response
            // we do not care about expires, because at the time this is called the data is
            // obvious valid and that header info is used in the indexing later on

            // -cache-control in response
            // the cache-control has many value options.
            cacheControl = responseHeader.get(httpResponseHeader.CACHE_CONTROL);
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    final Date date = responseHeader.date();
                    if (date == null) return "stale_no_date_given_in_response";
                    try {
                        final long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                        if (serverDate.correctedUTCTime() - date.getTime() > ttl) {
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

    
    /**
     * decide upon header information if a specific file should be taken from
     * the cache or not
     * 
     * @return whether the file should be taken from the cache
     */
    public boolean shallUseCacheForProxy() {

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable
        // in caches
        if (this.url.isPOST()) {
            return false;
        }
        if (this.url.isCGI()) {
            return false;
        }

        String cacheControl;
        if (requestHeader != null) {
            // -authorization cases in request
            if (requestHeader.containsKey(httpRequestHeader.AUTHORIZATION)) { return false; }

            // -ranges in request
            // we do not cache partial content
            if (requestHeader.containsKey(httpHeader.RANGE)) { return false; }

            // if the client requests a un-cached copy of the resource ...
            cacheControl = requestHeader.get(httpResponseHeader.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return false; }

            cacheControl = requestHeader.get(httpResponseHeader.CACHE_CONTROL);
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("NO-CACHE") || cacheControl.startsWith("MAX-AGE=0")) { return false; }
            }

            // -if-modified-since in request
            // The entity has to be transferred only if it has
            // been modified since the date given by the If-Modified-Since header.
            if (requestHeader.containsKey(httpRequestHeader.IF_MODIFIED_SINCE)) {
                // checking this makes only sense if the cached response contains
                // a Last-Modified field. If the field does not exist, we go the safe way
                if (!responseHeader.containsKey(httpResponseHeader.LAST_MODIFIED)) { return false; }
                // parse date
                Date d1, d2;
                d2 = responseHeader.lastModified(); if (d2 == null) { d2 = new Date(serverDate.correctedUTCTime()); }
                d1 = requestHeader.ifModifiedSince(); if (d1 == null) { d1 = new Date(serverDate.correctedUTCTime()); }
                // finally, we shall treat the cache as stale if the modification time is after the if-.. time
                if (d2.after(d1)) { return false; }
            }

            final String mimeType = getMimeType();
            if (!plasmaHTCache.isPicture(mimeType)) {
                // -cookies in request
                // unfortunately, we should reload in case of a cookie
                // but we think that pictures can still be considered as fresh
                // -set-cookie in cached response
                // this is a similar case as for COOKIE.
                if (requestHeader.containsKey(httpRequestHeader.COOKIE) ||
                    responseHeader.containsKey(httpResponseHeader.SET_COOKIE) ||
                    responseHeader.containsKey(httpResponseHeader.SET_COOKIE2)) {
                    return false; // too strong
                }
            }
        }

        if (responseHeader != null) {
            // -pragma in cached response
            // logically, we would not need to care about no-cache pragmas in cached response headers,
            // because they cannot exist since they are not written to the cache.
            // So this IF should always fail..
            cacheControl = responseHeader.get(httpResponseHeader.PRAGMA); 
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return false; }
    
            // see for documentation also:
            // http://www.web-caching.com/cacheability.html
            // http://vancouver-webpages.com/CacheNow/
    
            // look for freshnes information
            // if we don't have any freshnes indication, we treat the file as stale.
            // no handle for freshness control:
    
            // -expires in cached response
            // the expires value gives us a very easy hint when the cache is stale
            final Date expires = responseHeader.expires();
            if (expires != null) {
    //          System.out.println("EXPIRES-TEST: expires=" + expires + ", NOW=" + serverDate.correctedGMTDate() + ", url=" + url);
                if (expires.before(new Date(serverDate.correctedUTCTime()))) { return false; }
            }
            final Date lastModified = responseHeader.lastModified();
            cacheControl = responseHeader.get(httpResponseHeader.CACHE_CONTROL);
            if (cacheControl == null && lastModified == null && expires == null) { return false; }
    
            // -lastModified in cached response
            // we can apply a TTL (Time To Live)  heuristic here. We call the time delta between the last read
            // of the file and the last modified date as the age of the file. If we consider the file as
            // middel-aged then, the maximum TTL would be cache-creation plus age.
            // This would be a TTL factor of 100% we want no more than 10% TTL, so that a 10 month old cache
            // file may only be treated as fresh for one more month, not more.
            Date date = responseHeader.date();
            if (lastModified != null) {
                if (date == null) { date = new Date(serverDate.correctedUTCTime()); }
                final long age = date.getTime() - lastModified.getTime();
                if (age < 0) { return false; }
                // TTL (Time-To-Live) is age/10 = (d2.getTime() - d1.getTime()) / 10
                // the actual living-time is serverDate.correctedGMTDate().getTime() - d2.getTime()
                // therefore the cache is stale, if serverDate.correctedGMTDate().getTime() - d2.getTime() > age/10
                if (serverDate.correctedUTCTime() - date.getTime() > age / 10) { return false; }
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
                        final long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                        if (serverDate.correctedUTCTime() - date.getTime() > ttl) {
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
    
    public String getMimeType() {
        if (responseHeader == null) return null;
        
        String mimeType = responseHeader.mime();
        mimeType = mimeType.trim().toLowerCase();
        
        final int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType : mimeType.substring(0, pos));          
    }
    
    public String getCharacterEncoding() {
        if (responseHeader == null) return null;
        return responseHeader.getCharacterEncoding();      
    }
    
    public yacyURL referrerURL() {
        if (requestHeader == null) return null;
        try {
            return new yacyURL((String) requestHeader.get(httpRequestHeader.REFERER, ""), null);
        } catch (final Exception e) {
            return null;
        }        
    }
    
    public boolean validResponseStatus() {
        return (responseStatus == null) ? false : responseStatus.startsWith("200") || responseStatus.startsWith("203");
    }

    public Date ifModifiedSince() {
        return (requestHeader == null) ? null : requestHeader.ifModifiedSince();
    }

    public boolean requestWithCookie() {
        return (requestHeader == null) ? false : requestHeader.containsKey(httpRequestHeader.COOKIE);
    }

    public boolean requestProhibitsIndexing() {
        return (requestHeader == null) 
        ? false 
        : requestHeader.containsKey(httpHeader.X_YACY_INDEX_CONTROL) &&
          (requestHeader.get(httpHeader.X_YACY_INDEX_CONTROL)).toUpperCase().equals("NO-INDEX");
    }
    

}
