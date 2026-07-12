/**
 *  Jetty12ProxyChain
 *  Copyright 2026 by Michael Peter Christen
 *  First released 12.07.2026 at https://yacy.net
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

package net.yacy.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Locale;
import java.util.function.Predicate;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.HostPort;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.crawler.data.Cache;
import net.yacy.document.TextParser;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.http.AlternativeDomainNames;
import net.yacy.server.http.HTTPDProxyHandler;

/**
 * The complete Jetty 12 transparent-proxy handler chain.
 *
 * <p>Every handler of the optional transparent-proxy feature is a static
 * nested class of this file; {@link #wrap(Handler, Switchboard)} composes them
 * in the contract order: {@code .yacy} domain rewrite, CONNECT tunnel, then
 * the policy-gated cache/forward sequence in front of the web application.
 * The default (non-proxy) runtime lives in {@link Jetty12HttpServer}.</p>
 */
final class Jetty12ProxyChain {

    private Jetty12ProxyChain() {
    }

    /** Compose the transparent-proxy pipeline around the web application handler. */
    static Handler wrap(final Handler next, final Switchboard switchboard) {
        final Handler forwardProxy = new ForwardProxyHandler(switchboard);
        final Handler proxyCache = new ProxyCacheHandler(switchboard);
        final Handler proxyPipeline = new Handler.Sequence(proxyCache, forwardProxy);
        final Handler proxyPolicy = new ProxyPolicyHandler(proxyPipeline, switchboard);
        Handler pipeline = new Handler.Sequence(proxyPolicy, next);
        pipeline = new ConnectTunnelHandler(pipeline, switchboard);
        return new DomainHandler(pipeline, switchboard.peers);
    }

    /** Applies proxy classification and authorization once before cache or forwarding. */
    static final class ProxyPolicyHandler extends Handler.Wrapper {

        @FunctionalInterface
        interface Permission {
            String rejectionReason(Request request);
        }

        private final Predicate<Request> proxyRequest;
        private final Permission permission;

        ProxyPolicyHandler(final Handler proxyPipeline, final Switchboard switchboard) {
            this(proxyPipeline, request -> isProxyRequest(switchboard, request),
                    request -> rejectionReason(switchboard, request));
        }

        ProxyPolicyHandler(final Handler proxyPipeline,
                final Predicate<Request> proxyRequest, final Permission permission) {
            super(proxyPipeline);
            this.proxyRequest = proxyRequest;
            this.permission = permission;
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback)
                throws Exception {
            if (!this.proxyRequest.test(request)) {
                return false;
            }
            final String rejection = this.permission.rejectionReason(request);
            if (rejection != null) {
                Response.writeError(request, response, callback, 403, rejection);
                return true;
            }
            return super.handle(request, response, callback);
        }

        static boolean isProxyRequest(final Switchboard switchboard, final Request request) {
            if (isDirectPeerRequest(request, switchboard.getConfig("fileHost", "localpeer"))) {
                return false;
            }
            final String host = Request.getServerName(request);
            if (switchboard.peers != null) {
                if (switchboard.peers.myIPs().contains(host)
                        || host.equalsIgnoreCase(switchboard.peers.myAlternativeAddress())) {
                    return false;
                }
                if (switchboard.peers.mySeed() != null
                        && (switchboard.peers.mySeed().getIPs().contains(host)
                                || host.equalsIgnoreCase(
                                        switchboard.peers.mySeed().getHexHash() + ".yacyh"))) {
                    return false;
                }
            }
            final java.net.InetAddress resolved = Domains.dnsResolve(host);
            return resolved == null || !switchboard.myPublicIPs().contains(resolved.getHostAddress());
        }

        static boolean isDirectPeerRequest(final Request request, final String fileHost) {
            final String host = Request.getServerName(request);
            final int targetPort = request.getHttpURI().getPort();
            if (targetPort >= 0 && targetPort != Request.getLocalPort(request)) {
                return false;
            }
            if (host == null || "localhost".equalsIgnoreCase(host)
                    || fileHost.equalsIgnoreCase(host)
                    || Domains.isThisHostIP(host)) {
                return true;
            }
            final java.net.InetAddress resolved = Domains.dnsResolve(host);
            return resolved != null && Domains.isLocal(host, resolved);
        }

