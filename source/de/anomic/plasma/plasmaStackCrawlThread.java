package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import de.anomic.data.robotsParser;
import de.anomic.kelondro.kelondroTree;
import de.anomic.kelondro.kelondroRecords.Node;
import de.anomic.server.serverCodings;
import de.anomic.server.serverSemaphore;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;
import de.anomic.yacy.yacyCore;

public final class plasmaStackCrawlThread extends Thread {
    
    private final serverLog log = new serverLog("STACKCRAWL");
    private final plasmaSwitchboard sb;
    private boolean stopped = false;
    private stackCrawlQueue queue;
    
    public plasmaStackCrawlThread(plasmaSwitchboard sb, File dbPath, int dbCacheSize) throws IOException {
        this.sb = sb;
        this.setName(this.getClass().getName());
        
        this.queue = new stackCrawlQueue(dbPath,dbCacheSize);
        this.log.logInfo(this.queue.size() + " entries in the stackCrawl queue.");
        this.log.logInfo("STACKCRAWL thread initialized.");
    }
    
    public void stopIt() {
        this.stopped = true;
        this.interrupt();
        try {
			this.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public int getQueueSize() {
        return this.queue.size();
    }
    
    public void run() {
        while ((!this.stopped) && (!Thread.currentThread().isInterrupted())) {
            
            try {
                // getting a new message from the crawler queue
                stackCrawlMessage theMsg = this.queue.waitForMessage();
                
                // process message
                String rejectReason = stackCrawlDequeue(theMsg);
                
                if (rejectReason != null) {
                    this.sb.urlPool.errorURL.newEntry(
                            new URL(theMsg.url()), 
                            theMsg.referrerHash(), 
                            theMsg.initiatorHash(), 
                            yacyCore.seedDB.mySeed.hash,
                            theMsg.name, 
                            rejectReason, 
                            new bitfield(plasmaURL.urlFlagLength), 
                            false
                    );
                }                
                
            } catch (InterruptedException e) {
                Thread.interrupted();
                this.stopped = true;
            }
            catch (Exception e) {
                this.log.logSevere("plasmaStackCrawlThread.run/loop", e);
            }                        
        }
        
        try {
            this.log.logFine("Shutdown. Closing stackCrawl queue.");
			this.queue.close();
		} catch (IOException e) {
			this.log.logSevere("DB could not be closed properly.", e);
		}
        this.log.logInfo("Shutdown finished.");
    }
    
    
    public void stackCrawlEnqueue(
            String nexturlString, 
            String referrerString, 
            String initiatorHash, 
            String name, 
            Date loadDate, 
            int currentdepth, 
            plasmaCrawlProfile.entry profile) throws MalformedURLException {
        try {            
            this.queue.addMessage(new stackCrawlMessage(
                    initiatorHash,
                    nexturlString,
                    referrerString,
                    name,
                    loadDate,
                    profile.handle(),
                    currentdepth,
                    0,
                    0
                    ));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public String stackCrawlDequeue(stackCrawlMessage theMsg) throws InterruptedException {
        
        plasmaCrawlProfile.entry profile = this.sb.profiles.getEntry(theMsg.profileHandle());
        if (profile == null) {
            String errorMsg = "LOST PROFILE HANDLE '" + theMsg.profileHandle() + "' (must be internal error) for URL " + theMsg.url();
            this.log.logSevere(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        return stackCrawl(
                theMsg.url().toString(),
                theMsg.referrerHash(),
                theMsg.initiatorHash(),
                theMsg.name(),
                theMsg.loaddate(),
                theMsg.depth(),
                profile);
    }
    
    public String stackCrawl(String nexturlString, String referrerString, String initiatorHash, String name, Date loadDate, int currentdepth, plasmaCrawlProfile.entry profile) {
        // stacks a crawl item. The position can also be remote
        // returns null if successful, a reason string if not successful
        
        String reason = null; // failure reason
        
        // strange errors
        if (nexturlString == null) {
            reason = "denied_(url_null)";
            this.log.logSevere("Wrong URL in stackCrawl: url=null");
            return reason;
        }
        /*
         if (profile == null) {
         reason = "denied_(profile_null)";
         log.logError("Wrong Profile for stackCrawl: profile=null");
         return reason;
         }
         */
        URL nexturl = null;
        if ((initiatorHash == null) || (initiatorHash.length() == 0)) initiatorHash = plasmaURL.dummyHash;
        String referrerHash = plasmaURL.urlHash(referrerString);
        try {
            nexturl = new URL(nexturlString);
        } catch (MalformedURLException e) {
            reason = "denied_(url_'" + nexturlString + "'_wrong)";
            this.log.logSevere("Wrong URL in stackCrawl: " + nexturlString);
            return reason;
        }
        
        // check if ip is local ip address
        try {
            InetAddress hostAddress = InetAddress.getByName(nexturl.getHost());
            if (hostAddress.isSiteLocalAddress()) {
                reason = "denied_(private_ip_address)";
                this.log.logFine("Host in URL '" + nexturlString + "' has private ip address.");
                return reason;                
            } else if (hostAddress.isLoopbackAddress()) {
                reason = "denied_(loopback_ip_address)";
                this.log.logFine("Host in URL '" + nexturlString + "' has loopback ip address.");
                return reason;                  
            }
        } catch (UnknownHostException e) {
            reason = "denied_(unknown_host)";
            this.log.logFine("Unknown host in URL '" + nexturlString + "'.");
            return reason;
        }
        
        // check blacklist
        String hostlow = nexturl.getHost().toLowerCase();
        if (plasmaSwitchboard.urlBlacklist.isListed(hostlow, nexturl.getPath())) {
            reason = "denied_(url_in_blacklist)";
            this.log.logFine("URL '" + nexturlString + "' is in blacklist.");
            return reason;
        }        
        
        // filter deny
        if ((currentdepth > 0) && (profile != null) && (!(nexturlString.matches(profile.generalFilter())))) {
            reason = "denied_(does_not_match_filter)";
            /*
             urlPool.errorURL.newEntry(nexturl, referrerHash, initiatorHash, yacyCore.seedDB.mySeed.hash,
             name, reason, new bitfield(plasmaURL.urlFlagLength), false);*/
            this.log.logFine("URL '" + nexturlString + "' does not match crawling filter '" + profile.generalFilter() + "'.");
            return reason;
        }
        
        // deny cgi
        if (plasmaHTCache.isCGI(nexturlString))  {
            reason = "denied_(cgi_url)";
            /*
             urlPool.errorURL.newEntry(nexturl, referrerHash, initiatorHash, yacyCore.seedDB.mySeed.hash,
             name, reason, new bitfield(plasmaURL.urlFlagLength), false);*/
            this.log.logFine("URL '" + nexturlString + "' is cgi URL.");
            return reason;
        }
        
        // deny post properties
        if ((plasmaHTCache.isPOST(nexturlString)) && (profile != null) && (!(profile.crawlingQ())))  {
            reason = "denied_(post_url)";
            /*
             urlPool.errorURL.newEntry(nexturl, referrerHash, initiatorHash, yacyCore.seedDB.mySeed.hash,
             name, reason, new bitfield(plasmaURL.urlFlagLength), false);*/
            this.log.logFine("URL '" + nexturlString + "' is post URL.");
            return reason;
        }
        
        String nexturlhash = plasmaURL.urlHash(nexturl);
        String dbocc = "";
        if ((dbocc = this.sb.urlPool.exists(nexturlhash)) != null) {
            // DISTIGUISH OLD/RE-SEARCH CASES HERE!
            reason = "double_(registered_in_" + dbocc + ")";
            /*
             urlPool.errorURL.newEntry(nexturl, referrerHash, initiatorHash, yacyCore.seedDB.mySeed.hash,
             name, reason, new bitfield(plasmaURL.urlFlagLength), false);*/
            this.log.logFine("URL '" + nexturlString + "' is double registered in '" + dbocc + "'.");
            return reason;
        }
        
        // checking robots.txt
        if (robotsParser.isDisallowed(nexturl)) {
            reason = "denied_(robots.txt)";
            /*
             urlPool.errorURL.newEntry(nexturl, referrerHash, initiatorHash, yacyCore.seedDB.mySeed.hash,
             name, reason, new bitfield(plasmaURL.urlFlagLength), false);*/
            this.log.logFine("Crawling of URL '" + nexturlString + "' disallowed by robots.txt.");
            return reason;            
        }
        
        // store information
        boolean local = ((initiatorHash.equals(plasmaURL.dummyHash)) || (initiatorHash.equals(yacyCore.seedDB.mySeed.hash)));
        boolean global = 
            (profile != null) &&
            (profile.remoteIndexing()) /* granted */ &&
            (currentdepth == profile.generalDepth()) /* leaf node */ && 
            (initiatorHash.equals(yacyCore.seedDB.mySeed.hash)) /* not proxy */ &&
            ((yacyCore.seedDB.mySeed.isSenior()) ||
                    (yacyCore.seedDB.mySeed.isPrincipal())) /* qualified */;
        
        if ((!local)&&(!global)) {
            this.log.logFine("URL '" + nexturlString + "' can neither be crawled local nor global.");
        }
        
        this.sb.urlPool.noticeURL.newEntry(initiatorHash, /* initiator, needed for p2p-feedback */
                nexturl, /* url clear text string */
                loadDate, /* load date */
                referrerHash, /* last url in crawling queue */
                name, /* the anchor name */
                (profile == null) ? null : profile.handle(),  // profile must not be null!
                currentdepth, /*depth so far*/
                0, /*anchors, default value */
                0, /*forkfactor, default value */
                ((global) ? plasmaCrawlNURL.STACK_TYPE_LIMIT :
                ((local) ? plasmaCrawlNURL.STACK_TYPE_CORE : plasmaCrawlNURL.STACK_TYPE_REMOTE)) /*local/remote stack*/
        );
        
        return null;
    }
    
    public final class stackCrawlMessage {
        private String   initiator;     // the initiator hash, is NULL or "" if it is the own proxy;
        String   urlHash;          // the url's hash
        private String   referrerHash;      // the url's referrer hash
        private String   url;           // the url as string
        String   name;          // the name of the url, from anchor tag <a>name</a>     
        private Date     loaddate;      // the time when the url was first time appeared
        private String   profileHandle; // the name of the prefetch profile
        private int      depth;         // the prefetch depth so far, starts at 0
        private int      anchors;       // number of anchors of the parent
        private int      forkfactor;    // sum of anchors of all ancestors
        private bitfield flags;
        private int      handle;
        
        // loadParallel(URL url, String referer, String initiator, int depth, plasmaCrawlProfile.entry profile) {
        public stackCrawlMessage(
                String initiator, 
                String urlString, 
                String referrerUrlString, 
                String name, 
                Date loaddate, 
                String profileHandle,
                int depth, 
                int anchors, 
                int forkfactor) {
            try {
                // create new entry and store it into database
                this.urlHash       = plasmaURL.urlHash(urlString);
                this.initiator     = initiator;
                this.url           = urlString;
                this.referrerHash  = (referrerUrlString == null) ? plasmaURL.dummyHash : plasmaURL.urlHash(referrerUrlString);
                this.name          = (name == null) ? "" : name;
                this.loaddate      = (loaddate == null) ? new Date() : loaddate;
                this.profileHandle = profileHandle; // must not be null
                this.depth         = depth;
                this.anchors       = anchors;
                this.forkfactor    = forkfactor;
                this.flags         = new bitfield(plasmaURL.urlFlagLength);
                this.handle        = 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } 
        
        public stackCrawlMessage(String urlHash, byte[][] entryBytes) {
            if (urlHash == null) throw new NullPointerException();
            if (entryBytes == null) throw new NullPointerException();

            try {
                this.urlHash       = urlHash;
                this.initiator     = new String(entryBytes[1]);
                this.url           = new String(entryBytes[2]).trim();
                this.referrerHash      = (entryBytes[3]==null) ? plasmaURL.dummyHash : new String(entryBytes[3]);
                this.name          = (entryBytes[4] == null) ? "" : new String(entryBytes[4]).trim();
                this.loaddate      = new Date(86400000 * serverCodings.enhancedCoder.decodeBase64Long(new String(entryBytes[5])));
                this.profileHandle = (entryBytes[6] == null) ? null : new String(entryBytes[6]).trim();
                this.depth         = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entryBytes[7]));
                this.anchors       = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entryBytes[8]));
                this.forkfactor    = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entryBytes[9]));
                this.flags         = new bitfield(entryBytes[10]);
                this.handle        = Integer.parseInt(new String(entryBytes[11]));
            } catch (Exception e) {
                e.printStackTrace();
                throw new IllegalStateException();
            }
        }
        
        public String url() {
            return this.url;
        }        
        
        public String referrerHash() {
            return this.referrerHash;
        }
        
        public String initiatorHash() {
            if (this.initiator == null) return null;
            if (this.initiator.length() == 0) return null; 
            return this.initiator;
        }
        
        public Date loaddate() {
            return this.loaddate;
        }

        public String name() {
            return this.name;
        }

        public int depth() {
            return this.depth;
        }

        public String profileHandle() {
            return this.profileHandle;
        }        
        
        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append("urlHash: ").append(urlHash==null ? "null" : urlHash).append(" | ")
               .append("initiator: ").append(initiator==null?"null":initiator).append(" | ")
               .append("url: ").append(url==null?"null":url).append(" | ")
               .append("referrer: ").append((referrerHash == null) ? plasmaURL.dummyHash : referrerHash).append(" | ")
               .append("name: ").append((name == null) ? "null" : name).append(" | ")
               .append("loaddate: ").append((loaddate == null) ? new Date() : loaddate).append(" | ")
               .append("profile: ").append(profileHandle==null?"null":profileHandle).append(" | ")
               .append("depth: ").append(Integer.toString(depth)).append(" | ")
               .append("forkfactor: ").append(Integer.toString(forkfactor)).append(" | ")
               .append("flags: ").append((flags==null) ? "null" : flags.toString());
               return str.toString();
        }                      
        
        public byte[][] getBytes() {
            // stores the values from the object variables into the database
            String loaddatestr = serverCodings.enhancedCoder.encodeBase64Long(loaddate.getTime() / 86400000, plasmaURL.urlDateLength);
            // store the hash in the hash cache

            // even if the entry exists, we simply overwrite it
            byte[][] entry = new byte[][] { 
                    this.urlHash.getBytes(),
                    (this.initiator == null) ? "".getBytes() : this.initiator.getBytes(),
                    this.url.getBytes(),
                    this.referrerHash.getBytes(),
                    this.name.getBytes(),
                    loaddatestr.getBytes(),
                    (this.profileHandle == null) ? null : this.profileHandle.getBytes(),
                    serverCodings.enhancedCoder.encodeBase64Long(this.depth, plasmaURL.urlCrawlDepthLength).getBytes(),
                    serverCodings.enhancedCoder.encodeBase64Long(this.anchors, plasmaURL.urlParentBranchesLength).getBytes(),
                    serverCodings.enhancedCoder.encodeBase64Long(this.forkfactor, plasmaURL.urlForkFactorLength).getBytes(),
                    this.flags.getBytes(),
                    normalizeHandle(this.handle).getBytes()
            };
            return entry;
        }        
        
        private String normalizeHandle(int h) {
            String d = Integer.toHexString(h);
            while (d.length() < plasmaURL.urlHandleLength) d = "0" + d;
            return d;
        }
    }      
    
