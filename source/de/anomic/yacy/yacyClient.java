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
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndexEntity;
import de.anomic.plasma.plasmaWordIndexEntry;
import de.anomic.plasma.plasmaWordIndexEntryContainer;
import de.anomic.plasma.plasmaURLPattern;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyVersion;

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

            final URL url = new URL("http://" + address + "/yacy/hello.html");
            final serverObjects obj = new serverObjects(6);
            obj.put("iam", yacyCore.seedDB.mySeed.hash);
            obj.put("pattern", "");
            obj.put("count", "20");
            obj.put("key", key);
            obj.put("mytime", yacyCore.universalDateShortString(new Date()));
            obj.put("myUTC", System.currentTimeMillis());
            obj.put("seed", yacyCore.seedDB.mySeed.genSeedStr(key));
            result = nxTools.table(httpc.wput(url,
            105000, null, null,
            yacyCore.seedDB.sb.remoteProxyHost,
            yacyCore.seedDB.sb.remoteProxyPort,
            obj));
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
            otherPeer = yacySeed.genRemoteSeed((String) result.get("seed0"), key);
            if (otherPeer == null || !otherPeer.hash.equals(otherHash)) {
                yacyCore.log.logFine("yacyClient.publishMySeed: consistency error: other peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' wrong");
                return -1; // no success
            }
            otherPeerVersion = otherPeer.getVersion();
        }

        // set my own seed according to new information
        final yacySeed mySeedBkp = (yacySeed) yacyCore.seedDB.mySeed.clone();

        // we overwrite our own IP number only, if we do not portForwarding
        if (serverCore.portForwardingEnabled) {
            yacyCore.seedDB.mySeed.put("IP", serverCore.publicIP());
        } else {
            yacyCore.seedDB.mySeed.put("IP", (String) result.get("yourip"));
        }

        /* If we have port forwarding enabled but the other peer uses a too old yacy version
         * we can ignore the seed-type that was reported by the peer.
         * 
         * Otherwise we have to change our seed-type  
         * 
         * @see serverCore#portForwardingEnabled 
         */
        if (!serverCore.portForwardingEnabled || otherPeerVersion >= yacyVersion.YACY_SUPPORTS_PORT_FORWARDING) {
            String mytype = (String) result.get("yourtype");
            if (mytype == null) { mytype = yacySeed.PEERTYPE_JUNIOR; }        
            if (
                    (yacyCore.seedDB.mySeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR).equals(yacySeed.PEERTYPE_PRINCIPAL)) && 
                    (mytype.equals(yacySeed.PEERTYPE_SENIOR))
            ) { 
                mytype = yacySeed.PEERTYPE_PRINCIPAL;
            }

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
            yacyCore.seedDB.mySeed.put(yacySeed.PEERTYPE, mytype);
        }

        final String error = yacyCore.seedDB.mySeed.isProper();
        if (error != null) {
            yacyCore.seedDB.mySeed = mySeedBkp;
            yacyCore.log.logFine("yacyClient.publishMySeed mySeed error - not proper: " + error);
            return -1;
        }

        //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get("mytime")); // read remote time
        
        // read the seeds that the peer returned and integrate them into own database
        int i = 0;
        int count = 0;
        String seedStr;
        while ((seedStr = (String) result.get("seed" + i++)) != null) {
            // integrate new seed into own database
            // the first seed, "seed0" is the seed of the responding peer
            if (yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(seedStr, key), (i == 1))) count++;
        }
        return count;
    }

    public static yacySeed querySeed(yacySeed target, String seedHash) {
        final String key = crypt.randomSalt();
        try {
            final HashMap result = nxTools.table(httpc.wget(
            new URL("http://" + target.getAddress() +
            "/yacy/query.html?iam=" + yacyCore.seedDB.mySeed.hash +
            "&youare=" + target.hash + "&key=" + key +
            "&object=seed&env=" + seedHash),
            10000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
            if (result == null || result.size() == 0) { return null; }
            //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get("mytime")); // read remote time
            return yacySeed.genRemoteSeed((String) result.get("response"), key);
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.querySeed error:" + e.getMessage());
            return null;
        }
    }

    public static int queryRWICount(yacySeed target, String wordHash) {
        try {
            final HashMap result = nxTools.table(httpc.wget(
            new URL("http://" + target.getAddress() +
            "/yacy/query.html?iam=" + yacyCore.seedDB.mySeed.hash +
            "&youare=" + target.hash + "&key=" +
            "&object=rwicount&env=" + wordHash +
            "&ttl=0"),
            10000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
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
        final String querystr =
        "http://" + target.getAddress() +
        "/yacy/query.html?iam=" + yacyCore.seedDB.mySeed.hash +
        "&youare=" + target.hash +
        "&key=" +
        "&object=lurlcount&env=&ttl=0";
        try {
            final HashMap result = nxTools.table(httpc.wget(
            new URL(querystr), 6000, null, null,
            yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
//          yacyCore.log("DEBUG QUERY: query=" + querystr + "; result = " + result.toString());
            if ((result == null) || (result.size() == 0)) return -1;
            final String resp = (String) result.get("response");
            if (resp == null) { return -1; } else { return Integer.parseInt(resp); }
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.queryUrlCount error asking peer '" + target.getName() + "':" + e.toString());
            return -1;
        }
    }

    public static int search(String wordhashes, int count, boolean global,
                             yacySeed targetPeer, plasmaCrawlLURL urlManager,
                             plasmaWordIndex wordIndex, plasmaURLPattern blacklist,
                             plasmaSnippetCache snippets,
                             long duetime) {
        // send a search request to peer with remote Hash
        // this mainly converts the words into word hashes

        // INPUT:
        // iam     : complete seed of the requesting peer
        // youare  : seed hash of the target peer, used for testing network stability
        // key     : transmission key for response
        // search  : a list of search words
        // hsearch : a string of word hashes
        // fwdep   : forward depth. if "0" then peer may NOT ask another peer for more results
        // fwden   : forward deny, a list of seed hashes. They may NOT be target of forward hopping
        // count   : maximum number of wanted results
        // global  : if "true", then result may consist of answers from other peers
        // duetime : maximum time that a peer should spent to create a result

        // request result
        final String key = crypt.randomSalt();
        try {
            final String url = "http://" + targetPeer.getAddress() + "/yacy/search.html";
            /*
            String url = "http://" + targetPeer.getAddress() +
                "/yacy/search.html?myseed=" + yacyCore.seedCache.mySeed.genSeedStr(key) +
                "&youare=" + targetPeer.hash + "&key=" + key +
                "&myseed=" + yacyCore.seedCache.mySeed.genSeedStr(key) +
                "&count=" + count + "&resource=" + ((global) ? "global" : "local") +
                "&query=" + wordhashes;
             */
            final serverObjects obj = new serverObjects(9);
            obj.put("myseed", yacyCore.seedDB.mySeed.genSeedStr(key));
            obj.put("youare", targetPeer.hash);
            obj.put("key", key);
            obj.put("count", count);
            obj.put("resource", ((global) ? "global" : "local"));
            obj.put("query", wordhashes);
            obj.put("ttl", "0");
            obj.put("duetime", Long.toString(duetime));
            obj.put("mytime", yacyCore.universalDateShortString(new Date()));
            //yacyCore.log.logDebug("yacyClient.search url=" + url);
            final long timestamp = System.currentTimeMillis();
            final HashMap result = nxTools.table(httpc.wput(new URL(url),
                                           300000, null, null,
                                           yacyCore.seedDB.sb.remoteProxyHost,
                                           yacyCore.seedDB.sb.remoteProxyPort,
                                           obj));
            final long totalrequesttime = System.currentTimeMillis() - timestamp;
            
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
            //System.out.println("yacyClient: search result = " + result.toString()); // debug
            final int results = Integer.parseInt((String) result.get("count"));
            //System.out.println("***result count " + results);
            plasmaCrawlLURL.Entry link;

            // create containers
            final int words = wordhashes.length() / plasmaWordIndexEntry.wordHashLength;
            plasmaWordIndexEntryContainer[] container = new plasmaWordIndexEntryContainer[words];
            for (int i = 0; i < words; i++) {
                container[i] = new plasmaWordIndexEntryContainer(wordhashes.substring(i * plasmaWordIndexEntry.wordHashLength, (i + 1) * plasmaWordIndexEntry.wordHashLength));
            }

            // insert results to containers
            plasmaCrawlLURL.Entry lEntry;
            for (int n = 0; n < results; n++) {
                // get one single search result
                lEntry = urlManager.newEntry((String) result.get("resource" + n), true);
                if (lEntry != null && blacklist.isListed(lEntry.url().getHost().toLowerCase(), lEntry.url().getPath())) { continue; } // block with backlist
                link = urlManager.addEntry(lEntry, yacyCore.seedDB.mySeed.hash, targetPeer.hash, 2);
                // save the url entry
                final plasmaWordIndexEntry entry = new plasmaWordIndexEntry(link.hash(), link.wordCount(), 0, 0, 0,
                                                                      plasmaWordIndex.calcVirtualAge(link.moddate()), link.quality(),
                                                                      link.language(), link.doctype(), false);
                if (link.snippet() != null) {
                    // we don't store the snippets along the url entry, because they are search-specific.
                    // instead, they are placed in a snipped-search cache.
                    //System.out.println("--- RECEIVED SNIPPET '" + link.snippet() + "'");
                    snippets.storeToCache(wordhashes, link.hash(), link.snippet());
                }
                // add the url entry to the word indexes
                for (int m = 0; m < words; m++) {
                    container[m].add(new plasmaWordIndexEntry[]{entry}, System.currentTimeMillis());
                }
            }

            // finally insert the containers to the index
            for (int m = 0; m < words; m++) { wordIndex.addEntries(container[m], true); }

            // generate statistics
            long searchtime;
            try {
                searchtime = Integer.parseInt((String) result.get("searchtime"));
            } catch (NumberFormatException e) {
                searchtime = totalrequesttime;
            }
            yacyCore.log.logFine("yacyClient.search: processed " + results + " links from peer " + targetPeer.hash + ", score=" + targetPeer.selectscore + ", DHTdist=" + yacyDHTAction.dhtDistance(targetPeer.hash, wordhashes) + ", duetime=" + duetime + ", searchtime=" + searchtime + ", netdelay=" + (totalrequesttime - searchtime) + ", references=" + result.get("references"));
            return results;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.search error: '" + targetPeer.get("Name", "anonymous") + "' failed - " + e);
            //e.printStackTrace();
            return 0;
        }
    }

    public static HashMap permissionMessage(String targetHash) {
        // ask for allowed message size and attachement size
        // if this replies null, the peer does not answer
        if (yacyCore.seedDB == null || yacyCore.seedDB.mySeed == null) { return null; }
        final serverObjects post = new serverObjects(5);
        final String key = crypt.randomSalt();
        post.put("key", key);
        post.put("process", "permission");
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("youare", targetHash);
        post.put("mytime", yacyCore.universalDateShortString(new Date()));
        String address;
        if (targetHash.equals(yacyCore.seedDB.mySeed.hash)) {
            address = yacyCore.seedDB.mySeed.getAddress();
            //System.out.println("local address: " + address);
        } else {
            final yacySeed targetSeed = yacyCore.seedDB.getConnected(targetHash);
            if (targetSeed == null) { return null; }
            address = targetSeed.getAddress();
            //System.out.println("remote address: " + address);
        }
        if (address == null) { address = "localhost:8080"; }
        try {
            return nxTools.table(httpc.wput(
                new URL("http://" + address + "/yacy/message.html"),
                8000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post));
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.permissionMessage error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap postMessage(String targetHash, String subject, byte[] message) {
        // this post a message to the remote message board
        final serverObjects post = new serverObjects(7);
        final String key = crypt.randomSalt();
        post.put("key", key);
        post.put("process", "post");
        post.put("myseed", yacyCore.seedDB.mySeed.genSeedStr(key));
        post.put("youare", targetHash);
        post.put("subject", subject);
        post.put("mytime", yacyCore.universalDateShortString(new Date()));
        post.put("message", new String(message));
        String address;
        if (targetHash.equals(yacyCore.seedDB.mySeed.hash)) {
            address = yacyCore.seedDB.mySeed.getAddress();
        } else {
            address = yacyCore.seedDB.getConnected(targetHash).getAddress();
        }
        if (address == null) { address = "localhost:8080"; }
        //System.out.println("DEBUG POST "  + address + "/yacy/message.html" + post.toString());
        try {
            final ArrayList v = httpc.wput(new URL("http://" + address + "/yacy/message.html"), 20000, null, null,
            yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            //System.out.println("V=" + v.toString());
            return nxTools.table(v);
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap crawlOrder(yacySeed targetSeed, URL url, URL referrer) {
        // this post a message to the remote message board
        if (targetSeed == null) { return null; }
        if (yacyCore.seedDB.mySeed == null) { return null; }
        if (yacyCore.seedDB.mySeed == targetSeed) { return null; }

        // construct request
        final serverObjects post = new serverObjects(9);
        final String key = crypt.randomSalt();
        post.put("key", key);
        post.put("process", "crawl");
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("youare", targetSeed.hash);
        post.put("mytime", yacyCore.universalDateShortString(new Date()));
        post.put("url", crypt.simpleEncode(url.toString()));
        post.put("referrer", crypt.simpleEncode((referrer == null) ? "" : referrer.toString()));
        post.put("depth", "0");
        post.put("ttl", "0");

        final String address = targetSeed.getAddress();
        if (address == null) { return null; }
        try {
            return nxTools.table(httpc.wput(
            new URL("http://" + address + "/yacy/crawlOrder.html"),
            10000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post));
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

    public static HashMap crawlReceipt(yacySeed targetSeed, String process, String result, String reason, plasmaCrawlLURL.Entry entry, String wordhashes) {
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

        // construct request
        final String key = crypt.randomSalt();

        String address = targetSeed.getAddress();
        if (address == null) { return null; }
        try {
            return nxTools.table(httpc.wget(
            new URL("http://" + address + "/yacy/crawlReceipt.html?" +
            "iam=" + yacyCore.seedDB.mySeed.hash +
            "&youare=" + targetSeed.hash +
            "&process=" + process +
            "&key=" + key +
            "&urlhash=" + ((entry == null) ? "" : entry.hash()) +
            "&result=" + result +
            "&reason=" + reason +
            "&wordh=" + wordhashes +
            "&lurlEntry=" + ((entry == null) ? "" : crypt.simpleEncode(entry.toString(), key))
            ),
            60000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
        } catch (Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.crawlReceipt error:" + e.getMessage());
            return null;
        }
    }
    /*
         public static byte[] singleGET(String host, int port, String path, int timeout,
                                   String user, String password,
                                   httpHeader requestHeader) throws IOException {
     */

    public static String transferIndex(yacySeed targetSeed, plasmaWordIndexEntity[] indexes, HashMap urlCache, boolean gzipBody, int timeout) {
        HashMap in = transferRWI(targetSeed, indexes, gzipBody, timeout);
        if (in == null) { return "no_connection_1"; }
        String result = (String) in.get("result");
        if (result == null) { return "no_result_1"; }
        if (!(result.equals("ok"))) return result;
        // in now contains a list of unknown hashes
        final String uhss = (String) in.get("unknownURL");
        if (uhss == null) { return "no_unknownURL_tag_in_response"; }
        if (uhss.length() == 0) { return null; } // all url's known, we are ready here
        final String[] uhs = uhss.split(",");
//      System.out.println("DEBUG yacyClient.transferIndex: " + uhs.length + " urls unknown");
        if (uhs.length == 0) { return null; } // all url's known
        // extract the urlCache from the result
        plasmaCrawlLURL.Entry[] urls = new plasmaCrawlLURL.Entry[uhs.length];
        for (int i = 0; i < uhs.length; i++) {
            urls[i] = (plasmaCrawlLURL.Entry) urlCache.get(uhs[i]);
            if (urls[i] == null) System.out.println("DEBUG transferIndex: error with requested url hash '" + uhs[i] + "', unknownURL='" + uhss + "'");
        }
        in = transferURL(targetSeed, urls, gzipBody, timeout);
        if (in == null) { return "no_connection_2"; }
        result = (String) in.get("result");
        if (result == null) { return "no_result_2"; }
        if (!(result.equals("ok"))) { return result; }
//      int doubleentries = Integer.parseInt((String) in.get("double"));
//      System.out.println("DEBUG tansferIndex: transferred " + uhs.length + " URL's, double=" + doubleentries);
        return null;
    }

    private static HashMap transferRWI(yacySeed targetSeed, plasmaWordIndexEntity[] indexes, boolean gzipBody, int timeout) {
        final String address = targetSeed.getAddress();
        if (address == null) { return null; }
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
        Enumeration eenum;
        plasmaWordIndexEntry entry;
        for (int i = 0; i < indexes.length; i++) {
            eenum = indexes[i].elements(true);
            while (eenum.hasMoreElements()) {
                entry = (plasmaWordIndexEntry) eenum.nextElement();
                entrypost.append(indexes[i].wordHash()) 
                         .append(entry.toExternalForm()) 
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
            final ArrayList v = httpc.wput(new URL("http://" + address + "/yacy/transferRWI.html"), timeout, null, null,
            yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            // this should return a list of urlhashes that are unknwon
            if (v != null) {
                yacyCore.seedDB.mySeed.incSI(indexcount);
            }
            
            final HashMap result = nxTools.table(v);
            return result;
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.transferRWI error:" + e.getMessage());
            return null;
        }
    }

    private static HashMap transferURL(yacySeed targetSeed, plasmaCrawlLURL.Entry[] urls, boolean gzipBody, int timeout) {
        // this post a message to the remote message board
        final String address = targetSeed.getAddress();
        if (address == null) { return null; }
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
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                resource = urls[i].toString();
                if (resource != null) {
                    post.put("url" + urlc, resource);
                    urlc++;
                }
            }
        }
        post.put("urlc", Integer.toString(urlc));
        try {
            final ArrayList v = httpc.wput(new URL("http://" + address + "/yacy/transferURL.html"), timeout, null, null,
            yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            if (v != null) {
                yacyCore.seedDB.mySeed.incSU(urlc);
            }
            return nxTools.table(v);
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.transferRWI error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap getProfile(yacySeed targetSeed) {
        // this post a message to the remote message board
        final serverObjects post = new serverObjects(2);
        post.put("iam", yacyCore.seedDB.mySeed.hash);
        post.put("youare", targetSeed.hash);
        String address = targetSeed.getAddress();
        if (address == null) { address = "localhost:8080"; }
        try {
            final ArrayList v = httpc.wput(new URL("http://" + address + "/yacy/profile.html"), 20000, null, null,
            yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            return nxTools.table(v);
        } catch (Exception e) {
            yacyCore.log.logSevere("yacyClient.getProfile error:" + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        System.out.println("yacyClient Test");
        try {
            final plasmaSwitchboard sb = new plasmaSwitchboard(args[0], "httpProxy.init", "DATA/SETTINGS/httpProxy.conf");
            final yacyCore core = new yacyCore(sb);
            core.peerActions.loadSeedLists();
            final yacySeed target = core.seedDB.getConnected(args[1]);
            final String wordhashe = plasmaWordIndexEntry.word2hash("test");
            //System.out.println("permission=" + permissionMessage(args[1]));
            
            final HashMap result = nxTools.table(httpc.wget(
            new URL("http://" + target.getAddress() +
            "/yacy/search.html?myseed=" + core.seedDB.mySeed.genSeedStr(null) +
            "&youare=" + target.hash + "&key=" +
            "&myseed=" + core.seedDB.mySeed.genSeedStr(null) +
            "&count=10&resource=global" +
            "&query=" + wordhashe),
            5000, null, null, core.seedDB.sb.remoteProxyHost, core.seedDB.sb.remoteProxyPort));
            System.out.println("Result=" + result.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

}
