package de.anomic.plasma;

import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.crawler.AbstractImporter;
import de.anomic.crawler.Importer;
import de.anomic.kelondro.text.Reference;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.util.DateFormatter;

public class plasmaDbImporter extends AbstractImporter implements Importer {
	
	/**
	 * the source word index (the DB to import)
	 */
    private final Segment importWordIndex;
    
    /**
     * the destination word index (the home DB)
     */
    protected Segment homeWordIndex;
    private final int importStartSize;   

    private byte[] wordHash = "------------".getBytes();
    
    long wordChunkStart = System.currentTimeMillis(), wordChunkEnd = this.wordChunkStart;
    byte[] wordChunkStartHash = "------------".getBytes(), wordChunkEndHash;
    private long urlCounter = 0, wordCounter = 0, entryCounter = 0, notBoundEntryCounter = 0;
    

    public plasmaDbImporter(final Segment homeWI, final Segment importWI) {
    	super("PLASMADB");
        this.homeWordIndex = homeWI;
        this.importWordIndex = importWI;
        this.importStartSize = this.importWordIndex.termIndex().size();
    }

    /**
     * @see Importer#getJobName()
     */
    public String getJobName() {
        return this.importWordIndex.getLocation().toString();
    }

    /**
     * @see Importer#getStatus()
     */
    public String getStatus() {
        final StringBuilder theStatus = new StringBuilder();
        
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
            this.log.logInfo("Importing DB from '" + this.importWordIndex.getLocation().getAbsolutePath() + "'");
            this.log.logInfo("Home word index contains " + homeWordIndex.termIndex().size() + " words and " + homeWordIndex.urlMetadata().size() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.termIndex().size() + " words and " + this.importWordIndex.urlMetadata().size() + " URLs.");                        
            
            final HashSet<String> unknownUrlBuffer = new HashSet<String>();
            final HashSet<String> importedUrlBuffer = new HashSet<String>();
			
            // iterate over all words from import db
            //Iterator importWordHashIterator = this.importWordIndex.wordHashes(this.wordChunkStartHash, CrawlSwitchboard.RL_WORDFILES, false);
            Iterator<ReferenceContainer<WordReference>> indexContainerIterator = this.importWordIndex.termIndex().references(this.wordChunkStartHash, false, 100, false).iterator();
            while (!isAborted() && indexContainerIterator.hasNext()) {
                
                final TreeSet<String> entityUrls = new TreeSet<String>();
                ReferenceContainer<WordReference> newContainer = null;
                try {
                    this.wordCounter++;
                    newContainer = indexContainerIterator.next();
                    this.wordHash = newContainer.getTermHash();
                    
                    // loop throug the entities of the container and get the
                    // urlhash
                    final Iterator<WordReference> importWordIdxEntries = newContainer.entries();
                    Reference importWordIdxEntry;
                    while (importWordIdxEntries.hasNext()) {
                        // testing if import process was aborted
                        if (isAborted()) break;

                        // getting next word index entry
                        importWordIdxEntry = importWordIdxEntries.next();
                        final String urlHash = importWordIdxEntry.metadataHash();
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
                            }
                            // we need to import the url

                            // getting the url entry
                            final URLMetadataRow urlEntry = this.importWordIndex.urlMetadata().load(urlHash, null, 0);
                            if (urlEntry != null) {

                                /* write it into the home url db */
                                homeWordIndex.urlMetadata().store(urlEntry);
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
                        //} else {
                            // already known url
                        }
                        this.entryCounter++;
                    }
					
                    // testing if import process was aborted
                    if (isAborted()) break;
                    
                    // importing entity container to home db
                    if (newContainer.size() > 0) { homeWordIndex.termIndex().add(newContainer); }
                    
                    // delete complete index entity file
                    this.importWordIndex.termIndex().delete(this.wordHash);                 
                    
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
                                " | Elapsed time: " + DateFormatter.formatInterval(getElapsedTime()) +
                                " | Estimated time: " + DateFormatter.formatInterval(getEstimatedTime()) + "\n" + 
                                "Home Words = " + homeWordIndex.termIndex().size() + 
                                " | Import Words = " + this.importWordIndex.termIndex().size());
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
                    final TreeSet<ReferenceContainer<WordReference>> containers = this.importWordIndex.termIndex().references(this.wordHash, false, 100, false);
                    indexContainerIterator = containers.iterator();
                    // Make sure we don't get the same wordhash twice, but don't skip a word
                    if ((indexContainerIterator.hasNext())&&(!this.wordHash.equals((indexContainerIterator.next()).getTermHash()))) {
                        indexContainerIterator = containers.iterator();
                    }
                }
            }
            
            this.log.logInfo("Home word index contains " + homeWordIndex.termIndex().size() + " words and " + homeWordIndex.urlMetadata().size() + " URLs.");
            this.log.logInfo("Import word index contains " + this.importWordIndex.termIndex().size() + " words and " + this.importWordIndex.urlMetadata().size() + " URLs.");
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
