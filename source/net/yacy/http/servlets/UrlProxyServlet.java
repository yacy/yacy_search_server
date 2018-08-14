package net.yacy.http.servlets;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.regex.PatternSyntaxException;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Rewrite of the url-proxy servlet (YaCyProxyServlet "/proxy.html?url=xyz")
 * using different rewrite of url methode (using JSoup instead of regex for more flexibility)
 * (problem with regex was to also modify http header tags, causing problems with some relative link urls
 * and on included <base> header tag)
 * <p>
 * Design goal of this urlproxy
 * - option to handle links/urls the owner/user clicked on
 * - index visited pages on the fly (without to configure a permanent "transparent" proxy
 * <p>
 * For the goal and as distinguish from the "transparent" proxy we don't want (and need) to route all content
 * through the proxy (e.g. we are not interested in transporting css etc. but concentrate on searcheable content.
 * <p>
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
 * <p>
 * The rewrite of links can't be perfect, as all kinds of scripting etc. can be involved,
 * with jsoup only the <a href /> attributes of the body are modified. What will help to display
 * the page correct but will also results that e.g. with forms and javascript menues links will not
 * point to the original site (instead to the proxy url)
 * <p>
 * TODO: instead of using JSoup on top the (2 time parsing - for indexing & content rewrite) check option to joined parsing steps
 * <p>
 * Hint: a browser favorite of
 * javascript: window.location.href = ('http://localhost:9090/proxy.html?url=' + location.href);
 * will start the urlproxy with the current browser address.
 * <p>
 * This class is linked to YaCy within jetty using the defaults/web.xml configuration
 */
public class UrlProxyServlet extends HttpServlet implements Servlet {
    private static final long serialVersionUID = 4900000000000001121L;
    private String _stopProxyText = null;
    final private static Document.OutputSettings htmlOutput =
            new Document.OutputSettings().prettyPrint(false);

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        String tmps = config.getInitParameter("stopProxyText");
        if (tmps != null) {
            _stopProxyText = tmps;
        }

    }
    /* ------------------------------------------------------------ */

    @Override
    public void service(ServletRequest req, ServletResponse res) throws IOException {

        final HttpServletRequest request = (HttpServletRequest) req;
        final HttpServletResponse response = (HttpServletResponse) res;

        // 1 - check usser access rights
        if (!Switchboard.getSwitchboard().getConfigBool("proxyURL", false)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "proxy use not allowed. URL proxy globally switched off (see: System Administration -> Advanced Settings -> URL proxy)");
            return;
        }

        final String remoteHost = req.getRemoteAddr();
        if (!Domains.isThisHostIP(remoteHost) && !proxyippatternmatch(remoteHost)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "proxy use not granted for IP " + remoteHost + " (see: System Administration -> Advanced Settings -> URL Proxy Access Settings -> Restrict URL proxy use filter)");
            return;
        }

        if ("CONNECT".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        // 2 -  get target url
        final String strUrl = request.getParameter("url");
        if (strUrl == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "url parameter missing");
            return;
        }

        DigestURL proxyurl;
        try {
            proxyurl = new DigestURL(strUrl);
        } catch (final MalformedURLException e) {
            proxyurl = new DigestURL(URLDecoder.decode(strUrl, StandardCharsets.UTF_8.name()));
        }

        String hostwithport = proxyurl.getHost();
        if (proxyurl.getPort() != -1) hostwithport += ":" + proxyurl.getPort();

        // 4 - get target url
        RequestHeader reqHeader = ProxyHandler.convertHeaderFromJetty(request);
        reqHeader.remove(RequestHeader.KEEP_ALIVE);
        reqHeader.remove(HeaderFramework.CONTENT_LENGTH);

        final HashMap<String, Object> prop = new HashMap<>(4);
        prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_1_1);
        prop.put(HeaderFramework.CONNECTION_PROP_DIGESTURL, proxyurl);
        prop.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, Domains.LOCALHOST);
        prop.put(HeaderFramework.CONNECTION_PROP_CLIENT_HTTPSERVLETREQUEST, request);

        // 4 & 5 get & index target url
        final ByteArrayOutputStream outFromProxy = new ByteArrayOutputStream();
        HTTPDProxyHandler.doGet(prop, reqHeader, outFromProxy, ClientIdentification.yacyProxyAgent);

        // reparse header to extract content-length and mimetype
        final ResponseHeader proxyResponseHeader = new ResponseHeader(200); //
        InputStream inFromProxy = new ByteArrayInputStream(outFromProxy.toByteArray());
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        String line;
        while ((line = readLine(inFromProxy, lineBuf)) != null && !line.isEmpty()) {
            int p;
            if ((p = line.indexOf(':')) >= 0) {
                proxyResponseHeader.put(
                        line.substring(0, p).trim(),
                        line.substring(p + 1).trim()
                );
            }
        }

        if (line == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Proxy Header missing");
            return;
        }

        String location = proxyResponseHeader.get(HeaderFramework.LOCATION);
        if (location != null) {
            // rewrite location header
            if (location.startsWith("http")) {
                location = request.getServletPath() + "?url=" + location;
            } else {
                location = request.getServletPath() + "?url=" + proxyurl.getProtocol() + "://" + hostwithport + '/' + location;
            }
            response.addHeader(HeaderFramework.LOCATION, location);
        }

        final int httpStatus = proxyResponseHeader.getStatusCode();
        final String mimeType = proxyResponseHeader.getContentType();
        response.setStatus(httpStatus);
        response.setContentType(mimeType);

        // add some proxy-headers to response header
        copyHeader(response, proxyResponseHeader, HeaderFramework.SERVER);
        copyHeader(response, proxyResponseHeader, HeaderFramework.DATE);
        copyHeader(response, proxyResponseHeader, HeaderFramework.LAST_MODIFIED);
        copyHeader(response, proxyResponseHeader, HeaderFramework.EXPIRES);

        if ((httpStatus < HttpServletResponse.SC_BAD_REQUEST) && (mimeType != null) && mimeType.startsWith("text")) {
            if (proxyResponseHeader.containsKey(HeaderFramework.TRANSFER_ENCODING) && proxyResponseHeader.get(HeaderFramework.TRANSFER_ENCODING).contains("chunked")) {
                inFromProxy = new ChunkedInputStream(inFromProxy);
            }

            // 7 - modify target content
            final String servletstub = request.getScheme() + "://" + request.getServerName() + ':' + request.getServerPort() + request.getServletPath() + "?url=";
            Document doc;
            try {
                doc = Jsoup.parse(inFromProxy,
                        proxyResponseHeader.getCharacterEncoding(),
                        proxyurl.toString());
                doc.outputSettings(htmlOutput);
            } catch (IOException eio) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Proxy: parser error on " + proxyurl + "\n\n" + eio.getMessage());
                return;
            }
            byte[] sbb = render(request, response, proxyurl, hostwithport, servletstub, doc);

            response.setIntHeader(HeaderFramework.CONTENT_LENGTH, sbb.length);
            ServletOutputStream o = response.getOutputStream();
            o.write(sbb);
            o.flush();

        } else {
            if (httpStatus >= HttpServletResponse.SC_BAD_REQUEST) {
                if (HeaderFramework.http1_1.containsKey(Integer.toString(httpStatus))) {
                    //http1_1 includes http1_0 messages
                    final String httpStatusText = HeaderFramework.http1_1.get(Integer.toString(httpStatus));
                    response.sendError(httpStatus, "Site " + proxyurl + " returned with status " + httpStatus + " (" + httpStatusText + ')');
                } else {
                    response.sendError(httpStatus, "Site " + proxyurl + " returned with status " + httpStatus);
                }
                return;
            }
            if ((response.getHeader(HeaderFramework.CONTENT_LENGTH) == null) && prop.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE)) {
                response.setHeader(HeaderFramework.CONTENT_LENGTH, (String) prop.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE));
            }
            FileUtils.copy(inFromProxy, response.getOutputStream());
        }
    }

    private byte[] render(HttpServletRequest request, HttpServletResponse response, DigestURL proxyurl, String hostwithport, String servletstub, Document doc) {
        // rewrite all href with abs proxy url (must be abs because of <base> head tag

        Switchboard sb = Switchboard.getSwitchboard();

        boolean rewriteURLs_domainlist =
                sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist");

        Element bde = doc.body(); // start with body element to rewrite href links


        boolean logMalformedURL = ConcurrentLog.isFine("PROXY");

        Elements taglist = bde.getElementsByAttribute("href");
        for (Element e : taglist) {
            if (e.tagName().equals("a")) { // get <a> tag
                String absurl = e.absUrl("href"); // get href attribut as abs url
                if (absurl.startsWith("data:") || absurl.startsWith("#") || absurl.startsWith("mailto:") || absurl.startsWith("javascript:"))
                    continue;


                if (rewriteURLs_domainlist) try {
                    if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL(absurl)) != null)
                        continue;
                } catch (MalformedURLException ex) {
                    if (logMalformedURL)
                        ConcurrentLog.fine("PROXY", "ProxyServlet: malformed url for url-rewirte " + absurl);
                    continue;
                }
                e.attr("href", servletstub + absurl); // rewrite with abs proxy-url
            }
        }


        Element hd = doc.head();
        if (hd != null) {
            // add a base url if not exist (to make sure relative links point to original)
            Elements basetags = hd.getElementsByTag("base");
            if (basetags.isEmpty()) {
                hd.prependElement("base").attr("href",
                        proxyurl.getProtocol() + "://" + hostwithport +
                                proxyurl.getPath() /* + directory */);
            }
        }

        // 8 - add interaction elements (e.g. proxy exit button to switch back to original url)
        if (_stopProxyText != null) {

            String httpsAlertMsg = "";
            if (proxyurl.getProtocol().equalsIgnoreCase("https") &&
                    !request.getScheme().equalsIgnoreCase("https"))
                httpsAlertMsg = " &nbsp;  - <span style='color:red'>(Warning: secure target viewed over normal http)</span>";

            // use a template file, to allow full servlet functionallity header as iframe included
            String hdrtemplate = request.getScheme() + "://" + request.getServerName() + ':' + request.getServerPort() +
                    "/proxymsg/urlproxyheader.html?url=" + proxyurl;

            hdrtemplate = "<iframe src='" + hdrtemplate + "' width='98%' height='50px' >"
                    // alternative for no-frame supporting browser
                    + "<div width='100%' style='padding:5px; background:white; border-bottom: medium solid lightgrey;'>"
                    + "<div align='center' style='font-size:11px;'><a style='font-size:11px; color:black;' href='" + proxyurl + "'>" + _stopProxyText + "</a> "
                    + httpsAlertMsg + "</div></div>"
                    + "</iframe>";
            bde.prepend(hdrtemplate); // put as 1st element in body
        }


        // 9 - deliver to client
        byte[] sbb;
        if (doc.charset() == null) {
            sbb = UTF8.getBytes(doc.toString());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        } else { // keep orig charset
            sbb = doc.toString().getBytes(doc.charset());
            response.setCharacterEncoding(doc.charset().name());
        }
        return sbb;
    }

    private void copyHeader(HttpServletResponse response, ResponseHeader proxyResponseHeader, String server) {
        if (proxyResponseHeader.containsKey(server)) {
            response.setHeader(server, proxyResponseHeader.get(server));
        }
    }

    private static String readLine(final InputStream in, ByteArrayOutputStream buf) throws IOException {
        buf.reset();
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
    private static boolean proxyippatternmatch(final String key) {
        // the cfgippattern is a comma-separated list of patterns
        // each pattern may contain one wildcard-character '*' which matches anything
        final String[] cfgippattern = Switchboard.getSwitchboard().getConfigArray("proxyURL.access", "*");
        if (cfgippattern[0].equals("*")) {
            return true;
        }
        for (String pattern : cfgippattern) {
            try {
                if (key.matches(pattern)) {
                    return true;
                }
            } catch (PatternSyntaxException ex) {
                ConcurrentLog.warn("PROXY", "wrong ip pattern in url proxy config " + ex.getMessage());
            }
        }
        return false;
    }

    @Override
    public String getServletInfo() {
        return "YaCy Proxy Servlet";
    }

}
