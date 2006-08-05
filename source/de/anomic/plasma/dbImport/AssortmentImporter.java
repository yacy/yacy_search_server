package de.anomic.plasma.dbImport;

import java.io.File;
import java.util.Iterator;

import de.anomic.index.indexContainer;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndexAssortment;

public class AssortmentImporter extends AbstractImporter implements dbImporter{

    private int importStartSize;
    private int wordEntityCount = 0;
    private int wordEntryCount = 0;
    
    private File importAssortmentFile;
    private plasmaWordIndexAssortment assortmentFile;
    
    public AssortmentImporter(plasmaSwitchboard sb) {
        super(sb);
        this.jobType = "ASSORTMENT";
    }
    
    public void init(File theImportAssortmentFile, File theIndexFile, int theCacheSize, long preloadTime) {
        super.init(theImportAssortmentFile, theIndexFile);
        this.importAssortmentFile = theImportAssortmentFile;
        this.cacheSize = theCacheSize;
        if (this.cacheSize < 2*1024*1024) this.cacheSize = 2*1024*1024;
        
        String errorMsg = null;
        if (!this.importAssortmentFile.getName().matches("indexAssortment0[0-6][0-9]\\.db")) 
            errorMsg = "AssortmentFile '" + this.importAssortmentFile + "' has an invalid name.";
        if (!this.importAssortmentFile.exists()) 
            errorMsg = "AssortmentFile '" + this.importAssortmentFile + "' does not exist.";
        else if (this.importAssortmentFile.isDirectory()) 
            errorMsg = "AssortmentFile '" + this.importAssortmentFile + "' is a directory.";
        else if (!this.importAssortmentFile.canRead()) 
            errorMsg = "AssortmentFile '" + this.importAssortmentFile + "' is not readable.";
        else if (!this.importAssortmentFile.canWrite()) 
            errorMsg = "AssortmentFile '" + this.importAssortmentFile + "' is not writeable.";
        if (errorMsg != null) {
            this.log.logSevere(errorMsg);
            throw new IllegalStateException(errorMsg);
        }        
        
        // getting the assortment length 
        File importAssortmentPath = null;
        int assortmentNr = -1;
        try {
            importAssortmentPath = new File(this.importAssortmentFile.getParent());
            assortmentNr = Integer.valueOf(this.importAssortmentFile.getName().substring("indexAssortment".length(),"indexAssortment".length()+3)).intValue();
            if (assortmentNr <1 || assortmentNr > 64) {
                errorMsg = "AssortmentFile '" + this.importAssortmentFile + "' has an invalid name.";
            }
        } catch (NumberFormatException e) {
            errorMsg = "Unable to parse the assortment file number.";
            this.log.logSevere(errorMsg);
            throw new IllegalStateException(errorMsg);
        }        

        // initializing the import assortment db
        this.log.logInfo("Initializing source assortment file");
        this.assortmentFile = new plasmaWordIndexAssortment(importAssortmentPath,assortmentNr, this.cacheSize/1024, preloadTime, this.log);
        this.importStartSize = this.assortmentFile.size();
    }
    
    public long getEstimatedTime() {
        return (this.wordEntityCount==0)?0:((this.assortmentFile.size()*getElapsedTime())/(this.wordEntityCount))-getElapsedTime();
    }

    public String getJobName() {
        return this.getImportPath().toString();
    }

    public int getProcessingStatusPercent() {
        return (this.wordEntityCount)/((this.importStartSize<100)?1:(this.importStartSize)/100);
    }

    public String getStatus() {
        StringBuffer theStatus = new StringBuffer();
        
        theStatus.append("#Word Entities=").append(this.wordEntityCount).append("\n");
        theStatus.append("#Word Entries=").append(this.wordEntryCount);
        
        return theStatus.toString();
    }
    
    public void run() {
        try {            
            // getting a content interator
            Iterator contentIterator = this.assortmentFile.containers(null, true, false);
            while (contentIterator.hasNext()) {
                this.wordEntityCount++;                
                
                // getting next entry as byte array
                indexContainer container = (indexContainer) contentIterator.next();
                
                this.wordEntryCount += container.size();
                
                // importing entity container to home db
                this.sb.wordIndex.addEntries(container, System.currentTimeMillis(), false);
                
                if (this.wordEntityCount % 500 == 0) {
                    this.log.logFine(this.wordEntityCount + " word entities processed so far.");
                }
                if (this.wordEntryCount % 2000 == 0) {
                    this.log.logFine(this.wordEntryCount + " word entries processed so far.");
                }                
                if (isAborted()) break;
            }
        } catch (Exception e) {
            this.error = e.toString();     
            this.log.logSevere("Import process had detected an error",e);
        } finally {
            this.log.logInfo("Import process finished.");
            this.globalEnd = System.currentTimeMillis();
            this.sb.dbImportManager.finishedJobs.add(this);
            this.assortmentFile.close();
        }
    }

}
