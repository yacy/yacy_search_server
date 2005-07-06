// plasmaCrawlerLoader.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 25.02.2004
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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import de.anomic.server.serverSemaphore;
import de.anomic.server.logging.serverLog;

import org.apache.commons.pool.impl.GenericObjectPool;

public final class plasmaCrawlLoader extends Thread {

    private final plasmaHTCache   cacheManager;
    private final int             socketTimeout;
    private final int             loadTimeout;
    private final int             maxSlots;
    private final serverLog       log;   
    
    private final CrawlerMessageQueue theQueue;
    private final CrawlerPool crawlwerPool;
    private GenericObjectPool.Config cralwerPoolConfig = null; 
    private final ThreadGroup theThreadGroup = new ThreadGroup("CrawlerThreads");
    private boolean stopped = false;
    
    public plasmaCrawlLoader(
            plasmaHTCache cacheManager, 
            serverLog log, 
            int socketTimeout, 
            int loadTimeout, 
            int mslots, 
            boolean proxyUse, 
            String proxyHost, 
            int proxyPort) {
        this.setName("plasmaCrawlLoader");
        
    	this.cacheManager    = cacheManager;
    	this.log             = log;
    	this.socketTimeout   = socketTimeout;
    	this.loadTimeout     = loadTimeout;
    	this.maxSlots        = mslots;
        
        // configuring the crawler messagequeue
        this.theQueue = new CrawlerMessageQueue();
        
        // configuring the crawler thread pool
        // implementation of session thread pool
        this.cralwerPoolConfig = new GenericObjectPool.Config();
        
        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        this.cralwerPoolConfig.maxActive = this.maxSlots;
        
        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        this.cralwerPoolConfig.maxIdle = this.maxSlots / 2;
        this.cralwerPoolConfig.minIdle = this.maxSlots / 4;    
        
        // block undefinitely 
        this.cralwerPoolConfig.maxWait = -1; 
        
        // Action to take in case of an exhausted DBCP statement pool
        // 0 = fail, 1 = block, 2= grow        
        this.cralwerPoolConfig.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        this.cralwerPoolConfig.minEvictableIdleTimeMillis = 30000; 
//        config.testOnReturn = true;
        
        CrawlerFactory theFactory = new CrawlerFactory(
                this.theThreadGroup,
                cacheManager,
                socketTimeout,
                proxyUse,
                proxyHost,
                proxyPort,
                log);
        
        this.crawlwerPool = new CrawlerPool(theFactory,this.cralwerPoolConfig,this.theThreadGroup);        
        
        // start the crawl loader
        this.start();
    }
    
