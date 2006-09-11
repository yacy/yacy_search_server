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

package de.anomic.plasma.crawler.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Date;

import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpc;
import de.anomic.http.httpdBoundedSizeOutputStream;
import de.anomic.http.httpdLimitExceededException;
import de.anomic.http.httpdProxyHandler;
import de.anomic.index.indexURL;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlLoader;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.http.ResourceInfo;
import de.anomic.plasma.crawler.AbstractCrawlWorker;
import de.anomic.plasma.crawler.plasmaCrawlerPool;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverSystem;
import de.anomic.server.logging.serverLog;

public final class CrawlWorker extends AbstractCrawlWorker {

    public static final int DEFAULT_CRAWLING_RETRY_COUNT = 5;
    
    /**
     * The socket timeout that should be used
     */
    private int socketTimeout;
    
    /**
     * The maximum allowed file size
     */
    private long maxFileSize = -1;
    
    /**
     * The remote http proxy that should be used
     */
    private httpRemoteProxyConfig remoteProxyConfig;

    private String acceptEncoding;
    private String acceptLanguage;
    private String acceptCharset;
    
    /**
     * Constructor of this class
     * @param theTG
     * @param thePool
     * @param theSb
     * @param theCacheManager
     * @param theLog
     */
    public CrawlWorker(
            ThreadGroup theTG,
            plasmaCrawlerPool thePool,
            plasmaSwitchboard theSb,
            plasmaHTCache theCacheManager,
            serverLog theLog) {
        super(theTG,thePool,theSb,theCacheManager,theLog);

        // this crawler supports http
        this.protocol = "http";        
    }

