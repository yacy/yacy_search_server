//
//  Jetty8HttpServerImpl
//  Copyright 2011 by Florian Richter
//  First released 13.04.2011 at http://yacy.net
//  
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.KeyStore;
import java.util.EnumSet;
import java.util.Enumeration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import javax.servlet.DispatcherType;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.http.servlets.GSAsearchServlet;
import net.yacy.http.servlets.SolrServlet;
import net.yacy.http.servlets.YaCyDefaultServlet;
import net.yacy.http.servlets.YaCyProxyServlet;
import net.yacy.http.servlets.SolrServlet.Servlet404;
import net.yacy.search.Switchboard;
import net.yacy.utils.PKCS12Tool;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * class to embedded Jetty 8 http server into YaCy
 */
public class Jetty8HttpServerImpl implements YaCyHttpServer {

    private final Server server;
    private final int sslport = 8443; // the port to use for https

    /**
     * @param port TCP Port to listen for http requests
     */
    public Jetty8HttpServerImpl(int port) {
        Switchboard sb = Switchboard.getSwitchboard();
        
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        connector.setName("httpd:"+Integer.toString(port));
        server.addConnector(connector);
        
        // add ssl/https connector
        boolean useSSL = sb.getConfigBool("server.https", false);
        if (useSSL) {
            final SslContextFactory sslContextFactory = new SslContextFactory();
            final SSLContext sslContext = initSslContext(sb);
            if (sslContext != null) {
                sslContextFactory.setSslContext(sslContext);

                SslSelectChannelConnector sslconnector = new SslSelectChannelConnector(sslContextFactory);
                sslconnector.setPort(sslport);
                sslconnector.setName("ssld:" + Integer.toString(sslport)); // name must start with ssl (for withSSL() to work correctly)

                server.addConnector(sslconnector);
                ConcurrentLog.info("SERVER", "SSL support initialized successfully on port " + sslport);
            }
        }

        YacyDomainHandler domainHandler = new YacyDomainHandler();
        domainHandler.setAlternativeResolver(sb.peers);
        
        //add SolrServlet
        ServletContextHandler solrContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        solrContext.setContextPath("/solr");       
        solrContext.addServlet(new ServletHolder(Servlet404.class),"/*");  
     
        solrContext.addFilter(new FilterHolder(SolrServlet.class), "/*", EnumSet.of(DispatcherType.REQUEST));

        // configure root context
        ServletContextHandler htrootContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        htrootContext.setContextPath("/");  
        ServletHolder sholder = new ServletHolder(YaCyDefaultServlet.class);
        sholder.setInitParameter("resourceBase", "htroot");
        //sholder.setInitParameter("welcomeFile", "index.html"); // default is index.html, welcome.html
        htrootContext.addServlet(sholder,"/*");    
        
        // add proxy?url= servlet
        ServletHolder proxyholder= new ServletHolder(YaCyProxyServlet.class);
        htrootContext.addServlet(proxyholder,"/proxy.html");
        
        // add GSA servlet
        ServletHolder gsaholder = new ServletHolder (GSAsearchServlet.class);
        htrootContext.addServlet(gsaholder,"/gsa/search");

        // define list of YaCy specific general handlers
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] 
           {domainHandler, new ProxyCacheHandler(), new ProxyHandler()}); 

        // context handler for dispatcher and security (hint: dispatcher requires a context)
        ContextHandler context = new ContextHandler();
        context.setContextPath("/");
        context.setHandler(handlers);

        // make YaCy handlers (in context) and servlet context handlers available (both contain root context "/")
        // logic: 1. YaCy handlers are called if request not handled (e.g. proxy) then servlets handle it
        ContextHandlerCollection allrequesthandlers = new ContextHandlerCollection();
        allrequesthandlers.addHandler(context);
        allrequesthandlers.addHandler(solrContext);
        allrequesthandlers.addHandler(htrootContext);    
        allrequesthandlers.addHandler(new DefaultHandler()); // if not handled by other handler 
        
        // wrap all handlers by security handler
        Jetty8YaCySecurityHandler securityHandler = new Jetty8YaCySecurityHandler();
        securityHandler.setLoginService(new YaCyLoginService());
        securityHandler.setRealmName("YaCy Admin Interface");
        securityHandler.setHandler(new CrashProtectionHandler(allrequesthandlers));
                    
        server.setHandler(securityHandler);
    }

    /**
     * start http server
     */
    @Override
    public void startupServer() throws Exception {
        // option to finish running requests on shutdown
        server.setGracefulShutdown(3000);
        server.setStopAtShutdown(true);
        server.start();
    }

    /**
     * stop http server and wait for it
     */
    @Override
    public void stop() throws Exception {
        server.stop();  
        server.join();
    }

    @Override
    public void setMaxSessionCount(int maxBusy) {
        // TODO:
    }

    @Override
    public boolean withSSL() {        
        Connector[] clist = server.getConnectors(); 
        for (Connector c:clist) {
            if (c.getName().startsWith("ssl")) return true;
        }
        return false;
    }
  
    @Override
    public int getSslPort() {
        return sslport;
    }
    
    /**
     * reconnect with new port settings (after waiting milsec) - routine returns
     * immediately
     *
     * @param milsec wait time
     */
    @Override
    public void reconnect(final int milsec) {

        new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(milsec);
                } catch (final InterruptedException e) {
                    ConcurrentLog.logException(e);
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
                try { // reconnect with new settings (instead to stop/start server, just manipulate connectors
                    final Connector[] cons = server.getConnectors();
                    final int port = Switchboard.getSwitchboard().getConfigInt("port", 8090);
                    for (Connector con : cons) {
                        if (con.getName().startsWith("httpd") && con.getPort() != port) {
                            con.close();
                            con.stop();
                            if (!con.isStopped()) {
                                ConcurrentLog.warn("SERVER", "Reconnect: Jetty Connector failed to stop");
                            }
                            con.setPort(port);
                            con.start();
                            ConcurrentLog.fine("SERVER", "set new port for Jetty connector " + con.getName());
                        }
                    }
                } catch (Exception ex) {
                    ConcurrentLog.logException(ex);
                }
            }
        }.start();
    }

    @Override
    public InetSocketAddress generateSocketAddress(String extendedPortString) throws SocketException {
        // parsing the port configuration
        String bindIP = null;
        int bindPort;

        int pos = extendedPortString.indexOf(':');
        if (pos != -1) {
            bindIP = extendedPortString.substring(0,pos).trim();
            extendedPortString = extendedPortString.substring(pos+1);

            if (bindIP.length() > 0 && bindIP.charAt(0) == '#') {
                final String interfaceName = bindIP.substring(1);
                String hostName = null;
                if (ConcurrentLog.isFine("SERVER")) ConcurrentLog.fine("SERVER","Trying to determine IP address of interface '" + interfaceName + "'.");

                final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        final NetworkInterface interf = interfaces.nextElement();
                        if (interf.getName().equalsIgnoreCase(interfaceName)) {
                            final Enumeration<InetAddress> addresses = interf.getInetAddresses();
                            if (addresses != null) {
                                while (addresses.hasMoreElements()) {
                                    final InetAddress address = addresses.nextElement();
                                    if (address instanceof Inet4Address) {
                                        hostName = address.getHostAddress();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (hostName == null) {
                    ConcurrentLog.warn("SERVER", "Unable to find interface with name '" + interfaceName + "'. Binding server to all interfaces");
                    bindIP = null;
                } else {
                    ConcurrentLog.info("SERVER", "Binding server to interface '" + interfaceName + "' with IP '" + hostName + "'.");
                    bindIP = hostName;
                }
            }
        }
        bindPort = Integer.parseInt(extendedPortString);

        return (bindIP == null)
                ? new InetSocketAddress(bindPort)
                : new InetSocketAddress(bindIP, bindPort);

    }

    @Override
    public int getMaxSessionCount() {
        return server.getThreadPool().getThreads();
    }

    @Override
    public int getJobCount() {
        return getMaxSessionCount() - server.getThreadPool().getIdleThreads(); // TODO:
    }

    @Override
    public String getVersion() {
        return "Jetty " + Server.getVersion();
    }

    /**
     * Init SSL Context from config settings
     * @param sb Switchboard
     * @return default or sslcontext according to config
     */
    private SSLContext initSslContext(Switchboard sb) {

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
                    final FileOutputStream ksOut = new FileOutputStream(keyStoreFileName);
                    ks.store(ksOut, keyStorePwd.toCharArray());
                    ksOut.close();
 
                    // storing path to keystore into config file
                    sb.setConfig("keyStore", keyStoreFileName);
                }
 
                // importing certificate
                pkcsTool.importToJKS(keyStoreFileName, keyStorePwd);
 
                // removing entries from config file
                sb.setConfig("pkcs12ImportFile", "");
                sb.setConfig("keyStorePassword", "");
 
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
            ks.load(stream, keyStorePwd.toCharArray());
            stream.close();
 
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
            System.exit(0);
            return null;
        }
    }
    
}
