/**
 *  HTTPClient
 *  Copyright 2010 by Sebastian Gaebel
 *  First released 01.07.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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


package net.yacy.cora.protocol.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.http.ProxySettings.Protocol;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EntityUtils;


/**
 * HttpClient implementation which uses HttpComponents Client {@link http://hc.apache.org/}
 *
 * @author sixcooler
 *
 */
public class HTTPClient {

	private final static int maxcon = 200;
	private static IdledConnectionEvictor idledConnectionEvictor = null;
	private static HttpClient httpClient = initConnectionManager();
	private static final CredentialsProvider credsProvider = new BasicCredentialsProvider();
	private Set<Entry<String, String>> headers = null;
	private HttpResponse httpResponse = null;
	private HttpUriRequest currentRequest = null;
	private long upbytes = 0L;
	private int timeout = 10000;
	private ClientIdentification.Agent agent = null;
	private String host = null;
	private boolean redirecting = true;
	private String realm = null;


    public HTTPClient(final ClientIdentification.Agent agent) {
        super();
        this.agent = agent;
        this.timeout = agent.clientTimeout;
        HttpProtocolParams.setUserAgent(httpClient.getParams(), agent.userAgent);
    }
    
    public HTTPClient(final ClientIdentification.Agent agent, final int timeout) {
        super();
        this.agent = agent;
        this.timeout = timeout;
        HttpProtocolParams.setUserAgent(httpClient.getParams(), agent.userAgent);
    }

    public static void setDefaultUserAgent(final String defaultAgent) {
    	HttpProtocolParams.setUserAgent(httpClient.getParams(), defaultAgent);
    }

    public static HttpClient initConnectionManager() {
    	// Create and initialize scheme registry
		final SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
		schemeRegistry.register(new Scheme("https", 443, getSSLSocketFactory()));

		final PoolingClientConnectionManager clientConnectionManager = new PoolingClientConnectionManager(schemeRegistry);

		// Create and initialize HTTP parameters
		final HttpParams httpParams = new BasicHttpParams();
		/**
		 * ConnectionManager settings
		 */
		// how much connections do we need? - default: 20
		clientConnectionManager.setMaxTotal(maxcon);
		// for statistics same value should also be set here
		ConnectionInfo.setMaxcount(maxcon);
		// connections per host (2 default)
		clientConnectionManager.setDefaultMaxPerRoute(2);
		// Increase max connections for localhost
		final HttpHost localhost = new HttpHost(Domains.LOCALHOST);
		clientConnectionManager.setMaxPerRoute(new HttpRoute(localhost), maxcon);
		/**
		 * HTTP protocol settings
		 */
		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
		// UserAgent
		HttpProtocolParams.setUserAgent(httpParams, ClientIdentification.yacyInternetCrawlerAgent.userAgent);
		HttpProtocolParams.setUseExpectContinue(httpParams, false); // IMPORTANT - if not set to 'false' then servers do not process the request until a time-out of 2 seconds
		/**
		 * HTTP connection settings
		 */
		// timeout in milliseconds until a connection is established in milliseconds
		HttpConnectionParams.setConnectionTimeout(httpParams, 6000);
		// SO_LINGER affects the socket close operation in seconds
		// HttpConnectionParams.setLinger(httpParams, 6);
		// HttpConnectionParams.setSocketBufferSize(httpParams, 8192);
		// SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
		HttpConnectionParams.setSoTimeout(httpParams, 1000);
		// getting an I/O error when executing a request over a connection that has been closed at the server side
		HttpConnectionParams.setStaleCheckingEnabled(httpParams, true);
		// conserve bandwidth by minimizing the number of segments that are sent
		HttpConnectionParams.setTcpNoDelay(httpParams, false);
		// Defines whether the socket can be bound even though a previous connection is still in a timeout state.
		HttpConnectionParams.setSoReuseaddr(httpParams, true);

		/**
		 * HTTP client settings
		 */
		// ignore cookies, cause this may cause segfaults in default cookiestore and is not needed
		HttpClientParams.setCookiePolicy(httpParams, CookiePolicy.IGNORE_COOKIES);

		httpClient = new DefaultHttpClient(clientConnectionManager, httpParams);
		// disable the cookiestore, cause this may cause segfaults and is not needed
		((DefaultHttpClient) httpClient).setCookieStore(null);
		// add cutom keep alive strategy
		addCustomKeepAliveStrategy((DefaultHttpClient) httpClient);
		// ask for gzip
		((DefaultHttpClient) httpClient).addRequestInterceptor(new GzipRequestInterceptor());
		// uncompress gzip
		((DefaultHttpClient) httpClient).addResponseInterceptor(new GzipResponseInterceptor());
		// remove retries; we expect connections to fail; therefore we should not retry
		((DefaultHttpClient) httpClient).setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
		if (idledConnectionEvictor == null) {
		    idledConnectionEvictor = new IdledConnectionEvictor(clientConnectionManager);
		    idledConnectionEvictor.start();
		}
        return httpClient;
    }

