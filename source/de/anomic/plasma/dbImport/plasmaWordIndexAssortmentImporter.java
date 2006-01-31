package de.anomic.plasma.dbImport;

import java.io.File;
import java.util.Iterator;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.plasmaWordIndexAssortment;
import de.anomic.plasma.plasmaWordIndexEntryContainer;

public class plasmaWordIndexAssortmentImporter extends AbstractImporter implements dbImporter{

    private int importStartSize;
    private int wordEntityCount = 0;
    private int wordEntryCount = 0;
    
    private File importAssortmentFile;
    private plasmaWordIndexAssortment assortmentFile;
    
    public plasmaWordIndexAssortmentImporter(plasmaSwitchboard sb) {
        super(sb);
        this.jobType = "ASSORTMENT";
    }
    
    public void init(File importAssortmentFile, int cacheSize) {
        super.init(importAssortmentFile);
        this.importAssortmentFile = importAssortmentFile;
        this.cacheSize = cacheSize;
        if (this.cacheSize < 2*1024*1024) this.cacheSize = 8*1024*1024;
        
        String errorMsg = null;
        if (!importAssortmentFile.getName().matches("indexAssortment0[0-6][0-9]\\.db")) errorMsg = "AssortmentFile '" + importAssortmentFile + "' has an invalid name.";
        if (!importAssortmentFile.exists()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' does not exist.";
        else if (importAssortmentFile.isDirectory()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' is a directory.";
        else if (!importAssortmentFile.canRead()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' is not readable.";
        else if (!importAssortmentFile.canWrite()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' is not writeable.";
        
        
        File importAssortmentPath = null;
        int assortmentNr = -1;
        try {
            importAssortmentPath = new File(importAssortmentFile.getParent());
            assortmentNr = Integer.valueOf(importAssortmentFile.getName().substring("indexAssortment".length(),"indexAssortment".length()+3)).intValue();
            if (assortmentNr <1 || assortmentNr > 64) {
                errorMsg = "AssortmentFile '" + importAssortmentFile + "' has an invalid name.";
            }
        } catch (NumberFormatException e) {
            errorMsg = "Unable to parse the assortment file number.";
        }        
        
        if (errorMsg != null) {
            this.log.logSevere(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        
        this.log.logInfo("Initializing source assortment file");
        this.assortmentFile = new plasmaWordIndexAssortment(importAssortmentPath,assortmentNr,8*1024*1024, this.log);
        this.importStartSize = this.assortmentFile.size();
    }
    
    public long getEstimatedTime() {
        return (this.wordEntityCount==0)?0:this.assortmentFile.size()*((System.currentTimeMillis()-this.globalStart)/this.wordEntityCount);
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
            Iterator contentIter = this.assortmentFile.content();
            while (contentIter.hasNext()) {
                this.wordEntityCount++;                
                
                byte[][] row = (byte[][]) contentIter.next();
                String hash = new String(row[0]);
                plasmaWordIndexEntryContainer container;
                try {
                    container = this.assortmentFile.row2container(hash, row);
                } catch (NullPointerException e) {
                    this.log.logWarning("NullpointerException detected in row with hash  '" + hash + "'.");
                    if (this.wordEntityCount < this.importStartSize) continue;
                    return;
                }
                this.wordEntryCount += container.size();
                
                // importing entity container to home db
                this.sb.wordIndex.addEntries(container, true);
                
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
            this.log.logSevere("Error detected",e);
        } finally {
            this.globalEnd = System.currentTimeMillis();
            this.sb.dbImportManager.finishedJobs.add(this);
            this.assortmentFile.close();
        }
    }

}
