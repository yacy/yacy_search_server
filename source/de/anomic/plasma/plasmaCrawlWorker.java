//plasmaCrawlWorker.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//last major change: 21.04.2005 by Martin Thelian
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
import java.net.SocketException;
import java.net.URL;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.http.httpd;
import de.anomic.http.httpdProxyHandler;
import de.anomic.server.logging.serverLog;
import de.anomic.server.logging.serverMiniLogFormatter;

public final class plasmaCrawlWorker extends Thread {

    private static final String threadBaseName = "CrawlerWorker";
    
    private final CrawlerPool     myPool;
    private final plasmaHTCache   cacheManager;
    private final int             socketTimeout;
    private final boolean         remoteProxyUse;
    private final String          remoteProxyHost;
    private final int             remoteProxyPort;
    private final serverLog       log;
    
    public plasmaCrawlLoaderMessage theMsg;
    private URL url;
    private String referer;
    private String initiator;
    private int depth;
    private long startdate;
    private plasmaCrawlProfile.entry profile;
    //private String error;
    
    private boolean running = false;
    private boolean stopped = false;
    private boolean done = false;   
    
    private static boolean doAccessLogging = false; 
    /**
     * Do logging configuration for special proxy access log file
     */
    static {
        try {
            Logger crawlerLogger = Logger.getLogger("CRAWLER.access");
            crawlerLogger.setUseParentHandlers(false);
            FileHandler txtLog = new FileHandler("log/crawlerAccess%u%g.log",1024*1024, 20, true);
            txtLog.setFormatter(new serverMiniLogFormatter());
            txtLog.setLevel(Level.FINEST);
            crawlerLogger.addHandler(txtLog);     
            
            doAccessLogging = true;
        } catch (Exception e) { 
            System.err.println("PROXY: Unable to configure proxy access logging.");        
        }
    }    
        
    public plasmaCrawlWorker(
            ThreadGroup theTG, 
            CrawlerPool thePool, 
            plasmaHTCache cacheManager,
            int socketTimeout,
            boolean remoteProxyUse,
            String remoteProxyHost,
            int remoteProxyPort,
            serverLog log) {
        super(theTG,threadBaseName + "_inPool");
                
        this.myPool = thePool;
        this.cacheManager = cacheManager;
        this.socketTimeout = socketTimeout;
        this.remoteProxyUse = remoteProxyUse;
        this.remoteProxyHost = remoteProxyHost;
        this.remoteProxyPort = remoteProxyPort;
        this.log = log;
    }

