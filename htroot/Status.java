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
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.Memory;
import net.yacy.http.YaCyHttpServer;
import net.yacy.kelondro.io.ByteCount;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.OS;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.peers.PeerActions;
import net.yacy.peers.Seed;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Status
{

    private static final String SEEDSERVER = "seedServer";
    private static final String PEERSTATUS = "peerStatus";

    public static serverObjects respond(
        final RequestHeader header,
        final serverObjects post,
        final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        // check if the basic configuration was accessed before and forward
        prop.put("forwardToConfigBasic", 0);
        if ((post == null || !post.containsKey("noforward")) && sb.getConfig("server.servlets.called", "").indexOf("ConfigBasic.html", 0) < 0) {
            // forward to ConfigBasic
            prop.put("forwardToConfigBasic", 1);
        }
        if ( post != null ) {
            post.remove("noforward");
        }

        if ( post != null && !post.isEmpty() ) {
            if ( sb.adminAuthenticated(header) < 2 ) {
            	prop.authenticationRequired();
                return prop;
            }
            boolean redirect = false;
            if ( post.containsKey("login") ) {
                prop.put(serverObjects.ACTION_LOCATION, "");
                return prop;
            } else if ( post.containsKey("pauseCrawlJob") ) {
                final String jobType = post.get("jobType");
                if ( "localCrawl".equals(jobType) ) {
                    sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL, "user demand on Status.html");
                } else if ( "remoteTriggeredCrawl".equals(jobType) ) {
                    sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL, "user demand on Status.html");
                }
                redirect = true;
            } else if ( post.containsKey("continueCrawlJob") ) {
                final String jobType = post.get("jobType");
                if ( "localCrawl".equals(jobType) ) {
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                } else if ( "remoteTriggeredCrawl".equals(jobType) ) {
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                }
                redirect = true;
            } else if ( post.containsKey("ResetTraffic") ) {
                ByteCount.resetCount();
                redirect = true;
            } else if ( post.containsKey("popup") ) {
                final boolean trigger_enabled = post.getBoolean("popup");
                sb.setConfig("browserPopUpTrigger", trigger_enabled);
                redirect = true;
            } else if ( post.containsKey("tray") ) {
                final boolean trigger_enabled = post.getBoolean("tray");
                sb.setConfig("trayIcon", trigger_enabled);
                redirect = true;
            }

            if ( redirect ) {
                prop.put(serverObjects.ACTION_LOCATION, "");
                return prop;
            }
        }

        // update seed info
        //sb.updateMySeed(); // don't do this here. if Solr is stuck, this makes it worse. And it prevents that we can click on the Thread Dump menu.

        final boolean adminaccess = sb.adminAuthenticated(header) >= 2;
        if ( adminaccess ) {
            prop.put("showPrivateTable", "1");
            prop.put("privateStatusTable", "Status_p.inc");
        } else {
            prop.put("showPrivateTable", "0");
            prop.put("privateStatusTable", "");
        }

        // password protection
        if ( (sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").isEmpty())
            && (!sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false)) ) {
            prop.put("protection", "0"); // not protected
            prop.put("urgentSetPassword", "1");
        } else {
            prop.put("protection", "1"); // protected
        }

        if ( sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false) ) {
            prop.put("unrestrictedLocalAccess", 1);
        }

        // resource observer status
        if ( adminaccess ) {
            if ( !sb.observer.getDiskAvailable() ) {
                final String minFree = Formatter.bytesToString(sb.observer.getMinFreeDiskSteadystate());
                prop.put("warningDiskSpaceLow", "1");
                prop.put("warningDiskSpaceLow_minSpace", minFree);
            }
            if ( !sb.observer.getMemoryAvailable() ) {
                final String minFree =
                    Formatter.bytesToString(sb.observer.getMinFreeMemory() * 1024L * 1024L);
                prop.put("warningMemoryLow", "1");
                prop.put("warningMemoryLow_minSpace", minFree);
            }

        }

        // version information
        //final String versionstring = yacyVersion.combined2prettyVersion(sb.getConfig("version","0.1"));
        final String versionstring =
            yacyBuildProperties.getVersion() + "/" + yacyBuildProperties.getSVNRevision();
        prop.put("versionpp", versionstring);

        // place some more hints
        if (adminaccess && sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount() == 0) {
            prop.put("hintCrawlStart", "1");
        }

        if (adminaccess && sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount() > 500) {
            prop.put("hintCrawlMonitor", "1");
        }

        if (adminaccess && "intranet|webportal|allip".indexOf(env.getConfig(SwitchboardConstants.NETWORK_NAME, "unspecified")) >= 0) {
            prop.put("hintSupport", "1");
        }

        if (adminaccess && sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {
            prop.put("warningCrawlPaused", "1");
        }

        // hostname and port
        final String extendedPortString = sb.getConfig("port", "8090");
        final int pos = extendedPortString.indexOf(':', 0);
        prop.put("port", extendedPortString);
        if ( pos != -1 ) {
            prop.put("extPortFormat", "1");
            prop.putHTML("extPortFormat_extPort", extendedPortString);
        } else {
            prop.put("extPortFormat", "0");
        }
        final InetAddress hostIP = Domains.myPublicLocalIP();
        prop.put("host", hostIP != null ? hostIP.getHostAddress() : "Unkown IP");

        // ssl support
        prop.put("sslSupport", sb.getConfig("keyStore", "").isEmpty() || !sb.getConfigBool("server.https", false) ? 0 : 1);
        if (sb.getConfigBool("server.https", false)) prop.put("sslSupport_sslPort", sb.getHttpServer().getSslPort());
        
        // proxy information
        if ( sb.getConfigBool("remoteProxyUse", false) ) {
            prop.put("remoteProxy", "1");
            prop.putXML("remoteProxy_host", sb.getConfig("remoteProxyHost", "<unknown>"));
            prop.putXML("remoteProxy_port", sb.getConfig("remoteProxyPort", "<unknown>"));
            prop.put("remoteProxy_4Yacy", sb.getConfigBool("remoteProxyUse4Yacy", true) ? "0" : "1");
        } else {
            prop.put("remoteProxy", "0"); // not used
        }
        prop.put("info_isTransparentProxy", sb.getConfigBool("isTransparentProxy", false) ? "0" : "1");
        prop.put("info_proxyURL", sb.getConfigBool("proxyURL", false) ? "0" : "1");
        
        // peer information
        String thisHash = "";
        final String thisName = sb.peers.mySeed().getName();
        if ( sb.peers.mySeed() == null ) {
            thisHash = "not assigned";
            prop.put("peerAddress", "0"); // not assigned
            prop.put("peerStatistics", "0"); // unknown
        } else {
            final long uptime = 60000 * sb.peers.mySeed().getLong(Seed.UPTIME, 0L);
            prop.put("peerStatistics", "1");
            prop.put("peerStatistics_uptime", PeerActions.formatInterval(uptime));
            prop.putNum("peerStatistics_pagesperminute", sb.peers.mySeed().getPPM());
            prop.putNum(
                "peerStatistics_queriesperhour",
                Math.round(6000d * sb.peers.mySeed().getQPM()) / 100d);
            prop.putNum("peerStatistics_links", sb.peers.mySeed().getLinkCount());
            prop.put("peerStatistics_words", Formatter.number(sb.peers.mySeed().getWordCount()));
            prop.putNum("peerStatistics_disconnects", sb.peers.peerActions.disconnects);
            prop.put("peerStatistics_connects", Formatter.number(sb.peers.mySeed().get(Seed.CCOUNT, "0")));
            thisHash = sb.peers.mySeed().hash;
            if ( sb.peers.mySeed().getPublicAddress() == null ) {
                prop.put("peerAddress", "0"); // not assigned + instructions
                prop.put("warningGoOnline", "1");
            } else {
                prop.put("peerAddress", "1"); // Address
                prop.put("peerAddress_address", sb.peers.mySeed().getPublicAddress());
                prop.putXML("peerAddress_peername", sb.peers.mySeed().getName().toLowerCase());
            }
        }
        final String peerStatus =
            ((sb.peers.mySeed() == null) ? Seed.PEERTYPE_VIRGIN : sb.peers.mySeed().get(
                Seed.PEERTYPE,
                Seed.PEERTYPE_VIRGIN));

        if ( Seed.PEERTYPE_VIRGIN.equals(peerStatus)
            && "freeworld".equals(sb.getConfig(SwitchboardConstants.NETWORK_NAME, ""))
            && !SwitchboardConstants.CLUSTER_MODE_PRIVATE_PEER.equals(sb.getConfig(SwitchboardConstants.CLUSTER_MODE, ""))) {
            prop.put(PEERSTATUS, "0");
            prop.put("urgentStatusVirgin", "1");
        } else if ( Seed.PEERTYPE_JUNIOR.equals(peerStatus)
            && "freeworld".equals(sb.getConfig(SwitchboardConstants.NETWORK_NAME, ""))
            && !SwitchboardConstants.CLUSTER_MODE_PRIVATE_PEER.equals(sb.getConfig(SwitchboardConstants.CLUSTER_MODE, ""))) {
            prop.put(PEERSTATUS, "1");
            prop.put("warningStatusJunior", "1");
        } else if ( Seed.PEERTYPE_SENIOR.equals(peerStatus) ) {
            prop.put(PEERSTATUS, "2");
            prop.put("hintStatusSenior", "1");
        } else if ( Seed.PEERTYPE_PRINCIPAL.equals(peerStatus) ) {
            prop.put(PEERSTATUS, "3");
            prop.put("hintStatusPrincipal", "1");
            prop.putHTML("hintStatusPrincipal_seedURL", sb.peers.mySeed().get(Seed.SEEDLISTURL, "?"));
        }
        prop.putHTML("peerName", thisName);
        prop.put("hash", thisHash);

        final String seedUploadMethod = sb.getConfig("seedUploadMethod", "");
        if ( !"none".equalsIgnoreCase(seedUploadMethod)
            || ("".equals(seedUploadMethod) && (sb.getConfig("seedFTPPassword", "").length() > 0 || sb
                .getConfig("seedFilePath", "")
                .length() > 0)) ) {
            if ( "".equals(seedUploadMethod) ) {
                if ( sb.getConfig("seedFTPPassword", "").length() > 0 ) {
                    sb.setConfig("seedUploadMethod", "Ftp");
                }
                if ( sb.getConfig("seedFilePath", "").length() > 0 ) {
                    sb.setConfig("seedUploadMethod", "File");
                }
            }

            if ( "ftp".equalsIgnoreCase(seedUploadMethod) ) {
                prop.put(SEEDSERVER, "1"); // enabled
                prop.putHTML("seedServer_seedServer", sb.getConfig("seedFTPServer", ""));
            } else if ( "scp".equalsIgnoreCase(seedUploadMethod) ) {
                prop.put(SEEDSERVER, "1"); // enabled
                prop.putHTML("seedServer_seedServer", sb.getConfig("seedScpServer", ""));
            } else if ( "file".equalsIgnoreCase(seedUploadMethod) ) {
                prop.put(SEEDSERVER, "2"); // enabled
                prop.putHTML("seedServer_seedFile", sb.getConfig("seedFilePath", ""));
            }
            prop.put(
                "seedServer_lastUpload",
                PeerActions.formatInterval(System.currentTimeMillis() - sb.peers.lastSeedUpload_timeStamp));
        } else {
            prop.put(SEEDSERVER, "0"); // disabled
        }

        if ( sb.peers != null && sb.peers.sizeConnected() > 0 ) {
            prop.put("otherPeers", "1");
            prop.putNum("otherPeers_num", sb.peers.sizeConnected());
        } else {
            prop.put("otherPeers", "0"); // not online
        }

        if ( !sb.getConfigBool("browserPopUpTrigger", false) ) {
            prop.put("popup", "0");
        } else {
            prop.put("popup", "1");
        }

        if ( !OS.isWindows ) {
            prop.put("tray", "2");
        } else if ( !sb.getConfigBool("trayIcon", false) ) {
            prop.put("tray", "0");
        } else {
            prop.put("tray", "1");
        }

        // memory usage and system attributes
        prop.put("usedMemory", Formatter.bytesToString(MemoryControl.used()));
        prop.put("maxMemory", Formatter.bytesToString(MemoryControl.maxMemory()));
        prop.put("usedDisk", Formatter.bytesToString(sb.observer.getSizeOfDataPath(true)));
        prop.put("freeDisk", Formatter.bytesToString(sb.observer.getUsableSpace()));
        prop.put("processors", WorkflowProcessor.availableCPU);
        prop.put("load", Memory.load());

        // proxy traffic
        //prop.put("trafficIn",bytesToString(httpdByteCountInputStream.getGlobalCount()));
        prop.put("trafficProxy", Formatter.bytesToString(ByteCount.getAccountCount(ByteCount.PROXY)));
        prop.put("trafficCrawler", Formatter.bytesToString(ByteCount.getAccountCount(ByteCount.CRAWLER)));

        // connection information
        final YaCyHttpServer httpd =  sb.getHttpServer();

        prop.putNum("connectionsActive", httpd.getJobCount());
        prop.putNum("connectionsMax", httpd.getMaxSessionCount());

        // Queue information
        final int loaderJobCount = sb.crawlQueues.activeWorkerEntries().size();
        final int loaderMaxCount = sb.getConfigInt(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10);
        final int loaderPercent = (loaderMaxCount == 0) ? 0 : loaderJobCount * 100 / loaderMaxCount;
        prop.putNum("loaderQueueSize", loaderJobCount);
        prop.putNum("loaderQueueMax", loaderMaxCount);
        prop.put("loaderQueuePercent", (loaderPercent > 100) ? 100 : loaderPercent);

        prop.putNum("localCrawlQueueSize", sb
            .getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)
            .getJobCount());
        prop.put("localCrawlPaused", sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)
            ? "1"
            : "0");

        prop.putNum(
            "remoteTriggeredCrawlQueueSize",
            sb.getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL).getJobCount());
        prop.put(
            "remoteTriggeredCrawlPaused",
            sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL) ? "1" : "0");

        prop.putNum("stackCrawlQueueSize", sb.crawlStacker.size());

        // return rewrite properties
        prop.put("date", (new Date()).toString());
        return prop;
    }
}
