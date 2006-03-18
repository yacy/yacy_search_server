package de.anomic.plasma.dbImport;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroNaturalOrder;
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
    private long urlCounter = 0, wordCounter = 0, entryCounter = 0, notBoundEntryCounter = 0;
    

    public plasmaDbImporter(plasmaSwitchboard theSb) {
        super(theSb);
        this.jobType = "PLASMADB";
    }
    
    public String getJobName() {
        return this.importPath.toString();
    }

    public String getStatus() {
        StringBuffer theStatus = new StringBuffer();
        
        theStatus.append("Hash=").append(this.wordHash).append("\n");
        theStatus.append("#URL=").append(this.urlCounter).append("\n");
        theStatus.append("#Word Entity=").append(this.wordCounter).append("\n");
        theStatus.append("#Word Entry={").append(this.entryCounter);
        theStatus.append(" ,NotBound=").append(this.notBoundEntryCounter).append("}");
        
        return theStatus.toString();
    }
    
    public void init(File theImportPath, int theCacheSize) {
        super.init(theImportPath);
            
        this.homeWordIndex = this.sb.wordIndex;
        this.homeUrlDB = this.sb.urlPool.loadedURL;
        this.cacheSize = theCacheSize;
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
        this.importWordIndex = new plasmaWordIndex(this.importPath, (this.cacheSize/2)/1024, this.log);
        this.log.logFine("Initializing import URL db.");
        this.importUrlDB = new plasmaCrawlLURL(new File(this.importPath, "urlHash.db"), (this.cacheSize/2)/1024);
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
        //return (this.importStartSize-this.importWordIndex.size())/((this.importStartSize<100)?1:(this.importStartSize)/100);
        return (int)(this.wordCounter)/((this.importStartSize<100)?1:(this.importStartSize)/100);
    }

    public long getEstimatedTime() {
        return (this.wordCounter==0)?0:((this.importStartSize*getElapsedTime())/this.wordCounter)-getElapsedTime();
    }
    
    public void importWordsDB() {
        this.log.logInfo("STARTING DB-IMPORT");  
        
        try {                                                
            this.log.logInfo("Importing DB from '" + this.importPath.getAbsolutePath() + "' to '" + this.homeWordIndex.getRoot().getAbsolutePath() + "'.");
            this.log.logInfo("Home word index contains " + this.homeWordIndex.size() + " words and " + this.homeUrlDB.size() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.size() + " words and " + this.importUrlDB.size() + " URLs.");                        
            
            HashSet unknownUrlBuffer = new HashSet();
            HashSet importedUrlBuffer = new HashSet();
			
            // iterate over all words from import db
            //Iterator importWordHashIterator = this.importWordIndex.wordHashes(this.wordChunkStartHash, plasmaWordIndex.RL_WORDFILES, false);
            Iterator importWordHashIterator = this.importWordIndex.wordHashSet(this.wordChunkStartHash, plasmaWordIndex.RL_WORDFILES, false, 100).iterator();
            while (!isAborted() && importWordHashIterator.hasNext()) {
                
                TreeSet entityUrls = new TreeSet(new kelondroNaturalOrder(true));
                plasmaWordIndexEntryContainer newContainer = null;
                try {
                    this.wordCounter++;
                    this.wordHash = (String) importWordHashIterator.next();
                    newContainer = this.importWordIndex.getContainer(this.wordHash, true, -1);
                    
                    // loop throug the entities of the container and get the
                    // urlhash
                    Iterator importWordIdxEntries = newContainer.entries();
                    plasmaWordIndexEntry importWordIdxEntry;
                    while (importWordIdxEntries.hasNext()) {
                        // testing if import process was aborted
                        if (isAborted()) break;

                        // getting next word index entry
                        importWordIdxEntry = (plasmaWordIndexEntry) importWordIdxEntries.next();
                        String urlHash = importWordIdxEntry.getUrlHash();
                        entityUrls.add(urlHash);
                    }

                    Iterator urlIter = entityUrls.iterator();
                    while (urlIter.hasNext()) {	
                        if (isAborted()) break;
                        String urlHash = (String) urlIter.next();

                        if (importedUrlBuffer.contains(urlHash)) {
                            // already known url
                        } else if (unknownUrlBuffer.contains(urlHash)) {
                            // url known as unknown
                            unknownUrlBuffer.add(urlHash);
                            notBoundEntryCounter++;
                            newContainer.remove(urlHash);
                            continue;
                        } else {
                            // we need to import the url
                            try {	
                                // getting the url entry
                                plasmaCrawlLURL.Entry urlEntry = this.importUrlDB.getEntry(urlHash, null);

                                /* write it into the home url db */
                                plasmaCrawlLURL.Entry homeEntry = this.homeUrlDB.newEntry(urlEntry);
                                homeEntry.store();
                                importedUrlBuffer.add(urlHash);
                                this.urlCounter++;

                                if (this.urlCounter % 500 == 0) {
                                    this.log.logFine(this.urlCounter + " URLs processed so far.");
                                }
                            } catch (IOException e) {
                                unknownUrlBuffer.add(urlHash);
                                notBoundEntryCounter++;
                                newContainer.remove(urlHash);
                                continue;
                            }
                        }
                        this.entryCounter++;
                    }
					
                    // testing if import process was aborted
                    if (isAborted()) break;
                    
                    // importing entity container to home db
                    if (newContainer.size() > 0) { this.homeWordIndex.addEntries(newContainer, System.currentTimeMillis(), false); }
                    
                    // delete complete index entity file
                    this.importWordIndex.deleteIndex(this.wordHash);                 
                    
                    // print out some statistical information
                    if (this.entryCounter % 500 == 0) {
                        this.log.logFine(this.entryCounter + " word entries and " + this.wordCounter + " word entities processed so far.");
                    }

                    if (this.wordCounter%500 == 0) {
                        this.wordChunkEndHash = this.wordHash;
                        this.wordChunkEnd = System.currentTimeMillis();
                        long duration = this.wordChunkEnd - this.wordChunkStart;
                        this.log.logInfo(this.wordCounter + " word entities imported " +
                                "[" + this.wordChunkStartHash + " .. " + this.wordChunkEndHash + "] " +
                                this.getProcessingStatusPercent() + "%\n" + 
                                "Speed: "+ 500*1000/duration + " word entities/s" +
                                " | Elapsed time: " + serverDate.intervalToString(getElapsedTime()) +
                                " | Estimated time: " + serverDate.intervalToString(getEstimatedTime()) + "\n" + 
                                "Home Words = " + this.homeWordIndex.size() + 
                                " | Import Words = " + this.importWordIndex.size());
                        this.wordChunkStart = this.wordChunkEnd;
                        this.wordChunkStartHash = this.wordChunkEndHash;
                    }                    
                    
                } catch (Exception e) {
                    this.log.logSevere("Import of word entity '" + this.wordHash + "' failed.",e);
                } finally {
                    if (newContainer != null) newContainer.clear();
                }

                if (!importWordHashIterator.hasNext()) {
                    // We may not be finished yet, try to get the next chunk of wordHashes
                    TreeSet wordHashes = this.importWordIndex.wordHashSet(this.wordHash, plasmaWordIndex.RL_WORDFILES, false, 100);
                    importWordHashIterator = wordHashes.iterator();
                    // Make sure we don't get the same wordhash twice, but don't skip a word
                    if ((importWordHashIterator.hasNext())&&(!this.wordHash.equals(importWordHashIterator.next()))) {
                        importWordHashIterator = wordHashes.iterator();
                    }
                }
            }
            
            this.log.logInfo("Home word index contains " + this.homeWordIndex.size() + " words and " + this.homeUrlDB.size() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.size() + " words and " + this.importUrlDB.size() + " URLs.");
        } catch (Exception e) {
            this.log.logSevere("Database import failed.",e);
            e.printStackTrace();
            this.error = e.toString();
        } finally {
            this.log.logInfo("Import process finished.");
            if (this.importUrlDB != null) try { this.importUrlDB.close(); } catch (Exception e){}
            if (this.importWordIndex != null) try { this.importWordIndex.close(5000); } catch (Exception e){}
        }
    }    
    

    
}
