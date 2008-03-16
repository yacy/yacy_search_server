package de.anomic.plasma.dbImport;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;

public class plasmaCrawlNURLImporter extends AbstractImporter implements dbImporter {

	private File plasmaPath = null;
    private HashSet<String> importProfileHandleCache = new HashSet<String>();
    private plasmaCrawlProfile importProfileDB;
    private plasmaCrawlNURL importNurlDB;
    private int importStartSize;
    private int urlCount = 0;
    private int profileCount = 0;
    
    public plasmaCrawlNURLImporter(plasmaSwitchboard theSb) {
    	super("NURL",theSb);
    }

    public long getEstimatedTime() {
        return (this.urlCount==0)?0:((this.importStartSize*getElapsedTime())/(this.urlCount))-getElapsedTime();
    }

    public String getJobName() {
        return this.plasmaPath.toString();
    }

    public int getProcessingStatusPercent() {
        return (this.urlCount)/((this.importStartSize<100)?1:(this.importStartSize)/100);
    }

    public String getStatus() {
        StringBuffer theStatus = new StringBuffer();
        
        theStatus.append("#URLs=").append(this.urlCount).append("\n");
        theStatus.append("#Profiles=").append(this.profileCount);
        
        return theStatus.toString();
    }

    public void init(plasmaSwitchboard sb, int cacheSize) throws ImporterException {
        super.init();
        
        // TODO: we need more errorhandling here
        this.plasmaPath = sb.plasmaPath;
        this.cacheSize = cacheSize;
        if (this.cacheSize < 2*1024*1024) this.cacheSize = 8*1024*1024;
        File noticeUrlDbFile = new File(plasmaPath,"urlNotice1.db");
        File profileDbFile = new File(plasmaPath, plasmaSwitchboard.DBFILE_ACTIVE_CRAWL_PROFILES);
        
        String errorMsg = null;
        if (!plasmaPath.exists()) 
            errorMsg = "The import path '" + plasmaPath+ "' does not exist.";
        else if (!plasmaPath.isDirectory()) 
            errorMsg = "The import path '" + plasmaPath + "' is not a directory.";
        else if (!plasmaPath.canRead()) 
            errorMsg = "The import path '" + plasmaPath + "' is not readable.";
        else if (!plasmaPath.canWrite()) 
            errorMsg = "The import path '" + plasmaPath + "' is not writeable.";
        
        else if (!noticeUrlDbFile.exists()) 
            errorMsg = "The noticeUrlDB file '" + noticeUrlDbFile + "' does not exist.";
        else if (noticeUrlDbFile.isDirectory()) 
            errorMsg = "The noticeUrlDB file '" + noticeUrlDbFile + "' is not a file.";
        else if (!noticeUrlDbFile.canRead()) 
            errorMsg = "The noticeUrlDB file '" + noticeUrlDbFile + "' is not readable.";
        else if (!noticeUrlDbFile.canWrite()) 
            errorMsg = "The noticeUrlDB file '" + noticeUrlDbFile + "' is not writeable.";   
        
        else if (!profileDbFile.exists()) 
            errorMsg = "The profileDB file '" + profileDbFile + "' does not exist.";
        else if (profileDbFile.isDirectory()) 
            errorMsg = "The profileDB file '" + profileDbFile + "' is not a file.";
        else if (!profileDbFile.canRead()) 
            errorMsg = "The profileDB file '" + profileDbFile + "' is not readable.";
//        else if (!profileDbFile.canWrite()) 
//            errorMsg = "The profileDB file '" + profileDbFile + "' is not writeable.";                
        
        if (errorMsg != null) {
            this.log.logSevere(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }   
        
        // init noticeUrlDB
        this.log.logInfo("Initializing the source noticeUrlDB");
        this.importNurlDB = new plasmaCrawlNURL(plasmaPath);
        this.importStartSize = this.importNurlDB.size();
        //int stackSize = this.importNurlDB.stackSize();
        
        // init profile DB
        this.log.logInfo("Initializing the source profileDB");
        this.importProfileDB = new plasmaCrawlProfile(profileDbFile);
    }

    @SuppressWarnings("unchecked")
    public void run() {
        try {   
            // waiting on init thread to finish
            //this.importNurlDB.waitOnInitThread();
            
            // the stack types we want to import
            int[] stackTypes = new int[] {plasmaCrawlNURL.STACK_TYPE_CORE,
                                          plasmaCrawlNURL.STACK_TYPE_LIMIT,
                                          plasmaCrawlNURL.STACK_TYPE_REMOTE,
                                          -1};
            
            // looping through the various stacks
            for (int stackType=0; stackType< stackTypes.length; stackType++) {
                if (stackTypes[stackType] != -1) {
                    this.log.logInfo("Starting to import stacktype '" + stackTypes[stackType] + "' containing '" + this.importNurlDB.stackSize(stackTypes[stackType]) + "' entries.");
                } else {
                    this.log.logInfo("Starting to import '" + this.importNurlDB.size() + "' entries not available in any stack.");
                }
                
                // getting an iterator and loop through the URL entries
                Iterator<plasmaCrawlEntry> entryIter = (stackTypes[stackType] == -1) ? this.importNurlDB.iterator(stackType) : null;
                while (true) {
                    
                    String nextHash = null;
                    plasmaCrawlEntry nextEntry = null;
                    
                    try {                        
                        if (stackTypes[stackType] != -1) {
                            if (this.importNurlDB.stackSize(stackTypes[stackType]) == 0) break;
                            
                            this.urlCount++;
                            nextEntry = this.importNurlDB.pop(stackTypes[stackType], false);
                            nextHash = nextEntry.url().hash();
                        } else {
                            if (!entryIter.hasNext()) break;
                            
                            this.urlCount++;
                            nextEntry = entryIter.next();
                            nextHash = nextEntry.url().hash();
                        }
                    } catch (IOException e) {
                        this.log.logWarning("Unable to import entry: " + e.toString());
                        
                        if ((stackTypes[stackType] != -1) &&(this.importNurlDB.stackSize(stackTypes[stackType]) == 0)) break;
                        continue;
                    }
                    
                    // getting a handler to the crawling profile the url belongs to
                    try {
                        String profileHandle = nextEntry.profileHandle();
                        if (profileHandle == null) {
                            this.log.logWarning("Profile handle of url entry '" + nextHash + "' unknown.");
                            continue;
                        }
                        
                        // if we havn't imported the profile until yet we need to do it now
                        if (!this.importProfileHandleCache.contains(profileHandle)) {
                            
                            // testing if the profile is already known
                            plasmaCrawlProfile.entry profileEntry = this.sb.profilesActiveCrawls.getEntry(profileHandle);
                            
                            // if not we need to import it
                            if (profileEntry == null) {
                                // copy and store the source profile entry into the destination db
                                plasmaCrawlProfile.entry sourceEntry = this.importProfileDB.getEntry(profileHandle);
                                if (sourceEntry != null) {
                                    this.profileCount++;
                                    this.importProfileHandleCache.add(profileHandle);
                                    this.sb.profilesActiveCrawls.newEntry((HashMap<String, String>) sourceEntry.map().clone());
                                } else {
                                    this.log.logWarning("Profile '" + profileHandle + "' of url entry '" + nextHash + "' unknown.");
                                    continue;
                                }
                            }                        
                        }
                        
                        // if the url does not alredy exists in the destination stack we insert it now
                        if (!this.sb.crawlQueues.noticeURL.existsInStack(nextHash)) {
                            this.sb.crawlQueues.noticeURL.push((stackTypes[stackType] != -1) ? stackTypes[stackType] : plasmaCrawlNURL.STACK_TYPE_CORE, nextEntry);
                        }
                        
                        // removing hash from the import db
                    } finally {
                        this.importNurlDB.removeByURLHash(nextHash);
                    }
                    
                    if (this.urlCount % 100 == 0) {
                        this.log.logFine(this.urlCount + " URLs and '" + this.profileCount + "' profile entries processed so far.");
                    }                 
                    if (this.isAborted()) break; 
                }
                this.log.logInfo("Finished to import stacktype '" + stackTypes[stackType] + "'");
            }
            
            //int size = this.importNurlDB.size();
            //int stackSize = this.importNurlDB.stackSize();
            
            // TODO: what todo with nurlDB entries that do not exist in any stack?
            
        } catch (Exception e) {
            this.error = e.toString();     
            this.log.logSevere("Import process had detected an error",e);
        } finally { 
            this.log.logInfo("Import process finished.");
            this.globalEnd = System.currentTimeMillis();
            this.sb.dbImportManager.finishedJobs.add(this);
            this.importNurlDB.close();
            this.importProfileDB.close();
        }
    }
    
}
