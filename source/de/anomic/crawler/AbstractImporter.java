package de.anomic.crawler;

import de.anomic.server.logging.serverLog;

public abstract class AbstractImporter extends Thread implements Importer {

    protected int jobID = -1;
    protected String jobType;
    protected serverLog log;
    protected boolean stopped = false;
    protected boolean paused = false;
    protected long globalStart = System.currentTimeMillis();
    protected long globalEnd;
    protected long globalPauseLast;
    protected long globalPauseDuration;
    protected String error;

    public AbstractImporter(String theJobType) {
    	this.jobType = theJobType;

        // initializing the logger and setting a more verbose thread name
        this.log = new serverLog("IMPORT_" + this.jobType + "_" + this.jobID);
        this.setName("IMPORT_" + this.jobType + "_" + this.jobID);
    }
    
    public String getError() {
        return this.error;
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
        	this.globalPauseLast = System.currentTimeMillis();
            this.paused = true;
        }
    }
    
    public void continueIt() {
        synchronized(this) {
            if (this.paused) {
            	this.globalPauseDuration += System.currentTimeMillis()-this.globalPauseLast;
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
    
    public void setJobID(int id) {
    	if (this.jobID != -1) throw new IllegalStateException("job ID already assigned");
    	this.jobID = id;
    }
    
    public long getTotalRuntime() {
        return (this.globalEnd == 0)?System.currentTimeMillis()-(this.globalStart+this.globalPauseDuration):this.globalEnd-(this.globalStart+this.globalPauseDuration);
    }    
    
    public long getElapsedTime() {
    	if(this.paused) {
    		this.globalPauseDuration += System.currentTimeMillis()-this.globalPauseLast;
        	this.globalPauseLast = System.currentTimeMillis();
    	}
        return isStopped()?this.globalEnd-(this.globalStart+this.globalPauseDuration):System.currentTimeMillis()-(this.globalStart+this.globalPauseDuration);
    }

    public String getJobType() {
        return this.jobType;
    }
    
    public abstract long getEstimatedTime();
    public abstract String getJobName();
    public abstract int getProcessingStatusPercent();

}
