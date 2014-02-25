// IndexControlRWIs_p.java
// -----------------------
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.FieldCache;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexControlURLs_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();

        Segment segment = sb.index;

        // set default values
        prop.put("urlstring", "");
        prop.put("urlhash", "");
        prop.put("result", "");
        prop.put("otherHosts", "");
        prop.put("genUrlProfile", 0);
        prop.put("statistics", 1);
        prop.put("statistics_lines", 100);
        prop.put("statisticslines", 0);
        prop.put("reload", 0);
        prop.put("indexdump", 0);
        prop.put("lurlexport", 0);
        prop.put("reload", 0);
        prop.put("dumprestore", 1);
        List<File> dumpFiles =  segment.fulltext().dumpFiles();
        prop.put("dumprestore_dumpfile", dumpFiles.size() == 0 ? "" : dumpFiles.get(dumpFiles.size() - 1).getAbsolutePath());
        prop.put("dumprestore_optimizemax", 10);
        prop.put("cleanup", post == null || post.size() == 0 ? 1 : 0);
        prop.put("cleanup_solr", segment.fulltext().connectedRemoteSolr() ? 1 : 0);
        prop.put("cleanup_rwi", segment.termIndex() != null && !segment.termIndex().isEmpty() ? 1 : 0);
        prop.put("cleanup_citation", segment.connectedCitation() && !segment.urlCitation().isEmpty() ? 1 : 0);
        
        // show export messages
        final Fulltext.Export export = segment.fulltext().export();
        if ((export != null) && (export.isAlive())) {
        	// there is currently a running export
            prop.put("lurlexport", 2);
            prop.put("lurlexportfinished", 0);
    		prop.put("lurlexporterror", 0);
    		prop.put("lurlexport_exportfile", export.file().toString());
            prop.put("lurlexport_urlcount", export.count());
            prop.put("reload", 1);
        } else {
            prop.put("lurlexport", 1);
            prop.put("lurlexport_exportfile", sb.getDataPath() + "/DATA/EXPORT/" + GenericFormatter.SHORT_SECOND_FORMATTER.format());
            if (export == null) {
                // there has never been an export
                prop.put("lurlexportfinished", 0);
                prop.put("lurlexporterror", 0);
            } else {
                // an export was running but has finished
                prop.put("lurlexportfinished", 1);
                prop.put("lurlexportfinished_exportfile", export.file().toString());
                prop.put("lurlexportfinished_urlcount", export.count());
                if (export.failed() == null) {
                    prop.put("lurlexporterror", 0);
                } else {
                    prop.put("lurlexporterror", 1);
                    prop.put("lurlexporterror_exportfile", export.file().toString());
                    prop.put("lurlexporterror_exportfailmsg", export.failed());
                }
            }
        }

        if (post == null || env == null) {
            prop.putNum("ucount", segment.fulltext().collectionSize());
            return prop; // nothing to do
        }

        // post values that are set on numerous input fields with same name
        String urlstring = post.get("urlstring", "").trim();
        String urlhash = post.get("urlhash", "").trim();
        if (urlhash.isEmpty() && urlstring.length() > 0) {
            try {
                urlhash = ASCII.String(new DigestURL(urlstring).hash());
            } catch (final MalformedURLException e) {
            }
        }

        if (!urlstring.startsWith("http://") &&
            !urlstring.startsWith("https://") &&
            !urlstring.startsWith("ftp://") &&
            !urlstring.startsWith("smb://") &&
            !urlstring.startsWith("file://")) { urlstring = "http://" + urlstring; }

        prop.putHTML("urlstring", urlstring);
        prop.putHTML("urlhash", urlhash);
        prop.put("result", " ");

        // delete everything
        if ( post.containsKey("deletecomplete") ) {
            if ( post.get("deleteIndex", "").equals("on") ) {
                try {segment.fulltext().clearLocalSolr();} catch (final IOException e) {}
            }
            if ( post.get("deleteRemoteSolr", "").equals("on")) {
                try {segment.fulltext().clearRemoteSolr();} catch (final IOException e) {}
            }
            if ( post.get("deleteRWI", "").equals("on")) {
                if (segment.termIndex() != null) try {segment.termIndex().clear();} catch (final IOException e) {}
            }
            if ( post.get("deleteCitation", "").equals("on")) {
                if (segment.connectedCitation()) try {segment.urlCitation().clear();} catch (final IOException e) {}
            }
            if ( post.get("deleteCrawlQueues", "").equals("on") ) {
                sb.crawlQueues.clear();
                sb.crawlStacker.clear();
                ResultURLs.clearStacks();
            }
            if ( post.get("deleteCache", "").equals("on") ) {
                Cache.clear();
            }
            if ( post.get("deleteRobots", "").equals("on") ) {
                try {sb.robots.clear();} catch (final IOException e) {}
            }
            if ( post.get("deleteSearchFl", "").equals("on") ) {
                sb.tables.clear(WorkTables.TABLE_SEARCH_FAILURE_NAME);
            }
            post.remove("deletecomplete");
        }

        if (post.containsKey("urlhashdeleteall")) {
            ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
            int i = segment.removeAllUrlReferences(urlhash.getBytes(), sb.loader, agent, CacheStrategy.IFEXIST);
            prop.put("result", "Deleted URL and " + i + " references from " + i + " word indexes.");
        }

        if (post.containsKey("urlhashdelete")) {
            final DigestURL url = segment.fulltext().getURL(urlhash);
            if (url == null) {
                prop.putHTML("result", "No Entry for URL hash " + urlhash + "; nothing deleted.");
            } else {
                urlstring = url.toNormalform(true);
                prop.put("urlstring", "");
                sb.urlRemove(segment, urlhash.getBytes());
                prop.putHTML("result", "Removed URL " + urlstring);
            }
        }

        if (post.containsKey("urldelete")) {
            try {
                urlhash = ASCII.String((new DigestURL(urlstring)).hash());
            } catch (final MalformedURLException e) {
                urlhash = null;
            }
            if ((urlhash == null) || (urlstring == null)) {
                prop.put("result", "No input given; nothing deleted.");
            } else {
                sb.urlRemove(segment, urlhash.getBytes());
                prop.putHTML("result", "Removed URL " + urlstring);
            }
        }

        if (post.containsKey("urlstringsearch")) {
            try {
                final DigestURL url = new DigestURL(urlstring);
                urlhash = ASCII.String(url.hash());
                prop.put("urlhash", urlhash);
                final URIMetadataNode entry = segment.fulltext().getMetadata(ASCII.getBytes(urlhash));
                if (entry == null) {
                    prop.putHTML("result", "No Entry for URL " + url.toNormalform(true));
                    prop.putHTML("urlstring", urlstring);
                    prop.put("urlhash", "");
                } else {
                    prop.putAll(genUrlProfile(segment, entry, urlhash));
                    prop.put("statistics", 0);
                }
            } catch (final MalformedURLException e) {
                prop.putHTML("result", "bad url: " + urlstring);
                prop.put("urlhash", "");
            }
        }

        if (post.containsKey("urlhashsearch")) {
            final URIMetadataNode entry = segment.fulltext().getMetadata(ASCII.getBytes(urlhash));
            if (entry == null) {
                prop.putHTML("result", "No Entry for URL hash " + urlhash);
            } else {
                prop.putHTML("urlstring", entry.url().toNormalform(true));
                prop.putAll(genUrlProfile(segment, entry, urlhash));
                prop.put("statistics", 0);
            }
        }

        if (post.containsKey("lurlexport")) {
            // parse format
            int format = 0;
            final String fname = post.get("format", "url-text");
            final boolean dom = fname.startsWith("dom"); // if dom== false complete urls are exported, otherwise only the domain
            if (fname.endsWith("text")) format = 0;
            if (fname.endsWith("html")) format = 1;
            if (fname.endsWith("rss")) format = 2;

            // extend export file name
            String s = post.get("exportfile", "");
            if (s.indexOf('.',0) < 0) {
                if (format == 0) s = s + ".txt";
                if (format == 1) s = s + ".html";
                if (format == 2) s = s + ".xml";
            }
            final File f = new File(s);
            f.getParentFile().mkdirs();
            final String filter = post.get("exportfilter", ".*");
            final String query = post.get("exportquery", "*:*");
            final Fulltext.Export running = segment.fulltext().export(f, filter, query, format, dom);

            prop.put("lurlexport_exportfile", s);
            prop.put("lurlexport_urlcount", running.count());
            if ((running != null) && (running.failed() == null)) {
                prop.put("lurlexport", 2);
            }
            prop.put("reload", 1);
        }

        if (post.containsKey("indexdump")) {
            final File dump = segment.fulltext().dumpSolr();
            prop.put("indexdump", 1);
            prop.put("indexdump_dumpfile", dump.getAbsolutePath());
            dumpFiles =  segment.fulltext().dumpFiles();
            prop.put("dumprestore_dumpfile", dumpFiles.size() == 0 ? "" : dumpFiles.get(dumpFiles.size() - 1).getAbsolutePath());
            sb.tables.recordAPICall(post, "IndexControlURLs_p.html", WorkTables.TABLE_API_TYPE_STEERING, "solr dump generation");
        }

        if (post.containsKey("indexrestore")) {
            final File dump = new File(post.get("dumpfile", ""));
            segment.fulltext().restoreSolr(dump);
        }
        
        if (post.containsKey("optimizesolr")) {
        	final int size = post.getInt("optimizemax", 10);
        	segment.fulltext().optimize(size);
            sb.tables.recordAPICall(post, "IndexControlURLs_p.html", WorkTables.TABLE_API_TYPE_STEERING, "solr optimize " + size);
        }

        if (post.containsKey("rebootsolr")) {
            segment.fulltext().rebootSolr();
            FieldCache.DEFAULT.purgeAllCaches();
            sb.tables.recordAPICall(post, "IndexControlURLs_p.html", WorkTables.TABLE_API_TYPE_STEERING, "solr reboot");
        }

        if (post.containsKey("deletedomain")) {
            final String domain = post.get("domain");
            Set<String> hostnames = new HashSet<String>();
            hostnames.add(domain);
            segment.fulltext().deleteStaleDomainNames(hostnames, null);
            // trigger the loading of the table
            post.put("statistics", "");
        }

        if (post.containsKey("statistics")) {
            final int count = post.getInt("lines", 100);
            prop.put("statistics_lines", count);
            int cnt = 0;
            try {
                final Fulltext metadata = segment.fulltext();
                Map<String, ReversibleScoreMap<String>> scores = metadata.getDefaultConnector().getFacets(CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", count, CollectionSchema.host_s.getSolrFieldName());
                ReversibleScoreMap<String> stats = scores.get(CollectionSchema.host_s.getSolrFieldName());
                Iterator<String> statsiter = stats.keys(false);
                boolean dark = true;
                String hostname;
                prop.put("statisticslines_domains_" + cnt + "lines", count);
                while (statsiter.hasNext() && cnt < count) {
                    hostname = statsiter.next();
                    prop.put("statisticslines_domains_" + cnt + "_dark", (dark) ? "1" : "0");
                    prop.put("statisticslines_domains_" + cnt + "_domain", hostname);
                    prop.put("statisticslines_domains_" + cnt + "_count", stats.get(hostname));
                    dark = !dark;
                    cnt++;
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
            prop.put("statisticslines_domains", cnt);
            prop.put("statisticslines", 1);
        }

        // insert constants
        prop.putNum("ucount", segment.fulltext().collectionSize());
        // return rewrite properties
        return prop;
    }

    private static serverObjects genUrlProfile(final Segment segment, final URIMetadataNode entry, final String urlhash) {
        final serverObjects prop = new serverObjects();
        if (entry == null) {
            prop.put("genUrlProfile", "1");
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        final URIMetadataNode le = (entry.referrerHash() == null || entry.referrerHash().length != Word.commonHashLength) ? null : segment.fulltext().getMetadata(entry.referrerHash());
        if (entry.url() == null) {
            prop.put("genUrlProfile", "1");
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        prop.put("genUrlProfile", "2");
        prop.putHTML("genUrlProfile_urlNormalform", entry.url().toNormalform(true));
        prop.put("genUrlProfile_urlhash", urlhash);
        prop.put("genUrlProfile_urlDescr", entry.dc_title());
        prop.put("genUrlProfile_moddate", entry.moddate().toString());
        prop.put("genUrlProfile_loaddate", entry.loaddate().toString());
        prop.put("genUrlProfile_referrer", (le == null) ? 0 : 1);
        prop.putHTML("genUrlProfile_referrer_url", (le == null) ? "<unknown>" : le.url().toNormalform(true));
        prop.put("genUrlProfile_referrer_hash", (le == null) ? "" : ASCII.String(le.hash()));
        prop.put("genUrlProfile_doctype", String.valueOf(entry.doctype()));
        prop.put("genUrlProfile_language", entry.language());
        prop.put("genUrlProfile_size", entry.size());
        prop.put("genUrlProfile_wordCount", entry.wordCount());
        return prop;
    }

}
