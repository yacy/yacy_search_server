package de.anomic.plasma.dbImport;

import java.util.Vector;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;

public class dbImportManager {

    public final Vector finishedJobs = new Vector();
    public final ThreadGroup runningJobs = new ThreadGroup("ImporterThreads");
    public  int currMaxJobNr = 0;
    private plasmaSwitchboard sb;
    
    public dbImportManager(plasmaSwitchboard theSb) {
        this.sb = theSb;
    }
    
    public int getJobID() {
        int jobID;
        synchronized(runningJobs) {
            jobID = currMaxJobNr;
            currMaxJobNr++;
        }
        return jobID;
    }
    
    public dbImporter[] getRunningImporter() {
        Thread[] importThreads = new Thread[runningJobs.activeCount()*2];
        int activeCount = runningJobs.enumerate(importThreads);
        dbImporter[] importers = new dbImporter[activeCount];
        for (int i=0; i<activeCount; i++) {
            importers[i] = (dbImporter) importThreads[i];
        }
        return importers;
    }
    
    public dbImporter[] getFinishedImporter() {
        return (dbImporter[]) finishedJobs.toArray(new dbImporter[finishedJobs.size()]);
    }
    
    public dbImporter getImporterByID(int jobID) {

        Thread[] importThreads = new Thread[this.runningJobs.activeCount()*2];
        int activeCount = this.runningJobs.enumerate(importThreads);
        
        for (int i=0; i < activeCount; i++) {
            dbImporter currThread = (dbImporter) importThreads[i];
            if (currThread.getJobID() == Integer.valueOf(jobID).intValue()) {
                return currThread;
            }                    
        }        
        return null;        
    }
    
    public dbImporter getNewImporter(String type) {
        if (type == null) return null;
        if (type.length() == 0) return null;
        
        dbImporter newImporter = null;
        if (type.equals("plasmaDB")) {
            newImporter = new plasmaDbImporter(this.sb);
        } else if (type.equalsIgnoreCase("ASSORTMENT")) {
            newImporter = new plasmaWordIndexAssortmentImporter(this.sb);
        }
        return newImporter;
    }
    
    /**
     * Can be used to close all still running importer threads
     * e.g. on server shutdown
     */
    public void close() {
        /* waiting for all threads to finish */
        int threadCount  = runningJobs.activeCount();    
        Thread[] threadList = new Thread[threadCount];     
        threadCount = runningJobs.enumerate(threadList);
        
        if (threadCount == 0) return;
        
        serverLog log = new serverLog("DB-IMPORT");
        try {
            // trying to gracefull stop all still running sessions ...
            log.logInfo("Signaling shutdown to " + threadCount + " remaining dbImporter threads ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    ((plasmaDbImporter)currentThread).stopIt();
                }
            }      
            
            // waiting a few ms for the session objects to continue processing
            try { Thread.sleep(500); } catch (InterruptedException ex) {}    
            
            // interrupting all still running or pooled threads ...
            log.logInfo("Sending interruption signal to " + runningJobs.activeCount() + " remaining dbImporter threads ...");
            runningJobs.interrupt();  
            
            // we need to use a timeout here because of missing interruptable session threads ...
            log.logFine("Waiting for " + runningJobs.activeCount() + " remaining dbImporter threads to finish shutdown ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    log.logFine("Waiting for dbImporter thread '" + currentThread.getName() + "' [" + currentThreadIdx + "] to finish shutdown.");
                    try { currentThread.join(500); } catch (InterruptedException ex) {}
                }
            }
            
            log.logInfo("Shutdown of remaining dbImporter threads finished.");
        } catch (Exception e) {
            log.logSevere("Unexpected error while trying to shutdown all remaining dbImporter threads.",e);
        }
    }
    
}
