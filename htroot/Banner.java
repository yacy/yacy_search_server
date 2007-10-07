// Banner.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Marc Nause
//
// $LastChangedDate: 2007-10-07 $
// $LastChangedRevision: $
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

        yacySeed seed = yacyCore.seedDB.mySeed();
        String name = seed.get(yacySeed.NAME, "-");
        name = addTrailingBlanks(name,19).toUpperCase();
        String links = seed.get(yacySeed.LCOUNT, "0");
        links = addDots(links);
        links = addTrailingBlanks(links,19);
        String words = seed.get(yacySeed.ICOUNT, "0");
        words = addDots(words);
        words = addTrailingBlanks(words,19);

        String type = "";
        if (yacyCore.seedDB.mySeed().isVirgin()) {
            type = "VIRGIN";
        } else if(yacyCore.seedDB.mySeed().isJunior()) {
            type = "JUNIOR";
        } else if(yacyCore.seedDB.mySeed().isSenior()) {
            type = "SENIOR";
        } else if(yacyCore.seedDB.mySeed().isPrincipal()) {
            type = "PRINCIPAL";
        }
        type = addTrailingBlanks(type,19);
        String ppm = seed.getPPM() + " PAGES/MINUTE";
        ppm = addTrailingBlanks(ppm,19);

        String network = env.getConfig("network.unit.name", "unspecified").toUpperCase();
        network = addTrailingBlanks(network,19);
        String nlinks = yacyCore.seedDB.countActiveURL()+"";
        nlinks = addDots(nlinks);
        nlinks = addTrailingBlanks(nlinks,19);
        String nwords = yacyCore.seedDB.countActiveRWI()+"";
        nwords = addDots(nwords);
        nwords = addTrailingBlanks(nwords,19);
        String nqph = yacyCore.seedDB.countActiveQPM() + " QUERIES/MINUTE";
        nqph = addTrailingBlanks(nqph,19);
        String nppm = yacyCore.seedDB.countActivePPM() + " PAGES/MINUTE";
        nppm = addTrailingBlanks(nppm,19);

        return plasmaGrafics.getBannerPicture(width, height, bgcolor, textcolor, bordercolor, name, links, words, type, ppm, network, nlinks, nwords, nqph, nppm);
    }

    private static String addDots(String word) {
        String tmp = "";
        int len = word.length();
        while(len > 3) {
            if(tmp.equals("")) {
                tmp = word.substring(len-3,len);
            } else {
                tmp = word.substring(len-3,len) + "." + tmp;
            }
            word = word.substring(0,len-3);
            len = word.length();
        }
        word = word + "." + tmp;
        return word;
    }

    private static String addTrailingBlanks(String word, int length) {
        if (length > word.length()) {
            String blanks = "";
            length = length - word.length();
            int i = 0;
            while(i++ < length) {
                blanks += " ";
            }
            word = blanks + word;
        }
        return word;
    }

}
