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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.Memory;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
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
    
    private final static int default_timeout = 6000;
	private final static int maxcon = 200;
	private static IdleConnectionMonitorThread connectionMonitor = null;
	private final static RequestConfig dfltReqConf = initRequestConfig();
	private final static HttpClientBuilder clientBuilder = initClientBuilder();
	private final RequestConfig.Builder reqConfBuilder;
	private Set<Entry<String, String>> headers = null;
	private CloseableHttpResponse httpResponse = null;
	private HttpUriRequest currentRequest = null;
	private long upbytes = 0L;
	private String host = null;
	private final long timeout;


    public HTTPClient(final ClientIdentification.Agent agent) {
        super();
        this.timeout = agent.clientTimeout;
        clientBuilder.setUserAgent(agent.userAgent);
        reqConfBuilder = RequestConfig.copy(dfltReqConf);
        setTimout(agent.clientTimeout);
    }
    
    public HTTPClient(final ClientIdentification.Agent agent, final int timeout) {
        super();
        this.timeout = timeout;
        clientBuilder.setUserAgent(agent.userAgent);
        reqConfBuilder = RequestConfig.copy(dfltReqConf);
        setTimout(timeout);
    }

    public static void setDefaultUserAgent(final String defaultAgent) {
    	clientBuilder.setUserAgent(defaultAgent);
    }
    
    private static RequestConfig initRequestConfig() {
    	final RequestConfig.Builder builder = RequestConfig.custom();
    	// IMPORTANT - if not set to 'false' then servers do not process the request until a time-out of 2 seconds
    	builder.setExpectContinueEnabled(false);
		// timeout in milliseconds until a connection is established in milliseconds
		builder.setConnectionRequestTimeout(default_timeout);
		builder.setConnectTimeout(default_timeout);
		// SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
		builder.setSocketTimeout(default_timeout);
		// getting an I/O error when executing a request over a connection that has been closed at the server side
		builder.setStaleConnectionCheckEnabled(true);
		// ignore cookies, cause this may cause segfaults in default cookiestore and is not needed
		builder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
		builder.setRedirectsEnabled(true);
		builder.setRelativeRedirectsAllowed(true);
		return builder.build();
    }
    
    private static HttpClientBuilder initClientBuilder() {
    	final HttpClientBuilder builder = HttpClientBuilder.create();
    	
    	builder.setConnectionManager(initPoolingConnectionManager());
		builder.setDefaultRequestConfig(dfltReqConf);
		
    	// UserAgent
		builder.setUserAgent(ClientIdentification.yacyInternetCrawlerAgent.userAgent);
		
		// remove retries; we expect connections to fail; therefore we should not retry
		builder.disableAutomaticRetries();
		// disable the cookiestore, cause this may cause segfaults and is not needed
		builder.setDefaultCookieStore(null);
		builder.disableCookieManagement();
		
		// add custom keep alive strategy
		builder.setKeepAliveStrategy(customKeepAliveStrategy());
		
		// ask for gzip
		builder.addInterceptorLast(new GzipRequestInterceptor());
		// uncompress gzip
		builder.addInterceptorLast(new GzipResponseInterceptor());
		// Proxy
		builder.setRoutePlanner(ProxySettings.RoutePlanner);
    	builder.setDefaultCredentialsProvider(ProxySettings.CredsProvider);
		
    	return builder;
    }
    
    private static PoolingHttpClientConnectionManager initPoolingConnectionManager() {
    	final PlainConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
    	final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
    	        .register("http", plainsf)
    	        .register("https", getSSLSocketFactory())
    	        .build();
    	final PoolingHttpClientConnectionManager pooling = new PoolingHttpClientConnectionManager(registry, new DnsResolver(){
			@Override
			public InetAddress[] resolve(final String host0)throws UnknownHostException {
				final InetAddress ip = Domains.dnsResolve(host0);
				if (ip == null) throw new UnknownHostException(host0);
				return new InetAddress[]{ip};
			}});
    	// how much connections do we need? - default: 20
		pooling.setMaxTotal(maxcon);
		// for statistics same value should also be set here
		ConnectionInfo.setMaxcount(maxcon);
		// connections per host (2 default)
		pooling.setDefaultMaxPerRoute((int) (2 * Memory.cores()));
		// Increase max connections for localhost
		final HttpHost localhost = new HttpHost(Domains.LOCALHOST);
		pooling.setMaxPerRoute(new HttpRoute(localhost), maxcon);
		
		final SocketConfig socketConfig = SocketConfig.custom()
				// Defines whether the socket can be bound even though a previous connection is still in a timeout state.
				.setSoReuseAddress(true)
				// SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
				.setSoTimeout(3000)
				// conserve bandwidth by minimizing the number of segments that are sent
				.setTcpNoDelay(false)
				.build();
		pooling.setDefaultSocketConfig(socketConfig);
		
		if (connectionMonitor == null) {
			connectionMonitor = new IdleConnectionMonitorThread(pooling);
			connectionMonitor.start();
		}
		
		return pooling;
    }

    /**
     * This method should be called just before shutdown
     * to stop the ConnectionManager and idledConnectionEvictor
     *
     * @throws InterruptedException
     */
    public static void closeConnectionManager() throws InterruptedException {
    	if (connectionMonitor != null) {
    		// Shut down the evictor thread
    		connectionMonitor.shutdown();
    		connectionMonitor.join();
    	}
    }

