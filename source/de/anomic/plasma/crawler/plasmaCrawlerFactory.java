package de.anomic.plasma.crawler;

import java.lang.reflect.Constructor;

import org.apache.commons.pool.KeyedPoolableObjectFactory;

import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.crawler.http.CrawlWorker;
import de.anomic.server.logging.serverLog;

public final class plasmaCrawlerFactory implements KeyedPoolableObjectFactory {

    private plasmaCrawlerPool thePool;
    private final ThreadGroup theThreadGroup;
    private final plasmaHTCache   cacheManager;
    private final serverLog       theLog;
    private final plasmaSwitchboard sb;

    public plasmaCrawlerFactory(           
            ThreadGroup threadGroup,
            plasmaSwitchboard theSb,
            plasmaHTCache theCacheManager,
            serverLog log
    ) {

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
    public Object makeObject(Object key) throws Exception {        
        if (!(key instanceof String))
            throw new IllegalArgumentException("The object key must be of type string.");        
        
        // getting the class name
        String className = this.getClass().getPackage().getName() + "." + key + ".CrawlWorker";
        
        // loading class by name
        Class moduleClass = Class.forName(className);

        // getting the constructor
        Constructor classConstructor = moduleClass.getConstructor( new Class[] { 
                ThreadGroup.class,
                plasmaCrawlerPool.class,
                plasmaSwitchboard.class,
                plasmaHTCache.class,
                serverLog.class
        } );

        // instantiating class
        CrawlWorker theCrawlWorker = (CrawlWorker) classConstructor.newInstance(new Object[] {
              this.theThreadGroup,
              this.thePool,
              this.sb,
              this.cacheManager,
              this.theLog
        });        
        
        // return the newly created object
        return theCrawlWorker;
        
//        return new plasmaCrawlWorker(
//                this.theThreadGroup,
//                this.thePool,
//                this.sb,
//                this.cacheManager,
//                this.theLog);
    }

     /**
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object key, Object obj) {
        if (obj == null) return;
        if (obj instanceof CrawlWorker) {
            CrawlWorker theWorker = (CrawlWorker) obj;
            synchronized(theWorker) {
                theWorker.destroyed = true;
                theWorker.setName(CrawlWorker.threadBaseName + "_destroyed");
                theWorker.setStopped(true);
                theWorker.interrupt();
            }
        }
    }

    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object key, Object obj) {
        return true;
    }

    /**
     * @param obj 
     * 
     */
    public void activateObject(Object key, Object obj)  {
        //log.debug(" activateObject...");
    }

    /**
     * @param obj 
     * 
     */
    
    public void passivateObject(Object key, Object obj) { 
        //log.debug(" passivateObject..." + obj);
        /*
        if (obj instanceof plasmaCrawlWorker)  {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;             
        }
     */
    }
    
}
