//
//  SSIHandler
//  Copyright 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;


/**
 * Jetty Http HandlerWrapper applying server-side includes,
 * used for trickling display of search results
 */
public class SSIHandler extends ContentModHandler implements Handler, HandlerContainer {
	
	/**
	 * constructor
	 * @param h Handler to wrap (it's output is scanned for SSI-marks)
	 */
	public SSIHandler(Handler h) {
		super(h);
	}

	@Override
	protected void doContentMod(final byte[] in, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		ByteBuffer buffer = new ByteBuffer(in);
		OutputStream out = response.getOutputStream();
		int off = 0; // starting offset
        int p = buffer.indexOf("<!--#".getBytes(), off);
        int q;
        while (p >= 0) {
            q = buffer.indexOf("-->".getBytes(), p + 10);
            out.write(in, off, p - off);
            out.flush();
            parseSSI(buffer, p, request, response);
            off = q + 3;
            p = buffer.indexOf("<!--#".getBytes(), off);
        }
        out.write(in, off, in.length - off);
        out.flush();
	}
	
    private void parseSSI(final ByteBuffer in, final int off, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (in.startsWith("<!--#include virtual=\"".getBytes(), off)) {
            final int q = in.indexOf("\"".getBytes(), off + 22);
            if (q > 0) {
                final String path = in.toString(off + 22, q - off - 22);
                try {
                	//RequestDispatcher dispatcher = request.getRequestDispatcher("/"+path);
                    //
                    //TODO: dispatcher.include did not work in testing, temporarely added old writeContent - 
                    //  
                	//dispatcher.include(request, response);
                        writeContent(path,request,response);

                	response.flushBuffer();
                } catch (Exception e) {
                	ConcurrentLog.logException(e);
                	throw new ServletException();
                }
            }
        }
    }
    
    /**
     * temporarly added old writeContent for SSI to output include files
     * as request.parameter is not modifyable used quickfix to add parameter via request.setAttribute
     * and added temporarely the code in handler (TemplateHandler.handle) to read the request.attribute
     *  TODO: should finally be implementend otherwise eg. via HttpServletRequestWrapper
     */
    public void writeContent(final String path, HttpServletRequest request, HttpServletResponse response) {
        // check if there are arguments in path string
        String args;
        String fname = path;
        final int argpos = path.indexOf('?');
        if (argpos > 0) {
            fname = path.substring(0, argpos);
            args = path.substring(argpos + 1);
            String[] arglist = CommonPattern.AMP.split(args);
            for (String arg : arglist) {
                String[] argnv = arg.split("=");
                if (argnv.length > 1) {
                    request.setAttribute(argnv[0], argnv[1]);
                } else {
                    request.setAttribute(arg, null);
                }
            }
        }

        try {
            Handler h = this.getHandler();
            h.handle(fname, null, request, response);
        } catch (IOException ex) {
            ConcurrentLog.logException(ex);
        } catch (ServletException ex) {
            ConcurrentLog.logException(ex);
        }
    }    

}
