// list.java
// -----------------------
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
//
// This File is contributed by Alexander Schier
//
// $LastChangedDate: 2008-02-02 23:53:39 +0000 (Sa, 02 Feb 2008) $
// $LastChangedRevision: 4430 $
// $LastChangedBy: orbiter $
//
// LICENSE
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


import java.io.File;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.Seed;
import net.yacy.peers.Protocol;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public final class list {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        if (post == null || env == null) throw new NullPointerException("post: " + post + ", sb: " + env);
        final Switchboard sb = (Switchboard) env;

        final String blackListName = post.get("listname", "");

        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        if (!Protocol.authentifyRequest(post, env)) return prop;

        final String col = post.get("col", "");
        final File listsPath = env.getDataPath(SwitchboardConstants.LISTS_PATH, SwitchboardConstants.LISTS_PATH_DEFAULT);

        String otherPeerName = null;
        if (post.containsKey("iam")) {
            final Seed bla = sb.peers.get(post.get("iam", ""));
            if (bla != null) otherPeerName = bla.getName();
        }
        if (otherPeerName == null) otherPeerName = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP);

        if ((sb.isRobinsonMode()) && (!sb.isInMyCluster(otherPeerName))) {
            // if we are a robinson cluster, answer only if this client is known by our network definition
            return null;
        }

        if (col.equals("black")) {
            final StringBuilder out = new StringBuilder(10000);

            final String filenames=env.getConfig("BlackLists.Shared", "");
            final String[] filenamesarray = filenames.split(",");

            if (filenamesarray.length > 0){
                for (final String filename : filenamesarray) {
                    if (blackListName.equals("") || filename.equals(blackListName)) {
                        final File fileObj = new File(listsPath,filename);
                        out.append(FileUtils.getListString(fileObj, false)).append(serverCore.CRLF_STRING);
                    }
                }
            }

            prop.put("list",out.toString());
        } else {
            prop.put("list","");
        }

        return prop;
    }
}