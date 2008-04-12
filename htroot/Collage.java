// Collage.java 
// -----------------------
// part of YaCy
// (C) by Detlef Reichl; detlef!reichl()gmx!org
// Pforzheim, Germany, 2008
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

import java.util.Random;

import de.anomic.data.collageQueue;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Collage {
    private static final     int fifoMax  = 20;
    private static final     int posXMax  = 800;
    private static final     int posYMax  = 500;
    
    private static           int fifoPos  = -1;
    private static           int fifoSize = 0;
    private static           long zIndex  = 0;
    
    private static           collageQueue.ImageOriginEntry    origins[]      = new collageQueue.ImageOriginEntry[fifoMax];
    private static           Integer   imgWidth[]    = new Integer[fifoMax];
    private static           Integer   imgHeight[]   = new Integer[fifoMax];
    private static           Integer   imgPosX[]     = new Integer[fifoMax];
    private static           Integer   imgPosY[]     = new Integer[fifoMax];
    private static           long      imgZIndex[]   = new long[fifoMax];

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        final serverObjects prop = new serverObjects();
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        Random rand = new Random();
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        collageQueue.ImageOriginEntry nextOrigin = collageQueue.next(!authenticated);
        
        if (nextOrigin != null) {
            if (fifoSize == 0 || origins[fifoPos] != nextOrigin) {
                fifoPos = fifoPos + 1 == fifoMax ? 0 : fifoPos + 1;
                fifoSize = fifoSize + 1 > fifoMax ? fifoMax : fifoSize + 1;
                origins[fifoPos] = nextOrigin;
                
                float scale = rand.nextFloat() * 1.5f + 1;
                imgWidth[fifoPos]  = (int) (((float)nextOrigin.imageEntry.width()) / scale);
                imgHeight[fifoPos] = (int) (((float)nextOrigin.imageEntry.height()) / scale);

                imgPosX[fifoPos]   = rand.nextInt(posXMax);
                imgPosY[fifoPos]   = rand.nextInt(posYMax);
                
                imgZIndex[fifoPos] = zIndex;
                zIndex += 1;
            }
        }
        
        
        if (fifoSize > 0) {
            prop.put("imgurl", "1");        
        
            for (int i = 0; i < fifoSize; i++)
              prop.put("imgurl_list_" + i + "_url",
                       "<a href=\"" + origins[i].baseURL.toNormalform(true, false) + "\">"
                       + "<img src=\"" + origins[i].imageEntry.url().toNormalform(true, false) + "\" style=\"width:" + imgWidth[i]
                       + "px;height:" + imgHeight[i]
                       + "px;position:absolute;top:" + imgPosY[i]
                       + "px;left:" + imgPosX[i]
                       + "px;z-index:" + imgZIndex[i] + "\" title=\"" + origins[i].baseURL.toNormalform(true, false) + "\">"
                       + "</a><br>");

            prop.put("imgurl_list", fifoSize);
        } else {
            prop.put("imgurl", "0");
        }
        
        return prop;
    }
}