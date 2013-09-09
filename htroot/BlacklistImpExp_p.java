// BlacklistImpExp_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
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
// javac -classpath .:../classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.ListManager;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.Seed;
import net.yacy.repository.Blacklist;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class BlacklistImpExp_p {
    private final static String DISABLED = "disabled_";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // loading all blacklist files located in the directory
        final List<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);

        String blacklistToUse = null;
        final serverObjects prop = new serverObjects();
        prop.putHTML("blacklistEngine", Blacklist.getEngineInfo());

        // if we have not chosen a blacklist until yet we use the first file
        if (blacklistToUse == null && dirlist != null && !dirlist.isEmpty()) {
            blacklistToUse = dirlist.get(0);
        }

        // List known hosts for BlackList retrieval
        if (sb.peers != null && sb.peers.sizeConnected() > 0) { // no nullpointer error
            int peerCount = 0;
            try {
                final TreeMap<String, String> hostList = new TreeMap<String, String>();
                final Iterator<Seed> e = sb.peers.seedsConnected(true, false, null, (float) 0.0);
                while (e.hasNext()) {
                    final Seed seed = e.next();
                    if (seed != null) hostList.put(seed.get(Seed.NAME, "nameless"),seed.hash);
                }

                String peername;
                while ((peername = hostList.firstKey()) != null) {
                    final String Hash = hostList.get(peername);
                    prop.putHTML(DISABLED + "otherHosts_" + peerCount + "_hash", Hash);
                    prop.putXML(DISABLED + "otherHosts_" + peerCount + "_name", peername);
                    hostList.remove(peername);
                    peerCount++;
                }
            } catch (final Exception e) {
                // Log exception for debug purposes ("catch-all catch")
                ConcurrentLog.logException(e);
            }
            prop.put(DISABLED + "otherHosts", peerCount);
        }

        prop.putXML(DISABLED + "currentBlacklist", (blacklistToUse==null) ? "" : blacklistToUse);
        prop.put("disabled", (blacklistToUse == null) ? "1" : "0");

        int count = 0;
        for (String element : dirlist) {
            prop.putHTML("blackListNames_" + count + "_blackListName", element);
            count++;
        }
        prop.put("blackListNames", count);

        return prop;
    }
}
