package net.yacy.http.servlets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.server.http.ChunkedInputStream;
import net.yacy.server.http.HTTPDProxyHandler;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

/**
 * Servlet to implement proxy via url parameter "/proxy.html?url=xyz_urltoproxy"
 * this implementation uses the existing proxy functions from YaCy HTTPDProxyHandler
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
 * @deprecated since 1.81 use {@link UrlProxyServlet} instead.
 */
@Deprecated //use UrlProxyServlet instead
public class YaCyProxyServlet extends HttpServlet implements Servlet {
    private static final long serialVersionUID = 4900000000000001120L;
    
    @Override
    public void service (ServletRequest req, ServletResponse res) throws ServletException, IOException {

        final HttpServletRequest request = (HttpServletRequest) req;
        final HttpServletResponse response = (HttpServletResponse) res;

        if (!Switchboard.getSwitchboard().getConfigBool("proxyURL", false)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,"proxy use not allowed. URL proxy globally switched off (see: Content Semantic -> Augmented Browsing -> URL proxy)");
            return;
        }
        
        final String remoteHost = req.getRemoteHost();
        if (!Domains.isThisHostIP(remoteHost)) {
            if (!proxyippatternmatch(remoteHost)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "proxy use not granted for IP " + remoteHost + " (see: Content Semantic -> Augmented Browsing -> Restrict URL proxy use filter)");
                return;
            }
        }

        if ("CONNECT".equalsIgnoreCase(request.getMethod())) {
            return;
        }
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

        if (strARGS.startsWith("url=")) {
            final String strUrl = strARGS.substring(4); // strip "url="

            try {
                proxyurl = new URL(strUrl);
            } catch (final MalformedURLException e) {
                proxyurl = new URL(URLDecoder.decode(strUrl, StandardCharsets.UTF_8.name()));

            }
        }
        if (proxyurl == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,"url parameter missing");
            return;
        }

        String hostwithport = proxyurl.getHost();
        if (proxyurl.getPort() != -1) {
            hostwithport += ":" + proxyurl.getPort();
        }
        RequestHeader yacyRequestHeader = YaCyDefaultServlet.convertHeaderFromJetty(request);
        yacyRequestHeader.remove(RequestHeader.KEEP_ALIVE);
        yacyRequestHeader.remove(HeaderFramework.CONTENT_LENGTH);
        
        final HashMap<String, Object> prop = new HashMap<String, Object>();
        prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_1_1);
        prop.put(HeaderFramework.CONNECTION_PROP_PROTOCOL, proxyurl.getProtocol());
        prop.put(HeaderFramework.CONNECTION_PROP_HOST, hostwithport);
        prop.put(HeaderFramework.CONNECTION_PROP_PATH, proxyurl.getPath().replaceAll(" ", "%20"));
        prop.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, Domains.LOCALHOST);

        yacyRequestHeader.put(HeaderFramework.HOST, hostwithport );
        yacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, proxyurl.getPath());

        final ByteArrayOutputStream tmpproxyout = new ByteArrayOutputStream();
        HTTPDProxyHandler.doGet(prop, yacyRequestHeader, tmpproxyout, ClientIdentification.yacyProxyAgent);
        
        // reparse header to extract content-length and mimetype
        final ResponseHeader proxyResponseHeader = new ResponseHeader(200); //
        final InputStream proxyout = new ByteArrayInputStream(tmpproxyout.toByteArray());
        String line = readLine(proxyout);
        while (line != null && !line.equals("")) {
            int p;
            if ((p = line.indexOf(':')) >= 0) {
                // store a property
                proxyResponseHeader.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
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
            if (location.startsWith("http")) {
                location = request.getServletPath() + "?url=" + location;
            } else {
                location = request.getServletPath() + "?url=http://" + hostwithport + "/" + location;
            }
            response.addHeader(HeaderFramework.LOCATION, location);
        }

        final String mimeType = proxyResponseHeader.getContentType();
        response.setContentType(mimeType);
        response.setStatus(httpStatus);
        
        if ((mimeType != null) && (mimeType.startsWith("text"))) {
            final StringWriter buffer = new StringWriter();

            if (proxyResponseHeader.containsKey(HeaderFramework.TRANSFER_ENCODING) && proxyResponseHeader.get(HeaderFramework.TRANSFER_ENCODING).contains("chunked")) {
                FileUtils.copy(new ChunkedInputStream(proxyout), buffer, StandardCharsets.UTF_8);
            } else {
                FileUtils.copy(proxyout, buffer, StandardCharsets.UTF_8);
            }
            final String sbuffer = buffer.toString();

            final Pattern p = Pattern.compile("(href=\"|src=\")([^\"]+)|(href='|src=')([^']+)|(url\\(')([^']+)|(url\\(\")([^\"]+)|(url\\()([^\\)]+)");
            final Matcher m = p.matcher(sbuffer);
            final StringBuffer result = new StringBuffer(80);
            final Switchboard sb = Switchboard.getSwitchboard();
            final String servletstub = request.getServletPath()+"?url=";
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

                    String newurl = init + servletstub + url;
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

                    String newurl = init + servletstub + complete_url;
                    newurl = newurl.replaceAll("\\$", "\\\\\\$");
                    m.appendReplacement(result, newurl);

                } else if (url.startsWith("/")) {
                    // absolute path of form href="/absolute/path/to/linked/page"
                    String newurl = init + servletstub + "http://" + hostwithport + url;
                    newurl = newurl.replaceAll("\\$", "\\\\\\$");
                    m.appendReplacement(result, newurl);

                } else {
                    // relative path of form href="relative/path"
                    try {
                        MultiProtocolURL target = new MultiProtocolURL("http://" + hostwithport + directory + "/" + url);
                        String newurl = init + servletstub + target.toString();
                        newurl = newurl.replaceAll("\\$", "\\\\\\$");
                        m.appendReplacement(result, newurl);
                    } catch (final MalformedURLException e) {}
                }
            }
            m.appendTail(result);
           
            byte[] sbb = UTF8.getBytes(result.toString());

            // add some proxy-headers to response header
            response.setContentType(proxyResponseHeader.getContentType());
            if (proxyResponseHeader.containsKey(HeaderFramework.SERVER)) {
                response.addHeader(HeaderFramework.SERVER, proxyResponseHeader.get(HeaderFramework.SERVER));
            }
            if (proxyResponseHeader.containsKey(HeaderFramework.DATE)) {
                response.addHeader(HeaderFramework.DATE, proxyResponseHeader.get(HeaderFramework.DATE));
            }                    
            if (proxyResponseHeader.containsKey(HeaderFramework.LAST_MODIFIED)) {
                response.addHeader(HeaderFramework.LAST_MODIFIED, proxyResponseHeader.get(HeaderFramework.LAST_MODIFIED));
            }    
            if (proxyResponseHeader.containsKey(HeaderFramework.EXPIRES)) {
                response.addHeader(HeaderFramework.EXPIRES, proxyResponseHeader.get(HeaderFramework.EXPIRES));
            }   
            
            response.setIntHeader(HeaderFramework.CONTENT_LENGTH, sbb.length);
            response.getOutputStream().write(sbb);
                   
        } else {
            if ((response.getHeader(HeaderFramework.CONTENT_LENGTH) == null) && prop.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE)) {
                response.addHeader(HeaderFramework.CONTENT_LENGTH,  (String) prop.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE));
            }                  
            FileUtils.copy(proxyout, response.getOutputStream());
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
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * helper for proxy IP config pattern check
     */
    private boolean proxyippatternmatch(final String key) {
        // the cfgippattern is a comma-separated list of patterns
        // each pattern may contain one wildcard-character '*' which matches anything
        final String cfgippattern = Switchboard.getSwitchboard().getConfig("proxyURL.access", "*");
        if (cfgippattern.equals("*")) {
            return true;
        }
        final StringTokenizer st = new StringTokenizer(cfgippattern, ",");
        String pattern;
        while (st.hasMoreTokens()) {
            pattern = st.nextToken();
            if (key.matches(pattern)) {
                return true;
            }
        }
        return false;
    }    

    @Override
    public String getServletInfo() {
        return "YaCy Proxy Servlet";
    }

}
