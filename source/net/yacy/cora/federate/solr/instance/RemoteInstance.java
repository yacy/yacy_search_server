/**
 *  RemoteInstance
 *  Copyright 2013 by Michael Peter Christen
 *  First released 13.02.2013 at http://yacy.net
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

package net.yacy.cora.federate.solr.instance;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

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
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;

@SuppressWarnings("deprecation")
public class RemoteInstance implements SolrInstance {
    
    private String solrurl;
    private final Object client; // not declared as org.apache.http.impl.client.DefaultHttpClient to avoid warnings during compilation. TODO: switch to org.apache.http.impl.client.HttpClientBuilder
    private final String defaultCoreName;
    private final ConcurrentUpdateSolrServer defaultServer;
    private final Collection<String> coreNames;
    private final Map<String, ConcurrentUpdateSolrServer> server;
    private final int timeout;
    
    public static ArrayList<RemoteInstance> getShardInstances(final String urlList, Collection<String> coreNames, String defaultCoreName, final int timeout) throws IOException {
        urlList.replace(' ', ',');
        String[] urls = urlList.split(",");
        ArrayList<RemoteInstance> instances = new ArrayList<RemoteInstance>();
        for (final String u: urls) {
            RemoteInstance instance = new RemoteInstance(u, coreNames, defaultCoreName, timeout);
            instances.add(instance);
        }
        return instances;
    }
    
    public RemoteInstance(final String url, final Collection<String> coreNames, final String defaultCoreName, final int timeout) throws IOException {
        this.timeout = timeout;
        this.server= new HashMap<String, ConcurrentUpdateSolrServer>();
        this.solrurl = url == null ? "http://127.0.0.1:8983/solr/" : url; // that should work for the example configuration of solr 4.x.x
        this.coreNames = coreNames == null ? new ArrayList<String>() : coreNames;
        if (this.coreNames.size() == 0) {
            this.coreNames.add(CollectionSchema.CORE_NAME);
            this.coreNames.add(WebgraphSchema.CORE_NAME);
        }
        this.defaultCoreName = defaultCoreName == null ? CollectionSchema.CORE_NAME : defaultCoreName;
        if (!this.coreNames.contains(this.defaultCoreName)) this.coreNames.add(this.defaultCoreName);

        // check the url
        if (this.solrurl.endsWith("/")) {
            // this could mean that we have a path without a core name (correct)
            // or that the core name is appended and contains a badly '/' at the end (must be corrected)
            if (this.solrurl.endsWith(this.defaultCoreName + "/")) {
                this.solrurl = this.solrurl.substring(0, this.solrurl.length() - this.defaultCoreName.length() - 1);
            }
        } else {
            // this could mean that we have an url which ends with the core name (must be corrected)
            // or that the url has a mising '/' (must be corrected)
            if (this.solrurl.endsWith(this.defaultCoreName)) {
                this.solrurl = this.solrurl.substring(0, this.solrurl.length() - this.defaultCoreName.length());
            } else {
                this.solrurl = this.solrurl + "/";
            }
        }
        
        // Make a http client, connect using authentication. An url like
        // http://127.0.0.1:8983/solr/shard0
        // is proper, and contains the core name as last element in the path
        final MultiProtocolURL u;
        try {
            u = new MultiProtocolURL(this.solrurl + this.defaultCoreName);
        } catch (final MalformedURLException e) {
            throw new IOException(e.getMessage());
        }
        String solraccount, solrpw;
        String host = u.getHost();
        final String userinfo = u.getUserInfo();
        if (userinfo == null || userinfo.isEmpty()) {
            solraccount = ""; solrpw = "";
        } else {
            final int p = userinfo.indexOf(':');
            if (p < 0) {
                solraccount = userinfo; solrpw = "";
            } else {
                solraccount = userinfo.substring(0, p); solrpw = userinfo.substring(p + 1);
            }
        }
        if (solraccount.length() > 0) {
            org.apache.http.impl.conn.PoolingClientConnectionManager cm = new org.apache.http.impl.conn.PoolingClientConnectionManager(); // try also: ThreadSafeClientConnManager
            cm.setMaxTotal(100);
            cm.setDefaultMaxPerRoute(100);
            
            this.client = new org.apache.http.impl.client.DefaultHttpClient(cm) {
                @Override
                protected HttpContext createHttpContext() {
                    HttpContext context = super.createHttpContext();
                    AuthCache authCache = new org.apache.http.impl.client.BasicAuthCache();
                    BasicScheme basicAuth = new BasicScheme();
                    HttpHost targetHost = new HttpHost(u.getHost(), u.getPort(), u.getProtocol());
                    authCache.put(targetHost, basicAuth);
                    context.setAttribute(org.apache.http.client.protocol.ClientContext.AUTH_CACHE, authCache);
                    this.setHttpRequestRetryHandler(new org.apache.http.impl.client.DefaultHttpRequestRetryHandler(0, false)); // no retries needed; we expect connections to fail; therefore we should not retry
                    return context;
                }
            };
            org.apache.http.params.HttpParams params = ((org.apache.http.impl.client.DefaultHttpClient) this.client).getParams();
            org.apache.http.params.HttpConnectionParams.setConnectionTimeout(params, timeout);
            org.apache.http.params.HttpConnectionParams.setSoTimeout(params, timeout);
            ((org.apache.http.impl.client.DefaultHttpClient) this.client).addRequestInterceptor(new HttpRequestInterceptor() {
                @Override
                public void process(final HttpRequest request, final HttpContext context) throws IOException {
                    if (!request.containsHeader("Accept-Encoding")) request.addHeader("Accept-Encoding", "gzip");
                    if (!request.containsHeader("Connection")) request.addHeader("Connection", "close"); // prevent CLOSE_WAIT
                }

            });
            ((org.apache.http.impl.client.DefaultHttpClient) this.client).addResponseInterceptor(new HttpResponseInterceptor() {
                @Override
                public void process(final HttpResponse response, final HttpContext context) throws IOException {
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
            org.apache.http.impl.client.BasicCredentialsProvider credsProvider = new org.apache.http.impl.client.BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(host, AuthScope.ANY_PORT), new UsernamePasswordCredentials(solraccount, solrpw));
            ((org.apache.http.impl.client.DefaultHttpClient) this.client).setCredentialsProvider(credsProvider);
        } else {
            this.client = null;
        }
        
        this.defaultServer = (ConcurrentUpdateSolrServer) getServer(this.defaultCoreName);
        if (this.defaultServer == null) throw new IOException("cannot connect to url " + url + " and connect core " + defaultCoreName);
    }

    @Override
    public int hashCode() {
        return this.solrurl.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof RemoteInstance && ((RemoteInstance) o).solrurl.equals(this.solrurl);
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

    @Override
    public String getDefaultCoreName() {
        return this.defaultCoreName;
    }

    @Override
    public Collection<String> getCoreNames() {
        return this.coreNames;
    }

    @Override
    public SolrServer getDefaultServer() {
        return this.defaultServer;
    }

    @Override
    public SolrServer getServer(String name) {
        // try to get the server from the cache
        ConcurrentUpdateSolrServer s = this.server.get(name);
        if (s != null) return s;
        // create new http server
        if (this.client != null) {
            final MultiProtocolURL u;
            try {
                u = new MultiProtocolURL(this.solrurl + name);
            } catch (final MalformedURLException e) {
                return null;
            }
            String host = u.getHost();
            int port = u.getPort();
            String solrpath = u.getPath();
            String p = "http://" + host + ":" + port + solrpath;
            ConcurrentLog.info("RemoteSolrConnector", "connecting Solr authenticated with url:" + p);
            s = new ConcurrentUpdateSolrServer(p, ((org.apache.http.impl.client.DefaultHttpClient) this.client), 10, Runtime.getRuntime().availableProcessors());
        } else {
            ConcurrentLog.info("RemoteSolrConnector", "connecting Solr with url:" + this.solrurl + name);
            s = new ConcurrentUpdateSolrServer(this.solrurl + name, queueSizeByMemory(), Runtime.getRuntime().availableProcessors());
        }
        //s.setAllowCompression(true);
        s.setSoTimeout(this.timeout);
        //s.setMaxRetries(1); // Solr-Doc: No more than 1 recommended (depreciated)
        s.setSoTimeout(this.timeout);
        this.server.put(name, s);
        return s;
    }

    @Override
    public void close() {
    	if (this.client != null) ((org.apache.http.impl.client.DefaultHttpClient) this.client).getConnectionManager().shutdown();
    }

    public static int queueSizeByMemory() {
        return (int) Math.min(500, Math.max(1, MemoryControl.maxMemory() / 1024 / 1024 / 12));
    }
}
