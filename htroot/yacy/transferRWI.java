// transferRWI.java
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


import java.util.ArrayList;
import java.util.Iterator;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.HandleSet;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.IndexCell;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.EventChannel;
import net.yacy.peers.Network;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.peers.dht.FlatWordPartitionScheme;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class transferRWI {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) throws InterruptedException {

        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final String contentType = header.getContentType();
        if ((post == null) || (env == null)) {
            logWarning(contentType, "post or env is null!");
            return prop;
        }
        if (!Protocol.authentifyRequest(post, env)) {
            logWarning(contentType, "not authentified");
            return prop;
        }
        if (!post.containsKey("wordc")) {
            logWarning(contentType, "missing wordc");
            return prop;
        }
        if (!post.containsKey("entryc")) {
            logWarning(contentType, "missing entryc");
            return prop;
        }

        // request values
        final String iam      = post.get("iam", "");                      // seed hash of requester
        final String youare   = post.get("youare", "");                   // seed hash of the target peer, needed for network stability
//      final String key      = (String) post.get("key", "");             // transmission key
        final int wordc       = post.getInt("wordc", 0);                  // number of different words
        final int entryc      = post.getInt("entryc", 0);                 // number of entries in indexes
        byte[] indexes        = post.get("indexes", "").getBytes();       // the indexes, as list of word entries
        boolean granted       = sb.getConfigBool("allowReceiveIndex", false);
        final boolean blockBlacklist = sb.getConfigBool("indexReceiveBlockBlacklist", false);
        final long cachelimit = sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 100000);
        final Seed otherPeer = sb.peers.get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));

        // response values
        int pause = 0;
        String result = "ok";
        final StringBuilder unknownURLs = new StringBuilder(6000);

        if ((youare == null) || (!youare.equals(sb.peers.mySeed().hash))) {
        	sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Wrong target. Wanted peer=" + youare + ", iam=" + sb.peers.mySeed().hash);
            result = "wrong_target";
            pause = 0;
        } else if (otherPeer == null) {
            // we dont want to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Not granted. Other Peer is unknown");
            result = "not_granted";
            pause = 60000;
        } else if (!granted) {
            // we dont want to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Granted is false");
            result = "not_granted";
            pause = 60000;
        } else if (sb.isRobinsonMode()) {
            // we dont want to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Not granted. This peer is in robinson mode");
            result = "not_granted";
            pause = 60000;
        } else if (sb.index.termIndex().getBufferSize() > cachelimit) {
            // we are too busy to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". We are too busy (buffersize=" + sb.index.termIndex().getBufferSize() + ").");
            granted = false; // don't accept more words if there are too many words to flush
            result = "busy";
            pause = 60000;
        } else if (otherPeer.getVersion() < 0.75005845 && otherPeer.getVersion() >= 0.75005821) {
        	// version that sends [B@... hashes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Bad version.");
            result = "not_granted";
            pause = 1800000;
        } else {
            // we want and can receive indexes
            // log value status (currently added to find outOfMemory error
            if (sb.getLog().isFine()) sb.getLog().logFine("Processing " + indexes.length + " bytes / " + wordc + " words / " + entryc + " entries from " + otherPeerName);
            final long startProcess = System.currentTimeMillis();

            // decode request
            //System.out.println("STRINGS " + UTF8.String(indexes));
            final Iterator<String> it = FileUtils.strings(indexes);

            // free memory
            indexes = null;

            // now parse the Strings in the value-vector and write index entries
            String estring;
            int p;
            String wordHash;
            byte[] urlHash;
            WordReferenceRow iEntry;
            final HandleSet unknownURL = new RowHandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
            final HandleSet knownURL = new RowHandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
            final ArrayList<String> wordhashes = new ArrayList<String>();
            int received = 0;
            int blocked = 0;
            int receivedURL = 0;
            final IndexCell<WordReference> cell = sb.index.termIndex();
            int count = 0;
            while (it.hasNext()) {
                serverCore.checkInterruption();
                estring = it.next();
                count++;
                if (count > 1000) break; // protection against flooding

                // check if RWI entry is well-formed
                p = estring.indexOf('{',0);
                if (p < 0 || estring.indexOf("x=",0) < 0 || !(estring.indexOf("[B@",0) < 0)) {
                    blocked++;
                    continue;
                }
                wordHash = estring.substring(0, p);
                wordhashes.add(wordHash);
                iEntry = new WordReferenceRow(estring.substring(p));
                urlHash = iEntry.urlhash();

                // block blacklisted entries
                if ((blockBlacklist) && (Switchboard.urlBlacklist.hashInBlacklistedCache(BlacklistType.DHT, urlHash))) {
                    Network.log.logFine("transferRWI: blocked blacklisted URLHash '" + ASCII.String(urlHash) + "' from peer " + otherPeerName);
                    blocked++;
                    continue;
                }

                // check if the entry is in our network domain
                final String urlRejectReason = sb.crawlStacker.urlInAcceptedDomainHash(urlHash);
                if (urlRejectReason != null) {
                    Network.log.logWarning("transferRWI: blocked URL hash '" + ASCII.String(urlHash) + "' (" + urlRejectReason + ") from peer " + otherPeerName + "; peer is suspected to be a spam-peer (or something is wrong)");
                    //if (yacyCore.log.isFine()) yacyCore.log.logFine("transferRWI: blocked URL hash '" + urlHash + "' (" + urlRejectReason + ") from peer " + otherPeerName);
                    blocked++;
                    continue;
                }

                // learn entry
                try {
                    cell.add(wordHash.getBytes(), iEntry);
                } catch (final Exception e) {
                    Log.logException(e);
                }
                serverCore.checkInterruption();

                // check if we need to ask for the corresponding URL
                if (!(knownURL.has(urlHash) || unknownURL.has(urlHash)))  try {
                    if (sb.index.urlMetadata().exists(urlHash)) {
                        knownURL.put(urlHash);
                    } else {
                        unknownURL.put(urlHash);
                    }
                    receivedURL++;
                } catch (final Exception ex) {
                    sb.getLog().logWarning(
                                "transferRWI: DB-Error while trying to determine if URL with hash '" +
                                ASCII.String(urlHash) + "' is known.", ex);
                }
                received++;
            }
            sb.peers.mySeed().incRI(received);

            // finally compose the unknownURL hash list
            final Iterator<byte[]> bit = unknownURL.iterator();
            unknownURLs.ensureCapacity(unknownURL.size() * 25);
            while (bit.hasNext()) {
                unknownURLs.append(",").append(UTF8.String(bit.next()));
            }
            if (unknownURLs.length() > 0) { unknownURLs.delete(0, 1); }
            if (wordhashes.isEmpty() || received == 0) {
                sb.getLog().logInfo("Received 0 RWIs from " + otherPeerName + ", processed in " + (System.currentTimeMillis() - startProcess) + " milliseconds, requesting " + unknownURL.size() + " URLs, blocked " + blocked + " RWIs");
            } else {
                final String firstHash = wordhashes.get(0);
                final String lastHash = wordhashes.get(wordhashes.size() - 1);
                final long avdist = (FlatWordPartitionScheme.std.dhtDistance(firstHash.getBytes(), null, sb.peers.mySeed()) + FlatWordPartitionScheme.std.dhtDistance(lastHash.getBytes(), null, sb.peers.mySeed())) / 2;
                sb.getLog().logInfo("Received " + received + " RWIs, " + wordc + " Words [" + firstHash + " .. " + lastHash + "], processed in " + (System.currentTimeMillis() - startProcess) + " milliseconds, " + avdist + ", blocked " + blocked + ", requesting " + unknownURL.size() + "/" + receivedURL + " URLs from " + otherPeerName);
                EventChannel.channels(EventChannel.DHTRECEIVE).addMessage(new RSSMessage("Received " + received + " RWIs, " + wordc + " Words [" + firstHash + " .. " + lastHash + "], processed in " + (System.currentTimeMillis() - startProcess) + " milliseconds, " + avdist + ", blocked " + blocked + ", requesting " + unknownURL.size() + "/" + receivedURL + " URLs from " + otherPeerName, "", otherPeer.hash));
            }
            result = "ok";

            pause = (int) (sb.index.termIndex().getBufferSize() * 20000 / sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 100000)); // estimation of necessary pause time
        }

        prop.put("unknownURL", unknownURLs.toString());
        prop.put("result", result);
        prop.put("pause", pause);

        // return rewrite properties
        return prop;
    }

    /**
     * @param requestIdentifier
     * @param msg
     */
    private static void logWarning(final String requestIdentifier, final String msg) {
        Log.logWarning("transferRWI", requestIdentifier +" "+ msg);
    }
}
