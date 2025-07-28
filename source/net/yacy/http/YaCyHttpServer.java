//
//  YaCyHttpServer
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
import java.security.KeyStore;
import java.util.StringTokenizer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
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
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ProcessorUtils;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.http.servlets.YaCyDefaultServlet;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.utils.PKCS12Tool;

/**
 * class to embedded Jetty 9 http server into YaCy
 */
public class YaCyHttpServer {

    private final Server server;

    /**
     * @param port TCP Port to listen for http requests
     */
    public YaCyHttpServer(final int port, final String host) {
        final Switchboard sb = Switchboard.getSwitchboard();

        this.server = new Server();

        final int cores = ProcessorUtils.availableProcessors();
        final int acceptors = Math.max(1, Math.min(4, cores/2)); // original: Math.max(1, Math.min(4,cores/8));

        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setRequestHeaderSize(16384);
        final HttpConnectionFactory hcf = new HttpConnectionFactory(httpConfig);
        final ServerConnector connector = new ServerConnector(this.server, null, null, null, acceptors, -1, hcf);
        connector.setPort(port);
        connector.setHost(host);
        connector.setName("httpd-" + host + ":" + Integer.toString(port));
        connector.setIdleTimeout(9000); // timout in ms when no bytes send / received
        connector.setAcceptQueueSize(128);


        this.server.addConnector(connector);


        // add ssl/https connector
        final boolean useSSL = sb.getConfigBool("server.https", false);

        if (useSSL) {
            final SslContextFactory sslContextFactory = new SslContextFactory.Server();
            final SSLContext sslContext = initSslContext(sb);
            if (sslContext != null) {

                final int sslport = sb.getConfigInt(SwitchboardConstants.SERVER_SSLPORT, 8443);
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
                sslConnector.setIdleTimeout(9000); // timout in ms when no bytes send / received

                this.server.addConnector(sslConnector);
                ConcurrentLog.info("SERVER", "SSL support initialized successfully on port " + sslport);
            }
        }

        final YacyDomainHandler domainHandler = new YacyDomainHandler();
        domainHandler.setAlternativeResolver(sb.peers);

        // configure root context
        final WebAppContext htrootContext = new WebAppContext();
        htrootContext.setContextPath("/");
        final String htrootpath = sb.appPath + "/" + sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT);
        ConcurrentLog.info("Jetty9HttpServerImpl", "htrootpath = " + htrootpath);
        htrootContext.setErrorHandler(new YaCyErrorHandler()); // handler for custom error page
        try {
            htrootContext.setBaseResource(Resource.newResource(htrootpath));

            // set web.xml to use
            // make use of Jetty feature to define web.xml other as default WEB-INF/web.xml
            // and to use a DefaultsDescriptor merged with a individual web.xml
            // use defaults/web.xml as default and look in DATA/SETTINGS for local addition/changes
            htrootContext.setDefaultsDescriptor(sb.appPath + "/defaults/web.xml");
            final Resource webxml = Resource.newResource(sb.dataPath + "/DATA/SETTINGS/web.xml");
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

        final GzipHandler gzipHandler = new GzipHandler();
        /*
         * Decompression of incoming requests body is required for index distribution
         * APIs /yacy/transferRWI.html and /yacy/transferURL.html This was previously
         * handled by a GZIPRequestWrapper in the YaCyDefaultServlet.
         */
        gzipHandler.setInflateBufferSize(4096);

        if (!sb.getConfigBool(SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP,
                SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP_DEFAULT)) {
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
        if (sb.getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false)) {
            // Proxyhandlers are only needed if feature activated (save resources if not used)
            ConcurrentLog.info("SERVER", "load Jetty handler for transparent proxy");
            handlers.setHandlers(new Handler[]{new MonitorHandler(), domainHandler, new ProxyCacheHandler(), new ProxyHandler()});
        } else {
            handlers.setHandlers(new Handler[]{new MonitorHandler(), domainHandler});
        }
        // context handler for dispatcher and security (hint: dispatcher requires a context)
        final ContextHandler context = new ContextHandler();
        context.setServer(this.server);
        context.setContextPath("/");
        context.setHandler(handlers);
        context.setMaxFormContentSize(1024 * 1024 * 10); // allow 10MB, large forms may be required during crawl starts with long lists
        final org.eclipse.jetty.util.log.Logger log = Log.getRootLogger();
        context.setLogger(log);
        // make YaCy handlers (in context) and servlet context handlers available (both contain root context "/")
        // logic: 1. YaCy handlers are called if request not handled (e.g. proxy) then servlets handle it
        final ContextHandlerCollection allrequesthandlers = new ContextHandlerCollection();
        allrequesthandlers.setServer(this.server);
        allrequesthandlers.addHandler(context);
        allrequesthandlers.addHandler(htrootContext);
        allrequesthandlers.addHandler(new DefaultHandler()); // if not handled by other handler

        final YaCyLoginService loginService = new YaCyLoginService();
        // this is very important (as it is part of the user password hash)
        // changes will ivalidate all current existing user-password-hashes (from userDB)
        loginService.setName(sb.getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy"));

        final YaCySecurityHandler securityHandler = new YaCySecurityHandler();
        securityHandler.setLoginService(loginService);

        htrootContext.setSecurityHandler(securityHandler);

        // wrap all handlers
        final Handler crashHandler = new CrashProtectionHandler(this.server, allrequesthandlers);
        // check server access restriction and add InetAccessHandler if restrictions are needed
        // otherwise don't (to save performance)
        final String white = sb.getConfig("serverClient", "*");
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
                    if (!YaCyHttpServer.this.server.isRunning() || YaCyHttpServer.this.server.isStopped()) {
                        YaCyHttpServer.this.server.start();
                    }

                    // reconnect with new settings (instead to stop/start server, just manipulate connectors
                    final Connector[] cons = YaCyHttpServer.this.server.getConnectors();
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
     * forces loginservice to reload user credentials
     * (used after setting new pwd in cfg file/db)
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
     * removes user from knowuser cache of loginservice
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
