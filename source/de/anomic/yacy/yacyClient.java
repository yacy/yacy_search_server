// yacyClient.java
// -------------------------------------
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
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpc;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverObjects;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.xml.rssReader;

public final class yacyClient {

    public static int publishMySeed(String address, String otherHash) {
        // this is called to enrich the seed information by
        // - own address (if peer is behind a nat/router)
        // - check peer type (virgin/junior/senior/principal)
        // to do this, we send a 'Hello' to another peer
        // this carries the following information:
        // 'iam' - own hash
        // 'youare' - remote hash, to verify that we are correct
        // 'key' - a session key that the remote peer may use to answer
        // and the own seed string
        // we expect the following information to be send back:
        // - 'yourip' the ip of the connection peer (we)
        // - 'yourtype' the type of this peer that the other peer checked by asking for a specific word
        // and the remote seed string
        // the number of new seeds are returned
        // one exceptional failure case is when we know the other's peers hash, the other peers responds correctly
        // but they appear to be another peer by comparisment of the other peer's hash
        // this works of course only if we know the other peer's hash.
        
        HashMap<String, String> result = null;
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), null);
        for (int retry = 0; retry < 3; retry++) try {
            // generate request
            post.put("count", "20");
            post.put("seed", yacyCore.seedDB.mySeed().genSeedStr(post.get("key", "")));
            yacyCore.log.logFine("yacyClient.publishMySeed thread '" + Thread.currentThread().getName() + "' contacting peer at " + address);
            // send request
            result = nxTools.table(
                    httpc.wput(new yacyURL("http://" + address + "/yacy/hello.html", null),
                               yacySeed.b64Hash2hexHash(otherHash) + ".yacyh",
                               12000, 
                               null, 
                               null,
                               proxyConfig(),
                               post,
                               null
                    ), "UTF-8"
            );
            break;
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                yacyCore.log.logFine("yacyClient.publishMySeed thread '" + Thread.currentThread().getName() + "' interrupted.");
                return -1;
            } else {
                yacyCore.log.logFine("yacyClient.publishMySeed thread '" + Thread.currentThread().getName() + "' exception: " + e.getMessage() + "; retry = " + retry); // here VERY OFTEN a 'Connection reset' appears. What is the cause?
                // try again (go into loop)
            }
            result = null;
        }
        
        if (result == null || result.size() < 3) {
            yacyCore.log.logFine("yacyClient.publishMySeed result error: " +
            ((result == null) ? "result null" : ("result=" + result.toString())));
            return -1;
        }

        // check consistency with expectation
        yacySeed otherPeer = null;
        float otherPeerVersion = 0;
        String seed;
        if ((otherHash != null) &&
            (otherHash.length() > 0) &&
            ((seed = (String) result.get("seed0")) != null)) {
            if (seed.length() > yacySeed.maxsize) {
                yacyCore.log.logInfo("hello/client 0: rejected contacting seed; too large (" + seed.length() + " > " + yacySeed.maxsize + ")");
            } else {
                otherPeer = yacySeed.genRemoteSeed(seed, post.get("key", ""), true);
                if (otherPeer == null || !otherPeer.hash.equals(otherHash)) {
                    yacyCore.log.logFine("yacyClient.publishMySeed: consistency error: other peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' wrong");
                    return -1; // no success
                }
                otherPeerVersion = otherPeer.getVersion();
            }
        }

        // set my own seed according to new information
        // we overwrite our own IP number only, if we do not portForwarding
        if (serverCore.portForwardingEnabled || serverCore.useStaticIP) {
            yacyCore.seedDB.mySeed().put(yacySeed.IP, serverDomains.myPublicIP());
        } else {
            yacyCore.seedDB.mySeed().put(yacySeed.IP, (String) result.get("yourip"));
        }

        /* If we have port forwarding enabled but the other peer uses a too old yacy version
         * we can ignore the seed-type that was reported by the peer.
         * 
         * Otherwise we have to change our seed-type  
         * 
         * @see serverCore#portForwardingEnabled 
         */
        if (!serverCore.portForwardingEnabled || otherPeerVersion >= yacyVersion.YACY_SUPPORTS_PORT_FORWARDING) {
            String mytype = (String) result.get(yacySeed.YOURTYPE);
            if (mytype == null) { mytype = ""; }        
            yacyAccessible accessible = new yacyAccessible();
            if (mytype.equals(yacySeed.PEERTYPE_SENIOR)||mytype.equals(yacySeed.PEERTYPE_PRINCIPAL)) {
                accessible.IWasAccessed = true;
                if (yacyCore.seedDB.mySeed().isPrincipal()) {
                    mytype = yacySeed.PEERTYPE_PRINCIPAL;
                }
            } else {
                accessible.IWasAccessed = false;
            }
            accessible.lastUpdated = System.currentTimeMillis();
            yacyCore.amIAccessibleDB.put(otherHash, accessible);

            /* 
             * If we were reported as junior we have to check if your port forwarding channel is broken
             * If this is true we try to reconnect the sch channel to the remote server now.
             */
            if (mytype.equalsIgnoreCase(yacySeed.PEERTYPE_JUNIOR)) {
                yacyCore.log.logInfo("yacyClient.publishMySeed: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as junior.");
                if (serverCore.portForwardingEnabled) {
                    if (!Thread.currentThread().isInterrupted() && 
                         serverCore.portForwarding != null && 
                        !serverCore.portForwarding.isConnected()
                    ) {
                        yacyCore.log.logWarning("yacyClient.publishMySeed: Broken portForwarding channel detected. Trying to reconnect ...");                        
                        try {
                            serverCore.portForwarding.reconnect();
                        } catch (IOException e) {
                            yacyCore.log.logWarning("yacyClient.publishMySeed: Unable to reconnect to port forwarding host.");
                        }
                    }
                }
            } else if ((mytype.equalsIgnoreCase(yacySeed.PEERTYPE_SENIOR)) ||
                       (mytype.equalsIgnoreCase(yacySeed.PEERTYPE_PRINCIPAL))) {
                yacyCore.log.logFine("yacyClient.publishMySeed: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as " + mytype + ", accepted other peer.");
            } else {
                // wrong type report
                yacyCore.log.logFine("yacyClient.publishMySeed: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as " + mytype + ", rejecting other peer.");
                return -1;
            }
            if (yacyCore.seedDB.mySeed().orVirgin().equals(yacySeed.PEERTYPE_VIRGIN))
                yacyCore.seedDB.mySeed().put(yacySeed.PEERTYPE, mytype);
        }

        final String error = yacyCore.seedDB.mySeed().isProper();
        if (error != null) {
            yacyCore.log.logSevere("yacyClient.publishMySeed mySeed error - not proper: " + error);
            return -1;
        }

        //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time

        // read the seeds that the peer returned and integrate them into own database
        int i = 0;
        int count = 0;
        String seedStr;
        while ((seedStr = (String) result.get("seed" + i++)) != null) {
            // integrate new seed into own database
            // the first seed, "seed0" is the seed of the responding peer
            if (seedStr.length() > yacySeed.maxsize) {
                yacyCore.log.logInfo("hello/client: rejected contacting seed; too large (" + seedStr.length() + " > " + yacySeed.maxsize + ")");
            } else {
                //System.out.println("DEBUG yacyClient.publishMySeed seedStr = " + seedStr);
                if (yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(seedStr, post.get("key", ""), true), (i == 1))) count++;
            }
        }
        return count;
    }

    public static yacySeed querySeed(yacySeed target, String seedHash) {
        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash);
        post.put("object", "seed");
        post.put("env", seedHash);

        // send request
        try {
            final HashMap<String, String> result = nxTools.table(
                    httpc.wput(new yacyURL("http://" + target.getClusterAddress() + "/yacy/query.html", null),
                               target.getHexHash() + ".yacyh",
                               10000,
                               null,
                               null,
                               proxyConfig(),
                               post,
                               null
                    ), "UTF-8"
            );

            if (result == null || result.size() == 0) { return null; }
            target.setFlagDirectConnect(true);
            target.setLastSeenUTC();
            //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time
            return yacySeed.genRemoteSeed((String) result.get("response"), post.get("key", ""), true);
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.querySeed error:" + e.getMessage());
            return null;
        }
    }

    public static int queryRWICount(yacySeed target, String wordHash) {
        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash);
        post.put("object", "rwicount");
        post.put("ttl", "0");
        post.put("env", wordHash);
            
        // send request
        try {
            final HashMap<String, String> result = nxTools.table(
                    httpc.wput(new yacyURL("http://" + target.getClusterAddress() + "/yacy/query.html", null),
                               target.getHexHash() + ".yacyh",
                               10000, 
                               null, 
                               null,
                               proxyConfig(),
                               post,
                               null
                    ), "UTF-8"
            );
            
            if (result == null || result.size() == 0) { return -1; }
            final String resp = (String) result.get("response");
            if (resp == null) {
                return -1;
            } else try {
                target.setFlagDirectConnect(true);
                target.setLastSeenUTC();
                return Integer.parseInt(resp);
            } catch (NumberFormatException e) {
                return -1;
            }
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.queryRWICount error:" + e.getMessage());
            return -1;
        }
    }

    public static int queryUrlCount(yacySeed target) {        
        if (target == null) { return -1; }
        if (yacyCore.seedDB.mySeed() == null) return -1;

        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash);
        post.put("object", "lurlcount");
        post.put("ttl", "0");
        post.put("env", "");

        // send request
        try {
            final HashMap<String, String> result = nxTools.table(
                httpc.wput(new yacyURL("http://" + target.getClusterAddress() + "/yacy/query.html", null),
                           target.getHexHash() + ".yacyh",
                           10000,
                           null,
                           null,
                           proxyConfig(),
                           post,
                           null
                ), "UTF-8"
            );

            if ((result == null) || (result.size() == 0)) return -1;
            final String resp = (String) result.get("response");
            if (resp == null) {
                return -1;
            } else try {
                target.setFlagDirectConnect(true);
                target.setLastSeenUTC();
                return Integer.parseInt(resp);
            } catch (NumberFormatException e) {
                return -1;
            }
        } catch (IOException e) {
            yacyCore.log.logSevere("yacyClient.queryUrlCount error asking peer '" + target.getName() + "':" + e.toString());
            return -1;
        }
    }

    public static rssReader queryRemoteCrawlURLs(yacySeed target, int count) {
        // returns a list of 
        if (target == null) { return null; }
        if (yacyCore.seedDB.mySeed() == null) return null;
        
        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash);
        post.put("call", "remotecrawl");
        post.put("count", count);
        
        // send request
        try {
            final byte[] result = 
                httpc.wput(new yacyURL("http://" + target.getClusterAddress() + "/yacy/urls.xml", null),
                           target.getHexHash() + ".yacyh",
                           60000, /* a long time-out is needed */
                           null, 
                           null,
                           proxyConfig(),
                           post,
                           null
                );
            
            rssReader reader = rssReader.parse(result);
            if (reader == null) {
                // case where the rss reader does not understand the content
                yacyCore.log.logWarning("yacyClient.queryRemoteCrawlURLs failed asking peer '" + target.getName() + "': probably bad response from remote peer");
                System.out.println("***DEBUG*** rss input = " + new String(result));
                target.put(yacySeed.RCOUNT, "0");
                yacyCore.seedDB.update(target.hash, target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
                //e.printStackTrace();
                return null;
            }
            return reader;
        } catch (IOException e) {
            yacyCore.log.logSevere("yacyClient.queryRemoteCrawlURLs error asking peer '" + target.getName() + "':" + e.toString());
            return null;
        }
    }

    public static String[] search(
            String wordhashes,
            String excludehashes,
            String urlhashes,
            String prefer,
            String filter,
            int count,
            int maxDistance,
            boolean global, 
            int partitions,
            yacySeed target,
            plasmaWordIndex wordIndex,
            plasmaSearchRankingProcess containerCache,
            Map<String, TreeMap<String, String>> abstractCache,
            plasmaURLPattern blacklist,
            plasmaSearchRankingProfile rankingProfile,
            kelondroBitfield constraint
    ) {
        // send a search request to peer with remote Hash
        // this mainly converts the words into word hashes

        // INPUT:
        // iam        : complete seed of the requesting peer
        // youare     : seed hash of the target peer, used for testing network stability
        // key        : transmission key for response
        // search     : a list of search words
        // hsearch    : a string of word hashes
        // fwdep      : forward depth. if "0" then peer may NOT ask another peer for more results
        // fwden      : forward deny, a list of seed hashes. They may NOT be target of forward hopping
        // count      : maximum number of wanted results
        // global     : if "true", then result may consist of answers from other peers
        // partitions : number of remote peers that are asked (for evaluation of QPM)
        // duetime    : maximum time that a peer should spent to create a result

        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash);
        post.put("myseed", yacyCore.seedDB.mySeed().genSeedStr(post.get("key", "")));
        post.put("count", Math.max(10, count));
        post.put("resource", ((global) ? "global" : "local"));
        post.put("partitions", partitions);
        post.put("query", wordhashes);
        post.put("exclude", excludehashes);
        post.put("duetime", 1000);
        post.put("urls", urlhashes);
        post.put("prefer", prefer);
        post.put("filter", filter);
        post.put("ttl", "0");
        post.put("maxdist", maxDistance);
        post.put("profile", crypt.simpleEncode(rankingProfile.toExternalString()));
        post.put("constraint", (constraint == null) ? "" : constraint.exportB64());
        if (abstractCache != null) post.put("abstracts", "auto");
        final long timestamp = System.currentTimeMillis();

        // send request
        HashMap<String, String> result = null;
        try {
            result = nxTools.table(
                httpc.wput(new yacyURL("http://" + target.getClusterAddress() + "/yacy/search.html", null),
                        target.getHexHash() + ".yacyh",
                        60000, 
                        null, 
                        null,
                        proxyConfig(),
                        post,
                        null
                    ), "UTF-8"
                );
        } catch (IOException e) {
            yacyCore.log.logFine("SEARCH failed FROM " + target.hash + ":" + target.getName() + " (" + e.getMessage() + "), score=" + target.selectscore + ", DHTdist=" + yacyDHTAction.dhtDistance(target.hash, wordhashes.substring(0, 12)));
            yacyCore.peerActions.peerDeparture(target, "search request to peer created io exception: " + e.getMessage());
            return null;
        }

        if ((result == null) || (result.size() == 0)) {
            yacyCore.log.logFine("SEARCH failed FROM "
                    + target.hash
                    + ":"
                    + target.getName()
                    + " (zero response), score="
                    + target.selectscore
                    + ", DHTdist="
                    + yacyDHTAction.dhtDistance(target.hash, wordhashes
                            .substring(0, 12)));
            return null;
        }

        // compute all computation times
        final long totalrequesttime = System.currentTimeMillis() - timestamp;
        
        // OUTPUT:
        // version : application version of responder
        // uptime : uptime in seconds of responder
        // total : number of total available LURL's for this search
        // count : number of returned LURL's for this search
        // resource<n> : LURL of search
        // fwhop : hops (depth) of forwards that had been performed to construct this result
        // fwsrc : peers that helped to construct this result
        // fwrec : peers that would have helped to construct this result (recommendations)
        // searchtime : time that the peer actually spent to create the result
        // references : references (search hints) that was calculated during search
        
        // now create a plasmaIndex out of this result
        // System.out.println("yacyClient: " + ((urlhashes.length() == 0) ? "primary" : "secondary")+ " search result = " + result.toString()); // debug
        
        int results = 0, joincount = 0;
        try {
            results = Integer.parseInt(result.get("count"));
            joincount = Integer.parseInt(result.get("joincount"));
        } catch (NumberFormatException e) {
            yacyCore.log.logFine("SEARCH failed FROM " + target.hash + ":" + target.getName() + ", wrong output format");
            yacyCore.peerActions.peerDeparture(target, "search request to peer created number format exception");
            return null;
        }
        // System.out.println("***result count " + results);

        // create containers
        final int words = wordhashes.length() / yacySeedDB.commonHashLength;
        indexContainer[] container = new indexContainer[words];
        for (int i = 0; i < words; i++) {
            container[i] = plasmaWordIndex.emptyContainer(wordhashes.substring(i * yacySeedDB.commonHashLength, (i + 1) * yacySeedDB.commonHashLength), count);
        }

        // insert results to containers
        indexURLEntry urlEntry;
        String[] urls = new String[results];
        for (int n = 0; n < results; n++) {
            // get one single search result
            urlEntry = wordIndex.loadedURL.newEntry((String) result.get("resource" + n));
            if (urlEntry == null) continue;
            assert (urlEntry.hash().length() == 12) : "urlEntry.hash() = " + urlEntry.hash();
            if (urlEntry.hash().length() != 12) continue; // bad url hash
            indexURLEntry.Components comp = urlEntry.comp();
            if (blacklist.isListed(plasmaURLPattern.BLACKLIST_SEARCH, comp.url())) {
                yacyCore.log.logInfo("remote search (client): filtered blacklisted url " + comp.url() + " from peer " + target.getName());
                continue; // block with backlist
            }
            
            if (!plasmaSwitchboard.getSwitchboard().acceptURL(comp.url())) {
                yacyCore.log.logInfo("remote search (client): rejected url outside of our domain " + comp.url() + " from peer " + target.getName());
                continue; // reject url outside of our domain
            }

            // save the url entry
            indexRWIEntry entry;
            if (urlEntry.word() == null) {
                yacyCore.log.logWarning("remote search (client): no word attached from peer " + target.getName() + ", version " + target.getVersion());
                continue; // no word attached
            }

            // the search-result-url transports all the attributes of word indexes
            entry = urlEntry.word();
            if (!(entry.urlHash().equals(urlEntry.hash()))) {
                yacyCore.log.logInfo("remote search (client): url-hash " + urlEntry.hash() + " does not belong to word-attached-hash " + entry.urlHash() + "; url = " + comp.url() + " from peer " + target.getName());
                continue; // spammed
            }

            // passed all checks, store url
            try {
                wordIndex.loadedURL.store(urlEntry);
                wordIndex.loadedURL.stack(urlEntry, yacyCore.seedDB.mySeed().hash, target.hash, 2);
            } catch (IOException e) {
                yacyCore.log.logSevere("could not store search result", e);
                continue; // db-error
            }

            if (urlEntry.snippet() != null) {
                // we don't store the snippets along the url entry,
                // because they are search-specific.
                // instead, they are placed in a snipped-search cache.
                // System.out.println("--- RECEIVED SNIPPET '" + link.snippet() + "'");
                plasmaSnippetCache.storeToCache(wordhashes, urlEntry.hash(), urlEntry.snippet());
            }
            
            // add the url entry to the word indexes
            for (int m = 0; m < words; m++) {
                container[m].add(entry, System.currentTimeMillis());
            }
            
            // store url hash for statistics
            urls[n] = urlEntry.hash();
        }

        // store remote result to local result container
        synchronized (containerCache) {
            // insert one container into the search result buffer
            containerCache.insertRanked(container[0], false, joincount); // one is enough
            
            // integrate remote topwords
            String references = (String) result.get("references");
            yacyCore.log.logInfo("remote search (client): peer " + target.getName() + " sent references " + references);
            if (references != null) {
                // add references twice, so they can be countet (must have at least 2 entries)
                containerCache.addReferences(references.split(","));
                containerCache.addReferences(references.split(","));
            }
        }
        
        // read index abstract
        if (abstractCache != null) {
            Iterator<Map.Entry<String, String>> i = result.entrySet().iterator();
            Map.Entry<String, String> entry;
            TreeMap<String, String> singleAbstract;
            String wordhash;
            serverByteBuffer ci;
            while (i.hasNext()) {
                entry = i.next();
                if (entry.getKey().startsWith("indexabstract.")) {
                    wordhash = entry.getKey().substring(14);
                    synchronized (abstractCache) {
                        singleAbstract = (TreeMap<String, String>) abstractCache.get(wordhash); // a mapping from url-hashes to a string of peer-hashes
                        if (singleAbstract == null) singleAbstract = new TreeMap<String, String>();
                        ci = new serverByteBuffer(entry.getValue().getBytes());
                        //System.out.println("DEBUG-ABSTRACTFETCH: for word hash " + wordhash + " received " + ci.toString());
                        indexContainer.decompressIndex(singleAbstract, ci, target.hash);
                        abstractCache.put(wordhash, singleAbstract);
                    }
                }
            }
        }

        // insert the containers to the index
        for (int m = 0; m < words; m++) {
            wordIndex.addEntries(container[m], true);
        }
        
        // generate statistics
        long searchtime;
        try {
            searchtime = Integer.parseInt((String) result.get("searchtime"));
        } catch (NumberFormatException e) {
            searchtime = totalrequesttime;
        }
        yacyCore.log.logFine("SEARCH "
                + results
                + " URLS FROM "
                + target.hash
                + ":"
                + target.getName()
                + ", score="
                + target.selectscore
                + ", DHTdist="
                + ((wordhashes.length() < 12) ? "void" : Double
                        .toString(yacyDHTAction.dhtDistance(target.hash,
                                wordhashes.substring(0, 12))))
                + ", searchtime=" + searchtime + ", netdelay="
                + (totalrequesttime - searchtime) + ", references="
                + result.get("references"));
        return urls;
    }

    public static HashMap<String, String> permissionMessage(String targetHash) {
        // ask for allowed message size and attachement size
        // if this replies null, the peer does not answer
        if (yacyCore.seedDB == null || yacyCore.seedDB.mySeed() == null) { return null; }

        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetHash);
        post.put("process", "permission");
        
        // send request
        try {
            final HashMap<String, String> result = nxTools.table(
                httpc.wput(new yacyURL("http://" + targetAddress(targetHash) + "/yacy/message.html", null),
                           yacySeed.b64Hash2hexHash(targetHash)+ ".yacyh",
                           8000, 
                           null, 
                           null,
                           proxyConfig(),
                           post,
                           null
                ), "UTF-8"
            );
            return result;
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.permissionMessage error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, String> postMessage(String targetHash, String subject, byte[] message) {
        // this post a message to the remote message board

        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetHash);
        post.put("process", "post");
        post.put("myseed", yacyCore.seedDB.mySeed().genSeedStr(post.get("key", "")));
        post.put("subject", subject);
        try {
            post.put("message", new String(message, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            post.put("message", new String(message));
        }

        // send request
        try {
            final HashMap<String, String> result = nxTools.table(
                httpc.wput(new yacyURL("http://" + targetAddress(targetHash) + "/yacy/message.html", null),
                           yacySeed.b64Hash2hexHash(targetHash)+ ".yacyh",
                           20000, 
                           null, 
                           null,
                           proxyConfig(),
                           post,
                           null
                ), "UTF-8"
            );
            return result;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }
    
    public static String targetAddress(String targetHash) {
        // find target address    
        String address;
        if (targetHash.equals(yacyCore.seedDB.mySeed().hash)) {
            address = yacyCore.seedDB.mySeed().getClusterAddress();
        } else {
            final yacySeed targetSeed = yacyCore.seedDB.getConnected(targetHash);
            if (targetSeed == null) { return null; }
            address = targetSeed.getClusterAddress();
        }
        if (address == null) address = "localhost:8080";
        return address;
    }
    
    public static HashMap<String, String> transferPermission(String targetAddress, long filesize, String filename) {

        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), null);
        post.put("process", "permission");
        post.put("purpose", "crcon");
        post.put("filename", filename);
        post.put("filesize", Long.toString(filesize));
        post.put("can-send-protocol", "http");
        
        // send request
        try {
            final yacyURL url = new yacyURL("http://" + targetAddress + "/yacy/transfer.html", null);
            final HashMap<String, String> result = nxTools.table(
                httpc.wput(url,
                           url.getHost(),
                           6000, 
                           null, 
                           null,
                           proxyConfig(),
                           post,
                           null
                ), "UTF-8"
            );
            return result;
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.permissionTransfer error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, String> transferStore(String targetAddress, String access, String filename, byte[] file) {
        
        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), null);
        post.put("process", "store");
        post.put("purpose", "crcon");
        post.put("filename", filename);
        post.put("filesize", Long.toString(file.length));
        post.put("md5", serverCodings.encodeMD5Hex(file));
        post.put("access", access);
        HashMap<String, byte[]> files = new HashMap<String, byte[]>();
        files.put("filename", file);
        
        // send request
        try {
            final yacyURL url = new yacyURL("http://" + targetAddress + "/yacy/transfer.html", null);
            final HashMap<String, String> result = nxTools.table(
                httpc.wput(url,
                           url.getHost(),
                           20000, 
                           null, 
                           null,
                           proxyConfig(),
                           post,
                           files
                ), "UTF-8"
            );
            return result;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }
    
    public static String transfer(String targetAddress, String filename, byte[] file) {
        HashMap<String, String> phase1 = transferPermission(targetAddress, file.length, filename);
        if (phase1 == null) return "no connection to remote address " + targetAddress + "; phase 1";
        String access = (String) phase1.get("access");
        String nextaddress = (String) phase1.get("address");
        String protocol = (String) phase1.get("protocol");
        //String path = (String) phase1.get("path");
        //String maxsize = (String) phase1.get("maxsize");
        String response = (String) phase1.get("response");
        if ((response == null) || (protocol == null) || (access == null)) return "wrong return values from other peer; phase 1";
        if (!(response.equals("ok"))) return "remote peer rejected transfer: " + response;
        String accesscode = serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(access));
        if (protocol.equals("http")) {
            HashMap<String, String> phase2 = transferStore(nextaddress, accesscode, filename, file);
            if (phase2 == null) return "no connection to remote address " + targetAddress + "; phase 2";
            response = (String) phase2.get("response");
            if (response == null) return "wrong return values from other peer; phase 2";
            if (!(response.equals("ok"))) {
                return "remote peer failed with transfer: " + response;
            } 
            return null;
        }
        return "wrong protocol: " + protocol;
    }

    public static HashMap<String, String> crawlReceipt(yacySeed target, String process, String result, String reason, indexURLEntry entry, String wordhashes) {
        assert (target != null);
        assert (yacyCore.seedDB.mySeed() != null);
        assert (yacyCore.seedDB.mySeed() != target);

        /*
         the result can have one of the following values:
         negative cases, no retry
           unavailable - the resource is not avaiable (a broken link); not found or interrupted
           robot       - a robot-file has denied to crawl that resource
         
         negative cases, retry possible
           rejected    - the peer has rejected to load the resource
           dequeue     - peer too busy - rejected to crawl
         
         positive cases with crawling
           fill        - the resource was loaded and processed
           update      - the resource was already in database but re-loaded and processed
         
         positive cases without crawling
           known       - the resource is already in database, believed to be fresh and not reloaded
           stale       - the resource was reloaded but not processed because source had no changes
         
         */
        
        // prepare request
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash);
        post.put("process", process);
        post.put("urlhash", ((entry == null) ? "" : entry.hash()));
        post.put("result", result);
        post.put("reason", reason);
        post.put("wordh", wordhashes);
        post.put("lurlEntry", ((entry == null) ? "" : crypt.simpleEncode(entry.toString(), post.get("key", ""))));
        
        // determining target address
        final String address = target.getClusterAddress();
        if (address == null) { return null; }
            
        // send request
        try {
            return nxTools.table(
                    httpc.wput(new yacyURL("http://" + address + "/yacy/crawlReceipt.html", null),
                               target.getHexHash() + ".yacyh",
                               60000, 
                               null, 
                               null,
                               proxyConfig(),
                               post,
                               null
                    ), "UTF-8"
            );
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.crawlReceipt error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, Object> transferIndex(yacySeed target, indexContainer[] indexes, HashMap<String, indexURLEntry> urlCache, boolean gzipBody, int timeout) {
        
        HashMap<String, Object> resultObj = new HashMap<String, Object>();
        int payloadSize = 0;
        try {
            
            // check if we got all necessary urls in the urlCache (only for debugging)
            Iterator<indexRWIRowEntry> eenum;
            indexRWIEntry entry;
            for (int i = 0; i < indexes.length; i++) {
                eenum = indexes[i].entries();
                while (eenum.hasNext()) {
                    entry = (indexRWIEntry) eenum.next();
                    if (urlCache.get(entry.urlHash()) == null) {
                        yacyCore.log.logFine("DEBUG transferIndex: to-send url hash '" + entry.urlHash() + "' is not contained in urlCache");
                    }
                }
            }        
            
            // transfer the RWI without the URLs
            HashMap<String, String> in = transferRWI(target, indexes, gzipBody, timeout);
            resultObj.put("resultTransferRWI", in);

            if (in == null) {
                resultObj.put("result", "no_connection_1");
                return resultObj;
            }        
            if (in.containsKey("indexPayloadSize")) payloadSize += Integer.parseInt(in.get("indexPayloadSize"));

            String result = (String) in.get("result");
            if (result == null) { 
                resultObj.put("result", "no_result_1"); 
                return resultObj;
            }

            if (!(result.equals("ok"))) {
                target.setFlagAcceptRemoteIndex(false);
                yacyCore.seedDB.update(target.hash, target);
                resultObj.put("result", result);
                return resultObj;
            }

            // in now contains a list of unknown hashes
            final String uhss = (String) in.get("unknownURL");
            if (uhss == null) {
                resultObj.put("result","no_unknownURL_tag_in_response");
                return resultObj;
            }
            if (uhss.length() == 0) { return resultObj; } // all url's known, we are ready here

            final String[] uhs = uhss.split(",");
            if (uhs.length == 0) { return resultObj; } // all url's known

            // extract the urlCache from the result
            indexURLEntry[] urls = new indexURLEntry[uhs.length];
            for (int i = 0; i < uhs.length; i++) {
                urls[i] = (indexURLEntry) urlCache.get(uhs[i]);
                if (urls[i] == null) {
                    yacyCore.log.logFine("DEBUG transferIndex: requested url hash '" + uhs[i] + "', unknownURL='" + uhss + "'");
                }
            }
            
            in = transferURL(target, urls, gzipBody, timeout);
            resultObj.put("resultTransferURL", in);
            
            if (in == null) {
                resultObj.put("result","no_connection_2");
                return resultObj;
            }
            if (in.containsKey("urlPayloadSize")) payloadSize += Integer.parseInt(in.get("urlPayloadSize"));
            
            result = (String) in.get("result");
            if (result == null) {
                resultObj.put("result","no_result_2");
                return resultObj;
            }
            if (!(result.equals("ok"))) {
                target.setFlagAcceptRemoteIndex(false);
                yacyCore.seedDB.update(target.hash, target);
                resultObj.put("result",result);
                return resultObj;
            }
    //      int doubleentries = Integer.parseInt((String) in.get("double"));
    //      System.out.println("DEBUG tansferIndex: transferred " + uhs.length + " URL's, double=" + doubleentries);
            
            return resultObj;
        } finally {
            resultObj.put("payloadSize", new Integer(payloadSize));
        }
    }

    private static HashMap<String, String> transferRWI(yacySeed targetSeed, indexContainer[] indexes, boolean gzipBody, int timeout) {
        final String address = targetSeed.getPublicAddress();
        if (address == null) { return null; }

        // prepare post values
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetSeed.hash);
        
        // enabling gzip compression for post request body
        if ((gzipBody) && (targetSeed.getVersion() >= yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS)) {
            post.put(httpc.GZIP_POST_BODY,"true");
        }
        post.put("wordc", Integer.toString(indexes.length));
        
        int indexcount = 0;
        final StringBuffer entrypost = new StringBuffer(indexes.length*73);
        Iterator<indexRWIRowEntry> eenum;
        indexRWIEntry entry;
        for (int i = 0; i < indexes.length; i++) {
            eenum = indexes[i].entries();
            while (eenum.hasNext()) {
                entry = (indexRWIEntry) eenum.next();
                entrypost.append(indexes[i].getWordHash()) 
                         .append(entry.toPropertyForm()) 
                         .append(serverCore.CRLF_STRING);
                indexcount++;
            }
        }

        if (indexcount == 0) {
            // nothing to do but everything ok
            final HashMap<String, String> result = new HashMap<String, String>(2);
            result.put("result", "ok");
            result.put("unknownURL", "");
            return result;
        }

        post.put("entryc", indexcount);
        post.put("indexes", entrypost.toString());  
        try {
            final ArrayList<String> v = nxTools.strings(
                httpc.wput(
                    new yacyURL("http://" + address + "/yacy/transferRWI.html", null), 
                    targetSeed.getHexHash() + ".yacyh",
                    timeout, 
                    null, 
                    null,
                    proxyConfig(), 
                    post,
                    null
                ), "UTF-8");
            // this should return a list of urlhashes that are unknwon
            if ((v != null) && (v.size() > 0)) {
                yacyCore.seedDB.mySeed().incSI(indexcount);
            }
            
            final HashMap<String, String> result = nxTools.table(v);
            // return the transfered index data in bytes (for debugging only)
            result.put("indexPayloadSize", Integer.toString(entrypost.length()));
            return result;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.transferRWI error:" + e.getMessage());
            return null;
        }
    }

    private static HashMap<String, String> transferURL(yacySeed targetSeed, indexURLEntry[] urls, boolean gzipBody, int timeout) {
        // this post a message to the remote message board
        final String address = targetSeed.getPublicAddress();
        if (address == null) { return null; }

        // prepare post values
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetSeed.hash);
        
        // enabling gzip compression for post request body
        if ((gzipBody) && (targetSeed.getVersion() >= yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS)) {
            post.put(httpc.GZIP_POST_BODY,"true");
        }        
        
        String resource = "";
        int urlc = 0;
        int urlPayloadSize = 0;
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                resource = urls[i].toString();                
                if (resource != null) {
                    post.put("url" + urlc, resource);
                    urlPayloadSize += resource.length();
                    urlc++;
                }
            }
        }
        post.put("urlc", urlc);
        try {
            final ArrayList<String> v = nxTools.strings(
                httpc.wput(
                    new yacyURL("http://" + address + "/yacy/transferURL.html", null),
                    targetSeed.getHexHash() + ".yacyh",
                    timeout, 
                    null, 
                    null,
                    proxyConfig(), 
                    post,
                    null
                ), "UTF-8");
            
            if ((v != null) && (v.size() > 0)) {
                yacyCore.seedDB.mySeed().incSU(urlc);
            }
            
            HashMap<String, String> result = nxTools.table(v);
            // return the transfered url data in bytes (for debugging only)
            result.put("urlPayloadSize", Integer.toString(urlPayloadSize));            
            return result;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.transferURL error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, String> getProfile(yacySeed targetSeed) {

        // this post a message to the remote message board
        final serverObjects post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetSeed.hash);
         
        String address = targetSeed.getClusterAddress();
        if (address == null) { address = "localhost:8080"; }
        try {
            return nxTools.table(
                httpc.wput(
                    new yacyURL("http://" + address + "/yacy/profile.html", null), 
                    targetSeed.getHexHash() + ".yacyh",
                    12000,
                    null,
                    null,
                    proxyConfig(), 
                    post,
                    null
                ), "UTF-8");
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.getProfile error:" + e.getMessage());
            return null;
        }
    }
    
    private static final httpRemoteProxyConfig proxyConfig() {
        httpRemoteProxyConfig p = plasmaSwitchboard.getSwitchboard().remoteProxyConfig;
        return ((p != null) && (p.useProxy()) && (p.useProxy4Yacy())) ? p : null;
    }

    public static void main(String[] args) {
        System.out.println("yacyClient Test");
        try {
            final plasmaSwitchboard sb = new plasmaSwitchboard(new File(args[0]), "httpProxy.init", "DATA/SETTINGS/httpProxy.conf", false);
            /*final yacyCore core =*/ new yacyCore(sb);
            yacyCore.peerActions.loadSeedLists();
            final yacySeed target = yacyCore.seedDB.getConnected(args[1]);
            final String wordhashe = plasmaCondenser.word2hash("test");
            //System.out.println("permission=" + permissionMessage(args[1]));
            
            // should we use the proxy?
            boolean useProxy = (sb.remoteProxyConfig != null) && 
                               (sb.remoteProxyConfig.useProxy()) && 
                               (sb.remoteProxyConfig.useProxy4Yacy());            
            
            final HashMap<String, String> result = nxTools.table(
                    httpc.wget(
                            new yacyURL("http://" + target.getPublicAddress() + "/yacy/search.html" +
                                    "?myseed=" + yacyCore.seedDB.mySeed().genSeedStr(null) +
                                    "&youare=" + target.hash + "&key=" +
                                    "&myseed=" + yacyCore.seedDB.mySeed() .genSeedStr(null) +
                                    "&count=10" +
                                    "&resource=global" +
                                    "&query=" + wordhashe +
                                    "&network.unit.name=" + plasmaSwitchboard.getSwitchboard().getConfig("network.unit.name", yacySeed.DFLT_NETWORK_UNIT), null),
                                    target.getHexHash() + ".yacyh",
                                    5000, 
                                    null, 
                                    null, 
                                    (useProxy) ? sb.remoteProxyConfig : null,
                                    null,
                                    null
                    )
                    , "UTF-8");
            System.out.println("Result=" + result.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

}
