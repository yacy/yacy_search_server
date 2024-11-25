/**
 *  IndexDeletion_p
 *  Copyright 2013 by Michael Peter Christen
 *  First released 29.04.2013 at https://yacy.net
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

package net.yacy.htroot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Pattern;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.TransactionManager;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexDeletion_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        /* Acquire a transaction token for the next POST form submission */
        try {
            prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header));
        } catch (IllegalArgumentException e) {
            sb.log.fine("access by unauthorized or unknown user: no transaction token delivered");
        }

        final SolrConnector defaultConnector = sb.index.fulltext().getDefaultConnector();
        final SolrConnector webgraphConnector = sb.index.fulltext().getWebgraphConnector();
        if (post == null || post.size() == 0) defaultConnector.commit(false); // we must do a commit here because the user cannot see a proper count.

        String schemaName = CollectionSchema.CORE_NAME;
        if (post != null) schemaName = post.get("core", schemaName);

        // Delete by URL Matching
        String urldelete = post == null ? "" : post.get("urldelete", "");
        final boolean urldelete_mm_subpath_checked = post == null ? true : post.get("urldelete-mm", "subpath").equals("subpath");
        prop.putHTML("urldelete", urldelete);
        prop.put("urldelete-mm-subpath-checked", urldelete_mm_subpath_checked ? 1 : 0);
        prop.put("urldelete-mm-regexp-checked", urldelete_mm_subpath_checked ? 0 : 1);
        prop.put("urldelete-active", 0);

        // Delete by Age
        final int timedelete_number = post == null ? 14 : post.getInt("timedelete-number", 14);
        final String timedelete_unit = post == null ? "day" : post.get("timedelete-unit", "day");
        final boolean timedelete_source_loaddate_checked = post == null ? true : post.get("timedelete-source", "loaddate").equals("loaddate");
        for (int i = 1; i <= 90; i++) prop.put("timedelete-n-" + i, 0);
        prop.put("timedelete-n-" + timedelete_number, timedelete_number);
        prop.put("timedelete-u-year", timedelete_unit.equals("year") ? 1 : 0);
        prop.put("timedelete-u-month", timedelete_unit.equals("month") ? 1 : 0);
        prop.put("timedelete-u-day", timedelete_unit.equals("day") ? 1 : 0);
        prop.put("timedelete-u-hour", timedelete_unit.equals("hour") ? 1 : 0);
        prop.put("timedelete-source-loaddate-checked", timedelete_source_loaddate_checked ? 1 : 0);
        prop.put("timedelete-source-lastmodified-checked", timedelete_source_loaddate_checked ? 0 : 1);
        prop.put("timedelete-active", 0);

        // Delete Collections
        boolean collectiondelete_mode_unassigned_checked = post == null ? true : post.get("collectiondelete-mode", "unassigned").equals("unassigned");
        String collectiondelete = post == null ? "" : post.get("collectiondelete", "");
        if (post != null && post.containsKey("collectionlist")) {
            collectiondelete_mode_unassigned_checked = false;
            prop.put("collectiondelete-select", 1);
            try {
                final ScoreMap<String> collectionMap = defaultConnector.getFacets("*:*", 1000, CollectionSchema.collection_sxt.getSolrFieldName()).get(CollectionSchema.collection_sxt.getSolrFieldName());
                final Iterator<String> i = collectionMap.iterator();
                int c = 0;
                while (i.hasNext()) {
                    final String collection = i.next();
                    prop.put("collectiondelete-select_list_" + c + "_collection-name", collection + "/" + collectionMap.get(collection));
                    prop.put("collectiondelete-select_list_" + c + "_collection-value", collection);
                    c++;
                }
                prop.put("collectiondelete-select_list", c );
            } catch (final IOException e1) {
                prop.put("collectiondelete-select", 0);
            }
        } else {
            prop.put("collectiondelete-select", 0);
        }
        prop.put("collectiondelete-mode-unassigned-checked", collectiondelete_mode_unassigned_checked ? 1 : 0);
        prop.put("collectiondelete-mode-assigned-checked", collectiondelete_mode_unassigned_checked ? 0 : 1);
        prop.putHTML("collectiondelete-select_collectiondelete", collectiondelete);
        prop.put("collectiondelete-active", 0);

        // Delete by Solr Query
        prop.put("querydelete", "");
        String querydelete = post == null ? "" : post.get("querydelete", "");
        // simulate default search field if no field is given by adding text_t: as target field
        if (!querydelete.isEmpty() && !querydelete.contains(":")) querydelete = CollectionSchema.text_t.getSolrFieldName() + ":" + querydelete;
        prop.putHTML("querydelete", querydelete);
        prop.put("querydelete-active", 0);


        int count = post == null ? -1 : post.getInt("count", -1);

        if (post != null && (post.containsKey("simulate-urldelete") || post.containsKey("engage-urldelete"))) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            final boolean simulate = post.containsKey("simulate-urldelete");
            // parse the input
            urldelete = urldelete.trim();
            if (urldelete_mm_subpath_checked) {
                // collect using url stubs
                final Set<String> ids = new HashSet<String>();
                final String[] stubURLs = urldelete.indexOf('\n') > 0 || urldelete.indexOf('\r') > 0 ? urldelete.split("[\\r\\n]+") : urldelete.split(Pattern.quote("|"));
                for (String urlStub: stubURLs) {
                    if (urlStub == null || urlStub.length() == 0) continue;
                    final int pos = urlStub.indexOf("://",0);
                    if (pos == -1) {
                        if (urlStub.startsWith("ftp")) urlStub = "ftp://" + urlStub; else urlStub = "http://" + urlStub;
                    }
                    try {
                        final DigestURL u = new DigestURL(urlStub);
                        final BlockingQueue<SolrDocument> dq = defaultConnector.concurrentDocumentsByQuery(CollectionSchema.host_s.getSolrFieldName() + ":\"" + u.getHost() + "\"", null, 0, 100000000, Long.MAX_VALUE, 100, 1, false, CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName());
                        SolrDocument doc;
                        try {
                            while ((doc = dq.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                                final String url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                                if (url.startsWith(urlStub)) ids.add((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                            }
                        } catch (final InterruptedException e) {
                        }
                    } catch (final MalformedURLException e) {}
                }

                if (simulate) {
                    count = ids.size();
                    prop.put("urldelete-active", count == 0 ? 2 : 1);
                } else {
                    sb.remove(ids);
                    defaultConnector.commit(false);
                    ids.forEach(urlhash -> {try {sb.index.loadTimeIndex().remove(urlhash.getBytes());} catch (final IOException e) {}});
                    sb.tables.recordAPICall(post, "IndexDeletion_p.html", WorkTables.TABLE_API_TYPE_DELETION, "deletion, docs matching with " + urldelete);
                    prop.put("urldelete-active", 2);
                }
            } else {
                // collect using a regular expression on urls
                final String regexquery = CollectionSchema.sku.getSolrFieldName() + ":/" + urldelete + "/";
                if (simulate) {
                    try {
                        count = (int) defaultConnector.getCountByQuery("{!cache=false}" + regexquery);
                    } catch (final IOException e) {
                    }
                    prop.put("urldelete-active", count == 0 ? 2 : 1);
                } else {
                    try {
                        defaultConnector.deleteByQuery(regexquery);
                        defaultConnector.commit(false);
                        try {sb.index.loadTimeIndex().clear();} catch (final IOException e) {}
                        sb.tables.recordAPICall(post, "IndexDeletion_p.html", WorkTables.TABLE_API_TYPE_DELETION, "deletion, regex match = " + urldelete);
                    } catch (final IOException e) {
                    }
                    prop.put("urldelete-active", 2);
                }
            }
            prop.put("urldelete-active_count", count);
        }

        if (post != null && (post.containsKey("simulate-timedelete") || post.containsKey("engage-timedelete"))) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            final boolean simulate = post.containsKey("simulate-timedelete");
            Date deleteageDate = null;
            final long t = timeParser(timedelete_number, timedelete_unit); // year, month, day, hour
            if (t > 0) deleteageDate = new Date(t);
            final String collection1Query = (timedelete_source_loaddate_checked ? CollectionSchema.load_date_dt : CollectionSchema.last_modified).getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(deleteageDate) + "]";
            final String webgraphQuery = (timedelete_source_loaddate_checked ? WebgraphSchema.load_date_dt : WebgraphSchema.last_modified).getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(deleteageDate) + "]";
            if (simulate) {
                try {
                    count = (int) defaultConnector.getCountByQuery(collection1Query);
                } catch (final IOException e) {
                }
                prop.put("timedelete-active", count == 0 ? 2 : 1);
            } else {
                try {
                    defaultConnector.deleteByQuery(collection1Query);
                    defaultConnector.commit(false);
                    try {sb.index.loadTimeIndex().clear();} catch (final IOException e) {}
                    if (webgraphConnector != null) webgraphConnector.deleteByQuery(webgraphQuery);
                    sb.tables.recordAPICall(post, "IndexDeletion_p.html", WorkTables.TABLE_API_TYPE_DELETION, "deletion, docs older than " + timedelete_number + " " + timedelete_unit);
                } catch (final IOException e) {
                }
                prop.put("timedelete-active", 2);
            }
            prop.put("timedelete-active_count", count);
        }

        if (post != null && (post.containsKey("simulate-collectiondelete") || post.containsKey("engage-collectiondelete"))) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            final boolean simulate = post.containsKey("simulate-collectiondelete");
            collectiondelete = collectiondelete.replaceAll(" ","").replaceAll(",", "|");
            final String query = collectiondelete_mode_unassigned_checked ? "-" + CollectionSchema.collection_sxt + AbstractSolrConnector.CATCHALL_DTERM : collectiondelete.length() == 0 ? CollectionSchema.collection_sxt + ":\"\"" : QueryModifier.parseCollectionExpression(collectiondelete);
            if (simulate) {
                try {
                    count = (int) defaultConnector.getCountByQuery(query);
                } catch (final IOException e) {
                }
                prop.put("collectiondelete-active", count == 0 ? 2 : 1);
            } else {
                try {
                    defaultConnector.deleteByQuery(query);
                    defaultConnector.commit(false);
                    try {sb.index.loadTimeIndex().clear();} catch (final IOException e) {}
                    sb.tables.recordAPICall(post, "IndexDeletion_p.html", WorkTables.TABLE_API_TYPE_DELETION, "deletion, collection " + collectiondelete);
                } catch (final IOException e) {
                }
                prop.put("collectiondelete-active", 2);
            }
            prop.put("collectiondelete-active_count", count);
        }

        if (post != null && (post.containsKey("simulate-querydelete") || post.containsKey("engage-querydelete"))) {
            /* Check the transaction is valid */
            TransactionManager.checkPostTransaction(header, post);

            final boolean simulate = post.containsKey("simulate-querydelete");

            final SolrConnector connector = schemaName.equals(CollectionSchema.CORE_NAME) ? defaultConnector : sb.index.fulltext().getWebgraphConnector();
            if (simulate) {
                try {
                    count = (int) connector.getCountByQuery(querydelete);
                } catch (final IOException e) {
                }
                prop.put("querydelete-active", count == 0 ? 2 : 1);
            } else {
                try {
                    ConcurrentLog.info("IndexDeletion", "delete by query \"" + querydelete + "\", size before deletion = " + connector.getSize());
                    connector.deleteByQuery(querydelete);
                    connector.commit(false);
                    try {sb.index.loadTimeIndex().clear();} catch (final IOException e) {}
                    ConcurrentLog.info("IndexDeletion", "delete by query \"" + querydelete + "\", size after commit = " + connector.getSize());
                    sb.tables.recordAPICall(post, "IndexDeletion_p.html", WorkTables.TABLE_API_TYPE_DELETION, "deletion, solr query, q = " + querydelete);
                } catch (final IOException e) {
                }
                prop.put("querydelete-active", 2);
            }
            prop.put("querydelete-active_count", count);
        }
        prop.put("doccount", defaultConnector.getSize());


        prop.put("cores_" + 0 + "_name", CollectionSchema.CORE_NAME);
        prop.put("cores_" + 0 + "_selected", CollectionSchema.CORE_NAME.equals(schemaName) ? 1 : 0);
        prop.put("cores_" + 1 + "_name", WebgraphSchema.CORE_NAME);
        prop.put("cores_" + 1 + "_selected", WebgraphSchema.CORE_NAME.equals(schemaName) ? 1 : 0);
        prop.put("cores", 2);

        // return rewrite properties
        return prop;
    }

    private static long timeParser(final int number, final String unit) {
        if ("year".equals(unit)) return System.currentTimeMillis() - number * AbstractFormatter.normalyearMillis;
        if ("month".equals(unit)) return System.currentTimeMillis() - number * AbstractFormatter.monthAverageMillis;
        if ("day".equals(unit)) return System.currentTimeMillis() - number * AbstractFormatter.dayMillis;
        if ("hour".equals(unit)) return System.currentTimeMillis() - number * AbstractFormatter.hourMillis;
        if ("minute".equals(unit)) return System.currentTimeMillis() - number * AbstractFormatter.minuteMillis;
        return 0L;
    }

}
