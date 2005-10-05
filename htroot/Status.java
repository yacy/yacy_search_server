// Status.java 
// -----------------------
// part of YaCy
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

// You must compile this file with
// javac -classpath .:../Classes Status.java
// if the shell's current path is HTROOT

import java.lang.Math;
import java.text.DecimalFormat;
import java.io.File;

import de.anomic.http.httpHeader;
import de.anomic.http.httpdByteCountInputStream;
import de.anomic.http.httpdByteCountOutputStream;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class Status {

    private static final String SEEDSERVER = "seedServer";
    private static final String PEERSTATUS = "peerStatus";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        /*
          versionProbe=http://www.anomic.de/AnomicHTTPProxy/release.txt
          superseedFile=superseed.txt
         */
        // update seed info
        yacyCore.peerActions.updateMySeed();

        if (((plasmaSwitchboard) env).adminAuthenticated(header) >= 2) {
            prop.put("privateStatusTable", new File(env.getRootPath(), "htroot/Status_p.inc").toString());
        } else {
            prop.put("privateStatusTable", "");
        }

        // password protection
        if (env.getConfig("adminAccountBase64MD5", "").length() == 0) {
            prop.put("protection", 0); // not protected
        } else {
            prop.put("protection", 1); // protected
        }

        // version information
        prop.put("versionpp", yacy.combinedVersionString2PrettyString(env.getConfig("version","0.1")));
        double thisVersion = Double.parseDouble(env.getConfig("version","0.1"));
        // cut off the SVN Rev in the Version
        try {thisVersion = Math.round(thisVersion*1000.0)/1000.0;} catch (NumberFormatException e) {}
//      System.out.println("TEST: "+thisVersion);
        if (yacyCore.latestVersion >= (thisVersion+0.01)) { // only new Versions(not new SVN)
            prop.put("versioncomment", 1); // new version
        } else {
            prop.put("versioncomment", 0); // no comment
        }
        prop.put("versioncomment_latestVersion", Float.toString(yacyCore.latestVersion));

        prop.put("host", serverCore.publicLocalIP());
        prop.put("port", env.getConfig("port", "<unknown>"));

        // port forwarding: hostname and port
        if ((serverCore.portForwardingEnabled) && (serverCore.portForwarding != null)) {
            prop.put("portForwarding", 1);
            prop.put("portForwarding_host", serverCore.portForwarding.getHost());
            prop.put("portForwarding_port", Integer.toString(serverCore.portForwarding.getPort()));
            prop.put("portForwarding_status", serverCore.portForwarding.isConnected() ? 1:0);
        } else {
            prop.put("portForwarding", 0);
        }

        if (env.getConfig("remoteProxyUse", "false").equals("true")) {
            prop.put("remoteProxy", 1);
            prop.put("remoteProxy_host", env.getConfig("remoteProxyHost", "<unknown>"));
            prop.put("remoteProxy_port", env.getConfig("remoteProxyPort", "<unknown>"));
        } else {
            prop.put("remoteProxy", 0); // not used
        }

        // peer information
        String thisHash = "";
        final String thisName = env.getConfig("peerName", "<nameless>");
        if (yacyCore.seedDB.mySeed == null)  {
            thisHash = "not assigned";
            prop.put("peerAddress", 0);    // not assigned
            prop.put("peerStatistics", 0); // unknown
        } else {
            final long uptime = 60000 * Long.parseLong(yacyCore.seedDB.mySeed.get("Uptime", "0"));
            prop.put("peerStatistics", 1);
            prop.put("peerStatistics_uptime", serverDate.intervalToString(uptime));
            prop.put("peerStatistics_pagesperminute", yacyCore.seedDB.mySeed.get("ISpeed", "unknown"));
            prop.put("peerStatistics_links", yacyCore.seedDB.mySeed.get("LCount", "unknown"));
            prop.put("peerStatistics_words", yacyCore.seedDB.mySeed.get("ICount", "unknown"));
            prop.put("peerStatistics_juniorConnects", yacyCore.peerActions.juniorConnects);
            prop.put("peerStatistics_seniorConnects", yacyCore.peerActions.seniorConnects);
            prop.put("peerStatistics_principalConnects", yacyCore.peerActions.principalConnects);
            prop.put("peerStatistics_disconnects", yacyCore.peerActions.disconnects);
            prop.put("peerStatistics_connects", yacyCore.seedDB.mySeed.get("CCount", "0"));
            if (yacyCore.seedDB.mySeed.getAddress() == null) {
                thisHash = yacyCore.seedDB.mySeed.hash;
                prop.put("peerAddress", 1); // not assigned + instructions
            } else {
                thisHash = yacyCore.seedDB.mySeed.hash;
                prop.put("peerAddress", 2); // Address
                prop.put("peerAddress_address", yacyCore.seedDB.mySeed.getAddress());
                prop.put("peerAddress_peername", env.getConfig("peerName", "<nameless>").toLowerCase());
            }
        }
        final String peerStatus = ((yacyCore.seedDB.mySeed == null) ? yacySeed.PEERTYPE_VIRGIN : yacyCore.seedDB.mySeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN));
        if (peerStatus.equals(yacySeed.PEERTYPE_VIRGIN)) {
            prop.put(PEERSTATUS, 0);
        } else if (peerStatus.equals(yacySeed.PEERTYPE_JUNIOR)) {
            prop.put(PEERSTATUS, 1);
        } else if (peerStatus.equals(yacySeed.PEERTYPE_SENIOR)) {
            prop.put(PEERSTATUS, 2);
        } else if (peerStatus.equals(yacySeed.PEERTYPE_PRINCIPAL)) {
            prop.put(PEERSTATUS, 3);
            prop.put("peerStatus_seedURL", yacyCore.seedDB.mySeed.get("seedURL", "?"));
        }
        prop.put("peerName", thisName);
        prop.put("hash", thisHash);
        
        final String seedUploadMethod = env.getConfig("seedUploadMethod", "");
        if (!seedUploadMethod.equalsIgnoreCase("none") || 
            (seedUploadMethod.equals("") && env.getConfig("seedFTPPassword", "").length() > 0) ||
            (seedUploadMethod.equals("") && env.getConfig("seedFilePath", "").length() > 0)) {
            if (seedUploadMethod.equals("")) {
                if (env.getConfig("seedFTPPassword", "").length() > 0) {
                    env.setConfig("seedUploadMethod","Ftp");
                }
                if (env.getConfig("seedFilePath", "").length() > 0) {
                    env.setConfig("seedUploadMethod","File");
                }
            }

            if (seedUploadMethod.equalsIgnoreCase("ftp")) {
                prop.put(SEEDSERVER, 1); // enabled
                prop.put("seedServer_seedServer", env.getConfig("seedFTPServer", ""));
            } else if (seedUploadMethod.equalsIgnoreCase("scp")) {
                prop.put(SEEDSERVER, 1); // enabled
                prop.put("seedServer_seedServer", env.getConfig("seedScpServer", ""));
            } else if (seedUploadMethod.equalsIgnoreCase("file")) {
                prop.put(SEEDSERVER, 2); // enabled
                prop.put("seedServer_seedFile", env.getConfig("seedFilePath", ""));
            }
            prop.put("seedServer_lastUpload",
                    serverDate.intervalToString(System.currentTimeMillis()-((plasmaSwitchboard)env).yc.lastSeedUpload_timeStamp));
        } else {
            prop.put(SEEDSERVER, 0); // disabled
        }
        
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0){
            prop.put("otherPeers", 1);
            prop.put("otherPeers_num", yacyCore.seedDB.sizeConnected());
        }else{
            prop.put("otherPeers", 0); // not online
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

        final Runtime rt = Runtime.getRuntime();

        // memory usage and system attributes
        prop.put("freeMemory", bytesToString(rt.freeMemory()));
        prop.put("totalMemory", bytesToString(rt.totalMemory()));
        prop.put("maxMemory", bytesToString(rt.maxMemory()));
        prop.put("processors", rt.availableProcessors());

        // proxy traffic
        prop.put("trafficIn",bytesToString(httpdByteCountInputStream.getGlobalCount()));
        prop.put("trafficOut",bytesToString(httpdByteCountOutputStream.getGlobalCount()));

        // Queue information
        final plasmaSwitchboard sb = (plasmaSwitchboard)env;
        prop.put("indexingQueueSize", Integer.toString(sb.getThread("80_indexing").getJobCount()));
        prop.put("indexingQueueMax", Integer.toString(plasmaSwitchboard.indexingSlots));

        prop.put("loaderQueueSize", Integer.toString(sb.cacheLoader.size()));        
        prop.put("loaderQueueMax", Integer.toString(plasmaSwitchboard.crawlSlots));
        prop.put("loaderPaused",sb.crawlingIsPaused()?1:0);

        prop.put("localCrawlQueueSize", Integer.toString(sb.getThread("50_localcrawl").getJobCount()));
        prop.put("stackCrawlQueueSize", Integer.toString(sb.sbStackCrawlThread.getQueueSize()));       
        prop.put("remoteCrawlQueueSize", Integer.toString(sb.getThread("61_globalcrawltrigger").getJobCount()));
        

        // return rewrite properties
        return prop;
    }

    public static String bytesToString(long byteCount) {
        try {
            final StringBuffer byteString = new StringBuffer();

            final DecimalFormat df = new DecimalFormat( "0.00" );
            if (byteCount > 1073741824) {
                byteString.append(df.format((double)byteCount / (double)1073741824 ))
                          .append(" GB");
            } else if (byteCount > 1048576) {
                byteString.append(df.format((double)byteCount / (double)1048576))
                          .append(" MB");
            } else if (byteCount > 1024) {
                byteString.append(df.format((double)byteCount / (double)1024))
                          .append(" KB");
            } else {
                byteString.append(Long.toString(byteCount))
                .append(" Bytes");
            }

            return byteString.toString();
        } catch (Exception e) {
            return "unknown";
        }

    }

}
