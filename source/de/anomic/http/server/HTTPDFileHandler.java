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

package de.anomic.http.server;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import net.yacy.document.Classification;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ScraperInputStream;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.Domains;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.visualization.RasterPlotter;

import de.anomic.data.MimeTable;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverClassLoader;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;
import de.anomic.yacy.yacyBuildProperties;

public final class HTTPDFileHandler {
    
    private static final boolean safeServletsMode = false; // if true then all servlets are called synchronized
    
    // create a class loader
    private static final serverClassLoader provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
    private static serverSwitch switchboard = null;
    private static Switchboard sb = Switchboard.getSwitchboard();
    
    private static File     htRootPath     = null;
    private static File     htDocsPath     = null;
    private static File     htTemplatePath = null;
    private static String[] defaultFiles   = null;
    private static File     htDefaultPath  = null;
    private static File     htLocalePath   = null;
    
    protected static final class TemplateCacheEntry {
        Date lastModified;
        byte[] content;
    }
    private static final ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>> templateCache;    
    private static final ConcurrentHashMap<File, SoftReference<Method>> templateMethodCache;
    
    public static final boolean useTemplateCache;
    
    //private Properties connectionProperties = null;
    // creating a logger
    private static final Log theLogger = new Log("FILEHANDLER");
    
