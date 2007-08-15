// plasmaCrawlerLoader.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.net.URL;
import de.anomic.plasma.crawler.plasmaCrawlWorker;
import de.anomic.plasma.crawler.plasmaCrawlerException;
import de.anomic.plasma.crawler.plasmaCrawlerFactory;
import de.anomic.plasma.crawler.plasmaCrawlerMsgQueue;
import de.anomic.plasma.crawler.plasmaCrawlerPool;
import de.anomic.server.logging.serverLog;

public final class plasmaCrawlLoader extends Thread {

    public static plasmaSwitchboard switchboard;
    
    private final serverLog       log;   

    private HashSet supportedProtocols;
    
    private final plasmaCrawlerMsgQueue theQueue;
    private final plasmaCrawlerPool crawlwerPool;
    private GenericKeyedObjectPool.Config crawlerPoolConfig = null; 
    private final ThreadGroup theThreadGroup = new ThreadGroup("CrawlerThreads");
    private boolean stopped = false;

    public plasmaCrawlLoader(serverLog theLog) {
        
        this.setName("plasmaCrawlLoader");

        this.log             = theLog;

        // supported protocols 
        // TODO: change this, e.g. by loading settings from file
        this.supportedProtocols = new HashSet(Arrays.asList(new String[]{"http","https"/* ,"ftp" */}));
        
        // configuring the crawler messagequeue
        this.theQueue = new plasmaCrawlerMsgQueue();

        // configuring the crawler thread pool
        // implementation of session thread pool
        this.crawlerPoolConfig = new GenericKeyedObjectPool.Config();

        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        this.crawlerPoolConfig.maxActive = Integer.parseInt(switchboard.getConfig("crawler.MaxActiveThreads","10"));

        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        this.crawlerPoolConfig.maxIdle = Integer.parseInt(switchboard.getConfig("crawler.MaxIdleThreads","7"));
        
        // minIdle configuration not possible for keyedObjectPools
        //this.crawlerPoolConfig.minIdle = Integer.parseInt(switchboard.getConfig("crawler.MinIdleThreads","5"));    

        // block undefinitely 
        this.crawlerPoolConfig.maxWait = -1; 

        // Action to take in case of an exhausted DBCP statement pool
        // 0 = fail, 1 = block, 2= grow        
        this.crawlerPoolConfig.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        this.crawlerPoolConfig.minEvictableIdleTimeMillis = 30000; 
        //this.crawlerPoolConfig.timeBetweenEvictionRunsMillis = 30000;
//        config.testOnReturn = true;

        plasmaCrawlerFactory theFactory = new plasmaCrawlerFactory(
                this.theThreadGroup,
                switchboard,
                this.log);

        this.crawlwerPool = new plasmaCrawlerPool(theFactory,this.crawlerPoolConfig,this.theThreadGroup);        
        
        // start the crawl loader
        this.start();
    }

    public GenericKeyedObjectPool.Config getPoolConfig() {
        return this.crawlerPoolConfig;
    }

    public void setPoolConfig(GenericKeyedObjectPool.Config newConfig) {
        this.crawlwerPool.setConfig(newConfig);
    }
    
    public boolean isSupportedProtocol(String protocol) {
        if ((protocol == null) || (protocol.length() == 0)) return false;
        return this.supportedProtocols.contains(protocol.trim().toLowerCase());
    }
    
    public HashSet getSupportedProtocols() {
        return (HashSet) this.supportedProtocols.clone();
    }

    public void close() {
        try {
            // setting the stop flag to true
            this.stopped = true;

            // interrupting the plasmaCrawlLoader
            this.interrupt();

            // waiting for the thread to finish...
            this.log.logInfo("Waiting for plasmaCrawlLoader shutdown ...");
            this.join(5000);
        } catch (Exception e) {
            // we where interrupted while waiting for the crawlLoader Thread to finish
        }
    }

    public ThreadGroup threadStatus() {
        return this.theThreadGroup;
    }
    
