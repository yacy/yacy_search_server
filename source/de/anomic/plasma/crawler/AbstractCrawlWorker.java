package de.anomic.plasma.crawler;

import java.io.IOException;

import de.anomic.index.indexURL;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlLoaderMessage;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;
import de.anomic.yacy.yacyCore;

public abstract class AbstractCrawlWorker extends Thread implements plasmaCrawlWorker {
    
    /**
     * The protocol that is supported by this crawler
     * e.g. <code>http</code>, <code>ftp</code>, etc.
     */
    protected String protocol;
    
    /* ============================================================
     * Variables for thread pool management
     * ============================================================ */
    public boolean destroyed = false;
    protected boolean running = false;
    protected boolean stopped = false;
    /**
     * Specifies that the execution of the current crawl job has finished
     */
    protected boolean done = false;       
    
    /* ============================================================
     * Crawl job specific variables
     * ============================================================ */    
    public plasmaCrawlLoaderMessage theMsg;
    protected URL url;
    protected String name;
    protected String refererURLString;
    protected String initiator;
    protected int depth;
    protected long startdate;
    protected plasmaCrawlProfile.entry profile;  
    protected boolean acceptAllContent;
    
    /**
     * The crawler thread pool
     */
    protected final plasmaCrawlerPool myPool;
    
    /**
     * reference to the plasma switchboard
     */
    protected final plasmaSwitchboard sb;
    
    /**
     * reference to the cache manager
     */
    protected final plasmaHTCache   cacheManager;
    
    /**
     * Logging class
     */
    protected final serverLog       log;
    

    /**
     * Constructor of this class
     * @param theTG the crawl worker thread group
     * @param thePool the crawl worker thread pool
     * @param theSb plasma switchboard
     * @param theCacheManager cache manager
     * @param theLog server log
     */
    public AbstractCrawlWorker(
            ThreadGroup theTG,
            plasmaCrawlerPool thePool,
            plasmaSwitchboard theSb,
            plasmaHTCache theCacheManager,
            serverLog theLog
    ) {
        super(theTG,plasmaCrawlWorker.threadBaseName + "_created");

        this.myPool = thePool;
        this.sb = theSb;
        this.cacheManager = theCacheManager;
        this.log = theLog;
    }
        
    public void setNameTrailer(String trailer) {
        this.setName(plasmaCrawlWorker.threadBaseName + trailer);
    }
    
    public plasmaCrawlLoaderMessage getMessage() {
        return this.theMsg;
    }
    
    public abstract void close();
    
    public long getDuration() {
        final long startDate = this.startdate;
        return (startDate != 0) ? System.currentTimeMillis() - startDate : 0;
    }    
    
    public void run() {
        this.running = true;

        try {
            // The thread keeps running.
            while (!this.stopped && !this.isInterrupted() && !this.myPool.isClosed) {
                if (this.done) {       
                    synchronized (this) { 
                        // return thread back into pool
                        this.myPool.returnObject(this.protocol,this);
                        
                        // We are waiting for a new task now.
                        if (!this.stopped && !this.destroyed && !this.isInterrupted()) { 
                            this.wait(); 
                        }
                    }
                } else {
                    try {
                        // executing the new task
                        execute();
                    } finally {
                        reset();
                    }
                }
            }
        } catch (InterruptedException ex) {
            serverLog.logFiner("CRAWLER-POOL","Interruption of thread '" + this.getName() + "' detected."); 
        } finally {
            if (this.myPool != null && !this.destroyed) 
                this.myPool.invalidateObject(this.protocol,this);
        }
    }    
    
    public void execute() {
        try {
            // setting threadname
            this.setName(plasmaCrawlWorker.threadBaseName + "_" + this.url);

            // load some configuration variables
            init();

            // loading resource
            plasmaHTCache.Entry resource = load();
            
            // store a reference to the result in the message object
            // this is e.g. needed by the snippet fetcher
            this.theMsg.setResult(resource);

        } catch (IOException e) {
            //throw e;
        } finally {
            this.done = true;
        }
    }    
    
    public void execute(plasmaCrawlLoaderMessage theNewMsg) {
        synchronized (this) {

            this.theMsg = theNewMsg;

            this.url = theNewMsg.url;
            this.name = theNewMsg.name;
            this.refererURLString = theNewMsg.referer;
            this.initiator = theNewMsg.initiator;
            this.depth = theNewMsg.depth;
            this.profile = theNewMsg.profile;
            this.acceptAllContent = theNewMsg.acceptAllContent;

            this.startdate = System.currentTimeMillis();

            this.done = false;

            if (!this.running) {
                // if the thread is not running until yet, we need to start it now
                this.start();
            }  else {
                // inform the thread about the new crawl job
                this.notifyAll();
            }
        }
    }    
    
    public void setStopped(boolean isStopped) {
        this.stopped = isStopped;           
    }
    
    public void setDestroyed(boolean isDestroyed) {
        this.destroyed = isDestroyed;
    }

    public boolean isRunning() {
        return this.running;
    }
    
    public void reset() {
        this.theMsg = null;
        this.url = null;
        this.name = null;
        this.refererURLString = null;
        this.initiator = null;
        this.depth = 0;
        this.startdate = 0;
        this.profile = null;
        this.acceptAllContent = false;
    }    
    
    protected void addURLtoErrorDB(String failreason) {        
        // convert the referrer URL into a hash value
        String referrerHash = (this.refererURLString==null)?null:indexURL.urlHash(this.refererURLString);
        
        // create a new errorURL DB entry
        plasmaCrawlEURL.Entry ee = this.sb.urlPool.errorURL.newEntry(
                this.url,
                referrerHash,
                this.initiator,
                yacyCore.seedDB.mySeed.hash,
                this.name,
                (failreason==null)?"Unknown reason":failreason,
                new bitfield(indexURL.urlFlagLength)
        );
        
        // store the entry
        ee.store();
        
        // push it onto the stack
        this.sb.urlPool.errorURL.stackPushEntry(ee);
    }    
}
