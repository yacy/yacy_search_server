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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;
import de.anomic.ymage.ymageImageParser;

public class ViewImage {

    private static HashMap iconcache = new HashMap();
    private static String defaulticon = "htroot/env/grafics/dfltfvcn.ico";
    
    public static Image respond(httpHeader header, serverObjects post, serverSwitch env) {
        
        plasmaSwitchboard sb = (plasmaSwitchboard)env;
        
        // the url to the image can be either submitted with an url in clear text, or using a license key
        // if the url is given as clear text, the user must be authorized as admin
        // the license can be used also from non-authorized users
        
        String urlString = post.get("url", "");
        String urlLicense = post.get("code", "");
        boolean auth = ((String) header.get("CLIENTIP", "")).equals("localhost") || sb.verifyAuthentication(header, true); // handle access rights
        
        yacyURL url = null;
        if ((urlString.length() > 0) && (auth)) try {
            url = new yacyURL(urlString, null);
        } catch (MalformedURLException e1) {
            url = null;
        }
        
        if ((url == null) && (urlLicense.length() > 0)) {
            url = sb.licensedURLs.releaseLicense(urlLicense);
            urlString = (url == null) ? null : url.toNormalform(true, true);
        }
        
        if (url == null) return null;
        System.out.println("loading image from " + url.toString());
        
        int width = post.getInt("width", 0);
        int height = post.getInt("height", 0);
        int maxwidth = post.getInt("maxwidth", 0);
        int maxheight = post.getInt("maxheight", 0);
        int timeout = post.getInt("timeout", 5000);
        
        // getting the image as stream
        Image scaled = (Image) iconcache.get(urlString);
        if (scaled == null) {
            Object[] resource = plasmaSnippetCache.getResource(url, true, timeout, false);
            byte[] imgb = null;
            if (resource == null) {
                if (urlString.endsWith(".ico")) {
                    // load default favicon dfltfvcn.ico
                    try {
                        imgb = serverFileUtils.read(new File(sb.getRootPath(), defaulticon));
                    } catch (IOException e) {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                InputStream imgStream = (InputStream) resource[0];
                if (imgStream == null) return null;

                // read image data
                try {
                    imgb = serverFileUtils.read(imgStream);
                } catch (IOException e) {
                    return null;
                } finally {
                    try {
                        imgStream.close();
                    } catch (Exception e) {/* ignore this */}
                }
            }

            // read image
            Image image = ymageImageParser.parse(urlString.toString(), imgb);

            if ((auth) && ((width == 0) || (height == 0)) && (maxwidth == 0) && (maxheight == 0)) return image;

            // find original size
            int h = image.getHeight(null);
            int w = image.getWidth(null);

            // System.out.println("DEBUG: get access to image " +
            // url.toNormalform() + " is " + ((auth) ? "authorized" : "NOT
            // authorized"));

            // in case of not-authorized access shrink the image to prevent
            // copyright problems
            // so that images are not larger than thumbnails
            if ((!auth) && ((w > 16) || (h > 16))) {
                maxwidth = (int) Math.min(64.0, w * 0.6);
                maxheight = (int) Math.min(64.0, h * 0.6);
            }

            // calculate width & height from maxwidth & maxheight
            if ((maxwidth != 0) || (maxheight != 0)) {
                double hs = (w <= maxwidth) ? 1.0 : ((double) maxwidth) / ((double) w);
                double vs = (h <= maxheight) ? 1.0 : ((double) maxheight) / ((double) h);
                double scale = Math.min(hs, vs);
                if (!auth) scale = Math.min(scale, 0.6); // this is for copyright purpose
                if (scale < 1.0) {
                    width = (int) (w * scale);
                    height = (int) (h * scale);
                } else {
                    width = w;
                    height = h;
                }
            } else {
                width = w;
                height = h;
            }

            // check for minimum values
            width = Math.max(width, 1);
            height = Math.max(height, 1);

            // scale image
            scaled = ((w == width) && (h == height)) ? image : image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
            MediaTracker mediaTracker = new MediaTracker(new Container());
            mediaTracker.addImage(scaled, 0);
            try {mediaTracker.waitForID(0);} catch (InterruptedException e) {}

            if ((height == 16) && (width == 16) && (resource != null)) {
                // this might be a favicon, store image to cache for faster re-load later on
                iconcache.put(urlString, scaled);
            }
        }
        
        return scaled;
    }
    
}
