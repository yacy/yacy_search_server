/**
 *  HostBrowser
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 27.09.2012 at http://yacy.net
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.peers.graphics.WebStructureGraph.StructureEntry;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.SolrConfiguration;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class HostBrowser {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        Fulltext fulltext = sb.index.fulltext();
        final boolean admin = sb.verifyAuthentication(header);
        final boolean autoload = admin && sb.getConfigBool("browser.autoload", true);
        final boolean load4everyone = sb.getConfigBool("browser.load4everyone", false);
        final boolean loadRight = autoload || load4everyone; // add config later
        final boolean searchAllowed = sb.getConfigBool("publicSearchpage", true) || admin;

        final serverObjects prop = new serverObjects();
        
        // set default values
        prop.put("path", "");
        prop.put("result", "");
        prop.putNum("ucount", fulltext.size());
        prop.put("hosts", 0);
        prop.put("files", 0);
        prop.put("admin", 0);

        if (!searchAllowed) {
            prop.put("result", "You are not allowed to use this page. Please ask an administrator for permission.");
            return prop;
        }
        
        if (post == null || env == null) {
            return prop;
        }

        String path = post.get("path", "").trim();
        int p = path.lastIndexOf('/');
        if (p < 0 && path.length() > 0) path = path + "/"; else if (p > 7) path = path.substring(0, p + 1); // the search path shall always end with "/"
        if (path.length() > 0 && (
            !path.startsWith("http://") &&
            !path.startsWith("https://") &&
            !path.startsWith("ftp://") &&
            !path.startsWith("smb://") &&
            !path.startsWith("file://"))) { path = "http://" + path; }
        prop.putHTML("path", path);
        DigestURI pathURI = null;
        try {pathURI = new DigestURI(path);} catch (MalformedURLException e) {}

        String load = post.get("load", "");
        boolean wait = false;
        if (loadRight && autoload && path.length() != 0 && pathURI != null && load.length() == 0 && !sb.index.exists(pathURI.hash())) {
            // in case that the url does not exist and loading is wanted turn this request into a loading request
            load = path;
            wait = true;
        }
        if (load.length() > 0 && loadRight) {
            // stack URL
            DigestURI url;
            if (sb.crawlStacker.size() > 2) wait = false;
            try {
                url = new DigestURI(load);
                String reasonString = sb.crawlStacker.stackCrawl(new Request(
                        sb.peers.mySeed().hash.getBytes(),
                        url, null, load, new Date(),
                        sb.crawler.defaultProxyProfile.handle(),
                        0, 0, 0, 0
                    ));
                prop.put("result", reasonString == null ? ("added url to indexer: " + load) : ("not indexed url '" + load + "': " + reasonString));
                if (wait) for (int i = 0; i < 30; i++) {
                    if (sb.index.exists(url.hash())) break;
                    try {Thread.sleep(100);} catch (InterruptedException e) {}
                }
            } catch (MalformedURLException e) {
                prop.put("result", "bad url '" + load + "'");
            }
        }
        
        if (post.containsKey("hosts")) {
            // generate host list
            try {
                int maxcount = 200;
                ReversibleScoreMap<String> score = fulltext.getSolr().getFacet(YaCySchema.host_s.name(), maxcount);
                int c = 0;
                Iterator<String> i = score.keys(false);
                String host;
                while (i.hasNext() && c < maxcount) {
                    host = i.next();
                    prop.put("hosts_list_" + c + "_host", host);
                    prop.put("hosts_list_" + c + "_count", score.get(host));
                    c++;
                }
                prop.put("hosts_list", c);
                prop.put("hosts", 1);
            } catch (IOException e) {
                Log.logException(e);
            }
        }
        
        if (path.length() > 0) {

            p = path.substring(0, path.length() - 1).lastIndexOf('/');
            if (p < 8) {
                prop.put("files_root", 1);
            } else {
                prop.put("files_root", 0);
                prop.put("files_root_path", path.substring(0, p + 1));
            }
            try {
                // generate file list from path
                DigestURI uri = new DigestURI(path);
                String host = uri.getHost();
                prop.putHTML("outbound_host", host);
                prop.putHTML("inbound_host", host);
                String hosthash = ASCII.String(uri.hash(), 6, 6);
                
                // get all files for a specific host from the index
                BlockingQueue<SolrDocument> docs = fulltext.getSolr().concurrentQuery(YaCySchema.host_s.name() + ":" + host, 0, 100000, 60000);
                SolrDocument doc;
                Set<String> storedDocs = new HashSet<String>();
                Set<String> inboundLinks = new HashSet<String>();
                Map<String, ReversibleScoreMap<String>> outboundHosts = new HashMap<String, ReversibleScoreMap<String>>();
                int hostsize = 0;
                while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                    String u = (String) doc.getFieldValue(YaCySchema.sku.name());
                    hostsize++;
                    if (u.startsWith(path)) storedDocs.add(u);
                    // collect inboundlinks to browse the host
                    Iterator<String> links = SolrConfiguration.getLinks(doc, true);
                    while (links.hasNext()) {
                        u = links.next();
                        if (u.startsWith(path) && !storedDocs.contains(u)) inboundLinks.add(u);
                    }
                    
                    // collect outboundlinks to browse to the outbound
                    links = SolrConfiguration.getLinks(doc, false);
                    while (links.hasNext()) {
                        u = links.next();
                        try {
                            MultiProtocolURI mu = new MultiProtocolURI(u);
                            if (mu.getHost() != null) {
                                ReversibleScoreMap<String> lks = outboundHosts.get(mu.getHost());
                                if (lks == null) {
                                    lks = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
                                    outboundHosts.put(mu.getHost(), lks);
                                }
                                lks.set(u, u.length());
                            }
                        } catch (MalformedURLException e) {}
                    }
                }
                // now combine both lists into one
                Map<String, Boolean> files = new HashMap<String, Boolean>();
                for (String u: storedDocs) files.put(u, true);
                for (String u: inboundLinks) if (!storedDocs.contains(u)) files.put(u, false);
                
                // distinguish files and folders
                Map<String, Object> list = new TreeMap<String, Object>();
                for (Map.Entry<String, Boolean> entry: files.entrySet()) {
                    String file = entry.getKey().substring(path.length());
                    p = file.indexOf('/');
                    if (p < 0) {
                        // this is a file in the root path
                        list.put(entry.getKey(), entry.getValue()); // Boolean value: this is a file
                    } else {
                        // this is a directory path
                        String dir = path + file.substring(0, p + 1);
                        Object c = list.get(dir);
                        if (c == null) {
                            int[] linkedStored = new int[]{0,0};
                            linkedStored[entry.getValue().booleanValue() ? 1 : 0]++;
                            list.put(dir, linkedStored);
                        } else if (c instanceof int[]) {
                            ((int[]) c)[entry.getValue().booleanValue() ? 1 : 0]++;
                        }
                    }
                }
                
                int maxcount = 1000;
                int c = 0;
                for (Map.Entry<String, Object> entry: list.entrySet()) {
                    if (entry.getValue() instanceof Boolean) {
                        // this is a file
                        prop.put("files_list_" + c + "_type", 0);
                        prop.put("files_list_" + c + "_type_file", entry.getKey());
                        boolean indexed = ((Boolean) entry.getValue()).booleanValue();
                        try {uri = new DigestURI(entry.getKey());} catch (MalformedURLException e) {uri = null;}
                        boolean loading = load.equals(entry.getKey()) ||
                                (uri != null && sb.crawlQueues.urlExists(uri.hash()) != null);
                        //String failr = fulltext.failReason(ASCII.String(uri.hash()));
                        prop.put("files_list_" + c + "_type_stored", indexed ? 1 : loading ? 2 : 0);
                        prop.put("files_list_" + c + "_type_stored_load", loadRight ? 1 : 0);
                        if (loadRight) {
                            prop.put("files_list_" + c + "_type_stored_load_file", entry.getKey());
                            prop.put("files_list_" + c + "_type_stored_load_path", path);
                        }
                    } else {
                        // this is a folder
                        prop.put("files_list_" + c + "_type", 1);
                        prop.put("files_list_" + c + "_type_file", entry.getKey());
                        prop.put("files_list_" + c + "_type_count", ((int[]) entry.getValue())[1] + " stored / " + ((int[]) entry.getValue())[0] + " linked");
                    }
                    if (++c >= maxcount) break;
                }
                prop.put("files_list", c);
                prop.putHTML("files_path", path);
                prop.put("files_hostsize", hostsize);
                prop.put("files_subpathsize", storedDocs.size());
                prop.put("files", 1);

                // generate inbound-links table
                StructureEntry struct = sb.webStructure.incomingReferences(hosthash);
                if (struct != null && struct.references.size() > 0) {
                    maxcount = 200;
                    ReversibleScoreMap<String> score = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
                    for (Map.Entry<String, Integer> entry: struct.references.entrySet()) score.set(entry.getKey(), entry.getValue());
                    c = 0;
                    Iterator<String> i = score.keys(false);
                    while (i.hasNext() && c < maxcount) {
                        host = i.next();
                        prop.put("inbound_list_" + c + "_host", sb.webStructure.hostHash2hostName(host));
                        prop.put("inbound_list_" + c + "_count", score.get(host));
                        c++;
                    }
                    prop.put("inbound_list", c);
                    prop.put("inbound", 1);
                } else {
                    prop.put("inbound", 0);
                }
                
                // generate outbound-links table
                if (outboundHosts.size() > 0) {
                    maxcount = 200;
                    ReversibleScoreMap<String> score = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
                    for (Map.Entry<String, ReversibleScoreMap<String>> entry: outboundHosts.entrySet()) score.set(entry.getKey(), entry.getValue().size());
                    c = 0;
                    Iterator<String> i = score.keys(false);
                    while (i.hasNext() && c < maxcount) {
                        host = i.next();
                        prop.put("outbound_list_" + c + "_host", host);
                        prop.put("outbound_list_" + c + "_count", score.get(host));
                        prop.put("outbound_list_" + c + "_link", outboundHosts.get(host).getMinKey());
                        c++;
                    }
                    prop.put("outbound_list", c);
                    prop.put("outbound", 1);
                } else {
                    prop.put("outbound", 0);
                }
                
            } catch (Throwable e) {
                Log.logException(e);
            }
        }

        // insert constants
        prop.putNum("ucount", fulltext.size());
        // return rewrite properties
        return prop;
    }


}
