// CacheResource_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 10.08.2003
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
// javac -classpath .:../classes CacheResource_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CacheResource_p {

    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        final String path = ((post == null) ? "" : post.get("path", ""));

        // we dont need check the path, because we have do that in plasmaSwitchboard.java - Borg-0300
        final File cache = switchboard.getConfigPath(plasmaSwitchboardConstants.HTCACHE_PATH, plasmaSwitchboardConstants.HTCACHE_PATH_DEFAULT);

        final File f = new File(cache, path);
        byte[] resource;

        try {
            resource = serverFileUtils.read(f);
            prop.put("resource", resource);
        } catch (final IOException e) {
            prop.put("resource", new byte[0]);
        }
        return prop;
    }
}
