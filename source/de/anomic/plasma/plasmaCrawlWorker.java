//plasmaCrawlWorker.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
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

package de.anomic.plasma;

import java.io.File; 
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpc;
import de.anomic.http.httpdProxyHandler;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.logging.serverLog;
import de.anomic.server.logging.serverMiniLogFormatter;
import de.anomic.tools.bitfield;
import de.anomic.yacy.yacyCore;

public final class plasmaCrawlWorker extends Thread {
    
    private static final int DEFAULT_CRAWLING_RETRY_COUNT = 5;   
    private static final String threadBaseName = "CrawlerWorker";
    
    private final CrawlerPool     myPool;
    private final plasmaSwitchboard sb;
    private final plasmaHTCache   cacheManager;
    private final serverLog       log;
    private int socketTimeout;
    
    public plasmaCrawlLoaderMessage theMsg;
    private URL url;
    private String name;
    private String referer;
    private String initiator;
    private int depth;
    private long startdate;
    private plasmaCrawlProfile.entry profile;
    //private String error;
    
    private boolean running = false;
    private boolean stopped = false;
    private boolean done = false;   
    
    private static boolean doCrawlerLogging = false; 
    /**
     * Do logging configuration for special proxy access log file
     */
//    static {
//        try {
//            Logger crawlerLogger = Logger.getLogger("CRAWLER.access");
//            crawlerLogger.setUseParentHandlers(false);
//            FileHandler txtLog = new FileHandler("log/crawlerAccess%u%g.log",1024*1024, 20, true);
//            txtLog.setFormatter(new serverMiniLogFormatter());
//            txtLog.setLevel(Level.FINEST);
//            crawlerLogger.addHandler(txtLog);     
//            
//            doAccessLogging = true;
//        } catch (Exception e) { 
//            System.err.println("PROXY: Unable to configure proxy access logging.");        
//        }
//    }    
        
    public plasmaCrawlWorker(
            ThreadGroup theTG, 
            CrawlerPool thePool,
            plasmaSwitchboard theSb,
            plasmaHTCache theCacheManager,
            serverLog theLog) {
        super(theTG,threadBaseName + "_inPool");
                
        this.myPool = thePool;
        this.sb = theSb;
        this.cacheManager = theCacheManager;
        this.log = theLog;
        
        // setting the crawler timeout properly
        this.socketTimeout = (int) this.sb.getConfigLong("crawler.clientTimeout", 10000);
    }
    
    public long getDuration() {
        long startDate = this.startdate;
        return (startDate != 0) ? System.currentTimeMillis() - startDate : 0;
    }

    public synchronized void execute(plasmaCrawlLoaderMessage theNewMsg) {
        this.theMsg = theNewMsg;
        
        this.url = theNewMsg.url;
        this.name = theNewMsg.name;
        this.referer = theNewMsg.referer;
        this.initiator = theNewMsg.initiator;
        this.depth = theNewMsg.depth;
        this.profile = theNewMsg.profile;
        
        this.startdate = System.currentTimeMillis();        
        //this.error = null;        
        

        this.done = false;        
        if (!this.running)  {
           // this.setDaemon(true);
           this.start();
        }  else { 
           this.notifyAll();
        }            
    }

    public void reset() {
        this.theMsg = null;
        this.url = null;
        this.referer = null;
        this.initiator = null;
        this.depth = 0;
        this.startdate = 0;
        this.profile = null;
        //this.error = null;   
    }
    
    public void run()  {
        this.running = true;
        
        // The thread keeps running.
        while (!this.stopped && !Thread.interrupted()) { 
             if (this.done)  { 
                 // We are waiting for a task now.
                synchronized (this)  {
                   try  {
                      this.wait(); //Wait until we get a request to process.
                   } 
                   catch (InterruptedException e) {
                       this.stopped = true;
                       // log.error("", e);
                   }
                }
             } 
             else 
             { 
                //There is a task....let us execute it.
                try  {
                   execute();
                }  catch (Exception e) {
                    // log.error("", e);
                } 
                finally  {
                    reset();
                    
                    if (!this.stopped && !this.isInterrupted()) {
                        try {
                            this.myPool.returnObject(this);
                            this.setName(plasmaCrawlWorker.threadBaseName + "_inPool");
                        }
                        catch (Exception e1) {
                            log.logSevere("pool error", e1);
                        }
                    }
                }
             }
          }
    }
    
