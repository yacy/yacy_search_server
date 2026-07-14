/**
 *  Jetty12HttpServer
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

import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee8.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee8.security.RoleInfo;
import org.eclipse.jetty.ee8.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.google.common.net.InetAddresses;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.http.servlets.MonitorFilter;
import net.yacy.http.servlets.YaCyDefaultServlet;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverAccessTracker;

/**
 * The complete Jetty 12 server adapter behind {@link YaCyHttpServer}.
 *
 * <p>Every Jetty-12-specific class of the default (non-proxy) runtime is a
 * static nested class of this file: bootstrap and connectors, administrator
 * security, access rules, crash protection and the error page. This is the
 * single surface to port at the next servlet-container migration; the optional
 * transparent-proxy handlers live in {@link Jetty12ProxyChain}.</p>
 */
public class Jetty12HttpServer implements YaCyHttpServer {

    private final Server server;
    private final AdminLoginService loginService;

    public Jetty12HttpServer(final int port, final String host) {
        final Switchboard switchboard = Switchboard.getSwitchboard();
        final HttpServerBootstrapConfig bootstrap = HttpServerBootstrapConfig.from(switchboard, port, host);
        final SSLContext sslContext = bootstrap.httpsEnabled()
                ? HttpServerBootstrapConfig.ServerTlsContextFactory.create(switchboard)
                : null;
        this.loginService = new AdminLoginService();
        this.loginService.setName(bootstrap.adminRealm());
        this.server = createServer(bootstrap.httpPort(), bootstrap.bindHost(), bootstrap.acceptorCount(),
                sslContext, bootstrap.httpsPort());
        Handler requestPipeline = createWebAppHandler(this.server, bootstrap, this.loginService);
        if (bootstrap.transparentProxyEnabled()) {
            requestPipeline = Jetty12ProxyChain.wrap(requestPipeline, switchboard);
        } else {
            requestPipeline = new DisabledProxyHandler(requestPipeline);
        }
        final Handler crashProtected = new CrashProtectionHandler(requestPipeline);
        this.server.setHandler(AccessRules.wrap(crashProtected, bootstrap.serverClientRules()));
    }

    Jetty12HttpServer(final int port, final String host, final int acceptorCount,
            final SSLContext sslContext, final int sslPort) {
        this.loginService = null;
        this.server = createServer(port, host, acceptorCount, sslContext, sslPort);
    }

    private static Server createServer(final int port, final String host, final int acceptorCount,
            final SSLContext sslContext, final int sslPort) {
        final Server server = new Server();
        final Connection.Listener connectionCloseMonitor = new Connection.Listener() {
            @Override
            public void onClosed(final Connection connection) {
                if (connection.getEndPoint().getRemoteSocketAddress() instanceof InetSocketAddress) {
                    final InetSocketAddress remote =
                            (InetSocketAddress) connection.getEndPoint().getRemoteSocketAddress();
                    ConnectionInfo.removeServerConnection(MonitorFilter.connectionId(
                            remote.getAddress().getHostAddress(), remote.getPort()));
                }
            }
        };

        final HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setRequestHeaderSize(HttpServerBootstrapConfig.REQUEST_HEADER_SIZE);
        final ServerConnector httpConnector = new ServerConnector(server, acceptorCount, -1,
                new HttpConnectionFactory(httpConfiguration));
        configureConnector(httpConnector, host, port, "httpd-" + host + ":" + port,
                connectionCloseMonitor);
        httpConnector.setAcceptQueueSize(HttpServerBootstrapConfig.ACCEPT_QUEUE_SIZE);
        server.addConnector(httpConnector);

        if (sslContext != null) {
            final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setSslContext(sslContext);
            final HttpConfiguration httpsConfiguration = new HttpConfiguration(httpConfiguration);
            httpsConfiguration.addCustomizer(createSecureRequestCustomizer());
            final ServerConnector httpsConnector = new ServerConnector(server, acceptorCount, -1,
                    new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                    new HttpConnectionFactory(httpsConfiguration));
            configureConnector(httpsConnector, host, sslPort, "ssld:" + sslPort,
                    connectionCloseMonitor);
            httpsConnector.setAcceptQueueSize(HttpServerBootstrapConfig.ACCEPT_QUEUE_SIZE);
            server.addConnector(httpsConnector);
        }
        return server;
    }

