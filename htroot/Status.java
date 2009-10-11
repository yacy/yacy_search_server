// Status.java 
// -----------------------
// part of YaCy
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

// You must compile this file with
// javac -classpath .:../Classes Status.java
// if the shell's current path is HTROOT

import java.net.InetAddress;
import java.util.Date;

import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.workflow.WorkflowProcessor;

import de.anomic.http.io.ByteCountInputStream;
import de.anomic.http.io.ByteCountOutputStream;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.http.server.HTTPDemon;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyBuildProperties;
import de.anomic.yacy.yacySeed;

public class Status {

    private static final String SEEDSERVER = "seedServer";
    private static final String PEERSTATUS = "peerStatus";

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null) {
            if (sb.adminAuthenticated(header) < 2) {
                prop.put("AUTHENTICATE","admin log-in");
                return prop;
            }
            boolean redirect = false;
            if (post.containsKey("login")) {
                prop.put("LOCATION","");
                return prop;
            } else if (post.containsKey("pauseCrawlJob")) {
        		final String jobType = post.get("jobType");
        		if (jobType.equals("localCrawl")) 
                    sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
        		else if (jobType.equals("remoteTriggeredCrawl")) 
                    sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        		redirect = true;
        	} else if (post.containsKey("continueCrawlJob")) {
        		final String jobType = post.get("jobType");
        		if (jobType.equals("localCrawl")) 
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
        		else if (jobType.equals("remoteTriggeredCrawl")) 
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        		redirect = true;
        	} else if (post.containsKey("ResetTraffic")) {
        		ByteCountInputStream.resetCount();
        		ByteCountOutputStream.resetCount();
        		redirect = true;
        	} else if (post.containsKey("popup")) {
                final String trigger_enabled = post.get("popup");
                if (trigger_enabled.equals("false")) {
                    sb.setConfig("browserPopUpTrigger", "false");
                } else if (trigger_enabled.equals("true")){
                    sb.setConfig("browserPopUpTrigger", "true");
                }
                redirect = true;
        	} else if (post.containsKey("tray")) {
                final String trigger_enabled = post.get("tray");
                if (trigger_enabled.equals("false")) {
                    sb.setConfig("trayIcon", "false");
                } else if (trigger_enabled.equals("true")){
                    sb.setConfig("trayIcon", "true");
                }
                redirect = true;
        	}
        	
        	if (redirect) {
        		prop.put("LOCATION","");
        		return prop;
        	}
        }
        
        // update seed info
        sb.updateMySeed();

        final boolean adminaccess = sb.adminAuthenticated(header) >= 2;
        if (adminaccess) {
            prop.put("showPrivateTable", "1");
            prop.put("privateStatusTable", "Status_p.inc");
        } else { 
            prop.put("showPrivateTable", "0");
            prop.put("privateStatusTable", "");
        }

        // password protection
        if ((sb.getConfig(HTTPDemon.ADMIN_ACCOUNT_B64MD5, "").length() == 0) && (!sb.getConfigBool("adminAccountForLocalhost", false))) {
            prop.put("protection", "0"); // not protected
            prop.put("urgentSetPassword", "1");
        } else {
            prop.put("protection", "1"); // protected
        }
        
        // free disk space
        if ((adminaccess) && (!sb.observer.getDisksOK()))
        {
            final String minFree = Formatter.bytesToString(sb.observer.getMinFreeDiskSpace());
            prop.put("warningDiskSpaceLow", "1");
            prop.put("warningDiskSpaceLow_minSpace", minFree);
        }
        
        
        // version information
        //final String versionstring = yacyVersion.combined2prettyVersion(sb.getConfig("version","0.1"));
        final String versionstring = yacyBuildProperties.getVersion() + "/" + yacyBuildProperties.getSVNRevision();
        prop.put("versionpp", versionstring);
        
        // place some more hints
        if ((adminaccess) && (sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount() == 0)) {
            prop.put("hintCrawlStart", "1");
        }
        
        if ((adminaccess) && (sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount() > 500)) {
            prop.put("hintCrawlMonitor", "1");
        }
        
        // hostname and port
        final String extendedPortString = sb.getConfig("port", "8080");
        final int pos = extendedPortString.indexOf(":"); 
        prop.put("port",serverCore.getPortNr(extendedPortString));
        if (pos!=-1) {
            prop.put("extPortFormat", "1");
            prop.putHTML("extPortFormat_extPort",extendedPortString);
        } else {
            prop.put("extPortFormat", "0");
        }
        InetAddress hostIP = serverSwitch.myPublicLocalIP();
        prop.put("host", hostIP!=null ? hostIP.getHostAddress() : "Unkown IP");
        
        // ssl support
        prop.put("sslSupport",sb.getConfig("keyStore", "").length() == 0 ? "0" : "1");

        if (sb.getConfig("remoteProxyUse", "false").equals("true")) {
            prop.put("remoteProxy", "1");
            prop.putXML("remoteProxy_host", sb.getConfig("remoteProxyHost", "<unknown>"));
            prop.putXML("remoteProxy_port", sb.getConfig("remoteProxyPort", "<unknown>"));
            prop.put("remoteProxy_4Yacy", sb.getConfig("remoteProxyUse4Yacy", "true").equalsIgnoreCase("true") ? "0" : "1");
        } else {
            prop.put("remoteProxy", "0"); // not used
        }

        // peer information
        String thisHash = "";
        final String thisName = sb.getConfig("peerName", "<nameless>");
        if (sb.peers.mySeed() == null)  {
            thisHash = "not assigned";
            prop.put("peerAddress", "0");    // not assigned
            prop.put("peerStatistics", "0"); // unknown
        } else {
            final long uptime = 60000 * Long.parseLong(sb.peers.mySeed().get(yacySeed.UPTIME, "0"));
            prop.put("peerStatistics", "1");
            prop.put("peerStatistics_uptime", DateFormatter.formatInterval(uptime));
            prop.putNum("peerStatistics_pagesperminute", sb.peers.mySeed().getPPM());
            prop.putNum("peerStatistics_queriesperhour", Math.round(6000d * sb.peers.mySeed().getQPM()) / 100d);
            prop.putNum("peerStatistics_links", sb.peers.mySeed().getLinkCount());
            prop.put("peerStatistics_words", Formatter.number(sb.peers.mySeed().get(yacySeed.ICOUNT, "0")));
            prop.putNum("peerStatistics_disconnects", sb.peers.peerActions.disconnects);
            prop.put("peerStatistics_connects", Formatter.number(sb.peers.mySeed().get(yacySeed.CCOUNT, "0")));
            thisHash = sb.peers.mySeed().hash;
            if (sb.peers.mySeed().getPublicAddress() == null) {
                prop.put("peerAddress", "0"); // not assigned + instructions
                prop.put("warningGoOnline", "1");
            } else {
                prop.put("peerAddress", "1"); // Address
                prop.put("peerAddress_address", sb.peers.mySeed().getPublicAddress());
                prop.putXML("peerAddress_peername", sb.getConfig("peerName", "<nameless>").toLowerCase());
            }
        }
        final String peerStatus = ((sb.peers.mySeed() == null) ? yacySeed.PEERTYPE_VIRGIN : sb.peers.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN));
        if (peerStatus.equals(yacySeed.PEERTYPE_VIRGIN) && sb.getConfig(SwitchboardConstants.NETWORK_NAME, "").equals("freeworld")) {
            prop.put(PEERSTATUS, "0");
            prop.put("urgentStatusVirgin", "1");
        } else if (peerStatus.equals(yacySeed.PEERTYPE_JUNIOR) && sb.getConfig(SwitchboardConstants.NETWORK_NAME, "").equals("freeworld")) {
            prop.put(PEERSTATUS, "1");
            prop.put("warningStatusJunior", "1");
        } else if (peerStatus.equals(yacySeed.PEERTYPE_SENIOR)) {
            prop.put(PEERSTATUS, "2");
            prop.put("hintStatusSenior", "1");
        } else if (peerStatus.equals(yacySeed.PEERTYPE_PRINCIPAL)) {
            prop.put(PEERSTATUS, "3");
            prop.put("hintStatusPrincipal", "1");
            prop.put("hintStatusPrincipal_seedURL", sb.peers.mySeed().get(yacySeed.SEEDLIST, "?"));
        }
        prop.putHTML("peerName", thisName);
        prop.put("hash", thisHash);
        
        final String seedUploadMethod = sb.getConfig("seedUploadMethod", "");
        if (!seedUploadMethod.equalsIgnoreCase("none") || 
            (seedUploadMethod.equals("") && sb.getConfig("seedFTPPassword", "").length() > 0) ||
            (seedUploadMethod.equals("") && sb.getConfig("seedFilePath", "").length() > 0)) {
            if (seedUploadMethod.equals("")) {
                if (sb.getConfig("seedFTPPassword", "").length() > 0) {
                    sb.setConfig("seedUploadMethod","Ftp");
                }
                if (sb.getConfig("seedFilePath", "").length() > 0) {
                    sb.setConfig("seedUploadMethod","File");
                }
            }

            if (seedUploadMethod.equalsIgnoreCase("ftp")) {
                prop.put(SEEDSERVER, "1"); // enabled
                prop.putHTML("seedServer_seedServer", sb.getConfig("seedFTPServer", ""));
            } else if (seedUploadMethod.equalsIgnoreCase("scp")) {
                prop.put(SEEDSERVER, "1"); // enabled
                prop.putHTML("seedServer_seedServer", sb.getConfig("seedScpServer", ""));
            } else if (seedUploadMethod.equalsIgnoreCase("file")) {
                prop.put(SEEDSERVER, "2"); // enabled
                prop.putHTML("seedServer_seedFile", sb.getConfig("seedFilePath", ""));
            }
            prop.put("seedServer_lastUpload",
                    DateFormatter.formatInterval(System.currentTimeMillis() - sb.peers.lastSeedUpload_timeStamp));
        } else {
            prop.put(SEEDSERVER, "0"); // disabled
        }
        
        if (sb.peers != null && sb.peers.sizeConnected() > 0){
            prop.put("otherPeers", "1");
            prop.putNum("otherPeers_num", sb.peers.sizeConnected());
        }else{
            prop.put("otherPeers", "0"); // not online
        }

        if (sb.getConfig("browserPopUpTrigger", "false").equals("false")) {
            prop.put("popup", "0");
        } else {
            prop.put("popup", "1");
        }
        
        if (sb.getConfig("trayIcon", "false").equals("false")) {
            prop.put("tray", "0");
        } else {
            prop.put("tray", "1");
        }

        // memory usage and system attributes
        prop.put("freeMemory", Formatter.bytesToString(MemoryControl.free()));
        prop.put("totalMemory", Formatter.bytesToString(MemoryControl.total()));
        prop.put("maxMemory", Formatter.bytesToString(MemoryControl.maxMemory));
        prop.put("processors", WorkflowProcessor.availableCPU);

        // proxy traffic
        //prop.put("trafficIn",bytesToString(httpdByteCountInputStream.getGlobalCount()));
        prop.put("trafficProxy", Formatter.bytesToString(ByteCountOutputStream.getAccountCount("PROXY")));
        prop.put("trafficCrawler", Formatter.bytesToString(ByteCountInputStream.getAccountCount("CRAWLER")));

        // connection information
        final serverCore httpd = (serverCore) sb.getThread("10_httpd");
        prop.putNum("connectionsActive", httpd.getJobCount());
        prop.putNum("connectionsMax", httpd.getMaxSessionCount());
        
        // Queue information
        final int loaderJobCount = sb.crawlQueues.size();
        final int loaderMaxCount = Integer.parseInt(sb.getConfig(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, "10"));
        final int loaderPercent = (loaderMaxCount==0)?0:loaderJobCount*100/loaderMaxCount;
        prop.putNum("loaderQueueSize", loaderJobCount);
        prop.putNum("loaderQueueMax", loaderMaxCount);        
        prop.put("loaderQueuePercent", (loaderPercent>100) ? 100 : loaderPercent);
        
        prop.putNum("localCrawlQueueSize", sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount());
        prop.put("localCrawlPaused",sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL) ? "1" : "0");

        prop.putNum("remoteTriggeredCrawlQueueSize", sb.getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL).getJobCount());
        prop.put("remoteTriggeredCrawlPaused",sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL) ? "1" : "0");
        
        prop.putNum("stackCrawlQueueSize", sb.crawlStacker.size());

        // return rewrite properties
        prop.put("date",(new Date()).toString());
        return prop;
    }
}
