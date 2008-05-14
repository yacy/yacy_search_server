// yacyCore.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverSemaphore;
import de.anomic.server.logging.serverLog;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;

public class yacyCore {

    // statics
    public static final ThreadGroup publishThreadGroup = new ThreadGroup("publishThreadGroup");
    public static final HashMap<String, String> seedUploadMethods = new HashMap<String, String>();
    public static yacyPeerActions peerActions = null;
    public static final serverLog log = new serverLog("YACY");
    public static long lastOnlineTime = 0;
    /** pseudo-random key derived from a time-interval while YaCy startup*/
    public static long speedKey = 0;
    public static final Map<String, yacyAccessible> amIAccessibleDB = Collections.synchronizedMap(new HashMap<String, yacyAccessible>()); // Holds PeerHash / yacyAccessible Relations
    // constants for PeerPing behaviour
    private static final int PING_INITIAL = 10;
    private static final int PING_MAX_RUNNING = 3;
    private static final int PING_MIN_RUNNING = 1;
    private static final int PING_MIN_DBSIZE = 5;
    private static final int PING_MIN_PEERSEEN = 1; // min. accessible to force senior
    private static final long PING_MAX_DBAGE = 15 * 60 * 1000; // in milliseconds
    
    // public static yacyShare shareManager = null;
    // public static boolean terminate = false;

    // class variables
    private static int onlineMode = 1;
    plasmaSwitchboard sb;

    public static int yacyTime() {
        // the time since startup of yacy in seconds
        return (int) ((System.currentTimeMillis() - serverCore.startupTime) / 1000);
    }

    public yacyCore(plasmaSwitchboard sb) {
        long time = System.currentTimeMillis();

        this.sb = sb;
        sb.setConfig("yacyStatus", "");
        
        // create a peer news channel
        RSSFeed peernews = RSSFeed.channels(RSSFeed.PEERNEWS);
        peernews.addMessage(new RSSMessage("YaCy started", "", ""));

        loadSeedUploadMethods();

        // deploy peer actions
        peerActions = new yacyPeerActions(sb.wordIndex.seedDB, sb);

        log.logConfig("CORE INITIALIZED");
        // ATTENTION, VERY IMPORTANT: before starting the thread, the httpd yacy server must be running!

        speedKey = System.currentTimeMillis() - time;

        // start with a seedList update to propagate out peer, if possible
        onlineMode = Integer.parseInt(sb.getConfig("onlineMode", "1"));
        //lastSeedUpdate = universalTime();
        lastOnlineTime = 0;

        // cycle
        // within cycle: update seed file, strengthen network, pass news (new, old seed's)
        if (online()) {
            log.logConfig("you are in online mode");
        } else {
            log.logConfig("YOU ARE OFFLINE! ---");
            log.logConfig("--- TO START BOOTSTRAPING, YOU MUST USE THE PROXY,");
            log.logConfig("--- OR HIT THE BUTTON 'go online'");
            log.logConfig("--- ON THE STATUS PAGE http://localhost:" + serverCore.getPortNr(sb.getConfig("port", "8080")) + "/Status.html");
        }
    }

    synchronized static public void triggerOnlineAction() {
        lastOnlineTime = System.currentTimeMillis();
    }

    public final boolean online() {
        onlineMode = Integer.parseInt(sb.getConfig("onlineMode", "1"));
        return ((onlineMode == 2) || ((System.currentTimeMillis() - lastOnlineTime) < 10000));
    }

    public static int getOnlineMode() {
        return onlineMode;
    }

    public static void setOnlineMode(int newOnlineMode) {
        onlineMode = newOnlineMode;
        return;
    }
    
