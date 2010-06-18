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

package net.yacy.repository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.document.Document;
import net.yacy.document.TextParser;
import net.yacy.document.ParserException;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.Domains;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.FTPLoader;
import de.anomic.crawler.retrieval.FileLoader;
import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.crawler.retrieval.Request;
import de.anomic.crawler.retrieval.Response;
import de.anomic.crawler.retrieval.SMBLoader;
import de.anomic.http.client.Cache;
import de.anomic.http.client.Client;
import de.anomic.http.server.HeaderFramework;
import de.anomic.http.server.RequestHeader;
import de.anomic.http.server.ResponseHeader;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;

public final class LoaderDispatcher {

    private static final long minDelay = 250; // milliseconds; 4 accesses per second
    private static final ConcurrentHashMap<String, Long> accessTime = new ConcurrentHashMap<String, Long>(); // to protect targets from DDoS
    
    private final Switchboard sb;
    private final HashSet<String> supportedProtocols;
    private final HTTPLoader httpLoader;
    private final FTPLoader ftpLoader;
    private final SMBLoader smbLoader;
    private final FileLoader fileLoader;
    private final Log log;
    
    public LoaderDispatcher(final Switchboard sb) {
        this.sb = sb;
        this.supportedProtocols = new HashSet<String>(Arrays.asList(new String[]{"http","https","ftp","smb","file"}));
        
        // initiate loader objects
        this.log = new Log("LOADER");
        httpLoader = new HTTPLoader(sb, log);
        ftpLoader = new FTPLoader(sb, log);
        smbLoader = new SMBLoader(sb, log);
        fileLoader = new FileLoader(sb, log);
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
            final DigestURI url,
            final boolean forText,
            final boolean global,
            final long maxFileSize) throws IOException {
        return load(request(url, forText, global), maxFileSize);
    }
    
    /**
     * load a resource from the web, from ftp, from smb or a file
     * @param url
     * @param forText
     * @param global
     * @param cacheStratgy strategy according to CACHE_STRATEGY_NOCACHE,CACHE_STRATEGY_IFFRESH,CACHE_STRATEGY_IFEXIST,CACHE_STRATEGY_CACHEONLY
     * @return the loaded entity in a Response object
     * @throws IOException
     */
    public Response load(
            final DigestURI url,
            final boolean forText,
            final boolean global,
            CrawlProfile.CacheStrategy cacheStratgy,
            long maxFileSize) throws IOException {
        return load(request(url, forText, global), cacheStratgy, maxFileSize);
    }
    
    public void load(final DigestURI url, CrawlProfile.CacheStrategy cacheStratgy, long maxFileSize, File targetFile) throws IOException {

        byte[] b = load(request(url, false, true), cacheStratgy, maxFileSize).getContent();
        if (b == null) throw new IOException("load == null");
        File tmp = new File(targetFile.getAbsolutePath() + ".tmp");
        
        // transaction-safe writing
        File parent = targetFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        FileUtils.copy(b, tmp);
        tmp.renameTo(targetFile);
    }
    
