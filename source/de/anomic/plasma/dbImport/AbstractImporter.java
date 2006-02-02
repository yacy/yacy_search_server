package de.anomic.plasma.dbImport;

import java.io.File;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;

public abstract class AbstractImporter extends Thread implements dbImporter{

    protected int jobID;
    protected String jobType;
    protected serverLog log;
    protected boolean stopped = false;
    protected boolean paused = false;
    
    protected plasmaSwitchboard sb;
    protected File importPath;
    protected int cacheSize;
    
    protected long globalStart = System.currentTimeMillis();
    protected long globalEnd;
    protected String error;
    
    public AbstractImporter(plasmaSwitchboard theSb) {
        super(theSb.dbImportManager.runningJobs,"");
        this.sb = theSb;
    }
    
    public String getError() {
        return this.error;
    }    
    
    public void init(File theImportPath) {
        if (theImportPath == null) throw new NullPointerException("The Import path must not be null.");
        this.importPath = theImportPath;      
        
        // getting a job id from the import manager
        this.jobID = this.sb.dbImportManager.getJobID();
        
        // initializing the logger and setting a more verbose thread name
        this.log = new serverLog("IMPORT_" + this.jobType + "_" + this.jobID);
        this.setName("IMPORT_" + this.jobType + "_" + this.sb.dbImportManager.getJobID());
    }
    
    public void startIt() {
        this.start();
    }
    
    public void stopIt() throws InterruptedException {
        this.stopped = true;
        this.continueIt();
        this.join();
    }
    
    public void pauseIt() {
        synchronized(this) {
            this.paused = true;
        }
    }
    
    public void continueIt() {
        synchronized(this) {
            if (this.paused) {
                this.paused = false;
                this.notifyAll();
            }
        }
    }
    
    public boolean isPaused() {
        synchronized(this) {
            return this.paused;
        }
    }
    
    protected boolean isAborted() {
        synchronized(this) {
            if (this.paused) {
                try {
                    this.wait();
                }
                catch (InterruptedException e){}
            }
        }
        
        return (this.stopped) || Thread.currentThread().isInterrupted();
    }    
    
    public boolean isStopped() {
        return !this.isAlive();
    }
    
    public int getJobID() {
        return this.jobID;
    }
    
    public long getTotalRuntime() {
        return (this.globalEnd == 0)?System.currentTimeMillis()-this.globalStart:this.globalEnd-this.globalStart;
    }    
    
    public long getElapsedTime() {
        return isStopped()?this.globalEnd-this.globalStart:System.currentTimeMillis()-this.globalStart;
    }

    public String getJobType() {
        return this.jobType;
    }
    
    public File getImportPath() {
        return this.importPath;
    }
    
    public abstract long getEstimatedTime();
    public abstract String getJobName();
    public abstract int getProcessingStatusPercent();

}
