package de.anomic.crawler;

import java.util.Vector;

import net.yacy.kelondro.logging.Log;


public class ImporterManager {

    public final Vector<Importer> finishedJobs;
    public final ThreadGroup runningJobs;
    public  int currMaxJobNr;
    
    public ImporterManager() {
        this.finishedJobs = new Vector<Importer>();
        this.runningJobs = new ThreadGroup("ImporterThreads");
        this.currMaxJobNr = 0;
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
        final Thread[] importThreads = new Thread[this.runningJobs.activeCount()*2];
        final int activeCount = this.runningJobs.enumerate(importThreads);
        final Importer[] importers = new Importer[activeCount];
        for (int i = 0; i < activeCount; i++) {
            importers[i] = (Importer) importThreads[i];
        }
        return importers;
    }
    
    public Importer[] getFinishedImporter() {
        return this.finishedJobs.toArray(new Importer[this.finishedJobs.size()]);
    }
    
    public Importer getImporterByID(final int jobID) {

        final Thread[] importThreads = new Thread[this.runningJobs.activeCount()*2];

        for(final Thread importThread : importThreads) {
            final Importer currThread = (Importer) importThread;
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
        final Thread[] threadList = new Thread[threadCount];     
        threadCount = this.runningJobs.enumerate(threadList);
        
        if (threadCount == 0) return;
        
        final Log log = new Log("DB-IMPORT");
        try {
            // trying to gracefull stop all still running sessions ...
            log.logInfo("Signaling shutdown to " + threadCount + " remaining dbImporter threads ...");
            for (final Thread currentThread : threadList)  {
                if (currentThread.isAlive()) {
                    ((Importer)currentThread).stopIt();
                }
            }      
            
            // waiting a few ms for the session objects to continue processing
            try { Thread.sleep(500); } catch (final InterruptedException ex) {}    
            
            // interrupting all still running or pooled threads ...
            log.logInfo("Sending interruption signal to " + runningJobs.activeCount() + " remaining dbImporter threads ...");
            runningJobs.interrupt();  
            
            // we need to use a timeout here because of missing interruptable session threads ...
            if (log.isFine()) log.logFine("Waiting for " + runningJobs.activeCount() + " remaining dbImporter threads to finish shutdown ...");
            int currentThreadIdx = 0;
            for (final Thread currentThread : threadList)  {
                if (currentThread.isAlive()) {
                    if (log.isFine()) log.logFine("Waiting for dbImporter thread '" + currentThread.getName() + "' [" + currentThreadIdx++ + "] to finish shutdown.");
                    try { currentThread.join(500); } catch (final InterruptedException ex) {}
                }
            }
            
            log.logInfo("Shutdown of remaining dbImporter threads finished.");
        } catch (final Exception e) {
            log.logSevere("Unexpected error while trying to shutdown all remaining dbImporter threads.",e);
        }
    }
    
}
