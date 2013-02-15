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

import java.io.IOException;
import java.util.Iterator;

import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.yacy.ConfigurationSet;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexSchema_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null && post.containsKey("set")) {
            // read index schema table flags
            final Iterator<ConfigurationSet.Entry> i = sb.index.fulltext().getSolrSchema().entryIterator();
            ConfigurationSet.Entry entry;
            boolean modified = false; // flag to remember changes
            while (i.hasNext()) {
                entry = i.next();
                final String v = post.get("schema_" + entry.key());
                final String sfn = post.get("schema_solrfieldname_" + entry.key());
                if (sfn != null ) {
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
            if (modified) { // save settings to config file if modified
                try {
                    sb.index.fulltext().getSolrSchema().commit();
                    modified = false;
                } catch (IOException ex) {}
            }
            
            boolean lazy = post.getBoolean("lazy");
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, lazy);
            
        }

        int c = 0;
        boolean dark = false;
        // use enum SolrField to keep defined order
        for(YaCySchema field : YaCySchema.values()) {
            prop.put("schema_" + c + "_dark", dark ? 1 : 0); dark = !dark;
            prop.put("schema_" + c + "_checked", sb.index.fulltext().getSolrSchema().contains(field.name()) ? 1 : 0);
            prop.putHTML("schema_" + c + "_key", field.name());
            prop.putHTML("schema_" + c + "_solrfieldname",field.name().equalsIgnoreCase(field.getSolrFieldName()) ? "" : field.getSolrFieldName());
            if (field.getComment() != null) prop.putHTML("schema_" + c + "_comment",field.getComment());
            c++;
        }
        prop.put("schema", c);
        
        prop.put("lazy.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, true) ? 1 : 0);
        
        // return rewrite properties
        return prop;
    }
}
