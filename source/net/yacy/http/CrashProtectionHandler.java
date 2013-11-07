package net.yacy.http;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

public class CrashProtectionHandler extends HandlerWrapper implements Handler, HandlerContainer {
	
	public CrashProtectionHandler() {
		super();
	}
	
	public CrashProtectionHandler(Handler h) {
		super();
		this.setHandler(h);
	}
	

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		try {
			super.handle(target, baseRequest, request, response);
		} catch (Exception e) {
			// handle all we can
			writeResponse(request, response, e);
                        baseRequest.setHandled(true);
		}
	}
	
	private void writeResponse(HttpServletRequest request, HttpServletResponse response, Exception exc) throws IOException {
            PrintWriter out = response.getWriter();
            out.println("Ops!");
            out.println();
            out.println("Message: " + exc.getMessage());
            exc.printStackTrace(out);
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}
}
