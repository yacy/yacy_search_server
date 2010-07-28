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

import java.util.TreeMap;

import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.graphics.EncodedImage;
import de.anomic.yacy.graphics.NetworkGraph;

/** draw a picture of the yacy network */
public class NetworkPicture {
    
    private static final TreeMap<Long, EncodedImage> buffer = new TreeMap<Long, EncodedImage>();
    
    public static EncodedImage respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final boolean authorized = sb.adminAuthenticated(header) >= 2;
        
        long timeSeconds = System.currentTimeMillis() / 1000;
        EncodedImage bufferedImage = null;
        synchronized (buffer) {
            bufferedImage = buffer.get(timeSeconds);
        }
        if (bufferedImage != null) return bufferedImage;
        
        int width = 768;
        int height = 576;
        int passiveLimit = 720; // 12 hours
        int potentialLimit = 720;
        int maxCount = 1000;
        String bgcolor = NetworkGraph.COL_BACKGROUND;
        boolean corona = true;
        int coronaangle = 0;
        long communicationTimeout = -1;
        
        if (post != null) {
            width = post.getInt("width", 768);
            height = post.getInt("height", 576);
            passiveLimit = post.getInt("pal", 1440);
            potentialLimit = post.getInt("pol", 1440);
            maxCount = post.getInt("max", 1000);
            corona = post.get("corona", "true").equals("true");
            coronaangle = (corona) ? post.getInt("coronaangle", 0) : -1;
            communicationTimeout = post.getLong("ct", -1);
            bgcolor = post.get("bgcolor", bgcolor);
        }
        
        //too small values lead to an error, too big to huge CPU/memory consumption, resulting in possible DOS.
        if (width < 320 ) width = 320;
        if (width > 1920) width = 1920;
        if (height < 240) height = 240;
        if (height > 1920) height = 1920;
        if (!authorized) {
            width = Math.min(768, width);
            height = Math.min(576, height);
        }
        if (passiveLimit > 1000000) passiveLimit = 1000000;
        if (potentialLimit > 1000000) potentialLimit = 1000000;
        if (maxCount > 10000) maxCount = 10000;
        bufferedImage = new EncodedImage(NetworkGraph.getNetworkPicture(sb.peers, 10000, width, height, passiveLimit, potentialLimit, maxCount, coronaangle, communicationTimeout, env.getConfig(SwitchboardConstants.NETWORK_NAME, "unspecified"), env.getConfig("network.unit.description", "unspecified"), bgcolor).getImage(), "png");
        synchronized (buffer) {
            buffer.put(timeSeconds, bufferedImage);
            while (buffer.size() > 8) buffer.remove(buffer.firstKey());
        }
        return bufferedImage;
    }
    
}
