// Banner.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Marc Nause
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.visualization.RasterPlotter;

import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.graphics.NetworkGraph;

/** draw a banner with information about the peer */
public class Banner {

    public static RasterPlotter respond(final RequestHeader header, final serverObjects post, final serverSwitch env) throws IOException {
        final Switchboard sb = (Switchboard) env;
        final String IMAGE = "htroot/env/grafics/yacy.gif";
        int width = 468;
        int height = 60;
        String bgcolor     = "e7effc";
        String textcolor   = "000000";
        String bordercolor = "5090d0";

        if (post != null) {
            bgcolor = post.get("bgcolor", bgcolor);
            textcolor = post.get("textcolor", textcolor);
            bordercolor = post.get("bordercolor", bordercolor);
            width = post.getInt("width", width);
            height = post.getInt("heigth", height);
        }

        String name    = "";
        long   links   = 0;
        long   words   = 0;
        int    myppm   = 0;
        double myqph   = 0;
        String type    = "";
        final String network = env.getConfig(SwitchboardConstants.NETWORK_NAME, "unspecified").toUpperCase();
        final int    peers   = sb.peers.sizeConnected() + 1; // the '+ 1': the own peer is not included in sizeConnected()
        long   nlinks  = sb.peers.countActiveURL();
        long   nwords  = sb.peers.countActiveRWI();
        final double nqpm    = sb.peers.countActiveQPM();
        long   nppm    = sb.peers.countActivePPM();
        double nqph    = 0;

        final yacySeed seed = sb.peers.mySeed();
        if (seed != null){
            name    = seed.get(yacySeed.NAME, "-").toUpperCase();
            links   = seed.getLinkCount();
            words   = seed.getWordCount();
            myppm   = seed.getPPM();
            myqph   = 60d * seed.getQPM();

            if (sb.peers.mySeed().isVirgin()) {
                type = "VIRGIN";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if(sb.peers.mySeed().isJunior()) {
                type = "JUNIOR";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if(sb.peers.mySeed().isSenior()) {
                type = "SENIOR";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            } else if(sb.peers.mySeed().isPrincipal()) {
                type = "PRINCIPAL";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            }
        }

        if (!NetworkGraph.logoIsLoaded()) {
            ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
            final BufferedImage logo = ImageIO.read(new File(IMAGE));
            return NetworkGraph.getBannerPicture(1000, width, height, bgcolor, textcolor, bordercolor, name, links, words, type, myppm, network, peers, nlinks, nwords, nqph, nppm, logo);
        }
        
        return NetworkGraph.getBannerPicture(1000, width, height, bgcolor, textcolor, bordercolor, name, links, words, type, myppm, network, peers, nlinks, nwords, nqph, nppm);
    }

}