        static String rejectionReason(final Switchboard switchboard, final Request request) {
            final String remoteAddress = Request.getRemoteAddr(request);
            if (!ProxyAccessPolicy.isClientAllowed(
                    switchboard.getConfig("proxyClient", "*"), remoteAddress)) {
                return "proxy use not granted for IP " + remoteAddress;
            }
            final String requestHost = Request.getServerName(request);
            if (requestHost == null) {
                return "proxy target has no host";
            }
            final String host = requestHost.toLowerCase(Locale.ROOT);
            if (Switchboard.urlBlacklist.isListed(BlacklistType.PROXY, host,
                    request.getHttpURI().getCanonicalPath())) {
                return "URL '" + host + "' blocked by yacy proxy";
            }
            return null;
        }
    }

    /** Rewrites peer-domain authorities before requests enter the proxy chain. */
    static final class DomainHandler extends Handler.Wrapper {

        private final AlternativeDomainNames resolver;
        private final Predicate<String> localAddress;

        DomainHandler(final Handler next, final AlternativeDomainNames resolver) {
            this(next, resolver, host -> Domains.isLocal(host, null));
        }

        DomainHandler(final Handler next, final AlternativeDomainNames resolver,
                final Predicate<String> localAddress) {
            super(next);
            this.resolver = resolver;
            this.localAddress = localAddress;
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback)
                throws Exception {
            if (this.resolver == null) {
                return super.handle(request, response, callback);
            }
            final String host = Request.getServerName(request);
            if (host == null) {
                return super.handle(request, response, callback);
            }
            final String resolved = this.resolver.resolve(host);
            if (resolved == null) {
                return super.handle(request, response, callback);
            }
            final HostPort destination = new HostPort(resolved);
            final String destinationHost = destination.getHost();
            if (this.resolver.myIPs().contains(destinationHost) || this.localAddress.test(destinationHost)) {
                return super.handle(request, response, callback);
            }
            final int destinationPort = destination.getPort(80);
            final HttpURI.Mutable rewritten = HttpURI.build(request.getHttpURI())
                    .authority(destinationHost, destinationPort);
            if (rewritten.getScheme() == null) {
                rewritten.scheme(request.isSecure() ? "https" : "http");
            }
            final HttpURI rewrittenUri = rewritten.asImmutable();
            final HttpFields rewrittenHeaders = HttpFields.build(request.getHeaders())
                    .put(HttpHeader.HOST, destinationHost + (destinationPort == 80 ? "" : ":" + destinationPort))
                    .asImmutable();
            final Request rewrittenRequest = new Request.Wrapper(request) {
                @Override
                public HttpURI getHttpURI() {
                    return rewrittenUri;
                }

                @Override
                public HttpFields getHeaders() {
                    return rewrittenHeaders;
                }
            };
            return this.getHandler().handle(rewrittenRequest, response, callback);
        }
    }

    /** Jetty 12 CONNECT tunnel with YaCy's existing proxy authorization rules. */
    static final class ConnectTunnelHandler extends org.eclipse.jetty.server.handler.ConnectHandler {

        @FunctionalInterface
        interface Permission {
            String rejectionReason(Request request, HostPort destination);
        }

        private final Permission permission;

        ConnectTunnelHandler(final Handler next, final Switchboard switchboard) {
            this(next, (request, destination) -> rejectionReason(switchboard, request, destination));
        }

        ConnectTunnelHandler(final Handler next, final Permission permission) {
            super(next);
            this.permission = permission;
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback)
                throws Exception {
            if (!HttpMethod.CONNECT.is(request.getMethod())) {
                return super.handle(request, response, callback);
            }
            final HostPort destination;
            try {
                final String target = request.getHttpURI().getAuthority() != null
                        ? request.getHttpURI().getAuthority() : request.getHttpURI().getPath();
                if (target == null) {
                    Response.writeError(request, response, callback, 400, "Invalid CONNECT destination");
                    return true;
                }
                destination = new HostPort(target);
            } catch (final IllegalArgumentException error) {
                Response.writeError(request, response, callback, 400, "Invalid CONNECT destination");
                return true;
            }
            final String rejection = this.permission.rejectionReason(request, destination);
            if (rejection != null) {
                Response.writeError(request, response, callback, 403, rejection);
                return true;
            }
            return super.handle(request, response, callback);
        }

