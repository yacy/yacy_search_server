// ConfigHeuristics_p.java
// --------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 26.06.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2012-12-19 $
// $LastChangedRevision: $
// $LastChangedBy: reger $
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

import com.google.common.io.Files;
import java.io.File;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import java.io.IOException;
import java.util.Iterator;
import net.yacy.cora.federate.yacy.ConfigurationSet;
import net.yacy.cora.federate.opensearch.OpenSearchConnector;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigHeuristics_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        String osderrmsg = "";
        if (post != null) {

            // store this call as api call
            sb.tables.recordAPICall(post, "ConfigHeuristics.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "heuristic settings");

            if (post.containsKey("site_on")) sb.setConfig("heuristic.site", true);
            if (post.containsKey("site_off")) sb.setConfig("heuristic.site", false);
            if (post.containsKey("searchresult_on")) sb.setConfig("heuristic.searchresults", true);
            if (post.containsKey("searchresult_off")) sb.setConfig("heuristic.searchresults", false);
            if (post.containsKey("searchresultglobal_on")) sb.setConfig("heuristic.searchresults.crawlglobal", true);
            if (post.containsKey("searchresultglobal_off")) sb.setConfig("heuristic.searchresults.crawlglobal", false);
            if (post.containsKey("blekko_on")) sb.setConfig("heuristic.blekko", true);
            if (post.containsKey("blekko_off")) sb.setConfig("heuristic.blekko", false);
            if (post.containsKey("twitter_on")) sb.setConfig("heuristic.twitter", true);
            if (post.containsKey("twitter_off")) sb.setConfig("heuristic.twitter", false);
            if (post.containsKey("opensearch_on")) {
                sb.setConfig("heuristic.opensearch", true);
                // re-read config (and create work table)
                OpenSearchConnector os = new OpenSearchConnector(sb, true);
                if (os.getSize() == 0) {
                    osderrmsg = "no active search targets are configured";
        }

            }
            if (post.containsKey("opensearch_off")) sb.setConfig("heuristic.opensearch", false);
            if (post.containsKey("discoverosd")) {
                final boolean metafieldNOTavailable = sb.index.fulltext().getSolrScheme().containsDisabled(YaCySchema.outboundlinks_tag_txt.name());
                if (!metafieldNOTavailable) {
                OpenSearchConnector osc = new OpenSearchConnector(sb, false);
                if (osc.discoverFromSolrIndex(sb)) {
                    osderrmsg = "started background search for target systems, refresh page after some minutes";
                } else {
                    osderrmsg = "Solr index needs to be available and field outboundlinks_tag_txt on";
                }
                } else {
                    osderrmsg = "Error: field outboundlinks_tag_txt needs to be activated in Solr index";
                }
            }

            final String tmpurl = post.get("ossys_newurl");
            // if user entered new opensearch url but hit the wrong button, simulate "add" button
            if (tmpurl != null && !tmpurl.isEmpty()) post.put("addnewosd", 1);

            if (post.containsKey("addnewosd")) {
                // add new entry to config file
                final String tmpname = post.get("ossys_newtitle");
                if (tmpname != null && tmpurl !=null) {
                    if (!tmpname.isEmpty() && !tmpurl.isEmpty() && tmpurl.toLowerCase().contains("{searchterms}")) {
                        final String tmpcomment = post.get("ossys_newcomment");
                        OpenSearchConnector osc = new OpenSearchConnector(sb,false);
                        osc.add (tmpname,tmpurl,false,tmpcomment);
                    } else osderrmsg = "Url template must contain '{searchTerms}'";
                }
            }

            if (post.containsKey("setopensearch")) {
                // read index scheme table flags
                writeopensearchcfg (sb,post);
             }

            if (post.containsKey("switchsolrfieldson")) {
                final boolean metafieldNOTavailable = sb.index.fulltext().getSolrScheme().containsDisabled(YaCySchema.outboundlinks_tag_txt.name());
                if (metafieldNOTavailable) {
                    ConfigurationSet.Entry entry;
                    entry = sb.index.fulltext().getSolrScheme().get(YaCySchema.outboundlinks_tag_txt.name());
                    if (entry != null && !entry.enabled()) {
                        entry.setEnable(true);
                    }
                    entry = sb.index.fulltext().getSolrScheme().get(YaCySchema.inboundlinks_tag_txt.name());
                    if (entry != null && !entry.enabled()) {
                        entry.setEnable(true);
                    }
                    try {
                        sb.index.fulltext().getSolrScheme().commit();
                    } catch (IOException ex) {}
                }
            }

            // copy default opensearch heuristic config with sample entries
            if (post.containsKey("copydefaultosdconfig")) {
                // prepare a solr index profile switch list
                final File osdDefaultConfig = new File(sb.getDataPath(), "defaults/heuristicopensearch.conf");
                final File osdConfig = new File(sb.getDataPath(), "DATA/SETTINGS/heuristicopensearch.conf");
                if (!osdConfig.exists() && osdDefaultConfig.exists()) {
                    try {
                        Files.copy(osdDefaultConfig, osdConfig);
                    } catch (IOException ex) {
                        osderrmsg = "file I/O error during copy";
                    }
                } else {osderrmsg = "config file exists or default doesn't exist";}
            }
        }

        final boolean showmetafieldbutton = sb.index.fulltext().getSolrScheme().containsDisabled(YaCySchema.outboundlinks_tag_txt.name());
        if (showmetafieldbutton) prop.put("osdsolrfieldswitch",1);
        prop.put("site.checked", sb.getConfigBool("heuristic.site", false) ? 1 : 0);
        prop.put("searchresult.checked", sb.getConfigBool("heuristic.searchresults", false) ? 1 : 0);
        prop.put("searchresultglobal.checked", sb.getConfigBool("heuristic.searchresults.crawlglobal", false) ? 1 : 0);
        prop.put("blekko.checked", sb.getConfigBool("heuristic.blekko", false) ? 1 : 0);
        prop.put("twitter.checked", sb.getConfigBool("heuristic.twitter", false) ? 1 : 0);
        prop.put("opensearch.checked", sb.getConfigBool("heuristic.opensearch", false) ? 1 : 0);

        // display config file content
        final File f = new File (sb.getDataPath(),"DATA/SETTINGS/heuristicopensearch.conf");
        ConfigurationSet p = new ConfigurationSet(f);
        int c = 0;
        boolean dark = false;
        Iterator<ConfigurationSet.Entry> i = p.entryIterator();
        while (i.hasNext()) {
            ConfigurationSet.Entry e = i.next();
            prop.put("osdcfg_" + c + "_dark", dark ? 1 : 0);
            dark = !dark;
            prop.put("osdcfg_" + c + "_checked", e.enabled() ? 1 : 0);
            prop.putHTML("osdcfg_" + c + "_title", e.key());
            prop.putHTML("osdcfg_" + c + "_comment", e.getComment() != null ? e.getComment() : "");

            String tmps = e.getValue();
            prop.putHTML("osdcfg_" + c + "_url", tmps);
            tmps = tmps.substring(0,tmps.lastIndexOf("/"));
            prop.putHTML("osdcfg_" + c + "_urlhostlink", tmps);

            c++;
        }
        prop.put("osdcfg", c);
        prop.putHTML("osderrmsg",osderrmsg);
        return prop;
    }

    private static void writeopensearchcfg(final Switchboard sb, final serverObjects post) {
        // read index scheme table flags

        final File f = new File(sb.getDataPath(), "DATA/SETTINGS/heuristicopensearch.conf");
        ConfigurationSet cfg = new ConfigurationSet(f);
        final Iterator<ConfigurationSet.Entry> cfgentries = cfg.entryIterator();
        ConfigurationSet.Entry entry;
        boolean modified = false; // flag to remember changes
        while (cfgentries.hasNext()) {
            entry = cfgentries.next();
            final String sfn = post.get("ossys_url_" + entry.key());
            if (sfn != null) {
                if (!sfn.equals(entry.getValue())) {
                    entry.setValue(sfn);
                    modified = true;
}
            }
            // set enable flag
            String v = post.get("ossys_" + entry.key());
            boolean c = v != null && v.equals("checked");
            if (entry.enabled() != c) {
                entry.setEnable(c);
                modified = true;
            }
            // delete entry from config
            v = post.get("ossys_del_" + entry.key());
            c = v != null && v.equals("checked");
            if (c) {
                cfgentries.remove();
                modified = true;
            }
        }
        if (modified) { // save settings to config file if modified
            try {
                cfg.commit();
            } catch (IOException ex) {
            }
        }
        // re-read config (and create/update work table)
        if (sb.getConfigBool("heuristic.opensearch", true)) {
            OpenSearchConnector os = new OpenSearchConnector(sb, true);
        }
    }
}
