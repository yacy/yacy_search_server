package de.anomic.plasma.dbImport;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaCrawlNURL.Entry;

public class plasmaCrawlNURLImporter extends AbstractImporter implements
        dbImporter {

    private HashSet importProfileHandleCache = new HashSet();
    private plasmaCrawlProfile importProfileDB;
    private plasmaCrawlNURL importNurlDB;
    private int importStartSize;
    private int urlCount = 0;
    private int profileCount = 0;
    
    public plasmaCrawlNURLImporter(plasmaSwitchboard theSb) {
        super(theSb);
        this.jobType="NURL";
    }

    public long getEstimatedTime() {
        return (this.urlCount==0)?0:((this.importStartSize*getElapsedTime())/(this.urlCount))-getElapsedTime();
    }

    public String getJobName() {
        return this.importPath.toString();
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

    public void init(File theImportPath, int theCacheSize) {
        super.init(theImportPath);
        this.cacheSize = theCacheSize;
        
        File noticeUrlDbFile = new File(this.importPath,"urlNotice1.db");
        File profileDbFile = new File(this.importPath, "crawlProfiles0.db");
        
        String errorMsg = null;
        if (!this.importPath.exists()) 
            errorMsg = "The import path '" + this.importPath + "' does not exist.";
        else if (!this.importPath.isDirectory()) 
            errorMsg = "The import path '" + this.importPath + "' is not a directory.";
        else if (!this.importPath.canRead()) 
            errorMsg = "The import path '" + this.importPath + "' is not readable.";
        else if (!this.importPath.canWrite()) 
            errorMsg = "The import path '" + this.importPath + "' is not writeable.";
        
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
        this.importNurlDB =  new plasmaCrawlNURL(this.importPath, this.cacheSize*(3/4));
        this.importStartSize = this.importNurlDB.size();
        //int stackSize = this.importNurlDB.stackSize();
        
        // init profile DB
        this.log.logInfo("Initializing the source profileDB");
        this.importProfileDB = new plasmaCrawlProfile(profileDbFile,this.cacheSize*(1/3));
    }

    public void run() {
        try {   
            // waiting on init thread to finish
            this.importNurlDB.waitOnInitThread();
            
            // the stack types we want to import
            int[] stackTypes = new int[] {plasmaCrawlNURL.STACK_TYPE_CORE,
                                          plasmaCrawlNURL.STACK_TYPE_LIMIT,
                                          plasmaCrawlNURL.STACK_TYPE_REMOTE,
                                          -1};
            
            // looping through the various stacks
            for (int i=0; i< stackTypes.length; i++) {
                if (stackTypes[i] != -1) {
                    this.log.logInfo("Starting to import stacktype '" + stackTypes[i] + "' containing '" + this.importNurlDB.stackSize(stackTypes[i]) + "' entries.");
                } else {
                    this.log.logInfo("Starting to import '" + this.importNurlDB.size() + "' entries not available in any stack.");
                }
                
                // getting an interator and loop through the URL entries
                Iterator iter = (stackTypes[i] == -1)?this.importNurlDB.urlHashes("------------", true):null;
                while (true) {
                    
                    String nextHash = null;
                    Entry urlEntry = null;
                    
                    try {                        
                        if (stackTypes[i] != -1) {
                            if (this.importNurlDB.stackSize(stackTypes[i]) == 0) break;
                            
                            this.urlCount++;
                            urlEntry = this.importNurlDB.pop(stackTypes[i]);
                            nextHash = urlEntry.hash();
                        } else {
                            if (!iter.hasNext()) break;
                            
                            this.urlCount++;
                            nextHash = (String)iter.next();                            
                            urlEntry = this.importNurlDB.getEntry(nextHash);                
                        }
                    } catch (IOException e) {
                        this.log.logWarning("Unable to import entry: " + e.toString());
                        
                        if ((stackTypes[i] != -1) &&(this.importNurlDB.stackSize(stackTypes[i]) == 0)) break;
                        continue;
                    }
                    
                    // getting a handler to the crawling profile the url belongs to
                    try {
                        String profileHandle = urlEntry.profileHandle();
                        if (profileHandle == null) {
                            this.log.logWarning("Profile handle of url entry '" + nextHash + "' unknown.");
                            continue;
                        }
                        
                        // if we havn't imported the profile until yet we need to do it now
                        if (!this.importProfileHandleCache.contains(profileHandle)) {
                            
                            // testing if the profile is already known
                            plasmaCrawlProfile.entry profileEntry = this.sb.profiles.getEntry(profileHandle);
                            
                            // if not we need to import it
                            if (profileEntry == null) {
                                // copy and store the source profile entry into the destination db
                                plasmaCrawlProfile.entry sourceEntry = this.importProfileDB.getEntry(profileHandle);
                                if (sourceEntry != null) {
                                    this.profileCount++;
                                    this.importProfileHandleCache.add(profileHandle);
                                    this.sb.profiles.newEntry((TreeMap)((TreeMap)sourceEntry.map()).clone());
                                } else {
                                    this.log.logWarning("Profile '" + profileHandle + "' of url entry '" + nextHash + "' unknown.");
                                    continue;
                                }
                            }                        
                        }
                        
                        // if the url does not alredy exists in the destination stack we insert it now
                        if (!this.sb.urlPool.noticeURL.existsInStack(nextHash)) {
                            this.sb.urlPool.noticeURL.newEntry(urlEntry,(stackTypes[i] != -1)?stackTypes[i]:plasmaCrawlNURL.STACK_TYPE_CORE);
                        }
                        
                        // removing hash from the import db
                    } finally {
                        this.importNurlDB.remove(nextHash);
                    }
                    
                    if (this.urlCount % 100 == 0) {
                        this.log.logFine(this.urlCount + " URLs and '" + this.profileCount + "' profile entries processed so far.");
                    }                 
                    if (this.isAborted()) break; 
                }
                this.log.logInfo("Finished to import stacktype '" + stackTypes[i] + "'");
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
