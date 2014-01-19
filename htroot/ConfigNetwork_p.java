// ConfigNetwork_p.java
// --------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.04.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MapTools;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigNetwork_p
{

    public static serverObjects respond(
        @SuppressWarnings("unused") final RequestHeader header,
        final serverObjects post,
        final serverSwitch env) throws FileNotFoundException, IOException {

        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        int commit = 0;

        // load all options for network definitions
        final File networkBootstrapLocationsFile =
            new File(new File(sb.getAppPath(), "defaults"), "yacy.networks");
        final Set<String> networkBootstrapLocations = FileUtils.loadList(networkBootstrapLocationsFile);

        if ( post != null ) {

            // store this call as api call
            sb.tables.recordAPICall(
                post,
                "ConfigNetwork_p.html",
                WorkTables.TABLE_API_TYPE_CONFIGURATION,
                "network settings");

            if ( post.containsKey("changeNetwork") ) {
                String networkDefinition =
                    post.get("networkDefinition", "defaults/yacy.network.freeworld.unit");
                final String networkDefinitionURL =
                        post.get("networkDefinitionURL", "");
                if ( !networkDefinitionURL.equals("")) {
                	networkDefinition = networkDefinitionURL;
                }
                if ( networkDefinition.equals(sb.getConfig("network.unit.definition", "")) ) {
                    // no change
                    commit = 3;
                } else {
                    // shut down old network and index, start up new network and index
                    commit = 1;
                    sb.switchNetwork(networkDefinition);
                }
            }

            if ( post.containsKey("save") ) {

                // DHT control
                boolean indexDistribute = "on".equals(post.get("indexDistribute", ""));
                boolean indexReceive = "on".equals(post.get("indexReceive", ""));
                boolean indexReceiveSearch = "on".equals(post.get("indexReceiveSearch", ""));
                if ( !indexReceive ) {
                    // remove heuristics
                    sb.setConfig(SwitchboardConstants.HEURISTIC_SITE, false);
                    sb.setConfig(SwitchboardConstants.HEURISTIC_OPENSEARCH, false);
                }
                final boolean robinsonmode = "robinson".equals(post.get("network", ""));
                if ( robinsonmode ) {
                    indexDistribute = false;
                    indexReceive = false;
                    commit = 1;
                } else {
                    if ( !indexDistribute && !indexReceive ) {
                        prop.put("commitDHTIsRobinson", "1");
                        commit = 2;
                    } else if ( indexDistribute && indexReceive ) {
                        commit = 1;
                    } else {
                        if ( !indexReceive ) {
                            prop.put("commitDHTNoGlobalSearch", "1");
                        }
                        commit = 1;
                    }
                }

                sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, indexDistribute);
                sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_CRAWLING, "on".equals(post.get("indexDistributeWhileCrawling", "")));
                sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_INDEXING, "on".equals(post.get("indexDistributeWhileIndexing", "")));
                sb.peers.mySeed().setFlagAcceptRemoteIndex(indexReceive);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, indexReceive);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, indexReceiveSearch);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, false);

                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_BLOCK_BLACKLIST, "on".equals(post.get("indexReceiveBlockBlacklist", "")));

                if (post.containsKey("peertags")) {
                    sb.peers.mySeed().setPeerTags(MapTools.string2set(normalizedList(post.get("peertags")), ","));
                }

                sb.setConfig("cluster.mode", post.get(SwitchboardConstants.CLUSTER_MODE, SwitchboardConstants.CLUSTER_MODE_PUBLIC_PEER));
                sb.setConfig("cluster.peers.ipport", checkIPPortList(post.get("cluster.peers.ipport", "")));
                sb.setConfig(
                    "cluster.peers.yacydomain",
                    checkYaCyDomainList(post.get("cluster.peers.yacydomain", "")));

                // update the cluster hash set
                sb.clusterhashes = sb.peers.clusterHashes(sb.getConfig("cluster.peers.yacydomain", ""));
            }
        }

        // write answer code
        prop.put("commit", commit);

        // write remote crawl request settings
        prop.put("crawlResponse", sb.getConfigBool("crawlResponse", false) ? "1" : "0");
        final long RTCbusySleep =
            Math
                .max(1, env.getConfigInt(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, 100));
        final int RTCppm = (int) (60000L / RTCbusySleep);
        prop.put("acceptCrawlLimit", RTCppm);

        prop.put("indexDistributeWhileCrawling.on", sb.getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_CRAWLING, true));
        prop.put("indexDistributeWhileCrawling.off", !sb.getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_CRAWLING, true));
        prop.put("indexDistributeWhileIndexing.on", sb.getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_INDEXING, true));
        prop.put("indexDistributeWhileIndexing.off", !sb.getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_INDEXING, true));
        prop.put("indexReceiveBlockBlacklistChecked.on", sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_BLOCK_BLACKLIST, true));
        prop.put("indexReceiveBlockBlacklistChecked.off", !sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_BLOCK_BLACKLIST, true));
        prop.putHTML("peertags", MapTools.set2string(sb.peers.mySeed().getPeerTags(), ",", false));

        final boolean indexDistribute = sb.getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW, true);
        final boolean indexReceive = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
        final boolean indexReceiveSearch = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true);
        prop.put("indexDistributeChecked", indexDistribute);
        prop.put("indexReceiveChecked", indexReceive);
        prop.put("indexReceiveSearchChecked", indexReceiveSearch);
        
        // set seed information directly
        sb.peers.mySeed().setFlagAcceptRemoteCrawl(sb.getConfigBool("crawlResponse", false));
        sb.peers.mySeed().setFlagAcceptRemoteIndex(indexReceive);

        // set p2p/robinson mode flags and values
        prop.put("p2p.checked", indexDistribute || indexReceive);
        prop.put("robinson.checked", !(indexDistribute || indexReceive));
        prop.putHTML("cluster.peers.ipport", sb.getConfig("cluster.peers.ipport", ""));
        prop.putHTML("cluster.peers.yacydomain", sb.getConfig("cluster.peers.yacydomain", ""));
        StringBuilder hashes = new StringBuilder();
        for ( final byte[] h : sb.clusterhashes.keySet() ) {
            hashes.append(", ").append(ASCII.String(h));
        }
        if ( hashes.length() > 2 ) {
            hashes = hashes.delete(0, 2);
        }

        prop.put("cluster.peers.yacydomain.hashes", hashes.toString());

        // set p2p mode flags
        prop.put("privatepeerChecked", SwitchboardConstants.CLUSTER_MODE_PRIVATE_PEER.equals(sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "")));
        prop.put("publicclusterChecked", SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER.equals(sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "")));
        prop.put("publicpeerChecked", SwitchboardConstants.CLUSTER_MODE_PUBLIC_PEER.equals(sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "")));

        // set network configuration
        prop.putHTML("network.unit.definition", sb.getConfig("network.unit.definition", ""));
        prop.putHTML("network.unit.name", sb.getConfig(SwitchboardConstants.NETWORK_NAME, ""));
        prop.putHTML("network.unit.description", sb.getConfig("network.unit.description", ""));
        prop.putHTML("network.unit.domain", sb.getConfig(SwitchboardConstants.NETWORK_DOMAIN, ""));
        prop.putHTML("network.unit.dht", sb.getConfig(SwitchboardConstants.DHT_ENABLED, ""));
        networkBootstrapLocations.remove(sb.getConfig("network.unit.definition", ""));
        int c = 0;
        for ( final String s : networkBootstrapLocations ) {
            prop.put("networks_" + c++ + "_network", s);
        }
        prop.put("networks", c);

        return prop;
    }

    private static String normalizedList(String input) {
        input = input.replace(' ', ',');
        input = input.replace(' ', ';');
        input = input.replaceAll(",,", ",");
        if ( input.length() > 0 && input.charAt(0) == ',' ) {
            input = input.substring(1);
        }
        if ( input.endsWith(",") ) {
            input = input.substring(0, input.length() - 1);
        }
        return input;
    }

    private static String checkYaCyDomainList(final String input) {
        final String[] array = normalizedList(input).split(",");
        final StringBuilder output = new StringBuilder();
        for ( final String element : array ) {
            if ( (element.endsWith(".yacyh"))
                || (element.endsWith(".yacy"))
                || (element.indexOf(".yacyh=", 0) > 0)
                || (element.indexOf(".yacy=", 0) > 0) ) {
                output.append(",").append(element);
            }
        }

        if ( output.length() == 0 ) {
            return input;
        }
        return output.delete(0, 1).toString();
    }

    private static String checkIPPortList(final String input) {
        final String[] array = normalizedList(input).split(",");
        final StringBuilder output = new StringBuilder();
        for ( final String element : array ) {
            if ( element.indexOf(':', 0) >= 9 ) {
                output.append(",").append(element);
            }
        }
        if ( input.isEmpty() ) {
            return input;
        }
        return output.delete(0, 1).toString();
    }
}
