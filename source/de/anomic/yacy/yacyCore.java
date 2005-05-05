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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Vector;

import de.anomic.http.httpc;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverLog;
import de.anomic.server.serverSwitch;

public class yacyCore {
    
    // statics
    public static long startupTime = System.currentTimeMillis();
    public static yacySeedDB seedDB = null;
    public static yacyPeerActions peerActions = null;
    public static yacyDHTAction dhtAgent = null;
    public static serverLog log;
    public static long lastOnlineTime = 0;
    public static String latestVersion = "";
    public static long speedKey = 0;
    public static File yacyDBPath;
    
    //public static yacyShare shareManager = null;
    //public static boolean terminate = false;

    // class variables
    private int seedCacheSizeStamp = 0;
    private String oldIPStamp = "";
    private int onlineMode = 1;
    private plasmaSwitchboard switchboard;
    
    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    public static SimpleDateFormat shortFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
    
    public static long universalTime() {
	return universalDate().getTime();
    }

    public static Date universalDate() {
	return new GregorianCalendar(GMTTimeZone).getTime();
    }

    public static String universalDateShortString() {
	return shortFormatter.format(universalDate());
    }

    public static Date parseUniversalDate(String remoteTimeString) {
        if (remoteTimeString == null) return new Date();
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
	log = new serverLog("YACY", Integer.parseInt(switchboard.getConfig("yacyLoglevel", "0")));

	// create a yacy db
	yacyDBPath = new File(sb.getRootPath(), sb.getConfig("yacyDB", "DATA/YACYDB"));
	if (!(yacyDBPath.exists())) yacyDBPath.mkdir();

        // read memory amount
        int mem = Integer.parseInt(switchboard.getConfig("ramCacheSize", "1")) * 0x400 *
                  Integer.parseInt(switchboard.getConfig("ramCachePercentDHT", "1")) / 100;
        log.logSystem("DHT Cache memory = " + mem + " KB");
        
        // create or init seed cache
	seedDB = new yacySeedDB(
                      sb, 
		      new File(yacyDBPath, "seed.new.db"),
                      new File(yacyDBPath, "seed.old.db"),
                      new File(yacyDBPath, "seed.pot.db"),
                      mem);

        peerActions = new yacyPeerActions(seedDB, switchboard,
                                          new File(sb.getRootPath(), sb.getConfig("superseedFile", "superseed.txt")),
                                          switchboard.getConfig("superseedLocation", "http://www.yacy.net/yacy/superseed.txt"));
        dhtAgent = new yacyDHTAction(seedDB);
        peerActions.deploy(dhtAgent);
        
        // create or init index sharing
        //shareManager = new yacyShare(switchboard);
        
	seedCacheSizeStamp = seedDB.sizeConnected();

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
        log.logDebug("triggered Seed Publish");

        // we want to be a principal...
        if ((switchboard.getConfig("seedFTPPassword","").length() == 0) &&
            (switchboard.getConfig("seedFilePath", "").length() == 0)) {
            log.logDebug("yacyCore.publishSeedList: no FTP settings present; password-len=" +
                               switchboard.getConfig("seedFTPPassword","").length() + ", filePath=" +
                               switchboard.getConfig("seedFilePath", ""));
            return;
        }

        /*
        if (oldIPStamp.equals((String) seedDB.mySeed.get("IP", "127.0.0.1")))
            System.out.println("***DEBUG publishSeedList: oldIP is equal");
        if (seedCacheSizeStamp == seedDB.sizeConnected())
            System.out.println("***DEBUG publishSeedList: sizeConnected is equal");
        if (canReachMyself())
            System.out.println("***DEBUG publishSeedList: I can reach myself");
        */
        
        if ((!(oldIPStamp.equals((String) seedDB.mySeed.get("IP", "127.0.0.1")))) ||
            (seedCacheSizeStamp != seedDB.sizeConnected()) ||
            (!(canReachMyself()))) {
            // publish seed-list to ftp account, this can only a principal peer
            saveSeedList();
            seedCacheSizeStamp = seedDB.sizeConnected();
            oldIPStamp = (String) seedDB.mySeed.get("IP", "127.0.0.1");
        } else {
            log.logDebug("not necessary to publish: oldIP is equal, sizeConnected is equal and I can reach myself under the old IP.");
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
            log.logInfo("re-initialized seed list. received " + seedDB.sizeConnected() + " new peers");
        }
        int newSeeds = publishMySeed(false);
        if (newSeeds > 0) log.logInfo("received " + newSeeds + " new peers, know a total of " +
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
        
        public publishThread(yacySeed seed) {
            this.seed = seed;
            this.added = 0;
            this.error = null;
        }

        public void run() {
            try {
                added = yacyClient.publishMySeed(seed.getAddress(), seed.hash);
                if (added < 0) {
                    // no or wrong response, delete that address
                    log.logInfo("publish: disconnected " + seed.get("PeerType", "senior") + " peer '" + seed.getName() + "' from " + seed.getAddress());
                    peerActions.peerDeparture(seed);
                } else {
                    // success! we have published our peer to a senior peer
                    // update latest news from the other peer
                    log.logInfo("publish: handshaked " + seed.get("PeerType", "senior") + " peer '" + seed.getName() + "' at " + seed.getAddress());
                }
            } catch (Exception e) {
                error = e;
            }
        }
        
    }
    
    private int publishMySeed(boolean force) {
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
	String address;
	int added;
        yacySeed[] seeds;
        int attempts = seedDB.sizeConnected(); if (attempts > 10) attempts = 10;
        if (seedDB.mySeed.get("PeerType", "virgin").equals("virgin")) {
            seeds = seedDB.seedsByAge(true, attempts); // best for fast connection
        } else {
            seeds = seedDB.seedsByAge(false, attempts); // best for seed list maintenance/cleaning
        }
        if (seeds == null) return 0;
        Vector v = new Vector(); // memory for threads        
        publishThread t;
	for (int i = 0; i < seeds.length; i++) {
	    if (seeds[i] == null) continue;
	    log.logDebug("HELLO #" + i + " to peer " + seeds[i].get("Name", "")); // debug
            address = seeds[i].getAddress();
	    if ((address == null) || (!(seeds[i].isProper()))) {
		// we don't like that address, delete it
		peerActions.peerDeparture(seeds[i]);
	    } else {
		// ask senior peer
                t = new publishThread(seeds[i]);
                v.add(t);
                t.start();
	    }

            // wait
	    try {
                if (i == 0) Thread.currentThread().sleep(2000); // after the first time wait some seconds
		Thread.currentThread().sleep(1000 + 500 * v.size()); // wait a while
	    } catch (InterruptedException e) {}

            // check all threads
            for (int j = 0; j < v.size(); j++) {
                t = (publishThread) v.elementAt(j);
                added = t.added;
                if (!(t.isAlive())) {
                    //log.logDebug("PEER " + seeds[j].get("Name", "") + " request terminated"); // debug
                    if (added >= 0) {
                        // success! we have published our peer to a senior peer
                        // update latest news from the other peer
                        //log.logInfo("publish: handshaked " + t.seed.get("PeerType", "senior") + " peer '" + t.seed.getName() + "' at " + t.seed.getAddress());
                        peerActions.saveMySeed();
                        return added;
                    }
                }
            }
	}

	// if we have an address, we do nothing
	if ((seedDB.mySeed.isProper()) && (!(force))) return 0;
	
	// still no success: ask own NAT or internet responder
	boolean DI604use = switchboard.getConfig("DI604use", "false").equals("true");
	String  DI604pw  = switchboard.getConfig("DI604pw", "");
	String  ip       = switchboard.getConfig("staticIP", "");
	if(ip.equals("")){
		ip       = natLib.retrieveIP(DI604use, DI604pw, (switchboard.getConfig("yacyDebugMode", "false")=="false" ? false : true));
	}
	//System.out.println("DEBUG: new IP=" + ip);
	seedDB.mySeed.put("IP", ip);
	if (seedDB.mySeed.get("PeerType", "junior").equals("junior")) // ???????????????
	    seedDB.mySeed.put("PeerType", "senior"); // to start bootstraping, we need to be recognised as "senior" peer
	log.logInfo("publish: no recipient found, asked NAT or responder; our address is " +
						 ((seedDB.mySeed.getAddress() == null) ? "unknown" : seedDB.mySeed.getAddress()));
	peerActions.saveMySeed();
	return 0;
    }
    
  
    public boolean saveSeedList() {
	return saveSeedList(this.switchboard);
    }

    public static boolean saveSeedList(serverSwitch sb) {
	String logt;
	// be shure that we have something to say
	if (seedDB.mySeed.getAddress() == null) return false;
	// upload a seed file
	String  seedFTPServer   = sb.getConfig("seedFTPServer","");
	String  seedFTPAccount  = sb.getConfig("seedFTPAccount","");
	String  seedFTPPassword = sb.getConfig("seedFTPPassword","");
	File    seedFTPPath     = new File(sb.getConfig("seedFTPPath",""));
	File    seedFile        = new File(sb.getConfig("seedFilePath",""));
	String prevStatus = seedDB.mySeed.get("PeerType", "junior");
	if (prevStatus.equals("principal")) prevStatus = "senior";
	URL seedURL;
	try{
		seedURL = new URL(sb.getConfig("seedURL",""));
	}catch(MalformedURLException e){
		return false;
	}
	if ((seedFTPServer.length() != 0) && 
	    (seedFTPAccount.length() != 0) && 
	    (seedFTPPassword.length() != 0) && 
	    (seedFTPPath.toString().length() != 0)) {
	    try {
		seedDB.mySeed.put("PeerType", "principal"); // this information shall also be uploaded
		logt = seedDB.uploadCache(seedFTPServer, seedFTPAccount, seedFTPPassword, seedFTPPath, seedURL);
		log.logInfo(logt);
		if (logt.indexOf("Error") >= 0) {
		    seedDB.mySeed.put("PeerType", prevStatus);
		    log.logInfo("seed upload failed (ftp error): " + logt.substring(logt.indexOf("Error") + 6));
		    return false;
		}
		// check if seed file has arrived a public accessible location
		// seedURL=http://www.yacy.net/yacy/seed.txt
		
		// finally, set the principal status
		sb.setConfig("yacyStatus","principal");
		return true;
	    } catch (IOException e) {
		seedDB.mySeed.put("PeerType", prevStatus);
		log.logInfo("seed upload failed (IO error): " + e.getMessage());
		return false;
	    }
        }else if(seedFile.toString().length() != 0){ // [AS]
            try{
                seedDB.mySeed.put("PeerType", "principal"); // this information shall also be uploaded
                logt = seedDB.copyCache(seedFile, seedURL);
                log.logInfo(logt);
                if (logt.indexOf("Error") >= 0) {
                    seedDB.mySeed.put("PeerType", prevStatus);
                    log.logInfo("seed copy failed (IO error): " + logt.substring(logt.indexOf("Error") + 6));
                    return false;
                }
                // check if seed file has arrived a public accessible location
                // seedURL=http://www.yacy.net/yacy/seed.txt
                
                // finally, set the principal status
                sb.setConfig("yacyStatus","principal");
                return true;
            } catch (IOException e) {
                seedDB.mySeed.put("PeerType", prevStatus);
                log.logInfo("seed copy failed (IO error): " + e.getMessage());
                return false;
            }
        }
	seedDB.mySeed.put("PeerType", prevStatus);
	sb.setConfig("yacyStatus", prevStatus);
	return false;
    }

    private class vprobe implements Runnable {
	public vprobe() {}
	public final void run() {
	    // read the probe URL
	    String probeURL=switchboard.getConfig("onetimeAction", null);
	    if ((probeURL == null) || (probeURL.length() == 0)) return; // not wanted
	    // read version and date
            String proxyHost = switchboard.getConfig("remoteProxyHost", "");
            int proxyPort = Integer.parseInt(switchboard.getConfig("remoteProxyPort", "0"));
            if (!(switchboard.getConfig("remoteProxyUse", "false").equals("true"))) {
                proxyHost = null; proxyPort = 0;
            }
            String version = switchboard.getConfig("version", "");
	    String date = switchboard.getConfig("vdate", "");
	    probeURL = probeURL + "?version=" + version + "&date=" + date;
	    // open new connection
	    try {
		latestVersion = new String(httpc.singleGET(new URL(probeURL), 10000, null, null, proxyHost, proxyPort)).trim();
		float latest = Float.parseFloat(latestVersion);
		float thisver = Float.parseFloat(version);
		if (thisver > latest)  System.out.println("THIS SOFTWARE VERSION IS A PRE-RELEASE");
		if (thisver < latest)  {
		    log.logSystem("****************************************************************");
		    log.logSystem("* THIS SOFTWARE VERSION IS OUTDATED.");
		    log.logSystem("* PLEASE GO TO ANOMIC.DE AND DOWNLOAD THE LATEST VERSION " + latestVersion);
		    log.logSystem("****************************************************************");
		}
	    } catch (Exception e) {
		// we do nothing is this case
	    }
	}
    }

}
