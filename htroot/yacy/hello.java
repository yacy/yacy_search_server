// hello.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
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
//
// You must compile this file with
// javac -classpath .:../../classes hello.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.net.InetAddress;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;

import de.anomic.search.Switchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyVersion;
import de.anomic.yacy.dht.PeerSelection;

public final class hello {

    // example:
    // http://localhost:8090/yacy/hello.html?count=1&seed=p|{Hash=sCJ6Tq8T0N9x,IPType=&empty;,Port=8090,IP=,Uptime=8,rI=190,Version=0.10004882,PeerType=junior,UTC=+0200,RCount=0,sI=0,LastSeen=20080605103333,Name=intratest,CCount=5.0,SCount=40,news=,USpeed=0,CRTCnt=0,CRWCnt=0,BDate=20080605081349,rU=190,LCount=187,dct=1212668923654,ICount=2,sU=0,ISpeed=0,RSpeed=0.0,NCount=0,Flags=oooo}
    // http://localhost:8090/yacy/hello.html?count=10&seed=z|H4sIAAAAAAAAADWQW2vDMAyF_81eJork3GyGX-YxGigly2WFvZTQijbQJsHx1pWx_z7nMj1J4ug7B_2s6-GsP5q3G-G6vBz2e0iz8t6zfuBr7-5PUNanQfulhqyzTkuUCFXvmitrBJtq4ed3tkPTtRpXhIiRDAmq0uhHFIiQMduJ-NXYU9NCbrrP1vnjIdUqgk09uIK51V6rMBRIilAo2NajwzfhGcx8QUKsEIp5iCJo-eaTVUXPfPQ4k5dm4pp8NzaESsLzS-14QVNIMlA-ka2m1JuZJJWIBRwPo0GIIiYp4zCSkC5GQSLiJIah0p6X_rvlS-MTbWdhkCSBIni9jA_rfP3-Ae1Oye9dAQAA
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) throws InterruptedException {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        long start = System.currentTimeMillis();
        prop.put("message", "none");
        if ((post == null) || (env == null)) {
            prop.put("message", "no post or no enviroment");
            return prop;
        }
        if (!yacyNetwork.authentifyRequest(post, env)) {
            prop.put("message", "not in my network");
            return prop;
        }
        
//      final String iam      = (String) post.get("iam", "");      // complete seed of the requesting peer
//      final String mytime   = (String) post.get(MYTIME, ""); //
        final String key      = post.get("key", "");      // transmission key for response
        final String seed     = post.get("seed", "");
        int  count            = post.getInt("count", 0);
        long  magic           = post.getLong("magic", 0);
//      final Date remoteTime = yacyCore.parseUniversalDate(post.get(MYTIME)); // read remote time
        final String clientip = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "<unknown>"); // read an artificial header addendum
        long time = System.currentTimeMillis();
        final InetAddress ias = Domains.dnsResolve(clientip);
        long time_dnsResolve = System.currentTimeMillis() - time;
        if (ias == null) {
            yacyCore.log.logInfo("hello/server: failed contacting seed; clientip not resolvable (clientip=" + clientip + ", time_dnsResolve=" + time_dnsResolve + ")");
            prop.put("message", "cannot resolve your IP from your reported location " + clientip);
            return prop;
        }
        if (seed.length() > yacySeed.maxsize) {
        	yacyCore.log.logInfo("hello/server: rejected contacting seed; too large (" + seed.length() + " > " + yacySeed.maxsize + ", time_dnsResolve=" + time_dnsResolve + ")");
            prop.put("message", "your seed is too long (" + seed.length() + ")");
            return prop;
        }
        yacySeed remoteSeed;
        try {
            remoteSeed = yacySeed.genRemoteSeed(seed, key, true, ias.getHostAddress());
        } catch (IOException e) {
            yacyCore.log.logInfo("hello/server: bad seed: " + e.getMessage() + ", time_dnsResolve=" + time_dnsResolve);
            prop.put("message", "bad seed: " + e.getMessage());
            return prop;
        }
        
        if (remoteSeed == null || remoteSeed.hash == null) {
            yacyCore.log.logInfo("hello/server: bad seed: null, time_dnsResolve=" + time_dnsResolve);
            prop.put("message", "cannot parse your seed");
            return prop;
        }
        
//      final String properTest = remoteSeed.isProper();
        // The remote peer might not know its IP yet, so don't abort if the IP check fails
