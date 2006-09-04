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

import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.net.URL;
import de.anomic.plasma.crawler.plasmaCrawlWorker;
import de.anomic.plasma.crawler.plasmaCrawlerFactory;
import de.anomic.plasma.crawler.plasmaCrawlerMsgQueue;
import de.anomic.plasma.crawler.plasmaCrawlerPool;
import de.anomic.server.logging.serverLog;

public final class plasmaCrawlLoader extends Thread {

    public static plasmaSwitchboard switchboard;

    private final plasmaHTCache   cacheManager;
    private final serverLog       log;   

    private final plasmaCrawlerMsgQueue theQueue;
    private final plasmaCrawlerPool crawlwerPool;
    private GenericObjectPool.Config crawlerPoolConfig = null; 
    private final ThreadGroup theThreadGroup = new ThreadGroup("CrawlerThreads");
    private boolean stopped = false;

    public plasmaCrawlLoader(
            plasmaHTCache theCacheManager, 
            serverLog theLog) {
        
        this.setName("plasmaCrawlLoader");

        this.cacheManager    = theCacheManager;
        this.log             = theLog;

        // configuring the crawler messagequeue
        this.theQueue = new plasmaCrawlerMsgQueue();

        // configuring the crawler thread pool
        // implementation of session thread pool
        this.crawlerPoolConfig = new GenericObjectPool.Config();

        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        this.crawlerPoolConfig.maxActive = Integer.parseInt(switchboard.getConfig("crawler.MaxActiveThreads","10"));

        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        this.crawlerPoolConfig.maxIdle = Integer.parseInt(switchboard.getConfig("crawler.MaxIdleThreads","7"));
        this.crawlerPoolConfig.minIdle = Integer.parseInt(switchboard.getConfig("crawler.MinIdleThreads","5"));    

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
                this.cacheManager,
                this.log);

        this.crawlwerPool = new plasmaCrawlerPool(theFactory,this.crawlerPoolConfig,this.theThreadGroup);        
        
        // start the crawl loader
        this.start();
    }

    public GenericObjectPool.Config getPoolConfig() {
        return this.crawlerPoolConfig;
    }

    public void setPoolConfig(GenericObjectPool.Config newConfig) {
        this.crawlwerPool.setConfig(newConfig);
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
    
    public void run() {

        while (!this.stopped && !Thread.interrupted()) {
            try {
                // getting a new message from the crawler queue
                plasmaCrawlLoaderMessage theMsg = this.theQueue.waitForMessage();

                // getting a new crawler from the crawler pool
                plasmaCrawlWorker theWorker = (plasmaCrawlWorker) this.crawlwerPool.borrowObject();
                theWorker.execute(theMsg);

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

    public void loadParallel(
            URL url, 
            String name,
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile) {

        if (!this.crawlwerPool.isClosed) {            
            int crawlingPriority = 5;
            
            // creating a new crawler queue object
            plasmaCrawlLoaderMessage theMsg = new plasmaCrawlLoaderMessage(url, name, referer, initiator, depth, profile, crawlingPriority);
            
            // adding the message to the queue
            try  {
                this.theQueue.addMessage(theMsg);
            } catch (InterruptedException e) {
                this.log.logSevere("plasmaCrawlLoader.loadParallel", e);
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





