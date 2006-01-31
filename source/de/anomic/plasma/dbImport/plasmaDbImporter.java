package de.anomic.plasma.dbImport;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.plasmaWordIndexEntry;
import de.anomic.plasma.plasmaWordIndexEntryContainer;
import de.anomic.server.serverDate;

public class plasmaDbImporter extends AbstractImporter implements dbImporter {

    private plasmaCrawlLURL homeUrlDB;
    private plasmaWordIndex homeWordIndex;
    
    private plasmaCrawlLURL importUrlDB;
    private plasmaWordIndex importWordIndex;
    private int importStartSize;
    

    private String wordHash = "------------";
    
    long wordChunkStart = System.currentTimeMillis(), wordChunkEnd = this.wordChunkStart;
    String wordChunkStartHash = "------------", wordChunkEndHash;
    private long urlCounter = 0, wordCounter = 0, entryCounter = 0;
    

    public plasmaDbImporter(plasmaSwitchboard sb) {
        super(sb);
        this.jobType = "PLASMADB";
    }
    
    public String getJobName() {
        return this.importPath.toString();
    }

    public String getStatus() {
        StringBuffer theStatus = new StringBuffer();
        
        theStatus.append("Hash=").append(this.wordHash).append("\n");
        theStatus.append("#URL=").append(this.urlCounter).append("\n");
        theStatus.append("#Word Entities=").append(this.wordCounter).append("\n");
        theStatus.append("#Word Entries=").append(this.entryCounter);
        
        return theStatus.toString();
    }
    
    public void init(File theImportPath, int cacheSize) {
        super.init(theImportPath);
            
        this.homeWordIndex = this.sb.wordIndex;
        this.homeUrlDB = this.sb.urlPool.loadedURL;
        this.cacheSize = cacheSize;
        if (this.cacheSize < 2*1024*1024) this.cacheSize = 8*1024*1024;
        
        if (this.homeWordIndex.getRoot().equals(this.importPath)) {
            throw new IllegalArgumentException("Import and home DB directory must not be equal");
        }
        
        // configure import DB
        String errorMsg = null;
        if (!this.importPath.exists()) errorMsg = "Import directory does not exist.";
        if (!this.importPath.canRead()) errorMsg = "Import directory is not readable.";
        if (!this.importPath.canWrite()) errorMsg = "Import directory is not writeable";
        if (!this.importPath.isDirectory()) errorMsg = "ImportDirectory is not a directory.";
        if (errorMsg != null) {
            this.log.logSevere(errorMsg + "\nName: " + this.importPath.getAbsolutePath());
            throw new IllegalArgumentException(errorMsg);
        }         
        
        this.log.logFine("Initializing source word index db.");
        this.importWordIndex = new plasmaWordIndex(this.importPath, this.cacheSize/2, this.log);
        this.log.logFine("Initializing import URL db.");
        this.importUrlDB = new plasmaCrawlLURL(new File(this.importPath, "urlHash.db"), this.cacheSize/2);
        this.importStartSize = this.importWordIndex.size();
    }
    
    public void run() {
        try {
            importWordsDB();
        } finally {
            this.globalEnd = System.currentTimeMillis();
            this.sb.dbImportManager.finishedJobs.add(this);
        }
    }
    

    
    public int getProcessingStatusPercent() {
        // thid seems to be better:
        // (this.importStartSize-this.importWordIndex.size())*100/((this.importStartSize==0)?1:this.importStartSize);
        // but maxint (2,147,483,647) could be exceeded when WordIndexes reach 20M entries
        return (this.importStartSize-this.importWordIndex.size())/((this.importStartSize<100)?1:(this.importStartSize)/100);
    }

    
    public long getEstimatedTime() {
        return (this.wordCounter==0)?0:this.importWordIndex.size()*((System.currentTimeMillis()-this.globalStart)/this.wordCounter);
    }
    
    public void importWordsDB() {
        this.log.logInfo("STARTING DB-IMPORT");  
        
        try {                                                
            this.log.logInfo("Importing DB from '" + this.importPath.getAbsolutePath() + "' to '" + this.homeWordIndex.getRoot().getAbsolutePath() + "'.");
            this.log.logInfo("Home word index contains " + this.homeWordIndex.size() + " words and " + this.homeUrlDB.size() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.size() + " words and " + this.importUrlDB.size() + " URLs.");                        
            
            // iterate over all words from import db

            Iterator importWordHashIterator = this.importWordIndex.wordHashes(wordChunkStartHash, true, true);
            while (!isAborted() && importWordHashIterator.hasNext()) {
                
                plasmaWordIndexEntryContainer newContainer;
                try {
                    wordCounter++;
                    wordHash = (String) importWordHashIterator.next();
                    newContainer = importWordIndex.getContainer(wordHash, true, -1);
                    
                    if (newContainer.size() == 0) continue;
                    
                    // the combined container will fit, read the container
                    Iterator importWordIdxEntries = newContainer.entries();
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
                            plasmaCrawlLURL.Entry urlEntry = this.importUrlDB.getEntry(urlHash, importWordIdxEntry);                       
                            urlCounter++;
                            this.homeUrlDB.newEntry(urlEntry);
                            
                            if (urlCounter % 500 == 0) {
                                this.log.logFine(urlCounter + " URLs processed so far.");
                            }
                        } catch (IOException e) {}
                        
                        if (entryCounter % 500 == 0) {
                            this.log.logFine(entryCounter + " word entries and " + wordCounter + " word entities processed so far.");
                        }
                    }
                    
                    // testing if import process was aborted
                    if (isAborted()) break;
                    
                    // importing entity container to home db
                    homeWordIndex.addEntries(newContainer, true);
                                        
                    // delete complete index entity file
                    importWordIndex.deleteIndex(wordHash);                 
                    
                    // print out some statistical information
                    if (wordCounter%500 == 0) {
                        wordChunkEndHash = wordHash;
                        wordChunkEnd = System.currentTimeMillis();
                        long duration = wordChunkEnd - wordChunkStart;
                        log.logInfo(wordCounter + " word entities imported " +
                                "[" + wordChunkStartHash + " .. " + wordChunkEndHash + "] " +
                                this.getProcessingStatusPercent() + "%\n" + 
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
    

    
}
