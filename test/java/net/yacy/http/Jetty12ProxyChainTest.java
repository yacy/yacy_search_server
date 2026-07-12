package net.yacy.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.sun.net.httpserver.HttpServer;

import net.yacy.server.http.AlternativeDomainNames;

/**
 * Focused tests for the consolidated {@link Jetty12ProxyChain} transparent-proxy
 * handlers. The default-runtime adapter is covered by {@link Jetty12HttpServerTest}.
 */
@RunWith(Enclosed.class)
public class Jetty12ProxyChainTest {

    /** Single policy gate in front of the cache/forward sequence. */
    public static class ProxyPolicyHandlerTest {

        @Test
        public void numericLoopbackIsRecognizedAsThisHost() throws Exception {
            assertDirectPeerRequest("/api/version.xml", true);
        }

        @Test
        public void numericLoopbackOnAnotherPortRemainsAProxyTarget() throws Exception {
            assertDirectPeerRequest("http://127.0.0.1:9/resource", false);
        }

        @Test
        public void unresolvedAbsoluteHostRemainsAProxyTarget() throws Exception {
            assertDirectPeerRequest("http://example.invalid/resource", false);
        }

        @Test
        public void directRequestFallsThroughWithoutPermissionOrProxyPipeline() throws Exception {
            final AtomicInteger classification = new AtomicInteger();
            final AtomicInteger permission = new AtomicInteger();
            final AtomicInteger proxyCalls = new AtomicInteger();
            final Handler proxy = responseHandler(202, proxyCalls);
            final Handler gate = new Jetty12ProxyChain.ProxyPolicyHandler(proxy, request -> {
                classification.incrementAndGet();
                return false;
            }, request -> {
                permission.incrementAndGet();
                return null;
            });
            final String response = request(new Handler.Sequence(gate, responseHandler(204, null)),
                    "GET /ConfigBasic.html HTTP/1.1\r\nHost: 127.0.0.1:%d\r\n"
                            + "Connection: close\r\n\r\n");
            assertTrue(response, response.startsWith("HTTP/1.1 204"));
            assertEquals(1, classification.get());
            assertEquals(0, permission.get());
            assertEquals(0, proxyCalls.get());
        }

        @Test
        public void rejectedRequestNeverReachesProxyPipeline() throws Exception {
            final AtomicInteger permission = new AtomicInteger();
            final AtomicInteger proxyCalls = new AtomicInteger();
            final Handler gate = new Jetty12ProxyChain.ProxyPolicyHandler(
                    responseHandler(202, proxyCalls),
                    request -> true, request -> {
                        permission.incrementAndGet();
                        return "proxy use not granted";
                    });
            final String response = request(gate,
                    "GET http://example.invalid/resource HTTP/1.1\r\n"
                            + "Host: example.invalid\r\nConnection: close\r\n\r\n");
            assertTrue(response, response.startsWith("HTTP/1.1 403"));
            assertEquals(1, permission.get());
            assertEquals(0, proxyCalls.get());
        }

        @Test
        public void cacheHitEvaluatesPolicyOnceAndSkipsForwarding() throws Exception {
            final AtomicInteger classification = new AtomicInteger();
            final AtomicInteger permission = new AtomicInteger();
            final AtomicInteger forwarding = new AtomicInteger();
            final HttpFields headers = HttpFields.build().put("Content-Type", "text/plain").asImmutable();
            final Handler cache = new Jetty12ProxyChain.ProxyCacheHandler(request ->
                    new Jetty12ProxyChain.ProxyCacheHandler.CachedResponse(headers,
                            "cached".getBytes(StandardCharsets.UTF_8)));
            final Handler proxyPipeline = new Handler.Sequence(cache, responseHandler(202, forwarding));
            final Handler gate = countingGate(proxyPipeline, classification, permission);

            final String response = proxyRequest(gate);
            assertTrue(response, response.startsWith("HTTP/1.1 203"));
            assertEquals(1, classification.get());
            assertEquals(1, permission.get());
            assertEquals(0, forwarding.get());
        }

