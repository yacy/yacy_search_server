package net.yacy.http;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

/**
 * interface to embedded jetty http server
 */
public class HttpServer {
	
	private Server server = new Server();
	
	/**
	 * @param port TCP Port to listen for http requests
	 */
	public HttpServer(int port) {
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        //connector.setThreadPool(new QueuedThreadPool(20));
        server.addConnector(connector);
        
        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(true);
        resource_handler.setWelcomeFiles(new String[]{ "index.html" });
 
        resource_handler.setResourceBase("htroot/");
        
        ContextHandler context = new ContextHandler();
        context.setContextPath("/");
        context.setHandler(new SSIHandler(new TemplateHandler()));
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { context, resource_handler, new DefaultHandler() });
        server.setHandler(handlers);
	}
	
	public void start() throws Exception {
        server.start();
	}

	public void initYaCyServer() {
		// TODO: delete?
		// create directories
	}
	
	public void stop() throws Exception {
		server.stop();
        server.join();
	}

	/**
	 * just for testing and debugging
	 */
	public static void main(String[] args) throws Exception {
		HttpServer server = new HttpServer(8090);
		server.start();
		server.stop();
	}

}
