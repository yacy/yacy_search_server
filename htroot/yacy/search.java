// search.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
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
// javac -classpath .:../../Classes search.java
// if the shell's current path is htroot/yacy

import java.util.HashSet;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndexEntry;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class search {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch ss) {
        if (post == null || ss == null) { return null; }

        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) ss;
        serverObjects prop = new serverObjects();
        if (prop == null || sb == null) { return null; }

        //System.out.println("yacy: search received request = " + post.toString());

        final String  oseed  = (String) post.get("myseed", ""); // complete seed of the requesting peer
//      final String  youare = (String) post.get("youare", ""); // seed hash of the target peer, used for testing network stability
        final String  key    = (String) post.get("key", "");    // transmission key for response
        final String  query  = (String) post.get("query", "");  // a string of word hashes
//      final String  fwdep  = (String) post.get("fwdep", "");  // forward depth. if "0" then peer may NOT ask another peer for more results
//      final String  fwden  = (String) post.get("fwden", "");  // forward deny, a list of seed hashes. They may NOT be target of forward hopping
        final long    duetime= Long.parseLong((String) post.get("duetime", "3000"));
        final int     count  = Integer.parseInt((String) post.get("count", "10"));         // maximum number of wanted results
//      final boolean global = ((String) post.get("resource", "global")).equals("global"); // if true, then result may consist of answers from other peers
//      Date remoteTime = yacyCore.parseUniversalDate((String) post.get(yacySeed.MYTIME));        // read remote time
        if (yacyCore.seedDB == null) {
            yacyCore.log.logSevere("yacy.search: seed cache not initialized");
        } else {
            yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(oseed, key), true);
        }

        final HashSet keyhashes = new HashSet(query.length() / plasmaWordIndexEntry.wordHashLength);
        for (int i = 0; i < (query.length() / plasmaWordIndexEntry.wordHashLength); i++) {
            keyhashes.add(query.substring(i * plasmaWordIndexEntry.wordHashLength, (i + 1) * plasmaWordIndexEntry.wordHashLength));
        }
        final long timestamp = System.currentTimeMillis();
        
        plasmaSearchQuery squery = new plasmaSearchQuery(keyhashes, new String[]{plasmaSearchQuery.ORDER_YBR, plasmaSearchQuery.ORDER_DATE, plasmaSearchQuery.ORDER_QUALITY},
                                                        count, duetime, ".*");
        
        prop = sb.searchFromRemote(squery);
        prop.put("searchtime", Long.toString(System.currentTimeMillis() - timestamp));

        final int links = Integer.parseInt(prop.get("linkcount","0"));
        yacyCore.seedDB.mySeed.incSI(links);
        yacyCore.seedDB.mySeed.incSU(links);
        return prop;
    }

}