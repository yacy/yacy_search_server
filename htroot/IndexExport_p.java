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

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexExport_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();

        Segment segment = sb.index;
        long ucount = segment.fulltext().collectionSize();
        
        // set default values
        prop.put("otherHosts", "");
        prop.put("reload", 0);
        prop.put("indexdump", 0);
        prop.put("lurlexport", 0);
        prop.put("reload", 0);
        prop.put("dumprestore", 1);
        List<File> dumpFiles =  segment.fulltext().dumpFiles();
        prop.put("dumprestore_dumpfile", dumpFiles.size() == 0 ? "" : dumpFiles.get(dumpFiles.size() - 1).getAbsolutePath());
        prop.put("dumprestore_optimizemax", 10);
        prop.putNum("ucount", ucount);
        
        // show export messages
        Fulltext.Export export = segment.fulltext().export();
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
            prop.put("lurlexport_exportfilepath", sb.getDataPath() + "/DATA/EXPORT/");
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
            if (fname.endsWith("rss")) format = Fulltext.ExportFormat.rss;
            if (fname.endsWith("solr")) format = Fulltext.ExportFormat.solr;
            if (fname.endsWith("elasticsearch")) format = Fulltext.ExportFormat.elasticsearch;

            final String filter = post.get("exportfilter", ".*");
            final String query = post.get("exportquery", "*:*");
            final int maxseconds = post.getInt("exportmaxseconds", -1);
            final String path = post.get("exportfilepath", "");

            // store this call as api call: we do this even if there is a chance that it fails because recurring calls may do not fail
            if (maxseconds != -1) sb.tables.recordAPICall(post, "IndexExport_p.html", WorkTables.TABLE_API_TYPE_DUMP, format + "-dump, q=" + query + ", maxseconds=" + maxseconds);
            
            // start the export
            try {
                export = sb.index.fulltext().export(format, filter, query, maxseconds, new File(path), dom, text);
            } catch (IOException e) {
                prop.put("lurlexporterror", 1);
                prop.put("lurlexporterror_exportfile", "-no export-");
                prop.put("lurlexporterror_exportfailmsg", e.getMessage());
                return prop;
            }
            
            // show result
            prop.put("lurlexport_exportfile", export.file().toString());
            prop.put("lurlexport_urlcount", export.count());
            if ((export != null) && (export.failed() == null)) {
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
            //sb.tables.recordAPICall(post, "IndexExport_p.html", WorkTables.TABLE_API_TYPE_STEERING, "solr dump generation");
        }

        if (post.containsKey("indexrestore")) {
            final File dump = new File(post.get("dumpfile", ""));
            segment.fulltext().restoreSolr(dump);
        }

        // insert constants
        prop.putNum("ucount", ucount);
        // return rewrite properties
        return prop;
    }

}