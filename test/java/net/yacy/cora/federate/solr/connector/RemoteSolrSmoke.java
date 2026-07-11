package net.yacy.cora.federate.solr.connector;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;

import net.yacy.cora.federate.solr.instance.RemoteInstance;

/** Command-line integration probe for YaCY's Apache-based remote Solr path. */
public final class RemoteSolrSmoke {

    private RemoteSolrSmoke() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: RemoteSolrSmoke SOLR_BASE_URL");
        }
        final RemoteInstance instance = new RemoteInstance(
                args[0], null, "collection1", 10_000, false, Long.MAX_VALUE, false);
        try {
            final RemoteSolrConnector connector = new RemoteSolrConnector(instance, false);
            final QueryResponse response = connector.getResponseByParams(
                    new ModifiableSolrParams().set("q", "*:*").set("rows", 0));
            if (response.getResults() == null) {
                throw new IllegalStateException("remote Solr response has no result list");
            }
            System.out.println("Remote Solr numFound=" + response.getResults().getNumFound());
        } finally {
            instance.close();
        }
    }
}
