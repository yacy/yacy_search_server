package de.anomic.plasma.dbImport;

import java.io.File;

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
    public String getJobName();
    public String getJobType();
    public File getImportPath();
    public String getError();
    public String getStatus();
    
    public void init(File importPath, int cacheSize);
    public void startIt();    
}