    /**
     * generate a request object
     * @param url the target url
     * @param forText shows that this was a for-text crawling request
     * @param global shows that this was a global crawling request
     * @return the request object
     */
    public Request request(
            final DigestURI url,
            final boolean forText,
            final boolean global
                    ) {
        return new Request(
                    sb.peers.mySeed().hash.getBytes(), 
                    url, 
                    null, 
                    "", 
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
    
    public Response load(final Request request, long maxFileSize) throws IOException {
        CrawlProfile.entry crawlProfile = sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle());
        CrawlProfile.CacheStrategy cacheStrategy = CrawlProfile.CacheStrategy.IFEXIST;
        if (crawlProfile != null) cacheStrategy = crawlProfile.cacheStrategy();
        return load(request, cacheStrategy, maxFileSize);
    }
    
    public Response load(final Request request, CrawlProfile.CacheStrategy cacheStrategy, long maxFileSize) throws IOException {
        // get the protocol of the next URL
        final String protocol = request.url().getProtocol();
        final String host = request.url().getHost();
        
        // check if this loads a page from localhost, which must be prevented to protect the server
        // against attacks to the administration interface when localhost access is granted
        if (Domains.isLocal(host) && sb.getConfigBool("adminAccountForLocalhost", false)) throw new IOException("access to localhost not granted for url " + request.url());
        
        // check if we have the page in the cache

        CrawlProfile.entry crawlProfile = sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle());
        if (crawlProfile != null && cacheStrategy != CrawlProfile.CacheStrategy.NOCACHE) {
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
                DigestURI refererURL = null;
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
                if (cacheStrategy == CrawlProfile.CacheStrategy.IFEXIST || cacheStrategy == CrawlProfile.CacheStrategy.CACHEONLY) {
                    // well, just take the cache and don't care about freshness of the content
                    log.logInfo("cache hit/useall for: " + request.url().toNormalform(true, false));
                    return response;
                }
                
                // now the cacheStrategy must be CACHE_STRATEGY_IFFRESH, that means we should do a proxy freshness test
                assert cacheStrategy == CrawlProfile.CacheStrategy.IFFRESH : "cacheStrategy = " + cacheStrategy;
                if (response.isFreshForProxy()) {
                    log.logInfo("cache hit/fresh for: " + request.url().toNormalform(true, false));
                    return response;
                } else {
                    log.logInfo("cache hit/stale for: " + request.url().toNormalform(true, false));
                }
            }
        }
        
