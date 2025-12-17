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
// 15 dec 2025 by smokingwheels
// 16 dec 2025 by smokingwheels
// 17 dec 2025 by smokingwheels

package net.yacy.htroot;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

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

    private static final ConcurrentLog log = new ConcurrentLog("ViewImage");
    private static final ImageViewer VIEWER = new ImageViewer();

    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    /* ================= I2P stream ================= */

    private static InputStream openI2PStream(final DigestURL url) throws IOException {

        Proxy proxy = new Proxy(
                Proxy.Type.HTTP,
                new InetSocketAddress("127.0.0.1", 4444)
        );

        URL jurl = new URL(url.toNormalform(true));
        HttpURLConnection conn = (HttpURLConnection) jurl.openConnection(proxy);

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(false);

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("I2P proxy HTTP " + code);
        }

        return conn.getInputStream();
    }

    /* ================= Retry decode ================= */

    private static BufferedImage decodeWithRetries(
            final DigestURL url,
            final serverObjects post,
            final Switchboard sb,
            final boolean auth,
            final int attempts,
            final int delayMs) {

        for (int i = 1; i <= attempts; i++) {

            try (InputStream is =
                    isI2P(url)
                            ? openI2PStream(url)
                            : VIEWER.openInputStream(post, sb.loader, auth, url)) {

                if (is == null) return null;

                BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    if (i > 1) {
                        log.info("Image decoded after retry " + i + " : " + url.toNormalform(true));
                    }
                    return img;
                }

                log.info("Decode attempt " + i + " failed for " + url.toNormalform(true));

            } catch (SocketTimeoutException e) {
                log.warn("Timeout attempt " + i + " : " + url.toNormalform(true));

            } catch (UnknownHostException | ConnectException e) {
                log.warn("Hard network failure: " + url.toNormalform(true) + " : " + e.getMessage());
                return null;

            } catch (Exception e) {
                log.warn("Decode error: " + url.toNormalform(true) + " : " + e.getMessage());
                return null;
            }

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return null;
    }

    /* ================= Main ================= */

    public static Object respond(
            final RequestHeader header,
            final serverObjects post,
            final serverSwitch env) throws IOException {

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

            String urlExt = MultiProtocolURL.getFileExtension(url.getFileName());
            if (ext != null && ext.equalsIgnoreCase(urlExt)
                    && ImageViewer.isBrowserRendered(urlExt)) {
                return VIEWER.openInputStream(post, sb.loader, auth, url);
            }

            if (url.isFile()) {
                imageInStream = ImageIO.createImageInputStream(url.getFSFile());
                EncodedImage enc = VIEWER.parseAndScale(post, auth, url, ext, imageInStream);
                return enc != null ? enc : createPlaceholder(ext);
            }

            if (isI2P(url)) {

                BufferedImage img = decodeWithRetries(url, post, sb, auth, 5, 250);
                if (img == null) {
                    return createPlaceholder(ext);
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                String format = ext != null ? ext : "png";
                ImageIO.write(img, format, out);
                return new EncodedImage(out.toByteArray(), format, true);
            }

            inStream = VIEWER.openInputStream(post, sb.loader, auth, url);
            if (inStream == null) return createPlaceholder(ext);

            byte[] raw = readUpTo(inStream, MAX_IMAGE_BYTES);
            if (raw.length == 0 || raw.length >= MAX_IMAGE_BYTES) {
                return createPlaceholder(ext);
            }

            String format = ext != null ? ext : "png";
            return new EncodedImage(raw, format, true);

        } catch (Exception e) {
            log.warn("ViewImage error: " + e.getMessage());
            return createPlaceholder(ext);

        } finally {
            if (inStream != null) try { inStream.close(); } catch (IOException ignored) {}
            if (imageInStream != null) try { imageInStream.close(); } catch (IOException ignored) {}
        }
    }

    /* ================= Helpers ================= */

    private static boolean isI2P(final DigestURL url) {
        String host = url.getHost();
        return host != null && (host.endsWith(".i2p") || host.endsWith(".b32.i2p"));
    }

    private static byte[] readUpTo(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int r;

        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > max) break;
            out.write(buf, 0, r);
        }

        return out.toByteArray();
    }

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
            String fmt = ext != null ? ext : "png";
            ImageIO.write(img, fmt, out);
            return new EncodedImage(out.toByteArray(), fmt, false);

        } catch (Exception e) {
            return new EncodedImage(new byte[]{1}, ext != null ? ext : "png", false);
        }
    }
}
