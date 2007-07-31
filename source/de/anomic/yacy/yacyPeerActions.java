// yacyPeerActions.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
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

package de.anomic.yacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.tools.nxTools;

public class yacyPeerActions {
   
    private yacySeedDB seedDB;
    private plasmaSwitchboard sb;
    private HashSet actions;
    private HashMap userAgents;
    public  long juniorConnects;
    public  long seniorConnects;
    public  long principalConnects;
    public  long disconnects;
    private int  bootstrapLoadTimeout;
    
    public yacyPeerActions(yacySeedDB seedDB, plasmaSwitchboard switchboard) {
        this.seedDB = seedDB;
        this.sb = switchboard;
        this.actions = new HashSet();
        this.userAgents = new HashMap();
        this.juniorConnects = 0;
        this.seniorConnects = 0;
        this.principalConnects = 0;
        this.disconnects = 0;
        this.bootstrapLoadTimeout = (int) switchboard.getConfigLong("bootstrapLoadTimeout", 6000);
    }
    
    public void deploy(yacyPeerAction action) {
        actions.add(action);
    }
    
    public void updateMySeed() {
        if (sb.getConfig("peerName", "anomic").equals("anomic")) {
            // generate new peer name
            sb.setConfig("peerName", yacySeed.makeDefaultPeerName());
        }
        seedDB.mySeed.put(yacySeed.NAME, sb.getConfig("peerName", "nameless"));
        if ((serverCore.portForwardingEnabled) && (serverCore.portForwarding != null)) {
            seedDB.mySeed.put(yacySeed.PORT, Integer.toString(serverCore.portForwarding.getPort()));
        } else {
            seedDB.mySeed.put(yacySeed.PORT, Integer.toString(serverCore.getPortNr(sb.getConfig("port", "8080"))));
        }
        
        long uptime = (System.currentTimeMillis() - sb.startupTime) / 1000;
		long uptimediff = uptime - sb.lastseedcheckuptime;
		long indexedcdiff = sb.indexedPages - sb.lastindexedPages;
        //double requestcdiff = sb.requestedQueries - sb.lastrequestedQueries;
        if (uptimediff > 300 || uptimediff <= 0 || sb.lastseedcheckuptime == -1 ) {
			sb.lastseedcheckuptime = uptime;
			sb.lastindexedPages = sb.indexedPages;
            sb.lastrequestedQueries = sb.requestedQueries;
		}
        
        //the speed of indexing (pages/minute) of the peer
        sb.totalPPM = (int) (sb.indexedPages * 60 / Math.max(uptime, 1));
        seedDB.mySeed.put(yacySeed.ISPEED, Long.toString(Math.round(Math.max((float) indexedcdiff, 0f) * 60f / Math.max((float) uptimediff, 1f))));
        sb.totalQPM = sb.requestedQueries * 60d / Math.max((double) uptime, 1d);
        seedDB.mySeed.put(yacySeed.RSPEED, Double.toString(sb.totalQPM /*Math.max((float) requestcdiff, 0f) * 60f / Math.max((float) uptimediff, 1f)*/ ));
        
        seedDB.mySeed.put(yacySeed.UPTIME, Long.toString(uptime/60)); // the number of minutes that the peer is up in minutes/day (moving average MA30)
        seedDB.mySeed.put(yacySeed.LCOUNT, Integer.toString(sb.wordIndex.loadedURL.size())); // the number of links that the peer has stored (LURL's)
        seedDB.mySeed.put(yacySeed.NCOUNT, Integer.toString(sb.noticeURL.stackSize())); // the number of links that the peer has noticed, but not loaded (NURL's)
        seedDB.mySeed.put(yacySeed.ICOUNT, Integer.toString(sb.wordIndex.size())); // the minimum number of words that the peer has indexed (as it says)
        seedDB.mySeed.put(yacySeed.SCOUNT, Integer.toString(seedDB.sizeConnected())); // the number of seeds that the peer has stored
        seedDB.mySeed.put(yacySeed.CCOUNT, Double.toString(((int) ((seedDB.sizeConnected() + seedDB.sizeDisconnected() + seedDB.sizePotential()) * 60.0 / (uptime + 1.01)) * 100) / 100.0)); // the number of clients that the peer connects (as connects/hour)
        seedDB.mySeed.put(yacySeed.VERSION, sb.getConfig("version", ""));
        if (seedDB.mySeed.get(yacySeed.PEERTYPE,"").equals(yacySeed.PEERTYPE_PRINCIPAL)) {
            // attach information about seed location
            seedDB.mySeed.put("seedURL", sb.getConfig("seedURL", ""));
        }
        seedDB.mySeed.setFlagDirectConnect(true);
        seedDB.mySeed.setLastSeenUTC();
        seedDB.mySeed.put(yacySeed.UTC, serverDate.UTCDiffString());
        seedDB.mySeed.setFlagAcceptRemoteCrawl(sb.getConfig("crawlResponse", "").equals("true"));
        seedDB.mySeed.setFlagAcceptRemoteIndex(sb.getConfig("allowReceiveIndex", "").equals("true"));
        //mySeed.setFlagAcceptRemoteIndex(true);
    }
            
