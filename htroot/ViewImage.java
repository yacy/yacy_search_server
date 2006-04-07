// ViewImage.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created 03.04.2006
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

import java.awt.Container;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.net.MalformedURLException;
import java.net.URL;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ViewImage {

    public static Image respond(httpHeader header, serverObjects post, serverSwitch env) {
        
        plasmaSwitchboard sb = (plasmaSwitchboard)env;     
        
        String urls = post.get("url", "");
        URL url;
        try {
            url = new URL(urls);
        } catch (MalformedURLException e1) {
            return null;
        }
        int width = post.getInt("width", 0);
        int height = post.getInt("height", 0);
        int maxwidth = post.getInt("maxwidth", 0);
        int maxheight = post.getInt("maxheight", 0);
        int timeout = post.getInt("timeout", 5000);
        
        // load image
        byte[] imgb = sb.snippetCache.getResource(url, true, timeout);
        if (imgb == null) return null;
        
        // create image 
        MediaTracker mediaTracker = new MediaTracker(new Container()); 
        Image original = Toolkit.getDefaultToolkit().createImage(imgb); 
        mediaTracker.addImage(original, 0); 
        try {mediaTracker.waitForID(0);} catch (InterruptedException e) {} 
        boolean auth = ((String) header.get("CLIENTIP", "")).equals("localhost") || sb.verifyAuthentication(header, false); // handle access rights
        if ((auth) && ((width == 0) || (height == 0)) && (maxwidth == 0) && (maxheight == 0)) return original;

        // in case of not-authorized access shrink the image to prevent copyright problems
        // so that images are not larger than thumbnails
        if (!auth) {
            maxwidth = 64;
            maxheight = 64;
        }
        
        // calculate width & height from maxwidth & maxheight
        if ((maxwidth != 0) || (maxheight != 0)) {
            int h = original.getHeight(null);
            int w = original.getWidth(null);
            double hs = (w <= maxwidth) ? 1.0 : ((double) w) / ((double) maxwidth);
            double vs = (h <= maxheight) ? 1.0 : ((double) h) / ((double) maxheight);
            double scale = Math.max(hs, vs);
            if ((scale > 1.0) && (!auth)) scale = 0.6; // this is for copyright purpose
            if (scale > 1.0) {
                width = (int) (((double) w) / scale);
                height = (int) (((double) h) / scale);
            } else {
                width = w;
                height = h;
            }
        }
        
        // scale image 
        Image scaled = original.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING); 
        mediaTracker.addImage(scaled, 0); 
        try {mediaTracker.waitForID(0);} catch (InterruptedException e) {} 

        return scaled;
    }
    
}
