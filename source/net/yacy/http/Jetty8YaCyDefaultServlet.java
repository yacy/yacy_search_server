//  Jetty8YaCyDefaultServlet
//  ------------------------
//  Copyright 2013 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//  First released 2013 at http://yacy.net
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.InclusiveByteRange;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;

/* ------------------------------------------------------------ */
/**
 * YaCyDefaultServlet base on Jetty DefaultServlet.java 
 * handles static files and the YaCy servlets.
 */

/**
 * The default servlet. This servlet, normally mapped to /, provides the
 * handling for static content, OPTION and TRACE methods for the context. The
 * following initParameters are supported, these can be set either on the
 * servlet itself or as ServletContext initParameters :
 * <PRE>
 *  acceptRanges      If true, range requests and responses are
 *                    supported
 *
 *  dirAllowed        If true, directory listings are returned if no
 *                    welcome file is found. Else 403 Forbidden.
 *
 *  welcomeFile       name of the welcome file (default is "index.html", "welcome.html")
 *
 *
 *  resourceBase      Set to replace the context resource base
 *
 *  relativeResourceBase
 *                    Set with a pathname relative to the base of the
 *                    servlet context root. Useful for only serving static content out
 *                    of only specific subdirectories.
 *
 *  pathInfoOnly      If true, only the path info will be applied to the resourceBase
 *
 * </PRE>
 *
 */
public class Jetty8YaCyDefaultServlet extends YaCyDefaultServlet {

    private static final long serialVersionUID = 4900000000000001110L;
    

    /* ------------------------------------------------------------ */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath;
        String pathInfo;
        Enumeration<String> reqRanges = null;
        boolean included = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null; 
        if (included) {
            servletPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            if (servletPath == null) {
                servletPath = request.getServletPath();
                pathInfo = request.getPathInfo();
            }
        } else {
            servletPath = _pathInfoOnly ? "/" : request.getServletPath();
            pathInfo = request.getPathInfo();

            // Is this a Range request?
            reqRanges = request.getHeaders(HeaderFramework.RANGE);
            if (!hasDefinedRange(reqRanges)) {
                reqRanges = null;
            }
        }
        
        if (pathInfo.startsWith("/currentyacypeer/")) pathInfo = pathInfo.substring(16);
        String pathInContext = URIUtil.addPaths(servletPath, pathInfo);
        boolean endsWithSlash = (pathInfo == null ? request.getServletPath() : pathInfo).endsWith(URIUtil.SLASH);

        // Find the resource 
        Resource resource = null;

