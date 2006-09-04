package de.anomic.plasma.crawler;

import org.apache.commons.pool.impl.GenericKeyedObjectPool;

import de.anomic.plasma.crawler.http.CrawlWorker;
import de.anomic.server.logging.serverLog;

public final class plasmaCrawlerPool extends GenericKeyedObjectPool {
    private final ThreadGroup theThreadGroup;
    public boolean isClosed = false;

    public plasmaCrawlerPool(plasmaCrawlerFactory objFactory, GenericKeyedObjectPool.Config config, ThreadGroup threadGroup) {
        super(objFactory, config);
        this.theThreadGroup = threadGroup;
        objFactory.setPool(this);
    }

    public Object borrowObject(Object key) throws Exception  {
       return super.borrowObject(key);
    }

    public void returnObject(Object key,Object obj) {
        if (obj == null) return;
        if (obj instanceof CrawlWorker) {
            try {
                ((CrawlWorker)obj).setName(plasmaCrawlWorker.threadBaseName + "_inPool");
                super.returnObject(key,obj);
            } catch (Exception e) {
                ((CrawlWorker)obj).setStopped(true);
                serverLog.logSevere("CRAWLER-POOL","Unable to return crawler thread to pool.",e);                
            }
        } else {
            serverLog.logSevere("CRAWLER-POOL","Object of wrong type '" + obj.getClass().getName() +
            "' returned to pool.");            
        }        
    }        
    
    public void invalidateObject(Object key,Object obj) {
        if (obj == null) return;
        if (this.isClosed) return;
        if (obj instanceof CrawlWorker) {
            try {
                ((CrawlWorker)obj).setName(plasmaCrawlWorker.threadBaseName + "_invalidated");
                ((CrawlWorker)obj).setStopped(true);
                super.invalidateObject(key,obj);
            } catch (Exception e) {
                serverLog.logSevere("CRAWLER-POOL","Unable to invalidate crawling thread.",e); 
            }
        }
    }        
    
    public synchronized void close() throws Exception {
        try {
            /*
             * shutdown all still running session threads ...
             */
            this.isClosed  = true;

            /* waiting for all threads to finish */
            int threadCount  = this.theThreadGroup.activeCount();    
            Thread[] threadList = new Thread[threadCount];     
            threadCount = this.theThreadGroup.enumerate(threadList);

            // signaling shutdown to all still running or pooled threads ...
            serverLog.logInfo("CRAWLER","Signaling shutdown to " + threadCount + " remaining crawler threads ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                ((CrawlWorker)threadList[currentThreadIdx]).setStopped(true);
            }   

            // giving the crawlers some time to finish shutdown
            try { Thread.sleep(500); } catch(Exception e) {/* Ignore this. Shutdown in progress */}            

            // sending interrupted signal to all remaining threads
            serverLog.logInfo("CRAWLER","Sending interruption signal to " + this.theThreadGroup.activeCount() + " remaining crawler threads ...");
            this.theThreadGroup.interrupt();        

            // aborting all crawlers by closing all still open httpc sockets
            serverLog.logInfo("CRAWLER","Trying to abort  " + this.theThreadGroup.activeCount() + " remaining crawler threads ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    serverLog.logInfo("CRAWLER","Trying to shutdown crawler thread '" + currentThread.getName() + "' [" + currentThreadIdx + "].");
                    ((CrawlWorker)currentThread).close();
                }
            }            

            serverLog.logInfo("CRAWLER","Waiting for " + this.theThreadGroup.activeCount() + " remaining crawler threads to finish shutdown ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    serverLog.logInfo("CRAWLER","Waiting for crawler thread '" + currentThread.getName() + "' [" + currentThreadIdx + "] to finish shutdown.");
                    try { currentThread.join(500); } catch (InterruptedException ex) {/* Ignore this. Shutdown in progress */}
                }
            }
            serverLog.logWarning("CRAWLER","Shutdown of remaining crawler threads finish.");
        }
        catch (Exception e) {
            serverLog.logWarning("CRAWLER","Unexpected error while trying to shutdown all remaining crawler threads.",e);  
        }        

        super.close();        

    }

}
