//
//  ContentModHandler
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;

import org.eclipse.jetty.server.handler.HandlerWrapper;

/**
 * abstract jetty http handler:
 * used to change answer, provided by other handler
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
		if(baseRequest.isHandled()) {
			//baseRequest.setHandled(false);
			this.doContentMod(wrapped_response.getBuffer(), request, response);
			//baseRequest.setHandled(true);
		}
	}
	
	/**
	 * method doing editing of answer, overwrite this!
	 * @param in output of nested handler, to be processed
	 * @param request original request
	 * @param response original response object with outputstream to fill
	 * @throws IOException
	 * @throws ServletException
	 */
	protected abstract void doContentMod(byte[] in, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException ;
	
	/**
	 * response object, which holds outputstream in bytearray back 
	 */
	private class ContentModResponseWrapper extends HttpServletResponseWrapper {
		
		private ByteArrayServletOutputStream wrappedOutputStream = new ByteArrayServletOutputStream();

		public ContentModResponseWrapper(HttpServletResponse response) {
			super(response);
		}
		
		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			return wrappedOutputStream;
		}
		
		/**
		 * get bytearray hold back from outputstream
		 * @return
		 */
		public byte[] getBuffer() {
			return wrappedOutputStream.getBuffer();
		}
		
		public void setContentType(String mime) {
			super.setContentType(mime);
		}
	}
	
	/**
	 * ByteArrayOutputStream wrapped into a ServletOutputStream
	 */
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