        try {
            // find resource
            resource = getResource(pathInContext);

            // Look for a class resource
            boolean hasClass = false;
            if (reqRanges == null && !endsWithSlash) {
                final int p = pathInContext.lastIndexOf('.');
                if (p >= 0) {
                    String pathofClass = pathInContext.substring(0, p) + ".class";
                    Resource classresource = _resourceBase.addPath(pathofClass);
                    // Does a class resource exist?
                    if (classresource != null && classresource.exists() && !classresource.isDirectory()) {
                        hasClass = true;
                    }
                }
            }

            if (ConcurrentLog.isFine("FILEHANDLER")) {
                ConcurrentLog.fine("FILEHANDLER","YaCyDefaultServlet: uri=" + request.getRequestURI() + " resource=" + resource);
            }

            // Handle resource
            if (!hasClass && (resource == null || !resource.exists())) {
                if (included) {
                    throw new FileNotFoundException("!" + pathInContext);
                }
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else if (!resource.isDirectory()) {
                if (endsWithSlash && pathInContext.length() > 1) {
                    String q = request.getQueryString();
                    pathInContext = pathInContext.substring(0, pathInContext.length() - 1);
                    if (q != null && q.length() != 0) {
                        pathInContext += "?" + q;
                    }
                    response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(_servletContext.getContextPath(), pathInContext)));
                } else {
                    if (hasClass) { // this is a YaCy servlet, handle the template
                        handleTemplate(pathInfo, request, response);
                    } else {
                        if (included || passConditionalHeaders(request, response, resource)) {
                            sendData(request, response, included, resource, reqRanges);
                        }
                    }
                }
            } else { // resource is directory
                String welcome;

                if (!endsWithSlash || (pathInContext.length() == 1)) {
                    StringBuffer buf = request.getRequestURL();
                    synchronized (buf) {
                        int param = buf.lastIndexOf(";");
                        if (param < 0) {
                            buf.append('/');
                        } else {
                            buf.insert(param, '/');
                        }
                        String q = request.getQueryString();
                        if (q != null && q.length() != 0) {
                            buf.append('?');
                            buf.append(q);
                        }
                        response.setContentLength(0);
                        response.sendRedirect(response.encodeRedirectURL(buf.toString()));
                    }
                } // else look for a welcome file
                else if (null != (welcome = getWelcomeFile(pathInContext))) {
                    ConcurrentLog.fine("FILEHANDLER","welcome={}" + welcome);

                    // Forward to the index
                    RequestDispatcher dispatcher = request.getRequestDispatcher(welcome);
                    if (dispatcher != null) {
                        if (included) {
                            dispatcher.include(request, response);
                        } else {
                            dispatcher.forward(request, response);
                        }
                    }
                } else {
                    if (included || passConditionalHeaders(request, response, resource)) {
                        sendDirectory(request, response, resource, pathInContext);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            ConcurrentLog.logException(e);
            if (!response.isCommitted()) {
                response.sendError(500, e.getMessage());
            }
        } finally {
            if (resource != null) {
                resource.release();
            }
        }
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void sendData(HttpServletRequest request,
            HttpServletResponse response,
            boolean include,
            Resource resource,
            Enumeration reqRanges)
            throws IOException {

        final long content_length = resource.length();

        // Get the output stream (or writer)
        OutputStream out;
        boolean written;
        try {
            out = response.getOutputStream();

            // has a filter already written to the response?
            written = out instanceof HttpOutput
                    ? ((HttpOutput) out).isWritten()
                    : AbstractHttpConnection.getCurrentConnection().getGenerator().isWritten();
        } catch (IllegalStateException e) {
            out = new WriterOutputStream(response.getWriter());
            written = true; // there may be data in writer buffer, so assume written
        }

        if (reqRanges == null || !reqRanges.hasMoreElements() || content_length < 0) {
            //  if there were no ranges, send entire entity
            if (include) {
                resource.writeTo(out, 0, content_length);
            } else {
                writeHeaders(response, resource, written ? -1 : content_length);
                resource.writeTo(out, 0, content_length);
            }
        } else {
            // Parse the satisfiable ranges
            List ranges = InclusiveByteRange.satisfiableRanges(reqRanges, content_length);

            //  if there are no satisfiable ranges, send 416 response
            if (ranges == null || ranges.size() == 0) {
                writeHeaders(response, resource, content_length);
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        InclusiveByteRange.to416HeaderRangeString(content_length));
                resource.writeTo(out, 0, content_length);
                return;
            }

            //  if there is only a single valid range (must be satisfiable
            //  since were here now), send that range with a 216 response
            if (ranges.size() == 1) {
                InclusiveByteRange singleSatisfiableRange =
                        (InclusiveByteRange) ranges.get(0);
                long singleLength = singleSatisfiableRange.getSize(content_length);
                writeHeaders(response, resource, singleLength);
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        singleSatisfiableRange.toHeaderRangeString(content_length));
                resource.writeTo(out, singleSatisfiableRange.getFirst(content_length), singleLength);
                return;
            }

            //  multiple non-overlapping valid ranges cause a multipart
            //  216 response which does not require an overall
            //  content-length header
            //
            writeHeaders(response, resource, -1);
            String mimetype = response.getContentType();
            if (mimetype == null) {
                ConcurrentLog.warn("FILEHANDLER","YaCyDefaultServlet: Unknown mimetype for " + request.getRequestURI());
            }
            MultiPartOutputStream multi = new MultiPartOutputStream(out);
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

            // If the request has a "Request-Range" header then we need to
            // send an old style multipart/x-byteranges Content-Type. This
            // keeps Netscape and acrobat happy. This is what Apache does.
            String ctp;
            if (request.getHeader(HttpHeaders.REQUEST_RANGE) != null) {
                ctp = "multipart/x-byteranges; boundary=";
            } else {
                ctp = "multipart/byteranges; boundary=";
            }
            response.setContentType(ctp + multi.getBoundary());

            InputStream in = resource.getInputStream();
            long pos = 0;

            // calculate the content-length
            int length = 0;
            String[] header = new String[ranges.size()];
            for (int i = 0; i < ranges.size(); i++) {
                InclusiveByteRange ibr = (InclusiveByteRange) ranges.get(i);
                header[i] = ibr.toHeaderRangeString(content_length);
                length +=
                        ((i > 0) ? 2 : 0)
                        + 2 + multi.getBoundary().length() + 2
                        + (mimetype == null ? 0 : HeaderFramework.CONTENT_TYPE.length() + 2 + mimetype.length()) + 2
                        + HeaderFramework.CONTENT_RANGE.length() + 2 + header[i].length() + 2
                        + 2
                        + (ibr.getLast(content_length) - ibr.getFirst(content_length)) + 1;
            }
            length += 2 + 2 + multi.getBoundary().length() + 2 + 2;
            response.setContentLength(length);

            for (int i = 0; i < ranges.size(); i++) {
                InclusiveByteRange ibr = (InclusiveByteRange) ranges.get(i);
                multi.startPart(mimetype, new String[]{HeaderFramework.CONTENT_RANGE + ": " + header[i]});

                long start = ibr.getFirst(content_length);
                long size = ibr.getSize(content_length);
                if (in != null) {
                    // Handle non cached resource
                    if (start < pos) {
                        in.close();
                        in = resource.getInputStream();
                        pos = 0;
                    }
                    if (pos < start) {
                        in.skip(start - pos);
                        pos = start;
                    }

                    FileUtils.copy(in, multi, size);
                    pos += size;
                } else // Handle cached resource
                {
                    (resource).writeTo(multi, start, size);
                }

            }
            if (in != null) {
                in.close();
            }
            multi.close();
        }
    }
}