    public final void publishSeedList() {
        log.logFine("yacyCore.publishSeedList: Triggered Seed Publish");

        /*
        if (oldIPStamp.equals((String) seedDB.mySeed.get(yacySeed.IP, "127.0.0.1")))
            yacyCore.log.logDebug("***DEBUG publishSeedList: oldIP is equal");
        if (seedCacheSizeStamp == seedDB.sizeConnected())
            yacyCore.log.logDebug("***DEBUG publishSeedList: sizeConnected is equal");
        if (canReachMyself())
            yacyCore.log.logDebug("***DEBUG publishSeedList: I can reach myself");
        */

        if ((sb.wordIndex.seedDB.lastSeedUpload_myIP.equals(sb.wordIndex.seedDB.mySeed().get(yacySeed.IP, "127.0.0.1"))) &&
            (sb.wordIndex.seedDB.lastSeedUpload_seedDBSize == sb.wordIndex.seedDB.sizeConnected()) &&
            (canReachMyself()) &&
            (System.currentTimeMillis() - sb.wordIndex.seedDB.lastSeedUpload_timeStamp < 1000 * 60 * 60 * 24) &&
            (sb.wordIndex.seedDB.mySeed().isPrincipal())
        ) {
            log.logFine("yacyCore.publishSeedList: not necessary to publish: oldIP is equal, sizeConnected is equal and I can reach myself under the old IP.");
            return;
        }

        // getting the seed upload method that should be used ...
        final String seedUploadMethod = this.sb.getConfig("seedUploadMethod", "");

        if (
                (!seedUploadMethod.equalsIgnoreCase("none")) ||
                ((seedUploadMethod.equals("")) && (this.sb.getConfig("seedFTPPassword", "").length() > 0)) ||
                ((seedUploadMethod.equals("")) && (this.sb.getConfig("seedFilePath", "").length() > 0))
        ) {
            if (seedUploadMethod.equals("")) {
                if (this.sb.getConfig("seedFTPPassword", "").length() > 0) {
                    this.sb.setConfig("seedUploadMethod", "Ftp");
                }
                if (this.sb.getConfig("seedFilePath", "").length() > 0) {
                    this.sb.setConfig("seedUploadMethod", "File");
                }
            }
            // we want to be a principal...
            saveSeedList(sb);
        } else {
            if (seedUploadMethod.equals("")) {
                this.sb.setConfig("seedUploadMethod", "none");
            }
            log.logFine("yacyCore.publishSeedList: No uploading method configured");
            return;
        }
    }

    public final void peerPing() {
        if (!online()) { return; }
        if ((sb.isRobinsonMode()) && (sb.getConfig("cluster.mode", "").equals("privatepeer"))) {
            // in case this peer is a privat peer we omit the peer ping
            // all other robinson peer types do a peer ping:
            // the privatecluster does the ping to the other cluster members
            // the publiccluster does the ping to all peers, but prefers the own peer
            // the publicpeer does the ping to all peers
            return;
        }

        // before publishing, update some seed data
        peerActions.updateMySeed();

        // publish own seed to other peer, this can every peer, but makes only sense for senior peers
        if (sb.wordIndex.seedDB.sizeConnected() == 0) {
            // reload the seed lists
            peerActions.loadSeedLists();
            log.logInfo("re-initialized seed list. received " + sb.wordIndex.seedDB.sizeConnected() + " new peer(s)");
        }
        final int newSeeds = publishMySeed(false);
        if (newSeeds > 0) {
            log.logInfo("received " + newSeeds + " new peer(s), know a total of " + sb.wordIndex.seedDB.sizeConnected() + " different peers");
        }
    }

    private boolean canReachMyself() { // TODO: check if this method is necessary - depending on the used router it will not work
        // returns true if we can reach ourself under our known peer address
        // if we cannot reach ourself, we call a forced publishMySeed and return false
    	final int urlc = yacyClient.queryUrlCount(sb.wordIndex.seedDB.mySeed());
        if (urlc >= 0) {
            sb.wordIndex.seedDB.mySeed().setLastSeenUTC();
            return true;
        }
        log.logInfo("re-connect own seed");
        final String oldAddress = sb.wordIndex.seedDB.mySeed().getPublicAddress();
        /*final int newSeeds =*/ publishMySeed(true);
        return (oldAddress != null && oldAddress.equals(sb.wordIndex.seedDB.mySeed().getPublicAddress()));
    }