        private static String rejectionReason(final Switchboard switchboard, final Request request,
                final HostPort destination) {
            final String remoteAddress = Request.getRemoteAddr(request);
            if (!ProxyAccessPolicy.isClientAllowed(switchboard.getConfig("proxyClient", "*"), remoteAddress)) {
                return "proxy use not granted for IP " + remoteAddress;
            }
            final String destinationHost = destination.getHost().toLowerCase(Locale.ROOT);
            if (Switchboard.urlBlacklist.isListed(BlacklistType.PROXY, destinationHost, "/")) {
                return "URL '" + destinationHost + "' blocked by yacy proxy";
            }
            return null;
        }
    }

    /** Serves fresh YaCy proxy-cache entries before a request reaches the network. */
    static final class ProxyCacheHandler extends Handler.Abstract {

        record CachedResponse(HttpFields headers, byte[] content) {
        }

        @FunctionalInterface
        interface Lookup {
            CachedResponse find(Request request);
        }

        private final Lookup lookup;

        ProxyCacheHandler(final Switchboard switchboard) {
            this(request -> findCached(switchboard, request));
        }

        ProxyCacheHandler(final Lookup lookup) {
            this.lookup = lookup;
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback) {
            if (!"GET".equals(request.getMethod())) {
                return false;
            }
            final CachedResponse cached = this.lookup.find(request);
            if (cached == null) {
                return false;
            }
            response.setStatus(203);
            response.getHeaders().add(cached.headers());
            response.write(true, ByteBuffer.wrap(cached.content()), callback);
            return true;
        }

        private static CachedResponse findCached(final Switchboard switchboard, final Request request) {
            try {
                if (switchboard.crawler == null || switchboard.crawler.defaultProxyProfile == null) {
                    return null;
                }
                final DigestURL url = new DigestURL(request.getHttpURI().toString());
                final ResponseHeader responseHeader = Cache.getResponseHeader(url.hash());
                if (responseHeader == null) {
                    return null;
                }
                final RequestHeader requestHeader = new RequestHeader();
                request.getHeaders().forEach(field -> requestHeader.add(field.getName(), field.getValue()));
                final net.yacy.crawler.retrieval.Request crawlerRequest =
                        new net.yacy.crawler.retrieval.Request(null, url,
                                requestHeader.referer() == null ? null
                                        : new DigestURL(requestHeader.referer().toNormalform(true)).hash(),
                                "", responseHeader.lastModified(),
                                switchboard.crawler.defaultProxyProfile.handle(), 0,
                                switchboard.crawler.defaultProxyProfile.timezoneOffset());
                final net.yacy.crawler.retrieval.Response cachedResponse =
                        new net.yacy.crawler.retrieval.Response(crawlerRequest, requestHeader,
                                responseHeader, switchboard.crawler.defaultProxyProfile, false, null);
                final byte[] content = Cache.getContent(url.hash());
                if (content == null || !cachedResponse.isFreshForProxy()) {
                    return null;
                }
                final HttpFields.Mutable headers = HttpFields.build();
                responseHeader.forEach((name, value) -> headers.add(name, value));
                return new CachedResponse(headers.asImmutable(), content);
            } catch (final Exception invalidCacheEntry) {
                return null;
            }
        }
    }

    /** Streams an already authorized Jetty 12 HTTP proxy request. */
    static final class ForwardProxyHandler extends ProxyHandler.Forward {

        interface Capture {
            void append(ByteBuffer content);

            void complete();
        }

        @FunctionalInterface
        interface CaptureFactory {
            Capture begin(Request request, org.eclipse.jetty.client.Response upstream);
        }

        private final CaptureFactory captureFactory;
        private final Switchboard switchboard;
        private final int timeout;

        ForwardProxyHandler(final Switchboard switchboard) {
            this(new ProxyResponseStore(switchboard), switchboard,
                    switchboard.getConfigInt("proxy.clientTimeout", 10000));
        }

