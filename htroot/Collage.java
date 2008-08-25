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

import java.util.Random;

import de.anomic.crawler.ResultImages;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class Collage {
    private static           int fifoMax  = 20;
    
    private static           int fifoPos  = -1;
    private static           int fifoSize = 0;
    private static           long zIndex  = 0;
    
    private static           ResultImages.OriginEntry    origins[]      = new ResultImages.OriginEntry[fifoMax];
    private static           Integer   imgWidth[]    = new Integer[fifoMax];
    private static           Integer   imgHeight[]   = new Integer[fifoMax];
    private static           Integer   imgPosX[]     = new Integer[fifoMax];
    private static           Integer   imgPosY[]     = new Integer[fifoMax];
    private static           long      imgZIndex[]   = new long[fifoMax];
    private static final     Random rand = new Random();
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final serverObjects prop = new serverObjects();
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final boolean authenticated = sb.verifyAuthentication(header, false);
        final ResultImages.OriginEntry nextOrigin = ResultImages.next(!authenticated);
        int posXMax  = 800;
        int posYMax  = 500;
        boolean embed = false;
        
        if (post != null) {
        	embed = post.containsKey("emb");
        	posXMax = post.getInt("width", posXMax);
        	posYMax = post.getInt("height", posYMax);
        	if (post.containsKey("max")) fifoMax = post.getInt("max", fifoMax);
        }
        prop.put("emb", (embed) ? "0" : "1");
        
        if (nextOrigin != null) {
        	System.out.println("NEXTORIGIN=" + nextOrigin.imageEntry.url().toNormalform(true, false));
            if (fifoSize == 0 || origins[fifoPos] != nextOrigin) {
                fifoPos = fifoPos + 1 >= fifoMax ? 0 : fifoPos + 1;
                fifoSize = fifoSize + 1 > fifoMax ? fifoMax : fifoSize + 1;
                origins[fifoPos] = nextOrigin;
                
                final float scale = rand.nextFloat() * 1.5f + 1;
                imgWidth[fifoPos]  = (int) ((nextOrigin.imageEntry.width()) / scale);
                imgHeight[fifoPos] = (int) ((nextOrigin.imageEntry.height()) / scale);

                imgPosX[fifoPos]   = rand.nextInt((imgWidth[fifoPos] == 0) ? posXMax / 2 : Math.max(1, posXMax - imgWidth[fifoPos]));
                imgPosY[fifoPos]   = rand.nextInt((imgHeight[fifoPos] == 0) ? posYMax / 2 : Math.max(1, posYMax - imgHeight[fifoPos]));
                
                imgZIndex[fifoPos] = zIndex;
                zIndex += 1;
            }
        }
        
        if (fifoSize > 0) {
            prop.put("imgurl", "1");        
            int c = 0;
            final int yOffset = embed ? 0 : 70;
            for (int i = 0; i < fifoSize; i++) {
             
                final yacyURL baseURL = origins[i].baseURL;
                final yacyURL imageURL = origins[i].imageEntry.url();
                
                // check if this loads a page from localhost, which must be prevented to protect the server
                // against attacks to the administration interface when localhost access is granted
                if ((serverCore.isLocalhost(baseURL.getHost()) || serverCore.isLocalhost(imageURL.getHost())) &&
                    sb.getConfigBool("adminAccountForLocalhost", false)) continue;
                
                final long z = imgZIndex[i];
                prop.put("imgurl_list_" + c + "_url",
                       "<a href=\"" + baseURL.toNormalform(true, false) + "\">"
                       + "<img src=\"" + imageURL.toNormalform(true, false) + "\" "
                       + "style=\""
                       + ((imgWidth[i] == 0 || imgHeight[i] == 0) ? "" : "width:" + imgWidth[i] + "px;height:" + imgHeight[i] + "px;")
                       + "position:absolute;top:" + (imgPosY[i] + yOffset)
                       + "px;left:" + imgPosX[i]
                       + "px;z-index:" + z + "\" "
                       + "id=\"col" + z + "\" "
                       + "onmouseover=\"raise(" + z + ")\" onmouseout=\"lower(" + z + ")\" "
                       + "title=\"" + baseURL.toNormalform(true, false) + "\">"
                       + "</a><br>");
                c++;
            }
            prop.put("imgurl_list", c);
        } else {
            prop.put("imgurl", "0");
        }
        
        prop.putNum("refresh", Math.max(2, Math.min(5, 500 / (1 + ResultImages.queueSize(!authenticated)))));
        prop.put("emb_privateQueueSize", ResultImages.privateQueueHighSize() + "+" + ResultImages.privateQueueLowSize());
        prop.put("emb_publicQueueSize", ResultImages.publicQueueHighSize()  + "+" + ResultImages.publicQueueLowSize());
        return prop;
    }
}