    public void saveMySeed() {
        try {
          seedDB.mySeed.save(seedDB.myOwnSeedFile);
        } catch (IOException e) {}
    }

    public void loadSeedLists() {
        // uses the superseed to initialize the database with known seeds
        
        yacySeed     ys;
        String       seedListFileURL;
        URL          url;
        ArrayList    seedList;
        Iterator     enu;
        int          lc;
        int          sc = seedDB.sizeConnected();
        httpHeader   header;
        
        yacyCore.log.logInfo("BOOTSTRAP: " + sc + " seeds known from previous run");
        
        // - use the superseed to further fill up the seedDB
        int ssc = 0, c = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) break;
            seedListFileURL = sb.getConfig("network.unit.bootstrap.seedlist" + c, "");
            if (seedListFileURL.length() == 0) break;
            c++;
            if (
                    seedListFileURL.startsWith("http://") || 
                    seedListFileURL.startsWith("https://")
            ) {
                // load the seed list
                try {
                    httpHeader reqHeader = new httpHeader();
                    reqHeader.put(httpHeader.PRAGMA,"no-cache");
                    reqHeader.put(httpHeader.CACHE_CONTROL,"no-cache");
                    
                    url = new URL(seedListFileURL);
                    long start = System.currentTimeMillis();
                    header = httpc.whead(url, url.getHost(), this.bootstrapLoadTimeout, null, null, this.sb.remoteProxyConfig,reqHeader);
                    long loadtime = System.currentTimeMillis() - start;
                    if (header == null) {
                        if (loadtime > this.bootstrapLoadTimeout) {
                            yacyCore.log.logWarning("BOOTSTRAP: seed-list URL " + seedListFileURL + " not available, time-out after " + loadtime + " milliseconds");
                        } else {
                            yacyCore.log.logWarning("BOOTSTRAP: seed-list URL " + seedListFileURL + " not available, no content");
                        }
                    } else if (header.lastModified() == null) {
                        yacyCore.log.logWarning("BOOTSTRAP: seed-list URL " + seedListFileURL + " not usable, last-modified is missing");
                    } else if ((header.age() > 86400000) && (ssc > 0)) {
                        yacyCore.log.logInfo("BOOTSTRAP: seed-list URL " + seedListFileURL + " too old (" + (header.age() / 86400000) + " days)");
                    } else {
                        ssc++;
                        seedList = nxTools.strings(httpc.wget(url, url.getHost(), this.bootstrapLoadTimeout, null, null, this.sb.remoteProxyConfig,reqHeader, null), "UTF-8");
                        enu = seedList.iterator();
                        lc = 0;
                        while (enu.hasNext()) {
                            ys = yacySeed.genRemoteSeed((String) enu.next(), null, true);
                            if ((ys != null) && (ys.isProper() == null) &&
                                    ((seedDB.mySeed == null) || (seedDB.mySeed.hash != ys.hash))) {
                                if (connectPeer(ys, false)) lc++;
                                //seedDB.writeMap(ys.hash, ys.getMap(), "init");
                                //System.out.println("BOOTSTRAP: received peer " + ys.get(yacySeed.NAME, "anonymous") + "/" + ys.getAddress());
                                //lc++;
                            }
                        }
                        yacyCore.log.logInfo("BOOTSTRAP: " + lc + " seeds from seed-list URL " + seedListFileURL + ", AGE=" + (header.age() / 3600000) + "h");
                    }
                    
                } catch (IOException e) {
                    // this is when wget fails, commonly because of timeout
                    yacyCore.log.logWarning("BOOTSTRAP: failed to load seeds from seed-list URL " + seedListFileURL + ": " + e.getMessage());
                } catch (Exception e) {
                    // this is when wget fails; may be because of missing internet connection
                    yacyCore.log.logSevere("BOOTSTRAP: failed to load seeds from seed-list URL " + seedListFileURL + ": " + e.getMessage());
                }
            }
        }
        yacyCore.log.logInfo("BOOTSTRAP: " + (seedDB.sizeConnected() - sc) + " new seeds while bootstraping.");
    }

    private synchronized boolean connectPeer(yacySeed seed, boolean direct) {
        // store a remote peer's seed
        // returns true if the peer is new and previously unknown
        if (seed == null) {
            yacyCore.log.logSevere("connect: WRONG seed (NULL)");
            return false;
        }
        final String error = seed.isProper();
        if (error != null) {
            yacyCore.log.logSevere("connect: WRONG seed (" + seed.getName() + "/" + seed.hash + "): " + error);
            return false;
        }
        if ((this.seedDB.mySeed != null) && (seed.hash.equals(this.seedDB.mySeed.hash))) {
            yacyCore.log.logInfo("connect: SELF reference " + seed.getPublicAddress());
            return false;
        }
        final String peerType = seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);

        if ((peerType.equals(yacySeed.PEERTYPE_VIRGIN)) || (peerType.equals(yacySeed.PEERTYPE_JUNIOR))) {
            // reject unqualified seeds
            yacyCore.log.logFine("connect: rejecting NOT QUALIFIED " + peerType + " seed " + seed.getName());
            return false;
        }

        final yacySeed doubleSeed = this.seedDB.lookupByIP(seed.getInetAddress(), true, false, false);
        if ((doubleSeed != null) && (doubleSeed.getPort() == seed.getPort()) && (!(doubleSeed.hash.equals(seed.hash)))) {
            // a user frauds with his peer different peer hashes
            yacyCore.log.logFine("connect: rejecting FRAUD (double hashes " + doubleSeed.hash + "/" + seed.hash + " on same port " + seed.getPort() + ") peer " + seed.getName());
            return false;
        }

        if (seed.get(yacySeed.LASTSEEN, "").length() != 14) {
            // hack for peers that do not have a LastSeen date
            seed.setLastSeenUTC();
            yacyCore.log.logFine("connect: reset wrong date (" + seed.getName() + "/" + seed.hash + ")");
        }

        // connection time
        final long nowUTC0Time = System.currentTimeMillis(); // is better to have this value in a variable for debugging
        long ctimeUTC0 = seed.getLastSeenUTC();

        if (ctimeUTC0 > nowUTC0Time) {
            // the peer is future-dated, correct it
            seed.setLastSeenUTC();
            ctimeUTC0 = nowUTC0Time;
            assert (seed.getLastSeenUTC() - ctimeUTC0 < 100);
        }
        if (Math.abs(nowUTC0Time - ctimeUTC0) > 60 * 60 * 24 * 1000) {
            // the new connection is out-of-age, we reject the connection
            yacyCore.log.logFine("connect: rejecting out-dated peer '" + seed.getName() + "' from " + seed.getPublicAddress() + "; nowUTC0=" + nowUTC0Time + ", seedUTC0=" + ctimeUTC0 + ", TimeDiff=" + serverDate.intervalToString(Math.abs(nowUTC0Time - ctimeUTC0)));
            return false;
        }

        // disconnection time
        long dtimeUTC0;
        final yacySeed disconnectedSeed = seedDB.getDisconnected(seed.hash);
        if (disconnectedSeed == null) {
            dtimeUTC0 = 0; // never disconnected: virtually disconnected maximum time ago
        } else {
            dtimeUTC0 = Long.parseLong(disconnectedSeed.get("dct", "0"));
        }

        if (direct) {
            // remember the moment
            // Date applies the local UTC offset, which is wrong
            // we correct that by subtracting the local offset and adding
            // the remote offset.
            seed.setLastSeenUTC();
            seed.setFlagDirectConnect(true);
        } else {
            // set connection flag
            if (Math.abs(nowUTC0Time - ctimeUTC0) > 120000) seed.setFlagDirectConnect(false); // 2 minutes
        }

        // update latest version number
        if (seed.getVersion() > yacyVersion.latestRelease) yacyVersion.latestRelease = seed.getVersion();

        // prepare to update
        if (disconnectedSeed != null) {
            // if the indirect connect aims to announce a peer that we know
            // has been disconnected then we compare the dates:
            // if the new peer has a LastSeen date, and that date is before
            // the disconnection date, then we ignore the new peer
            if (!direct) {
                if (ctimeUTC0 < dtimeUTC0) {
                    // the disconnection was later, we reject the connection
                    yacyCore.log.logFine("connect: rejecting disconnected peer '" + seed.getName() + "' from " + seed.getPublicAddress());
                    return false;
                }
            }

            // this is a return of a lost peer
            yacyCore.log.logFine("connect: returned KNOWN " + peerType + " peer '" + seed.getName() + "' from " + seed.getPublicAddress());
            this.seedDB.addConnected(seed);
            return true;
        } else {
            final yacySeed connectedSeed = this.seedDB.getConnected(seed.hash);
            if (connectedSeed != null) {
                // the seed is known: this is an update
                try {
                    // if the old LastSeen date is later then the other
                    // info, then we reject the info
                    if ((ctimeUTC0 < (connectedSeed.getLastSeenUTC())) && (!direct)) {
                        yacyCore.log.logFine("connect: rejecting old info about peer '" + seed.getName() + "'");
                        return false;
                    }

                    if (connectedSeed.getName() != seed.getName()) {
                        // TODO: update seed name lookup cache
                    }
                } catch (NumberFormatException e) {
                    yacyCore.log.logFine("connect: rejecting wrong peer '" + seed.getName() + "' from " + seed.getPublicAddress() + ". Cause: " + e.getMessage());
                    return false;
                }
                yacyCore.log.logFine("connect: updated KNOWN " + ((direct) ? "direct " : "") + peerType + " peer '" + seed.getName() + "' from " + seed.getPublicAddress());
                seedDB.addConnected(seed);
                return true;
            } else {
                // the seed is new
                if (seed.get(yacySeed.IP, "127.0.0.1").equals(this.seedDB.mySeed.get(yacySeed.IP, "127.0.0.1"))) {
                    // seed from the same IP as the calling client: can be
                    // the case if there runs another one over a NAT
                    yacyCore.log.logFine("connect: saved NEW seed (myself IP) " + seed.getPublicAddress());
                } else {
                    // completely new seed
                    yacyCore.log.logFine("connect: saved NEW " + peerType + " peer '" + seed.getName() + "' from " + seed.getPublicAddress());
                }
                if (peerType.equals(yacySeed.PEERTYPE_SENIOR))
                    this.seniorConnects++; // update statistics
                if (peerType.equals(yacySeed.PEERTYPE_PRINCIPAL))
                    this.principalConnects++; // update statistics
                this.seedDB.addConnected(seed);
                return true;
            }
        }
    }

    private final void disconnectPeer(yacySeed seed) {
        // we do this if we did not get contact with the other peer
	    yacyCore.log.logFine("connect: no contact to a " + seed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN) + " peer '" + seed.getName() + "' at " + seed.getPublicAddress());
        synchronized (seedDB) {
	        if (!seedDB.hasDisconnected(seed.hash)) { disconnects++; }
            seed.put("dct", Long.toString(System.currentTimeMillis()));
	        seedDB.addDisconnected(seed); // update info
        }
    }

    public boolean peerArrival(yacySeed peer, boolean direct) {
        if (peer == null) return false;
        boolean res = connectPeer(peer, direct);
        // perform all actions if peer is effective new
        if (res) {
            Iterator i = actions.iterator();
            while (i.hasNext()) ((yacyPeerAction) i.next()).processPeerArrival(peer, direct);
        }
        return res;
    }
    
    public void peerDeparture(yacySeed peer) {
        if (peer == null) return;
        //System.out.println("PEER DEPARTURE:" + peer.toString());
        disconnectPeer(peer);
        // perform all actions
        Iterator i = actions.iterator();
        while (i.hasNext()) ((yacyPeerAction) i.next()).processPeerDeparture(peer);
    }
    
    public void peerPing(yacySeed peer) {
        if (peer == null) return;
        // this is called only if the peer has junior status
        seedDB.addPotential(peer);
        // perform all actions
        Iterator i = actions.iterator();
        while (i.hasNext()) ((yacyPeerAction) i.next()).processPeerPing(peer);
    }
    
    public void setUserAgent(String IP, String userAgent) {
        userAgents.put(IP, userAgent);
    }
    
    public String getUserAgent(String IP) {
        String userAgent = (String) userAgents.get(IP);
        return (userAgent == null) ? "" : userAgent;
    }
}