    static {
        final serverSwitch theSwitchboard = Switchboard.getSwitchboard();
        useTemplateCache = theSwitchboard.getConfig("enableTemplateCache","true").equalsIgnoreCase("true");
        templateCache = (useTemplateCache)? new ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>>() : new ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>>(0);
        templateMethodCache = (useTemplateCache) ? new ConcurrentHashMap<File, SoftReference<Method>>() : new ConcurrentHashMap<File, SoftReference<Method>>(0);
        
        if (switchboard == null) {
            switchboard = theSwitchboard;

            if (MimeTable.isEmpty()) {
                // load the mime table
                final String mimeTablePath = theSwitchboard.getConfig("mimeTable","");
                Log.logConfig("HTTPDFiles", "Loading mime mapping file " + mimeTablePath);
                MimeTable.init(new File(theSwitchboard.getRootPath(), mimeTablePath));
            }
            
            // create default files array
            initDefaultPath();
            
            // create a htRootPath: system pages
            if (htRootPath == null) {
                    htRootPath = new File(theSwitchboard.getRootPath(), theSwitchboard.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT));
                    if (!(htRootPath.exists())) htRootPath.mkdir();
            }
            
            // create a htDocsPath: user defined pages
            if (htDocsPath == null) {
                htDocsPath = theSwitchboard.getConfigPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
                if (!(htDocsPath.exists())) htDocsPath.mkdirs();
            }
            
            // create a repository path
            final File repository = new File(htDocsPath, "repository");
            if (!repository.exists()) repository.mkdirs();
            
            // create a htTemplatePath
            if (htTemplatePath == null) {
                htTemplatePath = theSwitchboard.getConfigPath("htTemplatePath","htroot/env/templates");
                if (!(htTemplatePath.exists())) htTemplatePath.mkdir();
            }
            //This is now handles by #%env/templates/foo%#
            //if (templates.isEmpty()) templates.putAll(httpTemplate.loadTemplates(htTemplatePath));
            
            // create htLocaleDefault, htLocalePath
            if (htDefaultPath == null) htDefaultPath = theSwitchboard.getConfigPath("htDefaultPath", "htroot");
            if (htLocalePath == null) htLocalePath = theSwitchboard.getConfigPath("locale.translated_html", "DATA/LOCALE/htroot");
        }
        
    }
    
    public static final void initDefaultPath() {
        // create default files array
        defaultFiles = switchboard.getConfig("defaultFiles","index.html").split(",");
        if (defaultFiles.length == 0) defaultFiles = new String[] {"index.html"};
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

        File docsPath  = new File(htDocsPath, path);
        if (docsPath.exists()) return docsPath;
        return new File(htDefaultPath, path);
    }
    
    private static final ResponseHeader getDefaultHeaders(final String path) {
        final ResponseHeader headers = new ResponseHeader();
        String ext;
        int pos;
        if ((pos = path.lastIndexOf('.')) < 0) {
            ext = "";
        } else {
            ext = path.substring(pos + 1).toLowerCase();
        }
        headers.put(HeaderFramework.SERVER, "AnomicHTTPD (www.anomic.de)");
        headers.put(HeaderFramework.DATE, DateFormatter.formatRFC1123(new Date()));
        if(!(Classification.isMediaExtension(ext))){
            headers.put(HeaderFramework.PRAGMA, "no-cache");         
        }
        return headers;
    }
    
    public static void doGet(final Properties conProp, final RequestHeader requestHeader, final OutputStream response) {
        doResponse(conProp, requestHeader, response, null);
    }
    
    public static void doHead(final Properties conProp, final RequestHeader requestHeader, final OutputStream response) {
        doResponse(conProp, requestHeader, response, null);
    }
    
    public static void doPost(final Properties conProp, final RequestHeader requestHeader, final OutputStream response, final InputStream body) {
        doResponse(conProp, requestHeader, response, body);
    }
    
    public static void doResponse(final Properties conProp, final RequestHeader requestHeader, final OutputStream out, final InputStream body) {
  
        String path = null;
        try {
            // getting some connection properties            
            final String method = conProp.getProperty(HeaderFramework.CONNECTION_PROP_METHOD);
            path = conProp.getProperty(HeaderFramework.CONNECTION_PROP_PATH);
            String argsString = conProp.getProperty(HeaderFramework.CONNECTION_PROP_ARGS); // is null if no args were given
            final String httpVersion = conProp.getProperty(HeaderFramework.CONNECTION_PROP_HTTP_VER);
            final String clientIP = conProp.getProperty(HeaderFramework.CONNECTION_PROP_CLIENTIP, "unknown-host");
            
            // check hack attacks in path
            if (path.indexOf("..") >= 0) {
                HTTPDemon.sendRespondError(conProp,out,4,403,null,"Access not allowed",null);
                return;
            }
            
            // url decoding of path
            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (final UnsupportedEncodingException e) {
                // This should never occur
                assert(false) : "UnsupportedEncodingException: " + e.getMessage();
            }
            
            // check against hack attacks in path
            if (path.indexOf("..") >= 0) {
                HTTPDemon.sendRespondError(conProp,out,4,403,null,"Access not allowed",null);
                return;
            }
            
            // check permission/granted access
            String authorization = requestHeader.get(RequestHeader.AUTHORIZATION);
            if (authorization != null && authorization.length() == 0) authorization = null;
            final String adminAccountBase64MD5 = switchboard.getConfig(HTTPDemon.ADMIN_ACCOUNT_B64MD5, "");
            
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
            
            final boolean adminAccountForLocalhost = sb.getConfigBool("adminAccountForLocalhost", false);
            final String refererHost = requestHeader.refererHost();
            boolean accessFromLocalhost = Domains.isLocal(clientIP) && (refererHost == null || refererHost.length() == 0 || Domains.isLocal(refererHost));
            final boolean grantedForLocalhost = adminAccountForLocalhost && accessFromLocalhost;
            final boolean protectedPage = path.indexOf("_p.") > 0;
            final boolean accountEmpty = adminAccountBase64MD5.length() == 0;
            final boolean softauth = accessFromLocalhost && authorization != null && authorization.length() > 6 && (adminAccountBase64MD5.equals(authorization.substring(6)));

            if (!softauth && !grantedForLocalhost && protectedPage && !accountEmpty) {
                // authentication required
                if (authorization == null) {
                    // no authorization given in response. Ask for that
                    final ResponseHeader responseHeader = getDefaultHeaders(path);
                    responseHeader.put(RequestHeader.WWW_AUTHENTICATE,"Basic realm=\"admin log-in\"");
                    //httpd.sendRespondHeader(conProp,out,httpVersion,401,headers);
                    final servletProperties tp=new servletProperties();
                    tp.put("returnto", path);
                    //TODO: separate error page Wrong Login / No Login
                    HTTPDemon.sendRespondError(conProp, out, 5, 401, "Wrong Authentication", "", new File("proxymsg/authfail.inc"), tp, null, responseHeader);
                    return;
                } else if (
                    (HTTPDemon.staticAdminAuthenticated(authorization.trim().substring(6), switchboard) == 4) ||
                    (sb.userDB.hasAdminRight(authorization, requestHeader.getHeaderCookies()))) {
                    //Authentication successful. remove brute-force flag
                    serverCore.bfHost.remove(conProp.getProperty(HeaderFramework.CONNECTION_PROP_CLIENTIP));
                } else {
                    // a wrong authentication was given or the userDB user does not have admin access. Ask again
                    Log.logInfo("HTTPD", "Wrong log-in for account 'admin' in http file handler for path '" + path + "' from host '" + clientIP + "'");
                    final Integer attempts = serverCore.bfHost.get(clientIP);
                    if (attempts == null)
                        serverCore.bfHost.put(clientIP, Integer.valueOf(1));
                    else
                        serverCore.bfHost.put(clientIP, Integer.valueOf(attempts.intValue() + 1));
    
                    final ResponseHeader headers = getDefaultHeaders(path);
                    headers.put(RequestHeader.WWW_AUTHENTICATE,"Basic realm=\"admin log-in\"");
                    HTTPDemon.sendRespondHeader(conProp,out,httpVersion,401,headers);
                    return;
                }
            }
        
            // parse arguments
            serverObjects args = new serverObjects();
            int argc = 0;
            if (argsString == null) {
                // no args here, maybe a POST with multipart extension
                int length = requestHeader.getContentLength();
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
                        final HashMap<String, byte[]> files = HTTPDemon.parseMultipart(requestHeader, args, body);
                        // integrate these files into the args
                        if (files != null) {
                            final Iterator<Map.Entry<String, byte[]>> fit = files.entrySet().iterator();
                            Map.Entry<String, byte[]> entry;
                            while (fit.hasNext()) {
                                entry = fit.next();
                                args.put(entry.getKey() + "$file", entry.getValue());
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
                    if ((val != null) && (val.indexOf("<script") >= 0) && !path.equals("/Crawler_p.html")) {
                        // deny request
                        HTTPDemon.sendRespondError(conProp,out,4,403,null,"bad post values",null);
                        return;
                    }
                }
            }
        
            // we are finished with parsing
            // the result of value hand-over is in args and argc
            if (path.length() == 0) {
                HTTPDemon.sendRespondError(conProp,out,4,400,null,"Bad Request",null);
                out.flush();
                return;
            }
            File targetClass=null;

            // locate the file
            if (path.length() > 0 && path.charAt(0) != '/' && path.charAt(0) != '\\') path = "/" + path; // attach leading slash
            
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
            final String targetExt = conProp.getProperty("EXT","");
            targetClass = rewriteClassFile(new File(htDefaultPath, path));
            if (path.endsWith("/") || path.endsWith("\\")) {
                String testpath;
                // attach default file name
                for (int i = 0; i < defaultFiles.length; i++) {
                    testpath = path + defaultFiles[i];
                    targetFile = getOverlayedFile(testpath);
                    targetClass = getOverlayedClass(testpath);
                    if (targetFile.exists()) {
                        path = testpath;
                        break;
                    }
                }
                targetFile = getLocalizedFile(path, localeSelection);
                
                //no defaultfile, send a dirlisting
                if (targetFile == null || !targetFile.exists() || (targetFile.exists() && targetFile.isDirectory())) {
                    final StringBuilder aBuffer = new StringBuilder();
                    aBuffer.append("<html>\n<head>\n</head>\n<body>\n<h1>Index of " + path + "</h1>\n  <ul>\n");
                    String[] list = targetFile.list();
                    if (list == null) list = new String[0]; // should not occur!
                    File f;
                    String size;
                    long sz;
                    String headline, author, description;
                    int images, links;
                    ContentScraper scraper;
                    for (int i = 0; i < list.length; i++) {
                        f = new File(targetFile, list[i]);
                        if (f.isDirectory()) {
                            aBuffer.append("    <li><a href=\"" + path + list[i] + "/\">" + list[i] + "/</a><br></li>\n");
                        } else {
                            if (list[i].endsWith("html") || (list[i].endsWith("htm"))) {
                                scraper = ContentScraper.parseResource(f);
                                headline = scraper.getTitle();
                                author = scraper.getAuthor();
                                description = scraper.getDescription();
                                images = scraper.getImages().size();
                                links = scraper.getAnchors().size();
                            } else {
                                headline = null;
                                author = null;
                                description = null;
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
                            if ((headline != null) && (headline.length() > 0)) aBuffer.append("<a href=\"" + list[i] + "\"><b>" + headline + "</b></a><br>");
                            aBuffer.append("<a href=\"" + path + list[i] + "\">" + list[i] + "</a><br>");
                            if ((author != null) && (author.length() > 0)) aBuffer.append("Author: " + author + "<br>");
                            if ((description != null) && (description.length() > 0)) aBuffer.append("Description: " + description + "<br>");
                            aBuffer.append(DateFormatter.formatShortDay(new Date(f.lastModified())) + ", " + size + ((images > 0) ? ", " + images + " images" : "") + ((links > 0) ? ", " + links + " links" : "") + "<br></li>\n");
                        }
                    }
                    aBuffer.append("  </ul>\n</body>\n</html>\n");

                    // write the list to the client
                    HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, "text/html; charset=UTF-8", aBuffer.length(), new Date(targetFile.lastModified()), null, new ResponseHeader(), null, null, true);
                    if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                        out.write(aBuffer.toString().getBytes("UTF-8"));
                    }
                    return;
                }
            } else {
                    //XXX: you cannot share a .png/.gif file with a name like a class in htroot.
                    if ( !(targetFile.exists()) &&
                            !((path.endsWith("png")||path.endsWith("gif") ||
                            matchesSuffix(path, switchboard.getConfig("cgi.suffixes", null)) ||
                            path.endsWith(".stream")) &&
                            targetClass!=null ) ){
                        targetFile = new File(htDocsPath, path);
                        targetClass = rewriteClassFile(new File(htDocsPath, path));
                    }
            }
            
            //File targetClass = rewriteClassFile(targetFile);
            //We need tp here
            servletProperties templatePatterns = null;
            Date targetDate;
            boolean nocache = false;
            
            if ((targetClass != null) && (path.endsWith("png"))) {
                // call an image-servlet to produce an on-the-fly - generated image
                Object img = null;
                try {
                    requestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, conProp.getProperty(HeaderFramework.CONNECTION_PROP_CLIENTIP));
                    requestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, path);
                    // in case that there are no args given, args = null or empty hashmap
                    img = invokeServlet(targetClass, requestHeader, args);
                } catch (final InvocationTargetException e) {
                    theLogger.logSevere("INTERNAL ERROR: " + e.toString() + ":" +
                    e.getMessage() +
                    " target exception at " + targetClass + ": " +
                    e.getTargetException().toString() + ":" +
                    e.getTargetException().getMessage() +
                    "; java.awt.graphicsenv='" + System.getProperty("java.awt.graphicsenv","") + "'");
                    Log.logException(e);
                    Log.logException(e.getTargetException());
                    targetClass = null;
                }
                if (img == null) {
                    // error with image generation; send file-not-found
                    HTTPDemon.sendRespondError(conProp, out, 3, 404, "File not Found", null, null);
                } else {
                    if (img instanceof RasterPlotter) {
                        final RasterPlotter yp = (RasterPlotter) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        final String mimeType = MimeTable.ext2mime(targetExt, "text/html");
                        final ByteBuffer result = RasterPlotter.exportImage(yp.getImage(), targetExt);

                        // write the array to the client
                        HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, mimeType, result.length(), targetDate, null, null, null, null, nocache);
                        if (!method.equals(HeaderFramework.METHOD_HEAD)) {
                            result.writeTo(out);
                        }
                    }
                    if (img instanceof Image) {
                        final Image i = (Image) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        final String mimeType = MimeTable.ext2mime(targetExt, "text/html");

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
            } else if (((switchboard.getConfigBool("cgi.allow", false)) &&                                  // check if CGI execution is allowed in config
                    (matchesSuffix(path, switchboard.getConfig("cgi.suffixes", null))) &&  // "right" file extension?
                    (path.substring(0, path.indexOf(targetFile.getName())).contains("/CGI-BIN/") ||
                    path.substring(0, path.indexOf(targetFile.getName())).contains("/cgi-bin/")) &&         // file in right directory?
                    targetFile.exists())
                    ) {

                String mimeType = "text/html";
                int statusCode = 200;

                ProcessBuilder pb;

                pb = new ProcessBuilder(targetFile.getAbsolutePath());

                String fileSeparator = System.getProperty("file.separator", "/");

                // set environment variables
                Map<String, String> env = pb.environment();
                env.put("SERVER_SOFTWARE", getDefaultHeaders(path).get(HeaderFramework.SERVER));
                env.put("SERVER_NAME", switchboard.getConfig("peerName", "<nameless>"));
                env.put("GATEWAY_INTERFACE", "CGI/1.1");
                if (httpVersion != null) {
                    env.put("SERVER_PROTOCOL", httpVersion);
                }
                env.put("SERVER_PORT", switchboard.getConfig("port", "8080"));
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
                env.put("DOCUMENT_ROOT", switchboard.getRootPath().getAbsolutePath() + fileSeparator + switchboard.getConfig("htDocsPath", "DATA/HTDOCS"));
                if (requestHeader.getContentType() != null) {
                    env.put("CONTENT_TYPE", requestHeader.getContentType());
                }
                if (method.equalsIgnoreCase(HeaderFramework.METHOD_POST) && body != null) {
                    env.put("CONTENT_LENGTH", Integer.toString(requestHeader.getContentLength()));
                }

                // add values from request header to environment (see: http://hoohoo.ncsa.uiuc.edu/cgi/env.html#headers)
                for (Map.Entry<String, String> requestHeaderEntry : requestHeader.entrySet()) {
                    env.put("HTTP_" + requestHeaderEntry.getKey().toUpperCase().replace("-", "_"), requestHeaderEntry.getValue());
                }

                int exitValue = 0;
                String cgiBody = null;

                try {
                    // start execution of script
                    Process p = pb.start();

                    OutputStream os = new BufferedOutputStream(p.getOutputStream());

                    if (method.equalsIgnoreCase(HeaderFramework.METHOD_POST) && body != null) {
                        byte[] buffer = new byte[1024];
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
                    } catch (InterruptedException ex) {

                    }

                    exitValue = p.exitValue();

                    InputStream is = new BufferedInputStream(p.getInputStream());

                    StringBuilder StringBuilder = new StringBuilder(1024);

                    while (is.available() > 0) {
                        StringBuilder.append((char) is.read());
                    }

                    String cgiReturn = StringBuilder.toString();
                    int indexOfDelimiter = cgiReturn.indexOf("\n\n");
                    String[] cgiHeader = new String[0];
                    if (indexOfDelimiter > -1) {
                        cgiHeader = cgiReturn.substring(0, indexOfDelimiter).split("\n");
                    }
                    cgiBody = cgiReturn.substring(indexOfDelimiter + 1);

                    String key;
                    String value;
                    for (int i = 0; i < cgiHeader.length; i++) {
                        indexOfDelimiter = cgiHeader[i].indexOf(':');
                        key = cgiHeader[i].substring(0, indexOfDelimiter).trim();
                        value = cgiHeader[i].substring(indexOfDelimiter + 1).trim();
                        conProp.setProperty(key, value);
                        if (key.equals("Cache-Control") && value.equals("no-cache")) {
                            nocache = true;
                        } else if (key.equals("Content-type")) {
                            mimeType = value;
                        } else if (key.equals("Status")) {
                            if (key.length() > 2) {
                                try {
                                    statusCode = Integer.parseInt(value.substring(0, 3));
                                } catch (NumberFormatException ex) {
                                    /* tough luck, we will just have to use 200 as default value */
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    exitValue = -1;
                }

                /* did the script return an exit value != 0 and still there is supposed to be
                 * everything right with the HTTP status? -> change status to 500 since 200 would
                 * be a lie
                 */
                if (exitValue != 0 && statusCode == 200) {
                    statusCode = 500;
                }

                targetDate = new Date(System.currentTimeMillis());

                if (exitValue == 0 || (cgiBody != null && !cgiBody.equals(""))) {
                    HTTPDemon.sendRespondHeader(conProp, out, httpVersion, statusCode, null, mimeType, cgiBody.length(), targetDate, null, null, null, null, nocache);
                    out.write(cgiBody.getBytes());
                } else {
                    HTTPDemon.sendRespondError(conProp, out, exitValue, statusCode, null, HeaderFramework.http1_1.get(Integer.toString(statusCode)), null);
                }
                

            } else if ((targetClass != null) && (path.endsWith(".stream"))) {
                // call rewrite-class
                requestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, conProp.getProperty(HeaderFramework.CONNECTION_PROP_CLIENTIP));
                requestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, path);
                //requestHeader.put(httpHeader.CONNECTION_PROP_INPUTSTREAM, body);
                //requestHeader.put(httpHeader.CONNECTION_PROP_OUTPUTSTREAM, out);
             
                HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null);                
                
                // in case that there are no args given, args = null or empty hashmap
                /* servletProperties tp = (servlerObjects) */ invokeServlet(targetClass, requestHeader, args);
                forceConnectionClose(conProp);
                return;                
            } else if (targetFile.exists() && targetFile.isFile() && targetFile.canRead()) {
                // we have found a file that can be written to the client
                // if this file uses templates, then we use the template
                // re-write - method to create an result
                String mimeType = MimeTable.ext2mime(targetExt, "text/html");
                final boolean zipContent = requestHeader.acceptGzip() && HTTPDemon.shallTransportZipped("." + conProp.getProperty("EXT",""));
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
                        // CGI-class: call the class to create a property for rewriting
                        try {
                            requestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, conProp.getProperty(HeaderFramework.CONNECTION_PROP_CLIENTIP));
                            requestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, path);
                            // in case that there are no args given, args = null or empty hashmap
                            final Object tmp = invokeServlet(targetClass, requestHeader, args);
                            if (tmp == null) {
                                // if no args given, then tp will be an empty Hashtable object (not null)
                                templatePatterns = new servletProperties();
                            } else if (tmp instanceof servletProperties) {
                                templatePatterns = (servletProperties) tmp;
                            } else {
                                templatePatterns = new servletProperties((serverObjects) tmp);
                            }
                            // check if the servlets requests authentification
                            if (templatePatterns.containsKey(servletProperties.ACTION_AUTHENTICATE)) {
                                // handle brute-force protection
                                if (authorization != null) {
                                    Log.logInfo("HTTPD", "dynamic log-in for account 'admin' in http file handler for path '" + path + "' from host '" + clientIP + "'");
                                    final Integer attempts = serverCore.bfHost.get(clientIP);
                                    if (attempts == null)
                                        serverCore.bfHost.put(clientIP, Integer.valueOf(1));
                                    else
                                        serverCore.bfHost.put(clientIP, Integer.valueOf(attempts.intValue() + 1));
                                }
                                // send authentication request to browser
                                final ResponseHeader headers = getDefaultHeaders(path);
                                headers.put(RequestHeader.WWW_AUTHENTICATE,"Basic realm=\"" + templatePatterns.get(servletProperties.ACTION_AUTHENTICATE, "") + "\"");
                                HTTPDemon.sendRespondHeader(conProp,out,httpVersion,401,headers);
                                return;
                            } else if (templatePatterns.containsKey(servletProperties.ACTION_LOCATION)) {
                                String location = templatePatterns.get(servletProperties.ACTION_LOCATION, "");
                                if (location.length() == 0) location = path;
                                
                                final ResponseHeader headers = getDefaultHeaders(path);
                                headers.setCookieVector(templatePatterns.getOutgoingHeader().getCookieVector()); //put the cookies into the new header TODO: can we put all headerlines, without trouble?
                                headers.put(HeaderFramework.LOCATION,location);
                                HTTPDemon.sendRespondHeader(conProp,out,httpVersion,302,headers);
                                return;
                            }
                            // add the application version, the uptime and the client name to every rewrite table
                            templatePatterns.put(servletProperties.PEER_STAT_VERSION, yacyBuildProperties.getVersion());
                            templatePatterns.put(servletProperties.PEER_STAT_UPTIME, ((System.currentTimeMillis() -  serverCore.startupTime) / 1000) / 60); // uptime in minutes
                            templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTNAME, switchboard.getConfig("peerName", "anomic"));
                            templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTID, ((Switchboard) switchboard).peers.myID());
                            templatePatterns.put(servletProperties.PEER_STAT_MYTIME, DateFormatter.formatShortSecond());
                            //System.out.println("respond props: " + ((tp == null) ? "null" : tp.toString())); // debug
                        } catch (final InvocationTargetException e) {
                            if (e.getCause() instanceof InterruptedException) {
                                throw new InterruptedException(e.getCause().getMessage());
                            }                            
                            
                            theLogger.logSevere("INTERNAL ERROR: " + e.toString() + ":" +
                                    e.getMessage() +
                                    " target exception at " + targetClass + ": " +
                                    e.getTargetException().toString() + ":" +
                                    e.getTargetException().getMessage(),e);
                            targetClass = null;
                            throw e;
                        }
                        nocache = true;
                    }

                    targetDate = new Date(targetFile.lastModified());
                    
                    // rewrite the file
                    InputStream fis = null;
                    
                    // read the file/template
                    TemplateCacheEntry templateCacheEntry = null;
                    long fileSize = targetFile.length();
                    if (useTemplateCache && fileSize <= 512 * 1024) {
                        // read from cache
                        SoftReference<TemplateCacheEntry> ref = templateCache.get(targetFile);
                        if (ref != null) {
                            templateCacheEntry = ref.get();
                            if (templateCacheEntry == null) templateCache.remove(targetFile);
                        }

                        Date targetFileDate = new Date(targetFile.lastModified());
                        if (templateCacheEntry == null || targetFileDate.after(templateCacheEntry.lastModified)) {
                            // loading the content of the template file into
                            // a byte array
                        templateCacheEntry = new TemplateCacheEntry();
                            templateCacheEntry.lastModified = targetFileDate;
                            templateCacheEntry.content = FileUtils.read(targetFile);

                            // storing the content into the cache
                            ref = new SoftReference<TemplateCacheEntry>(templateCacheEntry);
                            templateCache.put(targetFile, ref);
                            if (theLogger.isFinest()) theLogger.logFinest("Cache MISS for file " + targetFile);
                        } else {
                            if (theLogger.isFinest()) theLogger.logFinest("Cache HIT for file " + targetFile);
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
                        if(!path.startsWith("/repository")) {
                            mimeType = mimeType + "; charset=UTF-8";
                        } else {
                            // detect charset of html-files
                            if((path.endsWith("html") || path.endsWith("htm"))) {
                                // save position
                                fis.mark(1000);
                                // scrape document to look up charset
                                final ScraperInputStream htmlFilter = new ScraperInputStream(fis,"UTF-8",new DigestURI("http://localhost", null),null,false);
                                final String charset = htmlParser.patchCharsetEncoding(htmlFilter.detectCharset());
                                if(charset != null)
                                    mimeType = mimeType + "; charset="+charset;
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
                        TemplateEngine.writeTemplate(fis, o, templatePatterns, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));
                        fis.close();
                        HTTPDemon.sendRespondHeader(conProp, out, httpVersion, 200, null, mimeType, -1, targetDate, null, (templatePatterns == null) ? new ResponseHeader() : templatePatterns.getOutgoingHeader(), null, "chunked", nocache);
                        // send the content in chunked parts, see RFC 2616 section 3.6.1
                        final ChunkedOutputStream chos = new ChunkedOutputStream(out);
                        ServerSideIncludes.writeSSI(o, chos, authorization, clientIP);
                        //chos.write(result);
                        chos.finish();
                    } else {
                        // send page as whole thing, SSIs are not possible
                        final String contentEncoding = (zipContent) ? "gzip" : null;
                        // apply templates
                        final ByteBuffer o1 = new ByteBuffer();
                        TemplateEngine.writeTemplate(fis, o1, templatePatterns, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));
                        fis.close();
                        final ByteBuffer o = new ByteBuffer();
                        
                        if (zipContent) {
                            GZIPOutputStream zippedOut = new GZIPOutputStream(o);
                            ServerSideIncludes.writeSSI(o1, zippedOut, authorization, clientIP);
                            //httpTemplate.writeTemplate(fis, zippedOut, tp, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));
                            zippedOut.finish();
                            zippedOut.flush();
                            zippedOut.close();
                            zippedOut = null;
                        } else {
                            ServerSideIncludes.writeSSI(o1, o, authorization, clientIP);
                            //httpTemplate.writeTemplate(fis, o, tp, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));
                        }
                        if (method.equals(HeaderFramework.METHOD_HEAD)) {
                            HTTPDemon.sendRespondHeader(conProp, out,
                                    httpVersion, 200, null, mimeType, o.length(),
                                    targetDate, null, (templatePatterns == null) ? new ResponseHeader() : templatePatterns.getOutgoingHeader(),
                                    contentEncoding, null, nocache);
                        } else {
                            final byte[] result = o.getBytes(); // this interrupts streaming (bad idea!)
                            HTTPDemon.sendRespondHeader(conProp, out,
                                    httpVersion, 200, null, mimeType, result.length,
                                    targetDate, null, (templatePatterns == null) ? new ResponseHeader() : templatePatterns.getOutgoingHeader(),
                                    contentEncoding, null, nocache);
                            FileUtils.copy(result, out);
                        }  
                    }
                } else { // no html
                    
                    int statusCode = 200;
                    int rangeStartOffset = 0;
                    ResponseHeader header = new ResponseHeader();
                    
                    // adding the accept ranges header
                    header.put(HeaderFramework.ACCEPT_RANGES, "bytes");
                    
                    // reading the files md5 hash if availabe and use it as ETAG of the resource
                    String targetMD5 = null;
                    final File targetMd5File = new File(targetFile + ".md5");
                    try {
                        if (targetMd5File.exists()) {
                            //String description = null;
                            targetMD5 = new String(FileUtils.read(targetMd5File));
                            int pos = targetMD5.indexOf('\n');
                            if (pos >= 0) {
                                //description = targetMD5.substring(pos + 1);
                                targetMD5 = targetMD5.substring(0, pos);
                            }

                            // using the checksum as ETAG header
                            header.put(HeaderFramework.ETAG, targetMD5);
                        }
                    } catch (final IOException e) {
                        Log.logException(e);
                    }                        
                    
                    if (requestHeader.containsKey(HeaderFramework.RANGE)) {
                        final Object ifRange = requestHeader.ifRange();
                        if ((ifRange == null)||
                            (ifRange instanceof Date && targetFile.lastModified() == ((Date)ifRange).getTime()) ||
                            (ifRange instanceof String && ifRange.equals(targetMD5))) {
                            final String rangeHeaderVal = requestHeader.get(HeaderFramework.RANGE).trim();
                            if (rangeHeaderVal.startsWith("bytes=")) {
                                final String rangesVal = rangeHeaderVal.substring("bytes=".length());
                                final String[] ranges = rangesVal.split(",");
                                if ((ranges.length == 1)&&(ranges[0].endsWith("-"))) {
                                    rangeStartOffset = Integer.parseInt(ranges[0].substring(0,ranges[0].length()-1));
                                    statusCode = 206;
                                    if (header == null) header = new ResponseHeader();
                                    header.put(HeaderFramework.CONTENT_RANGE, "bytes " + rangeStartOffset + "-" + (targetFile.length()-1) + "/" + targetFile.length());
                                }
                            }
                        }
                    }
                    
                    // write the file to the client
                    targetDate = new Date(targetFile.lastModified());
                    final long   contentLength    = (zipContent)?-1:targetFile.length()-rangeStartOffset;
                    final String contentEncoding  = (zipContent) ? "gzip" : null;
                    final String transferEncoding = (httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1) && zipContent) ? "chunked" : null;
                    if (!httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1) && zipContent) forceConnectionClose(conProp);
                    
                    HTTPDemon.sendRespondHeader(conProp, out, httpVersion, statusCode, null, mimeType, contentLength, targetDate, null, header, contentEncoding, transferEncoding, nocache);
                
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
                        
                        /*
                        // wait a little time until everything closes so that clients can read from the streams/sockets
                        if ((contentLength >= 0) && (requestHeader.get(RequestHeader.CONNECTION, "close")).indexOf("keep-alive") == -1) {
                            // in case that the client knows the size in advance (contentLength present) the waiting will have no effect on the interface performance
                            // but if the client waits on a connection interruption this will slow down.
                            try {Thread.sleep(2000);} catch (final InterruptedException e) {} // FIXME: is this necessary?
                        }
                        */
                    }
                    
                    // check mime type again using the result array: these are 'magics'
