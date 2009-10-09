// LoaderDispatcher.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.10.2007 on http://yacy.net
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

package de.anomic.crawler.retrieval;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.logging.Log;

import de.anomic.crawler.CrawlProfile;
import de.anomic.document.Document;
import de.anomic.document.ParserException;
import de.anomic.http.client.Cache;
import de.anomic.http.metadata.HeaderFramework;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.http.metadata.ResponseHeader;
import de.anomic.kelondro.text.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverCore;
import de.anomic.yacy.yacyURL;

public final class LoaderDispatcher {

    private static final long minDelay = 250; // milliseconds; 4 accesses per second
    private static final ConcurrentHashMap<String, Long> accessTime = new ConcurrentHashMap<String, Long>(); // to protect targets from DDoS
    
    private final Switchboard sb;
    private final HashSet<String> supportedProtocols;
    private final HTTPLoader httpLoader;
    private final FTPLoader ftpLoader;
    private final Log log;
    
    public LoaderDispatcher(final Switchboard sb) {
        this.sb = sb;
        this.supportedProtocols = new HashSet<String>(Arrays.asList(new String[]{"http","https","ftp"}));
        
        // initiate loader objects
        this.log = new Log("LOADER");
        httpLoader = new HTTPLoader(sb, log);
        ftpLoader = new FTPLoader(sb, log);
    }
    
    public boolean isSupportedProtocol(final String protocol) {
        if ((protocol == null) || (protocol.length() == 0)) return false;
        return this.supportedProtocols.contains(protocol.trim().toLowerCase());
    }
    
    @SuppressWarnings("unchecked")
    public HashSet<String> getSupportedProtocols() {
        return (HashSet<String>) this.supportedProtocols.clone();
    }
    
    public Response load(
            final yacyURL url,
            final boolean forText,
            final boolean global
                    ) throws IOException {
        return load(request(url, forText, global), forText);
    }
    
    public Response load(
            final yacyURL url,
            final boolean forText,
            final boolean global,
            int cacheStratgy
    ) throws IOException {
        return load(request(url, forText, global), forText, cacheStratgy);
    }
    
    /**
     * generate a request object
     * @param url the target url
     * @param forText shows that this was a for-text crawling request
     * @param global shows that this was a global crawling request
     * @return the request object
     */
    public Request request(
            final yacyURL url,
            final boolean forText,
            final boolean global
                    ) {
        return new Request(
                    sb.peers.mySeed().hash, 
                    url, 
                    "", 
                    "", 
                    new Date(),
                    new Date(),
                    (forText) ?
                        ((global) ?
                            sb.crawler.defaultTextSnippetGlobalProfile.handle() :
                            sb.crawler.defaultTextSnippetLocalProfile.handle())
                        :
                        ((global) ?
                            sb.crawler.defaultMediaSnippetGlobalProfile.handle() :
                            sb.crawler.defaultMediaSnippetLocalProfile.handle()), // crawl profile
                    0, 
                    0, 
                    0);
    }
    
