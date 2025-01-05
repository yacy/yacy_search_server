// IndexControlURLs_p.java
// -----------------------
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.htroot;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.data.TransactionManager;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexControlURLs_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();

        /* Acquire a transaction token for the next possible POST form submissions */
        final String nextTransactionToken = TransactionManager.getTransactionToken(header);
        prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, nextTransactionToken);

        final Segment segment = sb.index;
        final long ucount = segment.fulltext().collectionSize();

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
        prop.put("reload", 0);
        prop.put("dumprestore", 1);
        prop.put("dumprestore_" + TransactionManager.TRANSACTION_TOKEN_PARAM, nextTransactionToken);
        final List<File> dumpFiles =  segment.fulltext().dumpFiles();
        prop.put("dumprestore_dumpfile", dumpFiles.size() == 0 ? "" : dumpFiles.get(dumpFiles.size() - 1).getAbsolutePath());
        prop.put("dumprestore_optimizemax", 10);
        prop.put("dumprestore_rebootSolrEnabled",
                sb.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT,
                        SwitchboardConstants.CORE_SERVICE_FULLTEXT_DEFAULT)
                        && !sb.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED,
                                SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED_DEFAULT));
        prop.put("cleanup", ucount == 0 ? 0 : 1);
        prop.put("cleanupsolr", segment.fulltext().connectedRemoteSolr() ? 1 : 0);
        prop.put("cleanuprwi", segment.termIndex() != null && !segment.termIndex().isEmpty() ? 1 : 0);
        prop.put("cleanupcitation", segment.connectedCitation() && !segment.urlCitation().isEmpty() ? 1 : 0);

        if (post == null || env == null) {
            prop.putNum("ucount", ucount);
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
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            if ( post.get("deleteIndex", "").equals("on") ) {
                try {
                    segment.fulltext().clearLocalSolr();
                    segment.loadTimeIndex().clear();
                } catch (final IOException e) {}
            }
            if ( post.get("deleteRemoteSolr", "").equals("on")) {
                try {
                    segment.fulltext().clearRemoteSolr();
                    segment.loadTimeIndex().clear();
                } catch (final IOException e) {}
            }
            if ( post.get("deleteRWI", "").equals("on")) {
                if (segment.termIndex() != null) try {segment.termIndex().clear();} catch (final IOException e) {}
            }
            if ( post.get("deleteCitation", "").equals("on")) {
                if (segment.connectedCitation()) try {segment.urlCitation().clear();} catch (final IOException e) {}
            }
            if ( post.get("deleteFirstSeen", "").equals("on")) {
                try {
                    segment.firstSeenIndex().clear();
                } catch (final IOException e) {}
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
            post.remove("deletecomplete");
        }

        if (post.containsKey("urlhashdeleteall")) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            final ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
            final int i = segment.removeAllUrlReferences(urlhash.getBytes(), sb.loader, agent, CacheStrategy.IFEXIST);
            try {segment.loadTimeIndex().remove(urlhash.getBytes());} catch (final IOException e) {}
            prop.put("result", "Deleted URL and " + i + " references from " + i + " word indexes.");
        }

        if (post.containsKey("urlhashdelete")) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            String url;
            try {
                url = segment.fulltext().getURL(urlhash);
                if (url == null) {
                    prop.putHTML("result", "No Entry for URL hash " + urlhash + "; nothing deleted.");
                } else {
                    prop.put("urlstring", "");
                    sb.urlRemove(segment, urlhash.getBytes());
                    prop.putHTML("result", "Removed URL " + url);
                }
                segment.loadTimeIndex().remove(urlhash.getBytes());
            } catch (final IOException e) {
                prop.putHTML("result", "Error when querying the url hash " + urlhash + ":" + e.getMessage());
            }
        }

        if (post.containsKey("urldelete")) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            try {
                urlhash = ASCII.String((new DigestURL(urlstring)).hash());
            } catch (final MalformedURLException e) {
                urlhash = null;
            }
            if ((urlhash == null) || (urlstring == null)) {
                prop.put("result", "No input given; nothing deleted.");
            } else {
                sb.urlRemove(segment, urlhash.getBytes());
                try {segment.loadTimeIndex().remove(urlhash.getBytes());} catch (final IOException e) {}
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
                    prop.putAll(genUrlProfile(segment, entry, urlhash, nextTransactionToken));
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
                prop.putAll(genUrlProfile(segment, entry, urlhash, nextTransactionToken));
                prop.put("statistics", 0);
            }
        }

        if (post.containsKey("optimizesolr")) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            final int size = post.getInt("optimizemax", 10);
            segment.fulltext().optimize(size);
            sb.tables.recordAPICall(post, "IndexControlURLs_p.html", WorkTables.TABLE_API_TYPE_STEERING, "solr optimize " + size);
        }

        if (post.containsKey("rebootsolr")) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            if (sb.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT,
                    SwitchboardConstants.CORE_SERVICE_FULLTEXT_DEFAULT)
                    && !sb.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED,
                            SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED_DEFAULT)) {
                /* This operation is designed only for an embdded local Solr with no mirroring to an external remote Solr server */
                segment.fulltext().rebootEmbeddedLocalSolr();
                sb.tables.recordAPICall(post, "IndexControlURLs_p.html", WorkTables.TABLE_API_TYPE_STEERING, "solr reboot");
            }
        }

        if (post.containsKey("deletedomain")) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            final String domain = post.get("domain");
            final Set<String> hostnames = new HashSet<String>();
            hostnames.add(domain);
            segment.fulltext().deleteStaleDomainNames(hostnames, null);
            try {segment.loadTimeIndex().clear();} catch (final IOException e) {} // delete all to prevent that existing entries reject reloading
            // trigger the loading of the table
            post.put("statistics", "");
        }

        if (post.containsKey("statistics")) {
            final int count = post.getInt("lines", 100);
            prop.put("statistics_lines", count);
            int cnt = 0;
            try {
                final Fulltext metadata = segment.fulltext();
                final Map<String, ReversibleScoreMap<String>> scores = metadata.getDefaultConnector().getFacets(CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", count, CollectionSchema.host_s.getSolrFieldName());
                final ReversibleScoreMap<String> stats = scores.get(CollectionSchema.host_s.getSolrFieldName());
                final Iterator<String> statsiter = stats.keys(false);
                boolean dark = true;
                String hostname;
                prop.put("statisticslines_domains_" + cnt + "lines", count);
                while (statsiter.hasNext() && cnt < count) {
                    hostname = statsiter.next();
                    prop.put("statisticslines_domains_" + cnt + "_" + TransactionManager.TRANSACTION_TOKEN_PARAM, nextTransactionToken);
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
        prop.putNum("ucount", ucount);
        // return rewrite properties
        return prop;
    }

    private static serverObjects genUrlProfile(final Segment segment, final URIMetadataNode entry, final String urlhash, final String nextTransactionToken) {
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
        prop.put("genUrlProfile_" + TransactionManager.TRANSACTION_TOKEN_PARAM, nextTransactionToken);
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