    protected class publishThread extends Thread {
        int added;
        private yacySeed seed;
        private final serverSemaphore sync;
        private final List<Thread> syncList;

        public publishThread(ThreadGroup tg, yacySeed seed,
                             serverSemaphore sync, List<Thread> syncList) throws InterruptedException {
            super(tg, "PublishSeed_" + seed.getName());

            this.sync = sync;
            this.sync.P();
            this.syncList = syncList;

            this.seed = seed;
            this.added = 0;
        }

        public final void run() {
            try {
                this.added = yacyClient.publishMySeed(sb.wordIndex.seedDB.mySeed(), seed.getClusterAddress(), seed.hash);
                if (this.added < 0) {
                    // no or wrong response, delete that address
                    String cause = "peer ping to peer resulted in error response (added < 0)";
                    log.logInfo("publish: disconnected " + this.seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + this.seed.getName() + "' from " + this.seed.getPublicAddress() + ": " + cause);
                    peerActions.peerDeparture(this.seed, cause);
                } else {
                    // success! we have published our peer to a senior peer
                    // update latest news from the other peer
                    log.logInfo("publish: handshaked " + this.seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + this.seed.getName() + "' at " + this.seed.getPublicAddress());
                    // check if seed's lastSeen has been updated
                    yacySeed newSeed = sb.wordIndex.seedDB.getConnected(this.seed.hash);
                    if (newSeed != null) {
                        if (newSeed.getLastSeenUTC() < (System.currentTimeMillis() - 10000)) {
                            // update last seed date
                            if (newSeed.getLastSeenUTC() >= this.seed.getLastSeenUTC()) {
                                log.logFine("publish: recently handshaked " + this.seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) +
                                    " peer '" + this.seed.getName() + "' at " + this.seed.getPublicAddress() + " with old LastSeen: '" +
                                    serverDate.formatShortSecond(new Date(newSeed.getLastSeenUTC())) + "'");
                                newSeed.setLastSeenUTC();
                                peerActions.peerArrival(newSeed, true);
                            } else {
                                log.logFine("publish: recently handshaked " + this.seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) +
                                    " peer '" + this.seed.getName() + "' at " + this.seed.getPublicAddress() + " with old LastSeen: '" +
                                    serverDate.formatShortSecond(new Date(newSeed.getLastSeenUTC())) + "', this is more recent: '" +
                                    serverDate.formatShortSecond(new Date(this.seed.getLastSeenUTC())) + "'");
                                this.seed.setLastSeenUTC();
                                peerActions.peerArrival(this.seed, true);
                            }
                        }
                    } else {
                        log.logFine("publish: recently handshaked " + this.seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + this.seed.getName() + "' at " + this.seed.getPublicAddress() + " not in connectedDB");
                    }
                }
            } catch (Exception e) {
                log.logSevere("publishThread: error with target seed " + seed.toString() + ": " + e.getMessage(), e);
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
            Map<String, yacySeed> seeds; // hash/yacySeed relation

            int attempts = sb.wordIndex.seedDB.sizeConnected();

            // getting a list of peers to contact
            if (sb.wordIndex.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN).equals(yacySeed.PEERTYPE_VIRGIN)) {
                if (attempts > PING_INITIAL) { attempts = PING_INITIAL; }
                Map<String, String> ch = plasmaSwitchboard.getSwitchboard().clusterhashes;
                seeds = sb.wordIndex.seedDB.seedsByAge(true, attempts - ((ch == null) ? 0 : ch.size())); // best for fast connection
                // add also all peers from cluster if this is a public robinson cluster
                if (plasmaSwitchboard.getSwitchboard().clusterhashes != null) {
                    Iterator<Map.Entry<String, String>> i = ch.entrySet().iterator();
                    String hash;
                    Map.Entry<String, String> entry;
                    yacySeed seed;
                    while (i.hasNext()) {
                        entry = i.next();
                        hash = entry.getKey();
                        seed = (yacySeed) seeds.get(hash);
                        if (seed == null) {
                            seed = sb.wordIndex.seedDB.get(hash);
                            if (seed == null) continue;
                        }
                        seed.setAlternativeAddress((String) entry.getValue());
                        seeds.put(hash, seed);
                	}
                }
            } else {
                int diff = PING_MIN_DBSIZE - amIAccessibleDB.size();
                if (diff > PING_MIN_RUNNING) {
                    diff = Math.min(diff, PING_MAX_RUNNING);
                    if (attempts > diff) { attempts = diff; }
                } else {
                    if (attempts > PING_MIN_RUNNING) { attempts = PING_MIN_RUNNING; }
                }
                seeds = sb.wordIndex.seedDB.seedsByAge(false, attempts); // best for seed list maintenance/cleaning
            }