    public GenericObjectPool.Config getPoolConfig() {
        return this.cralwerPoolConfig;
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
	        
	        // waiting for the thread to finish ...
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
                e.printStackTrace();
            }
        }
        
        // consuming the is interrupted flag
        this.isInterrupted();
        
        // closing the pool
        try {
            this.crawlwerPool.close();
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
       
    public void loadParallel(
            URL url, 
            String referer, 
            String initiator, 
            int depth, 
            plasmaCrawlProfile.entry profile) {

        if (!this.crawlwerPool.isClosed) {            
            int crawlingPriority = 5;
            
            // creating a new crawler queue object
            plasmaCrawlLoaderMessage theMsg = new plasmaCrawlLoaderMessage(url, referer,initiator,depth,profile, crawlingPriority);
            
            // adding the message to the queue
            try  {
                this.theQueue.addMessage(theMsg);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public int size() {
        return crawlwerPool.getNumActive();
    }
}


class CrawlerMessageQueue {
    private final serverSemaphore readSync;
    private final serverSemaphore writeSync;
    private final ArrayList messageList;
    
    public CrawlerMessageQueue()  {
        this.readSync  = new serverSemaphore (0);
        this.writeSync = new serverSemaphore (1);
        
        this.messageList = new ArrayList(10);        
    }
        
    /**
     * 
     * @param newMessage
     * @throws MessageQueueLockedException
     * @throws InterruptedException
     */
    public void addMessage(plasmaCrawlLoaderMessage newMessage) 
        throws InterruptedException, NullPointerException 
    {
        if (newMessage == null) throw new NullPointerException();
        
        this.writeSync.P();
        
            boolean insertionDoneSuccessfully = false;
            synchronized(this.messageList) {
                insertionDoneSuccessfully = this.messageList.add(newMessage);
            }
        
            if (insertionDoneSuccessfully)  {
                this.sortMessages();
                this.readSync.V();              
            }
        
        this.writeSync.V();
    }
    
    public plasmaCrawlLoaderMessage waitForMessage() throws InterruptedException {
        this.readSync.P();         
        this.writeSync.P();
        
        plasmaCrawlLoaderMessage newMessage = null;
        synchronized(this.messageList) {               
            newMessage = (plasmaCrawlLoaderMessage) this.messageList.remove(0);
        }

        this.writeSync.V();
        return newMessage;
    }
    
    protected void sortMessages() {
        Collections.sort(this.messageList, new Comparator()  { 
            public int compare(Object o1, Object o2)
            {
                plasmaCrawlLoaderMessage message1 = (plasmaCrawlLoaderMessage) o1; 
                plasmaCrawlLoaderMessage message2 = (plasmaCrawlLoaderMessage) o2; 
                
                int message1Priority = message1.crawlingPriority;
                int message2Priority = message2.crawlingPriority;
                
                if (message1Priority > message2Priority){ 
                    return -1; 
                } else if (message1Priority < message2Priority) { 
                    return 1; 
                }  else { 
                    return 0; 
                }            
            } 
        }); 
    }
}


final class CrawlerPool extends GenericObjectPool 
{
    private final ThreadGroup theThreadGroup;
    public boolean isClosed = false;
    
    
    public CrawlerPool(CrawlerFactory objFactory,
                       GenericObjectPool.Config config,
                       ThreadGroup threadGroup) {
        super(objFactory, config);
        this.theThreadGroup = threadGroup;
        objFactory.setPool(this);
    }

    public Object borrowObject() throws Exception  {
       return super.borrowObject();
    }

    public void returnObject(Object obj) throws Exception  {
        super.returnObject(obj);
    }        
    
    public synchronized void close() throws Exception {
        /*
         * shutdown all still running session threads ...
         */
        // interrupting all still running or pooled threads ...
        this.theThreadGroup.interrupt();
        
        /* waiting for all threads to finish */
        int threadCount  = this.theThreadGroup.activeCount();    
        Thread[] threadList = new Thread[threadCount];     
        threadCount = this.theThreadGroup.enumerate(threadList);
        
        try {
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                ((plasmaCrawlWorker)threadList[currentThreadIdx]).setStopped(true);
            }            
            
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                // we need to use a timeout here because of missing interruptable session threads ...
                if (threadList[currentThreadIdx].isAlive()) threadList[currentThreadIdx].join(500);
            }
        }
        catch (InterruptedException e) {
            System.err.println("Interruption while trying to shutdown all crawler threads.");  
        }        

        this.isClosed  = true;
        super.close();        
        
    }
    
}

final class CrawlerFactory implements org.apache.commons.pool.PoolableObjectFactory {

    private CrawlerPool thePool;
    private final ThreadGroup theThreadGroup;
    private final plasmaHTCache   cacheManager;
    private final int             socketTimeout;
    private final boolean         remoteProxyUse;
    private final String          remoteProxyHost;
    private final int             remoteProxyPort;   
    private final serverLog       theLog;
    
    public CrawlerFactory(           
            ThreadGroup theThreadGroup,
            plasmaHTCache cacheManager,
            int socketTimeout,
            boolean remoteProxyUse,
            String  remoteProxyHost,
            int remoteProxyPort,
            serverLog theLog) {
        
        super();  
        
        if (theThreadGroup == null)
            throw new IllegalArgumentException("The threadgroup object must not be null.");
        
        this.theThreadGroup = theThreadGroup;
        this.cacheManager = cacheManager;
        this.socketTimeout = socketTimeout;
        this.remoteProxyUse = remoteProxyUse;
        this.remoteProxyHost = remoteProxyHost;
        this.remoteProxyPort = remoteProxyPort;  
        this.theLog = theLog;
    }
    
    public void setPool(CrawlerPool thePool) {
        this.thePool = thePool;    
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
     */
    public Object makeObject() {
        return new plasmaCrawlWorker(
                this.theThreadGroup,
                this.thePool,
                this.cacheManager,
                this.socketTimeout,
                this.remoteProxyUse,
                this.remoteProxyHost,
                this.remoteProxyPort,
                this.theLog);
    }
    
     /**
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object obj) {
        if (obj instanceof plasmaCrawlWorker) {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;
            theWorker.setStopped(true);
        }
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object obj) {
        if (obj instanceof plasmaCrawlWorker) 
        {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;
            if (!theWorker.isAlive() || theWorker.isInterrupted()) return false;
            if (theWorker.isRunning()) return true;
            return false;
        }
        return true;
    }
    
    /**
     * @param obj 
     * 
     */
    public void activateObject(Object obj)  {
        //log.debug(" activateObject...");
    }

    /**
     * @param obj 
     * 
     */
    public void passivateObject(Object obj) { 
        //log.debug(" passivateObject..." + obj);
        if (obj instanceof plasmaCrawlWorker)  {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;             
        }
    }        
}




