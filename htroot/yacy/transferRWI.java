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


import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.text.ReferenceRow;
import de.anomic.kelondro.text.Blacklist;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.Log;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.dht.FlatWordPartitionScheme;

public final class transferRWI {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) throws InterruptedException {
        
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        final String contentType = header.getContentType();
        if ((post == null) || (env == null)) {
            logWarning(contentType, "post or env is null!");
            return prop;
        }
        if (!yacyNetwork.authentifyRequest(post, env)) {
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
        boolean granted       = sb.getConfig("allowReceiveIndex", "false").equals("true");
        final boolean blockBlacklist = sb.getConfig("indexReceiveBlockBlacklist", "false").equals("true");
        final long cachelimit = sb.getConfigLong(plasmaSwitchboardConstants.WORDCACHE_MAX_COUNT, 100000);
        final yacySeed otherPeer = sb.webIndex.peers().get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));                
        
        // response values
        int pause = 0;
        String result = "ok";
        final StringBuilder unknownURLs = new StringBuilder();
        
        if ((youare == null) || (!youare.equals(sb.webIndex.peers().mySeed().hash))) {
        	sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Wrong target. Wanted peer=" + youare + ", iam=" + sb.webIndex.peers().mySeed().hash);
            result = "wrong_target";
            pause = 0;
        } else if ((!granted) || (sb.isRobinsonMode())) {
            // we dont want to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Not granted.");
            result = "not_granted";
            pause = 0;
        } else if (sb.webIndex.index().getBufferSize() > cachelimit) {
            // we are too busy to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". We are too busy (buffersize=" + sb.webIndex.index().getBufferSize() + ").");
            granted = false; // don't accept more words if there are too many words to flush
            result = "busy";
            pause = 60000;
        } else {
            // we want and can receive indexes
            // log value status (currently added to find outOfMemory error
            if (sb.getLog().isFine()) sb.getLog().logFine("Processing " + indexes.length + " bytes / " + wordc + " words / " + entryc + " entries from " + otherPeerName);
            final long startProcess = System.currentTimeMillis();

            // decode request
            final List<String> v = FileUtils.strings(indexes, null);

            // free memory
            indexes = null;
            
            // the value-vector should now have the same length as entryc
            if (v.size() != entryc) sb.getLog().logSevere("ERROR WITH ENTRY COUNTER: v=" + v.size() + ", entryc=" + entryc);

            // now parse the Strings in the value-vector and write index entries
            String estring;
            int p;
            String wordHash;
            String urlHash;
            ReferenceRow iEntry;
            final HashSet<String> unknownURL = new HashSet<String>();
            final HashSet<String> knownURL = new HashSet<String>();
            final String[] wordhashes = new String[v.size()];
            int received = 0;
            int blocked = 0;
            int receivedURL = 0;
            final Iterator<String> i = v.iterator();
            while (i.hasNext()) {
                serverCore.checkInterruption();
                estring = i.next();
                
                // check if RWI entry is well-formed
                p = estring.indexOf("{");
                if ((p < 0) || (estring.indexOf("x=") < 0)) {
                    blocked++;
                    continue;
                }
                wordHash = estring.substring(0, p);
                wordhashes[received] = wordHash;
                iEntry = new ReferenceRow(estring.substring(p));
                urlHash = iEntry.urlHash();
                
                // block blacklisted entries
                if ((blockBlacklist) && (plasmaSwitchboard.urlBlacklist.hashInBlacklistedCache(Blacklist.BLACKLIST_DHT, urlHash))) {
                    if (yacyCore.log.isFine()) yacyCore.log.logFine("transferRWI: blocked blacklisted URLHash '" + urlHash + "' from peer " + otherPeerName);
                    blocked++;
                    continue;
                }
                
                // learn entry
                try {
                    sb.webIndex.index().add(wordHash, iEntry);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                serverCore.checkInterruption();

                // check if we need to ask for the corresponding URL
                if (!(knownURL.contains(urlHash)||unknownURL.contains(urlHash)))  try {
                    if (sb.webIndex.metadata().exists(urlHash)) {
                        knownURL.add(urlHash);
                    } else {
                        unknownURL.add(urlHash);
                    }
                    receivedURL++;
                } catch (final Exception ex) {
                    sb.getLog().logWarning(
                                "transferRWI: DB-Error while trying to determine if URL with hash '" +
                                urlHash + "' is known.", ex);
                }
                received++;
            }
            sb.webIndex.peers().mySeed().incRI(received);

            // finally compose the unknownURL hash list
            final Iterator<String> it = unknownURL.iterator();  
            unknownURLs.ensureCapacity(unknownURL.size() * 25);
            while (it.hasNext()) {
                unknownURLs.append(",").append(it.next());
            }
            if (unknownURLs.length() > 0) { unknownURLs.delete(0, 1); }
            if ((wordhashes.length == 0) || (received == 0)) {
                sb.getLog().logInfo("Received 0 RWIs from " + otherPeerName + ", processed in " + (System.currentTimeMillis() - startProcess) + " milliseconds, requesting " + unknownURL.size() + " URLs, blocked " + blocked + " RWIs");
            } else {
                final long avdist = (FlatWordPartitionScheme.std.dhtDistance(wordhashes[0], null, sb.webIndex.peers().mySeed()) + FlatWordPartitionScheme.std.dhtDistance(wordhashes[received - 1], null, sb.webIndex.peers().mySeed())) / 2;
                sb.getLog().logInfo("Received " + received + " Entries " + wordc + " Words [" + wordhashes[0] + " .. " + wordhashes[received - 1] + "]/" + avdist + " from " + otherPeerName + ", processed in " + (System.currentTimeMillis() - startProcess) + " milliseconds, requesting " + unknownURL.size() + "/" + receivedURL + " URLs, blocked " + blocked + " RWIs");
                RSSFeed.channels(RSSFeed.INDEXRECEIVE).addMessage(new RSSMessage("Received " + received + " RWIs [" + wordhashes[0] + " .. " + wordhashes[received - 1] + "]/" + avdist + " from " + otherPeerName + ", requesting " + unknownURL.size() + " URLs, blocked " + blocked, "", ""));
            }
            result = "ok";
            
            pause = (int) (sb.webIndex.index().getBufferSize() * 20000 / sb.getConfigLong(plasmaSwitchboardConstants.WORDCACHE_MAX_COUNT, 100000)); // estimation of necessary pause time
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
