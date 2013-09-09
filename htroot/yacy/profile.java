// profile.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// This file ist contributed by Alexander Schier
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
// javac -classpath .:../../Classes hello.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.Protocol;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class profile {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        if ((post == null) || (env == null)) return prop;
        if (!Protocol.authentifyRequest(post, env)) return prop;

        if ((sb.isRobinsonMode()) &&
           	(!sb.isPublicRobinson()) &&
           	(!sb.isInMyCluster(header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP)))) {
               // if we are a robinson cluster, answer only if this client is known by our network definition
        	prop.put("list", "0");
            return prop;
        }

        final Properties profile = new Properties();
        int count=0;
        String key="";
        String value="";

        FileInputStream fileIn = null;
        try {
            fileIn = new FileInputStream(new File("DATA/SETTINGS/profile.txt"));
            profile.load(fileIn);
        } catch(final IOException e) {
        } finally {
            if (fileIn != null) try { fileIn.close(); fileIn = null; } catch (final Exception e) {}
        }

        final Iterator<Object> it = profile.keySet().iterator();
        while (it.hasNext()) {
            key = (String) it.next();
            value=profile.getProperty(key, "").replaceAll("\r","").replaceAll("\n","\\\\n");
            if( !(key.equals("")) && !(value.equals("")) ){
                prop.put("list_"+count+"_key", key);
                prop.put("list_"+count+"_value", value);
                count++;
            }
        }
        prop.put("list", count);

        // return rewrite properties
        return prop;
    }

}
