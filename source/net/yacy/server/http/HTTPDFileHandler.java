// HTTPDFileHandler.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

/*
 Class documentation:
 this class provides a file servlet and CGI interface
 for the httpd server.
 Whenever this server is addressed to load a local file,
 this class searches for the file in the local path as
 configured in the setting property 'rootPath'
 The servlet loads the file and returns it to the client.
 Every file can also act as an template for the built-in
 CGI interface. There is no specific path for CGI functions.
 CGI functionality is triggered, if for the file to-be-served
 'template.html' also a file 'template.class' exists. Then,
 the class file is called with the GET/POST properties that
 are attached to the http call.
 Possible variable hand-over are:
 - form method GET
 - form method POST, enctype text/plain
 - form method POST, enctype multipart/form-data
 The class that creates the CGI respond must have at least one
 static method of the form
 public static java.util.Hashtable respond(java.util.HashMap, serverSwitch)
 In the HashMap, the GET/POST variables are handed over.
 The return value is a Property object that contains replacement
 key/value pairs for the patterns in the template file.
 The templates must have the form
 either '#['<name>']#' for single attributes, or
 '#{'<enumname>'}#' and '#{/'<enumname>'}#' for enumerations of
 values '#['<value>']#'.
 A single value in repetitions/enumerations in the template has
 the property key '_'<enumname><count>'_'<value>
 Please see also the example files 'test.html' and 'test.java'
 */

package net.yacy.server.http;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.data.UserDB;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ScraperInputStream;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverClassLoader;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.visualization.RasterPlotter;

public final class HTTPDFileHandler {

    // create a class loader
    private static final serverClassLoader provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
    private static serverSwitch switchboard = null;
    private static Switchboard sb = Switchboard.getSwitchboard();
    private final static byte[] UNRESOLVED_PATTERN = ASCII.getBytes("-UNRESOLVED_PATTERN-");


    private static File     htRootPath     = null;
    private static File     htDocsPath     = null;
    private static String[] defaultFiles   = null;
    private static File     htDefaultPath  = null;
    private static File     htLocalePath   = null;
    public  static String   indexForward   = "";

    protected static final class TemplateCacheEntry {
        Date lastModified;
        byte[] content;
    }
    private static final ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>> templateCache;
    private static final ConcurrentHashMap<File, SoftReference<Method>> templateMethodCache;

    public static final boolean useTemplateCache;

    //private Properties connectionProperties = null;
    // creating a logger
    private static final ConcurrentLog theLogger = new ConcurrentLog("FILEHANDLER");

