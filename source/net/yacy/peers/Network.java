// yacyCore.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
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

package net.yacy.peers;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.operation.yacySeedUploadFile;
import net.yacy.peers.operation.yacySeedUploadFtp;
import net.yacy.peers.operation.yacySeedUploadScp;
import net.yacy.peers.operation.yacySeedUploader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverCore;

public class Network
{

    // statics
    public static final ThreadGroup publishThreadGroup = new ThreadGroup("publishThreadGroup");
    public static final HashMap<String, String> seedUploadMethods = new HashMap<String, String>();
    public static final ConcurrentLog log = new ConcurrentLog("YACY");
    /** pseudo-random key derived from a time-interval while YaCy startup */
    public static long speedKey = 0;
    public static long magic = System.currentTimeMillis();
    public static final Map<String, Accessible> amIAccessibleDB = new ConcurrentHashMap<String, Accessible>(); // Holds PeerHash / yacyAccessible Relations
    // constants for PeerPing behavior
    private static final int PING_INITIAL = 20;
    private static final int PING_MAX_RUNNING = 3;
    private static final int PING_MIN_RUNNING = 1;
    private static final int PING_MIN_DBSIZE = 5;
    private static final int PING_MIN_PEERSEEN = 1; // min. accessible to force senior
    private static final long PING_MAX_DBAGE = 15 * 60 * 1000; // in milliseconds

    // public static yacyShare shareManager = null;
    // public static boolean terminate = false;

    // class variables
    Switchboard sb;

    public static int yacyTime() {
        // the time since startup of yacy in seconds
        return Math.max(0, (int) ((System.currentTimeMillis() - serverCore.startupTime) / 1000));
    }

    public Network(final Switchboard sb) {
        final long time = System.currentTimeMillis();

        this.sb = sb;
        sb.setConfig("yacyStatus", "");

        // create a peer news channel
        final RSSFeed peernews = EventChannel.channels(EventChannel.PEERNEWS);
        peernews.addMessage(new RSSMessage("YaCy started", "", ""));

        // ensure that correct IP is used
        final String staticIP = sb.getConfig("staticIP", "");
        if ( staticIP.length() != 0 && Seed.isProperIP(staticIP) == null ) {
            serverCore.useStaticIP = true;
            sb.peers.mySeed().setIP(staticIP);
            log.info("staticIP set to " + staticIP);
        } else {
            serverCore.useStaticIP = false;
        }

        loadSeedUploadMethods();

        log.config("CORE INITIALIZED");
        // ATTENTION, VERY IMPORTANT: before starting the thread, the httpd yacy server must be running!

        speedKey = System.currentTimeMillis() - time;
    }

    public final void publishSeedList() {
        if ( log.isFine() ) {
            log.fine("yacyCore.publishSeedList: Triggered Seed Publish");
        }

        /*
        if (oldIPStamp.equals((String) seedDB.mySeed.get(yacySeed.IP, "127.0.0.1")))
            yacyCore.log.logDebug("***DEBUG publishSeedList: oldIP is equal");
        if (seedCacheSizeStamp == seedDB.sizeConnected())
            yacyCore.log.logDebug("***DEBUG publishSeedList: sizeConnected is equal");
        if (canReachMyself())
            yacyCore.log.logDebug("***DEBUG publishSeedList: I can reach myself");
        */

        if ( (this.sb.peers.lastSeedUpload_myIP.equals(this.sb.peers.mySeed().getIP()))
            && (this.sb.peers.lastSeedUpload_seedDBSize == this.sb.peers.sizeConnected())
            && (System.currentTimeMillis() - this.sb.peers.lastSeedUpload_timeStamp < 1000 * 60 * 60 * 24)
            && (this.sb.peers.mySeed().isPrincipal()) ) {
            if ( log.isFine() ) {
                log
                    .fine("yacyCore.publishSeedList: not necessary to publish: oldIP is equal, sizeConnected is equal and I can reach myself under the old IP.");
            }
            return;
        }

        // getting the seed upload method that should be used ...
        final String seedUploadMethod = this.sb.getConfig("seedUploadMethod", "");

        if ( (!seedUploadMethod.equalsIgnoreCase("none"))
            || ((seedUploadMethod.equals("")) && (this.sb.getConfig("seedFTPPassword", "").length() > 0))
            || ((seedUploadMethod.equals("")) && (this.sb.getConfig("seedFilePath", "").length() > 0)) ) {
            if ( seedUploadMethod.equals("") ) {
                if ( this.sb.getConfig("seedFTPPassword", "").length() > 0 ) {
                    this.sb.setConfig("seedUploadMethod", "Ftp");
                }
                if ( this.sb.getConfig("seedFilePath", "").length() > 0 ) {
                    this.sb.setConfig("seedUploadMethod", "File");
                }
            }
            // we want to be a principal...
            saveSeedList(this.sb);
        } else {
            if ( seedUploadMethod.equals("") ) {
                this.sb.setConfig("seedUploadMethod", "none");
            }
            if ( log.isFine() ) {
                log.fine("yacyCore.publishSeedList: No uploading method configured");
            }
            return;
        }
    }

