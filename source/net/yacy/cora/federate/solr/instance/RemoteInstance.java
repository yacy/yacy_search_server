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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang.StringUtils;
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
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

@SuppressWarnings("deprecation")
public class RemoteInstance implements SolrInstance {
    
    private String solrurl;
    private final HttpClient client;
    private final String defaultCoreName;
    private final ConcurrentUpdateSolrClient defaultServer;
    private final Collection<String> coreNames;
    private final Map<String, ConcurrentUpdateSolrClient> server;
    private final int timeout;
    
	/**
	 * @param urlList
	 *            the list of URLs of remote Solr shard instances. Must not be null.
	 * @param coreNames
	 *            the Solr core names for the main collection and the webgraph
	 * @param defaultCoreName
	 *            the core name of the main collection
	 * @param timeout
	 *            the connection timeout in milliseconds
	 * @param trustSelfSignedOnAuthenticatedServer
	 *            when true, self-signed certificates are accepcted for an https
	 *            connection to a remote server with authentication credentials
	 * @throws IOException
	 *             when a connection could not be opened to a remote Solr instance
	 */
	public static ArrayList<RemoteInstance> getShardInstances(final String urlList, Collection<String> coreNames,
			String defaultCoreName, final int timeout, final boolean trustSelfSignedOnAuthenticatedServer)
			throws IOException {
        urlList.replace(' ', ',');
        String[] urls = CommonPattern.COMMA.split(urlList);
        ArrayList<RemoteInstance> instances = new ArrayList<RemoteInstance>();
        for (final String u: urls) {
            RemoteInstance instance = new RemoteInstance(u, coreNames, defaultCoreName, timeout, trustSelfSignedOnAuthenticatedServer);
            instances.add(instance);
        }
        return instances;
    }
    
	/**
	 * @param url
	 *            the remote Solr URL. A default localhost URL is assumed when null.
	 * @param coreNames
	 *            the Solr core names for the main collection and the webgraph
	 * @param defaultCoreName
	 *            the core name of the main collection
	 * @param timeout
	 *            the connection timeout in milliseconds
	 * @param trustSelfSignedOnAuthenticatedServer
	 *            when true, self-signed certificates are accepcted for an https
	 *            connection to a remote server with authentication credentials
	 * @throws IOException
	 *             when a connection could not be opened to the remote Solr instance
	 */
	public RemoteInstance(final String url, final Collection<String> coreNames, final String defaultCoreName,
			final int timeout, final boolean trustSelfSignedOnAuthenticatedServer) throws IOException {
        this.timeout = timeout;
        this.server= new HashMap<String, ConcurrentUpdateSolrClient>();
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
            this.client = buildCustomHttpClient(timeout, u, solraccount, solrpw, host, trustSelfSignedOnAuthenticatedServer);
        } else if(u.isHTTPS()){
        	/* Here we must trust self-signed certificates as most peers with SSL enabled use such certificates */
        	this.client = buildCustomHttpClient(timeout, u, solraccount, solrpw, host, true);
        } else {
        	// The default HttpSolrClient will be used
        	this.client = null;
        }
        
