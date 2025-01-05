/**
 *  snapshot
 *  Copyright 2014 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 02.12.2014 at https://yacy.net
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

package net.yacy.htroot.api;

import java.awt.Container;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.apache.http.HttpStatus;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Html2Image;
import net.yacy.crawler.data.Snapshots;
import net.yacy.crawler.data.Snapshots.Revisions;
import net.yacy.crawler.data.Transactions;
import net.yacy.document.ImageParser;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.http.servlets.TemplateProcessingException;
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

    	final serverObjects defaultResponse = new serverObjects();


        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final String ext = header.get(HeaderFramework.CONNECTION_PROP_EXT, "");

        if(ext.isEmpty()) {
			throw new TemplateProcessingException("Missing extension. Try with rss, xml, json, pdf, png or jpg." + ext,
					HttpStatus.SC_BAD_REQUEST);
        }


        if (ext.equals("rss")) {
            // create a report about the content of the snapshot directory
            if (!authenticated) {
            	defaultResponse.authenticationRequired();
            	return defaultResponse;
            }
            final int maxcount = post == null ? 10 : post.getInt("maxcount", 10);
            final int depthx = post == null ? -1 : post.getInt("depth", -1);
            final Integer depth = depthx == -1 ? null : depthx;
            final String orderx = post == null ? "ANY" : post.get("order", "ANY");
            final Snapshots.Order order = Snapshots.Order.valueOf(orderx);
            final String statex = post == null ? Transactions.State.INVENTORY.name() : post.get("state", Transactions.State.INVENTORY.name());
            final Transactions.State state = Transactions.State.valueOf(statex);
            final String host = post == null ? null : post.get("host");
            final Map<String, Revisions> iddate = Transactions.select(host, depth, order, maxcount, state);
            // now select the URL from the index for these ids in iddate and make an RSS feed
            final RSSFeed rssfeed = new RSSFeed(Integer.MAX_VALUE);
            rssfeed.setChannel(new RSSMessage("Snapshot list for host = " + host + ", depth = " + depth + ", order = " + order + ", maxcount = " + maxcount, "", ""));
            for (final Map.Entry<String, Revisions> e: iddate.entrySet()) {
                try {
                    final String u = e.getValue().url == null ? sb.index.fulltext().getURL(e.getKey()) : e.getValue().url;
                    if (u == null) continue;
                    final RSSMessage message = new RSSMessage(u, "", new DigestURL(u), e.getKey());
                    message.setPubDate(e.getValue().dates[0]);
                    rssfeed.addMessage(message);
                } catch (final IOException ee) {
                    ConcurrentLog.logException(ee);
                }
            }
            final byte[] rssBinary = UTF8.getBytes(rssfeed.toString());
            return new ByteArrayInputStream(rssBinary);
        }

        // for the following methods we (mostly) need an url or a url hash
        if (post == null) post = new serverObjects();
        final boolean xml = ext.equals("xml");
        final boolean pdf = ext.equals("pdf");
        if (pdf && !authenticated) {
        	defaultResponse.authenticationRequired();
        	return defaultResponse;
        }
        final boolean pngjpg = ext.equals("png") || ext.equals(DEFAULT_EXT);
        String urlhash = post.get("urlhash", "");
        final String url = post.get("url", "");
        DigestURL durl = null;
        if (urlhash.length() == 0 && url.length() > 0) {
            try {
                durl = new DigestURL(url);
                urlhash = ASCII.String(durl.hash());
            } catch (final MalformedURLException e) {
            }
        }
        if (durl == null && urlhash.length() > 0) {
            try {
                final String u = sb.index.fulltext().getURL(urlhash);
                durl = u == null ? null : new DigestURL(u);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        if (ext.equals("json")) {
            // command interface: view and change a transaction state, get metadata about transactions in the past
            final String command = post.get("command", "metadata");
            final String statename = post.get("state");
            final JSONObject result = new JSONObject();
            try {
                if (command.equals("status")) {
                    // return a status of the transaction archive
                    final JSONObject sizes = new JSONObject();
                    for (final Map.Entry<String, Integer> state: Transactions.sizes().entrySet()) sizes.put(state.getKey(), state.getValue());
                    result.put("size", sizes);
                } else if (command.equals("list")) {
                    if (!authenticated) {
                    	defaultResponse.authenticationRequired();
                    	return defaultResponse;
                    }
                    // return a status of the transaction archive
                    final String host = post.get("host");
                    final String depth = post.get("depth");
                    final int depthi = depth == null ? -1 : Integer.parseInt(depth);
                    for (final Transactions.State state: statename == null ?
                            new Transactions.State[]{Transactions.State.INVENTORY, Transactions.State.ARCHIVE} :
                            new Transactions.State[]{Transactions.State.valueOf(statename)}) {
                        if (host == null) {
                            final JSONObject hostCountInventory = new JSONObject();
                            for (final String h: Transactions.listHosts(state)) {
                                final int size = Transactions.listIDsSize(h, depthi, state);
                                if (size > 0) hostCountInventory.put(h, size);
                            }
                            result.put("count." + state.name(), hostCountInventory);
                        } else {
                            final TreeMap<Integer, Collection<Revisions>> ids = Transactions.listIDs(host, depthi, state);
                            if (ids == null) {
                                result.put("result", "fail");
                                result.put("comment", "no entries for host " + host + " found");
                            } else {
                                for (final Map.Entry<Integer, Collection<Revisions>> entry: ids.entrySet()) {
                                    for (final Revisions r: entry.getValue()) {
                                        try {
                                            final JSONObject metadata = new JSONObject();
                                            final String u = r.url != null ? r.url : sb.index.fulltext().getURL(r.urlhash);
                                            metadata.put("url", u == null ? "unknown" : u);
                                            metadata.put("dates", r.dates);
                                            assert r.depth == entry.getKey().intValue();
                                            metadata.put("depth", entry.getKey().intValue());
                                            result.put(r.urlhash, metadata);
                                        } catch (final IOException e) {}
                                    }
                                }
                            }
                        }
                    }
                } else if (command.equals("commit")) {
                    if (!authenticated) {
                    	defaultResponse.authenticationRequired();
                    	return defaultResponse;
                    }
                    final Revisions r = Transactions.commit(urlhash);
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
                    if (!authenticated) {
                    	defaultResponse.authenticationRequired();
                    	return defaultResponse;
                    }
                    final Revisions r = Transactions.rollback(urlhash);
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
                            final JSONObject metadata = new JSONObject();
                            final String u = r.url != null ? r.url : sb.index.fulltext().getURL(r.urlhash);
                            metadata.put("url", u == null ? "unknown" : u);
                            metadata.put("dates", r.dates);
                            metadata.put("depth", r.depth);
                            metadata.put("state", state.name());
                            result.put(r.urlhash, metadata);
                        }
                    } catch (IOException |IllegalArgumentException e) {}
                }
            } catch (final JSONException e) {
                ConcurrentLog.logException(e);
            }
            String json = result.toString();
            if (post.containsKey("callback")) json = post.get("callback") + "([" + json + "]);";
            return new ByteArrayInputStream(UTF8.getBytes(json));
        }

        // for the following methods we always need the durl to fetch data
        if (durl == null) {
        	throw new TemplateMissingParameterException("Missing valid url or urlhash parameter");
        }

        if (xml) {
            final Collection<File> xmlSnapshots = Transactions.findPaths(durl, "xml", Transactions.State.ANY);
            File xmlFile = null;
            if (xmlSnapshots.isEmpty()) {
				throw new TemplateProcessingException("Could not find the xml snapshot file.", HttpStatus.SC_NOT_FOUND);
            }
            xmlFile = xmlSnapshots.iterator().next();
            try {
                final byte[] xmlBinary = FileUtils.read(xmlFile);
                return new ByteArrayInputStream(xmlBinary);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                throw new TemplateProcessingException("Could not read the xml snapshot file.");
            }
        }

        if (pdf || pngjpg) {
            Collection<File> pdfSnapshots = Transactions.findPaths(durl, "pdf", Transactions.State.INVENTORY);
            File pdfFile = null;
            if (pdfSnapshots.isEmpty()) {
                // if the client is authenticated, we create the pdf on the fly!
                if (!authenticated) {
					throw new TemplateProcessingException(
							"Could not find the pdf snapshot file. You must be authenticated to generate one on the fly.",
							HttpStatus.SC_NOT_FOUND);
                }
                final SolrDocument sd = sb.index.fulltext().getMetadata(durl.hash());
                boolean success = false;
                if (sd == null) {
                    success = Transactions.store(durl, new Date(), 99, false, true, sb.getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false) ? "http://127.0.0.1:" + sb.getConfigInt(SwitchboardConstants.SERVER_PORT, 8090) : null, sb.getConfig("crawler.http.acceptLanguage", null));
                } else {
                    final SolrInputDocument sid = sb.index.fulltext().getDefaultConfiguration().toSolrInputDocument(sd);
                    success = Transactions.store(sid, false, true, true, sb.getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false) ? "http://127.0.0.1:" + sb.getConfigInt(SwitchboardConstants.SERVER_PORT, 8090) : null, sb.getConfig("crawler.http.acceptLanguage", null));
                }
                if (success) {
                    pdfSnapshots = Transactions.findPaths(durl, "pdf", Transactions.State.ANY);
                    if (!pdfSnapshots.isEmpty()) {
                    	pdfFile = pdfSnapshots.iterator().next();
                    }
                }
            } else {
                pdfFile = pdfSnapshots.iterator().next();
            }
            if (pdfFile == null) {
				throw new TemplateProcessingException(
						"Could not find the pdf snapshot file and could not generate one on the fly.",
						HttpStatus.SC_NOT_FOUND);
            }
            if (pdf) {
                try {
                    final byte[] pdfBinary = FileUtils.read(pdfFile);
                    return new ByteArrayInputStream(pdfBinary);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
					throw new TemplateProcessingException("Could not read the pdf snapshot file.");
                }
            }

            if (pngjpg) {
                final int width = Math.min(post.getInt("width", DEFAULT_WIDTH), DEFAULT_WIDTH);
                final int height = Math.min(post.getInt("height", DEFAULT_HEIGHT), DEFAULT_HEIGHT);
                String imageFileStub = pdfFile.getAbsolutePath(); imageFileStub = imageFileStub.substring(0, imageFileStub.length() - 3); // cut off extension
                final File imageFile = new File(imageFileStub + DEFAULT_WIDTH + "." + DEFAULT_HEIGHT + "." + ext);
                if (!imageFile.exists() && authenticated) {
                    if(!Html2Image.pdf2image(pdfFile, imageFile, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_DENSITY, DEFAULT_QUALITY)) {
						throw new TemplateProcessingException(
								"Could not generate the " + ext + " image snapshot file.");
                    }
                }
                if (!imageFile.exists()) {
					throw new TemplateProcessingException(
							"Could not find the " + ext
									+ " image snapshot file. You must be authenticated to generate one on the fly.",
							HttpStatus.SC_NOT_FOUND);
                }
                if (width == DEFAULT_WIDTH && height == DEFAULT_HEIGHT) {
                    try {
                        final byte[] imageBinary = FileUtils.read(imageFile);
                        return new ByteArrayInputStream(imageBinary);
                    } catch (final IOException e) {
                        ConcurrentLog.logException(e);
						throw new TemplateProcessingException("Could not read the " + ext + " image snapshot file.");
                    }
                }
                // lets read the file and scale
                Image image;
                try {
                    image = ImageParser.parse(imageFile.getAbsolutePath(), FileUtils.read(imageFile));
                    if(image == null) {
                    	throw new TemplateProcessingException("Could not parse the " + ext + " image snapshot file.");
                    }
                    final Image scaled = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
                    final MediaTracker mediaTracker = new MediaTracker(new Container());
                    mediaTracker.addImage(scaled, 0);
                    try {mediaTracker.waitForID(0);} catch (final InterruptedException e) {}

					/*
					 * Ensure there is no alpha component on the ouput image, as it is pointless
					 * here and it is not well supported by the JPEGImageWriter from OpenJDK
					 */
					final BufferedImage scaledBufferedImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    scaledBufferedImg.createGraphics().drawImage(scaled, 0, 0, width, height, null);
                    return new EncodedImage(scaledBufferedImg, ext, true);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
					throw new TemplateProcessingException("Could not scale the " + ext + " image snapshot file.");
                }

            }
        }

		throw new TemplateProcessingException(
				"Unsupported extension : " + ext + ". Try with rss, xml, json, pdf, png or jpg.",
				HttpStatus.SC_BAD_REQUEST);
    }
}