            if ((seeds == null) || seeds.size() == 0) { return 0; }
            if (seeds.size() < attempts) { attempts = seeds.size(); }

            // This will try to get Peers that are not currently in amIAccessibleDB
            Iterator<yacySeed> si = seeds.values().iterator();
            yacySeed seed;

            // include a YaCyNews record to my seed
            try {
                final yacyNewsRecord record = sb.wordIndex.newsPool.myPublication();
                if (record == null) {
                    sb.wordIndex.seedDB.mySeed().put("news", "");
                } else {
                    sb.wordIndex.seedDB.mySeed().put("news", de.anomic.tools.crypt.simpleEncode(record.toString()));
                }
            } catch (IOException e) {
                log.logSevere("publishMySeed: problem with news encoding", e);
            }
            sb.wordIndex.seedDB.mySeed().setUnusedFlags();

            // include current citation-rank file count
            sb.wordIndex.seedDB.mySeed().put(yacySeed.CRWCNT, Integer.toString(sb.rankingOwnDistribution.size()));
            sb.wordIndex.seedDB.mySeed().put(yacySeed.CRTCNT, Integer.toString(sb.rankingOtherDistribution.size()));
            int newSeeds = -1;
            //if (seeds.length > 1) {
            // holding a reference to all started threads
            int contactedSeedCount = 0;
            final List<Thread> syncList = Collections.synchronizedList(new LinkedList<Thread>()); // memory for threads
            final serverSemaphore sync = new serverSemaphore(attempts);

            // going through the peer list and starting a new publisher thread for each peer
            int i = 0;
            while (si.hasNext()) {
                seed = (yacySeed) si.next();
                if (seed == null) {
                    sync.P();
                    continue;
                }
                i++;

                final String address = seed.getClusterAddress();
                log.logFine("HELLO #" + i + " to peer '" + seed.get(yacySeed.NAME, "") + "' at " + address); // debug
                String seederror = seed.isProper();
                if ((address == null) || (seederror != null)) {
                    // we don't like that address, delete it
                    peerActions.peerDeparture(seed, "peer ping to peer resulted in address = " + address + "; seederror = " + seederror);
                    sync.P();
                } else {
                    // starting a new publisher thread
                    contactedSeedCount++;
                    (new publishThread(yacyCore.publishThreadGroup, seed, sync, syncList)).start();
                }
            }

            // receiving the result of all started publisher threads
            for (int j = 0; j < contactedSeedCount; j++) {

                // waiting for the next thread to finish
                sync.P();

                // if this is true something is wrong ...
                if (syncList.isEmpty()) {
                    log.logWarning("PeerPing: syncList.isEmpty()==true");
                    continue;
                    //return 0;
                }

                // getting a reference to the finished thread
                final publishThread t = (publishThread) syncList.remove(0);

                // getting the amount of new reported seeds
                if (t.added >= 0) {
                    if (newSeeds == -1) {
                        newSeeds =  t.added;
                    } else {
                        newSeeds += t.added;
                    }
                }
            }