    /**
     * This method should be called just before shutdown
     * to stop the ConnectionManager and idledConnectionEvictor
     *
     * @throws InterruptedException
     */
    public static void closeConnectionManager() throws InterruptedException {
    	if (idledConnectionEvictor != null) {
    		// Shut down the evictor thread
        	idledConnectionEvictor.shutdown();
        	idledConnectionEvictor.join();
    	}
		if (httpClient != null) {
			// Shut down the connection manager
			httpClient.getConnectionManager().shutdown();
		}

    }

    public static void setAuth(final String host, final int port, final String user, final String pw) {
        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials(user, pw);
        final AuthScope scope = new AuthScope(host, port);
        credsProvider.setCredentials(scope, creds);
        httpClient.getParams().setParameter(ClientContext.CREDS_PROVIDER, credsProvider);
    }

    /**
     * this method sets a host on which more than the default of 2 router per host are allowed
     *
     * @param the host to be raised in 'route per host'
     */
    public static void setMaxRouteHost(final String host) {
    	final HttpHost mHost = new HttpHost(host);
    	((PoolingClientConnectionManager) httpClient.getConnectionManager()).setMaxPerRoute(new HttpRoute(mHost), 50);
    }

    /**
     * This method sets the Header used for the request
     *
     * @param entrys to be set as request header
     */
    public void setHeader(final Set<Entry<String, String>> entrys) {
    	this.headers = entrys;
    }

    /**
     * This method sets the timeout of the Connection and Socket
     *
     * @param timeout in milliseconds
     */
    public void setTimout(final int timeout) {
    	this.timeout = timeout;
    }

    /**
     * This method sets the UserAgent to be used for the request
     *
     * @param userAgent
     */
    public void setUserAgent(final ClientIdentification.Agent agent) {
    	this.agent = agent;
    }

    /**
     * This method sets the host to be called at the request
     *
     * @param host
     */
    public void setHost(final String host) {
    	this.host = host;
    }

    /**
     * This method sets if requests should follow redirects
     *
     * @param redirecting
     */
    public void setRedirecting(final boolean redirecting) {
    	this.redirecting = redirecting;
    }

