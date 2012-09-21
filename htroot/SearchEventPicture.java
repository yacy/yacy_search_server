// SearchEventPicture.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 24.10.2005
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.graphics.NetworkGraph;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.RasterPlotter;

// draw a picture of the yacy network

public class SearchEventPicture {

    public static RasterPlotter respond(final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final String eventID = header.get("event", SearchEventCache.lastEventID);
        if (eventID == null) return null;
        final RasterPlotter yp = NetworkGraph.getSearchEventPicture(sb.peers, eventID, 0, 0);
        if (yp == null) return new RasterPlotter(1, 1, RasterPlotter.DrawMode.MODE_SUB, "000000"); // empty image

        return yp;
    }

}
