package net.yacy.cora.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
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
public class Client {

	private final static int maxcon = 20;
	private static IdledConnectionEvictor idledConnectionEvictor = null;
	private static HttpClient httpClient = null;
	private Header[] headers = null;
	private HttpResponse httpResponse = null;
	private HttpUriRequest currentRequest = null;
	private long upbytes = 0L;
	private int timeout = 10000;
	private String userAgent = null;
	private String host = null;
	private boolean redirecting = true;
    
    public Client() {
    	super();
    	if (httpClient == null) {
    		initConnectionManager();
    	}
    }
    
    private static void initConnectionManager() {
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
		// Increase max connections for localhost to 100
		HttpHost localhost = new HttpHost("locahost");
		connPerRoute.setMaxForRoute(new HttpRoute(localhost), maxcon);
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams, connPerRoute);
		// how long to wait for getting a connection from manager in milliseconds
		ConnManagerParams.setTimeout(httpParams, 3000L);
		/**
		 * HTTP protocol settings
		 */
		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
		// UserAgent
		HttpProtocolParams.setUserAgent(httpParams, "yacy (" + systemOST +") yacy.net");
		/**
		 * HTTP connection settings
		 */
		// timeout in milliseconds until a connection is established in milliseconds
		HttpConnectionParams.setConnectionTimeout(httpParams, 10000);
		// SO_LINGER affects the socket close operation in seconds
		// HttpConnectionParams.setLinger(httpParams, 6);
		// TODO: is default ok?
		// HttpConnectionParams.setSocketBufferSize(httpParams, 8192);
		// SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
		HttpConnectionParams.setSoTimeout(httpParams, 5000);
		// getting an I/O error when executing a request over a connection that has been closed at the server side
		HttpConnectionParams.setStaleCheckingEnabled(httpParams, true);
		// conserve bandwidth by minimizing the number of segments that are sent
		HttpConnectionParams.setTcpNoDelay(httpParams, false);
		// TODO: testing noreuse - there will be HttpConnectionParams.setSoReuseaddr(HttpParams params, boolean reuseaddr) in core-4.1
		
		// Create and initialize scheme registry
		final SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

		ClientConnectionManager clientConnectionManager = new ThreadSafeClientConnManager(httpParams, schemeRegistry);

		httpClient = new DefaultHttpClient(clientConnectionManager, httpParams);
		// cookie policy
		httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BEST_MATCH);
		// ask for gzip
		((AbstractHttpClient) httpClient).addRequestInterceptor(new GzipRequestInterceptor());
		// uncompress gzip
		((AbstractHttpClient) httpClient).addResponseInterceptor(new GzipResponseInterceptor());

		idledConnectionEvictor = new IdledConnectionEvictor(clientConnectionManager);
		idledConnectionEvictor.start();
        
    }
    
    /**
     * this should be called just before shutdown
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
     * 
     * @param timeout in milliseconds
     */
    public void setTimout(final int timeout) {
    	this.timeout = timeout;
    }
    
    /**
     * @param userAgent
     */
    public void setUserAgent(final String userAgent) {
    	this.userAgent = userAgent;
    }
    
    /**
     * @param host
     */
    public void setHost(final String host) {
    	this.host = host;
    }
    
    /**
     * 
     * @param redirecting
     */
    public void setRedirecting(final boolean redirecting) {
    	this.redirecting = redirecting;
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
	public byte[] POSTbytes(final String uri, final LinkedHashMap<String,ContentBody> parts) throws IOException {
    	final HttpPost httpPost = new HttpPost(uri);

    	final MultipartEntity multipartEntity = new MultipartEntity();
    	for (Entry<String,ContentBody> part : parts.entrySet())
    		multipartEntity.addPart(part.getKey(), part.getValue());
    	// statistics
    	upbytes = multipartEntity.getContentLength();

    	httpPost.setEntity(multipartEntity);
    	
    	return getContentBytes(httpPost, Long.MAX_VALUE);
    }
	
	/**
	 * 
	 * @return HttpResponse from call
	 */
	public HttpResponse getHttpResponse() {
		return httpResponse;
	}
	
	public HashMap<String, String> getHeaderHashMap() {
		if (httpResponse == null) return null;
		final HashMap<String, String> hmap = new HashMap<String, String>();
		for (Header h : httpResponse.getAllHeaders()) {
			hmap.put(h.getName(), h.getValue());
		}
		return hmap;
	}
	
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
	    		currentRequest.abort();
				ConnectionInfo.removeConnection(currentRequest.hashCode());
				currentRequest = null;
	    		throw e;
	    	}
		}
	}
	
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
			currentRequest.abort();
			ConnectionInfo.removeConnection(currentRequest.hashCode());
			currentRequest = null;
		}
	}
    
    private byte[] getContentBytes(final HttpUriRequest httpUriRequest, final long maxBytes) throws IOException {
    	byte[] content = null;
    	try {
    		execute(httpUriRequest);
    		if (httpResponse == null) return null;
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
			httpUriRequest.abort();
			ConnectionInfo.removeConnection(httpUriRequest.hashCode());
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
        	// execute the method
			httpResponse = httpClient.execute(httpUriRequest, httpContext);
		} catch (ClientProtocolException e) {
			httpUriRequest.abort();
			ConnectionInfo.removeConnection(httpUriRequest.hashCode());
			throw new IOException("Client can't execute: " + e.getMessage());
		}
    }
    
    private void setHeaders(final HttpUriRequest httpUriRequest) {
    	if (headers != null) {
    		for (Header header : headers) {
    			httpUriRequest.addHeader(header);
    		}
    	}
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
    	ConnectionInfo.addConnection(new ConnectionInfo(
    			httpUriRequest.getURI().getScheme(),
    			port == 80 ? thost : thost + ":" + port,
    			httpUriRequest.getMethod() + " " + httpUriRequest.getURI().getPath(),
    			httpUriRequest.hashCode(),
    			System.currentTimeMillis(),
    			upbytes));
    }
    
    /**
     * provide system information for client identification
     */
    private static final String systemOST = System.getProperty("os.arch", "no-os-arch") + " " +
            System.getProperty("os.name", "no-os-name") + " " + System.getProperty("os.version", "no-os-version") +
            "; " + "java " + System.getProperty("java.version", "no-java-version") + "; " + generateLocation();
    
    /**
     * generating the location string
     * 
     * @return
     */
    public static String generateLocation() {
        String loc = System.getProperty("user.timezone", "nowhere");
        final int p = loc.indexOf('/');
        if (p > 0) {
            loc = loc.substring(0, p);
        }
        loc = loc + "/" + System.getProperty("user.language", "dumb");
        return loc;
    }

    /**
     * @return the systemOST
     */
    public static String getSystemOST() {
        return systemOST;
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
		Client client = new Client();
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
			Client.closeConnectionManager();
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
