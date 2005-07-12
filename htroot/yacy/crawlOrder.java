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
	String url        = crypt.simpleDecode((String) post.get("url", ""), key); // the url string to crawl
	String referrer   = crypt.simpleDecode((String) post.get("referrer", ""), key); // the referrer url
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
	int     acceptDelay = Integer.parseInt(switchboard.getConfig("crawlResponseDelay", "0"));
	
        if (orderDepth > acceptDepth) orderDepth = acceptDepth;

	// check if requester is authorized
        if ((yacyCore.seedDB.mySeed == null) || (!(yacyCore.seedDB.mySeed.hash.equals(youare)))) {
	    // this request has a wrong target
            response = "denied";
	    reason = "authentify-problem";
            delay = "3600"; // may request one hour later again
	} else if (orderDepth > 0) {
            response = "denied";
            reason = "order must be 0";
            delay = "3600"; // may request one hour later again
        } else if (!(granted)) {
            response = "denied";
            reason = "not granted to remote crawl";
            delay = "3600"; // may request one hour later again
        } else try {
            yacySeed requester = yacyCore.seedDB.getConnected(iam);
            int queuesize = switchboard.queueSize();
            String urlhash = plasmaURL.urlHash(new URL(url));
            if (requester == null) {
                response = "denied";
                reason = "unknown-client";
                delay = "240";
            } else if (!((requester.isSenior()) || (requester.isPrincipal()))) {
                response = "denied";
                reason = "not-qualified";
                delay = "240";
            } else if (queuesize > 1) {
                response = "rejected";
                reason = "busy";
                delay = "" + (queuesize * acceptDelay);
            } else if (!(process.equals("crawl"))) {
                response = "denied";
                reason = "unknown-order";
                delay = "9999";
            } else {
		// stack url  
		String reasonString = switchboard.stackCrawl(url, referrer, iam, "REMOTE-CRAWLING", new Date(), 0, switchboard.defaultRemoteProfile);
                if (reasonString == null) {
                    // liftoff!
                    response = "stacked";
                    reason = "ok";
                    delay = "" + acceptDelay; // this value needs to be calculated individually
                } else if (reasonString.equals("double_(already_loaded)")) {
                    // case where we have already the url loaded;
                    reason = reasonString;
                    delay = "" + (acceptDelay / 4);
                    // send lurl-Entry as response
                    plasmaCrawlLURL.Entry entry = switchboard.urlPool.loadedURL.getEntry(plasmaCrawlLURL.urlHash(url));
                    if (entry != null) {
                        response = "double";
                        switchboard.urlPool.loadedURL.notifyGCrawl(entry.hash(), iam, youare);
                        lurl = crypt.simpleEncode(entry.toString());
                        delay = "1";
                    } else {
                        response = "rejected";
                    }
                } else {
                    response = "rejected";
                    reason = reasonString;
                    delay = "" + (acceptDelay / 4);
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

}