    public Response load(final Request request, final boolean acceptOnlyParseable) throws IOException {
        CrawlProfile.entry crawlProfile = sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle());
        int cacheStrategy = CrawlProfile.CACHE_STRATEGY_IFFRESH;
        if (crawlProfile != null) cacheStrategy = crawlProfile.cacheStrategy();
        return load(request, acceptOnlyParseable, cacheStrategy);
    }
    
    public Response load(final Request request, final boolean acceptOnlyParseable, int cacheStrategy) throws IOException {
        // get the protocol of the next URL
        final String protocol = request.url().getProtocol();
        final String host = request.url().getHost();
        
        // check if this loads a page from localhost, which must be prevented to protect the server
        // against attacks to the administration interface when localhost access is granted
        if (serverCore.isLocalhost(host) && sb.getConfigBool("adminAccountForLocalhost", false)) throw new IOException("access to localhost not granted for url " + request.url());
        
        // check if we have the page in the cache

        CrawlProfile.entry crawlProfile = sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle());
        if (crawlProfile != null && cacheStrategy != CrawlProfile.CACHE_STRATEGY_NOCACHE) {
            // we have passed a first test if caching is allowed
            // now see if there is a cache entry
        
            ResponseHeader cachedResponse = (request.url().isLocal()) ? null : Cache.getResponseHeader(request.url());
            byte[] content = null;
            try {
                content = (cachedResponse == null) ? null : Cache.getContent(request.url());
            } catch (IOException e) {
                e.printStackTrace();
                content = null;
            }
            if (cachedResponse != null && content != null) {
                // yes we have the content
                
                // create request header values and a response object because we need that
                // in case that we want to return the cached content in the next step
                final RequestHeader requestHeader = new RequestHeader();
                requestHeader.put(HeaderFramework.USER_AGENT, HTTPLoader.crawlerUserAgent);
                yacyURL refererURL = null;
                if (request.referrerhash() != null) refererURL = sb.getURL(Segments.Process.LOCALCRAWLING, request.referrerhash());
                if (refererURL != null) requestHeader.put(RequestHeader.REFERER, refererURL.toNormalform(true, true));
                Response response = new Response(
                        request,
                        requestHeader,
                        cachedResponse,
                        "200",
                        crawlProfile,
                        content);
                
                // check which caching strategy shall be used
                if (cacheStrategy == CrawlProfile.CACHE_STRATEGY_IFEXIST || cacheStrategy == CrawlProfile.CACHE_STRATEGY_CACHEONLY) {
                    // well, just take the cache and don't care about freshness of the content
                    log.logInfo("cache hit/useall for: " + request.url().toNormalform(true, false));
                    return response;
                }
                
                // now the cacheStrategy must be CACHE_STRATEGY_IFFRESH, that means we should do a proxy freshness test
                assert cacheStrategy == CrawlProfile.CACHE_STRATEGY_IFFRESH : "cacheStrategy = " + cacheStrategy;
                if (response.isFreshForProxy()) {
                    log.logInfo("cache hit/fresh for: " + request.url().toNormalform(true, false));
                    return response;
                } else {
                    log.logInfo("cache hit/stale for: " + request.url().toNormalform(true, false));
                }
            }
        }
        
        // check case where we want results from the cache exclusively, and never from the internet (offline mode)
        if (cacheStrategy == CrawlProfile.CACHE_STRATEGY_CACHEONLY) {
            // we had a chance to get the content from the cache .. its over. We don't have it.
            return null;
        }
        
        // now forget about the cache, nothing there. Try to load the content from the internet
        
        // check access time: this is a double-check (we checked possibly already in the balancer)
        // to make sure that we don't DoS the target by mistake
        if (!request.url().isLocal()) {
            final Long lastAccess = accessTime.get(host);
            long wait = 0;
            if (lastAccess != null) wait = Math.max(0, minDelay + lastAccess.longValue() - System.currentTimeMillis());
            if (wait > 0) {
                // force a sleep here. Instead just sleep we clean up the accessTime map
                final long untilTime = System.currentTimeMillis() + wait;
                cleanupAccessTimeTable(untilTime);
                if (System.currentTimeMillis() < untilTime)
                    try {Thread.sleep(untilTime - System.currentTimeMillis());} catch (final InterruptedException ee) {}
            }
        }

        // now it's for sure that we will access the target. Remember the access time
        accessTime.put(host, System.currentTimeMillis());
        
        // load resource from the internet
        Response response = null;
        if ((protocol.equals("http") || (protocol.equals("https")))) response = httpLoader.load(request, acceptOnlyParseable);
        if (protocol.equals("ftp")) response = ftpLoader.load(request);
        if (response != null) {
            // we got something. Now check if we want to store that to the cache
            String storeError = response.shallStoreCacheForCrawler();
            if (storeError == null) {
                try {
                    Cache.store(request.url(), response.getResponseHeader(), response.getContent());
                } catch (IOException e) {
                    log.logWarning("cannot write " + response.url() + " to Cache (3): " + e.getMessage(), e);
                }
            } else {
                log.logWarning("cannot write " + response.url() + " to Cache (4): " + storeError);
            }
            return response;
        }
        
        throw new IOException("Unsupported protocol '" + protocol + "' in url " + request.url());
    }

    /**
     * 
     * @param url
     * @param fetchOnline
     * @param socketTimeout
     * @param forText 
     * @return an Object array containing
     * <table>
     * <tr><td>[0]</td><td>the content as {@link InputStream}</td></tr>
     * <tr><td>[1]</td><td>the content-length as {@link Integer}</td></tr>
     * </table>
     * @throws IOException 
     */
    public Object[] getResource(final yacyURL url, final boolean fetchOnline, final int socketTimeout, final boolean forText, final boolean reindexing) throws IOException {
        // load the url as resource from the web
        long contentLength = -1;
            
        // trying to load the resource body from cache
        InputStream resource = Cache.getContentStream(url);
        if (resource != null) {
            contentLength = Cache.getResourceContentLength(url);
        } else if (fetchOnline) {
            // if the content is not available in cache try to download it from web
            
            // try to download the resource using the loader
            final Response entry = load(url, forText, reindexing);
            if (entry == null) return null; // not found in web
            
            // read resource body (if it is there)
            final byte[] resourceArray = entry.getContent();
        
            // in case that the resource was not in ram, read it from disk
            if (resourceArray == null) {
                resource = Cache.getContentStream(url);   
                contentLength = Cache.getResourceContentLength(url); 
            } else {
                resource = new ByteArrayInputStream(resourceArray);
                contentLength = resourceArray.length;
            }
        } else {
            return null;
        }
        return new Object[]{resource, Long.valueOf(contentLength)};
    }
    
    /**
     * Tries to load and parse a resource specified by it's URL.
     * If the resource is not stored in cache and if fetchOnline is set the
     * this function tries to download the resource from web.
     * 
     * @param url the URL of the resource
     * @param fetchOnline specifies if the resource should be loaded from web if it'as not available in the cache
     * @param timeout 
     * @param forText 
     * @param global the domain of the search. If global == true then the content is re-indexed
     * @return the parsed document as {@link Document}
     */
    public static Document retrieveDocument(final yacyURL url, final boolean fetchOnline, final int timeout, final boolean forText, final boolean global) {

        // load resource
        long resContentLength = 0;
        InputStream resContent = null;
        ResponseHeader responseHeader = null;
        try {
            // trying to load the resource from the cache
            resContent = Cache.getContentStream(url);
            responseHeader = Cache.getResponseHeader(url);
            if (resContent != null) {
                // if the content was found
                resContentLength = Cache.getResourceContentLength(url);
            } else if (fetchOnline) {
                // if not found try to download it
                
                // download resource using the crawler and keep resource in memory if possible
                final Response entry = Switchboard.getSwitchboard().loader.load(url, forText, global);
                
                // getting resource metadata (e.g. the http headers for http resources)
                if (entry != null) {

                    // read resource body (if it is there)
                    final byte[] resourceArray = entry.getContent();
                    if (resourceArray != null) {
                        resContent = new ByteArrayInputStream(resourceArray);
                        resContentLength = resourceArray.length;
                    } else {
                        resContent = Cache.getContentStream(url); 
                        resContentLength = Cache.getResourceContentLength(url);
                    }
                }
                
                // if it is still not available, report an error
                if (resContent == null) {
                    Log.logFine("snippet fetch", "plasmaHTCache.Entry cache is NULL for url " + url);
                    return null;
                }
            } else {
                Log.logFine("snippet fetch", "no resource available for url " + url);
                return null;
            }
        } catch (final Exception e) {
            Log.logFine("snippet fetch", "error loading resource: " + e.getMessage() + " for url " + url);
            return null;
        } 

        // parse resource
        Document document = null;
        try {
            document = Document.parseDocument(url, resContentLength, resContent, responseHeader);            
        } catch (final ParserException e) {
            Log.logFine("snippet fetch", "parser error " + e.getMessage() + " for url " + url);
            return null;
        } finally {
            try { resContent.close(); } catch (final Exception e) {}
        }
        return document;
    }
    

    public synchronized void cleanupAccessTimeTable(long timeout) {
    	final Iterator<Map.Entry<String, Long>> i = accessTime.entrySet().iterator();
        Map.Entry<String, Long> e;
        while (i.hasNext()) {
            e = i.next();
            if (System.currentTimeMillis() > timeout) break;
            if (System.currentTimeMillis() - e.getValue().longValue() > minDelay) i.remove();
        }
    }
}