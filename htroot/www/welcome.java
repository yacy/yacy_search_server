package www;
// welcome.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
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

// You must compile this file with
// javac -classpath .:../classes index.java
// if the shell's current path is HTROOT

import java.io.File;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class welcome {

    public static serverObjects respond(final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        // update seed info
        sb.updateMySeed();

        prop.putHTML("peername", sb.peers.mySeed().getName());
        prop.putHTML("peerdomain", sb.peers.mySeed().getName().toLowerCase());
        prop.putHTML("peeraddress", sb.peers.mySeed().getPublicAddress());
        prop.put("hostname", env.myPublicIP());
        prop.put("hostip", Domains.dnsResolve(env.myPublicIP()).getHostAddress());
        prop.put("port", env.getConfig("port","8090"));
        prop.put("clientip", header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, ""));

        final String peertype = (sb.peers.mySeed() == null) ? Seed.PEERTYPE_JUNIOR : sb.peers.mySeed().get(Seed.PEERTYPE, Seed.PEERTYPE_VIRGIN);
        final boolean senior = (peertype.equals(Seed.PEERTYPE_SENIOR)) || (peertype.equals(Seed.PEERTYPE_PRINCIPAL));
        if (senior) { prop.put("couldcan", "can"); } else { prop.put("couldcan", "could"); }
        if (senior) { prop.put("seniorinfo", "This peer runs in senior mode which means that your peer can be accessed using the addresses shown above."); } else { prop.putHTML("seniorinfo", "<b>Nobody can access your peer from the outside of your intranet. You must open your firewall and/or set a 'virtual server' in the settings of your router to enable access to the addresses as shown below.</b>"); }
        final File wwwpath = env.getDataPath("htDocsPath", "DATA/HTDOCS");
        prop.putHTML("wwwpath", wwwpath.isAbsolute() ? wwwpath.getAbsolutePath() : "<application_root_path>/" + env.getConfig("htDocsPath", "DATA/HTDOCS"));

        // return rewrite properties
        return prop;
    }
}