    public synchronized void execute(plasmaCrawlLoaderMessage theMsg) {
        this.theMsg = theMsg;
        
        this.url = theMsg.url;
        this.referer = theMsg.referer;
        this.initiator = theMsg.initiator;
        this.depth = theMsg.depth;
        this.profile = theMsg.profile;
        
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
                            this.setName(this.threadBaseName + "_inPool");
                        }
                        catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                }
             }
          }
    }
    
    public void execute() throws IOException {
        try {
            this.setName(this.threadBaseName + "_" + this.url);
            load(this.url, this.referer, this.initiator, this.depth, this.profile,
                 this.socketTimeout, this.remoteProxyHost, this.remoteProxyPort, this.remoteProxyUse,
                 this.cacheManager, this.log);
            
        } catch (IOException e) {
            //throw e;
        }
        finally {
            this.done = true;
        }
    }

    /*
    private httpc newhttpc(String server, int port, boolean ssl) throws IOException {
        // a new httpc connection, combined with possible remote proxy
        if (remoteProxyUse)
             return httpc.getInstance(server, port, socketTimeout, ssl, remoteProxyHost, remoteProxyPort);
        else return httpc.getInstance(server, port, socketTimeout, ssl);
    }

    private void load(
            URL url, 
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile
        ) throws IOException {
        if (url == null) return;
        Date requestDate = new Date(); // remember the time...
        String host = url.getHost();
        String path = url.getPath();
        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
    
        // set referrer; in some case advertise a little bit:
        referer = referer.trim();
        if (referer.length() == 0) referer = "http://www.yacy.net/yacy/";
        
        // take a file from the net
        httpc remote = null;
        try {
            // create a request header
            httpHeader requestHeader = new httpHeader();
            requestHeader.put("User-Agent", httpdProxyHandler.userAgent);
            requestHeader.put("Referer", referer);
            requestHeader.put("Accept-Encoding", "gzip,deflate");
    
            //System.out.println("CRAWLER_REQUEST_HEADER=" + requestHeader.toString()); // DEBUG
                    
            // open the connection
            remote = newhttpc(host, port, ssl); 
            
            // send request
            httpc.response res = remote.GET(path, requestHeader);
                
            if (res.status.startsWith("200")) {
                // the transfer is ok
                long contentLength = res.responseHeader.contentLength();
                
                // reserve cache entry
                plasmaHTCache.Entry htCache = cacheManager.newEntry(requestDate, depth, url, requestHeader, res.status, res.responseHeader, initiator, profile);
                
                // request has been placed and result has been returned. work off response
                File cacheFile = cacheManager.getCachePath(url);
                try {
                    if (!(plasmaParser.supportedMimeTypesContains(res.responseHeader.mime()))) {
                        // if the response has not the right file type then reject file
                        remote.close();
                        log.logInfo("REJECTED WRONG MIME TYPE " + res.responseHeader.mime() + " for url " + url.toString());
                        htCache.status = plasmaHTCache.CACHE_UNFILLED;
                    } else if ((profile.storeHTCache()) && ((error = htCache.shallStoreCache()) == null)) {
                        // we write the new cache entry to file system directly
                        cacheFile.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(cacheFile);
                        htCache.cacheArray = res.writeContent(fos); // writes in cacheArray and cache file
                        fos.close();
                        htCache.status = plasmaHTCache.CACHE_FILL;
                    } else {
                        if (error != null) log.logDebug("CRAWLER NOT STORED RESOURCE " + url.toString() + ": " + error);
                        // anyway, the content still lives in the content scraper
                        htCache.cacheArray = res.writeContent(null); // writes only into cacheArray
                        htCache.status = plasmaHTCache.CACHE_PASSING;
                    }
                    // enQueue new entry with response header
                    if ((initiator == null) || (initiator.length() == 0)) {
                        // enqueued for proxy writings
                        cacheManager.stackProcess(htCache);
                    } else {
                        // direct processing for crawling
                        cacheManager.process(htCache);
                    }
                } catch (SocketException e) {
                    // this may happen if the client suddenly closes its connection
                    // maybe the user has stopped loading
                    // in that case, we are not responsible and just forget it
                    // but we clean the cache also, since it may be only partial
                    // and most possible corrupted
                    if (cacheFile.exists()) cacheFile.delete();
                    log.logError("CRAWLER LOADER ERROR1: with url=" + url.toString() + ": " + e.toString());
                }
            } else {
                // if the response has not the right response type then reject file
                log.logInfo("REJECTED WRONG STATUS TYPE '" + res.status + "' for url " + url.toString());
                // not processed any further
            }
            remote.close();
        } catch (Exception e) {
            // this may happen if the targeted host does not exist or anything with the
            // remote server was wrong.
            log.logError("CRAWLER LOADER ERROR2 with url=" + url.toString() + ": " + e.toString());
            e.printStackTrace();
        } finally {
            if (remote != null) httpc.returnInstance(remote);
        }
    }
    */
    
    public void setStopped(boolean stopped) {
        this.stopped = stopped;           
    }

    public boolean isRunning() {
        return this.running;
    }

    public static void load(
            URL url, 
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile,
            int socketTimeout,
            String remoteProxyHost,
            int remoteProxyPort,
            boolean remoteProxyUse,
            plasmaHTCache cacheManager,
            serverLog log
        ) throws IOException {
        if (url == null) return;
        Date requestDate = new Date(); // remember the time...
        String host = url.getHost();
        String path = url.getPath();
        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
    
        // set referrer; in some case advertise a little bit:
        referer = (referer == null) ? "" : referer.trim();
        if (referer.length() == 0) referer = "http://www.yacy.net/yacy/";
        
        // take a file from the net
        httpc remote = null;
        try {
            // create a request header
            httpHeader requestHeader = new httpHeader();
            requestHeader.put("User-Agent", httpdProxyHandler.userAgent);
            requestHeader.put("Referer", referer);
            requestHeader.put("Accept-Encoding", "gzip,deflate");
    
            //System.out.println("CRAWLER_REQUEST_HEADER=" + requestHeader.toString()); // DEBUG
                    
            // open the connection
            if (remoteProxyUse)
                remote = httpc.getInstance(host, port, socketTimeout, ssl, remoteProxyHost, remoteProxyPort);
            else
                remote = httpc.getInstance(host, port, socketTimeout, ssl);
            
            // send request
            httpc.response res = remote.GET(path, requestHeader);
                
            if (res.status.startsWith("200")) {
                // the transfer is ok
                long contentLength = res.responseHeader.contentLength();
                
                // reserve cache entry
                plasmaHTCache.Entry htCache = cacheManager.newEntry(requestDate, depth, url, requestHeader, res.status, res.responseHeader, initiator, profile);
                
                // request has been placed and result has been returned. work off response
                File cacheFile = cacheManager.getCachePath(url);
                try {
                    String error = null;
                    if (!(plasmaParser.supportedMimeTypesContains(res.responseHeader.mime()))) {
                        // if the response has not the right file type then reject file
                        remote.close();
                        log.logInfo("REJECTED WRONG MIME TYPE " + res.responseHeader.mime() + " for url " + url.toString());
                        htCache.status = plasmaHTCache.CACHE_UNFILLED;
                    } else if ((profile == null) || ((profile.storeHTCache()) && ((error = htCache.shallStoreCache()) == null))) {
                        // we write the new cache entry to file system directly
                        cacheFile.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(cacheFile);
                        htCache.cacheArray = res.writeContent(fos); // writes in cacheArray and cache file
                        fos.close();
                        htCache.status = plasmaHTCache.CACHE_FILL;
                    } else {
                        if (error != null) log.logDebug("CRAWLER NOT STORED RESOURCE " + url.toString() + ": " + error);
                        // anyway, the content still lives in the content scraper
                        htCache.cacheArray = res.writeContent(null); // writes only into cacheArray
                        htCache.status = plasmaHTCache.CACHE_PASSING;
                    }
                    // enQueue new entry with response header
                    if ((initiator == null) || (initiator.length() == 0)) {
                        // enqueued for proxy writings
                        cacheManager.stackProcess(htCache);
                    } else {
                        // direct processing for crawling
                        cacheManager.process(htCache);
                    }
                } catch (SocketException e) {
                    // this may happen if the client suddenly closes its connection
                    // maybe the user has stopped loading
                    // in that case, we are not responsible and just forget it
                    // but we clean the cache also, since it may be only partial
                    // and most possible corrupted
                    if (cacheFile.exists()) cacheFile.delete();
                    log.logError("CRAWLER LOADER ERROR1: with url=" + url.toString() + ": " + e.toString());
                }
            } else {
                // if the response has not the right response type then reject file
                log.logInfo("REJECTED WRONG STATUS TYPE '" + res.status + "' for url " + url.toString());
                // not processed any further
            }
            remote.close();
        } catch (Exception e) {
            // this may happen if the targeted host does not exist or anything with the
            // remote server was wrong.
            log.logError("CRAWLER LOADER ERROR2 with url=" + url.toString() + ": " + e.toString(),e);
        } finally {
            if (remote != null) httpc.returnInstance(remote);
        }
    }
    
}