    static SecureRequestCustomizer createSecureRequestCustomizer() {
        final SecureRequestCustomizer secureRequests = new SecureRequestCustomizer();
        // Preserve Jetty 9 behavior and support YaCy's configurable/self-signed certificates,
        // whose subject does not necessarily match the requested peer hostname.
        secureRequests.setSniHostCheck(false);
        return secureRequests;
    }

    private static void configureConnector(final ServerConnector connector, final String host,
            final int port, final String name, final Connection.Listener listener) {
        connector.setHost(host);
        connector.setPort(port);
        connector.setName(name);
        connector.setIdleTimeout(HttpServerBootstrapConfig.CONNECTOR_IDLE_TIMEOUT_MILLIS);
        connector.addBean(listener);
    }

    private static Handler createWebAppHandler(final Server server,
            final HttpServerBootstrapConfig bootstrap,
            final AdminLoginService loginService) {
        final WebAppContext webApp = new WebAppContext();
        webApp.setServer(server);
        webApp.setContextPath("/");
        webApp.setMaxFormContentSize(HttpServerBootstrapConfig.MAX_FORM_CONTENT_SIZE);
        webApp.setErrorHandler(new ErrorPageHandler());
        webApp.setBaseResource(webApp.getResourceFactory().newResource(Path.of(bootstrap.htrootPath())));
        webApp.setDefaultsDescriptor(bootstrap.defaultsWebXml());
        if (Files.exists(Path.of(bootstrap.overrideWebXml()))) {
            webApp.setDescriptor(bootstrap.overrideWebXml());
        }

        final ServletHolder defaultServlet = new ServletHolder(YaCyDefaultServlet.class);
        defaultServlet.setInitParameter("resourceBase", bootstrap.htrootPath());
        defaultServlet.setAsyncSupported(true);
        webApp.addServlet(defaultServlet, "/*");

        final FilterHolder monitorFilter = new FilterHolder(MonitorFilter.class);
        monitorFilter.setAsyncSupported(true);
        webApp.addFilter(monitorFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        final AdminSecurityHandler security = new AdminSecurityHandler();
        security.setLoginService(loginService);
        webApp.setSecurityHandler(security);

        return createGzipHandler(webApp.get(), bootstrap.gzipResponsesEnabled());
    }

    static GzipHandler createGzipHandler(final Handler handler, final boolean gzipResponsesEnabled) {
        final GzipHandler gzip = new GzipHandler(handler);
        gzip.setIncludedMethods(HttpMethod.GET.asString());
        gzip.setInflateBufferSize(HttpServerBootstrapConfig.REQUEST_INFLATE_BUFFER_SIZE);
        gzip.addIncludedInflationPaths("/*");
        gzip.addExcludedInflationPaths("*.svgz");
        if (!gzipResponsesEnabled) {
            gzip.addExcludedMethods(HttpMethod.GET.asString());
        }
        /*
         * Jetty 12.1 replaced GzipHandler with CompressionHandler plus
         * GzipCompression/GzipDecoderConfig. Restore that adapter when YaCy moves
         * back to 12.1 or later; it also supports method-specific decompression.
         */
        return gzip;
    }

    @Override
    public void startupServer() throws Exception {
        this.server.setStopAtShutdown(true);
        this.server.start();
    }

    @Override
    public void stop() throws Exception {
        this.server.stop();
        this.server.join();
    }

    @Override
    public void reconnect(final int milliseconds) {
        new Thread(() -> {
            if (milliseconds > 0) {
                try {
                    Thread.sleep(milliseconds);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                if (!this.server.isRunning() || this.server.isStopped()) {
                    this.server.start();
                }
                final int httpPort = Switchboard.getSwitchboard().getLocalPort();
                final int httpsPort = Switchboard.getSwitchboard().getConfigInt(
                        SwitchboardConstants.SERVER_SSLPORT, 8443);
                for (final Connector connector : this.server.getConnectors()) {
                    final ServerConnector networkConnector = (ServerConnector) connector;
                    final int desiredPort = connector.getName().startsWith("ssl") ? httpsPort : httpPort;
                    if (networkConnector.getPort() != desiredPort) {
                        networkConnector.close();
                        networkConnector.stop();
                        networkConnector.setPort(desiredPort);
                        networkConnector.start();
                        ConcurrentLog.info("SERVER", "set new port for Jetty connector " + connector.getName());
                    }
                }
            } catch (final Exception error) {
                ConcurrentLog.logException(error);
            }
        }, "Jetty12HttpServer.reconnect").start();
    }

    @Override
    public boolean withSSL() {
        for (final Connector connector : this.server.getConnectors()) {
            if (connector.getName().startsWith("ssl")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getSslPort() {
        for (final Connector connector : this.server.getConnectors()) {
            if (connector.getName().startsWith("ssl")) {
                return ((ServerConnector) connector).getLocalPort();
            }
        }
        return -1;
    }

    int getHttpPort() {
        for (final Connector connector : this.server.getConnectors()) {
            if (connector.getName().startsWith("httpd")) {
                return ((ServerConnector) connector).getLocalPort();
            }
        }
        return -1;
    }

    @Override
    public void resetUser(final String username) {
        if (this.loginService != null) {
            this.loginService.reloadUser(username);
        }
    }

    @Override
    public void removeUser(final String username) {
        if (this.loginService != null) {
            this.loginService.removeCachedUser(username);
        }
    }

    @Override
    public String getVersion() {
        return "Jetty " + Server.getVersion();
    }

    @Override
    public int getServerThreads() {
        return this.server.getThreadPool().getThreads() - this.server.getThreadPool().getIdleThreads();
    }

    @Override
    public String toString() {
        return this.server.dump() + "\n\n" + this.server.getState();
    }

    /** Adapts YaCy's address/path rules to Jetty 12's native access handler. */
    public static final class AccessRules {

        private AccessRules() {
        }

        /** Validate one configured address/path expression with Jetty's active parser. */
        public static void checkPattern(final String pattern) {
            final InetAccessHandler validator = new InetAccessHandler();
            validator.include(HttpServerBootstrapConfig.InetPathAccessRule.parse(pattern).asJettyPattern());
        }

        static Handler wrap(final Handler next, final String configuredRules) {
            if (configuredRules == null || "*".equals(configuredRules.trim())) {
                return next;
            }
            final InetAccessHandler access = new InetAccessHandler(next);
            int accepted = 0;
            for (final String configuredRule : configuredRules.split(",")) {
                final String pattern = configuredRule.trim();
                if (pattern.isEmpty()) {
                    continue;
                }
                try {
                    final HttpServerBootstrapConfig.InetPathAccessRule rule = HttpServerBootstrapConfig.InetPathAccessRule.parse(pattern);
                    access.include(rule.asJettyPattern());
                    accepted++;
                } catch (final IllegalArgumentException error) {
                    ConcurrentLog.severe("SERVER", "Server Access Settings - IP filter: " + error.getMessage());
                }
            }
            if (accepted == 0) {
                return next;
            }
            final String loopbackAddress = InetAddress.getLoopbackAddress().getHostAddress();
            access.include(loopbackAddress);
            ConcurrentLog.info("SERVER", "activated IP access restriction to: ["
                    + loopbackAddress + "," + configuredRules + "]");
            return access;
        }
    }

    /** Last-resort exception barrier around Jetty 12's complete request pipeline. */
    static final class CrashProtectionHandler extends Handler.Wrapper {

        CrashProtectionHandler(final Handler next) {
            super(next);
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback)
                throws Exception {
            final AtomicBoolean completed = new AtomicBoolean();
            final Callback protectedCallback = new Callback() {
                @Override
                public void succeeded() {
                    if (completed.compareAndSet(false, true)) {
                        callback.succeeded();
                    }
                }

                @Override
                public void failed(final Throwable failure) {
                    handleFailure(request, response, callback, completed, failure);
                }
            };
            try {
                return super.handle(request, response, protectedCallback);
            } catch (final Throwable failure) {
                handleFailure(request, response, callback, completed, failure);
                return true;
            }
        }

        private static void handleFailure(final Request request, final Response response,
                final Callback callback, final AtomicBoolean completed, final Throwable failure) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            ConcurrentLog.severe("HTTP", "event=http.request subsystem=http result=exception method="
                    + request.getMethod() + " target=" + request.getHttpURI().getPath()
                    + " status=500 reason=" + failure.getMessage());
            if (response.isCommitted()) {
                callback.failed(failure);
                return;
            }
            Response.writeError(request, response, callback, 500, "Internal Server Error");
        }
    }

    /** Rejects CONNECT explicitly when transparent proxy support is disabled. */
    static final class DisabledProxyHandler extends Handler.Wrapper {

        private static final String REJECTION_MESSAGE = "Transparent proxy is disabled";
        private static final String REJECTION_HEADER = "X-YaCy-Proxy-Error";

        DisabledProxyHandler(final Handler next) {
            super(next);
        }

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback)
                throws Exception {
            if (HttpMethod.CONNECT.is(request.getMethod())) {
                response.getHeaders().put(REJECTION_HEADER, REJECTION_MESSAGE);
                Response.writeError(request, response, callback, 403, REJECTION_MESSAGE);
                return true;
            }
            return super.handle(request, response, callback);
        }
    }

    /** YaCy-branded EE8 error page for Jetty 12. */
    static final class ErrorPageHandler extends org.eclipse.jetty.ee8.nested.ErrorHandler {

        @Override
        protected void writeErrorPageBody(final HttpServletRequest request, final Writer writer,
                final int code, final String message, final boolean showStacks) throws IOException {
            final String uri = request.getRequestURI();
            this.writeErrorPageMessage(request, writer, code, message, uri);
            if (showStacks) {
                this.writeErrorPageStacks(request, writer);
            }
            writer.write("<br/><hr /><small>YaCy " + yacyBuildProperties.getVersion()
                    + "  - <i> powered by Jetty </i> - </small>");
            for (int i = 0; i < 20; i++) {
                writer.write("<br/>                                \n");
            }
        }
    }

    /** Jetty 12 EE8 adapter for YaCy's container-neutral administrator policy. */
    static final class AdminSecurityHandler extends ConstraintSecurityHandler {

        @Override
        protected void doStart() throws Exception {
            if (getAuthenticator() == null && "DIGEST".equalsIgnoreCase(getAuthMethod())) {
                setAuthenticator(createDigestAuthenticator());
            }
            super.doStart();
        }

        static DigestAuthenticator createDigestAuthenticator() {
            final DigestAuthenticator authenticator = new DigestAuthenticator();
            // YaCy stores the RFC 2617 MD5 HA1 value, not the clear-text password.
            authenticator.setAlgorithm("MD5");
            return authenticator;
        }

        @Override
        public void handle(final String pathInContext,
                final org.eclipse.jetty.ee8.nested.Request baseRequest,
                final HttpServletRequest request, final HttpServletResponse response)
                throws IOException, ServletException {
            AdminSecurity.AuthenticationContext.setSocketPeerIp(baseRequest.getRemoteAddr());
            try {
                final Switchboard switchboard = Switchboard.getSwitchboard();
                request.setAttribute(RequestHeader.EFFECTIVE_CLIENT_IP_ATTRIBUTE,
                        resolveTrustedClientIp(request,
                                switchboard.getConfig(
                                        SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED,
                                        SwitchboardConstants.SERVER_REVERSE_PROXY_TRUSTED_DEFAULT)));
                super.handle(pathInContext, baseRequest, request, response);
            } finally {
                AdminSecurity.AuthenticationContext.clear();
            }
        }

        @Override
        protected RoleInfo prepareConstraintInfo(final String pathInContext,
                final org.eclipse.jetty.ee8.nested.Request request) {
            final Switchboard switchboard = Switchboard.getSwitchboard();
            final String socketRemoteIp = request.getRemoteAddr();
            final String trackingRemoteIp = RequestHeader.client(request);
            serverAccessTracker.track(trackingRemoteIp, pathInContext);
            final AdminSecurity.AccessPolicy policy = new AdminSecurity.AccessPolicy(
                    switchboard.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, false),
                    switchboard.isRobinsonMode() && !switchboard.isPublicRobinson(),
                    switchboard.getConfigBool(SwitchboardConstants.PUBLIC_SEARCHPAGE, true),
                    switchboard.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false),
                    switchboard.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"),
                    switchboard.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""));
            final AdminSecurity.AccessPolicy.Decision decision = policy.decide(pathInContext, socketRemoteIp,
                    request.getHeader(RequestHeader.REFERER),
                    request.getHeader(RequestHeader.AUTHORIZATION));
            if (decision == AdminSecurity.AccessPolicy.Decision.PUBLIC) {
                return super.prepareConstraintInfo(pathInContext, request);
            }
            if (decision == AdminSecurity.AccessPolicy.Decision.LOCAL_BYPASS) {
                return null;
            }
            final RoleInfo roleInfo = new RoleInfo();
            roleInfo.setChecked(true);
            roleInfo.addRole(SwitchboardConstants.ADMIN_ACCOUNT_ROLE);
            return roleInfo;
        }

        /** Resolve the trusted client address for routing and tracking, never for access control. */
        static String resolveTrustedClientIp(final HttpServletRequest request,
                final String trustedProxyPatterns) {
            final String socketRemoteIp = request.getRemoteAddr();
            if (!ProxyAccessPolicy.isClientAllowed(trustedProxyPatterns, socketRemoteIp)) {
                return socketRemoteIp;
            }
            final String forwardedRemoteIp = request.getHeader(RequestHeader.X_Real_IP);
            if (forwardedRemoteIp == null) {
                return socketRemoteIp;
            }
            final String candidate = forwardedRemoteIp.trim();
            return InetAddresses.isInetAddress(candidate) ? candidate : socketRemoteIp;
        }
    }

