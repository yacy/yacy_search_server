// yacyPeerActions.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 22.02.2005
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverSystem;
import de.anomic.tools.disorderSet;

public class yacyPeerActions {
   
    private yacySeedDB seedDB;
    private plasmaSwitchboard sb;
    private HashSet actions;
    private File superseedFile;
    private String superseedURL;
    public  long juniorConnects;
    public  long seniorConnects;
    public  long principalConnects;
    public  long disconnects;
    
    public yacyPeerActions(yacySeedDB seedDB, plasmaSwitchboard switchboard, 
			 File superseedFile,
			 String superseedURL) throws IOException {
        this.seedDB = seedDB;
        this.sb = switchboard;
        this.actions = new HashSet();
        this.superseedFile = superseedFile;
	this.superseedURL = superseedURL;
        this.superseedURL = superseedURL;
        this.juniorConnects = 0;
        this.seniorConnects = 0;
        this.principalConnects = 0;
        this.disconnects = 0;
    }
    
    public void deploy(yacyPeerAction action) {
        actions.add(action);
    }
    
    public void updateMySeed() {
    	    if (sb.getConfig("peerName", "nameless").equals("nameless")) sb.setConfig("peerName", serverCore.publicIP().getHostName() + yacyCore.speedKey + serverSystem.infoKey() + (System.currentTimeMillis() & 99));
	    seedDB.mySeed.put("Name", sb.getConfig("peerName", "nameless"));
	    seedDB.mySeed.put("Port", sb.getConfig("port", "8080"));
	    seedDB.mySeed.put("ISpeed", "unknown"); // the speed of indexing (words/minute) of the peer
	    long uptime = ((yacyCore.universalTime() - Long.parseLong(sb.getConfig("startupTime", "0"))) / 1000) / 60;
	    seedDB.mySeed.put("Uptime", "" + uptime); // the number of minutes that the peer is up in minutes/day (moving average MA30)
	    seedDB.mySeed.put("LCount", "" + sb.lUrlSize()); // the number of links that the peer has stored (LURL's)
	    seedDB.mySeed.put("ICount", "" + sb.cacheSizeMin()); // the minimum number of words that the peer has indexed (as it says)
	    seedDB.mySeed.put("SCount", "" + seedDB.sizeConnected()); // the number of seeds that the peer has stored
	    seedDB.mySeed.put("CCount", "" + (((int) ((seedDB.sizeConnected() + seedDB.sizeDisconnected() + seedDB.sizePotential()) * 60.0 / (uptime + 1.01)) * 100) / 100.0)); // the number of clients that the peer connects (as connects/hour)
	    seedDB.mySeed.put("Version", sb.getConfig("version", ""));
	    if (seedDB.mySeed.get("PeerType","").equals("principal")) {
		// attach information about seed location
		seedDB.mySeed.put("seedURL", sb.getConfig("seedURL", ""));
	    }
            seedDB.mySeed.setFlagDirectConnect(true);
            seedDB.mySeed.put("LastSeen", yacyCore.universalDateShortString());
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
	Vector       seedList;
	Enumeration  enu;
        int          lc;
        int          sc = seedDB.sizeConnected();
        httpHeader   header;
        
        yacyCore.log.logInfo("BOOTSTRAP: " + sc + " seeds known from previous run");

        // - load the superseed: a list of URL's
	disorderSet superseed = loadSuperseed(superseedFile, superseedURL);

        // - use the superseed to further fill up the seedDB
        int ssc = 0;
	for (int i = 0; i < superseed.size(); i++) {
	    seedListFileURL = (String) superseed.any();
	    if (seedListFileURL.startsWith("http://")) {
		// load the seed list
		try {
                    url = new URL(seedListFileURL);
                    header = httpc.whead(url, 5000, null, null, sb.remoteProxyHost, sb.remoteProxyPort);
                    if ((header == null) || (header.lastModified() == null)) {
                        yacyCore.log.logInfo("BOOTSTRAP: seed-list url " + seedListFileURL + " not available");
                    } else if ((header.age() > 86400000) && (ssc > 0)) {
                        yacyCore.log.logInfo("BOOTSTRAP: seed-list url " + seedListFileURL + " too old (" + (header.age() / 86400000) + " days)");
                    } else {
                        ssc++;
                        seedList = httpc.wget(url, 5000, null, null, sb.remoteProxyHost, sb.remoteProxyPort);
                        enu = seedList.elements();
                        lc = 0;
                        while (enu.hasMoreElements()) {
                            ys = yacySeed.genRemoteSeed((String) enu.nextElement(), null, new Date());
                            if ((ys != null) && (ys.isProper()) &&
                            ((seedDB.mySeed == null) || (seedDB.mySeed.hash != ys.hash))) {
                                if (connectPeer(ys, false)) lc++;
                                //seedDB.writeMap(ys.hash, ys.getMap(), "init");
                                //System.out.println("BOOTSTRAP: received peer " + ys.get("Name", "anonymous") + "/" + ys.getAddress());
                                //lc++;
                            }
                        }
                        yacyCore.log.logInfo("BOOTSTRAP: " + lc + " seeds from seed-list url " + seedListFileURL + ", AGE=" + (header.age() / 3600000) + "h");
                    }
                    
		} catch (Exception e) {
		    // this is when wget fails; may be because of missing internet connection
		    // we do nothing here and go silently over it
                    System.out.println("BOOTSTRAP: failed to load seeds from seed-list url " + seedListFileURL);
		}
	    }
  	}
        yacyCore.log.logInfo("BOOTSTRAP: " + (seedDB.sizeConnected() - sc) + " new seeds while bootstraping.");
    }

