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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.EnumSet;
import java.util.Enumeration;
import javax.servlet.DispatcherType;
import net.yacy.cora.federate.solr.SolrServlet;
import net.yacy.cora.federate.solr.SolrServlet.Servlet404;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import org.eclipse.jetty.server.Connector;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * class to embedded Jetty 8 http server into YaCy
 */
public class Jetty8HttpServerImpl implements YaCyHttpServer {

    private final Server server;

    /**
     * @param port TCP Port to listen for http requests
     */
    public Jetty8HttpServerImpl(int port) {
        Switchboard sb = Switchboard.getSwitchboard();
        
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        connector.setName("httpd:"+Integer.toString(port));
        //connector.setThreadPool(new QueuedThreadPool(20));
        server.addConnector(connector);
    	
        YacyDomainHandler domainHandler = new YacyDomainHandler();
        domainHandler.setAlternativeResolver(sb.peers);

        /*  this is now handled by YaCyDefaultServlet
        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(true);
        resource_handler.setWelcomeFiles(new String[]{"index.html"});
        resource_handler.setResourceBase("htroot/");
        */
        
        //add SolrServlet
        ServletContextHandler solrContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        solrContext.setContextPath("/solr");       
        solrContext.addServlet(new ServletHolder(Servlet404.class),"/*");  
     
        SolrServlet.initCore(sb.index.fulltext().getDefaultEmbeddedConnector());
        solrContext.addFilter(new FilterHolder(SolrServlet.class), "/*", EnumSet.of(DispatcherType.REQUEST));

        // configure root context
        ServletContextHandler htrootContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        htrootContext.setContextPath("/");  
        ServletHolder sholder = new ServletHolder(Jetty8YaCyDefaultServlet.class);
        sholder.setInitParameter("resourceBase", "htroot");
        //sholder.setInitParameter("welcomeFile", "index.html"); // default is index.html, welcome.html
        sholder.setInitParameter("gzip","false");
        htrootContext.addServlet(sholder,"/*");    
        
        // add proxy?url= servlet
        ServletHolder proxyholder= new ServletHolder(YaCyProxyServlet.class);
        htrootContext.addServlet(proxyholder,"/proxy.html");
        
        // add GSA servlet
        ServletHolder gsaholder = new ServletHolder (GSAsearchServlet.class);
        htrootContext.addServlet(gsaholder,"/gsa/search");

        // assemble the servlet handlers
        ContextHandlerCollection servletContext = new ContextHandlerCollection();                
        servletContext.setHandlers(new Handler[] { solrContext, htrootContext });        

        // define list of YaCy specific general handlers
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] 
           {domainHandler, new ProxyCacheHandler(), new ProxyHandler()
            /*, resource_handler, new DefaultHandler() */}); 

        // context handler for dispatcher and security (hint: dispatcher requires a context)
        ContextHandler context = new ContextHandler();
        context.setContextPath("/");
        context.setHandler(handlers);

        // make YaCy handlers (in context) and servlet context handlers available (both contain root context "/")
        // logic: 1. YaCy handlers are called if request not handled (e.g. proxy) then servlets handle it
        ContextHandlerCollection allrequesthandlers = new ContextHandlerCollection();
        allrequesthandlers.addHandler(context);
        allrequesthandlers.addHandler(servletContext);
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
        return false; // TODO:
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

}
