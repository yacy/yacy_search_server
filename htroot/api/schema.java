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

import net.yacy.cora.federate.solr.SchemaConfiguration;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class schema {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final servletProperties prop = new servletProperties();
        final Switchboard sb = (Switchboard) env;

        String schemaName = CollectionSchema.CORE_NAME;
        if (post != null) schemaName = post.get("core", schemaName); 
        
        // write schema
        int c = 0;
        SchemaConfiguration solrSchema = schemaName.equals(CollectionSchema.CORE_NAME) ? sb.index.fulltext().getDefaultConfiguration() : sb.index.fulltext().getWebgraphConfiguration();
        SchemaDeclaration[] cc = schemaName.equals(CollectionSchema.CORE_NAME) ? CollectionSchema.values() : WebgraphSchema.values();
        for (SchemaDeclaration field : cc) {
            if (solrSchema.contains(field.name())) {
                addField(prop, c, field);
                c++;
            }
        }
        if (schemaName.equals(CollectionSchema.CORE_NAME)) {
            // add additional coordinate field for collection1
            if (solrSchema.contains(CollectionSchema.coordinate_p)) {
                addField(prop, c, CollectionSchema.coordinate_p_0_coordinate);
                c++;
                addField(prop, c, CollectionSchema.coordinate_p_1_coordinate);
                c++;
            }
        }
            
        //if (solrScheme.contains(YaCySchema.author)) {addField(prop, c, YaCySchema.author_sxt);}
        prop.put("fields", c);

        if (schemaName.equals(CollectionSchema.CORE_NAME)) {
            prop.put("copyFieldAuthor", solrSchema.contains(CollectionSchema.author) ? 1 : 0);
            
            prop.put("solruniquekey", CollectionSchema.id.getSolrFieldName());
            prop.put("solrdefaultsearchfield",
                    solrSchema.contains(CollectionSchema.text_t) ? CollectionSchema.text_t.getSolrFieldName() :
                    solrSchema.contains(CollectionSchema.fuzzy_signature_text_t) ? CollectionSchema.fuzzy_signature_text_t.getSolrFieldName() :
                    solrSchema.contains(CollectionSchema.h1_txt) ? CollectionSchema.h1_txt.getSolrFieldName() :
                    CollectionSchema.id.getSolrFieldName()
                    );
        } else {
            prop.put("copyFieldAuthor", 0);
            
            prop.put("solruniquekey", WebgraphSchema.id.getSolrFieldName());
            prop.put("solrdefaultsearchfield",
                    solrSchema.contains(WebgraphSchema.target_linktext_t) ? WebgraphSchema.target_linktext_t.getSolrFieldName() :
                    solrSchema.contains(WebgraphSchema.target_name_t) ? WebgraphSchema.target_name_t.getSolrFieldName() :
                    solrSchema.contains(WebgraphSchema.target_alt_t) ? WebgraphSchema.target_alt_t.getSolrFieldName() :
                    WebgraphSchema.id.getSolrFieldName()
                    );
        }     

        // add CORS Access header
        final ResponseHeader outgoingHeader = new ResponseHeader(200);
        outgoingHeader.put(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
        prop.setOutgoingHeader(outgoingHeader);   
        
        // return rewrite properties
        return prop;
    }
    
    private static void addField(servletProperties prop, int c, SchemaDeclaration field) {
        prop.put("fields_" + c + "_solrname", field.getSolrFieldName());
        prop.put("fields_" + c + "_type", field.getType().printName());
        prop.put("fields_" + c + "_comment", field.getComment());
        prop.put("fields_" + c + "_indexedChecked", field.isIndexed() ? 1 : 0);
        prop.put("fields_" + c + "_storedChecked", field.isStored() ? 1 : 0);
        prop.put("fields_" + c + "_multiValuedChecked", field.isMultiValued() ? 1 : 0);
        prop.put("fields_" + c + "_omitNormsChecked", field.isOmitNorms() ? 1 : 0);
    }
}
