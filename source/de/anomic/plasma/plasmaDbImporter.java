package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import de.anomic.server.serverDate;
import de.anomic.server.logging.serverLog;

public class plasmaDbImporter extends Thread {

    public static final Vector finishedJobs = new Vector();
    public static final ThreadGroup runningJobs = new ThreadGroup("DbImport");
    public static int currMaxJobNr = 0;
    
    private final int jobNr;
    private final plasmaCrawlLURL homeUrlDB;
    private final plasmaWordIndex homeWordIndex;
    
    private final plasmaCrawlLURL importUrlDB;
    private final plasmaWordIndex importWordIndex;
    //private final String importPath;
    private final File importRoot;
    private final int importStartSize;
    
    private final serverLog log;
    private boolean stopped = false;
    private boolean paused = false;
    private String wordHash = "------------";
    
    long wordChunkStart = System.currentTimeMillis(), wordChunkEnd = this.wordChunkStart;
    String wordChunkStartHash = "------------", wordChunkEndHash;
    private long urlCounter = 0, wordCounter = 0, entryCounter = 0;
    
    private long globalStart = System.currentTimeMillis();
    private long globalEnd;
    
    private String error;
    
    public void stoppIt() {
        this.stopped = true;
        this.continueIt();
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
    
    /**
     * Can be used to close all still running importer threads
     * e.g. on server shutdown
     */
    public static void close() {
        /* waiting for all threads to finish */
        int threadCount  = runningJobs.activeCount();    
        Thread[] threadList = new Thread[threadCount];     
        threadCount = plasmaDbImporter.runningJobs.enumerate(threadList);
        
        if (threadCount == 0) return;
        
        serverLog log = new serverLog("DB-IMPORT");
        try {
            // trying to gracefull stop all still running sessions ...
            log.logInfo("Signaling shutdown to " + threadCount + " remaining dbImporter threads ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    ((plasmaDbImporter)currentThread).stoppIt();
                }
            }      
            
            // waiting a few ms for the session objects to continue processing
            try { Thread.sleep(500); } catch (InterruptedException ex) {}    
            
            // interrupting all still running or pooled threads ...
            log.logInfo("Sending interruption signal to " + runningJobs.activeCount() + " remaining dbImporter threads ...");
            plasmaDbImporter.runningJobs.interrupt();  
            
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
    
    public String getError() {
        return this.error;
    }
    
    public int getJobNr() {
        return this.jobNr;
    }
    
    public String getCurrentWordhash() {
        return this.wordHash;
    }
    
    public long getUrlCounter() {
        return this.urlCounter;
    }
    
    public long getWordEntityCounter() {
        return this.wordCounter;
    }
    
    public long getWordEntryCounter() {
        return this.entryCounter;
    }
    
    public File getImportRoot() {
        return this.importRoot;
    }
    
    public int getImportWordDbSize() {
        return this.importWordIndex.size();
    }
    
    public plasmaDbImporter(plasmaWordIndex theHomeIndexDB, plasmaCrawlLURL theHomeUrlDB, String theImportPath) {
        super(runningJobs,"DB-Import_" + theImportPath);
        
        this.log = new serverLog("DB-IMPORT");
        
        synchronized(runningJobs) {
            this.jobNr = currMaxJobNr;
            currMaxJobNr++;
        }
        
        if (theImportPath == null) throw new NullPointerException();
        //this.importPath = theImportPath;
        this.importRoot = new File(theImportPath);
        
        if (theHomeIndexDB == null) throw new NullPointerException();
        this.homeWordIndex = theHomeIndexDB;
        
        if (theHomeUrlDB == null) throw new NullPointerException();
        this.homeUrlDB = theHomeUrlDB;
        
        if (this.homeWordIndex.getRoot().equals(this.importRoot)) {
            throw new IllegalArgumentException("Import and home DB directory must not be equal");
        }
        
        // configure import DB
        String errorMsg = null;
        if (!this.importRoot.exists()) errorMsg = "Import directory does not exist.";
        if (!this.importRoot.canRead()) errorMsg = "Import directory is not readable.";
        if (!this.importRoot.canWrite()) errorMsg = "Import directory is not writeable";
        if (!this.importRoot.isDirectory()) errorMsg = "ImportDirectory is not a directory.";
        if (errorMsg != null) {
            this.log.logSevere(errorMsg + "\nName: " + this.importRoot.getAbsolutePath());
            throw new IllegalArgumentException(errorMsg);
        }         
        
        this.log.logFine("Initializing source word index db.");
        this.importWordIndex = new plasmaWordIndex(this.importRoot, 8*1024*1024, this.log);
        this.log.logFine("Initializing import URL db.");
        this.importUrlDB = new plasmaCrawlLURL(new File(this.importRoot, "urlHash.db"), 4*1024*1024);
        this.importStartSize = this.importWordIndex.size();
    }
    
    public void run() {
        try {
            importWordsDB();
        } finally {
            this.globalEnd = System.currentTimeMillis();
            finishedJobs.add(this);
        }
    }
    
    public long getTotalRuntime() {
        return (this.globalEnd == 0)?System.currentTimeMillis()-this.globalStart:this.globalEnd-this.globalStart;
    }
    
    public int getProcessingStatus() {
        return (this.importStartSize-this.importWordIndex.size())/(this.importStartSize/100);
    }
    
    public long getElapsedTime() {
        return System.currentTimeMillis()-this.globalStart;
    }
    
    public long getEstimatedTime() {
        return (this.wordCounter==0)?0:this.importWordIndex.size()*((System.currentTimeMillis()-this.globalStart)/this.wordCounter);
    }
    
    public void importWordsDB() {
        this.log.logInfo("STARTING DB-IMPORT");  
        
        try {                                                
            this.log.logInfo("Importing DB from '" + this.importRoot.getAbsolutePath() + "' to '" + this.homeWordIndex.getRoot().getAbsolutePath() + "'.");
            this.log.logInfo("Home word index contains " + this.homeWordIndex.size() + " words and " + this.homeUrlDB.size() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.size() + " words and " + this.importUrlDB.size() + " URLs.");                        
            
            // iterate over all words from import db

            Iterator importWordHashIterator = this.importWordIndex.wordHashes(wordChunkStartHash, true, true);
            while (!isAborted() && importWordHashIterator.hasNext()) {
                
                plasmaWordIndexEntity importWordIdxEntity = null;
                try {
                    wordCounter++;
                    wordHash = (String) importWordHashIterator.next();
                    importWordIdxEntity = importWordIndex.getEntity(wordHash, true, -1);
                    
                    if (importWordIdxEntity.size() == 0) {
                        importWordIdxEntity.deleteComplete();
                        continue;
                    }
                    
                    // creating a container used to hold the imported entries
                    plasmaWordIndexEntryContainer newContainer = new plasmaWordIndexEntryContainer(wordHash,importWordIdxEntity.size());
                    
                    // the combined container will fit, read the container
                    Iterator importWordIdxEntries = importWordIdxEntity.elements(true);
                    plasmaWordIndexEntry importWordIdxEntry;
                    while (importWordIdxEntries.hasNext()) {
                        
                        // testing if import process was aborted
                        if (isAborted()) break;

                        // getting next word index entry
                        entryCounter++;
                        importWordIdxEntry = (plasmaWordIndexEntry) importWordIdxEntries.next();
                        String urlHash = importWordIdxEntry.getUrlHash();                    
                        if ((this.importUrlDB.exists(urlHash)) && (!this.homeUrlDB.exists(urlHash))) try {
                            // importing the new url
                            plasmaCrawlLURL.Entry urlEntry = this.importUrlDB.getEntry(urlHash);                       
                            urlCounter++;
                            this.homeUrlDB.newEntry(urlEntry);
                            
                            if (urlCounter % 500 == 0) {
                                this.log.logFine(urlCounter + " URLs processed so far.");
                            }
                        } catch (IOException e) {}
                        
                        // adding word index entity to container
                        newContainer.add(importWordIdxEntry,System.currentTimeMillis());
                        
                        if (entryCounter % 500 == 0) {
                            this.log.logFine(entryCounter + " word entries and " + wordCounter + " word entities processed so far.");
                        }
                    }
                    
                    // testing if import process was aborted
                    if (isAborted()) break;
                    
                    // importing entity container to home db
                    homeWordIndex.addEntries(newContainer, true);
                                        
                    // delete complete index entity file
                    importWordIdxEntity.close();
                    importWordIndex.deleteIndex(wordHash);                 
                    
                    // print out some statistical information
                    if (wordCounter%500 == 0) {
                        wordChunkEndHash = wordHash;
                        wordChunkEnd = System.currentTimeMillis();
                        long duration = wordChunkEnd - wordChunkStart;
                        log.logInfo(wordCounter + " word entities imported " +
                                "[" + wordChunkStartHash + " .. " + wordChunkEndHash + "] " +
                                this.getProcessingStatus() + "%\n" + 
                                "Speed: "+ 500*1000/duration + " word entities/s" +
                                " | Elapsed time: " + serverDate.intervalToString(getElapsedTime()) +
                                " | Estimated time: " + serverDate.intervalToString(getEstimatedTime()) + "\n" + 
                                "Home Words = " + homeWordIndex.size() + 
                                " | Import Words = " + importWordIndex.size());
                        wordChunkStart = wordChunkEnd;
                        wordChunkStartHash = wordChunkEndHash;
                    }                    
                    
                } catch (Exception e) {
                    log.logSevere("Import of word entity '" + wordHash + "' failed.",e);
                } finally {
                    if (importWordIdxEntity != null) try { importWordIdxEntity.close(); } catch (Exception e) {}
                }
            }
            
            this.log.logInfo("Home word index contains " + homeWordIndex.size() + " words and " + homeUrlDB.size() + " URLs.");
            this.log.logInfo("Import word index contains " + importWordIndex.size() + " words and " + importUrlDB.size() + " URLs.");
            
            this.log.logInfo("DB-IMPORT FINISHED");
        } catch (Exception e) {
            this.log.logSevere("Database import failed.",e);
            e.printStackTrace();
            this.error = e.toString();
        } finally {
            if (importUrlDB != null) try { importUrlDB.close(); } catch (Exception e){}
            if (importWordIndex != null) try { importWordIndex.close(5000); } catch (Exception e){}
        }
    }    
    
    private boolean isAborted() {
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
    
}
