/**
 *  HTTPClient
 *  Copyright 2010 by Sebastian Gaebel
 *  First released 01.07.2010 at http://yacy.net
 *  
 *  $LastChangedDate: 2010-06-16 17:11:21 +0200 (Mi, 16 Jun 2010) $
 *  $LastChangedRevision: 7020 $
 *  $LastChangedBy: sixcooler $
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
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ConnectionInfo;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
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
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;


/**
 * HttpClient implementation which uses HttpComponents Client {@link http://hc.apache.org/}
 * 
 * @author sixcooler
 *
 */
public class HTTPClient {

	private final static int maxcon = 20;
	private static IdledConnectionEvictor idledConnectionEvictor = null;
	private static HttpClient httpClient = initConnectionManager();
	private Header[] headers = null;
	private HttpResponse httpResponse = null;
	private HttpUriRequest currentRequest = null;
	private long upbytes = 0L;
	private int timeout = 10000;
	private String userAgent = null;
	private String host = null;
	private boolean redirecting = true;
	private String realm = null;
    
    public HTTPClient() {
    	super();
    }
    
    public static void setDefaultUserAgent(final String defaultAgent) {
    	HttpProtocolParams.setUserAgent(httpClient.getParams(), defaultAgent);
    }
    
    private static HttpClient initConnectionManager() {
		// Create and initialize HTTP parameters
		final HttpParams httpParams = new BasicHttpParams();
		/**
		 * ConnectionManager settings
		 */
		// TODO: how much connections do we need? - default: 20
		ConnManagerParams.setMaxTotalConnections(httpParams, maxcon);
		// for statistics same value should also be set here
		ConnectionInfo.setMaxcount(maxcon);
		// connections per host (2 default)
		final ConnPerRouteBean connPerRoute = new ConnPerRouteBean(2);
		// Increase max connections for localhost
		HttpHost localhost = new HttpHost("locahost");
		connPerRoute.setMaxForRoute(new HttpRoute(localhost), maxcon);
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams, connPerRoute);
		// how long to wait for getting a connection from manager in milliseconds
		ConnManagerParams.setTimeout(httpParams, 9000L);
		/**
		 * HTTP protocol settings
		 */
		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
		// UserAgent
		HttpProtocolParams.setUserAgent(httpParams, MultiProtocolURI.yacybotUserAgent);
		HttpProtocolParams.setUseExpectContinue(httpParams, false); // IMPORTANT - if not set to 'false' then servers do not process the request until a time-out of 2 seconds
		/**
		 * HTTP connection settings
		 */
		// timeout in milliseconds until a connection is established in milliseconds
		HttpConnectionParams.setConnectionTimeout(httpParams, 9500);
		// SO_LINGER affects the socket close operation in seconds
		// HttpConnectionParams.setLinger(httpParams, 6);
		// TODO: is default ok?
		// HttpConnectionParams.setSocketBufferSize(httpParams, 8192);
		// SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
		HttpConnectionParams.setSoTimeout(httpParams, 9900);
		// getting an I/O error when executing a request over a connection that has been closed at the server side
		HttpConnectionParams.setStaleCheckingEnabled(httpParams, true);
		// conserve bandwidth by minimizing the number of segments that are sent
		HttpConnectionParams.setTcpNoDelay(httpParams, false);
		// TODO: testing noreuse - there will be HttpConnectionParams.setSoReuseaddr(HttpParams params, boolean reuseaddr) in core-4.1
		
		// Create and initialize scheme registry
		final SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", getSSLSocketFactory(), 443));

		ClientConnectionManager clientConnectionManager = new ThreadSafeClientConnManager(httpParams, schemeRegistry);

		httpClient = new DefaultHttpClient(clientConnectionManager, httpParams);
		// ask for gzip
		((AbstractHttpClient) httpClient).addRequestInterceptor(new GzipRequestInterceptor());
		// uncompress gzip
		((AbstractHttpClient) httpClient).addResponseInterceptor(new GzipResponseInterceptor());

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
    
