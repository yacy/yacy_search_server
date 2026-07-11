//
//  Jetty9HttpServerImpl
//  Copyright 2011 by Florian Richter
//  First released 13.04.2011 at https://yacy.net
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program in the file lgpl21.txt
//  If not, see <http://www.gnu.org/licenses/>.
//

package net.yacy.http;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.EnumSet;
import java.util.StringTokenizer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.servlet.DispatcherType;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.http.servlets.MonitorFilter;
import net.yacy.http.servlets.YaCyDefaultServlet;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.utils.PKCS12Tool;

/**
 * class to embedded Jetty 9 http server into YaCy
 */
public class Jetty9HttpServerImpl implements YaCyHttpServer {

    private final Server server;

    /**
     * @param port TCP Port to listen for http requests
     * @param host The network interface this connector binds to as an IP address or a hostname.
     */
    public Jetty9HttpServerImpl(final int port, final String host) {
        final Switchboard sb = Switchboard.getSwitchboard();
        final HttpServerBootstrapConfig bootstrap = HttpServerBootstrapConfig.from(sb, port, host);

        this.server = new Server();

        // remove the ConnectionInfo tracking entry (added per request by the MonitorFilter)
        // when the tcp connection closes; added as bean to each connector below
        final Connection.Listener connectionCloseMonitor = new Connection.Listener() {
            @Override
            public void onOpened(final Connection connection) {
            }
            @Override
            public void onClosed(final Connection connection) {
                final InetSocketAddress remote = connection.getEndPoint().getRemoteAddress();
                if (remote != null) {
                    ConnectionInfo.removeServerConnection(MonitorFilter.connectionId(remote.getAddress().getHostAddress(), remote.getPort()));
                }
            }
        };

        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setRequestHeaderSize(HttpServerBootstrapConfig.REQUEST_HEADER_SIZE);
        final HttpConnectionFactory hcf = new HttpConnectionFactory(httpConfig);
        final ServerConnector connector = new ServerConnector(this.server, null, null, null, bootstrap.acceptorCount(), -1, hcf);
        connector.setPort(bootstrap.httpPort());
        connector.setHost(bootstrap.bindHost());
        connector.setName("httpd-" + bootstrap.bindHost() + ":" + Integer.toString(bootstrap.httpPort()));
        connector.setIdleTimeout(HttpServerBootstrapConfig.CONNECTOR_IDLE_TIMEOUT_MILLIS);
        connector.setAcceptQueueSize(HttpServerBootstrapConfig.ACCEPT_QUEUE_SIZE);
        connector.addBean(connectionCloseMonitor);

        this.server.addConnector(connector);


        // add ssl/https connector
        final boolean useSSL = bootstrap.httpsEnabled();

        if (useSSL) {
            final SslContextFactory sslContextFactory = new SslContextFactory.Server();
            final SSLContext sslContext = this.initSslContext(sb);
            if (sslContext != null) {

                final int sslport = bootstrap.httpsPort();
                sslContextFactory.setSslContext(sslContext);

                // SSL HTTP Configuration
                final HttpConfiguration https_config = new HttpConfiguration();
                https_config.addCustomizer(new SecureRequestCustomizer());

                // SSL Connector
                final ServerConnector sslConnector = new ServerConnector(this.server,
                        new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                        new HttpConnectionFactory(https_config));
                sslConnector.setPort(sslport);
                sslConnector.setName("ssld:" + Integer.toString(sslport)); // name must start with ssl (for withSSL() to work correctly)
                sslConnector.setIdleTimeout(HttpServerBootstrapConfig.CONNECTOR_IDLE_TIMEOUT_MILLIS);
                sslConnector.addBean(connectionCloseMonitor);

                this.server.addConnector(sslConnector);
                ConcurrentLog.info("SERVER", "SSL support initialized successfully on port " + sslport);
            }
        }

        final YacyDomainHandler domainHandler = new YacyDomainHandler();
        domainHandler.setAlternativeResolver(sb.peers);

        // configure root context
        final WebAppContext htrootContext = new WebAppContext();
        htrootContext.setContextPath("/");
        final String htrootpath = bootstrap.htrootPath();
        ConcurrentLog.info("Jetty9HttpServerImpl", "htrootpath = " + htrootpath);
        htrootContext.setErrorHandler(new YaCyErrorHandler()); // handler for custom error page
        try {
            htrootContext.setBaseResource(Resource.newResource(htrootpath));

            // set web.xml to use
            // make use of Jetty feature to define web.xml other as default WEB-INF/web.xml
            // and to use a DefaultsDescriptor merged with a individual web.xml
            // use defaults/web.xml as default and look in DATA/SETTINGS for local addition/changes
            htrootContext.setDefaultsDescriptor(bootstrap.defaultsWebXml());
            final Resource webxml = Resource.newResource(bootstrap.overrideWebXml());
            if (webxml.exists()) {
                htrootContext.setDescriptor(webxml.getName());
            }

        } catch (final IOException ex) {
            if (htrootContext.getBaseResource() == null) {
                ConcurrentLog.severe("SERVER", "could not find directory: htroot ");
            } else {
                ConcurrentLog.warn("SERVER", "could not find: defaults/web.xml or DATA/SETTINGS/web.xml");
            }
        }

        // as fundamental component leave this hardcoded, other servlets may be defined in web.xml only
        final ServletHolder sholder = new ServletHolder(YaCyDefaultServlet.class);
        sholder.setInitParameter("resourceBase", htrootpath);
        sholder.setAsyncSupported(true); // needed for YaCyQoSFilter
        //sholder.setInitParameter("welcomeFile", "index.html"); // default is index.html, welcome.html
        htrootContext.addServlet(sholder, "/*");

        // as fundamental component this filter is hardcoded too: it feeds the
        // Connections_p.html monitoring and rejects requests above the connection limit
        final FilterHolder monitorFilter = new FilterHolder(MonitorFilter.class);
        monitorFilter.setAsyncSupported(true);
        htrootContext.addFilter(monitorFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        final GzipHandler gzipHandler = new GzipHandler();
        /*
         * Decompression of incoming requests body is required for index distribution
         * APIs /yacy/transferRWI.html and /yacy/transferURL.html This was previously
         * handled by a GZIPRequestWrapper in the YaCyDefaultServlet.
         */
        gzipHandler.setInflateBufferSize(HttpServerBootstrapConfig.REQUEST_INFLATE_BUFFER_SIZE);

        if (!bootstrap.gzipResponsesEnabled()) {
            /* Gzip compression of responses can be disabled by user configuration */
            gzipHandler.setExcludedMethods(HttpMethod.GET.asString(), HttpMethod.POST.asString());
        }
        htrootContext.setGzipHandler(gzipHandler);

        // -----------------------------------------------------------------------------
        // here we set and map the mandatory servlets, needed for typical YaCy operation
        // to make sure they are available even if removed in individual web.xml
        // additional, optional or individual servlets or servlet mappings can be set in web.xml

        // in Jetty 9 servlet should be set only once
        // therefore only the settings in web.xml is used
        //add SolrSelectServlet
        //htrootContext.addServlet(SolrSelectServlet.class, "/solr/select"); // uses the default core, collection1
        //htrootContext.addServlet(SolrSelectServlet.class, "/solr/collection1/select"); // the same servlet, identifies the collection1 core using the path
        //htrootContext.addServlet(SolrSelectServlet.class, "/solr/webgraph/select"); // the same servlet, identifies the webgraph core using the path

        //htrootContext.addServlet(SolrServlet.class, "/solr/collection1/admin/luke");
        //htrootContext.addServlet(SolrServlet.class, "/solr/webgraph/admin/luke");

        // add proxy?url= servlet
        //htrootContext.addServlet(YaCyProxyServlet.class,"/proxy.html");

        // add GSA servlet
        //htrootContext.addServlet(GSAsearchServlet.class,"/gsa/search");
        // --- eof default servlet mappings --------------------------------------------

        // define list of YaCy specific general handlers
        final HandlerList handlers = new HandlerList();
        if (bootstrap.transparentProxyEnabled()) {
            // Proxyhandlers are only needed if feature activated (save resources if not used)
            ConcurrentLog.info("SERVER", "load Jetty handler for transparent proxy");
            handlers.setHandlers(new Handler[]{domainHandler, new ProxyCacheHandler(), new ProxyHandler()});
        } else {
            handlers.setHandlers(new Handler[]{domainHandler});
        }
        // context handler for dispatcher and security (hint: dispatcher requires a context)
        final ContextHandler context = new ContextHandler();
        context.setServer(this.server);
        context.setContextPath("/");
        context.setHandler(handlers);
        context.setMaxFormContentSize(HttpServerBootstrapConfig.MAX_FORM_CONTENT_SIZE);
        // make YaCy handlers (in context) and servlet context handlers available (both contain root context "/")
        // logic: 1. YaCy handlers are called if request not handled (e.g. proxy) then servlets handle it
        final ContextHandlerCollection allrequesthandlers = new ContextHandlerCollection();
        allrequesthandlers.setServer(this.server);
        allrequesthandlers.addHandler(context);
        allrequesthandlers.addHandler(htrootContext);
        allrequesthandlers.addHandler(new DefaultHandler()); // if not handled by other handler

        final YaCyLoginService loginService = new YaCyLoginService();
        // This is part of the built-in administrator's DIGEST password hash.
        // Changing it invalidates the configured administrator password hash.
        loginService.setName(bootstrap.adminRealm());

        final YaCySecurityHandler securityHandler = new YaCySecurityHandler();
        securityHandler.setLoginService(loginService);

        htrootContext.setSecurityHandler(securityHandler);

        // wrap all handlers
        final Handler crashHandler = new CrashProtectionHandler(this.server, allrequesthandlers);
        // check server access restriction and add InetAccessHandler if restrictions are needed
        // otherwise don't (to save performance)
        final String white = bootstrap.serverClientRules();
        if (!white.equals("*")) { // full ip (allowed ranges 0-255 or prefix  10.0-255,0,0-100  or CIDR notation 192.168.1.0/24)
            final StringTokenizer st = new StringTokenizer(white, ",");
            final InetAccessHandler whiteListHandler;
            if (white.contains("|")) {
                /*
                 * At least one pattern includes a path definition : we must use the
                 * InetPathAccessHandler as InetAccessHandler doesn't support path patterns
                 */
                whiteListHandler = new InetPathAccessHandler();
            } else {
                whiteListHandler = new InetAccessHandler();
            }
            int i = 0;
            while (st.hasMoreTokens()) {
                final String pattern = st.nextToken();
                try {
                    whiteListHandler.include(pattern);
                } catch (final IllegalArgumentException nex) { // catch format exception on wrong ip address pattern
                    ConcurrentLog.severe("SERVER", "Server Access Settings - IP filter: " + nex.getMessage());
                    continue;
                }
                i++;
            }
            if (i > 0) {
                final String loopbackAddress = InetAddress.getLoopbackAddress().getHostAddress();
                whiteListHandler.include(loopbackAddress);
                whiteListHandler.setHandler(crashHandler);
                this.server.setHandler(whiteListHandler);

                ConcurrentLog.info("SERVER","activated IP access restriction to: [" + loopbackAddress + "," + white +"]");
            } else {
                this.server.setHandler(crashHandler); // InetAccessHandler not needed
            }
        } else {
            this.server.setHandler(crashHandler); // InetAccessHandler not needed
        }
    }

    /**
     * start http server
     */
    public void startupServer() throws Exception {
        // option to finish running requests on shutdown
//        server.setGracefulShutdown(3000);
        this.server.setStopAtShutdown(true);
        this.server.start();
    }

    /**
     * stop http server and wait for it
     */
    public void stop() throws Exception {
        this.server.stop();
        this.server.join();
    }

    /**
     * @return true if ssl/https connector is available
     */
    public boolean withSSL() {
        final Connector[] clist = this.server.getConnectors();
        for (final Connector c:clist) {
            if (c.getName().startsWith("ssl")) return true;
        }
        return false;
    }

    /**
     * The port of actual running ssl connector
     * @return the ssl/https port or -1 if not active
     */
    public int getSslPort() {
        final Connector[] clist = this.server.getConnectors();
        for (final Connector c:clist) {
            if (c.getName().startsWith("ssl")) {
                final int port =((ServerConnector)c).getLocalPort();
                return port;
            }
        }
        return -1;
    }

    /**
     * reconnect with new port settings (after waiting milsec) - routine returns
     * immediately
     * checks http and ssl connector for new port settings
     * @param milsec wait time
     */
    public void reconnect(final int milsec) {

        new Thread("Jetty8HttpServer.reconnect") {

            @Override
            public void run() {
                if (milsec > 0) try {
                    Thread.sleep(milsec);
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
                try {
                    if (!Jetty9HttpServerImpl.this.server.isRunning() || Jetty9HttpServerImpl.this.server.isStopped()) {
                        Jetty9HttpServerImpl.this.server.start();
                    }

                    // reconnect with new settings (instead to stop/start server, just manipulate connectors
                    final Connector[] cons = Jetty9HttpServerImpl.this.server.getConnectors();
                    final int port = Switchboard.getSwitchboard().getLocalPort();
                    final int sslport = Switchboard.getSwitchboard().getConfigInt(SwitchboardConstants.SERVER_SSLPORT, 8443);
                    for (final Connector con : cons) {
                        // check http connector
                        if (con.getName().startsWith("httpd") && ((ServerConnector)con).getPort() != port) {
                            ((ServerConnector)con).close();
                            con.stop();
                            if (!con.isStopped()) {
                                ConcurrentLog.warn("SERVER", "Reconnect: Jetty Connector failed to stop");
                            }
                            ((ServerConnector)con).setPort(port);
                            con.start();
                            ConcurrentLog.info("SERVER", "set new port for Jetty connector " + con.getName());
                            continue;
                        }
                        // check https connector
                        if (con.getName().startsWith("ssl") && ((ServerConnector)con).getPort() != sslport) {
                            ((ServerConnector)con).close();
                            con.stop();
                            if (!con.isStopped()) {
                                ConcurrentLog.warn("SERVER", "Reconnect: Jetty Connector failed to stop");
                            }
                            ((ServerConnector)con).setPort(sslport);
                            con.start();
                            ConcurrentLog.info("SERVER", "set new port for Jetty connector " + con.getName());
                        }
                    }
                } catch (final Exception ex) {
                    ConcurrentLog.logException(ex);
                }
            }
        }.start();
    }

    /**
     * Forces the login service to reload the built-in administrator credentials
     * after they were changed in the configuration.
     * @param username
     */
    public void resetUser(final String username) {
        final YaCySecurityHandler hx = this.server.getChildHandlerByClass(YaCySecurityHandler.class);
        if (hx != null) {
            final YaCyLoginService loginservice = (YaCyLoginService) hx.getLoginService();
            if (loginservice.removeUser(username)) { // remove old credential from cache
                loginservice.loadUserInfo(username);
            }
        }
    }

    /**
     * Removes the built-in administrator from the login service cache.
     * @param username
     */
    public void removeUser(final String username) {
        final YaCySecurityHandler hx = this.server.getChildHandlerByClass(YaCySecurityHandler.class);
        if (hx != null) {
            final YaCyLoginService loginservice = (YaCyLoginService) hx.getLoginService();
            loginservice.removeUser(username);
        }
    }

    /**
     * get Jetty version
     * @return version_string
     */
    public String getVersion() {
        return "Jetty " + Server.getVersion();
    }

    /**
     * Init SSL Context from config settings
     * @param sb Switchboard
     * @return default or sslcontext according to config
     */
    private SSLContext initSslContext(final Switchboard sb) {

        // getting the keystore file name
        String keyStoreFileName = sb.getConfig("keyStore", "").trim();

        // getting the keystore pwd
        String keyStorePwd = sb.getConfig("keyStorePassword", "").trim();

        // take a look if we have something to import
        final String pkcs12ImportFile = sb.getConfig("pkcs12ImportFile", "").trim();

        // if no keyStore and no import is defined, then set the default key
        if (keyStoreFileName.isEmpty() && keyStorePwd.isEmpty() && pkcs12ImportFile.isEmpty()) {
            keyStoreFileName = "defaults/freeworldKeystore";
            keyStorePwd = "freeworld";
            sb.setConfig("keyStore", keyStoreFileName);
            sb.setConfig("keyStorePassword", keyStorePwd);
        }

        if (pkcs12ImportFile.length() > 0) {
            ConcurrentLog.info("SERVER", "Import certificates from import file '" + pkcs12ImportFile + "'.");

            try {
                // getting the password
                final String pkcs12ImportPwd = sb.getConfig("pkcs12ImportPwd", "").trim();

                // creating tool to import cert
                final PKCS12Tool pkcsTool = new PKCS12Tool(pkcs12ImportFile,pkcs12ImportPwd);

                // creating a new keystore file
                if (keyStoreFileName.isEmpty()) {
                    // using the default keystore name
                    keyStoreFileName = "DATA/SETTINGS/myPeerKeystore";

                    // creating an empty java keystore
                    final KeyStore ks = KeyStore.getInstance("JKS");
                    ks.load(null,keyStorePwd.toCharArray());
                    try (
                        /* Automatically closed by this try-with-resources statement */
                        final FileOutputStream ksOut = new FileOutputStream(keyStoreFileName);
                    ) {
                        ks.store(ksOut, keyStorePwd.toCharArray());
                    }

                    // storing path to keystore into config file
                    sb.setConfig("keyStore", keyStoreFileName);
                }

                // importing certificate
                pkcsTool.importToJKS(keyStoreFileName, keyStorePwd);

                // removing entries from config file
                sb.setConfig("pkcs12ImportFile", "");
                sb.setConfig("pkcs12ImportPwd", "");

                // deleting original import file
                // TODO: should we do this
            } catch (final Exception e) {
                ConcurrentLog.severe("SERVER", "Unable to import certificate from import file '" + pkcs12ImportFile + "'.",e);
            }
        } else if (keyStoreFileName.isEmpty()) return null;

        // get the ssl context
        try {
            ConcurrentLog.info("SERVER","Initializing SSL support ...");

            // creating a new keystore instance of type (java key store)
            if (ConcurrentLog.isFine("SERVER")) ConcurrentLog.fine("SERVER", "Initializing keystore ...");
            final KeyStore ks = KeyStore.getInstance("JKS");

            // loading keystore data from file
            if (ConcurrentLog.isFine("SERVER")) ConcurrentLog.fine("SERVER","Loading keystore file " + keyStoreFileName);
            final FileInputStream stream = new FileInputStream(keyStoreFileName);
            try {
                ks.load(stream, keyStorePwd.toCharArray());
            } finally {
                try {
                    stream.close();
                } catch(final IOException ioe) {
                    ConcurrentLog.warn("SERVER", "Could not close input stream on file " + keyStoreFileName);
                }
            }

            // creating a keystore factory
            if (ConcurrentLog.isFine("SERVER")) ConcurrentLog.fine("SERVER","Initializing key manager factory ...");
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks,keyStorePwd.toCharArray());

            // initializing the ssl context
            if (ConcurrentLog.isFine("SERVER")) ConcurrentLog.fine("SERVER","Initializing SSL context ...");
            final SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(kmf.getKeyManagers(), null, null);

            return sslcontext;
        } catch (final Exception e) {
            final String errorMsg = "FATAL ERROR: Unable to initialize the SSL Socket factory. " + e.getMessage();
            ConcurrentLog.severe("SERVER",errorMsg);
            System.out.println(errorMsg);
            return null;
        }
    }

    public int getServerThreads() {
        return this.server == null ? 0 : this.server.getThreadPool().getThreads() - this.server.getThreadPool().getIdleThreads();
    }

    @Override
    public String toString() {
        return this.server.dump() + "\n\n" + this.server.getState();
    }
}
