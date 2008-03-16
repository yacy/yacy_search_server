package de.anomic.plasma.dbImport;

import de.anomic.plasma.plasmaSwitchboard;

public interface dbImporter {

    // functions to pause and continue importing
    public boolean isPaused();
    public void pauseIt();
    public void continueIt();
    public void stopIt() throws InterruptedException;
    public boolean isStopped();
    
    // getting status information
    public long getTotalRuntime();
    public long getElapsedTime();
    public long getEstimatedTime();
    public int getProcessingStatusPercent();
    
    public int getJobID();
    public void setJobID(int id);
    public String getJobName();
    public String getJobType();
    public String getError();
    public String getStatus();
    public void init(plasmaSwitchboard switchboard, int cacheSize) throws ImporterException;
    public void startIt();    
}
