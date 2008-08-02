package de.anomic.plasma;

import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.crawler.AbstractImporter;
import de.anomic.crawler.Importer;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexURLReference;
import de.anomic.server.serverDate;

public class plasmaDbImporter extends AbstractImporter implements Importer {
	
	/**
	 * the source word index (the DB to import)
	 */
    private final plasmaWordIndex importWordIndex;
    
    /**
     * the destination word index (the home DB)
     */
    protected plasmaWordIndex homeWordIndex;
    private final int importStartSize;   

    private String wordHash = "------------";
    
    long wordChunkStart = System.currentTimeMillis(), wordChunkEnd = this.wordChunkStart;
    String wordChunkStartHash = "------------", wordChunkEndHash;
    private long urlCounter = 0, wordCounter = 0, entryCounter = 0, notBoundEntryCounter = 0;
    

    public plasmaDbImporter(final plasmaWordIndex homeWI, final plasmaWordIndex importWI) {
    	super("PLASMADB");
        this.homeWordIndex = homeWI;
        this.importWordIndex = importWI;
        this.importStartSize = this.importWordIndex.size();
    }

    /**
     * @see Importer#getJobName()
     */
    public String getJobName() {
        return this.importWordIndex.getLocation(true).toString();
    }

    /**
     * @see Importer#getStatus()
     */
    public String getStatus() {
        final StringBuffer theStatus = new StringBuffer();
        
        theStatus.append("Hash=").append(this.wordHash).append("\n");
        theStatus.append("#URL=").append(this.urlCounter).append("\n");
        theStatus.append("#Word Entity=").append(this.wordCounter).append("\n");
        theStatus.append("#Word Entry={").append(this.entryCounter);
        theStatus.append(" ,NotBound=").append(this.notBoundEntryCounter).append("}");
        
        return theStatus.toString();
    }
    
    public void run() {
        try {
            importWordsDB();
        } finally {
            this.globalEnd = System.currentTimeMillis();
            //this.sb.dbImportManager.finishedJobs.add(this);
        }
    }

    /**
     * @see Importer#getProcessingStatusPercent()
     */
    public int getProcessingStatusPercent() {
        // thid seems to be better:
        // (this.importStartSize-this.importWordIndex.size())*100/((this.importStartSize==0)?1:this.importStartSize);
        // but maxint (2,147,483,647) could be exceeded when WordIndexes reach 20M entries
        //return (this.importStartSize-this.importWordIndex.size())/((this.importStartSize<100)?1:(this.importStartSize)/100);
        return (int)(this.wordCounter)/((this.importStartSize<100)?1:(this.importStartSize)/100);
    }

    /**
     * @see Importer#getElapsedTime()
     */
    public long getEstimatedTime() {
        return (this.wordCounter==0)?0:((this.importStartSize*getElapsedTime())/this.wordCounter)-getElapsedTime();
    }
    