        ForwardProxyHandler(final CaptureFactory captureFactory) {
            this(captureFactory, null, 10000);
        }

        private ForwardProxyHandler(final CaptureFactory captureFactory,
                final Switchboard switchboard, final int timeout) {
            this.captureFactory = captureFactory;
            this.switchboard = switchboard;
            this.timeout = timeout;
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback) {
            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.POST.is(request.getMethod())
                    && !HttpMethod.HEAD.is(request.getMethod())) {
                Response.writeError(request, response, callback, 501, "Unsupported proxy request method");
                return true;
            }
            if (this.switchboard != null) {
                this.switchboard.proxyLastAccess = System.currentTimeMillis();
            }
            return super.handle(request, response, callback);
        }

        @Override
        protected void configureHttpClient(final HttpClient httpClient) {
            super.configureHttpClient(httpClient);
            httpClient.setConnectTimeout(this.timeout);
            httpClient.setIdleTimeout(this.timeout);
        }

        @Override
        protected void addProxyHeaders(final Request clientRequest,
                final org.eclipse.jetty.client.Request upstreamRequest) {
            addViaHeader(clientRequest, upstreamRequest);
            if (this.switchboard != null
                    && this.switchboard.getConfigBool("proxy.sendXForwardedForHeader", true)) {
                final String remoteAddress = Request.getRemoteAddr(clientRequest);
                if (!Domains.isThisHostIP(remoteAddress)) {
                    upstreamRequest.headers(headers -> headers.put(HttpHeader.X_FORWARDED_FOR,
                            remoteAddress));
                }
            }
        }

        @Override
        protected HttpURI rewriteHttpURI(final Request request) {
            if (request.getHttpURI().isAbsolute()) {
                return request.getHttpURI();
            }
            return HttpURI.build(request.getHttpURI())
                    .scheme(request.isSecure() ? "https" : "http")
                    .authority(Request.getServerName(request), Request.getServerPort(request))
                    .asImmutable();
        }

        @Override
        protected org.eclipse.jetty.client.Response.CompleteListener newServerToProxyResponseListener(
                final Request clientRequest, final org.eclipse.jetty.client.Request upstreamRequest,
                final Response clientResponse, final Callback clientCallback) {
            return new ProxyResponseListener(clientRequest, upstreamRequest, clientResponse, clientCallback) {
                private Capture capture;

                @Override
                public void onHeaders(final org.eclipse.jetty.client.Response upstreamResponse) {
                    super.onHeaders(upstreamResponse);
                    this.capture = ForwardProxyHandler.this.captureFactory.begin(
                            clientRequest, upstreamResponse);
                }

                @Override
                public void onContent(final org.eclipse.jetty.client.Response upstreamResponse,
                        final org.eclipse.jetty.io.Content.Chunk chunk, final Runnable demander) {
                    if (this.capture != null) {
                        this.capture.append(chunk.getByteBuffer().asReadOnlyBuffer());
                    }
                    super.onContent(upstreamResponse, chunk, demander);
                }

                @Override
                public void onComplete(final Result result) {
                    super.onComplete(result);
                    if (result.isSucceeded() && this.capture != null) {
                        whenComplete((ignored, failure) -> {
                            if (failure == null) {
                                this.capture.complete();
                            }
                        });
                    }
                }
            };
        }

