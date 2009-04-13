//plasmaCrawlWorker.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2006
//
// $LastChangedDate: 2006-08-12 16:28:14 +0200 (Sa, 12 Aug 2006) $
// $LastChangedRevision: 2397 $
// $LastChangedBy: theli $
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.crawler;

import java.io.IOException;
import java.util.Date;

import de.anomic.data.Blacklist;
import de.anomic.http.httpClient;
import de.anomic.http.httpResponse;
import de.anomic.http.httpRequestHeader;
import de.anomic.http.httpResponseHeader;
import de.anomic.http.httpdProxyCacheEntry;
import de.anomic.kelondro.util.Log;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.parser.Document;
import de.anomic.yacy.yacyURL;

public final class HTTPLoader {

    private static final String DEFAULT_ENCODING = "gzip,deflate";
    private static final String DEFAULT_LANGUAGE = "en-us,en;q=0.5";
    private static final String DEFAULT_CHARSET = "ISO-8859-1,utf-8;q=0.7,*;q=0.7";
    private static final long   DEFAULT_MAXFILESIZE = 1024 * 1024 * 10;
    public  static final int    DEFAULT_CRAWLING_RETRY_COUNT = 5;
    public  static final String crawlerUserAgent = "yacybot (" + httpClient.getSystemOST() +") http://yacy.net/bot.html";
    public  static final String yacyUserAgent = "yacy (" + httpClient.getSystemOST() +") yacy.net";
    
    /**
     * The socket timeout that should be used
     */
    private final int socketTimeout;
    
    /**
     * The maximum allowed file size
     */
    //private long maxFileSize = -1;
    
    //private String acceptEncoding;
    //private String acceptLanguage;
    //private String acceptCharset;
    private final plasmaSwitchboard sb;
    private final Log log;
    
    public HTTPLoader(final plasmaSwitchboard sb, final Log theLog) {
        this.sb = sb;
        this.log = theLog;
        
        // refreshing timeout value
        this.socketTimeout = (int) sb.getConfigLong("crawler.clientTimeout", 10000);
    }

    /**
     * @param entry
     * @param requestDate
     * @param requestHeader
     * @param responseHeader
     * @param responseStatus Status-Code SPACE Reason-Phrase
     * @return
     */
    protected Document createCacheEntry(final CrawlEntry entry, final Date requestDate, final httpRequestHeader requestHeader, final httpResponseHeader responseHeader, final String responseStatus) {
        Document metadata = new httpdProxyCacheEntry(
                entry.depth(),
                entry.url(),
                entry.name(),
                responseStatus,
                requestHeader,
                responseHeader, 
                entry.initiator(),
                sb.webIndex.profilesActiveCrawls.getEntry(entry.profileHandle())
        );
        plasmaHTCache.storeMetadata(responseHeader, metadata);
        return metadata;
    }    
   
    public Document load(final CrawlEntry entry, final String parserMode) throws IOException {
        long start = System.currentTimeMillis();
        Document doc = load(entry, parserMode, DEFAULT_CRAWLING_RETRY_COUNT);
        Latency.update(entry.url().hash().substring(6), entry.url().getHost(), System.currentTimeMillis() - start);
        return doc;
    }
    
    private Document load(final CrawlEntry entry, final String parserMode, final int retryCount) throws IOException {

        if (retryCount < 0) {
            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "redirection counter exceeded").store();
            throw new IOException("Redirection counter exceeded for URL " + entry.url().toString() + ". Processing aborted.");
        }
        
