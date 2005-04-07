// yacyClient.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 02.12.2004
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

import java.io.*;
import java.util.*;
import java.net.*;
import de.anomic.tools.*;
import de.anomic.plasma.*;
import de.anomic.net.*;
import de.anomic.http.*;
import de.anomic.server.*;

public class yacyClient {

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

	String key = crypt.randomSalt();
	HashMap result = null;
	try {
	    /*
	    URL url = new URL("http://" + address + "/yacy/hello.html?iam=" + yacyCore.seedCache.mySeed.hash +
                                "&pattern=&count=20" + 
			      "&key=" + key + "&seed=" + yacyCore.seedCache.mySeed.genSeedStr(key));
	    yacyCore.log.logDebug("HELLO to URL " + url.toString());
	    result = nxTools.table(httpc.wget(url,
			10000, null, null, yacyCore.seedCache.sb.remoteProxyHost, yacyCore.seedCache.sb.remoteProxyPort));
	    */

	    URL url = new URL("http://" + address + "/yacy/hello.html");
	    serverObjects obj = new serverObjects();
	    obj.put("iam", yacyCore.seedDB.mySeed.hash);
	    obj.put("pattern", "");
	    obj.put("count", "20");
	    obj.put("key", key);
            obj.put("mytime", yacyCore.universalDateShortString());
	    obj.put("seed", yacyCore.seedDB.mySeed.genSeedStr(key));
            result = nxTools.table(httpc.wput(url, 
					      20000, null, null,
					      yacyCore.seedDB.sb.remoteProxyHost,
					      yacyCore.seedDB.sb.remoteProxyPort,
					      obj));
	} catch (Exception e) {
	    yacyCore.log.logDebug("yacyClient.publishMySeed exception:" + e.getMessage());
	    return -1;
	}
	if ((result == null) || (result.size() < 3)) {
	    yacyCore.log.logDebug("yacyClient.publishMySeed result error: " +
				  ((result == null) ? "result null" : ("result=" + result.toString())));
	    return -1;
	}

        Date remoteTime = yacyCore.parseUniversalDate((String) result.get("mytime")); // read remote time
        
	// check consistency with expectation
	if ((otherHash != null ) && (otherHash.length() > 0)) {
	    yacySeed otherPeer = yacySeed.genRemoteSeed((String) result.get("seed0"), key, remoteTime);
	    if ((otherPeer == null) || (!(otherPeer.hash.equals(otherHash)))) {
		yacyCore.log.logDebug("yacyClient.publishMySeed consistency error: other peer wrong");
		return -1; // no success
	    }
	}

	// set my own seed according to new information
	yacySeed mySeedBkp = (yacySeed) yacyCore.seedDB.mySeed.clone();
	yacyCore.seedDB.mySeed.put("IP", (String) result.get("yourip"));
	String mytype = (String) result.get("yourtype");
	if (mytype == null) mytype = "junior";
	if ((yacyCore.seedDB.mySeed.get("PeerType", "junior").equals("principal")) && (mytype.equals("senior"))) mytype = "principal";
	yacyCore.seedDB.mySeed.put("PeerType", mytype);

	if (!(yacyCore.seedDB.mySeed.isProper())) {
	    yacyCore.seedDB.mySeed = mySeedBkp;
	    yacyCore.log.logDebug("yacyClient.publishMySeed mySeed error: not proper");
	    return -1;
	}