    /** Jetty 12 login-service adapter for YaCy's single built-in administrator. */
    static final class AdminLoginService extends HashLoginService {

        record AdminCredentialConfig(String username, String hash, String realm) {
        }

        private final Supplier<AdminCredentialConfig> credentials;
        private UserStore userStore;

        AdminLoginService() {
            this(AdminLoginService::configuredCredentials);
        }

        AdminLoginService(final Supplier<AdminCredentialConfig> credentials) {
            this.credentials = credentials;
        }

        private static AdminCredentialConfig configuredCredentials() {
            final Switchboard switchboard = Switchboard.getSwitchboard();
            return new AdminCredentialConfig(
                    switchboard.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"),
                    switchboard.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""),
                    switchboard.getConfig(SwitchboardConstants.ADMIN_REALM, ""));
        }

        @Override
        protected void doStart() throws Exception {
            this.userStore = new UserStore();
            this.setUserStore(this.userStore);
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            if (this.userStore != null) {
                this.userStore.stop();
                this.userStore = null;
            }
        }

        @Override
        protected UserPrincipal loadUserInfo(final String username) {
            if (username == null || username.isEmpty()) {
                return null;
            }
            UserPrincipal user = super.loadUserInfo(username);
            if (user != null || this.userStore == null) {
                return user;
            }
            final AdminCredentialConfig configured = this.credentials.get();
            if (!username.equals(configured.username())) {
                return null;
            }
            final AdminCredential credential = new AdminCredential(
                    username, configured.hash(), configured.realm(), configured.username());
            this.userStore.addUser(username, credential,
                    new String[] {SwitchboardConstants.ADMIN_ACCOUNT_ROLE});
            user = this.userStore.getUserPrincipal(username);
            return user;
        }

