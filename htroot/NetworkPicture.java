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

import java.util.concurrent.Semaphore;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.peers.graphics.NetworkGraph;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/** draw a picture of the yacy network */
public class NetworkPicture
{

    private static final ConcurrentLog log = new ConcurrentLog("NetworkPicture");
    private static final Semaphore sync = new Semaphore(1, true);
    private static EncodedImage buffer = null;
    private static long lastAccessSeconds = 0;

    public static EncodedImage respond(
        final RequestHeader header,
        final serverObjects post,
        final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final boolean authorized = sb.adminAuthenticated(header) >= 2;

        final long timeSeconds = System.currentTimeMillis() / 1000;
        if (buffer != null && !authorized && timeSeconds - lastAccessSeconds < 2) {
            if (log.isFine()) log.fine("cache hit (1); authorized = "
                + authorized
                + ", timeSeconds - lastAccessSeconds = "
                + (timeSeconds - lastAccessSeconds));
            return buffer;
        }

        if ( buffer != null && sync.availablePermits() == 0 ) {
            return buffer;
        }
        sync.acquireUninterruptibly();

        if (buffer != null && !authorized && timeSeconds - lastAccessSeconds < 2) {
            if (log.isFine()) log.fine("cache hit (2); authorized = "
                + authorized
                + ", timeSeconds - lastAccessSeconds = "
                + (timeSeconds - lastAccessSeconds));
            sync.release();
            return buffer;
        }

        int width = 1280; // 640x480 = VGA, 768x576 = SD/4:3, 1024x576 =SD/16:9 1280x720 = HD/16:9, 1920x1080 = FULL HD/16:9
        int height = 720;
        int passiveLimit = 1440; // minutes; 1440 = 1 day; 720 = 12 hours; 1440 = 24 hours, 10080 = 1 week;
        int potentialLimit = 1440;
        int maxCount = 9000;
        String bgcolor = NetworkGraph.COL_BACKGROUND;
        boolean corona = true;
        int coronaangle = 0;
        long communicationTimeout = -1;
        int cyc = 0;

        if ( post != null ) {
            width = post.getInt("width", width);
            height = post.getInt("height", height);
            passiveLimit = post.getInt("pal", passiveLimit);
            potentialLimit = post.getInt("pol", potentialLimit);
            maxCount = post.getInt("max", maxCount);
            corona = post.getBoolean("corona") || post.containsKey("coronaangle");
            coronaangle = (corona) ? post.getInt("coronaangle", 0) : -1;
            communicationTimeout = post.getLong("ct", -1);
            bgcolor = post.get("bgcolor", bgcolor);
            cyc = post.getInt("cyc", 0);
        }

        //too small values lead to an error, too big to huge CPU/memory consumption, resulting in possible DOS.
        if ( width < 320 ) {
            width = 320;
        }
        if ( width > 1920 ) {
            width = 1920;
        }
        if ( height < 240 ) {
            height = 240;
        }
        if ( height > 1280 ) {
            height = 1280;
        }
        if ( !authorized ) {
            width = Math.min(1280, width);
            height = Math.min(1280, height);
        }
        if ( passiveLimit > 1000000 ) {
            passiveLimit = 1000000;
        }
        if ( potentialLimit > 1000000 ) {
            potentialLimit = 1000000;
        }
        if ( maxCount > 10000 ) {
            maxCount = 10000;
        }
        buffer =
            new EncodedImage(NetworkGraph.getNetworkPicture(
                sb.peers,
                width,
                height,
                passiveLimit,
                potentialLimit,
                maxCount,
                coronaangle,
                communicationTimeout,
                env.getConfig(SwitchboardConstants.NETWORK_NAME, "unspecified"),
                env.getConfig("network.unit.description", "unspecified"),
                bgcolor,
                cyc), "png");
        lastAccessSeconds = System.currentTimeMillis() / 1000;

        sync.release();
        return buffer;
    }

}
