// yacyCore.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 03.12.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
  the yacy process of getting in touch of other peers starts as follows:
  - init seed cache. It is needed to determine the right peer for the Hello-Process
  - create a own seed. This can be a new one or one loaded from a file
  - The httpd must start up then first
  - the own seed is completed by performing the 'yacyHello' process. This
    process will result in a request back to the own peer to check if it runs
    in server mode. This is the reason that the httpd must be started in advance.

*/

// contributions:
// principal peer status via file generation by Alexander Schier [AS]


package de.anomic.yacy;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import de.anomic.http.httpc;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverSemaphore;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;

public class yacyCore {
    
    // statics
    public static ThreadGroup publishThreadGroup = new ThreadGroup("publishThreadGroup");
    public static long startupTime = System.currentTimeMillis();
    public static yacySeedDB seedDB = null;
    public static yacyNewsPool newsPool = null;
    public static final Hashtable seedUploadMethods = new Hashtable();
    public static yacyPeerActions peerActions = null;
    public static yacyDHTAction dhtAgent = null;
    public static serverLog log;
    public static long lastOnlineTime = 0;
    public static float latestVersion = (float) 0.1;
    public static long speedKey = 0;
    public static File yacyDBPath;
    
    //public static yacyShare shareManager = null;
    //public static boolean terminate = false;

    // class variables
    private int        lastSeedUpload_seedDBSize = 0;
    public long lastSeedUpload_timeStamp = System.currentTimeMillis();
    private String lastSeedUpload_myPeerType = "";    
    private String lastSeedUpload_myIP = "";
    
    
    private int onlineMode = 1;
    private plasmaSwitchboard switchboard;
    
    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("America/Los_Angeles");
    public static String universalDateShortPattern = "yyyyMMddHHmmss";
    public static SimpleDateFormat shortFormatter = new SimpleDateFormat(universalDateShortPattern);
    
    public static long universalTime() {
	return universalDate().getTime();
    }

    public static Date universalDate() {
	return new GregorianCalendar(GMTTimeZone).getTime();
    }

    public static String universalDateShortString() {
	return universalDateShortString(universalDate());
    }

    public static String universalDateShortString(Date date) {
	return shortFormatter.format(date);
    }

    public static Date parseUniversalDate(String remoteTimeString) {
        if ((remoteTimeString == null) || (remoteTimeString.length() == 0)) return new Date();
        try {
            return yacyCore.shortFormatter.parse(remoteTimeString);
        } catch (java.text.ParseException e) {
            return new Date();
        }
    }
    
    public static int yacyTime() {
        // the time since startup of yacy in seconds
        return (int) ((System.currentTimeMillis() - startupTime) / 1000);
    }
        
    public yacyCore(plasmaSwitchboard sb) throws IOException {
        long time = System.currentTimeMillis();
        
        this.switchboard = sb;
        switchboard.setConfig("yacyStatus","");
        
        // set log level
        log = new serverLog("YACY");
        
        // create a yacy db
        yacyDBPath = new File(sb.getRootPath(), sb.getConfig("yacyDB", "DATA/YACYDB"));
        if (!(yacyDBPath.exists())) yacyDBPath.mkdir();
        
        // read memory amount
        int mem = Integer.parseInt(switchboard.getConfig("ramCacheDHT", "1024")) / 1024;
        log.logSystem("DHT Cache memory = " + mem + " KB");
        
        // create or init seed cache
        seedDB = new yacySeedDB(
                sb, 
                new File(yacyDBPath, "seed.new.db"),
                new File(yacyDBPath, "seed.old.db"),
                new File(yacyDBPath, "seed.pot.db"),
                mem);
        
        // create or init news database
        newsPool = new yacyNewsPool(yacyDBPath, 1024);
        
        loadSeedUploadMethods();
        
        // deploy peer actions
        peerActions = new yacyPeerActions(seedDB, switchboard,
                new File(sb.getRootPath(), sb.getConfig("superseedFile", "superseed.txt")),
                switchboard.getConfig("superseedLocation", "http://www.yacy.net/yacy/superseed.txt"));
        dhtAgent = new yacyDHTAction(seedDB);
        peerActions.deploy(dhtAgent);
        peerActions.deploy(new yacyNewsAction(newsPool));
        
        // create or init index sharing
        //shareManager = new yacyShare(switchboard);
        
        lastSeedUpload_seedDBSize = seedDB.sizeConnected();
        
        log.logSystem("CORE INITIALIZED");
        // ATTENTION, VERY IMPORTANT: before starting the thread, the httpd yacy server must be running!
        
        speedKey = System.currentTimeMillis() - time;
        
        // start with a seedList update to propagate out peer, if possible
        onlineMode = Integer.parseInt(switchboard.getConfig("onlineMode", "1"));
        //lastSeedUpdate = universalTime();
        lastOnlineTime = 0;
        
        // cycle
        // within cycle: update seed file, strengthen network, pass news (new, old seed's)
        if (online())
            log.logSystem("you are in online mode");
        else {
            log.logSystem("YOU ARE OFFLINE! ---");
            log.logSystem("--- TO START BOOTSTRAPING, YOU MUST USE THE PROXY,");
            log.logSystem("--- OR HIT THE BUTTON 'go online'");
            log.logSystem("--- ON THE STATUS PAGE http://localhost:" + switchboard.getConfig("port", "8080") + "/Status.html");
        }
    }

    
    synchronized static public void triggerOnlineAction() {
	lastOnlineTime = universalTime();
    }

