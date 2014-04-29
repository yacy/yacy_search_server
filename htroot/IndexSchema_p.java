/**
 *  IndexSchemaFulltext_p
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 13.02.2013 at http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import net.yacy.cora.federate.solr.SchemaConfiguration;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphConfiguration;
import net.yacy.search.schema.WebgraphSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexSchema_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        String schemaName = CollectionSchema.CORE_NAME;
        if (post != null) schemaName = post.get("core", schemaName); 
        SchemaConfiguration cs = schemaName.equals(CollectionSchema.CORE_NAME) ? sb.index.fulltext().getDefaultConfiguration() : sb.index.fulltext().getWebgraphConfiguration();
        
        if (post != null && post.containsKey("set")) {
            // read index schema table flags
            final Iterator<SchemaConfiguration.Entry> i = cs.entryIterator();
            SchemaConfiguration.Entry entry;
            boolean modified = false; // flag to remember changes
            while (i.hasNext()) {
                entry = i.next();
                if (post.containsKey("schema_solrfieldname_" + entry.key()) ) { // can't use schem_... checkbox only contained if checked
                    // only handle displayed (contained) fields
                    final String v = post.get("schema_" + entry.key());
                    final String sfn = post.get("schema_solrfieldname_" + entry.key());
                    if (sfn != null) {
                        // set custom solr field name
                        if (!sfn.equals(entry.getValue())) {
                            entry.setValue(sfn);
                            modified = true;
                        }
                    }
                    // set enable flag
                    final boolean c = v != null && v.equals("checked");
                    if (entry.enabled() != c) {
                        entry.setEnable(c);
                        modified = true;
                    }
                } 
            }
            if (modified) { // save settings to config file if modified
                try {
                    cs.commit();
                    sb.index.fulltext().getDefaultConfiguration().commit();
                    sb.index.fulltext().getWebgraphConfiguration().commit();
                    modified = false;
                } catch (final IOException ex) {}
            }            
        }
        
        if (post != null && post.containsKey("resetselectiontodefault")) {
            // reset Solr field selection to default configuration             
            File solrInitFile;
            if (cs instanceof WebgraphConfiguration) { // get default configuration for webgraph
                solrInitFile = new File(sb.getAppPath(), "defaults/" + Switchboard.SOLR_WEBGRAPH_CONFIGURATION_NAME);
            } else { // or get default configuration for collection1
                solrInitFile = new File(sb.getAppPath(), "defaults/" + Switchboard.SOLR_COLLECTION_CONFIGURATION_NAME);
            }
            try {
                SchemaConfiguration solrConfigurationInit = new SchemaConfiguration(solrInitFile);
                Iterator<SchemaConfiguration.Entry> it = cs.entryIterator(); // get current configuration
                while (it.hasNext()) { // iterate over entries and enable/disable according to default
                    SchemaConfiguration.Entry etr = it.next();
                    etr.setEnable(solrConfigurationInit.contains(etr.key()));
                }
                cs.commit();
            } catch (final IOException ex) {
                ConcurrentLog.warn("IndexSchema", "file " + solrInitFile.getAbsolutePath() + " not found");
            }
        }
        
        int c = 0;
        boolean dark = false;
        // use enum SolrField to keep defined order
        SchemaDeclaration[] cc = schemaName.equals(CollectionSchema.CORE_NAME) ? CollectionSchema.values() : WebgraphSchema.values();
        String filterstr = null;
        // set active filter button property
        boolean viewall = (post == null) || (filterstr = post.get("filter")) == null;
        boolean viewactiveonly = !viewall && "active".equals(filterstr);
        boolean viewdisabledonly = !viewall && "disabled".equals(filterstr);
        prop.put("viewall",viewall);
        prop.put("activeonly", viewactiveonly);
        prop.put("disabledonly", viewdisabledonly);
        
        for(SchemaDeclaration field : cc) {
            boolean showline = viewactiveonly ? cs.contains(field.name()) : (viewdisabledonly ? cs.containsDisabled(field.name()): true);
            if (showline) {
                prop.put("schema_" + c + "_dark", dark ? 1 : 0); dark = !dark;
                prop.put("schema_" + c + "_checked", cs.contains(field.name()) ? 1 : 0);
                prop.putHTML("schema_" + c + "_key", field.name());
                prop.putHTML("schema_" + c + "_solrfieldname",field.name().equalsIgnoreCase(field.getSolrFieldName()) ? "" : field.getSolrFieldName());
                if (field.getComment() != null) prop.putHTML("schema_" + c + "_comment",field.getComment());
                c++;
            }
        }
        prop.put("schema", c);

        prop.put("cores_" + 0 + "_name", CollectionSchema.CORE_NAME);
        prop.put("cores_" + 0 + "_selected", CollectionSchema.CORE_NAME.equals(schemaName) ? 1 : 0);
        prop.put("cores_" + 1 + "_name", WebgraphSchema.CORE_NAME);
        prop.put("cores_" + 1 + "_selected", WebgraphSchema.CORE_NAME.equals(schemaName) ? 1 : 0);
        prop.put("cores", 2);
        prop.put("core", schemaName);
        
        // return rewrite properties
        return prop;
    }
}