//      if ((properTest != null) && (! properTest.substring(0,1).equals("IP"))) { return null; }

        // we easily know the caller's IP:
        final String userAgent = header.get(HeaderFramework.USER_AGENT, "<unknown>");
        final String reportedip = remoteSeed.getIP();
        final String reportedPeerType = remoteSeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR);
        final float clientversion = remoteSeed.getVersion();

        if (sb.isRobinsonMode() && !sb.isPublicRobinson()) {
        	// if we are a robinson cluster, answer only if this client is known by our network definition
            prop.put("message", "I am robinson, I do not answer");
            return prop;
        }
        
        long[] callback = new long[]{-1, -1};
        if (sb.clusterhashes != null) remoteSeed.setAlternativeAddress(sb.clusterhashes.get(remoteSeed.hash.getBytes()));
        
        // if the remote client has reported its own IP address and the client supports
        // the port forwarding feature (if client version >= 0.383) then we try to 
        // connect to the reported IP address first
        time = System.currentTimeMillis();
        long time_backping = 0;
        String backping_method = "none";
        if (reportedip.length() > 0 &&
            !clientip.equals(reportedip) &&
            clientversion >= yacyVersion.YACY_SUPPORTS_PORT_FORWARDING &&
            magic != 0) {            
            serverCore.checkInterruption();
            
            // try first the reportedip, since this may be a connect from a port-forwarding host
            prop.put("yourip", reportedip);
            remoteSeed.setIP(reportedip);
            callback = yacyClient.queryUrlCount(remoteSeed);
            time_backping = System.currentTimeMillis() - time;
            backping_method = "reportedip=" + reportedip;
        } else {
            prop.put("yourip", ias.getHostAddress());
            remoteSeed.setIP(ias.getHostAddress());
        }

        // if the previous attempt (using the reported ip address) was not successful,
        // then try the ip where the request came from
        if (callback[0] < 0 || (magic != 0 && magic != callback[1])) {
            boolean isNotLocal = true;

            // we are only allowed to connect to the client IP address if it's not our own address
            if (serverCore.useStaticIP) {
                    isNotLocal = !ias.isSiteLocalAddress();
            }

            if (isNotLocal) {
                serverCore.checkInterruption();
                
                prop.put("yourip", clientip);
                remoteSeed.setIP(clientip);
                callback = yacyClient.queryUrlCount(remoteSeed);
                time_backping = System.currentTimeMillis() - time;
                backping_method = "clientip=" + clientip;
            }
        }

//      System.out.println("YACYHELLO: YOUR IP=" + clientip);
        // set lastseen value (we have seen that peer, it contacted us!)
        remoteSeed.setLastSeenUTC();
        
        // assign status
        if (callback[0] >= 0) {
            if (remoteSeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) == null) {
                prop.put(yacySeed.YOURTYPE, yacySeed.PEERTYPE_SENIOR);
                remoteSeed.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR);
            } else if (remoteSeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_PRINCIPAL).equals(yacySeed.PEERTYPE_PRINCIPAL)) {
                prop.put(yacySeed.YOURTYPE, yacySeed.PEERTYPE_PRINCIPAL);
            } else {
                prop.put(yacySeed.YOURTYPE, yacySeed.PEERTYPE_SENIOR);
                remoteSeed.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR);
            }
            // connect the seed
            yacyCore.log.logInfo("hello/server: responded remote senior peer '" + remoteSeed.getName() + "' from " + reportedip + ", time_dnsResolve=" + time_dnsResolve + ", time_backping=" + time_backping + ", method=" + backping_method + ", urls=" + callback[0]);
            sb.peers.peerActions.peerArrival(remoteSeed, true);
        } else {
            prop.put(yacySeed.YOURTYPE, yacySeed.PEERTYPE_JUNIOR);
            remoteSeed.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR);
            yacyCore.log.logInfo("hello/server: responded remote junior peer '" + remoteSeed.getName() + "' from " + reportedip + ", time_dnsResolve=" + time_dnsResolve + ", time_backping=" + time_backping + ", method=" + backping_method + ", urls=" + callback[0]);
            // no connection here, instead store junior in connection cache
            if ((remoteSeed.hash != null) && (remoteSeed.isProper(false) == null)) {
                sb.peers.peerActions.peerPing(remoteSeed);
            }
        }
        sb.peers.peerActions.setUserAgent(clientip, userAgent);
        if (!(prop.get(yacySeed.YOURTYPE)).equals(reportedPeerType)) {
            yacyCore.log.logInfo("hello/server: changing remote peer '" + remoteSeed.getName() +
                                                           "' [" + reportedip +
                                             "] peerType from '" + reportedPeerType +
                                                        "' to '" + prop.get(yacySeed.YOURTYPE) + "'.");
        }

        serverCore.checkInterruption();
        final StringBuilder seeds = new StringBuilder(768);
        // attach some more seeds, as requested
        if (sb.peers.sizeConnected() > 0) {
            if (count > sb.peers.sizeConnected()) { count = sb.peers.sizeConnected(); }
            if (count > 100) { count = 100; }
            
            // latest seeds
            final Map<String, yacySeed> ySeeds = PeerSelection.seedsByAge(sb.peers, true, count); // peerhash/yacySeed relation
            
            // attach also my own seed
            seeds.append("seed0=").append(sb.peers.mySeed().genSeedStr(key)).append(serverCore.CRLF_STRING);
            count = 1;            
            
            // attach other seeds
            if (ySeeds != null) {
                seeds.ensureCapacity((ySeeds.size() + 1) * 768);
                final Iterator<yacySeed> si = ySeeds.values().iterator();
                yacySeed s;
                String seedString;
                while (si.hasNext()) {
                	s = si.next();
                    if ((s != null) && (s.isProper(false) == null)) try {
                        seedString = s.genSeedStr(key);
                        if (seedString != null) {
                            seeds.append("seed").append(count).append('=').append(seedString).append(serverCore.CRLF_STRING);
                            count++;
                        }
                    } catch (final ConcurrentModificationException e) {
                        Log.logException(e);
                    }
                }
            }
        } else {
            // attach also my own seed
            seeds.append("seed0=").append(sb.peers.mySeed().genSeedStr(key)).append(serverCore.CRLF_STRING);
        }

        prop.put("seedlist", seeds.toString());
        // return rewrite properties
        prop.put("message", "ok " + seed.length());
        yacyCore.log.logInfo("hello/server: responded remote peer '" + remoteSeed.getName() + "' [" + reportedip + "] in " + (System.currentTimeMillis() - start) + " milliseconds");
        return prop;
    }

}