        @Test
        public void cacheMissEvaluatesPolicyOnceBeforeForwarding() throws Exception {
            final AtomicInteger classification = new AtomicInteger();
            final AtomicInteger permission = new AtomicInteger();
            final AtomicInteger lookups = new AtomicInteger();
            final AtomicInteger forwarding = new AtomicInteger();
            final Handler cache = new Jetty12ProxyChain.ProxyCacheHandler(request -> {
                lookups.incrementAndGet();
                return null;
            });
            final Handler proxyPipeline = new Handler.Sequence(cache, responseHandler(202, forwarding));
            final Handler gate = countingGate(proxyPipeline, classification, permission);

            final String response = proxyRequest(gate);
            assertTrue(response, response.startsWith("HTTP/1.1 202"));
            assertEquals(1, classification.get());
            assertEquals(1, permission.get());
            assertEquals(1, lookups.get());
            assertEquals(1, forwarding.get());
        }

        private static Handler responseHandler(final int status, final AtomicInteger calls) {
            return new Handler.Abstract() {
                @Override
                public boolean handle(final Request request, final Response response,
                        final Callback callback) {
                    if (calls != null) {
                        calls.incrementAndGet();
                    }
                    response.setStatus(status);
                    callback.succeeded();
                    return true;
                }
            };
        }

        private static Handler countingGate(final Handler proxyPipeline,
                final AtomicInteger classification, final AtomicInteger permission) {
            return new Jetty12ProxyChain.ProxyPolicyHandler(proxyPipeline, request -> {
                classification.incrementAndGet();
                return true;
            }, request -> {
                permission.incrementAndGet();
                return null;
            });
        }

        private static String proxyRequest(final Handler handler) throws Exception {
            return request(handler, "GET http://example.invalid/resource HTTP/1.1\r\n"
                    + "Host: example.invalid\r\nConnection: close\r\n\r\n");
        }

        private static void assertDirectPeerRequest(final String target, final boolean expected)
                throws Exception {
            final Handler check = new Handler.Abstract() {
                @Override
                public boolean handle(final Request request, final Response response,
                        final Callback callback) {
                    final boolean actual = Jetty12ProxyChain.ProxyPolicyHandler.isDirectPeerRequest(
                            request, "localpeer");
                    response.setStatus(actual == expected ? 204 : 500);
                    callback.succeeded();
                    return true;
                }
            };
            final String host = target.startsWith("http://")
                    ? java.net.URI.create(target).getRawAuthority() : "127.0.0.1:%d";
            final String response = request(check, "GET " + target + " HTTP/1.1\r\nHost: "
                    + host + "\r\nConnection: close\r\n\r\n");
            assertTrue(response, response.startsWith("HTTP/1.1 204"));
        }
    }

    /** {@code .yacy} authority rewrite in front of the proxy selection. */
    public static class DomainHandlerTest {

        @Test
        public void rewritesPeerAuthorityBeforeCallingNextHandler() throws Exception {
            final AtomicReference<String> authority = new AtomicReference<>();
            final AtomicReference<String> hostHeader = new AtomicReference<>();
            final Handler capture = new Handler.Abstract() {
                @Override
                public boolean handle(final Request request, final Response response,
                        final Callback callback) {
                    authority.set(request.getHttpURI().getAuthority());
                    hostHeader.set(request.getHeaders().get(HttpHeader.HOST));
                    response.setStatus(204);
                    callback.succeeded();
                    return true;
                }
            };
            final Server server = new Server();
            final ServerConnector connector = new ServerConnector(server, 1, 1);
            connector.setHost("127.0.0.1");
            connector.setPort(0);
            server.addConnector(connector);
            server.setHandler(new Jetty12ProxyChain.DomainHandler(capture, resolver(), ignored -> false));
            try {
                server.start();
                try (Socket client = new Socket("127.0.0.1", connector.getLocalPort())) {
                    final OutputStream output = client.getOutputStream();
                    output.write(("GET /status?q=1 HTTP/1.1\r\nHost: peer.yacy\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    try (InputStream input = client.getInputStream()) {
                        while (input.read() >= 0) {
                            // Drain the response so the exchange completes before assertions.
                        }
                    }
                }
                assertEquals("198.51.100.7:8090", authority.get());
                assertEquals("198.51.100.7:8090", hostHeader.get());
            } finally {
                server.stop();
                server.join();
            }
        }

        private static AlternativeDomainNames resolver() {
            return new AlternativeDomainNames() {
                @Override
                public String resolve(final String name) {
                    return "peer.yacy".equals(name) ? "198.51.100.7:8090" : null;
                }

                @Override public String myAlternativeAddress() { return "local.yacy"; }
                @Override public Set<String> myIPs() { return Collections.singleton("127.0.0.1"); }
                @Override public int myPort() { return 8090; }
                @Override public String myName() { return "local"; }
                @Override public String myID() { return "local-id"; }
            };
        }
    }