            int accessible = 0;
            int notaccessible = 0;
            final long cutofftime = System.currentTimeMillis() - PING_MAX_DBAGE;
            final int dbSize;
            synchronized (amIAccessibleDB) {
                dbSize = amIAccessibleDB.size();
                Iterator<String> ai = amIAccessibleDB.keySet().iterator();
                while (ai.hasNext()) {
                    yacyAccessible ya = (yacyAccessible) amIAccessibleDB.get(ai.next());
                    if (ya.lastUpdated < cutofftime) {
                        ai.remove();
                    } else {
                        if (ya.IWasAccessed) {
                            accessible++;
                        } else {
                            notaccessible++;
                        }
                    }
                }
                log.logFine("DBSize before -> after Cleanup: " + dbSize + " -> " + amIAccessibleDB.size());
            }
            log.logInfo("PeerPing: I am accessible for " + accessible +
                " peer(s), not accessible for " + notaccessible + " peer(s).");

            if ((accessible + notaccessible) > 0) {
                final String newPeerType;
                // At least one other Peer told us our type
                if ((accessible >= PING_MIN_PEERSEEN) ||
                    (accessible >= notaccessible)) {
                    // We can be reached from a majority of other Peers
                    if (sb.wordIndex.seedDB.mySeed().isPrincipal()) {
                        newPeerType = yacySeed.PEERTYPE_PRINCIPAL;
                    } else {
                        newPeerType = yacySeed.PEERTYPE_SENIOR;
                    }
                } else {
                    // We cannot be reached from the outside
                    newPeerType = yacySeed.PEERTYPE_JUNIOR;
                }
                if (sb.wordIndex.seedDB.mySeed().orVirgin().equals(newPeerType)) {
                    log.logInfo("PeerPing: myType is " + sb.wordIndex.seedDB.mySeed().orVirgin());
                } else {
                    log.logInfo("PeerPing: changing myType from '" + sb.wordIndex.seedDB.mySeed().orVirgin() + "' to '" + newPeerType + "'");
                    sb.wordIndex.seedDB.mySeed().put(yacySeed.PEERTYPE, newPeerType);
                }
            } else {
                log.logInfo("PeerPing: No data, staying at myType: " + sb.wordIndex.seedDB.mySeed().orVirgin());
            }

            // success! we have published our peer to a senior peer
            // update latest news from the other peer
            // log.logInfo("publish: handshaked " + t.seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + t.seed.getName() + "' at " + t.seed.getAddress());
            sb.wordIndex.seedDB.saveMySeed();

            // if we have an address, we do nothing
            if (sb.wordIndex.seedDB.mySeed().isProper() == null && !force) { return 0; }
            if (newSeeds > 0) return newSeeds;
            
            // still no success: ask own NAT or internet responder
            //final boolean DI604use = switchboard.getConfig("DI604use", "false").equals("true");
            //final String  DI604pw  = switchboard.getConfig("DI604pw", "");
            String  ip = sb.getConfig("staticIP", "");
            //if (ip.equals("")) ip = natLib.retrieveIP(DI604use, DI604pw);
            
