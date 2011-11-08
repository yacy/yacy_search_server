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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.solr.SolrChardingConnector;
import net.yacy.cora.services.federated.solr.SolrChardingSelection;
import net.yacy.cora.services.federated.solr.SolrConnector;
import net.yacy.cora.services.federated.solr.SolrScheme;
import net.yacy.cora.services.federated.solr.SolrSingleConnector;
import net.yacy.cora.storage.ConfigurationSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segments;
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
            String solrurls = post.get("solr.indexing.url", env.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr"));
            final BufferedReader r = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(UTF8.getBytes(solrurls))));
            final StringBuilder s = new StringBuilder();
            String s0;
            try {
                while ((s0 = r.readLine()) != null) {
                    s0 = s0.trim();
                    if (s0.length() > 0) s.append(s0).append(',');
                }
            } catch (final IOException e1) {
            }
            if (s.length() > 0) s.setLength(s.length() - 1);
            solrurls = s.toString().trim();
            env.setConfig("federated.service.solr.indexing.url", solrurls);
            env.setConfig("federated.service.solr.indexing.charding", post.get("solr.indexing.charding", env.getConfig("federated.service.solr.indexing.charding", "modulo-host-md5")));
            final String schemename = post.get("solr.indexing.schemefile", env.getConfig("federated.service.solr.indexing.schemefile", "solr.keys.default.list"));
            env.setConfig("federated.service.solr.indexing.schemefile", schemename);

            if (solrWasOn) {
                // switch off
                sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).getSolr().close();
                sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).connectSolr(null);
            }

            final SolrScheme scheme = new SolrScheme(new File(env.getDataPath(), "DATA/SETTINGS/" + schemename));

            if (solrIsOnAfterwards) {
                // switch on
                final boolean usesolr = sb.getConfigBool("federated.service.solr.indexing.enabled", false) & solrurls.length() > 0;
                try {
                    sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).connectSolr((usesolr) ? new SolrChardingConnector(solrurls, scheme, SolrChardingSelection.Method.MODULO_HOST_MD5, 10000) : null);
                } catch (final IOException e) {
                    Log.logException(e);
                    sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).connectSolr(null);
                }
            }

            // read index scheme table flags
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
        if (sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).getSolr() == null) {
            prop.put("table", 0);
        } else {
            prop.put("table", 1);
            final SolrConnector solr = sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).getSolr();
            final long[] size = (solr instanceof SolrChardingConnector) ? ((SolrChardingConnector) solr).getSizeList() : new long[]{((SolrSingleConnector) solr).getSize()};
            final String[] urls = (solr instanceof SolrChardingConnector) ? ((SolrChardingConnector) solr).getAdminInterfaceList() : new String[]{((SolrSingleConnector) solr).getAdminInterface()};
            boolean dark = false;
            for (int i = 0; i < size.length; i++) {
                prop.put("table_list_" + i + "_dark", dark ? 1 : 0); dark = !dark;
                prop.put("table_list_" + i + "_url", urls[i]);
                prop.put("table_list_" + i + "_size", size[i]);
            }
            prop.put("table_list", size.length);
        }

        // write scheme
        SolrScheme scheme = (sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).getSolr() == null) ? null : sb.indexSegments.segment(Segments.Process.LOCALCRAWLING).getSolr().getScheme();
        final String schemename = sb.getConfig("federated.service.solr.indexing.schemefile", "solr.keys.default.list");
        if (scheme == null) {
            scheme = new SolrScheme(new File(env.getDataPath(), "DATA/SETTINGS/" + schemename));
        }
        final Iterator<ConfigurationSet.Entry> i = scheme.allIterator();
        int c = 0;
        boolean dark = false;
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

        // fill attribute fields
        prop.put("yacy.indexing.enabled.checked", env.getConfigBool("federated.service.yacy.indexing.enabled", true) ? 1 : 0);
        prop.put("solr.indexing.enabled.checked", env.getConfigBool("federated.service.solr.indexing.enabled", false) ? 1 : 0);
        prop.put("solr.indexing.url", env.getConfig("federated.service.solr.indexing.url", "http://127.0.0.1:8983/solr").replace(",", "\n"));
        prop.put("solr.indexing.charding", env.getConfig("federated.service.solr.indexing.charding", "modulo-host-md5"));
        prop.put("solr.indexing.schemefile", schemename);

        // return rewrite properties
        return prop;
    }
}
