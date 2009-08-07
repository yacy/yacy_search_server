// LoaderDispatcher.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.10.2007 on http://yacy.net
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

package de.anomic.crawler.retrieval;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.crawler.CrawlProfile;
import de.anomic.http.client.Cache;
import de.anomic.http.metadata.HeaderFramework;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.http.metadata.ResponseHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverCore;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

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
    
    public static byte[] toBytes(Response response) {
        if (response == null) return null;
        return response.getContent();
    }
    
    public Response load(final yacyURL url) throws IOException {
        return load(url, true, false);
    }
    
    public Response load(final yacyURL url, int cachePolicy) throws IOException {
        return load(url, true, false, cachePolicy);
    }
    
    public Response load(
            final yacyURL url,
            final boolean forText,
            final boolean global
                    ) throws IOException {
        return load(request(url, forText, global));
    }
    
    public Response load(
            final yacyURL url,
            final boolean forText,
            final boolean global,
            int cacheStratgy
    ) throws IOException {
        return load(request(url, forText, global), cacheStratgy);
    }
    
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
    
    public Response load(final Request request) throws IOException {
        CrawlProfile.entry crawlProfile = sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle());
        int cacheStrategy = CrawlProfile.CACHE_STRATEGY_IFFRESH;
        if (crawlProfile != null) cacheStrategy = crawlProfile.cacheStrategy();
        return load(request, cacheStrategy);
    }
    
    public Response load(final Request request, int cacheStrategy) throws IOException {
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
            byte[] content = (cachedResponse == null) ? null : Cache.getContent(request.url());
            if (cachedResponse != null && content != null) {
                // yes we have the content
                
                // create request header values and a response object because we need that
                // in case that we want to return the cached content in the next step
                final RequestHeader requestHeader = new RequestHeader();
                requestHeader.put(HeaderFramework.USER_AGENT, HTTPLoader.crawlerUserAgent);
                yacyURL refererURL = null;
                if (request.referrerhash() != null) refererURL = sb.getURL(request.referrerhash());
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
        if ((protocol.equals("http") || (protocol.equals("https")))) response = httpLoader.load(request);
        if (protocol.equals("ftp")) response = ftpLoader.load(request);
        if (response != null) {
            // we got something. Now check if we want to store that to the cache
            String storeError = response.shallStoreCache();
            if (storeError == null) {
                Cache.store(request.url(), response.getResponseHeader(), response.getContent());
            } else {
                if (Cache.log.isFine()) Cache.log.logFine("no storage of url " + request.url() + ": " + storeError);
            }
            return response;
        }
        
        throw new IOException("Unsupported protocol '" + protocol + "' in url " + request.url());
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