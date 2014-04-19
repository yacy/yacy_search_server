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
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.crawler.data.ResultURLs.EventOrigin;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;

public final class crawlReceipt {


    /*
     * this is used to respond on a remote crawling request
     */

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null) || !Protocol.authentifyRequest(post, env)) {
            return prop;
        }

        final ConcurrentLog log = sb.getLog();

        //int proxyPrefetchDepth = Integer.parseInt(env.getConfig("proxyPrefetchDepth", "0"));
        //int crawlingDepth = Integer.parseInt(env.getConfig("crawlingDepth", "0"));

        // request values
        final String iam        = post.get("iam", "");      // seed hash of requester
        final String youare     = post.get("youare", "");    // seed hash of the target peer, needed for network stability
        final String result     = post.get("result", "");   // the result; either "ok" or "fail"
        final String reason     = post.get("reason", "");   // the reason for that result
        //String words      = post.get("wordh", "");    // priority word hashes
        final String propStr    = crypt.simpleDecode(post.get("lurlEntry", ""));

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

        final Seed otherPeer = sb.peers.get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));

        if ((sb.peers.mySeed() == null) || (!(sb.peers.mySeed().hash.equals(youare)))) {
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
        	return prop;
    	}

        // generating a new loaded URL entry
        final URIMetadataNode entry = URIMetadataNode.importEntry(propStr);
        if (entry == null) {
            if (log.isWarn()) log.warn("crawlReceipt: RECEIVED wrong RECEIPT (entry null) from peer " + iam + "\n\tURL properties: "+ propStr);
            prop.put("delay", "3600");
            return prop;
        }

        if (entry.url() == null) {
            if (log.isWarn()) log.warn("crawlReceipt: RECEIVED wrong RECEIPT (url null) for hash " + ASCII.String(entry.hash()) + " from peer " + iam + "\n\tURL properties: "+ propStr);
            prop.put("delay", "3600");
            return prop;
        }

        // check if the entry is in our network domain
        final String urlRejectReason = sb.crawlStacker.urlInAcceptedDomain(entry.url());
        if (urlRejectReason != null) {
            log.warn("crawlReceipt: RECEIVED wrong RECEIPT (" + urlRejectReason + ") for hash " + ASCII.String(entry.hash()) + " from peer " + iam + "\n\tURL properties: "+ propStr);
            prop.put("delay", "9999");
            return prop;
        }

        // Check URL against DHT blacklist
        if (Switchboard.urlBlacklist.isListed(BlacklistType.DHT, entry.url())) {
            // URL is blacklisted
            log.warn("crawlReceipt: RECEIVED wrong RECEIPT (URL is blacklisted) for URL " + ASCII.String(entry.hash()) + ":" + entry.url().toNormalform(false) + " from peer " + iam);
            prop.put("delay", "9999");
            return prop;
        }

        if ("fill".equals(result)) try {
            // put new entry into database
            sb.index.fulltext().putMetadata(entry);
            ResultURLs.stack(ASCII.String(entry.url().hash()), entry.url().getHost(), youare.getBytes(), iam.getBytes(), EventOrigin.REMOTE_RECEIPTS);
            sb.crawlQueues.delegatedURL.remove(entry.hash()); // the delegated work has been done
            if (log.isInfo()) log.info("crawlReceipt: RECEIVED RECEIPT from " + otherPeerName + " for URL " + ASCII.String(entry.hash()) + ":" + entry.url().toNormalform(false));

            // ready for more
            prop.put("delay", "10");
            return prop;
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            prop.put("delay", "3600");
            return prop;
        }

        sb.crawlQueues.delegatedURL.remove(entry.hash()); // the delegated work is transformed into an error case
        sb.crawlQueues.errorURL.push(entry.url(), 997, null, FailCategory.FINAL_LOAD_CONTEXT, result + ":" + reason, -1);
        //switchboard.noticeURL.remove(receivedUrlhash);
        prop.put("delay", "3600");
        return prop;

         // return rewrite properties

    }

}