        synchronized boolean removeCachedUser(final String username) {
            if (this.userStore == null || this.userStore.getUserPrincipal(username) == null) {
                return false;
            }
            this.userStore.removeUser(username);
            return true;
        }

        synchronized void reloadUser(final String username) {
            this.removeCachedUser(username);
            this.loadUserInfo(username);
        }
    }

    /** Jetty 12 credential facade over YaCy's container-neutral password verifier. */
    static final class AdminCredential extends Credential {

        private static final long serialVersionUID = 1L;

        private final String username;
        private final String configuredHash;
        private final String realm;
        private final String configuredAdminUser;
        private final Credential digestCredential;
        private final BooleanSupplier localhostRequest;

        AdminCredential(final String username, final String configuredHash,
                final String realm, final String configuredAdminUser) {
            this(username, configuredHash, realm, configuredAdminUser,
                    AdminSecurity.AuthenticationContext::isLocalhostRequest);
        }

        AdminCredential(final String username, final String configuredHash,
                final String realm, final String configuredAdminUser,
                final BooleanSupplier localhostRequest) {
            this.username = username;
            this.configuredHash = configuredHash;
            this.realm = realm;
            this.configuredAdminUser = configuredAdminUser;
            this.localhostRequest = localhostRequest;
            this.digestCredential = configuredHash.startsWith("MD5:")
                    ? Credential.getCredential(configuredHash)
                    : null;
        }

        @Override
        public boolean check(final Object credentials) {
            if (credentials instanceof Credential) {
                return this.digestCredential != null
                        && ((Credential) credentials).check(this.digestCredential);
            }
            if (credentials instanceof String) {
                return AdminSecurity.checkAdminPassword(this.username, this.configuredHash,
                        this.realm, this.configuredAdminUser,
                        this.localhostRequest.getAsBoolean(), (String) credentials);
            }
            throw new UnsupportedOperationException("Unsupported administrator credential type");
        }
    }
}