    final class stackCrawlQueue {
        
        private final serverSemaphore readSync;
        private final serverSemaphore writeSync;
        private final LinkedList urlEntryHashCache;
        private final kelondroTree urlEntryCache;
        
        public stackCrawlQueue(File cacheStacksPath, int bufferkb) throws IOException  {
            // init the read semaphore
            this.readSync  = new serverSemaphore (0);
            
            // init the write semaphore
            this.writeSync = new serverSemaphore (1);
            
            // init the message list
            this.urlEntryHashCache = new LinkedList();
            
            // create a stack for newly entered entries
            if (!(cacheStacksPath.exists())) cacheStacksPath.mkdir(); // make the path

            File cacheFile = new File(cacheStacksPath, "urlPreNotice.db");
            if (cacheFile.exists()) {
                // open existing cache
                this.urlEntryCache = new kelondroTree(cacheFile, bufferkb * 0x400);
                
                // loop through the list and fill the messageList with url hashs
                Iterator iter = this.urlEntryCache.nodeIterator(true,false);
                Node n;
                while (iter.hasNext()) {
                    n = (Node) iter.next();
                    if (n == null) {
                        System.out.println("ERROR! null element found");
                        continue;
                    }
                    String urlHash = new String(n.getKey());
                    this.urlEntryHashCache.add(urlHash);
                    this.readSync.V();
                }
            } else {
                // create new cache
                cacheFile.getParentFile().mkdirs();
                this.urlEntryCache = new kelondroTree(cacheFile, bufferkb * 0x400, plasmaCrawlNURL.ce);
            }            
        }
        
