//  YaCyDefaultServlet
//  Copyright 2013 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//  First released 2013 at https://yacy.net
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
package net.yacy.http.servlets;

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
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.server.InclusiveByteRange;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;

import com.google.common.net.HttpHeaders;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.BadTransactionException;
import net.yacy.data.InvalidURLLicenceException;
import net.yacy.data.TransactionManager;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverClassLoader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.server.http.HTTPDFileHandler;
import net.yacy.server.http.TemplateEngine;
import net.yacy.visualization.RasterPlotter;

/**
 * YaCyDefaultServlet based on Jetty DefaultServlet.java
 * handles static files and the YaCy servlets.
 *
 * This interface impements the YaCy specific and standard Servlet routines
 * which should not have a dependency on the implemented Jetty version.
 * The Jetty version specific code is moved to the Jetty8HttpServerImpl.java implementation
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
 *  resourceBase      Set to replace the context resource base
 *
 * </PRE>
 */
public class YaCyDefaultServlet extends HttpServlet  {

    private static final long serialVersionUID = 4900000000000001110L;
    protected ServletContext _servletContext;
    protected boolean _acceptRanges = true;
    protected boolean _dirAllowed = true;
    protected Resource _resourceBase;
    protected MimeTypes _mimeTypes;
    protected String[] _welcomes;

