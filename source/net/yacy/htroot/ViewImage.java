
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
// Modified 15 dec 2025 By smokingwheels


package net.yacy.htroot;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.ImageViewer;

public class ViewImage {

    /** Single instance of ImageViewer */
    private static final ImageViewer VIEWER = new ImageViewer();
    private static final ConcurrentLog log = new ConcurrentLog("ViewImage");

    /**
     * Main respond method with fallback (no 500 errors)
     */
    public static Object respond(final RequestHeader header, final serverObjects post, final serverSwitch env)
            throws IOException {

        final Switchboard sb = (Switchboard) env;

        if (post == null) {
            throw new TemplateMissingParameterException("please fill at least url or code parameter");
        }

        final String ext = header.get(HeaderFramework.CONNECTION_PROP_EXT, null);
        final boolean auth = ImageViewer.hasFullViewingRights(header, sb);
        final DigestURL url = VIEWER.parseURL(post, auth);

        InputStream inStream = null;
        ImageInputStream imageInStream = null;

        try {

            // If browser can display natively, bypass YaCy processing
            final String urlExt = MultiProtocolURL.getFileExtension(url.getFileName());
            if (ext != null && ext.equalsIgnoreCase(urlExt) && ImageViewer.isBrowserRendered(urlExt)) {
                return VIEWER.openInputStream(post, sb.loader, auth, url);
            }

            // Load raw image
            if (url.isFile()) {

                // local file path → use normal decoding
                imageInStream = ImageIO.createImageInputStream(url.getFSFile());

                EncodedImage encoded = VIEWER.parseAndScale(post, auth, url, ext, imageInStream);
                final int blen = (encoded == null || encoded.getImage() == null) ? -1 : encoded.getImage().length();
                log.info("local parseAndScale ext=" + (encoded == null ? "null" : encoded.getExtension())
                        + " bytes=" + blen + " url=" + url.toNormalform(true));

                if (encoded == null || encoded.getImage() == null || encoded.getImage().length() == 0) {
                    return createPlaceholder(ext);
                }

                return encoded;

            } else {

                // remote (HTTP/I2P)
                try {
                    inStream = VIEWER.openInputStream(post, sb.loader, auth, url);
                    log.info("opened stream for " + url.toNormalform(true));

                    if (inStream == null) {
                        return createPlaceholder(ext);
                    }

                    // RAW PASS-THROUGH MODE for I2P
                    byte[] raw = inStream.readAllBytes();

                    if (raw == null || raw.length == 0) {
                        return createPlaceholder(ext);
                    }

                    String extension = ext != null ? ext : MultiProtocolURL.getFileExtension(url.getFileName());
                    return new EncodedImage(raw, extension, true);

                } catch (UnknownHostException e) {
                    log.warn("UnknownHost (addressbook?) for " + url.toNormalform(true) + " : " + e.getMessage());
                    return createPlaceholder(ext);

                } catch (ConnectException e) {
                    log.warn("ConnectException (proxy/router?) for " + url.toNormalform(true) + " : " + e.getMessage());
                    return createPlaceholder(ext);

                } catch (SocketTimeoutException e) {
                    log.warn("Timeout for " + url.toNormalform(true) + " : " + e.getMessage());
                    return createPlaceholder(ext);

                } catch (IOException e) {
                    // unwrap nested causes (often wrapped by loaders)
                    Throwable c = e.getCause();
                    while (c != null) {
                        if (c instanceof UnknownHostException) {
                            log.warn("Wrapped UnknownHost (addressbook?) for " + url.toNormalform(true) + " : " + c.getMessage());
                            return createPlaceholder(ext);
                        }
                        if (c instanceof ConnectException) {
                            log.warn("Wrapped ConnectException (proxy/router?) for " + url.toNormalform(true) + " : " + c.getMessage());
                            return createPlaceholder(ext);
                        }
                        if (c instanceof SocketTimeoutException) {
                            log.warn("Wrapped Timeout for " + url.toNormalform(true) + " : " + c.getMessage());
                            return createPlaceholder(ext);
                        }
                        c = c.getCause();
                    }

                    log.warn("IOException fetching image " + url.toNormalform(true) + " : " + e.getMessage());
                    return createPlaceholder(ext);
                }
            }

        } catch (Exception e) {
            log.warn("ViewImage error for " + (url == null ? "null" : url.toNormalform(true)) + " : " + e.getMessage());
            return createPlaceholder(ext);

        } finally {
            if (inStream != null) try { inStream.close(); } catch (IOException ignored) {}
            if (imageInStream != null) try { imageInStream.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Placeholder fallback image (prevents 500 errors)
     */
    private static EncodedImage createPlaceholder(String ext) {

        try {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(0, 0, 64, 64);
            g.setColor(Color.RED);
            g.drawString("ERR", 20, 35);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String fmt = (ext != null && ext.length() > 0) ? ext : "png";
            ImageIO.write(img, fmt, out);

            return new EncodedImage(out.toByteArray(), fmt, false);

        } catch (Exception e) {
            return new EncodedImage(new byte[]{1}, (ext != null ? ext : "png"), false);
        }
    }
}
