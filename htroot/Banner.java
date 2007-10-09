// Banner.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
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
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageMatrix;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

/** draw a banner with information about the peer */
public class Banner {

    public static ymageMatrix respond(httpHeader header, serverObjects post, serverSwitch env) {

        int width = 468;
        int height = 60;
        String bgcolor = plasmaGrafics.COL_BACKGROUND;
        String textcolor = "ffffff";
        String bordercolor = "";

        if (post != null) {
            bgcolor = post.get("bgcolor", bgcolor);
            textcolor = post.get("textcolor", textcolor);
            bordercolor = post.get("bordercolor", bordercolor);
        }

        String name = "";
        long links = 0;
        long words = 0;
        int myppm = 0;
        double myqph = 0;
        String type = "";
        String network = "";
        long nlinks = 0;
        long nwords = 0;
        double nqpm = 0;
        double nqph = 0;
        long nppm = 0;

        yacySeed seed = yacyCore.seedDB.mySeed();
        if (seed != null){
            name = seed.get(yacySeed.NAME, "-").toUpperCase();
            links = Long.parseLong(seed.get(yacySeed.LCOUNT, "0"));
            words = Long.parseLong(seed.get(yacySeed.ICOUNT, "0"));
            myppm = seed.getPPM();
            myqph = 60d * seed.getQPM();
            network = env.getConfig("network.unit.name", "unspecified").toUpperCase();
            nlinks = yacyCore.seedDB.countActiveURL();
            nwords = yacyCore.seedDB.countActiveRWI();
            nqpm = yacyCore.seedDB.countActiveQPM();
            nppm = yacyCore.seedDB.countActivePPM();

            if (yacyCore.seedDB.mySeed().isVirgin()) {
                type = "VIRGIN";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if(yacyCore.seedDB.mySeed().isJunior()) {
                type = "JUNIOR";
                nqph = Math.round(6000d * nqpm) / 100d;
            } else if(yacyCore.seedDB.mySeed().isSenior()) {
                type = "SENIOR";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            } else if(yacyCore.seedDB.mySeed().isPrincipal()) {
                type = "PRINCIPAL";
                nlinks = nlinks + links;
                nwords = nwords + words;
                nqph = Math.round(6000d * nqpm + 100d * myqph) / 100d;
                nppm = nppm + myppm;
            }
        }

        return plasmaGrafics.getBannerPicture(1000, width, height, bgcolor, textcolor, bordercolor, name, links, words, type, myppm, network, nlinks, nwords, nqph, nppm);
    }

}
