/**
 *  RequestHeader
 *  Copyright 2008 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 22.08.2008 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.security.Principal;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.NumberTools;
import org.eclipse.jetty.server.CookieCutter;
import org.eclipse.jetty.util.URIUtil;

/**
 * YaCy servlet request header.
 * YaCy runs in a servlet container (Jetty), starting 2016 this implements the
 * widely used HttpServletRequest for tighter and further standardization and
 * adherence to common standards, to make the use of HttpServletRequest parameters
 * available to YaCy servlets.
 */
public class RequestHeader extends HeaderFramework implements HttpServletRequest {

    // request header properties

    public static final String CONNECTION = "Connection";
    public static final String PROXY_CONNECTION = "Proxy-Connection";
    public static final String KEEP_ALIVE = "Keep-Alive";

    public static final String AUTHORIZATION = "Authorization";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";

    public static final String UPGRADE = "Upgrade";
    public static final String TE = "TE";

    public static final String X_CACHE = "X-Cache";
    public static final String X_CACHE_LOOKUP = "X-Cache-Lookup";
    public static final String X_Real_IP = "X-Real-IP";

    public static final String COOKIE = "Cookie";

    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String IF_RANGE = "If-Range";
    public static final String REFERER = "Referer"; // a misspelling of referrer that occurs as an HTTP header field. Its defined so in the http protocol, so please don't 'fix' it!

    private static final long serialVersionUID = 0L;

    public enum FileType {
        HTML, JSON, XML
    }

    private final HttpServletRequest _request; // reference to the original request
    private Date date_cache_IfModifiedSince = null;

    public RequestHeader() {
        super();
        this._request = null;
    }

    public RequestHeader(HttpServletRequest request) {
        super();
        this._request = request;
    }