    /** Raw byte tunnelling for permitted CONNECT destinations. */
    public static class ConnectTunnelHandlerTest {

        @Test
        public void tunnelsBytesToPermittedDestination() throws Exception {
            try (ServerSocket origin = new ServerSocket(0, 1)) {
                final Thread echo = new Thread(() -> echoOnce(origin), "connect-origin-echo");
                echo.start();
                final Server proxy = new Server();
                final ServerConnector connector = new ServerConnector(proxy, 1, 1);
                connector.setHost("127.0.0.1");
                connector.setPort(0);
                proxy.addConnector(connector);
                proxy.setHandler(new Jetty12ProxyChain.ConnectTunnelHandler(
                        (Handler) null, (request, destination) -> null));
                try {
                    proxy.start();
                    try (Socket client = new Socket("127.0.0.1", connector.getLocalPort())) {
                        final OutputStream output = client.getOutputStream();
                        output.write(("CONNECT 127.0.0.1:" + origin.getLocalPort()
                                + " HTTP/1.1\r\nHost: 127.0.0.1:" + origin.getLocalPort()
                                + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        output.flush();
                        final InputStream input = client.getInputStream();
                        final String headers = readHeaders(input);
                        assertEquals(true, headers.startsWith("HTTP/1.1 200"));
                        output.write("tunnel-data".getBytes(StandardCharsets.US_ASCII));
                        output.flush();
                        assertEquals("tunnel-data", new String(input.readNBytes(11), StandardCharsets.US_ASCII));
                    }
                } finally {
                    proxy.stop();
                    proxy.join();
                    echo.join(5000L);
                }
            }
        }

        private static void echoOnce(final ServerSocket origin) {
            try (Socket socket = origin.accept()) {
                final byte[] data = socket.getInputStream().readNBytes(11);
                socket.getOutputStream().write(data);
                socket.getOutputStream().flush();
            } catch (final Exception error) {
                throw new AssertionError(error);
            }
        }

        private static String readHeaders(final InputStream input) throws Exception {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4) {
                final int next = input.read();
                if (next < 0) {
                    break;
                }
                bytes.write(next);
                final byte expected = new byte[] {'\r', '\n', '\r', '\n'}[matched];
                matched = next == expected ? matched + 1 : (next == '\r' ? 1 : 0);
            }
            return bytes.toString(StandardCharsets.US_ASCII);
        }
    }

    /** Streaming forward proxy for absolute and transparent origin-form requests. */
    public static class ForwardProxyHandlerTest {

        @Test
        public void forwardsAbsoluteHttpRequest() throws Exception {
            assertForwarded(true);
        }

        @Test
        public void forwardsTransparentOriginFormRequest() throws Exception {
            assertForwarded(false);
        }

