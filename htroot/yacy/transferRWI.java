// transferRWI.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
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
// javac -classpath .:../classes transferRWI.java


import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import de.anomic.http.httpHeader;
import de.anomic.index.indexEntry;
import de.anomic.index.indexURLEntryNew;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyDHTAction;

public final class transferRWI {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch ss) throws InterruptedException {
        if (post == null || ss == null) { return null; }

        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) ss;
        final serverObjects prop = new serverObjects();
        if (prop == null || sb == null) { return null; }

        // request values
        final String iam      = post.get("iam", "");                      // seed hash of requester
//      final String youare   = (String) post.get("youare", "");          // seed hash of the target peer, needed for network stability
//      final String key      = (String) post.get("key", "");             // transmission key
        final int wordc       = Integer.parseInt(post.get("wordc", ""));  // number of different words
        final int entryc      = Integer.parseInt(post.get("entryc", "")); // number of entries in indexes
        byte[] indexes        = post.get("indexes", "").getBytes();       // the indexes, as list of word entries
        boolean granted       = sb.getConfig("allowReceiveIndex", "false").equals("true");
        boolean blockBlacklist = sb.getConfig("indexReceiveBlockBlacklist", "false").equals("true");
        boolean checkLimit    = sb.getConfigBool("indexDistribution.dhtReceiptLimitEnabled", true);
        final long cachelimit = sb.getConfigLong("indexDistribution.dhtReceiptLimit", 1000);
        final yacySeed otherPeer = yacyCore.seedDB.get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));                
        
        // response values
        String       result      = "ok";
        StringBuffer unknownURLs = new StringBuffer();
        int          pause       = 0;

        if (!granted) {
            // we dont want to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". Not granted.");
            result = "not_granted";
            pause = 0;
        } else if (checkLimit && sb.wordIndex.kSize() > cachelimit) {
            // we are too busy to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". We are too busy (buffersize=" + sb.wordIndex.kSize() + ").");
            granted = false; // don't accept more words if there are too many words to flush
            result = "busy";
            pause = 60000;
        } else if (checkLimit && sb.wordIndex.wSize() > (sb.wordIndex.getMaxWordCount() + cachelimit)) {
            // we are too busy flushing the ramCache to receive indexes
            sb.getLog().logInfo("Rejecting RWIs from peer " + otherPeerName + ". We are too busy (wordcachesize=" + sb.wordIndex.wSize() + ").");
            granted = false; // don't accept more words if there are too many words to flush
            result = "busy";
            pause = 300000;
        } else {
            // we want and can receive indexes
            // log value status (currently added to find outOfMemory error
            sb.getLog().logFine("Processing " + indexes.length + " bytes / " + wordc + " words / " + entryc + " entries from " + otherPeerName);
            final long startProcess = System.currentTimeMillis();

            // decode request
            final LinkedList v = new LinkedList();
            int s = 0;
            int e;
            while (s < indexes.length) {
                e = s; while (e < indexes.length) if (indexes[e++] < 32) {e--; break;}
                if ((e - s) > 0) v.add(new String(indexes, s, e - s));
                s = e; while (s < indexes.length) if (indexes[s++] >= 32) {s--; break;}
            }
            // free memory
            indexes = null;
            
            // the value-vector should now have the same length as entryc
            if (v.size() != entryc) sb.getLog().logSevere("ERROR WITH ENTRY COUNTER: v=" + v.size() + ", entryc=" + entryc);

            // now parse the Strings in the value-vector and write index entries
            String estring;
            int p;
            String wordHash;
            String urlHash;
            indexEntry iEntry;
            int wordhashesSize = v.size();
            final HashSet unknownURL = new HashSet();
            String[] wordhashes = new String[v.size()];
            int received = 0;
            for (int i = 0; i < wordhashesSize; i++) {
                serverCore.checkInterruption();
                
                estring = (String) v.removeFirst();
                p = estring.indexOf("{");
                if (p > 0) {
                    wordHash = estring.substring(0, p);
                    wordhashes[received] = wordHash;
                    iEntry = new indexURLEntryNew(estring.substring(p));
                    sb.wordIndex.addEntry(wordHash, iEntry, System.currentTimeMillis(), true);
                    serverCore.checkInterruption();
                    
                    urlHash = iEntry.urlHash();
                    try {
                        if ((!(unknownURL.contains(urlHash))) &&
                            (!(sb.urlPool.loadedURL.exists(urlHash)))) {
                            if ((blockBlacklist) && (plasmaSwitchboard.urlBlacklist.hashInBlacklistedCache(urlHash))) {
                                int deleted = sb.wordIndex.tryRemoveURLs(urlHash);
                                yacyCore.log.logFine("transferRWI: blocked blacklisted URLHash '" + urlHash + "' from peer " + otherPeerName + "; deleted " + deleted + " URL entries from RWIs");
                                //TODO: set to logFine if it works.
                            }
                            else {
                                unknownURL.add(urlHash);                                
                            }
                        }
                    } catch (Exception ex) {
                        sb.getLog().logWarning(
                          "transferRWI: DB-Error while trying to determine if URL with hash '" +
                          urlHash + "' is known.", ex);
                        unknownURL.add(urlHash);
                    }
                    received++;
                }
            }
            yacyCore.seedDB.mySeed.incRI(received);

            // finally compose the unknownURL hash list
            final Iterator it = unknownURL.iterator();  
            unknownURLs.ensureCapacity(unknownURL.size()*13);
            while (it.hasNext()) {
                unknownURLs.append(",").append((String) it.next());
            }
            if (unknownURLs.length() > 0) { unknownURLs.delete(0, 1); }
            if (wordhashes.length == 0) {
                sb.getLog().logInfo("Received 0 RWIs from " + otherPeerName + ", processed in " + (System.currentTimeMillis() - startProcess) + " milliseconds, requesting " + unknownURL.size() + " URLs");
            } else {
                final double avdist = (yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, wordhashes[0]) + yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, wordhashes[received - 1])) / 2.0;
                sb.getLog().logInfo("Received " + received + " Words [" + wordhashes[0] + " .. " + wordhashes[received - 1] + "]/" + avdist + " from " + otherPeerName + ", processed in " + (System.currentTimeMillis() - startProcess) + " milliseconds, requesting " + unknownURL.size() + " URLs");
            }
            result = "ok";
            
            if (checkLimit) {
                pause = (sb.wordIndex.kSize() < 500) ? 0 : 60 * sb.wordIndex.kSize(); // estimation of necessary pause time
            }
        }

        prop.put("unknownURL", unknownURLs.toString());
        prop.put("result", result);
        prop.put("pause", Integer.toString(pause));

        // return rewrite properties
        return prop;
    }
}