    private disorderSet loadSuperseed(File local, String url) {
	// this returns a list of locations where seed list-files can be found
	disorderSet supsee = new disorderSet();
	String line;
	// read in local file
        int lc = 0;
        try {
	    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(local)));
	    while ((line = br.readLine()) != null) {
		line = line.trim();
		//System.out.println("one line in file:" + line);
		if (line.length() > 0) supsee.add(line);
	    }
	    br.close();
            lc = supsee.size();
            yacyCore.log.logInfo("BOOTSTRAP: " + lc + " seed-list urls from superseed file " + local.toString());
	} catch (IOException e) {
	    //e.printStackTrace();
	    supsee = new disorderSet();
            yacyCore.log.logInfo("BOOTSTRAP: failed to load seed-list urls from superseed file " + local.toString() + ": " + e.getMessage());
	}
	// read in remote file from url
        try {
            Vector remote = httpc.wget(new URL(url), 5000, null, null, sb.remoteProxyHost, sb.remoteProxyPort);
            if ((remote != null) && (remote.size() > 0)) {
                Enumeration e = remote.elements();
                while (e.hasMoreElements()) {
                    line = (String) e.nextElement();
                    if (line != null) {
                        line = line.trim();
                        supsee.add(line);
                    }
                }
            }
            yacyCore.log.logInfo("BOOTSTRAP: " + (supsee.size() - lc) + " seed-list urls from superseed URL " + url);
        } catch (Exception e) {
	    supsee = new disorderSet();
            yacyCore.log.logInfo("BOOTSTRAP: failed to load seed-list urls from superseed URL " + url + ": " + e.getMessage());        
        }
	return supsee;
    }

    synchronized public boolean connectPeer(yacySeed seed, boolean direct) {
	// store a remote peer's seed
	// returns true if the peer is new and previously unknown
	if (seed == null) {
	    yacyCore.log.logInfo("connect: WRONG seed (NULL)");
	    return false;
	} else if (!(seed.isProper())) {
	    yacyCore.log.logInfo("connect: WRONG seed (" + seed.getName() + "/" + seed.hash + ")");
	    return false;
	} else if ((seedDB.mySeed != null) && (seed.hash.equals(seedDB.mySeed.hash))) {
	    yacyCore.log.logInfo("connect: SELF reference " + seed.getAddress());
	    return false;
	} else {
            String peerType = seed.get("PeerType", "virgin");
            // reject unqualified seeds
            if ((peerType.equals("virgin")) || (peerType.equals("junior"))) {
                yacyCore.log.logDebug("connect: rejecting NOT QUALIFIED " + peerType + " seed " + seed.getName());
                return false;
            }
            
	    // we may store that seed, but still have different cases
	    if (seed.get("LastSeen", "").length() < 14) {
		// hack for peers that do not have a LastSeen date
		seed.put("LastSeen", "20040101000000");
	    }
            
            // connection time
            long ctime;
            try {
                ctime = yacyCore.shortFormatter.parse(seed.get("LastSeen", "20040101000000")).getTime();
                // maybe correct it slightly
                if (ctime > yacyCore.universalTime()) {
                    ctime = ((2 * ctime) + yacyCore.universalTime()) / 3;
                    seed.put("LastSeen", yacyCore.shortFormatter.format(new Date(ctime)));
                }
            } catch (java.text.ParseException e) {
                ctime = yacyCore.universalTime();
            }
            
            // disconnection time
            long dtime;
            yacySeed disconnectedSeed = seedDB.getDisconnected(seed.hash);
            if (disconnectedSeed == null) {
                dtime = 0; // never disconnected: virtually disconnected maximum time ago
            } else try {
                dtime = yacyCore.shortFormatter.parse((String) disconnectedSeed.get("disconnected", "20040101000000")).getTime();
            } catch (java.text.ParseException e) {
                dtime = 0;
            }
       
	    if (direct) {
		// remember the moment
                ctime = yacyCore.universalTime();
		seed.put("LastSeen", yacyCore.shortFormatter.format(new Date(ctime)));
                seed.setFlagDirectConnect(true);
	    } else {
                // set connection flag
                if ((yacyCore.universalTime() - ctime) > 120000) seed.setFlagDirectConnect(false); // 2 minutes
            }

	    // prepare to update
	    if (disconnectedSeed != null) {
		// if the indirect connect aims to announce a peer that we know has been disconnected
		// then we compare the dates:
		// if the new peer has a LastSeen date, and that date is before the disconnection date,
		// then we ignore the new peer
                if (!(direct)) {
                    if (ctime < dtime) {
                        // the disconnection was later, we reject the connection
                        yacyCore.log.logDebug("connect: rejecting disconnected peer '" + seed.getName() + "' from " + seed.getAddress());
                        return false;
                    }
                    if ((yacyCore.universalTime() - ctime) > 3600000) {
                        // the new connection is out-of-age, we reject the connection
                        yacyCore.log.logDebug("connect: rejecting out-dated peer '" + seed.getName() + "' from " + seed.getAddress());
                        return false;
                    }
                    if ((yacyCore.universalTime() - ctime) > 3600000) {
                        // the new connection is future-dated, we reject the connection
                        yacyCore.log.logDebug("connect: rejecting future-dated peer '" + seed.getName() + "' from " + seed.getAddress());
                        return false;
                    }
                }
                
		// this is a return of a lost peer
		yacyCore.log.logDebug("connect: returned KNOWN " + peerType + " peer '" + seed.getName() + "' from " + seed.getAddress());
		seedDB.addConnected(seed);
		return false;
	    } else {
                yacySeed connectedSeed = seedDB.getConnected(seed.hash);
                if (connectedSeed != null) {
                    // the seed is known: this is an update
                    try {
                        // if the old LastSeen date is later then the other info, then we reject the info
                        if ((ctime < yacyCore.shortFormatter.parse(connectedSeed.get("LastSeen", "20040101000000")).getTime()) && (!(direct))) {
                            yacyCore.log.logDebug("connect: rejecting old info about peer '" + seed.getName() + "'");
                            return false;
                        }
                    } catch (java.text.ParseException e) {}
                    yacyCore.log.logDebug("connect: updated KNOWN " + ((direct) ? "direct " : "") +  peerType + " peer '" + seed.getName() + "' from " + seed.getAddress());
		    seedDB.addConnected(seed);
                    return false;
                } else {
                    // the seed is new
                    if (((String) seed.get("IP", "127.0.0.1")).equals((String) seedDB.mySeed.get("IP", "127.0.0.1"))) {
                        // seed from the same IP as the calling client: can be the case if there runs another one over a NAT
                        yacyCore.log.logDebug("connect: saved NEW seed (myself IP) " + seed.getAddress());
                    } else {
                        // completely new seed
                        yacyCore.log.logDebug("connect: saved NEW " + peerType + " peer '" + seed.getName() + "' from " + seed.getAddress());
                    }
                    if (peerType.equals("senior")) seniorConnects++; // update statistics
                    if (peerType.equals("principal")) principalConnects++; // update statistics
		    seedDB.addConnected(seed);
                    return true;
                }
                
            }
	}
    }

    synchronized public void disconnectPeer(yacySeed seed) {
	// we do this if we did not get contact with the other peer
	yacyCore.log.logDebug("connect: no contact to a " + seed.get("PeerType", "virgin") + " peer '" + seed.getName() + "' at " + seed.getAddress());
	if (!(seedDB.hasDisconnected(seed.hash))) disconnects++;
        seed.put("disconnected", yacyCore.universalDateShortString());
	seedDB.addDisconnected(seed); // update info
    }
    
    public boolean peerArrival(yacySeed peer, boolean direct) {
        boolean res = connectPeer(peer, direct);
        // perform all actions if peer is effective new
        if (res) {
            Iterator i = actions.iterator();
            while (i.hasNext()) ((yacyPeerAction) i.next()).processPeerArrival(peer, direct);
        }
        return res;
    }
    
    public void peerDeparture(yacySeed peer) {
        //System.out.println("PEER DEPARTURE:" + peer.toString());
        disconnectPeer(peer);
        // perform all actions
        Iterator i = actions.iterator();
        while (i.hasNext()) ((yacyPeerAction) i.next()).processPeerDeparture(peer);
    }
    
    public void peerPing(yacySeed peer) {
        // this is called only if the peer has junior status
        seedDB.addPotential(peer);
        // perform all actions
        Iterator i = actions.iterator();
        while (i.hasNext()) ((yacyPeerAction) i.next()).processPeerPing(peer);
    }
}