        // check case where we want results from the cache exclusively, and never from the internet (offline mode)
        if (cacheStrategy == CrawlProfile.CacheStrategy.CACHEONLY) {
            // we had a chance to get the content from the cache .. its over. We don't have it.
            throw new IOException("cache only strategy");
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
        if (host != null) accessTime.put(host, System.currentTimeMillis());
        
        // load resource from the internet
        Response response = null;
        if ((protocol.equals("http") || (protocol.equals("https")))) response = httpLoader.load(request, maxFileSize);
        if (protocol.equals("ftp")) response = ftpLoader.load(request, true);
        if (protocol.equals("smb")) response = smbLoader.load(request, true);
        if (protocol.equals("file")) response = fileLoader.load(request, true);
        if (response != null) {
            // we got something. Now check if we want to store that to the cache
            // first check looks if we want to store the content to the cache
            if (!crawlProfile.storeHTCache()) {
                // no caching wanted. Thats ok, do not write any message
                return response;
            }
            // second check tells us if the protocoll tells us something about caching
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
     * load the url as resource from the web or the cache
     * @param url
     * @param fetchOnline
     * @param socketTimeout
     * @param forText 
     * @return the content as {@link byte[]}
     * @throws IOException 
     */
    public byte[] getResource(final DigestURI url, final boolean fetchOnline, final int socketTimeout, final boolean forText, final boolean reindexing) throws IOException {
        byte[] resource = Cache.getContent(url);
        if (resource != null) return resource;
        
        if (!fetchOnline) return null;
        
        // try to download the resource using the loader
        final long maxFileSize = sb.getConfigLong("crawler.http.maxFileSize", HTTPLoader.DEFAULT_MAXFILESIZE);
        final Response entry = load(url, forText, reindexing, maxFileSize);
        if (entry == null) return null; // not found in web
        
        // read resource body (if it is there)
        return entry.getContent();
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
    public static Document retrieveDocument(final DigestURI url, final boolean fetchOnline, final int timeout, final boolean forText, final boolean global, long maxFileSize) {

        // load resource
        byte[] resContent = null;
        ResponseHeader responseHeader = null;
        try {
            // trying to load the resource from the cache
            resContent = Cache.getContent(url);
            responseHeader = Cache.getResponseHeader(url);
            if (resContent != null) {
                // if the content was found
            } else if (fetchOnline) {
                // if not found try to download it
                
                // download resource using the crawler and keep resource in memory if possible
                final Response entry = Switchboard.getSwitchboard().loader.load(url, forText, global, maxFileSize);
                
                // getting resource metadata (e.g. the http headers for http resources)
                if (entry != null) {

                    // read resource body (if it is there)
                    final byte[] resourceArray = entry.getContent();
                    if (resourceArray != null) {
                        resContent = resourceArray;
                    } else {
                        resContent = Cache.getContent(url); 
                    }
                    
                    // read a fresh header
                    responseHeader = entry.getResponseHeader();
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
            document = parseDocument(url, resContent.length, new ByteArrayInputStream(resContent), responseHeader);            
        } catch (final ParserException e) {
            Log.logFine("snippet fetch", "parser error " + e.getMessage() + " for url " + url);
            return null;
        } finally {
            resContent = null;
        }
        return document;
    }
    
    /**
     * Parse the resource
     * @param url the URL of the resource
     * @param contentLength the contentLength of the resource
     * @param resourceStream the resource body as stream
     * @param docInfo metadata about the resource
     * @return the extracted data
     * @throws ParserException
     */
    public static Document parseDocument(final DigestURI url, final long contentLength, final InputStream resourceStream, ResponseHeader responseHeader) throws ParserException {
        try {
            if (resourceStream == null) return null;

            // STEP 1: if no resource metadata is available, try to load it from cache 
            if (responseHeader == null) {
                // try to get the header from the htcache directory
                try {                    
                    responseHeader = Cache.getResponseHeader(url);
                } catch (final Exception e) {
                    // ignore this. resource info loading failed
                }   
            }
            
            // STEP 2: if the metadata is still null try to download it from web
            if ((responseHeader == null) && (url.getProtocol().startsWith("http"))) {
                // TODO: we need a better solution here
                // e.g. encapsulate this in the crawlLoader class
                
                // getting URL mimeType
                try {
                    responseHeader = Client.whead(url.toString());
                } catch (final Exception e) {
                    // ingore this. http header download failed
                } 
            }

            // STEP 3: if the metadata is still null try to guess the mimeType of the resource
            String supportError = TextParser.supports(url, responseHeader == null ? null : responseHeader.mime());
            if (supportError != null) {
                return null;
            }
            if (responseHeader == null) {
                return TextParser.parseSource(url, null, null, contentLength, resourceStream);
            }
            return TextParser.parseSource(url, responseHeader.mime(), responseHeader.getCharacterEncoding(), contentLength, resourceStream);
        } catch (final InterruptedException e) {
            // interruption of thread detected
            return null;
        }
    }

    public static ContentScraper parseResource(final LoaderDispatcher loader, final DigestURI location, CrawlProfile.CacheStrategy cachePolicy) throws IOException {
        // load page
        final long maxFileSize = loader.sb.getConfigLong("crawler.http.maxFileSize", HTTPLoader.DEFAULT_MAXFILESIZE);
        Response r = loader.load(location, true, false, cachePolicy, maxFileSize);
        byte[] page = (r == null) ? null : r.getContent();
        if (page == null) throw new IOException("no response from url " + location.toString());
        
        // scrape content
        final ContentScraper scraper = new ContentScraper(location);
        final Writer writer = new TransformerWriter(null, null, scraper, null, false);
        writer.write(new String(page, "UTF-8"));
        
        return scraper;
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
    
    public void loadIfNotExistBackground(String url, File cache, long maxFileSize) {
        new Loader(url, cache, maxFileSize).start();
    }
    
    private class Loader extends Thread {

        private String url;
        private File cache;
        private long maxFileSize;
        
        public Loader(String url, File cache, long maxFileSize) {
            this.url = url;
            this.cache = cache;
            this.maxFileSize = maxFileSize;
        }
        
        public void run() {
            if (this.cache.exists()) return;
            try {
                // load from the net
                Response response = load(new DigestURI(this.url), false, true, CrawlProfile.CacheStrategy.NOCACHE, this.maxFileSize);
                byte[] b = response.getContent();
                FileUtils.copy(b, this.cache);
            } catch (MalformedURLException e) {} catch (IOException e) {}
        }
    }
}