    public boolean online() {
	this.onlineMode = Integer.parseInt(switchboard.getConfig("onlineMode", "1"));
	return ((onlineMode == 2) || ((universalTime() - lastOnlineTime) < 10000));
    }

    public void loadSeeds() {
        //new Thread(new vprobe()).start(); 
        peerActions.loadSeedLists(); // start to bootstrap the network here
        publishSeedList();
    }
    
    public void publishSeedList() {
        
        log.logDebug("yacyCore.publishSeedList: Triggered Seed Publish");
        
        /*
        if (oldIPStamp.equals((String) seedDB.mySeed.get("IP", "127.0.0.1")))
            yacyCore.log.logDebug("***DEBUG publishSeedList: oldIP is equal");
        if (seedCacheSizeStamp == seedDB.sizeConnected())
            yacyCore.log.logDebug("***DEBUG publishSeedList: sizeConnected is equal");
        if (canReachMyself())
            yacyCore.log.logDebug("***DEBUG publishSeedList: I can reach myself");
        */
        
        if (
                (this.lastSeedUpload_myIP.equals(seedDB.mySeed.get("IP", "127.0.0.1"))) &&
                (this.lastSeedUpload_seedDBSize == seedDB.sizeConnected()) &&
                (canReachMyself()) &&
                (System.currentTimeMillis() - this.lastSeedUpload_timeStamp < 1000*60*60*24) &&
                (seedDB.mySeed.isPrincipal())
        ) {
            log.logDebug("yacyCore.publishSeedList: not necessary to publish: oldIP is equal, sizeConnected is equal and I can reach myself under the old IP.");
            return;
        }
        
        // getting the seed upload method that should be used ...
        String seedUploadMethod = this.switchboard.getConfig("seedUploadMethod","");
        
        if (
                (!seedUploadMethod.equalsIgnoreCase("none")) || 
                ((seedUploadMethod.equals("")) && (this.switchboard.getConfig("seedFTPPassword","").length() > 0)) ||
                ((seedUploadMethod.equals("")) && (this.switchboard.getConfig("seedFilePath", "").length() > 0))
        ) {
            if (seedUploadMethod.equals("")) {
                if (this.switchboard.getConfig("seedFTPPassword","").length() > 0)
                    this.switchboard.setConfig("seedUploadMethod","Ftp");
                if (this.switchboard.getConfig("seedFilePath","").length() > 0)
                    this.switchboard.setConfig("seedUploadMethod","File");                
            }
            // we want to be a principal...
            saveSeedList();            
        } else {
            if (seedUploadMethod.equals("")) this.switchboard.setConfig("seedUploadMethod","none");
            log.logDebug("yacyCore.publishSeedList: No uploading method configured");
            return;
        }
        
            
    }
    
    public void peerPing() {
        if (!(online())) return;
        
        // before publishing, update some seed data
        peerActions.updateMySeed();
        
        
        // publish own seed to other peer, this can every peer, but makes only sense for senior peers
        int oldSize = seedDB.sizeConnected();
        if (oldSize == 0) {
            // reload the seed lists
            peerActions.loadSeedLists();
            log.logInfo("re-initialized seed list. received " + seedDB.sizeConnected() + " new peer(s)");
        }
        int newSeeds = publishMySeed(false);
        if (newSeeds > 0) log.logInfo("received " + newSeeds + " new peer(s), know a total of " +
        seedDB.sizeConnected() + " different peers");
    }
    
