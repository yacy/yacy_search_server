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
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.BannerData;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.RasterPlotter;

/** Draw a banner with information about the peer. */
public class Banner {

    public static RasterPlotter respond(
        @SuppressWarnings("unused") final RequestHeader header,
        final serverObjects post,
        final serverSwitch env) throws IOException {
        final Switchboard sb = (Switchboard) env;
        final String pathToImage = "htroot/env/grafics/yacy.png";
        int width = 468;
        int height = 60;
        String bgcolor = "e7effc";
        String textcolor = "000000";
        String bordercolor = "5090d0";

        if (post != null) {
            bgcolor = post.get("bgcolor", bgcolor);
            textcolor = post.get("textcolor", textcolor);
            bordercolor = post.get("bordercolor", bordercolor);
            width = post.getInt("width", width);
            height = post.getInt("heigth", height);
        }

        String name = "";
        long links = 0;
        long words = 0;
        int myppm = 0;
        double myqph = 0;
        String type = "";
        final String network =
                env.getConfig(
                        SwitchboardConstants.NETWORK_NAME,
                        "unspecified").toUpperCase();
        
        // the '+ 1': the own peer is not included in sizeConnected()
        final int peers = sb.peers.sizeConnected() + 1;
        long nlinks = sb.peers.countActiveURL();
        long nwords = sb.peers.countActiveRWI();
        final double nqpm = sb.peers.countActiveQPM();
        long nppm = sb.peers.countActivePPM();
        double nqph = 0;

        final Seed seed = sb.peers.mySeed();
        if (seed != null) {
            name = seed.get(Seed.NAME, "-").toUpperCase();
            links = seed.getLinkCount();
            words = seed.getWordCount();
            myppm = seed.getPPM();
            myqph = 60d * seed.getQPM();

            if (sb.peers.mySeed().isVirgin()) {
                type = "VIRGIN";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if (sb.peers.mySeed().isJunior()) {
                type = "JUNIOR";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if (sb.peers.mySeed().isSenior()) {
                type = "SENIOR";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            } else if (sb.peers.mySeed().isPrincipal()) {
                type = "PRINCIPAL";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            }
        }

        final BannerData data =
                new BannerData(
                        width, height, bgcolor, textcolor, bordercolor, name, links,
                        words, type, myppm, network, peers, nlinks, nwords,
                        nqph, nppm);

        if (!net.yacy.peers.graphics.Banner.logoIsLoaded()) {
            // do not write a cache to disc; keep in RAM
            ImageIO.setUseCache(false);
            final BufferedImage logo = ImageIO.read(new File(pathToImage));
            return net.yacy.peers.graphics.Banner.getBannerPicture(
                data,
                1000,
                logo);
        }

        return net.yacy.peers.graphics.Banner.getBannerPicture(data, 1000);
    }

}
