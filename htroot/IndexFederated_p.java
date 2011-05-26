/**
 *  IndexFederated_p
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.05.2011 at http://yacy.net
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

import java.io.IOException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.solr.SolrChardingConnector;
import net.yacy.cora.services.federated.solr.SolrChardingSelection;
import net.yacy.cora.services.federated.solr.SolrScheme;
import net.yacy.kelondro.logging.Log;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexFederated_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        Switchboard sb = (Switchboard) env;

        if (post != null && post.containsKey("set")) {
            // yacy
            env.setConfig("federated.service.yacy.indexing.enabled", post.getBoolean("yacy.indexing.enabled", false));
            
            // solr
            boolean solrWasOn = env.getConfigBool("federated.service.solr.indexing.enabled", true);
            boolean solrIsOnAfterwards = post.getBoolean("solr.indexing.enabled", false);
            env.setConfig("federated.service.solr.indexing.enabled", solrIsOnAfterwards);
            env.setConfig("federated.service.solr.indexing.url", post.get("solr.indexing.url", env.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr")));
            env.setConfig("federated.service.solr.indexing.charding", post.get("solr.indexing.charding", env.getConfig("federated.service.solr.indexing.charding", "modulo-host-md5")));
            env.setConfig("federated.service.solr.indexing.scheme", post.get("solr.indexing.scheme", env.getConfig("federated.service.solr.indexing.scheme", "SolrCellExtended")));

            if (solrWasOn && !solrIsOnAfterwards) {
                // switch off
                sb.solrConnector.close();
                sb.solrConnector = null;
            }
            
            if (!solrWasOn && solrIsOnAfterwards) {
                // switch on
                String solrurls = sb.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr");
                boolean usesolr = sb.getConfigBool("federated.service.solr.indexing.enabled", false) & solrurls.length() > 0;
                try {
                    sb.solrConnector = (usesolr) ? new SolrChardingConnector(solrurls, SolrScheme.SolrCellExtended, SolrChardingSelection.Method.MODULO_HOST_MD5) : null;
                } catch (IOException e) {
                    Log.logException(e);
                    sb.solrConnector = null;
                }
            }
        }
        
        // show solr host table
        if (sb.solrConnector == null) {
            prop.put("table", 0);
        } else {
            prop.put("table", 1);
            try {
                long[] size = sb.solrConnector.getSizeList();
                String[] urls = sb.solrConnector.getAdminInterfaceList();
                boolean dark = false;
                for (int i = 0; i < size.length; i++) {
                    prop.put("table_list_" + i + "_dark", dark ? 1 : 0); dark = !dark;
                    prop.put("table_list_" + i + "_url", urls[i]);
                    prop.put("table_list_" + i + "_size", size[i]);
                }
                prop.put("table_list", size.length);
            } catch (IOException e) {
                Log.logException(e);
                prop.put("table", 0);
            }
        }
        
        // fill attribute fields
        prop.put("yacy.indexing.enabled.checked", env.getConfigBool("federated.service.yacy.indexing.enabled", true) ? 1 : 0);
        prop.put("solr.indexing.enabled.checked", env.getConfigBool("federated.service.solr.indexing.enabled", false) ? 1 : 0);
        prop.put("solr.indexing.url", env.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr"));
        prop.put("solr.indexing.charding", env.getConfig("federated.service.solr.indexing.charding", "modulo-host-md5"));
        prop.put("solr.indexing.scheme", env.getConfig("federated.service.solr.indexing.scheme", "SolrCellExtended"));

        // return rewrite properties
        return prop;
    }
}