    static {
        final serverSwitch theSwitchboard = Switchboard.getSwitchboard();
        useTemplateCache = theSwitchboard.getConfig("enableTemplateCache","true").equalsIgnoreCase("true");
        templateCache = (useTemplateCache)? new ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>>() : new ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>>(0);
        templateMethodCache = new ConcurrentHashMap<File, SoftReference<Method>>();

        if (switchboard == null) {
            switchboard = theSwitchboard;

            if (Classification.countMimes() == 0) {
                // load the mime table
                final String mimeTablePath = theSwitchboard.getConfig("mimeTable","");
                ConcurrentLog.config("HTTPDFiles", "Loading mime mapping file " + mimeTablePath);
                Classification.init(new File(theSwitchboard.getAppPath(), mimeTablePath));
            }

            // create default files array
            initDefaultPath();

            // create a htRootPath: system pages
            if (htRootPath == null) {
                    htRootPath = new File(theSwitchboard.getAppPath(), theSwitchboard.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT));
                    if (!(htRootPath.exists())) htRootPath.mkdir();
            }

            // create a htDocsPath: user defined pages
            if (htDocsPath == null) {
                htDocsPath = theSwitchboard.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
                if (!(htDocsPath.exists())) htDocsPath.mkdirs();
            }

            // create a repository path
            final File repository = new File(htDocsPath, "repository");
            if (!repository.exists()) repository.mkdirs();

            // create htLocaleDefault, htLocalePath
            if (htDefaultPath == null) htDefaultPath = theSwitchboard.getAppPath("htDefaultPath", "htroot");
            if (htLocalePath == null) htLocalePath = theSwitchboard.getDataPath("locale.translated_html", "DATA/LOCALE/htroot");
        }

    }

    public static final void initDefaultPath() {
        // create default files array
        defaultFiles = switchboard.getConfig(SwitchboardConstants.BROWSER_DEFAULT,"index.html").split(",");
        if (defaultFiles.length == 0) defaultFiles = new String[] {"index.html"};
        indexForward = switchboard.getConfig(SwitchboardConstants.INDEX_FORWARD, "");
        if (indexForward.startsWith("/")) indexForward = indexForward.substring(1);
    }

    /** Returns a path to the localized or default file according to the locale.language (from he switchboard)
     * @param path relative from htroot */
    public static File getLocalizedFile(final String path){
        return getLocalizedFile(path, switchboard.getConfig("locale.language","default"));
    }

    /** Returns a path to the localized or default file according to the parameter localeSelection
     * @param path relative from htroot
     * @param localeSelection language of localized file; locale.language from switchboard is used if localeSelection.equals("") */
    public static File getLocalizedFile(final String path, final String localeSelection){
        //if (htDefaultPath == null) htDefaultPath = switchboard.getConfigPath("htDefaultPath", "htroot");
        //if (htLocalePath == null) htLocalePath = switchboard.getConfigPath("locale.translated_html", "DATA/LOCALE/htroot");
        //if (htDocsPath == null) htDocsPath = switchboard.getConfigPath(plasmaSwitchboardConstants.HTDOCS_PATH, plasmaSwitchboardConstants.HTDOCS_PATH_DEFAULT);

        if (path.startsWith("/repository/"))
            return new File(switchboard.getConfig("repositoryPath", "DATA/HTDOCS/repository"), path.substring(11));
        if (!(localeSelection.equals("default"))) {
            final File localePath = new File(htLocalePath, localeSelection + '/' + path);
            if (localePath.exists()) return localePath;  // avoid "NoSuchFile" troubles if the "localeSelection" is misspelled
        }

        final File docsPath  = new File(htDocsPath, path);
        if (docsPath.exists()) return docsPath;
        return new File(htDefaultPath, path);
    }

    private static final ResponseHeader getDefaultHeaders(final String path) {
        final ResponseHeader headers = new ResponseHeader(200);
        String ext;
        int pos;
        if ((pos = path.lastIndexOf('.')) < 0) {
            ext = "";
        } else {
            ext = path.substring(pos + 1).toLowerCase();
        }
        headers.put(HeaderFramework.SERVER, "AnomicHTTPD (www.anomic.de)");
        headers.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(new Date()));
        if(!(Classification.isMediaExtension(ext))){
            headers.put(HeaderFramework.PRAGMA, "no-cache");
        }
        return headers;
    }

    public static void doGet(final HashMap<String, Object> conProp, final RequestHeader requestHeader, final OutputStream response) {
        doResponse(conProp, requestHeader, response, null);
    }

    public static void doHead(final HashMap<String, Object> conProp, final RequestHeader requestHeader, final OutputStream response) {
        doResponse(conProp, requestHeader, response, null);
    }

    public static void doPost(final HashMap<String, Object> conProp, final RequestHeader requestHeader, final OutputStream response, final InputStream body) {
        doResponse(conProp, requestHeader, response, body);
    }

    public static void doResponse(final HashMap<String, Object> conProp, final RequestHeader requestHeader, final OutputStream out, final InputStream body) {

        String path = null;
        try {
            // getting some connection properties
            final String method = (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD);
            path = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PATH);
            String argsString = (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS); // is null if no args were given
            final String httpVersion = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER);
            String clientIP = (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP); if (clientIP == null) clientIP = "unknown-host";

            // check hack attacks in path
            if (path.indexOf("..",0) >= 0) {
                HTTPDemon.sendRespondError(conProp,out,4,403,null,"Access not allowed",null);
                return;
            }

            path = UTF8.decodeURL(path);

            // check against hack attacks in path
            if (path.indexOf("..",0) >= 0) {
                HTTPDemon.sendRespondError(conProp,out,4,403,null,"Access not allowed",null);
                return;
            }

            // allow proper access to current peer via virtual directory
            if (path.startsWith("/currentyacypeer/")) {
            	path = path.substring(16);
            }

            // cache settings
            boolean nocache = path.contains("?") || body != null;

            // a bad patch to map the /xml/ path to /api/
            if (path.startsWith("/xml/")) {
                path = "/api/" + path.substring(5);
            }
            // another bad patch to map the /util/ path to /api/util/ to support old yacybars
            if (path.startsWith("/util/")) {
                path = "/api/util/" + path.substring(6);
            }
            // one more for bookmarks
            if (path.startsWith("/bookmarks/")) {
                path = "/api/bookmarks/" + path.substring(11);
            }
            // another patch for the gsa interface
            if (path.startsWith("/gsa/search") && !path.startsWith("/gsa/searchresult")) {
                path = "/gsa/searchresult" + path.substring(11);
            }

            // these are the 5 cases where an access granted:
            // (the alternative is that we deliver a 401 to request authorization)

            // -1- the page is not protected; or
            final boolean protectedPage = path.indexOf("_p.",0) > 0;
            boolean accessGranted = !protectedPage;

            // -2- a password is not configured; or
            final String adminAccountBase64MD5 = switchboard.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
            if (!accessGranted) {
                accessGranted = adminAccountBase64MD5.isEmpty();
            }

            // -3- access from localhost is granted and access comes from localhost; or
            final String refererHost = requestHeader.refererHost();
            if (!accessGranted) {
                final boolean adminAccountForLocalhost = sb.getConfigBool("adminAccountForLocalhost", false);
                final boolean accessFromLocalhost = Domains.isLocalhost(clientIP) && (refererHost == null || refererHost.isEmpty() || Domains.isLocalhost(refererHost));
                accessGranted = adminAccountForLocalhost && accessFromLocalhost;
            }

            // -4- a password is configured and access comes from localhost
            //     and the realm-value of a http-authentify String is equal to the stored base64MD5; or
            String realmProp = requestHeader.get(RequestHeader.AUTHORIZATION);
            if (realmProp != null && realmProp.isEmpty()) realmProp = null;
            final String realmValue = realmProp == null ? null : realmProp.substring(6);
            if (!accessGranted) {
                final boolean accessFromLocalhost = Domains.isLocalhost(clientIP) && (refererHost == null || refererHost.isEmpty() || Domains.isLocalhost(refererHost));
                accessGranted = accessFromLocalhost && realmValue != null && realmProp.length() > 6 && (adminAccountBase64MD5.equals(realmValue));
                //if (!accessGranted) Log.logInfo("HTTPDFileHandler", "access blocked, clientIP=" + clientIP + ", path=" + path);
            }

            // -5- a password is configured and access comes with matching http-authentify
            if (!accessGranted) {
                accessGranted = realmProp != null && realmValue != null && (sb.userDB.hasAdminRight(realmProp, requestHeader.getHeaderCookies()) || adminAccountBase64MD5.equals(Digest.encodeMD5Hex(realmValue)));
            }

            // in case that we are still not granted we ask for a password
            if (!accessGranted) {
                ConcurrentLog.info("HTTPD", "Wrong log-in for path '" + path + "' from host '" + clientIP + "'");
                final Integer attempts = serverCore.bfHost.get(clientIP);
                if (attempts == null)
                    serverCore.bfHost.put(clientIP, Integer.valueOf(1));
                else
                    serverCore.bfHost.put(clientIP, Integer.valueOf(attempts.intValue() + 1));

                final ResponseHeader responseHeader = getDefaultHeaders(path);
                responseHeader.put(RequestHeader.WWW_AUTHENTICATE, "Basic realm=\"" + serverObjects.ADMIN_AUTHENTICATE_MSG + "\"");
                final servletProperties tp=new servletProperties();
                tp.put("returnto", path);
                HTTPDemon.sendRespondError(conProp, out, 5, 401, "Wrong Authentication", "", new File("proxymsg/authfail.inc"), tp, null, responseHeader);
                return;
            }

            // Authentication successful. remove brute-force flag
            serverCore.bfHost.remove(conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP));

            // parse arguments
            serverObjects args = new serverObjects();
            int argc = 0;
            if (argsString == null) {
                // no args here, maybe a POST with multipart extension
                final int length = requestHeader.getContentLength();
                //System.out.println("HEADER: " + requestHeader.toString()); // DEBUG

                /* don't parse body in case of a POST CGI call since it has to be
                 * handed over to the CGI script unaltered and parsed by the script
                 */
                if (method.equals(HeaderFramework.METHOD_POST) &&
                        !(switchboard.getConfigBool("cgi.allow", false) &&
                        matchesSuffix(path, switchboard.getConfig("cgi.suffixes", null)))
                        ) {

                    // if its a POST, it can be either multipart or as args in the body
                    if ((requestHeader.containsKey(HeaderFramework.CONTENT_TYPE)) &&
                            (requestHeader.get(HeaderFramework.CONTENT_TYPE).toLowerCase().startsWith("multipart"))) {
                        // parse multipart
                        final Map<String, byte[]> files = HTTPDemon.parseMultipart(requestHeader, args, body);
                        // integrate these files into the args
                        if (files != null) {
                            final Iterator<Map.Entry<String, byte[]>> fit = files.entrySet().iterator();
                            Map.Entry<String, byte[]> entry;
                            while (fit.hasNext()) {
                                entry = fit.next();
                                args.add(entry.getKey() + "$file", entry.getValue());
                            }
                        }
                        argc = Integer.parseInt(requestHeader.get("ARGC"));
                    } else {
                        // parse args in body
                        argc = HTTPDemon.parseArgs(args, body, length);
                    }
                } else {
                    // no args
                    argsString = null;
                    args = null;
                    argc = 0;
                }
            } else {
                // simple args in URL (stuff after the "?")
                argc = HTTPDemon.parseArgs(args, argsString);
            }

            // check for cross site scripting - attacks in request arguments
            if (args != null && argc > 0) {
                // check all values for occurrences of script values
                final Iterator<String> e = args.values().iterator(); // enumeration of values
                String val;
                while (e.hasNext()) {
                    val = e.next();
                    if ((val != null) && (val.indexOf("<script",0) >= 0) && !path.equals("/Crawler_p.html")) {
                        // deny request
                        HTTPDemon.sendRespondError(conProp,out,4,403,null,"bad post values",null);
                        return;
                    }
                }
            }

            if (args != null) nocache = true;

            // we are finished with parsing
            // the result of value hand-over is in args and argc
            if (path.isEmpty()) {
                HTTPDemon.sendRespondError(conProp,out,4,400,null,"Bad Request",null);
                out.flush();
                return;
            }
            File targetClass = null;

            // locate the file
            if (!path.isEmpty() && path.charAt(0) != '/' && path.charAt(0) != '\\') {
                path = "/" + path; // attach leading slash
            }
            if (path.endsWith("index.html")) {
                path = path.substring(0, path.length() - 10);
            }

            // a different language can be desired (by i.e. ConfigBasic.html) than the one stored in the locale.language
            String localeSelection = switchboard.getConfig("locale.language","default");
            if (args != null && (args.containsKey("language"))) {
                // TODO 9.11.06 Bost: a class with information about available languages is needed.
                // the indexOf(".") is just a workaround because there from ConfigLanguage.html commes "de.lng" and
                // from ConfigBasic.html comes just "de" in the "language" parameter
                localeSelection = args.get("language", localeSelection);
                if (localeSelection.indexOf('.') != -1)
                    localeSelection = localeSelection.substring(0, localeSelection.indexOf('.'));
            }

            File targetFile = getLocalizedFile(path, localeSelection);
            String targetExt = (String) conProp.get("EXT"); if (targetExt == null) targetExt = "";
            targetClass = rewriteClassFile(new File(htDefaultPath, path));
            if (path.endsWith("/") || path.endsWith("\\")) {
                String testpath;
                // look for indexForward setting
                if (indexForward.length() > 0 && (targetFile = getOverlayedFile(path + indexForward)).exists()) {
                    testpath = path + indexForward;
                    targetClass = getOverlayedClass(testpath);
                    path = testpath;
                } else {
                    // attach default file name(s)
                    for (final String defaultFile : defaultFiles) {
                        testpath = path + defaultFile;
                        targetFile = getOverlayedFile(testpath);
                        targetClass = getOverlayedClass(testpath);
                        if (targetFile.exists()) {
                            path = testpath;
                            break;
                        }
                    }
                }
                targetFile = getLocalizedFile(path, localeSelection);

                //no defaultfile, send a dirlisting
                if (targetFile == null || !targetFile.exists() || (targetFile.exists() && targetFile.isDirectory())) {
                    final StringBuilder aBuffer = new StringBuilder();
                    aBuffer.append("<html>\n<head>\n</head>\n<body>\n<h1>Index of " + CharacterCoding.unicode2html(path, true) + "</h1>\n  <ul>\n");
                    String[] list = targetFile.list();
                    if (list == null) list = new String[0]; // should not occur!
                    File f;
                    String size;
                    long sz;
                    String headline, author, publisher;
                    List<String> descriptions;
                    int images, links;
                    ContentScraper scraper;
                    for (final String element : list) {
                        f = new File(targetFile, element);
                        if (f.isDirectory()) {
                            aBuffer.append("    <li><a href=\"" + path + element + "/\">" + element + "/</a><br/></li>\n");
                        } else {
                            if (element.endsWith("html") || (element.endsWith("htm"))) {
                                scraper = ContentScraper.parseResource(f, 10000);
                                Collection<String> t = scraper.getTitles();
                                headline = t.size() > 0 ? t.iterator().next() : "";
                                author = scraper.getAuthor();
                                publisher = scraper.getPublisher();
                                descriptions = scraper.getDescriptions();
                                images = scraper.getImages().size();
                                links = scraper.getAnchors().size();
                            } else {
                                headline = null;
                                author = null;
                                publisher = null;
                                descriptions = null;
                                images = 0;
                                links = 0;
                            }
                            sz = f.length();
                            if (sz < 1024) {
                                size = sz + " bytes";
                            } else if (sz < 1024 * 1024) {
                                size = (sz / 1024) + " KB";
                            } else {
                                size = (sz / 1024 / 1024) + " MB";
                            }
                            aBuffer.append("    <li>");
                            if (headline != null && headline.length() > 0) aBuffer.append("<a href=\"" + element + "\"><b>" + headline + "</b></a><br/>");
                            aBuffer.append("<a href=\"" + path + element + "\">" + element + "</a><br/>");
                            if (author != null && author.length() > 0) aBuffer.append("Author: " + author + "<br/>");
                            if (publisher != null && publisher.length() > 0) aBuffer.append("Publisher: " + publisher + "<br/>");
                            if (descriptions != null && descriptions.size() > 0) {
                                for (String d: descriptions) {
                                    aBuffer.append("Description: " + d + "<br/>");
                                }
                            }
                            aBuffer.append(GenericFormatter.SHORT_DAY_FORMATTER.format(new Date(f.lastModified())) + ", " + size + ((images > 0) ? ", " + images + " images" : "") + ((links > 0) ? ", " + links + " links" : "") + "<br/></li>\n");
                        }
                    }
                    aBuffer.append("  </ul>\n</body>\n</html>\n");

                    // write the list to the client
                    HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, "text/html; charset=UTF-8", aBuffer.length(), new Date(targetFile.lastModified()), null, new ResponseHeader(200), null, null, true);
                    if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                        out.write(UTF8.getBytes(aBuffer.toString()));
                    }
                    return;
                }
            } else {
                    //XXX: you cannot share a .png/.gif file with a name like a class in htroot.
                    if ( !(targetFile.exists()) &&
                            !((path.endsWith("png")||path.endsWith("gif") || path.indexOf('.') < 0 ||
                            matchesSuffix(path, switchboard.getConfig("cgi.suffixes", null)) ||
                            path.endsWith(".stream")) &&
                            targetClass!=null ) ){
                        targetFile = new File(htDocsPath, path);
                        targetClass = rewriteClassFile(new File(htDocsPath, path));
                    }
            }

            // implement proxy via url (not in servlet, because we need binary access on ouputStream)
            if (path.equals("/proxy.html")) {
            	final List<Pattern> urlProxyAccess = Domains.makePatterns(sb.getConfig("proxyURL.access", Domains.LOCALHOST));
                final UserDB.Entry user = sb.userDB.getUser(requestHeader);
                final boolean user_may_see_proxyurl = Domains.matchesList(clientIP, urlProxyAccess) || (user!=null && user.hasRight(UserDB.AccessRight.PROXY_RIGHT));
            	if (sb.getConfigBool("proxyURL", false) && user_may_see_proxyurl) {
            		doURLProxy(conProp, requestHeader, out);
            		return;
            	}
                HTTPDemon.sendRespondError(conProp,out,3,403,"Access denied",null,null);
            }

            // track all files that had been accessed so far
            if (targetFile != null && targetFile.exists()) {
                if (args != null && !args.isEmpty()) sb.setConfig("server.servlets.submitted", appendPath(sb.getConfig("server.servlets.submitted", ""), path));
            }

            //File targetClass = rewriteClassFile(targetFile);
            //We need tp here
            servletProperties templatePatterns = null;
            Date targetDate;

            if ((targetClass != null) && ((path.endsWith("png") || path.endsWith("gif")))) {
                // call an image-servlet to produce an on-the-fly - generated image
                Object img = null;
                requestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP));
                requestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, path);
                requestHeader.put(HeaderFramework.CONNECTION_PROP_EXT, "png");
                // in case that there are no args given, args = null or empty hashmap
                img = invokeServlet(targetClass, requestHeader, args, null);
                if (img == null) {
                    // error with image generation; send file-not-found
                    HTTPDemon.sendRespondError(conProp, out, 3, 404, "File not Found", null, null);
                } else {
                    if (img instanceof RasterPlotter) {
                        final RasterPlotter yp = (RasterPlotter) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        final String mimeType = Classification.ext2mime(targetExt, "text/html");
                        // write the array to the client
                        if ("png".equals(targetExt)) {
                            final byte[] result =  ((RasterPlotter) img).pngEncode(1);
                            HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, mimeType, result.length, targetDate, null, null, null, null, nocache);
                            if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                                out.write(result);
                            }
                        } else {
                            final ByteBuffer result = RasterPlotter.exportImage(yp.getImage(), targetExt);
                            HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, mimeType, result.length(), targetDate, null, null, null, null, nocache);
                            if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                                result.writeTo(out);
                            }
                            result.close();
                        }
                    }
                    if (img instanceof EncodedImage) {
                        final EncodedImage yp = (EncodedImage) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        final String mimeType = Classification.ext2mime(targetExt, "text/html");
                        final ByteBuffer result = yp.getImage();

                        // write the array to the client
                        HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, mimeType, result.length(), targetDate, null, null, null, null, nocache);
                        if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                            result.writeTo(out);
                        }
                    }
                    /*
                    if (img instanceof BufferedImage) {
                        final BufferedImage i = (BufferedImage) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        final String mimeType = MimeTable.ext2mime(targetExt, "text/html");

                        // generate an byte array from the generated image
                        int width = i.getWidth(); if (width < 0) width = 96; // bad hack
                        int height = i.getHeight(); if (height < 0) height = 96; // bad hack
                        final ByteBuffer result = RasterPlotter.exportImage(i, targetExt);

                        // write the array to the client
                        HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, mimeType, result.length(), targetDate, null, null, null, null, nocache);
                        if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                            result.writeTo(out);
                        }
                    }
                    */
                    if (img instanceof Image) {
                        final Image i = (Image) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        final String mimeType = Classification.ext2mime(targetExt, "text/html");

                        // generate an byte array from the generated image
                        int width = i.getWidth(null); if (width < 0) width = 96; // bad hack
                        int height = i.getHeight(null); if (height < 0) height = 96; // bad hack
                        final BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        bi.createGraphics().drawImage(i, 0, 0, width, height, null);
                        final ByteBuffer result = RasterPlotter.exportImage(bi, targetExt);

                        // write the array to the client
                        HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, mimeType, result.length(), targetDate, null, null, null, null, nocache);
                        if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                            result.writeTo(out);
                        }
                    }
                }
            // old-school CGI execution
            } else if ((switchboard.getConfigBool("cgi.allow", false) // check if CGI execution is allowed in config
                    && matchesSuffix(path, switchboard.getConfig("cgi.suffixes", null)) // "right" file extension?
                    && path.substring(0, path.indexOf(targetFile.getName())).toUpperCase().contains("/CGI-BIN/") // file in right directory?
                    && targetFile.exists())
                    ) {

                if (!targetFile.canExecute()) {
                    HTTPDemon.sendRespondError(
                            conProp,
                            out,
                            -1,
                            403,
                            null,
                            HeaderFramework.http1_1.get(
                                    Integer.toString(403)),
                            null);
                    ConcurrentLog.warn(
                            "HTTPD",
                            "CGI script " + targetFile.getPath()
                            + " could not be executed due to "
                            + "insufficient access rights.");
                } else {
                    String mimeType = "text/html";
                    int statusCode = 200;

                    final ProcessBuilder pb =
                            new ProcessBuilder(assembleCommandFromShebang(targetFile));
                    pb.directory(targetFile.getParentFile());

                    final String fileSeparator =
                            System.getProperty("file.separator", "/");

                    // set environment variables
                    final Map<String, String> env = pb.environment();
                    env.put(
                            "SERVER_SOFTWARE",
                            getDefaultHeaders(path).get(HeaderFramework.SERVER));
                    env.put("SERVER_NAME", sb.peers.mySeed().getName());
                    env.put("GATEWAY_INTERFACE", "CGI/1.1");
                    if (httpVersion != null) {
                        env.put("SERVER_PROTOCOL", httpVersion);
                    }
                    env.put("SERVER_PORT", switchboard.getConfig("port", "8090"));
                    env.put("REQUEST_METHOD", method);
    //                env.put("PATH_INFO", "");         // TODO: implement
    //                env.put("PATH_TRANSLATED", "");   // TODO: implement
                    env.put("SCRIPT_NAME", path);
                    if (argsString != null) {
                        env.put("QUERY_STRING", argsString);
                    }
                    env.put("REMOTE_ADDR", clientIP);
    //                env.put("AUTH_TYPE", "");         // TODO: implement
    //                env.put("REMOTE_USER", "");       // TODO: implement
    //                env.put("REMOTE_IDENT", "");      // I don't think we need this
                    env.put(
                            "DOCUMENT_ROOT",
                            switchboard.getAppPath().getAbsolutePath()
                            + fileSeparator + switchboard.getConfig("htDocsPath", "DATA/HTDOCS"));
                    if (requestHeader.getContentType() != null) {
                        env.put("CONTENT_TYPE", requestHeader.getContentType());
                    }
                    if (method.equalsIgnoreCase(HeaderFramework.METHOD_POST)
                            && body != null) {
                        env.put(
                                "CONTENT_LENGTH",
                                Integer.toString(requestHeader.getContentLength()));
                    }

                    /* add values from request header to environment
                     * (see: http://hoohoo.ncsa.uiuc.edu/cgi/env.html#headers) */
                    for (final Map.Entry<String, String> requestHeaderEntry
                            : requestHeader.entrySet()) {
                        env.put("HTTP_"
                            + requestHeaderEntry.getKey().toUpperCase().replace("-", "_"),
                            requestHeaderEntry.getValue());
                    }

                    int exitValue = 0;
                    String cgiBody = null;
                    final StringBuilder error = new StringBuilder(256);

                    try {
                        // start execution of script
                        final Process p = pb.start();

                        final OutputStream os =
                                new BufferedOutputStream(p.getOutputStream());

                        if (method.equalsIgnoreCase(
                                HeaderFramework.METHOD_POST) && body != null) {
                            final byte[] buffer = new byte[1024];
                            int len = requestHeader.getContentLength();
                            while (len > 0) {
                                body.read(buffer);
                                len = len - buffer.length;
                                os.write(buffer);
                            }
                        }

                        os.close();

                        try {
                            p.waitFor();
                        } catch (final InterruptedException ex) {

                        }

                        exitValue = p.exitValue();

                        final InputStream is =
                                new BufferedInputStream(p.getInputStream());

                        final InputStream es =
                                new BufferedInputStream(p.getErrorStream());

                        final StringBuilder processOutput =
                                new StringBuilder(1024);

                        while (is.available() > 0) {
                            processOutput.append((char) is.read());
                        }

                        while (es.available() > 0) {
                            error.append((char) es.read());
                        }

                        int indexOfDelimiter = processOutput.indexOf("\n\n", 0);
                        final String[] cgiHeader;
                        if (indexOfDelimiter > -1) {
                            cgiHeader = CommonPattern.NEWLINE.split(processOutput.substring(0, indexOfDelimiter));
                        } else {
                            cgiHeader = new String[0];
                        }
                        cgiBody = processOutput.substring(indexOfDelimiter + 1);

                        String key;
                        String value;
                        for (final String element : cgiHeader) {
                            indexOfDelimiter = element.indexOf(':');
                            key = element.substring(0, indexOfDelimiter).trim();
                            value = element.substring(indexOfDelimiter + 1).trim();
                            conProp.put(key, value);
                            if ("Cache-Control".equals(key)
                                    && "no-cache".equals(value)) {
                                nocache = true;
                            } else if ("Content-type".equals(key)) {
                                mimeType = value;
                            } else if ("Status".equals(key)) {
                                if (key.length() > 2) {
                                    try {
                                        statusCode =
                                                Integer.parseInt(
                                                        value.substring(0, 3));
                                    } catch (final NumberFormatException ex) {
                                        ConcurrentLog.warn(
                                                "HTTPD",
                                                "CGI script " + targetFile.getPath()
                                                + " returned illegal status code \""
                                                + value + "\".");
                                    }
                                }
                            }
                        }
                    } catch (final IOException ex) {
                        exitValue = -1;
                    }

                    /* did the script return an exit value != 0
                     * and still there is supposed to be
                     * everything right with the HTTP status?
                     * -> change status to 500 since 200 would
                     * be a lie
                     */
                    if (exitValue != 0 && statusCode == 200) {
                        statusCode = 500;
                    }

                    targetDate = new Date(System.currentTimeMillis());

                    if (cgiBody != null && !cgiBody.isEmpty()) {
                        HTTPDemon.sendRespondHeader(
                                conProp,
                                out,
                                httpVersion,
                                statusCode,
                                null,
                                mimeType,
                                cgiBody.length(),
                                targetDate,
                                null,
                                null,
                                null,
                                null,
                                nocache);
                        out.write(UTF8.getBytes(cgiBody));
                    } else {
                        HTTPDemon.sendRespondError(
                                conProp,
                                out,
                                exitValue,
                                statusCode,
                                null,
                                HeaderFramework.http1_1.get(
                                        Integer.toString(statusCode)),
                                null);
                        ConcurrentLog.warn(
                                "HTTPD",
                                "CGI script " + targetFile.getPath()
                                + " returned exit value " + exitValue
                                + ", body empty: "
                                + (cgiBody == null || cgiBody.isEmpty()));
                        if (error.length() > 0) {
                            ConcurrentLog.warn("HTTPD", "Reported error: " + error);
                        }
                    }
                }
            } else if (targetClass != null && (path.endsWith(".stream") || path.substring(path.length() - 8).indexOf('.') < 0)) {
                // call rewrite-class
                requestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP));
                requestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, path);
                requestHeader.put(HeaderFramework.CONNECTION_PROP_EXT, path.endsWith(".stream") ? "stream" : "");
                //requestHeader.put(httpHeader.CONNECTION_PROP_INPUTSTREAM, body);
                //requestHeader.put(httpHeader.CONNECTION_PROP_OUTPUTSTREAM, out);

                // prepare response header
                ResponseHeader header = new ResponseHeader(200);
                header.put(HeaderFramework.CONTENT_TYPE, getMimeFromServlet(targetClass, requestHeader, args, "text/xml"));
                header.put(HeaderFramework.CORS_ALLOW_ORIGIN, "*"); // allow Cross-Origin Resource Sharing for all stream servlets
                conProp.remove(HeaderFramework.CONNECTION_PROP_PERSISTENT);
                final boolean zipContent = requestHeader.acceptGzip();
                if (zipContent) header.put(HeaderFramework.CONTENT_ENCODING, "gzip");

                // send response head
                HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, header);
                forceConnectionClose(conProp);

                // send response content
                OutputStream o = zipContent ? new GZIPOutputStream(out) : out;
                invokeServlet(targetClass, requestHeader, args, o);

                // immediately close stream as this terminates the http transmission
                if (o instanceof GZIPOutputStream) ((GZIPOutputStream) o).finish();
                o.flush();
                o.close();
                out.flush();
                out.close();
                return;
            } else if (targetFile.exists() && targetFile.isFile() && targetFile.canRead()) {
                // we have found a file that can be written to the client
                // if this file uses templates, then we use the template
                // re-write - method to create an result
                String mimeType = Classification.ext2mime(targetExt, "text/html");
                String ext = (String) conProp.get("EXT"); if (ext == null) ext = "";
                final boolean zipContent = requestHeader.acceptGzip() && HTTPDemon.shallTransportZipped("." + ext);
                if (path.endsWith("html") ||
                        path.endsWith("htm") ||
                        path.endsWith("xml") ||
                        path.endsWith("json") ||
                        path.endsWith("rdf") ||
                        path.endsWith("rss") ||
                        path.endsWith("csv") ||
                        path.endsWith("pac") ||
                        path.endsWith("src") ||
                        path.endsWith("vcf") ||
                        path.endsWith("kml") ||
                        path.endsWith("gpx") ||
                        path.endsWith("css") ||
                        path.endsWith("/") ||
                        path.equals("/robots.txt")) {

                    /*targetFile = getLocalizedFile(path);
                    if (!(targetFile.exists())) {
                        // try to find that file in the htDocsPath
                        File trialFile = new File(htDocsPath, path);
                        if (trialFile.exists()) targetFile = trialFile;
                    }*/


                    // call rewrite-class

                    if (targetClass != null) {
                        requestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP));
                        requestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, path);
                        final int ep = path.lastIndexOf(".");
                        requestHeader.put(HeaderFramework.CONNECTION_PROP_EXT, path.substring(ep + 1));
                        // in case that there are no args given, args = null or empty hashmap
                        final Object tmp = invokeServlet(targetClass, requestHeader, args, null);
                        if (tmp == null) {
                            // if no args given, then tp will be an empty Hashtable object (not null)
                            templatePatterns = new servletProperties();
                        } else if (tmp instanceof servletProperties) {
                            templatePatterns = (servletProperties) tmp;
                        } else {
                            templatePatterns = new servletProperties((serverObjects) tmp);
                        }
                        // check if the servlets requests authentication
                        if (templatePatterns.containsKey(serverObjects.ACTION_AUTHENTICATE)) {
                            // handle brute-force protection
                            if (realmProp != null) {
                                ConcurrentLog.info("HTTPD", "dynamic log-in for account 'admin' in http file handler for path '" + path + "' from host '" + clientIP + "'");
                                final Integer attempts = serverCore.bfHost.get(clientIP);
                                if (attempts == null)
                                    serverCore.bfHost.put(clientIP, Integer.valueOf(1));
                                else
                                    serverCore.bfHost.put(clientIP, Integer.valueOf(attempts.intValue() + 1));
                            }
                            // send authentication request to browser
                            final ResponseHeader headers = getDefaultHeaders(path);
                            headers.put(RequestHeader.WWW_AUTHENTICATE,"Basic realm=\"" + templatePatterns.get(serverObjects.ACTION_AUTHENTICATE, "") + "\"");
                            HTTPDemon.sendRespondHeader(conProp,out,httpVersion,401,headers);
                            return;
                        } else if (templatePatterns.containsKey(serverObjects.ACTION_LOCATION)) {
                            String location = templatePatterns.get(serverObjects.ACTION_LOCATION, "");
                            if (location.isEmpty()) location = path;

                            final ResponseHeader headers = getDefaultHeaders(path);
                            headers.setAdditionalHeaderProperties(templatePatterns.getOutgoingHeader().getAdditionalHeaderProperties()); //put the cookies into the new header TODO: can we put all headerlines, without trouble?
                            headers.put(HeaderFramework.LOCATION,location);
                            HTTPDemon.sendRespondHeader(conProp,out,httpVersion,302,headers);
                            return;
                        }
                        // add the application version, the uptime and the client name to every rewrite table
                        templatePatterns.put(servletProperties.PEER_STAT_VERSION, yacyBuildProperties.getVersion());
                        templatePatterns.put(servletProperties.PEER_STAT_UPTIME, ((System.currentTimeMillis() -  serverCore.startupTime) / 1000) / 60); // uptime in minutes
                        templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTNAME, sb.peers.mySeed().getName());
                        templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTID, ((Switchboard) switchboard).peers.myID());
                        templatePatterns.put(servletProperties.PEER_STAT_MYTIME, GenericFormatter.SHORT_SECOND_FORMATTER.format());
                        final Seed myPeer = sb.peers.mySeed();
                        templatePatterns.put("newpeer", myPeer.getAge() >= 1 ? 0 : 1);
                        templatePatterns.putHTML("newpeer_peerhash", myPeer.hash);
                        templatePatterns.put("p2p", sb.getConfigBool(SwitchboardConstants.DHT_ENABLED, true) || !sb.isRobinsonMode() ? 1 : 0);
                        //System.out.println("respond props: " + ((tp == null) ? "null" : tp.toString())); // debug
                        nocache = true;
                    }

                    targetDate = new Date(targetFile.lastModified());
                    Date expireDate = null;
                    if (templatePatterns == null) {
                    	// if the file will not be changed, cache it in the browser
                    	expireDate = new Date(new Date().getTime() + (31l * 24 * 60 * 60 * 1000));
                    }


                    // rewrite the file
                    InputStream fis = null;

                    // read the file/template
                    TemplateCacheEntry templateCacheEntry = null;
                    final long fileSize = targetFile.length();
                    if (useTemplateCache && fileSize <= 512 * 1024) {
                        // read from cache
                        SoftReference<TemplateCacheEntry> ref = templateCache.get(targetFile);
                        if (ref != null) {
                            templateCacheEntry = ref.get();
                            if (templateCacheEntry == null) templateCache.remove(targetFile);
                        }

                        final Date targetFileDate = new Date(targetFile.lastModified());
                        if (templateCacheEntry == null || targetFileDate.after(templateCacheEntry.lastModified)) {
                            // loading the content of the template file into
                            // a byte array
                            templateCacheEntry = new TemplateCacheEntry();
                            templateCacheEntry.lastModified = targetFileDate;
                            templateCacheEntry.content = FileUtils.read(targetFile);

                            // storing the content into the cache
                            ref = new SoftReference<TemplateCacheEntry>(templateCacheEntry);
                            if (MemoryControl.shortStatus()) templateCache.clear();
                            templateCache.put(targetFile, ref);
                            if (theLogger.isFinest()) theLogger.finest("Cache MISS for file " + targetFile);
                        } else {
                            if (theLogger.isFinest()) theLogger.finest("Cache HIT for file " + targetFile);
                        }

                        // creating an inputstream needed by the template
                        // rewrite function
                        fis = new ByteArrayInputStream(templateCacheEntry.content);
                        templateCacheEntry = null;
                    } else if (fileSize <= Math.min(4 * 1024 * 1204, MemoryControl.available() / 100)) {
                        // read file completely into ram, avoid that too many files are open at the same time
                        fis = new ByteArrayInputStream(FileUtils.read(targetFile));
                    } else {
                        fis = new BufferedInputStream(new FileInputStream(targetFile));
                    }

                    if (mimeType.startsWith("text")) {
                        // every text-file distributed by yacy is UTF-8
                        if (!path.startsWith("/repository")) {
                            mimeType = mimeType + "; charset=UTF-8";
                        } else {
                            // detect charset of html-files
                            if ((path.endsWith("html") || path.endsWith("htm"))) {
                                // save position
                                fis.mark(1000);
                                // scrape document to look up charset
                                final ScraperInputStream htmlFilter = new ScraperInputStream(fis, "UTF-8", new DigestURL("http://localhost"), null, false, 10);
                                final String charset = htmlParser.patchCharsetEncoding(htmlFilter.detectCharset());
                                htmlFilter.close();
                                if (charset != null) mimeType = mimeType + "; charset="+charset;
                                // reset position
                                fis.reset();
                            }
                        }
                    }

                    // write the array to the client
                    // we can do that either in standard mode (whole thing completely) or in chunked mode
                    // since yacy clients do not understand chunked mode (yet), we use this only for communication with the administrator
                    final boolean yacyClient = requestHeader.userAgent().startsWith("yacy");
                    final boolean chunked = !method.equals(HeaderFramework.METHOD_HEAD) && !yacyClient && httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1);
                    if (chunked) {
                        // send page in chunks and parse SSIs
                        final ByteBuffer o = new ByteBuffer();
                        // apply templates
                        TemplateEngine.writeTemplate(fis, o, templatePatterns, UNRESOLVED_PATTERN);
                        fis.close();
                        ResponseHeader rh = (templatePatterns == null) ? new ResponseHeader(200) : templatePatterns.getOutgoingHeader();
                        HTTPDemon.sendRespondHeader(conProp, out,
                                httpVersion, rh.getStatusCode(), null, mimeType, -1,
                                targetDate, expireDate, rh,
                                null, "chunked", nocache);
                        // send the content in chunked parts, see RFC 2616 section 3.6.1
                        final ChunkedOutputStream chos = new ChunkedOutputStream(out);
                        // GZIPOutputStream does not implement flush (this is a bug IMHO)
                        // so we can't compress this stuff, without loosing the cool SSI trickle feature
                        ServerSideIncludes.writeSSI(o, chos, realmProp, clientIP, requestHeader);
                        //chos.write(result);
                        chos.finish();
                    } else {
                        // send page as whole thing, SSIs are not possible
                        final String contentEncoding = (zipContent) ? "gzip" : null;
                        // apply templates
                        final ByteBuffer o1 = new ByteBuffer();
                        TemplateEngine.writeTemplate(fis, o1, templatePatterns, UNRESOLVED_PATTERN);
                        fis.close();
                        final ByteBuffer o = new ByteBuffer();

                        if (zipContent) {
                            GZIPOutputStream zippedOut = new GZIPOutputStream(o);
                            ServerSideIncludes.writeSSI(o1, zippedOut, realmProp, clientIP, requestHeader);
                            //httpTemplate.writeTemplate(fis, zippedOut, tp, UNRESOLVED_PATTERN);
                            zippedOut.finish();
                            zippedOut.flush();
                            zippedOut.close();
                            zippedOut = null;
                        } else {
                            ServerSideIncludes.writeSSI(o1, o, realmProp, clientIP, requestHeader);
                            //httpTemplate.writeTemplate(fis, o, tp, UNRESOLVED_PATTERN);
                        }
                        ResponseHeader rh = (templatePatterns == null) ? new ResponseHeader(200) : templatePatterns.getOutgoingHeader();
                        if (method.equals(HeaderFramework.METHOD_HEAD)) {
                            HTTPDemon.sendRespondHeader(conProp, out,
                                    httpVersion, rh.getStatusCode(), null, mimeType, o.length(),
                                    targetDate, expireDate, rh,
                                    contentEncoding, null, nocache);
                        } else {
                            final byte[] result = o.getBytes(); // this interrupts streaming (bad idea!)
                            HTTPDemon.sendRespondHeader(conProp, out,
                                    httpVersion, rh.getStatusCode(), null, mimeType, result.length,
                                    targetDate, expireDate, rh,
                                    contentEncoding, null, nocache);
                            FileUtils.copy(result, out);
                        }
                    }
                } else { // no html

                    int statusCode = 200;
                    int rangeStartOffset = 0;
                    final ResponseHeader header = new ResponseHeader(statusCode);

                    // adding the accept ranges header
                    header.put(HeaderFramework.ACCEPT_RANGES, "bytes");

                    // reading the files md5 hash if availabe and use it as ETAG of the resource
                    String targetMD5 = null;
                    final File targetMd5File = new File(targetFile + ".md5");
                    try {
                        if (targetMd5File.exists()) {
                            //String description = null;
                            targetMD5 = UTF8.String(FileUtils.read(targetMd5File));
                            final int pos = targetMD5.indexOf('\n');
                            if (pos >= 0) {
                                //description = targetMD5.substring(pos + 1);
                                targetMD5 = targetMD5.substring(0, pos);
                            }

                            // using the checksum as ETAG header
                            header.put(HeaderFramework.ETAG, targetMD5);
                        }
                    } catch (final IOException e) {
                        ConcurrentLog.logException(e);
                    }

                    if (requestHeader.containsKey(HeaderFramework.RANGE)) {
                        final Object ifRange = requestHeader.ifRange();
                        if ((ifRange == null)||
                            (ifRange instanceof Date && targetFile.lastModified() == ((Date)ifRange).getTime()) ||
                            (ifRange instanceof String && ifRange.equals(targetMD5))) {
                            final String rangeHeaderVal = requestHeader.get(HeaderFramework.RANGE).trim();
                            if (rangeHeaderVal.startsWith("bytes=")) {
                                final String rangesVal = rangeHeaderVal.substring("bytes=".length());
                                final String[] ranges = CommonPattern.COMMA.split(rangesVal);
                                if ((ranges.length == 1)&&(ranges[0].endsWith("-"))) {
                                    rangeStartOffset = NumberTools.parseIntDecSubstring(ranges[0], 0, ranges[0].length() - 1);
                                    statusCode = 206;
                                    header.put(HeaderFramework.CONTENT_RANGE, "bytes " + rangeStartOffset + "-" + (targetFile.length()-1) + "/" + targetFile.length());
                                }
                            }
                        }
                    }

                    // write the file to the client
                    targetDate = new Date(targetFile.lastModified());
                    // cache file for one month in browser (but most browsers won't cache for that long)
                    final Date expireDate = new Date(new Date().getTime() + (31l * 24 * 60 * 60 * 1000));
                    final long   contentLength    = (zipContent)?-1:targetFile.length()-rangeStartOffset;
                    final String contentEncoding  = (zipContent) ? "gzip" : null;
                    final String transferEncoding = (httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1) && zipContent) ? "chunked" : null;
                    if (!httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1) && zipContent) forceConnectionClose(conProp);

                    HTTPDemon.sendRespondHeader(conProp, out, httpVersion, statusCode, null, mimeType, contentLength, targetDate, expireDate, header, contentEncoding, transferEncoding, nocache);

                    if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                        ChunkedOutputStream chunkedOut = null;
                        GZIPOutputStream zipped = null;
                        OutputStream newOut = out;

                        if (transferEncoding != null) {
                            chunkedOut = new ChunkedOutputStream(newOut);
                            newOut = chunkedOut;
                        }
                        if (contentEncoding != null) {
                            zipped = new GZIPOutputStream(newOut);
                            newOut = zipped;
                        }

                        FileUtils.copyRange(targetFile, newOut, rangeStartOffset);

                        if (zipped != null) {
                            zipped.flush();
                            zipped.finish();
                        }
                        if (chunkedOut != null) {
                            chunkedOut.finish();
                        }

                        // flush all
                        try {newOut.flush();}catch (final Exception e) {}
                    }
                }
            } else {
                if (!targetFile.exists()) ConcurrentLog.warn("HTTPDFileHandler", "target file " + targetFile.getAbsolutePath() + " does not exist");
                //if (!targetFile.isFile()) Log.logWarning("HTTPDFileHandler", "target file " + targetFile.getAbsolutePath() + " is not a file");
                //if (!targetFile.canRead()) Log.logWarning("HTTPDFileHandler", "target file " + targetFile.getAbsolutePath() + " cannot read");
                HTTPDemon.sendRespondError(conProp,out,3,404,"File not Found",null,null);
                return;
            }
        } catch (final Exception e) {
            try {
                // error handling
                if (e instanceof NullPointerException) {
                    ConcurrentLog.logException(e);
                }
                int httpStatusCode = 400;
                final String httpStatusText = null;
                final StringBuilder errorMessage = new StringBuilder(2000);
                Exception errorExc = null;

                final String errorMsg = e.getMessage();
                if (
                        (e instanceof InterruptedException) ||
                        ((errorMsg != null) && (errorMsg.startsWith("Socket closed")) && (Thread.currentThread().isInterrupted()))
                   ) {
                    errorMessage.append("Interruption detected while processing query.");
                    httpStatusCode = 503;
                } else {
                    if ((errorMsg != null) &&
                        (
                           errorMsg.contains("Broken pipe") ||
                           errorMsg.contains("Connection reset") ||
                           errorMsg.contains("Read timed out") ||
                           errorMsg.contains("Connection timed out") ||
                           errorMsg.contains("Software caused connection abort")
                       )) {
                        // client closed the connection, so we just end silently
                        errorMessage.append("Client unexpectedly closed connection while processing query.");
                    } else {
                        errorMessage.append("Unexpected error while processing query.");
                        httpStatusCode = 500;
                        errorExc = e;
                    }
                }

                errorMessage.append("\nSession: ").append(Thread.currentThread().getName())
                            .append("\nQuery:   ").append(path)
                            .append("\nClient:  ").append(conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP))
                            .append("\nReason:  ").append(e.getMessage());

                if (!conProp.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                    // sending back an error message to the client
                    // if we have not already send an http header
                    HTTPDemon.sendRespondError(conProp,out, 4, httpStatusCode, httpStatusText, errorMessage.toString(), errorExc);
                } else {
                    // otherwise we close the connection
                    forceConnectionClose(conProp);
                }

                // if it is an unexpected error we log it
                if (httpStatusCode == 500) {
                    theLogger.warn(errorMessage.toString(), e);
                }

            } catch (final Exception ee) {
                forceConnectionClose(conProp);
            }

        } finally {
            try {out.flush();}catch (final Exception e) {}
        }
    }

    /**
     * Returns a list which contains parts of command
     * which is used to start external process for
     * CGI scripts.
     * @param targetFile file to run
     * @return list of parts of command
     * @throws FileNotFoundException
     * @throws IOException if file can not be accessed
     */
    private static List<String> assembleCommandFromShebang(final File targetFile) throws FileNotFoundException {
        final List<String > ret = new ArrayList<String>();
        final BufferedReader br = new BufferedReader(new FileReader(targetFile), 512);
        String line;
        try {
            line = br.readLine();
            if (line.startsWith("#!")) {
                ret.addAll(Arrays.asList(CommonPattern.SPACE.split(line.substring(2))));
            }
            ret.add(targetFile.getAbsolutePath());
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } finally {
            try {br.close();} catch (final IOException e) {}
        }
        return ret;
    }

    private static final String appendPath(final String proplist, final String path) {
        if (proplist.isEmpty()) return path;
        if (proplist.indexOf(path) >= 0) return proplist;
        return proplist + "," + path;
    }

    public static final File getOverlayedClass(final String path) {
        File targetClass;
        targetClass = rewriteClassFile(new File(htDefaultPath, path)); //works for default and localized files
        if (targetClass == null || !targetClass.exists()) {
            //works for htdocs
            targetClass=rewriteClassFile(new File(htDocsPath, path));
        }
        return targetClass;
    }

    public static final File getOverlayedFile(final String path) {
        File targetFile;
        targetFile = getLocalizedFile(path);
        if (!targetFile.exists()) {
            targetFile = new File(htDocsPath, path);
        }
        return targetFile;
    }

    private static final void forceConnectionClose(final HashMap<String, Object> conprop) {
        if (conprop != null) {
            conprop.put(HeaderFramework.CONNECTION_PROP_PERSISTENT, "close");
        }
    }

    private static final File rewriteClassFile(final File template) {
        try {
            String f = template.getCanonicalPath();
            int cp = f.length() - 8;
            if (cp < 0) {
                final int p = f.lastIndexOf('.');
                f = p < 0 ? f + ".class" : f.substring(0, p) + ".class";
            } else {
                final int p = f.substring(cp).lastIndexOf('.');
                f = p < 0 ? f + ".class" : f.substring(0, cp + p) + ".class";
            }
            final File cf = new File(f);
            if (cf.exists()) return cf;
            return null;
        } catch (final IOException e) {
            return null;
        }
    }

    private static final Method rewriteMethod(final File classFile, final String methodName) throws InvocationTargetException {
        Method m = null;
        // now make a class out of the stream
        try {
            if (templateMethodCache != null && "respond".equals(methodName)) {
                final SoftReference<Method> ref = templateMethodCache.get(classFile);
                if (ref != null) {
                    m = ref.get();
                    if (m == null) {
                        templateMethodCache.remove(classFile);
                    } else {
                        return m;
                    }
                }
            }

            final Class<?> c = provider.loadClass(classFile);
            Class<?>[] params = new Class[] {
                    RequestHeader.class,
                    serverObjects.class,
                    serverSwitch.class };
            try {
                m = c.getMethod(methodName, params);
            } catch (final NoSuchMethodException e) {
                params = new Class[] {
                    RequestHeader.class,
                    serverObjects.class,
                    serverSwitch.class,
                    OutputStream.class};
                m = c.getMethod(methodName, params);
            }

            if (MemoryControl.shortStatus()) {
                templateMethodCache.clear();
            } else {
                // store the method into the cache
                if (templateMethodCache != null && "respond".equals(methodName)) {
                    templateMethodCache.put(classFile, new SoftReference<Method>(m));
                }
            }

        } catch (final ClassNotFoundException e) {
            ConcurrentLog.severe("HTTPDFileHandler", "class " + classFile + " is missing:" + e.getMessage());
            throw new InvocationTargetException(e, "class " + classFile + " is missing:" + e.getMessage());
        } catch (final NoSuchMethodException e) {
            ConcurrentLog.severe("HTTPDFileHandler", "method 'respond' not found in class " + classFile + ": " + e.getMessage());
            throw new InvocationTargetException(e, "method 'respond' not found in class " + classFile + ": " + e.getMessage());
        }
        return m;
    }

    private static final Object invokeServlet(final File targetClass, final RequestHeader request, final serverObjects args, final OutputStream os) {
        try {
            if (os == null) {
                return rewriteMethod(targetClass, "respond").invoke(null, new Object[] {request, args, switchboard});
            }
            return rewriteMethod(targetClass, "respond").invoke(null, new Object[] {request, args, switchboard, os});
        } catch (final Throwable e) {
            theLogger.severe("INTERNAL ERROR: " + e.toString() + ":" +
                            e.getMessage() +
                            " target exception at " + targetClass + ": " +
                            "; java.awt.graphicsenv='" + System.getProperty("java.awt.graphicsenv","") + "'");
            ConcurrentLog.logException(e);
            ConcurrentLog.logException(e.getCause());
            if (e instanceof InvocationTargetException) ConcurrentLog.logException(((InvocationTargetException) e).getTargetException());
            return null;
        }
    }

    private static final String getMimeFromServlet(final File targetClass, final RequestHeader request, final serverObjects args, final String dflt) {
        try {
            return (String) rewriteMethod(targetClass, "mime").invoke(null, new Object[] {request, args, switchboard});
        } catch (final Throwable e) {
            theLogger.severe("INTERNAL ERROR: " + e.toString() + ":" +
                            e.getMessage() +
                            " target exception at " + targetClass + ": " +
                            "; java.awt.graphicsenv='" + System.getProperty("java.awt.graphicsenv","") + "'");
            ConcurrentLog.logException(e);
            ConcurrentLog.logException(e.getCause());
            if (e instanceof InvocationTargetException) ConcurrentLog.logException(((InvocationTargetException) e).getTargetException());
            return dflt;
        }
    }

    /**
     * Tells if a filename ends with a suffix from a given list.
     * @param filename the filename
     * @param suffixList the list of suffixes which is a string of suffixes separated by commas
     * @return true if the filename ends with a suffix from the list, else false
     */
    private static boolean matchesSuffix(final String name, final String suffixList) {
        boolean ret = false;

        if (suffixList != null && name != null) {
            final String[] suffixes = CommonPattern.COMMA.split(suffixList);
            find:
            for (int i = 0; i < suffixes.length; i++) {
                if (name.endsWith("." + suffixes[i].trim())) {
                    ret = true;
                    break find;
                }
            }
        }

        return ret;
    }

    /**
     * do a proxy request for document
     * extracts url from GET-parameter url
     * not in separete servlet, because we need access to binary outstream
     * @throws IOException
     */
    private static void doURLProxy(final HashMap<String, Object> conProp, final RequestHeader requestHeader, final OutputStream out) throws IOException {
        final String httpVersion = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER);
		URL proxyurl = null;
		String action = "";

		if(conProp != null && conProp.containsKey("ARGS")) {
			String strARGS = (String) conProp.get("ARGS");
			if(strARGS.startsWith("action=")) {
				int detectnextargument = strARGS.indexOf("&");
				action = strARGS.substring (7, detectnextargument);
				strARGS = strARGS.substring(detectnextargument+1);
			}
			if(strARGS.startsWith("url=")) {
				final String strUrl = strARGS.substring(4); // strip url=

				try {
				proxyurl = new URL(strUrl);
				} catch (final MalformedURLException e) {
					proxyurl = new URL (URLDecoder.decode(strUrl, UTF8.charset.name()));

				}
			}
		}

		if (proxyurl==null) {
			throw new IOException("no url as argument supplied");
		}
		String host = proxyurl.getHost();
		if (proxyurl.getPort() != -1) {
			host += ":" + proxyurl.getPort();
		}

		// set properties for proxy connection
   		final HashMap<String, Object> prop = new HashMap<String, Object>();
		prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_1_1);
		prop.put(HeaderFramework.CONNECTION_PROP_HOST, host);
		prop.put(HeaderFramework.CONNECTION_PROP_PATH, proxyurl.getFile().replaceAll(" ", "%20"));
		prop.put(HeaderFramework.CONNECTION_PROP_REQUESTLINE, "PROXY");
		prop.put("CLIENTIP", "0:0:0:0:0:0:0:1");

		// remove some stuff from request header, so it isn't send to the server
		requestHeader.remove("CLIENTIP");
		requestHeader.remove("EXT");
		requestHeader.remove("PATH");
		requestHeader.remove("Authorization");
		requestHeader.remove("Connection");
		requestHeader.put(HeaderFramework.HOST, proxyurl.getHost());

		// temporarily add argument to header to pass it on to augmented browsing
		requestHeader.put("YACYACTION", action);

		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		HTTPDProxyHandler.doGet(prop, requestHeader, o, ClientIdentification.yacyProxyAgent);

		// reparse header to extract content-length and mimetype
		final ResponseHeader outgoingHeader = new ResponseHeader(200);
		final InputStream in = new ByteArrayInputStream(o.toByteArray());
		String line = readLine(in);
		while(line != null && !line.equals("")) {
			int p;
			if ((p = line.indexOf(':')) >= 0) {
				// store a property
				outgoingHeader.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
			}
			line = readLine(in);
		}
		if (line==null) {
			HTTPDemon.sendRespondError(conProp,out,3,500,"null",null,null);
			return;
		}

		final int httpStatus = Integer.parseInt((String) prop.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_STATUS));

		String directory = "";
		if (proxyurl.getPath().lastIndexOf('/') > 0)
			directory = proxyurl.getPath().substring(0, proxyurl.getPath().lastIndexOf('/'));

		String location = "";

		if (outgoingHeader.containsKey("Location")) {
			// rewrite location header
			location = outgoingHeader.get("Location");
			if (location.startsWith("http")) {
				location = "/proxy.html?action="+action+"&url=" + location;
			} else {
				location = "/proxy.html?action="+action+"&url=http://" + proxyurl.getHost() + "/" + location;
			}
			outgoingHeader.put("Location", location);
		}

		final String mimeType = outgoingHeader.getContentType();
		if ((mimeType.startsWith("text/html") || mimeType.startsWith("text"))) {
			final StringWriter buffer = new StringWriter();

			if (outgoingHeader.containsKey(HeaderFramework.TRANSFER_ENCODING)) {
				FileUtils.copy(new ChunkedInputStream(in), buffer, UTF8.charset);
			} else {
				FileUtils.copy(in, buffer, UTF8.charset);
			}

			final String sbuffer = buffer.toString();

			final Pattern p = Pattern.compile("(href=\"|src=\")([^\"]+)|(href='|src=')([^']+)|(url\\(')([^']+)|(url\\(\")([^\"]+)|(url\\()([^\\)]+)");
			final Matcher m = p.matcher(sbuffer);
			final StringBuffer result = new StringBuffer(80);
			String init, url;
			MultiProtocolURL target;
			while (m.find()) {
				init = null;
				if(m.group(1) != null) init = m.group(1);
				if(m.group(3) != null) init = m.group(3);
				if(m.group(5) != null) init = m.group(5);
				if(m.group(7) != null) init = m.group(7);
				if(m.group(9) != null) init = m.group(9);
				url = null;
				if(m.group(2) != null) url = m.group(2);
				if(m.group(4) != null) url = m.group(4);
				if(m.group(6) != null) url = m.group(6);
				if(m.group(8) != null) url = m.group(8);
				if(m.group(10) != null) url = m.group(10);
				if (url.startsWith("data:") || url.startsWith("#") || url.startsWith("mailto:") || url.startsWith("javascript:")) {
                                    String newurl = init + url;
                                    newurl = newurl.replaceAll("\\$","\\\\\\$");
				    m.appendReplacement(result, newurl);

				} else if (url.startsWith("http")) {
                                    // absoulte url of form href="http://domain.com/path"
                                    if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist")) {
                                        try {
                                            if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL(url)) != null) {
                                                continue;
                                            }
                                        } catch (final MalformedURLException e) {
                                            theLogger.fine("malformed url for url-rewirte: " + url);
                                            continue;
                                        }
                                    }

                                    String newurl = init + "/proxy.html?url=" + url;
                                    newurl = newurl.replaceAll("\\$","\\\\\\$");
				    m.appendReplacement(result, newurl);

				} else if (url.startsWith("//")) {
                                    // absoulte url but same protocol of form href="//domain.com/path"
                                    final String complete_url = proxyurl.getProtocol() + ":" +  url;
                                    if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist")) {
                                        try {
                                            if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL(complete_url)) != null) {
                                                continue;
                                            }
                                        } catch (final MalformedURLException e) {
                                            theLogger.fine("malformed url for url-rewirte: " + complete_url);
                                            continue;
                                        }
                                    }

                                    String newurl = init + "/proxy.html?url=" + complete_url;
                                    newurl = newurl.replaceAll("\\$", "\\\\\\$");
                                    m.appendReplacement(result, newurl);

				} else if (url.startsWith("/")) {
                                    // absolute path of form href="/absolute/path/to/linked/page"
                                    String newurl = init + "/proxy.html?url=http://" + host + url;
                                    newurl = newurl.replaceAll("\\$","\\\\\\$");
				    m.appendReplacement(result, newurl);

				} else {
					// relative path of form href="relative/path"
					try {
						target = new MultiProtocolURL("http://" + host + directory + "/" + url);
						String newurl = init + "/proxy.html?url=" + target.toString();
						newurl = newurl.replaceAll("\\$","\\\\\\$");
						m.appendReplacement(result, newurl);
					}
					catch (final MalformedURLException e) {}

				}
			}
			m.appendTail(result);

			final byte[] sbb = UTF8.getBytes(result.toString());

			if (outgoingHeader.containsKey(HeaderFramework.TRANSFER_ENCODING)) {
				HTTPDemon.sendRespondHeader(conProp, out, httpVersion, httpStatus, outgoingHeader);
				final ChunkedOutputStream cos = new ChunkedOutputStream(out);
				cos.write(sbb);
				cos.finish();
				cos.close();
			} else {
				outgoingHeader.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(sbb.length));
				HTTPDemon.sendRespondHeader(conProp, out, httpVersion, httpStatus, outgoingHeader);
				out.write(sbb);
			}
		} else {
			if (!outgoingHeader.containsKey(HeaderFramework.CONTENT_LENGTH))
				outgoingHeader.put(HeaderFramework.CONTENT_LENGTH, (String) prop.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE));
    		HTTPDemon.sendRespondHeader(conProp, out, httpVersion, httpStatus, outgoingHeader);
			FileUtils.copy(in, out);
		}
		return;
    }

	private static String readLine(final InputStream in) throws IOException {
		final ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int b;
		while ((b=in.read()) != '\r' && b != -1) {
			buf.write(b);
		}
		if (b == -1) return null;
		b = in.read(); // read \n
		if (b == -1) return null;
		return buf.toString("UTF-8");
	}

}
