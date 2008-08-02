// crawlReceipt.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
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
// javac -classpath .:../classes crawlOrder.java


import java.io.IOException;

import de.anomic.crawler.ZURL;
import de.anomic.http.httpHeader;
import de.anomic.index.indexURLReference;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacySeed;

public final class crawlReceipt {

    
    /*
     * this is used to respond on a remote crawling request
     */

    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        if (!yacyNetwork.authentifyRequest(post, env)) return prop;
        
        final serverLog log = sb.getLog();

        //int proxyPrefetchDepth = Integer.parseInt(env.getConfig("proxyPrefetchDepth", "0"));
        //int crawlingDepth = Integer.parseInt(env.getConfig("crawlingDepth", "0"));

        // request values
        final String iam        = post.get("iam", "");      // seed hash of requester
        final String youare     = post.get("youare", "");    // seed hash of the target peer, needed for network stability
        //String process    = post.get("process", "");  // process type
        final String key        = post.get("key", "");      // transmission key
        //String receivedUrlhash    = post.get("urlhash", "");  // the url hash that has been crawled
        final String result     = post.get("result", "");   // the result; either "ok" or "fail"
        final String reason     = post.get("reason", "");   // the reason for that result
        //String words      = post.get("wordh", "");    // priority word hashes
        final String propStr    = crypt.simpleDecode(post.get("lurlEntry", ""), key);
        
        /*
         the result can have one of the following values:
         negative cases, no retry
           unavailable - the resource is not available (a broken link); not found or interrupted
           exception   - an exception occurred
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
        
        final yacySeed otherPeer = sb.webIndex.seedDB.get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));        

        if ((sb.webIndex.seedDB.mySeed() == null) || (!(sb.webIndex.seedDB.mySeed().hash.equals(youare)))) {
            // no yacy connection / unknown peers
            prop.put("delay", "3600");
            return prop;
        }
        
        if (propStr == null) {
            // error with url / wrong key
            prop.put("delay", "3600");
            return prop;
        }
        
        if ((sb.isRobinsonMode()) && (!sb.isInMyCluster(otherPeer))) {
        	// we reject urls that are from outside our cluster
        	prop.put("delay", "9999");
    	}
        
        // generating a new loaded URL entry
        final indexURLReference entry = indexURLReference.importEntry(propStr);
        if (entry == null) {
            log.logWarning("crawlReceipt: RECEIVED wrong RECEIPT (entry null) from peer " + iam + "\n\tURL properties: "+ propStr);
            prop.put("delay", "3600");
            return prop;
        }
        
        final indexURLReference.Components comp = entry.comp();
        if (comp.url() == null) {
            log.logWarning("crawlReceipt: RECEIVED wrong RECEIPT (url null) for hash " + entry.hash() + " from peer " + iam + "\n\tURL properties: "+ propStr);
            prop.put("delay", "3600");
            return prop;
        }
        
        // check if the entry is in our network domain
        final String urlRejectReason = sb.acceptURL(comp.url());
        if (urlRejectReason != null) {
            log.logWarning("crawlReceipt: RECEIVED wrong RECEIPT (" + urlRejectReason + ") for hash " + entry.hash() + " from peer " + iam + "\n\tURL properties: "+ propStr);
            prop.put("delay", "9999");
            return prop;
        }
        
        if (result.equals("fill")) try {
            // put new entry into database
            sb.webIndex.putURL(entry);
            sb.crawlResults.stack(entry, youare, iam, 1);
            sb.crawlQueues.delegatedURL.remove(entry.hash()); // the delegated work has been done
            log.logInfo("crawlReceipt: RECEIVED RECEIPT from " + otherPeerName + " for URL " + entry.hash() + ":" + comp.url().toNormalform(false, true));

            // ready for more
            prop.put("delay", "10");
            return prop;
        } catch (final IOException e) {
            e.printStackTrace();
            prop.put("delay", "3600");
            return prop;
        }

        sb.crawlQueues.delegatedURL.remove(entry.hash()); // the delegated work is transformed into an error case
        final ZURL.Entry ee = sb.crawlQueues.errorURL.newEntry(
                entry.toBalancerEntry(iam),
                youare,
                null,
                0,
                result + ":" + reason);
        ee.store();
        sb.crawlQueues.errorURL.push(ee);
        //switchboard.noticeURL.remove(receivedUrlhash);
        prop.put("delay", "3600");
        return prop;
	
         // return rewrite properties
	
    }

}
