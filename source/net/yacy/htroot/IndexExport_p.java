// IndexExport_p.java
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
import java.util.Date;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexExport_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();

        final Segment segment = sb.index;
        // we have two counts of document: total number and such that are exportable with status code 200
        final long ucount = segment.fulltext().collectionSize();
        long ucount200 = ucount;
        try {
            ucount200 = segment.fulltext().getDefaultConnector().getCountByQuery(CollectionSchema.httpstatus_i.getSolrFieldName() + ":200");
        } catch (final IOException e1) {}

        // set default values
        prop.put("reload", 0);
        prop.put("lurlexport", 0);
        prop.putNum("ucount", ucount);
        prop.putNum("ucount200", ucount200);

        // show export messages
        Fulltext.Export export = segment.fulltext().export();
        if ((export != null) && (export.isAlive())) {
            // there is currently a running export
            prop.put("lurlexport", 2);
            prop.put("lurlexportfinished", 0);
            prop.put("lurlexporterror", 0);
            prop.put("lurlexport_exportfile", export.file().toString());
            prop.put("lurlexport_urlcount", export.docCount());
            prop.put("reload", 1);
        } else {
            prop.put("lurlexport", 1);
            prop.put("lurlexport_exportfilepath", sb.getDataPath() + "/DATA/EXPORT/");
            if (export == null) {
                // there has never been an export
                prop.put("lurlexportfinished", 0);
                prop.put("lurlexporterror", 0);
            } else {
                // an export was running but has finished
                prop.put("lurlexportfinished", 1);
                prop.put("lurlexportfinished_exportfile", export.file().toString());
                prop.put("lurlexportfinished_urlcount", export.docCount());
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
            return prop; // nothing to do
        }

        if (post.containsKey("lurlexport")) {
            // parse format
            Fulltext.ExportFormat format = Fulltext.ExportFormat.text;
            final String fname = post.get("format", "url-text");
            final boolean dom = fname.startsWith("dom"); // if dom== false complete urls are exported, otherwise only the domain
            final boolean text = fname.startsWith("text");
            if (fname.endsWith("text")) format = Fulltext.ExportFormat.text;
            if (fname.endsWith("html")) format = Fulltext.ExportFormat.html;

            final String filter = post.get("exportfilter", ".*");
            String query = post.get("exportquery", "*:*");
            final int maxseconds = post.getInt("exportmaxseconds", -1);
            long maxChunkSize = post.getLong("maxchunksize", Long.MAX_VALUE);
            if (maxChunkSize <= 0) maxChunkSize = Long.MAX_VALUE;
            final String path = post.get("exportfilepath", "");
            final boolean minified = post.get("minified", "no").equals("yes");

            // store this call as api call: we do this even if there is a chance that it fails because recurring calls may do not fail
            if (maxseconds != -1) sb.tables.recordAPICall(post, "IndexExport_p.html", WorkTables.TABLE_API_TYPE_DUMP, format + "-dump, q=" + query + ", maxseconds=" + maxseconds);

            // start the export
            try {
                File filepath = new File(path);

                // modify query according to maxseconds
                final long now = System.currentTimeMillis();
                if (maxseconds > 0) {
                    final long from = now - maxseconds * 1000L;
                    final String nowstr = new Date(now).toInstant().toString();
                    final String fromstr = new Date(from).toInstant().toString();
                    final String dateq = CollectionSchema.load_date_dt.getSolrFieldName() + ":[" + fromstr + " TO " + nowstr + "]";
                    query = query == null || AbstractSolrConnector.CATCHALL_QUERY.equals(query) ? dateq : query + " AND " + dateq;
                } else {
                    query = query == null? AbstractSolrConnector.CATCHALL_QUERY : query;
                }

                // check the oldest and latest entry in the index for this query
                SolrDocumentList firstdoclist, lastdoclist;
                Object firstdateobject, lastdateobject;
                firstdoclist = sb.index.fulltext().getDefaultConnector().getDocumentListByQuery(
                        query, CollectionSchema.load_date_dt.getSolrFieldName() + " asc", 0, 1,CollectionSchema.load_date_dt.getSolrFieldName());
                lastdoclist = sb.index.fulltext().getDefaultConnector().getDocumentListByQuery(
                        query, CollectionSchema.load_date_dt.getSolrFieldName() + " desc", 0, 1,CollectionSchema.load_date_dt.getSolrFieldName());

                final long doccount;
                final Date firstdate, lastdate;
                if (firstdoclist.size() == 0 || lastdoclist.size() == 0) {
                    /* Now check again the number of documents without sorting, for compatibility with old fields indexed without DocValues fields (prior to YaCy 1.90)
                     * When the local Solr index contains such old documents, requests with sort query return nothing and trace in logs
                     * "java.lang.IllegalStateException: unexpected docvalues type NONE for field..." */
                    doccount = sb.index.fulltext().getDefaultConnector().getCountByQuery(query);
                    if(doccount == 0) {
                        /* Finally no document to export was found */
                        throw new IOException("number of exported documents == 0");
                    }
                    /* we use default date values just to generate a proper dump file path */
                    firstdate = new Date(0);
                    lastdate = new Date(0);

                } else {
                    doccount = firstdoclist.getNumFound();

                    // create the export name
                    final SolrDocument firstdoc = firstdoclist.get(0);
                    final SolrDocument lastdoc = lastdoclist.get(0);
                    firstdateobject = firstdoc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
                    lastdateobject = lastdoc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());

                    /* When firstdate or lastdate is null, we use a default one just to generate a proper dump file path
                     * This should not happen because load_date_dt field is mandatory in the main Solr schema,
                     * but for some reason some documents might end up here with an empty load_date_dt field value */
                    if(firstdateobject instanceof Date) {
                        firstdate = (Date) firstdateobject;
                    } else {
                        ConcurrentLog.warn("Fulltext", "The required field " + CollectionSchema.load_date_dt.getSolrFieldName() + " is empty on document with id : "
                                + firstdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                        firstdate = new Date(0);
                    }
                    if(lastdateobject instanceof Date) {
                        lastdate = (Date) lastdateobject;
                    } else {
                        ConcurrentLog.warn("Fulltext", "The required field " + CollectionSchema.load_date_dt.getSolrFieldName() + " is empty on document with id : "
                                + lastdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                        lastdate = new Date(0);
                    }
                }

                final String filename = SwitchboardConstants.YACY_PACK_PREFIX +
                        "f" + GenericFormatter.SHORT_MINUTE_FORMATTER.format(firstdate) + "_" +
                        "l" + GenericFormatter.SHORT_MINUTE_FORMATTER.format(lastdate) + "_" +
                        "n" + GenericFormatter.SHORT_MINUTE_FORMATTER.format(new Date(now)) + "_" +
                        "c" + String.format("%1$012d", doccount)+ "_tc"; // the name ends with the transaction token ('c' = 'created')

                export = sb.index.fulltext().export(filepath, filename, format.getExt(), filter, query, format, dom, text, maxChunkSize, minified);

            } catch (final IOException e) {
                prop.put("lurlexporterror", 1);
                prop.put("lurlexporterror_exportfile", "-no export-");
                prop.put("lurlexporterror_exportfailmsg", e.getMessage());
                return prop;
            }

            // show result
            prop.put("lurlexport_exportfile", export.file().toString());
            prop.put("lurlexport_urlcount", export.docCount());
            if ((export != null) && (export.failed() == null)) {
                prop.put("lurlexport", 2);
            }
            prop.put("reload", 1);
        }

        // insert constants
        prop.putNum("ucount", ucount);
        // return rewrite properties
        return prop;
    }

}