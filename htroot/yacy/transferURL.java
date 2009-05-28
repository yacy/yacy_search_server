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

import de.anomic.content.RSSMessage;
import de.anomic.data.Blacklist;
import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.xml.RSSFeed;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacySeed;

public final class transferURL {

    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) throws InterruptedException {
        final long start = System.currentTimeMillis();
        long freshdate = 0;
        try {freshdate = DateFormatter.parseShortDay("20061101").getTime();} catch (final ParseException e1) {}
        
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        if (!yacyNetwork.authentifyRequest(post, env)) return prop;

        // request values
        final String iam      = post.get("iam", "");      // seed hash of requester
        final String youare   = post.get("youare", "");   // seed hash of the target peer, needed for network stability
//      final String key      = post.get("key", "");      // transmission key
        final int urlc        = post.getInt("urlc", 0);    // number of transported urls
        final boolean granted = sb.getConfig("allowReceiveIndex", "false").equals("true");
        final boolean blockBlacklist = sb.getConfig("indexReceiveBlockBlacklist", "false").equals("true");

        // response values
        String result = "";
        String doublevalues = "0";

        final yacySeed otherPeer = sb.peers.get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));

        if ((youare == null) || (!youare.equals(sb.peers.mySeed().hash))) {
        	sb.getLog().logInfo("Rejecting URLs from peer " + otherPeerName + ". Wrong target. Wanted peer=" + youare + ", iam=" + sb.peers.mySeed().hash);
            result = "wrong_target";
        } else if ((!granted) || (sb.isRobinsonMode())) {
        	sb.getLog().logInfo("Rejecting URLs from peer " + otherPeerName + ". Not granted.");
            result = "error_not_granted";
        } else {
            int received = 0;
            int blocked = 0;
            final int sizeBefore = sb.indexSegment.metadata().size();
            // read the urls from the other properties and store
            String urls;
            URLMetadataRow lEntry;
            for (int i = 0; i < urlc; i++) {
                serverCore.checkInterruption();
                
                // read new lurl-entry
                urls = post.get("url" + i);
                if (urls == null) {
                    if (yacyCore.log.isFine()) yacyCore.log.logFine("transferURL: got null URL-string from peer " + otherPeerName);
                    blocked++;
                    continue;
                }

                // parse new lurl-entry
                lEntry = URLMetadataRow.importEntry(urls);
                if (lEntry == null) {
                    yacyCore.log.logWarning("transferURL: received invalid URL (entry null) from peer " + otherPeerName + "\n\tURL Property: " + urls);
                    blocked++;
                    continue;
                }
                
                // check if entry is well-formed
                final URLMetadataRow.Components metadata = lEntry.metadata();
                if (metadata.url() == null) {
                    yacyCore.log.logWarning("transferURL: received invalid URL from peer " + otherPeerName + "\n\tURL Property: " + urls);
                    blocked++;
                    continue;
                }
                
                // check whether entry is too old
                if (lEntry.freshdate().getTime() <= freshdate) {
                    if (yacyCore.log.isFine()) yacyCore.log.logFine("transerURL: received too old URL from peer " + otherPeerName + ": " + lEntry.freshdate());
                    blocked++;
                    continue;
                }
                
                // check if the entry is blacklisted
                if ((blockBlacklist) && (plasmaSwitchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_DHT, metadata.url()))) {
                    if (yacyCore.log.isFine()) yacyCore.log.logFine("transferURL: blocked blacklisted URL '" + metadata.url().toNormalform(false, true) + "' from peer " + otherPeerName);
                    lEntry = null;
                    blocked++;
                    continue;
                }
                
                // check if the entry is in our network domain
                final String urlRejectReason = sb.crawlStacker.urlInAcceptedDomain(metadata.url());
                if (urlRejectReason != null) {
                    if (yacyCore.log.isFine()) yacyCore.log.logFine("transferURL: blocked URL '" + metadata.url() + "' (" + urlRejectReason + ") from peer " + otherPeerName);
                    lEntry = null;
                    blocked++;
                    continue;
                }
                
                // write entry to database
                try {
                    sb.indexSegment.metadata().store(lEntry);
                    sb.crawlResults.stack(lEntry, iam, iam, 3);
                    if (yacyCore.log.isFine()) yacyCore.log.logFine("transferURL: received URL '" + metadata.url().toNormalform(false, true) + "' from peer " + otherPeerName);
                    received++;
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }

            sb.peers.mySeed().incRU(received);

            // return rewrite properties
            final int more = sb.indexSegment.metadata().size() - sizeBefore;
            doublevalues = Integer.toString(received - more);
            sb.getLog().logInfo("Received " + received + " URLs from peer " + otherPeerName + " in " + (System.currentTimeMillis() - start) + " ms, blocked " + blocked + " URLs");
            RSSFeed.channels(RSSFeed.INDEXRECEIVE).addMessage(new RSSMessage("Received " + received + " URLs from peer " + otherPeerName + ", blocked " + blocked, "", ""));
            if ((received - more) > 0) sb.getLog().logSevere("Received " + doublevalues + " double URLs from peer " + otherPeerName);
            result = "ok";
        }

        prop.put("double", doublevalues);
        prop.put("result", result);
        return prop;
    }
}
