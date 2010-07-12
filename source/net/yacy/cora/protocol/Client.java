package net.yacy.cora.protocol;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;


/**
 * 
 * @author sixcooler
 *
 */
public class Client {

	private static IdledConnectionEvictor idledConnectionEvictor = null;
	private static HttpClient httpClient = null;
	private static int count = 0;
	private int timeout = 10000;
	private String userAgent = null;
	private String host = null;
    
    public Client() {
    	super();
    	if (httpClient == null) {
    		initConnectionManager();
    	}
    }
    
    private static void initConnectionManager() {
		// Create and initialize HTTP parameters
		HttpParams httpParams = new BasicHttpParams();
		/**
		 * ConnectionManager settings
		 */
		// TODO: how much connections do we need? - default: 20
		// ConnManagerParams.setMaxTotalConnections(httpParams, 100);
		// perhaps we need more than 2(default) connections per host?
		ConnPerRouteBean connPerRoute = new ConnPerRouteBean(2);
		// Increase max connections for localhost to 100
		HttpHost localhost = new HttpHost("locahost");
		connPerRoute.setMaxForRoute(new HttpRoute(localhost), 100);
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
		HttpConnectionParams.setLinger(httpParams, 6);
		// TODO: is default ok?
		// HttpConnectionParams.setSocketBufferSize(httpParams, 8192);
		// SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
		HttpConnectionParams.setSoTimeout(httpParams, 5000);
		// getting an I/O error when executing a request over a connection that has been closed at the server side
		HttpConnectionParams.setStaleCheckingEnabled(httpParams, true);
		// conserve bandwidth by minimizing the number of segments that are sent
		HttpConnectionParams.setTcpNoDelay(httpParams, false);
		
		// Create and initialize scheme registry
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

		ClientConnectionManager clientConnectionManager = new ThreadSafeClientConnManager(httpParams, schemeRegistry);

		httpClient = new DefaultHttpClient(clientConnectionManager, httpParams);

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
     * number of active connections
     * 
     * @return number of active connections
     */
    public static int connectionCount() {
    	return count;
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
    	HttpGet httpGet = new HttpGet(uri);
    	return getContentBytes(httpGet, maxBytes);
    }
    
    /**
     * 
     * @param uri the url to post
     * @param parts to post
     * @return content bytes
     * @throws IOException 
     */
	public byte[] POSTbytes(final String uri, LinkedHashMap<String,ContentBody> parts) throws IOException {
    	HttpPost httpPost = new HttpPost(uri);

    	MultipartEntity multipartEntity = new MultipartEntity();
    	for (Entry<String,ContentBody> part : parts.entrySet())
    		multipartEntity.addPart(part.getKey(), part.getValue());

    	httpPost.setEntity(multipartEntity);
    	
    	return getContentBytes(httpPost, Long.MAX_VALUE);
    }
    
    private byte[] getContentBytes(HttpUriRequest httpUriRequest, long maxBytes) throws IOException {
    	count++;
    	byte[] content = null;
    	final HttpContext httpContext = new BasicHttpContext();
    	httpUriRequest.getParams().setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
    	if (userAgent != null)
    		httpUriRequest.getParams().setParameter(CoreProtocolPNames.USER_AGENT, userAgent);
    	if (host != null) 
    		httpUriRequest.getParams().setParameter(HTTP.TARGET_HOST, host);
    	
    	try {
    		// execute the method
        	HttpResponse httpResponse = httpClient.execute(httpUriRequest, httpContext);
        	// get the response body
        	HttpEntity httpEntity = httpResponse.getEntity();
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
			throw e;
		}
		count--;
		return content;
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
		client.setHost("sixcooler");
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
		// Post some
		try {
			System.out.println(new String(client.POSTbytes(url, newparts)));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
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