            // yacyCore.log.logDebug("DEBUG: new IP=" + ip);
            sb.wordIndex.seedDB.mySeed().put(yacySeed.IP, ip);
            if (sb.wordIndex.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR).equals(yacySeed.PEERTYPE_JUNIOR)) // ???????????????
                sb.wordIndex.seedDB.mySeed().put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR); // to start bootstraping, we need to be recognised as PEERTYPE_SENIOR peer
            log.logInfo("publish: no recipient found, our address is " +
                    ((sb.wordIndex.seedDB.mySeed().getPublicAddress() == null) ? "unknown" : sb.wordIndex.seedDB.mySeed().getPublicAddress()));
            sb.wordIndex.seedDB.saveMySeed();
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
                final Thread[] threadList = new Thread[threadCount];
                threadCount = yacyCore.publishThreadGroup.enumerate(threadList);

                // we need to use a timeout here because of missing interruptable session threads ...
                log.logFine("publish: Waiting for " + yacyCore.publishThreadGroup.activeCount() +  " remaining publishing threads to finish shutdown ...");
                for (int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++) {
                    final Thread currentThread = threadList[currentThreadIdx];

                    if (currentThread.isAlive()) {
                        log.logFine("publish: Waiting for remaining publishing thread '" + currentThread.getName() + "' to finish shutdown");
                        try { currentThread.join(500); } catch (InterruptedException ex) {}
                    }
                }

                log.logInfo("publish: Shutdown off all remaining publishing thread finished.");

            } catch (Exception ee) {
                log.logWarning("publish: Unexpected error while trying to shutdown all remaining publishing threads.", e);
            }

            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static HashMap<String, String> getSeedUploadMethods() {
        synchronized (yacyCore.seedUploadMethods) {
            return (HashMap<String, String>) yacyCore.seedUploadMethods.clone();
        }
    }

    public static yacySeedUploader getSeedUploader(String methodname) {
        String className = null;
        synchronized (yacyCore.seedUploadMethods) {
            if (yacyCore.seedUploadMethods.containsKey(methodname)) {
                className = (String) yacyCore.seedUploadMethods.get(methodname);
            }
        }

        if (className == null) { return null; }
        try {
            final Class<?> uploaderClass = Class.forName(className);
            final Object uploader = uploaderClass.newInstance();
            return (yacySeedUploader) uploader;
        } catch (Exception e) {
            return null;
        }
    }

    public static void loadSeedUploadMethods() {
        final HashMap<String, String> availableUploaders = new HashMap<String, String>();
        try {
            final String uploadersPkgName = yacyCore.class.getPackage().getName() + ".seedUpload";
            final String packageURI = yacyCore.class.getResource("/" + uploadersPkgName.replace('.', '/')).toString();

            // open the parser directory
            final File uploadersDir = new File(new URI(packageURI));
            if ((uploadersDir == null) || (!uploadersDir.exists()) || (!uploadersDir.isDirectory())) {
                yacyCore.seedUploadMethods.clear();
                changeSeedUploadMethod("none");
            }

            final String[] uploaderClasses = uploadersDir.list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("yacySeedUpload") && name.endsWith(".class");
                }
            });

            final String javaClassPath = System.getProperty("java.class.path");

            if (uploaderClasses == null) { return; }
            for (int uploaderNr = 0; uploaderNr < uploaderClasses.length; uploaderNr++) {
                final String className = uploaderClasses[uploaderNr].substring(0, uploaderClasses[uploaderNr].indexOf(".class"));
                final String fullClassName = uploadersPkgName + "." + className;
                try {
                    final Class<?> uploaderClass = Class.forName(fullClassName);
                    final Object theUploader = uploaderClass.newInstance();
                    if (!(theUploader instanceof yacySeedUploader)) { continue; }
                    final String[] neededLibx = ((yacySeedUploader)theUploader).getLibxDependencies();
                    if (neededLibx != null) {
                        for (int libxId=0; libxId < neededLibx.length; libxId++) {
                            if (javaClassPath.indexOf(neededLibx[libxId]) == -1) {
                                throw new Exception("Missing dependency");
                            }
                        }
                    }
                    availableUploaders.put(className.substring("yacySeedUpload".length()), fullClassName);
                } catch (Exception e) { /* we can ignore this for the moment */
                } catch (Error e)     { /* we can ignore this for the moment */
                }
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
        if (method == null || method.length() == 0) { return false; }

        if (method.equalsIgnoreCase("none")) { return true; }

        synchronized (yacyCore.seedUploadMethods) {
            return yacyCore.seedUploadMethods.containsKey(method);
        }
    }

    public static final String saveSeedList(plasmaSwitchboard sb) {
        try {
            // return an error if this is not successful, and NULL if everything is fine
            String logt;

            // be shure that we have something to say
            if (sb.wordIndex.seedDB.mySeed().getPublicAddress() == null) {
                final String errorMsg = "We have no valid IP address until now";
                log.logWarning("SaveSeedList: " + errorMsg);
                return errorMsg;
            }

            // getting the configured seed uploader
            String seedUploadMethod = sb.getConfig("seedUploadMethod", "");

            // for backward compatiblity ....
            if (seedUploadMethod.equalsIgnoreCase("Ftp") ||
                (seedUploadMethod.equals("") &&
                 sb.getConfig("seedFTPPassword", "").length() > 0)) {

                seedUploadMethod = "Ftp";
                sb.setConfig("seedUploadMethod", seedUploadMethod);

            } else if (seedUploadMethod.equalsIgnoreCase("File") ||
                       (seedUploadMethod.equals("") &&
                        sb.getConfig("seedFilePath", "").length() > 0)) {

                seedUploadMethod = "File";
                sb.setConfig("seedUploadMethod", seedUploadMethod);
            }

            //  determine the seed uploader that should be used ...
            if (seedUploadMethod.equalsIgnoreCase("none")) { return "no uploader specified"; }

            yacySeedUploader uploader = getSeedUploader(seedUploadMethod);
            if (uploader == null) {
                final String errorMsg = "Unable to get the proper uploader-class for seed uploading method '" + seedUploadMethod + "'.";
                log.logWarning("SaveSeedList: " + errorMsg);
                return errorMsg;
            }

            // ensure that the seed file url is configured properly
            yacyURL seedURL;
            try {
                final String seedURLStr = sb.getConfig("seedURL", "");
                if (seedURLStr.length() == 0) { throw new MalformedURLException("The seed-file url must not be empty."); }
                if (!(
                        seedURLStr.toLowerCase().startsWith("http://") ||
                        seedURLStr.toLowerCase().startsWith("https://")
                )) {
                    throw new MalformedURLException("Unsupported protocol.");
                }
                seedURL = new yacyURL(seedURLStr, null);
            } catch (MalformedURLException e) {
                final String errorMsg = "Malformed seed file URL '" + sb.getConfig("seedURL", "") + "'. " + e.getMessage();
                log.logWarning("SaveSeedList: " + errorMsg);
                return errorMsg;
            }

            // upload the seed-list using the configured uploader class
            String prevStatus = sb.wordIndex.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR);
            if (prevStatus.equals(yacySeed.PEERTYPE_PRINCIPAL)) { prevStatus = yacySeed.PEERTYPE_SENIOR; }

            try {
                sb.wordIndex.seedDB.mySeed().put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_PRINCIPAL); // this information shall also be uploaded

                log.logFine("SaveSeedList: Using seed uploading method '" + seedUploadMethod + "' for seed-list uploading." +
                            "\n\tPrevious peerType is '" + sb.wordIndex.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR) + "'.");

