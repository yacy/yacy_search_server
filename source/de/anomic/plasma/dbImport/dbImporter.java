package de.anomic.plasma.dbImport;

import java.util.HashMap;


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
    //public void init(File plasmaPath, File indexPrimaryPath, File indexSecondaryPath, int cacheSize, long preloadTime);
    public void init(HashMap<String, String> initParams) throws ImporterException;
    public void startIt();    
}