    public void init() {
        // refreshing timeout value        
        if (this.theMsg.timeout < 0) {
            this.socketTimeout = (int) this.sb.getConfigLong("crawler.clientTimeout", 10000);
        } else {
            this.socketTimeout = this.theMsg.timeout;
        }
        
        // maximum allowed file size
        this.maxFileSize = this.sb.getConfigLong("crawler.http.maxFileSize", -1);
        
        // some http header values
        this.acceptEncoding = this.sb.getConfig("crawler.http.acceptEncoding", "gzip,deflate");
        this.acceptLanguage = this.sb.getConfig("crawler.http.acceptLanguage","en-us,en;q=0.5");
        this.acceptCharset  = this.sb.getConfig("crawler.http.acceptCharset","ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        
        // getting the http proxy config
        this.remoteProxyConfig = this.sb.remoteProxyConfig;        
    }
    
    public plasmaHTCache.Entry load() throws IOException {
        return load(DEFAULT_CRAWLING_RETRY_COUNT);
    }    

    protected plasmaHTCache.Entry createCacheEntry(URL requestUrl, Date requestDate, httpHeader requestHeader, httpc.response response) {
        IResourceInfo resourceInfo = new ResourceInfo(requestUrl,requestHeader,response.responseHeader);
        return this.cacheManager.newEntry(
                requestDate, 
                this.depth, 
                this.url, 
                this.name,  
                response.status,
                resourceInfo, 
                this.initiator, 
                this.profile
        );
    }    
    
    private plasmaHTCache.Entry load(int crawlingRetryCount) throws IOException {

        // if the recrawling limit was exceeded we stop crawling now
        if (crawlingRetryCount <= 0) return null;

        Date requestDate = new Date(); // remember the time...
        String host = this.url.getHost();
        String path = this.url.getFile();
        int port = this.url.getPort();
        boolean ssl = this.url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        
        // check if url is in blacklist
        String hostlow = host.toLowerCase();
        if (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_CRAWLER, hostlow, path)) {
            this.log.logInfo("CRAWLER Rejecting URL '" + this.url.toString() + "'. URL is in blacklist.");
            addURLtoErrorDB(plasmaCrawlEURL.DENIED_URL_IN_BLACKLIST);
            return null;
        }

        // TODO: resolve yacy and yacyh domains
        //String yAddress = yacyCore.seedDB.resolveYacyAddress(host);

        // take a file from the net
        httpc remote = null;
        plasmaHTCache.Entry htCache = null;
        try {
            // create a request header
            httpHeader requestHeader = new httpHeader();
            requestHeader.put(httpHeader.USER_AGENT, httpdProxyHandler.crawlerUserAgent);
            if (this.refererURLString != null && this.refererURLString.length() > 0)
                requestHeader.put(httpHeader.REFERER, this.refererURLString);
            if (this.acceptLanguage != null && this.acceptLanguage.length() > 0)
                requestHeader.put(httpHeader.ACCEPT_LANGUAGE, this.acceptLanguage);
            if (this.acceptCharset != null && this.acceptCharset.length() > 0)
                requestHeader.put(httpHeader.ACCEPT_CHARSET, this.acceptCharset);
            if (this.acceptEncoding != null && this.acceptEncoding.length() > 0)
                requestHeader.put(httpHeader.ACCEPT_ENCODING, this.acceptEncoding);

            // open the connection
            remote = ((this.remoteProxyConfig != null) && (this.remoteProxyConfig.useProxy()))
                   ? httpc.getInstance(host, host, port, this.socketTimeout, ssl, this.remoteProxyConfig,"CRAWLER",null)
                   : httpc.getInstance(host, host, port, this.socketTimeout, ssl, "CRAWLER",null);

            // specifying if content encoding is allowed
            remote.setAllowContentEncoding((this.acceptEncoding != null && this.acceptEncoding.length() > 0));

            // send request
            httpc.response res = remote.GET(path, requestHeader);

            if (res.status.startsWith("200") || res.status.startsWith("203")) {
                // the transfer is ok
                
                // create a new cache entry
                htCache = createCacheEntry(this.url,requestDate, requestHeader, res); 
                
                // aborting download if content is to long ...
                if (htCache.cacheFile().getAbsolutePath().length() > serverSystem.maxPathLength) {
                    remote.close();
                    this.log.logInfo("REJECTED URL " + this.url.toString() + " because path too long '" + this.cacheManager.cachePath.getAbsolutePath() + "'");
                    addURLtoErrorDB(plasmaCrawlEURL.DENIED_CACHEFILE_PATH_TOO_LONG);                    
                    return (htCache = null);
                }

                // reserve cache entry
                if (!htCache.cacheFile().getCanonicalPath().startsWith(this.cacheManager.cachePath.getCanonicalPath())) {
                    // if the response has not the right file type then reject file
                    remote.close();
                    this.log.logInfo("REJECTED URL " + this.url.toString() + " because of an invalid file path ('" +
                                htCache.cacheFile().getCanonicalPath() + "' does not start with '" +
                                this.cacheManager.cachePath.getAbsolutePath() + "').");
                    addURLtoErrorDB(plasmaCrawlEURL.DENIED_INVALID_CACHEFILE_PATH);
                    return (htCache = null);
                }

                // request has been placed and result has been returned. work off response
                File cacheFile = this.cacheManager.getCachePath(this.url);
                try {
                    if ((this.acceptAllContent) || (plasmaParser.supportedContent(plasmaParser.PARSER_MODE_CRAWLER,this.url,res.responseHeader.mime()))) {
                        // delete old content
                        if (cacheFile.isFile()) {
                            this.cacheManager.deleteFile(this.url);
                        }
                        
                        // create parent directories
                        cacheFile.getParentFile().mkdirs();
                        
                        OutputStream fos = null;
                        try {
                            // creating an output stream
                            fos = new FileOutputStream(cacheFile); 
                            
                            // check the maximum allowed file size                            
                            if (this.maxFileSize > -1) {
                                long contentLength = (res.isGzipped()) ? res.getGzippedLength() : res.responseHeader.contentLength();
                                if (contentLength == -1) {
                                    fos = new httpdBoundedSizeOutputStream(fos,this.maxFileSize);                     
                                } else if (contentLength > this.maxFileSize) {
                                    remote.close();
                                    this.log.logInfo("REJECTED URL " + this.url + " because file size '" + contentLength + "' exceeds max filesize limit.");
                                    addURLtoErrorDB(plasmaCrawlEURL.DENIED_FILESIZE_LIMIT_EXCEEDED);                    
                                    return null;
                                }
                            }

                            // we write the new cache entry to file system directly
                            res.writeContent(fos);
                            htCache.setCacheArray(null);
                            this.cacheManager.writeFileAnnouncement(cacheFile);
                        } finally {
                            if (fos!=null)try{fos.close();}catch(Exception e){/* ignore this */}
                        }
                        
                        // enQueue new entry with response header
                        if (this.profile != null) {
                            this.cacheManager.push(htCache);
                        }
                    } else {
                        // if the response has not the right file type then reject file
                        remote.close();
                        this.log.logInfo("REJECTED WRONG MIME/EXT TYPE " + res.responseHeader.mime() + " for URL " + this.url.toString());
                        addURLtoErrorDB(plasmaCrawlEURL.DENIED_WRONG_MIMETYPE_OR_EXT);
                        htCache = null;
                    }
                } catch (SocketException e) {
                    // this may happen if the client suddenly closes its connection
                    // maybe the user has stopped loading
                    // in that case, we are not responsible and just forget it
                    // but we clean the cache also, since it may be only partial
                    // and most possible corrupted
                    if (cacheFile.exists()) cacheFile.delete();
                    this.log.logSevere("CRAWLER LOADER ERROR1: with URL=" + this.url.toString() + ": " + e.toString());
                    addURLtoErrorDB(plasmaCrawlEURL.DENIED_CONNECTION_ERROR);
                    htCache = null;
                }
            } else if (res.status.startsWith("30")) {
                if (crawlingRetryCount > 0) {
                    if (res.responseHeader.containsKey(httpHeader.LOCATION)) {
                        // getting redirection URL
                        String redirectionUrlString = (String) res.responseHeader.get(httpHeader.LOCATION);
                        redirectionUrlString = redirectionUrlString.trim();

                        if (redirectionUrlString.length() == 0) {
                            this.log.logWarning("CRAWLER Redirection of URL=" + this.url.toString() + " aborted. Location header is empty.");
                            addURLtoErrorDB(plasmaCrawlEURL.DENIED_REDIRECTION_HEADER_EMPTY);
                            return null;
                        }
                        
                        // normalizing URL
                        redirectionUrlString = new URL(this.url, redirectionUrlString).toNormalform();

                        // generating the new URL object
                        URL redirectionUrl = new URL(redirectionUrlString);

                        // returning the used httpc
                        httpc.returnInstance(remote);
                        remote = null;

                        // restart crawling with new url
                        this.log.logInfo("CRAWLER Redirection detected ('" + res.status + "') for URL " + this.url.toString());
                        this.log.logInfo("CRAWLER ..Redirecting request to: " + redirectionUrl);

                        // if we are already doing a shutdown we don't need to retry crawling
                        if (Thread.currentThread().isInterrupted()) {
                            this.log.logSevere("CRAWLER Retry of URL=" + this.url.toString() + " aborted because of server shutdown.");
                            addURLtoErrorDB(plasmaCrawlEURL.DENIED_SERVER_SHUTDOWN);
                            return null;
                        }

                        // generating url hash
                        String urlhash = indexURL.urlHash(redirectionUrl);
                        
                        // removing url from loader queue
                        plasmaCrawlLoader.switchboard.urlPool.noticeURL.remove(urlhash);

                        // retry crawling with new url
                        this.url = redirectionUrl;
                        plasmaHTCache.Entry redirectedEntry = load(crawlingRetryCount-1);
                        
                        if (redirectedEntry != null) {
//                            TODO: Here we can store the content of the redirection
//                            as content of the original URL if some criterias are met
//                            See: http://www.yacy-forum.de/viewtopic.php?t=1719                                                                                   
//                            
//                            plasmaHTCache.Entry newEntry = (plasmaHTCache.Entry) redirectedEntry.clone();
//                            newEntry.url = url;
//                            TODO: which http header should we store here?    
//                                                      
//                            // enQueue new entry with response header
//                            if (profile != null) {
//                                cacheManager.push(newEntry);                                
//                            }                            
//                            htCache = newEntry;
                        }
                    }
                } else {
                    this.log.logInfo("Redirection counter exceeded for URL " + this.url.toString() + ". Processing aborted.");
                    addURLtoErrorDB(plasmaCrawlEURL.DENIED_REDIRECTION_COUNTER_EXCEEDED);
                }
            }else {
                // if the response has not the right response type then reject file
                this.log.logInfo("REJECTED WRONG STATUS TYPE '" + res.status + "' for URL " + this.url.toString());
                
                // not processed any further
                addURLtoErrorDB(plasmaCrawlEURL.DENIED_WRONG_HTTP_STATUSCODE + res.statusCode +  ")");
            }
            
            if (remote != null) remote.close();
            return htCache;
        } catch (Exception e) {
            boolean retryCrawling = false;
            String errorMsg = e.getMessage();
            String failreason = null;

            if ((e instanceof IOException) && 
                (errorMsg != null) && 
                (errorMsg.indexOf("socket closed") >= 0) &&
                (Thread.currentThread().isInterrupted())
            ) {
                this.log.logInfo("CRAWLER Interruption detected because of server shutdown.");
                failreason = plasmaCrawlEURL.DENIED_SERVER_SHUTDOWN;
            } else if (e instanceof httpdLimitExceededException) {
                this.log.logWarning("CRAWLER Max file size limit '" + this.maxFileSize + "' exceeded while downloading URL " + this.url);
                failreason = plasmaCrawlEURL.DENIED_FILESIZE_LIMIT_EXCEEDED;                    
            } else if (e instanceof MalformedURLException) {
                this.log.logWarning("CRAWLER Malformed URL '" + this.url.toString() + "' detected. ");
                failreason = plasmaCrawlEURL.DENIED_MALFORMED_URL;
            } else if (e instanceof NoRouteToHostException) {
                this.log.logWarning("CRAWLER No route to host found while trying to crawl URL  '" + this.url.toString() + "'.");
                failreason = plasmaCrawlEURL.DENIED_NO_ROUTE_TO_HOST;
            } else if ((e instanceof UnknownHostException) ||
                       ((errorMsg != null) && (errorMsg.indexOf("unknown host") >= 0))) {
                this.log.logWarning("CRAWLER Unknown host in URL '" + this.url.toString() + "'. " +
                        "Referer URL: " + ((this.refererURLString == null) ?"Unknown":this.refererURLString));
                failreason = plasmaCrawlEURL.DENIED_UNKNOWN_HOST;
            } else if (e instanceof java.net.BindException) {
                this.log.logWarning("CRAWLER BindException detected while trying to download content from '" + this.url.toString() +
                "'. Retrying request.");
                failreason = plasmaCrawlEURL.DENIED_CONNECTION_BIND_EXCEPTION;                
                retryCrawling = true;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Corrupt GZIP trailer") >= 0)) {
                this.log.logWarning("CRAWLER Problems detected while receiving gzip encoded content from '" + this.url.toString() +
                "'. Retrying request without using gzip content encoding.");
                failreason = plasmaCrawlEURL.DENIED_CONTENT_DECODING_ERROR;
                retryCrawling = true;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Read timed out") >= 0)) {
                this.log.logWarning("CRAWLER Read timeout while receiving content from '" + this.url.toString() +
                "'. Retrying request.");
                failreason = plasmaCrawlEURL.DENIED_CONNECTION_TIMEOUT;
                retryCrawling = true;
            } else if ((errorMsg != null) && (errorMsg.indexOf("connect timed out") >= 0)) {
                this.log.logWarning("CRAWLER Timeout while trying to connect to '" + this.url.toString() +
                "'. Retrying request.");
                failreason = plasmaCrawlEURL.DENIED_CONNECTION_TIMEOUT;
                retryCrawling = true;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Connection timed out") >= 0)) {
                this.log.logWarning("CRAWLER Connection timeout while receiving content from '" + this.url.toString() +
                "'. Retrying request.");
                failreason = plasmaCrawlEURL.DENIED_CONNECTION_TIMEOUT;
                retryCrawling = true;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Connection refused") >= 0)) {
                this.log.logWarning("CRAWLER Connection refused while trying to connect to '" + this.url.toString() + "'.");
                failreason = plasmaCrawlEURL.DENIED_CONNECTION_REFUSED;
            } else if ((errorMsg != null) && (errorMsg.indexOf("There is not enough space on the disk") >= 0)) {
                this.log.logSevere("CRAWLER Not enough space on the disk detected while crawling '" + this.url.toString() + "'. " +
                "Pausing crawlers. ");
                plasmaCrawlLoader.switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                plasmaCrawlLoader.switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                failreason = plasmaCrawlEURL.DENIED_OUT_OF_DISK_SPACE;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Network is unreachable") >=0)) {
                this.log.logSevere("CRAWLER Network is unreachable while trying to crawl URL '" + this.url.toString() + "'. ");
                failreason = plasmaCrawlEURL.DENIED_NETWORK_IS_UNREACHABLE;
            } else if ((errorMsg != null) && (errorMsg.indexOf("No trusted certificate found")>= 0)) {
                this.log.logSevere("CRAWLER No trusted certificate found for URL '" + this.url.toString() + "'. ");  
                failreason = plasmaCrawlEURL.DENIED_SSL_UNTRUSTED_CERT;
            } else {
                this.log.logSevere("CRAWLER Unexpected Error with URL '" + this.url.toString() + "': " + e.toString(),e);
                failreason = plasmaCrawlEURL.DENIED_CONNECTION_ERROR;
            }

            if (retryCrawling) {
                // if we are already doing a shutdown we don't need to retry crawling
                if (Thread.currentThread().isInterrupted()) {
                    this.log.logSevere("CRAWLER Retry of URL=" + this.url.toString() + " aborted because of server shutdown.");
                    return null;
                }

                // returning the used httpc
                if (remote != null) httpc.returnInstance(remote);
                remote = null;

                // setting the retry counter to 1
                if (crawlingRetryCount > 2) crawlingRetryCount = 2;

                // retry crawling
                return load(crawlingRetryCount - 1);
            }
            if (failreason != null) {
                addURLtoErrorDB(failreason);
            }
            return null;
        } finally {
            if (remote != null) httpc.returnInstance(remote);
        }
    }
    
    public void close() {
        if (this.isAlive()) {
            try {
                // trying to close all still open httpc-Sockets first                    
                int closedSockets = httpc.closeOpenSockets(this);
                if (closedSockets > 0) {
                    this.log.logInfo(closedSockets + " HTTP-client sockets of thread '" + this.getName() + "' closed.");
                }
            } catch (Exception e) {/* ignore this. shutdown in progress */}
        }
    }    
}