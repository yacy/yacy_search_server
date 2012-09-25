/**
 *  schema_p
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 13.01.2012 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
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

import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.index.SolrConfiguration;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class schema_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        // write scheme
        int c = 0;
        /*
        //<field name="#[solrname]#" type="#[type]#"#(indexedChecked)#:: indexed="true"#(/indexedChecked)##(storedChecked)#:: stored="true"#(/storedChecked)##(multiValuedChecked)#:: multiValued="true"#(/multiValuedChecked)##(omitNormsChecked)#:: omitNorms="true"#(/omitNormsChecked)#/>
        if (sb == null) {
            for (SolrType type : SolrType.values()) {
                prop.put("fields_" + c + "_solrname", field.getSolrFieldName());
                prop.put("fields_" + c + "_type", field.getType().printName());
                prop.put("fields_" + c + "_comment", field.getComment());
                prop.put("fields_" + c + "_indexedChecked", field.isIndexed() ? 1 : 0);
                prop.put("fields_" + c + "_storedChecked", field.isStored() ? 1 : 0);
                prop.put("fields_" + c + "_multiValuedChecked", field.isMultiValued() ? 1 : 0);
                prop.put("fields_" + c + "_omitNormsChecked", field.isOmitNorms() ? 1 : 0);
                c++;
            }
            prop.put("fields", c);
        } else {
        */
        SolrConfiguration solrScheme = sb.index.fulltext().getSolrScheme();
        for (YaCySchema field : YaCySchema.values()) {
            if (solrScheme.contains(field.name())) {
                prop.put("fields_" + c + "_solrname", field.getSolrFieldName());
                prop.put("fields_" + c + "_type", field.getType().printName());
                prop.put("fields_" + c + "_comment", field.getComment());
                prop.put("fields_" + c + "_indexedChecked", field.isIndexed() ? 1 : 0);
                prop.put("fields_" + c + "_storedChecked", field.isStored() ? 1 : 0);
                prop.put("fields_" + c + "_multiValuedChecked", field.isMultiValued() ? 1 : 0);
                prop.put("fields_" + c + "_omitNormsChecked", field.isOmitNorms() ? 1 : 0);
                c++;
            }
        }
        prop.put("fields", c);
        //}

        prop.put("solruniquekey",YaCySchema.id.getSolrFieldName());
        prop.put("solrdefaultsearchfield",YaCySchema.text_t.getSolrFieldName());
        // return rewrite properties
        return prop;
    }
}
