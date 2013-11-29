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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.FailType;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.HarvestProcess;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.peers.graphics.WebStructureGraph.StructureEntry;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment.ReferenceReport;
import net.yacy.search.index.Segment.ReferenceReportCache;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class HostBrowser {
    
    final static long TIMEOUT = 10000L;
    
    public static enum StoreType {
        LINK, INDEX, EXCLUDED, FAILED;
    }
    
    @SuppressWarnings("deprecation")
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
        prop.put("hosts", 0);
        prop.put("files", 0);
        prop.put("admin", admin ? 1 : 0);

        if (admin) { // show top nav to admins
            prop.put("topmenu",1);
        } else { // for other respect setting in Search Design Configuration
            prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);
        }
        
        if (!searchAllowed) {
            prop.put("result", "You are not allowed to use this page. Please ask an administrator for permission.");
            prop.putNum("ucount", 0);
            return prop;
        }

        String path = post == null ? "" : post.get("path", "").trim();
        if (admin) sb.index.fulltext().commit(true);
        if (post == null || env == null) {
            prop.putNum("ucount", fulltext.collectionSize());
            return prop;
        }

        int p = path.lastIndexOf('/');
        if (p < 0 && path.length() > 0) path = path + "/"; else if (p > 7) path = path.substring(0, p + 1); // the search path shall always end with "/"
        if (path.length() > 0 && (
            !path.startsWith("http://") &&
            !path.startsWith("https://") &&
            !path.startsWith("ftp://") &&
            !path.startsWith("smb://") &&
            !path.startsWith("file://"))) { path = "http://" + path; }
        prop.putHTML("path", path);
        prop.put("delete", admin && path.length() > 0 ? 1 : 0);
        
        DigestURL pathURI = null;
        try {pathURI = new DigestURL(path);} catch (final MalformedURLException e) {}

        String load = post.get("load", "");
        boolean wait = false;
        if (loadRight && autoload && path.length() != 0 && pathURI != null && load.length() == 0 && !sb.index.exists(ASCII.String(pathURI.hash()))) {
            // in case that the url does not exist and loading is wanted turn this request into a loading request
            load = path;
            wait = true;
        }
        if (load.length() > 0 && loadRight) {
            // stack URL
            DigestURL url;
            if (sb.crawlStacker.size() > 2) wait = false;
            try {
                url = new DigestURL(load);
                String reasonString = sb.crawlStacker.stackCrawl(new Request(
                        sb.peers.mySeed().hash.getBytes(),
                        url, null, load, new Date(),
                        sb.crawler.defaultProxyProfile.handle(),
                        0, 0, 0, 0
                    ));
                prop.putHTML("result", reasonString == null ? ("added url to indexer: " + load) : ("not indexed url '" + load + "': " + reasonString));
                if (wait) for (int i = 0; i < 30; i++) {
                    if (sb.index.exists(ASCII.String(url.hash()))) break;
                    try {Thread.sleep(100);} catch (final InterruptedException e) {}
                }
            } catch (final MalformedURLException e) {
                prop.putHTML("result", "bad url '" + load + "'");
            }
        }

        if (admin && post.containsKey("deleteLoadErrors")) {
            try {
                fulltext.getDefaultConnector().deleteByQuery("-" + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200 AND " 
                        + CollectionSchema.httpstatus_i.getSolrFieldName() + ":[* TO *]"); // make sure field exists
                ConcurrentLog.info ("HostBrowser:", "delete documents with httpstatus_i <> 200");
                fulltext.getDefaultConnector().deleteByQuery(CollectionSchema.failtype_s.getSolrFieldName() + ":\"" + FailType.fail.name() + "\"" );
                ConcurrentLog.info ("HostBrowser:", "delete documents with failtype_s = fail");
                fulltext.getDefaultConnector().deleteByQuery(CollectionSchema.failtype_s.getSolrFieldName() + ":\"" + FailType.excl.name() + "\"" );
                ConcurrentLog.info ("HostBrowser:", "delete documents with failtype_s = excl");
                prop.putNum("ucount", fulltext.collectionSize());
                return prop;
            } catch (final IOException ex) {
                ConcurrentLog.logException(ex);
            }
        }
        
        if (post.containsKey("hosts")) {
            // generate host list
            try {
                boolean onlyCrawling = "crawling".equals(post.get("hosts", ""));
                boolean onlyErrors = "error".equals(post.get("hosts", ""));
                
                int maxcount = admin ? 2 * 3 * 2 * 5 * 7 * 2 * 3 : 360; // which makes nice matrixes for 2, 3, 4, 5, 6, 7, 8, 9 rows/colums
                
                // collect hosts from index
                ReversibleScoreMap<String> hostscore = fulltext.getDefaultConnector().getFacets(AbstractSolrConnector.CATCHALL_TERM, maxcount, CollectionSchema.host_s.getSolrFieldName()).get(CollectionSchema.host_s.getSolrFieldName());
                if (hostscore == null) hostscore = new ClusteredScoreMap<String>();
                
                // collect hosts from crawler
                final Map<String, Integer[]> crawler = (admin) ? sb.crawlQueues.noticeURL.getDomainStackHosts(StackType.LOCAL, sb.robots) : new HashMap<String, Integer[]>();
                for (Map.Entry<String, Integer[]> host: crawler.entrySet()) {
                    hostscore.inc(host.getKey(), host.getValue()[0]);
                }
                
                // collect the errorurls
                Map<String, ReversibleScoreMap<String>> exclfacets = admin ? fulltext.getDefaultConnector().getFacets(CollectionSchema.failtype_s.getSolrFieldName() + ":" + FailType.excl.name(), maxcount, CollectionSchema.host_s.getSolrFieldName()) : null;
                ReversibleScoreMap<String> exclscore = exclfacets == null ? new ClusteredScoreMap<String>() : exclfacets.get(CollectionSchema.host_s.getSolrFieldName());
                Map<String, ReversibleScoreMap<String>> failfacets = admin ? fulltext.getDefaultConnector().getFacets(CollectionSchema.failtype_s.getSolrFieldName() + ":" + FailType.fail.name(), maxcount, CollectionSchema.host_s.getSolrFieldName()) : null;
                ReversibleScoreMap<String> failscore = failfacets == null ? new ClusteredScoreMap<String>() : failfacets.get(CollectionSchema.host_s.getSolrFieldName());
                
                int c = 0;
                Iterator<String> i = hostscore.keys(false);
                String host;
                while (i.hasNext() && c < maxcount) {
                    host = i.next();
                    prop.putHTML("hosts_list_" + c + "_host", host);
                    boolean inCrawler = crawler.containsKey(host);
                    int exclcount = exclscore.get(host);
                    int failcount = failscore.get(host);
                    int errors = exclcount + failcount;
                    prop.put("hosts_list_" + c + "_count", hostscore.get(host) - errors);
                    prop.put("hosts_list_" + c + "_crawler", inCrawler ? 1 : 0);
                    if (inCrawler) prop.put("hosts_list_" + c + "_crawler_pending", crawler.get(host)[0]);
                    prop.put("hosts_list_" + c + "_errors", errors > 0 ? 1 : 0);
                    if (errors > 0) {
                        prop.put("hosts_list_" + c + "_errors_exclcount", exclcount);
                        prop.put("hosts_list_" + c + "_errors_failcount", failcount);
                    }
                    prop.put("hosts_list_" + c + "_type", inCrawler ? 2 : errors > 0 ? 1 : 0);
                    if (onlyCrawling) {
                        if (inCrawler) c++;
                    } else if (onlyErrors) {
                        if (errors > 0) c++;
                    } else {
                        c++;
                    }
                }
                prop.put("hosts_list", c);
                prop.put("hosts", 1);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
        
        if (path.length() > 0) {
            boolean delete = false;
            if (admin && post.containsKey("delete")) {
                // delete the complete path!! That includes everything that matches with this prefix.
                delete = true;
            }
            int facetcount=post.getInt("facetcount", 0);
            boolean complete = post.getBoolean("complete");
            if (complete) { // we want only root paths for complete lists
                p = path.indexOf('/', 10);
                if (p > 0) path = path.substring(0, p + 1);
            }
            prop.put("files_complete", complete ? 1 : 0);
            prop.putHTML("files_complete_path", path);
            p = path.substring(0, path.length() - 1).lastIndexOf('/');
            if (p < 8) {
                prop.put("files_root", 1);
            } else {
                prop.put("files_root", 0);
                prop.putHTML("files_root_path", path.substring(0, p + 1));
            }
            try {
                // generate file list from path
                DigestURL uri = new DigestURL(path);
                String host = uri.getHost();
                prop.putHTML("outbound_host", host);
                if (admin) prop.putHTML("outbound_admin_host", host); //used for WebStructurePicture_p link
                prop.putHTML("inbound_host", host);
                String hosthash = ASCII.String(uri.hash(), 6, 6);
                String[] pathparts = uri.getPaths();
                
                // get all files for a specific host from the index
                StringBuilder q = new StringBuilder();
                q.append(CollectionSchema.host_s.getSolrFieldName()).append(':').append(host);
                if (pathparts.length > 0 && pathparts[0].length() > 0) {
                    for (String pe: pathparts) {
                        if (pe.length() > 0) q.append(" AND ").append(CollectionSchema.url_paths_sxt.getSolrFieldName()).append(":\"").append(pe).append('\"');
                    }
                } else {
                    if (facetcount > 1000 || post.containsKey("nepr")) {
                        q.append(" AND ").append(CollectionSchema.url_paths_sxt.getSolrFieldName()).append(":[* TO *]");
                    }
                }
                BlockingQueue<SolrDocument> docs = fulltext.getDefaultConnector().concurrentDocumentsByQuery(q.toString(), 0, 100000, TIMEOUT, 100,
                        CollectionSchema.id.getSolrFieldName(),
                        CollectionSchema.sku.getSolrFieldName(),
                        CollectionSchema.failreason_s.getSolrFieldName(),
                        CollectionSchema.failtype_s.getSolrFieldName(),
                        CollectionSchema.inboundlinks_protocol_sxt.getSolrFieldName(),
                        CollectionSchema.inboundlinks_urlstub_sxt.getSolrFieldName(),
                        CollectionSchema.outboundlinks_protocol_sxt.getSolrFieldName(),
                        CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName(),
                        CollectionSchema.clickdepth_i.getSolrFieldName(),
                        CollectionSchema.references_i.getSolrFieldName(),
                        CollectionSchema.references_internal_i.getSolrFieldName(),
                        CollectionSchema.references_external_i.getSolrFieldName(),
                        CollectionSchema.references_exthosts_i.getSolrFieldName(),
                        CollectionSchema.cr_host_chance_d.getSolrFieldName(),
                        CollectionSchema.cr_host_norm_i.getSolrFieldName()   
                        );
                SolrDocument doc;
                Set<String> storedDocs = new HashSet<String>();
                Map<String, FailType> errorDocs = new HashMap<String, FailType>();
                Set<String> inboundLinks = new HashSet<String>();
                Map<String, ReversibleScoreMap<String>> outboundHosts = new HashMap<String, ReversibleScoreMap<String>>();
                Map<String, InfoCacheEntry> infoCache = new HashMap<String, InfoCacheEntry>();
                int hostsize = 0;
                final List<String> deleteIDs = new ArrayList<String>();
                long timeoutList = System.currentTimeMillis() + TIMEOUT;
                long timeoutReferences = System.currentTimeMillis() + 3000;
                ReferenceReportCache rrCache = sb.index.getReferenceReportCache();
                while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                    String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                    String errortype = (String) doc.getFieldValue(CollectionSchema.failtype_s.getSolrFieldName());
                    FailType error = errortype == null ? null : FailType.valueOf(errortype);
                    String ids = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                    infoCache.put(ids, new InfoCacheEntry(sb.index.fulltext(), rrCache, doc, ids, System.currentTimeMillis() < timeoutReferences));
                    if (u.startsWith(path)) {
                        if (delete) {
                            deleteIDs.add(ids);
                        } else {
                            if (error == null) storedDocs.add(u); else if (admin) errorDocs.put(u, error);
                        }
                    } else if (complete) {
                        if (error == null) storedDocs.add(u); else if (admin) errorDocs.put(u, error);
                    }
                    if ((complete || u.startsWith(path)) && !storedDocs.contains(u)) inboundLinks.add(u); // add the current link
                    if (error == null) {
                        hostsize++;
                        // collect inboundlinks to browse the host
                        Iterator<String> links = URIMetadataNode.getLinks(doc, true);
                        while (links.hasNext()) {
                            u = links.next();
                            if ((complete || u.startsWith(path)) && !storedDocs.contains(u)) inboundLinks.add(u);
                        }
                        
                        // collect referrer links
                        links = URIMetadataNode.getLinks(doc, false);
                        while (links.hasNext()) {
                            u = links.next();
                            try {
                                MultiProtocolURL mu = new MultiProtocolURL(u);
                                if (mu.getHost() != null) {
                                    ReversibleScoreMap<String> lks = outboundHosts.get(mu.getHost());
                                    if (lks == null) {
                                        lks = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
                                        outboundHosts.put(mu.getHost(), lks);
                                    }
                                    lks.set(u, u.length());
                                }
                            } catch (final MalformedURLException e) {}
                        }
                    }
                    if (System.currentTimeMillis() > timeoutList) break;
                }
                if (deleteIDs.size() > 0) sb.remove(deleteIDs);
                
                // collect from crawler
                List<Request> domainStackReferences = (admin) ? sb.crawlQueues.noticeURL.getDomainStackReferences(StackType.LOCAL, host, 1000, 3000) : new ArrayList<Request>(0);
                Set<String> loadingLinks = new HashSet<String>();
                for (Request crawlEntry: domainStackReferences) loadingLinks.add(crawlEntry.url().toNormalform(true));
                
                // now combine all lists into one
                Map<String, StoreType> files = new HashMap<String, StoreType>();
                for (String u: storedDocs) files.put(u, StoreType.INDEX);
                for (Map.Entry<String, FailType> e: errorDocs.entrySet()) files.put(e.getKey(), e.getValue() == FailType.fail ? StoreType.FAILED : StoreType.EXCLUDED);
                for (String u: inboundLinks) if (!files.containsKey(u)) files.put(u, StoreType.LINK);
                for (String u: loadingLinks) if (u.startsWith(path) && !files.containsKey(u)) files.put(u, StoreType.LINK);
                ConcurrentLog.info("HostBrowser", "collected " + files.size() + " urls for path " + path);

                // distinguish files and folders
                Map<String, Object> list = new TreeMap<String, Object>(); // a directory list; if object is boolean, its a file; if its a int[], then its a folder
                int pl = path.length();
                String file;
                for (Map.Entry<String, StoreType> entry: files.entrySet()) {
                    if (entry.getKey().length() < pl) continue; // this is not inside the path
                    if (!entry.getKey().startsWith(path)) continue;
                    file = entry.getKey().substring(pl);
                    StoreType type = entry.getValue();
                    p = file.indexOf('/');
                    if (p < 0) {
                        // this is a file
                        list.put(entry.getKey(), type); // StoreType value: this is a file; true -> file is in index; false -> not in index, maybe in crawler
                    } else {
                        // this is a directory path or a file in a subdirectory
                        String remainingPath = file.substring(0, p + 1);
                        if (complete && remainingPath.indexOf('.') > 0) {
                            list.put(entry.getKey(), type); // StoreType value: this is a file
                        } else {
                            String dir = path + remainingPath;
                            Object c = list.get(dir);
                            if (c == null) {
                                int[] linkedStoredIncrawlerError = new int[]{0,0,0,0};
                                if (type == StoreType.LINK) linkedStoredIncrawlerError[0]++;
                                if (type == StoreType.INDEX) linkedStoredIncrawlerError[1]++;
                                if (loadingLinks.contains(entry.getKey())) linkedStoredIncrawlerError[2]++;
                                if (errorDocs.containsKey(entry.getKey())) linkedStoredIncrawlerError[3]++;
                                list.put(dir, linkedStoredIncrawlerError);
                            } else if (c instanceof int[]) {
                                if (type == StoreType.LINK) ((int[]) c)[0]++;
                                if (type == StoreType.INDEX) ((int[]) c)[1]++;
                                if (loadingLinks.contains(entry.getKey())) ((int[]) c)[2]++;
                                if (errorDocs.containsKey(entry.getKey())) ((int[]) c)[3]++;
                            }
                        }
                    }
                }
                
                int maxcount = 1000;
                int c = 0;
                // first list only folders
                int filecounter = 0;
                for (Map.Entry<String, Object> entry: list.entrySet()) {
                    if ((entry.getValue() instanceof StoreType)) {
                        filecounter++;
                    } else {
                        // this is a folder
                        prop.put("files_list_" + c + "_type", 1);
                        prop.putHTML("files_list_" + c + "_type_url", entry.getKey());
                        int linked = ((int[]) entry.getValue())[0];
                        int stored = ((int[]) entry.getValue())[1];
                        int crawler = ((int[]) entry.getValue())[2];
                        int error = ((int[]) entry.getValue())[3];
                        prop.put("files_list_" + c + "_type_stored", stored);
                        prop.put("files_list_" + c + "_type_linked", linked);
                        prop.put("files_list_" + c + "_type_pendingVisible", crawler > 0 ? 1 : 0);
                        prop.put("files_list_" + c + "_type_pending", crawler);
                        prop.put("files_list_" + c + "_type_excludedVisible", 0);
                        prop.put("files_list_" + c + "_type_excluded", 0);
                        prop.put("files_list_" + c + "_type_failedVisible", error > 0 ? 1 : 0);
                        prop.put("files_list_" + c + "_type_failed", error);
                        if (++c >= maxcount) break;
                    }
                }
                // then list files
                for (Map.Entry<String, Object> entry: list.entrySet()) {
                    if (entry.getValue() instanceof StoreType) {
                        // this is a file
                        prop.put("files_list_" + c + "_type", 0);
                        prop.putHTML("files_list_" + c + "_type_url", entry.getKey());
                        StoreType type = (StoreType) entry.getValue();
                        try {uri = new DigestURL(entry.getKey());} catch (final MalformedURLException e) {uri = null;}
                        HarvestProcess process = uri == null ? null : sb.crawlQueues.exists(uri.hash());
                        boolean loading = load.equals(entry.getKey()) || (process != null && process != HarvestProcess.ERRORS);
                        boolean error =  process == HarvestProcess.ERRORS || type == StoreType.EXCLUDED || type == StoreType.FAILED;
                        boolean dc = type != StoreType.INDEX && !error && !loading && list.containsKey(entry.getKey() + "/");
                        if (!dc) {
                            prop.put("files_list_" + c + "_type_stored", type == StoreType.INDEX ? 1 : error ? 3 : loading ? 2 : 0 /*linked*/);
                            if (type == StoreType.INDEX) {
                                String ids = ASCII.String(uri.hash());
                                InfoCacheEntry ice = infoCache.get(ids);
                                prop.put("files_list_" + c + "_type_stored_comment", ice.toString()); // ice.toString() contains html, therefore do not use putHTML here
                            }
                            prop.put("files_list_" + c + "_type_stored_load", loadRight ? 1 : 0);
                            if (error) {
                                FailType failType = errorDocs.get(entry.getKey());
                                if (failType == null) {
                                    // maybe this is only in the errorURL
                                    prop.putHTML("files_list_" + c + "_type_stored_error", process == HarvestProcess.ERRORS ? sb.crawlQueues.errorURL.get(ASCII.String(uri.hash())).getFailReason() : "unknown error");
                                } else {
                                    String ids = ASCII.String(uri.hash());
                                    InfoCacheEntry ice = infoCache.get(ids);
                                    prop.put("files_list_" + c + "_type_stored_error", failType == FailType.excl ? "excluded from indexing" : "load fail; " + ice.toString());
                                }
                            }
                            if (loadRight) {
                                prop.putHTML("files_list_" + c + "_type_stored_load_url", entry.getKey());
                                prop.putHTML("files_list_" + c + "_type_stored_load_path", path);
                            }
                            if (++c >= maxcount) break;
                        }
                    }
                }
                prop.put("files_list", c);
                prop.putHTML("files_path", path);
                prop.put("files_hostsize", hostsize);
                prop.put("files_subpathloadsize", storedDocs.size());
                prop.put("files_subpathdetectedsize", filecounter - storedDocs.size());
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
                        prop.putHTML("inbound_list_" + c + "_host", sb.webStructure.hostHash2hostName(host));
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
                        prop.putHTML("outbound_list_" + c + "_host", host);
                        prop.put("outbound_list_" + c + "_count", score.get(host));
                        prop.put("outbound_list_" + c + "_link", outboundHosts.get(host).getMinKey());
                        c++;
                    }
                    prop.put("outbound_list", c);
                    prop.put("outbound", 1);
                } else {
                    prop.put("outbound", 0);
                }
                
            } catch (final Throwable e) {
                ConcurrentLog.logException(e);
            }
        }

        // return rewrite properties
        prop.putNum("ucount", fulltext.collectionSize());
        return prop;
    }

    public static final class InfoCacheEntry {
        public Integer cr_n;
        public Double  cr_c;
        public int clickdepth, references, references_internal, references_external, references_exthosts;
        public List<String> references_internal_urls, references_external_urls;
        public InfoCacheEntry(final Fulltext fulltext, final ReferenceReportCache rrCache, final SolrDocument doc, final String urlhash, boolean fetchReferences) {
            this.cr_c = (Double) doc.getFieldValue(CollectionSchema.cr_host_chance_d.getSolrFieldName());
            this.cr_n = (Integer) doc.getFieldValue(CollectionSchema.cr_host_norm_i.getSolrFieldName());            
            Integer cd = (Integer) doc.getFieldValue(CollectionSchema.clickdepth_i.getSolrFieldName());
            Integer rc = (Integer) doc.getFieldValue(CollectionSchema.references_i.getSolrFieldName());
            Integer rc_internal = (Integer) doc.getFieldValue(CollectionSchema.references_internal_i.getSolrFieldName());
            Integer rc_external = (Integer) doc.getFieldValue(CollectionSchema.references_external_i.getSolrFieldName());
            Integer rc_exthosts = (Integer) doc.getFieldValue(CollectionSchema.references_exthosts_i.getSolrFieldName());
            this.clickdepth = (cd == null || cd.intValue() < 0) ? 999 : cd.intValue();
            this.references = (rc == null || rc.intValue() <= 0) ? 0 : rc.intValue();
            this.references_internal = (rc_internal == null || rc_internal.intValue() <= 0) ? 0 : rc_internal.intValue();
            // calculate the url reference list
            this.references_internal_urls = new ArrayList<String>();
            this.references_external_urls = new ArrayList<String>();
            if (fetchReferences) {
                // get the references from the citation index
                try {
                    ReferenceReport rr = rrCache.getReferenceReport(ASCII.getBytes(urlhash), false);
                    List<String> internalIDs = new ArrayList<String>();
                    List<String> externalIDs = new ArrayList<String>();
                    HandleSet iids = rr.getInternallIDs();
                    for (byte[] b: iids) internalIDs.add(ASCII.String(b));
                    HandleSet eids = rr.getExternalIDs();
                    for (byte[] b: eids) externalIDs.add(ASCII.String(b));
                    // get all urls from the index and store them here
                    for (String id: internalIDs) {
                        if (id.equals(urlhash)) continue; // no self-references
                        DigestURL u = fulltext.getURL(ASCII.getBytes(id));
                        if (u != null) references_internal_urls.add(u.toNormalform(true));
                    }
                    for (String id: externalIDs) {
                        if (id.equals(urlhash)) continue; // no self-references
                        DigestURL u = fulltext.getURL(ASCII.getBytes(id));
                        if (u != null) references_external_urls.add(u.toNormalform(true));
                    }
                } catch (final IOException e) {
                }
            }
            this.references_external = (rc_external == null || rc_external.intValue() <= 0) ? 0 : rc_external.intValue();
            this.references_exthosts = (rc_exthosts == null || rc_exthosts.intValue() <= 0) ? 0 : rc_exthosts.intValue();
        }
        public String toString() {
            StringBuilder sbi = new StringBuilder();
            int c = 0;
            for (String s: references_internal_urls) {
                sbi.append("<a href='").append(s).append("' target='_blank'><img src='env/grafics/i16.gif' alt='info' title='" + s + "' width='12' height='12'/></a>");
                c++;
                if (c % 80 == 0) sbi.append("<br/>");
            }
            if (sbi.length() > 0) sbi.insert(0, "<br/>internal referrer:</br>");
            StringBuilder sbe = new StringBuilder();
            c = 0;
            for (String s: references_external_urls) {
                sbe.append("<a href='").append(s).append("' target='_blank'><img src='env/grafics/i16.gif' alt='info' title='" + s + "' width='12' height='12'/></a>");
                c++;
                if (c % 80 == 0) sbe.append("<br/>");
            }
            if (sbe.length() > 0) sbe.insert(0, "<br/>external referrer:</br>");
            return
                    (this.clickdepth >= 0 ?
                            "clickdepth: " + this.clickdepth :
                            "") +
                    (this.cr_c != null ? ", cr=" + (Math.round(this.cr_c * 1000.0d) / 1000.0d) : "") +
                    (this.cr_n != null ? ", crn=" + this.cr_n : "") +
                    (this.references >= 0 ?
                            ", refs: " + this.references_exthosts + " hosts, " + this.references_external + " ext, " + this.references_internal + " int" + sbi.toString() + sbe.toString() :
                            "");
        }
    }

}
