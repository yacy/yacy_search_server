// ConfigNetwork_p.java
// --------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.04.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

import java.io.File;
import java.util.HashSet;

import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverBusyThread;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ConfigNetwork_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        int commit = 0;
        
        // load all options for network definitions
        File networkBootstrapLocationsFile = new File(new File(sb.getRootPath(), "defaults"), "yacy.networks");
        HashSet<String> networkBootstrapLocations = serverFileUtils.loadList(networkBootstrapLocationsFile);
        
        
        if (post != null) {
            
            if (post.containsKey("changeNetwork")) {
                String networkDefinition = post.get("networkDefinition", "defaults/yacy.network.freeworld.unit");
                if (networkDefinition.equals(sb.getConfig("network.unit.definition", ""))) {
                    // no change
                    commit = 3;
                } else {
                    // shut down old network and index, start up new network and index
                    commit = 1;
                    sb.switchNetwork(networkDefinition);
                    // check if the password is given
                    if (sb.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "").length() == 0) {
                        prop.put("commitPasswordWarning", "1");
                    }
                }
            }
            
            if (post.containsKey("save")) {
                boolean crawlResponse = post.get("crawlResponse", "off").equals("on");
                
                // DHT control
                boolean indexDistribute = post.get("indexDistribute", "").equals("on");
                boolean indexReceive = post.get("indexReceive", "").equals("on");
                boolean robinsonmode = post.get("network", "").equals("robinson");
                String clustermode = post.get("cluster.mode", "publicpeer");
                if (robinsonmode) {
                    indexDistribute = false;
                    indexReceive = false;
                    if ((clustermode.equals("privatepeer")) || (clustermode.equals("publicpeer"))) {
                        prop.put("commitRobinsonWithoutRemoteIndexing", "1");
                        crawlResponse = false;
                    }
                    if ((clustermode.equals("privatecluster")) || (clustermode.equals("publiccluster"))) {
                        prop.put("commitRobinsonWithRemoteIndexing", "1");
                        crawlResponse = true;
                    }
                    commit = 1;
                } else {
                    if (!indexDistribute && !indexReceive) {
                        prop.put("commitDHTIsRobinson", "1");
                        commit = 2;
                    } else if (indexDistribute && indexReceive) {
                        commit = 1;
                    } else {
                        if (!indexReceive) prop.put("commitDHTNoGlobalSearch", "1");
                        commit = 1;
                    }
                    if (!crawlResponse) {
                        prop.put("commitCrawlPlea", "1");
                    }
                }
                
                if (indexDistribute) {
                    sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, "true");
                } else {
                    sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, "false");
                }
    
                if (post.get("indexDistributeWhileCrawling","").equals("on")) {
                    sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_CRAWLING, "true");
                } else {
                    sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_CRAWLING, "false");
                }
    
                if (post.get("indexDistributeWhileIndexing","").equals("on")) {
                    sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_INDEXING, "true");
                } else {
                    sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_INDEXING, "false");
                }
    
                if (indexReceive) {
                    sb.setConfig("allowReceiveIndex", "true");
                    sb.webIndex.seedDB.mySeed().setFlagAcceptRemoteIndex(true);
                } else {
                    sb.setConfig("allowReceiveIndex", "false");
                    sb.webIndex.seedDB.mySeed().setFlagAcceptRemoteIndex(false);
                }
    
                if (post.get("indexReceiveBlockBlacklist", "").equals("on")) {
                    sb.setConfig("indexReceiveBlockBlacklist", "true");
                } else {
                    sb.setConfig("indexReceiveBlockBlacklist", "false");
                }
                    
                if (post.containsKey("peertags")) {
                    sb.webIndex.seedDB.mySeed().setPeerTags(serverCodings.string2set(normalizedList((String) post.get("peertags")), ","));
                }
                
                sb.setConfig("cluster.mode", post.get("cluster.mode", "publicpeer"));
                
                // read remote crawl request settings
                sb.setConfig("crawlResponse", (crawlResponse) ? "true" : "false");
                int newppm = 1;
                try {
                    newppm = Math.max(1, Integer.parseInt(post.get("acceptCrawlLimit", "1")));
                } catch (NumberFormatException e) {}
                long newBusySleep = Math.max(100, 60000 / newppm);
                serverBusyThread rct = sb.getThread(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                rct.setBusySleep(newBusySleep);
                sb.setConfig(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, Long.toString(newBusySleep));
                
                sb.setConfig("cluster.peers.ipport", checkIPPortList(post.get("cluster.peers.ipport", "")));
                sb.setConfig("cluster.peers.yacydomain", checkYaCyDomainList(post.get("cluster.peers.yacydomain", "")));
                
                // update the cluster hash set
                sb.clusterhashes = sb.webIndex.seedDB.clusterHashes(sb.getConfig("cluster.peers.yacydomain", ""));
            }            
        }
        
        // write answer code
        prop.put("commit", commit);
        
        // write remote crawl request settings
        prop.put("crawlResponse", sb.getConfigBool("crawlResponse", false) ? "1" : "0");
        long RTCbusySleep = 100;
        try {
            RTCbusySleep = Integer.parseInt(env.getConfig(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, "100"));
        } catch (NumberFormatException e) {}
        int RTCppm = (int) (60000L / (RTCbusySleep + 1));
        prop.put("acceptCrawlLimit", RTCppm);
        
        boolean indexDistribute = sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, "true").equals("true");
        boolean indexReceive = sb.getConfig("allowReceiveIndex", "true").equals("true");
        prop.put("indexDistributeChecked", (indexDistribute) ? "1" : "0");
        prop.put("indexDistributeWhileCrawling.on", (sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_CRAWLING, "true").equals("true")) ? "1" : "0");
        prop.put("indexDistributeWhileCrawling.off", (sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_CRAWLING, "true").equals("true")) ? "0" : "1");
        prop.put("indexDistributeWhileIndexing.on", (sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_INDEXING, "true").equals("true")) ? "1" : "0");
        prop.put("indexDistributeWhileIndexing.off", (sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW_WHILE_INDEXING, "true").equals("true")) ? "0" : "1");
        prop.put("indexReceiveChecked", (indexReceive) ? "1" : "0");
        prop.put("indexReceiveBlockBlacklistChecked.on", (sb.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "1" : "0");
        prop.put("indexReceiveBlockBlacklistChecked.off", (sb.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "0" : "1");
        prop.putHTML("peertags", serverCodings.set2string(sb.webIndex.seedDB.mySeed().getPeerTags(), ",", false));

        // set seed information directly
        sb.webIndex.seedDB.mySeed().setFlagAcceptRemoteCrawl(sb.getConfigBool("crawlResponse", false));
        sb.webIndex.seedDB.mySeed().setFlagAcceptRemoteIndex(indexReceive);
        
        // set p2p/robinson mode flags and values
        prop.put("p2p.checked", (indexDistribute || indexReceive) ? "1" : "0");
        prop.put("robinson.checked", (indexDistribute || indexReceive) ? "0" : "1");
        prop.put("cluster.peers.ipport", sb.getConfig("cluster.peers.ipport", ""));
        prop.put("cluster.peers.yacydomain", sb.getConfig("cluster.peers.yacydomain", ""));
        prop.put("cluster.peers.yacydomain.hashes", (sb.clusterhashes.size() == 0) ? "" : sb.clusterhashes.toString());
        
        // set p2p mode flags
        prop.put("privatepeerChecked", (sb.getConfig("cluster.mode", "").equals("privatepeer")) ? "1" : "0");
        prop.put("privateclusterChecked", (sb.getConfig("cluster.mode", "").equals("privatecluster")) ? "1" : "0");
        prop.put("publicclusterChecked", (sb.getConfig("cluster.mode", "").equals("publiccluster")) ? "1" : "0");
        prop.put("publicpeerChecked", (sb.getConfig("cluster.mode", "").equals("publicpeer")) ? "1" : "0");
        
        // set network configuration
        prop.put("network.unit.definition", sb.getConfig("network.unit.definition", ""));
        prop.put("network.unit.name", sb.getConfig("network.unit.name", ""));
        prop.put("network.unit.description", sb.getConfig("network.unit.description", ""));
        prop.put("network.unit.domain", sb.getConfig("network.unit.domain", ""));
        prop.put("network.unit.dht", sb.getConfig("network.unit.dht", ""));
        networkBootstrapLocations.remove(sb.getConfig("network.unit.definition", ""));
        int c = 0;
        for (String s: networkBootstrapLocations) prop.put("networks_" + c++ + "_network", s);
        prop.put("networks", c);
        
        return prop;
    }
    
    public static String normalizedList(String input) {
        input = input.replace(' ', ',');
        input = input.replace(' ', ';');
        input = input.replaceAll(",,", ",");
        if (input.startsWith(",")) input = input.substring(1);
        if (input.endsWith(",")) input = input.substring(0, input.length() - 1);
        return input;
    }
    
    public static String checkYaCyDomainList(String input) {
        input = normalizedList(input);
        String[] s = input.split(",");
        input = "";
        for (int i = 0; i < s.length; i++) {
            if ((s[i].endsWith(".yacyh")) || (s[i].endsWith(".yacy")) ||
                (s[i].indexOf(".yacyh=") > 0) || (s[i].indexOf(".yacy=") > 0)) input += "," + s[i];
        }
        if (input.length() == 0) return input; else return input.substring(1);
    }
    
    public static String checkIPPortList(String input) {
        input = normalizedList(input);
        String[] s = input.split(",");
        input = "";
        for (int i = 0; i < s.length; i++) {
            if (s[i].indexOf(':') >= 9) input += "," + s[i];
        }
        if (input.length() == 0) return input; else return input.substring(1);
    }
}
