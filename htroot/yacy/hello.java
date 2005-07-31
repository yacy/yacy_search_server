//hello.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//last major change: 30.06.2004
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

//You must compile this file with
//javac -classpath .:../../Classes hello.java
//if the shell's current path is HTROOT

import java.net.InetAddress;
import java.util.Date;

import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class hello {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects(); // return variable that accumulates replacements
        
        if ((post == null) ||
                (env == null) ||
                (yacyCore.seedDB == null) ||
                (yacyCore.seedDB.mySeed == null)) return new serverObjects();
        
        String iam     = (String) post.get("iam","");      // complete seed of the requesting peer
        String key     = (String) post.get("key","");      // transmission key for response
        String seed    = (String) post.get("seed","");     // 
        String pattern = (String) post.get("pattern","");  // 
        String countStr= (String) post.get("count","0");   // 
        String mytime  = (String) post.get("mytime","");   // 
        int    count   = 0;
        try {count = (countStr == null) ? 0 : Integer.parseInt(countStr);} catch (NumberFormatException e) {count = 0;}
        Date remoteTime = yacyCore.parseUniversalDate((String) post.get("mytime")); // read remote time
        yacySeed remoteSeed = yacySeed.genRemoteSeed(seed, key, remoteTime);
        
        //System.out.println("YACYHELLO: REMOTESEED=" + ((remoteSeed == null) ? "NULL" : remoteSeed.toString()));
        if (remoteSeed == null) return new serverObjects();
        
        // we easily know the caller's IP:
        String clientip = (String) header.get("CLIENTIP", "<unknown>"); // read an artificial header addendum
        String reportedip = remoteSeed.get("IP", "");
        String reportedPeerType = remoteSeed.get("PeerType", "junior");
        float clientversion = remoteSeed.getVersion();

        int urls = -1;
        // if the remote client has reported its own IP address and the client supports
        // the port forwarding feature (if client version >= 0.383) then we try to 
        // connect to the reported IP address first
        if ((reportedip.length() > 0) && (!(clientip.equals(reportedip))) && (clientversion >= (float)0.383)) {
            // try first the reportedip, since this may be a connect from a port-forwarding host
            prop.put("yourip", reportedip);
            remoteSeed.put("IP", reportedip);
            urls = yacyClient.queryUrlCount(remoteSeed);
        }
        
        // if the previous attempt (using the reported ip address) was not successful, try the ip where 
        // the request came from
        if (urls < 0) {                        
            boolean isLocalIP = false;
            if (serverCore.portForwardingEnabled) {
                try {
                    InetAddress clientAddress = InetAddress.getByName(clientip);
                    InetAddress[] localAddress = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
                    for (int i=0; i<localAddress.length; i++) {
                        if (localAddress[i].equals(clientAddress)) {
                            isLocalIP = true;
                            break;
                        }
                    }  
                } catch (Exception e) {}
            }
            
            // we are only allowed to connect to the client IP address if it's not our own address
            if (!isLocalIP) {
                prop.put("yourip", clientip);
                remoteSeed.put("IP", clientip);
                urls = yacyClient.queryUrlCount(remoteSeed);
            }
        }
        
        //System.out.println("YACYHELLO: YOUR IP=" + clientip);
        
        // assign status
        if (urls >= 0) {
            if (remoteSeed.get("PeerType", "senior") == null) {
                prop.put("yourtype", "senior");
                remoteSeed.put("PeerType", "senior");
            } else if (remoteSeed.get("PeerType", "principal").equals("principal")) {
                prop.put("yourtype", "principal");
            } else {
                prop.put("yourtype", "senior");
                remoteSeed.put("PeerType", "senior");
            }
            // connect the seed
            yacyCore.peerActions.peerArrival(remoteSeed, true);
        } else {
            prop.put("yourtype", "junior");
            remoteSeed.put("LastSeen", yacyCore.universalDateShortString());
            yacyCore.peerActions.juniorConnects++; // update statistics
            remoteSeed.put("PeerType", "junior");
            yacyCore.log.logInfo("hello: responded remote junior peer '" + remoteSeed.getName() + "' from " + reportedip);
            // no connection here, instead store junior in connection cache
            if ((remoteSeed.hash != null) && (remoteSeed.isProper() == null)) yacyCore.peerActions.peerPing(remoteSeed);
        }
        if (!((String)prop.get("yourtype")).equals(reportedPeerType)) {
            yacyCore.log.logInfo("hello: changing remote peer '" + remoteSeed.getName() + "' [" + reportedip + "] peerType from '" + reportedPeerType + "' to '" + prop.get("yourtype") + "'.");
        }
        
        String seeds = "";
        
        // attach also my own seed
        seeds += "seed0=" + yacyCore.seedDB.mySeed.genSeedStr(key) + serverCore.crlfString;
        
        // attach some more seeds, as requested
        if (yacyCore.seedDB != null) {
            if (count > yacyCore.seedDB.sizeConnected()) count = yacyCore.seedDB.sizeConnected();
            if (count > 100) count = 100;
            yacySeed[] ys = yacyCore.seedDB.seedsByAge(true, count); // latest seeds
            int c = 1;
            for (int i = 1; i < ys.length; i++) {
                if ((ys[i] != null) && (ys[i].isProper() == null)) {
                    seeds += "seed" + c + "=" + ys[i].genSeedStr(key) + serverCore.crlfString;
                    c++;
                }
            }
        }
        
        prop.put("mytime", yacyCore.universalDateShortString());
        prop.put("seedlist", seeds);
        // return rewrite properties
        return prop;
    }
    
}