    protected File _htLocalePath;
    protected File _htDocsPath;
    protected static final serverClassLoader provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
    protected ConcurrentHashMap<String, Method> templateMethodCache = null;
    // settings for multipart/form-data
    protected static final File TMPDIR = new File(System.getProperty("java.io.tmpdir"));
    protected static final int SIZE_FILE_THRESHOLD = 1024 * 1024 * 1024; // 1GB is a lot but appropriate for multi-document pushed using the push_p.json servlet
    protected static final FileItemFactory DISK_FILE_ITEM_FACTORY = new DiskFileItemFactory(SIZE_FILE_THRESHOLD, TMPDIR);
    /* ------------------------------------------------------------ */
    @Override
    public void init() throws UnavailableException {
        final Switchboard sb = Switchboard.getSwitchboard();
        this._htDocsPath = sb.htDocsPath;
        this._htLocalePath = sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot");

        this._servletContext = getServletContext();

        this._mimeTypes = new MimeTypes();
        final String tmpstr = this.getServletContext().getInitParameter("welcomeFile");
        if (tmpstr == null) {
            this._welcomes = HTTPDFileHandler.defaultFiles;
        } else {
            this._welcomes = new String[]{tmpstr,"index.html"};
        }
        this._acceptRanges = getInitBoolean("acceptRanges", this._acceptRanges);
        this._dirAllowed = getInitBoolean("dirAllowed", this._dirAllowed);

        Resource.setDefaultUseCaches(false); // caching is handled internally (prevent double caching)

        final String rb = getInitParameter("resourceBase");
        try {
            if (rb != null) {
                this._resourceBase = Resource.newResource(rb);
            } else {
                this._resourceBase = Resource.newResource(sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT)); //default
            }
        } catch (final IOException e) {
            ConcurrentLog.severe("FILEHANDLER", "YaCyDefaultServlet: resource base (htRootPath) missing");
            ConcurrentLog.logException(e);
            throw new UnavailableException(e.toString());
        }
        if (ConcurrentLog.isFine("FILEHANDLER")) {
            ConcurrentLog.fine("FILEHANDLER","YaCyDefaultServlet: resource base = " + this._resourceBase);
        }
        this.templateMethodCache = new ConcurrentHashMap<String, Method>();
    }

    /* ------------------------------------------------------------ */
    protected boolean getInitBoolean(final String name, final boolean dft) {
        final String value = getInitParameter(name);
        if (value == null || value.length() == 0) {
            return dft;
        }
        return (value.startsWith("t")
                || value.startsWith("T")
                || value.startsWith("y")
                || value.startsWith("Y")
                || value.startsWith("1"));
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
    public Resource getResource(final String pathInContext) {
        Resource r = null;
        try {
            if (this._resourceBase != null) {
                r = this._resourceBase.addPath(pathInContext);
            } else {
                final URL u = this._servletContext.getResource(pathInContext);
                r = Resource.newResource(u);
            }

            if (ConcurrentLog.isFine("FILEHANDLER")) {
                ConcurrentLog.fine("FILEHANDLER","YaCyDefaultServlet: Resource " + pathInContext + "=" + r);
            }
        } catch (final IOException e) {
            // ConcurrentLog.logException(e);
        }

        return r;
    }

    /* ------------------------------------------------------------ */
    protected boolean hasDefinedRange(final Enumeration<String> reqRanges) {
        return (reqRanges != null && reqRanges.hasMoreElements());
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo;
        Enumeration<String> reqRanges = null;
        final boolean included = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null;
        if (included) {
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            if (pathInfo == null) {
                pathInfo = request.getPathInfo();
            }
        } else {
            pathInfo = request.getPathInfo();

            // Is this a Range request?
            reqRanges = request.getHeaders(HeaderFramework.RANGE);
            if (!hasDefinedRange(reqRanges)) {
                reqRanges = null;
            }
        }

        String pathInContext =  pathInfo == null ? "/" : pathInfo; // this is the path of the resource in _resourceBase (= path within htroot respective htDocs)
        final boolean endsWithSlash = pathInContext.endsWith(URIUtil.SLASH);

        // Find the resource
        Resource resource = null;

        try {

            // Look for a class resource
            boolean hasClass = false;
            if (reqRanges == null && !endsWithSlash) {
                final int p = pathInContext.lastIndexOf('.');
                if (p >= 0) {
                    final Method rewriteMethod = rewriteMethod(pathInContext);
                    if (rewriteMethod != null) {
                        hasClass = true;
                    } else {
                        final String pathofClass = pathInContext.substring(0, p) + ".class";
                        final Resource classresource = this._resourceBase.addPath(pathofClass);
                        // Does a class resource exist?
                        if (classresource != null && classresource.exists() && !classresource.isDirectory()) {
                            hasClass = true;
                        }
                    }
                }
            }

            // find resource
            resource = getResource(pathInContext);

            if (!hasClass && (resource == null || !resource.exists()) && !pathInContext.contains("..")) {
                // try to get this in the alternative htDocsPath
            	if (resource != null) resource.close();
                resource = Resource.newResource(new File(this._htDocsPath, pathInContext));
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
                    final String q = request.getQueryString();
                    pathInContext = pathInContext.substring(0, pathInContext.length() - 1);
                    if (q != null && q.length() != 0) {
                        pathInContext += "?" + q;
                    }
                    response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(this._servletContext.getContextPath(), pathInContext)));
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

                if (!endsWithSlash) {
                    final StringBuffer buf = request.getRequestURL();
                    synchronized (buf) {
                        final int param = buf.lastIndexOf(";");
                        if (param < 0) {
                            buf.append('/');
                        } else {
                            buf.insert(param, '/');
                        }
                        final String q = request.getQueryString();
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
                    final RequestDispatcher dispatcher = request.getRequestDispatcher(welcome);
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
        } catch (final IllegalArgumentException e) {
            ConcurrentLog.logException(e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } finally {
            if (resource != null) {
                resource.close();
            }
        }
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doTrace(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doTrace(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Allow", "GET,HEAD,POST,OPTIONS");
    }

    /* ------------------------------------------------------------ */
    /**
     * Finds a matching welcome file for the supplied path.
     * The filename to look is set as servlet context init parameter
     * default is "index.html"
     * @param pathInContext path in context
     * @return The path of the matching welcome file in context or null.
     */
    protected String getWelcomeFile(final String pathInContext) {
        if (this._welcomes == null) {
            return null;
        }
        for (final String _welcome : this._welcomes) {
            final String welcome_in_context = URIUtil.addPaths(pathInContext, _welcome);
            final Resource welcome = getResource(welcome_in_context);
            if (welcome != null && welcome.exists()) {
                return _welcome;
            }
        }
        return null;
    }
    /* ------------------------------------------------------------ */
    /* Check modification date headers.
     * send a 304 response instead of content if not modified since
     */
    protected boolean passConditionalHeaders(final HttpServletRequest request, final HttpServletResponse response, final Resource resource)
            throws IOException {
        try {
            if (!request.getMethod().equals(HttpMethod.HEAD.asString())) {

                final String ifms = request.getHeader(HttpHeader.IF_MODIFIED_SINCE.asString());
                if (ifms != null) {

                    final long ifmsl = request.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.asString());
                    if (ifmsl != -1) {
                        if (resource.lastModified() / 1000 <= ifmsl / 1000) {
                            response.reset();
                            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                            response.flushBuffer();
                            return false;
                        }
                    }
                }

                // Parse the if[un]modified dates and compare to resource
                final long date = request.getDateHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString());

                if (date != -1) {
                    if (resource.lastModified() / 1000 > date / 1000) {
                        response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                        return false;
                    }
                }
            }
        } catch (final IllegalArgumentException iae) {
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, iae.getMessage());
                return false;
            }
            throw iae;
        }
        return true;
    }

    /* ------------------------------------------------------------------- */
    protected void sendDirectory(final HttpServletRequest request,
            final HttpServletResponse response,
            final Resource resource,
            final String pathInContext)
            throws IOException {
        if (!this._dirAllowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final String base = URIUtil.addPaths(request.getRequestURI(), URIUtil.SLASH);

        final String dir = resource.getListHTML(base, pathInContext.length() > 1, null);
        if (dir == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No directory");
            return;
        }

        final byte[] data = dir.getBytes(StandardCharsets.UTF_8);
        response.setContentType(MimeTypes.Type.TEXT_HTML_UTF_8.asString());
        response.setContentLength(data.length);
        response.setHeader(HeaderFramework.CACHE_CONTROL, "no-cache, no-store");
        response.setDateHeader(HeaderFramework.EXPIRES, System.currentTimeMillis() + 10000); // consider that directories are not modified that often
        response.setDateHeader(HeaderFramework.LAST_MODIFIED, resource.lastModified());
        response.getOutputStream().write(data);
    }

    /* ------------------------------------------------------------ */
    /**
     * send static content
     *
     * @param request
     * @param response
     * @param include  is a include file (send without changing/adding headers)
     * @param resource the static content
     * @param reqRanges
     * @throws IOException
     */
    protected void sendData(final HttpServletRequest request,
            final HttpServletResponse response,
            final boolean include,
            final Resource resource,
            final Enumeration<String> reqRanges)
            throws IOException {

        final long content_length = resource.length();

        // Get the output stream (or writer)
        OutputStream out;
        try {
            out = response.getOutputStream();
        } catch (final IllegalStateException e) {
            out = new WriterOutputStream(response.getWriter());
        }

        // remove the last-modified field since caching otherwise does not work
        /*
           https://www.ietf.org/rfc/rfc2616.txt
           "if the response does have a Last-Modified time, the heuristic
           expiration value SHOULD be no more than some fraction of the interval
           since that time. A typical setting of this fraction might be 10%."
        */
        if (response.containsHeader(HeaderFramework.LAST_MODIFIED)) {
            response.getHeaders(HeaderFramework.LAST_MODIFIED).clear(); // if this field is present, the reload-time is a 10% fraction of ttl and other caching headers do not work
        }

        // cache-control: allow shared caching (i.e. proxies) and set expires age for cache
        response.setHeader(HeaderFramework.CACHE_CONTROL, "public, max-age=" + Integer.toString(600)); // seconds; ten minutes

        if (reqRanges == null || !reqRanges.hasMoreElements() || content_length < 0) {
            //  if there were no ranges, send entire entity
            if (include) {
                resource.writeTo(out, 0, content_length);
            } else {
                writeHeaders(response, resource, content_length);
                resource.writeTo(out, 0, content_length);
            }
        } else {
            // Parse the satisfiable ranges
            final List<InclusiveByteRange> ranges = InclusiveByteRange.satisfiableRanges(reqRanges, content_length);

            //  if there are no satisfiable ranges, send 416 response
            if (ranges == null || ranges.isEmpty()) {
                writeHeaders(response, resource, content_length);
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HttpHeader.CONTENT_RANGE.asString(),
                        InclusiveByteRange.to416HeaderRangeString(content_length));
                resource.writeTo(out, 0, content_length);
                out.close();
                return;
            }

            //  if there is only a single valid range (must be satisfiable
            //  since were here now), send that range with a 216 response
            if (ranges.size() == 1) {
                final InclusiveByteRange singleSatisfiableRange = ranges.iterator().next();
                final long singleLength = singleSatisfiableRange.getSize();
                writeHeaders(response, resource, singleLength);
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader(HttpHeader.CONTENT_RANGE.asString(),
                        singleSatisfiableRange.toHeaderRangeString(content_length));
                resource.writeTo(out, singleSatisfiableRange.getFirst(), singleLength);
                out.close();
                return;
            }

            //  multiple non-overlapping valid ranges cause a multipart
            //  216 response which does not require an overall
            //  content-length header
            //
            writeHeaders(response, resource, -1);
            final String mimetype = response.getContentType();
            if (mimetype == null) {
                ConcurrentLog.warn("FILEHANDLER","YaCyDefaultServlet: Unknown mimetype for " + request.getRequestURI());
            }
            final MultiPartOutputStream multi = new MultiPartOutputStream(out);
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
            final String[] header = new String[ranges.size()];
            for (int i = 0; i < ranges.size(); i++) {
                final InclusiveByteRange ibr = ranges.get(i);
                header[i] = ibr.toHeaderRangeString(content_length);
                length +=
                        ((i > 0) ? 2 : 0)
                        + 2 + multi.getBoundary().length() + 2
                        + (mimetype == null ? 0 : HeaderFramework.CONTENT_TYPE.length() + 2 + mimetype.length()) + 2
                        + HeaderFramework.CONTENT_RANGE.length() + 2 + header[i].length() + 2
                        + 2
                        + (ibr.getLast() - ibr.getFirst()) + 1;
            }
            length += 2 + 2 + multi.getBoundary().length() + 2 + 2;
            response.setContentLength(length);

            for (int i = 0; i < ranges.size(); i++) {
                final InclusiveByteRange ibr = ranges.get(i);
                multi.startPart(mimetype, new String[]{HeaderFramework.CONTENT_RANGE + ": " + header[i]});

                final long start = ibr.getFirst();
                final long size = ibr.getSize();
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
            if (in != null) in.close();
            multi.close();
        }
    }

    /* ------------------------------------------------------------ */
    protected void writeHeaders(final HttpServletResponse response, final Resource resource, final long count) {
        if (response.getContentType() == null) {
            final String extensionmime;
            if ((extensionmime = this._mimeTypes.getMimeByExtension(resource.getName())) != null) {
                response.setContentType(extensionmime);
            }
        }
        /*
         * DO NOT enable this again, removal of the LAST_MODIFIED field enables caching
        long lml = resource.lastModified();
        if (lml >= 0) {
            response.setDateHeader(HeaderFramework.LAST_MODIFIED, lml);
        }
        */

        if (count != -1) {
            if (count < Integer.MAX_VALUE) {
                response.setContentLength((int) count);
            } else {
                response.setHeader(HeaderFramework.CONTENT_LENGTH, Long.toString(count));
            }
        }

        if (this._acceptRanges) {
            response.setHeader(HeaderFramework.ACCEPT_RANGES, "bytes");
        }
    }

    protected Object invokeServlet(final Method targetMethod, final RequestHeader request, final serverObjects args) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return targetMethod.invoke(null, new Object[]{request, args, Switchboard.getSwitchboard()}); // add switchboard
    }

    /**
     * Returns the URL base for this peer, determined from request HTTP header "Host" when present. Use this when absolute URL rendering is required,
     * otherwise relative URLs should be preferred.<br/>
     * Note : this implementation lets the responsibility to any eventual Reverse Proxy to eventually rewrite the rendered absolute URL. Example Apache directive :
     * <code>Substitute "s|http://internal.yacypeer.com:8090/|http://www.example.com/yacy/|in"</code>.
     * From a security point of view this is preferable than eventually relying blindly here on a X-Forwarded-Host HTTP header that can be forged by an attacker.
     * @param header request header.
     * @param sb Switchboard instance.
     * @return the application context (URL request base) from request header or default configuration. This is
     * either http://hostname:port or https://hostname:sslport
     */
    public static String getContext(final RequestHeader header, final Switchboard sb) {
        String protocol = "http";
        String hostAndPort = null;
        if (header != null) {
            hostAndPort = header.get(HeaderFramework.HOST);
            protocol = header.getScheme();
        }

        /* Host and port still null : let's use the default local ones */
        if (hostAndPort == null) {
            if (sb != null) {
                hostAndPort = Domains.LOCALHOST + ":" + sb.getConfigInt(SwitchboardConstants.SERVER_PORT, 8090);
            } else {
                hostAndPort = Domains.LOCALHOST + ":8090";
            }
        }

        if(header != null) {
            String protocolHeader = header.getScheme();

            /* Let's check this header has a valid value */
            if("http".equals(protocolHeader) || "https".equals(protocolHeader)) {
                protocol = protocolHeader.toLowerCase(Locale.ROOT);
            } else if(protocolHeader != null && !protocolHeader.isEmpty()) {
                ConcurrentLog.warn("FILEHANDLER","YaCyDefaultServlet: illegal protocol scheme header value : " + protocolHeader);
            }

            /* This peer can also be behind a reverse proxy requested using https, even if the request coming to this YaCy peer is http only
             * Possible scenario (happens for example when YaCy is deployed on Heroku Platform) : User browser -> https://reverseProxy/yacyURL -> http://yacypeer/yacyURL
             * In that case, absolute URLs rendered by this peer (in rss feeds for example) must effectively start with the https scheme */
            protocolHeader = header.get(HttpHeaders.X_FORWARDED_PROTO.toString(), "").toLowerCase(Locale.ROOT);

            /* Here we only allow an upgrade from HTTP to HTTPS, not the reverse (we don't want a forged HTTP header by an eventual attacker to force fallback to HTTP) */
            if("https".equals(protocolHeader)) {
                protocol = protocolHeader;
            } else if(!protocolHeader.isEmpty()) {
                ConcurrentLog.warn("FILEHANDLER","YaCyDefaultServlet: illegal " + HttpHeaders.X_FORWARDED_PROTO.toString() + " header value : " + protocolHeader);
            }
        }

        return protocol + "://" + hostAndPort;
    }

    private RequestHeader generateLegacyRequestHeader(final HttpServletRequest request, final String target, final String targetExt) {
        final RequestHeader legacyRequestHeader = new RequestHeader(request);

        legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, target); // target may contain a server side include (SSI)
        legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_EXT, targetExt);
        return legacyRequestHeader;
    }

    /**
     * Returns a path to the localized or default file according to the
     * parameter localeSelection
     *
     * @param path relative from htroot
     * @param localeSelection language of localized file; locale.language from
     * switchboard is used if localeSelection.equals("")
     */
    public File getLocalizedFile(final String path, final String localeSelection) throws IOException {
        if (!(localeSelection.equals("default"))) {
            final File localePath = new File(this._htLocalePath, localeSelection + '/' + path);
            if (localePath.exists()) {
                return localePath;  // avoid "NoSuchFile" troubles if the "localeSelection" is misspelled
            }
        }

        final File docsPath = new File(this._htDocsPath, path);
        if (docsPath.exists()) {
            return docsPath;
        }
        return this._resourceBase.addPath(path).getFile();
    }

    private final Method rewriteMethod(final String target) {
        assert target.charAt(0) == '/';

        final Method cachedMethod = this.templateMethodCache.get(target);
        if (cachedMethod != null) return cachedMethod;

        final int p = target.lastIndexOf('.');
        if (p < 0) {
            return null;
        }
        final String classname = "net.yacy.htroot" + target.substring(0, p).replace('/', '.');
        try {
            final Class<?> servletClass = Class.forName(classname);
            final Method rewriteMethod = rewriteMethod(servletClass);
            this.templateMethodCache.put(target, rewriteMethod);
            return rewriteMethod;
        } catch (final ClassNotFoundException | InvocationTargetException e) {
            try {
                final Class<?> servletClass = Class.forName(classname + "_"); // for some targets we need alternative names
                final Method rewriteMethod = rewriteMethod(servletClass);
                this.templateMethodCache.put(target, rewriteMethod);
                return rewriteMethod;
            } catch (final ClassNotFoundException | InvocationTargetException ee) {
                return null;
            }
        }
    }

    private final static Method rewriteMethod(final Class<?> rewriteClass) throws InvocationTargetException {
        final Class<?>[] params = (Class<?>[]) Array.newInstance(Class.class, 3);
        params[0] = RequestHeader.class;
        params[1] = serverObjects.class;
        params[2] = serverSwitch.class;
        try {
            final Method m = rewriteClass.getMethod("respond", params);
            return m;
        } catch (final NoSuchMethodException e) {
            ConcurrentLog.severe("FILEHANDLER","YaCyDefaultServlet: method 'respond' not found in class " + rewriteClass.getName()  + ": " + e.getMessage());
            throw new InvocationTargetException(e, "method 'respond' not found in class " + rewriteClass.getName()  + ": " + e.getMessage());
        }
    }

    /**
     * Handles a YaCy servlet template, reads the template and replaces the template
     * items with actual values. Because of supported server side includes target
     * might not be the same as request.getPathInfo
     *
     * @param target the path to the template
     * @param request the remote servlet request
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    protected void handleTemplate(final String target,  final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        final Switchboard sb = Switchboard.getSwitchboard();

        String localeSelection = sb.getConfig("locale.language", "browser");
        if (localeSelection.endsWith("browser")) {
            final String lng = request.getLocale().getLanguage();
            if (lng.equalsIgnoreCase("en")) { // because en is handled as "default" in localizer
                localeSelection = "default";
            } else {
                localeSelection = lng;
            }
        }
        final File targetLocalizedFile = getLocalizedFile(target, localeSelection);
        final Method targetMethod = rewriteMethod(target);
        final String targetExt = target.substring(target.lastIndexOf('.') + 1);

        final long now = System.currentTimeMillis();
        if (target.endsWith(".css")) {
            response.setDateHeader(HeaderFramework.LAST_MODIFIED, now);
            response.setDateHeader(HeaderFramework.EXPIRES, now + 3600000); // expires in 1 hour (which is still often, others use 1 week, month or year)
        } else if (target.endsWith(".png")) {
            // expires in 1 minute (reduce heavy image creation load)
            if (response.containsHeader(HeaderFramework.LAST_MODIFIED)) {
                response.getHeaders(HeaderFramework.LAST_MODIFIED).clear();
            }
            response.setHeader(HeaderFramework.CACHE_CONTROL, "public, max-age=" + Integer.toString(60));
        } else {
            response.setDateHeader(HeaderFramework.LAST_MODIFIED, now);
            response.setDateHeader(HeaderFramework.EXPIRES, now); // expires now
        }

        if (target.endsWith(".json")) {
            response.setHeader(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
        }

        if (targetMethod != null) {
            final serverObjects args = new serverObjects();
            final Enumeration<String> argNames = request.getParameterNames(); // on ssi jetty dispatcher merged local ssi query parameters
            while (argNames.hasMoreElements()) {
                final String argName = argNames.nextElement();
                // standard attributes are just pushed as string
                args.put(argName, request.getParameter(argName));
            }
            final RequestHeader legacyRequestHeader = generateLegacyRequestHeader(request, target, targetExt);
            // add multipart-form fields to parameter
            if (ServletFileUpload.isMultipartContent(request)) {
                parseMultipart(request, args);
            }
            // eof modification to read attribute
            Object tmp;
            try {
                if (args.isEmpty()) {
                    // yacy servlets typically test for args != null (but not for args .isEmpty())
                    tmp = invokeServlet(targetMethod, legacyRequestHeader, null);
                } else {
                    tmp = invokeServlet(targetMethod, legacyRequestHeader, args);
                }
            } catch(final InvocationTargetException e) {
                if(e.getCause() instanceof InvalidURLLicenceException) {
                    /* A non authorized user is trying to fetch a image with a bad or already released license code */
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getCause().getMessage());
                    return;
                }
                if(e.getCause() instanceof BadTransactionException) {
                    /* A request for a protected page with server-side effects failed because the transaction is not valid :
                     * for example because missing or invalid transaction token*/
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getCause().getMessage()
                            + " If you sent this request with a web browser, please refresh the origin page.");
                    return;
                }
                if (e.getCause() instanceof TemplateProcessingException) {
                    /* A template processing error occurred, and the HTTP status and message have been set */
                    response.sendError(((TemplateProcessingException) e.getCause()).getStatus(),
                            e.getCause().getMessage());
                    return;
                }
                if(e.getCause() instanceof DisallowedMethodException) {
                    /* The request was sent using an disallowed HTTP method */
                    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, e.getCause().getMessage());
                    return;
                }
                ConcurrentLog.logException(e);
                throw new ServletException(targetLocalizedFile.getAbsolutePath());
            } catch (IllegalArgumentException | IllegalAccessException e) {
                ConcurrentLog.logException(e);
                throw new ServletException(targetLocalizedFile.getAbsolutePath());
            }

            if (tmp instanceof RasterPlotter || tmp instanceof EncodedImage || tmp instanceof Image) {

                net.yacy.cora.util.ByteBuffer result = null;

                if (tmp instanceof RasterPlotter) {
                    final RasterPlotter yp = (RasterPlotter) tmp;
                    // send an image to client
                    result = RasterPlotter.exportImage(yp.getImage(), "png");
                } else if (tmp instanceof EncodedImage) {
                    final EncodedImage yp = (EncodedImage) tmp;
                    result = yp.getImage();
                    /** When encodedImage is empty, return a code 500 rather than only an empty response
                     * as it is better handled across different browsers */
                    if(result == null || result.length() == 0) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        if(result != null) {
                            result.close();
                        }
                        return;
                    }
                    if (yp.isStatic()) { // static image never expires
                        response.setDateHeader(HeaderFramework.EXPIRES, now + 3600000); // expires in 1 hour
                    }
                } else if (tmp instanceof Image) {
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
                    final BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    bi.createGraphics().drawImage(i, 0, 0, width, height, null);
                    result = RasterPlotter.exportImage(bi, targetExt);
                }

                updateRespHeadersForImages(target, response);
                final String mimeType = Classification.ext2mime(targetExt, MimeTypes.Type.TEXT_HTML.asString());
                response.setContentType(mimeType);
                response.setContentLength(result.length());
                response.setStatus(HttpServletResponse.SC_OK);

                result.writeTo(response.getOutputStream());
                result.close();
                return;
            }

            if (tmp instanceof InputStream) {
                /* Images and favicons can also be written directly from an inputStream */
                updateRespHeadersForImages(target, response);

                writeInputStream(response, targetExt, (InputStream)tmp);
                return;
            }

            servletProperties templatePatterns;
            if (tmp == null) {
                // if no args given, then tp will be an empty Hashtable object (not null)
                templatePatterns = new servletProperties();
            } else if (tmp instanceof servletProperties) {
                templatePatterns = (servletProperties) tmp;

                if (templatePatterns.getOutgoingHeader() != null) {
                    // handle responseHeader entries set by servlet
                    final ResponseHeader tmpouthdr = templatePatterns.getOutgoingHeader();
                    for (final String hdrkey : tmpouthdr.keySet()) {
                        if (!HeaderFramework.STATUS_CODE.equals(hdrkey)) { // skip default init response status value (not std. )
                            final String val = tmpouthdr.get(hdrkey);
                            if (!response.containsHeader(hdrkey) && val != null) { // to be on the safe side, add only new hdr (mainly used for CORS_ALLOW_ORIGIN)
                                response.setHeader(hdrkey, tmpouthdr.get(hdrkey));
                            }
                        }
                    }
                    // handle login cookie
                    if (tmpouthdr.getCookiesEntries() != null) {
                        for (final Cookie c : tmpouthdr.getCookiesEntries()) {
                            response.addCookie(c);
                        }
                    }
                }
            } else {
                templatePatterns = new servletProperties((serverObjects) tmp);
            }

            if(templatePatterns.containsKey(TransactionManager.TRANSACTION_TOKEN_PARAM)) {
                /* The response contains a transaction token : we also write the transaction token as a custom header
                 * to allow usage by external tools (such as curl or wget) without the need to parse HTML */
                response.setHeader(HeaderFramework.X_YACY_TRANSACTION_TOKEN, templatePatterns.get(TransactionManager.TRANSACTION_TOKEN_PARAM));
            }

            // handle YaCy http commands
            // handle action auth: check if the servlets requests authentication
            if (templatePatterns.containsKey(serverObjects.ACTION_AUTHENTICATE)) {
                if (!request.authenticate(response)) {
                    return;
                }
            //handle action forward
            } else if (templatePatterns.containsKey(serverObjects.ACTION_LOCATION)) {
                String location = templatePatterns.get(serverObjects.ACTION_LOCATION, "");

                if (location.isEmpty()) {
                    location = request.getPathInfo();
                }
                //TODO: handle equivalent of this from httpdfilehandler
                // final ResponseHeader headers = getDefaultHeaders(request.getPathInfo());
                // headers.setAdditionalHeaderProperties(templatePatterns.getOutgoingHeader().getAdditionalHeaderProperties()); //put the cookies into the new header TODO: can we put all headerlines, without trouble?

                response.setHeader(HeaderFramework.LOCATION, location);
                response.setStatus(HttpServletResponse.SC_FOUND);
                return;
            }

            if (targetLocalizedFile.exists() && targetLocalizedFile.isFile() && targetLocalizedFile.canRead()) {

                sb.setConfig(SwitchboardConstants.SERVER_SERVLETS_CALLED, appendPath(sb.getConfig(SwitchboardConstants.SERVER_SERVLETS_CALLED, ""), target));
                if (args != null && !args.isEmpty()) {
                    sb.setConfig("server.servlets.submitted", appendPath(sb.getConfig("server.servlets.submitted", ""), target));
                }

                // add the application version, the uptime and the client name to every rewrite table
                templatePatterns.put(servletProperties.PEER_STAT_VERSION, yacyBuildProperties.getVersion());
                templatePatterns.put(servletProperties.PEER_STAT_UPTIME, ((System.currentTimeMillis() - sb.startupTime) / 1000) / 60); // uptime in minutes
                templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTNAME, sb.peers.mySeed().getName());
                templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTID, sb.peers.myID());
                templatePatterns.put(servletProperties.PEER_STAT_MYTIME, GenericFormatter.SHORT_SECOND_FORMATTER.format());
                templatePatterns.put(servletProperties.RELATIVE_BASE, YaCyDefaultServlet.getRelativeBase(target));
                templatePatterns.put(SwitchboardConstants.REFERRER_META_POLICY, sb.getConfig(SwitchboardConstants.REFERRER_META_POLICY, SwitchboardConstants.REFERRER_META_POLICY_DEFAULT));
                final Seed myPeer = sb.peers.mySeed();
                templatePatterns.put("newpeer", myPeer.getAge() >= 1 ? 0 : 1);
                templatePatterns.putHTML("newpeer_peerhash", myPeer.hash);
                final boolean authorized = sb.adminAuthenticated(legacyRequestHeader) >= 2;
                templatePatterns.put("authorized", authorized ? 1 : 0); // used in templates and other html (e.g. to display lock/unlock symbol)

                templatePatterns.put("simpleheadernavbar", sb.getConfig("decoration.simpleheadernavbar", "navbar-default"));

                // add navigation keys to enable or disable menu items
                templatePatterns.put("navigation-p2p", sb.getConfigBool(SwitchboardConstants.NETWORK_UNIT_DHT, true) || !sb.isRobinsonMode() ? 1 : 0);
                templatePatterns.put("navigation-p2p_authorized", authorized ? 1 : 0);
                final String submitted = sb.getConfig("server.servlets.submitted", "");
                final boolean crawler_enabled = true; /*
                        submitted.contains("Crawler_p") ||
                        submitted.contains("ConfigBasic") ||
                        submitted.contains("Load_RSS_p");*/
                @SuppressWarnings("unused")
				final boolean advanced_enabled =
                        crawler_enabled ||
                        submitted.contains("IndexImportMediawiki_p") ||
                        submitted.contains("CrawlStart");
                templatePatterns.put("navigation-crawlmonitor", crawler_enabled);
                templatePatterns.put("navigation-crawlmonitor_authorized", authorized ? 1 : 0);
                templatePatterns.put("navigation-advanced", advanced_enabled);
                templatePatterns.put("navigation-advanced_authorized", authorized ? 1 : 0);
                templatePatterns.put(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
                templatePatterns.put(SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
                templatePatterns.put(SwitchboardConstants.GREETING_IMAGE_ALT, sb.getConfig(SwitchboardConstants.GREETING_IMAGE_ALT, ""));
                templatePatterns.put("clientlanguage", localeSelection);

                final String mimeType = Classification.ext2mime(targetExt, MimeTypes.Type.TEXT_HTML.asString());

                InputStream fis;
                final long fileSize = targetLocalizedFile.length();

                if (fileSize <= Math.min(4 * 1024 * 1204, MemoryControl.available() / 100)) {
                    // read file completely into ram, avoid that too many files are open at the same time
                    fis = new ByteArrayInputStream(FileUtils.read(targetLocalizedFile));
                } else {
                    fis = new BufferedInputStream(new FileInputStream(targetLocalizedFile));
                }

                // set response header
                response.setContentType(mimeType);
                response.setStatus(HttpServletResponse.SC_OK);
                final ByteArrayOutputStream bas = new ByteArrayOutputStream(4096);
                try {
                    // apply templates
                    TemplateEngine.writeTemplate(targetLocalizedFile.getName(), fis, bas, templatePatterns);

                    // handle SSI
                    parseSSI (bas.toByteArray(),request,response);
                } finally {
                    try {
                        fis.close();
                    } catch(final IOException ignored) {
                        ConcurrentLog.warn("FILEHANDLER", "YaCyDefaultServlet: could not close target file " + targetLocalizedFile.getName());
                    }

                    try {
                        bas.close();
                    } catch(final IOException ignored) {
                        /* Should never happen with a ByteArrayOutputStream */
                    }
                }
            }
        }
    }

    /**
     * Returns the relative path prefix necessary to reach htroot from the deepest level of targetPath.<br>
     * Example : targetPath="api/citation.html" returns "../"
     * targetPath is supposed to have been cleaned earlier from special chars such as "?", spaces, "//".
     * @param targetPath target path relative to htroot
     * @return the relative path prefix, eventually empty
     */
    protected static String getRelativeBase(String targetPath) {
        final StringBuilder relativeBase = new StringBuilder();
        if(targetPath != null) {
            /* Normalize target path : it is relative to htroot, starting with a slash or not */
            if(targetPath.startsWith("/")) {
                targetPath = targetPath.substring(1, targetPath.length());
            }

            int slashIndex = targetPath.indexOf('/', 0);
            while(slashIndex >= 0) {
                relativeBase.append("../");
                slashIndex = targetPath.indexOf('/', slashIndex + 1);
            }
        }
        return relativeBase.toString();
    }

    /**
     * Eventually update response headers for image resources
     * @param target the query target
     * @param response servlet response to eventually update
     */
    private void updateRespHeadersForImages(final String target, final HttpServletResponse response) {
        if (target.equals("/ViewImage.png") || target.equals("/ViewFavicon.png")) {
            if (response.containsHeader(HeaderFramework.LAST_MODIFIED)) {
                response.getHeaders(HeaderFramework.LAST_MODIFIED).clear(); // if this field is present, the reload-time is a 10% fraction of ttl and other caching headers do not work
            }

            // cache-control: allow shared caching (i.e. proxies) and set expires age for cache
            response.setHeader(HeaderFramework.CACHE_CONTROL, "public, max-age=" + Integer.toString(600)); // seconds; ten minutes
        }
    }


    /**
     * Write input stream content to response and close input stream.
     * @param response servlet response. Must not be null.
     * @param targetExt response file format
     * @param inStream
     * @throws IOException when a read/write error occured.
     */
    private void writeInputStream(final HttpServletResponse response, final String targetExt, final InputStream inStream)
            throws IOException {
        final String mimeType = Classification.ext2mime(targetExt, MimeTypes.Type.TEXT_HTML.asString());
        response.setContentType(mimeType);
        response.setStatus(HttpServletResponse.SC_OK);
        final byte[] buffer = new byte[4096];
        int l, size = 0;
        try {
            while ((l = inStream.read(buffer)) > 0) {
                response.getOutputStream().write(buffer, 0, l);
                size += l;
            }
            response.setContentLength(size);
        } catch(final IOException e){
            /** No need to log full stack trace (in most cases resource is not available because of a network error) */
            ConcurrentLog.fine("FILEHANDLER", "YaCyDefaultServlet: resource content stream could not be written to response.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        } finally {
            try {
                inStream.close();
            } catch(final IOException ignored) {
            }
        }
    }

    /**
     * Append a path string to comma separated string of pathes if not already
     * contained in the proplist string
     * @param proplist comma separated string of pathes
     * @param path path to be appended
     * @return comma separated string of pathes including param path
     */
    private String appendPath(final String proplist, final String path) {
        if (proplist.length() == 0) return path;
        if (proplist.contains(path)) return proplist;
        return proplist + "," + path;
    }

    /**
     * parse SSI line and include resource (<!--#include virtual="file.html" -->)
     */
    protected void parseSSI(final byte[] in, final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        final ByteBuffer buffer = new ByteBuffer(in);
        final OutputStream out = response.getOutputStream();
        final byte[] inctxt ="<!--#include virtual=\"".getBytes();
        int offset = 0;
        int p = buffer.indexOf(inctxt, offset);
        int end;
        while (p >= 0 && (end = buffer.indexOf("-->".getBytes(), p + 24)) > 0 ) { // min length 24; <!--#include virtual="a"
            out.write(in, offset, p - offset);
            out.flush();
            // find right end quote
            final int rightquote = buffer.indexOf("\"".getBytes(), p + 23);
            if (rightquote > 0 && rightquote < end) {
                final String path = buffer.toString(p + 22, rightquote - p - 22);
                final RequestDispatcher dispatcher = request.getRequestDispatcher(path);
                try {
                    dispatcher.include(request, response);
                } catch (final IOException ex) {
                    if (path.indexOf("yacysearch") < 0) ConcurrentLog.warn("FILEHANDLER", "YaCyDefaultServlet: parseSSI dispatcher problem - " + ex.getMessage() + ": " + path);
                    // this is probably a time-out; it may occur during search requests; for search requests we consider that normal
                }
            } else {
                ConcurrentLog.warn("FILEHANDLER", "YaCyDefaultServlet: parseSSI closing quote missing " + buffer.toString(p, end - p) + " in " + request.getPathInfo());
            }
            offset = end + 3; // after "-->"
            p = buffer.indexOf(inctxt, offset);
        }
        out.write(in, offset, in.length - offset);
        //DO NOT out.close(); because that would interrupt the server stream - it causes that the content is cut off from here on
        buffer.close();
    }

    /**
     * TODO: add same functionality & checks as in HTTPDemon.parseMultipart
     *
     * parse multi-part form data for formfields, see also original
     * implementation in HTTPDemon.parseMultipart
     *
     * For file data the parameter for the formfield contains the filename and a
     * additional parameter with appendix [fieldname]$file conteins the upload content
     * (e.g. <input type="file" name="upload">  upload="local/filename" upload$file=[content])
     *
     * @param request
     * @param args found fields/values are added to the map
     */
    protected void parseMultipart(final HttpServletRequest request, final serverObjects args) throws IOException {

        // reject too large uploads
        if (request.getContentLength() > SIZE_FILE_THRESHOLD) throw new IOException("FileUploadException: uploaded file too large = " + request.getContentLength());

        // check if we have enough memory
        if (!MemoryControl.request(request.getContentLength() * 3, false)) {
            throw new IOException("not enough memory available for request. request.getContentLength() = " + request.getContentLength() + ", MemoryControl.available() = " + MemoryControl.available());
        }
        final ServletFileUpload upload = new ServletFileUpload(DISK_FILE_ITEM_FACTORY);
        upload.setFileSizeMax(SIZE_FILE_THRESHOLD);
        try {
            // Parse the request to get form field items
            final List<FileItem> fileItems = upload.parseRequest(request);
            // Process the uploaded file items
            final Iterator<FileItem> i = fileItems.iterator();
            final BlockingQueue<Map.Entry<String, byte[]>> files = new LinkedBlockingQueue<>();
            while (i.hasNext()) {
                final FileItem item = i.next();
                if (item.isFormField()) {
                    // simple text
                    if (item.getContentType() == null || !item.getContentType().contains("charset")) {
                        // old yacy clients use their local default charset, on most systems UTF-8 (I hope ;)
                        args.add(item.getFieldName(), item.getString(StandardCharsets.UTF_8.name()));
                    } else {
                        // use default encoding (given as header or ISO-8859-1)
                        args.add(item.getFieldName(), item.getString());
                    }
                } else {
                    // read file upload
                    args.add(item.getFieldName(), item.getName()); // add the filename to the parameters
                    InputStream filecontent = null;
                    try {
                        filecontent = item.getInputStream();
                        files.put(new AbstractMap.SimpleEntry<String, byte[]>(item.getFieldName(), FileUtils.read(filecontent)));
                    } catch (final IOException e) {
                        ConcurrentLog.info("FILEHANDLER", e.getMessage());
                    } finally {
                        if (filecontent != null) try {filecontent.close();} catch (final IOException e) {ConcurrentLog.info("FILEHANDLER", e.getMessage());}
                    }
                }
            }
            if (files.size() <= 1) { // TODO: should include additonal checks to limit parameter.size below rel. large SIZE_FILE_THRESHOLD
                for (final Map.Entry<String, byte[]> job: files) { // add the file content to parameter fieldname$file
                    final String n = job.getKey();
                    final byte[] v = job.getValue();
                    final String filename = args.get(n);
                    if (filename != null && filename.endsWith(".gz")) {
                        // transform this value into base64
                        final String b64 = Base64Order.standardCoder.encode(v);
                        args.put(n + "$file", b64);
                        args.remove(n);
                        args.put(n, filename + ".base64");
                    } else {
                        args.put(n + "$file", v); // the byte[] is transformed into UTF8. You cannot push binaries here
                    }
                }
            } else {
                // do this concurrently (this would all be superfluous if serverObjects could store byte[] instead only String)
                final int t = Math.min(files.size(), Runtime.getRuntime().availableProcessors());
                final Map.Entry<String, byte[]> POISON = new AbstractMap.SimpleEntry<>(null, null);
                final Thread[] p = new Thread[t];
                for (int j = 0; j < t; j++) {
                    files.put(POISON);
                    p[j] = new Thread("YaCyDefaultServlet.parseMultipart-" + j) {
                        @Override
                        public void run() {
                            Map.Entry<String, byte[]> job;
                            try {while ((job = files.take()) != POISON) {
                                final String n = job.getKey();
                                final byte[] v = job.getValue();
                                final String filename = args.get(n);
                                final String b64 = Base64Order.standardCoder.encode(v);
                                synchronized (args) {
                                    args.put(n + "$file", b64);
                                    args.remove(n);
                                    args.put(n, filename + ".base64");
                                }
                            }} catch (final InterruptedException e) {}
                        }
                    };
                    p[j].start();
                }
                for (int j = 0; j < t; j++) p[j].join();
            }
        } catch (final Exception ex) {
            ConcurrentLog.info("FILEHANDLER", ex.getMessage());
        }
    }
 }