//              logt = seedDB.uploadCache(seedFTPServer, seedFTPAccount, seedFTPPassword, seedFTPPath, seedURL);
                logt = sb.wordIndex.seedDB.uploadCache(uploader, sb, sb.wordIndex.seedDB, seedURL);
                if (logt != null) {
                    if (logt.indexOf("Error") >= 0) {
                        sb.wordIndex.seedDB.mySeed().put(yacySeed.PEERTYPE, prevStatus);
                        final String errorMsg = "SaveSeedList: seed upload failed using " + uploader.getClass().getName() + " (error): " + logt.substring(logt.indexOf("Error") + 6);
                        log.logSevere(errorMsg);
                        return errorMsg;
                    }
                    log.logInfo(logt);
                }

                // finally, set the principal status
                sb.setConfig("yacyStatus", yacySeed.PEERTYPE_PRINCIPAL);
                return null;
            } catch (Exception e) {
                sb.wordIndex.seedDB.mySeed().put(yacySeed.PEERTYPE, prevStatus);
                sb.setConfig("yacyStatus", prevStatus);
                final String errorMsg = "SaveSeedList: Seed upload failed (IO error): " + e.getMessage();
                log.logInfo(errorMsg, e);
                return errorMsg;
            }
        } finally {
            sb.wordIndex.seedDB.lastSeedUpload_seedDBSize = sb.wordIndex.seedDB.sizeConnected();
            sb.wordIndex.seedDB.lastSeedUpload_timeStamp = System.currentTimeMillis();
            sb.wordIndex.seedDB.lastSeedUpload_myIP = sb.wordIndex.seedDB.mySeed().get(yacySeed.IP, "127.0.0.1");
        }
    }

}
