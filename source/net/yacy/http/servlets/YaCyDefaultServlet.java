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
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB.AccessRight;
import net.yacy.data.UserDB.Entry;
import net.yacy.http.ProxyHandler;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.http.HTTPDFileHandler;
import net.yacy.server.http.TemplateEngine;
import net.yacy.server.serverClassLoader;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.visualization.RasterPlotter;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.server.InclusiveByteRange;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;

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
 *  pathInfoOnly      If true, only the path info will be applied to the resourceBase
 *
 * </PRE>
 */
public class YaCyDefaultServlet extends HttpServlet  {

    private static final long serialVersionUID = 4900000000000001110L;
    protected ServletContext _servletContext;
    protected boolean _acceptRanges = true;
    protected boolean _dirAllowed = true;
    protected boolean _pathInfoOnly = false;
    protected Resource _resourceBase;
    protected MimeTypes _mimeTypes;
    protected String[] _welcomes;    
    
    protected File _htLocalePath;
    protected File _htDocsPath;    
    protected static final serverClassLoader provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
    protected ConcurrentHashMap<File, SoftReference<Method>> templateMethodCache = null;
    // settings for multipart/form-data
    protected static final File TMPDIR = new File(System.getProperty("java.io.tmpdir"));
    protected static final int SIZE_FILE_THRESHOLD = 20 * 1024 * 1024;
    protected static final FileItemFactory DISK_FILE_ITEM_FACTORY = new DiskFileItemFactory(SIZE_FILE_THRESHOLD, TMPDIR);
    /* ------------------------------------------------------------ */
    @Override
    public void init() throws UnavailableException {
        Switchboard sb = Switchboard.getSwitchboard();
        _htDocsPath = sb.htDocsPath;
        _htLocalePath = sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot");
        
        _servletContext = getServletContext();

        _mimeTypes = new MimeTypes(); 
        String tmpstr = this.getServletContext().getInitParameter("welcomeFile");
        if (tmpstr == null) { 
            _welcomes = HTTPDFileHandler.defaultFiles;
        } else {
            _welcomes = new String[]{tmpstr,"index.html"};
        }
        _acceptRanges = getInitBoolean("acceptRanges", _acceptRanges);
        _dirAllowed = getInitBoolean("dirAllowed", _dirAllowed);
        _pathInfoOnly = getInitBoolean("pathInfoOnly", _pathInfoOnly);

        Resource.setDefaultUseCaches(false); // caching is handled internally (prevent double caching)

        String rb = getInitParameter("resourceBase");
        try {
            if (rb != null) {
                _resourceBase = Resource.newResource(rb);
            } else {
                _resourceBase = Resource.newResource(sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT)); //default
            }
        } catch (IOException e) {
            ConcurrentLog.severe("FILEHANDLER", "YaCyDefaultServlet: resource base (htRootPath) missing");
            ConcurrentLog.logException(e);
            throw new UnavailableException(e.toString());
        }
        if (ConcurrentLog.isFine("FILEHANDLER")) {
            ConcurrentLog.fine("FILEHANDLER","YaCyDefaultServlet: resource base = " + _resourceBase);
        }
        templateMethodCache = new ConcurrentHashMap<File, SoftReference<Method>>();
    }


    /* ------------------------------------------------------------ */
    protected boolean getInitBoolean(String name, boolean dft) {
        String value = getInitParameter(name);
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
    public Resource getResource(String pathInContext) {
        Resource r = null;
        try {
            if (_resourceBase != null) {
                r = _resourceBase.addPath(pathInContext);
            } else {
                URL u = _servletContext.getResource(pathInContext);
                r = Resource.newResource(u);
            }

            if (ConcurrentLog.isFine("FILEHANDLER")) {
                ConcurrentLog.fine("FILEHANDLER","YaCyDefaultServlet: Resource " + pathInContext + "=" + r);
            }
        } catch (IOException e) {
            // ConcurrentLog.logException(e);
        }

        return r;
    }

    /* ------------------------------------------------------------ */
    protected boolean hasDefinedRange(Enumeration<String> reqRanges) {
        return (reqRanges != null && reqRanges.hasMoreElements());
    }
    
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
        
        String pathInContext = URIUtil.addPaths(servletPath, pathInfo);
        boolean endsWithSlash = (pathInfo == null ? request.getServletPath() : pathInfo).endsWith(URIUtil.SLASH);

        // Find the resource 
        Resource resource = null;

        try {

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
            
            // find resource
            resource = getResource(pathInContext);

            if (!hasClass && (resource == null || !resource.exists()) && !pathInContext.contains("..")) {
                // try to get this in the alternative htDocsPath
                resource = Resource.newResource(new File(HTTPDFileHandler.htDocsPath, pathInContext));
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

                if (!endsWithSlash) {
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
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } finally {
            if (resource != null) {
                resource.release();
            }
        }
    }
    
    /* ------------------------------------------------------------ */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doTrace(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
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
    protected String getWelcomeFile(String pathInContext) {
        if (_welcomes == null) {
            return null;
        }
        for (String _welcome : _welcomes) {
            String welcome_in_context = URIUtil.addPaths(pathInContext, _welcome);
            Resource welcome = getResource(welcome_in_context);
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
    protected boolean passConditionalHeaders(HttpServletRequest request, HttpServletResponse response, Resource resource)
            throws IOException {
        try {
            if (!request.getMethod().equals(HttpMethods.HEAD)) {

                String ifms = request.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
                if (ifms != null) {

                    long ifmsl = request.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);
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
                long date = request.getDateHeader(HttpHeaders.IF_UNMODIFIED_SINCE);

                if (date != -1) {
                    if (resource.lastModified() / 1000 > date / 1000) {
                        response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                        return false;
                    }
                }
            }
        } catch (IllegalArgumentException iae) {
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, iae.getMessage());
            }
            throw iae;
        }
        return true;
    }

    /* ------------------------------------------------------------------- */
    protected void sendDirectory(HttpServletRequest request,
            HttpServletResponse response,
            Resource resource,
            String pathInContext)
            throws IOException {
        if (!_dirAllowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
         
        String base = URIUtil.addPaths(request.getRequestURI(), URIUtil.SLASH);

        String dir = resource.getListHTML(base, pathInContext.length() > 1);
        if (dir == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No directory");
            return;
        }

        byte[] data = dir.getBytes("UTF-8");
        response.setContentType(MimeTypes.TEXT_HTML_UTF_8);
        response.setContentLength(data.length);
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
    protected void sendData(HttpServletRequest request,
            HttpServletResponse response,
            boolean include,
            Resource resource,
            Enumeration<String> reqRanges)
            throws IOException {

        final long content_length = resource.length();

        // Get the output stream (or writer)
        OutputStream out;
        try {
            out = response.getOutputStream();
        } catch (IllegalStateException e) {
            out = new WriterOutputStream(response.getWriter());
        }

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
            List<?> ranges = InclusiveByteRange.satisfiableRanges(reqRanges, content_length);

            //  if there are no satisfiable ranges, send 416 response
            if (ranges == null || ranges.size() == 0) {
                writeHeaders(response, resource, content_length);
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        InclusiveByteRange.to416HeaderRangeString(content_length));
                resource.writeTo(out, 0, content_length);
                out.close();
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
                out.close();
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
            if (in != null) in.close();
            multi.close();
        }
    }

    /* ------------------------------------------------------------ */
    protected void writeHeaders(HttpServletResponse response, Resource resource, long count) {
        if (response.getContentType() == null) {
            Buffer extensionmime;
            if ((extensionmime = _mimeTypes.getMimeByExtension(resource.getName())) != null) {
                response.setContentType(extensionmime.toString());
            }
        }

        long lml = resource.lastModified();
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

        if (_acceptRanges) {
            response.setHeader(HeaderFramework.ACCEPT_RANGES, "bytes");
        }
    }

    
    protected Object invokeServlet(final File targetClass, final RequestHeader request, final serverObjects args) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return rewriteMethod(targetClass).invoke(null, new Object[]{request, args, Switchboard.getSwitchboard()}); // add switchboard
    }

    protected RequestHeader generateLegacyRequestHeader(HttpServletRequest request, String target, String targetExt) {
        RequestHeader legacyRequestHeader = ProxyHandler.convertHeaderFromJetty(request);

        legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, request.getRemoteAddr());
        legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, target);
        legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_EXT, targetExt);
        Switchboard sb = Switchboard.getSwitchboard();
        if (legacyRequestHeader.containsKey(RequestHeader.AUTHORIZATION)) {
            if (HttpServletRequest.BASIC_AUTH.equalsIgnoreCase(request.getAuthType())) {
            } else {
                // handle DIGEST auth for legacyHeader (create username:md5pwdhash
                if (request.getUserPrincipal() != null) {
                    String userpassEncoded = request.getHeader(RequestHeader.AUTHORIZATION); // e.g. "Basic AdminMD5hash"
                    if (userpassEncoded != null) {
                        if (request.isUserInRole(AccessRight.ADMIN_RIGHT.toString()) && !sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5,"").isEmpty()) {
                            // fake admin authentication for legacyRequestHeader (as e.g. DIGEST is not supported by legacyRequestHeader)
                            legacyRequestHeader.put(RequestHeader.AUTHORIZATION, HttpServletRequest.BASIC_AUTH + " "
                                    + sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""));
                        } else {
                            // fake Basic auth header for Digest auth  (Basic username:md5pwdhash)
                            String username = request.getRemoteUser();
                            Entry user = sb.userDB.getEntry(username);
                            if (user != null) {
                                legacyRequestHeader.put(RequestHeader.AUTHORIZATION, HttpServletRequest.BASIC_AUTH + " "
                                        + username + ":" + user.getMD5EncodedUserPwd());
                            }
                        }
                    }
                }
            }
        }
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
            final File localePath = new File(_htLocalePath, localeSelection + '/' + path);
            if (localePath.exists()) {
                return localePath;  // avoid "NoSuchFile" troubles if the "localeSelection" is misspelled
            }
        }

        File docsPath = new File(_htDocsPath, path);
        if (docsPath.exists()) {
            return docsPath;
        }
        return _resourceBase.addPath(path).getFile();
    }

    protected File rewriteClassFile(final File template) {
        try {
            String f = template.getCanonicalPath();
            final int p = f.lastIndexOf('.');
            if (p < 0) {
                return null;
            }
            f = f.substring(0, p) + ".class";
            final File cf = new File(f);
            if (cf.exists()) {
                return cf;
            }
            return null;
        } catch (final IOException e) {
            return null;
        }
    }

    protected Method rewriteMethod(final File classFile) throws InvocationTargetException {
        Method m = null;
        // now make a class out of the stream
        try {
            final SoftReference<Method> ref = templateMethodCache.get(classFile);
            if (ref != null) {
                m = ref.get();
                if (m == null) {
                    templateMethodCache.remove(classFile);
                } else {
                    return m;
                }
            }

            final Class<?> c = provider.loadClass(classFile);           
            final Class<?>[] params = new Class[]{
                RequestHeader.class,
                serverObjects.class,
                serverSwitch.class};
            m = c.getMethod("respond", params);

            if (MemoryControl.shortStatus()) {
                templateMethodCache.clear();
            } else {
                // store the method into the cache
                templateMethodCache.put(classFile, new SoftReference<Method>(m));
            }
        } catch (final ClassNotFoundException e) {
            ConcurrentLog.severe("FILEHANDLER","YaCyDefaultServlet: class " + classFile + " is missing:" + e.getMessage());
            throw new InvocationTargetException(e, "class " + classFile + " is missing:" + e.getMessage());
        } catch (final NoSuchMethodException e) {
            ConcurrentLog.severe("FILEHANDLER","YaCyDefaultServlet: method 'respond' not found in class " + classFile + ": " + e.getMessage());
            throw new InvocationTargetException(e, "method 'respond' not found in class " + classFile + ": " + e.getMessage());
        }
        return m;
    }

    protected void handleTemplate(String target,  HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
        Switchboard sb = Switchboard.getSwitchboard();

        String localeSelection = sb.getConfig("locale.language", "default");
        File targetFile = getLocalizedFile(target, localeSelection);
        File targetClass = rewriteClassFile(_resourceBase.addPath(target).getFile());
        String targetExt = target.substring(target.lastIndexOf('.') + 1);

        if ((targetClass != null)) {
            serverObjects args = new serverObjects();
            Enumeration<String> argNames = request.getParameterNames();
            while (argNames.hasMoreElements()) {
                String argName = argNames.nextElement();
                args.put(argName, request.getParameter(argName));
            }
            //TODO: for SSI request, local parameters are added as attributes, put them back as parameter for the legacy request
            //      likely this should be implemented via httpservletrequestwrapper to supply complete parameters  
            Enumeration<String> attNames = request.getAttributeNames();
            while (attNames.hasMoreElements()) {
                String argName = attNames.nextElement();
                args.put(argName, request.getAttribute(argName).toString());
            }          
            RequestHeader legacyRequestHeader = generateLegacyRequestHeader(request, target, targetExt);
            // add multipart-form fields to parameter
            if (ServletFileUpload.isMultipartContent(request)) {
                final String bodyEncoding = request.getHeader(HeaderFramework.CONTENT_ENCODING);
                if (HeaderFramework.CONTENT_ENCODING_GZIP.equalsIgnoreCase(bodyEncoding)) {
                    parseMultipart(new GZIPRequestWrapper(request),args);
                } else {
                    parseMultipart(request, args);
                }
            }
            // eof modification to read attribute
            Object tmp;
            try {
                if (args.isEmpty()) {
                    // yacy servlets typically test for args != null (but not for args .isEmpty())
                    tmp = invokeServlet(targetClass, legacyRequestHeader, null); 
                } else {
                    tmp = invokeServlet(targetClass, legacyRequestHeader, args);
                }
            } catch (InvocationTargetException e) {
                ConcurrentLog.logException(e);
                throw new ServletException(targetFile.getAbsolutePath());
            } catch (IllegalArgumentException e) {
                ConcurrentLog.logException(e);
                throw new ServletException(targetFile.getAbsolutePath());
            } catch (IllegalAccessException e) {
                ConcurrentLog.logException(e);
                throw new ServletException(targetFile.getAbsolutePath());
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
                    final BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    bi.createGraphics().drawImage(i, 0, 0, width, height, null);
                    result = RasterPlotter.exportImage(bi, targetExt);
                }

                final String mimeType = Classification.ext2mime(targetExt, MimeTypes.TEXT_HTML);
                response.setContentType(mimeType);
                response.setContentLength(result.length());
                response.setStatus(HttpServletResponse.SC_OK);

                result.writeTo(response.getOutputStream());
                result.close();
                return;
            }

            servletProperties templatePatterns;
            if (tmp == null) {
                // if no args given, then tp will be an empty Hashtable object (not null)
                templatePatterns = new servletProperties();
            } else if (tmp instanceof servletProperties) {
                templatePatterns = (servletProperties) tmp;
            } else {
                templatePatterns = new servletProperties((serverObjects) tmp);
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

            if (targetFile.exists() && targetFile.isFile() && targetFile.canRead()) {
                
                sb.setConfig("server.servlets.called", appendPath(sb.getConfig("server.servlets.called", ""), target));
                if (args != null && !args.isEmpty()) {
                    sb.setConfig("server.servlets.submitted", appendPath(sb.getConfig("server.servlets.submitted", ""), target));
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
                templatePatterns.put("navigation-p2p", sb.getConfigBool(SwitchboardConstants.DHT_ENABLED, true) || !sb.isRobinsonMode() ? 1 : 0);
                String submitted = sb.getConfig("server.servlets.submitted", "");
                boolean crawler_enabled = submitted.indexOf("Crawler_p") >= 0;
                boolean advanced_enabled = crawler_enabled || submitted.indexOf("ConfigBasic") >= 0  || submitted.indexOf("CrawlStart") >= 0;
                templatePatterns.put("navigation-crawlmonitor", crawler_enabled);
                templatePatterns.put("navigation-advanced", advanced_enabled);
                templatePatterns.put(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
                templatePatterns.put(SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
                
                String mimeType = Classification.ext2mime(targetExt, MimeTypes.TEXT_HTML);

                InputStream fis;
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
                TemplateEngine.writeTemplate(targetFile.getName(), fis, bas, templatePatterns);                
                fis.close();
                // handle SSI
                parseSSI (bas.toByteArray(),request,response);
            }
        }
    }
    
    private static final String appendPath(String proplist, String path) {
        if (proplist.length() == 0) return path;
        if (proplist.indexOf(path) >= 0) return proplist;
        return proplist + "," + path;
    }
    
    /**
     * parse SSI line and include resource (<!--#include virtual="file.html" -->)
     */
    protected void parseSSI(final byte[] in, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        ByteBuffer buffer = new ByteBuffer(in);
        OutputStream out = response.getOutputStream();
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
                RequestDispatcher dispatcher = request.getRequestDispatcher(path);
                try {
                    dispatcher.include(request, response);
                } catch (IOException ex) {
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
        out.close();
        buffer.close();
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
    protected void parseMultipart(HttpServletRequest request, serverObjects args) throws IOException {

        // reject too large uploads
        if (request.getContentLength() > SIZE_FILE_THRESHOLD) throw new IOException("FileUploadException: uploaded file too large = " + request.getContentLength());

        // check if we have enough memory
        if (!MemoryControl.request(request.getContentLength() * 3, false)) {
        	throw new IOException("not enough memory available for request. request.getContentLength() = " + request.getContentLength() + ", MemoryControl.available() = " + MemoryControl.available());
        }                
        ServletFileUpload upload = new ServletFileUpload(DISK_FILE_ITEM_FACTORY);
        upload.setFileSizeMax(SIZE_FILE_THRESHOLD);
        try {
            // Parse the request to get form field items
            @SuppressWarnings("unchecked")             
            List<FileItem> fileItems = upload.parseRequest(request);                 
            // Process the uploaded file items
            Iterator<FileItem> i = fileItems.iterator();
            while (i.hasNext()) {
                FileItem item = i.next();
                if (item.isFormField()) {
                    // simple text
                    if (item.getContentType() == null || !item.getContentType().contains("charset")) {
                        // old yacy clients use their local default charset, on most systems UTF-8 (I hope ;)
                        args.add(item.getFieldName(), item.getString("UTF-8"));
                    } else {
                        // use default encoding (given as header or ISO-8859-1)
                        args.add(item.getFieldName(), item.getString());
                    }
                }
            }
        } catch (Exception ex) {
            ConcurrentLog.logException(ex);
        }
    }

    /**
     * wraps request to uncompress gzip'ed input stream
     */
    private class GZIPRequestWrapper extends HttpServletRequestWrapper {

        private final ServletInputStream is;

        public GZIPRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.is = new GZIPRequestStream(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return is;
        }

    }

    private class GZIPRequestStream extends ServletInputStream {

        private final GZIPInputStream in;

        public GZIPRequestStream(HttpServletRequest request) throws IOException {
            this.in = new GZIPInputStream(request.getInputStream());
        }

        @Override
        public int read() throws IOException {
            return in.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return in.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return in.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
 }