        @Override
        protected void onProxyToClientResponseComplete(final Request clientRequest,
                final org.eclipse.jetty.client.Request upstreamRequest,
                final org.eclipse.jetty.client.Response upstreamResponse,
                final Response clientResponse, final Callback clientCallback) {
            final StringBuilder message = new StringBuilder(96);
            message.append(GenericFormatter.SHORT_SECOND_FORMATTER.format(new Date())).append(' ')
                    .append(Request.getRemoteAddr(clientRequest)).append(' ')
                    .append(clientRequest.getMethod()).append(' ')
                    .append(clientRequest.getHttpURI());
            HTTPDProxyHandler.proxyLog.fine(message.toString());
            super.onProxyToClientResponseComplete(clientRequest, upstreamRequest, upstreamResponse,
                    clientResponse, clientCallback);
        }
    }

    /** Bridges a completed Jetty 12 proxy response into YaCy's cache/index path. */
    static final class ProxyResponseStore implements ForwardProxyHandler.CaptureFactory {

        private final Switchboard switchboard;

        ProxyResponseStore(final Switchboard switchboard) {
            this.switchboard = switchboard;
        }

        @Override
        public ForwardProxyHandler.Capture begin(final Request request,
                final org.eclipse.jetty.client.Response upstream) {
            try {
                if (this.switchboard.crawler == null
                        || this.switchboard.crawler.defaultProxyProfile == null) {
                    return null;
                }
                final DigestURL url = new DigestURL(request.getHttpURI().toString());
                final ResponseHeader responseHeader = new ResponseHeader(upstream.getStatus());
                for (final HttpField field : upstream.getHeaders()) {
                    responseHeader.add(field.getName(), field.getValue());
                }
                final net.yacy.crawler.retrieval.Request crawlerRequest =
                        new net.yacy.crawler.retrieval.Request(null, url, null, "",
                                responseHeader.lastModified(),
                                this.switchboard.crawler.defaultProxyProfile.handle(), 0,
                                this.switchboard.crawler.defaultProxyProfile.timezoneOffset());
                final net.yacy.crawler.retrieval.Response yacyResponse =
                        new net.yacy.crawler.retrieval.Response(crawlerRequest, null, responseHeader,
                                this.switchboard.crawler.defaultProxyProfile, false, null);
                final String storeError = yacyResponse.shallStoreCacheForProxy();
                final boolean storeHTCache = yacyResponse.profile().storeHTCache();
                final String supportError = TextParser.supports(url, yacyResponse.getMimeType());
                if (storeError != null || (!storeHTCache && supportError == null)) {
                    return null;
                }
                return new CacheCapture(this.switchboard, yacyResponse);
            } catch (final IOException invalidUrl) {
                return null;
            }
        }

        static final class CacheCapture implements ForwardProxyHandler.Capture {

            private final Switchboard switchboard;
            private final net.yacy.crawler.retrieval.Response response;
            private final int maxContentSize;
            private final ByteArrayOutputStream content = new ByteArrayOutputStream();
            private boolean discarded;

            private CacheCapture(final Switchboard switchboard,
                    final net.yacy.crawler.retrieval.Response response) {
                this(switchboard, response,
                        (int) net.yacy.crawler.retrieval.Response.CRAWLER_MAX_SIZE_TO_CACHE);
            }

            CacheCapture(final Switchboard switchboard,
                    final net.yacy.crawler.retrieval.Response response,
                    final int maxContentSize) {
                this.switchboard = switchboard;
                this.response = response;
                this.maxContentSize = maxContentSize;
            }

            @Override
            public synchronized void append(final ByteBuffer buffer) {
                if (this.discarded) {
                    return;
                }
                final ByteBuffer copy = buffer.slice();
                if (copy.remaining() > this.maxContentSize - this.content.size()) {
                    this.content.reset();
                    this.discarded = true;
                    return;
                }
                final byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                this.content.writeBytes(bytes);
            }

            @Override
            public void complete() {
                final byte[] bytes;
                synchronized (this) {
                    if (this.discarded) {
                        return;
                    }
                    bytes = this.content.toByteArray();
                }
                if (bytes.length == 0) {
                    return;
                }
                final Thread writer = new Thread(() -> {
                    try {
                        if (Cache.getResponseHeader(this.response.url().hash()) != null) {
                            Cache.delete(this.response.url().hash());
                        }
                        this.response.setContent(bytes);
                        Cache.store(this.response.url(), this.response.getResponseHeader(), bytes);
                        this.switchboard.toIndexer(this.response);
                    } catch (final IOException ignored) {
                        // A proxy response must still reach its client when cache storage fails.
                    }
                }, "Jetty12ProxyChain.ResponseStore(" + this.response.url().toNormalform(true) + ")");
                writer.setPriority(Thread.MIN_PRIORITY);
                writer.start();
            }

            synchronized int bufferedSize() {
                return this.content.size();
            }

            synchronized boolean isDiscarded() {
                return this.discarded;
            }
        }
    }
}
