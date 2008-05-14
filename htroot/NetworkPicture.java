// NetworkPicture.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

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
