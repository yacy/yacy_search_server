// this is a temporary 1-to-1 copy of the transferURL servlet

package de.anomic.http.server.servlets;

import java.io.IOException;
import java.text.ParseException;

import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.util.DateFormatter;

import de.anomic.content.RSSMessage;
import de.anomic.data.Blacklist;
import de.anomic.document.parser.xml.RSSFeed;
import de.anomic.http.server.RequestHeader;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacySeed;

























public final class transferURL {
 
 public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) throws InterruptedException {
     final long start = System.currentTimeMillis();
     long freshdate = 0;
     try {freshdate = DateFormatter.parseShortDay("20061101").getTime();} catch (final ParseException e1) {}
     
     // return variable that accumulates replacements
     final Switchboard sb = (Switchboard) env;
     final serverObjects prop = new serverObjects();
     if ((post == null) || (env == null)) return prop;
     if (!yacyNetwork.authentifyRequest(post, env)) return prop;

     // request values
     final String iam      = post.get("iam", "");      // seed hash of requester
     final String youare   = post.get("youare", "");   // seed hash of the target peer, needed for network stability
//   final String key      = post.get("key", "");      // transmission key
     final int urlc        = post.getInt("urlc", 0);    // number of transported urls
     final boolean granted = sb.getConfig("allowReceiveIndex", "false").equals("true");
     final boolean blockBlacklist = sb.getConfig("indexReceiveBlockBlacklist", "false").equals("true");

     // response values
     String result = "";
     String doublevalues = "0";

     final yacySeed otherPeer = sb.peers.get(iam);
     final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));

     if ((youare == null) || (!youare.equals(sb.peers.mySeed().hash))) {
         yacyCore.log.logInfo("Rejecting URLs from peer " + otherPeerName + ". Wrong target. Wanted peer=" + youare + ", iam=" + sb.peers.mySeed().hash);
         result = "wrong_target";
     } else if ((!granted) || (sb.isRobinsonMode())) {
         yacyCore.log.logInfo("Rejecting URLs from peer " + otherPeerName + ". Not granted.");
         result = "error_not_granted";
     } else {
         int received = 0;
         int blocked = 0;
         final int sizeBefore = sb.indexSegments.urlMetadata(Segments.Process.DHTIN).size();
         // read the urls from the other properties and store
         String urls;
         URIMetadataRow lEntry;
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
             lEntry = URIMetadataRow.importEntry(urls);
             if (lEntry == null) {
                 yacyCore.log.logWarning("transferURL: received invalid URL (entry null) from peer " + otherPeerName + "\n\tURL Property: " + urls);
                 blocked++;
                 continue;
             }
             
             // check if entry is well-formed
             final URIMetadataRow.Components metadata = lEntry.metadata();
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
             if ((blockBlacklist) && (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_DHT, metadata.url()))) {
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
             yacyCore.log.logInfo("Accepting URL " + i + "/" + urlc + " from peer " + otherPeerName + ": " + lEntry.metadata().url().toNormalform(true, false));
             try {
                 sb.indexSegments.urlMetadata(Segments.Process.DHTIN).store(lEntry);
                 sb.crawlResults.stack(lEntry, iam, iam, 3);
                 if (yacyCore.log.isFine()) yacyCore.log.logFine("transferURL: received URL '" + metadata.url().toNormalform(false, true) + "' from peer " + otherPeerName);
                 received++;
             } catch (final IOException e) {
                 e.printStackTrace();
             }
         }

         sb.peers.mySeed().incRU(received);

         // return rewrite properties
         final int more = sb.indexSegments.urlMetadata(Segments.Process.DHTIN).size() - sizeBefore;
         doublevalues = Integer.toString(received - more);
         yacyCore.log.logInfo("Received " + received + " URLs from peer " + otherPeerName + " in " + (System.currentTimeMillis() - start) + " ms, blocked " + blocked + " URLs");
         RSSFeed.channels(RSSFeed.INDEXRECEIVE).addMessage(new RSSMessage("Received " + received + " URLs from peer " + otherPeerName + ", blocked " + blocked, "", ""));
         if ((received - more) > 0) yacyCore.log.logSevere("Received " + doublevalues + " double URLs from peer " + otherPeerName);
         result = "ok";
     }

     prop.put("double", doublevalues);
     prop.put("result", result);
     return prop;
 }
}