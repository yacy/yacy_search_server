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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.solr.SolrChardingConnector;
import net.yacy.cora.services.federated.solr.SolrChardingSelection;
import net.yacy.cora.services.federated.solr.SolrScheme;
import net.yacy.cora.storage.ConfigurationSet;
import net.yacy.kelondro.logging.Log;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexFederated_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null && post.containsKey("set")) {
            // yacy
            env.setConfig("federated.service.yacy.indexing.enabled", post.getBoolean("yacy.indexing.enabled", false));

            // solr
            final boolean solrWasOn = env.getConfigBool("federated.service.solr.indexing.enabled", true);
            final boolean solrIsOnAfterwards = post.getBoolean("solr.indexing.enabled", false);
            env.setConfig("federated.service.solr.indexing.enabled", solrIsOnAfterwards);
            env.setConfig("federated.service.solr.indexing.url", post.get("solr.indexing.url", env.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr")));
            env.setConfig("federated.service.solr.indexing.charding", post.get("solr.indexing.charding", env.getConfig("federated.service.solr.indexing.charding", "modulo-host-md5")));
            env.setConfig("federated.service.solr.indexing.schemefile", post.get("solr.indexing.schemefile", env.getConfig("federated.service.solr.indexing.schemefile", "solr.keys.default.list")));

            if (solrWasOn && !solrIsOnAfterwards) {
                // switch off
                sb.solrConnector.close();
                sb.solrConnector = null;
            }

            if (!solrWasOn && solrIsOnAfterwards) {
                // switch on
                final String solrurls = sb.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr");
                final boolean usesolr = sb.getConfigBool("federated.service.solr.indexing.enabled", false) & solrurls.length() > 0;
                final SolrScheme scheme = new SolrScheme(new File(env.getDataPath(), "DATA/SETTINGS/solr.keys.default.list"));
                try {
                    sb.solrConnector = (usesolr) ? new SolrChardingConnector(solrurls, scheme, SolrChardingSelection.Method.MODULO_HOST_MD5) : null;
                } catch (final IOException e) {
                    Log.logException(e);
                    sb.solrConnector = null;
                }
            }

            // read index scheme table flags
            final SolrScheme scheme = sb.solrConnector.getScheme();
            final Iterator<ConfigurationSet.Entry> i = scheme.allIterator();
            ConfigurationSet.Entry entry;
            while (i.hasNext()) {
                entry = i.next();
                final String v = post.get("scheme_" + entry.key());
                final boolean c = v != null && v.equals("checked");
                try {
                    if (entry.enabled()) {
                        if (!c) scheme.disable(entry.key());
                    } else {
                        if (c) scheme.enable(entry.key());
                    }
                } catch (final IOException e) {}
            }
        }

        // show solr host table
        if (sb.solrConnector == null) {
            prop.put("table", 0);
        } else {
            prop.put("table", 1);
            try {
                final long[] size = sb.solrConnector.getSizeList();
                final String[] urls = sb.solrConnector.getAdminInterfaceList();
                boolean dark = false;
                for (int i = 0; i < size.length; i++) {
                    prop.put("table_list_" + i + "_dark", dark ? 1 : 0); dark = !dark;
                    prop.put("table_list_" + i + "_url", urls[i]);
                    prop.put("table_list_" + i + "_size", size[i]);
                }
                prop.put("table_list", size.length);

                // write scheme
                final SolrScheme scheme = sb.solrConnector.getScheme();
                final Iterator<ConfigurationSet.Entry> i = scheme.allIterator();
                int c = 0;
                dark = false;
                ConfigurationSet.Entry entry;
                while (i.hasNext()) {
                    entry = i.next();
                    prop.put("scheme_" + c + "_dark", dark ? 1 : 0); dark = !dark;
                    prop.put("scheme_" + c + "_checked", scheme.contains(entry.key()) ? 1 : 0);
                    prop.putHTML("scheme_" + c + "_key", entry.key());
                    prop.putHTML("scheme_" + c + "_comment", scheme.commentHeadline(entry.key()));
                    c++;
                }
                prop.put("scheme", c);
            } catch (final IOException e) {
                Log.logException(e);
                prop.put("table", 0);
            }
        }

        // fill attribute fields
        prop.put("yacy.indexing.enabled.checked", env.getConfigBool("federated.service.yacy.indexing.enabled", true) ? 1 : 0);
        prop.put("solr.indexing.enabled.checked", env.getConfigBool("federated.service.solr.indexing.enabled", false) ? 1 : 0);
        prop.put("solr.indexing.url", env.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr"));
        prop.put("solr.indexing.charding", env.getConfig("federated.service.solr.indexing.charding", "modulo-host-md5"));
        prop.put("solr.indexing.schemefile", env.getConfig("federated.service.solr.indexing.schemefile", "solr.keys.default.list"));

        // return rewrite properties
        return prop;
    }
}