    public DigestURL referer() {
        final String referer = get(REFERER);
        if (referer == null) return null;
        try {
            return new DigestURL(referer);
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    public String refererHost() {
        final MultiProtocolURL url = referer();
        if (url == null) return null;
        return url.getHost();
    }

    
    public Date ifModifiedSince() {
        if (this.date_cache_IfModifiedSince != null) return date_cache_IfModifiedSince;
        long time = this.getDateHeader(RequestHeader.IF_MODIFIED_SINCE);
        if (time > 0) this.date_cache_IfModifiedSince = new Date(time);
        return this.date_cache_IfModifiedSince;
    }

    public FileType fileType() {
        String path = this.getPathInfo();
        if (path == null) return FileType.HTML;
        path = path.toLowerCase(Locale.ROOT);
        if (path.endsWith(".json")) return FileType.JSON;
        if (path.endsWith(".xml")) return FileType.XML;
        if (path.endsWith(".rdf")) return FileType.XML;
        if (path.endsWith(".rss")) return FileType.XML;
        return FileType.HTML;
    }

    public boolean accessFromLocalhost() {
        // authorization for localhost, only if flag is set to grant localhost access as admin
        final String clientIP = this.getRemoteAddr();
        if ( !Domains.isLocalhost(clientIP) ) {
            return false;
        }
        final String refererHost = this.refererHost();
        if (refererHost == null || refererHost.isEmpty() || Domains.isLocalhost(refererHost)) return true;
        return false;
    }
    
    /**
     * Gets the header entry "Cookie" as on string containing all cookies
     *
     * @return String with cookies separated by ';'
     * @see getCookies()
     * @deprecated depreceated since 1.92, use getCookies()
     */
    @Deprecated
    public String getHeaderCookies() {
        String cookiestring = this.get(COOKIE); // get from legacy or HttpServletRequest
        if (cookiestring == null) {
            return "";
        }
		return cookiestring;
    }

    // implementation of HttpServletRequest procedures
    // the general approach is to prefer values in the YaCy legacy RequestHeader.map and if no value exists
    // to use the httpservletrequest. This approach is used, because legacy requestheader allows to add or
    // change header values. This makes sure a modified or added value is used.
    // At this point of implementation a original request is not required, so test for _request != null is needed.

    /**
     * This overrides the legacy get() to make sure the original _request values
     * are considered
     * @param key header name
     * @return value
     */
    @Override
    public String get(Object key) {
        String value = super.get(key); // important to use super.get
        if (value == null && _request != null) {
            return _request.getHeader((String)key);
        }
        return value;
    }

    /**
     * Override legacy containsKey to be sure original request headers are incl.
     * in the check.
     * Use of this legacy methode is discouraged
     * @param key headername
     * @return
     */
    @Override
    public boolean containsKey(Object key) {
        boolean val = super.containsKey(key);
        if (val) {
            return val;
        } else if (_request != null) {
            return _request.getHeader((String) key) != null;
        }
        return val;
    }

    /**
     * Override legacy mime()
     * @return mime string or "application/octet-stream" if content type missing
     * @see getContentType()
     */
    @Override
    public String mime() {
        if (super.containsKey(HeaderFramework.CONTENT_TYPE)) {
            return super.mime();
        }
		if (_request != null) {
		    return _request.getContentType();
		}
        return "application/octet-stream";
    }

    @Override
    public String getAuthType() {
        if (_request != null) {
            return _request.getAuthType();
        }
        return null; // according to spec return only value if authenticated
    }

    @Override
    public Cookie[] getCookies() {
        if (_request != null) {
            return _request.getCookies();
        }
		String cstr = super.get(COOKIE);
		if (cstr != null) {
		    CookieCutter cc = new CookieCutter(); // reuse jetty cookie parser
		    cc.addCookieField(cstr);
		    return cc.getCookies();
		}
		return null;
    }

    @Override
    public long getDateHeader(String name) {
        Date d = super.headerDate(name);
        if (d != null) {
            return d.getTime();
        }
		if (_request != null) {
		    return _request.getDateHeader(name);
		}
		return -1;
    }

    @Override
    public String getHeader(String name) {
        return this.get(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (_request != null) {
            return _request.getHeaders(name);
        }
        return null;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        if (_request != null) {
            return _request.getHeaderNames();
        }
        return null; // not supported in legacy RequestHeader, safe to return null
    }

    @Override
    public int getIntHeader(String name) {
        if (super.containsKey(name)) {
            String val = super.get(name);
            if (val != null) {
                try {
                    return Integer.parseInt(val);
                } catch (NumberFormatException ex) {}
            }
        } else if(_request != null) {
            return _request.getIntHeader(name);
        }
        return -1;
    }

    @Override
    public String getMethod() {
        if (_request != null) {
            return _request.getMethod();
        }
		return HeaderFramework.METHOD_POST;
    }

    @Override
    public String getPathInfo() {
        if (super.containsKey(HeaderFramework.CONNECTION_PROP_PATH)) {
            return super.get(HeaderFramework.CONNECTION_PROP_PATH);
        } else if (_request != null) {
            return _request.getPathInfo();
        }
        return ""; // TODO: in difference to standard return empty string (instead null) as we not always check for null
    }

    @Override
    public String getPathTranslated() {
        if (_request != null) {
            return _request.getPathTranslated();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getContextPath() {
        if (_request != null) {
            return _request.getContextPath();
        }
		return "";
    }

    @Override
    public String getQueryString() {
        if (_request != null) {
            return _request.getQueryString();
        }
		return null;
    }

    @Override
    public String getRemoteUser() {
        if (_request != null)
            return _request.getRemoteUser();
		return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        if (_request != null) {
            return _request.isUserInRole(role);
        }
		return false;
    }

    @Override
    public Principal getUserPrincipal() {
        if (_request != null) {
            return _request.getUserPrincipal();
        }
		return null;
    }

    @Override
    public String getRequestedSessionId() {
        if (_request != null) {
            return _request.getRequestedSessionId();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getRequestURI() {
        if (_request != null) {
            return _request.getRequestURI();
        }
		return super.get(HeaderFramework.CONNECTION_PROP_PATH, "/"); // TODO: property as header discouraged (but currently used)
    }

    @Override
    public StringBuffer getRequestURL() {
        if (_request != null) {
            return _request.getRequestURL();
        }
		StringBuffer sbuf = new StringBuffer(32);
		URIUtil.appendSchemeHostPort(sbuf, this.getScheme(), this.getServerName(), this.getServerPort());
		sbuf.append(this.getRequestURI());
		return sbuf;
    }

    @Override
    public String getServletPath() {
        if (_request != null) {
            return _request.getServletPath();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public HttpSession getSession(boolean create) {
        if (_request != null) {
            return _request.getSession(create);
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public HttpSession getSession() {
        if (_request != null) {
            return _request.getSession();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String changeSessionId() {
        if (_request != null) {
            return _request.changeSessionId();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        if (_request != null) {
            return _request.isRequestedSessionIdValid();
        }
		return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        if (_request != null) {
            return _request.isRequestedSessionIdFromCookie();
        }
		return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        if (_request != null) {
            return _request.isRequestedSessionIdFromURL();
        }
		return false;
    }

    @Deprecated // As of Version 2.1 of the Java Servlet API, use isRequestedSessionIdFromURL() instead.
    @Override
    public boolean isRequestedSessionIdFromUrl() {
        if (_request != null) {
            return _request.isRequestedSessionIdFromUrl();
        }
		return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        if (_request != null) {
            return _request.authenticate(response);
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void login(String username, String password) throws ServletException {
        if (_request != null) {
            _request.login(username, password);
        } else {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    @Override
    public void logout() throws ServletException {
        if (_request != null) {
            _request.logout();
        }

        super.remove(AUTHORIZATION);
        // TODO: take care of legacy login cookie (and possibly cached UserDB login status)

    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        if (_request != null) {
            return _request.getParts();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        if (_request != null) {
            return _request.getPart(name);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        if (_request != null) {
            return _request.upgrade(handlerClass);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Object getAttribute(String name) {
        if (_request != null) {
            return _request.getAttribute(name);
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        if (_request != null) {
            return _request.getAttributeNames();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getCharacterEncoding() {
        String enc = super.getCharacterEncoding();
        if (enc == null && _request != null) return _request.getCharacterEncoding();
        return enc;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        if (_request != null) {
            _request.setCharacterEncoding(env);
        } else {
            // charset part of Content-Type header
            // Example: "Content-Type: text/html; charset=ISO-8859-4"
            // see https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.17
            //
            final String mime = mime();
            super.put(CONTENT_TYPE, mime + "; charset=" + env);
        }
    }

    @Override
    public int getContentLength() {
        int len = super.getContentLength();
        if (len < 0 && _request != null) return _request.getContentLength();
        return len;
    }

    @Override
    public long getContentLengthLong() {
        long len = super.getContentLengthLong();
        if (len < 0 && _request != null) return _request.getContentLengthLong();
        return len;
    }

    @Override
    public String getContentType() {
        if (super.containsKey(HeaderFramework.CONTENT_TYPE)) {
            return super.mime();
        }
		if (_request != null) {
		    return _request.getContentType();
		}
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (_request != null) {
            return _request.getInputStream();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getParameter(String name) {
        if (_request != null) {
            return _request.getParameter(name);
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Enumeration<String> getParameterNames() {
        if (_request != null) {
            return _request.getParameterNames();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String[] getParameterValues(String name) {
        if (_request != null) {
            return _request.getParameterValues(name);
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (_request != null) {
            return _request.getParameterMap();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getProtocol() {
        // here we can directly check original request, as protocol is not expected to be modified
        if (_request != null) {
            return _request.getProtocol();
        }
		return super.get(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_1_1);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getScheme() {
        // here we can directly check original request first, as scheme is not expected to be changed
        if (_request != null) {
            return _request.getScheme();
        }
		if (super.containsKey(HeaderFramework.CONNECTION_PROP_PROTOCOL)) {
		    return super.get(HeaderFramework.CONNECTION_PROP_PROTOCOL);
		}
		return "http";
    }

    @Override
    public String getServerName() {
        if (super.containsKey(HeaderFramework.HOST)) {
            final String hostport = super.get(HeaderFramework.HOST);
            if (hostport.contains("[")) { // handle ipv6
                final int pos = hostport.lastIndexOf(']');
                if (pos > 0) {
                    return hostport.substring(0, pos + 1);
                }
            } else if (hostport.contains(":")) {
                final int pos = hostport.indexOf(':');
                if (pos > 0) {
                    return hostport.substring(0, pos);
                }
            }
            return hostport;
        } else if (_request != null) {
            return _request.getServerName();
        } else {
            return Domains.LOCALHOST;
        }
    }

    @Override
    public int getServerPort() {
        if (super.containsKey(HeaderFramework.HOST)) {
            final String hostport = super.get(HeaderFramework.HOST);
            int port = getScheme().equals("https") ? 443 : 80; // init with default ports
            final int pos = hostport.lastIndexOf(':');
            if (pos > 0 && hostport.lastIndexOf(']') < pos) { // check for ipv6
                port = NumberTools.parseIntDecSubstring(hostport, pos + 1);
            }
            return port;
        } else if (_request != null) {
            return _request.getServerPort();
        } else {
            return 80;
        }
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (_request != null) {
            return _request.getReader();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getRemoteAddr() {
        if (this._request != null) {
        	return client(_request);
        }
		return super.get(HeaderFramework.CONNECTION_PROP_CLIENTIP);
    }

    public static String client(final ServletRequest request) {
        String clientHost = request.getRemoteAddr();
        if (request instanceof HttpServletRequest) {
        	String XRealIP = ((HttpServletRequest) request).getHeader(X_Real_IP);
        	if (XRealIP != null && XRealIP.length() > 0) clientHost = XRealIP; // get IP through nginx config "proxy_set_header X-Real-IP $remote_addr;"
        }
		return clientHost;
    }
    
    @Override
    public String getRemoteHost() {
        if (_request != null) {
            return host(_request);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    public static String host(final ServletRequest request) {
        String clientHost = request.getRemoteHost();
        if (request instanceof HttpServletRequest) {
        	String XRealIP = ((HttpServletRequest) request).getHeader(X_Real_IP);
        	if (XRealIP != null && XRealIP.length() > 0) clientHost = XRealIP; // get IP through nginx config "proxy_set_header X-Real-IP $remote_addr;"
        }
		return clientHost;
    }

    @Override
    public void setAttribute(String name, Object o) {
        if (_request != null) {
            _request.setAttribute(name, o);
        } else {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    @Override
    public void removeAttribute(String name) {
        if (_request != null) {
            _request.removeAttribute(name);
        } else {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    @Override
    public Locale getLocale() {
        if (this._request != null) {
            return _request.getLocale();
        } else if (super.containsKey(HeaderFramework.ACCEPT_LANGUAGE)) {
            final String lng = super.get(HeaderFramework.ACCEPT_LANGUAGE);
            return Locale.forLanguageTag(lng);
        }
        return Locale.getDefault(); // to avoid dependency on Switchboard just use system default
    }

    @Override
    public Enumeration<Locale> getLocales() {
        if (this._request != null) {
            return _request.getLocales();
        }
		throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isSecure() {
        if (_request != null) {
            return _request.isSecure();
        }
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        if (_request != null) {
            return _request.getRequestDispatcher(path);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    @Deprecated // Deprecated. As of Version 2.1 of the Java Servlet API, use ServletContext.getRealPath(java.lang.String) instead.
    public String getRealPath(String path) {
        if (_request != null) {
            return _request.getRealPath(path);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getRemotePort() {
        if (_request != null) {
            return _request.getRemotePort();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getLocalName() {
        if (_request != null) {
            return _request.getLocalName();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getLocalAddr() {
        if (_request != null) {
            return _request.getLocalAddr();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getLocalPort() {
        if (_request != null) {
            return _request.getLocalPort();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ServletContext getServletContext() {
        if (_request != null) {
            return _request.getServletContext();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        if (_request != null) {
            return _request.startAsync();
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        if (_request != null) {
            return _request.startAsync(servletRequest, servletResponse);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isAsyncStarted() {
        if (_request != null) {
            return _request.isAsyncStarted();
        }
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        if (_request != null) {
            return _request.isAsyncStarted();
        }
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        if (_request != null) {
            return _request.getAsyncContext();
        }
        return null;
    }

    @Override
    public DispatcherType getDispatcherType() {
        if (_request != null) {
            return _request.getDispatcherType();
        }
        return null;
    }




}
