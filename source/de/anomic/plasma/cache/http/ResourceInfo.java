// ResourceInfo.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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

package de.anomic.plasma.cache.http;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.ResourceInfoFactory;
import de.anomic.server.serverDate;
import de.anomic.yacy.yacyURL;

public class ResourceInfo implements IResourceInfo {
    private final yacyURL url;
    private final httpHeader responseHeader;
    private httpHeader requestHeader;
    
    /**
     * Constructor used by the {@link ResourceInfoFactory}
     * @param objectURL
     * @param objectInfo
     */
    public ResourceInfo(final yacyURL objectURL, final Map<String, String> objectInfo) {
        if (objectURL == null) throw new NullPointerException();
        if (objectInfo == null) throw new NullPointerException();
        
        // generating the url hash
        this.url = objectURL;
        
        // create the http header object
        this.responseHeader =  new httpHeader(null, objectInfo);
    }

    public ResourceInfo(final yacyURL objectURL, final httpHeader requestHeaders, final httpHeader responseHeaders) {
        if (objectURL == null) throw new NullPointerException();
        if (responseHeaders == null) throw new NullPointerException();  
        
        // generating the url hash
        this.url = objectURL;
        
        this.requestHeader = requestHeaders;
        this.responseHeader = responseHeaders;
    }
    
    public TreeMap<String, String> getMap() {
        return this.responseHeader;
    }
    
    /**
     * @see de.anomic.plasma.cache.IResourceInfo#getMimeType()
     */
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

    /**
     * @see de.anomic.plasma.cache.IResourceInfo#getModificationDate()
     */
    public Date getModificationDate() {
        Date docDate = null;
        
        if (this.responseHeader != null) {
            docDate = this.responseHeader.lastModified();
            if (docDate == null) docDate = this.responseHeader.date();
        }
        if (docDate == null) docDate = new Date(serverDate.correctedUTCTime());   
        
        return docDate;
    }
    
    public yacyURL getRefererUrl() {
        if (this.requestHeader == null) return null;
        try {
            return new yacyURL((String) this.requestHeader.get(httpHeader.REFERER, ""), null);
        } catch (final Exception e) {
            return null;
        }        
    }
    
    /**
     * @see de.anomic.plasma.cache.IResourceInfo#getUrl()
     */
    public yacyURL getUrl() {
        return this.url;
    }
    
    /**
     * @see de.anomic.plasma.cache.IResourceInfo#getUrlHash()
     */    
    public String getUrlHash() {
        return this.url.hash();
    }
    
    public void setRequestHeader(final httpHeader reqestHeader) {
        this.requestHeader = reqestHeader;
    }

    /**
     * @see de.anomic.plasma.cache.IResourceInfo#shallIndexCacheForCrawler()
     */
    public String shallIndexCacheForCrawler() {
        final String mimeType = this.getMimeType();
        if (plasmaHTCache.isPicture(mimeType)) { return "Media_Content_(Picture)"; }
        if (!plasmaHTCache.isText(mimeType)) { return "Media_Content_(not_text)"; }
        return null;
    }

    /**
     * @see de.anomic.plasma.cache.IResourceInfo#shallIndexCacheForProxy()
     */
    public String shallIndexCacheForProxy() {
        // -set-cookie in response
        // the set-cookie from the server does not indicate that the content is special
        // thus we do not care about it here for indexing                
        
        // a picture cannot be indexed
        final String mimeType = this.getMimeType();
        if (plasmaHTCache.isPicture(mimeType)) {
            return "Media_Content_(Picture)";
        }
        if (!plasmaHTCache.isText(mimeType)) {
            return "Media_Content_(not_text)";
        }

        // -if-modified-since in request
        // if the page is fresh at the very moment we can index it
        final Date ifModifiedSince = getModificationDate();
        if ((ifModifiedSince != null) && (this.responseHeader.containsKey(httpHeader.LAST_MODIFIED))) {
            // parse date
            Date d = this.responseHeader.lastModified();
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
        if (this.responseHeader.containsKey(httpHeader.PRAGMA) &&
            (this.responseHeader.get(httpHeader.PRAGMA)).toUpperCase().equals("NO-CACHE")) {
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
        if (expires != null && expires.before(new Date(serverDate.correctedUTCTime()))) {
            return "Stale_(Expired)";
        }

        // -lastModified in cached response
        // this information is too weak to use it to prevent indexing
        // even if we can apply a TTL heuristic for cache usage

        // -cache-control in cached response
        // the cache-control has many value options.
        String cacheControl = this.responseHeader.get(httpHeader.CACHE_CONTROL);
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
//          } else if (cacheControl.startsWith("PUBLIC")) {
//              // ok, do nothing
            } else if (cacheControl.startsWith("MAX-AGE=")) {
                // we need also the load date
                final Date date = this.responseHeader.date();
                if (date == null) {
                    return "Stale_(no_date_given_in_response)";
                }
                try {
                    final long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                    if (serverDate.correctedUTCTime() - date.getTime() > ttl) {
                        //System.out.println("***not indexed because cache-control");
                        return "Stale_(expired_by_cache-control)";
                    }
                } catch (final Exception e) {
                    return "Error_(" + e.getMessage() + ")";
                }
            }
        }
        return null;
    }