    private boolean canReachMyself() {
        // returns true if we can reach ourself under our known peer address
        // if we cannot reach ourself, we call a forced publishMySeed and return false
        int urlc = yacyClient.queryUrlCount(seedDB.mySeed);
        if (urlc >= 0) {
            seedDB.mySeed.put("LastSeen", universalDateShortString());
            return true;
        }
        log.logInfo("re-connect own seed");
        String oldAddress = seedDB.mySeed.getAddress();
        int newSeeds = publishMySeed(true);
        return ((oldAddress != null) && (oldAddress.equals(seedDB.mySeed.getAddress())));
    }
    
    
    protected class publishThread extends Thread {

        public int added;
        public yacySeed seed;
        public Exception error;
        private final serverSemaphore sync;
        private final List syncList;
        
        public publishThread(ThreadGroup tg, yacySeed seed, serverSemaphore sync, List syncList) throws InterruptedException {
            super(tg, "PublishSeed_" + seed.getName());
            
            this.sync = sync;
            this.sync.P();            
            this.syncList = syncList;
            
            this.seed = seed;
            this.added = 0;
            this.error = null;
        }

        public void run() {
            try {                
                this.added = yacyClient.publishMySeed(seed.getAddress(), seed.hash);
                if (this.added < 0) {
                    // no or wrong response, delete that address
                    log.logInfo("publish: disconnected " + this.seed.get("PeerType", "senior") + " peer '" + this.seed.getName() + "' from " + this.seed.getAddress());
                    peerActions.peerDeparture(this.seed);
                } else {
                    // success! we have published our peer to a senior peer
                    // update latest news from the other peer
                    log.logInfo("publish: handshaked " + this.seed.get("PeerType", "senior") + " peer '" + this.seed.getName() + "' at " + this.seed.getAddress());
                }
            } catch (Exception e) {
                log.logError("publishThread: error with target seed " + seed.getMap() + ": " + e.getMessage(), e);
                this.error = e;
            } finally {
                this.syncList.add(this);
                this.sync.V();
            }
        }
        
    }
    
