// IndexExportImportSolr_p.java
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
import java.util.List;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexExportImportSolr_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();

        final Segment segment = sb.index;
        // we have two counts of document: total number and such that are exportable with status code 200
        final long ucount = segment.fulltext().collectionSize();

        // set default values
        prop.put("indexdump", 0);
        prop.put("indexRestore", 0);
        prop.put("dumprestore", 1);
        prop.put("dumprestore_dumpRestoreEnabled", sb.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT,
                SwitchboardConstants.CORE_SERVICE_FULLTEXT_DEFAULT));
        List<File> dumpFiles =  segment.fulltext().dumpFiles();
        prop.put("dumprestore_dumpfile", dumpFiles.size() == 0 ? "" : dumpFiles.get(dumpFiles.size() - 1).getAbsolutePath());
        prop.put("dumprestore_optimizemax", 10);
        prop.putNum("ucount", ucount);

        if (post == null || env == null) {
            return prop; // nothing to do
        }

        if (post.containsKey("indexdump")) {
            try {
                final File dump = segment.fulltext().dumpEmbeddedSolr();
                prop.put("indexdump", 1);
                prop.put("indexdump_dumpfile", dump.getAbsolutePath());
                dumpFiles =  segment.fulltext().dumpFiles();
                prop.put("dumprestore_dumpfile", dumpFiles.size() == 0 ? "" : dumpFiles.get(dumpFiles.size() - 1).getAbsolutePath());
                // sb.tables.recordAPICall(post, "IndexExport_p.html", WorkTables.TABLE_API_TYPE_STEERING, "solr dump generation");
            } catch(final SolrException e) {
                if(ErrorCode.SERVICE_UNAVAILABLE.code == e.code()) {
                    prop.put("indexdump", 2);
                } else {
                    prop.put("indexdump", 3);
                }
            }
        }

        if (post.containsKey("indexrestore")) {
            try {
                final File dump = new File(post.get("dumpfile", ""));
                segment.fulltext().restoreEmbeddedSolr(dump);
                prop.put("indexRestore", 1);
            } catch(final SolrException e) {
                if(ErrorCode.SERVICE_UNAVAILABLE.code == e.code()) {
                    prop.put("indexRestore", 2);
                } else {
                    prop.put("indexRestore", 3);
                }
            }
        }

        // insert constants
        prop.putNum("ucount", ucount);
        // return rewrite properties
        return prop;
    }

}