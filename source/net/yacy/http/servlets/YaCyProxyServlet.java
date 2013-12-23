package net.yacy.http.servlets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.http.ProxyHandler;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.server.http.ChunkedInputStream;
import net.yacy.server.http.HTTPDProxyHandler;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.servlets.ProxyServlet;

/**
 * Servlet to implement proxy via url parameter "/proxy.html?url=xyz_urltoproxy"
 * this implementation uses the existing proxy functions from YaCy HTTPDProxyHandler
 * 
 * InitParameters
 *    ProxyHost : hostname of proxy host, default is "localhost"
 *    ProxyPort : port of the proxy host, default 8090
 * 
 * functionality
 *  - get parameters
 *  - convert headers to YaCy style headers and parameters
 *  - call existing HTTPDProxy
 *  - revert response headers back from YaCy style to servlet specification
 *  - handle rewrite of link (to point to proxy)
 *  - send to client
 * 
 * later improvemnts should/could use implementation to avoid back and forth converting
 * between YaCy and Servlet header/parameter style and use proxy implementation within
 * servlet specification or a existing reverse-proxy library.
 * 
 */
public class YaCyProxyServlet extends ProxyServlet implements Servlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // must be lower case (header names are internally converted to lower)
        _DontProxyHeaders.add("host"); // to prevent Host header setting from original servletrequest (which is localhost)

    }
    /* ------------------------------------------------------------ */

    @Override
    public void service (ServletRequest req, ServletResponse res) throws ServletException, IOException {

        final HttpServletRequest request = (HttpServletRequest) req;
        final HttpServletResponse response = (HttpServletResponse) res;

        if ("CONNECT".equalsIgnoreCase(request.getMethod())) {
            handleConnect(request, response);
        } else {
            String action = null;

            final Continuation continuation = ContinuationSupport.getContinuation(request);

            if (!continuation.isInitial()) {
                response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT); // Need better test that isInitial
                return;
            }
            URL proxyurl = null;
            String strARGS = request.getQueryString();
            if (strARGS == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"url parameter missing");
                return;
            }

            if (strARGS.startsWith("action=")) {
                int detectnextargument = strARGS.indexOf("&");
                action = strARGS.substring(7, detectnextargument);
                strARGS = strARGS.substring(detectnextargument + 1);
            }
            if (strARGS.startsWith("url=")) {
                final String strUrl = strARGS.substring(4); // strip "url="

                try {
                    proxyurl = new URL(strUrl);
                } catch (final MalformedURLException e) {
                    proxyurl = new URL(URLDecoder.decode(strUrl, UTF8.charset.name()));

                }
            }
            if (proxyurl == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"url parameter missing");
                return;
            }
            int port = proxyurl.getPort();
            if (port < 1) {
                port = 80;
            }

            String host = proxyurl.getHost();
            if (proxyurl.getPort() != -1) {
                host += ":" + proxyurl.getPort();
            }
            RequestHeader yacyRequestHeader = ProxyHandler.convertHeaderFromJetty(request);
            yacyRequestHeader.remove(RequestHeader.KEEP_ALIVE);
            yacyRequestHeader.remove(HeaderFramework.CONTENT_LENGTH);
            
            final HashMap<String, Object> prop = new HashMap<String, Object>();
            prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_1_1);
            prop.put(HeaderFramework.CONNECTION_PROP_HOST, proxyurl.getHost());
            prop.put(HeaderFramework.CONNECTION_PROP_PATH, proxyurl.getFile().replaceAll(" ", "%20"));
            prop.put(HeaderFramework.CONNECTION_PROP_REQUESTLINE, "PROXY");
            prop.put("CLIENTIP", "0:0:0:0:0:0:0:1");

            yacyRequestHeader.put(HeaderFramework.HOST, proxyurl.getHost());
            // temporarily add argument to header to pass it on to augmented browsing
            if (action != null) yacyRequestHeader.put("YACYACTION", action);

            final ByteArrayOutputStream tmpproxyout = new ByteArrayOutputStream();
            HTTPDProxyHandler.doGet(prop, yacyRequestHeader, tmpproxyout, ClientIdentification.yacyProxyAgent);
            
            // reparse header to extract content-length and mimetype
            final ResponseHeader outgoingHeader = new ResponseHeader(200); // 
            final InputStream proxyout = new ByteArrayInputStream(tmpproxyout.toByteArray());
            String line = readLine(proxyout);
            while (line != null && !line.equals("")) {
                int p;
                if ((p = line.indexOf(':')) >= 0) {
                    // store a property
                    outgoingHeader.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
                }
                line = readLine(proxyout);
            }
            if (line == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Proxy Header missing");
                return;
            }

            final int httpStatus = Integer.parseInt((String) prop.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_STATUS));

            String directory = "";
            if (proxyurl.getPath().lastIndexOf('/') > 0) {
                directory = proxyurl.getPath().substring(0, proxyurl.getPath().lastIndexOf('/'));
            }
           
            if (response.getHeader(HeaderFramework.LOCATION) != null) {
                // rewrite location header
                String location = response.getHeader(HeaderFramework.LOCATION);
                String actioncmdstr = (action != null) ? "action=" + action + "&" : "";
                if (location.startsWith("http")) {
                    location = "/proxy.html?" + actioncmdstr + "url=" + location;
                } else {
                    location = "/proxy.html?" + actioncmdstr + "url=http://" + proxyurl.getHost() + "/" + location;
                }
                //outgoingHeader.put(HeaderFramework.LOCATION, location);
                response.addHeader(HeaderFramework.LOCATION, location);
            }

            //final String mimeType = outgoingHeader.getContentType();
            final String mimeType = outgoingHeader.getContentType();
            if ((mimeType != null) && (mimeType.startsWith("text/html") || mimeType.startsWith("text"))) {
                final StringWriter buffer = new StringWriter();

                if (outgoingHeader.containsKey(HeaderFramework.TRANSFER_ENCODING)) {
                    FileUtils.copy(new ChunkedInputStream(proxyout), buffer, UTF8.charset);
                } else {
                    FileUtils.copy(proxyout, buffer, UTF8.charset);
                }
                final String sbuffer = buffer.toString();

                final Pattern p = Pattern.compile("(href=\"|src=\")([^\"]+)|(href='|src=')([^']+)|(url\\(')([^']+)|(url\\(\")([^\"]+)|(url\\()([^\\)]+)");
                final Matcher m = p.matcher(sbuffer);
                final StringBuffer result = new StringBuffer(80);
                Switchboard sb = Switchboard.getSwitchboard();
                while (m.find()) {
                    String init = null;
                    if (m.group(1) != null) { init = m.group(1); }
                    if (m.group(3) != null) { init = m.group(3); }
                    if (m.group(5) != null) { init = m.group(5); }
                    if (m.group(7) != null) { init = m.group(7); }
                    if (m.group(9) != null) { init = m.group(9); }
                    String url = null;
                    if (m.group(2) != null) { url = m.group(2); }
                    if (m.group(4) != null) { url = m.group(4); }
                    if (m.group(6) != null) { url = m.group(6); }
                    if (m.group(8) != null) { url = m.group(8); }
                    if (m.group(10) != null) { url = m.group(10); }
                    if (url.startsWith("data:") || url.startsWith("#") || url.startsWith("mailto:") || url.startsWith("javascript:")) {
                        String newurl = init + url;
                        newurl = newurl.replaceAll("\\$", "\\\\\\$");
                        m.appendReplacement(result, newurl);

                    } else if (url.startsWith("http")) {
                        // absoulte url of form href="http://domain.com/path"
                        if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist")) {
                            try {
                                if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL(url)) != null) {
                                    continue;
                                }
                            } catch (final MalformedURLException e) {
                                ConcurrentLog.fine("PROXY","ProxyServlet: malformed url for url-rewirte " + url);
                                continue;
                            }
                        }

                        String newurl = init + "/proxy.html?url=" + url;
                        newurl = newurl.replaceAll("\\$", "\\\\\\$");
                        m.appendReplacement(result, newurl);

                    } else if (url.startsWith("//")) {
                        // absoulte url but same protocol of form href="//domain.com/path"
                        final String complete_url = proxyurl.getProtocol() + ":" + url;
                        if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist")) {
                            try {
                            if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL(complete_url)) != null) {
                                continue;
                            }
                            } catch (MalformedURLException ex) {
                                ConcurrentLog.fine("PROXY","ProxyServlet: malformed url for url-rewirte " + complete_url);
                                continue;
                            }
                        }

                        String newurl = init + "/proxy.html?url=" + complete_url;
                        newurl = newurl.replaceAll("\\$", "\\\\\\$");
                        m.appendReplacement(result, newurl);

                    } else if (url.startsWith("/")) {
                        // absolute path of form href="/absolute/path/to/linked/page"
                        String newurl = init + "/proxy.html?url=http://" + host + url;
                        newurl = newurl.replaceAll("\\$", "\\\\\\$");
                        m.appendReplacement(result, newurl);

                    } else {
                        // relative path of form href="relative/path"
                        try {
                            MultiProtocolURL target = new MultiProtocolURL("http://" + host + directory + "/" + url);
                            String newurl = init + "/proxy.html?url=" + target.toString();
                            newurl = newurl.replaceAll("\\$", "\\\\\\$");
                            m.appendReplacement(result, newurl);
                        } catch (final MalformedURLException e) {}
                    }
                }
                m.appendTail(result);
               
                byte[] sbb = UTF8.getBytes(result.toString());

                // add some proxy-headers to response header
                response.setContentType(outgoingHeader.getContentType());
                if (outgoingHeader.containsKey(HeaderFramework.SERVER)) {
                    response.addHeader(HeaderFramework.SERVER, outgoingHeader.get(HeaderFramework.SERVER));
                }
                if (outgoingHeader.containsKey(HeaderFramework.DATE)) {
                    response.addHeader(HeaderFramework.DATE, outgoingHeader.get(HeaderFramework.DATE));
                }                    
                if (outgoingHeader.containsKey(HeaderFramework.LAST_MODIFIED)) {
                    response.addHeader(HeaderFramework.LAST_MODIFIED, outgoingHeader.get(HeaderFramework.LAST_MODIFIED));
                }    
                if (outgoingHeader.containsKey(HeaderFramework.EXPIRES)) {
                    response.addHeader(HeaderFramework.EXPIRES, outgoingHeader.get(HeaderFramework.EXPIRES));
                }   
                
                response.setStatus(httpStatus);
                response.addIntHeader(HeaderFramework.CONTENT_LENGTH, sbb.length);
                response.getOutputStream().write(sbb);
                       
            } else {
                if ((response.getHeader(HeaderFramework.CONTENT_LENGTH) == null) && prop.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE)) {
                    response.addHeader(HeaderFramework.CONTENT_LENGTH,  (String) prop.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE));
                }
                response.setStatus(httpStatus);                    
                FileUtils.copy(proxyout, response.getOutputStream());
            }
        }
    }

    private String readLine(final InputStream in) throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != '\r' && b != -1) {
            buf.write(b);
        }
        if (b == -1) {
            return null;
        }
        b = in.read(); // read \n
        if (b == -1) {
            return null;
        }
        return buf.toString("UTF-8");
    }


    /**
     * get destination url (from query parameter &url=http://....)
     * override to prevent calculating destination url from request
     * 
     * @param request 
     * @param uri not used
     * @return destination url from query parameter &url=_destinationurl_
     * @throws MalformedURLException 
     */
    @Override
    protected HttpURI proxyHttpURI(HttpServletRequest request, String uri) throws MalformedURLException {
        String strARGS = request.getQueryString();
        if (strARGS.startsWith("action=")) {
            int detectnextargument = strARGS.indexOf("&");
            strARGS = strARGS.substring(detectnextargument + 1);
        }
        if (strARGS.startsWith("url=")) {
            final String strUrl = strARGS.substring(4); // strip url=

            try {
                URL newurl = new URL(strUrl);
                int port = newurl.getPort();
                if (port < 1) {
                    port = newurl.getDefaultPort();
                }
                return proxyHttpURI(newurl.getProtocol(), newurl.getHost(), port, newurl.getPath());
            } catch (final MalformedURLException e) {
                ConcurrentLog.fine("PROXY", "url parameter missing");
            }
        }
        return null;
    }

    @Override
    public String getServletInfo() {
        return "YaCy Proxy Servlet";
    }

}
