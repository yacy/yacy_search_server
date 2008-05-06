package de.anomic.crawler;

import java.util.Vector;

import de.anomic.server.logging.serverLog;

public class ImporterManager {

    public final Vector<Importer> finishedJobs = new Vector<Importer>();
    public final ThreadGroup runningJobs = new ThreadGroup("ImporterThreads");
    public  int currMaxJobNr = 0;
    
    public ImporterManager() {
    }
    
    public int generateUniqueJobID() {
        int jobID;
        synchronized(this.runningJobs) {
            jobID = this.currMaxJobNr;
            this.currMaxJobNr++;
        }
        return jobID;
    }
    
    public Importer[] getRunningImporter() {
        Thread[] importThreads = new Thread[this.runningJobs.activeCount()*2];
        int activeCount = this.runningJobs.enumerate(importThreads);
        Importer[] importers = new Importer[activeCount];
        for (int i=0; i<activeCount; i++) {
            importers[i] = (Importer) importThreads[i];
        }
        return importers;
    }
    
    public Importer[] getFinishedImporter() {
        return (Importer[]) this.finishedJobs.toArray(new Importer[this.finishedJobs.size()]);
    }
    
    public Importer getImporterByID(int jobID) {

        Thread[] importThreads = new Thread[this.runningJobs.activeCount()*2];
        int activeCount = this.runningJobs.enumerate(importThreads);
        
        for (int i=0; i < activeCount; i++) {
            Importer currThread = (Importer) importThreads[i];
            if (currThread.getJobID() == jobID) {
                return currThread;
            }                    
        }        
        return null;        
    }
    
    /**
     * Can be used to close all still running importer threads
     * e.g. on server shutdown
     */
    public void close() {
        /* clear the finished thread list */
        this.finishedJobs.clear();
        
        /* waiting for all threads to finish */
        int threadCount  = this.runningJobs.activeCount();    
        Thread[] threadList = new Thread[threadCount];     
        threadCount = this.runningJobs.enumerate(threadList);
        
        if (threadCount == 0) return;
        
        serverLog log = new serverLog("DB-IMPORT");
        try {
            // trying to gracefull stop all still running sessions ...
            log.logInfo("Signaling shutdown to " + threadCount + " remaining dbImporter threads ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    ((Importer)currentThread).stopIt();
                }
            }      
            
            // waiting a few ms for the session objects to continue processing
            try { Thread.sleep(500); } catch (InterruptedException ex) {}    
            
            // interrupting all still running or pooled threads ...
            log.logInfo("Sending interruption signal to " + runningJobs.activeCount() + " remaining dbImporter threads ...");
            runningJobs.interrupt();  
            
            // we need to use a timeout here because of missing interruptable session threads ...
            if (log.isFine()) log.logFine("Waiting for " + runningJobs.activeCount() + " remaining dbImporter threads to finish shutdown ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    if (log.isFine()) log.logFine("Waiting for dbImporter thread '" + currentThread.getName() + "' [" + currentThreadIdx + "] to finish shutdown.");
                    try { currentThread.join(500); } catch (InterruptedException ex) {}
                }
            }
            
            log.logInfo("Shutdown of remaining dbImporter threads finished.");
        } catch (Exception e) {
            log.logSevere("Unexpected error while trying to shutdown all remaining dbImporter threads.",e);
        }
    }
    
}
