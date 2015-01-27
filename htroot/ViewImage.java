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
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.URLLicense;
import net.yacy.document.ImageParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ViewImage {

    private static Map<String, Image> iconcache = new ConcurrentARC<String, Image>(1000, Math.max(10, Math.min(32, WorkflowProcessor.availableCPU * 2)));
    private static String defaulticon = "htroot/env/grafics/dfltfvcn.ico";
    private static byte[] defaulticonb;
    static {
        try {
            defaulticonb = FileUtils.read(new File(defaulticon));
        } catch (final IOException e) {
        }
    }

    public static Object respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final Switchboard sb = (Switchboard)env;

        // the url to the image can be either submitted with an url in clear text, or using a license key
        // if the url is given as clear text, the user must be authorized as admin
        // the license can be used also from non-authorized users

        String urlString = post.get("url", "");
        final String urlLicense = post.get("code", "");
        boolean isStatic = post.getBoolean("isStatic");
        String ext = header.get("EXT", null);
        final boolean quadratic = post.containsKey("quadratic");
        final boolean auth = Domains.isLocalhost(header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "")) || sb.verifyAuthentication(header); // handle access rights

        DigestURL url = null;
        if ((urlString.length() > 0) && (auth)) try {
            url = new DigestURL(urlString);
        } catch (final MalformedURLException e1) {
            url = null;
        }

        if ((url == null) && (urlLicense.length() > 0)) {
            urlString = URLLicense.releaseLicense(urlLicense);
            try {
                url = new DigestURL(urlString);
            } catch (final MalformedURLException e1) {
                url = null;
                urlString = null;
            }
        }

        if (urlString == null) return null;

        int width = post.getInt("width", 0);
        int height = post.getInt("height", 0);
        int maxwidth = post.getInt("maxwidth", 0);
        int maxheight = post.getInt("maxheight", 0);

        // get the image as stream
        if (MemoryControl.shortStatus()) iconcache.clear();
        Image image = iconcache.get(urlString);
        if (image == null) {
            byte[] resourceb = null;
            if (url != null) try {
                String agentName = post.get("agentName", auth ? ClientIdentification.yacyIntranetCrawlerAgentName : ClientIdentification.yacyInternetCrawlerAgentName);
                ClientIdentification.Agent agent = ClientIdentification.getAgent(agentName);
                resourceb = sb.loader.loadContent(sb.loader.request(url, false, true), CacheStrategy.IFEXIST, BlacklistType.SEARCH, agent);
            } catch (final IOException e) {
                ConcurrentLog.fine("ViewImage", "cannot load: " + e.getMessage());
            }
            byte[] imgb = null;
            if (resourceb == null) {
                if (urlString.endsWith(".ico")) {
                    // load default favicon dfltfvcn.ico
                    if (defaulticonb == null) try {
                        imgb = FileUtils.read(new File(sb.getAppPath(), defaulticon));
                    } catch (final IOException e) {
                        return null;
                    } else {
                        imgb = defaulticonb;
                    }
                } else {
                    return null;
                }
            } else {
                final InputStream imgStream = new ByteArrayInputStream(resourceb);

                // read image data
                try {
                    imgb = FileUtils.read(imgStream);
                } catch (final IOException e) {
                    return null;
                } finally {
                    try {
                        imgStream.close();
                    } catch (final Exception e) {}
                }
            }
            
            // gif images are not loaded because of an animated gif bug within jvm which sends java into an endless loop with high CPU
            if (ext.equals("gif") && "gif".equals(MultiProtocolURL.getFileExtension(url.getFileName()))) {
                return new ByteArrayInputStream(imgb);
            }
            
            // read image
            image = ImageParser.parse(urlString, imgb);
            if (image == null) {
                return null;
            }
            if ((auth && (width == 0 || height == 0) && maxwidth == 0 && maxheight == 0)) return ext == null ? image : new EncodedImage(image, ext, isStatic);
            // find original size
            final int h = image.getHeight(null);
            final int w = image.getWidth(null);
            
            // if a quadratic flag is set, we cut the image out to be in quadratic shape
            if (quadratic && w != h) {
                if (w > h) {
                    final BufferedImage dst = new BufferedImage(h, h, BufferedImage.TYPE_INT_RGB);    
                    Graphics2D g = dst.createGraphics();
                    g.drawImage(image, 0, 0, h - 1, h - 1, (w - h) / 2, 0, h + (w - h) / 2, h - 1, null);
                    g.dispose();
                    image = dst;
                } else {
                    final BufferedImage dst = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);    
                    Graphics2D g = dst.createGraphics();
                    g.drawImage(image, 0, 0, w - 1, w - 1, 0, (h - w) / 2, w - 1, w + (h - w) / 2, null);
                    g.dispose();
                    image = dst;
                }
            }

            // in case of not-authorized access shrink the image to prevent
            // copyright problems, so that images are not larger than thumbnails
            if (auth) {
                maxwidth = (maxwidth == 0) ? w : maxwidth;
                maxheight = (maxheight == 0) ? h : maxheight;
            } else if ((w > 16) || (h > 16)) {
                maxwidth = Math.min(96, w);
                maxheight = Math.min(96, h);
            } else {
                maxwidth = 16;
                maxheight = 16;
            }

            // calculate width & height from maxwidth & maxheight
            if (maxwidth < w || maxheight < h) {
                // scale image
                final double hs = (w <= maxwidth) ? 1.0 : ((double) maxwidth) / ((double) w);
                final double vs = (h <= maxheight) ? 1.0 : ((double) maxheight) / ((double) h);
                final double scale = Math.min(hs, vs);
                //if (!auth) scale = Math.min(scale, 0.6); // this is for copyright purpose
                if (scale < 1.0) {
                    width = Math.max(1, (int) (w * scale));
                    height = Math.max(1, (int) (h * scale));
                } else {
                    width = Math.max(1, w);
                    height = Math.max(1, h);
                }

                if (w != width && h != height) {
                    // compute scaled image
                    final Image scaled = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
                    final MediaTracker mediaTracker = new MediaTracker(new Container());
                    mediaTracker.addImage(scaled, 0);
                    try {mediaTracker.waitForID(0);} catch (final InterruptedException e) {}

                    // make a BufferedImage out of that
                    final BufferedImage i = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    try {
                        i.createGraphics().drawImage(scaled, 0, 0, width, height, null);
                        image = i;
                        // check outcome
                        final Raster raster = i.getData();
                        int[] pixel = new int[3];
                        pixel = raster.getPixel(0, 0, pixel);
                        if (pixel[0] != 0 || pixel[1] != 0 || pixel[2] != 0) image = i;
                    } catch (final Exception e) {
                        //java.lang.ClassCastException: [I cannot be cast to [B
                    }

                }
            } else {
                // do not scale
                width = w;
                height = h;
            }

            if ((height == 16) && (width == 16) && (resourceb != null)) {
                // this might be a favicon, store image to cache for faster re-load later on
                iconcache.put(urlString, image);
            }
        }

        return ext == null ? image : new EncodedImage(image, ext, isStatic);
    }

}
