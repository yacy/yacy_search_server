// ViewImage.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

import java.awt.Container;
import java.awt.Image;
import java.awt.MediaTracker;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;
import de.anomic.ymage.ymageImageParser;

public class ViewImage {

    private static HashMap<String, Image> iconcache = new HashMap<String, Image>();
    private static String defaulticon = "htroot/env/grafics/dfltfvcn.ico";
    
    public static Image respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        
        final plasmaSwitchboard sb = (plasmaSwitchboard)env;
        
        // the url to the image can be either submitted with an url in clear text, or using a license key
        // if the url is given as clear text, the user must be authorized as admin
        // the license can be used also from non-authorized users
        
        String urlString = post.get("url", "");
        final String urlLicense = post.get("code", "");
        final boolean auth = ((String) header.get(httpRequestHeader.CONNECTION_PROP_CLIENTIP, "")).equals("localhost") || sb.verifyAuthentication(header, true); // handle access rights
        
        yacyURL url = null;
        if ((urlString.length() > 0) && (auth)) try {
            url = new yacyURL(urlString, null);
        } catch (final MalformedURLException e1) {
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
        final int timeout = post.getInt("timeout", 5000);
        
        // getting the image as stream
        Image scaled = iconcache.get(urlString);
        if (scaled == null) {
            final Object[] resource = plasmaSnippetCache.getResource(url, true, timeout, false, true);
            byte[] imgb = null;
            if (resource == null) {
                if (urlString.endsWith(".ico")) {
                    // load default favicon dfltfvcn.ico
                    try {
                        imgb = serverFileUtils.read(new File(sb.getRootPath(), defaulticon));
                    } catch (final IOException e) {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                final InputStream imgStream = (InputStream) resource[0];
                if (imgStream == null) return null;

                // read image data
                try {
                    imgb = serverFileUtils.read(imgStream);
                } catch (final IOException e) {
                    return null;
                } finally {
                    try {
                        imgStream.close();
                    } catch (final Exception e) {/* ignore this */}
                }
            }

            // read image
            final Image image = ymageImageParser.parse(urlString, imgb);

            if ((auth) && ((width == 0) || (height == 0)) && (maxwidth == 0) && (maxheight == 0)) return image;

            // find original size
            final int h = image.getHeight(null);
            final int w = image.getWidth(null);
            
            // in case of not-authorized access shrink the image to prevent
            // copyright problems, so that images are not larger than thumbnails
            if (auth) {
                maxwidth = (maxwidth == 0) ? w : maxwidth;
                maxheight = (maxheight == 0) ? h : maxheight;
            } else if ((w > 16) || (h > 16)) {
                maxwidth = (int) Math.min(64.0, w * 0.6);
                maxheight = (int) Math.min(64.0, h * 0.6);
            } else {
                maxwidth = 16;
                maxheight = 16;
            }

            // calculate width & height from maxwidth & maxheight
            if ((maxwidth < w) || (maxheight < h)) {
                // scale image
                final double hs = (w <= maxwidth) ? 1.0 : ((double) maxwidth) / ((double) w);
                final double vs = (h <= maxheight) ? 1.0 : ((double) maxheight) / ((double) h);
                double scale = Math.min(hs, vs);
                if (!auth) scale = Math.min(scale, 0.6); // this is for copyright purpose
                if (scale < 1.0) {
                    width = Math.max(1, (int) (w * scale));
                    height = Math.max(1, (int) (h * scale));
                } else {
                    width = Math.max(1, w);
                    height = Math.max(1, h);
                }
                
                // compute scaled image
                scaled = ((w == width) && (h == height)) ? image : image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
                final MediaTracker mediaTracker = new MediaTracker(new Container());
                mediaTracker.addImage(scaled, 0);
                try {mediaTracker.waitForID(0);} catch (final InterruptedException e) {}
            } else {
                // do not scale
                width = w;
                height = h;
                scaled = image;
            }

            if ((height == 16) && (width == 16) && (resource != null)) {
                // this might be a favicon, store image to cache for faster re-load later on
                iconcache.put(urlString, scaled);
            }
        }
        
        return scaled;
    }
    
}
