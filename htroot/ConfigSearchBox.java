// ConfigSearchBox.java
// -----------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 25.09.2009 on http://yacy.net
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigSearchBox {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        String myaddress = sb.peers.mySeed().getPublicAddress();
        if (myaddress == null) myaddress = "localhost:" + sb.getConfig("port", "8090");
        prop.put("myaddress", myaddress);
        return prop;
    }

}