    /**
     * This method sets the Header used for the request
     * 
     * @param entrys to be set as request header
     */
    public void setHeader(final Set<Entry<String, String>> entrys) {
    	if (entrys != null) {
	    	int i = 0;
	    	headers = new Header[entrys.size()];
	    	for (final Entry<String, String> entry : entrys) {
	    		headers[i++] = new BasicHeader(entry.getKey(),entry.getValue());
	    	}
    	}
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
    public void setUserAgent(final String userAgent) {
    	this.userAgent = userAgent;
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
    	return GETbytes(uri, Long.MAX_VALUE);
    }
    
    /**
     * This method GETs a page from the server.
     * 
     * @param uri the url to get
     * @param maxBytes to get
     * @return content bytes
     * @throws IOException 
     */
    public byte[] GETbytes(final String uri, long maxBytes) throws IOException {
    	final HttpGet httpGet = new HttpGet(uri);
    	return getContentBytes(httpGet, maxBytes);
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
        if (currentRequest != null) throw new IOException("Client is in use!");
        final HttpGet httpGet = new HttpGet(uri);
        currentRequest = httpGet;
        execute(httpGet);
    }

    /**
     * This method gets HEAD response
     * 
     * @param uri the url to Response from
     * @return the HttpResponse
     * @throws IOException
     */
    public HttpResponse HEADResponse(final String uri) throws IOException {
    	final HttpHead httpHead = new HttpHead(uri);
    	execute(httpHead);
    	finish();
    	ConnectionInfo.removeConnection(httpHead.hashCode());
    	return httpResponse;
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
    public void POST(final String uri, final InputStream instream, long length) throws IOException {
    	if (currentRequest != null) throw new IOException("Client is in use!");
    	final HttpPost httpPost = new HttpPost(uri);
    	final InputStreamEntity inputStreamEntity = new InputStreamEntity(instream, length);
    	// statistics
    	upbytes = length;
    	httpPost.setEntity(inputStreamEntity);
    	currentRequest = httpPost;
    	execute(httpPost);
    }
    
    /**
     * This method POSTs a page from the server.
     * 
     * @param uri the url to post
     * @param parts to post
     * @return content bytes
     * @throws IOException 
     */
	public byte[] POSTbytes(final String uri, final LinkedHashMap<String, ContentBody> parts, final boolean usegzip) throws IOException {
    	final HttpPost httpPost = new HttpPost(uri);

    	final MultipartEntity multipartEntity = new MultipartEntity();
    	for (Entry<String,ContentBody> part : parts.entrySet())
    		multipartEntity.addPart(part.getKey(), part.getValue());
    	// statistics
    	upbytes = multipartEntity.getContentLength();

    	if (usegzip) {
    		httpPost.setEntity(new GzipCompressingEntity(multipartEntity));
    	} else {
    		httpPost.setEntity(multipartEntity);
    	}
    	
    	return getContentBytes(httpPost, Long.MAX_VALUE);
    }
	
	/**
	 * 
	 * @return HttpResponse from call
	 */
	public HttpResponse getHttpResponse() {
		return httpResponse;
	}
	
	/**
	 * 
	 * @return status code from http request
	 */
	public int getStatusCode() {
	    return httpResponse.getStatusLine().getStatusCode();
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
		if (httpResponse != null && currentRequest != null) {
			final HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) try {
				return httpEntity.getContent();
			} catch (final IOException e) {
	    		ConnectionInfo.removeConnection(currentRequest.hashCode());
	    		currentRequest.abort();
				currentRequest = null;
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
		if (httpResponse != null && currentRequest != null) {
			final HttpEntity httpEntity = httpResponse.getEntity();
	    	if (httpEntity != null) try {
	    		httpEntity.writeTo(outputStream);
	    		outputStream.flush();
	    		// TODO: The name of this method is misnomer.
	    		// It will be renamed to #finish() in the next major release of httpcore
	    		httpEntity.consumeContent();
				ConnectionInfo.removeConnection(currentRequest.hashCode());
				currentRequest = null;
	    	} catch (final IOException e) {
	    		ConnectionInfo.removeConnection(currentRequest.hashCode());
	    		currentRequest.abort();
				currentRequest = null;
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
		if (httpResponse != null) {
			final HttpEntity httpEntity = httpResponse.getEntity();
	    	if (httpEntity != null && httpEntity.isStreaming()) {
	    		// TODO: The name of this method is misnomer.
	    		// It will be renamed to #finish() in the next major release of httpcore
	    		httpEntity.consumeContent();
	    	}
		}
		if (currentRequest != null) {
			ConnectionInfo.removeConnection(currentRequest.hashCode());
			currentRequest.abort();
			currentRequest = null;
		}
	}
    
    private byte[] getContentBytes(final HttpUriRequest httpUriRequest, final long maxBytes) throws IOException {
    	byte[] content = null;
    	try {
    		execute(httpUriRequest);
    		if (httpResponse == null || httpResponse.getStatusLine().getStatusCode() != 200) return null;
        	// get the response body
        	final HttpEntity httpEntity = httpResponse.getEntity();
        	if (httpEntity != null) {
        		if (httpEntity.getContentLength()  < maxBytes) {
        			content = EntityUtils.toByteArray(httpEntity);
        		}
        		// TODO: The name of this method is misnomer.
        		// It will be renamed to #finish() in the next major release of httpcore
        		httpEntity.consumeContent();
        	}
		} catch (final IOException e) {
			ConnectionInfo.removeConnection(httpUriRequest.hashCode());
			httpUriRequest.abort();
			throw e;
		}
		ConnectionInfo.removeConnection(httpUriRequest.hashCode());
		return content;
    }
    
    private void execute(final HttpUriRequest httpUriRequest) throws IOException {
    	final HttpContext httpContext = new BasicHttpContext();
    	setHeaders(httpUriRequest);
    	setParams(httpUriRequest.getParams());
    	setProxy(httpUriRequest.getParams());
    	// statistics
    	storeConnectionInfo(httpUriRequest);
    	try {
        	// execute the method; some asserts confirm that that the request can be send with Content-Length and is therefore not terminated by EOF
    	    if (httpUriRequest instanceof HttpEntityEnclosingRequest) {
    	        HttpEntityEnclosingRequest hrequest = (HttpEntityEnclosingRequest) httpUriRequest;
    	        HttpEntity entity = hrequest.getEntity();
    	        assert entity != null;
    	        //assert !entity.isChunked();
    	        //assert entity.getContentLength() >= 0;
    	        assert !hrequest.expectContinue();
    	    }
			httpResponse = httpClient.execute(httpUriRequest, httpContext);
		} catch (Exception e) {
		    //e.printStackTrace();
			ConnectionInfo.removeConnection(httpUriRequest.hashCode());
			httpUriRequest.abort();
			throw new IOException("Client can't execute: " + e.getMessage());
		}
    }
    
    private void setHeaders(final HttpUriRequest httpUriRequest) {
    	if (headers != null) {
    		for (Header header : headers) {
    			httpUriRequest.addHeader(header);
    		}
    	}
        if (realm != null)
            httpUriRequest.setHeader("Authorization", "realm=" + realm);
    }
    
    private void setParams(final HttpParams httpParams) {
    	HttpClientParams.setRedirecting(httpParams, redirecting);
    	HttpConnectionParams.setConnectionTimeout(httpParams, timeout);
    	HttpConnectionParams.setSoTimeout(httpParams, timeout);
    	if (userAgent != null)
    		HttpProtocolParams.setUserAgent(httpParams, userAgent);
    	if (host != null) 
    		httpParams.setParameter(HTTP.TARGET_HOST, host);
    }
    
    private void setProxy(final HttpParams httpParams) {
    	if (ProxySettings.use)
    		ConnRouteParams.setDefaultProxy(httpParams, ProxySettings.getProxyHost());
    	// TODO find a better way for this
    	ProxySettings.setProxyCreds((AbstractHttpClient) httpClient);
    }
    
    private void storeConnectionInfo(final HttpUriRequest httpUriRequest) {
    	final int port = httpUriRequest.getURI().getPort();
    	final String thost = httpUriRequest.getURI().getHost();
    	assert thost != null : "uri = " + httpUriRequest.getURI().toString();
    	ConnectionInfo.addConnection(new ConnectionInfo(
    			httpUriRequest.getURI().getScheme(),
    			port == 80 ? thost : thost + ":" + port,
    			httpUriRequest.getMethod() + " " + httpUriRequest.getURI().getPath(),
    			httpUriRequest.hashCode(),
    			System.currentTimeMillis(),
    			upbytes));
    }
    
    private static SSLSocketFactory getSSLSocketFactory() {
    	final TrustManager trustManager = new X509TrustManager() {
			public void checkClientTrusted(X509Certificate[] chain, String authType)
					throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain, String authType)
					throws CertificateException {
			}

			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
    	};
    	SSLContext sslContext = null;
    	try {
    		sslContext = SSLContext.getInstance("TLS");
    		sslContext.init(null, new TrustManager[] { trustManager }, null);
		} catch (NoSuchAlgorithmException e) {
			// should not happen
			// e.printStackTrace();
		} catch (KeyManagementException e) {
			// should not happen
			// e.printStackTrace();
		}

		final SSLSocketFactory sslSF = new SSLSocketFactory(sslContext);
		sslSF.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    	return sslSF;
    }

	/**
	 * testing
	 * 
	 * @param args urls to test
	 */
	public static void main(final String[] args) {
		String url = null;
		// prepare Parts
		final LinkedHashMap<String,ContentBody> newparts = new LinkedHashMap<String,ContentBody>();
		try {
			newparts.put("foo", new StringBody("FooBar"));
			newparts.put("bar", new StringBody("BarFoo"));
		} catch (UnsupportedEncodingException e) {
			System.out.println(e.getStackTrace());
		}
		HTTPClient client = new HTTPClient();
		client.setUserAgent("foobar");
		client.setRedirecting(false);
		// Get some
		for (int i = 0; i < args.length; i++) {
			url = args[i];
			if (!url.toUpperCase().startsWith("HTTP://")) {
				url = "http://" + url;
			}
			try {
				System.out.println(new String(client.GETbytes(url)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		// Head some
//		try {
//			client.HEADResponse(url);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		for (Header header: client.getHttpResponse().getAllHeaders()) {
			System.out.println("Header " + header.getName() + " : " + header.getValue());
//			for (HeaderElement element: header.getElements())
//				System.out.println("Element " + element.getName() + " : " + element.getValue());
		}
		System.out.println(client.getHttpResponse().getLocale());
		System.out.println(client.getHttpResponse().getProtocolVersion());
		System.out.println(client.getHttpResponse().getStatusLine());
		// Post some
//		try {
//			System.out.println(new String(client.POSTbytes(url, newparts)));
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		}
		// Close out connection manager
		try {
			HTTPClient.closeConnectionManager();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 
	 * @see: http://hc.apache.org/httpcomponents-client-4.0.1/tutorial/html/connmgmt.html#d4e638
	 *
	 */
	public static class IdledConnectionEvictor extends Thread {

		private final ClientConnectionManager clientConnectionManager;

		private volatile boolean shutdown;

		public IdledConnectionEvictor(ClientConnectionManager clientConnectionManager) {
			super();
			this.clientConnectionManager = clientConnectionManager;
		}

		@Override
		public void run() {
			try {
				while (!shutdown) {
					synchronized (this) {
						wait(5000);
						// Close expired connections
						clientConnectionManager.closeExpiredConnections();
						// Optionally, close connections
						// that have been idle longer than 5 sec
						// (some SOHO router act strange on >5sec idled connections)
						clientConnectionManager.closeIdleConnections(5, TimeUnit.SECONDS);
					}
				}
			} catch (InterruptedException ex) {
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