    private void execute(plasmaCrawlLoaderMessage theMsg, boolean useThreadPool) throws Exception {
        // getting the protocol of the next URL                
        String protocol = theMsg.url.getProtocol();
        
        // TODO: remove this
        if (protocol.equals("https")) protocol = "http";
        
        // get a new worker thread
        plasmaCrawlWorker theWorker = null;
        if (useThreadPool) {
            // getting a new crawler from the crawler pool
            theWorker = (plasmaCrawlWorker) this.crawlwerPool.borrowObject(protocol);
        } else {
            // create a new one
            theWorker = (plasmaCrawlWorker) this.crawlwerPool.getFactory().makeObject(protocol,false);
        }
        
        if (theWorker == null) {
            this.log.logWarning("Unsupported protocol '" + protocol + "' in url " + theMsg.url);
        } else {
            theWorker.execute(theMsg);            
        }
    }
    
    public void run() {

        while (!this.stopped && !Thread.interrupted()) {
            try {
                // getting a new message from the crawler queue
                plasmaCrawlLoaderMessage theMsg = this.theQueue.waitForMessage();

                // start new crawl job
                this.execute(theMsg, true);

            } catch (InterruptedException e) {
                Thread.interrupted();
                this.stopped = true;
            }
            catch (Exception e) {
                this.log.logSevere("plasmaCrawlLoader.run/loop", e);
            }
        }

        // consuming the "is interrupted"-flag
        this.isInterrupted();

        // closing the pool
        try {
            this.crawlwerPool.close();
        }
        catch (Exception e) {
            this.log.logSevere("plasmaCrawlLoader.run/close", e);
        }
        
    }
    
    public plasmaHTCache.Entry loadSync(
            URL url, 
            String urlName,
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile,
            int timeout,
            boolean keepInMemory
    ) throws plasmaCrawlerException {

        plasmaHTCache.Entry result = null;
        if (!this.crawlwerPool.isClosed) {            
            int crawlingPriority = 5;

            // creating a new crawler queue object
            plasmaCrawlLoaderMessage theMsg = new plasmaCrawlLoaderMessage(
                    url, 
                    urlName, 
                    referer, 
                    initiator, 
                    depth, 
                    profile, 
                    crawlingPriority,
                    true,
                    timeout,
                    keepInMemory
            );


            try {
                // start new crawl job
                this.execute(theMsg, false);

                // wait for the crawl job result
                result = theMsg.waitForResult();                
            } catch (Exception e) {
                this.log.logSevere("plasmaCrawlLoader.loadSync: Unexpected error", e);
                throw new plasmaCrawlerException("Unexpected error: " + e.getMessage());
            }
            
            // check if an error has occured
            if (result == null) {
                String errorMsg = theMsg.getError();
                throw new plasmaCrawlerException(errorMsg);
            }            
        }        

        // return the result
        return result;
    }

    public void loadAsync(
            URL url, 
            String urlName,
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile,
            int timeout,
            boolean keepInMemory
    ) {

        if (!this.crawlwerPool.isClosed) {            
            int crawlingPriority = 5;
            
            // creating a new crawler queue object
            plasmaCrawlLoaderMessage theMsg = new plasmaCrawlLoaderMessage(
                    url,                // url
                    urlName,            // url name
                    referer,            // referer URL
                    initiator,          // crawling initiator peer
                    depth,              // crawling depth
                    profile,            // crawling profile
                    crawlingPriority,   // crawling priority
                    false,              // only download documents whose mimetypes are enabled for the crawler
                    timeout,            // -1 = use default crawler timeout
                    keepInMemory        // kept in memory ?
            );
            
            // adding the message to the queue
            try  {
                this.theQueue.addMessage(theMsg);
            } catch (InterruptedException e) {
                this.log.logSevere("plasmaCrawlLoader.loadAsync", e);
            }
        }
    }

    public int getNumIdleWorker() {
        return this.crawlwerPool.getNumIdle();
    }
    
    public int getNumActiveWorker() {
        return size();
    }
    
    public int size() {
        return this.crawlwerPool.getNumActive();
    }
}





