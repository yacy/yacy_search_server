// ConfigProperties_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
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
// javac -classpath .:../classes Config_p.java
// if the shell's current path is HTROOT

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigProperties_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        String key = "";
        String value = "";

        //change a key
        if (post != null && post.containsKey("key") && post.containsKey("value")) {
            key = post.get("key").trim();
            value = post.get("value").trim();
            if (key != null && !key.isEmpty()) {
                env.setConfig(key, value);
            }
        }
        prop.putHTML("keyPosted", key);
        prop.putHTML("valuePosted", value);

        Iterator<String> keys = env.configKeys();

        final List<String> list = new ArrayList<String>(250);

        while (keys.hasNext()) {
            list.add(keys.next());
        }

        Collections.sort(list);

        int count = 0;
        keys = list.iterator();
        while (keys.hasNext()) {
            key = keys.next();

            // only display lines if they are no commment
            if (!key.startsWith("#")) {
                prop.putHTML("options_" + count + "_key", key);
                prop.putHTML("options_" + count + "_value", env.getConfig(key, "ERROR"));
                count++;
            }
        }

        prop.put("options", count);
        return prop;
    }

}
