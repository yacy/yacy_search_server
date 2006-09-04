package de.anomic.plasma.crawler;

import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;

public final class plasmaCrawlerFactory implements org.apache.commons.pool.PoolableObjectFactory {

    private plasmaCrawlerPool thePool;
    private final ThreadGroup theThreadGroup;
    private final plasmaHTCache   cacheManager;
    private final serverLog       theLog;
    private final plasmaSwitchboard sb;

    public plasmaCrawlerFactory(           
            ThreadGroup threadGroup,
            plasmaSwitchboard theSb,
            plasmaHTCache theCacheManager,
            serverLog log) {

        super();  

        if (threadGroup == null)
            throw new IllegalArgumentException("The threadgroup object must not be null.");

        this.theThreadGroup = threadGroup;
        this.cacheManager = theCacheManager;
        this.sb = theSb;  
        this.theLog = log;
    }

    public void setPool(plasmaCrawlerPool pool) {
        this.thePool = pool;    
    }

    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
     */
    public Object makeObject() {
        return new plasmaCrawlWorker(
                this.theThreadGroup,
                this.thePool,
                this.sb,
                this.cacheManager,
                this.theLog);
    }

     /**
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object obj) {
        if (obj == null) return;
        if (obj instanceof plasmaCrawlWorker) {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;
            synchronized(theWorker) {
                theWorker.destroyed = true;
                theWorker.setName(plasmaCrawlWorker.threadBaseName + "_destroyed");
                theWorker.setStopped(true);
                theWorker.interrupt();
            }
        }
    }

    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object obj) {
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
        /*
        if (obj instanceof plasmaCrawlWorker)  {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;             
        }
     */
    }
    
}