    public void execute() throws IOException {
        try {
            // setting threadname
            this.setName(plasmaCrawlWorker.threadBaseName + "_" + this.url);
            
            // refreshing timeout value
            this.socketTimeout = (int) this.sb.getConfigLong("crawler.clientTimeout", 10000);
            
            // loading resource
            load(
                    this.url, 
                    this.name, 
                    this.referer, 
                    this.initiator, 
                    this.depth, 
                    this.profile,
                    this.socketTimeout, 
                    this.sb.remoteProxyConfig,
                    this.cacheManager, 
                    this.log
            );
            
        } catch (IOException e) {
            //throw e;
        }
        finally {
            this.done = true;
        }
    }
    
    public void setStopped(boolean isStopped) {
        this.stopped = isStopped;           
    }

    public boolean isRunning() {
        return this.running;
    }
    
    public void close() {
        if (this.isAlive()) {
            try {
                // trying to close all still open httpc-Sockets first                    
                int closedSockets = httpc.closeOpenSockets(this);
                if (closedSockets > 0) {
                    this.log.logInfo(closedSockets + " HTTP-client sockets of thread '" + this.getName() + "' closed.");
                }
            } catch (Exception e) {}
        }            
    }    

    public static void load(
            URL url, 
            String name,
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile,
            int socketTimeout,
            httpRemoteProxyConfig theRemoteProxyConfig,
            plasmaHTCache cacheManager,
            serverLog log
        ) throws IOException {
        load(url,
             name,
             referer,
             initiator,
             depth, 
             profile,
             socketTimeout, 
             theRemoteProxyConfig,
             cacheManager, 
             log, 
             DEFAULT_CRAWLING_RETRY_COUNT,
             true
        );
    }
    