        private static void assertForwarded(final boolean absoluteTarget) throws Exception {
            final ByteArrayOutputStream captured = new ByteArrayOutputStream();
            final CountDownLatch captureComplete = new CountDownLatch(1);
            final HttpServer origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            origin.createContext("/resource", exchange -> {
                final byte[] body = "proxied-content".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            origin.start();
            final Server proxy = new Server();
            final ServerConnector connector = new ServerConnector(proxy, 1, 1);
            connector.setHost("127.0.0.1");
            connector.setPort(0);
            proxy.addConnector(connector);
            proxy.setHandler(new Jetty12ProxyChain.ForwardProxyHandler(
                    (request, upstream) -> new Jetty12ProxyChain.ForwardProxyHandler.Capture() {
                        @Override
                        public void append(final ByteBuffer content) {
                            final byte[] bytes = new byte[content.remaining()];
                            content.get(bytes);
                            captured.writeBytes(bytes);
                        }

                        @Override
                        public void complete() {
                            captureComplete.countDown();
                        }
                    }));
            try {
                proxy.start();
                try (Socket client = new Socket("127.0.0.1", connector.getLocalPort())) {
                    final OutputStream output = client.getOutputStream();
                    final String target = absoluteTarget
                            ? "http://127.0.0.1:" + origin.getAddress().getPort() + "/resource"
                            : "/resource";
                    output.write(("GET " + target + " HTTP/1.1\r\nHost: 127.0.0.1:"
                            + origin.getAddress().getPort() + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    final String response = readAll(client.getInputStream());
                    assertTrue(response.startsWith("HTTP/1.1 200"));
                    assertTrue(response.contains("proxied-content"));
                }
                assertTrue(captureComplete.await(2, TimeUnit.SECONDS));
                assertArrayEquals("proxied-content".getBytes(StandardCharsets.UTF_8),
                        captured.toByteArray());
            } finally {
                proxy.stop();
                proxy.join();
                origin.stop(0);
            }
        }
    }

    /** Cache hits are served with 203 without a network round trip. */
    public static class ProxyCacheHandlerTest {

        @Test
        public void servesFreshCachedResponseWithoutNetworkHandler() throws Exception {
            final HttpFields headers = HttpFields.build().put("Content-Type", "text/plain").asImmutable();
            final Handler cache = new Jetty12ProxyChain.ProxyCacheHandler(request ->
                    new Jetty12ProxyChain.ProxyCacheHandler.CachedResponse(headers,
                            "cached-content".getBytes(StandardCharsets.UTF_8)));
            final String response = request(cache,
                    "GET http://example.invalid/resource HTTP/1.1\r\n"
                            + "Host: example.invalid\r\nConnection: close\r\n\r\n");
            assertTrue(response, response.startsWith("HTTP/1.1 203"));
            assertTrue(response, response.contains("cached-content"));
        }
    }

    /** Size-capped copy of streamed proxy responses for the cache/index path. */
    public static class ProxyResponseStoreTest {

        @Test
        public void discardsCacheCaptureWhenStreamingResponseExceedsLimit() {
            final Jetty12ProxyChain.ProxyResponseStore.CacheCapture capture =
                    new Jetty12ProxyChain.ProxyResponseStore.CacheCapture(null, null, 4);

            capture.append(ByteBuffer.wrap(new byte[] {1, 2}));
            capture.append(ByteBuffer.wrap(new byte[] {3, 4}));
            assertFalse(capture.isDiscarded());
            assertEquals(4, capture.bufferedSize());

            capture.append(ByteBuffer.wrap(new byte[] {5}));
            assertTrue(capture.isDiscarded());
            assertEquals(0, capture.bufferedSize());

            capture.append(ByteBuffer.wrap(new byte[] {6, 7}));
            assertEquals(0, capture.bufferedSize());
            capture.complete();
        }
    }

    static String request(final Handler handler, final String requestTemplate) throws Exception {
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
                return readAll(client.getInputStream());
            }
        } finally {
            server.stop();
            server.join();
        }
    }

    static String readAll(final InputStream input) throws Exception {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        input.transferTo(result);
        return result.toString(StandardCharsets.ISO_8859_1);
    }
}