        this.defaultServer = (ConcurrentUpdateSolrClient) getServer(this.defaultCoreName);
        if (this.defaultServer == null) throw new IOException("cannot connect to url " + url + " and connect core " + defaultCoreName);
    }

    /**
     * @param solraccount eventual user name used to authenticate on the target Solr
     * @param solraccount eventual password used to authenticate on the target Solr
     * @param trustSelfSignedCertificates when true, https connections to an host rpviding a self-signed certificate are accepted
     * @return a new apache HttpClient instance usable as a custom http client by SolrJ
     */
	private static HttpClient buildCustomHttpClient(final int timeout, final MultiProtocolURL u, final String solraccount, final String solrpw,
			final String host, final boolean trustSelfSignedCertificates) {
		
		/* Important note : deprecated use of Apache classes is required because SolrJ still use them internally (see HttpClientUtil). 
		 * Upgrade only when Solr implementation will become compatible */
		
		org.apache.http.impl.conn.PoolingClientConnectionManager cm;
		SchemeRegistry registry = null;
		if(trustSelfSignedCertificates) {
            SSLContext sslContext;
			try {
				sslContext = SSLContextBuilder.create().loadTrustMaterial(TrustSelfSignedStrategy.INSTANCE).build();
	            registry = new SchemeRegistry();
	            registry.register(
	                    new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
	            registry.register(
	                    new Scheme("https", 443, new SSLSocketFactory(sslContext, AllowAllHostnameVerifier.INSTANCE)));
			} catch (final Exception e) {
				// Should not happen
				ConcurrentLog.warn("RemoteInstance", "Error when initializing SSL context trusting self-signed certificates.", e);
				registry = null;
			}
		}
		if(registry != null) {
			cm = new org.apache.http.impl.conn.PoolingClientConnectionManager(registry);
		} else {
			cm = new org.apache.http.impl.conn.PoolingClientConnectionManager(); // try also: ThreadSafeClientConnManager
		}
		cm.setMaxTotal(100);
		cm.setDefaultMaxPerRoute(100);
		
		org.apache.http.impl.client.DefaultHttpClient result = new org.apache.http.impl.client.DefaultHttpClient(cm) {
		    @Override
		    protected HttpContext createHttpContext() {
		        HttpContext context = super.createHttpContext();
		        AuthCache authCache = new org.apache.http.impl.client.BasicAuthCache();
		        BasicScheme basicAuth = new BasicScheme();
		        HttpHost targetHost = new HttpHost(u.getHost(), u.getPort(), u.getProtocol());
		        authCache.put(targetHost, basicAuth);
		        context.setAttribute(org.apache.http.client.protocol.HttpClientContext.AUTH_CACHE, authCache);
		        this.setHttpRequestRetryHandler(new org.apache.http.impl.client.DefaultHttpRequestRetryHandler(0, false)); // no retries needed; we expect connections to fail; therefore we should not retry
		        return context;
		    }
		};
		org.apache.http.params.HttpParams params = result.getParams();
		/* Set the maximum time to establish a connection to the remote server */
		org.apache.http.params.HttpConnectionParams.setConnectionTimeout(params, timeout);
		/* Set the maximum time between data packets reception one a connection has been established */
		org.apache.http.params.HttpConnectionParams.setSoTimeout(params, timeout);
		result.addRequestInterceptor(new HttpRequestInterceptor() {
		    @Override
		    public void process(final HttpRequest request, final HttpContext context) throws IOException {
		        if (!request.containsHeader(HeaderFramework.ACCEPT_ENCODING)) request.addHeader(HeaderFramework.ACCEPT_ENCODING, HeaderFramework.CONTENT_ENCODING_GZIP);
		        if (!request.containsHeader(HTTP.CONN_DIRECTIVE)) request.addHeader(HTTP.CONN_DIRECTIVE, "close"); // prevent CLOSE_WAIT
		    }

		});
		result.addResponseInterceptor(new HttpResponseInterceptor() {
		    @Override
		    public void process(final HttpResponse response, final HttpContext context) throws IOException {
		        HttpEntity entity = response.getEntity();
		        if (entity != null) {
		            Header ceheader = entity.getContentEncoding();
		            if (ceheader != null) {
		                HeaderElement[] codecs = ceheader.getElements();
		                for (HeaderElement codec : codecs) {
		                    if (codec.getName().equalsIgnoreCase(HeaderFramework.CONTENT_ENCODING_GZIP)) {
		                        response.setEntity(new GzipDecompressingEntity(response.getEntity()));
		                        return;
		                    }
		                }
		            }
		        }
		    }
		});
		if(solraccount != null && !solraccount.isEmpty()) {
			org.apache.http.impl.client.BasicCredentialsProvider credsProvider = new org.apache.http.impl.client.BasicCredentialsProvider();
			credsProvider.setCredentials(new AuthScope(host, AuthScope.ANY_PORT), new UsernamePasswordCredentials(solraccount, solrpw));
			result.setCredentialsProvider(credsProvider);
		}
		
		return result;
	}

    @Override
    public int hashCode() {
        return this.solrurl.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof RemoteInstance && ((RemoteInstance) o).solrurl.equals(this.solrurl);
    }

	/**
	 * @param toExternalAddress
	 *            when true, try to replace the eventual loopback host part of the
	 *            Solr URL with the external host name of the hosting machine
	 * @param externalHost
	 *            the eventual external host name or address to use when
	 *            toExternalAddress is true
	 * @return the administration URL of the remote Solr instance
	 */
	public String getAdminInterface(final boolean toExternalAddress, final String externalHost) {
		String u = this.solrurl;
		if (toExternalAddress && externalHost != null && !externalHost.trim().isEmpty()) {
			try {
				MultiProtocolURL url = new MultiProtocolURL(u);

				if(url.isLocal()) {
					url = url.ofNewHost(externalHost);
					u = url.toString();
				}

			} catch (final MalformedURLException ignored) {
				/*
				 * This should not happen as the solrurl attribute has already been parsed in
				 * the constructor
				 */
			}
		}
		return u;
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
    public SolrClient getDefaultServer() {
        return this.defaultServer;
    }

    @Override
    public SolrClient getServer(String name) {
        // try to get the server from the cache
        ConcurrentUpdateSolrClient s = this.server.get(name);
        if (s != null) return s;
        // create new http server
        final MultiProtocolURL u;
        try {
            u = new MultiProtocolURL(this.solrurl + name);
        } catch (final MalformedURLException e) {
            return null;
        }
        if (this.client != null) {
        	final String solrServerURL;
        	if(StringUtils.isNotEmpty(u.getUserInfo())) {
        		/* Remove user authentication info from the URL, as authentication will be handled by the custom http client */
                String host = u.getHost();
                int port = u.getPort();
                String solrpath = u.getPath();
                solrServerURL = u.getProtocol() + "://" + host + ":" + port + solrpath;
                ConcurrentLog.info("RemoteSolrConnector", "connecting Solr authenticated with url : " + u);        		
        	} else {
        		solrServerURL = u.toString();
        		ConcurrentLog.info("RemoteSolrConnector", "connecting Solr with url : " + u);
        	}
        	ConcurrentUpdateSolrClient.Builder builder = new ConcurrentUpdateSolrClient.Builder(solrServerURL);
        	builder.withHttpClient(this.client);
            builder.withQueueSize(10);
            builder.withThreadCount(Runtime.getRuntime().availableProcessors());
            s = builder.build();
        } else {
            ConcurrentLog.info("RemoteSolrConnector", "connecting Solr with url : " + this.solrurl + name);
            ConcurrentUpdateSolrClient.Builder builder = new ConcurrentUpdateSolrClient.Builder(u.toString());
            builder.withQueueSize(queueSizeByMemory());
            builder.withThreadCount(Runtime.getRuntime().availableProcessors());
            s = builder.build();
        }
        //s.setAllowCompression(true);
        /* Set the maximum time to establish a connection to the remote server */
        s.setConnectionTimeout(this.timeout);
        /* Set the maximum time between data packets reception one a connection has been established */
        s.setSoTimeout(this.timeout);
        //s.setMaxRetries(1); // Solr-Doc: No more than 1 recommended (depreciated)
        this.server.put(name, s);
        return s;
    }

    @Override
    public void close() {
    	if (this.client != null) ((org.apache.http.impl.client.DefaultHttpClient) this.client).getConnectionManager().shutdown();
    }

    public static int queueSizeByMemory() {
        return (int) Math.min(30, Math.max(1, MemoryControl.maxMemory() / 1024 / 1024 / 12));
    }
}
