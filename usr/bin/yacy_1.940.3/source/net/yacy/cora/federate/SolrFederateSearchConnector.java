/**
 * SolrFederateSearchConnector.java
 * Copyright 2015 by Burkhard Buelte
 * First released 19.01.2015 at https://yacy.net
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */
package net.yacy.cora.federate;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryParams;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;

/**
 * Search connecter to collect query results from remote Solr systems which
 * provide results as Solr documents
 */
public class SolrFederateSearchConnector extends AbstractFederateSearchConnector {

    private String corename;

    @Override
    public boolean init(String instance, String cfgFileName) {
        boolean initResult = super.init(instance, cfgFileName); // init local schema cfg
        if (initResult) {
            if (this.localcfg.contains("_baseurl")) {
                setBaseurl(this.localcfg.get("_baseurl").getValue());
            } else {
                ConcurrentLog.config(instance, "no _baseurl given in config file "+cfgFileName);
                initResult = false;
            }
            if (this.localcfg.contains("_corename")) {
                setCoreName(this.localcfg.get("_corename").getValue());
            } else {
                ConcurrentLog.config(instance, "no _corename given in config file "); // not mandatory
                this.corename = "";
            }
        }
        return initResult;
    }

    public void setBaseurl(String url) {
        if (url.endsWith("/")) {
            this.baseurl = url;
        } else {
            this.baseurl = url + "/";
        }
    }

    public void setCoreName(String core) {
        this.corename = core;
    }

    /**
     * Core query implementation
     * all query and search routines will use this routine to query the remote system
     *
     * @param query
     * @return list of solr documents (metadata) accordng to local YaCy internal schema
     */
    @Override
    public List<URIMetadataNode> query(QueryParams query) {

        List<URIMetadataNode> docs = new ArrayList<URIMetadataNode>();
        Collection<String> remotecorename = new ArrayList<String>();
        remotecorename.add(corename);
        ModifiableSolrParams msp = new SolrQuery(query.getQueryGoal().getQueryString(false));
        msp.add(CommonParams.QT, "/"); // important to override default append of /select
        msp.add(CommonParams.ROWS, Integer.toString(query.itemsPerPage));
        try {
			boolean trustSelfSignedOnAuthenticatedServer = SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED_DEFAULT;
			if (Switchboard.getSwitchboard() != null) {
				trustSelfSignedOnAuthenticatedServer = Switchboard.getSwitchboard().getConfigBool(
						SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED,
						SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED_DEFAULT);
			}
			RemoteInstance instance = new RemoteInstance(baseurl, remotecorename, corename, 20000,
					trustSelfSignedOnAuthenticatedServer, Long.MAX_VALUE, false);
            try {
				boolean useBinaryResponseWriter = SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED_DEFAULT;
				if (Switchboard.getSwitchboard() != null) {
					useBinaryResponseWriter = Switchboard.getSwitchboard().getConfigBool(
							SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED,
							SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED_DEFAULT);
				}
                SolrConnector solrConnector = new RemoteSolrConnector(instance,  useBinaryResponseWriter);
                try {
                    this.lastaccesstime = System.currentTimeMillis();
                    SolrDocumentList docList = solrConnector.getDocumentListByParams(msp);
                    // convert to YaCy schema documentlist
                    for (SolrDocument doc : docList) {
                        try {
                            URIMetadataNode anew = toYaCySchema(doc);
                            docs.add(anew);
                        } catch (MalformedURLException ex) { }
                    }
                } catch (IOException | SolrException e) {
                } finally {
                    solrConnector.close();
                }
            } catch (Throwable ee) {
            } finally {
                instance.close();
            }
        } catch (IOException eee) {
        }
        return docs;
    }
}
