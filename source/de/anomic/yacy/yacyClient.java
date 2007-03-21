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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.http.httpc;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchTimingProfile;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;

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
        
        final String key = crypt.randomSalt();
        HashMap result = null;
        try {
            /*
            URL url = new URL("http://" + address + "/yacy/hello.html?iam=" +
                              yacyCore.seedCache.mySeed.hash +
                              "&pattern=&count=20" +
                              "&key=" + key + "&seed=" + yacyCore.seedCache.mySeed.genSeedStr(key));
            yacyCore.log.logDebug("HELLO to URL " + url.toString());
            result = nxTools.table(httpc.wget(url,
                        10000, null, null, yacyCore.seedCache.sb.remoteProxyHost, yacyCore.seedCache.sb.remoteProxyPort));
             */

            // building URL
            final URL url = new URL("http://" + address + "/yacy/hello.html");
            
            // should we use the proxy?
            boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) &&  
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
            
            // adding all needed parameters
            final serverObjects obj = new serverObjects(6);
                obj.put("iam", yacyCore.seedDB.mySeed.hash);
                obj.put("pattern", "");
                obj.put("count", "20");
                obj.put("key", key);
                obj.put("mytime", yacyCore.universalDateShortString(new Date()));
                obj.put("myUTC", System.currentTimeMillis());
                obj.put("seed", yacyCore.seedDB.mySeed.genSeedStr(key));
            
            // sending request
            result = nxTools.table(
                    httpc.wput(url,
                               yacySeed.b64Hash2hexHash(otherHash) + ".yacyh",
                               105000, 
                               null, 
                               null,
                               (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null,
                               obj,
                               null
                    ), "UTF-8"
            );
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                yacyCore.log.logFine("yacyClient.publishMySeed thread '" + Thread.currentThread().getName() + "' interrupted.");
            } else {
                yacyCore.log.logFine("yacyClient.publishMySeed exception:" + e.getMessage());
            }
            return -1;
        }
        if (result == null || result.size() < 3) {
            yacyCore.log.logFine("yacyClient.publishMySeed result error: " +
            ((result == null) ? "result null" : ("result=" + result.toString())));
            return -1;
        }

        // check consistency with expectation
        yacySeed otherPeer = null;
        float otherPeerVersion = 0;
        if (otherHash != null && otherHash.length() > 0) {
            otherPeer = yacySeed.genRemoteSeed((String) result.get("seed0"), key, true);
            if (otherPeer == null || !otherPeer.hash.equals(otherHash)) {
                yacyCore.log.logFine("yacyClient.publishMySeed: consistency error: other peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' wrong");
                return -1; // no success
            }
            otherPeerVersion = otherPeer.getVersion();
        }

        // set my own seed according to new information
        final yacySeed mySeedBkp = (yacySeed) yacyCore.seedDB.mySeed.clone();

        // we overwrite our own IP number only, if we do not portForwarding
        if (serverCore.portForwardingEnabled || serverCore.useStaticIP) {
            yacyCore.seedDB.mySeed.put(yacySeed.IP, serverCore.publicIP());
        } else {
            yacyCore.seedDB.mySeed.put(yacySeed.IP, (String) result.get("yourip"));
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
            if (mytype == null) { mytype = yacySeed.PEERTYPE_JUNIOR; }        
            yacyAccessible accessible = new yacyAccessible();
            if (mytype.equals(yacySeed.PEERTYPE_SENIOR)||mytype.equals(yacySeed.PEERTYPE_PRINCIPAL)) {
                accessible.IWasAccessed = true;
                if (yacyCore.seedDB.mySeed.isPrincipal()) {
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
            } else {
                yacyCore.log.logFine("yacyClient.publishMySeed: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as " + mytype + ".");
            }
            if (yacyCore.seedDB.mySeed.orVirgin().equals(yacySeed.PEERTYPE_VIRGIN))
                yacyCore.seedDB.mySeed.put(yacySeed.PEERTYPE, mytype);
        }

        final String error = yacyCore.seedDB.mySeed.isProper();
        if (error != null) {
            yacyCore.seedDB.mySeed = mySeedBkp;
            yacyCore.log.logFine("yacyClient.publishMySeed mySeed error - not proper: " + error);
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
            if (yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(seedStr, key, true), (i == 1))) count++;
        }
        return count;
    }

    public static yacySeed querySeed(yacySeed target, String seedHash) {
        final String key = crypt.randomSalt();
        try {
            // should we use the proxy?
            boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) &&  
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
            
            // sending request
            final HashMap result = nxTools.table(
                    httpc.wget(
                            new URL("http://" + target.getAddress() +
                                    "/yacy/query.html" +
                                    "?iam=" + yacyCore.seedDB.mySeed.hash +
                                    "&youare=" + target.hash + 
                                    "&key=" + key +
                                    "&object=seed" +
                                    "&env=" + seedHash
                            ),
                            target.getHexHash() + ".yacyh",
                            10000, 
                            null, 
                            null, 
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null,
                            null
                    )
                    , "UTF-8");
            
            if (result == null || result.size() == 0) { return null; }
            //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time
            return yacySeed.genRemoteSeed((String) result.get("response"), key, true);
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.querySeed error:" + e.getMessage());
            return null;
        }
    }

    public static int queryRWICount(yacySeed target, String wordHash) {
        try {
            // should we use the proxy?
            boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
            
            // sending request
            final HashMap result = nxTools.table(
                    httpc.wget(
                            new URL("http://" + target.getAddress() +
                                    "/yacy/query.html" +
                                    "?iam=" + yacyCore.seedDB.mySeed.hash +
                                    "&youare=" + target.hash + 
                                    "&key=" +
                                    "&object=rwicount" +
                                    "&env=" + wordHash +
                                    "&ttl=0"
                            ),
                            target.getHexHash() + ".yacyh",
                            10000, 
                            null, 
                            null, 
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null
                    )
                    , "UTF-8");
            
            if (result == null || result.size() == 0) { return -1; }
            return Integer.parseInt((String) result.get("response"));
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.queryRWICount error:" + e.getMessage());
            return -1;
        }
    }

    public static int queryUrlCount(yacySeed target) {        
        if (target == null) { return -1; }
        if (yacyCore.seedDB.mySeed == null) return -1;
        
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // building url
        final String querystr =
            "http://" + target.getAddress() +
            "/yacy/query.html" +
            "?iam=" + yacyCore.seedDB.mySeed.hash +
            "&youare=" + target.hash +
            "&key=" +
            "&object=lurlcount" +
            "&env=" +
            "&ttl=0";
        
        // seinding request
        try {
            final HashMap result = nxTools.table(
                    httpc.wget(
                            new URL(querystr),
                            target.getHexHash() + ".yacyh",
                            6000, 
                            null, 
                            null,
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null
                    )
                    , "UTF-8");
            
//          yacyCore.log("DEBUG QUERY: query=" + querystr + "; result = " + result.toString());
            if ((result == null) || (result.size() == 0)) return -1;
            final String resp = (String) result.get("response");
            if (resp == null) {
                return -1;
            } else try {
                return Integer.parseInt(resp);
            } catch (NumberFormatException e) {
                return -1;
            }
        } catch (IOException e) {
            yacyCore.log.logSevere("yacyClient.queryUrlCount error asking peer '" + target.getName() + "':" + e.toString());
            return -1;
        }
    }

    public static String[] search(
            String wordhashes,
            String urlhashes,
            String prefer,
            String filter,
            int maxDistance,
            boolean global, 
            int partitions,
            yacySeed targetPeer,
            plasmaCrawlLURL urlManager, 
            plasmaWordIndex wordIndex,
            indexContainer containerCache,
            Map abstractCache,
            plasmaURLPattern blacklist, 
            plasmaSnippetCache snippets, 
            plasmaSearchTimingProfile timingProfile,
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

        // request result
        final String key = crypt.randomSalt();
        try {            
            // should we use the proxy?
            boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
            
            // building url
            final String url = "http://" + targetPeer.getAddress() + "/yacy/search.html";
            
            // adding all needed parameters
            /*
            String url = "http://" + targetPeer.getAddress() +
                "/yacy/search.html?myseed=" + yacyCore.seedCache.mySeed.genSeedStr(key) +
                "&youare=" + targetPeer.hash + "&key=" + key +
                "&myseed=" + yacyCore.seedCache.mySeed.genSeedStr(key) +
                "&count=" + count + "&resource=" + ((global) ? "global" : "local") +
                "&query=" + wordhashes;
             */
            final serverObjects obj = new serverObjects(9);
            long duetime = timingProfile.duetime();
            obj.put("myseed", yacyCore.seedDB.mySeed.genSeedStr(key));
            obj.put("youare", targetPeer.hash);
            obj.put("key", key);
            obj.put("count", timingProfile.getTargetCount(plasmaSearchTimingProfile.PROCESS_POSTSORT));
            obj.put("resource", ((global) ? "global" : "local"));
            obj.put("partitions", partitions);
            obj.put("query", wordhashes);
            obj.put("urls", urlhashes);
            obj.put("prefer", prefer);
            obj.put("filter", filter);
            obj.put("ttl", "0");
            obj.put("duetime", Long.toString(duetime));
            obj.put("timing", crypt.simpleEncode(timingProfile.targetToString())); // new duetimes splitted by specific search tasks
            obj.put("maxdist", maxDistance);
            obj.put("profile", crypt.simpleEncode(rankingProfile.toExternalString()));
            obj.put("constraint", constraint.exportB64());
            obj.put("mytime", yacyCore.universalDateShortString(new Date()));
            if (abstractCache != null) obj.put("abstracts", "auto");
            
            //yacyCore.log.logDebug("yacyClient.search url=" + url);
            final long timestamp = System.currentTimeMillis();
            
            // sending request
            HashMap result = null;
            try {
                result = nxTools.table(
                    httpc.wput(
                            new URL(url),
                            targetPeer.getHexHash() + ".yacyh",
                            300000, 
                            null, 
                            null,
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null,
                            obj,
                            null
                    ), "UTF-8"
                );
            } catch (IOException e) {
                yacyCore.log.logFine("SEARCH failed FROM " + targetPeer.hash + ":" + targetPeer.getName() + " (" + e.getMessage() + "), score=" + targetPeer.selectscore + ", DHTdist=" + yacyDHTAction.dhtDistance(targetPeer.hash, wordhashes.substring(0, 12)));
                yacyCore.peerActions.peerDeparture(targetPeer);
                return null;
            }

            if (result.size() == 0) {
                yacyCore.log.logFine("SEARCH failed FROM " + targetPeer.hash + ":" + targetPeer.getName() + " (zero response), score=" + targetPeer.selectscore + ", DHTdist=" + yacyDHTAction.dhtDistance(targetPeer.hash, wordhashes.substring(0, 12)));
                return null;
            }
            
            // compute all computation times
            final long totalrequesttime = System.currentTimeMillis() - timestamp;
            String returnProfile = (String) result.get("profile");
            if (returnProfile != null) timingProfile.putYield(returnProfile);
            
            /*
            HashMap result = nxTools.table(httpc.wget(new URL(url),
                            300000, null, null, yacyCore.seedCache.remoteProxyHost, yacyCore.seedCache.remoteProxyPort));
             */
            // OUTPUT:
            // version     : application version of responder
            // uptime      : uptime in seconds of responder
            // total       : number of total available LURL's for this search
            // count       : number of returned LURL's for this search
            // resource<n> : LURL of search
            // fwhop       : hops (depth) of forwards that had been performed to construct this result
            // fwsrc       : peers that helped to construct this result
            // fwrec       : peers that would have helped to construct this result (recommendations)
            // searchtime  : time that the peer actually spent to create the result
            // references  : references (search hints) that was calculated during search

            // now create a plasmaIndex out of this result
            //System.out.println("yacyClient: " + ((urlhashes.length() == 0) ? "primary" : "secondary")+ " search result = " + result.toString()); // debug
            final int results = Integer.parseInt((String) result.get("count"));
            //System.out.println("***result count " + results);
            
            // create containers
            final int words = wordhashes.length() / yacySeedDB.commonHashLength;
            indexContainer[] container = new indexContainer[words];
            for (int i = 0; i < words; i++) {
                container[i] = wordIndex.emptyContainer(wordhashes.substring(i * yacySeedDB.commonHashLength, (i + 1) * yacySeedDB.commonHashLength));
            }

            // insert results to containers
            indexURLEntry urlEntry;
            String[] urls = new String[results];
            for (int n = 0; n < results; n++) {
                // get one single search result
                urlEntry = urlManager.newEntry((String) result.get("resource" + n));
                if (urlEntry == null) continue;
                assert (urlEntry.hash().length() == 12) : "urlEntry.hash() = " + urlEntry.hash();
                if (urlEntry.hash().length() != 12) continue; // bad url hash
                indexURLEntry.Components comp = urlEntry.comp();
                if (blacklist.isListed(plasmaURLPattern.BLACKLIST_SEARCH, comp.url())) continue; // block with backlist
                urlManager.store(urlEntry);
                urlManager.stack(urlEntry, yacyCore.seedDB.mySeed.hash, targetPeer.hash, 2);

                // save the url entry
                indexRWIEntry entry;
                if (urlEntry.word() == null) {
                    yacyCore.log.logWarning("DEBUG-SEARCH: no word attached from peer " + targetPeer.getName() + ", version " + targetPeer.getVersion());
                    continue; // no word attached
                }
                // the search-result-url transports all the attributes of word indexes
                entry = urlEntry.word();

                if (urlEntry.snippet() != null) {
                    // we don't store the snippets along the url entry, because they are search-specific.
                    // instead, they are placed in a snipped-search cache.
                    //System.out.println("--- RECEIVED SNIPPET '" + link.snippet() + "'");
                    snippets.storeToCache(wordhashes, urlEntry.hash(), urlEntry.snippet());
                }
                // add the url entry to the word indexes
                for (int m = 0; m < words; m++) {
                    container[m].add(entry, System.currentTimeMillis());
                }
                // store url hash for statistics
                urls[n] = urlEntry.hash();
            }

            // insert the containers to the index
            for (int m = 0; m < words; m++) { containerCache.addAllUnique(container[m]); }

            // read index abstract
            if (abstractCache != null) {
                Iterator i = result.entrySet().iterator();
                Map.Entry entry;
                TreeMap singleAbstract;
                String wordhash;
                serverByteBuffer ci;
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    if (((String) entry.getKey()).startsWith("indexabstract.")) {
                        wordhash = ((String) entry.getKey()).substring(14);
                        synchronized (abstractCache) {
                            singleAbstract = (TreeMap) abstractCache.get(wordhash); // a mapping from url-hashes to a string of peer-hashes
                            if (singleAbstract == null) singleAbstract = new TreeMap();
                            ci = new serverByteBuffer(((String) entry.getValue()).getBytes());
                            System.out.println("DEBUG-ABSTRACTFETCH: for word hash " + wordhash + " received " + ci.toString());
                            plasmaURL.decompressIndex(singleAbstract, ci, targetPeer.hash);
                            abstractCache.put(wordhash, singleAbstract);
                        }
                    }
                }
            }
            // generate statistics
            long searchtime;
            try {
                searchtime = Integer.parseInt((String) result.get("searchtime"));
            } catch (NumberFormatException e) {
                searchtime = totalrequesttime;
            }
            yacyCore.log.logFine("SEARCH " + results + " URLS FROM " + targetPeer.hash + ":" + targetPeer.getName() + ", score=" + targetPeer.selectscore + ", DHTdist=" + ((wordhashes.length() < 12) ? "void" : Double.toString(yacyDHTAction.dhtDistance(targetPeer.hash, wordhashes.substring(0, 12)))) + ", duetime=" + duetime + ", searchtime=" + searchtime + ", netdelay=" + (totalrequesttime - searchtime) + ", references=" + result.get("references"));
            return urls;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.search error: '" + targetPeer.get(yacySeed.NAME, "anonymous") + "' failed - " + e);
            e.printStackTrace();
            return null;
        }
    }

    public static HashMap permissionMessage(String targetHash) {
        // ask for allowed message size and attachement size
        // if this replies null, the peer does not answer
        if (yacyCore.seedDB == null || yacyCore.seedDB.mySeed == null) { return null; }
        
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
                
        // adding all needed parameters
        final String key = crypt.randomSalt();
        final serverObjects post = new serverObjects(5);
            post.put("key", key);
            post.put("process", "permission");
            post.put("iam", yacyCore.seedDB.mySeed.hash);
            post.put("youare", targetHash);
            post.put("mytime", yacyCore.universalDateShortString(new Date()));
            
        // get target address    
        String address = targetAddress(targetHash);
        
        // sending request
        try {
            return nxTools.table(
                    httpc.wput(
                            new URL("http://" + address + "/yacy/message.html"),
                            yacySeed.b64Hash2hexHash(targetHash) + ".yacyh",
                            8000, 
                            null, 
                            null, 
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null,
                            post,
                            null
                    ), "UTF-8"
            );
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.permissionMessage error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap postMessage(String targetHash, String subject, byte[] message) {
        // this post a message to the remote message board
        
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // adding all needed parameters
        final String key = crypt.randomSalt();
        final serverObjects post = new serverObjects(7);
            post.put("key", key);
            post.put("process", "post");
            post.put("myseed", yacyCore.seedDB.mySeed.genSeedStr(key));
            post.put("youare", targetHash);
            post.put("subject", subject);
            post.put("mytime", yacyCore.universalDateShortString(new Date()));
            try {
                post.put("message", new String(message, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                post.put("message", new String(message));
            }
        // get target address    
        String address = targetAddress(targetHash);
        
        // sending request
        try {
            return nxTools.table(
                httpc.wput(
                    new URL("http://" + address + "/yacy/message.html"),
                    yacySeed.b64Hash2hexHash(targetHash) + ".yacyh",
                    20000, 
                    null, 
                    null,
                    (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null, 
                    post,
                    null
               ), "UTF-8");
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }

    
    public static String targetAddress(String targetHash) {
        // find target address    
        String address;
        if (targetHash.equals(yacyCore.seedDB.mySeed.hash)) {
            address = yacyCore.seedDB.mySeed.getAddress();
        } else {
            final yacySeed targetSeed = yacyCore.seedDB.getConnected(targetHash);
            if (targetSeed == null) { return null; }
            address = targetSeed.getAddress();
        }
        if (address == null) address = "localhost:8080";
        return address;
    }
    
    public static HashMap transferPermission(String targetAddress, long filesize, String filename) {
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB != null) &&
                           (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // add all needed parameters
        final String key = crypt.randomSalt();
        final serverObjects post = new serverObjects();
        post.put("key", key);
        post.put("process", "permission");
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("purpose", "crcon");
        post.put("filename", filename);
        post.put("filesize", Long.toString(filesize));
        post.put("can-send-protocol", "http");
        
        // send request
        try {
            final URL url = new URL("http://" + targetAddress + "/yacy/transfer.html");
            return nxTools.table(
                    httpc.wput(
                            url,
                            url.getHost(), // yacyCore.seedDB.mySeed.getHexHash() + ".yacyh",
                            8000, 
                            null, 
                            null, 
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null,
                            post,
                            null
                    ), "UTF-8"
            );
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.permissionTransfer error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap transferStore(String targetAddress, String access, String filename, byte[] file) {
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB != null) &&
                           (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // adding all needed parameters
        final String key = crypt.randomSalt();
        final serverObjects post = new serverObjects(7);
        post.put("key", key);
        post.put("process", "store");
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("purpose", "crcon");
        post.put("filename", filename);
        post.put("filesize", Long.toString(file.length));
        post.put("md5", serverCodings.encodeMD5Hex(file));
        post.put("access", access);
        HashMap files = new HashMap();
        files.put("filename", file);
        
        // sending request
        try {
            final URL url = new URL("http://" + targetAddress + "/yacy/transfer.html");
            return nxTools.table(
                httpc.wput(
                    url,
                    url.getHost(), // yacyCore.seedDB.mySeed.getHexHash() + ".yacyh",
                    20000, 
                    null, 
                    null,
                    (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null, 
                    post,
                    files
                ), "UTF-8");
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }
    
    public static String transfer(String targetAddress, String filename, byte[] file) {
        HashMap phase1 = transferPermission(targetAddress, file.length, filename);
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
            HashMap phase2 = transferStore(nextaddress, accesscode, filename, file);
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
    
    public static HashMap crawlOrder(yacySeed targetSeed, URL url, URL referrer, int timeout) {
        return crawlOrder(targetSeed, new URL[]{url}, new URL[]{referrer}, timeout); 
    }
    
    public static HashMap crawlOrder(yacySeed targetSeed, URL[] url, URL[] referrer, int timeout) {
        // this post a message to the remote message board
        if (targetSeed == null) { return null; }
        if (yacyCore.seedDB.mySeed == null) { return null; }
        if (yacyCore.seedDB.mySeed == targetSeed) { return null; }

        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // construct request
        final String key = crypt.randomSalt();
        final serverObjects post = new serverObjects(9);
        post.put("key", key);
        post.put("process", "crawl");
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("youare", targetSeed.hash);
        post.put("mytime", yacyCore.universalDateShortString(new Date()));
        if (url.length == 1) {
            post.put("url", crypt.simpleEncode(url[0].toString()));
            post.put("referrer", crypt.simpleEncode((referrer[0] == null) ? "" : referrer[0].toString()));
        } else {
            for (int i=0; i< url.length; i++) {
                post.put("url" + i, crypt.simpleEncode(url[i].toString()));
                post.put("ref" + i, crypt.simpleEncode((referrer[i] == null) ? "" : referrer[i].toString()));
            }
        }
        post.put("depth", "0");
        post.put("ttl", "0");

        // determining target address
        final String address = targetSeed.getAddress();
        if (address == null) { return null; }
        
        // sending request
        try {
            return nxTools.table(
                    httpc.wput(
                            new URL("http://" + address + "/yacy/crawlOrder.html"),
                            targetSeed.getHexHash() + ".yacyh",
                            timeout, 
                            null, 
                            null, 
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null, 
                            post,
                            null
                    ), "UTF-8"
            );
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.crawlOrder error: peer=" + targetSeed.getName() + ", error=" + e.getMessage());
            return null;
        }
    }

    /*
        Test:
        http://217.234.95.114:5777/yacy/crawlOrder.html?key=abc&iam=S-cjM67KhtcJ&youare=EK31N7RgRqTn&process=crawl&referrer=&depth=0&url=p|http://www.heise.de/newsticker/meldung/53245
        version=0.297 uptime=225 accepted=true reason=ok delay=30 depth=0
        -er crawlt, Ergebnis erscheint aber unter falschem initiator
     */

    public static HashMap crawlReceipt(yacySeed targetSeed, String process, String result, String reason, indexURLEntry entry, String wordhashes) {
        if (targetSeed == null) { return null; }
        if (yacyCore.seedDB.mySeed == null) { return null; }
        if (yacyCore.seedDB.mySeed == targetSeed) { return null; }

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
        
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) &&
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());

        // construct request
        final String key = crypt.randomSalt();

        // determining target address
        String address = targetSeed.getAddress();
        if (address == null) { return null; }
        
        // sending request
        try {
            return nxTools.table(
                    httpc.wget(
                            new URL("http://" + address + "/yacy/crawlReceipt.html" +
                                    "?iam=" + yacyCore.seedDB.mySeed.hash +
                                    "&youare=" + targetSeed.hash +
                                    "&process=" + process +
                                    "&key=" + key +
                                    "&urlhash=" + ((entry == null) ? "" : entry.hash()) +
                                    "&result=" + result +
                                    "&reason=" + reason +
                                    "&wordh=" + wordhashes +
                                    "&lurlEntry=" + ((entry == null) ? "" : crypt.simpleEncode(entry.toString(), key))
                            ),
                            targetSeed.getHexHash() + ".yacyh",
                            60000, 
                            null, 
                            null,             
                            (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null
                    )
                    , "UTF-8");
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.crawlReceipt error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap transferIndex(yacySeed targetSeed, indexContainer[] indexes, HashMap urlCache, boolean gzipBody, int timeout) {
        
        HashMap resultObj = new HashMap();
        int payloadSize = 0;
        try {
            
            // check if we got all necessary urls in the urlCache (only for debugging)
            Iterator eenum;
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
            HashMap in = transferRWI(targetSeed, indexes, gzipBody, timeout);
            resultObj.put("resultTransferRWI", in);
            
            if (in == null) {
                resultObj.put("result", "no_connection_1");
                return resultObj;
            }        
            if (in.containsKey("indexPayloadSize")) payloadSize += ((Integer)in.get("indexPayloadSize")).intValue();
            
            String result = (String) in.get("result");
            if (result == null) { 
                resultObj.put("result","no_result_1"); 
                return resultObj;
            }
            if (!(result.equals("ok"))) {
                targetSeed.setFlagAcceptRemoteIndex(false);
                yacyCore.seedDB.update(targetSeed.hash, targetSeed);
                resultObj.put("result",result);
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
            
            in = transferURL(targetSeed, urls, gzipBody, timeout);
            resultObj.put("resultTransferURL", in);
            
            if (in == null) {
                resultObj.put("result","no_connection_2");
                return resultObj;
            }
            if (in.containsKey("urlPayloadSize")) payloadSize += ((Integer)in.get("urlPayloadSize")).intValue();
            
            result = (String) in.get("result");
            if (result == null) {
                resultObj.put("result","no_result_2");
                return resultObj;
            }
            if (!(result.equals("ok"))) {
                targetSeed.setFlagAcceptRemoteIndex(false);
                yacyCore.seedDB.update(targetSeed.hash, targetSeed);
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

    private static HashMap transferRWI(yacySeed targetSeed, indexContainer[] indexes, boolean gzipBody, int timeout) {
        final String address = targetSeed.getAddress();
        if (address == null) { return null; }
        
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // prepare post values
        final serverObjects post = new serverObjects(7);
        final String key = crypt.randomSalt();
        
        // enabling gzip compression for post request body
        if ((gzipBody) && (targetSeed.getVersion() >= yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS)) {
            post.put(httpc.GZIP_POST_BODY,"true");
        }
        post.put("key", key);
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("youare", targetSeed.hash);
        post.put("wordc", Integer.toString(indexes.length));
        
        
        int indexcount = 0;
        final StringBuffer entrypost = new StringBuffer(indexes.length*73);
        Iterator eenum;
        indexRWIEntry entry;
        for (int i = 0; i < indexes.length; i++) {
            eenum = indexes[i].entries();
            while (eenum.hasNext()) {
                entry = (indexRWIEntry) eenum.next();
                entrypost.append(indexes[i].getWordHash()) 
                         .append(entry.toPropertyForm()) 
                         .append(serverCore.crlfString);
                indexcount++;
            }
        }

        if (indexcount == 0) {
            // nothing to do but everything ok
            final HashMap result = new HashMap(2);
            result.put("result", "ok");
            result.put("unknownURL", "");
            return result;
        }

        post.put("entryc", Integer.toString(indexcount));
        post.put("indexes", entrypost.toString());
        try {
            final ArrayList v = nxTools.strings(
                httpc.wput(
                    new URL("http://" + address + "/yacy/transferRWI.html"), 
                    targetSeed.getHexHash() + ".yacyh",
                    timeout, 
                    null, 
                    null,
                    (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null, 
                    post,
                    null
                ), "UTF-8");
            // this should return a list of urlhashes that are unknwon
            if ((v != null) && (v.size() > 0)) {
                yacyCore.seedDB.mySeed.incSI(indexcount);
            }
            
            final HashMap result = nxTools.table(v);
            // return the transfered index data in bytes (for debugging only)
            result.put("indexPayloadSize", new Integer(entrypost.length()));
            return result;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.transferRWI error:" + e.getMessage());
            return null;
        }
    }

    private static HashMap transferURL(yacySeed targetSeed, indexURLEntry[] urls, boolean gzipBody, int timeout) {
        // this post a message to the remote message board
        final String address = targetSeed.getAddress();
        if (address == null) { return null; }
        
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // prepare post values
        final serverObjects post = new serverObjects(5+urls.length);
        final String key = crypt.randomSalt();
        
        // enabling gzip compression for post request body
        if ((gzipBody) && (targetSeed.getVersion() >= yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS)) {
            post.put(httpc.GZIP_POST_BODY,"true");
        }        
        
        post.put("key", key);
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("youare", targetSeed.hash);
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
        post.put("urlc", Integer.toString(urlc));
        try {
            final ArrayList v = nxTools.strings(
                httpc.wput(
                    new URL("http://" + address + "/yacy/transferURL.html"),
                    targetSeed.getHexHash() + ".yacyh",
                    timeout, 
                    null, 
                    null,
                    (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null, 
                    post,
                    null
                ), "UTF-8");
            
            if ((v != null) && (v.size() > 0)) {
                yacyCore.seedDB.mySeed.incSU(urlc);
            }
            
            HashMap result = nxTools.table(v);
            // return the transfered url data in bytes (for debugging only)
            result.put("urlPayloadSize", new Integer(urlPayloadSize));            
            return result;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.transferURL error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap getProfile(yacySeed targetSeed) {
        
        // should we use the proxy?
        boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                           (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());
        
        // this post a message to the remote message board
        final serverObjects post = new serverObjects(2);
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("youare", targetSeed.hash);
        String address = targetSeed.getAddress();
        if (address == null) { address = "localhost:8080"; }
        try {
            return nxTools.table(
                httpc.wput(
                    new URL("http://" + address + "/yacy/profile.html"), 
                    targetSeed.getHexHash() + ".yacyh",
                    10000, 
                    null, 
                    null,
                    (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null, 
                    post,
                    null
                ), "UTF-8");
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.getProfile error:" + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        System.out.println("yacyClient Test");
        try {
            final plasmaSwitchboard sb = new plasmaSwitchboard(args[0], "httpProxy.init", "DATA/SETTINGS/httpProxy.conf");
            /*final yacyCore core =*/ new yacyCore(sb);
            yacyCore.peerActions.loadSeedLists();
            final yacySeed target = yacyCore.seedDB.getConnected(args[1]);
            final String wordhashe = plasmaCondenser.word2hash("test");
            //System.out.println("permission=" + permissionMessage(args[1]));
            
            // should we use the proxy?
            boolean useProxy = (yacyCore.seedDB.sb.remoteProxyConfig != null) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy()) && 
                               (yacyCore.seedDB.sb.remoteProxyConfig.useProxy4Yacy());            
            
            final HashMap result = nxTools.table(
                    httpc.wget(
                            new URL("http://" + target.getAddress() + "/yacy/search.html" +
                                    "?myseed=" + yacyCore.seedDB.mySeed.genSeedStr(null) +
                                    "&youare=" + target.hash + "&key=" +
                                    "&myseed=" + yacyCore.seedDB.mySeed.genSeedStr(null) +
                                    "&count=10" +
                                    "&resource=global" +
                                    "&query=" + wordhashe),
                                    target.getHexHash() + ".yacyh",
                                    5000, 
                                    null, 
                                    null, 
                                    (useProxy)?yacyCore.seedDB.sb.remoteProxyConfig:null
                    )
                    , "UTF-8");
            System.out.println("Result=" + result.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

}
