/**
 *  HTTPClient
 *  Copyright 2010 by Sebastian Gaebel
 *  First released 01.07.2010 at https://yacy.net
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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.IdleConnectionEvictor;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EntityUtils;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.http.auth.YaCyDigestSchemeFactory;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.Memory;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.NamePrefixThreadFactory;


/**
 * HttpClient implementation which uses <a href="http://hc.apache.org/">HttpComponents Client</a>.
 *
 * @author sixcooler
 *
 */
public class HTTPClient implements Closeable {

    private static final int default_timeout = 6000;

    /** Maximum number of simultaneously open outgoing HTTP connections in the pool */
    private static final int maxcon = 200;

    /** Default sleep time in seconds between each run of the connection evictor */
    private static final int DEFAULT_CONNECTION_EVICTOR_SLEEP_TIME = 5;

    /** Default maximum time in seconds to keep alive an idle connection in the pool */
    private static final int DEFAULT_POOLED_CONNECTION_TIME_TO_LIVE = 30;

    private static RequestConfig DFLTREQUESTCONFIG = initRequestConfig(default_timeout);

    /** Use the custom YaCyDigestScheme for HTTP Digest Authentication */
    private static final Lookup<AuthSchemeProvider> AUTHSCHEMEREGISTRY = RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.BASIC, new BasicSchemeFactory())
            .register(AuthSchemes.DIGEST, new YaCyDigestSchemeFactory())
            .build();

    /** The connection manager holding the configured connection pool for this client */
    public static final PoolingHttpClientConnectionManager CONNECTION_MANAGER = initPoolingConnectionManager();

    /** Default setting to apply when the JVM system option jsse.enableSNIExtension is not defined */
    public static final boolean ENABLE_SNI_EXTENSION_DEFAULT = true;

    /** When true, Server Name Indication (SNI) extension is enabled on outgoing TLS connections.
     * @see <a href="https://tools.ietf.org/html/rfc6066#section-3">RFC 6066 definition</a>
     * @see <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=7127374">JDK 1.7 bug</a> on "unrecognized_name" warning for SNI */
    public static final AtomicBoolean ENABLE_SNI_EXTENSION = new AtomicBoolean(
            Boolean.parseBoolean(System.getProperty("jsse.enableSNIExtension", Boolean.toString(ENABLE_SNI_EXTENSION_DEFAULT))));


    /**
     * Background daemon thread evicting expired idle connections from the pool.
     * This may be eventually already done by the pool itself on connection request,
     * but this background task helps when no request is made to the pool for a long
     * time period.
     */
    private static final IdleConnectionEvictor EXPIRED_CONNECTIONS_EVICTOR = new IdleConnectionEvictor(
            CONNECTION_MANAGER, DEFAULT_CONNECTION_EVICTOR_SLEEP_TIME, TimeUnit.SECONDS,
            DEFAULT_POOLED_CONNECTION_TIME_TO_LIVE, TimeUnit.SECONDS);

    static {
        EXPIRED_CONNECTIONS_EVICTOR.start();
    }

    private final static HttpClientBuilder clientBuilder = initClientBuilder();
    private final RequestConfig.Builder reqConfBuilder;
    private Set<Entry<String, String>> headers = null;
    private long upbytes = 0L;
    private String host = null;
    private final long timeout;
    private static ExecutorService executor = Executors
            .newCachedThreadPool(new NamePrefixThreadFactory(HTTPClient.class.getSimpleName() + ".execute"));

    /** these are the main variable to hold information and to take care of closing: */
    private CloseableHttpClient client = null;
    private CloseableHttpResponse httpResponse = null;
    private HttpUriRequest currentRequest = null;


    public HTTPClient(final ClientIdentification.Agent agent) {
        super();
        this.timeout = agent.clientTimeout;
        clientBuilder.setUserAgent(agent.userAgent);
        this.reqConfBuilder = RequestConfig.copy(DFLTREQUESTCONFIG);
        setTimout(agent.clientTimeout);
    }

    public HTTPClient(final ClientIdentification.Agent agent, final int timeout) {
        super();
        this.timeout = timeout;
        clientBuilder.setUserAgent(agent.userAgent);
        this.reqConfBuilder = RequestConfig.copy(DFLTREQUESTCONFIG);
        setTimout(timeout);
    }

    private static RequestConfig initRequestConfig(int timeout) {
        final RequestConfig.Builder builder = RequestConfig.custom();
        // IMPORTANT - if not set to 'false' then servers do not process the request until a time-out of 2 seconds
        builder.setExpectContinueEnabled(false);
        // timeout in milliseconds until a connection is established in milliseconds
        builder.setConnectionRequestTimeout(timeout);
        builder.setConnectTimeout(timeout);
        // SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
        builder.setSocketTimeout(timeout);
        // ignore cookies, cause this may cause segfaults in default cookiestore and is not needed
        builder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
        builder.setRedirectsEnabled(true);
        builder.setRelativeRedirectsAllowed(true);
        return builder.build();
    }

    private static HttpClientBuilder initClientBuilder() {
        final HttpClientBuilder builder = HttpClientBuilder.create();

        builder.setConnectionManager(CONNECTION_MANAGER);
        builder.setConnectionManagerShared(true);

        builder.setDefaultRequestConfig(DFLTREQUESTCONFIG);

        // UserAgent
        builder.setUserAgent(ClientIdentification.yacyInternetCrawlerAgent.userAgent);

        // remove retries; we expect connections to fail; therefore we should not retry
        //builder.disableAutomaticRetries();
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
        final PoolingHttpClientConnectionManager pooling = new PoolingHttpClientConnectionManager(registry, null, null, new DnsResolver(){
            @Override
            public InetAddress[] resolve(final String host0)throws UnknownHostException {
                final InetAddress ip = Domains.dnsResolve(host0);
                if (ip == null) throw new UnknownHostException(host0);
                return new InetAddress[]{ip};
            }}, DEFAULT_POOLED_CONNECTION_TIME_TO_LIVE, TimeUnit.SECONDS);
        initPoolMaxConnections(pooling, maxcon);

        pooling.setValidateAfterInactivity(default_timeout); // on init set to default 5000ms
        final SocketConfig socketConfig = SocketConfig.custom()
                // Defines whether the socket can be bound even though a previous connection is still in a timeout state.
                .setSoReuseAddress(true)
                // SO_TIMEOUT: maximum period inactivity between two consecutive data packets in milliseconds
                .setSoTimeout(default_timeout)
                // conserve bandwidth by minimizing the number of segments that are sent
                .setTcpNoDelay(false)
                .build();
        pooling.setDefaultSocketConfig(socketConfig);

        return pooling;
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
    public static void initPoolMaxConnections(final PoolingHttpClientConnectionManager pool, final int maxConnections) {
        if (pool == null) {
            throw new IllegalArgumentException("pool parameter must not be null");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections parameter must be greater than zero");
        }
        pool.setMaxTotal(maxConnections);
        // for statistics same value should also be set here
        ConnectionInfo.setMaxcount(maxConnections);

        // connections per host (2 default)
        pool.setDefaultMaxPerRoute((int) (2 * Memory.cores()));

        // Increase max connections for localhost
        final HttpHost localhost = new HttpHost(Domains.LOCALHOST);
        pool.setMaxPerRoute(new HttpRoute(localhost), maxConnections);
    }

    /**
     * This method should be called just before shutdown to stop the
     * ConnectionManager and the idle connections evictor.
     *
     * @throws InterruptedException
     *             when the current thread is interrupted before the idle
     *             connections evictor thread termination.
     */
    public static void closeConnectionManager() throws InterruptedException {
        try {
            if (EXPIRED_CONNECTIONS_EVICTOR != null) {
                // Shut down the evictor thread
                EXPIRED_CONNECTIONS_EVICTOR.shutdown();
                EXPIRED_CONNECTIONS_EVICTOR.awaitTermination(1L, TimeUnit.SECONDS);
            }
        } finally {
            if (CONNECTION_MANAGER != null) {
                CONNECTION_MANAGER.shutdown();
            }
        }
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
        this.reqConfBuilder.setSocketTimeout(timeout);
        this.reqConfBuilder.setConnectTimeout(timeout);
        this.reqConfBuilder.setConnectionRequestTimeout(timeout);
        DFLTREQUESTCONFIG = initRequestConfig(timeout);
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
        this.reqConfBuilder.setRedirectsEnabled(redirecting);
        this.reqConfBuilder.setRelativeRedirectsAllowed(redirecting);
    }

    /**
     * This method GETs a page from the server.
     *
     * @param uri the url to get
     * @param username user name for HTTP authentication : only sent requesting localhost
     * @param pass password for HTTP authentication : only sent when requesting localhost
     * @param concurrent whether a new thread should be created to handle the request.
     * Ignored when requesting localhost or when the authentication password is not null
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
     * @param username user name for HTTP authentication : only sent requesting localhost
     * @param pass password for HTTP authentication : only sent when requesting localhost
     * @param concurrent whether a new thread should be created to handle the request.
     * Ignored when requesting localhost or when the authentication password is not null
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
     * @param username user name for HTTP authentication : only sent requesting localhost
     * @param pass password for HTTP authentication : only sent when requesting localhost
     * @param maxBytes to get
     * @param concurrent whether a new thread should be created to handle the request.
     * Ignored when requesting localhost or when the authentication password is not null
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
     * @param username user name for HTTP authentication : only sent requesting localhost
     * @param pass password for HTTP authentication : only sent when requesting localhost
     * @param maxBytes maximum response bytes to read
     * @param concurrent whether a new thread should be created to handle the request.
     * Ignored when requesting localhost or when the authentication password is not null
     * @return content bytes
     * @throws IOException
     */
    public byte[] GETbytes(final MultiProtocolURL url, final String username, final String pass, final int maxBytes, final boolean concurrent) throws IOException {
        final boolean localhost = Domains.isLocalhost(url.getHost());
        final String urix = url.toNormalform(true);

        try {
            this.currentRequest = new HttpGet(urix);
        } catch (final IllegalArgumentException e) {
            throw new IOException(e.getMessage()); // can be caused  at java.net.URI.create()
        }
        if (!localhost) setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        if (!localhost || pass == null) {
            return getContentBytes(maxBytes, concurrent);
        }

        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope("localhost", url.getPort()),
                new UsernamePasswordCredentials(username, pass));

        try (final CloseableHttpClient httpclient = clientBuilder.setDefaultCredentialsProvider(credsProvider)
                .setDefaultAuthSchemeRegistry(AUTHSCHEMEREGISTRY).build()) {
            this.httpResponse = httpclient.execute(this.currentRequest);
            final HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) {
                if (getStatusCode() == HttpStatus.SC_OK) {
                    if (maxBytes >= 0 && httpEntity.getContentLength() > maxBytes) {
                        /* When anticipated content length is already known and exceed the specified limit :
                         * throw an exception and abort the connection, consistently with getByteArray() implementation
                         * Otherwise returning null and consuming fully the entity can be very long on large resources */
                        throw new IOException("Content to download exceed maximum value of " + Formatter.bytesToString(maxBytes));
                    }
                    return getByteArray(httpEntity, maxBytes);
                }
            }
        } finally {
            close();
        }
        return null;
    }

    /**
     * This method GETs a page from the server.
     * to be used for streaming out
     * Please take care to call close()!
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
     * Please take care to call close()!
     *
     * @param url the url to get
     * @throws IOException
     */
    public void GET(final MultiProtocolURL url, final boolean concurrent) throws IOException {
        if (this.currentRequest != null) throw new IOException("Client is in use!");
        final String urix = url.toNormalform(true);

        try {
            this.currentRequest = new HttpGet(urix);
        } catch (final IllegalArgumentException e) {
            throw new IOException(e.getMessage()); // can be caused  at java.net.URI.create()
        }
        setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service

        execute(concurrent);
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
        this.currentRequest = new HttpHead(url.toNormalform(true));
        setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        execute(concurrent);
        return this.httpResponse;
    }

    /**
     * This method POSTs a page from the server.
     * to be used for streaming out
     * Please take care to call close()!
     *
     * @param uri the url to post
     * @param instream the input to post
     * @param length the contentlength
     * @throws IOException
     */
    /*
    public void POST(final String uri, final InputStream instream, final long length, final boolean concurrent) throws IOException {
        POST(new MultiProtocolURL(uri), instream, length, concurrent);
    }
     */

    /**
     * This method POSTs a page from the server.
     * to be used for streaming out
     * Please take care to call close()!
     *
     * @param url the url to post
     * @param instream the input to post
     * @param length the contentlength
     * @throws IOException
     */
    public void POST(final MultiProtocolURL url, final InputStream instream, final long length, final boolean concurrent) throws IOException {
        if (this.currentRequest != null) throw new IOException("Client is in use!");
        this.currentRequest = new HttpPost(url.toNormalform(true));
        String host = url.getHost();
        if (host == null) host = Domains.LOCALHOST;
        setHost(host); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        final NonClosingInputStreamEntity inputStreamEntity = new NonClosingInputStreamEntity(instream, length);
        // statistics
        this.upbytes = length;
        ((HttpPost) this.currentRequest).setEntity(inputStreamEntity);
        execute(concurrent);
    }

    /**
     * send data to the server named by uri
     *
     * @param uri the url to post
     * @param parts to post
     * @return content bytes
     * @throws IOException
     */
    /*
    public byte[] POSTbytes(final String uri, final Map<String, ContentBody> parts, final boolean usegzip, final boolean concurrent) throws IOException {
        final MultiProtocolURL url = new MultiProtocolURL(uri);
        return POSTbytes(url, url.getHost(), parts, usegzip, concurrent);
    }
     */

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
        return POSTbytes(url, vhost, post, null, null, usegzip, concurrent);
    }

    /**
     * Send data using HTTP POST method to the server named by vhost
     *
     * @param url address to request on the server
     * @param vhost name of the server at address which should respond. When null, localhost is assumed.
     * @param post data to send (name-value-pairs)
     * @param userName user name for HTTP authentication : only sent when requesting localhost
     * @param password encoded password for HTTP authentication : only sent when requesting localhost
     * @param usegzip if the body should be gzipped
     * @return response body
     * @throws IOException when an error occurred
     */
    public byte[] POSTbytes(final MultiProtocolURL url, final String vhost, final Map<String, ContentBody> post,
            final String userName, final String password, final boolean usegzip, final boolean concurrent) throws IOException {
        this.currentRequest = new HttpPost(url.toNormalform(true));
        final boolean localhost = Domains.isLocalhost(url.getHost());
        if (!localhost) setHost(url.getHost()); // overwrite resolved IP, needed for shared web hosting DO NOT REMOVE, see http://en.wikipedia.org/wiki/Shared_web_hosting_service
        if (vhost == null) setHost(Domains.LOCALHOST);

        final MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        for (final Entry<String,ContentBody> part : post.entrySet()) entityBuilder.addPart(part.getKey(), part.getValue());
        final HttpEntity multipartEntity = entityBuilder.build();
        // statistics
        this.upbytes = multipartEntity.getContentLength();

        if (usegzip) {
            ((HttpPost) this.currentRequest).setEntity(new GzipCompressingEntity(multipartEntity));
        } else {
            ((HttpPost) this.currentRequest).setEntity(multipartEntity);
        }

        if (!localhost || password == null) {
            return getContentBytes(Integer.MAX_VALUE, concurrent);
        }

        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope("localhost", url.getPort()),
                new UsernamePasswordCredentials(userName, password));

        try (final CloseableHttpClient httpclient = clientBuilder.setDefaultCredentialsProvider(credsProvider)
                .setDefaultAuthSchemeRegistry(AUTHSCHEMEREGISTRY).build()) {
            this.httpResponse = httpclient.execute(this.currentRequest);
            final HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) {
                if (getStatusCode() == HttpStatus.SC_OK) {
                    return getByteArray(httpEntity, Integer.MAX_VALUE);
                }
            }
        } finally {
            close();
        }
        return null;
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
    /*
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
     */

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
     * Get Mime type from the response header
     * @return mime type (trimmed and lower cased) or null when not specified
     */
    public String getMimeType() {
        String mimeType = null;
        if (this.httpResponse != null) {

            final Header contentType = this.httpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE);

            if (contentType != null) {

                mimeType = contentType.getValue();

                if (mimeType != null) {
                    mimeType = mimeType.trim().toLowerCase(Locale.ROOT);

                    final int pos = mimeType.indexOf(';');
                    if(pos >= 0) {
                        mimeType = mimeType.substring(0, pos);
                    }
                }
            }
        }
        return mimeType;
    }

    /**
     * Get character encoding from the response header
     *
     * @return the characters set name or null when not specified
     */
    public String getCharacterEncoding() {
        String charsetName = null;
        if (this.httpResponse != null) {

            final Header contentTypeHeader = this.httpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE);

            if (contentTypeHeader != null) {

                final String contentType = contentTypeHeader.getValue();

                if (contentType != null) {

                    final String[] parts = CommonPattern.SEMICOLON.split(contentType);
                    if (parts != null && parts.length > 1) {

                        for (int i = 1; i < parts.length; i++) {
                            final String param = parts[i].trim();
                            if (param.startsWith("charset=")) {
                                String charset = param.substring("charset=".length()).trim();
                                if (charset.length() > 0 && (charset.charAt(0) == '\"' || charset.charAt(0) == '\'')) {
                                    charset = charset.substring(1);
                                }
                                if (charset.endsWith("\"") || charset.endsWith("'")) {
                                    charset = charset.substring(0, charset.length() - 1);
                                }
                                charsetName = charset.trim();
                            }
                        }
                    }
                }
            }
        }

        return charsetName;
    }

    /**
     * This method gets direct access to the content-stream
     * Since this way is uncontrolled by the Client think of using 'writeTo' instead!
     * Please take care to call close()!
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
                close();
                throw e;
            }
        }
        return null;
    }

    /**
     * This method streams the content to the outputStream
     * Please take care to call close()!
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
            } finally {
                close();
            }
        }
    }

    /**
     * This method ensures correct close of client-connections
     * This method should be used after every use of GET or POST and writeTo or getContentstream!
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        try {
            if (this.httpResponse != null) {
                // Ensures that the entity content Stream is closed.
                EntityUtils.consumeQuietly(this.httpResponse.getEntity());
                this.httpResponse.close();
            }
            if (this.client != null) {
                this.client.close();
            }
        } finally {
            if (this.currentRequest != null) {
                ConnectionInfo.removeConnection(this.currentRequest.hashCode());
                this.currentRequest.abort();
                this.currentRequest = null;
            }
        }
    }

    private byte[] getContentBytes(final int maxBytes, final boolean concurrent) throws IOException {
        try {
            execute(concurrent);
            if (this.httpResponse == null) return null;
            // get the response body
            final HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) {
                if (getStatusCode() == HttpStatus.SC_OK) {
                    if (maxBytes >= 0 && httpEntity.getContentLength() > maxBytes) {
                        /* When anticipated content length is already known and exceed the specified limit :
                         * throw an exception and abort the connection, consistently with getByteArray() implementation
                         * Otherwise returning null and consuming fully the entity can be very long on large resources */
                        throw new IOException("Content to download exceed maximum value of " + Formatter.bytesToString(maxBytes));
                    }
                    return getByteArray(httpEntity, maxBytes);
                }
            }
        } finally {
            close();
        }
        return null;
    }

    private void execute(final boolean concurrent) throws IOException {
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(this.reqConfBuilder.build());
        if (this.host != null)
            context.setTargetHost(new HttpHost(this.host));

        setHeaders();
        // statistics
        storeConnectionInfo();
        // execute the method; some asserts confirm that that the request can be send with Content-Length and is therefore not terminated by EOF
        if (this.currentRequest instanceof HttpEntityEnclosingRequest) {
            final HttpEntityEnclosingRequest hrequest = (HttpEntityEnclosingRequest) this.currentRequest;
            final HttpEntity entity = hrequest.getEntity();
            assert entity != null;
            //assert !entity.isChunked();
            //assert entity.getContentLength() >= 0;
            assert !hrequest.expectContinue();
        }

        final String initialThreadName = Thread.currentThread().getName();
        final String uri = this.currentRequest.getURI().toString();
        Thread.currentThread().setName("HTTPClient-" + uri);
        final long time = System.currentTimeMillis();
        try {

            this.client = clientBuilder.build();
            if (concurrent) {
                final FutureTask<CloseableHttpResponse> t = new FutureTask<CloseableHttpResponse>(new Callable<CloseableHttpResponse>() {
                    @Override
                    public CloseableHttpResponse call() throws ClientProtocolException, IOException {
                        final CloseableHttpResponse response = HTTPClient.this.client.execute(HTTPClient.this.currentRequest, context);
                        return response;
                    }
                });
                executor.execute(t);
                try {
                    this.httpResponse = t.get(this.timeout, TimeUnit.MILLISECONDS);
                } catch (final ExecutionException e) {
                    throw e.getCause();
                } catch (final Throwable e) {}
                try {t.cancel(true);} catch (final Throwable e) {}
                if (this.httpResponse == null) {
                    throw new IOException("timout to client after " + this.timeout + "ms" + " for url " + uri);
                }
            } else {
                this.httpResponse = this.client.execute(this.currentRequest, context);
            }
            this.httpResponse.setHeader(HeaderFramework.RESPONSE_TIME_MILLIS, Long.toString(System.currentTimeMillis() - time));
        } catch (final Throwable e) {
            long runtime = System.currentTimeMillis() - time;
            close();
            throw new IOException("Client can't execute: "
                    + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage())
                    + ", timeout=" + this.timeout
                    + ", duration=" + Long.toString(runtime)
                    + ", concurrent=" + Boolean.toString(concurrent)
                    + ", url=" + uri);
        } finally {
            /* Restore the thread initial name */
            Thread.currentThread().setName(initialThreadName);
        }
    }

    /**
     * Return entity content loaded as a byte array
     * @param entity HTTP entity
     * @param maxBytes maximum bytes to read. -1 means no maximum limit.
     * @return content bytes or null when entity content is null.
     * @throws IOException when a read error occured or content length is over maxBytes
     */
    public static byte[] getByteArray(final HttpEntity entity, int maxBytes) throws IOException {
        try (final InputStream instream = entity.getContent()) {
            if (instream == null) {
                return null;
            }
            final long contentLength = entity.getContentLength();
            /*
             * When no maxBytes is specified, the default limit is
             * Integer.MAX_VALUE as a byte array size can not be over
             */
            if (maxBytes < 0) {
                maxBytes = Integer.MAX_VALUE;
            }
            /*
             * Content length may already be known now : check it before
             * downloading
             */
            if (contentLength > maxBytes) {
                throw new IOException("Content to download exceed maximum value of " + Formatter.bytesToString(maxBytes));
            }
            int initialSize = Math.min(maxBytes, (int) contentLength);
            /* ContentLenght may be negative because unknown for now */
            if (initialSize < 0) {
                initialSize = 4096;
            }
            final ByteArrayBuffer buffer = new ByteArrayBuffer(initialSize);
            final byte[] tmp = new byte[4096];
            int l = 0;
            /* Sum is a long to enable check against Integer.MAX_VALUE */
            long sum = 0;
            while ((l = instream.read(tmp)) != -1) {
                sum += l;
                /*
                 * Check total length while downloading as content length might
                 * not be known at beginning
                 */
                if (sum > maxBytes) {
                    throw new IOException("Download exceeded maximum value of " + Formatter.bytesToString(maxBytes));
                }
                buffer.append(tmp, 0, l);
            }
            return buffer.toByteArray();
        } catch (final OutOfMemoryError e) {
            throw new IOException(e.toString());
        } finally {
            // Ensures that the entity content is fully consumed and the content stream, if exists, is closed.
            EntityUtils.consume(entity);
        }
    }

    private void setHeaders() {
        if (this.headers != null) {
            for (final Entry<String, String> entry : this.headers) {
                this.currentRequest.setHeader(entry.getKey(),entry.getValue());
            }
        }
        if (this.host != null) this.currentRequest.setHeader(HTTP.TARGET_HOST, this.host);
        this.currentRequest.setHeader(HTTP.CONN_DIRECTIVE, "close"); // don't keep alive, prevent CLOSE_WAIT state
    }

    private void storeConnectionInfo() {
        final int port = this.currentRequest.getURI().getPort();
        final String thost = this.currentRequest.getURI().getHost();
        //assert thost != null : "uri = " + httpUriRequest.getURI().toString();
        ConnectionInfo.addConnection(new ConnectionInfo(
                this.currentRequest.getURI().getScheme(),
                port == -1 ? thost : thost + ":" + port,
                        this.currentRequest.getMethod() + " " + this.currentRequest.getURI().getPath(),
                        this.currentRequest.hashCode(),
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

        return new SSLConnectionSocketFactory(
                sslContext,
                new NoopHostnameVerifier()) {

            @Override
            protected void prepareSocket(final SSLSocket socket) throws IOException {
                if(!ENABLE_SNI_EXTENSION.get()) {
                    /* Set the SSLParameters server names to empty so we don't use SNI extension.
                     * See https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#ClientSNIExamples */
                    final SSLParameters sslParams = socket.getSSLParameters();
                    sslParams.setServerNames(Collections.emptyList());
                    socket.setSSLParameters(sslParams);
                }
            }
        };
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
            public long getKeepAliveDuration(final HttpResponse response, final HttpContext context) {
                final long keepAlive = super.getKeepAliveDuration(response, context);
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
//        try {
//            client.HEADResponse(url);
//        } catch (final IOException e) {
//            e.printStackTrace();
//        }
        for (final Header header: client.getHttpResponse().getAllHeaders()) {
            System.out.println("Header " + header.getName() + " : " + header.getValue());
//            for (HeaderElement element: header.getElements())
//                System.out.println("Element " + element.getName() + " : " + element.getValue());
        }
//        System.out.println(client.getHttpResponse().getLocale());
        System.out.println(client.getHttpResponse().getProtocolVersion());
        System.out.println(client.getHttpResponse().getStatusLine());
        // Post some
//        try {
//            System.out.println(UTF8.String(client.POSTbytes(url, newparts)));
//        } catch (final IOException e1) {
//            e1.printStackTrace();
//        }
        // Close out connection manager
        try {
            client.close();
            HTTPClient.closeConnectionManager();
        } catch (final InterruptedException | IOException e) {
                e.printStackTrace();
        }
    }

}
