/**
 *  snapshot
 *  Copyright 2014 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 02.12.2014 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

import java.awt.Container;
import java.awt.Image;
import java.awt.MediaTracker;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Html2Image;
import net.yacy.crawler.data.Snapshots;
import net.yacy.document.ImageParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class snapshot {
    
    //width = 1024, height = 1024, density = 300, quality = 75
    private final static int DEFAULT_WIDTH = 1024;
    private final static int DEFAULT_HEIGHT = 1024;
    private final static int DEFAULT_DENSITY = 300;
    private final static int DEFAULT_QUALITY = 75;
    private final static String DEFAULT_EXT = "jpg";

    public static Object respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final String ext = header.get("EXT", "");
        
        if (ext.equals("rss")) {
            // create a report about the content of the snapshot directory
            if (!authenticated) return null;
            int maxcount = post == null ? 10 : post.getInt("maxcount", 10);
            int depthx = post == null ? -1 : post.getInt("depth", -1);
            Integer depth = depthx == -1 ? null : depthx;
            String orderx = post == null ? "ANY" : post.get("order", "ANY");
            Snapshots.Order order = Snapshots.Order.valueOf(orderx);
            String host = post == null ? null : post.get("host");
            Map<String, Date> iddate = sb.snapshots.select(host, depth, order, maxcount);
            // now select the URL from the index for these ids in iddate and make an RSS feed
            RSSFeed rssfeed = new RSSFeed(Integer.MAX_VALUE);
            rssfeed.setChannel(new RSSMessage("Snapshot list for host = " + host + ", depth = " + depth + ", order = " + order + ", maxcount = " + maxcount, "", ""));
            for (Map.Entry<String, Date> e: iddate.entrySet()) {
                try {
                    DigestURL u = sb.index.fulltext().getURL(e.getKey());
                    if (u == null) continue;
                    RSSMessage message = new RSSMessage(u.toNormalform(true), "", u, e.getKey());
                    message.setPubDate(e.getValue());
                    rssfeed.addMessage(message);
                } catch (IOException ee) {
                    ConcurrentLog.logException(ee);
                }
            }
            byte[] rssBinary = UTF8.getBytes(rssfeed.toString());
            return new ByteArrayInputStream(rssBinary);
        }

        if (post == null) return null;
        final boolean pdf = ext.equals("pdf");
        if (pdf && !authenticated) return null;
        final boolean pngjpg = ext.equals("png") || ext.equals("jpg");
        String urlhash = post.get("urlhash", "");
        String url = post.get("url", "");
        if (url.length() == 0 && urlhash.length() == 0) return null;
        DigestURL durl = null;
        if (urlhash.length() == 0) {
            try {
                durl = new DigestURL(url);
                urlhash = ASCII.String(durl.hash());
            } catch (MalformedURLException e) {
            }
        }
        if (durl == null) {
            try {
                durl = sb.index.fulltext().getURL(urlhash);
            } catch (IOException e) {
                ConcurrentLog.logException(e);
            }
        }
        if (durl == null) return null;
        url = durl.toNormalform(true);
        Collection<File> snapshots = sb.snapshots.findPaths(durl, "pdf");
        File pdfFile = null;
        if (snapshots.size() == 0) {
            // if the client is authenticated, we create the pdf on the fly!
            if (!authenticated) return null;
            pdfFile = sb.snapshots.downloadPDFSnapshot(durl, 99, new Date(), true, sb.getConfigBool("isTransparentProxy", false) ? "http://127.0.0.1:" + sb.getConfigInt("port", 8090) : null, ClientIdentification.yacyProxyAgent.userAgent);
        } else {
            pdfFile = snapshots.iterator().next();
        }
        if (pdfFile == null) return null;
        if (pdf) {
            try {
                byte[] pdfBinary = FileUtils.read(pdfFile);
                return new ByteArrayInputStream(pdfBinary);
            } catch (IOException e) {
                ConcurrentLog.logException(e);
                return null;
            }
        }
        
        if (pngjpg) {
            int width = Math.min(post.getInt("width", DEFAULT_WIDTH), DEFAULT_WIDTH);
            int height = Math.min(post.getInt("height", DEFAULT_HEIGHT), DEFAULT_HEIGHT);
            String imageFileStub = pdfFile.getAbsolutePath(); imageFileStub = imageFileStub.substring(0, imageFileStub.length() - 3); // cut off extension
            File imageFile = new File(imageFileStub + DEFAULT_WIDTH + "." + DEFAULT_HEIGHT + "." + DEFAULT_EXT);
            if (!imageFile.exists() && authenticated) {
                Html2Image.pdf2image(pdfFile, imageFile, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_DENSITY, DEFAULT_QUALITY);
            }
            if (!imageFile.exists()) return null;
            if (width == DEFAULT_WIDTH && height == DEFAULT_HEIGHT) {
                try {
                    byte[] imageBinary = FileUtils.read(imageFile);
                    return new ByteArrayInputStream(imageBinary);
                } catch (IOException e) {
                    ConcurrentLog.logException(e);
                    return null;
                }
            }
            // lets read the file and scale
            Image image;
            try {
                image = ImageParser.parse(imageFile.getAbsolutePath(), FileUtils.read(imageFile));
                final Image scaled = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
                final MediaTracker mediaTracker = new MediaTracker(new Container());
                mediaTracker.addImage(scaled, 0);
                try {mediaTracker.waitForID(0);} catch (final InterruptedException e) {}
                return scaled;
            } catch (IOException e) {
                ConcurrentLog.logException(e);
                return null;
            }

        }
        
        
        return null;
    }
}
