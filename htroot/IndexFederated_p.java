/**
 *  IndexFederated_p
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.05.2011 at http://yacy.net
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.solr.common.SolrException;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.solr.instance.ShardInstance;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.OS;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexFederated_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null && post.containsKey("setrwi")) {
            //yacy
            boolean post_core_rwi = post.getBoolean(SwitchboardConstants.CORE_SERVICE_RWI);
            final boolean previous_core_rwi = sb.index.connectedRWI() && env.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, false);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_RWI, post_core_rwi);
            if (previous_core_rwi && !post_core_rwi) sb.index.disconnectRWI(); // switch off
            if (!previous_core_rwi && post_core_rwi) try {
                final int wordCacheMaxCount = (int) sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
                final long fileSizeMax = (OS.isWindows) ? sb.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : sb.getConfigLong( "filesize.max.other", Integer.MAX_VALUE);
                sb.index.connectRWI(wordCacheMaxCount, fileSizeMax);
            } catch (final IOException e) { ConcurrentLog.logException(e); } // switch on
        }

        if (post != null && post.containsKey("setcitation")) {
            boolean post_core_citation = post.getBoolean(SwitchboardConstants.CORE_SERVICE_CITATION);
            final boolean previous_core_citation = sb.index.connectedCitation() && env.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, false);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_CITATION, post_core_citation);
            if (previous_core_citation && !post_core_citation) sb.index.disconnectCitation(); // switch off
            if (!previous_core_citation && post_core_citation) try {
                final int wordCacheMaxCount = (int) sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
                final long fileSizeMax = (OS.isWindows) ? sb.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : sb.getConfigLong( "filesize.max.other", Integer.MAX_VALUE);
                sb.index.connectCitation(wordCacheMaxCount, fileSizeMax);
            } catch (final IOException e) { ConcurrentLog.logException(e); } // switch on
            boolean webgraph = post.getBoolean(SwitchboardConstants.CORE_SERVICE_WEBGRAPH);
            sb.index.fulltext().setUseWebgraph(webgraph);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, webgraph);
        }
        
        if (post != null && post.containsKey("setsolr")) {
            boolean post_core_fulltext = post.getBoolean(SwitchboardConstants.CORE_SERVICE_FULLTEXT);
            final boolean previous_core_fulltext = sb.index.fulltext().connectedLocalSolr() && env.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, false);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_FULLTEXT, post_core_fulltext);

            if (previous_core_fulltext && !post_core_fulltext) {
                // switch off
                sb.index.fulltext().disconnectLocalSolr();
            }
            if (!previous_core_fulltext && post_core_fulltext) {
                // switch on
                try { sb.index.fulltext().connectLocalSolr(); } catch (final IOException e) { ConcurrentLog.logException(e); }
            }
            
            // solr
            final boolean solrRemoteWasOn = sb.index.fulltext().connectedRemoteSolr() && env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, true);
            String solrurls = post.get("solr.indexing.url", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr"));
            final boolean solrRemoteIsOnAfterwards = post.getBoolean("solr.indexing.solrremote") & solrurls.length() > 0;
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, solrRemoteIsOnAfterwards);
            final BufferedReader r = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(UTF8.getBytes(solrurls))));
            final StringBuilder s = new StringBuilder();
            String s0;
            try {
                while ((s0 = r.readLine()) != null) {
                    s0 = s0.trim();
                    if (s0.length() > 0) {
                        s.append(s0).append(',');
                    }
                }
            } catch (final IOException e1) {
            }
            if (s.length() > 0) {
                s.setLength(s.length() - 1);
            }
            solrurls = s.toString().trim();
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, solrurls);
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, post.get("solr.indexing.sharding", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, "modulo-host-md5")));
            
            if (solrRemoteWasOn && !solrRemoteIsOnAfterwards) {
                // switch off
                try {
                    sb.index.fulltext().disconnectRemoteSolr();
                } catch (final Throwable e) {
                    ConcurrentLog.logException(e);
                }
            }
            
            final boolean writeEnabled = post.getBoolean("solr.indexing.solrremote.writeenabled");
            sb.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_WRITEENABLED, writeEnabled);

            if (solrRemoteIsOnAfterwards) try {
                if (solrRemoteWasOn) sb.index.fulltext().disconnectRemoteSolr();
                // switch on
                final boolean usesolr = sb.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) & solrurls.length() > 0;
                final int solrtimeout = sb.getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_TIMEOUT, 10000);
                
                try {
                    if (usesolr) {
                        ArrayList<RemoteInstance> instances = RemoteInstance.getShardInstances(solrurls, null, null, solrtimeout);
                        sb.index.fulltext().connectRemoteSolr(instances, writeEnabled);
                    } else {
                        sb.index.fulltext().disconnectRemoteSolr();
                    }
                } catch (final Throwable e) {
                    ConcurrentLog.logException(e);
                    try {
                        sb.index.fulltext().disconnectRemoteSolr();
                    } catch (final Throwable ee) {
                        ConcurrentLog.logException(ee);
                    }
                }
            } catch (final SolrException e) {
                ConcurrentLog.severe("IndexFederated_p", "change of solr connection failed", e);
            }
            
            boolean lazy = post.getBoolean("solr.indexing.lazy");
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, lazy);
        }

        // show solr host table
        if (!sb.index.fulltext().connectedRemoteSolr()) {
            prop.put("table", 0);
        } else {
            prop.put("table", 1);
            final SolrConnector solr = sb.index.fulltext().getDefaultRemoteSolrConnector();
            final long[] size = new long[]{((RemoteSolrConnector) solr).getSize()};
            final ArrayList<String> urls = ((ShardInstance) ((RemoteSolrConnector) solr).getInstance()).getAdminInterfaces();
            boolean dark = false;
            for (int i = 0; i < size.length; i++) {
                prop.put("table_list_" + i + "_dark", dark ? 1 : 0); dark = !dark;
                prop.put("table_list_" + i + "_url", urls.get(i));
                prop.put("table_list_" + i + "_size", size[i]);
            }
            prop.put("table_list", size.length);
        }

        prop.put(SwitchboardConstants.CORE_SERVICE_FULLTEXT + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, false) ? 1 : 0);
        prop.put(SwitchboardConstants.CORE_SERVICE_RWI + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, false) ? 1 : 0);
        prop.put(SwitchboardConstants.CORE_SERVICE_CITATION + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, false) ? 1 : 0);
        prop.put(SwitchboardConstants.CORE_SERVICE_WEBGRAPH + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, false) ? 1 : 0);
        prop.put("solr.indexing.solrremote.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) ? 1 : 0);
        prop.put("solr.indexing.url", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr").replace(",", "\n"));
        prop.put("solr.indexing.sharding", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, "modulo-host-md5"));
        prop.put("solr.indexing.solrremote.writeenabled.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_WRITEENABLED, true));
        prop.put("solr.indexing.lazy.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, true) ? 1 : 0);
        
        // return rewrite properties
        return prop;
    }
}
