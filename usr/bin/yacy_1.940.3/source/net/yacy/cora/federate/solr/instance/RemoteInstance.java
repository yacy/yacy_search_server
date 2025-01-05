/**
 *  RemoteInstance
 *  Copyright 2013 by Michael Peter Christen
 *  First released 13.02.2013 at https://yacy.net
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

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
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.params.ModifiableSolrParams;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.http.StrictSizeLimitResponseInterceptor;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

/**
 * Handle access to a remote Solr instance.
 */
@SuppressWarnings("deprecation")
public class RemoteInstance implements SolrInstance {
	
	/** Default maximum time in seconds to keep alive an idle connection in the pool */
	private static final int DEFAULT_POOLED_CONNECTION_TIME_TO_LIVE = 30;
	
	/** Default total maximum number of connections in the pool */
	private static final int DEFAULT_POOL_MAX_TOTAL = 100;
	
	/** The connection manager holding the HTTP connections pool shared between remote Solr clients. */
	public static final org.apache.http.impl.conn.PoolingClientConnectionManager CONNECTION_MANAGER = buildConnectionManager();
	
	/** Default setting to apply when the JVM system option jsse.enableSNIExtension is not defined */
	public static final boolean ENABLE_SNI_EXTENSION_DEFAULT = true;
	
	/** When true, Server Name Indication (SNI) extension is enabled on outgoing TLS connections.
	 * @see <a href="https://tools.ietf.org/html/rfc6066#section-3">RFC 6066 definition</a> 
	 * @see <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=7127374">JDK 1.7 bug</a> on "unrecognized_name" warning for SNI */
	public static final AtomicBoolean ENABLE_SNI_EXTENSION = new AtomicBoolean(
			Boolean.parseBoolean(System.getProperty("jsse.enableSNIExtension", Boolean.toString(ENABLE_SNI_EXTENSION_DEFAULT))));
	
	/** A custom scheme registry allowing https connections to servers using self-signed certificate */
	private static final org.apache.http.conn.scheme.SchemeRegistry SCHEME_REGISTRY = buildTrustSelfSignedSchemeRegistry();
	
	/** Solr server URL */
    private String solrurl;
    
    /** HTTP client used to request the Solr server */
    private final HttpClient client;
    
    /** Default Solr core name */
    private final String defaultCoreName;
    
    /** Solr client for the default core */
    private final SolrClient defaultServer;
    
    /** Solr core names for the main collection and the webgraph */
    private final Collection<String> coreNames;
    
    /** Map from Solr core names to SolrClient instances */
    private final Map<String, SolrClient> server;
    
    /** Connection timeout in milliseconds */
    private final int timeout;
    
	/**
	 * When true, the instance will be used for update operations. The Solr client
	 * is adjusted for better performance of multiple updates.
	 */
	private final boolean concurrentUpdates;
    
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
	 * Build a new instance optimized for concurrent updates, with no limit on responses size.
	 *  
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
		this(url, coreNames, defaultCoreName, timeout, trustSelfSignedOnAuthenticatedServer, Long.MAX_VALUE, true);
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
	 * @param maxBytesPerReponse
	 *            maximum acceptable decompressed size in bytes for a response from
	 *            the remote Solr server. Negative value or Long.MAX_VALUE means no
	 *            limit.
	 * @param concurrentUpdates
	 *            when true, the instance will be used for update operations. The
	 *            Solr client is adjusted for better performance of multiple
	 *            updates.
	 * @throws IOException
	 *             when a connection could not be opened to the remote Solr instance
	 */
	public RemoteInstance(final String url, final Collection<String> coreNames, final String defaultCoreName,
			final int timeout, final boolean trustSelfSignedOnAuthenticatedServer, final long maxBytesPerResponse, final boolean concurrentUpdates) throws IOException {
        this.timeout = timeout;
        this.concurrentUpdates = concurrentUpdates;
        this.server= new HashMap<String, SolrClient>();
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
            this.client = buildCustomHttpClient(timeout, u, solraccount, solrpw, host, trustSelfSignedOnAuthenticatedServer, maxBytesPerResponse);
        } else if(u.isHTTPS()){
        	/* Here we must trust self-signed certificates as most peers with SSL enabled use such certificates */
        	this.client = buildCustomHttpClient(timeout, u, solraccount, solrpw, host, true, maxBytesPerResponse);
        } else {
        	/* Build a http client using the Solr utils as in the HttpSolrClient constructor implementation. 
        	 * The main difference is that a shared connection manager is used (configured in the buildConnectionManager() function) */
            final ModifiableSolrParams params = new ModifiableSolrParams();
            params.set(HttpClientUtil.PROP_FOLLOW_REDIRECTS, false);
            /* Accept gzip compression of responses to reduce network usage */
            params.set(HttpClientUtil.PROP_ALLOW_COMPRESSION, true);
            
            /* Set the maximum time to establish a connection to the remote server */
            params.set(HttpClientUtil.PROP_CONNECTION_TIMEOUT, this.timeout);
            /* Set the maximum time between data packets reception once a connection has been established */
            params.set(HttpClientUtil.PROP_SO_TIMEOUT, this.timeout);
            
            
            this.client = HttpClientUtil.createClient(params);
            if(this.client instanceof org.apache.http.impl.client.DefaultHttpClient) {
            	if(this.client.getParams() != null) {
            		/* Set the maximum time to get a connection from the shared connections pool */
            	    org.apache.http.client.params.HttpClientParams.setConnectionManagerTimeout(this.client.getParams(), timeout);
            	}
            	
        		if (maxBytesPerResponse >= 0 && maxBytesPerResponse < Long.MAX_VALUE) {
        			/*
        			 * Add in last position the eventual interceptor limiting the response size, so
        			 * that this is the decompressed amount of bytes that is considered
        			 */
        			((org.apache.http.impl.client.DefaultHttpClient)this.client).addResponseInterceptor(new StrictSizeLimitResponseInterceptor(maxBytesPerResponse),
        					((org.apache.http.impl.client.DefaultHttpClient)this.client).getResponseInterceptorCount());
        		}
            }
        }
        
