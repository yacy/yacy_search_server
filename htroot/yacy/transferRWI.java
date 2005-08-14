// transferRWI.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last change: 24.01.2005
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


import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndexEntry;
import de.anomic.plasma.plasmaWordIndexEntryContainer;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyDHTAction;

public class transferRWI {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
        
	if ((post == null) || (env == null)) return prop;

	// request values
	String iam      = (String) post.get("iam", "");      // seed hash of requester
        String youare   = (String) post.get("youare", "");   // seed hash of the target peer, needed for network stability
	String key      = (String) post.get("key", "");      // transmission key
	int wordc       = Integer.parseInt((String) post.get("wordc", ""));    // number of different words
        int entryc      = Integer.parseInt((String) post.get("entryc", ""));   // number of entries in indexes
        byte[] indexes  = ((String) post.get("indexes", "")).getBytes();  // the indexes, as list of word entries
        boolean granted = switchboard.getConfig("allowReceiveIndex", "false").equals("true");
	
        // response values
        String result = "";
        String unknownURLs = "";
        
        if (granted) {
            // decode request
            Vector v = new Vector();
            int s = 0;
            int e;
            while (s < indexes.length) {
                e = s; while (e < indexes.length) if (indexes[e++] < 32) {e--; break;}
                if ((e - s) > 0) v.add(new String(indexes, s, e - s));
                s = e; while (s < indexes.length) if (indexes[s++] >= 32) {s--; break;}
            }
            // the value-vector should now have the same length as entryc
            if (v.size() != entryc) System.out.println("ERROR WITH ENTRY COUNTER: v=" + v.size() + ", entryc=" + entryc);
            
            // now parse the Strings in the value-vector and write index entries
            String estring;
            int p;
            String wordHash;
            String urlHash;
            plasmaWordIndexEntry entry;
            HashSet unknownURL = new HashSet();
            String[] wordhashes = new String[v.size()];
            int received = 0;
            for (int i = 0; i < v.size(); i++) {
                estring = (String) v.elementAt(i);
                p = estring.indexOf("{");
                if (p > 0) {
                    wordHash = estring.substring(0, p);
                    wordhashes[i] = wordHash;
                    entry = new plasmaWordIndexEntry(estring.substring(p));
                    switchboard.wordIndex.addEntries(plasmaWordIndexEntryContainer.instantContainer(wordHash, System.currentTimeMillis(), entry));
                    urlHash = entry.getUrlHash();
                    if ((!(unknownURL.contains(urlHash))) &&
                    (!(switchboard.urlPool.loadedURL.exists(urlHash)))) {
                        unknownURL.add(urlHash);
                    }
                    received++;
                }
            }
            yacyCore.seedDB.mySeed.incRI(received);
            
            // finally compose the unknownURL hash list
            Iterator it = unknownURL.iterator();
            while (it.hasNext()) unknownURLs += "," + (String) it.next();
            if (unknownURLs.length() > 0) unknownURLs = unknownURLs.substring(1);
            if (wordhashes.length == 0)
                switchboard.getLog().logInfo("Received 0 Words from peer " + iam + ", requested " + unknownURL.size() + " URLs");
            else {
                double avdist = (yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, wordhashes[0]) + yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, wordhashes[wordhashes.length - 1])) / 2.0;
                switchboard.getLog().logInfo("Received " + received + " Words [" + wordhashes[0] + " .. " + wordhashes[wordhashes.length - 1] + "]/" + avdist + " from peer " + iam + ", requested " + unknownURL.size() + " URLs");
            }
            result = "ok";
        } else {
            result = "error_not_granted";
        }
        
        prop.put("unknownURL", unknownURLs);
        prop.put("result", result);
        
	// return rewrite properties
	return prop;
    }

}
