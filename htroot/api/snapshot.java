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
import java.util.TreeMap;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Html2Image;
import net.yacy.cora.util.JSONException;
import net.yacy.cora.util.JSONObject;
import net.yacy.crawler.data.Snapshots;
import net.yacy.crawler.data.Transactions;
import net.yacy.crawler.data.Snapshots.Revisions;
import net.yacy.document.ImageParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class snapshot {
    
    //width = 1024, height = 1024, density = 300, quality = 75
    private final static int DEFAULT_WIDTH = 1024;
    private final static int DEFAULT_HEIGHT = 1024;
    private final static int DEFAULT_DENSITY = 300;
    private final static int DEFAULT_QUALITY = 75;
    private final static String DEFAULT_EXT = "jpg";

    public static Object respond(final RequestHeader header, serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final String ext = header.get(HeaderFramework.CONNECTION_PROP_EXT, "");
        
        
        if (ext.equals("rss")) {
            // create a report about the content of the snapshot directory
            if (!authenticated) return null;
            int maxcount = post == null ? 10 : post.getInt("maxcount", 10);
            int depthx = post == null ? -1 : post.getInt("depth", -1);
            Integer depth = depthx == -1 ? null : depthx;
            String orderx = post == null ? "ANY" : post.get("order", "ANY");
            Snapshots.Order order = Snapshots.Order.valueOf(orderx);
            String statex = post == null ? Transactions.State.INVENTORY.name() : post.get("state", Transactions.State.INVENTORY.name());
            Transactions.State state = Transactions.State.valueOf(statex);
            String host = post == null ? null : post.get("host");
            Map<String, Revisions> iddate = Transactions.select(host, depth, order, maxcount, state);
            // now select the URL from the index for these ids in iddate and make an RSS feed
            RSSFeed rssfeed = new RSSFeed(Integer.MAX_VALUE);
            rssfeed.setChannel(new RSSMessage("Snapshot list for host = " + host + ", depth = " + depth + ", order = " + order + ", maxcount = " + maxcount, "", ""));
            for (Map.Entry<String, Revisions> e: iddate.entrySet()) {
                try {
                    DigestURL u = e.getValue().url == null ? sb.index.fulltext().getURL(e.getKey()) : new DigestURL(e.getValue().url);
                    if (u == null) continue;
                    RSSMessage message = new RSSMessage(u.toNormalform(true), "", u, e.getKey());
                    message.setPubDate(e.getValue().dates[0]);
                    rssfeed.addMessage(message);
                } catch (IOException ee) {
                    ConcurrentLog.logException(ee);
                }
            }
            byte[] rssBinary = UTF8.getBytes(rssfeed.toString());
            return new ByteArrayInputStream(rssBinary);
        }

        // for the following methods we (mostly) need an url or a url hash
        if (post == null) post = new serverObjects();
        final boolean xml = ext.equals("xml");
        final boolean pdf = ext.equals("pdf");
        if (pdf && !authenticated) return null;
        final boolean pngjpg = ext.equals("png") || ext.equals("jpg");
        String urlhash = post.get("urlhash", "");
        String url = post.get("url", "");
        DigestURL durl = null;
        if (urlhash.length() == 0 && url.length() > 0) {
            try {
                durl = new DigestURL(url);
                urlhash = ASCII.String(durl.hash());
            } catch (MalformedURLException e) {
            }
        }
        if (durl == null && urlhash.length() > 0) {
            try {
                durl = sb.index.fulltext().getURL(urlhash);
            } catch (IOException e) {
                ConcurrentLog.logException(e);
            }
        }
        if (url.length() == 0 && durl != null) url = durl.toNormalform(true);

        if (ext.equals("json")) {
            // command interface: view and change a transaction state, get metadata about transactions in the past
            String command = post.get("command", "metadata");
            String statename = post.get("state");
            JSONObject result = new JSONObject();
            try {
                if (command.equals("status")) {
                    // return a status of the transaction archive
                    JSONObject sizes = new JSONObject();
                    for (Map.Entry<String, Integer> state: Transactions.sizes().entrySet()) sizes.put(state.getKey(), state.getValue());
                    result.put("size", sizes);
                } else if (command.equals("list")) {
                    if (!authenticated) return null;
                    // return a status of the transaction archive
                    String host = post.get("host");
                    String depth = post.get("depth");
                    int depthi = depth == null ? -1 : Integer.parseInt(depth);
                    for (Transactions.State state: statename == null ?
                            new Transactions.State[]{Transactions.State.INVENTORY, Transactions.State.ARCHIVE} :
                            new Transactions.State[]{Transactions.State.valueOf(statename)}) {
                        if (host == null) {
                            JSONObject hostCountInventory = new JSONObject();
                            for (String h: Transactions.listHosts(state)) {
                                int size = Transactions.listIDsSize(h, depthi, state);
                                if (size > 0) hostCountInventory.put(h, size);
                            }
                            result.put("count." + state.name(), hostCountInventory);
                        } else {
                            TreeMap<Integer, Collection<Revisions>> ids = Transactions.listIDs(host, depthi, state);
                            if (ids == null) {
                                result.put("result", "fail");
                                result.put("comment", "no entries for host " + host + " found");
                            } else {
                                for (Map.Entry<Integer, Collection<Revisions>> entry: ids.entrySet()) {
                                    for (Revisions r: entry.getValue()) {
                                        try {
                                            JSONObject metadata = new JSONObject();
                                            DigestURL u = r.url != null ? new DigestURL(r.url) : sb.index.fulltext().getURL(r.urlhash);
                                            metadata.put("url", u == null ? "unknown" : u.toNormalform(true));
                                            metadata.put("dates", r.dates);
                                            assert r.depth == entry.getKey().intValue();
                                            metadata.put("depth", entry.getKey().intValue());
                                            result.put(r.urlhash, metadata);
                                        } catch (IOException e) {}
                                    }
                                }
                            }
                        }
                    }
                } else if (command.equals("commit")) {
                    if (!authenticated) return null;
                    Revisions r = Transactions.commit(urlhash);
                    if (r != null) {
                        result.put("result", "success");
                        result.put("depth", r.depth);
                        result.put("url", r.url);
                        result.put("dates", r.dates);
                    } else {
                        result.put("result", "fail");
                    }
                    result.put("urlhash", urlhash);
                } else if (command.equals("rollback")) {
                    if (!authenticated) return null;
                    Revisions r = Transactions.rollback(urlhash);
                    if (r != null) {
                        result.put("result", "success");
                        result.put("depth", r.depth);
                        result.put("url", r.url);
                        result.put("dates", r.dates);
                    } else {
                        result.put("result", "fail");
                    }
                    result.put("urlhash", urlhash);
                } else if (command.equals("metadata")) {
                    try {
                        Revisions r;
                        Transactions.State state = statename == null || statename.length() == 0 ? null : Transactions.State.valueOf(statename);
                        if (state == null) {
                            r = Transactions.getRevisions(Transactions.State.INVENTORY, urlhash);
                            if (r != null) state = Transactions.State.INVENTORY;
                            r = Transactions.getRevisions(Transactions.State.ARCHIVE, urlhash);
                            if (r != null) state = Transactions.State.ARCHIVE;
                        } else {
                            r = Transactions.getRevisions(state, urlhash);
                        }
                        if (r != null) {
                            JSONObject metadata = new JSONObject();
                            DigestURL u;
                            u = r.url != null ? new DigestURL(r.url) : sb.index.fulltext().getURL(r.urlhash);
                            metadata.put("url", u == null ? "unknown" : u.toNormalform(true));
                            metadata.put("dates", r.dates);
                            metadata.put("depth", r.depth);
                            metadata.put("state", state.name());
                            result.put(r.urlhash, metadata);
                        }
                    } catch (IOException |IllegalArgumentException e) {}
                }
            } catch (JSONException e) {
                ConcurrentLog.logException(e);
            }
            String json = result.toString();
            if (post.containsKey("callback")) json = post.get("callback") + "([" + json + "]);";
            return new ByteArrayInputStream(UTF8.getBytes(json));
        }
        
        // for the following methods we always need the durl to fetch data
        if (durl == null) return null;
        
        if (xml) {
            Collection<File> xmlSnapshots = Transactions.findPaths(durl, "xml", Transactions.State.ANY);
            File xmlFile = null;
            if (xmlSnapshots.size() == 0) {
                return null;
            }
            xmlFile = xmlSnapshots.iterator().next();
            try {
                byte[] xmlBinary = FileUtils.read(xmlFile);
                return new ByteArrayInputStream(xmlBinary);
            } catch (IOException e) {
                ConcurrentLog.logException(e);
                return null;
            }
        }
        
        if (pdf || pngjpg) {
            Collection<File> pdfSnapshots = Transactions.findPaths(durl, "pdf", Transactions.State.INVENTORY);
            File pdfFile = null;
            if (pdfSnapshots.size() == 0) {
                // if the client is authenticated, we create the pdf on the fly!
                if (!authenticated) return null;
                SolrDocument sd = sb.index.fulltext().getMetadata(durl.hash());
                boolean success = false;
                if (sd == null) {
                    success = Transactions.store(durl, new Date(), 99, false, true, sb.getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false) ? "http://127.0.0.1:" + sb.getConfigInt("port", 8090) : null, sb.getConfig("crawler.http.acceptLanguage", null));
                } else {
                    SolrInputDocument sid = sb.index.fulltext().getDefaultConfiguration().toSolrInputDocument(sd);
                    success = Transactions.store(sid, false, true, true, sb.getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false) ? "http://127.0.0.1:" + sb.getConfigInt("port", 8090) : null, sb.getConfig("crawler.http.acceptLanguage", null));
                }
                if (success) {
                    pdfSnapshots = Transactions.findPaths(durl, "pdf", Transactions.State.ANY);
                    if (pdfSnapshots.size() != 0) pdfFile = pdfSnapshots.iterator().next();
                }
            } else {
                pdfFile = pdfSnapshots.iterator().next();
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
                    return new EncodedImage(scaled, ext, true);
                } catch (IOException e) {
                    ConcurrentLog.logException(e);
                    return null;
                }
    
            }
        }
        
        return null;
    }
}