	// read the seeds that the peer returned and integrate them into own database
        int i = 0;
	String seedStr;
	int count = 0;
	while ((seedStr = (String) result.get("seed" + i++)) != null) {
	    // integrate new seed into own database
	    // the first seed, "seed0" is the seed of the responding peer
	    if (yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(seedStr, key, remoteTime), (i == 1))) count++;
	}
	return count;
    }


    public static yacySeed querySeed(yacySeed target, String seedHash) {
	String key = crypt.randomSalt();
        try {
            HashMap result = nxTools.table(httpc.wget(
                            new URL("http://" + target.getAddress() +
                                    "/yacy/query.html?iam=" + yacyCore.seedDB.mySeed.hash +
                                    "&youare=" + target.hash + "&key=" + key +
                                    "&object=seed&env=" + seedHash), 
                            10000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
            if ((result == null) || (result.size() == 0)) return null;
            Date remoteTime = yacyCore.parseUniversalDate((String) result.get("mytime")); // read remote time
            return yacySeed.genRemoteSeed((String) result.get("response"), key, remoteTime);
        } catch (Exception e) {
            yacyCore.log.logError("yacyClient.querySeed error:" + e.getMessage());
            return null;
        }
    }

    public static int queryRWICount(yacySeed target, String wordHash) {
        try {
            HashMap result = nxTools.table(httpc.wget(
                            new URL("http://" + target.getAddress() +
                                    "/yacy/query.html?iam=" + yacyCore.seedDB.mySeed.hash +
                                    "&youare=" + target.hash + "&key=" + 
                                    "&object=rwicount&env=" + wordHash +
                                    "&ttl=0"), 
                            10000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
            if ((result == null) || (result.size() == 0)) return -1;
	    return Integer.parseInt((String) result.get("response"));
	} catch (Exception e) {
            yacyCore.log.logError("yacyClient.queryRWICount error:" + e.getMessage());
	    return -1;
	}
    }

    public static int queryUrlCount(yacySeed target) {
	if (target == null) return -1;
	if (yacyCore.seedDB.mySeed == null) return -1;
	String querystr =
	    "http://" + target.getAddress() +
	    "/yacy/query.html?iam=" + yacyCore.seedDB.mySeed.hash +
	    "&youare=" + target.hash +
	    "&key=" + 
	    "&object=lurlcount&env=&ttl=0";
	try {
	    HashMap result = nxTools.table(httpc.wget(
				new URL(querystr), 5000, null, null,
                                yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
	    //yacyCore.log("DEBUG QUERY: query=" + querystr + "; result = " + result.toString());
	    if ((result == null) || (result.size() == 0)) return -1;
            String resp = (String) result.get("response");
            if (resp == null) return -1; else return Integer.parseInt(resp);
	} catch (Exception e) {
            //yacyCore.log.logError("yacyClient.queryUrlCount error asking peer '" + target.getName() + "':" + e.toString());
	    return -1;
	}
    }

    public static int search(String wordhashes, int count, boolean global,
			     yacySeed targetPeer, plasmaCrawlLURL urlManager, plasmaSearch searchManager,
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
	String key = crypt.randomSalt();
	try {
	    String url = "http://" + targetPeer.getAddress() + "/yacy/search.html";
	    /*
	    String url = "http://" + targetPeer.getAddress() +
		"/yacy/search.html?myseed=" + yacyCore.seedCache.mySeed.genSeedStr(key) +
		"&youare=" + targetPeer.hash + "&key=" + key +
		"&myseed=" + yacyCore.seedCache.mySeed.genSeedStr(key) +
		"&count=" + count + "&resource=" + ((global) ? "global" : "local") +
		"&query=" + wordhashes;
	    */
	    serverObjects obj = new serverObjects();
	    obj.put("myseed", yacyCore.seedDB.mySeed.genSeedStr(key));
	    obj.put("youare", targetPeer.hash);
	    obj.put("key", key);
	    obj.put("count", count);
	    obj.put("resource", ((global) ? "global" : "local"));
	    obj.put("query", wordhashes);
            obj.put("ttl", "0");
            obj.put("duetime", "" + duetime);
	    obj.put("mytime", yacyCore.universalDateShortString());
	    //yacyCore.log.logDebug("yacyClient.search url=" + url);
            long timestamp = System.currentTimeMillis();
            HashMap result = nxTools.table(httpc.wput(new URL(url), 
						      300000, null, null,
						      yacyCore.seedDB.sb.remoteProxyHost,
						      yacyCore.seedDB.sb.remoteProxyPort,
						      obj));
            long totalrequesttime = System.currentTimeMillis() - timestamp;
            
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
	    int results = Integer.parseInt((String) result.get("count"));
            //System.out.println("***result count " + results);
	    plasmaCrawlLURL.entry link;
	    String wordhash;
	    for (int n = 0; n < results; n++) {
		link = urlManager.newEntry((String) result.get("resource" + n), true, yacyCore.seedDB.mySeed.hash, targetPeer.hash, 2);
		for (int m = 0; m < wordhashes.length() / plasmaCrawlLURL.urlHashLength; m++) {
		    wordhash = wordhashes.substring(m * plasmaCrawlLURL.urlHashLength, (m + 1) * plasmaCrawlLURL.urlHashLength);
		    searchManager.addWordIndex(link.url(), link.hash(), link.moddate(), link.quality(),
					       wordhash, link.wordCount(), 0, 0, 0, link.language(), link.doctype(), false);
		}
	    }
            long searchtime;
            try {
                searchtime = Integer.parseInt("" + (String) result.get("searchtime"));
            } catch (NumberFormatException e) {
                searchtime = totalrequesttime;
            }
            yacyCore.log.logDebug("yacyClient.search: processed " + results + " links from peer " + targetPeer.hash + "; duetime=" + duetime + ", searchtime=" + searchtime + ", netdelay=" + (totalrequesttime - searchtime) + ", references=" + result.get("references"));
	    return results;
	} catch (Exception e) {
	    yacyCore.log.logError("yacyClient.search error: '" + targetPeer.get("Name", "anonymous") + "' failed - " + e);
	    //e.printStackTrace();
	    return 0;
	}
    }

    public static HashMap permissionMessage(String targetHash) {
	// ask for allowed message size and attachement size
	// if this replies null, the peer does not answer
        if ((yacyCore.seedDB == null) || (yacyCore.seedDB.mySeed == null)) return null;
	serverObjects post = new serverObjects();
	String key = crypt.randomSalt();
	post.put("key", key);
	post.put("process", "permission");
	post.put("iam", yacyCore.seedDB.mySeed.hash);
	post.put("youare", targetHash);
	post.put("mytime", yacyCore.universalDateShortString());
	String address;
	if (targetHash.equals(yacyCore.seedDB.mySeed.hash)) {
	    address = yacyCore.seedDB.mySeed.getAddress();
	    //System.out.println("local address: " + address);
	} else {
            yacySeed targetSeed = yacyCore.seedDB.getConnected(targetHash);
            if (targetSeed == null) return null;
	    address = targetSeed.getAddress();
	    //System.out.println("remote address: " + address);
	}
	if (address == null) address = "localhost:8080";
	try {
            return nxTools.table(httpc.wput(
                    new URL("http://" + address + "/yacy/message.html"),
                    8000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post));
	} catch (Exception e) {
	    // most probably a network time-out exception
            yacyCore.log.logError("yacyClient.permissionMessage error:" + e.getMessage());
	    return null;
	}
    }

    public static HashMap postMessage(String targetHash, String subject, byte[] message) {
	// this post a message to the remote message board
	serverObjects post = new serverObjects();
	String key = crypt.randomSalt();
	post.put("key", key);
	post.put("process", "post");
	post.put("myseed", yacyCore.seedDB.mySeed.genSeedStr(key));
	post.put("youare", targetHash);
	post.put("subject", subject);
	post.put("mytime", yacyCore.universalDateShortString());
	post.put("message", new String(message));
	String address;
	if (targetHash.equals(yacyCore.seedDB.mySeed.hash))
	    address = yacyCore.seedDB.mySeed.getAddress();
	else
	    address = yacyCore.seedDB.getConnected(targetHash).getAddress();
	if (address == null) address = "localhost:8080";
	//System.out.println("DEBUG POST "  + address + "/yacy/message.html" + post.toString());
        try {
            Vector v = httpc.wput(new URL("http://" + address + "/yacy/message.html"), 20000, null, null,
                              yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            //System.out.println("V=" + v.toString());
            return nxTools.table(v);
        } catch (Exception e) {
            yacyCore.log.logError("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap crawlOrder(yacySeed targetSeed, String url, String referrer, int depth) {
    	// this post a message to the remote message board
	if (targetSeed == null) return null;
	if (yacyCore.seedDB.mySeed == null) return null;
        if (yacyCore.seedDB.mySeed == targetSeed) return null;
        
        // construct request
	String key = crypt.randomSalt();
	String address = targetSeed.getAddress();
	if (address == null) return null;
        try {
            return nxTools.table(httpc.wget(
                    new URL("http://" + address + "/yacy/crawlOrder.html?"+
                    "key=" + key +
                    "&process=crawl" +
                    "&youare=" + targetSeed.hash +
                    "&iam=" + yacyCore.seedDB.mySeed.hash +
                    "&url=" + crypt.simpleEncode(url) +
                    "&referrer=" + crypt.simpleEncode(referrer) +
                    "&depth=" + depth +
                    "&ttl=0"
                    ),
                    10000, null, null, yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort));
	} catch (Exception e) {
	    // most probably a network time-out exception
            yacyCore.log.logError("yacyClient.crawlOrder error: peer=" + targetSeed.getName() + ", error=" + e.getMessage());
	    return null;
	}
    }
    
    /*
        Test:
        http://217.234.95.114:5777/yacy/crawlOrder.html?key=abc&iam=S-cjM67KhtcJ&youare=EK31N7RgRqTn&process=crawl&referrer=&depth=0&url=p|http://www.heise.de/newsticker/meldung/53245
        version=0.297 uptime=225 accepted=true reason=ok delay=30 depth=0
        -er crawlt, Ergebnis erscheint aber unter falschem initiator
    */
    
    public static HashMap crawlReceipt(yacySeed targetSeed, String process, String result, String reason, plasmaCrawlLURL.entry entry, String wordhashes) {
        if (targetSeed == null) return null;
	if (yacyCore.seedDB.mySeed == null) return null;
        if (yacyCore.seedDB.mySeed == targetSeed) return null;
        
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
	String key = crypt.randomSalt();
	
        String address = targetSeed.getAddress();
	if (address == null) return null;
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
            yacyCore.log.logError("yacyClient.crawlReceipt error:" + e.getMessage());
	    return null;
	}
    }
    /*
         public static byte[] singleGET(String host, int port, String path, int timeout,
                                   String user, String password,
                                   httpHeader requestHeader) throws IOException {
     */
    
    public static String transferIndex(yacySeed targetSeed, plasmaWordIndexEntity[] indexes, plasmaCrawlLURL urlDB) {
        HashMap in = transferRWI(targetSeed, indexes, urlDB);
        if (in == null) return "no_connection_1";
        String result = (String) in.get("result");
        if (result == null) return "no_result_1";
        if (!(result.equals("ok"))) return result;
        // in now contains a list of unknown hashes
        String uhss = (String) in.get("unknownURL");
        if (uhss == null) return "no_unknownURL_tag_in_response";
        if (uhss.length() == 0) return null; // all url's known, we are ready here
        String[] uhs = uhss.split(",");
        //System.out.println("DEBUG yacyClient.transferIndex: " + uhs.length + " urls unknown");
        if (uhs.length == 0) return null; // all url's known
        // extract the urlCache from the result
        HashMap urlCache = (HashMap) in.get("$URLCACHE$");
        plasmaCrawlLURL.entry[] urls = new plasmaCrawlLURL.entry[uhs.length];
        for (int i = 0; i < uhs.length; i++) {
            urls[i] = (plasmaCrawlLURL.entry) urlCache.get(uhs[i]);
            if (urls[i] == null) System.out.println("DEBUG transferIndex: error with requested url hash '" + uhs[i] + "', unknownURL='" + uhss + "'");
        }
        in = transferURL(targetSeed, urls);
        if (in == null) return "no_connection_2";
        result = (String) in.get("result");
        if (result == null) return "no_result_2";
        if (!(result.equals("ok"))) return result;
        int doubleentries = Integer.parseInt((String) in.get("double"));
        //System.out.println("DEBUG tansferIndex: transferred " + uhs.length + " URL's, double=" + doubleentries);
        return null;
    }
    
    private static HashMap transferRWI(yacySeed targetSeed, plasmaWordIndexEntity[] indexes, plasmaCrawlLURL urlDB) {
	String address = targetSeed.getAddress();
	if (address == null) return null;
        // prepare post values
	serverObjects post = new serverObjects();
	String key = crypt.randomSalt();
	post.put("key", key);
        post.put("iam", yacyCore.seedDB.mySeed.hash);
	post.put("youare", targetSeed.hash);
	post.put("wordc", "" + indexes.length);
        int indexcount = 0;
        String entrypost = "";
        Enumeration eenum;
        plasmaWordIndexEntry entry;
        HashMap urlCache = new HashMap();
        plasmaCrawlLURL.entry urlentry;
        HashSet unknownURLs = new HashSet();
        for (int i = 0; i < indexes.length; i++) {
            eenum = indexes[i].elements(true);
            while (eenum.hasMoreElements()) {
                entry = (plasmaWordIndexEntry) eenum.nextElement();
                // check if an LURL-Entry exists
                if (urlCache.containsKey(entry.getUrlHash())) {
                    // easy case: the url is known and in the cache
                    entrypost += indexes[i].wordHash() + entry.toExternalForm() + serverCore.crlfString;
                    indexcount++;
                } else if (unknownURLs.contains(entry.getUrlHash())) {
                    // in this case, we do nothing
                } else {
                    // try to get the entry from the urlDB
                    if ((urlDB.exists(entry.getUrlHash())) &&
                        ((urlentry = urlDB.getEntry(entry.getUrlHash())) != null)) {
                        // good case: store the urlentry to the cache
                        urlCache.put(entry.getUrlHash(), urlentry);
                        // add index to list
                        entrypost += indexes[i].wordHash() + entry.toExternalForm() + serverCore.crlfString;
                        indexcount++;
                    } else {
                        // this is bad: the url is unknown. We put the link to a set and delete then later
                        unknownURLs.add(entry.getUrlHash());
                    }
                }
            }
        }
        
        // we loop again and delete all links where the url is unknown
        Iterator it;
        String urlhash;
        for (int i = 0; i < indexes.length; i++) {
            it = unknownURLs.iterator();
            while (it.hasNext()) {
                urlhash = (String) it.next();
                try {
                    if (indexes[i].contains(urlhash)) indexes[i].removeEntry(urlhash, true);
                } catch (IOException e) {}
            }
        }
        
        post.put("entryc", "" + indexcount);
        post.put("indexes", entrypost);
	try {
            Vector v = httpc.wput(new URL("http://" + address + "/yacy/transferRWI.html"), 60000, null, null,
                              yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            // this should return a list of urlhashes that are unknwon
            if (v != null) {
                yacyCore.seedDB.mySeed.incSI(indexcount);
            }
            
            HashMap result = nxTools.table(v);
            result.put("$URLCACHE$", urlCache);
            result.put("$UNKNOWNC$", "" + unknownURLs.size());
            return result;
        } catch (Exception e) {
            yacyCore.log.logError("yacyClient.transferRWI error:" + e.getMessage());
            return null;
        }
    }
    
    private static HashMap transferURL(yacySeed targetSeed, plasmaCrawlLURL.entry[] urls) {
	// this post a message to the remote message board
	String address = targetSeed.getAddress();
	if (address == null) return null;
        // prepare post values
	serverObjects post = new serverObjects();
	String key = crypt.randomSalt();
	post.put("key", key);
        post.put("iam", yacyCore.seedDB.mySeed.hash);
	post.put("youare", targetSeed.hash);
	String resource = "";
        int urlc = 0;
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                resource = urls[i].toString();
                if (resource != null) {
                    post.put("url" + i, resource);
                    urlc++;
                }
            }
        }
        post.put("urlc", "" + urlc);
	try {
            Vector v = httpc.wput(new URL("http://" + address + "/yacy/transferURL.html"), 60000, null, null,
                              yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            if (v != null) {
                yacyCore.seedDB.mySeed.incSU(urlc);
            }
            return nxTools.table(v);
        } catch (Exception e) {
            yacyCore.log.logError("yacyClient.transferRWI error:" + e.getMessage());
            return null;
        }
    }
    
    public static HashMap getProfile(yacySeed targetSeed) {
	// this post a message to the remote message board
	serverObjects post = new serverObjects();
	post.put("iam", yacyCore.seedDB.mySeed.hash);
	post.put("youare", targetSeed.hash);
	String address = targetSeed.getAddress();
	if (address == null) address = "localhost:8080";
	try {
            Vector v = httpc.wput(new URL("http://" + address + "/yacy/profile.html"), 20000, null, null,
                              yacyCore.seedDB.sb.remoteProxyHost, yacyCore.seedDB.sb.remoteProxyPort, post);
            return nxTools.table(v);
        } catch (Exception e) {
            yacyCore.log.logError("yacyClient.getProfile error:" + e.getMessage());
            return null;
        }
    }
        
    public static void main(String[] args) {
        System.out.println("yacyClient Test");
        try {
            plasmaSwitchboard sb = new plasmaSwitchboard(args[0], "httpProxy.init", "DATA/SETTINGS/httpProxy.conf");
            yacyCore core = new yacyCore(sb);
            core.peerActions.loadSeedLists();
            yacySeed target = core.seedDB.getConnected(args[1]);
            String wordhashe = plasmaWordIndexEntry.word2hash("test");
	    //System.out.println("permission=" + permissionMessage(args[1]));
    
            HashMap result = nxTools.table(httpc.wget(
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