        final Date requestDate = new Date(); // remember the time...
        final String host = entry.url().getHost();
        final String path = entry.url().getFile();
        int port = entry.url().getPort();
        final boolean ssl = entry.url().getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        
        // check if url is in blacklist
        final String hostlow = host.toLowerCase();
        if (plasmaSwitchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, hostlow, path)) {
            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "url in blacklist").store();
            throw new IOException("CRAWLER Rejecting URL '" + entry.url().toString() + "'. URL is in blacklist.");
        }
        
        // take a file from the net
        Document htCache = null;
        final long maxFileSize = sb.getConfigLong("crawler.http.maxFileSize", DEFAULT_MAXFILESIZE);
        //try {
            // create a request header
            final httpRequestHeader requestHeader = new httpRequestHeader();
            requestHeader.put(httpRequestHeader.USER_AGENT, crawlerUserAgent);
            yacyURL refererURL = null;
            if (entry.referrerhash() != null) refererURL = sb.getURL(entry.referrerhash());
            if (refererURL != null) requestHeader.put(httpRequestHeader.REFERER, refererURL.toNormalform(true, true));
            requestHeader.put(httpRequestHeader.ACCEPT_LANGUAGE, sb.getConfig("crawler.http.acceptLanguage", DEFAULT_LANGUAGE));
            requestHeader.put(httpRequestHeader.ACCEPT_CHARSET, sb.getConfig("crawler.http.acceptCharset", DEFAULT_CHARSET));
            requestHeader.put(httpRequestHeader.ACCEPT_ENCODING, sb.getConfig("crawler.http.acceptEncoding", DEFAULT_ENCODING));

            // HTTP-Client
            final httpClient client = new httpClient(socketTimeout, requestHeader);
            
            httpResponse res = null;
            try {
                // send request
                res = client.GET(entry.url().toString());

                if (res.getStatusCode() == 200 || res.getStatusCode() == 203) {
                    // the transfer is ok
                    
                    // create a new cache entry
                    htCache = createCacheEntry(entry, requestDate, requestHeader, res.getResponseHeader(), res.getStatusLine()); 
                    
                    // request has been placed and result has been returned. work off response
                    //try {
                        if (plasmaParser.supportedContent(parserMode, entry.url(), res.getResponseHeader().mime())) {
                            
                            // get the content length and check if the length is allowed
                            long contentLength = res.getResponseHeader().getContentLength();
                            if (maxFileSize >= 0 && contentLength > maxFileSize) {
                                sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "file size limit exceeded");                    
                                throw new IOException("REJECTED URL " + entry.url() + " because file size '" + contentLength + "' exceeds max filesize limit of " + maxFileSize + " bytes.");
                            }
    
                            // we write the new cache entry to file system directly
                            res.setAccountingName("CRAWLER");
                            final byte[] responseBody = res.getData();
                            contentLength = responseBody.length;
                            
                            // check length again in case it was not possible to get the length before loading
                            if (maxFileSize >= 0 && contentLength > maxFileSize) {
                                sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "file size limit exceeded");                    
                                throw new IOException("REJECTED URL " + entry.url() + " because file size '" + contentLength + "' exceeds max filesize limit of " + maxFileSize + " bytes.");
                            }
                            
                            htCache.setCacheArray(responseBody);
                        } else {
                            // if the response has not the right file type then reject file
                            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "wrong mime type or wrong extension");
                            throw new IOException("REJECTED WRONG MIME/EXT TYPE " + res.getResponseHeader().mime() + " for URL " + entry.url().toString());
                        }
                        return htCache;
                        /*
                    } catch (final SocketException e) {
                        // this may happen if the client suddenly closes its connection
                        // maybe the user has stopped loading
                        // in that case, we are not responsible and just forget it
                        // but we clean the cache also, since it may be only partial
                        // and most possible corrupted
                        this.log.logSevere("CRAWLER LOADER ERROR1: with URL=" + entry.url().toString() + ": " + e.toString());
                        sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_CONNECTION_ERROR);
                        htCache = null;
                    }*/
                } else if (res.getStatusLine().startsWith("30")) {
                        if (res.getResponseHeader().containsKey(httpRequestHeader.LOCATION)) {
                            // getting redirection URL
                            String redirectionUrlString = res.getResponseHeader().get(httpRequestHeader.LOCATION);
                            redirectionUrlString = redirectionUrlString.trim();
    
                            if (redirectionUrlString.length() == 0) {
                                sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "redirection header empy");
                                throw new IOException("CRAWLER Redirection of URL=" + entry.url().toString() + " aborted. Location header is empty.");
                            }
                            
                            // normalizing URL
                            final yacyURL redirectionUrl = yacyURL.newURL(entry.url(), redirectionUrlString);
    
                            // restart crawling with new url
                            this.log.logInfo("CRAWLER Redirection detected ('" + res.getStatusLine() + "') for URL " + entry.url().toString());
                            this.log.logInfo("CRAWLER ..Redirecting request to: " + redirectionUrl);
    
                            // if we are already doing a shutdown we don't need to retry crawling
                            if (Thread.currentThread().isInterrupted()) {
                                sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "server shutdown");
                                throw new IOException("CRAWLER Retry of URL=" + entry.url().toString() + " aborted because of server shutdown.");
                            }
    
                            // generating url hash
                            final String urlhash = redirectionUrl.hash();
                            
                            // check if the url was already indexed
                            final String dbname = sb.urlExists(urlhash);
                            if (dbname != null) {
                                sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "redirection to double content");
                                throw new IOException("CRAWLER Redirection of URL=" + entry.url().toString() + " ignored. The url appears already in db " + dbname);
                            }
                            
                            // retry crawling with new url
                            entry.redirectURL(redirectionUrl);
                            return load(entry, plasmaParser.PARSER_MODE_URLREDIRECTOR, retryCount - 1);
                        }
                } else {
                    // if the response has not the right response type then reject file
                    sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "wrong http status code " + res.getStatusCode() +  ")");
                    throw new IOException("REJECTED WRONG STATUS TYPE '" + res.getStatusLine() + "' for URL " + entry.url().toString());
                }
            } finally {
                if(res != null) {
                    // release connection
                    res.closeStream();
                }
            }
            return htCache;
    }
    
}
