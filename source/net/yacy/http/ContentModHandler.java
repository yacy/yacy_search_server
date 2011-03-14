package net.yacy.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Request;

import org.eclipse.jetty.server.handler.HandlerWrapper;

/**
 * jetty http handler:
 * Handles Server-side Includes, used for trickling display of search results
 */
public abstract class ContentModHandler extends HandlerWrapper implements Handler, HandlerContainer {
	
	public ContentModHandler() {
		super();
	}
	
	public ContentModHandler(Handler h) {
		super();
		this.setHandler(h);
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		// wrap response
		ContentModResponseWrapper wrapped_response = new ContentModResponseWrapper(response);
		super.handle(target, baseRequest, request, wrapped_response);
		wrapped_response.commit(this, request, response);
	}
	
	protected abstract void doContentMod(byte[] in, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException ;
	
	private class ContentModResponseWrapper extends HttpServletResponseWrapper {
		
		private HttpServletResponse wrappedResponse;
		private ByteArrayServletOutputStream wrappedOutputStream = new ByteArrayServletOutputStream();

		public ContentModResponseWrapper(HttpServletResponse response) {
			super(response);
			wrappedResponse = response;
		}
		
		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			return wrappedOutputStream;
		}
		
		public void commit(ContentModHandler cmh, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
			cmh.doContentMod(wrappedOutputStream.getBuffer(), request, response);
		}
	}
	
	private class ByteArrayServletOutputStream extends ServletOutputStream {
		
		private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		public ByteArrayServletOutputStream() {
			super();
		}

		@Override
		public void write(int b) throws IOException {
			buffer.write(b);
		}
		
		public void write(byte[] b) throws IOException {
			buffer.write(b);
		}
		
		public byte[] getBuffer() {
			return buffer.toByteArray();
		}
		
	}

}
