/**
 *  SolrSingleConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
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

package net.yacy.cora.services.federated.solr;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;
import net.yacy.kelondro.logging.Log;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;


public class SolrSingleConnector implements SolrConnector {

    private final String solrurl, host, solrpath, solraccount, solrpw;
    private final int port;
    private HttpSolrServer server;

    /**
     * create a new solr connector
     * @param url the solr url, like http://192.168.1.60:8983/solr/ or http://admin:pw@192.168.1.60:8983/solr/
     * @param scheme
     * @throws IOException
     */
    public SolrSingleConnector(final String url) throws IOException {
        this.solrurl = url;

        // connect using authentication
        final MultiProtocolURI u = new MultiProtocolURI(this.solrurl);
        this.host = u.getHost();
        this.port = u.getPort();
        this.solrpath = u.getPath();
        final String userinfo = u.getUserInfo();
        if (userinfo == null || userinfo.length() == 0) {
            this.solraccount = ""; this.solrpw = "";
        } else {
            final int p = userinfo.indexOf(':');
            if (p < 0) {
                this.solraccount = userinfo; this.solrpw = "";
            } else {
                this.solraccount = userinfo.substring(0, p); this.solrpw = userinfo.substring(p + 1);
            }
        }
        if (this.solraccount.length() > 0) {
            final DefaultHttpClient client = new DefaultHttpClient() {
                @Override
                protected HttpContext createHttpContext() {
                    HttpContext context = super.createHttpContext();
                    AuthCache authCache = new BasicAuthCache();
                    BasicScheme basicAuth = new BasicScheme();
                    HttpHost targetHost = new HttpHost(u.getHost(), u.getPort(), u.getProtocol());
                    authCache.put(targetHost, basicAuth);
                    context.setAttribute(ClientContext.AUTH_CACHE, authCache);
                    return context;
                }
            };
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(this.host, AuthScope.ANY_PORT), new UsernamePasswordCredentials(this.solraccount, this.solrpw));
            client.setCredentialsProvider(credsProvider);
            this.server = new HttpSolrServer("http://" + this.host + ":" + this.port + this.solrpath, client);
        } else {
            this.server = new HttpSolrServer(this.solrurl);
        }
        this.server.setAllowCompression(true);
        this.server.setConnectionTimeout(60000);
        this.server.setMaxRetries(10);
        this.server.setSoTimeout(60000);
    }

    @Override
    public synchronized void close() {
        try {
            this.server.commit();
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getSize() {
        try {
            final SolrDocumentList list = get("*:*", 0, 1);
            return list.getNumFound();
        } catch (final Throwable e) {
            Log.logException(e);
            return 0;
        }
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        try {
            this.server.deleteByQuery("*:*");
            this.server.commit();
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final String id) throws IOException {
        try {
            this.server.deleteById(id);
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(final List<String> ids) throws IOException {
        try {
            this.server.deleteById(ids);
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean exists(final String id) throws IOException {
        try {
            final SolrDocumentList list = get("id:" + id, 0, 1);
            return list.getNumFound() > 0;
        } catch (final Throwable e) {
            Log.logException(e);
            return false;
        }
    }

    public void add(final File file, final String solrId) throws IOException {
        final ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addFile(file);
        up.setParam("literal.id", solrId);
        up.setParam("uprefix", "attr_");
        up.setParam("fmap.content", "attr_content");
        //up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        try {
            this.server.request(up);
            this.server.commit();
        } catch (final Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void add(final SolrDoc solrdoc) throws IOException, SolrException {
        try {
            this.server.add(solrdoc);
            //this.server.commit();
        } catch (SolrServerException e) {
            Log.logWarning("SolrConnector", e.getMessage() + " DOC=" + solrdoc.toString());
            throw new IOException(e);
        }
    }

    @Override
    public void add(final Collection<SolrDoc> solrdocs) throws IOException, SolrException {
        ArrayList<SolrInputDocument> l = new ArrayList<SolrInputDocument>();
        for (SolrDoc d: solrdocs) l.add(d);
        try {
            this.server.add(l);
            //this.server.commit();
        } catch (SolrServerException e) {
            Log.logWarning("SolrConnector", e.getMessage() + " DOC=" + solrdocs.toString());
            throw new IOException(e);
        }
    }

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList get(final String querystring, final int offset, final int count) throws IOException {
        // construct query
        final SolrQuery query = new SolrQuery();
        query.setQuery(querystring);
        query.setRows(count);
        query.setStart(offset);
        //query.addSortField( "price", SolrQuery.ORDER.asc );

        // query the server
        //SearchResult result = new SearchResult(count);
        try {
            final QueryResponse rsp = this.server.query( query );
            final SolrDocumentList docs = rsp.getResults();
            return docs;
            // add the docs into the YaCy search result container
            /*
            for (SolrDocument doc: docs) {
                result.put(element)
            }
            */
        } catch (final Throwable e) {
            throw new IOException(e);
        }

        //return result;
    }


    public String getAdminInterface() {
        final InetAddress localhostExternAddress = Domains.myPublicLocalIP();
        final String localhostExtern = localhostExternAddress == null ? "127.0.0.1" : localhostExternAddress.getHostAddress();
        String u = this.solrurl;
        int p = u.indexOf("localhost",0); if (p < 0) p = u.indexOf("127.0.0.1",0);
        if (p >= 0) u = u.substring(0, p) + localhostExtern + u.substring(p + 9);
        return u + (u.endsWith("/") ? "admin/" : "/admin/");
    }

    public static void main(final String args[]) {
        SolrSingleConnector solr;
        try {
            //SolrScheme scheme = new SolrScheme();
            solr = new SolrSingleConnector("http://127.0.0.1:8983/solr");
            solr.clear();
            final File exampleDir = new File("/Data/workspace2/yacy/test/parsertest/");
            long t, t0, a = 0;
            int c = 0;
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
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