        public void close() throws IOException {
            // closing the db
            this.urlEntryCache.close();
            
            // clearing the hash list
            this.urlEntryHashCache.clear();            
        }

        public void addMessage(stackCrawlMessage newMessage) 
        throws InterruptedException, IOException {
            if (newMessage == null) throw new NullPointerException();
            
            this.writeSync.P();
            try {
                
                boolean insertionDoneSuccessfully = false;
                synchronized(this.urlEntryHashCache) {                    
                    byte[][] oldValue = this.urlEntryCache.put(newMessage.getBytes());                        
                    if (oldValue == null) {
                        insertionDoneSuccessfully = this.urlEntryHashCache.add(newMessage.urlHash);
                    }
                }
                
                if (insertionDoneSuccessfully)  {
                    this.readSync.V();              
                }
            } finally {
                this.writeSync.V();
            }
        }
        
        public int size() {
            synchronized(this.urlEntryHashCache) {
                return this.urlEntryHashCache.size();
            }         
        }
        
        public stackCrawlMessage waitForMessage() throws InterruptedException, IOException {
            this.readSync.P();         
            this.writeSync.P();
            
            String urlHash = null;
            byte[][] entryBytes = null;
            stackCrawlMessage newMessage = null;
            synchronized(this.urlEntryHashCache) {               
                 urlHash = (String) this.urlEntryHashCache.remove();
                 entryBytes = this.urlEntryCache.remove(urlHash.getBytes());                 
            }
            
            this.writeSync.V();
            
            newMessage = new stackCrawlMessage(urlHash,entryBytes);
            return newMessage;
        }
    }    
    
}
