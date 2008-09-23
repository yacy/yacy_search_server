// Banner.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Marc Nause
//
// $LastChangedDate: 2007-10-09 23:07:00 +0200 (Mi, 09 Okt 2007) $
// $LastChangedRevision: 4154 $
// $LastChangedBy: low012 $
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

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaGrafics;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;
import de.anomic.ymage.ymageMatrix;

/** draw a banner with information about the peer */
public class Banner {

    public static ymageMatrix respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) throws IOException {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final String IMAGE = "htroot/env/grafics/yacy.gif";
        int width = 468;
        int height = 60;
        String bgcolor     = "ddeeee";
        String textcolor   = "000000";
        String bordercolor = "aaaaaa";

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
        final String network = env.getConfig(plasmaSwitchboardConstants.NETWORK_NAME, "unspecified").toUpperCase();
        final int    peers   = sb.webIndex.seedDB.sizeConnected() + 1; // the '+ 1': the own peer is not included in sizeConnected()
        long   nlinks  = sb.webIndex.seedDB.countActiveURL();
        long   nwords  = sb.webIndex.seedDB.countActiveRWI();
        final double nqpm    = sb.webIndex.seedDB.countActiveQPM();
        long   nppm    = sb.webIndex.seedDB.countActivePPM();
        double nqph    = 0;

        final yacySeed seed = sb.webIndex.seedDB.mySeed();
        if (seed != null){
            name    = seed.get(yacySeed.NAME, "-").toUpperCase();
            links   = Long.parseLong(seed.get(yacySeed.LCOUNT, "0"));
            words   = Long.parseLong(seed.get(yacySeed.ICOUNT, "0"));
            myppm   = seed.getPPM();
            myqph   = 60d * seed.getQPM();

            if (sb.webIndex.seedDB.mySeed().isVirgin()) {
                type = "VIRGIN";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if(sb.webIndex.seedDB.mySeed().isJunior()) {
                type = "JUNIOR";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if(sb.webIndex.seedDB.mySeed().isSenior()) {
                type = "SENIOR";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            } else if(sb.webIndex.seedDB.mySeed().isPrincipal()) {
                type = "PRINCIPAL";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            }
        }

        if (!plasmaGrafics.logoIsLoaded()) {
            final BufferedImage logo = ImageIO.read(new File(IMAGE));
            return plasmaGrafics.getBannerPicture(1000, width, height, bgcolor, textcolor, bordercolor, name, links, words, type, myppm, network, peers, nlinks, nwords, nqph, nppm, logo);
        }
        
        return plasmaGrafics.getBannerPicture(1000, width, height, bgcolor, textcolor, bordercolor, name, links, words, type, myppm, network, peers, nlinks, nwords, nqph, nppm);
    }

}