    /**
     * This method sets the authorization realm for the request
     *
     * @param realm
     */
    public void setRealm(final String realm) {
        this.realm = realm;
    }

    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final String uri) throws IOException {
        return GETbytes(uri, Integer.MAX_VALUE);
    }

    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final MultiProtocolURI url) throws IOException {
        return GETbytes(url, Integer.MAX_VALUE);
    }

    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @param maxBytes to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final String uri, final int maxBytes) throws IOException {
        return GETbytes(new MultiProtocolURI(uri), maxBytes);
    }


    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @param maxBytes to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final MultiProtocolURI url, final int maxBytes) throws IOException {
        final boolean localhost = Domains.isLocalhost(url.getHost());
        final String urix = url.toNormalform(true);
        HttpGet httpGet = null;
        try {
            httpGet = new HttpGet(urix);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage()); // can be caused  at java.net.URI.create()
        }
        httpGet.addHeader(new BasicHeader("Connection", "close")); // don't keep alive, prevent CLOSE_WAIT state
        if (!localhost) setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        return getContentBytes(httpGet, url.getHost(), maxBytes);
    }

    /**
     * This method GETs a page from the server.
     * to be used for streaming out
     * Please take care to call finish()!
     *
     * @param uri the url to get
     * @throws IOException
     */
    public void GET(final String uri) throws IOException {
        if (this.currentRequest != null) throw new IOException("Client is in use!");
        final MultiProtocolURI url = new MultiProtocolURI(uri);
        final String urix = url.toNormalform(true);
        HttpGet httpGet = null;
        try {
            httpGet = new HttpGet(urix);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage()); // can be caused  at java.net.URI.create()
        }
        httpGet.addHeader(new BasicHeader("Connection", "close")); // don't keep alive, prevent CLOSE_WAIT state
        setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        this.currentRequest = httpGet;
        execute(httpGet, url.getHost());
    }

    /**
     * This method gets HEAD response
     *
     * @param uri the url to Response from
     * @return the HttpResponse
     * @throws IOException
     */
    public HttpResponse HEADResponse(final String uri) throws IOException {
        final MultiProtocolURI url = new MultiProtocolURI(uri);
        final HttpHead httpHead = new HttpHead(url.toNormalform(true));
        httpHead.addHeader(new BasicHeader("Connection", "close")); // don't keep alive, prevent CLOSE_WAIT state
        setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
    	execute(httpHead, url.getHost());
    	finish();
    	ConnectionInfo.removeConnection(httpHead.hashCode());
    	return this.httpResponse;
    }

    /**
     * This method POSTs a page from the server.
     * to be used for streaming out
     * Please take care to call finish()!
     *
     * @param uri the url to post
     * @param instream the input to post
     * @param length the contentlength
     * @throws IOException
     */
    public void POST(final String uri, final InputStream instream, final long length) throws IOException {
    	if (this.currentRequest != null) throw new IOException("Client is in use!");
        final MultiProtocolURI url = new MultiProtocolURI(uri);
        final HttpPost httpPost = new HttpPost(url.toNormalform(true));
        httpPost.addHeader(new BasicHeader("Connection", "close")); // don't keep alive, prevent CLOSE_WAIT state
        String host = url.getHost();
        if (host == null) host = Domains.LOCALHOST;
        setHost(host); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        final NonClosingInputStreamEntity inputStreamEntity = new NonClosingInputStreamEntity(instream, length);
    	// statistics
    	this.upbytes = length;
    	httpPost.setEntity(inputStreamEntity);
    	this.currentRequest = httpPost;
    	execute(httpPost, host);
    }

    /**
     * send data to the server named by uri
     *
     * @param uri the url to post
     * @param parts to post
     * @return content bytes
     * @throws IOException
     */
    public byte[] POSTbytes(final String uri, final Map<String, ContentBody> parts, final boolean usegzip) throws IOException {
        final MultiProtocolURI url = new MultiProtocolURI(uri);
        return POSTbytes(url, url.getHost(), parts, usegzip);
    }

    /**
     * send data to the server named by vhost
     *
     * @param url address of the server
     * @param vhost name of the server at address which should respond
     * @param post data to send (name-value-pairs)
     * @param usegzip if the body should be gzipped
     * @return response body
     * @throws IOException
     */
    public byte[] POSTbytes(final MultiProtocolURI url, final String vhost, final Map<String, ContentBody> post, final boolean usegzip) throws IOException {
    	final HttpPost httpPost = new HttpPost(url.toNormalform(true));
        httpPost.addHeader(new BasicHeader("Connection", "close")); // don't keep alive, prevent CLOSE_WAIT state

        setHost(vhost); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
    	if (vhost == null) setHost(Domains.LOCALHOST);

        final MultipartEntity multipartEntity = new MultipartEntity();
        for (final Entry<String,ContentBody> part : post.entrySet())
            multipartEntity.addPart(part.getKey(), part.getValue());
        // statistics
        this.upbytes = multipartEntity.getContentLength();

        if (usegzip) {
            httpPost.setEntity(new GzipCompressingEntity(multipartEntity));
        } else {
            httpPost.setEntity(multipartEntity);
        }

        return getContentBytes(httpPost, url.getHost(), Integer.MAX_VALUE);
    }

    /**
     * send stream-data to the server named by uri
     *
     * @param uri the url to post
     * @param instream the stream to send
     * @param length the length of the stream
     * @return content bytes
     * @throws IOException
     */
    public byte[] POSTbytes(final String uri, final InputStream instream, final long length) throws IOException {
        final MultiProtocolURI url = new MultiProtocolURI(uri);
        final HttpPost httpPost = new HttpPost(url.toNormalform(true));
        httpPost.addHeader(new BasicHeader("Connection", "close")); // don't keep alive, prevent CLOSE_WAIT state
        String host = url.getHost();
        if (host == null) host = Domains.LOCALHOST;
        setHost(host); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service

        final InputStreamEntity inputStreamEntity = new InputStreamEntity(instream, length);
        // statistics
        this.upbytes = length;
        httpPost.setEntity(inputStreamEntity);
        return getContentBytes(httpPost, host, Integer.MAX_VALUE);
    }

	/**
	 *
	 * @return HttpResponse from call
	 */
    public HttpResponse getHttpResponse() {
		return this.httpResponse;
	}

	/**
	 *
	 * @return status code from http request
	 */
    public int getStatusCode() {
	    return this.httpResponse.getStatusLine().getStatusCode();
	}

    /**
     * This method gets direct access to the content-stream
     * Since this way is uncontrolled by the Client think of using 'writeTo' instead!
     * Please take care to call finish()!
     *
     * @return the content as InputStream
     * @throws IOException
     */
	public InputStream getContentstream() throws IOException {
        if (this.httpResponse != null && this.currentRequest != null) {
            final HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) try {
                    return httpEntity.getContent();
            } catch (final IOException e) {
                ConnectionInfo.removeConnection(this.currentRequest.hashCode());
                this.currentRequest.abort();
                this.currentRequest = null;
                throw e;
            }
        }
        return null;
    }

    /**
     * This method streams the content to the outputStream
     * Please take care to call finish()!
     *
     * @param outputStream
     * @throws IOException
     */
	public void writeTo(final OutputStream outputStream) throws IOException {
        if (this.httpResponse != null && this.currentRequest != null) {
            final HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) try {
                httpEntity.writeTo(outputStream);
                outputStream.flush();
                // Ensures that the entity content is fully consumed and the content stream, if exists, is closed.
                EntityUtils.consume(httpEntity);
                ConnectionInfo.removeConnection(this.currentRequest.hashCode());
                this.currentRequest = null;
            } catch (final IOException e) {
                ConnectionInfo.removeConnection(this.currentRequest.hashCode());
                this.currentRequest.abort();
                this.currentRequest = null;
                throw e;
            }
        }
    }

    /**
     * This method ensures correct finish of client-connections
     * This method should be used after every use of GET or POST and writeTo or getContentstream!
     *
     * @throws IOException
     */
    public void finish() throws IOException {
        if (this.httpResponse != null) {
                final HttpEntity httpEntity = this.httpResponse.getEntity();
        if (httpEntity != null && httpEntity.isStreaming()) {
            // Ensures that the entity content is fully consumed and the content stream, if exists, is closed.
            EntityUtils.consume(httpEntity);
        }
        }
        if (this.currentRequest != null) {
                ConnectionInfo.removeConnection(this.currentRequest.hashCode());
                this.currentRequest.abort();
                this.currentRequest = null;
        }
    }

    private byte[] getContentBytes(final HttpUriRequest httpUriRequest, String host, final int maxBytes) throws IOException {
    	try {
            execute(httpUriRequest, host);
            if (this.httpResponse == null) return null;
            // get the response body
            final HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) {
                if (getStatusCode() == 200 && (maxBytes < 0 || httpEntity.getContentLength() < maxBytes)) {
                    return getByteArray(httpEntity, maxBytes);
                }
                // Ensures that the entity content is fully consumed and the content stream, if exists, is closed.
            	EntityUtils.consume(httpEntity);
            }
            return null;
        } catch (final IOException e) {
                httpUriRequest.abort();
                throw e;
        } finally {
        	ConnectionInfo.removeConnection(httpUriRequest.hashCode());
        }
    }

    private void execute(final HttpUriRequest httpUriRequest, String host) throws IOException {
    	final HttpContext httpContext = new BasicHttpContext();
    	setHeaders(httpUriRequest);
    	setParams(httpUriRequest.getParams());
    	setProxy(httpUriRequest.getParams(), host);
    	// statistics
    	storeConnectionInfo(httpUriRequest);
    	// execute the method; some asserts confirm that that the request can be send with Content-Length and is therefore not terminated by EOF
	    if (httpUriRequest instanceof HttpEntityEnclosingRequest) {
	        final HttpEntityEnclosingRequest hrequest = (HttpEntityEnclosingRequest) httpUriRequest;
	        final HttpEntity entity = hrequest.getEntity();
	        assert entity != null;
	        //assert !entity.isChunked();
	        //assert entity.getContentLength() >= 0;
	        assert !hrequest.expectContinue();
	    }

	    Thread.currentThread().setName("HTTPClient-" + httpUriRequest.getURI().getHost());
	    try {
	        final long time = System.currentTimeMillis();
            this.httpResponse = httpClient.execute(httpUriRequest, httpContext);
            this.httpResponse.setHeader(HeaderFramework.RESPONSE_TIME_MILLIS, Long.toString(System.currentTimeMillis() - time));
        } catch (final IOException e) {
            ConnectionInfo.removeConnection(httpUriRequest.hashCode());
            httpUriRequest.abort();
            throw new IOException("Client can't execute: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        }
    }

    private static byte[] getByteArray(final HttpEntity entity, final int maxBytes) throws IOException {
        final InputStream instream = entity.getContent();
        if (instream == null) {
            return null;
        }
        try {
            int i = maxBytes < 0 ?  (int)entity.getContentLength() : Math.min(maxBytes, (int)entity.getContentLength());
            if (i < 0) {
                i = 4096;
            }
            final ByteArrayBuffer buffer = new ByteArrayBuffer(i);
            byte[] tmp = new byte[4096];
            int l, sum = 0;
            while((l = instream.read(tmp)) != -1) {
            	sum += l;
            	if (maxBytes >= 0 && sum > maxBytes) throw new IOException("Download exceeded maximum value of " + maxBytes + " bytes");
                buffer.append(tmp, 0, l);
            }
            return buffer.toByteArray();
        } catch (final OutOfMemoryError e) {
            throw new IOException(e.toString());
        } finally {
            instream.close();
        }
    }

    private void setHeaders(final HttpUriRequest httpUriRequest) {
    	if (this.headers != null) {
            for (final Entry<String, String> entry : this.headers) {
                    httpUriRequest.setHeader(entry.getKey(),entry.getValue());
            }
    	}
    	if (this.host != null)
    		httpUriRequest.setHeader(HTTP.TARGET_HOST, this.host);
        if (this.realm != null)
            httpUriRequest.setHeader("Authorization", "realm=" + this.realm);
    }

    private void setParams(final HttpParams httpParams) {
    	HttpClientParams.setRedirecting(httpParams, this.redirecting);
    	HttpConnectionParams.setConnectionTimeout(httpParams, this.timeout);
    	HttpConnectionParams.setSoTimeout(httpParams, this.timeout);
    	if (this.agent != null)
    		HttpProtocolParams.setUserAgent(httpParams, this.agent.userAgent);
    	if (this.host != null)
    		httpParams.setParameter(HTTP.TARGET_HOST, this.host);
    }

    private static void setProxy(final HttpParams httpParams, String host) {
    	if (ProxySettings.useForHost(host, Protocol.HTTP))
    		ConnRouteParams.setDefaultProxy(httpParams, ProxySettings.getProxyHost());
    	// TODO find a better way for this
    	ProxySettings.setProxyCreds((DefaultHttpClient) httpClient);
    }

    private void storeConnectionInfo(final HttpUriRequest httpUriRequest) {
    	final int port = httpUriRequest.getURI().getPort();
    	final String thost = httpUriRequest.getURI().getHost();
    	//assert thost != null : "uri = " + httpUriRequest.getURI().toString();
    	ConnectionInfo.addConnection(new ConnectionInfo(
    			httpUriRequest.getURI().getScheme(),
    			port == -1 ? thost : thost + ":" + port,
    			httpUriRequest.getMethod() + " " + httpUriRequest.getURI().getPath(),
    			httpUriRequest.hashCode(),
    			System.currentTimeMillis(),
    			this.upbytes));
    }

    private static SSLSocketFactory getSSLSocketFactory() {
    	final TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                            throws CertificateException {
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                            throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                    return null;
            }
    	};
    	SSLContext sslContext = null;
    	try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManager }, null);
        } catch (final NoSuchAlgorithmException e) {
            // should not happen
            // e.printStackTrace();
        } catch (final KeyManagementException e) {
            // should not happen
            // e.printStackTrace();
        }

        final SSLSocketFactory sslSF = new SSLSocketFactory(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    	return sslSF;
    }

    /**
     * If the Keep-Alive header is not present in the response,
     * HttpClient assumes the connection can be kept alive indefinitely.
     * Here we limit this to 5 seconds.
     *
     * @param defaultHttpClient
     */
    private static void addCustomKeepAliveStrategy(final DefaultHttpClient defaultHttpClient) {
    	defaultHttpClient.setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
			@Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
		        // Honor 'keep-alive' header
				String param, value;
				HeaderElement element;
		        HeaderElementIterator it = new BasicHeaderElementIterator(
		                response.headerIterator(HTTP.CONN_KEEP_ALIVE));
		        while (it.hasNext()) {
		            element = it.nextElement();
		            param = element.getName();
		            value = element.getValue();
		            if (value != null && param.equalsIgnoreCase("timeout")) {
		                try {
		                    return Long.parseLong(value) * 1000;
		                } catch(final NumberFormatException e) {
		                }
		            }
		        }
		        // Keep alive for 5 seconds only
		        return 5 * 1000;
			}
    	});
    }

    /**
     * testing
     *
     * @param args urls to test
     */
    public static void main(final String[] args) {
        String url = null;
        // prepare Parts
        final Map<String,ContentBody> newparts = new LinkedHashMap<String,ContentBody>();
        try {
            newparts.put("foo", new StringBody("FooBar"));
            newparts.put("bar", new StringBody("BarFoo"));
        } catch (final UnsupportedEncodingException e) {
            System.out.println(e.getStackTrace());
        }
        final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        client.setRedirecting(false);
        // Get some
        for (final String arg : args) {
            url = arg;
            if (!url.toUpperCase().startsWith("HTTP://")) {
                    url = "http://" + url;
            }
            try {
                System.out.println(UTF8.String(client.GETbytes(url)));
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        // Head some
//		try {
//			client.HEADResponse(url);
//		} catch (final IOException e) {
//			e.printStackTrace();
//		}
        for (final Header header: client.getHttpResponse().getAllHeaders()) {
            System.out.println("Header " + header.getName() + " : " + header.getValue());
//			for (HeaderElement element: header.getElements())
//				System.out.println("Element " + element.getName() + " : " + element.getValue());
        }
        System.out.println(client.getHttpResponse().getLocale());
        System.out.println(client.getHttpResponse().getProtocolVersion());
        System.out.println(client.getHttpResponse().getStatusLine());
        // Post some
//		try {
//			System.out.println(UTF8.String(client.POSTbytes(url, newparts)));
//		} catch (final IOException e1) {
//			e1.printStackTrace();
//		}
        // Close out connection manager
        try {
                HTTPClient.closeConnectionManager();
        } catch (final InterruptedException e) {
                e.printStackTrace();
        }
    }


	/**
	 *
	 * @see: http://hc.apache.org/httpcomponents-client-4.0.1/tutorial/html/connmgmt.html#d4e638
	 *
	 */
	private static class IdledConnectionEvictor extends Thread {

		private final ClientConnectionManager clientConnectionManager;

		private volatile boolean shutdown;

		public IdledConnectionEvictor(final ClientConnectionManager clientConnectionManager) {
			super();
			this.clientConnectionManager = clientConnectionManager;
		}

		@Override
		public void run() {
			try {
				while (!this.shutdown) {
					synchronized (this) {
						wait(5000);
						// Close expired connections
						this.clientConnectionManager.closeExpiredConnections();
						// Optionally, close connections
						// that have been idle longer than 5 sec
						// (some SOHO router act strange on >5sec idled connections)
						this.clientConnectionManager.closeIdleConnections(5, TimeUnit.SECONDS);
					}
				}
			} catch (final InterruptedException ex) {
				// terminate
			}
		}

		public void shutdown() {
			this.shutdown = true;
			synchronized (this) {
				notifyAll();
			}
		}

	}

}
