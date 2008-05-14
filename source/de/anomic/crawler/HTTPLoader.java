//plasmaCrawlWorker.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Date;

import de.anomic.http.HttpClient;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.JakartaCommonsHttpResponse;
import de.anomic.http.httpHeader;
import de.anomic.http.httpdBoundedSizeOutputStream;
import de.anomic.http.httpdLimitExceededException;
import de.anomic.index.indexReferenceBlacklist;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.http.ResourceInfo;
import de.anomic.server.serverSystem;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public final class HTTPLoader {

    public static final int DEFAULT_CRAWLING_RETRY_COUNT = 5;
    private static final String crawlerUserAgent = "yacybot (" + HttpClient.getSystemOST() +") http://yacy.net/bot.html";
    
    /**
     * The socket timeout that should be used
     */
    private int socketTimeout;
    
    /**
     * The maximum allowed file size
     */
    private long maxFileSize = -1;
    
    private String acceptEncoding;
    private String acceptLanguage;
    private String acceptCharset;
    private plasmaSwitchboard sb;
    private serverLog log;
    
    public HTTPLoader(plasmaSwitchboard sb, serverLog theLog) {
        this.sb = sb;
        this.log = theLog;
        
        // refreshing timeout value
        this.socketTimeout = (int) sb.getConfigLong("crawler.clientTimeout", 10000);
        
        // maximum allowed file size
        this.maxFileSize = sb.getConfigLong("crawler.http.maxFileSize", -1);
        
        // some http header values
        this.acceptEncoding = sb.getConfig("crawler.http.acceptEncoding", "gzip,deflate");
        this.acceptLanguage = sb.getConfig("crawler.http.acceptLanguage","en-us,en;q=0.5");
        this.acceptCharset  = sb.getConfig("crawler.http.acceptCharset","ISO-8859-1,utf-8;q=0.7,*;q=0.7");
            }

    /**
     * @param entry
     * @param requestDate
     * @param requestHeader
     * @param responseHeader
     * @param responseStatus Status-Code SPACE Reason-Phrase
     * @return
     */
    protected plasmaHTCache.Entry createCacheEntry(CrawlEntry entry, Date requestDate, httpHeader requestHeader, httpHeader responseHeader, final String responseStatus) {
        IResourceInfo resourceInfo = new ResourceInfo(entry.url(), requestHeader, responseHeader);
        return plasmaHTCache.newEntry(
                requestDate, 
                entry.depth(),
                entry.url(),
                entry.name(),
                responseStatus,
                resourceInfo, 
                entry.initiator(),
                sb.webIndex.profilesActiveCrawls.getEntry(entry.profileHandle())
        );
    }    
   
    public plasmaHTCache.Entry load(CrawlEntry entry, String parserMode) {
        return load(entry, parserMode, DEFAULT_CRAWLING_RETRY_COUNT);
    }
    
    private plasmaHTCache.Entry load(CrawlEntry entry, String parserMode, int retryCount) {

        if (retryCount < 0) {
            this.log.logInfo("Redirection counter exceeded for URL " + entry.url().toString() + ". Processing aborted.");
            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_REDIRECTION_COUNTER_EXCEEDED).store();
            return null;
        }
        
        Date requestDate = new Date(); // remember the time...
        String host = entry.url().getHost();
        String path = entry.url().getFile();
        int port = entry.url().getPort();
        boolean ssl = entry.url().getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        
        // check if url is in blacklist
        String hostlow = host.toLowerCase();
        if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_CRAWLER, hostlow, path)) {
            this.log.logInfo("CRAWLER Rejecting URL '" + entry.url().toString() + "'. URL is in blacklist.");
            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_URL_IN_BLACKLIST).store();
            return null;
        }
        
        // take a file from the net
        plasmaHTCache.Entry htCache = null;
        try {
            // create a request header
            httpHeader requestHeader = new httpHeader();
            requestHeader.put(httpHeader.USER_AGENT, crawlerUserAgent);
            yacyURL refererURL = null;
            if (entry.referrerhash() != null) refererURL = sb.getURL(entry.referrerhash());
            if (refererURL != null)
                requestHeader.put(httpHeader.REFERER, refererURL.toNormalform(true, true));
            if (this.acceptLanguage != null && this.acceptLanguage.length() > 0)
                requestHeader.put(httpHeader.ACCEPT_LANGUAGE, this.acceptLanguage);
            if (this.acceptCharset != null && this.acceptCharset.length() > 0)
                requestHeader.put(httpHeader.ACCEPT_CHARSET, this.acceptCharset);
            if (this.acceptEncoding != null && this.acceptEncoding.length() > 0)
                requestHeader.put(httpHeader.ACCEPT_ENCODING, this.acceptEncoding);

            // HTTP-Client
            JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(socketTimeout, requestHeader, null);
            
            JakartaCommonsHttpResponse res = null;
            try {
                // send request
                res = client.GET(entry.url().toString());

            if (res.getStatusCode() == 200 || res.getStatusCode() == 203) {
                // the transfer is ok
                
                // create a new cache entry
                htCache = createCacheEntry(entry, requestDate, requestHeader, res.getResponseHeader(), res.getStatusLine()); 
                
                // aborting download if content is to long ...
                if (htCache.cacheFile().getAbsolutePath().length() > serverSystem.maxPathLength) {
                    this.log.logInfo("REJECTED URL " + entry.url().toString() + " because path too long '" + plasmaHTCache.cachePath.getAbsolutePath() + "'");
                    sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_CACHEFILE_PATH_TOO_LONG);                    
                    return (htCache = null);
                }

                // reserve cache entry
                if (!htCache.cacheFile().getCanonicalPath().startsWith(plasmaHTCache.cachePath.getCanonicalPath())) {
                    // if the response has not the right file type then reject file
                    this.log.logInfo("REJECTED URL " + entry.url().toString() + " because of an invalid file path ('" +
                                htCache.cacheFile().getCanonicalPath() + "' does not start with '" +
                                plasmaHTCache.cachePath.getAbsolutePath() + "').");
                    sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_INVALID_CACHEFILE_PATH);
                    return (htCache = null);
                }

                // request has been placed and result has been returned. work off response
                File cacheFile = plasmaHTCache.getCachePath(entry.url());
                try {
                    if (plasmaParser.supportedContent(parserMode, entry.url(), res.getResponseHeader().mime())) {
                        // delete old content
                        if (cacheFile.isFile()) {
                            plasmaHTCache.deleteURLfromCache(entry.url());
                        }
                        
                        // create parent directories
                        cacheFile.getParentFile().mkdirs();
                        
                        OutputStream fos = null;
                        try {
                            // creating an output stream
                            fos = new FileOutputStream(cacheFile); 
                            
                            // getting content length
                            long contentLength = res.getResponseHeader().contentLength();
                            
                            // check the maximum allowed file size                            
                            if (this.maxFileSize > -1) {                                
                                if (contentLength == -1) {
                                    fos = new httpdBoundedSizeOutputStream(fos,this.maxFileSize);                     
                                } else if (contentLength > this.maxFileSize) {
                                    this.log.logInfo("REJECTED URL " + entry.url() + " because file size '" + contentLength + "' exceeds max filesize limit of " + this.maxFileSize + " bytes.");
                                    sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_FILESIZE_LIMIT_EXCEEDED);                    
                                    return null;
                                }
                            }

                            // we write the new cache entry to file system directly
                            ((JakartaCommonsHttpResponse)res).setAccountingName("CRAWLER");
                            byte[] responseBody = res.getData();
                            fos.write(responseBody);
                            htCache.setCacheArray(responseBody);
                            plasmaHTCache.writeFileAnnouncement(cacheFile);
                        } finally {
                            if (fos!=null)try{fos.close();}catch(Exception e){/* ignore this */}
                        }
                        
                        return htCache;
                    } else {
                        // if the response has not the right file type then reject file
                        this.log.logInfo("REJECTED WRONG MIME/EXT TYPE " + res.getResponseHeader().mime() + " for URL " + entry.url().toString());
                        sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_WRONG_MIMETYPE_OR_EXT);
                        return null;
                    }
                } catch (SocketException e) {
                    // this may happen if the client suddenly closes its connection
                    // maybe the user has stopped loading
                    // in that case, we are not responsible and just forget it
                    // but we clean the cache also, since it may be only partial
                    // and most possible corrupted
                    if (cacheFile.exists()) cacheFile.delete();
                    this.log.logSevere("CRAWLER LOADER ERROR1: with URL=" + entry.url().toString() + ": " + e.toString());
                    sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_CONNECTION_ERROR);
                    htCache = null;
                }
            } else if (res.getStatusLine().startsWith("30")) {
                    if (res.getResponseHeader().containsKey(httpHeader.LOCATION)) {
                        // getting redirection URL
                        String redirectionUrlString = (String) res.getResponseHeader().get(httpHeader.LOCATION);
                        redirectionUrlString = redirectionUrlString.trim();

                        if (redirectionUrlString.length() == 0) {
                            this.log.logWarning("CRAWLER Redirection of URL=" + entry.url().toString() + " aborted. Location header is empty.");
                            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_REDIRECTION_HEADER_EMPTY);
                            return null;
                        }
                        
                        // normalizing URL
                        yacyURL redirectionUrl = yacyURL.newURL(entry.url(), redirectionUrlString);

                        // restart crawling with new url
                        this.log.logInfo("CRAWLER Redirection detected ('" + res.getStatusLine() + "') for URL " + entry.url().toString());
                        this.log.logInfo("CRAWLER ..Redirecting request to: " + redirectionUrl);

                        // if we are already doing a shutdown we don't need to retry crawling
                        if (Thread.currentThread().isInterrupted()) {
                            this.log.logSevere("CRAWLER Retry of URL=" + entry.url().toString() + " aborted because of server shutdown.");
                            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_SERVER_SHUTDOWN);
                            return null;
                        }

                        // generating url hash
                        String urlhash = redirectionUrl.hash();
                        
                        // check if the url was already indexed
                        String dbname = sb.urlExists(urlhash);
                        if (dbname != null) {
                            this.log.logWarning("CRAWLER Redirection of URL=" + entry.url().toString() + " ignored. The url appears already in db " + dbname);
                            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_REDIRECTION_TO_DOUBLE_CONTENT);
                            return null;
                        }
                        
                        // retry crawling with new url
                        entry.redirectURL(redirectionUrl);
                        return load(entry, plasmaParser.PARSER_MODE_URLREDIRECTOR, retryCount - 1);
                        
                    }
            } else {
                // if the response has not the right response type then reject file
                this.log.logInfo("REJECTED WRONG STATUS TYPE '" + res.getStatusLine() + "' for URL " + entry.url().toString());
                
                // not processed any further
                sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_WRONG_HTTP_STATUSCODE + res.getStatusCode() +  ")");
            }
            
            } finally {
                if(res != null) {
                    // release connection
                    res.closeStream();
                }
            }
            return htCache;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            String failreason = null;

            if ((e instanceof IOException) && 
                (errorMsg != null) && 
                (errorMsg.indexOf("socket closed") >= 0) &&
                (Thread.currentThread().isInterrupted())
            ) {
                this.log.logInfo("CRAWLER Interruption detected because of server shutdown.");
                failreason = ErrorURL.DENIED_SERVER_SHUTDOWN;
            } else if (e instanceof httpdLimitExceededException) {
                this.log.logWarning("CRAWLER Max file size limit '" + this.maxFileSize + "' exceeded while downloading URL " + entry.url());
                failreason = ErrorURL.DENIED_FILESIZE_LIMIT_EXCEEDED;                    
            } else if (e instanceof MalformedURLException) {
                this.log.logWarning("CRAWLER Malformed URL '" + entry.url().toString() + "' detected. ");
                failreason = ErrorURL.DENIED_MALFORMED_URL;
            } else if (e instanceof NoRouteToHostException) {
                this.log.logWarning("CRAWLER No route to host found while trying to crawl URL  '" + entry.url().toString() + "'.");
                failreason = ErrorURL.DENIED_NO_ROUTE_TO_HOST;
            } else if ((e instanceof UnknownHostException) ||
                       ((errorMsg != null) && (errorMsg.indexOf("unknown host") >= 0))) {
                yacyURL u = (entry.referrerhash() == null) ? null : sb.getURL(entry.referrerhash());
                this.log.logWarning("CRAWLER Unknown host in URL '" + entry.url() + "'. " +
                        "Referer URL: " + ((u == null) ? "Unknown" : u.toNormalform(true, true)));
                failreason = ErrorURL.DENIED_UNKNOWN_HOST;
            } else if (e instanceof java.net.BindException) {
                this.log.logWarning("CRAWLER BindException detected while trying to download content from '" + entry.url().toString() +
                "'. Retrying request.");
                failreason = ErrorURL.DENIED_CONNECTION_BIND_EXCEPTION;
            } else if ((errorMsg != null) && (
            		(errorMsg.indexOf("Corrupt GZIP trailer") >= 0) ||
            		(errorMsg.indexOf("Not in GZIP format") >= 0) ||
            		(errorMsg.indexOf("Unexpected end of ZLIB") >= 0)
            )) {
                this.log.logWarning("CRAWLER Problems detected while receiving gzip encoded content from '" + entry.url().toString() +
                "'. Retrying request without using gzip content encoding.");
                failreason = ErrorURL.DENIED_CONTENT_DECODING_ERROR;
                this.acceptEncoding = null;
            } else if ((errorMsg != null) && (errorMsg.indexOf("The host did not accept the connection within timeout of") >= 0)) {
                this.log.logWarning("CRAWLER Timeout while trying to connect to '" + entry.url().toString() +
                "'. Retrying request.");
                failreason = ErrorURL.DENIED_CONNECTION_TIMEOUT;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Read timed out") >= 0)) {
                this.log.logWarning("CRAWLER Read timeout while receiving content from '" + entry.url().toString() +
                "'. Retrying request.");
                failreason = ErrorURL.DENIED_CONNECTION_TIMEOUT;
            } else if ((errorMsg != null) && (errorMsg.indexOf("connect timed out") >= 0)) {
                this.log.logWarning("CRAWLER Timeout while trying to connect to '" + entry.url().toString() +
                "'. Retrying request.");
                failreason = ErrorURL.DENIED_CONNECTION_TIMEOUT;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Connection timed out") >= 0)) {
                this.log.logWarning("CRAWLER Connection timeout while receiving content from '" + entry.url().toString() +
                "'. Retrying request.");
                failreason = ErrorURL.DENIED_CONNECTION_TIMEOUT;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Connection refused") >= 0)) {
                this.log.logWarning("CRAWLER Connection refused while trying to connect to '" + entry.url().toString() + "'.");
                failreason = ErrorURL.DENIED_CONNECTION_REFUSED;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Circular redirect to '")>= 0)) {
                this.log.logWarning("CRAWLER Redirect Error with URL '" + entry.url().toString() + "': "+ e.toString());  
                failreason = ErrorURL.DENIED_REDIRECTION_COUNTER_EXCEEDED;
            } else if ((errorMsg != null) && (errorMsg.indexOf("There is not enough space on the disk") >= 0)) {
                this.log.logSevere("CRAWLER Not enough space on the disk detected while crawling '" + entry.url().toString() + "'. " +
                "Pausing crawlers. ");
                sb.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                sb.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                failreason = ErrorURL.DENIED_OUT_OF_DISK_SPACE;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Network is unreachable") >=0)) {
                this.log.logSevere("CRAWLER Network is unreachable while trying to crawl URL '" + entry.url().toString() + "'. ");
                failreason = ErrorURL.DENIED_NETWORK_IS_UNREACHABLE;
            } else if ((errorMsg != null) && (errorMsg.indexOf("No trusted certificate found")>= 0)) {
                this.log.logSevere("CRAWLER No trusted certificate found for URL '" + entry.url().toString() + "'. ");  
                failreason = ErrorURL.DENIED_SSL_UNTRUSTED_CERT;
            } else {
                this.log.logSevere("CRAWLER Unexpected Error with URL '" + entry.url().toString() + "': " + e.toString(), e);
                failreason = ErrorURL.DENIED_CONNECTION_ERROR;
            }

            if (failreason != null) {
                // add url into error db
                sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.seedDB.mySeed().hash, new Date(), 1, failreason);
            }
            return null;
        }
    }
    
}