//                    if (serverByteBuffer.equals(result, 1, "PNG".getBytes())) mimeType = mimeTable.getProperty("png","text/html");
//                    else if (serverByteBuffer.equals(result, 0, "GIF89".getBytes())) mimeType = mimeTable.getProperty("gif","text/html");
//                    else if (serverByteBuffer.equals(result, 6, "JFIF".getBytes())) mimeType = mimeTable.getProperty("jpg","text/html");
                    //System.out.print("MAGIC:"); for (int i = 0; i < 10; i++) System.out.print(Integer.toHexString((int) result[i]) + ","); System.out.println();
                }
            } else {
                HTTPDemon.sendRespondError(conProp,out,3,404,"File not Found",null,null);
                return;
            }
        } catch (final Exception e) {     
            try {
                // doing some errorhandling ...
                //Log.logException(e);
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
                            .append("\nClient:  ").append(conProp.getProperty(HeaderFramework.CONNECTION_PROP_CLIENTIP,"unknown")) 
                            .append("\nReason:  ").append(e.toString());    
                
                if (!conProp.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                    // sending back an error message to the client 
                    // if we have not already send an http header
                    HTTPDemon.sendRespondError(conProp,out, 4, httpStatusCode, httpStatusText, new String(errorMessage),errorExc);
                } else {
                    // otherwise we close the connection
                    forceConnectionClose(conProp);
                }    
                
                // if it is an unexpected error we log it 
                if (httpStatusCode == 500) {
                    theLogger.logWarning(new String(errorMessage),e);
                }
                
            } catch (final Exception ee) {
                forceConnectionClose(conProp);
            }            
            
        } finally {
            try {out.flush();}catch (final Exception e) {}
        }
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
    
    private static final void forceConnectionClose(final Properties conprop) {
        if (conprop != null) {
            conprop.setProperty(HeaderFramework.CONNECTION_PROP_PERSISTENT,"close");            
        }
    }

    private static final File rewriteClassFile(final File template) {
        try {
            String f = template.getCanonicalPath();
            final int p = f.lastIndexOf('.');
            if (p < 0) return null;
            f = f.substring(0, p) + ".class";
            //System.out.println("constructed class path " + f);
            final File cf = new File(f);
            if (cf.exists()) return cf;
            return null;
        } catch (final IOException e) {
            return null;
        }
    }
    
    private static final Method rewriteMethod(final File classFile) {                
        Method m = null;
        // now make a class out of the stream
        try {
            if (useTemplateCache) {                
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
            final Class<?>[] params = new Class[] {
                    RequestHeader.class,
                    serverObjects.class,
                    serverSwitch.class };
            m = c.getMethod("respond", params);
            
            if (useTemplateCache) {
                // storing the method into the cache
                final SoftReference<Method> ref = new SoftReference<Method>(m);
                templateMethodCache.put(classFile, ref);
            }
            
        } catch (final ClassNotFoundException e) {
            System.out.println("INTERNAL ERROR: class " + classFile + " is missing:" + e.getMessage()); 
        } catch (final NoSuchMethodException e) {
            System.out.println("INTERNAL ERROR: method respond not found in class " + classFile + ": " + e.getMessage());
        }
        //System.out.println("found method: " + m.toString());
        return m;
    }
    
    public static final Object invokeServlet(final File targetClass, final RequestHeader request, final serverObjects args) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        // debug functions: for special servlets call them without reflection to get better stack trace results
        /*
        if (targetClass.getName().equals("transferURL.class")) {
            try {
                return transferURL.respond(request, args, switchboard);
            } catch (Exception e) {
                Log.logException(e);
                Log.logSevere("HTTPFileHandler", "fail of transferURL", e);
                throw new InvocationTargetException(e);
            }
        }
        if (targetClass.getName().equals("crawlReceipt.class")) {
            try {
                return crawlReceipt.respond(request, args, switchboard);
            } catch (Exception e) {
                Log.logException(e);
                Log.logSevere("HTTPFileHandler", "fail of crawlReceipt", e);
                throw new InvocationTargetException(e);
            }
        }
        */
        Object result;
        if (safeServletsMode) synchronized (switchboard) {
            result = rewriteMethod(targetClass).invoke(null, new Object[] {request, args, switchboard});
        } else {
            result = rewriteMethod(targetClass).invoke(null, new Object[] {request, args, switchboard});
        }
        return result;
    }

    /**
     * Tells if a filename ends with a suffix from a given list.
     * @param filename the filename
     * @param suffixList the list of suffixes which is a string of suffixes separated by commas
     * @return true if the filename ends with a suffix from the list, else false
     */
    private static boolean matchesSuffix(String name, String suffixList) {
        boolean ret = false;

        if (suffixList != null && name != null) {
            String[] suffixes = suffixList.split(",");
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
    
}
