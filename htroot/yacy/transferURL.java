// transferURL.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
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
// javac -classpath .:../classes transferRWI.java

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.crawler.data.ResultURLs.EventOrigin;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.peers.EventChannel;
import net.yacy.peers.Network;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class transferURL {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final long start = System.currentTimeMillis();
        long freshdate = 0;
        try {freshdate = GenericFormatter.SHORT_DAY_FORMATTER.parse("20061101").getTime();} catch (final ParseException e1) {}

        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        if (!Protocol.authentifyRequest(post, env)) return prop;

        // request values
        final String iam      = post.get("iam", "");      // seed hash of requester
        final String youare   = post.get("youare", "");   // seed hash of the target peer, needed for network stability
//      final String key      = post.get("key", "");      // transmission key
        final int urlc        = post.getInt("urlc", 0);    // number of transported urls
        final boolean granted = sb.getConfigBool("allowReceiveIndex", false);
        final boolean blockBlacklist = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_BLOCK_BLACKLIST, false);

        // response values
        String result = "";
        String doublevalues = "0";

        final Seed otherPeer = sb.peers.get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));

        if ((youare == null) || (!youare.equals(sb.peers.mySeed().hash))) {
            Network.log.info("Rejecting URLs from peer " + otherPeerName + ". Wrong target. Wanted peer=" + youare + ", iam=" + sb.peers.mySeed().hash);
            result = "wrong_target";
        } else if ((!granted) || (sb.isRobinsonMode())) {
            Network.log.info("Rejecting URLs from peer " + otherPeerName + ". Not granted.");
            result = "error_not_granted";
        } else {
            int received = 0;
            int blocked = 0;
            int doublecheck = 0;
            // read the urls from the other properties and store
            String urls;
            URIMetadataNode lEntry;
            Map<String, URIMetadataNode> lEm = new HashMap<String, URIMetadataNode>();
            for (int i = 0; i < urlc; i++) {

                // read new lurl-entry
                urls = post.get("url" + i);
                if (urls == null) {
                    if (Network.log.isFine()) Network.log.fine("transferURL: got null URL-string from peer " + otherPeerName);
                    blocked++;
                    continue;
                }

                // parse new lurl-entry
                lEntry = URIMetadataNode.importEntry(urls);
                if (lEntry == null) {
                	if (Network.log.isWarn()) Network.log.warn("transferURL: received invalid URL (entry null) from peer " + otherPeerName + "\n\tURL Property: " + urls);
                    blocked++;
                    continue;
                }

                // check if entry is well-formed
                if (lEntry.url() == null) {
                	if (Network.log.isWarn()) Network.log.warn("transferURL: received invalid URL from peer " + otherPeerName + "\n\tURL Property: " + urls);
                    blocked++;
                    continue;
                }

                // check whether entry is too old
                if (lEntry.freshdate().getTime() <= freshdate) {
                    if (Network.log.isFine()) Network.log.fine("transerURL: received too old URL from peer " + otherPeerName + ": " + lEntry.freshdate());
                    blocked++;
                    continue;
                }

                // check if the entry is blacklisted
                if ((blockBlacklist) && (Switchboard.urlBlacklist.isListed(BlacklistType.DHT, lEntry.url()))) {
                	if (Network.log.isFine()) Network.log.fine("transferURL: blocked blacklisted URL '" + lEntry.url().toNormalform(false) + "' from peer " + otherPeerName);
                    lEntry = null;
                    blocked++;
                    continue;
                }

                // check if the entry is in our network domain
                final String urlRejectReason = sb.crawlStacker.urlInAcceptedDomain(lEntry.url());
                if (urlRejectReason != null) {
                    if (Network.log.isFine()) Network.log.fine("transferURL: blocked URL '" + lEntry.url() + "' (" + urlRejectReason + ") from peer " + otherPeerName);
                    lEntry = null;
                    blocked++;
                    continue;
                }

                lEm.put(ASCII.String(lEntry.hash()), lEntry);
            }
            
            doublecheck = 0;
            for (String id : lEm.keySet()) {
                if (sb.index.getLoadTime(id) < 0) {
                    lEntry = lEm.get(id);

                    // write entry to database
                    if (Network.log.isFine()) Network.log.fine("Accepting URL from peer " + otherPeerName + ": " + lEntry.url().toNormalform(true));
                    try {
                        sb.index.fulltext().putMetadata(lEntry);
                        ResultURLs.stack(ASCII.String(lEntry.url().hash()), lEntry.url().getHost(), iam.getBytes(), iam.getBytes(), EventOrigin.DHT_TRANSFER);
                        if (Network.log.isFine()) Network.log.fine("transferURL: received URL '" + lEntry.url().toNormalform(false) + "' from peer " + otherPeerName);
                        received++;
                    } catch (final IOException e) {
                        ConcurrentLog.logException(e);
                    }
                } else {
                    doublecheck++;
                }
            }

            sb.peers.mySeed().incRU(received);

            // return rewrite properties
            Network.log.info("Received " + received + " URLs from peer " + otherPeerName + " in " + (System.currentTimeMillis() - start) + " ms, blocked " + blocked + " URLs");
            EventChannel.channels(EventChannel.DHTRECEIVE).addMessage(new RSSMessage("Received " + received + ", blocked " + blocked + " URLs from peer " + otherPeerName, "", otherPeer.hash));
            if (doublecheck > 0) {
            	Network.log.warn("Received " + doublecheck + "/" + urlc + " double URLs from peer " + otherPeerName); // double should not happen because we demanded only documents which we do not have yet
            	doublevalues = Integer.toString(doublecheck);
            }
            result = "ok";
        }

        prop.put("double", doublevalues);
        prop.put("result", result);
        return prop;
    }
}