//    public static void setAuth(final String host, final int port, final String user, final String pw) {
//        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials(user, pw);
//        final AuthScope scope = new AuthScope(host, port);
//        credsProvider.setCredentials(scope, creds);
//        httpClient.getParams().setParameter(ClientContext.CREDS_PROVIDER, credsProvider);
//    }

//    /**
//     * this method sets a host on which more than the default of 2 router per host are allowed
//     *
//     * @param the host to be raised in 'route per host'
//     */
//    public static void setMaxRouteHost(final String host) {
//    	final HttpHost mHost = new HttpHost(host);
//    	((PoolingClientConnectionManager) httpClient.getConnectionManager()).setMaxPerRoute(new HttpRoute(mHost), 50);
//    }

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
    	reqConfBuilder.setSocketTimeout(timeout);
    	reqConfBuilder.setConnectTimeout(timeout);
    	reqConfBuilder.setConnectionRequestTimeout(timeout);
    }

    /**
     * This method sets the UserAgent to be used for the request
     *
     * @param userAgent
     */
    public void setUserAgent(final ClientIdentification.Agent agent) {
    	clientBuilder.setUserAgent(agent.userAgent);
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
    	reqConfBuilder.setRedirectsEnabled(redirecting);
    	reqConfBuilder.setRelativeRedirectsAllowed(redirecting);
    }

    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final String uri, final String username, final String pass, final boolean concurrent) throws IOException {
        return GETbytes(uri, username, pass, Integer.MAX_VALUE, concurrent);
    }

    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final MultiProtocolURL url, final String username, final String pass, final boolean concurrent) throws IOException {
        return GETbytes(url, username, pass, Integer.MAX_VALUE, concurrent);
    }

    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @param maxBytes to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final String uri, final String username, final String pass, final int maxBytes, final boolean concurrent) throws IOException {
        return GETbytes(new MultiProtocolURL(uri), username, pass, maxBytes, concurrent);
    }


    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @param maxBytes to get
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final MultiProtocolURL url, final String username, final String pass, final int maxBytes, final boolean concurrent) throws IOException {
        final boolean localhost = Domains.isLocalhost(url.getHost());
        final String urix = url.toNormalform(true);
        HttpGet httpGet = null;
        try {
            httpGet = new HttpGet(urix);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage()); // can be caused  at java.net.URI.create()
        }
        if (!localhost) setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        if (localhost && pass != null) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    AuthScope.ANY, // thats ok since we tested for localhost!
                    new UsernamePasswordCredentials(username, pass));
            CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
            byte[] content = null;
            try {
                this.httpResponse = httpclient.execute(httpGet);
                try {
                    HttpEntity httpEntity = this.httpResponse.getEntity();
                    if (httpEntity != null) {
                        if (getStatusCode() == 200 && (maxBytes < 0 || httpEntity.getContentLength() < maxBytes)) {
                            content = getByteArray(httpEntity, maxBytes);
                        }
                        // Ensures that the entity content is fully consumed and the content stream, if exists, is closed.
                        EntityUtils.consume(httpEntity);
                    }
                } finally {
                    this.httpResponse.close();
                }
            } finally {
                httpclient.close();
            }
            return content;
        }
        return getContentBytes(httpGet, maxBytes, concurrent);
    }
    
    /**
     * This method GETs a page from the server.
     * to be used for streaming out
     * Please take care to call finish()!
     *
     * @param uri the url to get
     * @throws IOException
     */
    public void GET(final String uri, final boolean concurrent) throws IOException {
    	GET(new MultiProtocolURL(uri), concurrent);
    }

    /**
     * This method GETs a page from the server.
     * to be used for streaming out
     * Please take care to call finish()!
     *
     * @param url the url to get
     * @throws IOException
     */
    public void GET(final MultiProtocolURL url, final boolean concurrent) throws IOException {
        if (this.currentRequest != null) throw new IOException("Client is in use!");
        final String urix = url.toNormalform(true);
        HttpGet httpGet = null;
        try {
            httpGet = new HttpGet(urix);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage()); // can be caused  at java.net.URI.create()
        }
        setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        this.currentRequest = httpGet;
        execute(httpGet, concurrent);
    }

    /**
     * This method gets HEAD response
     *
     * @param uri the url to Response from
     * @return the HttpResponse
     * @throws IOException
     */
    public HttpResponse HEADResponse(final String uri, final boolean concurrent) throws IOException {
    	return HEADResponse(new MultiProtocolURL(uri), concurrent);
    }

    /**
     * This method gets HEAD response
     *
     * @param url the url to Response from
     * @return the HttpResponse
     * @throws IOException
     */
    public HttpResponse HEADResponse(final MultiProtocolURL url, final boolean concurrent) throws IOException {
        final HttpHead httpHead = new HttpHead(url.toNormalform(true));
        setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
    	execute(httpHead, concurrent);
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
    public void POST(final String uri, final InputStream instream, final long length, final boolean concurrent) throws IOException {
    	POST(new MultiProtocolURL(uri), instream, length, concurrent);
    }

    /**
     * This method POSTs a page from the server.
     * to be used for streaming out
     * Please take care to call finish()!
     *
     * @param url the url to post
     * @param instream the input to post
     * @param length the contentlength
     * @throws IOException
     */
    public void POST(final MultiProtocolURL url, final InputStream instream, final long length, final boolean concurrent) throws IOException {
    	if (this.currentRequest != null) throw new IOException("Client is in use!");
        final HttpPost httpPost = new HttpPost(url.toNormalform(true));
        String host = url.getHost();
        if (host == null) host = Domains.LOCALHOST;
        setHost(host); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        final NonClosingInputStreamEntity inputStreamEntity = new NonClosingInputStreamEntity(instream, length);
    	// statistics
    	this.upbytes = length;
    	httpPost.setEntity(inputStreamEntity);
    	this.currentRequest = httpPost;
    	execute(httpPost, concurrent);
    }

    /**
     * send data to the server named by uri
     *
     * @param uri the url to post
     * @param parts to post
     * @return content bytes
     * @throws IOException
     */
    public byte[] POSTbytes(final String uri, final Map<String, ContentBody> parts, final boolean usegzip, final boolean concurrent) throws IOException {
        final MultiProtocolURL url = new MultiProtocolURL(uri);
        return POSTbytes(url, url.getHost(), parts, usegzip, concurrent);
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
    public byte[] POSTbytes(final MultiProtocolURL url, final String vhost, final Map<String, ContentBody> post, final boolean usegzip, final boolean concurrent) throws IOException {
    	final HttpPost httpPost = new HttpPost(url.toNormalform(true));

        setHost(vhost); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
    	if (vhost == null) setHost(Domains.LOCALHOST);
    	
    	final MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
    	for (final Entry<String,ContentBody> part : post.entrySet())
    		entityBuilder.addPart(part.getKey(), part.getValue());
    	final HttpEntity multipartEntity = entityBuilder.build();
        // statistics
        this.upbytes = multipartEntity.getContentLength();

        if (usegzip) {
            httpPost.setEntity(new GzipCompressingEntity(multipartEntity));
        } else {
            httpPost.setEntity(multipartEntity);
        }

        return getContentBytes(httpPost, Integer.MAX_VALUE, concurrent);
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
    public byte[] POSTbytes(final String uri, final InputStream instream, final long length, final boolean concurrent) throws IOException {
        final MultiProtocolURL url = new MultiProtocolURL(uri);
        final HttpPost httpPost = new HttpPost(url.toNormalform(true));
        String host = url.getHost();
        if (host == null) host = Domains.LOCALHOST;
        setHost(host); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service

        final InputStreamEntity inputStreamEntity = new InputStreamEntity(instream, length);
        // statistics
        this.upbytes = length;
        httpPost.setEntity(inputStreamEntity);
        return getContentBytes(httpPost, Integer.MAX_VALUE, concurrent);
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
                this.httpResponse.close();
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
                this.httpResponse.close();
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
	        this.httpResponse.close();
        }
        if (this.currentRequest != null) {
                ConnectionInfo.removeConnection(this.currentRequest.hashCode());
                this.currentRequest.abort();
                this.currentRequest = null;
        }
    }

    private byte[] getContentBytes(final HttpUriRequest httpUriRequest, final int maxBytes, final boolean concurrent) throws IOException {
        byte[] content = null;
    	try {
            execute(httpUriRequest, concurrent);
            if (this.httpResponse == null) return null;
            // get the response body
            final HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) {
                if (getStatusCode() == 200 && (maxBytes < 0 || httpEntity.getContentLength() < maxBytes)) {
                    content = getByteArray(httpEntity, maxBytes);
                }
                // Ensures that the entity content is fully consumed and the content stream, if exists, is closed.
            	EntityUtils.consume(httpEntity);
            }
        } catch (final IOException e) {
                httpUriRequest.abort();
                throw e;
        } finally {
        	if (this.httpResponse != null) this.httpResponse.close();
        	ConnectionInfo.removeConnection(httpUriRequest.hashCode());
        }
    	return content;
    }

    private void execute(final HttpUriRequest httpUriRequest, final boolean concurrent) throws IOException {
    	final HttpClientContext context = HttpClientContext.create();
    	context.setRequestConfig(reqConfBuilder.build());
    	if (this.host != null)
    		context.setTargetHost(new HttpHost(this.host));
    	
    	setHeaders(httpUriRequest);
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
        final long time = System.currentTimeMillis();
	    try {
	        final CloseableHttpClient client = clientBuilder.build();
	        if (concurrent) {
	            final CloseableHttpResponse[] thr = new CloseableHttpResponse[]{null};
	            final Throwable[] te = new Throwable[]{null};
	            Thread t = new Thread() {
	                @Override
                    public void run() {
	                   this.setName("HTTPClient.execute(" + httpUriRequest.getURI() + ")");
	                   try {
	                       thr[0] = client.execute(httpUriRequest, context);
	                   } catch (Throwable e) {
	                       te[0] = e;
	                   }
	                }
	            };
	            t.start();
	            try {t.join(this.timeout);} catch (InterruptedException e) {}
	            if (t.isAlive()) try {t.interrupt();} catch (Throwable e) {}
	            if (te[0] != null) throw te[0];
	            if (thr[0] == null) throw new IOException("timout to client after " + this.timeout + "ms");
	            this.httpResponse = thr[0];
	        } else {
	            this.httpResponse = client.execute(httpUriRequest, context);
	        }
            this.httpResponse.setHeader(HeaderFramework.RESPONSE_TIME_MILLIS, Long.toString(System.currentTimeMillis() - time));
        } catch (final Throwable e) {
            ConnectionInfo.removeConnection(httpUriRequest.hashCode());
            httpUriRequest.abort();
            if (this.httpResponse != null) this.httpResponse.close();
            throw new IOException("Client can't execute: "
            		+ (e.getCause() == null ? e.getMessage() : e.getCause().getMessage())
            		+ " duration=" + Long.toString(System.currentTimeMillis() - time));
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
    	if (this.host != null) httpUriRequest.setHeader(HTTP.TARGET_HOST, this.host);
        httpUriRequest.setHeader("Connection", "close"); // don't keep alive, prevent CLOSE_WAIT state
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

    private static SSLConnectionSocketFactory getSSLSocketFactory() {
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

        final SSLConnectionSocketFactory sslSF = new SSLConnectionSocketFactory(
                sslContext,
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    	return sslSF;
    }

    /**
     * If the Keep-Alive header is not present in the response,
     * HttpClient assumes the connection can be kept alive indefinitely.
     * Here we limit this to 5 seconds if unset and to a max of 25 seconds
     *
     * @param defaultHttpClient
     */
	private static ConnectionKeepAliveStrategy customKeepAliveStrategy() {
		return new DefaultConnectionKeepAliveStrategy() {
			@Override
			public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
				long keepAlive = super.getKeepAliveDuration(response, context);
				return Math.min(Math.max(keepAlive, 5000), 25000);
			}
		};
	}

    /**
     * testing
     *
     * @param args urls to test
     */
    public static void main(final String[] args) {
        String url = null;
        // prepare Parts
//        final Map<String,ContentBody> newparts = new LinkedHashMap<String,ContentBody>();
//        try {
//            newparts.put("foo", new StringBody("FooBar"));
//            newparts.put("bar", new StringBody("BarFoo"));
//        } catch (final UnsupportedEncodingException e) {
//            System.out.println(e.getStackTrace());
//        }
        final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        client.setRedirecting(false);
        // Get some
        for (final String arg : args) {
            url = arg;
            if (!url.toUpperCase().startsWith("HTTP://")) {
                    url = "http://" + url;
            }
            try {
                System.out.println(UTF8.String(client.GETbytes(url, null, null, true)));
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
//        System.out.println(client.getHttpResponse().getLocale());
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

	public static class IdleConnectionMonitorThread extends Thread {
	    
	    private final HttpClientConnectionManager connMgr;
	    private volatile boolean shutdown;
	    
	    public IdleConnectionMonitorThread(HttpClientConnectionManager connMgr) {
	        super();
	        this.setName("HTTPClient.IdleConnectionMonitorThread");
	        this.connMgr = connMgr;
	    }

	    @Override
	    public void run() {
	        try {
	            while (!shutdown) {
	                synchronized (this) {
	                    wait(5000);
	                    // Close expired connections
	                    connMgr.closeExpiredConnections();
	                    // Optionally, close connections
	                    // that have been idle longer than 30 sec
	                    connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
	                }
	            }
                connMgr.shutdown();
	        } catch (final InterruptedException ex) {
	            // terminate
	        }
	    }
	    
	    public void shutdown() {
	        shutdown = true;
	        synchronized (this) {
	            notifyAll();
	        }
	    }
	}

}
