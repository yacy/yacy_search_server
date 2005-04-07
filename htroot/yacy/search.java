// search.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 02.06.2003
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


// you must compile this file with
// javac -classpath .:../../Classes search.java
// if the shell's current path is htroot/yacy

import java.util.*;
import de.anomic.tools.*;
import de.anomic.server.*;
import de.anomic.plasma.*;
import de.anomic.yacy.*;
import de.anomic.http.*;

public class search {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();

	// be save
	if ((post == null) || (env == null)) return prop;

	//System.out.println("yacy: search received request = " + post.toString());

	String  oseed  = (String) post.get("myseed", ""); // complete seed of the requesting peer
	String  youare = (String) post.get("youare", ""); // seed hash of the target peer, used for testing network stability
	String  key    = (String) post.get("key", "");    // transmission key for response
	String  query  = (String) post.get("query", "");  // a string of word hashes
	String  fwdep  = (String) post.get("fwdep", "");  // forward depth. if "0" then peer may NOT ask another peer for more results
	String  fwden  = (String) post.get("fwden", "");  // forward deny, a list of seed hashes. They may NOT be target of forward hopping
        long    duetime= Long.parseLong((String) post.get("duetime", "3000"));
	int     count  = Integer.parseInt((String) post.get("count", "10"));     // maximum number of wanted results
	boolean global = ((String) post.get("resource", "global")).equals("global"); // if true, then result may consist of answers from other peers
        Date remoteTime = yacyCore.parseUniversalDate((String) post.get("mytime")); // read remote time
        if (yacyCore.seedDB == null) {
            yacyCore.log.logError("yacy.search: seed cache not initialized");
        } else {
            yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(oseed, key, remoteTime), true);
        }

        HashSet keyhashes = new HashSet();
        for (int i = 0; i < (query.length() / plasmaWordIndexEntry.wordHashLength); i++) {
            keyhashes.add(query.substring(i * plasmaWordIndexEntry.wordHashLength, (i + 1) * plasmaWordIndexEntry.wordHashLength));
        }
        long timestamp = System.currentTimeMillis();
	prop = switchboard.searchFromRemote(keyhashes, count, global, duetime);
        prop.put("searchtime", "" + (System.currentTimeMillis() - timestamp));
        
        int links = Integer.parseInt(prop.get("linkcount","0"));
        yacyCore.seedDB.mySeed.incSI(links);
        yacyCore.seedDB.mySeed.incSU(links);
	return prop;
    }

}
