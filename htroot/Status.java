// Status.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 12.07.2004
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

// You must compile this file with
// javac -classpath .:../Classes Status.java
// if the shell's current path is HTROOT

import de.anomic.http.httpHeader;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public class Status {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
	serverObjects prop = new serverObjects();

	// set values
	String s;
	int pos;

	/*
	  versionProbe=http://www.anomic.de/AnomicHTTPProxy/release.txt
	  superseedFile=superseed.txt
	*/
        // update seed info
        yacyCore.peerActions.updateMySeed();

	// password protection
	if (env.getConfig("adminAccountBase64MD5", "").length() == 0)
	    prop.put("protection", 0);//not protected
	else
	    prop.put("protection", 1);//protected

	// version information
	if ((yacyCore.latestVersion == null) || (yacyCore.latestVersion.length() < 3) || (yacyCore.latestVersion.equals(env.getConfig("version",""))))
	    prop.put("versioncomment", 0);//no commet
	else
	    prop.put("versioncomment", 1);//new version
	    prop.put("versioncomment_latestVersion", yacyCore.latestVersion);

	prop.put("host", serverCore.publicIP());
	prop.put("port", env.getConfig("port", "<unknown>"));
	if (env.getConfig("remoteProxyUse", "false").equals("true")) {
	    prop.put("remoteProxy", 1);
		prop.put("remoteProxy_host", env.getConfig("remoteProxyHost", "<unknown>"));
		prop.put("remoteProxy_port", env.getConfig("remoteProxyPort", "<unknown>"));
	} else {
	    prop.put("remoteProxy", 0);//not used
	}

	// peer information
	String thisHash = "";
	String thisName = env.getConfig("peerName", "<nameless>");
	if (yacyCore.seedDB.mySeed == null)  {
            thisHash = "not assigned";
            prop.put("peerAddress", 0);//not assigned
            prop.put("peerStatistics", 0);//unknown
        } else {
            prop.put("peerStatistics", 1);
            prop.put("peerStatistics_uptime", yacyCore.seedDB.mySeed.get("Uptime", "unknown"));
            prop.put("peerStatistics_links", yacyCore.seedDB.mySeed.get("LCount", "unknown"));
            prop.put("peerStatistics_words", yacyCore.seedDB.mySeed.get("ICount", "unknown"));
            prop.put("peerStatistics_juniorConnects", yacyCore.peerActions.juniorConnects);
            prop.put("peerStatistics_seniorConnects", yacyCore.peerActions.seniorConnects);
            prop.put("peerStatistics_principalConnects", yacyCore.peerActions.principalConnects);
            prop.put("peerStatistics_disconnects", yacyCore.peerActions.disconnects);
            prop.put("peerStatistics_connects", yacyCore.seedDB.mySeed.get("CCount", "0"));
            if (yacyCore.seedDB.mySeed.getAddress() == null) {
                thisHash = yacyCore.seedDB.mySeed.hash;
                prop.put("peerAddress", 1);//not assigned + instructions
            } else {
                thisHash = yacyCore.seedDB.mySeed.hash;
                prop.put("peerAddress", 2);//Address
                prop.put("peerAddress_address", yacyCore.seedDB.mySeed.getAddress());
                prop.put("peerAddress_peername", env.getConfig("peerName", "<nameless>").toLowerCase());
            }
        }
	String peerStatus = ((yacyCore.seedDB.mySeed == null) ? "virgin" : yacyCore.seedDB.mySeed.get("PeerType", "virgin"));
	if (peerStatus.equals("virgin")) {
	    prop.put("peerStatus", 0);//virgin
	} else if (peerStatus.equals("junior")) {
	    prop.put("peerStatus", 1);//junior
	} else if (peerStatus.equals("senior")) {
	    prop.put("peerStatus", 2);//senior
	} else if (peerStatus.equals("principal")) {
	    prop.put("peerStatus", 3);//principal
	    prop.put("peerStatus_seedURL", yacyCore.seedDB.mySeed.get("seedURL", "?"));
	}
	prop.put("peerName", thisName);
	prop.put("hash", thisHash);
	if ((env.getConfig("seedFTPServer","").length() != 0) && 
	    (env.getConfig("seedFTPAccount","").length() != 0) && 
	    (env.getConfig("seedFTPPassword","").length() != 0) && 
	    (env.getConfig("seedFTPPath","").length() != 0)) {
	    prop.put("seedServer", 1);//enabled
	    prop.put("seedServer_seedFTPServer", env.getConfig("seedFTPServer",""));
	} else {
	    prop.put("seedServer", 0);//disabled
	}

	if ((yacyCore.seedDB != null) && (yacyCore.seedDB.sizeConnected() > 0)){
		prop.put("otherPeers", 1);
		prop.put("otherPeers_num", yacyCore.seedDB.sizeConnected());
	}else{
		prop.put("otherPeers", 0);//not online
	}

	// pop-up trigger management
	if (post != null) {
	    if (post.containsKey("dispop")) env.setConfig("browserPopUpTrigger", "false");
	    if (post.containsKey("enpop")) env.setConfig("browserPopUpTrigger", "true");
	}

	if (env.getConfig("browserPopUpTrigger", "false").equals("false")) {
	    prop.put("popup", 0);
	} else {
	    prop.put("popup", 1); 
	}
	
	if (env.getConfig("onlineMode", "1").equals("1")) {
	    prop.put("omode", 1);
	} else {
	    prop.put("omode", 2);
	}

    // return rewrite properties
	return prop;
    }

}
