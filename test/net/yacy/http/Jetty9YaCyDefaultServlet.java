//  YaCyDefaultServlet
//  Copyright 2013 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//  First released 2013 at http://yacy.net
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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.AsyncContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.http.TemplateEngine;
import net.yacy.server.serverClassLoader;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.visualization.RasterPlotter;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import org.eclipse.jetty.http.HttpContent;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.InclusiveByteRange;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.util.QuotedStringTokenizer;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

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
 *  gzip              If set to true, then static content will be served as
 *                    gzip content encoded if a matching resource is
 *                    found ending with ".gz"
 *
 *  resourceBase      Set to replace the context resource base
 *
 *  resourceCache     If set, this is a context attribute name, which the servlet
 *                    will use to look for a shared ResourceCache instance.
 *
 *  relativeResourceBase
 *                    Set with a pathname relative to the base of the
 *                    servlet context root. Useful for only serving static content out
 *                    of only specific subdirectories.
 *
 *  pathInfoOnly      If true, only the path info will be applied to the resourceBase
 *
 *
 *  etags             If True, weak etags will be generated and handled.
 *
 * </PRE>
 */
public class Jetty9YaCyDefaultServlet extends YaCyDefaultServlet implements ResourceFactory {

    private static final long serialVersionUID = 4900000000000001110L;

    private boolean _gzip=true;

    /* ------------------------------------------------------------ */
    @Override
    public void init() throws UnavailableException {
        super.init();
        _gzip=getInitBoolean("gzip",_gzip);
    }

