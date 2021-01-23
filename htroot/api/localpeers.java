// localpeers.java
// ------------
// (C) 2021 by Michael Peter Christen; mc@yacy.net
// first published 23.01.2021 on http://yacy.net
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class localpeers {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        int c = 0;
        for (String urlstub: Switchboard.getSwitchboard().localpeers) {
            prop.putJSON("peers_" + c + "_urlstub", urlstub);
            c++;
        }
        prop.put("peers", c);

        // return rewrite properties
        return prop;
    }

}
