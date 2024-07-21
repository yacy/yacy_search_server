/**
 *  IndexFederated_p
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.05.2011 at https://yacy.net
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

package net.yacy.htroot;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.solr.common.SolrException;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.ShardSelection;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.solr.instance.ShardInstance;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.TransactionManager;
import net.yacy.kelondro.util.OS;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexFederated_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null && post.containsKey("setrwi")) {
        	/* Check the transaction is valid */
        	TransactionManager.checkPostTransaction(header, post);

            //yacy
            final boolean post_core_rwi = post.getBoolean("core.service.rwi");
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
        	/* Check the transaction is valid */
        	TransactionManager.checkPostTransaction(header, post);

            final boolean post_core_citation = post.getBoolean(SwitchboardConstants.CORE_SERVICE_CITATION);
            final boolean previous_core_citation = sb.index.connectedCitation() && env.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, false);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_CITATION, post_core_citation);
            if (previous_core_citation && !post_core_citation) sb.index.disconnectCitation(); // switch off
            if (!previous_core_citation && post_core_citation) try {
                final int wordCacheMaxCount = (int) sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
                final long fileSizeMax = (OS.isWindows) ? sb.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : sb.getConfigLong( "filesize.max.other", Integer.MAX_VALUE);
                sb.index.connectCitation(wordCacheMaxCount, fileSizeMax);
            } catch (final IOException e) { ConcurrentLog.logException(e); } // switch on
            final boolean webgraph = post.getBoolean(SwitchboardConstants.CORE_SERVICE_WEBGRAPH);
            sb.index.fulltext().setUseWebgraph(webgraph);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, webgraph);
        }

        if (post != null && post.containsKey("setsolr")) {
        	/* Check the transaction is valid */
        	TransactionManager.checkPostTransaction(header, post);

            final boolean post_core_fulltext = post.getBoolean(SwitchboardConstants.CORE_SERVICE_FULLTEXT);
			final boolean previous_core_fulltext = sb.index.fulltext().connectedLocalSolr() && env.getConfigBool(
					SwitchboardConstants.CORE_SERVICE_FULLTEXT, SwitchboardConstants.CORE_SERVICE_FULLTEXT_DEFAULT);
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
			final boolean solrRemoteWasOn = sb.index.fulltext().connectedRemoteSolr()
					&& env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED,
							SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED_DEFAULT);
            String solrurls = post.get("solr.indexing.url", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr"));
            final boolean solrRemoteIsOnAfterwards = post.getBoolean("solr.indexing.solrremote") & solrurls.length() > 0;
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, solrRemoteIsOnAfterwards);

			env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED, post
					.getBoolean(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED));

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
            final String shardMethodName = post.get("solr.indexing.sharding", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, ShardSelection.Method.MODULO_HOST_MD5.name()));
            final ShardSelection.Method shardMethod = ShardSelection.Method.valueOf(shardMethodName);
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, shardMethod.name());

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
					final boolean usesolr = sb.getConfigBool(
							SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED,
							SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED_DEFAULT)
							& solrurls.length() > 0;
                final int solrtimeout = sb.getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_TIMEOUT, 10000);
        		final boolean trustSelfSignedOnAuthenticatedServer = Switchboard.getSwitchboard().getConfigBool(
        				SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED,
        				SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED_DEFAULT);

                try {
                    if (usesolr) {
                        final ArrayList<RemoteInstance> instances = RemoteInstance.getShardInstances(solrurls, null, null, solrtimeout, trustSelfSignedOnAuthenticatedServer);
                        sb.index.fulltext().connectRemoteSolr(instances, shardMethod, writeEnabled);
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

            final boolean lazy = post.getBoolean("solr.indexing.lazy");
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, lazy);
        }

        /* Acquire a transaction token for the next POST form submission */
        try {
            prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header));
        } catch (IllegalArgumentException e) {
            sb.log.fine("access by unauthorized or unknown user: no transaction token delivered");
        }

        // show solr host table
        if (!sb.index.fulltext().connectedRemoteSolr()) {
            prop.put("table", 0);
        } else {
            prop.put("table", 1);
            final SolrConnector solr = sb.index.fulltext().getDefaultRemoteSolrConnector();
            final long[] size = new long[]{((RemoteSolrConnector) solr).getSize()};

            final boolean toExternalAddress = !header.accessFromLocalhost();
            String externalHost = null;
            if(toExternalAddress) {
            	/* The request does not come from the same computer than this peer : we get the external address used to reach it from the request headers, and we will use
            	 * it if some remote solr instance(s) URLs are configured with loopback address alias (such as 'localhost', '127.0.0.1', '[::1]')
            	 * to render the externally reachable Solr instance(s) admin URLs */
            	externalHost = header.getServerName();
            }

			final ArrayList<String> urls = ((ShardInstance) ((RemoteSolrConnector) solr).getInstance())
					.getAdminInterfaces(toExternalAddress, externalHost);
            boolean dark = false;
            for (int i = 0; i < size.length; i++) {
                prop.put("table_list_" + i + "_dark", dark ? 1 : 0); dark = !dark;
                prop.put("table_list_" + i + "_url", urls.get(i));
                prop.put("table_list_" + i + "_size", size[i]);
            }
            prop.put("table_list", size.length);
        }

		prop.put(SwitchboardConstants.CORE_SERVICE_FULLTEXT + ".checked",
				env.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT,
						SwitchboardConstants.CORE_SERVICE_FULLTEXT_DEFAULT) ? 1 : 0);
        prop.put("core.service.rwi.checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, false) ? 1 : 0);
        prop.put(SwitchboardConstants.CORE_SERVICE_CITATION + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, false) ? 1 : 0);
        prop.put(SwitchboardConstants.CORE_SERVICE_WEBGRAPH + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, false) ? 1 : 0);
		prop.put("solr.indexing.solrremote.checked",
				env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED,
						SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED_DEFAULT) ? 1 : 0);
		prop.put(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED + ".checked",
				env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED,
						SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED_DEFAULT));
        prop.put("solr.indexing.url", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr").replace(",", "\n"));
        final String thisShardingMethodName = env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, ShardSelection.Method.MODULO_HOST_MD5.name());
        int mc = 0;
        for (final ShardSelection.Method method: ShardSelection.Method.values()) {
            prop.put("solr.indexing.sharding.methods_" + mc + "_method", method.name());
            prop.put("solr.indexing.sharding.methods_" + mc + "_description", method.description);
            prop.put("solr.indexing.sharding.methods_" + mc + "_selected", method.name().equals(thisShardingMethodName) ? 1 : 0);
            mc++;
        }
        prop.put("solr.indexing.sharding.methods", mc);
        prop.put("solr.indexing.solrremote.writeenabled.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_WRITEENABLED, true));
        prop.put("solr.indexing.lazy.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, true) ? 1 : 0);

        // return rewrite properties
        return prop;
    }
}