    public void importWordsDB() {
        this.log.logInfo("STARTING DB-IMPORT");  
        
        try {
            this.log.logInfo("Importing DB from '" + this.importWordIndex.getLocation(true).getAbsolutePath() + "'");
            this.log.logInfo("Home word index contains " + homeWordIndex.size() + " words and " + homeWordIndex.countURL() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.size() + " words and " + this.importWordIndex.countURL() + " URLs.");                        
            
            final HashSet<String> unknownUrlBuffer = new HashSet<String>();
            final HashSet<String> importedUrlBuffer = new HashSet<String>();
			
            // iterate over all words from import db
            //Iterator importWordHashIterator = this.importWordIndex.wordHashes(this.wordChunkStartHash, plasmaWordIndex.RL_WORDFILES, false);
            Iterator<indexContainer> indexContainerIterator = this.importWordIndex.indexContainerSet(this.wordChunkStartHash, false, false, 100).iterator();
            while (!isAborted() && indexContainerIterator.hasNext()) {
                
                final TreeSet<String> entityUrls = new TreeSet<String>();
                indexContainer newContainer = null;
                try {
                    this.wordCounter++;
                    newContainer = indexContainerIterator.next();
                    this.wordHash = newContainer.getWordHash();
                    
                    // loop throug the entities of the container and get the
                    // urlhash
                    final Iterator<indexRWIRowEntry> importWordIdxEntries = newContainer.entries();
                    indexRWIEntry importWordIdxEntry;
                    while (importWordIdxEntries.hasNext()) {
                        // testing if import process was aborted
                        if (isAborted()) break;

                        // getting next word index entry
                        importWordIdxEntry = importWordIdxEntries.next();
                        final String urlHash = importWordIdxEntry.urlHash();
                        entityUrls.add(urlHash);
                    }

                    final Iterator<String> urlIter = entityUrls.iterator();
                    while (urlIter.hasNext()) {	
                        if (isAborted()) break;
                        final String urlHash = urlIter.next();

                        if (!importedUrlBuffer.contains(urlHash)) {
                            if (unknownUrlBuffer.contains(urlHash)) {
                                // url known as unknown
                                unknownUrlBuffer.add(urlHash);
                                notBoundEntryCounter++;
                                newContainer.remove(urlHash);
                                continue;
                            } else {
                                // we need to import the url

                                // getting the url entry
                                final indexURLReference urlEntry = this.importWordIndex.getURL(urlHash, null, 0);
                                if (urlEntry != null) {

                                    /* write it into the home url db */
                                    homeWordIndex.putURL(urlEntry);
                                    importedUrlBuffer.add(urlHash);
                                    this.urlCounter++;

                                    if (this.urlCounter % 500 == 0) {
                                        this.log.logFine(this.urlCounter + " URLs processed so far.");
                                    }

                                } else {
                                    unknownUrlBuffer.add(urlHash);
                                    notBoundEntryCounter++;
                                    newContainer.remove(urlHash);
                                    continue;
                                }
                            }
                        //} else {
                            // already known url
                        }
                        this.entryCounter++;
                    }
					
                    // testing if import process was aborted
                    if (isAborted()) break;
                    
                    // importing entity container to home db
                    if (newContainer.size() > 0) { homeWordIndex.addEntries(newContainer); }
                    
                    // delete complete index entity file
                    this.importWordIndex.deleteContainer(this.wordHash);                 
                    
                    // print out some statistical information
                    if (this.entryCounter % 500 == 0) {
                        this.log.logFine(this.entryCounter + " word entries and " + this.wordCounter + " word entities processed so far.");
                    }

                    if (this.wordCounter%500 == 0) {
                        this.wordChunkEndHash = this.wordHash;
                        this.wordChunkEnd = System.currentTimeMillis();
                        final long duration = this.wordChunkEnd - this.wordChunkStart;
                        this.log.logInfo(this.wordCounter + " word entities imported " +
                                "[" + this.wordChunkStartHash + " .. " + this.wordChunkEndHash + "] " +
                                this.getProcessingStatusPercent() + "%\n" + 
                                "Speed: "+ 500*1000/duration + " word entities/s" +
                                " | Elapsed time: " + serverDate.formatInterval(getElapsedTime()) +
                                " | Estimated time: " + serverDate.formatInterval(getEstimatedTime()) + "\n" + 
                                "Home Words = " + homeWordIndex.size() + 
                                " | Import Words = " + this.importWordIndex.size());
                        this.wordChunkStart = this.wordChunkEnd;
                        this.wordChunkStartHash = this.wordChunkEndHash;
                    }                    
                    
                } catch (final Exception e) {
                    this.log.logSevere("Import of word entity '" + this.wordHash + "' failed.",e);
                } finally {
                    if (newContainer != null) newContainer.clear();
                }

                if (!indexContainerIterator.hasNext()) {
                    // We may not be finished yet, try to get the next chunk of wordHashes
                    final TreeSet<indexContainer> containers = this.importWordIndex.indexContainerSet(this.wordHash, false, false, 100);
                    indexContainerIterator = containers.iterator();
                    // Make sure we don't get the same wordhash twice, but don't skip a word
                    if ((indexContainerIterator.hasNext())&&(!this.wordHash.equals((indexContainerIterator.next()).getWordHash()))) {
                        indexContainerIterator = containers.iterator();
                    }
                }
            }
            
            this.log.logInfo("Home word index contains " + homeWordIndex.size() + " words and " + homeWordIndex.countURL() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.size() + " words and " + this.importWordIndex.countURL() + " URLs.");
        } catch (final Exception e) {
            this.log.logSevere("Database import failed.",e);
            e.printStackTrace();
            this.error = e.toString();
        } finally {
            this.log.logInfo("Import process finished.");
            if (this.importWordIndex != null) try { this.importWordIndex.close(); } catch (final Exception e){}
        }
    }    

}
