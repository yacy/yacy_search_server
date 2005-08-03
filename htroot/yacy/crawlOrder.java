// crawlOrder.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 02.05.2004
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
// javac -classpath .:../classes crawlOrder.java


import java.net.URL;
import java.util.Date;
import java.util.Vector;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class crawlOrder {

    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
        
	if ((post == null) || (env == null)) return prop;

	int proxyPrefetchDepth = Integer.parseInt(env.getConfig("proxyPrefetchDepth", "0"));
	int crawlingdepth = Integer.parseInt(env.getConfig("crawlingDepth", "0"));

	// request values
	String iam        = (String) post.get("iam", "");       // seed hash of requester
      	String youare     = (String) post.get("youare", "");    // seed hash of the target peer, needed for network stability
	String process    = (String) post.get("process", "");   // process type
	String key        = (String) post.get("key", "");       // transmission key
	int    orderDepth = Integer.parseInt((String) post.get("depth", "0"));     // crawl depth
        
	// response values
        /*
         the result can have one of the following values:
         negative cases, no retry
           denied      - the peer does not want to crawl that
           exception   - an exception occurred

         negative case, retry possible
           rejected    - the peer has rejected to process, but a re-try should be possible
         
         positive case with crawling
           stacked     - the resource is processed asap
	 
         positive case without crawling	 
           double      - the resource is already in database, believed to be fresh and not reloaded
                         the resource is also returned in lurl
        */
	String  response    = "denied";
	String  reason      = "false-input";
	String  delay       = "5";
        String  lurl        = "";
        boolean granted     = switchboard.getConfig("crawlResponse", "false").equals("true");
	int     acceptDepth = Integer.parseInt(switchboard.getConfig("crawlResponseDepth", "0"));
        int     ppm         = yacyCore.seedDB.mySeed.getPPM();
	int     acceptDelay = (ppm == 0) ? 10 : (2 + 60 / yacyCore.seedDB.mySeed.getPPM());
	
        if (orderDepth > acceptDepth) orderDepth = acceptDepth;

	// check if requester is authorized
        if ((yacyCore.seedDB.mySeed == null) || (!(yacyCore.seedDB.mySeed.hash.equals(youare)))) {
	    // this request has a wrong target
            response = "denied";
	    reason = "authentify-problem";
            delay = "3600"; // may request one hour later again
	} else if (orderDepth > 0) {
            response = "denied";
            reason = "order depth must be 0";
            delay = "3600"; // may request one hour later again
        } else if (!(granted)) {
            response = "denied";
            reason = "not granted to remote crawl";
            delay = "3600"; // may request one hour later again
        } else try {
            yacySeed requester = yacyCore.seedDB.getConnected(iam);
            int queuesize = switchboard.coreCrawlJobSize() + switchboard.limitCrawlTriggerJobSize() + switchboard.remoteTriggeredCrawlJobSize();
            if (requester == null) {
                response = "denied";
                reason = "unknown-client";
                delay = "240";
            } else if (!((requester.isSenior()) || (requester.isPrincipal()))) {
                response = "denied";
                reason = "not-qualified";
                delay = "240";
            } else if (queuesize > 100) {
                response = "rejected";
                reason = "busy";
                delay = Integer.toString(30 + queuesize * acceptDelay);
            } else if (!(process.equals("crawl"))) {
                response = "denied";
                reason = "unknown-order";
                delay = "9999";
            } else {
                // read the urls/referrer-vector
                Vector urlv = new Vector();
                Vector refv = new Vector();
                String refencoded = (String) post.get("referrer", null);
                String urlencoded = (String) post.get("url", null);
                if (urlencoded != null) {
                    // old method: only one url
                    urlv.add(crypt.simpleDecode(urlencoded, key)); // the url string to crawl
                } else {
                    // new method: read a vector of urls
                    while ((urlencoded = (String) post.get("url" + urlv.size(), null)) != null) {
                        urlv.add(crypt.simpleDecode(urlencoded, key));
                    }
                }
                if (refencoded != null) {
                    // old method: only one url
                    refv.add(crypt.simpleDecode(refencoded, key)); // the referrer url
                } else {
                    // new method: read a vector of urls
                    while ((refencoded = (String) post.get("ref" + refv.size(), null)) != null) {
                        refv.add(crypt.simpleDecode(refencoded, key));
                    }
                }

                // stack the urls
                Object[] stackresult;
                int count = Math.min(urlv.size(), refv.size());
                if (count == 1) {
                    // old method: only one url
                    stackresult = stack(switchboard, (String) urlv.elementAt(0), (String) refv.elementAt(0), iam, youare);
                    response = (String) stackresult[0];
                    reason = (String) stackresult[1];
                    lurl = (String) stackresult[2];
                    delay = (response.equals("stacked")) ? Integer.toString(5 + acceptDelay) : "1"; // this value needs to be calculated individually
                } else {
                    // new method: several urls
                    int stackCount = 0;
                    int doubleCount = 0;
                    int rejectedCount = 0;
                    for (int i = 0; i < count; i++) {
                        stackresult = stack(switchboard, (String) urlv.elementAt(i), (String) refv.elementAt(i), iam, youare);
                        response = (String) stackresult[0];
                        prop.put("list_" + i + "_job", (String) stackresult[0] + "," + (String) stackresult[1]);
                        prop.put("list_" + i + "_lurl", (String) stackresult[2]);
                        prop.put("list_" + i + "_count", i);
                    }
                    prop.put("list", count);
                    response = "enqueued";
                    reason = "ok";
                    lurl = "";
                    delay = Integer.toString(stackCount * acceptDelay + 1);
                }
	    }
        } catch (Exception e) {
            // mist
            e.printStackTrace();
            reason = "ERROR: " + e.getMessage();
            delay = "600";
        }

	prop.put("response", response);
	prop.put("reason", reason);
	prop.put("delay", delay);
	prop.put("depth", acceptDepth);
        prop.put("lurl", lurl);
        prop.put("forward", "");
        prop.put("key", key);
        
	// return rewrite properties
	return prop;
    }
    

    private static Object[] stack(plasmaSwitchboard switchboard, String url, String referrer, String iam, String youare) {
        String response, reason, lurl;
        // stack url
        String reasonString = switchboard.stackCrawl(url, referrer, iam, "REMOTE-CRAWLING", new Date(), 0, switchboard.defaultRemoteProfile);
        if (reasonString == null) {
            // liftoff!
            response = "stacked";
            reason = "ok";
            lurl = "";
        } else if (reasonString.startsWith("double")) {
            // case where we have already the url loaded;
            reason = reasonString;
            // send lurl-Entry as response
            plasmaCrawlLURL.Entry entry = switchboard.urlPool.loadedURL.getEntry(plasmaCrawlLURL.urlHash(url));
            if (entry != null) {
                response = "double";
                switchboard.urlPool.loadedURL.notifyGCrawl(entry.hash(), iam, youare);
                lurl = crypt.simpleEncode(entry.toString());
            } else {
                response = "rejected";
                lurl = "";
            }
        } else {
            response = "rejected";
            reason = reasonString;
            lurl = "";
        }
        return new Object[]{response, reason, lurl};
    }
    
}