    /* ------------------------------------------------------------ */
    /**
     * get Resource to serve. Map a path to a resource. The default
     * implementation calls HttpContext.getResource but derived servlets may
     * provide their own mapping.
     *
     * @param pathInContext The path to find a resource for.
     * @return The resource to serve.
     */
    @Override
    public Resource getResource(String pathInContext) {
        Resource r = null;
        if (_relativeResourceBase != null) {
            pathInContext = URIUtil.addPaths(_relativeResourceBase, pathInContext);
        }

        try {
            if (_resourceBase != null) {
                r = _resourceBase.addPath(pathInContext);
            } else {
                URL u = _servletContext.getResource(pathInContext);
                r = Resource.newResource(u);
            }

            if (ConcurrentLog.isFine("YaCyDefaultServlet")) {
                ConcurrentLog.fine("YaCyDefaultServlet","Resource " + pathInContext + "=" + r);
            }
        } catch (IOException e) {
            // ConcurrentLog.logException(e);
        }

        return r;
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath = null;
        String pathInfo = null;
        Enumeration<String> reqRanges = null;
        Boolean included = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null;
        if (included != null && included.booleanValue()) {
            servletPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            if (servletPath == null) {
                servletPath = request.getServletPath();
                pathInfo = request.getPathInfo();
            }
        } else {
            included = Boolean.FALSE;
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

        // Find the resource and content
        Resource resource = null;
        HttpContent content = null;
        try {
            // Look for a class resource
            boolean hasClass = false;
            if (reqRanges == null && !endsWithSlash) {
                final int p = pathInContext.lastIndexOf('.');
                if (p >= 0) {
                    String pathofClass = pathInContext.substring(0, p) + ".class";
                    resource = getResource(pathofClass);
                    // Does a class resource exist?
                    if (resource != null && resource.exists() && !resource.isDirectory()) {
                        hasClass = true;
                    }
                }
            }
            // is gzip enabled?
            String pathInContextGz=null;
            boolean gzip=false;
            if (!included.booleanValue() && _gzip && reqRanges==null && !endsWithSlash )
            {
                // Look for a gzip resource
                pathInContextGz=pathInContext+".gz";                
                resource=getResource(pathInContextGz);
                // Does a gzip resource exist?
                if (resource!=null && resource.exists() && !resource.isDirectory())
                {
                    // Tell caches that response may vary by accept-encoding
                    response.addHeader(HttpHeader.VARY.asString(),HttpHeader.ACCEPT_ENCODING.asString());
                    
                    // Does the client accept gzip?
                    String accept=request.getHeader(HttpHeader.ACCEPT_ENCODING.asString());
                    if (accept!=null && accept.indexOf("gzip")>=0)
                        gzip=true;
                }
            }
            
            // find resource
            if (!gzip) resource = getResource(pathInContext);

            if (ConcurrentLog.isFine("YaCyDefaultServlet")) {
                ConcurrentLog.fine("YaCyDefaultServlet","uri=" + request.getRequestURI() + " resource=" + resource + (content != null ? " content" : ""));
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
                    // ensure we have content
                    if (content == null) {
                        content = new HttpContent.ResourceAsHttpContent(resource, _mimeTypes.getMimeByExtension(resource.toString()), response.getBufferSize(), _etags);
                    }

                    if (hasClass) { // this is a YaCy servlet, handle the template
                        handleTemplate(pathInfo, request, response);
                    } else {
                        if (included.booleanValue() || passConditionalHeaders(request, response, resource, content)) {
                            //sendData(request, response, included.booleanValue(), resource, content, reqRanges);
                            if (gzip) {
                                response.setHeader(HttpHeader.CONTENT_ENCODING.asString(), "gzip");
                                String mt = _servletContext.getMimeType(pathInContext);
                                if (mt != null) {
                                    response.setContentType(mt);
                                }
                            }
                            sendData(request, response, included.booleanValue(), resource, content, reqRanges);
                        }
                    }
                }
            } else {
                if (!endsWithSlash || (pathInContext.length() == 1 && request.getAttribute("org.eclipse.jetty.server.nullPathInfo") != null)) {
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
                } else { // look for a welcome file
                    String welcomeFileName = getWelcomeFile (pathInContext);
                    if (welcomeFileName != null) {
                        RequestDispatcher rd = request.getRequestDispatcher(welcomeFileName);
                        rd.forward(request, response);
                    } else { // send directory listing
                        content = new HttpContent.ResourceAsHttpContent(resource, _mimeTypes.getMimeByExtension(resource.toString()), _etags);
                        if (included.booleanValue() || passConditionalHeaders(request, response, resource, content)) {
                            sendDirectory(request, response, resource, pathInContext);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            ConcurrentLog.logException(e);
            if (!response.isCommitted()) {
                response.sendError(500, e.getMessage());
            }
        } finally {
            if (content != null) {
                content.release();
            } else if (resource != null) {
                resource.close();
            }
        }
    }

    /* ------------------------------------------------------------ */
    @Override
    protected boolean hasDefinedRange(Enumeration<String> reqRanges) {
        return (reqRanges != null && reqRanges.hasMoreElements());
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    /* ------------------------------------------------------------ */
    /* Check modification date headers.
     */
    @Override
    protected boolean passConditionalHeaders(HttpServletRequest request, HttpServletResponse response, Resource resource, HttpContent content)
            throws IOException {
        try {
            if (!HttpMethod.HEAD.is(request.getMethod())) {
                if (_etags) {
                    String ifm = request.getHeader(HttpHeader.IF_MATCH.asString());
                    if (ifm != null) {
                        boolean match = false;
                        if (content.getETag() != null) {
                            QuotedStringTokenizer quoted = new QuotedStringTokenizer(ifm, ", ", false, true);
                            while (!match && quoted.hasMoreTokens()) {
                                String tag = quoted.nextToken();
                                if (content.getETag().toString().equals(tag)) {
                                    match = true;
                                }
                            }
                        }

                        if (!match) {
                            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                            return false;
                        }
                    }

                    String if_non_match_etag = request.getHeader(HttpHeader.IF_NONE_MATCH.asString());
                    if (if_non_match_etag != null && content.getETag() != null) {
                        // Look for GzipFiltered version of etag
                        if (content.getETag().toString().equals(request.getAttribute("o.e.j.s.GzipFilter.ETag"))) {
                            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                            response.setHeader(HeaderFramework.ETAG, if_non_match_etag);
                            return false;
                        }

                        // Handle special case of exact match.
                        if (content.getETag().toString().equals(if_non_match_etag)) {
                            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                            response.setHeader(HeaderFramework.ETAG, content.getETag());
                            return false;
                        }

                        // Handle list of tags
                        QuotedStringTokenizer quoted = new QuotedStringTokenizer(if_non_match_etag, ", ", false, true);
                        while (quoted.hasMoreTokens()) {
                            String tag = quoted.nextToken();
                            if (content.getETag().toString().equals(tag)) {
                                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                                response.setHeader(HeaderFramework.ETAG, content.getETag());
                                return false;
                            }
                        }

                        // If etag requires content to be served, then do not check if-modified-since
                        return true;
                    }
                }

                // Handle if modified since
                String ifms = request.getHeader(HttpHeader.IF_MODIFIED_SINCE.asString());
                if (ifms != null) {
                    //Get jetty's Response impl
                    String mdlm = content.getLastModified();
                    if (mdlm != null && ifms.equals(mdlm)) {
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        if (_etags) {
                            response.setHeader(HeaderFramework.ETAG, content.getETag());
                        }
                        response.flushBuffer();
                        return false;
                    }

                    long ifmsl = request.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.asString());
                    if (ifmsl != -1 && resource.lastModified() / 1000 <= ifmsl / 1000) {
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        if (_etags) {
                            response.setHeader(HeaderFramework.ETAG, content.getETag());
                        }
                        response.flushBuffer();
                        return false;
                    }
                }

                // Parse the if[un]modified dates and compare to resource
                long date = request.getDateHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString());
                if (date != -1 && resource.lastModified() / 1000 > date / 1000) {
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        } catch (IllegalArgumentException iae) {
            if (!response.isCommitted()) {
                response.sendError(400, iae.getMessage());
            }
            throw iae;
        }
        return true;
    }


    /* ------------------------------------------------------------------- */
    @Override
    protected void sendDirectory(HttpServletRequest request,
            HttpServletResponse response,
            Resource resource,
            String pathInContext)
            throws IOException {
        if (!_dirAllowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        byte[] data = null;
        String base = URIUtil.addPaths(request.getRequestURI(), URIUtil.SLASH);

        String dir = resource.getListHTML(base, pathInContext.length() > 1);
        if (dir == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "No directory");
            return;
        }

        data = dir.getBytes("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void sendData(HttpServletRequest request,
            HttpServletResponse response,
            boolean include,
            Resource resource,
            HttpContent content,
            Enumeration<String> reqRanges)
            throws IOException {
        final long content_length = (content == null) ? resource.length() : content.getContentLength();

        // Get the output stream (or writer)
        OutputStream out = null;
        boolean written;
        try {
            out = response.getOutputStream();

            // has a filter already written to the response?
            written = out instanceof HttpOutput
                    ? ((HttpOutput) out).isWritten()
                    : true;
        } catch (IllegalStateException e) {
            out = new WriterOutputStream(response.getWriter());
            written = true; // there may be data in writer buffer, so assume written
        }

        if (reqRanges == null || !reqRanges.hasMoreElements() || content_length < 0) {
            //  if there were no ranges, send entire entity
            if (include) {
                resource.writeTo(out, 0, content_length);
            } // else if we can't do a bypass write because of wrapping
            else if (content == null || written || !(out instanceof HttpOutput)) {
                // write normally
                writeHeaders(response, content, written ? -1 : content_length);
                ByteBuffer buffer = (content == null) ? null : content.getIndirectBuffer();
                if (buffer != null) {
                    BufferUtil.writeTo(buffer, out);
                } else {
                    resource.writeTo(out, 0, content_length);
                }
            } // else do a bypass write
            else {
                // write the headers
                if (response instanceof Response) {
                    Response r = (Response) response;
                    writeOptionHeaders(r.getHttpFields());
                    r.setHeaders(content);
                } else {
                    writeHeaders(response, content, content_length);
                }

                // write the content asynchronously if supported
                if (request.isAsyncSupported()) {
                    final AsyncContext context = request.startAsync();

                    ((HttpOutput) out).sendContent(content, new Callback() {
                        @Override
                        public void succeeded() {
                            context.complete();
                        }

                        @Override
                        public void failed(Throwable x) {
                            ConcurrentLog.logException(x);
                            context.complete();
                        }
                    });
                } // otherwise write content blocking
                else {
                    ((HttpOutput) out).sendContent(content);
                }
            }
        } else {
            // Parse the satisfiable ranges
            List<InclusiveByteRange> ranges = InclusiveByteRange.satisfiableRanges(reqRanges, content_length);

            //  if there are no satisfiable ranges, send 416 response
            if (ranges == null || ranges.size() == 0) {
                writeHeaders(response, content, content_length);
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HeaderFramework.CONTENT_RANGE,
                        InclusiveByteRange.to416HeaderRangeString(content_length));
                resource.writeTo(out, 0, content_length);
                return;
            }

            //  if there is only a single valid range (must be satisfiable
            //  since were here now), send that range with a 216 response
            if (ranges.size() == 1) {
                InclusiveByteRange singleSatisfiableRange = ranges.get(0);
                long singleLength = singleSatisfiableRange.getSize(content_length);
                writeHeaders(response, content, singleLength);
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader(HeaderFramework.CONTENT_RANGE,
                        singleSatisfiableRange.toHeaderRangeString(content_length));
                resource.writeTo(out, singleSatisfiableRange.getFirst(content_length), singleLength);
                return;
            }

            //  multiple non-overlapping valid ranges cause a multipart
            //  216 response which does not require an overall
            //  content-length header
            //
            writeHeaders(response, content, -1);
            String mimetype = (content == null || content.getContentType() == null ? null : content.getContentType().toString());
            if (mimetype == null) {
                ConcurrentLog.warn("YaCyDefaultServlet", "Unknown mimetype for " + request.getRequestURI());
            }
            MultiPartOutputStream multi = new MultiPartOutputStream(out);
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

            // If the request has a "Request-Range" header then we need to
            // send an old style multipart/x-byteranges Content-Type. This
            // keeps Netscape and acrobat happy. This is what Apache does.
            String ctp;
            if (request.getHeader(HttpHeader.REQUEST_RANGE.asString()) != null) {
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
                InclusiveByteRange ibr = ranges.get(i);
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
                InclusiveByteRange ibr = ranges.get(i);
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

    /* ------------------------------------------------------------ */
    @Override
    protected void writeHeaders(HttpServletResponse response, HttpContent content, long count) {
        if (content.getContentType() != null && response.getContentType() == null) {
            response.setContentType(content.getContentType().toString());
        }

        if (response instanceof Response) {
            Response r = (Response) response;
            HttpFields fields = r.getHttpFields();

            if (content.getLastModified() != null) {
                fields.put(HeaderFramework.LAST_MODIFIED, content.getLastModified());
            } else if (content.getResource() != null) {
                long lml = content.getResource().lastModified();
                if (lml != -1) {
                    fields.putDateField(HeaderFramework.LAST_MODIFIED, lml);
                }
            }

            if (count != -1) {
                r.setLongContentLength(count);
            }

            writeOptionHeaders(fields);

            if (_etags) {
                fields.put(HeaderFramework.ETAG, content.getETag());
            }
        } else {
            long lml = content.getResource().lastModified();
            if (lml >= 0) {
                response.setDateHeader(HeaderFramework.LAST_MODIFIED, lml);
            }

            if (count != -1) {
                if (count < Integer.MAX_VALUE) {
                    response.setContentLength((int) count);
                } else {
                    response.setHeader(HeaderFramework.CONTENT_LENGTH, Long.toString(count));
                }
            }

            writeOptionHeaders(response);

            if (_etags) {
                response.setHeader(HeaderFramework.ETAG, content.getETag().toString());
            }
        }
    }

     @Override
    public void handleTemplate(String target,  HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
        Switchboard sb = Switchboard.getSwitchboard();

        String localeSelection = Switchboard.getSwitchboard().getConfig("locale.language", "default");
        File targetFile = getLocalizedFile(target, localeSelection);
        File targetClass = rewriteClassFile(_resourceBase.addPath(target).getFile());
        String targetExt = target.substring(target.lastIndexOf('.') + 1, target.length());

        if ((targetClass != null)) {
            serverObjects args = new serverObjects();
            @SuppressWarnings("unchecked")
            Enumeration<String> argNames = request.getParameterNames();
            while (argNames.hasMoreElements()) {
                String argName = argNames.nextElement();
                args.put(argName, request.getParameter(argName));
            }
            //TODO: for SSI request, local parameters are added as attributes, put them back as parameter for the legacy request
            //      likely this should be implemented via httpservletrequestwrapper to supply complete parameters  
            @SuppressWarnings("unchecked")
            Enumeration<String> attNames = request.getAttributeNames();
            while (attNames.hasMoreElements()) {
                String argName = attNames.nextElement();
                args.put(argName, request.getAttribute(argName).toString());
            }

            // add multipart-form fields to parameter
            if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
                parseMultipart(request, args);
            }
            // eof modification to read attribute
            RequestHeader legacyRequestHeader = generateLegacyRequestHeader(request, target, targetExt);

            Object tmp;
            try {
                tmp = invokeServlet(targetClass, legacyRequestHeader, args);
            } catch (InvocationTargetException e) {
                ConcurrentLog.logException(e);
                throw new ServletException();
            } catch (IllegalArgumentException e) {
                ConcurrentLog.logException(e);
                throw new ServletException();
            } catch (IllegalAccessException e) {
                ConcurrentLog.logException(e);
                throw new ServletException();
            }

            if (tmp instanceof RasterPlotter || tmp instanceof EncodedImage || tmp instanceof Image) {

                net.yacy.cora.util.ByteBuffer result = null;

                if (tmp instanceof RasterPlotter) {
                    final RasterPlotter yp = (RasterPlotter) tmp;
                    // send an image to client
                    result = RasterPlotter.exportImage(yp.getImage(), "png");
                }
                if (tmp instanceof EncodedImage) {
                    final EncodedImage yp = (EncodedImage) tmp;
                    result = yp.getImage();
                }

                if (tmp instanceof Image) {
                    final Image i = (Image) tmp;

                    // generate an byte array from the generated image
                    int width = i.getWidth(null);
                    if (width < 0) {
                        width = 96; // bad hack
                    }
                    int height = i.getHeight(null);
                    if (height < 0) {
                        height = 96; // bad hack
                    }
                    final BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    bi.createGraphics().drawImage(i, 0, 0, width, height, null);
                    result = RasterPlotter.exportImage(bi, targetExt);
                }

                final String mimeType = Classification.ext2mime(targetExt, "text/html");
                response.setContentType(mimeType);
                response.setContentLength(result.length());
                response.setStatus(HttpServletResponse.SC_OK);

                result.writeTo(response.getOutputStream());

                return;
            }

            servletProperties templatePatterns = null;
            if (tmp == null) {
                // if no args given, then tp will be an empty Hashtable object (not null)
                templatePatterns = new servletProperties();
            } else if (tmp instanceof servletProperties) {
                templatePatterns = (servletProperties) tmp;
            } else {
                templatePatterns = new servletProperties((serverObjects) tmp);
            }
            // add the application version, the uptime and the client name to every rewrite table
            templatePatterns.put(servletProperties.PEER_STAT_VERSION, yacyBuildProperties.getVersion());
            templatePatterns.put(servletProperties.PEER_STAT_UPTIME, ((System.currentTimeMillis() - serverCore.startupTime) / 1000) / 60); // uptime in minutes
            templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTNAME, sb.peers.mySeed().getName());
            templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTID, sb.peers.myID());
            templatePatterns.put(servletProperties.PEER_STAT_MYTIME, GenericFormatter.SHORT_SECOND_FORMATTER.format());
            Seed myPeer = sb.peers.mySeed();
            templatePatterns.put("newpeer", myPeer.getAge() >= 1 ? 0 : 1);
            templatePatterns.putHTML("newpeer_peerhash", myPeer.hash);
            templatePatterns.put("p2p", sb.getConfigBool(SwitchboardConstants.DHT_ENABLED, true) || !sb.isRobinsonMode() ? 1 : 0);

            if (targetFile.exists() && targetFile.isFile() && targetFile.canRead()) {
                String mimeType = Classification.ext2mime(targetExt, "text/html");

                InputStream fis = null;
                long fileSize = targetFile.length();

                if (fileSize <= Math.min(4 * 1024 * 1204, MemoryControl.available() / 100)) {
                    // read file completely into ram, avoid that too many files are open at the same time
                    fis = new ByteArrayInputStream(FileUtils.read(targetFile));
                } else {
                    fis = new BufferedInputStream(new FileInputStream(targetFile));
                }

                // set response header
                response.setContentType(mimeType);
                response.setStatus(HttpServletResponse.SC_OK);
                ByteArrayOutputStream bas = new ByteArrayOutputStream(4096);
                // apply templates
                TemplateEngine.writeTemplate(fis, bas, templatePatterns, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));                
                fis.close();
                // handle SSI
                doContentMod (bas.toByteArray(),request,response);
            }
        }
    }

    protected void doContentMod(final byte[] in, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        net.yacy.cora.util.ByteBuffer buffer = new net.yacy.cora.util.ByteBuffer(in);
        OutputStream out = response.getOutputStream();


        // remove virtual host "currentyacypeer"
        int off = 0; // starting offset
        int x = buffer.indexOf("/currentyacypeer/".getBytes(), off);
        while (x >= 0) {
            for (int i = 0; i < 16; i++) {
                in[x + i] = 32;
            }
            off = x + 16;
            x = buffer.indexOf("/currentyacypeer/".getBytes(), off);
        }

        // check and handle SSI (ServerSideIncludes)
        off = 0;
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
	
    // parse SSI line and include resource
    @Override
    protected void parseSSI(final net.yacy.cora.util.ByteBuffer in, final int off, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (in.startsWith("<!--#include virtual=\"".getBytes(), off)) {
            final int q = in.indexOf("\"".getBytes(), off + 22);
            if (q > 0) {
                final String path = in.toString(off + 22, q - off - 22);
                try {
                    RequestDispatcher dispatcher = request.getRequestDispatcher("/" + path);
                    dispatcher.include(request, response);
                    response.flushBuffer();
                } catch (Exception e) {
                    ConcurrentLog.logException(e);
                    throw new ServletException();
                }
            }
        }
    }
    /**
     * TODO: add same functionality & checks as in HTTPDemon.parseMultipart
     *
     * parse multi-part form data for formfields (only), see also original
     * implementation in HTTPDemon.parseMultipart
     *
     * @param request
     * @param args found fields/values are added to the map
     */
    public void parseMultipart(HttpServletRequest request, serverObjects args) {
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // maximum size that will be stored in memory
        factory.setSizeThreshold(4096 * 16);
        // Location to save data that is larger than maxMemSize.
        // factory.setRepository(new File("."));
        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setSizeMax(4096 * 16);
        try {
            // Parse the request to get form field items
            @SuppressWarnings("unchecked")
            List<FileItem> fileItems = upload.parseRequest(request);
            // Process the uploaded file items
            Iterator<FileItem> i = fileItems.iterator();
            while (i.hasNext()) {
                FileItem fi = i.next();
                if (fi.isFormField()) {
                    args.put(fi.getFieldName(), fi.getString());
                }
            }
        } catch (Exception ex) {
            ConcurrentLog.logException(ex);
        }
    }
}