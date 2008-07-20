// NetworkPicture.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 08.10.2005
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

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaGrafics;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageMatrix;

/** draw a picture of the yacy network */
public class NetworkPicture {
    
    public static ymageMatrix respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        int width = 768;
        int height = 576;
        int passiveLimit = 300;
        int potentialLimit = 300;
        int maxCount = 1000;
        String bgcolor = plasmaGrafics.COL_BACKGROUND;
        boolean corona = true;
        
        if (post != null) {
            width = post.getInt("width", 768);
            height = post.getInt("height", 576);
            passiveLimit = post.getInt("pal", 300);
            potentialLimit = post.getInt("pol", 300);
            maxCount = post.getInt("max", 1000);
            corona = post.get("corona", "true").equals("true");
            bgcolor = post.get("bgcolor", bgcolor);
        }
        
        //too small values lead to an error, too big to huge CPU/memory consumption, resulting in possible DOS.
        if (width < 320 ) width = 320;
        if (width > 1920) width = 1920;
        if (height < 240) height = 240;
        if (height > 1920) height = 1920;
        if (passiveLimit > 1000000) passiveLimit = 1000000;
        if (potentialLimit > 1000000) potentialLimit = 1000000;
        if (maxCount > 1000) maxCount = 1000;
        return plasmaGrafics.getNetworkPicture(sb.webIndex.seedDB, 10000, width, height, passiveLimit, potentialLimit, maxCount, corona, env.getConfig("network.unit.name", "unspecified"), env.getConfig("network.unit.description", "unspecified"), bgcolor);
    }
    
}