        this.defaultServer = getServer(this.defaultCoreName);
        if (this.defaultServer == null) throw new IOException("cannot connect to url " + url + " and connect core " + defaultCoreName);
    }
	
	/**
	 * Initialize the maximum connections for the given pool
	 * 
	 * @param pool
	 *            a pooling connection manager. Must not be null.
	 * @param maxConnections.
	 *            The new maximum connections values. Must be greater than 0.
	 * @throws IllegalArgumentException
	 *             when pool is null or when maxConnections is lower than 1
	 */
	public static void initPoolMaxConnections(final org.apache.http.impl.conn.PoolingClientConnectionManager pool, int maxConnections) {
		if (pool == null) {
			throw new IllegalArgumentException("pool parameter must not be null");
		}
		if (maxConnections <= 0) {
			throw new IllegalArgumentException("maxConnections parameter must be greater than zero");
		}
		pool.setMaxTotal(maxConnections);
		
        /* max connections per host */
        pool.setDefaultMaxPerRoute((int) (2 * Memory.cores()));
	}
	
	/**
	 * @return a connection manager with a HTTP connection pool
	 */
	private static org.apache.http.impl.conn.PoolingClientConnectionManager buildConnectionManager() {
		/* Important note : use of deprecated Apache classes is required because SolrJ still use them internally (see HttpClientUtil). 
		 * Upgrade only when Solr implementation will become compatible */
		
		final org.apache.http.impl.conn.PoolingClientConnectionManager cm = new org.apache.http.impl.conn.PoolingClientConnectionManager(
		        org.apache.http.impl.conn.SchemeRegistryFactory.createDefault(), DEFAULT_POOLED_CONNECTION_TIME_TO_LIVE, TimeUnit.SECONDS);
		initPoolMaxConnections(cm, DEFAULT_POOL_MAX_TOTAL);
		return cm;
	}
	
	/**
	 * @return a custom scheme registry allowing https connections to servers using
	 *         a self-signed certificate
	 */
	private static org.apache.http.conn.scheme.SchemeRegistry buildTrustSelfSignedSchemeRegistry() {
		/* Important note : use of deprecated Apache classes is required because SolrJ still use them internally (see HttpClientUtil). 
		 * Upgrade only when Solr implementation will become compatible */
	    org.apache.http.conn.scheme.SchemeRegistry registry = null;
		SSLContext sslContext;
		try {
			sslContext = SSLContextBuilder.create().loadTrustMaterial(TrustSelfSignedStrategy.INSTANCE).build();
			registry = new org.apache.http.conn.scheme.SchemeRegistry();
			registry.register(new org.apache.http.conn.scheme.Scheme("http", 80, org.apache.http.conn.scheme.PlainSocketFactory.getSocketFactory()));
			registry.register(
					new org.apache.http.conn.scheme.Scheme("https", 443, new org.apache.http.conn.ssl.SSLSocketFactory(sslContext, org.apache.http.conn.ssl.AllowAllHostnameVerifier.INSTANCE) {
						@Override
						protected void prepareSocket(SSLSocket socket) throws IOException {
			        		if(!ENABLE_SNI_EXTENSION.get()) {
			        			/* Set the SSLParameters server names to empty so we don't use SNI extension.
			        			 * See https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#ClientSNIExamples */
			        			final SSLParameters sslParams = socket.getSSLParameters();
			        			sslParams.setServerNames(Collections.emptyList());
			        			socket.setSSLParameters(sslParams);
			        		}
						}
					}));
		} catch (final Exception e) {
			// Should not happen
			ConcurrentLog.warn("RemoteInstance",
					"Error when initializing SSL context trusting self-signed certificates.", e);
			registry = null;
		}
		return registry;
	}

    /**
     * @param solraccount eventual user name used to authenticate on the target Solr
     * @param solraccount eventual password used to authenticate on the target Solr
     * @param trustSelfSignedCertificates when true, https connections to an host providing a self-signed certificate are accepted
	 * @param maxBytesPerReponse
	 *            maximum acceptable decompressed size in bytes for a response from
	 *            the remote Solr server. Negative value or Long.MAX_VALUE means no
	 *            limit.
     * @return a new apache HttpClient instance usable as a custom http client by SolrJ
     */
	private static HttpClient buildCustomHttpClient(final int timeout, final MultiProtocolURL u, final String solraccount, final String solrpw,
			final String host, final boolean trustSelfSignedCertificates, final long maxBytesPerResponse) {
		
		/* Important note : use of deprecated Apache classes is required because SolrJ still use them internally (see HttpClientUtil). 
		 * Upgrade only when Solr implementation will become compatible */
		
		
		org.apache.http.impl.client.DefaultHttpClient result = new org.apache.http.impl.client.DefaultHttpClient(CONNECTION_MANAGER) {
		    @Override
		    protected HttpContext createHttpContext() {
		        HttpContext context = super.createHttpContext();
		        AuthCache authCache = new org.apache.http.impl.client.BasicAuthCache();
		        BasicScheme basicAuth = new BasicScheme();
		        HttpHost targetHost = new HttpHost(u.getHost(), u.getPort(), u.getProtocol());
		        authCache.put(targetHost, basicAuth);
		        context.setAttribute(org.apache.http.client.protocol.HttpClientContext.AUTH_CACHE, authCache);
				if (trustSelfSignedCertificates && SCHEME_REGISTRY != null) {
					context.setAttribute(org.apache.http.client.protocol.ClientContext.SCHEME_REGISTRY, SCHEME_REGISTRY);
				}
		        this.setHttpRequestRetryHandler(new org.apache.http.impl.client.DefaultHttpRequestRetryHandler(0, false)); // no retries needed; we expect connections to fail; therefore we should not retry
		        return context;
		    }
		};
		org.apache.http.params.HttpParams params = result.getParams();
		/* Set the maximum time to establish a connection to the remote server */
		org.apache.http.params.HttpConnectionParams.setConnectionTimeout(params, timeout);
		/* Set the maximum time between data packets reception one a connection has been established */
		org.apache.http.params.HttpConnectionParams.setSoTimeout(params, timeout);
		/* Set the maximum time to get a connection from the shared connections pool */
		org.apache.http.client.params.HttpClientParams.setConnectionManagerTimeout(params, timeout);
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
		
		if (maxBytesPerResponse >= 0 && maxBytesPerResponse < Long.MAX_VALUE) {
			/*
			 * Add in last position the eventual interceptor limiting the response size, so
			 * that this is the decompressed amount of bytes that is considered
			 */
			result.addResponseInterceptor(new StrictSizeLimitResponseInterceptor(maxBytesPerResponse),
					result.getResponseInterceptorCount());
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

    /**
     * @param name the name of the Solr core
     */
    @Override
    public SolrClient getServer(final String name) {
        // try to get the server from the cache
    	SolrClient s = this.server.get(name);
        if (s != null) return s;
        // create new http server
        final MultiProtocolURL u;
        try {
            u = new MultiProtocolURL(this.solrurl + name);
        } catch (final MalformedURLException e) {
            return null;
        }
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
        if(this.concurrentUpdates) {
        	final ConcurrentUpdateSolrClient.Builder builder = new ConcurrentUpdateSolrClient.Builder(solrServerURL);
        	builder.withHttpClient(this.client);
        	builder.withQueueSize(queueSizeByMemory());
        	builder.withThreadCount(Runtime.getRuntime().availableProcessors());
        	s = builder.build();
        } else {
        	final HttpSolrClient.Builder builder = new HttpSolrClient.Builder(solrServerURL);
        	builder.withHttpClient(this.client);
        	s = builder.build();
        }
        this.server.put(name, s);
        return s;
    }

	/**
	 * Closes each eventually open Solr client and its associated resources. The
	 * common connections manager is not closed here as it will be reused for other
	 * RemoteInstances. The shutdown the connection manager at YaCy shutdown, use
	 * the {@link #closeConnectionManager()} function.
	 */
	@Override
	public void close() {
		for (final SolrClient solrClient : this.server.values()) {
			/*
			 * Close every open Solr client : this is important as it shutdowns client's
			 * internal asynchronous tasks executor. To release the common connection
			 * manager, see closeConnectionManager().
			 */
			try {
				solrClient.close();
			} catch (final IOException ignored) {
			}
		}
	}
    
	/**
	 * Shutdown the connection manager and close all its active and inactive HTTP
	 * connections. Must be called at the end of the application.
	 */
	public static void closeConnectionManager() {
		try {
		} finally {
			if (CONNECTION_MANAGER != null) {
				CONNECTION_MANAGER.shutdown();
			}
		}
	}

    public static int queueSizeByMemory() {
        return (int) Math.min(30, Math.max(1, MemoryControl.maxMemory() / 1024 / 1024 / 12));
    }
}