    public final void peerPing() {
        if ( (this.sb.isRobinsonMode())
            && (this.sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PRIVATE_PEER)) ) {
            // in case this peer is a privat peer we omit the peer ping
            // all other robinson peer types do a peer ping:
            // the privatecluster does the ping to the other cluster members
            // the publiccluster does the ping to all peers, but prefers the own peer
            // the publicpeer does the ping to all peers
            return;
        }

        // before publishing, update some seed data
        this.sb.updateMySeed();

        // publish own seed to other peer, this can every peer, but makes only sense for senior peers
        if ( this.sb.peers.sizeConnected() == 0 ) {
            // reload the seed lists
            this.sb.loadSeedLists();
            log.info("re-initialized seed list. received "
                + this.sb.peers.sizeConnected()
                + " new peer(s)");
        }
        final int newSeeds = publishMySeed(false);
        if ( newSeeds > 0 ) {
            log.info("received "
                + newSeeds
                + " new peer(s), know a total of "
                + this.sb.peers.sizeConnected()
                + " different peers");
        }
    }

    // use our own formatter to prevent concurrency locks with other processes
    private final static GenericFormatter my_SHORT_SECOND_FORMATTER = new GenericFormatter(
        GenericFormatter.FORMAT_SHORT_SECOND,
        GenericFormatter.time_second);

    protected class publishThread extends Thread
    {
        int added;
        private final Seed seed;
        private final Semaphore sync;
        private final List<Thread> syncList;

        public publishThread(
            final ThreadGroup tg,
            final Seed seed,
            final Semaphore sync,
            final List<Thread> syncList) throws InterruptedException {
            super(tg, "PublishSeed_" + seed.getName());

            this.sync = sync;
            this.sync.acquire();
            this.syncList = syncList;

            this.seed = seed;
            this.added = 0;
        }

        @Override
        public final void run() {
            try {
                this.added =
                    Protocol.hello(
                        Network.this.sb.peers.mySeed(),
                        Network.this.sb.peers.peerActions,
                        this.seed.getClusterAddress(),
                        this.seed.hash,
                        this.seed.getName());
                if ( this.added < 0 ) {
                    // no or wrong response, delete that address
                    final String cause = "peer ping to peer resulted in error response (added < 0)";
                    log.info("publish: disconnected "
                        + this.seed.get(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR)
                        + " peer '"
                        + this.seed.getName()
                        + "' from "
                        + this.seed.getPublicAddress()
                        + ": "
                        + cause);
                    Network.this.sb.peers.peerActions.peerDeparture(this.seed, cause);
                } else {
                    // success! we have published our peer to a senior peer
                    // update latest news from the other peer
                    log.info("publish: handshaked "
                        + this.seed.get(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR)
                        + " peer '"
                        + this.seed.getName()
                        + "' at "
                        + this.seed.getPublicAddress());
                    // check if seed's lastSeen has been updated
                    final Seed newSeed = Network.this.sb.peers.getConnected(this.seed.hash);
                    if ( newSeed != null ) {
                        if ( !newSeed.isOnline() ) {
                            if ( log.isFine() ) {
                                log.fine("publish: recently handshaked "
                                    + this.seed.get(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR)
                                    + " peer '"
                                    + this.seed.getName()
                                    + "' at "
                                    + this.seed.getPublicAddress()
                                    + " is not online."
                                    + " Removing Peer from connected");
                            }
                            Network.this.sb.peers.peerActions.peerDeparture(newSeed, "peer not online");
                        } else if ( newSeed.getLastSeenUTC() < (System.currentTimeMillis() - 10000) ) {
                            // update last seed date
                            if ( newSeed.getLastSeenUTC() >= this.seed.getLastSeenUTC() ) {
                                if ( log.isFine() ) {
                                    log
                                        .fine("publish: recently handshaked "
                                            + this.seed.get(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR)
                                            + " peer '"
                                            + this.seed.getName()
                                            + "' at "
                                            + this.seed.getPublicAddress()
                                            + " with old LastSeen: '"
                                            + my_SHORT_SECOND_FORMATTER.format(new Date(newSeed
                                                .getLastSeenUTC())) + "'");
                                }
                                newSeed.setLastSeenUTC();
                                Network.this.sb.peers.peerActions.peerArrival(newSeed, true);
                            } else {
                                if ( log.isFine() ) {
                                    log
                                        .fine("publish: recently handshaked "
                                            + this.seed.get(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR)
                                            + " peer '"
                                            + this.seed.getName()
                                            + "' at "
                                            + this.seed.getPublicAddress()
                                            + " with old LastSeen: '"
                                            + my_SHORT_SECOND_FORMATTER.format(new Date(newSeed
                                                .getLastSeenUTC()))
                                            + "', this is more recent: '"
                                            + my_SHORT_SECOND_FORMATTER.format(new Date(this.seed
                                                .getLastSeenUTC()))
                                            + "'");
                                }
                                this.seed.setLastSeenUTC();
                                Network.this.sb.peers.peerActions.peerArrival(this.seed, true);
                            }
                        }
                    } else {
                        if ( log.isFine() ) {
                            log.fine("publish: recently handshaked "
                                + this.seed.get(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR)
                                + " peer '"
                                + this.seed.getName()
                                + "' at "
                                + this.seed.getPublicAddress()
                                + " not in connectedDB");
                        }
                    }
                }
            } catch (final Exception e ) {
                ConcurrentLog.logException(e);
                log.severe(
                    "publishThread: error with target seed " + this.seed.toString() + ": " + e.getMessage(),
                    e);
            } finally {
                this.syncList.add(this);
                this.sync.release();
            }
        }
    }

    private int publishMySeed(final boolean force) {
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
            Map<String, Seed> seeds; // hash/yacySeed relation

            int attempts = this.sb.peers.sizeConnected();

            // getting a list of peers to contact
            if ( this.sb.peers.mySeed().get(Seed.PEERTYPE, Seed.PEERTYPE_VIRGIN).equals(Seed.PEERTYPE_VIRGIN) ) {
                if ( attempts > PING_INITIAL ) {
                    attempts = PING_INITIAL;
                }
                final Map<byte[], String> ch = Switchboard.getSwitchboard().clusterhashes;
                seeds = DHTSelection.seedsByAge(this.sb.peers, true, attempts - ((ch == null) ? 0 : ch.size())); // best for fast connection
                // add also all peers from cluster if this is a public robinson cluster
                if ( ch != null ) {
                    final Iterator<Map.Entry<byte[], String>> i = ch.entrySet().iterator();
                    String hash;
                    Map.Entry<byte[], String> entry;
                    Seed seed;
                    while ( i.hasNext() ) {
                        entry = i.next();
                        hash = ASCII.String(entry.getKey());
                        seed = seeds.get(hash);
                        if ( seed == null ) {
                            seed = this.sb.peers.get(hash);
                            if ( seed == null ) {
                                continue;
                            }
                        }
                        seed.setAlternativeAddress(entry.getValue());
                        seeds.put(hash, seed);
                    }
                }
            } else {
                int diff = PING_MIN_DBSIZE - amIAccessibleDB.size();
                if ( diff > PING_MIN_RUNNING ) {
                    diff = Math.min(diff, PING_MAX_RUNNING);
                    if ( attempts > diff ) {
                        attempts = diff;
                    }
                } else {
                    if ( attempts > PING_MAX_RUNNING ) {
                        attempts = PING_MAX_RUNNING;
                    }
                }
                seeds = DHTSelection.seedsByAge(this.sb.peers, false, attempts); // best for seed list maintenance/cleaning
            }

            if ( seeds == null || seeds.isEmpty() ) {
                return 0;
            }
            if ( seeds.size() < attempts ) {
                attempts = seeds.size();
            }

            // This will try to get Peers that are not currently in amIAccessibleDB
            final Iterator<Seed> si = seeds.values().iterator();
            Seed seed;

            // include a YaCyNews record to my seed
            try {
                final NewsDB.Record record = this.sb.peers.newsPool.myPublication();
                if ( record == null ) {
                    this.sb.peers.mySeed().put(Seed.NEWS, "");
                } else {
                    this.sb.peers.mySeed().put(Seed.NEWS, net.yacy.utils.crypt.simpleEncode(record.toString()));
                }
            } catch (final Exception e ) {
                log.severe("publishMySeed: problem with news encoding", e);
            }
            this.sb.peers.mySeed().setUnusedFlags();
            int newSeeds = -1;
            //if (seeds.length > 1) {
            // holding a reference to all started threads
            int contactedSeedCount = 0;
            final List<Thread> syncList = Collections.synchronizedList(new LinkedList<Thread>()); // memory for threads
            final Semaphore sync = new Semaphore(attempts);

            // going through the peer list and starting a new publisher thread for each peer
            int i = 0;
            while ( si.hasNext() ) {
                seed = si.next();
                if ( seed == null || seed.hash.equals(this.sb.peers.mySeed().hash)) {
                    sync.acquire();
                    continue;
                }
                i++;

                final String address = seed.getClusterAddress();
                if ( log.isFine() ) {
                    log.fine("HELLO #" + i + " to peer '" + seed.get(Seed.NAME, "") + "' at " + address); // debug
                }
                final String seederror = seed.isProper(false);
                if ( (address == null) || (seederror != null) ) {
                    // we don't like that address, delete it
                    this.sb.peers.peerActions.peerDeparture(seed, "peer ping to peer resulted in address = "
                        + address
                        + "; seederror = "
                        + seederror);
                    sync.acquire();
                } else {
                    // starting a new publisher thread
                    contactedSeedCount++;
                    (new publishThread(Network.publishThreadGroup, seed, sync, syncList)).start();
                }
            }

            // receiving the result of all started publisher threads
            for ( int j = 0; j < contactedSeedCount; j++ ) {

                // waiting for the next thread to finish
                sync.acquire();

                // if this is true something is wrong ...
                if ( syncList.isEmpty() ) {
                    log.warn("PeerPing: syncList.isEmpty()==true");
                    continue;
                    //return 0;
                }

                // getting a reference to the finished thread
                final publishThread t = (publishThread) syncList.remove(0);

                // getting the amount of new reported seeds
                if ( t.added >= 0 ) {
                    if ( newSeeds == -1 ) {
                        newSeeds = t.added;
                    } else {
                        newSeeds += t.added;
                    }
                }
            }

            int accessible = 0;
            int notaccessible = 0;
            final long cutofftime = System.currentTimeMillis() - PING_MAX_DBAGE;
            final int dbSize;
            synchronized ( amIAccessibleDB ) {
                dbSize = amIAccessibleDB.size();
                final Iterator<String> ai = amIAccessibleDB.keySet().iterator();
                while ( ai.hasNext() ) {
                    final Accessible ya = amIAccessibleDB.get(ai.next());
                    if ( ya.lastUpdated < cutofftime ) {
                        ai.remove();
                    } else {
                        if ( ya.IWasAccessed ) {
                            accessible++;
                        } else {
                            notaccessible++;
                        }
                    }
                }
                if ( log.isFine() ) {
                    log
                        .fine("DBSize before -> after Cleanup: "
                            + dbSize
                            + " -> "
                            + amIAccessibleDB.size());
                }
            }
            log.info("PeerPing: I am accessible for "
                + accessible
                + " peer(s), not accessible for "
                + notaccessible
                + " peer(s).");

            if ( (accessible + notaccessible) > 0 ) {
                final String newPeerType;
                // At least one other Peer told us our type
                if ( (accessible >= PING_MIN_PEERSEEN) || (accessible >= notaccessible) ) {
                    // We can be reached from a majority of other Peers
                    if ( this.sb.peers.mySeed().isPrincipal() ) {
                        newPeerType = Seed.PEERTYPE_PRINCIPAL;
                    } else {
                        newPeerType = Seed.PEERTYPE_SENIOR;
                    }
                } else {
                    // We cannot be reached from the outside
                    newPeerType = Seed.PEERTYPE_JUNIOR;
                }
                if ( this.sb.peers.mySeed().orVirgin().equals(newPeerType) ) {
                    log.info("PeerPing: myType is " + this.sb.peers.mySeed().orVirgin());
                } else {
                    log.info("PeerPing: changing myType from '"
                        + this.sb.peers.mySeed().orVirgin()
                        + "' to '"
                        + newPeerType
                        + "'");
                    this.sb.peers.mySeed().put(Seed.PEERTYPE, newPeerType);
                }
            } else {
                log.info("PeerPing: No data, staying at myType: " + this.sb.peers.mySeed().orVirgin());
            }

            // success! we have published our peer to a senior peer
            // update latest news from the other peer
            // log.logInfo("publish: handshaked " + t.seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + t.seed.getName() + "' at " + t.seed.getAddress());
            this.sb.peers.saveMySeed();

            // if we have an address, we do nothing
            if ( this.sb.peers.mySeed().isProper(true) == null && !force ) {
                return 0;
            }
            if ( newSeeds > 0 ) {
                return newSeeds;
            }

            // still no success: ask own NAT or internet responder
            //final boolean DI604use = switchboard.getConfig("DI604use", "false").equals("true");
            //final String  DI604pw  = switchboard.getConfig("DI604pw", "");
            final String ip = this.sb.getConfig("staticIP", "");
            //if (ip.equals("")) ip = natLib.retrieveIP(DI604use, DI604pw);

            // yacyCore.log.logDebug("DEBUG: new IP=" + ip);
            if ( Seed.isProperIP(ip) == null ) {
                this.sb.peers.mySeed().setIP(ip);
            }
            if ( this.sb.peers.mySeed().get(Seed.PEERTYPE, Seed.PEERTYPE_JUNIOR).equals(Seed.PEERTYPE_JUNIOR) ) {
                this.sb.peers.mySeed().put(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR); // to start bootstraping, we need to be recognised as PEERTYPE_SENIOR peer
            }
            log.info("publish: no recipient found, our address is "
                + ((this.sb.peers.mySeed().getPublicAddress() == null) ? "unknown" : this.sb.peers
                    .mySeed()
                    .getPublicAddress()));
            this.sb.peers.saveMySeed();
            return 0;
        } catch (final InterruptedException e ) {
            try {
                log.info("publish: Interruption detected while publishing my seed.");

                // consuming the theads interrupted signal
                Thread.interrupted();

                // interrupt all already started publishThreads
                log.info("publish: Signaling shutdown to "
                    + Network.publishThreadGroup.activeCount()
                    + " remaining publishing threads ...");
                Network.publishThreadGroup.interrupt();

                // waiting some time for the publishThreads to finish execution
                try {
                    Thread.sleep(500);
                } catch (final InterruptedException ex ) {
                }

                // getting the amount of remaining publishing threads
                int threadCount = Network.publishThreadGroup.activeCount();
                final Thread[] threadList = new Thread[threadCount];
                threadCount = Network.publishThreadGroup.enumerate(threadList);

                // we need to use a timeout here because of missing interruptable session threads ...
                if ( log.isFine() ) {
                    log.fine("publish: Waiting for "
                        + Network.publishThreadGroup.activeCount()
                        + " remaining publishing threads to finish shutdown ...");
                }
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ ) {
                    final Thread currentThread = threadList[currentThreadIdx];

                    if ( currentThread.isAlive() ) {
                        if ( log.isFine() ) {
                            log.fine("publish: Waiting for remaining publishing thread '"
                                + currentThread.getName()
                                + "' to finish shutdown");
                        }
                        try {
                            currentThread.join(500);
                        } catch (final InterruptedException ex ) {
                        }
                    }
                }

                log.info("publish: Shutdown off all remaining publishing thread finished.");

            } catch (final Exception ee ) {
                log.warn(
                    "publish: Unexpected error while trying to shutdown all remaining publishing threads.",
                    e);
            }

            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static HashMap<String, String> getSeedUploadMethods() {
        synchronized ( Network.seedUploadMethods ) {
            return (HashMap<String, String>) Network.seedUploadMethods.clone();
        }
    }

    public static yacySeedUploader getSeedUploader(final String methodname) {
        String className = null;
        synchronized ( Network.seedUploadMethods ) {
            if ( Network.seedUploadMethods.containsKey(methodname) ) {
                className = Network.seedUploadMethods.get(methodname);
            }
        }

        if ( className == null ) {
            return null;
        }
        try {
            final Class<?> uploaderClass = Class.forName(className);
            final Object uploader = uploaderClass.newInstance();
            return (yacySeedUploader) uploader;
        } catch (final Exception e ) {
            return null;
        }
    }

    public static void loadSeedUploadMethods() {
        yacySeedUploader uploader;
        uploader = new yacySeedUploadFile();
        Network.seedUploadMethods.put(uploader
            .getClass()
            .getSimpleName()
            .substring("yacySeedUpload".length()), uploader.getClass().getCanonicalName());
        uploader = new yacySeedUploadFtp();
        Network.seedUploadMethods.put(uploader
            .getClass()
            .getSimpleName()
            .substring("yacySeedUpload".length()), uploader.getClass().getCanonicalName());
        uploader = new yacySeedUploadScp();
        Network.seedUploadMethods.put(uploader
            .getClass()
            .getSimpleName()
            .substring("yacySeedUpload".length()), uploader.getClass().getCanonicalName());
    }

    public static boolean changeSeedUploadMethod(final String method) {
        if ( method == null || method.isEmpty() ) {
            return false;
        }

        if ( method.equalsIgnoreCase("none") ) {
            return true;
        }

        synchronized ( Network.seedUploadMethods ) {
            return Network.seedUploadMethods.containsKey(method);
        }
    }

    public static final String saveSeedList(final Switchboard sb) {
        try {
            // return an error if this is not successful, and NULL if everything is fine
            String logt;

            // be shure that we have something to say
            if ( sb.peers.mySeed().getPublicAddress() == null ) {
                final String errorMsg = "We have no valid IP address until now";
                log.warn("SaveSeedList: " + errorMsg);
                return errorMsg;
            }

            // getting the configured seed uploader
            String seedUploadMethod = sb.getConfig("seedUploadMethod", "");

            // for backward compatiblity ....
            if ( seedUploadMethod.equalsIgnoreCase("Ftp")
                || (seedUploadMethod.equals("") && sb.getConfig("seedFTPPassword", "").length() > 0) ) {

                seedUploadMethod = "Ftp";
                sb.setConfig("seedUploadMethod", seedUploadMethod);

            } else if ( seedUploadMethod.equalsIgnoreCase("File")
                || (seedUploadMethod.equals("") && sb.getConfig("seedFilePath", "").length() > 0) ) {

                seedUploadMethod = "File";
                sb.setConfig("seedUploadMethod", seedUploadMethod);
            }

            //  determine the seed uploader that should be used ...
            if ( seedUploadMethod.equalsIgnoreCase("none") ) {
                return "no uploader specified";
            }

            final yacySeedUploader uploader = getSeedUploader(seedUploadMethod);
            if ( uploader == null ) {
                final String errorMsg =
                    "Unable to get the proper uploader-class for seed uploading method '"
                        + seedUploadMethod
                        + "'.";
                log.warn("SaveSeedList: " + errorMsg);
                return errorMsg;
            }

            // ensure that the seed file url is configured properly
            DigestURL seedURL;
            try {
                final String seedURLStr = sb.peers.mySeed().get(Seed.SEEDLISTURL, "");
                if ( seedURLStr.isEmpty() ) {
                    throw new MalformedURLException("The seed-file url must not be empty.");
                }
                if ( !(seedURLStr.toLowerCase().startsWith("http://") || seedURLStr.toLowerCase().startsWith(
                    "https://")) ) {
                    throw new MalformedURLException("Unsupported protocol.");
                }
                seedURL = new DigestURL(seedURLStr);
                final String host = seedURL.getHost();
                if (Domains.isIntranet(host)) { // check seedlist reacheable
                    final String errorMsg = "seedURL in local network rejected (local hosts can't be reached from outside)";
                    log.warn("SaveSeedList: " + errorMsg);
                    return errorMsg;
                }
            } catch (final MalformedURLException e ) {
                final String errorMsg =
                    "Malformed seed file URL '"
                        + sb.peers.mySeed().get(Seed.SEEDLISTURL, "")
                        + "'. "
                        + e.getMessage();
                log.warn("SaveSeedList: " + errorMsg);
                return errorMsg;
            }

            // upload the seed-list using the configured uploader class
            String prevStatus = sb.peers.mySeed().get(Seed.PEERTYPE, Seed.PEERTYPE_JUNIOR);
            if ( prevStatus.equals(Seed.PEERTYPE_PRINCIPAL) ) {
                prevStatus = Seed.PEERTYPE_SENIOR;
            }

            try {
                sb.peers.mySeed().put(Seed.PEERTYPE, Seed.PEERTYPE_PRINCIPAL); // this information shall also be uploaded

                if ( log.isFine() ) {
                    log.fine("SaveSeedList: Using seed uploading method '"
                        + seedUploadMethod
                        + "' for seed-list uploading."
                        + "\n\tPrevious peerType is '"
                        + sb.peers.mySeed().get(Seed.PEERTYPE, Seed.PEERTYPE_JUNIOR)
                        + "'.");
                }

                logt = sb.peers.uploadSeedList(uploader, sb, sb.peers, seedURL);
                if ( logt != null ) {
                    if ( logt.indexOf("Error", 0) >= 0 ) {
                        sb.peers.mySeed().put(Seed.PEERTYPE, prevStatus);
                        final String errorMsg =
                            "SaveSeedList: seed upload failed using "
                                + uploader.getClass().getName()
                                + " (error): "
                                + logt.substring(logt.indexOf("Error", 0) + 6);
                        log.severe(errorMsg);
                        return errorMsg;
                    }
                    log.info(logt);
                }

                // finally, set the principal status
                sb.setConfig("yacyStatus", Seed.PEERTYPE_PRINCIPAL);
                return null;
            } catch (final Exception e ) {
                sb.peers.mySeed().put(Seed.PEERTYPE, prevStatus);
                sb.setConfig("yacyStatus", prevStatus);
                final String errorMsg = "SaveSeedList: Seed upload failed (IO error): " + e.getMessage();
                log.info(errorMsg, e);
                return errorMsg;
            }
        } finally {
            sb.peers.lastSeedUpload_seedDBSize = sb.peers.sizeConnected();
            sb.peers.lastSeedUpload_timeStamp = System.currentTimeMillis();
            sb.peers.lastSeedUpload_myIP = sb.peers.mySeed().getIP();
        }
    }

}
