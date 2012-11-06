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
import java.net.InetAddress;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;

import org.apache.commons.httpclient.HttpException;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;


public class RemoteSolrConnector extends SolrServerConnector implements SolrConnector {

    private final String solrurl, host, solrpath, solraccount, solrpw;
    private DefaultHttpClient client;
    private final int port;

    /**
     * create a new solr connector
     * @param url the solr url, like http://192.168.1.60:8983/solr/ or http://admin:pw@192.168.1.60:8983/solr/
     * @param scheme
     * @throws IOException
     */
    public RemoteSolrConnector(final String url) throws IOException {
        super();
        this.solrurl = url;

        // connect using authentication
        final MultiProtocolURI u = new MultiProtocolURI(this.solrurl);
        this.host = u.getHost();
        this.port = u.getPort();
        this.solrpath = u.getPath();
        final String userinfo = u.getUserInfo();
        if (userinfo == null || userinfo.isEmpty()) {
            this.solraccount = ""; this.solrpw = "";
        } else {
            final int p = userinfo.indexOf(':');
            if (p < 0) {
                this.solraccount = userinfo; this.solrpw = "";
            } else {
                this.solraccount = userinfo.substring(0, p); this.solrpw = userinfo.substring(p + 1);
            }
        }
        HttpSolrServer s;
        if (this.solraccount.length() > 0) {
            this.client = new DefaultHttpClient() {
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
            this.client.addRequestInterceptor(new HttpRequestInterceptor() {
                @Override
                public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
                    if (!request.containsHeader("Accept-Encoding")) request.addHeader("Accept-Encoding", "gzip");
                }

            });
            this.client.addResponseInterceptor(new HttpResponseInterceptor() {
                @Override
                public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        Header ceheader = entity.getContentEncoding();
                        if (ceheader != null) {
                            HeaderElement[] codecs = ceheader.getElements();
                            for (HeaderElement codec : codecs) {
                                if (codec.getName().equalsIgnoreCase("gzip")) {
                                    response.setEntity(new GzipDecompressingEntity(response.getEntity()));
                                    return;
                                }
                            }
                        }
                    }
                }
            });
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(this.host, AuthScope.ANY_PORT), new UsernamePasswordCredentials(this.solraccount, this.solrpw));
            this.client.setCredentialsProvider(credsProvider);
            s = new HttpSolrServer("http://" + this.host + ":" + this.port + this.solrpath, this.client);
        } else {
            s = new HttpSolrServer(this.solrurl);
        }
        s.setAllowCompression(true);
        s.setConnectionTimeout(60000);
        s.setMaxRetries(1); // Solr-Doc: No more than 1 recommended (depreciated)
        s.setSoTimeout(60000);
        super.init(s);
    }

    public void terminate() {
        if (this.client != null) this.client.getConnectionManager().shutdown();
    }

    @Override
    public synchronized void close() {
        super.close();
        this.terminate();
    }

    @Override
    public QueryResponse query(ModifiableSolrParams params) throws IOException {
        try {
            // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
            String q = params.get("q");
            String threadname = Thread.currentThread().getName();
            if (q != null) Thread.currentThread().setName("solr query: q = " + q);
            
            QueryRequest request = new QueryRequest(params);
            ResponseParser responseParser = new XMLResponseParser();
            request.setResponseParser(responseParser);
            long t = System.currentTimeMillis();
            NamedList<Object> result = server.request(request);
            QueryResponse response = new QueryResponse(result, server);
            response.setElapsedTime(System.currentTimeMillis() - t);

            if (q != null) Thread.currentThread().setName(threadname);
            return response;
        } catch (SolrServerException e) {
            throw new IOException(e);
        } catch (Throwable e) {
            throw new IOException("Error executing query", e);
        }
    }

    public String getAdminInterface() {
        final InetAddress localhostExternAddress = Domains.myPublicLocalIP();
        final String localhostExtern = localhostExternAddress == null ? "127.0.0.1" : localhostExternAddress.getHostAddress();
        String u = this.solrurl;
        int p = u.indexOf("localhost",0);
        if (p < 0) p = u.indexOf("127.0.0.1",0);
        if (p < 0) p = u.indexOf("0:0:0:0:0:0:0:1",0);
        if (p >= 0) u = u.substring(0, p) + localhostExtern + u.substring(p + 9);
        return u + (u.endsWith("/") ? "admin/" : "/admin/");
    }

    public static void main(final String args[]) {
        RemoteSolrConnector solr;
        try {
            solr = new RemoteSolrConnector("http://127.0.0.1:8983/solr");
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
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