    private int publishMySeed(boolean force) {
        try {
            // call this after the httpd was started up
            
            // we need to find out our own ip
            // This is not always easy, since the application may
            // live behind a firewall or nat.
            // the normal way to do this is either measure the value that java gives us,
            // but this is not correct if the peer lives behind a NAT/Router or has several
            // addresses and not the right one can be found out.
            // We have several alternatives:
            // 1. ask another peer. This should be normal and the default method.
            //    but if no other peer lives, or we don't know them, we cannot do that
            // 2. ask own NAT. This is only an option if the NAT is a DI604, because this is the
            //    only supported for address retrieval
            // 3. ask ip respond services in the internet. There are several, and they are all
            //    probed until we get a valid response.
            
            // init yacyHello-process
            yacySeed[] seeds;
            
            int attempts = seedDB.sizeConnected(); 
            if (attempts > 10) attempts = 10;
            
            // getting a list of peers to contact
            if (seedDB.mySeed.get("PeerType", "virgin").equals("virgin")) {
                seeds = seedDB.seedsByAge(true, attempts); // best for fast connection
            } else {
                seeds = seedDB.seedsByAge(false, attempts); // best for seed list maintenance/cleaning
            }
            if (seeds == null) return 0;
            
            // include a YaCyNews record to my seed
            try {
                yacyNewsRecord record = newsPool.myPublication();
                if (record == null) 
                    seedDB.mySeed.put("news", "");
                else
                    seedDB.mySeed.put("news", de.anomic.tools.crypt.simpleEncode(record.toString()));
            } catch (IOException e) {
                log.logError("publishMySeed: problem with news encoding", e);
            }
            
            // holding a reference to all started threads
            int contactedSeedCount = 0;
            List syncList = Collections.synchronizedList(new LinkedList()); // memory for threads
            serverSemaphore sync = new serverSemaphore(attempts);
            
            // going through the peer list and starting a new publisher thread for each peer
            for (int i = 0; i < seeds.length; i++) {
                if (seeds[i] == null) continue;
                
                String address = seeds[i].getAddress();
                log.logDebug("HELLO #" + i + " to peer '" + seeds[i].get("Name", "") + "' at " + address); // debug            
                if ((address == null) || (seeds[i].isProper() != null)) {
                    // we don't like that address, delete it
                    peerActions.peerDeparture(seeds[i]);
                    sync.V();
                } else {
                    // starting a new publisher thread
                    contactedSeedCount++;
                    (new publishThread(yacyCore.publishThreadGroup,seeds[i],sync,syncList)).start();
                }
            }
            
            // receiving the result of all started publisher threads
            int newSeeds = -1;
            for (int j=0; j < contactedSeedCount; j++) {
                
                // waiting for the next thread to finish
                sync.P();              
                
                // if this is true something is wrong ...
                if (syncList.isEmpty()) return 0;
                
                // getting a reference to the finished thread
                publishThread t = (publishThread) syncList.remove(0);
                
                // getting the amount of new reported seeds
                if (t.added >= 0) {
                    if (newSeeds==-1) newSeeds =  t.added;
                    else           newSeeds += t.added;
                }            
            }
            
            if (newSeeds >= 0) {
                // success! we have published our peer to a senior peer
                // update latest news from the other peer
                //log.logInfo("publish: handshaked " + t.seed.get("PeerType", "senior") + " peer '" + t.seed.getName() + "' at " + t.seed.getAddress());
                peerActions.saveMySeed();
                return newSeeds;
            }        
            
//            // wait
//            try {
//                if (i == 0) Thread.currentThread().sleep(2000); // after the first time wait some seconds
//                Thread.currentThread().sleep(1000 + 500 * v.size()); // wait a while
//            } catch (InterruptedException e) {}
//            
//            // check all threads
//            for (int j = 0; j < v.size(); j++) {
//                t = (publishThread) v.elementAt(j);
//                added = t.added;
//                if (!(t.isAlive())) {
//                    //log.logDebug("PEER " + seeds[j].get("Name", "") + " request terminated"); // debug
//                    if (added >= 0) {
//                        // success! we have published our peer to a senior peer
//                        // update latest news from the other peer
//                        //log.logInfo("publish: handshaked " + t.seed.get("PeerType", "senior") + " peer '" + t.seed.getName() + "' at " + t.seed.getAddress());
//                        peerActions.saveMySeed();
//                        return added;
//                    }
//                }
//            }
            
            // if we have an address, we do nothing
            if ((seedDB.mySeed.isProper() == null) && (!(force))) return 0;
            
            // still no success: ask own NAT or internet responder
            boolean DI604use = switchboard.getConfig("DI604use", "false").equals("true");
            String  DI604pw  = switchboard.getConfig("DI604pw", "");
            String  ip       = switchboard.getConfig("staticIP", "");
            if(ip.equals("")){
                ip = natLib.retrieveIP(DI604use, DI604pw, (switchboard.getConfig("yacyDebugMode", "false")=="false" ? false : true));
            }
            //yacyCore.log.logDebug("DEBUG: new IP=" + ip);
            seedDB.mySeed.put("IP", ip);
            if (seedDB.mySeed.get("PeerType", "junior").equals("junior")) // ???????????????
                seedDB.mySeed.put("PeerType", "senior"); // to start bootstraping, we need to be recognised as "senior" peer
            log.logInfo("publish: no recipient found, asked NAT or responder; our address is " +
                    ((seedDB.mySeed.getAddress() == null) ? "unknown" : seedDB.mySeed.getAddress()));
            peerActions.saveMySeed();
            return 0;
        } catch (InterruptedException e) {
            try {
                log.logInfo("publish: Interruption detected while publishing my seed.");
                
                // consuming the theads interrupted signal
                Thread.interrupted();
                
                // interrupt all already started publishThreads
                log.logInfo("publish: Signaling shutdown to " + yacyCore.publishThreadGroup.activeCount() +  " remaining publishing threads ...");
                yacyCore.publishThreadGroup.interrupt();
                
                // waiting some time for the publishThreads to finish execution
                try { Thread.sleep(500); } catch (InterruptedException ex) {}
                
                // getting the amount of remaining publishing threads
                int threadCount  = yacyCore.publishThreadGroup.activeCount();    
                Thread[] threadList = new Thread[threadCount];     
                threadCount = yacyCore.publishThreadGroup.enumerate(threadList);
                
                // we need to use a timeout here because of missing interruptable session threads ...
                log.logDebug("publish: Trying to abort " + yacyCore.publishThreadGroup.activeCount() +  " remaining publishing threads ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    
                    if (currentThread.isAlive()) {
                        log.logDebug("publish: Closing socket of publishing thread '" + currentThread.getName() + "' [" + currentThreadIdx + "].");
                        httpc.closeOpenSockets(currentThread);
                    }
                }
                
                // we need to use a timeout here because of missing interruptable session threads ...
                log.logDebug("publish: Waiting for " + yacyCore.publishThreadGroup.activeCount() +  " remaining publishing threads to finish shutdown ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    
                    if (currentThread.isAlive()) {
                        log.logDebug("publish: Waiting for remaining publishing thread '" + currentThread.getName() + "' to finish shutdown");
                        try { currentThread.join(500); }catch (InterruptedException ex) {}
                    }
                }       
                
                log.logInfo("publish: Shutdown off all remaining publishing thread finished.");
                
            }
            catch (Exception ee) {
                log.logWarning("publish: Unexpected error while trying to shutdown all remaining publishing threads.",e);  
            }
            
            return 0;
        }
    }
    