    public String shallStoreCacheForProxy() {
        if (this.requestHeader != null) {
            // -authorization cases in request
            // authorization makes pages very individual, and therefore we cannot use the
            // content in the cache
            if (this.requestHeader.containsKey(httpHeader.AUTHORIZATION)) { return "personalized"; }
            // -ranges in request and response
            // we do not cache partial content
            if (this.requestHeader.containsKey(httpHeader.RANGE)) { return "partial"; }
        }
        
        if (this.responseHeader != null) {
            // -ranges in request and response
            // we do not cache partial content            
            if (this.responseHeader.containsKey(httpHeader.CONTENT_RANGE)) { return "partial"; }

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
            String cacheControl = this.responseHeader.get(httpHeader.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return "controlled_no_cache"; }

            // -expires in response
            // we do not care about expires, because at the time this is called the data is
            // obvious valid and that header info is used in the indexing later on

            // -cache-control in response
            // the cache-control has many value options.
            cacheControl = this.responseHeader.get(httpHeader.CACHE_CONTROL);
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    final Date date = this.responseHeader.date();
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

    public boolean shallUseCacheForProxy() {
        
        String cacheControl;
        if (this.requestHeader != null) {
            // -authorization cases in request
            if (this.requestHeader.containsKey(httpHeader.AUTHORIZATION)) { return false; }

            // -ranges in request
            // we do not cache partial content
            if (this.requestHeader.containsKey(httpHeader.RANGE)) { return false; }

            // if the client requests a un-cached copy of the resource ...
            cacheControl = this.requestHeader.get(httpHeader.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return false; }

            cacheControl = this.requestHeader.get(httpHeader.CACHE_CONTROL);
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("NO-CACHE") || cacheControl.startsWith("MAX-AGE=0")) { return false; }
            }

            // -if-modified-since in request
            // The entity has to be transferred only if it has
            // been modified since the date given by the If-Modified-Since header.
            if (this.requestHeader.containsKey(httpHeader.IF_MODIFIED_SINCE)) {
                // checking this makes only sense if the cached response contains
                // a Last-Modified field. If the field does not exist, we go the safe way
                if (!this.responseHeader.containsKey(httpHeader.LAST_MODIFIED)) { return false; }
                // parse date
                Date d1, d2;
                d2 = this.responseHeader.lastModified(); if (d2 == null) { d2 = new Date(serverDate.correctedUTCTime()); }
                d1 = this.requestHeader.ifModifiedSince(); if (d1 == null) { d1 = new Date(serverDate.correctedUTCTime()); }
                // finally, we shall treat the cache as stale if the modification time is after the if-.. time
                if (d2.after(d1)) { return false; }
            }

            final String mimeType = this.getMimeType();
            if (!plasmaHTCache.isPicture(mimeType)) {
                // -cookies in request
                // unfortunately, we should reload in case of a cookie
                // but we think that pictures can still be considered as fresh
                // -set-cookie in cached response
                // this is a similar case as for COOKIE.
                if (this.requestHeader.containsKey(httpHeader.COOKIE) ||
                    this.responseHeader.containsKey(httpHeader.SET_COOKIE) ||
                    this.responseHeader.containsKey(httpHeader.SET_COOKIE2)) {
                    return false; // too strong
                }
            }
        }

        // -pragma in cached response
        // logically, we would not need to care about no-cache pragmas in cached response headers,
        // because they cannot exist since they are not written to the cache.
        // So this IF should always fail..
        cacheControl = this.responseHeader.get(httpHeader.PRAGMA); 
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
            if (expires.before(new Date(serverDate.correctedUTCTime()))) { return false; }
        }
        final Date lastModified = this.responseHeader.lastModified();
        cacheControl = this.responseHeader.get(httpHeader.CACHE_CONTROL);
        if (cacheControl == null && lastModified == null && expires == null) { return false; }

        // -lastModified in cached response
        // we can apply a TTL (Time To Live)  heuristic here. We call the time delta between the last read
        // of the file and the last modified date as the age of the file. If we consider the file as
        // middel-aged then, the maximum TTL would be cache-creation plus age.
        // This would be a TTL factor of 100% we want no more than 10% TTL, so that a 10 month old cache
        // file may only be treated as fresh for one more month, not more.
        Date date = this.responseHeader.date();
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
        return true;
    }

    public boolean validResponseStatus(final String responseStatus) {
        return responseStatus.startsWith("200") ||
               responseStatus.startsWith("203");
    }

    public Date ifModifiedSince() {
        return (this.requestHeader == null) ? null : this.requestHeader.ifModifiedSince();
    }

    public boolean requestWithCookie() {
        return (this.requestHeader == null) ? false : this.requestHeader.containsKey(httpHeader.COOKIE);
    }

    public boolean requestProhibitsIndexing() {
        return (this.requestHeader == null) 
        ? false 
        : this.requestHeader.containsKey(httpHeader.X_YACY_INDEX_CONTROL) &&
          (this.requestHeader.get(httpHeader.X_YACY_INDEX_CONTROL)).toUpperCase().equals("NO-INDEX");
    }
    
    public httpHeader getRequestHeader() {
        return this.requestHeader;
    }
    
    public httpHeader getResponseHeader() {
        return this.responseHeader;
    }
}
