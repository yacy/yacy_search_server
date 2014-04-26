package net.yacy.http.servlets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.StringTokenizer;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Rewrite of the url-proxy servlet (YaCyProxyServlet "/proxy.html?url=xyz")
 * using different rewrite of url methode (using JSoup instead of regex for more flexibility)
 * (problem with regex was to also modify http header tags, causing problems with some relative link urls
 * and on included <base> header tag)
 *
 * Design goal of this urlproxy
 * - option to handle links/urls the owner/user clicked on
 * - index visited pages on the fly (without to configure a permanent "transparent" proxy
 *
 * For the goal and as distinguish from the "transparent" proxy we don't want (and need) to route all content
 * through the proxy (e.g. we are not interested in transporting css etc. but concentrate on searcheable content.
 *
 * general functionallity to implement
 * 1 - check user access right
 * 2 - get target url from parameter
 * 3 - check target url accepteable
 * 4 - get target url
 * 5 - index target url
 * 6 - perform any custom event/treatment (for/on this user clicked url) - not implemented
 * 7 - modify loaded target content (like rewrite links to get proxied)
 * 8   - optionally add augmentation / interaction - not implemented
 * 9 - deliver to client broser
 *
 * The rewrite of links can't be perfect, as all kinds of scripting etc. can be involved,
 * with jsoup only the <a href /> attributes of the body are modified. What will help to display
 * the page correct but will also results that e.g. with forms and javascript menues links will not
 * point to the original site (instead to the proxy url)
 *
 * TODO: instead of using JSoup on top the (2 time parsing - for indexing & content rewrite) check option to joined parsing steps
 *
 * Hint: a browser favorite of
 *         javascript: window.location.href = ('http://localhost:9090/proxy.html?url=' + location.href);
 * will start the urlproxy with the current browser address.
 */
public class UrlProxyServlet extends ProxyServlet implements Servlet {

    private String _stopProxyText = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // must be lower case (header names are internally converted to lower)
        _DontProxyHeaders.add("host"); // to prevent Host header setting from original servletrequest (which is localhost)
        String tmps = config.getInitParameter("stopProxyText");
        if (tmps != null) {
            _stopProxyText = tmps;
        }

    }
    /* ------------------------------------------------------------ */

    @Override
    public void service (ServletRequest req, ServletResponse res) throws ServletException, IOException {

        final HttpServletRequest request = (HttpServletRequest) req;
        final HttpServletResponse response = (HttpServletResponse) res;

        // 1 - check usser access rights
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
            handleConnect(request, response);
        } else {

            final Continuation continuation = ContinuationSupport.getContinuation(request);

            if (!continuation.isInitial()) {
                response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT); // Need better test that isInitial
                return;
            }
            // 2 -  get target url
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
                    proxyurl = new URL(URLDecoder.decode(strUrl, UTF8.charset.name()));

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
            // 4 - get target url
            RequestHeader yacyRequestHeader = ProxyHandler.convertHeaderFromJetty(request);
            yacyRequestHeader.remove(RequestHeader.KEEP_ALIVE);
            yacyRequestHeader.remove(HeaderFramework.CONTENT_LENGTH);
            
            final HashMap<String, Object> prop = new HashMap<String, Object>();
            prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_1_1);
            prop.put(HeaderFramework.CONNECTION_PROP_HOST, hostwithport);
            prop.put(HeaderFramework.CONNECTION_PROP_PATH, proxyurl.getPath().replaceAll(" ", "%20"));
            if (proxyurl.getQuery() != null) prop.put(HeaderFramework.CONNECTION_PROP_ARGS, proxyurl.getQuery());
            prop.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, Domains.LOCALHOST);

            yacyRequestHeader.put(HeaderFramework.HOST, hostwithport );
            yacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, proxyurl.getPath());

            // 4 & 5 get & index target url
            final ByteArrayOutputStream tmpproxyout = new ByteArrayOutputStream();
            HTTPDProxyHandler.doGet(prop, yacyRequestHeader, tmpproxyout, ClientIdentification.yacyProxyAgent);

            // reparse header to extract content-length and mimetype
            final ResponseHeader proxyResponseHeader = new ResponseHeader(200); //
            InputStream proxyout = new ByteArrayInputStream(tmpproxyout.toByteArray());
            String line = readLine(proxyout);
            while (line != null && !line.equals("")) {
                int p;
                if ((p = line.indexOf(':')) >= 0) {
                    // store a property
                    proxyResponseHeader.put(line.substring(0, p).trim(), line.substring(p + 1).trim());
                }
                line = readLine(proxyout);
            }
            if (line == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Proxy Header missing");
                return;
            }
  
            if (proxyResponseHeader.containsKey(HeaderFramework.LOCATION)) {
                // rewrite location header
                String location = proxyResponseHeader.get(HeaderFramework.LOCATION);
                if (location.startsWith("http")) {
                    location = request.getServletPath() + "?url=" + location;
                } else {
                    location = request.getServletPath() + "?url=http://" + hostwithport + "/" + location;
                }
                response.addHeader(HeaderFramework.LOCATION, location);
            } 

            final int httpStatus = proxyResponseHeader.getStatusCode();
            final String mimeType = proxyResponseHeader.getContentType();
            response.setStatus(httpStatus);
            response.setContentType(mimeType);
            
            if ((httpStatus == HttpServletResponse.SC_OK) && (mimeType != null) && mimeType.startsWith("text")) {
                if (proxyResponseHeader.containsKey(HeaderFramework.TRANSFER_ENCODING) && proxyResponseHeader.get(HeaderFramework.TRANSFER_ENCODING).contains("chunked")) {
                     proxyout = new ChunkedInputStream(proxyout);
                }

                // 7 - modify target content
                final String servletstub = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getServletPath() + "?url=";
                Document doc;
                try {            
                    doc = Jsoup.parse(proxyout, UTF8.charset.name(), proxyurl.toString());
                } catch (IOException eio) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Proxy: parser error on " + proxyurl.toString());
                    return;
                }

                Element bde = doc.body(); // start with body element to rewrite href links
                // rewrite all href with abs proxy url (must be abs because of <base> head tag
                Elements taglist = bde.getElementsByAttribute("href");
                final Switchboard sb = Switchboard.getSwitchboard();
                for (Element e : taglist) {
                    if (e.tagName().equals("a")) { // get <a> tag
                        String absurl = e.absUrl("href"); // get href attribut as abs url
                        if (absurl.startsWith("data:") || absurl.startsWith("#") || absurl.startsWith("mailto:") || absurl.startsWith("javascript:")) {
                            continue;
                        } else {
                            if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist")) {
                                try {
                                    if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL(absurl)) != null) {
                                        continue;
                                    }
                                } catch (MalformedURLException ex) {
                                    ConcurrentLog.fine("PROXY", "ProxyServlet: malformed url for url-rewirte " + absurl);
                                    continue;
                                }
                            }
                            e.attr("href", servletstub + absurl); // rewrite with abs proxy-url
                        }
                    }
                }

                Element hd = doc.head();
                if (hd != null) {
                    // add a base url if not exist (to make sure relative links point to original)
                    Elements basetags = hd.getElementsByTag("base");
                    if (basetags.isEmpty()) {
                        Element newbasetag = hd.prependElement("base");
                        String basestr = proxyurl.getProtocol() + "://" + hostwithport + proxyurl.getPath(); //+directory;
                        newbasetag.attr("href", basestr);
                        }
                    }

                // 8 - add interaction elements (e.g. proxy exit button to switch back to original url)
                // TODO: use a template file for
                if (_stopProxyText != null) {
                    bde.prepend("<div width='100%' style='padding:5px; background:white; border-bottom: medium solid lightgrey;'>"
                        + "<div align='center' style='font-size:11px; color:darkgrey;'><a href='" + proxyurl + "'>" + _stopProxyText + "</a></div></div>");
                }

                // 9 - deliver to client
                byte[] sbb = UTF8.getBytes(doc.toString());

                // add some proxy-headers to response header
                if (proxyResponseHeader.containsKey(HeaderFramework.SERVER)) {
                    response.setHeader(HeaderFramework.SERVER, proxyResponseHeader.get(HeaderFramework.SERVER));
                }
                if (proxyResponseHeader.containsKey(HeaderFramework.DATE)) {
                    response.setHeader(HeaderFramework.DATE, proxyResponseHeader.get(HeaderFramework.DATE));
                }
                if (proxyResponseHeader.containsKey(HeaderFramework.LAST_MODIFIED)) {
                    response.setHeader(HeaderFramework.LAST_MODIFIED, proxyResponseHeader.get(HeaderFramework.LAST_MODIFIED));
                }
                if (proxyResponseHeader.containsKey(HeaderFramework.EXPIRES)) {
                    response.setHeader(HeaderFramework.EXPIRES, proxyResponseHeader.get(HeaderFramework.EXPIRES));
                }

                response.setIntHeader(HeaderFramework.CONTENT_LENGTH, sbb.length);
                response.getOutputStream().write(sbb);

            } else {
                if ((response.getHeader(HeaderFramework.CONTENT_LENGTH) == null) && prop.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE)) {
                    response.setHeader(HeaderFramework.CONTENT_LENGTH, (String) prop.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE));
                }
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