    public static Hashtable getSeedUploadMethods() {
        synchronized (yacyCore.seedUploadMethods) {
            return (Hashtable) yacyCore.seedUploadMethods.clone();
        }        
    }
    
    public static yacySeedUploader getSeedUploader(String methodname) {
        String className = null;
        synchronized (yacyCore.seedUploadMethods) {
            if (yacyCore.seedUploadMethods.containsKey(methodname)) {
                className = (String) yacyCore.seedUploadMethods.get(methodname);
            }
        }    
        
        if (className == null) return null;
        try {
            Class uploaderClass = Class.forName(className);
            Object uploader = uploaderClass.newInstance();
            return (yacySeedUploader) uploader;
        } catch (Exception e) {
            return null;
        }
    }
    
    public static void loadSeedUploadMethods() {
        Hashtable availableUploaders = new Hashtable();
        
        try {
            String uploadersPkgName = yacyCore.class.getPackage().getName() + ".seedUpload";
            String packageURI = yacyCore.class.getResource("/"+uploadersPkgName.replace('.','/')).toString(); 
            
            // open the parser directory
            File uploadersDir = new File(new URI(packageURI));
            if ((uploadersDir == null) || (!uploadersDir.exists()) || (!uploadersDir.isDirectory())) {
                yacyCore.seedUploadMethods.clear(); 
                changeSeedUploadMethod("none");
            }
            
            String[] uploaderClasses = uploadersDir.list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("yacySeedUpload") && name.endsWith(".class");
                }});
            
            String javaClassPath = System.getProperty("java.class.path");
            
            if (uploaderClasses == null) return;
            for (int uploaderNr=0; uploaderNr<uploaderClasses.length; uploaderNr++) {
                String className = uploaderClasses[uploaderNr].substring(0,uploaderClasses[uploaderNr].indexOf(".class"));
                String fullClassName = uploadersPkgName + "." + className;
                try {
                    Class uploaderClass = Class.forName(fullClassName);
                    Object theUploader = uploaderClass.newInstance();
                    if (!(theUploader instanceof yacySeedUploader)) continue;
                    String[] neededLibx = ((yacySeedUploader)theUploader).getLibxDependences();
                    if (neededLibx != null) {
                        for (int libxId=0; libxId < neededLibx.length; libxId++) {
                            if (javaClassPath.indexOf(neededLibx[libxId]) == -1) 
                                throw new Exception("Missing dependency");
                        }
                    }
                    availableUploaders.put(className.substring("yacySeedUpload".length()),fullClassName);
                } catch (Exception e) { /* we can ignore this for the moment */                     
                } catch (Error e)     { /* we can ignore this for the moment */ }
            }
        } catch (Exception e) {
            
        } finally {
            synchronized (yacyCore.seedUploadMethods) {
                yacyCore.seedUploadMethods.clear();
                yacyCore.seedUploadMethods.putAll(availableUploaders);
            }
        }
    }

    public static boolean changeSeedUploadMethod(String method) {
        if ((method == null)||(method.length() == 0)) return false;
        
        if (method.equalsIgnoreCase("none")) return true;
        
        synchronized (yacyCore.seedUploadMethods) {
            return yacyCore.seedUploadMethods.containsKey(method);
        }        
    }
    
    public String saveSeedList() {
        // return an error if this is not successful, and NULL if everything is fine
        return saveSeedList(this.switchboard);
    }
    
    public String saveSeedList(serverSwitch sb) {
        try {
            // return an error if this is not successful, and NULL if everything is fine
            String logt;
            
            // be shure that we have something to say
            if (seedDB.mySeed.getAddress() == null) {
                String errorMsg = "We have no valid IP address until now";
                log.logWarning("SaveSeedList: " + errorMsg);
                return errorMsg;
            }
            
            // getting the configured seed uploader
            String seedUploadMethod = sb.getConfig("seedUploadMethod","");
            
            // for backward compatiblity ....
            if (
                    (seedUploadMethod.equalsIgnoreCase("Ftp")) || 
                    ((seedUploadMethod.equals("")) &&
                            (sb.getConfig("seedFTPPassword","").length() > 0))
            ) {
                seedUploadMethod = "Ftp";
                sb.setConfig("seedUploadMethod",seedUploadMethod);
            } else if (
                    (seedUploadMethod.equalsIgnoreCase("File")) ||
                    ((seedUploadMethod.equals("")) &&
                            (sb.getConfig("seedFilePath", "").length() > 0))                
            ) {
                seedUploadMethod = "File";
                sb.setConfig("seedUploadMethod",seedUploadMethod);            
            }
            
            //  determine the seed uploader that should be used ...       
            if (seedUploadMethod.equalsIgnoreCase("none")) return "no uploader specified";
            
            yacySeedUploader uploader = getSeedUploader(seedUploadMethod);
            if (uploader == null) {
                String errorMsg = "Unable to get the proper uploader-class for seed uploading method '" + seedUploadMethod + "'.";
                log.logWarning("SaveSeedList: " + errorMsg);
                return errorMsg;               
            }
            
            // ensure that the seed file url is configured properly
            URL seedURL;
            try{
                String seedURLStr = sb.getConfig("seedURL","");
                if (seedURLStr.length() == 0) throw new MalformedURLException("The seed-file url must not be empty.");
                if (!seedURLStr.toLowerCase().startsWith("http://")) throw new MalformedURLException("Unsupported protocol.");
                seedURL = new URL(seedURLStr);
            }catch(MalformedURLException e){
                String errorMsg = "Malformed seed file URL '" + sb.getConfig("seedURL","") + "'. " + e.getMessage();
                log.logWarning("SaveSeedList: " + errorMsg);            
                return errorMsg;
            }              
            
            // upload the seed-list using the configured uploader class
            String prevStatus = seedDB.mySeed.get("PeerType", "junior");
            if (prevStatus.equals("principal")) prevStatus = "senior";
            
            try {
                seedDB.mySeed.put("PeerType", "principal"); // this information shall also be uploaded
                
                log.logDebug("SaveSeedList: Using seed uploading method '" + seedUploadMethod + "' for seed-list uploading." +
                        "\n\tPrevious peerType is '" + seedDB.mySeed.get("PeerType", "junior") + "'.");
                
                //logt = seedDB.uploadCache(seedFTPServer, seedFTPAccount, seedFTPPassword, seedFTPPath, seedURL);
                logt = seedDB.uploadCache(uploader,sb, seedDB, seedURL);
                if (logt != null) {
                    if (logt.indexOf("Error") >= 0) {
                        seedDB.mySeed.put("PeerType", prevStatus);
                        String errorMsg = "SaveSeedList: seed upload failed using " + uploader.getClass().getName() + " (error): " + logt.substring(logt.indexOf("Error") + 6);
                        log.logError(errorMsg);
                        return errorMsg;
                    }
                    log.logInfo(logt);
                }
                
                // finally, set the principal status
                sb.setConfig("yacyStatus","principal");
                return null;
            } catch (Exception e) {
                seedDB.mySeed.put("PeerType", prevStatus);
                sb.setConfig("yacyStatus", prevStatus);
                String errorMsg = "SaveSeedList: Seed upload failed (IO error): " + e.getMessage();
                log.logInfo(errorMsg,e);
                return errorMsg;
            }
        } finally {
            this.lastSeedUpload_seedDBSize = seedDB.sizeConnected();
            this.lastSeedUpload_timeStamp = System.currentTimeMillis();
            
            this.lastSeedUpload_myIP = seedDB.mySeed.get("IP", "127.0.0.1");  
            this.lastSeedUpload_myPeerType = seedDB.mySeed.get("PeerType", yacySeed.PEERTYPE_JUNIOR);            
        }
    }

}
