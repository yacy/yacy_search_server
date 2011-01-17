package net.yacy.http;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class HttpServer {
	
	public static void initYaCyServer() {
		// create directories
	}
	
	/**
	 * @param port TCP Port to listen for http requests
	 * @throws Exception 
	 */
	public static void runHttpServer(int port) throws Exception {
        Server server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        //connector.setThreadPool(new QueuedThreadPool(20));
        server.addConnector(connector);
        
        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(true);
        resource_handler.setWelcomeFiles(new String[]{ "index.html" });
 
        resource_handler.setResourceBase("DATA/HTDOCS");
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { new TemplateHandler(), resource_handler, new DefaultHandler() });
        server.setHandler(handlers);
        
        server.start();
        server.join();
    }

	/**
	 * just for testing and debugging
	 */
	public static void main(String[] args) throws Exception {
		runHttpServer(8080);
	}

}