    private static void load(
            URL url,
            String name,
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile,
            int socketTimeout,
            httpRemoteProxyConfig theRemoteProxyConfig,
            plasmaHTCache cacheManager,
            serverLog log,
            int crawlingRetryCount,
            boolean useContentEncodingGzip
        ) throws IOException {
        if (url == null) return;
        
        // if the recrawling limit was exceeded we stop crawling now
        if (crawlingRetryCount <= 0) return;
        
        // getting a reference to the plasmaSwitchboard
        plasmaSwitchboard sb = plasmaCrawlLoader.switchboard;           
        
        Date requestDate = new Date(); // remember the time...
        String host = url.getHost();
        String path = url.getPath();
        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
    
        // check if url is in blacklist
        String hostlow = host.toLowerCase();
        if (plasmaSwitchboard.urlBlacklist.isListed(hostlow, path)) {
            log.logInfo("CRAWLER Rejecting URL '" + url.toString() + "'. URL is in blacklist.");
            sb.urlPool.errorURL.newEntry(
                    url, 
                    referer,
                    initiator, 
                    yacyCore.seedDB.mySeed.hash,
                    name, 
                    "denied_(url_in_blacklist)", 
                    new bitfield(plasmaURL.urlFlagLength), 
                    true
            );
            return;
        }            
        
        // TODO: resolve yacy and yacyh domains
        String yAddress = yacyCore.seedDB.resolveYacyAddress(host);
        
        // set referrer; in some case advertise a little bit:
        referer = (referer == null) ? "" : referer.trim();
        if (referer.length() == 0) referer = "http://www.yacy.net/yacy/";
        
        // take a file from the net
        httpc remote = null;
        try {
            // create a request header
            httpHeader requestHeader = new httpHeader();
            requestHeader.put(httpHeader.USER_AGENT, httpdProxyHandler.userAgent);
            requestHeader.put(httpHeader.REFERER, referer);
            requestHeader.put(httpHeader.ACCEPT_LANGUAGE, sb.getConfig("crawler.acceptLanguage","en-us,en;q=0.5"));
            requestHeader.put(httpHeader.ACCEPT_CHARSET, sb.getConfig("crawler.acceptCharset","ISO-8859-1,utf-8;q=0.7,*;q=0.7"));
            if (useContentEncodingGzip) requestHeader.put(httpHeader.ACCEPT_ENCODING, "gzip,deflate");
    
            //System.out.println("CRAWLER_REQUEST_HEADER=" + requestHeader.toString()); // DEBUG
                    
            // open the connection
            remote = ((theRemoteProxyConfig != null) && (theRemoteProxyConfig.useProxy())) 
                   ? httpc.getInstance(host, port, socketTimeout, ssl, theRemoteProxyConfig)
                   : httpc.getInstance(host, port, socketTimeout, ssl);
            
            // specifying if content encoding is allowed
            remote.setAllowContentEncoding(useContentEncodingGzip);
            
            // send request
            httpc.response res = remote.GET(path, requestHeader);
                
            if (res.status.startsWith("200") || res.status.startsWith("203")) {
                // the transfer is ok
                
                // TODO: aborting download if content is to long ...
                long contentLength = res.responseHeader.contentLength();
                
                // reserve cache entry
                plasmaHTCache.Entry htCache = cacheManager.newEntry(requestDate, depth, url, name, requestHeader, res.status, res.responseHeader, initiator, profile);
                
                // request has been placed and result has been returned. work off response
                File cacheFile = cacheManager.getCachePath(url);
                try {
                    if (plasmaParser.supportedContent(url,res.responseHeader.mime())) {
                        if (cacheFile.isFile()) {
                            cacheManager.deleteFile(url);
                        }                        
                        // we write the new cache entry to file system directly
                        cacheFile.getParentFile().mkdirs();
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(cacheFile);
                            res.writeContent(fos); // superfluous write to array
                            htCache.cacheArray = null;
                            cacheManager.writeFileAnnouncement(cacheFile);
                            //htCache.cacheArray = res.writeContent(fos); // writes in cacheArray and cache file
                        } finally {
                            if (fos!=null)try{fos.close();}catch(Exception e){}
                        }
                    } else {
                        // if the response has not the right file type then reject file
                        remote.close();
                        log.logInfo("REJECTED WRONG MIME/EXT TYPE " + res.responseHeader.mime() + " for URL " + url.toString());                        
                    }
                    // enQueue new entry with response header
                    if (profile != null) {
                        cacheManager.push(htCache);
                    }
                } catch (SocketException e) {
                    // this may happen if the client suddenly closes its connection
                    // maybe the user has stopped loading
                    // in that case, we are not responsible and just forget it
                    // but we clean the cache also, since it may be only partial
                    // and most possible corrupted
                    if (cacheFile.exists()) cacheFile.delete();
                    log.logSevere("CRAWLER LOADER ERROR1: with URL=" + url.toString() + ": " + e.toString());
                }
            } else if (res.status.startsWith("30")) {
                if (crawlingRetryCount > 0) {                    
                    if (res.responseHeader.containsKey(httpHeader.LOCATION)) {
                        // getting redirection URL
                        String redirectionUrlString = (String) res.responseHeader.get(httpHeader.LOCATION);
                        redirectionUrlString = redirectionUrlString.trim();
                        
                        // normalizing URL
                        redirectionUrlString = plasmaParser.urlNormalform(redirectionUrlString);
                        
                        // generating the new URL object
                        URL redirectionUrl = new URL(url, redirectionUrlString);
                        
                        // returning the used httpc
                        httpc.returnInstance(remote); 
                        remote = null;
                        
                        // restart crawling with new url
                        log.logInfo("CRAWLER Redirection detected ('" + res.status + "') for URL " + url.toString());
                        log.logInfo("CRAWLER ..Redirecting request to: " + redirectionUrl);
                        
                        // if we are already doing a shutdown we don't need to retry crawling
                        if (Thread.currentThread().isInterrupted()) {
                            log.logSevere("CRAWLER Retry of URL=" + url.toString() + " aborted because of server shutdown.");
                            return;
                        }                        
                        
                        // generating url hash 
                        String urlhash = plasmaURL.urlHash(redirectionUrl);

                        // removing url from loader queue
                        plasmaCrawlLoader.switchboard.urlPool.noticeURL.remove(urlhash);                        
                        
                        // retry crawling with new url
                        load(redirectionUrl,
                             name,
                             referer,
                             initiator,
                             depth, 
                             profile,
                             socketTimeout, 
                             theRemoteProxyConfig,
                             cacheManager, 
                             log, 
                             --crawlingRetryCount,
                             useContentEncodingGzip
                        );
                    }
                } else {
                    log.logInfo("Redirection counter exceeded for URL " + url.toString() + ". Processing aborted.");
                }
            }else {
                // if the response has not the right response type then reject file
                log.logInfo("REJECTED WRONG STATUS TYPE '" + res.status + "' for URL " + url.toString());
                // not processed any further
            }
            if (remote != null) remote.close();
        } catch (Exception e) {
            boolean retryCrawling = false;
            String errorMsg = e.getMessage();
            
            if (e instanceof MalformedURLException) {
                log.logWarning("CRAWLER Malformed URL '" + url.toString() + "' detected. ");    
            } else if (e instanceof NoRouteToHostException) {
                log.logWarning("CRAWLER No route to host found while trying to crawl URL  '" + url.toString() + "'."); 
            } else if ((e instanceof UnknownHostException) || 
                       ((errorMsg != null) && (errorMsg.indexOf("unknown host") >= 0))) {                    
                log.logWarning("CRAWLER Unknown host in URL '" + url.toString() + "'. " +
                        "Referer URL: " + ((referer == null) ?"Unknown":referer));
            } else if (e instanceof java.net.BindException) {
                log.logWarning("CRAWLER BindException detected while trying to download content from '" + url.toString() + 
                "'. Retrying request.");
                retryCrawling = true;
            } else if ((errorMsg != null) && (errorMsg.indexOf("Corrupt GZIP trailer") >= 0)) {
                log.logWarning("CRAWLER Problems detected while receiving gzip encoded content from '" + url.toString() + 
                "'. Retrying request without using gzip content encoding.");
                retryCrawling = true;                  
            } else if ((errorMsg != null) && (errorMsg.indexOf("Read timed out") >= 0)) {
                log.logWarning("CRAWLER Read timeout while receiving content from '" + url.toString() + 
                "'. Retrying request.");
                retryCrawling = true;
            } else if ((errorMsg != null) && (errorMsg.indexOf("connect timed out") >= 0)) {
                log.logWarning("CRAWLER Timeout while trying to connect to '" + url.toString() + 
                "'. Retrying request."); 
                retryCrawling = true;                                                                           
            } else if ((errorMsg != null) && (errorMsg.indexOf("Connection timed out") >= 0)) {
                log.logWarning("CRAWLER Connection timeout while receiving content from '" + url.toString() + 
                "'. Retrying request."); 
                retryCrawling = true;                                                       
            } else if ((errorMsg != null) && (errorMsg.indexOf("Connection refused") >= 0)) {
                log.logWarning("CRAWLER Connection refused while trying to connect to '" + url.toString() + "'.");
            } else if ((errorMsg != null) && (errorMsg.indexOf("There is not enough space on the disk") >= 0)) {
                log.logSevere("CRAWLER Not enough space on the disk detected while crawling '" + url.toString() + "'. " + 
                "Pausing crawlers. ");
                plasmaCrawlLoader.switchboard.pauseCrawling();
            } else if ((errorMsg != null) && (errorMsg.indexOf("Network is unreachable") >=0)) {
                log.logSevere("CRAWLER Network is unreachable while trying to crawl URL '" + url.toString() + "'. ");
            } else if ((errorMsg != null) && (errorMsg.indexOf("No trusted certificate found")>= 0)) {
                log.logSevere("CRAWLER No trusted certificate found for URL '" + url.toString() + "'. ");
            } else {
                log.logSevere("CRAWLER Unexpected Error with URL '" + url.toString() + "': " + e.toString(),e);
            }
            
            
            if (retryCrawling) {    
                // if we are already doing a shutdown we don't need to retry crawling
                if (Thread.currentThread().isInterrupted()) {
                    log.logSevere("CRAWLER Retry of URL=" + url.toString() + " aborted because of server shutdown.");
                    return;
                }
                
                // returning the used httpc
                httpc.returnInstance(remote);
                remote = null;                    
                
                // setting the retry counter to 1 
                if (crawlingRetryCount > 2) crawlingRetryCount = 2;
                
                // retry crawling
                load(url,
                        name,
                        referer,
                        initiator,
                        depth,
                        profile,
                        socketTimeout,
                        theRemoteProxyConfig,
                        cacheManager,
                        log,
                        --crawlingRetryCount,
                        false
                );         
            }
        } finally {
            if (remote != null) httpc.returnInstance(remote);
        }
    }
    
}
