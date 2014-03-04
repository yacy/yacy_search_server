/**
 *  SolrSingleConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
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

package net.yacy.cora.federate.solr.connector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import net.yacy.cora.federate.solr.instance.SolrInstance;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.solr.instance.ShardInstance;

import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

public class RemoteSolrConnector extends SolrServerConnector implements SolrConnector {

    private final SolrInstance instance;
    private final String corename;
    private final boolean useBinaryResponseWriter;
    
    /**
     * create a new solr connector
     * @param instance the instance of the remote solr url, like http://192.168.1.60:8983/solr/ or http://admin:pw@192.168.1.60:8983/solr/
     * @throws IOException
     */
    public RemoteSolrConnector(final SolrInstance instance, final boolean useBinaryResponseWriter) throws IOException {
        super();
        this.instance = instance;
        this.useBinaryResponseWriter = useBinaryResponseWriter;
        this.corename = this.instance.getDefaultCoreName();
        SolrServer s = instance.getServer(this.corename);
        super.init(s);
    }
    
    public RemoteSolrConnector(final SolrInstance instance, final boolean useBinaryResponseWriter, String corename) {
        super();
        this.instance = instance;
        this.useBinaryResponseWriter = useBinaryResponseWriter;
        this.corename = corename == null ? this.instance.getDefaultCoreName() : corename;
        SolrServer s = instance.getServer(this.corename);
        super.init(s);
    }

    @Override
    public int hashCode() {
        return this.instance.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RemoteSolrConnector && ((RemoteSolrConnector) o).instance.equals(this.instance);
    }

    public SolrInstance getInstance() {
        return this.instance;
    }

    @Override
    public synchronized void close() {
        super.close();
    }
    
    @Override
    public int bufferSize() {
        return 0;
    }

    @Override
    public void clearCaches() {
        // we do not have a direct access to the caches here, thus we simply do nothing.
    }

    @Override
    public QueryResponse getResponseByParams(ModifiableSolrParams params) throws IOException {
        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        String q = params.get("q");
        String threadname = Thread.currentThread().getName();
        if (q != null) Thread.currentThread().setName("solr query: q = " + q);
        
        QueryRequest request = new QueryRequest(params);
        ResponseParser responseParser = useBinaryResponseWriter ? new BinaryResponseParser() : new XMLResponseParser();
        request.setResponseParser(responseParser);
        long t = System.currentTimeMillis();
        NamedList<Object> result = null;
        try {
            result = this.server.request(request);
        } catch (final Throwable e) {
            //ConcurrentLog.logException(e);
            throw new IOException(e.getMessage());
            /*
            Log.logException(e);
            server = instance.getServer(this.corename);
            super.init(server);
            try {
                result = server.request(request);
            } catch (final Throwable e1) {
                throw new IOException(e1.getMessage());
            }
            */
        }
        QueryResponse response = new QueryResponse(result, this.server);
        response.setElapsedTime(System.currentTimeMillis() - t);

        if (q != null) Thread.currentThread().setName(threadname);
        return response;
    }

    public static void main(final String args[]) {
        RemoteSolrConnector solr;
        try {
            RemoteInstance instance = new RemoteInstance("http://127.0.0.1:8983/solr/", null, "collection1", 10000);
            ArrayList<RemoteInstance> instances = new ArrayList<RemoteInstance>();
            instances.add(instance);
            solr = new RemoteSolrConnector(new ShardInstance(instances, ShardSelection.Method.MODULO_HOST_MD5, true), true, "solr");
            solr.clear();
            final File exampleDir = new File("test/parsertest/");
            long t, t0, a = 0;
            int c = 0;
            System.out.println("push files in " + exampleDir.getAbsolutePath() + " to Solr");
            for (final String s: exampleDir.list()) {
                if (s.startsWith(".")) continue;
                t = System.currentTimeMillis();
                solr.add(new File(exampleDir, s), s);
                t0 = (System.currentTimeMillis() - t);
                a += t0;
                c++;
                System.out.println("pushed file " + s + " to solr, " + t0 + " milliseconds");
            }
            System.out.println("pushed " + c + " files in " + a + " milliseconds, " + (a / c) + " milliseconds average; " + (60000 / a * c) + " PPM");
            solr.commit(false);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

}
