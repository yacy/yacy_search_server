package net.yacy.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee8.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.security.Credential;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.http.servlets.MonitorFilter;
import net.yacy.search.SwitchboardConstants;

/**
 * Focused tests for the consolidated {@link Jetty12HttpServer} adapter and its
 * nested default-runtime handlers. The transparent-proxy chain is covered by
 * {@link Jetty12ProxyChainTest}.
 */
@RunWith(Enclosed.class)
public class Jetty12HttpServerTest {

    /** Lifecycle, connector and compression contract of the bootstrap. */
    public static class ServerLifecycleTest {

        @Test
        public void startsAndStopsHttpConnectorOnEphemeralPort() throws Exception {
            final Logger jettyLogger = Logger.getLogger("org.eclipse.jetty.server.Server");
            final AtomicBoolean receivedJettyLog = new AtomicBoolean();
            final java.util.logging.Handler capture = new java.util.logging.Handler() {
                @Override
                public void publish(final LogRecord record) {
                    receivedJettyLog.set(true);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
            capture.setLevel(Level.ALL);
            jettyLogger.addHandler(capture);
            final Jetty12HttpServer server = new Jetty12HttpServer(
                    0, "127.0.0.1", 1, null, -1);
            assertFalse(server.withSSL());
            assertEquals(-1, server.getSslPort());
            assertTrue(server.getVersion().startsWith("Jetty 12.0.37"));

            try {
                server.startupServer();
                assertTrue(server.getHttpPort() > 0);
                assertTrue(server.getServerThreads() >= 0);
            } finally {
                server.stop();
                jettyLogger.removeHandler(capture);
            }
            assertTrue("Jetty 12 lifecycle log did not reach JUL", receivedJettyLog.get());
        }

        @Test
        public void configuresHttpsConnectorWhenSslContextIsAvailable() throws Exception {
            final Jetty12HttpServer server = new Jetty12HttpServer(
                    0, "127.0.0.1", 1, SSLContext.getDefault(), 0);
            assertTrue(server.withSSL());
            try {
                server.startupServer();
                assertTrue(server.getHttpPort() > 0);
                assertTrue(server.getSslPort() > 0);
            } finally {
                server.stop();
            }
        }

        @Test
        public void acceptsConfiguredCertificatesWithoutJetty12SniHostRejection() {
            final SecureRequestCustomizer secureRequests =
                    Jetty12HttpServer.createSecureRequestCustomizer();
            assertFalse(secureRequests.isSniHostCheck());
            assertFalse(secureRequests.isSniRequired());
        }

        @Test
        public void tracksForwardedClientOnlyForTrustedReverseProxy() {
            final HttpServletRequest trustedProxyRequest = requestWithRemoteAddress(
                    "127.0.0.1", "198.51.100.23");
            assertEquals("198.51.100.23",
                    Jetty12HttpServer.AdminSecurityHandler.resolveTrustedClientIp(
                            trustedProxyRequest,
                            SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED_DEFAULT));

            final HttpServletRequest spoofedDirectRequest = requestWithRemoteAddress(
                    "203.0.113.10", "127.0.0.1");
            assertEquals("203.0.113.10",
                    Jetty12HttpServer.AdminSecurityHandler.resolveTrustedClientIp(
                            spoofedDirectRequest,
                            SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED_DEFAULT));

            final HttpServletRequest invalidForwardedAddress = requestWithRemoteAddress(
                    "127.0.0.1", "unknown, 198.51.100.23");
            assertEquals("127.0.0.1",
                    Jetty12HttpServer.AdminSecurityHandler.resolveTrustedClientIp(
                            invalidForwardedAddress,
                            SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED_DEFAULT));

            final HttpServletRequest trustedProxyIpv6Request = requestWithRemoteAddress(
                    "::1", "2001:db8::23");
            assertEquals("2001:db8::23",
                    Jetty12HttpServer.AdminSecurityHandler.resolveTrustedClientIp(
                            trustedProxyIpv6Request,
                            SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED_DEFAULT));
        }

        @Test
        public void exposesOnlyServerValidatedProxyAddressToRequestHeader() {
            final HttpServletRequest trustedProxyRequest = requestWithRemoteAddress(
                    "127.0.0.1", "198.51.100.23");
            trustedProxyRequest.setAttribute(RequestHeader.EFFECTIVE_CLIENT_IP_ATTRIBUTE,
                    Jetty12HttpServer.AdminSecurityHandler.resolveTrustedClientIp(
                            trustedProxyRequest,
                            SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED_DEFAULT));

            final RequestHeader trustedProxyHeader = new RequestHeader(trustedProxyRequest);
            assertEquals("198.51.100.23", trustedProxyHeader.getRemoteAddr());
            assertEquals("127.0.0.1", trustedProxyHeader.getRemoteSocketAddr());

            final HttpServletRequest spoofedDirectRequest = requestWithRemoteAddress(
                    "203.0.113.10", "127.0.0.1");
            spoofedDirectRequest.setAttribute(RequestHeader.EFFECTIVE_CLIENT_IP_ATTRIBUTE,
                    Jetty12HttpServer.AdminSecurityHandler.resolveTrustedClientIp(
                            spoofedDirectRequest,
                            SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED_DEFAULT));

            final RequestHeader spoofedDirectHeader = new RequestHeader(spoofedDirectRequest);
            assertEquals("203.0.113.10", spoofedDirectHeader.getRemoteAddr());
            assertEquals("203.0.113.10", spoofedDirectHeader.getRemoteSocketAddr());
        }

        private static HttpServletRequest requestWithRemoteAddress(final String socketRemoteIp,
                final String forwardedRemoteIp) {
            final Map<String, Object> attributes = new HashMap<String, Object>();
            return (HttpServletRequest) Proxy.newProxyInstance(
                    Jetty12HttpServerTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    (proxy, method, args) -> {
                        if ("getRemoteAddr".equals(method.getName())) {
                            return socketRemoteIp;
                        }
                        if ("getRemoteHost".equals(method.getName())) {
                            return socketRemoteIp;
                        }
                        if ("getHeader".equals(method.getName())
                                && RequestHeader.X_Real_IP.equals(args[0])) {
                            return forwardedRemoteIp;
                        }
                        if ("setAttribute".equals(method.getName())) {
                            attributes.put((String) args[0], args[1]);
                            return null;
                        }
                        if ("getAttribute".equals(method.getName())) {
                            return attributes.get(args[0]);
                        }
                        return null;
                    });
        }

        @Test
        public void preservesJetty9SvgCompressionContract() {
            final GzipHandler compression = Jetty12HttpServer.createGzipHandler(null, true);
            assertTrue(compression.isMimeTypeDeflatable("image/svg+xml"));
            assertFalse(compression.isMimeTypeDeflatable("image/png"));
            assertEquals(HttpServerBootstrapConfig.REQUEST_INFLATE_BUFFER_SIZE,
                    compression.getInflateBufferSize());
            assertTrue(java.util.Arrays.asList(compression.getIncludedMethods()).contains("GET"));
            assertTrue(java.util.Arrays.asList(compression.getIncludedInflationPaths()).contains("/*"));
            assertTrue(java.util.Arrays.asList(compression.getExcludedInflationPaths()).contains("*.svgz"));
        }

        @Test
        public void canDisableGzipResponsesWithoutDisablingRequestInflation() {
            final GzipHandler compression = Jetty12HttpServer.createGzipHandler(null, false);
            assertTrue(java.util.Arrays.asList(compression.getExcludedMethods()).contains("GET"));
            assertEquals(HttpServerBootstrapConfig.REQUEST_INFLATE_BUFFER_SIZE,
                    compression.getInflateBufferSize());
        }

        @Test
        public void preservesJetty9WebApplicationFormContentLimit() throws Exception {
            final Server server = new Server();
            final ServerConnector connector = new ServerConnector(server, 1, 1);
            connector.setHost("127.0.0.1");
            connector.setPort(0);
            server.addConnector(connector);

            final ServletContextHandler webApp = new ServletContextHandler();
            webApp.setContextPath("/");
            webApp.setMaxFormContentSize(HttpServerBootstrapConfig.MAX_FORM_CONTENT_SIZE);
            webApp.addServlet(new ServletHolder(new HttpServlet() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void doPost(final HttpServletRequest request,
                        final HttpServletResponse response) throws java.io.IOException {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().print(request.getParameter("value").length());
                }
            }), "/*");
            server.setHandler(webApp);

            try {
                server.start();
                final String accepted = postForm(connector.getLocalPort(), "value=" + "a".repeat(1024));
                assertTrue(accepted, accepted.startsWith("HTTP/1.1 200"));
                assertTrue(accepted, accepted.contains("1024"));

                final String rejected = postForm(connector.getLocalPort(),
                        "value=" + "a".repeat(HttpServerBootstrapConfig.MAX_FORM_CONTENT_SIZE));
                assertTrue(rejected, rejected.startsWith("HTTP/1.1 400"));
            } finally {
                server.stop();
                server.join();
            }
        }

        private static String postForm(final int port, final String body) throws Exception {
            final byte[] content = body.getBytes(StandardCharsets.US_ASCII);
            try (Socket client = new Socket("127.0.0.1", port)) {
                final OutputStream output = client.getOutputStream();
                output.write(("POST / HTTP/1.1\r\nHost: 127.0.0.1:" + port
                        + "\r\nContent-Type: application/x-www-form-urlencoded"
                        + "\r\nContent-Length: " + content.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(content);
                output.flush();
                return readAll(client.getInputStream());
            }
        }
    }

    /** Regression coverage for the incoming-request accounting leak reported in #800 and #808. */
    public static class ConnectionTrackingTest {

        private int previousServerMaxCount;

        @Before
        public void resetConnectionTracking() {
            this.previousServerMaxCount = ConnectionInfo.getServerMaxcount();
            ConnectionInfo.getServerConnections().clear();
            ConnectionInfo.setServerMaxcount(100);
        }

        @After
        public void restoreConnectionTracking() {
            ConnectionInfo.getServerConnections().clear();
            ConnectionInfo.setServerMaxcount(this.previousServerMaxCount);
        }

        @Test
        public void releasesTrackingAfterRepeatedSynchronousRequests() throws Exception {
            final Jetty12HttpServer httpServer = trackedHttpServer();
            try {
                httpServer.startupServer();
                for (int request = 0; request < 3; request++) {
                    final String response = closeRequest(httpServer.getHttpPort(), "/request-" + request);
                    assertTrue(response, response.startsWith("HTTP/1.1 204"));
                    assertTrackingReturnsToZero();
                }
            } finally {
                httpServer.stop();
            }
        }

        @Test
        public void releasesTrackingWhileKeepAliveConnectionRemainsOpen() throws Exception {
            final Jetty12HttpServer httpServer = trackedHttpServer();
            try {
                httpServer.startupServer();
                try (Socket client = new Socket("127.0.0.1", httpServer.getHttpPort())) {
                    final OutputStream output = client.getOutputStream();
                    output.write(("GET /first HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                            + "Connection: keep-alive\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    assertTrue(readHeaders(client.getInputStream()).startsWith("HTTP/1.1 204"));
                    assertTrackingReturnsToZero();

                    output.write(("GET /second HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    assertTrue(readAll(client.getInputStream()).startsWith("HTTP/1.1 204"));
                }
                assertTrackingReturnsToZero();
            } finally {
                httpServer.stop();
            }
        }

        @Test
        public void releasesTrackingWhenFilterChainThrows() throws Exception {
            final MonitorFilter filter = new MonitorFilter();
            try {
                filter.doFilter(request("198.51.100.10", 41000), response(new AtomicInteger()),
                        (request, response) -> {
                            throw new ServletException("expected test failure");
                        });
                org.junit.Assert.fail("Expected the filter chain failure");
            } catch (final ServletException expected) {
                assertEquals("expected test failure", expected.getMessage());
            }
            assertEquals("Failed requests must not remain active", 0, ConnectionInfo.getServerCount());
        }

        @Test
        public void admitsRequestThatReachesConfiguredLimit() throws Exception {
            ConnectionInfo.setServerMaxcount(1);
            final AtomicInteger status = new AtomicInteger();
            final AtomicBoolean invoked = new AtomicBoolean();

            new MonitorFilter().doFilter(request("198.51.100.11", 41001), response(status),
                    (request, response) -> {
                        invoked.set(true);
                        assertEquals("The admitted request must occupy the available slot",
                                1, ConnectionInfo.getServerCount());
                    });

            assertEquals("A request filling the final slot must not be rejected", 0, status.get());
            assertTrue("A request filling the final slot must invoke the filter chain", invoked.get());
            assertEquals("Completed requests must not remain active", 0, ConnectionInfo.getServerCount());
        }

        @Test
        public void enforcesServerConnectionLimitAtomically() throws Exception {
            ConnectionInfo.setServerMaxcount(1);
            final CountDownLatch ready = new CountDownLatch(2);
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch enteredFilterChain = new CountDownLatch(1);
            final CountDownLatch rejectedRequestFinished = new CountDownLatch(1);
            final CountDownLatch releaseFilterChain = new CountDownLatch(1);
            final AtomicInteger admitted = new AtomicInteger();
            final AtomicInteger rejected = new AtomicInteger();
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final AtomicInteger nextPort = new AtomicInteger(41010);
            final Runnable requestTask = () -> {
                ready.countDown();
                try {
                    start.await();
                    final AtomicInteger status = new AtomicInteger();
                    new MonitorFilter().doFilter(
                            request("198.51.100.12", nextPort.getAndIncrement()), response(status),
                            (request, response) -> {
                                admitted.incrementAndGet();
                                enteredFilterChain.countDown();
                                try {
                                    releaseFilterChain.await();
                                } catch (final InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new ServletException(interrupted);
                                }
                            });
                    if (status.get() == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
                        rejected.incrementAndGet();
                        rejectedRequestFinished.countDown();
                    }
                } catch (final Throwable error) {
                    failure.compareAndSet(null, error);
                }
            };
            final Thread first = new Thread(requestTask, "connection-limit-first");
            final Thread second = new Thread(requestTask, "connection-limit-second");

            first.start();
            second.start();
            try {
                assertTrue("Concurrent requests were not ready", ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                assertTrue("No request was admitted", enteredFilterChain.await(5, TimeUnit.SECONDS));
                assertTrue("Excess request was not rejected",
                        rejectedRequestFinished.await(5, TimeUnit.SECONDS));
                assertEquals("Exactly one request may be admitted", 1, admitted.get());
                assertEquals("Exactly one excess request must be rejected", 1, rejected.get());
                assertEquals("The active request count must not exceed the limit",
                        1, ConnectionInfo.getServerCount());
            } finally {
                start.countDown();
                releaseFilterChain.countDown();
                first.join(5_000L);
                second.join(5_000L);
            }

            assertFalse("First request thread did not stop", first.isAlive());
            assertFalse("Second request thread did not stop", second.isAlive());
            if (failure.get() != null) {
                throw new AssertionError("Concurrent limit enforcement failed", failure.get());
            }
            assertEquals("Completed requests must not remain active",
                    0, ConnectionInfo.getServerCount());
        }

        @Test
        public void releasesTrackingWhenAsynchronousRequestCompletes() throws Exception {
            final AtomicReference<AsyncListener> registeredListener = new AtomicReference<>();
            final AsyncContext asyncContext = (AsyncContext) Proxy.newProxyInstance(
                    Jetty12HttpServerTest.class.getClassLoader(),
                    new Class<?>[]{AsyncContext.class},
                    (proxy, method, args) -> {
                        if ("addListener".equals(method.getName())) {
                            registeredListener.set((AsyncListener) args[0]);
                        }
                        return defaultValue(method.getReturnType());
                    });

            new MonitorFilter().doFilter(request("198.51.100.12", 41002, asyncContext),
                    response(new AtomicInteger()), (request, response) -> {
                    });

            assertEquals("Async requests must remain active until completion",
                    1, ConnectionInfo.getServerCount());
            assertNotNull(registeredListener.get());
            registeredListener.get().onComplete(null);
            assertEquals("Completed async requests must not remain active",
                    0, ConnectionInfo.getServerCount());
        }

        @Test
        public void tracksConcurrentRequestsFromSameRemoteConnectionIndependently() throws Exception {
            final CountDownLatch enteredFilterChains = new CountDownLatch(2);
            final CountDownLatch releaseFilterChains = new CountDownLatch(1);
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final Runnable requestTask = () -> {
                try {
                    new MonitorFilter().doFilter(request("127.0.0.1", 41003),
                            response(new AtomicInteger()), (servletRequest, servletResponse) -> {
                                enteredFilterChains.countDown();
                                try {
                                    releaseFilterChains.await();
                                } catch (final InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new ServletException(interrupted);
                                }
                            });
                } catch (final Throwable error) {
                    failure.compareAndSet(null, error);
                }
            };
            final Thread first = new Thread(requestTask, "connection-tracking-first");
            final Thread second = new Thread(requestTask, "connection-tracking-second");

            first.start();
            second.start();
            try {
                assertTrue("Both requests did not enter their filter chains",
                        enteredFilterChains.await(5, TimeUnit.SECONDS));
                assertEquals("Each active request needs its own tracking entry",
                        2, ConnectionInfo.getServerCount());
            } finally {
                releaseFilterChains.countDown();
                first.join(5_000L);
                second.join(5_000L);
            }

            assertFalse("First request thread did not stop", first.isAlive());
            assertFalse("Second request thread did not stop", second.isAlive());
            if (failure.get() != null) {
                throw new AssertionError("Concurrent tracked request failed", failure.get());
            }
            assertEquals("Completed concurrent requests must not remain active",
                    0, ConnectionInfo.getServerCount());
        }

        private static Jetty12HttpServer trackedHttpServer() throws Exception {
            final Jetty12HttpServer httpServer = new Jetty12HttpServer(0, "127.0.0.1", 1, null, -1);
            final Field serverField = Jetty12HttpServer.class.getDeclaredField("server");
            serverField.setAccessible(true);
            final Server server = (Server) serverField.get(httpServer);

            final ServletContextHandler webApp = new ServletContextHandler();
            webApp.setContextPath("/");
            webApp.addFilter(new FilterHolder(MonitorFilter.class), "/*",
                    EnumSet.of(DispatcherType.REQUEST));
            webApp.addServlet(new ServletHolder(new HttpServlet() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void doGet(final HttpServletRequest request,
                        final HttpServletResponse response) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            }), "/*");
            server.setHandler(webApp);
            return httpServer;
        }

        private static String closeRequest(final int port, final String path) throws Exception {
            try (Socket client = new Socket("127.0.0.1", port)) {
                final OutputStream output = client.getOutputStream();
                output.write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.flush();
                return readAll(client.getInputStream());
            }
        }

        private static void assertTrackingReturnsToZero() throws InterruptedException {
            final long deadline = System.nanoTime() + 2_000_000_000L;
            while (ConnectionInfo.getServerCount() != 0 && System.nanoTime() < deadline) {
                Thread.sleep(10L);
            }
            assertEquals("Completed requests must not remain in the active server count",
                    0, ConnectionInfo.getServerCount());
        }

        private static HttpServletRequest request(final String remoteAddress, final int remotePort) {
            return request(remoteAddress, remotePort, null);
        }

        private static HttpServletRequest request(final String remoteAddress, final int remotePort,
                final AsyncContext asyncContext) {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    Jetty12HttpServerTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getScheme" -> "http";
                        case "getRemoteAddr", "getRemoteHost" -> remoteAddress;
                        case "getRemotePort" -> remotePort;
                        case "getMethod" -> "GET";
                        case "getRequestURI" -> "/regression";
                        case "isAsyncStarted" -> asyncContext != null;
                        case "getAsyncContext" -> asyncContext;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static HttpServletResponse response(final AtomicInteger status) {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    Jetty12HttpServerTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        if ("sendError".equals(method.getName()) || "setStatus".equals(method.getName())) {
                            status.set((Integer) args[0]);
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(final Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
            if (type == char.class) return '\0';
            return null;
        }
    }

    /** Regression coverage for stale connection accounting cleanup. */
    public static class ConnectionInfoCleanupTest {

        private static final long STALE_INIT_TIME =
                System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(31);

        @Before
        public void resetConnectionInfo() {
            ConnectionInfo.getAllConnections().clear();
            ConnectionInfo.getServerConnections().clear();
        }

        @After
        public void clearConnectionInfo() {
            ConnectionInfo.getAllConnections().clear();
            ConnectionInfo.getServerConnections().clear();
        }

        @Test
        public void clientCleanupRemovesEveryStaleEntryFromClientPoolOnly() {
            ConnectionInfo.addConnection(connection(1001L, STALE_INIT_TIME));
            ConnectionInfo.addConnection(connection(1002L, STALE_INIT_TIME));
            ConnectionInfo.addConnection(connection(1003L, System.currentTimeMillis()));
            ConnectionInfo.addServerConnection(connection(2001L, STALE_INIT_TIME));

            assertEquals("All stale client entries must be removed",
                    2, ConnectionInfo.cleanUpClientConnections());
            assertEquals("The active client entry must remain", 1, ConnectionInfo.getCount());
            assertEquals("Client cleanup must not alter server entries",
                    1, ConnectionInfo.getServerCount());
        }

        @Test
        public void serverCleanupRemovesEveryStaleEntryFromServerPoolOnly() {
            ConnectionInfo.addServerConnection(connection(2001L, STALE_INIT_TIME));
            ConnectionInfo.addServerConnection(connection(2002L, STALE_INIT_TIME));
            ConnectionInfo.addServerConnection(connection(2003L, System.currentTimeMillis()));
            ConnectionInfo.addConnection(connection(1001L, STALE_INIT_TIME));

            assertEquals("All stale server entries must be removed",
                    2, ConnectionInfo.cleanUpServerConnections());
            assertEquals("The active server entry must remain", 1, ConnectionInfo.getServerCount());
            assertEquals("Server cleanup must not alter client entries",
                    1, ConnectionInfo.getCount());
        }

        private static ConnectionInfo connection(final long id, final long initTime) {
            return new ConnectionInfo("http", "example.test", "GET /", id, initTime, 0L);
        }
    }

    /** Address/path rule adaptation to Jetty 12's native access handler. */
    public static class AccessRulesTest {

        private static final Handler NEXT = new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) {
                return false;
            }
        };

        @Test
        public void leavesUnrestrictedPipelineUntouched() {
            assertSame(NEXT, Jetty12HttpServer.AccessRules.wrap(NEXT, "*"));
        }

        @Test
        public void usesNativeJetty12HandlerForAddressAndPathRule() {
            final Handler wrapped = Jetty12HttpServer.AccessRules.wrap(NEXT, "192.0.2.0/24|/api/*");
            assertTrue(wrapped instanceof InetAccessHandler);
            assertSame(NEXT, ((InetAccessHandler) wrapped).getHandler());
        }

        @Test
        public void ignoresEmptyRuleList() {
            assertSame(NEXT, Jetty12HttpServer.AccessRules.wrap(NEXT, " , "));
        }

        @Test
        public void validatesConfiguredAddressAndPathPattern() {
            Jetty12HttpServer.AccessRules.checkPattern("192.0.2.0/24|/api/*");
        }

        @Test(expected = IllegalArgumentException.class)
        public void rejectsMalformedConfiguredAddressPattern() {
            Jetty12HttpServer.AccessRules.checkPattern("not an address|/api/*");
        }
    }

    /** Administrator credential verification for BASIC and DIGEST. */
    public static class AdminCredentialTest {

        @Test
        public void verifiesBasicPasswordAgainstRealmHash() {
            final String hash = "MD5:" + Digest.encodeMD5Hex("admin:YaCy:test-password");
            final Jetty12HttpServer.AdminCredential credential = new Jetty12HttpServer.AdminCredential(
                    "admin", hash, "YaCy", "admin");
            assertTrue(credential.check("test-password"));
            assertFalse(credential.check("wrong-password"));
        }

        @Test
        public void acceptsConfiguredHashAsPasswordOnlyFromLocalhost() {
            final String hash = "MD5:" + Digest.encodeMD5Hex("admin:YaCy:test-password");
            final Jetty12HttpServer.AdminCredential remoteCredential = new Jetty12HttpServer.AdminCredential(
                    "admin", hash, "YaCy", "admin", () -> false);
            final Jetty12HttpServer.AdminCredential localCredential = new Jetty12HttpServer.AdminCredential(
                    "admin", hash, "YaCy", "admin", () -> true);
            assertFalse(remoteCredential.check(hash));
            assertTrue(localCredential.check(hash));
        }

        @Test
        public void acceptsJettyDigestCredentialForConfiguredDigest() {
            final String hash = "MD5:" + Digest.encodeMD5Hex("admin:YaCy:test-password");
            final Jetty12HttpServer.AdminCredential credential = new Jetty12HttpServer.AdminCredential(
                    "admin", hash, "YaCy", "admin");
            assertTrue(credential.check(Credential.getCredential(hash)));
        }

        @Test
        public void digestChallengeUsesTheAlgorithmOfYacysStoredHa1() {
            final DigestAuthenticator authenticator =
                    Jetty12HttpServer.AdminSecurityHandler.createDigestAuthenticator();
            assertEquals("MD5", authenticator.getAlgorithm());
        }
    }

    /** Administrator login-service cache and credential reload contract. */
    public static class AdminLoginServiceTest {

        @Test
        public void reloadsChangedAdministratorCredentialAndRole() throws Exception {
            final AtomicReference<Jetty12HttpServer.AdminLoginService.AdminCredentialConfig> configured =
                    new AtomicReference<>(credentials("first-password"));
            final Jetty12HttpServer.AdminLoginService service =
                    new Jetty12HttpServer.AdminLoginService(configured::get);
            service.setName("YaCy");
            service.start();
            try {
                final UserIdentity first = service.login("admin", "first-password", null, ignored -> null);
                assertNotNull(first);
                assertTrue(first.isUserInRole(SwitchboardConstants.ADMIN_ACCOUNT_ROLE));
                assertTrue(service.removeCachedUser("admin"));
                assertFalse(service.removeCachedUser("admin"));

                configured.set(credentials("second-password"));
                service.reloadUser("admin");
                assertNull(service.login("admin", "first-password", null, ignored -> null));
                assertNotNull(service.login("admin", "second-password", null, ignored -> null));
            } finally {
                service.stop();
            }
        }

        private static Jetty12HttpServer.AdminLoginService.AdminCredentialConfig credentials(
                final String password) {
            return new Jetty12HttpServer.AdminLoginService.AdminCredentialConfig("admin",
                    "MD5:" + Digest.encodeMD5Hex("admin:YaCy:" + password), "YaCy");
        }
    }

    /** Exception barrier around the complete request pipeline. */
    public static class CrashProtectionHandlerTest {

        @Test
        public void recognizesClientDisconnectFailuresThroughCauseChain() {
            assertTrue(Jetty12HttpServer.CrashProtectionHandler.isClientDisconnect(
                    new IOException("Broken pipe")));
            assertTrue(Jetty12HttpServer.CrashProtectionHandler.isClientDisconnect(
                    new SocketException("Connection reset")));
            assertTrue(Jetty12HttpServer.CrashProtectionHandler.isClientDisconnect(
                    new IllegalStateException("wrapped", new EofException("closed"))));
            assertTrue(Jetty12HttpServer.CrashProtectionHandler.isClientDisconnect(
                    new ClosedChannelException()));
        }

        @Test
        public void doesNotHideUnrelatedIoOrApplicationFailures() {
            assertFalse(Jetty12HttpServer.CrashProtectionHandler.isClientDisconnect(
                    new IOException("disk failure")));
            assertFalse(Jetty12HttpServer.CrashProtectionHandler.isClientDisconnect(
                    new IllegalStateException("COMMITTED")));
        }

        @Test
        public void convertsSynchronousHandlerFailureTo500Response() throws Exception {
            final Server server = new Server();
            final ServerConnector connector = new ServerConnector(server, 1, 1);
            connector.setHost("127.0.0.1");
            connector.setPort(0);
            server.addConnector(connector);
            final Handler failing = new Handler.Abstract() {
                @Override
                public boolean handle(final Request request, final Response response,
                        final Callback callback) {
                    throw new IllegalStateException("expected test failure");
                }
            };
            server.setHandler(new Jetty12HttpServer.CrashProtectionHandler(failing));
            try {
                server.start();
                final java.net.http.HttpResponse<String> response =
                        java.net.http.HttpClient.newHttpClient().send(
                                java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                                        "http://127.0.0.1:" + connector.getLocalPort() + "/failure"))
                                        .GET().build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(500, response.statusCode());
            } finally {
                server.stop();
                server.join();
            }
        }
    }

    /** CONNECT rejection while normal traffic passes when the proxy is disabled. */
    public static class DisabledProxyHandlerTest {

        @Test
        public void passesReverseProxyHostToWebApplication() throws Exception {
            final Handler handler = new Jetty12HttpServer.DisabledProxyHandler(fallback());
            final String response = request(handler,
                    "GET /resource HTTP/1.1\r\n"
                            + "Host: search.example.org\r\nConnection: close\r\n\r\n");
            assertTrue(response, response.startsWith("HTTP/1.1 204"));
        }

        @Test
        public void rejectsConnectWith403() throws Exception {
            final Handler handler = new Jetty12HttpServer.DisabledProxyHandler(fallback());
            final String response = requestHeaders(handler,
                    "CONNECT example.invalid:443 HTTP/1.1\r\n"
                            + "Host: example.invalid:443\r\nConnection: close\r\n\r\n");
            assertTrue(response, response.startsWith("HTTP/1.1 403"));
            assertTrue(response, response.contains(
                    "X-YaCy-Proxy-Error: Transparent proxy is disabled"));
        }

        @Test
        public void passesDirectPeerRequestToWebApplication() throws Exception {
            final Handler handler = new Jetty12HttpServer.DisabledProxyHandler(fallback());
            final String response = request(handler,
                    "GET /ConfigBasic.html HTTP/1.1\r\n"
                            + "Host: 127.0.0.1:%d\r\nConnection: close\r\n\r\n");
            assertTrue(response, response.startsWith("HTTP/1.1 204"));
        }

        private static Handler fallback() {
            return new Handler.Abstract() {
                @Override
                public boolean handle(final Request request, final Response response,
                        final Callback callback) {
                    response.setStatus(204);
                    callback.succeeded();
                    return true;
                }
            };
        }

        private static String request(final Handler handler, final String requestTemplate)
                throws Exception {
            return request(handler, requestTemplate, false);
        }

        private static String requestHeaders(final Handler handler, final String requestTemplate)
                throws Exception {
            return request(handler, requestTemplate, true);
        }

        private static String request(final Handler handler, final String requestTemplate,
                final boolean headersOnly) throws Exception {
            final Server server = new Server();
            final ServerConnector connector = new ServerConnector(server, 1, 1);
            connector.setHost("127.0.0.1");
            connector.setPort(0);
            server.addConnector(connector);
            server.setHandler(handler);
            try {
                server.start();
                try (Socket client = new Socket("127.0.0.1", connector.getLocalPort())) {
                    final OutputStream output = client.getOutputStream();
                    output.write(String.format(requestTemplate, connector.getLocalPort())
                            .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    return headersOnly ? readHeaders(client.getInputStream())
                            : readAll(client.getInputStream());
                }
            } finally {
                server.stop();
                server.join();
            }
        }

        private static String readHeaders(final InputStream input) throws Exception {
            final ByteArrayOutputStream result = new ByteArrayOutputStream();
            int state = 0;
            int value;
            while ((value = input.read()) >= 0) {
                result.write(value);
                state = switch (state) {
                    case 0 -> value == '\r' ? 1 : 0;
                    case 1 -> value == '\n' ? 2 : 0;
                    case 2 -> value == '\r' ? 3 : 0;
                    case 3 -> value == '\n' ? 4 : 0;
                    default -> state;
                };
                if (state == 4) {
                    break;
                }
            }
            return result.toString(StandardCharsets.ISO_8859_1);
        }
    }

    static String readAll(final InputStream input) throws Exception {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        input.transferTo(result);
        return result.toString(StandardCharsets.ISO_8859_1);
    }

    static String readHeaders(final InputStream input) throws Exception {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        int state = 0;
        int value;
        while ((value = input.read()) >= 0) {
            result.write(value);
            state = switch (state) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> state;
            };
            if (state == 4) break;
        }
        return result.toString(StandardCharsets.ISO_8859_1);
    }
}
