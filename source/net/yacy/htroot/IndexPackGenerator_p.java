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

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexPackGenerator_p {

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

            // get the collection facet
            ReversibleScoreMap<String> collections = null;
            prop.put("lurlexport_collections", 0);
            try {
                collections = sb.index.fulltext().getDefaultConnector().getFacets(AbstractSolrConnector.CATCHALL_QUERY, 1000, CollectionSchema.collection_sxt.getSolrFieldName()).get(CollectionSchema.collection_sxt.getSolrFieldName());
                if (collections != null) {
                    int i = 0;
                    for (final String collection: collections) {
                        if (collection.startsWith("robot_")) continue;
                        prop.put("lurlexport_collections_" + i + "_collection", collection);
                        prop.putNum("lurlexport_collections_" + i + "_count", collections.get(collection));
                        prop.putNum("lurlexport_collections_" + i + "_selected", "user".equals(collection) ? 1 : 0);
                        i++;
                    }
                    prop.put("lurlexport_collections", i);
                }
            } catch (final IOException e) {}
        }

        // show Pack folder contents
        int i = 0;
        boolean dark = true;
        for (final String file: sb.packsInHold()) {
            prop.put("packs_" + i + "_file", file);
            prop.put("packs_" + i + "_type", "hold");
            prop.put("packs_" + i + "_size", new File(sb.packsHoldPath, file).length() / 1024);
            prop.put("packs_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        for (final String file: sb.packsInLoaded()) {
            prop.put("packs_" + i + "_file", file);
            prop.put("packs_" + i + "_type", "loaded");
            prop.put("packs_" + i + "_size", new File(sb.packsLoadedPath, file).length() / 1024);
            prop.put("packs_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        for (final String file: sb.packsInLive()) {
            prop.put("packs_" + i + "_file", file);
            prop.put("packs_" + i + "_type", "live");
            prop.put("packs_" + i + "_size", new File(sb.packsLivePath, file).length() / 1024);
            prop.put("packs_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        prop.put("packs", i);

        if (post == null || env == null) {
            return prop; // nothing to do
        }

        if (post.containsKey("lurlexport")) {
            try {
                // parse format
                Fulltext.ExportFormat format = Fulltext.ExportFormat.elasticsearch;
                final String fname = post.get("format", "full-elasticsearch");
                final boolean dom = fname.startsWith("dom"); // if dom== false complete urls are exported, otherwise only the domain
                final boolean text = fname.startsWith("text");
                if (fname.endsWith("rss")) format = Fulltext.ExportFormat.rss;
                if (fname.endsWith("solr")) format = Fulltext.ExportFormat.solr;
                if (fname.endsWith("elasticsearch")) format = Fulltext.ExportFormat.elasticsearch;

                final String filter = post.get("exportfilter", ".*");
                String query = post.get("exportquery", "*:*");
                final String collection = post.get("collection", "user");
                query += " AND " + CollectionSchema.collection_sxt.getSolrFieldName() + ":\"" + collection + "\"";

                // store this call as api call: we do this even if there is a chance that it fails because recurring calls may do not fail
                sb.tables.recordAPICall(post, "IndexPackGenerator_p.html", WorkTables.TABLE_API_TYPE_DUMP, "PackGenerator, q=" + query);

                // start the export
                /*
                    Tier Tags:
                    | Tier      | Size      | Notes              |
                    |-----------|-----------|--------------------|
                    | common    | ≤ 1 GB    | IndexPackGenerator |
                    | uncommon  | 1–5 GB    | large web crawls   |
                    | rare      | 5–50 GB   | custom parser      |
                    | epic      | 50–200 GB | special infra      |
                    | legendary | any       | human curation     |
                 */
                final long now = System.currentTimeMillis();
                final long doccount = sb.index.fulltext().getDefaultConnector().getCountByQuery(query);
                if (doccount == 0) throw new IOException("number of exported documents == 0");
                final String category = post.get("category", "scroll"); // core, scroll, regula, gem, fiction, map, echo, spirit, vault
                final String tier = "common"; // common, uncommon, rare, epic, legendary, legendary
                final String origin = "web"; // web, synth,
                String slug = post.get("slug", "export").trim().replaceAll(" ", "-");
                if (slug.isEmpty()) slug = "export";

                // if collection is not user, the slug is the collection name
                if (!"user".equals(collection)) {
                    slug = collection.trim().replaceAll(" ", "-");
                }
                // we can not construct the file name
                final String filename =
                        SwitchboardConstants.YACY_PACK_PREFIX +
                        category + "-" + tier + "-" + origin + "_" +
                        slug + "_" +
                        GenericFormatter.SHORT_DAY_FORMATTER.format(new Date(now));
                // file name schema: YaCyPack_<category>-<tier>-<origin>_<slug>_<YYMMDD>.jsonlist
                // possible storage paths are: hold, load, loaded, unload, live; we use hold here, loaded would also be correct
                export = sb.index.fulltext().export(new File(sb.getDataPath() + "/DATA/PACKS/hold/"), filename, format.getExt(), filter, query, format, dom, text, -1, true);
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